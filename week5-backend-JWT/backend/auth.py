
import os

from flask import Blueprint, jsonify, request
from flask_jwt_extended import create_access_token
from google.auth.transport.requests import Request
from google.oauth2 import id_token

auth_bp = Blueprint("auth", __name__)


@auth_bp.route("/api/auth/google", methods=["POST"])
def google_login():
    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return jsonify({"error": "JSON body required"}), 400

    google_token = data.get("id_token")

    if not isinstance(google_token, str) or not google_token:
        return jsonify({"error": "id_token required"}), 400

    try:
        user_info = id_token.verify_oauth2_token(
            google_token,
            Request(),
            os.environ["GOOGLE_WEB_CLIENT_ID"]
        )
    except ValueError:
        return jsonify({"error": "Invalid Google ID token"}), 401

    access_token = create_access_token(
        identity=user_info["sub"]
    )

    return jsonify({
        "access_token": access_token,
        "token_type": "Bearer",
        "user": {
            "name": user_info.get("name", ""),
            "email": user_info.get("email", "")
        }
    }), 200
