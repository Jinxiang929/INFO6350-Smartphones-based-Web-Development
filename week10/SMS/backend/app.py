from contextlib import closing
from pathlib import Path
import sqlite3

from flask import Flask, jsonify, request

app = Flask(__name__)
app.config["STATS_DB"] = Path(__file__).with_name("sms_stats.sqlite3")


def connect_db():
    db = sqlite3.connect(app.config["STATS_DB"])
    db.execute("""
        CREATE TABLE IF NOT EXISTS sms_stats (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            sent INTEGER NOT NULL,
            received INTEGER NOT NULL
        )
    """)
    return db


@app.get("/api/sms-stats")
def read_stats():
    with closing(connect_db()) as db:
        row = db.execute(
            "SELECT sent, received FROM sms_stats WHERE id = 1"
        ).fetchone()
    sent, received = row or (0, 0)
    return jsonify(sent=sent, received=received)


@app.put("/api/sms-stats")
def write_stats():
    body = request.get_json(silent=True)
    if not isinstance(body, dict):
        return jsonify(error="Expected a JSON object"), 400

    sent, received = body.get("sent"), body.get("received")
    if not all(type(n) is int and 0 <= n <= 2_147_483_647 for n in (sent, received)):
        return jsonify(error="Expected two non-negative integer counts"), 400

    with closing(connect_db()) as db, db:
        db.execute("""
            INSERT INTO sms_stats (id, sent, received) VALUES (1, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                sent = excluded.sent, received = excluded.received
        """, (sent, received))
    return jsonify(sent=sent, received=received)


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5070)