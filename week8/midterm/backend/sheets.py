import os
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from threading import Lock
from uuid import UUID

import gspread
from flask import Blueprint, current_app, jsonify, request
from flask_jwt_extended import get_jwt_identity, jwt_required
from google.auth.exceptions import GoogleAuthError
from requests.exceptions import RequestException

from translation import SUPPORTED_LANGUAGES


sheets_bp = Blueprint("sheets", __name__, url_prefix="/api")
sheets_lock = Lock()


def error_response(message, status):
    return jsonify({
        "success": False,
        "error": message
    }), status


@lru_cache(maxsize=1)
def get_worksheet():
    base_dir = Path(__file__).resolve().parent

    credentials_path = Path(
        os.getenv("GOOGLE_CREDENTIALS_FILE", "credentials.json")
    )

    if not credentials_path.is_absolute():
        credentials_path = base_dir / credentials_path

    spreadsheet_id = os.getenv("GOOGLE_SHEETS_ID", "").strip()
    worksheet_name = os.getenv(
        "GOOGLE_SHEETS_WORKSHEET", "week7"
    ).strip()

    if not spreadsheet_id or not worksheet_name:
        raise RuntimeError("Google Sheets configuration is missing")

    client = gspread.service_account(
        filename=str(credentials_path)
    )

    return client.open_by_key(spreadsheet_id).worksheet(
        worksheet_name
    )


@sheets_bp.after_request
def prevent_sheets_response_caching(response):
    response.headers["Cache-Control"] = "no-store"
    return response


@sheets_bp.route("/submit", methods=["POST"])
@jwt_required()
def save_record():
    user_id = get_jwt_identity()
    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return error_response(
            "Request body must be a JSON object",
            400
        )

    operation = data.get("operation", "speech_to_text")
    text = data.get("text")
    record_id = data.get("record_id")

    if operation not in (
        "speech_to_text",
        "text_to_speech",
        "translation"
    ):
        return error_response("Unsupported operation", 400)

    if not isinstance(text, str) or not text.strip():
        return error_response("Text is required", 400)

    text = text.strip()
    text_limit = 2000 if operation == "translation" else 20000

    if len(text) > text_limit:
        return error_response(
            f"Text must not exceed {text_limit} characters",
            400
        )

    translated_text = ""
    target_language = ""
    source_language = ""

    if operation == "translation":
        translated_text = data.get("translated_text")
        target_language = data.get("target_language")
        source_language = data.get("source_language", "auto")

        if (
            not isinstance(translated_text, str)
            or not translated_text.strip()
        ):
            return error_response(
                "Translated text is required",
                400
            )

        translated_text = translated_text.strip()

        if len(translated_text) > 20000:
            return error_response(
                "Translated text must not exceed 20000 characters",
                400
            )

        if not isinstance(target_language, str):
            return error_response(
                "Target language is required",
                400
            )

        if not isinstance(source_language, str):
            return error_response(
                "Source language must be a string",
                400
            )

        target_language = target_language.strip().lower()
        source_language = source_language.strip().lower()

        if target_language not in SUPPORTED_LANGUAGES:
            return error_response(
                "Unsupported target language",
                400
            )

        if (
            source_language != "auto"
            and source_language not in SUPPORTED_LANGUAGES
        ):
            return error_response(
                "Unsupported source language",
                400
            )

    if not isinstance(record_id, str):
        return error_response("A valid record ID is required", 400)

    try:
        record_id = str(UUID(record_id))
    except ValueError:
        return error_response("A valid record ID is required", 400)

    expected_prefix = [
        record_id,
        user_id,
        operation,
        text
    ]

    expected_details = [
        translated_text,
        target_language,
        source_language
    ]

    try:
        # Serialize duplicate checks and writes in this server process.
        with sheets_lock:
            worksheet = get_worksheet()
            existing = worksheet.find(record_id, in_column=1)

            if existing is not None:
                row = worksheet.row_values(existing.row)

                # Older records may contain only the original five columns.
                padded_row = (row + [""] * 8)[:8]

                if (
                    padded_row[:4] != expected_prefix
                    or padded_row[5:8] != expected_details
                ):
                    return error_response(
                        "Record ID conflicts with an existing record",
                        409
                    )

                return jsonify({
                    "success": True,
                    "record_id": record_id,
                    "message": "Record already saved"
                }), 200

            worksheet.append_row(
                [
                    *expected_prefix,
                    datetime.now(timezone.utc).isoformat(),
                    *expected_details
                ],
                value_input_option="RAW"
            )

    except (
        OSError,
        ValueError,
        RuntimeError,
        gspread.exceptions.GSpreadException,
        GoogleAuthError,
        RequestException
    ) as error:
        current_app.logger.warning(
            "Google Sheets save failed: type=%s",
            type(error).__name__
        )

        return error_response(
            "Unable to save to Google Sheets. "
            "Check the server credentials, worksheet, and permissions",
            502
        )

    return jsonify({
        "success": True,
        "record_id": record_id,
        "message": "Record saved successfully"
    }), 201