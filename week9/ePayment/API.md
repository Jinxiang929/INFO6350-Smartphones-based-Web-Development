# PaymentApp API Reference

The Flask backend supports two Stripe sandbox flows. The Android app uses `/payments/intents` with Stripe PaymentSheet. The original `/payments/create` endpoint also remains available for a standalone backend demo.

## Base URLs

| Client | Base URL |
|---|---|
| Mac terminal or Stripe CLI | `http://127.0.0.1:5050` |
| Android Emulator | `http://10.0.2.2:5050` |

Send JSON request bodies with `Content-Type: application/json`. Amounts are positive integers in the smallest currency unit. For example, `2500` represents `25.00` USD or CAD. The payment endpoints accept `usd` and `cad` and normalize the currency to lowercase.

## Endpoint summary

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Checks whether Flask is responding. |
| `POST` | `/payments/intents` | Creates a PaymentIntent for Android PaymentSheet. |
| `GET` | `/payments/<id>` | Retrieves the current status of a PaymentIntent from Stripe. |
| `POST` | `/stripe/webhook` | Receives signed Stripe payment events and records their results in Google Sheets. |
| `POST` | `/payments/create` | Creates and confirms a sandbox payment using a predefined test scenario. |

## `GET /health`

Checks whether the Flask server is reachable. This endpoint has no request body.

### Example request

```bash
curl -i http://127.0.0.1:5050/health
```

### Example response

HTTP `200 OK`

```json
{
  "status": "ok"
}
```

| Status | Meaning |
|---|---|
| `200` | Flask is running and can serve requests. |

Use a payment request and a webhook delivery to verify Stripe and Google Sheets connectivity.

## `POST /payments/intents`

