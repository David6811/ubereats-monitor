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

# The two shopping strips the driver refuses to pick up from: no parking, and a
# long walk once you find some. Centres taken from the retail cores.
CBD = [
    {"key": "springvale", "label": "Springvale 市中心", "lat": -37.9483, "lon": 145.1518},
    {"key": "dandenong", "label": "Dandenong 市中心", "lat": -37.9820, "lon": 145.2148},
]
CBD_RADIUS_KM = 0.7

# Chains with their own car park. Never refused, whatever else a rule says, and
# left out of the shopping-strip expansion.
ALWAYS_OK = ["McDonald", "KFC", "Red Rooster", "Coles", "Woolworths", "Aldi"]

# Over this, an offer is judged against the far set instead of the live one.
FAR_OVER_DOLLARS = 30

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
                rules = json.load(handle)
            # A file written before the far set existed gets one, so the driver
            # sees the shipped group rather than an empty one.
            if not rules.get("far", {}).get("suburbs"):
                rules["far"] = {"overDollars": FAR_OVER_DOLLARS, "suburbs": STARTER_ALLOW}
            return rules
        except Exception:
            pass
    return {
        "version": 1,
        "profiles": [{"name": "默认", "suburbs": STARTER_ALLOW}],
        "active": "默认",
        "suburbs": {"allow": STARTER_ALLOW, "deny": []},
        # The set a payout over the threshold unlocks. Shipped with the whole
        # service area in it, which is wider than any of the working sets.
        "far": {"overDollars": FAR_OVER_DOLLARS, "suburbs": STARTER_ALLOW},
        "stores": {"deny": [], "cbdDeny": [], "alwaysOk": ALWAYS_OK},
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
        "SELECT name, suburb, kind, setting, mall, lat, lon FROM store ORDER BY name"
    ).fetchall()
    db.close()
    return [dict(row) for row in rows]


def haversine_km(a, b):
    import math

    radius = 6371.0088
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    h = (math.sin((lat2 - lat1) / 2) ** 2 +
         math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2)
    return 2 * radius * math.asin(math.sqrt(h))


def cbd_groups():
    """Stores in each core, split by whether they have their own car park.

    Only the ones that do not are proposed for the deny list - a Coles or a KFC
    with its own lot is fine to pick up from, and blanket-refusing the whole
    core would lose those.
    """
    everything = stores()
    counts = {}
    for row in everything:
        counts[row["name"]] = counts.get(row["name"], 0) + 1

    out = []
    for core in CBD:
        centre = (core["lat"], core["lon"])
        near = [
            dict(row, metres=int(haversine_km(centre, (row["lat"], row["lon"])) * 1000))
            for row in everything
            if haversine_km(centre, (row["lat"], row["lon"])) <= CBD_RADIUS_KM
        ]
        def shape(rows):
            return sorted(
                [
                    {
                        "name": r["name"],
                        "kind": r["kind"],
                        "setting": r["setting"],
                        "mall": r["mall"],
                        "metres": r["metres"],
                        # A name that also exists elsewhere would take those with it.
                        "elsewhere": counts.get(r["name"], 1) - 1,
                    }
                    for r in rows
                ],
                key=lambda r: r["metres"],
            )
        out.append({
            "key": core["key"],
            "label": core["label"],
            "radiusKm": CBD_RADIUS_KM,
            # A whitelisted chain is not proposed for refusal in the first place.
            "deny": shape([
                r for r in near
                if r["setting"] in ("STRIP", "MALL")
                and not any(ok.lower() in r["name"].lower() for ok in ALWAYS_OK)
            ]),
            "keep": shape([
                r for r in near
                if r["setting"] == "STANDALONE_PARKING"
                or any(ok.lower() in r["name"].lower() for ok in ALWAYS_OK)
            ]),
            "unknown": shape([r for r in near if r["setting"] == "STANDALONE"]),
        })
    return out


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

    def end_headers(self):
        # The page is edited while it is open. Without this the browser keeps
        # serving the copy it already has, and a fix looks like it did nothing.
        self.send_header("Cache-Control", "no-store, must-revalidate")
        super().end_headers()

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
        if self.path == "/api/cbd":
            return self.send_json({"cores": cbd_groups(), "alwaysOk": ALWAYS_OK})

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
