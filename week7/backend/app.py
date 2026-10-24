import os
from datetime import timedelta
from pathlib import Path
from threading import Lock

from dotenv import load_dotenv
from flask import Flask, jsonify, render_template
from flask_jwt_extended import (
    JWTManager,
    get_jwt,
    jwt_required,
)
from openai import OpenAI

from auth import auth_bp
from sheets import sheets_bp
from speech import speech_bp
from translation import translation_bp
from tts import tts_bp


# Load configuration from this backend folder.
BASE_DIR = Path(__file__).resolve().parent

load_dotenv(
    dotenv_path=BASE_DIR / ".env",
    override=True
)

app = Flask(__name__)

# Limit the entire upload request to 20 MB.
app.config["MAX_CONTENT_LENGTH"] = 20 * 1000 * 1000

# Configure Google authentication.
app.config["GOOGLE_WEB_CLIENT_ID"] = os.getenv(
    "GOOGLE_WEB_CLIENT_ID", ""
).strip()

if not app.config["GOOGLE_WEB_CLIENT_ID"]:
    raise RuntimeError(
        "Please configure GOOGLE_WEB_CLIENT_ID in .env"
    )

# Configure application JWTs.
jwt_secret_key = os.getenv("JWT_SECRET_KEY", "").strip()

if len(jwt_secret_key.encode("utf-8")) < 32:
    raise RuntimeError(
        "JWT_SECRET_KEY must contain at least 32 bytes. "
        "Please configure a randomly generated secret in .env"
    )

app.config["JWT_SECRET_KEY"] = jwt_secret_key
app.config["JWT_ALGORITHM"] = "HS256"
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(hours=1)
app.config["JWT_TOKEN_LOCATION"] = ["headers"]

jwt = JWTManager(app)

# Store revoked token IDs for this server process.
revoked_tokens = set()
revoked_tokens_lock = Lock()


@jwt.token_in_blocklist_loader
def check_if_token_revoked(jwt_header, jwt_payload):
    with revoked_tokens_lock:
        return jwt_payload["jti"] in revoked_tokens


@jwt.revoked_token_loader
def handle_revoked_token(jwt_header, jwt_payload):
    return jsonify({
        "success": False,
        "error": "Access token revoked. Please sign in again"
    }), 401


@app.route("/api/auth/logout", methods=["POST"])
@jwt_required()
def logout():
    token_id = get_jwt()["jti"]

    with revoked_tokens_lock:
        revoked_tokens.add(token_id)

    return jsonify({
        "success": True,
        "message": "Signed out successfully"
    }), 200, {
        "Cache-Control": "no-store"
    }


# Configure the server-side OpenAI client.
openai_api_key = os.getenv("OPENAI_API_KEY", "").strip()

if not openai_api_key:
    raise RuntimeError(
        "Please configure OPENAI_API_KEY in .env"
    )

app.extensions["openai_client"] = OpenAI(
    api_key=openai_api_key,
    timeout=120.0,
    max_retries=0
)

# Configure speech-to-text.
app.config["OPENAI_STT_MODEL"] = os.getenv(
    "OPENAI_STT_MODEL", "gpt-transcribe"
).strip()

if not app.config["OPENAI_STT_MODEL"]:
    raise RuntimeError(
        "Please configure OPENAI_STT_MODEL in .env"
    )

# Configure translation.
app.config["OPENAI_TRANSLATION_MODEL"] = os.getenv(
    "OPENAI_TRANSLATION_MODEL", "gpt-5.6"
).strip()

if not app.config["OPENAI_TRANSLATION_MODEL"]:
    raise RuntimeError(
        "Please configure OPENAI_TRANSLATION_MODEL in .env"
    )

# Configure text-to-speech.
app.config["OPENAI_TTS_MODEL"] = os.getenv(
    "OPENAI_TTS_MODEL", "gpt-4o-mini-tts"
).strip()

app.config["OPENAI_TTS_VOICE"] = os.getenv(
    "OPENAI_TTS_VOICE", "coral"
).strip()

if not app.config["OPENAI_TTS_MODEL"]:
    raise RuntimeError(
        "Please configure OPENAI_TTS_MODEL in .env"
    )

if not app.config["OPENAI_TTS_VOICE"]:
    raise RuntimeError(
        "Please configure OPENAI_TTS_VOICE in .env"
    )

# Register API routes.
app.register_blueprint(auth_bp)
app.register_blueprint(speech_bp)
app.register_blueprint(tts_bp)
app.register_blueprint(translation_bp)
app.register_blueprint(sheets_bp)


@jwt.unauthorized_loader
def handle_missing_token(reason):
    return jsonify({
        "success": False,
        "error": "A Bearer access token is required"
    }), 401


@jwt.invalid_token_loader
def handle_invalid_token(reason):
    app.logger.warning(
        "JWT validation failed: %s",
        reason
    )

    return jsonify({
        "success": False,
        "error": "Invalid access token"
    }), 401


@jwt.expired_token_loader
def handle_expired_token(jwt_header, jwt_payload):
    return jsonify({
        "success": False,
        "error": "Access token expired. Please sign in again"
    }), 401


@app.errorhandler(413)
def handle_upload_too_large(error):
    return jsonify({
        "success": False,
        "error": "The upload request must not exceed 20 MB"
    }), 413


@app.after_request
def configure_response_headers(response):
    # Support Google sign-in during local HTTP development.
    response.headers["Referrer-Policy"] = (
        "no-referrer-when-downgrade"
    )
    response.headers["Cross-Origin-Opener-Policy"] = (
        "same-origin-allow-popups"
    )
    return response


@app.route("/", methods=["GET"])
def home():
    return render_template(
        "index.html",
        google_client_id=app.config["GOOGLE_WEB_CLIENT_ID"]
    )


@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({
        "success": True,
        "message": "Week 7 backend is running"
    }), 200


if __name__ == "__main__":
    app.run(
        host="127.0.0.1",
        port=3000,
        debug=True
    )