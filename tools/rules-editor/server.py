#!/usr/bin/env python3
"""Rules editor for the Uber Eats offer monitor.

A local page, served from the standard library, that writes rules.json and
pushes it to the phone. No dependencies: macOS ships python3, and adb is
already here.

    python3 tools/rules-editor/server.py

Then open http://localhost:8777
"""

import http.server
import json
import os
import socketserver
import sqlite3
import subprocess
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(ROOT, "data")
RULES = os.path.join(DATA, "rules.json")
STORES_DB = os.path.join(DATA, "stores.db")
SUBURBS_CSV = os.path.join(ROOT, "app/src/main/assets/melbourne-suburbs.csv")

PHONE_DIR = "/sdcard/Android/data/com.weixu.ueatsmonitor/files"

# Geocoded once against Nominatim and pinned here, so the two places the driver
# actually starts from are one click away and work with no network.
PLACES = [
    {"label": "Wells Rd, Aspendale Gardens",
     "full": "317-369 Wells Rd, Aspendale Gardens VIC 3195",
     "lat": -38.02506, "lon": 145.12873},
    {"label": "Ambrie Cres, Noble Park",
     "full": "13 Ambrie Cres, Noble Park VIC 3174",
     "lat": -37.95370, "lon": 145.17477},
]
PORT = 8777

STARTER_ALLOW = [
    "Aspendale", "Aspendale Gardens", "Bangholme", "Bonbeach", "Braeside",
    "Carrum", "Chelsea", "Chelsea Heights", "Cheltenham", "Clarinda",
    "Clayton South", "Dandenong", "Dingley Village", "Doveton", "Edithvale",
    "Hampton", "Hampton East", "Heatherton", "Highett", "Keysborough",
    "Mentone", "Moorabbin", "Moorabbin Airport", "Moorabbin East",
    "Mordialloc", "Noble Park", "Parkdale", "Patterson Lakes", "Pennydale",
    "Seaford", "Springvale South", "Waterways",
]


def adb(*args):
    """Runs adb and returns (ok, output). Never raises."""
    try:
        done = subprocess.run(
            ["adb", *args], capture_output=True, text=True, timeout=30
        )
        return done.returncode == 0, (done.stdout + done.stderr).strip()
    except Exception as error:  # adb missing, phone asleep, cable out
        return False, str(error)


def load_rules():
    if os.path.exists(RULES):
        try:
            with open(RULES) as handle:
                return json.load(handle)
        except Exception:
            pass
    return {
        "version": 1,
        "suburbs": {"allow": STARTER_ALLOW, "deny": []},
        "stores": {"deny": []},
        "addresses": {"deny": []},
    }


def suburbs():
    out = []
    if not os.path.exists(SUBURBS_CSV):
        return out
    with open(SUBURBS_CSV) as handle:
        for line in handle:
            if line.startswith("#") or not line.strip():
                continue
            name, lat, lon = line.rstrip("\n").rsplit(",", 2)
            out.append({"name": name, "lat": float(lat), "lon": float(lon)})
    return out


def stores():
    if not os.path.exists(STORES_DB):
        return []
    db = sqlite3.connect(STORES_DB)
    db.row_factory = sqlite3.Row
    rows = db.execute(
        "SELECT name, suburb, kind, setting, mall FROM store ORDER BY name"
    ).fetchall()
    db.close()
    return [dict(row) for row in rows]


def phone_location():
    """The phone's last known fix, so the anchor can be set from where you stand."""
    ok, out = adb(
        "shell",
        "dumpsys location | grep -m1 -A2 'last location' || dumpsys location | grep -m3 'Location\\['",
    )
    if not ok:
        return None
    import re

    match = re.search(r"(-?\d+\.\d{4,}),\s*(-?\d+\.\d{4,})", out)
    if match:
        return {"lat": float(match.group(1)), "lon": float(match.group(2))}
    return None


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=HERE, **kwargs)

    def log_message(self, *args):
        pass

    def send_json(self, payload, status=200):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/state":
            connected, devices = adb("devices")
            attached = [
                line.split("\t")[0]
                for line in devices.splitlines()[1:]
                if line.strip().endswith("device")
            ]
            return self.send_json(
                {
                    "rules": load_rules(),
                    "suburbs": suburbs(),
                    "stores": stores(),
                    "places": PLACES,
                    "phone": attached[0] if attached else None,
                }
            )
        if self.path.startswith("/api/geocode"):
            from urllib.parse import urlparse, parse_qs, quote

            query = parse_qs(urlparse(self.path).query).get("q", [""])[0].strip()
            if not query:
                return self.send_json({"hit": None})
            # curl, not urllib: python on macOS ships without a root certificate
            # bundle, so urlopen fails on the certificate while curl succeeds.
            url = ("https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
                   + quote(query + ", Victoria, Australia"))
            try:
                done = subprocess.run(
                    ["curl", "-s", "-m", "20", "-A", "ueats-rules-editor/1.0", url],
                    capture_output=True, text=True, timeout=25,
                )
                found = json.loads(done.stdout or "[]")
                if found:
                    return self.send_json({"hit": {
                        "lat": float(found[0]["lat"]),
                        "lon": float(found[0]["lon"]),
                        "name": found[0]["display_name"],
                    }})
            except Exception as error:
                return self.send_json({"hit": None, "error": str(error)})
            return self.send_json({"hit": None})

        if self.path == "/api/phone-location":
            return self.send_json({"location": phone_location()})
        return super().do_GET()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")

        if self.path == "/api/save":
            os.makedirs(DATA, exist_ok=True)
            with open(RULES, "w") as handle:
                json.dump(body, handle, ensure_ascii=False, indent=2)
            ok, out = adb("push", RULES, PHONE_DIR + "/rules.json")
            return self.send_json({"saved": True, "pushed": ok, "detail": out})

        return self.send_json({"error": "unknown"}, status=404)


if __name__ == "__main__":
    os.makedirs(DATA, exist_ok=True)
    # Threaded on purpose: a single-threaded server is held hostage by one
    # keep-alive connection, and an adb call inside a request can take seconds.
    class Server(socketserver.ThreadingTCPServer):
        allow_reuse_address = True
        daemon_threads = True

    with Server(("127.0.0.1", PORT), Handler) as server:
        url = f"http://localhost:{PORT}"
        print(f"规则编辑器: {url}")
        print("Ctrl-C 退出")
        webbrowser.open(url)
        server.serve_forever()
