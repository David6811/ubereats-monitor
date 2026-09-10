#!/usr/bin/env python3
"""Rebuilds the service-area shop table from OpenStreetMap.

The table answers one question on the offer card: what kind of place is this
pickup, and where will I park. Run it when shops are missing:

    python3 tools/stores/build.py

Writes app/src/main/assets/service-area-stores.csv. Uses curl, not urllib:
macOS python ships without a root certificate bundle.
"""

import json
import math
import os
import subprocess
import time
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "app/src/main/assets/service-area-stores.csv")
CACHE = os.path.join(HERE, "cache")
# The public endpoint sheds load hard; the mirrors are tried in turn.
OVERPASS = (
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
)

# The hull the driver works: Hampton in the north west to Doveton in the east,
# Seaford in the south.
BBOX = (-38.13, 145.00, -37.90, 145.30)

# What can hand over a delivery. Anything else is not a pickup.
FOOD = ("restaurant", "fast_food", "cafe", "pub", "bar", "ice_cream")
SHOPS = (
    "supermarket", "convenience", "bakery", "butcher", "greengrocer",
    "deli", "alcohol", "chemist", "pharmacy",
)

# A car park this far from the door still counts as the shop's own.
PARKING_M = 80
# Six shops within this radius is a strip: kerbside parking, all of it taken.
NEIGHBOUR_M = 60
STRIP_NEIGHBOURS = 6

# Between calls. The public endpoints refuse a client that does not wait.
PAUSE = 30


def overpass(query):
    """One Overpass call, retried: the public endpoint sheds load under pressure."""
    for attempt in range(9):
        endpoint = OVERPASS[attempt % len(OVERPASS)]
        done = subprocess.run(
            ["curl", "-s", "-m", "240", "-X", "POST", "-d", query, endpoint],
            capture_output=True, text=True, timeout=260,
        )
        body = done.stdout.strip()
        if body.startswith("{"):
            return json.loads(body)["elements"]
        print(f"  {endpoint.split('/')[2]} 没给数据，等一下再试", flush=True)
        time.sleep(60 * (attempt + 1))
    sys.exit("Overpass 一直不给数据，稍后再跑")


def cached(name, query):
    """Every answer is kept, so a run that dies part way costs only what is left."""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name + ".json")
    if os.path.exists(path):
        with open(path) as handle:
            elements = json.load(handle)
        print(f"  {name}: {len(elements)}（用缓存）", flush=True)
        return elements
    elements = overpass(query)
    with open(path, "w") as handle:
        json.dump(elements, handle)
    print(f"  {name}: {len(elements)}", flush=True)
    time.sleep(PAUSE)
    return elements


def gather(kinds, key):
    """One tag per call. Asking for four at once is what the mirrors refuse."""
    found = []
    for kind in kinds:
        found += cached(f"{key}-{kind}", area([f'["{key}"="{kind}"]']))
    return found


def area(clauses, geometry=False):
    south, west, north, east = BBOX
    box = f"({south},{west},{north},{east})"
    body = "".join(f'  node{c}{box};\n  way{c}{box};\n' for c in clauses)
    out = "out geom tags;" if geometry else "out center tags;"
    return f"[out:json][timeout:180];\n(\n{body});\n{out}\n"


def shapes_of(elements):
    """The outlines of the mall buildings, as lists of (lat, lon)."""
    rings = []
    for element in elements:
        points = element.get("geometry") or []
        ring = [(p["lat"], p["lon"]) for p in points if "lat" in p]
        if len(ring) >= 3:
            rings.append(ring)
    return rings


def inside(point, ring):
    """Ray casting. A shop counts as in a mall only if it is actually within it."""
    lat, lon = point
    crossings = 0
    for at in range(len(ring)):
        (lat1, lon1), (lat2, lon2) = ring[at], ring[(at + 1) % len(ring)]
        if (lat1 > lat) != (lat2 > lat):
            edge = lon1 + (lat - lat1) / (lat2 - lat1) * (lon2 - lon1)
            if lon < edge:
                crossings += 1
    return crossings % 2 == 1


def point(element):
    centre = element.get("center") or element
    return centre.get("lat"), centre.get("lon")


def metres(a, b):
    radius = 6371008.8
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    h = (math.sin((lat2 - lat1) / 2) ** 2 +
         math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2)
    return 2 * radius * math.asin(math.sqrt(h))


def main():
    print("拉店铺…", flush=True)
    raw = gather(list(FOOD), "amenity") + gather(list(SHOPS), "shop")

    print("拉停车场…", flush=True)
    parks = cached("parking", area(['["amenity"="parking"]']))

    print("拉商场…", flush=True)
    malls = shapes_of(cached("mall", area(['["shop"="mall"]'], geometry=True)))

    shops = []
    for element in raw:
        tags = element.get("tags", {})
        name = (tags.get("name") or "").strip().replace(",", " ")
        lat, lon = point(element)
        if not name or lat is None:
            continue
        shops.append({
            "name": name,
            "at": (lat, lon),
            "kind": tags.get("amenity") or tags.get("shop"),
        })
    print(f"  {len(shops)} 家有名字的店，{len(parks)} 个停车场，{len(malls)} 个商场轮廓", flush=True)

    park_points = [(point(p), p.get("tags", {})) for p in parks]
    park_points = [(at, t) for at, t in park_points if at[0] is not None]

    rows = []
    for shop in shops:
        nearest, nearest_tags = None, {}
        for at, tags in park_points:
            gap = metres(shop["at"], at)
            if nearest is None or gap < nearest:
                nearest, nearest_tags = gap, tags
        neighbours = sum(
            1 for other in shops
            if other is not shop and metres(shop["at"], other["at"]) <= NEIGHBOUR_M
        )
        in_mall = any(inside(shop["at"], ring) for ring in malls)

        if in_mall:
            setting = "MALL"
        elif neighbours >= STRIP_NEIGHBOURS:
            setting = "STRIP"
        elif nearest is not None and nearest <= PARKING_M:
            setting = "STANDALONE_PARKING"
        else:
            setting = "STANDALONE"

        rows.append((
            shop["name"],
            round(shop["at"][0], 4),
            round(shop["at"][1], 4),
            shop["kind"],
            setting,
            int(nearest) if nearest is not None else -1,
            nearest_tags.get("parking", "surface"),
            neighbours,
        ))

    rows.sort(key=lambda r: r[0].lower())
    with open(OUT, "w") as handle:
        handle.write("# name,lat,lon,kind,setting,parking_m,parking_type,neighbours_60m\n")
        for row in rows:
            handle.write(",".join(str(field) for field in row) + "\n")

    counts = {}
    for row in rows:
        counts[row[4]] = counts.get(row[4], 0) + 1
    print(f"写出 {len(rows)} 家 → {OUT}")
    for setting, count in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"  {setting:20} {count}")


if __name__ == "__main__":
    main()
