from flask import Flask, request, jsonify

app = Flask(__name__)


@app.route("/", methods=["GET"])
def home():
    return jsonify({
        "message": "Calculator API is running"
    })


@app.route("/add", methods=["POST"])
def add():
    data = request.get_json() # required request body is JSON

    if not data or "num1" not in data or "num2" not in data:
        return jsonify({
            "error": "Please provide num1 and num2"
        }), 400

    try:
        num1 = float(data["num1"])
        num2 = float(data["num2"])
    except (ValueError, TypeError):
        return jsonify({
            "error": "num1 and num2 must be numbers"
        }), 400

    result = num1 + num2

    return jsonify({
        "num1": num1,
        "num2": num2,
        "result": result
    })


if __name__ == "__main__":
    app.run(debug=True)
    
# Client program → HTTP request → Flask service → JSON → Client program