import asyncio
import queue
import re
import subprocess
import threading
import tkinter as tk
from tkinter import messagebox, ttk
from uuid import UUID

try:
    from winrt.windows.devices.bluetooth import BluetoothDevice
    from winrt.windows.devices.bluetooth.rfcomm import RfcommServiceId
    from winrt.windows.networking.sockets import StreamSocket
    from winrt.windows.storage.streams import (
        DataReader,
        DataWriter,
        InputStreamOptions,
        UnicodeEncoding,
    )
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "WinRT Bluetooth packages are required. Install them with: "
        "python -m pip install -r requirements.txt"
    ) from exc


APP_UUID = UUID("0f14b64b-d8f4-42fe-b8b4-2894cb9f6e91")


class WinBluetoothChatClient:
    def __init__(self, event_queue: queue.Queue[tuple[str, str]]) -> None:
        self.event_queue = event_queue
        self.loop = asyncio.new_event_loop()
        self.thread = threading.Thread(target=self._run_loop, daemon=True)
        self.thread.start()

        self.socket: StreamSocket | None = None
        self.reader: DataReader | None = None
        self.writer: DataWriter | None = None
        self.read_task: asyncio.Task | None = None
        self.connected_address: str | None = None
        self.connected_name: str | None = None

    def _run_loop(self) -> None:
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def submit(self, coroutine: asyncio.coroutines) -> None:
        asyncio.run_coroutine_threadsafe(coroutine, self.loop)

    async def connect(self, address: str, friendly_name: str | None = None) -> None:
        await self.disconnect()

        try:
            bt_address = int(address.replace(":", "").replace("-", ""), 16)
        except ValueError:
            self.event_queue.put(("error", "Invalid Bluetooth address format."))
            return

        try:
            device = await BluetoothDevice.from_bluetooth_address_async(bt_address)
            if device is None:
                self.event_queue.put(
                    ("error", "No paired Bluetooth device was found with that address.")
                )
                return

            service_id = RfcommServiceId.from_uuid(APP_UUID)
            services_result = await device.get_rfcomm_services_for_id_async(service_id)
            services = list(services_result.services)
            if not services:
                self.event_queue.put(
                    (
                        "error",
                        "Android project 4 is not advertising the expected RFCOMM service. "
                        "Open the Android app and keep Bluetooth enabled.",
                    )
                )
                return

            service = services[0]
            socket = StreamSocket()
            await socket.connect_async(
                service.connection_host_name,
                service.connection_service_name,
            )

            reader = DataReader(socket.input_stream)
            reader.unicode_encoding = UnicodeEncoding.UTF8
            reader.input_stream_options = InputStreamOptions.PARTIAL
            writer = DataWriter(socket.output_stream)
            writer.unicode_encoding = UnicodeEncoding.UTF8

            self.socket = socket
            self.reader = reader
            self.writer = writer
            self.connected_address = address
            self.connected_name = friendly_name or address
            self.read_task = asyncio.create_task(self._read_loop())
            self.event_queue.put(("connected", f"Connected to {self.connected_name}"))
        except Exception as exc:  # noqa: BLE001
            self.event_queue.put(("error", f"Connection failed: {exc}"))

    async def _read_loop(self) -> None:
        buffer = ""
        try:
            while self.reader is not None:
                size = await self.reader.load_async(256)
                if size == 0:
                    break

                raw = bytearray(size)
                self.reader.read_bytes(raw)
                chunk = raw.decode("utf-8", errors="replace")
                buffer += chunk

                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    text = line.rstrip()
                    if text:
                        sender = self.connected_name or self.connected_address or "Android device"
                        self.event_queue.put(("message", f"{sender}: {text}"))
        except asyncio.CancelledError:
            return
        except Exception as exc:  # noqa: BLE001
            self.event_queue.put(("error", f"Receive loop stopped: {exc}"))
        finally:
            await self.disconnect(notify=False)
            self.event_queue.put(("disconnected", "Connection closed."))

    async def send(self, message: str) -> None:
        if self.writer is None:
            self.event_queue.put(("error", "No active connection."))
            return

        try:
            self.writer.write_string(message + "\n")
            await self.writer.store_async()
            self.event_queue.put(("message", f"Windows: {message}"))
        except Exception as exc:  # noqa: BLE001
            self.event_queue.put(("error", f"Send failed: {exc}"))

    async def disconnect(self, notify: bool = True) -> None:
        if self.read_task is not None:
            self.read_task.cancel()
            self.read_task = None

        if self.writer is not None:
            try:
                self.writer.detach_stream()
            except Exception:  # noqa: BLE001
                pass
            self.writer = None

        if self.reader is not None:
            try:
                self.reader.detach_stream()
            except Exception:  # noqa: BLE001
                pass
            self.reader = None

        if self.socket is not None:
            try:
                self.socket.close()
            except Exception:  # noqa: BLE001
                pass
            self.socket = None

        self.connected_address = None
        self.connected_name = None
        if notify:
            self.event_queue.put(("status", "Disconnected"))

    def stop(self) -> None:
        future = asyncio.run_coroutine_threadsafe(self.disconnect(notify=False), self.loop)
        future.result(timeout=5)
        self.loop.call_soon_threadsafe(self.loop.stop)


