from flask import Flask, request, jsonify
from openai import OpenAI

import os
from datetime import timedelta
from dotenv import load_dotenv
from flask_jwt_extended import JWTManager, jwt_required, get_jwt_identity
from auth import auth_bp
from threading import Lock
from flask_jwt_extended import get_jwt

load_dotenv()

app = Flask(__name__)
client = OpenAI()

app.config["JWT_SECRET_KEY"] = os.environ["JWT_SECRET_KEY"]
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(hours=1)

jwt = JWTManager(app)
app.register_blueprint(auth_bp)

# Revoked JWT IDs (development only)
revoked_tokens = set()
revoked_tokens_lock = Lock()


@jwt.token_in_blocklist_loader
def check_if_token_revoked(jwt_header, jwt_payload):
    with revoked_tokens_lock:
        return jwt_payload["jti"] in revoked_tokens

conversation_history = {}
history_lock = Lock()



@app.route("/api/chat", methods=["POST"])
@jwt_required()
def chat():
    user_id = get_jwt_identity()

    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return jsonify({
            "error": "Request body must be JSON"
        }), 400

    prompt = data.get("prompt", "")

    if not isinstance(prompt, str) or not prompt.strip():
        return jsonify({
            "error": "Prompt is required"
        }), 400

    try:
        with history_lock:
            previous_response_id = conversation_history.get(user_id)

            if previous_response_id is None:
                response = client.responses.create(
                    model="gpt-5.6",
                    input=prompt
                )
            else:
                response = client.responses.create(
                    model="gpt-5.6",
                    input=prompt,
                    previous_response_id=previous_response_id
                )

            conversation_history[user_id] = response.id

        return jsonify({
            "response": response.output_text
        })

    except Exception:
        app.logger.exception("Chat request failed")
        return jsonify({
            "error": "Chat request failed"
        }), 500

@app.route("/api/auth/logout", methods=["POST"])
@jwt_required()
def logout():
    jti = get_jwt()["jti"]

    with revoked_tokens_lock:
        revoked_tokens.add(jti)

    return jsonify({
        "message": "Logged out successfully"
    }), 200

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=3000,
        debug=True
    )