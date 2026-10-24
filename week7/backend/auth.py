from flask import Blueprint, current_app, jsonify, request
from google.auth.exceptions import GoogleAuthError, TransportError
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token
from flask_jwt_extended import (
    create_access_token,
    get_jwt_identity,
    jwt_required,
)


auth_bp = Blueprint("auth", __name__, url_prefix="/api/auth")

@auth_bp.after_request
def prevent_auth_response_caching(response):
    response.headers["Cache-Control"] = "no-store"
    return response

@auth_bp.route("/google", methods=["POST"])
def authenticate_google():
    # Read the JSON request body.
    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return jsonify({
            "success": False,
            "error": "Request body must be a JSON object"
        }), 400

    token = data.get("id_token")

    if not isinstance(token, str) or not token.strip():
        return jsonify({
            "success": False,
            "error": "Google ID token is required"
        }), 400

    # Verify the token using Google's public certificates.
    try:
        user_info = id_token.verify_oauth2_token(
            token.strip(),
            google_requests.Request(),
            audience=current_app.config["GOOGLE_WEB_CLIENT_ID"]
        )

        google_id = user_info.get("sub")

        if not isinstance(google_id, str) or not google_id:
            raise ValueError("Missing Google user ID")

    except TransportError:
        current_app.logger.warning(
            "Unable to contact Google for token verification"
        )

        return jsonify({
            "success": False,
            "error": "Google authentication service unavailable"
        }), 503

    except (ValueError, GoogleAuthError):
        return jsonify({
            "success": False,
            "error": "Invalid or expired Google ID token"
        }), 401

    # Use the verified Google user ID as the JWT identity.
    access_token = create_access_token(identity=google_id)

    expires_in = int(
        current_app.config[
            "JWT_ACCESS_TOKEN_EXPIRES"
        ].total_seconds()
    )

    return jsonify({
        "success": True,
        "access_token": access_token,
        "token_type": "Bearer",
        "expires_in": expires_in,
        "user": {
            "google_id": google_id,
            "email": user_info.get("email"),
            "name": user_info.get("name")
        }
    }), 200
    
@auth_bp.route("/me", methods=["GET"])
@jwt_required()
def get_current_user():
    # This identity comes from a verified application JWT.
    google_id = get_jwt_identity()

    return jsonify({
        "success": True,
        "user": {
            "google_id": google_id
        }
    }), 200