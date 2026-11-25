# SMSapp

SMSapp is an Android app built with Kotlin, Jetpack Compose, and MVVM. It sends SMS messages, displays the latest incoming message, and posts a notification that opens the **Receive** screen. The Python Flask backend is used when you tap **Sync SMS statistics**.

## Emulator and phone number reference

| Emulator in Android Studio | AVD name | ADB serial when pinned | Self-send number with the original SIM | Destination for a two-emulator test | Earlier custom SIM number |
| --- | --- | --- | --- | --- | --- |
| Pixel 10 Pro | `Pixel_10_Pro` | `emulator-5554` | `+15551234567` | `5554` | `15555215554` |
| Pixel 10 Pro XL | `Pixel_10_Pro_XL` | `emulator-5556` | `+15551234567` | `5556` | `15555215556` |

The demo below uses one emulator. Two-emulator delivery was not verified in this setup.

- **Self-send:** Both original virtual SIM profiles use the same number. The full self-send test was verified on **Pixel 10 Pro XL** with the default modem settings. If you previously set a custom SIM number, use the active number shown in Android **Settings → About phone**. The `-phone-number` launch option takes digits only.
- **Simulated sender number:** `6505551234`. Use it as the sender in the `adb emu sms send` command. SMSapp displays it in the notification and the **From** line.
- **ADB serial:** Use the actual `emulator-…` value from `adb devices -l`. An emulator started on its own may get `emulator-5554` even if its AVD is **Pixel 10 Pro XL**. The numbers in the destination column are console ports. [Android Emulator device-to-device SMS](https://developer.android.com/studio/run/emulator-networking-voice)

## Test A — Send an SMS to yourself from Android Studio

You can run this demo entirely in Android Studio and the emulator. The full self-send test was verified on **Pixel 10 Pro XL** with its original SIM number, `+15551234567`.

1. Open `week10/SMS/android` in Android Studio. The project compiles with Android SDK Platform 37.
2. Start one emulator in **Device Manager**. If the build you want to test is already installed, open **SMSapp** from the emulator's app list. To install or update it, select the emulator next to **Run** in Android Studio and click **Run ▶**.
3. Open **Receive** and turn on **Receive SMS** and **App notifications**. Accept the in-app confirmations and any Android permission prompts. Both rows should show **Allowed**.
4. Switch to **Send**. In **Phone number**, enter the emulator's own SIM number. Use `+15551234567` for the original SIM on either Pixel. If you set a custom number, check **Android Settings → About phone** first. Enter `SMSapp self-test` as the message.
5. Tap **Send SMS → Allow**. If Android asks for SMS permission, allow it. Check **Latest sent SMS** for the destination and body.
6. Open the emulator's notification shade and tap the alert labeled **SMSapp**. Confirm that it opens **Receive** and that **Latest received SMS** shows `SMSapp self-test` and the sender number.

## Test B — Use ADB to simulate an incoming SMS

Use Terminal for this test when you want the Mac to generate a new incoming SMS on the emulator. This command was verified on **Pixel 10 Pro**. [Android Emulator SMS console command](https://developer.android.com/studio/run/emulator-console#sms)

1. Keep **Receive SMS** and **App notifications** set to **Allowed**. Stay on **Receive** to watch the message appear there, or press the emulator's **Home** button first to test notifications while SMSapp is in the background.
2. In a Mac terminal, run the first two lines to list the running devices. Set `SERIAL` to the device you actually opened, then confirm its AVD name with the last line. This example uses `emulator-5556`.

   ```bash
   ADB="$HOME/Library/Android/sdk/platform-tools/adb"
   "$ADB" devices -l
   SERIAL=emulator-5556
   "$ADB" -s "$SERIAL" shell getprop ro.boot.qemu.avd_name
   ```

   The last command should print `Pixel_10_Pro_XL` or `Pixel_10_Pro`, depending on which emulator you opened. `$HOME` expands to your Mac home directory. Set `ADB` once per terminal session and check `SERIAL` again whenever you switch emulators.

3. Run this command once for each new simulated incoming SMS. `6505551234` is the simulated sender number.

   ```bash
   "$ADB" -s "$SERIAL" emu sms send 6505551234 "SMSapp single-emulator demo"
   ```

4. Open the notification shade and tap the **SMSapp** alert titled **You have a message from 6505551234**. Confirm **Receive → Latest received SMS** shows **From 6505551234** and `SMSapp single-emulator demo`.

For a permission check, turn off **App notifications** and run the same command with a different message. **Latest received SMS** should update without a new SMSapp alert. Turn notifications back on before the demo.

## Optional cold start with a fixed port

If the emulator is already running, stop it in **Device Manager** before starting the same AVD from Terminal. Run one of the following commands. These use the default modem configuration and skip loading the Quick Boot snapshot. [Android Emulator command-line options](https://developer.android.com/studio/run/emulator-commandline)

**Pixel 10 Pro XL on port `5556`**

```bash
"$HOME/Library/Android/sdk/emulator/emulator" \
  -avd Pixel_10_Pro_XL -port 5556 -no-snapshot-load
```

**Pixel 10 Pro on port `5554`**

```bash
"$HOME/Library/Android/sdk/emulator/emulator" \
  -avd Pixel_10_Pro -port 5554 -no-snapshot-load
```

If the XL shows a different SIM number and you want to repeat the verified self-send test, append `-phone-number 15551234567` to the XL startup command. Check the assigned number in **About phone** after it starts.

## Optional backend check

From the repository root, start Flask in a separate terminal. The backend stores the most recently synced sent and received counts.

```bash
cd week10/SMS/backend
python3 -m venv .venv
source .venv/bin/activate
python -m pip install Flask
python app.py
```

Tap **Sync SMS statistics** in SMSapp and look for **SMS statistics saved.** To check the saved counts from the Mac, run:

```bash
curl http://127.0.0.1:5070/api/sms-stats
```
