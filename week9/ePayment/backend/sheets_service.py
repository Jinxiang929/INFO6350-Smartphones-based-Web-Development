import os
from threading import Lock
from urllib.parse import quote

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

_write_lock = Lock()


def append_transaction(row, *, event_id=None, checkout_id=""):
    credentials = service_account.Credentials.from_service_account_file(
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"],
        scopes=["https://www.googleapis.com/auth/spreadsheets"],
    )

    spreadsheet_id = os.environ["GOOGLE_SHEET_ID"]
    values_url = (
        f"https://sheets.googleapis.com/v4/spreadsheets/{spreadsheet_id}/values"
    )
    append_range = quote("Transactions!A1:J", safe="")
    values = row if event_id is None else [*row, event_id, checkout_id]

    with _write_lock, AuthorizedSession(credentials, refresh_timeout=10) as session:
        if event_id is not None:
            event_range = quote("Transactions!I2:I", safe="")
            existing = session.get(f"{values_url}/{event_range}", timeout=10)
            existing.raise_for_status()

            if [event_id] in existing.json().get("values", []):
                return None

        response = session.post(
            f"{values_url}/{append_range}:append",
            params={
                "valueInputOption": "RAW",
                "insertDataOption": "INSERT_ROWS",
            },
            json={"values": [values]},
            timeout=10,
        )
        response.raise_for_status()
        return response.json()["updates"]["updatedRange"]