from datetime import datetime
from pathlib import Path
from tempfile import TemporaryDirectory
from uuid import uuid4

import qrcode
import cv2
import numpy as np
from flask import Flask, jsonify, request, send_file
from qrcode.exceptions import DataOverflowError

app = Flask(__name__)
OUTPUT_DIR = Path(__file__).resolve().parents[2] / "generated_qr"


@app.post("/qr/generate")
def generate_qr():
    body = request.get_json(silent=True)
    text = body.get("text") if isinstance(body, dict) else None

    if not isinstance(text, str) or not text.strip():
        return jsonify(error="Enter text or a URL."), 400

    try:
        image = qrcode.make(text)
    except DataOverflowError:
        return jsonify(error="The text is too long for a QR code."), 400

    filename = f"qr-{datetime.now():%Y%m%d-%H%M%S}-{uuid4().hex}.png"
    output_path = OUTPUT_DIR / filename

    try:
        OUTPUT_DIR.mkdir(exist_ok=True)
        with TemporaryDirectory(dir=OUTPUT_DIR) as temp_dir:
            temp_path = Path(temp_dir) / filename
            image.save(temp_path, format="PNG")
            temp_path.replace(output_path)
    except OSError:
        app.logger.exception("Could not save QR code to %s", output_path)
        return jsonify(error="Could not save the QR code."), 500

    return send_file(output_path, mimetype="image/png")


@app.post("/qr/decode")
def decode_qr():
    upload = request.files.get("image")
    if upload is None:
        return jsonify(error="Choose an image to upload."), 400

    image_bytes = upload.read()
    if not image_bytes:
        return jsonify(error="The uploaded file is empty."), 400

    buffer = np.frombuffer(image_bytes, dtype=np.uint8)
    try:
        image = cv2.imdecode(buffer, cv2.IMREAD_GRAYSCALE)
    except cv2.error:
        image = None

    if image is None:
        return jsonify(error="Could not read the uploaded image."), 400

    detector = cv2.QRCodeDetectorAruco()

    try:
        _, decoded_values, _, _ = detector.detectAndDecodeMulti(image)
        texts = [value for value in decoded_values if value]
    except (cv2.error, UnicodeDecodeError):
        texts = []

    if not texts:
        try:
            text, _, _ = cv2.QRCodeDetector().detectAndDecode(image)
        except (cv2.error, UnicodeDecodeError):
            text = ""

        if text:
            texts.append(text)

    if not texts:
        return jsonify(error="No readable QR code found in the image."), 422

    return jsonify(texts=texts)

if __name__ == "__main__":
    app.run(port=5060)