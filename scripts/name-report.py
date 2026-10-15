#!/usr/bin/env python3
"""Sums up a name check saved by TMPlayer (Settings, Save name check).

The file is the app's reading of every file name in the chat opened last: one tab separated line
per file under a header line (see NameReport in core). This prints what is worth a second look:
names that read as nothing, titles that still carry release tags, groups and extras, and the
shapes the parser saw most. It reads the file and prints; nothing is sent anywhere.

    python3 scripts/name-report.py tmplayer-names-2026-10-15-2130.txt
    python3 scripts/name-report.py --all tmplayer-names-2026-10-15-2130.txt   # every row too
"""

import argparse
import collections
import csv
import re
import sys

# Words that should never survive into a title: when one does, the cut was in the wrong place.
LEFTOVER = re.compile(
    r"\b(\d{3,4}p|x26[45]|hevc|hdrip|web-?dl|webrip|bluray|dvdrip|aac|ac3|esubs?|\d+(\.\d+)?\s?[mg]b|full movie)\b",
    re.IGNORECASE,
)


def read(path):
    with open(path, encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle, delimiter="\t", quoting=csv.QUOTE_NONE))
    if rows and "file_name" not in rows[0]:
        sys.exit(f"{path} is not a TMPlayer name check")
    return rows


def shape(name):
    """The name with its letters and digits folded, so similar names count together."""
    name = re.sub(r"[A-Za-z]+", "a", name)
    name = re.sub(r"\d+", "9", name)
    return name[:60]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("file")
    parser.add_argument("--all", action="store_true", help="print every row as read")
    args = parser.parse_args()
    rows = read(args.file)

    total = len(rows)
    films = sum(r["film"] == "yes" for r in rows)
    episodes = sum(bool(r["episode"]) or bool(r["air_date"]) for r in rows)
    grouped = collections.Counter(r["group"] for r in rows if r["group"])
    extras = collections.Counter(r["extra"] for r in rows if r["extra"])
    parts = sum(bool(r["part"]) for r in rows)
    languages = collections.Counter(code for r in rows for code in r["languages"].split(",") if code)
    no_title = [r for r in rows if not r["title"].strip()]
    leftovers = [r for r in rows if LEFTOVER.search(r["title"])]
    no_year = [r for r in rows if r["film"] == "no" and not r["episode"] and not r["air_date"]]

    print(f"{total} files: {films} read as films, {episodes} as episodes, {len(no_year)} as other videos")
    print(f"{len(grouped)} groups holding {sum(grouped.values())} files; {parts} pieces of split films")
    if extras:
        print("extras: " + ", ".join(f"{kind} {count}" for kind, count in extras.most_common()))
    if languages:
        print("languages: " + ", ".join(f"{code} {count}" for code, count in languages.most_common()))

    def listing(title, items, column="file_name"):
        if not items:
            return
        print(f"\n{title} ({len(items)}):")
        for r in items[:25]:
            print(f"  {r[column]}  ->  {r['title']!r} {r['year']}")
        if len(items) > 25:
            print(f"  ... and {len(items) - 25} more")

    listing("Titles that read as nothing", no_title)
    listing("Titles still carrying release tags", leftovers)

    shapes = collections.Counter(shape(r["file_name"]) for r in rows if r["file_name"])
    print("\nCommonest name shapes:")
    for pattern, count in shapes.most_common(10):
        print(f"  {count:5}  {pattern}")

    if args.all:
        print()
        for r in rows:
            fields = [r["title"], r["year"], r["season"], r["episode"], r["air_date"], r["part"], r["extra"], r["group"]]
            print(f"{r['file_name']}\n    " + " | ".join(fields))


if __name__ == "__main__":
    main()
