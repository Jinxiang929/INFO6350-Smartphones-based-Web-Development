# QRCode

QRCode is an Android app built with Kotlin, Jetpack Compose, and MVVM. It uses a Python Flask backend to generate QR codes and decode uploaded images. Live camera scanning runs on Android with CameraX and ML Kit.

## Features

- **Generate a QR code.** Enter text or a URL, then tap **Generate**. The app displays the PNG and Flask saves a copy under `week10/generated_qr/` on the Mac.
- **Scan with the camera.** Tap **Scan with Camera** and point the camera at a QR code. The decoded text appears under **Scan result**. Tap **Stop Camera** to close the preview.
- **Scan an image file.** Tap **Choose Image** and select an image. Flask decodes the image with OpenCV, and the app lists the decoded text under **Image scan result**.
- **Handle camera permission.** The app checks for camera access when scanning starts and requests it when needed. If access is denied, the app displays a message explaining that live scanning requires camera access.

## Run locally on macOS

Use Python 3.13, Android Studio with Android SDK Platform 37, and an Android 17 (API 37) emulator.

### 1. Start Flask

From the repository root, run:

```bash
cd week10/QRCode/backend
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python app.py
```

Keep this terminal open. Flask listens on `http://127.0.0.1:5060` on the Mac.

### 2. Connect the emulator to Flask

Open `week10/QRCode/android` in Android Studio and start one emulator. Once it has booted, run this command in a **second Mac terminal**:

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" -e reverse tcp:5060 tcp:5060
```

The command sends requests from the emulator's `127.0.0.1:5060` to Flask on the Mac. Run it again after restarting the emulator. Once forwarding is set up, select the emulator in Android Studio and click **Run**.

If the app shows `Failed to connect to /127.0.0.1:5060`, check that Flask is still running and rerun the forwarding command.

## Test the app

1. **Generate.** Enter `test-scan`, tap **Generate**, and confirm that the app displays a QR code and that a new PNG appears in `week10/generated_qr/`.
2. **Camera.** Tap **Scan with Camera** and allow access. Present the QR code to the camera and check for **Scan result: test-scan**. On the emulator, load the saved PNG under **Extended Controls → Camera → Virtual scene images**, then aim the virtual camera at it.
3. **Image upload.** From the repository root, copy the generated PNG into the emulator's Downloads folder. Replace `<generated-file>.png` with the actual file name:

   ```bash
   "$HOME/Library/Android/sdk/platform-tools/adb" -e push \
     "week10/generated_qr/<generated-file>.png" \
     "/sdcard/Download/qr-upload-test.png"
   ```

   In the app, tap **Choose Image → Downloads → qr-upload-test.png**. Confirm that **Image scan result** shows `test-scan`.

## Project structure

- `android/` contains the Compose UI, `QrViewModel` for screen state, and `QrRepository` for Flask requests.
- `backend/` contains the Flask app and its Python dependencies.

| Endpoint | Input | Successful response |
| --- | --- | --- |
| `POST /qr/generate` | JSON with a `text` string | `image/png` |
| `POST /qr/decode` | Multipart form data with an `image` file | JSON with a `texts` array |
