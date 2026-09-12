#!/usr/bin/env python3
"""Builds the service-area road tables from OpenStreetMap.

The offer card names where it is going as a pair of streets - "Ashleigh Street &
Jean Court, Keysborough" - and the app has no way to turn that into a place. The
suburb alone is not enough: a job from Noble Park to Noble Park is not zero
kilometres away, and the suburb says it is.

    python3 tools/roads/build.py

Writes two files beside the shop table:

  service-area-crossings.csv   RoadA|RoadB|lat,lon    where two named roads meet
  service-area-roads.csv       Road|lat,lon lat,lon   points along each road

The crossings are exact rather than computed. Two roads that cross share one
node in OSM, so a node used by two differently named ways is the junction, to
the metre. The road points are the fallback for a card that names only one
street, sampled every 300 m - a street's own length is the error there anyway.

Uses curl, not urllib: macOS python ships without a root certificate bundle.
"""

import json
import math
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, "app/src/main/assets")
CACHE = os.path.join(HERE, "cache")

OVERPASS = (
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
)

# The same hull the shop table uses: Hampton in the north west to Doveton in the
# east, Seaford in the south.
BBOX = (-38.13, 145.00, -37.90, 145.30)

# What a delivery address can be on. Service roads, tracks and footpaths are not
# addresses and would double the file.
KINDS = "residential|unclassified|tertiary|secondary|primary|living_street|trunk"

# Along a road, for the one-street fallback. Finer than this buys nothing: the
# error there is the length of the street, not the spacing of the points.
SAMPLE_M = 300

# The public endpoints refuse a client that does not wait between calls.
PAUSE = 20
TILES = 4


def tiles():
    la1, lo1, la2, lo2 = BBOX
    for i in range(TILES):
        for j in range(TILES):
            yield (
                la1 + (la2 - la1) * i / TILES,
                lo1 + (lo2 - lo1) * j / TILES,
                la1 + (la2 - la1) * (i + 1) / TILES,
                lo1 + (lo2 - lo1) * (j + 1) / TILES,
            )


def fetch(box):
    """One tile's named roads, with the node ids that make them up."""
    name = "roads-%.3f-%.3f.json" % (box[0], box[1])
    path = os.path.join(CACHE, name)
    if os.path.exists(path):
        with open(path) as handle:
            return json.load(handle)

    query = (
        "[out:json][timeout:180];"
        'way["highway"~"^(%s)$"]["name"](%f,%f,%f,%f);' % (KINDS, *box) +
        # "out body", not "out tags": the tags alone come back without the node
        # list, and the node list is the whole point - a node two differently
        # named ways share is the junction between them.
        "out body qt;>;out skel qt;"
    )
    for endpoint in OVERPASS:
        done = subprocess.run(
            ["curl", "-s", "-m", "240", "-A", "ueats-roads/1.0",
             endpoint, "--data-urlencode", "data=" + query],
            capture_output=True, text=True,
        )
        try:
            data = json.loads(done.stdout)
        except Exception:
            time.sleep(PAUSE)
            continue
        if "elements" in data:
            os.makedirs(CACHE, exist_ok=True)
            with open(path, "w") as handle:
                json.dump(data, handle)
            return data
        time.sleep(PAUSE)
    raise SystemExit("Overpass would not answer for tile %s" % (box,))


def metres(a, b):
    r = math.pi / 180
    dla = (b[0] - a[0]) * r
    dlo = (b[1] - a[1]) * r
    h = (math.sin(dla / 2) ** 2 +
         math.cos(a[0] * r) * math.cos(b[0] * r) * math.sin(dlo / 2) ** 2)
    return 2 * 6371000 * math.asin(math.sqrt(h))


def main():
    nodes = {}          # id -> (lat, lon)
    ways = []           # (name, [node ids])
    for at, box in enumerate(tiles(), 1):
        print("tile %d/%d %s" % (at, TILES * TILES, [round(x, 3) for x in box]), flush=True)
        data = fetch(box)
        for el in data["elements"]:
            if el["type"] == "node":
                nodes[el["id"]] = (el["lat"], el["lon"])
            elif el["type"] == "way" and el.get("tags", {}).get("name"):
                ways.append((el["tags"]["name"], el.get("nodes", [])))
        time.sleep(PAUSE)

    # --- crossings: a node used by two differently named ways is a junction ---
    at_node = {}
    for name, ids in ways:
        for i in ids:
            at_node.setdefault(i, set()).add(name)

    crossings = {}
    for node, names in at_node.items():
        if len(names) < 2 or node not in nodes:
            continue
        ordered = sorted(names)
        for i in range(len(ordered)):
            for j in range(i + 1, len(ordered)):
                crossings.setdefault((ordered[i], ordered[j]), nodes[node])

    # --- road points, thinned to one every SAMPLE_M ---
    along = {}
    for name, ids in ways:
        points = [nodes[i] for i in ids if i in nodes]
        kept = along.setdefault(name, [])
        for point in points:
            if not kept or min(metres(point, k) for k in kept[-4:]) >= SAMPLE_M:
                kept.append(point)

    os.makedirs(ASSETS, exist_ok=True)
    out = os.path.join(ASSETS, "service-area-crossings.csv")
    with open(out, "w") as handle:
        for (a, b), (lat, lon) in sorted(crossings.items()):
            handle.write("%s|%s|%.5f,%.5f\n" % (a, b, lat, lon))
    print("crossings:", len(crossings), "->", out, os.path.getsize(out) // 1024, "KB")

    out = os.path.join(ASSETS, "service-area-roads.csv")
    with open(out, "w") as handle:
        for name, points in sorted(along.items()):
            handle.write("%s|%s\n" % (name, " ".join("%.5f,%.5f" % p for p in points)))
    print("roads:", len(along), "->", out, os.path.getsize(out) // 1024, "KB")


if __name__ == "__main__":
    main()
