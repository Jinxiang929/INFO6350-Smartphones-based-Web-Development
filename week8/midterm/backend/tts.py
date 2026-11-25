from flask import Blueprint, Response, current_app, jsonify, request
from flask_jwt_extended import jwt_required
from openai import (
    APIConnectionError,
    APIError,
    APITimeoutError,
    BadRequestError,
    RateLimitError,
)


tts_bp = Blueprint(
    "tts",
    __name__,
    url_prefix="/api/speech"
)


def error_response(message, status):
    return jsonify({
        "success": False,
        "error": message
    }), status


@tts_bp.after_request
def prevent_tts_response_caching(response):
    response.headers["Cache-Control"] = "no-store"
    return response


@tts_bp.route("/synthesize", methods=["POST"])
@jwt_required()
def synthesize_speech():
    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return error_response(
            "Request body must be a JSON object",
            400
        )

    text = data.get("text")

    if not isinstance(text, str) or not text.strip():
        return error_response("Text is required", 400)

    text = text.strip()

    if len(text) > 2000:
        return error_response(
            "Text must not exceed 2000 characters",
            400
        )

    client = current_app.extensions["openai_client"]

    try:
        # Generate MP3 audio without saving a permanent server file.
        with client.audio.speech.with_streaming_response.create(
            model=current_app.config["OPENAI_TTS_MODEL"],
            voice=current_app.config["OPENAI_TTS_VOICE"],
            input=text,
            response_format="mp3"
        ) as response:
            audio_data = response.read()

    except BadRequestError:
        return error_response(
            "OpenAI rejected the speech request. "
            "Check the text, model, and voice configuration",
            400
        )

    except APITimeoutError:
        return error_response(
            "Speech generation timed out. Please try shorter text",
            504
        )

    except RateLimitError:
        return error_response(
            "OpenAI rate limit or quota exceeded",
            429
        )

    except APIConnectionError:
        return error_response(
            "Unable to connect to OpenAI",
            503
        )

    except APIError as error:
        current_app.logger.warning(
            "Speech generation failed: type=%s status=%s",
            type(error).__name__,
            getattr(error, "status_code", None)
        )

        return error_response(
            "Speech generation failed. "
            "Check the server API key and model access",
            502
        )

    if not audio_data:
        return error_response(
            "OpenAI returned empty audio",
            502
        )

    return Response(
        audio_data,
        mimetype="audio/mpeg",
        headers={
            "Content-Disposition": 'inline; filename="speech.mp3"'
        }
    )