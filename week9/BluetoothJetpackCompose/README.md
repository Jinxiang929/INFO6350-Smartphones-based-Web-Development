# Bluetooth Classroom Demo — Kotlin + Jetpack Compose

This Android app demonstrates the Bluetooth Classic lifecycle using two physical
Android phones. It supports runtime permissions, device discovery, pairing initiated
from the app, RFCOMM connections, disconnection, and two-way text messaging.

The Compose screen displays state and forwards user actions. `BluetoothViewModel`
manages screen state and connection tasks. `BluetoothRepository` handles Android
Bluetooth APIs, broadcasts, socket I/O, and resource cleanup.

## Open and run

1. Open the `BluetoothJetpackCompose` folder in Android Studio.
2. Sync Gradle. Use JDK 17 and install Android SDK Platform 35.
3. Enable USB debugging on two physical Android phones and install the same build on
   both.
4. On each phone, open the app and tap **Turn Bluetooth ON**. Grant the permissions
   Android requests. If the app asks you to tap the action again after granting
   permission, tap **Turn Bluetooth ON** again and confirm the system prompt.

## Two-phone classroom test

### Discover and pair from the app

1. Start with two phones that are not already paired. If needed, forget their existing
   pairing in Android Bluetooth settings before starting the test.
2. On Phone A, tap **Make device discoverable** and accept the Android prompt.
3. On Phone B, tap **Scan nearby devices**. Find Phone A under **Nearby devices**
   and tap it to start pairing.
4. Confirm the Android pairing prompt on each phone where it appears. On Phone B,
   verify that Phone A appears automatically under **Paired devices**.
5. To test rejection, start with unpaired phones and reject the system prompt.
   Verify that the app reports the unsuccessful pairing and lets you tap the device
   again.
6. To test the app timeout, leave the pairing prompt unanswered. If Android has not
   already returned a result after 30 seconds, return to the app and verify that
   `Pairing...` clears, the timeout status appears, and the device can be tapped
   again. If a system prompt is still open, respond to it before retrying. A later
   successful Android result still updates the paired list.

### Stop and restart the server

1. On Phone A, tap **Start server / wait for incoming connection**.
2. Wait for `Server listening`, then tap **Stop server** before Phone B connects.
3. Verify that the start button becomes available again. Start the server once more
   and wait for `Server listening`.

### Connect and exchange messages in both directions

1. On Phone B, select Phone A under **Paired devices**. Use **Get paired devices**
   to reload the list if needed.
2. Verify that both phones show a connected device.
3. On Phone B, enter `Hello from B` and tap **TEST – SEND STRING**. Check that
   Phone A displays it under **Received messages**.
4. On Phone A, enter `Hello from A` and tap **TEST – SEND STRING**. Check that
   Phone B displays it under **Received messages**.
5. For the classroom demo, send three messages in each direction over the same
   connection. Keep Phone A as the server and Phone B as the client.

### Disconnect and reconnect

1. Tap **Disconnect** on either phone. Verify that both phones eventually show
   `Not connected` and that the send button is disabled.
2. On Phone A, start the server again. On Phone B, tap Phone A under
   **Paired devices** using the existing pairing.
3. Send another message and verify that it arrives after reconnecting.
4. If **Cancel connection** is visible while Phone B is connecting, tap it and
   verify that you can try connecting again after cleanup finishes.

## Implementation notes

- The app uses Bluetooth Classic RFCOMM and the service UUID
  `00001101-0000-1000-8000-00805F9B34FB`.
- On Android 12 and later, the app requests `BLUETOOTH_SCAN`,
  `BLUETOOTH_CONNECT`, and `BLUETOOTH_ADVERTISE`. On supported older Android
  versions, it requests location permission for discovery.
- The app starts pairing and Android handles confirmation. The 30-second timeout
  releases the app's waiting state. Android may still complete the pairing and
  report the result afterward.
- Both ends send and receive UTF-8 text separated by newlines over one RFCOMM
  connection.
- Stopping or disconnecting closes the sockets and ends pending I/O. The app
  keeps the Android pairing record for the next connection.