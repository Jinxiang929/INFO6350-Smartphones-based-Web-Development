# 4-windows

Windows Python app for testing Android project `4` with Python 3.13.

## What it does

- Connects from Windows to Android project `4` over Classic Bluetooth RFCOMM
- Uses WinRT Bluetooth packages that provide prebuilt Windows wheels for Python 3.13
- Lists paired Bluetooth devices via Windows device information so you can select one instead of typing the address
- Sends and receives plain text chat messages

## Requirements

- Windows with Bluetooth hardware
- Python 3.13
- The Windows PC and Android phone must already be paired in system Bluetooth settings

## Install

```powershell
cd 4-windows
python -m pip install -r requirements.txt
```

## Run

```powershell
cd 4-windows
python main.py
```

## How to test with Android project 4

1. Pair the Windows PC and Android phone in the system Bluetooth settings first.
2. Open Android project `4` on the phone.
3. Keep Bluetooth enabled on the phone.
4. In Windows, run `python main.py`.
5. Press `Refresh Paired Devices` if needed.
6. Select the phone from the paired device list and press `Connect Selected Device`.
7. After the connection is established, send messages from either side.

## Notes

- This Windows app is client-only.
- Android project `4` acts as the listener/server side for testing.
- This app uses Classic Bluetooth RFCOMM, not BLE.
- Some WinRT Bluetooth calls also require `winrt-Windows.Foundation.Collections` at runtime.
- `winrt.windows.networking` comes from `winrt-Windows.Networking` and is required for `HostName`.
- The `winrt.windows.storage.streams` import comes from `winrt-Windows.Storage.Streams`, not `winrt-Windows.Storage`.
