from flask import Flask, request, jsonify
from openai import OpenAI

app = Flask(__name__)
client = OpenAI()

# Store the previous response ID for conversation context
previous_response_id = None


@app.route("/api/chat", methods=["POST"])
def chat():
    global previous_response_id

    data = request.get_json()

    if not data:
        return jsonify({
            "error": "Request body must be JSON"
        }), 400

    prompt = data.get("prompt", "")

    if not prompt:
        return jsonify({
            "error": "Prompt is required"
        }), 400

    try:
        if previous_response_id is None:
            # First message
            response = client.responses.create(
                model="gpt-5.6",
                input=prompt
            )
        else:
            # Continue the previous conversation
            response = client.responses.create(
                model="gpt-5.6",
                input=prompt,
                previous_response_id=previous_response_id
            )

        # Save this response ID for the next request
        previous_response_id = response.id

        return jsonify({
            "response": response.output_text
        })

    except Exception as e:
        return jsonify({
            "error": str(e)
        }), 500


if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=3000,
        debug=True
    )