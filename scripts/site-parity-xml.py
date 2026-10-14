#!/usr/bin/env python3
"""Compares the sitemap, the changelog feed and robots.txt of two builds of the site.

  sitemap   the same <url> set; per <loc>: lastmod, changefreq, priority and every xhtml:link
            alternate; every <loc> answers 200 on the new side and is its own canonical
  feed      the channel's fields, and the items matched by GUID with the same title, link, date
            and description, in the same order
  robots    robots.txt is byte-identical and still names the sitemap

Prints `what  check  expected  got` as TSV and exits 1 on any difference.

Usage: scripts/site-parity-xml.py http://127.0.0.1:4001 http://127.0.0.1:4002 [--jobs 16]
"""

import argparse
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

ORIGIN = "https://tmplayer.org"
NS = {"s": "http://www.sitemaps.org/schemas/sitemap/0.9", "x": "http://www.w3.org/1999/xhtml"}


def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "site-parity"}),
                                    timeout=30) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:  # noqa: BLE001
        return 0, str(e).encode()


def sitemap(body):
    out = {}
    for u in ET.fromstring(body).findall("s:url", NS):
        loc = u.findtext("s:loc", namespaces=NS)
        alts = sorted((l.get("hreflang"), l.get("href")) for l in u.findall("x:link", NS))
        out[loc] = {
            "lastmod": u.findtext("s:lastmod", namespaces=NS),
            "changefreq": u.findtext("s:changefreq", namespaces=NS),
            "priority": u.findtext("s:priority", namespaces=NS),
            "alternates": alts,
            "images": sorted(i.findtext("{http://www.google.com/schemas/sitemap-image/1.1}loc") or ""
                             for i in u.findall("{http://www.google.com/schemas/sitemap-image/1.1}image")),
        }
    return out


def feed(body):
    root = ET.fromstring(body)
    ch = root.find("channel")
    chan = {c.tag: (c.text or "").strip() for c in ch if c.tag != "item"}
    chan["atom"] = sorted(str(sorted(c.attrib.items())) for c in ch if c.tag.endswith("}link"))
    items = []
    for it in ch.findall("item"):
        items.append({c.tag: " ".join((c.text or "").split()) for c in it})
    return chan, items


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--jobs", type=int, default=16)
    a = ap.parse_args()
    old, new = a.old.rstrip("/"), a.new.rstrip("/")
    rows = []

    def add(*r):
        rows.append("\t".join(str(x)[:200] for x in r))

    s1, b1 = get(old + "/sitemap.xml")
    s2, b2 = get(new + "/sitemap.xml")
    if s2 != 200:
        add("/sitemap.xml", "status", s1, s2)
    else:
        m1, m2 = sitemap(b1), sitemap(b2)
        for loc in sorted(set(m1) - set(m2)):
            add(loc, "sitemap", "listed", "missing")
        for loc in sorted(set(m2) - set(m1)):
            add(loc, "sitemap", "(not listed)", "extra")
        for loc in sorted(set(m1) & set(m2)):
            for k in m1[loc]:
                if m1[loc][k] != m2[loc][k]:
                    add(loc, f"sitemap {k}", m1[loc][k], m2[loc][k])
        if list(m1) != list(m2) and set(m1) == set(m2):
            add("/sitemap.xml", "sitemap order", "same order", "reordered")

        def live(loc):
            st, body = get(new + loc[len(ORIGIN):])
            if st != 200:
                return (loc, f"status {st}")
            m = re.search(rb'<link rel="canonical" href="([^"]*)"', body)
            if not m or m.group(1).decode() != loc:
                return (loc, f"canonical {m and m.group(1).decode()}")
            return None

        with ThreadPoolExecutor(a.jobs) as ex:
            for r in ex.map(live, list(m2)):
                if r:
                    add(r[0], "sitemap live", "200 and self-canonical", r[1])

    s1, b1 = get(old + "/changelog/feed.xml")
    s2, b2 = get(new + "/changelog/feed.xml")
    if s2 != 200:
        add("/changelog/feed.xml", "status", s1, s2)
    else:
        (c1, i1), (c2, i2) = feed(b1), feed(b2)
        for k in sorted(set(c1) | set(c2)):
            if c1.get(k) != c2.get(k):
                add("feed channel", k, c1.get(k), c2.get(k))
        g1 = [i.get("guid") for i in i1]
        g2 = [i.get("guid") for i in i2]
        if g1 != g2:
            add("feed", "guids", g1, g2)
        by2 = {i.get("guid"): i for i in i2}
        for it in i1:
            other = by2.get(it.get("guid"))
            if other is None:
                continue
            for k in sorted(set(it) | set(other)):
                if it.get(k) != other.get(k):
                    add(f"feed {it.get('guid')}", k, it.get(k), other.get(k))

    s1, b1 = get(old + "/robots.txt")
    s2, b2 = get(new + "/robots.txt")
    if b1 != b2 or s2 != 200:
        add("/robots.txt", "robots", "identical", f"status {s2}, {len(b2)} bytes")
    if b"Sitemap: https://tmplayer.org/sitemap.xml" not in b2:
        add("/robots.txt", "robots", "names the sitemap", "does not")

    for r in rows:
        print(r)
    print(f"parity-xml: {len(rows)} differences", file=sys.stderr)
    return 1 if rows else 0


if __name__ == "__main__":
    sys.exit(main())
