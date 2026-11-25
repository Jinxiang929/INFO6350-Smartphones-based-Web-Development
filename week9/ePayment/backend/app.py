import os
import re
import time
from datetime import datetime, timezone

import stripe
from flask import Flask, jsonify, request
from google.auth.exceptions import GoogleAuthError
from requests.exceptions import RequestException

from sheets_service import append_transaction
from uuid import UUID

app = Flask(__name__)


def log_payment_result(
    intent_id,
    amount,
    currency,
    note,
    status,
    customer_name,
    error_message="",
    *,
    event_id=None,
    occurred_at=None,
    checkout_id="",
):
    timestamp = occurred_at or datetime.now(timezone.utc)
    row = [
        timestamp.isoformat(),
        intent_id or "",
        amount,
        currency,
        note.strip(),
        status,
        customer_name.strip(),
        error_message,
    ]

    try:
        append_transaction(row, event_id=event_id, checkout_id=checkout_id)
    except (GoogleAuthError, RequestException, OSError, KeyError, ValueError) as exc:
        app.logger.error(
            "Sheets write unconfirmed: payment_intent_id=%s event_id=%s error_type=%s",
            intent_id,
            event_id,
            type(exc).__name__,
        )
        return False

    return True


@app.get("/health")
def health():
    return jsonify(status="ok")


@app.post("/payments/create")
def create_payment():
    if not request.is_json:
        return jsonify(
            success=False,
            message="Content-Type must be application/json",
        ), 415

    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return jsonify(
            success=False,
            message="Request body must be a valid JSON object",
        ), 400

    amount = data.get("amount")
    if type(amount) is not int or amount <= 0:
        return jsonify(
            success=False,
            message="amount must be a positive integer in minor units",
        ), 400

    currency = data.get("currency")
    if not isinstance(currency, str):
        return jsonify(success=False, message="currency must be a string"), 400

    currency = currency.strip().lower()
    if currency not in ("usd", "cad"):
        return jsonify(success=False, message="currency must be usd or cad"), 400

    note = data.get("note")
    if not isinstance(note, str):
        return jsonify(success=False, message="note must be a string"), 400

    customer_name = data.get("customer_name")
    if not isinstance(customer_name, str) or not customer_name.strip():
        return jsonify(success=False, message="customer_name is required"), 400

    test_scenario = data.get("test_scenario", "success")
    if test_scenario not in ("success", "decline"):
        return jsonify(
            success=False,
            message="test_scenario must be success or decline",
        ), 400

    secret_key = os.environ.get("STRIPE_SECRET_KEY", "")
    if not secret_key.startswith("sk_test_"):
        return jsonify(
            success=False,
            status="configuration_error",
            message="Stripe test secret key is missing or invalid",
        ), 500

    test_payment_methods = {
        "success": "pm_card_visa",
        "decline": "pm_card_visa_chargeDeclined",
    }
    client = stripe.StripeClient(secret_key)

    try:
        intent = client.v1.payment_intents.create({
            "amount": amount,
            "currency": currency,
            "automatic_payment_methods": {
                "enabled": True,
                "allow_redirects": "never",
            },
            "payment_method": test_payment_methods[test_scenario],
            "confirm": True,
        })
    except stripe.CardError as exc:
        failed_intent = getattr(exc.error, "payment_intent", None)
        intent_id = getattr(failed_intent, "id", None)
        status = getattr(failed_intent, "status", "failed")
        message = exc.user_message or "The card payment failed"

        logged = log_payment_result(
            intent_id, amount, currency, note, status, customer_name, message,
        )

        return jsonify(
            success=False,
            payment_intent_id=intent_id,
            status=status,
            error_code=exc.code,
            message=message,
            logged=logged,
        ), 402
    except stripe.StripeError as exc:
        app.logger.error(
            "Stripe request failed: type=%s request_id=%s",
            type(exc).__name__,
            exc.request_id,
        )
        return jsonify(
            success=False,
            status="stripe_error",
            message="Could not complete the Stripe request",
        ), 502

    succeeded = intent.status == "succeeded"
    logged = log_payment_result(
        intent.id, amount, currency, note, intent.status, customer_name,
    )
    return jsonify(
        success=succeeded,
        payment_intent_id=intent.id,
        status=intent.status,
        message="Payment completed" if succeeded else "Payment is not complete",
        logged=logged,
    ), 200


