from flask import Flask, request, jsonify
from openai import OpenAI

app = Flask(__name__)
client = OpenAI()


@app.route("/api/chat", methods=["POST"])
def chat():

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
        response = client.responses.create(
            model="gpt-5.6",
            input=prompt
        )

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
        port=5001,
        debug=True
    )