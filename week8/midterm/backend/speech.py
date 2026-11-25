from pathlib import Path

from flask import Blueprint, current_app, jsonify, request
from flask_jwt_extended import jwt_required
from openai import (
    APIConnectionError,
    APIError,
    APITimeoutError,
    BadRequestError,
    RateLimitError,
)


speech_bp = Blueprint(
    "speech",
    __name__,
    url_prefix="/api/speech"
)

AUDIO_CONTENT_TYPES = {
    ".mp3": "audio/mpeg",
    ".mp4": "audio/mp4",
    ".mpeg": "audio/mpeg",
    ".mpga": "audio/mpeg",
    ".m4a": "audio/mp4",
    ".wav": "audio/wav",
    ".webm": "audio/webm",
}


@speech_bp.after_request
def prevent_speech_response_caching(response):
    response.headers["Cache-Control"] = "no-store"
    return response


@speech_bp.route("/transcribe", methods=["POST"])
@jwt_required()
def transcribe_audio():
    # Read the uploaded file from the multipart form.
    audio = request.files.get("audio")

    if audio is None:
        return jsonify({
            "success": False,
            "error": "An audio file is required in the audio field"
        }), 400

    if not audio.filename:
        return jsonify({
            "success": False,
            "error": "An audio filename is required"
        }), 400

    extension = Path(audio.filename).suffix.lower()

    if extension not in AUDIO_CONTENT_TYPES:
        return jsonify({
            "success": False,
            "error": (
                "Unsupported audio format. "
                "Use mp3, mp4, mpeg, mpga, m4a, wav, or webm"
            )
        }), 415

    # Read the uploaded audio without saving a permanent file.
    audio_data = audio.read()

    if not audio_data:
        return jsonify({
            "success": False,
            "error": "The audio file is empty"
        }), 400

    client = current_app.extensions["openai_client"]

    try:
        # Forward the audio using a normalized filename and content type.
        transcription = client.audio.transcriptions.create(
            model=current_app.config["OPENAI_STT_MODEL"],
            file=(
                f"recording{extension}",
                audio_data,
                AUDIO_CONTENT_TYPES[extension]
            )
        )

    except BadRequestError:
        return jsonify({
            "success": False,
            "error": (
                "OpenAI rejected the transcription request. "
                "Check the audio file and server model configuration"
            )
        }), 400

    except APITimeoutError:
        return jsonify({
            "success": False,
            "error": "Transcription timed out. Please try a shorter recording"
        }), 504

    except RateLimitError:
        return jsonify({
            "success": False,
            "error": "OpenAI rate limit or quota exceeded"
        }), 429

    except APIConnectionError:
        return jsonify({
            "success": False,
            "error": "Unable to connect to OpenAI"
        }), 503

    except APIError as error:
        current_app.logger.warning(
            "Transcription failed: type=%s status=%s",
            type(error).__name__,
            getattr(error, "status_code", None)
        )

        return jsonify({
            "success": False,
            "error": (
                "Transcription failed. Check the server API key "
                "and transcription model access"
            )
        }), 502

    text = transcription.text.strip()

    if not text:
        return jsonify({
            "success": False,
            "error": "No speech could be transcribed from the recording"
        }), 422

    return jsonify({
        "success": True,
        "text": text
    }), 200