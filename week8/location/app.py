
import os
import sqlite3
import ipaddress

from datetime import datetime, timezone

from flask import (
    Flask,
    request,
    render_template,
    jsonify
)

from user_agents import parse


app = Flask(__name__)

DATABASE = os.path.join(
    app.root_path, "visitors.db"
)


# -----------------------------
# Database
# -----------------------------

def get_db():
    conn = sqlite3.connect(DATABASE)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    with get_db() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS visits (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ip TEXT,
                ip_version TEXT,
                browser TEXT,
                os TEXT,
                device TEXT,
                user_agent TEXT,
                path TEXT,
                visited_at TEXT
            )
        """)


# -----------------------------
# IP Analysis
# -----------------------------

def get_ip_info(ip):
    try:
        address = ipaddress.ip_address(ip)

        return {
            "ip": ip,
            "version": f"IPv{address.version}",
            "private": address.is_private,
            "loopback": address.is_loopback,
            "global": address.is_global
        }

    except ValueError:
        return {
            "ip": ip,
            "version": "Unknown",
            "private": False,
            "loopback": False,
            "global": False
        }


# -----------------------------
# Visitor Tracking
# -----------------------------


def record_visitor():

    client_ip = request.remote_addr or "Unknown"

    raw_user_agent = request.headers.get(
        "User-Agent", "Unknown"
    )

    ua = parse(raw_user_agent)
    ip_info = get_ip_info(client_ip)

    timestamp = datetime.now(
        timezone.utc
    ).strftime("%Y-%m-%d %H:%M:%S UTC")

    visitor_info = {
        "ip": client_ip,
        "ip_version": ip_info["version"],
        "private": ip_info["private"],
        "loopback": ip_info["loopback"],
        "global": ip_info["global"],
        "browser": ua.browser.family,
        "os": ua.os.family,
        "device": ua.device.family,
        "user_agent": raw_user_agent,
        "visited_at": timestamp
    }

    with get_db() as conn:
        conn.execute("""
            INSERT INTO visits (
                ip,
                ip_version,
                browser,
                os,
                device,
                user_agent,
                path,
                visited_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            client_ip,
            ip_info["version"],
            ua.browser.family,
            ua.os.family,
            ua.device.family,
            raw_user_agent,
            request.path,
            timestamp
        ))

    return visitor_info


# -----------------------------
# Visitor Page
# -----------------------------


@app.route("/")
def index():

    visitor_info = record_visitor()

    return render_template(
        "welcome.html",
        visitor=visitor_info
    )



# -----------------------------
# Admin Dashboard
# -----------------------------

@app.route("/admin")
def admin():

    return render_template("admin.html")


# -----------------------------
# Visitor API
# -----------------------------

@app.route("/api/visitors")
def visitors_api():

    with get_db() as conn:

        rows = conn.execute("""
            SELECT *
            FROM visits
            ORDER BY id DESC
            LIMIT 100
        """).fetchall()

        total = conn.execute("""
            SELECT COUNT(*) FROM visits
        """).fetchone()[0]

    visitors = [dict(row) for row in rows]

    return jsonify({
        "total_visits": total,
        "visitors": visitors
    })


# -----------------------------
# Start Server
# -----------------------------


if __name__ == "__main__":
    init_db()

    print("\nAdmin Dashboard: http://127.0.0.1:5001/admin\n")

    app.run(
        host="0.0.0.0",
        port=5001,
        debug=False
    )

