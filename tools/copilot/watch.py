#!/usr/bin/env python3
"""Watches the phone for offers and pushes back notes on the two stops.

Run it on the laptop while the phone is reachable over adb - the phone's
hotspot is enough, no cable needed:

    python3 tools/copilot/watch.py

It tails logcat, so nothing on the phone has to know this is running. When no
laptop is listening the phone simply has no notes, which is the normal state.

Right now the notes come from data/stores.db, which already classifies 1042
shops by where you park. The Street View photo and the model reading it slot
into advise_pickup() once the two API keys exist.
"""

import json
import os
import re
import subprocess
import sqlite3
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
STORES_DB = os.path.join(ROOT, "data", "stores.db")
BRIEF = os.path.join(ROOT, "data", "brief.json")
PHONE_DIR = "/sdcard/Android/data/com.weixu.ueatsmonitor/files"

# The line UberScreenService prints for every card it reads.
OFFER = re.compile(
    r"offer\|\s*pickup=(?P<pickup>.*?)\s*\|\s*dropoff=(?P<dropoff>.*?)"
    r"\s*\|\s*payout=(?P<payout>\S+)\s*\|\s*kind=(?P<kind>\w+)"
)

SETTING_SAYS = {
    "MALL": "商场里，得进停车场再走一段",
    "STRIP": "主街店面，门口多半停不了",
    "STANDALONE_PARKING": "独立店，自己有停车场",
    "STANDALONE": "独立店，停车情况不明",
}

PARKING_SAYS = {
    "surface": "地面停车场",
    "street_side": "只有路边位",
    "underground": "地下停车场",
    "multi-storey": "多层停车楼",
}


def stores():
    if not os.path.exists(STORES_DB):
        return []
    db = sqlite3.connect(STORES_DB)
    db.row_factory = sqlite3.Row
    rows = db.execute(
        "SELECT name, suburb, setting, parking_type, parking_m, mall FROM store"
    ).fetchall()
    db.close()
    return [dict(row) for row in rows]


def find_store(name, rows):
    """The shop OCR read, matched against the table. Exact first, then contained."""
    wanted = name.strip().lower()
    if not wanted:
        return None
    for row in rows:
        if row["name"].lower() == wanted:
            return row
    for row in rows:
        low = row["name"].lower()
        if low in wanted or wanted in low:
            return row
    return None


def advise_pickup(name, rows):
    row = find_store(name, rows)
    if row is None:
        # Saying nothing beats inventing a car park for a shop we have never seen.
        return "这家店不在我们的表里，停车情况不知道"
    parts = [SETTING_SAYS.get(row["setting"], row["setting"])]
    if row["parking_type"] in PARKING_SAYS:
        parts.append(PARKING_SAYS[row["parking_type"]])
    if row["parking_m"] is not None and row["parking_m"] >= 0:
        parts.append("最近的停车点约 " + str(row["parking_m"]) + " 米")
    if row["mall"]:
        parts.append("在 " + row["mall"])
    return "，".join(parts)


def advise_dropoff(address):
    # Nothing offline says anything true about one house. Left honest until the
    # Street View photo and the model are wired in.
    return "送餐点还没有建议（等街景和模型接上）"


def write_and_push(pickup, dropoff, rows):
    brief = {
        "at": int(time.time() * 1000),
        "pickup": {"place": pickup, "advice": advise_pickup(pickup, rows)},
        "dropoff": {"place": dropoff, "advice": advise_dropoff(dropoff)},
    }
    os.makedirs(os.path.dirname(BRIEF), exist_ok=True)
    with open(BRIEF, "w") as handle:
        json.dump(brief, handle, ensure_ascii=False, indent=2)
    done = subprocess.run(
        ["adb", "push", BRIEF, PHONE_DIR + "/brief.json"],
        capture_output=True, text=True,
    )
    ok = "推回手机" if done.returncode == 0 else "推不回去：" + done.stderr.strip()
    print("  取货：" + brief["pickup"]["advice"])
    print("  " + ok, flush=True)


def main():
    rows = stores()
    print("盯着手机的派单，" + str(len(rows)) + " 家店在表里。Ctrl-C 退出", flush=True)
    tail = subprocess.Popen(
        ["adb", "logcat", "-T", "1", "-s", "UEatsMonitor:I"],
        stdout=subprocess.PIPE, text=True,
    )
    for line in tail.stdout:
        found = OFFER.search(line)
        if not found:
            continue
        pickup = found.group("pickup")
        dropoff = found.group("dropoff")
        print(time.strftime("%H:%M:%S") + " 派单 " + pickup + " → " + dropoff, flush=True)
        write_and_push(pickup, dropoff, rows)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
