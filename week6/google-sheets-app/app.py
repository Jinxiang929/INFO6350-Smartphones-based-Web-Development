
from flask import Flask, request, jsonify, render_template
import gspread
from datetime import datetime, timezone
from uuid import uuid4
from flask import render_template
import os

from dotenv import load_dotenv
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests
from functools import wraps

load_dotenv()

GOOGLE_WEB_CLIENT_ID = os.environ["GOOGLE_WEB_CLIENT_ID"]

app = Flask(__name__)

# Authenticate with Google
gc = gspread.service_account(filename="credentials.json")

SPREADSHEET_ID = "1zV6eGhOD9J8vOvXSuQiky4k4Zch06J8BZRhOYXlJS_0"

# Connect to the spreadsheet
sheet = gc.open_by_key(SPREADSHEET_ID).sheet1


def verify_google_token(token):
    """
    Verify a Google ID Token (JWT).
    Returns verified user information.
    Raises ValueError if verification fails.
    """

    id_info = id_token.verify_oauth2_token(
        token,
        google_requests.Request(),
        GOOGLE_WEB_CLIENT_ID
    )

    return {
        "google_id": id_info["sub"],
        "email": id_info.get("email"),
        "name": id_info.get("name")
    }
    

def require_google_auth(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):

        # Read Authorization header
        auth_header = request.headers.get("Authorization", "")

        if not auth_header.startswith("Bearer "):
            return jsonify({
                "success": False,
                "error": "Missing or invalid Authorization header"
            }), 401

        # Extract Google ID Token
        token = auth_header.removeprefix("Bearer ").strip()

        if not token:
            return jsonify({
                "success": False,
                "error": "Missing Google ID Token"
            }), 401

        try:
            # Verify JWT with Google
            user = verify_google_token(token)

            # Store verified user information for this request
            request.google_user = user

        except ValueError:
            return jsonify({
                "success": False,
                "error": "Invalid or expired Google ID Token"
            }), 401

        except Exception:
            app.logger.exception("Token verification error")
            return jsonify({
                "success": False,
                "error": "Authentication service unavailable"
            }), 503

        return f(*args, **kwargs)

    return decorated_function



@app.route("/api/auth/google", methods=["POST"])
def google_auth():

    data = request.get_json(silent=True) or {}
    token = data.get("id_token")

    if not isinstance(token, str) or not token:
        return jsonify({
            "success": False,
            "error": "Missing Google ID Token"
        }), 400

    try:
        user = verify_google_token(token)

        return jsonify({
            "success": True,
            "user": user
        }), 200

    except ValueError:
        return jsonify({
            "success": False,
            "error": "Invalid or expired Google ID Token"
        }), 401
    except Exception:
        app.logger.exception("Google token verification failed")
        return jsonify({
            "success": False,
            "error": "Authentication service unavailable"
        }), 503


@app.route("/api/submit", methods=["POST"])
@require_google_auth
def submit():
    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return jsonify({"error": "Expected a JSON object"}), 400

    name = data.get("name")
    message = data.get("message")

    if not isinstance(name, str) or not name.strip():
        return jsonify({"error": "Name is required"}), 400

    if not isinstance(message, str) or not message.strip():
        return jsonify({"error": "Message is required"}), 400

    row_id = str(uuid4())
    timestamp = datetime.now(timezone.utc).isoformat()

    try:
        sheet.append_row([
            row_id,
            name.strip(),
            message.strip(),
            timestamp
        ], value_input_option="RAW")
    except gspread.exceptions.APIError:
        app.logger.exception("Google Sheets API error")
        return jsonify({"error": "Failed to save data"}), 502

    return jsonify({
        "success": True,
        "id": row_id,
        "message": "Data saved successfully"
    }), 201


@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})

@app.route("/", methods=["GET"])
def home():
    return render_template("index.html")

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=3000, debug=False)