@app.post("/payments/intents")
def create_payment_sheet_intent():
    if not request.is_json:
        return jsonify(message="Content-Type must be application/json"), 415

    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return jsonify(message="Request body must be a valid JSON object"), 400

    amount = data.get("amount")
    if type(amount) is not int or amount <= 0:
        return jsonify(message="amount must be a positive integer in minor units"), 400

    currency = data.get("currency")
    if not isinstance(currency, str) or currency.strip().lower() not in ("usd", "cad"):
        return jsonify(message="currency must be usd or cad"), 400
    currency = currency.strip().lower()

    token_id = data.get("confirmation_token_id")
    if not isinstance(token_id, str) or not re.fullmatch(
        r"ctoken_[A-Za-z0-9]{1,200}", token_id
    ):
        return jsonify(message="A valid confirmation_token_id is required"), 400

    note = data.get("note", "")
    if not isinstance(note, str) or len(note.strip()) > 500:
        return jsonify(message="note must be a string of up to 500 characters"), 400
    note = note.strip()

    customer_name = data.get("customer_name", "")
    if not isinstance(customer_name, str) or len(customer_name.strip()) > 500:
        return jsonify(
            message="customer_name must be a string of up to 500 characters",
        ), 400
    customer_name = customer_name.strip()
    
    checkout_id = data.get("checkout_id", "")
    if not isinstance(checkout_id, str):
        return jsonify(message="checkout_id must be a UUID string"), 400

    if checkout_id:
        try:
            checkout_id = str(UUID(checkout_id))
        except ValueError:
            return jsonify(message="checkout_id must be a valid UUID"), 400

    secret_key = os.environ.get("STRIPE_SECRET_KEY", "")
    if not secret_key.startswith("sk_test_"):
        return jsonify(message="Stripe test secret key is missing or invalid"), 500

    client = stripe.StripeClient(secret_key)

    try:
        token = client.v1.confirmation_tokens.retrieve(token_id)

        if token.payment_intent or token.setup_intent:
            return jsonify(message="Confirmation token has already been used"), 409

        if token.expires_at is not None and token.expires_at <= time.time():
            return jsonify(message="Confirmation token has expired"), 400

        if not customer_name:
            preview = getattr(token, "payment_method_preview", None)
            billing_details = getattr(preview, "billing_details", None)
            cardholder = getattr(billing_details, "name", None) or ""
            customer_name = cardholder.strip()[:500]

        metadata = {"payment_source": "payment_sheet"}
        if customer_name:
            metadata["customer_name"] = customer_name
        if note:
            metadata["note"] = note
        if checkout_id:
            metadata["checkout_id"] = checkout_id

        intent = client.v1.payment_intents.create(
            {
                "amount": amount,
                "currency": currency,
                "capture_method": "automatic",
                "automatic_payment_methods": {
                    "enabled": True,
                    "allow_redirects": "never",
                },
                "metadata": metadata,
            },
            options={"idempotency_key": f"payment-sheet:{token_id}"},
        )
    except stripe.IdempotencyError:
        return jsonify(message="Use a new confirmation token for a changed payment"), 409
    except stripe.StripeError as exc:
        if exc.code == "resource_missing":
            return jsonify(message="Confirmation token not found"), 400
        if exc.code in ("amount_too_small", "amount_too_large"):
            return jsonify(message="Amount is outside Stripe's supported range"), 400

        app.logger.error(
            "Stripe intent preparation failed: type=%s code=%s request_id=%s",
            type(exc).__name__,
            exc.code,
            exc.request_id,
        )
        return jsonify(message="Could not prepare the payment"), 502

    response = jsonify(
        payment_intent_id=intent.id,
        client_secret=intent.client_secret,
        status=intent.status,
    )
    response.headers["Cache-Control"] = "no-store"
    return response, 201


@app.post("/stripe/webhook")
def stripe_webhook():
    webhook_secret = os.environ.get("STRIPE_WEBHOOK_SECRET", "")
    if not webhook_secret.startswith("whsec_"):
        return jsonify(message="Stripe webhook signing secret is missing"), 500

    try:
        event = stripe.Webhook.construct_event(
            request.get_data(),
            request.headers.get("Stripe-Signature"),
            webhook_secret,
        )
    except stripe.SignatureVerificationError:
        return jsonify(message="Invalid Stripe signature"), 400
    except ValueError:
        return jsonify(message="Invalid webhook body"), 400

    if event.livemode or event.type not in (
        "payment_intent.succeeded",
        "payment_intent.payment_failed",
    ):
        return jsonify(received=True), 200

    intent = event.data.object
    metadata = getattr(intent, "metadata", None)
    if getattr(metadata, "payment_source", None) != "payment_sheet":
        return jsonify(received=True), 200

    error_message = ""
    if event.type == "payment_intent.payment_failed":
        payment_error = getattr(intent, "last_payment_error", None)
        error_message = getattr(payment_error, "message", None) or "Payment failed"

    logged = log_payment_result(
        intent_id=intent.id,
        amount=intent.amount,
        currency=intent.currency,
        note=getattr(metadata, "note", None) or "",
        status=intent.status,
        customer_name=getattr(metadata, "customer_name", None) or "",
        error_message=error_message,
        event_id=event.id,
        occurred_at=datetime.fromtimestamp(event.created, timezone.utc),
        checkout_id=getattr(metadata, "checkout_id", None) or "",
    )
    if not logged:
        return jsonify(message="Could not confirm the Sheets write"), 502

    return jsonify(received=True), 200


@app.get("/payments/<payment_intent_id>")
def get_payment(payment_intent_id):
    if not payment_intent_id.startswith("pi_"):
        return jsonify(message="Invalid PaymentIntent ID"), 400

    secret_key = os.environ.get("STRIPE_SECRET_KEY", "")
    if not secret_key.startswith("sk_test_"):
        return jsonify(message="Stripe test secret key is missing or invalid"), 500

    client = stripe.StripeClient(secret_key)

    try:
        intent = client.v1.payment_intents.retrieve(payment_intent_id)
    except stripe.StripeError as exc:
        if exc.code == "resource_missing":
            return jsonify(message="PaymentIntent not found"), 404

        app.logger.error(
            "Stripe lookup failed: code=%s request_id=%s",
            exc.code,
            exc.request_id,
        )
        return jsonify(message="Could not retrieve the payment"), 502

    return jsonify(
        payment_intent_id=intent.id,
        status=intent.status,
        amount=intent.amount,
        currency=intent.currency,
        error_code=getattr(intent.last_payment_error, "code", None),
    ), 200


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5050)