class App:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title("4-windows Bluetooth Chat Tester")
        self.root.geometry("860x620")

        self.events: queue.Queue[tuple[str, str]] = queue.Queue()
        self.client = WinBluetoothChatClient(self.events)

        self.status_var = tk.StringVar(value="Idle")
        self.address_var = tk.StringVar()
        self.message_var = tk.StringVar()
        self.devices: list[tuple[str, str, str]] = []

        self._build_ui()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)
        self.root.after(100, self.process_events)

    def _build_ui(self) -> None:
        outer = ttk.Frame(self.root, padding=16)
        outer.pack(fill=tk.BOTH, expand=True)

        ttk.Label(
            outer,
            text="4-windows Bluetooth Chat Tester",
            font=("Segoe UI", 16, "bold"),
        ).pack(anchor=tk.W)

        ttk.Label(
            outer,
            text=(
                "This tool connects from Windows to Android project 4 over Classic Bluetooth RFCOMM. "
                "Pair the phone and PC first, then select the phone from the paired device list."
            ),
            wraplength=760,
        ).pack(anchor=tk.W, pady=(6, 14))

        row = ttk.Frame(outer)
        row.pack(fill=tk.X)

        ttk.Label(row, text="Phone Bluetooth Address").pack(side=tk.LEFT)
        ttk.Entry(row, textvariable=self.address_var, width=24).pack(side=tk.LEFT, padx=(10, 8))
        ttk.Button(row, text="Connect", command=self.connect).pack(side=tk.LEFT)
        ttk.Button(row, text="Disconnect", command=self.disconnect).pack(side=tk.LEFT, padx=(8, 0))
        ttk.Button(row, text="Refresh Paired Devices", command=self.refresh_devices).pack(
            side=tk.LEFT, padx=(8, 0)
        )

        ttk.Label(outer, textvariable=self.status_var).pack(anchor=tk.W, pady=(12, 8))

        devices_frame = ttk.LabelFrame(outer, text="Paired Bluetooth Devices", padding=10)
        devices_frame.pack(fill=tk.X, pady=(0, 12))

        self.device_list = tk.Listbox(devices_frame, height=6)
        self.device_list.pack(fill=tk.X)
        ttk.Button(
            devices_frame, text="Connect Selected Device", command=self.connect_selected
        ).pack(anchor=tk.W, pady=(8, 0))

        self.log = tk.Text(outer, wrap=tk.WORD, state=tk.DISABLED, height=22)
        self.log.pack(fill=tk.BOTH, expand=True)

        send_row = ttk.Frame(outer)
        send_row.pack(fill=tk.X, pady=(12, 0))
        ttk.Entry(send_row, textvariable=self.message_var).pack(side=tk.LEFT, fill=tk.X, expand=True)
        ttk.Button(send_row, text="Send Message", command=self.send_message).pack(side=tk.LEFT, padx=(8, 0))

    def append_log(self, message: str) -> None:
        self.log.configure(state=tk.NORMAL)
        self.log.insert(tk.END, message + "\n")
        self.log.see(tk.END)
        self.log.configure(state=tk.DISABLED)

    def connect(self) -> None:
        address = self.address_var.get().strip()
        if not address:
            messagebox.showinfo("Missing Address", "Enter the Android phone Bluetooth address first.")
            return
        self.status_var.set("Connecting...")
        friendly_name = None
        for name, device_address, _device_id in self.devices:
            if device_address == address:
                friendly_name = name
                break
        target_label = friendly_name or address
        self.append_log(f"Connecting to {target_label}...")
        self.client.submit(self.client.connect(address, friendly_name))

    def connect_selected(self) -> None:
        selection = self.device_list.curselection()
        if not selection:
            messagebox.showinfo("No Device Selected", "Select a paired device first.")
            return

        _name, address, _device_id = self.devices[selection[0]]
        self.address_var.set(address)
        self.connect()

    def disconnect(self) -> None:
        self.client.submit(self.client.disconnect())

    def send_message(self) -> None:
        message = self.message_var.get().strip()
        if not message:
            return
        self.client.submit(self.client.send(message))
        self.message_var.set("")

    def refresh_devices(self) -> None:
        self.status_var.set("Loading paired devices...")
        self.append_log("Refreshing paired Bluetooth devices...")
        threading.Thread(target=self._load_paired_devices, daemon=True).start()

    def _load_paired_devices(self) -> None:
        script = r"""
$devices = Get-CimInstance Win32_PnPEntity |
    Where-Object { $_.PNPClass -eq 'Bluetooth' -and $_.Name -notmatch 'Enumerator|Radio|Adapter|Device Information Service|LE Enumerator' } |
    Select-Object Name, DeviceID, Status

$result = foreach ($d in $devices) {
    $addr = $null
    if ($d.DeviceID -match 'DEV_([0-9A-F]{12})') {
        $hex = $Matches[1]
        $addr = ($hex -split '(.{2})' | Where-Object { $_ }) -join ':'
    }

    [PSCustomObject]@{
        name = $d.Name
        address = $addr
        device_id = $d.DeviceID
        status = $d.Status
    }
}

$result | ConvertTo-Json -Depth 3
"""
        try:
            completed = subprocess.run(
                ["powershell", "-NoProfile", "-Command", script],
                capture_output=True,
                text=True,
                check=True,
            )
            output = completed.stdout.strip()
            devices: list[tuple[str, str, str]] = []
            if output:
                import json

                parsed = json.loads(output)
                if isinstance(parsed, dict):
                    parsed = [parsed]
                for item in parsed:
                    name = item.get("name") or "Unknown device"
                    address = item.get("address") or ""
                    device_id = item.get("device_id") or ""
                    if address:
                        devices.append((name, address, device_id))
            self.events.put(("devices", devices))
        except Exception as exc:  # noqa: BLE001
            self.events.put(("error", f"Failed to load paired devices: {exc}"))

    def process_events(self) -> None:
        try:
            while True:
                event, payload = self.events.get_nowait()
                if event == "connected":
                    self.status_var.set(payload)
                    self.append_log(payload)
                elif event == "message":
                    self.append_log(payload)
                elif event == "disconnected":
                    self.status_var.set("Disconnected")
                    self.append_log(payload)
                elif event == "error":
                    self.status_var.set("Error")
                    self.append_log(payload)
                elif event == "status":
                    self.status_var.set(payload)
                    self.append_log(payload)
                elif event == "devices":
                    self.devices = list(payload)
                    self.device_list.delete(0, tk.END)
                    for name, address, _device_id in self.devices:
                        self.device_list.insert(tk.END, f"{name} ({address})")
                    self.status_var.set(f"Loaded {len(self.devices)} paired device(s)")
                    self.append_log(f"Loaded {len(self.devices)} paired device(s).")
        except queue.Empty:
            pass

        self.root.after(100, self.process_events)

    def on_close(self) -> None:
        self.client.stop()
        self.root.destroy()


def main() -> None:
    root = tk.Tk()
    app = App(root)
    app.append_log("Ready. Pair the phone first, open Android project 4, then connect from Windows.")
    app.refresh_devices()
    root.mainloop()


if __name__ == "__main__":
    main()
