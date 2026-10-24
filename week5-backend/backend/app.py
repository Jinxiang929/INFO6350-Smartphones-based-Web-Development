
from flask import Flask, request, jsonify
from openai import OpenAI
from dotenv import load_dotenv
from auth import auth_bp
from threading import Lock

# Load environment variables
load_dotenv()

app = Flask(__name__)
client = OpenAI()

# Register Google Sign-In API
app.register_blueprint(auth_bp)

# Store conversation context (local demo only)
previous_response_id = None
history_lock = Lock()


@app.route("/api/chat", methods=["POST"])
def chat():
    global previous_response_id

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

            previous_response_id = response.id

        return jsonify({
            "response": response.output_text
        }), 200

    except Exception:
        app.logger.exception("Chat request failed")
        return jsonify({
            "error": "Chat request failed"
        }), 500


if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=3000,
        debug=True
    )