Creates a Stripe PaymentIntent for the PaymentSheet checkout. After the user taps **Pay**, PaymentSheet generates a Stripe ConfirmationToken. The Android repository sends its ID and the checkout details to Flask. Flask validates the token, creates the PaymentIntent, and returns the client secret. PaymentSheet uses that secret to confirm the payment with Stripe. [Stripe Android PaymentSheet flow](https://docs.stripe.com/payments/mobile/accept-payment?platform=android&type=payment#confirm-the-intent)

### Request fields

| Field | Type | Required | Description |
|---|---|---|---|
| `amount` | Integer | Yes | Positive amount in minor currency units. |
| `currency` | String | Yes | `usd` or `cad`. |
| `confirmation_token_id` | String | Yes | An unused, unexpired Stripe ConfirmationToken ID beginning with `ctoken_`. |
| `customer_name` | String | No | Customer name from Card Setup. Flask trims whitespace and accepts up to 500 characters. If empty or omitted, Flask uses the cardholder name from the token when available. |
| `note` | String | No | Note from the Payment screen. Flask trims whitespace and accepts up to 500 characters. |
| `checkout_id` | String | No | The Android-generated UUID for this checkout. Flask normalizes a supplied UUID. |

### Example request body

Send this body to `POST /payments/intents`. Replace `ctoken_REPLACE_ME` with a fresh ConfirmationToken from the same Stripe sandbox.

```json
{
  "amount": 2500,
  "currency": "usd",
  "confirmation_token_id": "ctoken_REPLACE_ME",
  "customer_name": "Lucia Demo",
  "note": "API documentation test",
  "checkout_id": "8f08a67e-3c89-4b2d-abf5-23bac522775a"
}
```

### Example response

HTTP `201 Created` with `Cache-Control: no-store`

```json
{
  "payment_intent_id": "pi_REPLACE_ME",
  "client_secret": "REDACTED_CLIENT_SECRET",
  "status": "requires_payment_method"
}
```

`201` confirms intent creation. `requires_payment_method` is the expected initial status for this flow. Read the final status using `GET /payments/<id>` after PaymentSheet attempts confirmation. Keep the client secret out of logs, screenshots, and documentation.

### Status codes

| Status | Meaning |
|---|---|
| `201` | The PaymentIntent was created or returned for an identical idempotent request. |
| `400` | The JSON body, amount, currency, token, name, note, or UUID is invalid. This includes a missing or expired token and an amount outside Stripe's supported range. |
| `409` | The token has already been used, or the request conflicts with a previous request using the same token. |
| `415` | The request lacks a supported JSON content type. |
| `500` | The server's Stripe test secret key is missing or fails the test-key check. |
| `502` | Flask could not complete the Stripe request. |

Example validation response:

```json
{
  "message": "A valid confirmation_token_id is required"
}
```

Flask derives its Stripe idempotency key from `confirmation_token_id`. The checkout UUID correlates records belonging to the same checkout. [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests)

## `GET /payments/<id>`

Fetches the current PaymentIntent directly from Stripe. The path parameter `<id>` is the PaymentIntent ID beginning with `pi_`. This endpoint has no request body.

### Example request

```bash
PAYMENT_INTENT_ID='pi_REPLACE_ME'
curl -i "http://127.0.0.1:5050/payments/$PAYMENT_INTENT_ID"
```

### Example response for a successful payment

HTTP `200 OK`

```json
{
  "payment_intent_id": "pi_REPLACE_ME",
  "status": "succeeded",
  "amount": 2500,
  "currency": "usd",
  "error_code": null
}
```

### Example response for a declined card

HTTP `200 OK`

```json
{
  "payment_intent_id": "pi_REPLACE_ME",
  "status": "requires_payment_method",
  "amount": 2500,
  "currency": "usd",
  "error_code": "card_declined"
}
```

HTTP `200` indicates that the lookup succeeded. The JSON `status` and `error_code` describe the payment. `error_code` is `null` when Stripe has no recorded last payment error.

### Status codes

| Status | Meaning |
|---|---|
| `200` | Stripe returned the PaymentIntent. |
| `400` | The supplied ID does not begin with `pi_`. |
| `404` | Stripe could not find that PaymentIntent in the configured sandbox. |
| `500` | The server's Stripe test secret key is missing or fails the test-key check. |
| `502` | The Stripe lookup failed for another reason. |

## `POST /stripe/webhook`

Receives Stripe events. In local development, Stripe CLI forwards signed events to Flask. Start the listener in the same sandbox used by Android and Flask.

```bash
stripe listen \
  --events payment_intent.succeeded,payment_intent.payment_failed \
  --forward-to http://127.0.0.1:5050/stripe/webhook
```

Use the `whsec_` value printed by the listener as `STRIPE_WEBHOOK_SECRET` in the backend environment. Flask calls Stripe's signature verifier with the raw HTTP request body, the `Stripe-Signature` header, and that signing secret. [Stripe webhook signature verification](https://docs.stripe.com/webhooks#verify-events)

### Example incoming event

This is an abbreviated example of the JSON sent by Stripe. Test the route using Stripe CLI forwarding so the request carries a valid signature.

```http
POST /stripe/webhook HTTP/1.1
Content-Type: application/json
Stripe-Signature: t=TIMESTAMP,v1=SIGNATURE
```

```json
{
  "id": "evt_REPLACE_ME",
  "object": "event",
  "type": "payment_intent.succeeded",
  "livemode": false,
  "created": 1794700800,
  "data": {
    "object": {
      "id": "pi_REPLACE_ME",
      "object": "payment_intent",
      "amount": 2500,
      "currency": "usd",
      "status": "succeeded",
      "last_payment_error": null,
      "metadata": {
        "payment_source": "payment_sheet",
        "customer_name": "Lucia Demo",
        "note": "API documentation test",
        "checkout_id": "8f08a67e-3c89-4b2d-abf5-23bac522775a"
      }
    }
  }
}
```

### How the webhook writes to Google Sheets

1. Flask verifies the signature before processing the event.
2. Flask selects sandbox events of type `payment_intent.succeeded` or `payment_intent.payment_failed` whose PaymentIntent metadata has `payment_source: payment_sheet`. The standalone `/payments/create` endpoint writes its own rows.
3. Flask reads the amount and status from the PaymentIntent and the note, customer name, and checkout UUID from its metadata. For a failure, Flask uses `last_payment_error.message` when available.
4. The Sheets client checks column **I** for the Stripe `evt_` ID. If that event was already recorded, Flask acknowledges the delivery. Otherwise, the client appends a row to `Transactions!A1:J` using the service account.

| Column | Stored value |
|---|---|
| A | Stripe event timestamp in UTC |
| B | PaymentIntent ID (`pi_`) |
| C | Amount in minor currency units |
| D | Currency |
| E | Payment note |
| F | Stripe PaymentIntent status |
| G | Customer name |
| H | Error message for a failed attempt |
| I | Stripe event ID (`evt_`) |
| J | Checkout UUID |

The event ID is the deduplication key. Separate failure and success events can produce separate rows for the same PaymentIntent or checkout. The Sheets check and append are serialized within the single local Flask process. [Stripe duplicate event handling](https://docs.stripe.com/webhooks#handle-duplicate-events)

### Example acknowledgement

HTTP `200 OK`

```json
{
  "received": true
}
```

### Status codes

| Status | Meaning |
|---|---|
| `200` | An eligible event was recorded or had already been recorded. The same acknowledgement is returned for validly signed events outside this flow. |
| `400` | The Stripe signature is missing or invalid, or the event body is invalid. |
| `500` | The webhook signing secret is missing or fails the `whsec_` check. |
| `502` | Flask could not confirm the Google Sheets write. |

An App success message can appear before the webhook finishes. To verify logging, check the forwarded event in Stripe CLI and find the same `pi_` ID in column **B** and the matching `evt_` ID in column **I**.

## `POST /payments/create`

Runs the original backend-only payment demo. Flask creates and confirms a test PaymentIntent using the selected scenario, then attempts to write the result directly to Google Sheets. This route does not use a PaymentSheet ConfirmationToken.

### Request fields

| Field | Type | Required | Description |
|---|---|---|---|
| `amount` | Integer | Yes | Positive amount in minor currency units. |
| `currency` | String | Yes | `usd` or `cad`. |
| `note` | String | Yes | Payment note. An empty string is accepted. |
| `customer_name` | String | Yes | Nonblank customer name. |
| `test_scenario` | String | No | `success` or `decline`. Defaults to `success`. |

### Example request

```bash
curl -i http://127.0.0.1:5050/payments/create \
  -H 'Content-Type: application/json' \
  --data '{"amount":2500,"currency":"usd","note":"Backend API demo","customer_name":"Lucia Demo","test_scenario":"success"}'
```

### Example success response

HTTP `200 OK`

```json
{
  "success": true,
  "payment_intent_id": "pi_REPLACE_ME",
  "status": "succeeded",
  "message": "Payment completed",
  "logged": true
}
```

### Example decline response

Change `test_scenario` to `decline` to exercise the decline path.

HTTP `402 Payment Required`

```json
{
  "success": false,
  "payment_intent_id": "pi_REPLACE_ME",
  "status": "requires_payment_method",
  "error_code": "card_declined",
  "message": "Your card was declined.",
  "logged": true
}
```

`logged` reports whether Flask confirmed the Sheets write. Use `success` and `status` to check the payment outcome.

### Status codes

| Status | Meaning |
|---|---|
| `200` | Stripe returned a PaymentIntent. The response includes its status and whether it succeeded. |
| `400` | The request body or one of its fields is invalid. |
| `402` | Stripe reported a card payment error, such as a decline. |
| `415` | The request lacks a supported JSON content type. |
| `500` | The server's Stripe test secret key is missing or fails the test-key check. |
| `502` | Flask could not complete the Stripe request. |