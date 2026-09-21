#!/usr/bin/env python3
"""Builds the static editor into docs/, which GitHub Pages serves.

The page used to ask a local server for the suburb list, the store table and
the CBD groupings, and to write rules.json and push it over adb. Those tables
change only when the store database is rebuilt, so they are written out here
as JSON once; the rules themselves now live in Supabase and the page talks to
it directly.

    python3 tools/rules-editor/build.py
"""

import json
import os
import shutil

import server  # the same table readers, so the two never drift

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(server.ROOT, "docs")


def main():
    os.makedirs(os.path.join(OUT, "data"), exist_ok=True)
    shutil.copy(os.path.join(HERE, "index.html"), os.path.join(OUT, "index.html"))
    shutil.copy(os.path.join(HERE, "cloud.js"), os.path.join(OUT, "cloud.js"))
    shutil.copy(os.path.join(HERE, "suburbs.geojson"), os.path.join(OUT, "suburbs.geojson"))
    tables = {
        "suburbs.json": server.suburbs(),
        "stores.json": server.stores(),
        "cbd.json": {"cores": server.cbd_groups(), "alwaysOk": server.ALWAYS_OK},
    }
    for name, table in tables.items():
        with open(os.path.join(OUT, "data", name), "w") as handle:
            json.dump(table, handle, ensure_ascii=False, separators=(",", ":"))
    # Pages must not run Jekyll over the geojson and the underscore-free tree.
    open(os.path.join(OUT, ".nojekyll"), "w").close()
    print("built docs/:", ", ".join(sorted(os.listdir(OUT))))


if __name__ == "__main__":
    main()
