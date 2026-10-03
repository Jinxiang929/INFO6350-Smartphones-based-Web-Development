from flask import Flask, request, render_template_string
from openai import OpenAI

app = Flask(__name__)
client = OpenAI()

HTML = """
<!DOCTYPE html>
<html>
<head>
    <title>PetFilter AI</title>
</head>

<body>

    <h1>🐾 PetFilter AI</h1>

    <form method="POST">

        <textarea
            name="prompt"
            placeholder="Enter your prompt..."
            rows="5"
            cols="50"
        ></textarea>

        <br><br>

        <button type="submit">
            Send
        </button>

    </form>

    {% if prompt %}
        <h2>Your Prompt:</h2>
        <p>{{ prompt }}</p>
    {% endif %}
    
    {% if response %}
        <h2>AI Response:</h2>
        <p>{{ response }}</p>
    {% endif %}

</body>
</html>
"""


@app.route("/", methods=["GET", "POST"])
def home():

    prompt = ""
    ai_response = ""

    if request.method == "POST":

        prompt = request.form.get("prompt", "")

        if prompt:
            try:
                response = client.responses.create(
                    model="gpt-5.6",
                    input=prompt
                )

                ai_response = response.output_text

            except Exception as e:
                ai_response = f"Error: {str(e)}"

    return render_template_string(
        HTML,
        prompt=prompt,
        response=ai_response
    )


if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=3000,
        debug=True
    )