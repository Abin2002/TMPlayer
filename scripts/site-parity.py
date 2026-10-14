#!/usr/bin/env python3
"""Compares every page of two builds of the site and fails on any difference a reader, a crawler or
a search engine could notice.

Both sides are base URLs served Vercel-style (scripts/serve-vercel.py, or the real hosts). The
page list is the baseline's (`--urls`, one path per line, as written by AM00); `--new-dir` also
lists the new build's pages so an extra page is caught.

Checks per page, each reported as one TSV line `url  check  expected  got`:

  status    the page answers 200 (404.html: 404 for an unknown path)
  head      <html lang dir>, <title>, every <meta> and <link> as a set of attribute tuples
  canonical the canonical equals og:url and the page's own slash URL (404 has none)
  hreflang  the alternates are reciprocal: each alternate page links back to this one
  og-image  each og:image answers 200 with its stated type, and its pixel size matches
  jsonld    every JSON-LD block, parsed and compared as data (key order ignored)
  langs     the tm-langs and tm-strings JSON blocks, as data
  text      the visible text, block by block, whitespace collapsed
  elements  every element in document order with its attributes (href, src, alt, class, ...)
  h1        exactly one <h1>
  assets    stylesheets and scripts, matched by content (hashed /_astro/ names map back)

allowed-diffs.json (next to this script, or --allowed) lists differences that are expected, each
with a reason: {"check": "...", "url": "regex", "expected": "regex", "got": "regex", "reason": "..."}.

Usage: scripts/site-parity.py http://127.0.0.1:4001 http://127.0.0.1:4002 --urls urls.txt
       [--new-dir site/dist] [--page id] [--lang es] [--jobs 16] [--quiet]
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlsplit

ROOT = Path(__file__).resolve().parent.parent
ORIGIN = "https://tmplayer.org"
LANG_PREFIXES = ["es", "pt", "fr", "de", "it", "pl", "ru", "uk", "tr", "id", "vi", "ar", "fa", "zh",
                 "zh-tw", "ja", "ko", "hi", "bn", "ml"]
PAGE_DIRS = {
    "home": "", "features": "features/", "download": "download/", "install": "install/",
    "android_tv": "android-tv/", "fire_tv": "fire-tv/", "faq": "faq/", "formats": "formats/",
    "translate": "translate/", "guide_android_tv": "guides/telegram-videos-on-android-tv/",
    "guide_fire_tv": "guides/telegram-videos-on-fire-tv/",
    "guide_no_casting": "guides/telegram-on-tv-without-casting/",
    "legal": "legal/", "privacy": "privacy/", "security": "security/", "signing": "signing/",
    "support": "support/", "changelog": "changelog/", "404": "404",
}
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source",
        "track", "wbr"}
INLINE = {"a", "abbr", "b", "bdi", "br", "cite", "code", "em", "i", "img", "kbd", "mark", "q",
          "s", "samp", "small", "span", "strong", "sub", "sup", "svg", "time", "u", "var", "wbr",
          "button", "label", "path", "circle", "g", "line", "rect", "polyline", "polygon", "use"}
DROP_ATTRS = re.compile(r"^data-i18n(-.*)?$|^data-astro-.*")
URL_ATTRS = {"href", "src", "poster", "data-theme-src", "data-dark-src", "action"}


# ---------------------------------------------------------------------------- fetching

def fetch(url, cache={}):
    if url in cache:
        return cache[url]
    req = urllib.request.Request(url, headers={"User-Agent": "site-parity"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            out = (r.status, dict((k.lower(), v) for k, v in r.headers.items()), r.read())
    except urllib.error.HTTPError as e:
        out = (e.code, dict((k.lower(), v) for k, v in e.headers.items()), e.read())
    except Exception as e:  # noqa: BLE001 - a failed fetch is a finding, not a crash
        out = (0, {}, str(e).encode())
    cache[url] = out
    return out


# ---------------------------------------------------------------------------- parsing

class Page(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.html_attrs = {}
        self.title = None
        self.metas = []
        self.links = []
        self.ld = []
        self.json_blocks = {}
        self.inline_scripts = []
        self.scripts = []
        self.styles = []
        self.elements = []
        self.texts = []
        self.h1 = 0
        self._stack = []
        self._buf = []
        self._in = None
        self._raw = []
        self._in_head = False
        self._in_body = False
        self.feed(text)
        self.close()
        self._flush()

    def path(self):
        return "/".join(t for t, _ in self._stack[-4:])

    def _flush(self):
        s = " ".join("".join(self._buf).split())
        if s:
            self.texts.append((self.path(), s))
        self._buf = []

    def handle_starttag(self, tag, attrs):
        a = {k: (v if v is not None else "") for k, v in attrs if not DROP_ATTRS.match(k)}
        if tag == "html":
            self.html_attrs = a
        elif tag == "head":
            self._in_head = True
        elif tag == "body":
            self._in_head = False
            self._in_body = True
        elif tag == "meta":
            self.metas.append(tuple(sorted(a.items())))
        elif tag == "link":
            if a.get("rel") == "stylesheet":
                self.styles.append(a.get("href", ""))
                a = {k: v for k, v in a.items() if k != "href"}
            self.links.append(tuple(sorted(a.items())))
        elif tag == "title":
            self._in = "title"
            self._raw = []
        elif tag == "script":
            if a.get("src"):
                self.scripts.append((a["src"], tuple(sorted((k, v) for k, v in a.items() if k != "src"))))
            self._in = ("script", a)
            self._raw = []
        elif tag == "style":
            self._in = ("style", a)
            self._raw = []
        if tag == "h1":
            self.h1 += 1
        if self._in_body and tag not in ("script", "style"):
            if tag not in INLINE:
                self._flush()
            self.elements.append((tag, tuple(sorted(a.items()))))
            if tag not in VOID:
                self._stack.append((tag, a.get("class", "")))

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if self._in_body and tag not in VOID and tag not in ("script", "style") and self._stack:
            self._stack.pop()

    def handle_endtag(self, tag):
        if tag in ("title", "script", "style") and self._in:
            raw = "".join(self._raw)
            if self._in == "title":
                self.title = " ".join(raw.split())
            elif self._in[0] == "script":
                a = self._in[1]
                if a.get("type") == "application/ld+json":
                    self.ld.append(raw)
                elif a.get("type") == "application/json":
                    self.json_blocks[a.get("id", "")] = raw
                elif not a.get("src"):
                    self.inline_scripts.append(" ".join(raw.split()))
            self._in = None
            return
        if not self._in_body or tag in VOID:
            return
        if tag not in INLINE:
            self._flush()
        # Pop to the matching tag (HTML's optional end tags close implicitly).
        for i in range(len(self._stack) - 1, -1, -1):
            if self._stack[i][0] == tag:
                del self._stack[i:]
                break

    def handle_data(self, data):
        if self._in:
            self._raw.append(data)
        elif self._in_body:
            self._buf.append(data)


# ---------------------------------------------------------------------------- normalizing

def norm_url(value, page_url):
    """Resolves a relative URL against the page, so `shot.webp` and `/features/shot.webp` agree."""
    if not value or value.startswith(("#", "mailto:", "tel:", "data:", "javascript:")):
        return value
    if re.match(r"^[a-z][a-z0-9+.-]*:", value) or value.startswith("//"):
        return value
    return urlsplit(urljoin(ORIGIN + page_url, value))._replace(scheme="", netloc="").geturl()


def norm_attrs(attrs, page_url):
    out = []
    for k, v in attrs:
        if k in URL_ATTRS:
            v = norm_url(v, page_url)
        elif k == "srcset":
            v = ", ".join(" ".join([norm_url(p.split()[0], page_url)] + p.split()[1:])
                          for p in v.split(",") if p.strip())
        out.append((k, v))
    return tuple(sorted(out))


def deep_sort(x):
    if isinstance(x, dict):
        return {k: deep_sort(v) for k, v in sorted(x.items())}
    if isinstance(x, list):
        return [deep_sort(v) for v in x]
    return x


def short(x, n=160):
    s = x if isinstance(x, str) else json.dumps(x, ensure_ascii=False, sort_keys=True)
    s = s.replace("\t", " ").replace("\n", " ")
    return s if len(s) <= n else s[:n] + "..."


# ---------------------------------------------------------------------------- comparing

class Report:
    def __init__(self, allowed):
        self.rows = []
        self.allowed = allowed
        self.suppressed = 0

    def add(self, url, check, expected, got):
        e, g = short(expected), short(got)
        for a in self.allowed:
            if a["check"] != check:
                continue
            if not re.search(a.get("url", ""), url):
                continue
            if re.search(a.get("expected", ""), e) and re.search(a.get("got", ""), g):
                self.suppressed += 1
                return
        self.rows.append((url, check, e, g))


def seq_diff(rep, url, check, a, b, limit=3):
    """Reports where two sequences part ways, by aligning on their longest common runs."""
    import difflib
    sm = difflib.SequenceMatcher(a=a, b=b, autojunk=False)
    n = 0
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op == "equal":
            continue
        rep.add(url, check, a[i1:i2] if i2 > i1 else "(nothing)", b[j1:j2] if j2 > j1 else "(nothing)")
        n += 1
        if n >= limit:
            break


def asset_key(base, src, page_url):
    if not src:
        return src
    u = norm_url(src, page_url)
    if u.startswith("/_vercel/") or re.match(r"^https?:", u):
        return u
    status, _, body = fetch(base + u)
    if status != 200:
        return f"{u} -> {status}"
    # The hashed copy differs from the old file only in the font URLs being absolute.
    body = body.replace(b'url("/fonts/', b'url("fonts/')
    return "sha:" + hashlib.sha256(body).hexdigest()[:16]


def image_size(body):
    try:
        out = subprocess.run(["identify", "-format", "%w %h\n", "-"], input=body, capture_output=True,
                             timeout=30, check=True).stdout.decode().split("\n")[0]
        return tuple(int(x) for x in out.split())
    except Exception:  # noqa: BLE001
        return None


def compare(old_base, new_base, url, rep):
    is404 = url == "404"
    path = "/no-such-page-parity/" if is404 else url
    s1, h1, b1 = fetch(old_base + path)
    s2, h2, b2 = fetch(new_base + path)
    if s1 != s2:
        rep.add(url, "status", s1, s2)
        if s2 != 200 and s2 != 404:
            return None
    p1 = Page(b1.decode("utf-8", "replace"))
    p2 = Page(b2.decode("utf-8", "replace"))
    page_url = "/404.html" if is404 else url

    if p1.html_attrs != p2.html_attrs:
        rep.add(url, "head", p1.html_attrs, p2.html_attrs)
    if p1.title != p2.title:
        rep.add(url, "head", p1.title, p2.title)
    for kind, a, b in (("meta", p1.metas, p2.metas), ("link", p1.links, p2.links)):
        a = sorted(norm_attrs(x, page_url) for x in a)
        b = sorted(norm_attrs(x, page_url) for x in b)
        if a != b:
            for x in [x for x in a if x not in b][:4]:
                rep.add(url, "head", f"{kind} {dict(x)}", "(missing)")
            for x in [x for x in b if x not in a][:4]:
                rep.add(url, "head", "(absent)", f"{kind} {dict(x)}")
    if a_dup := [x for x in p2.metas if p2.metas.count(x) > 1]:
        if [x for x in p1.metas if p1.metas.count(x) > 1] != a_dup:
            rep.add(url, "head", "no duplicate meta", a_dup[:1])

    metas2 = {dict(m).get("property") or dict(m).get("name"): dict(m).get("content") for m in p2.metas}
    canon = [dict(l)["href"] for l in p2.links if dict(l).get("rel") == "canonical"]
    if not is404:
        want = ORIGIN + url
        if canon != [want]:
            rep.add(url, "canonical", want, canon)
        if metas2.get("og:url") != want:
            rep.add(url, "canonical", f"og:url {want}", metas2.get("og:url"))
    elif canon:
        rep.add(url, "canonical", "no canonical on 404", canon)

    ld1 = [deep_sort(json.loads(x)) for x in p1.ld]
    try:
        ld2 = [deep_sort(json.loads(x)) for x in p2.ld]
    except json.JSONDecodeError as e:
        rep.add(url, "jsonld", "parses", str(e))
        ld2 = []
    if ld1 != ld2:
        seq_diff(rep, url, "jsonld", ld1, ld2)
    def crumbs(lds):
        return any(d.get("@type") == "BreadcrumbList"
                   or any(g.get("@type") == "BreadcrumbList" for g in d.get("@graph", []))
                   for d in lds if isinstance(d, dict))
    if crumbs(ld1) and not crumbs(ld2):
        rep.add(url, "jsonld", "a BreadcrumbList", "none")

    for bid in sorted(set(p1.json_blocks) | set(p2.json_blocks)):
        a = p1.json_blocks.get(bid)
        b = p2.json_blocks.get(bid)
        if (a and json.loads(a)) != (b and json.loads(b)):
            rep.add(url, "langs", f"{bid} {short(a or '(none)')}", short(b or "(none)"))

    t1, t2 = [t for _, t in p1.texts], [t for _, t in p2.texts]
    if t1 != t2:
        seq_diff(rep, url, "text", t1, t2)
    e1 = [(t, norm_attrs(a, page_url)) for t, a in p1.elements]
    e2 = [(t, norm_attrs(a, page_url)) for t, a in p2.elements]
    if e1 != e2:
        seq_diff(rep, url, "elements", [f"<{t} {dict(a)}>" for t, a in e1], [f"<{t} {dict(a)}>" for t, a in e2])
    if p2.h1 != 1 and p1.h1 != p2.h1:
        rep.add(url, "h1", p1.h1, p2.h1)

    if p1.inline_scripts != p2.inline_scripts:
        seq_diff(rep, url, "assets", p1.inline_scripts, p2.inline_scripts)
    st1 = [asset_key(old_base, s, page_url) for s in p1.styles]
    st2 = [asset_key(new_base, s, page_url) for s in p2.styles]
    if st1 != st2:
        rep.add(url, "assets", f"styles {st1}", st2)
    sc1 = [(asset_key(old_base, s, page_url), a) for s, a in p1.scripts]
    sc2 = [(asset_key(new_base, s, page_url), a) for s, a in p2.scripts]
    if sc1 != sc2:
        rep.add(url, "assets", f"scripts {sc1}", sc2)

    # og:image: answers, with the declared type and size
    img = metas2.get("og:image")
    if img:
        local = new_base + urlsplit(img)._replace(scheme="", netloc="").geturl()
        st, hd, body = fetch(local)
        if st != 200:
            rep.add(url, "og-image", f"{img} 200", st)
        else:
            if metas2.get("og:image:type") and not hd.get("content-type", "").startswith(metas2["og:image:type"]):
                rep.add(url, "og-image", metas2["og:image:type"], hd.get("content-type"))
            want = (metas2.get("og:image:width"), metas2.get("og:image:height"))
            got = image_size(body)
            if want[0] and (got is None or (str(got[0]), str(got[1])) != want):
                rep.add(url, "og-image", f"{want[0]}x{want[1]}", got)
    return p2


def reciprocal(pages, rep):
    """Each hreflang alternate must list this page among its own alternates."""
    alts = {}
    for url, p in pages.items():
        if p is None:
            continue
        alts[url] = {dict(l)["href"].replace(ORIGIN, "") for l in p.links
                     if dict(l).get("rel") == "alternate" and dict(l).get("hreflang")}
    for url, mine in alts.items():
        for other in mine:
            if other in alts and url not in alts[other]:
                rep.add(url, "hreflang", f"{other} links back", "it does not")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--urls", required=True)
    ap.add_argument("--new-dir")
    ap.add_argument("--page", action="append", help="page id (home, features, ...); repeatable")
    ap.add_argument("--lang", action="append", help="URL prefix (es, zh-tw, en); repeatable")
    ap.add_argument("--jobs", type=int, default=16)
    ap.add_argument("--allowed", default=str(Path(__file__).with_name("allowed-diffs.json")))
    ap.add_argument("--quiet", action="store_true")
    a = ap.parse_args()

    urls = [u.strip() for u in Path(a.urls).read_text().split("\n") if u.strip()]
    rep = Report(json.loads(Path(a.allowed).read_text()) if Path(a.allowed).exists() else [])
    if a.new_dir:
        rels = [str(p.parent.relative_to(a.new_dir)) for p in Path(a.new_dir).rglob("index.html")]
        new = sorted("/" if r == "." else f"/{r}/" for r in rels)
        for u in sorted(set(new) - set(urls)):
            rep.add(u, "status", "(no such page)", "extra page")
        if not (Path(a.new_dir) / "404.html").exists():
            rep.add("404", "status", "404.html", "missing")
    urls = urls + ["404"]

    def wanted(u):
        if a.page:
            rest = u
            for pre in LANG_PREFIXES:
                if u.startswith(f"/{pre}/"):
                    rest = u[len(pre) + 1:]
                    break
            if not any(rest == "/" + PAGE_DIRS[p] or (p == "404" and u == "404")
                       or (p == "changelog" and rest.startswith("/changelog/")) for p in a.page):
                return False
        if a.lang:
            pre = next((p for p in LANG_PREFIXES if u.startswith(f"/{p}/")), "en")
            if pre not in a.lang:
                return False
        return True

    urls = [u for u in urls if wanted(u)]
    with ThreadPoolExecutor(a.jobs) as ex:
        results = dict(zip(urls, ex.map(lambda u: compare(a.old.rstrip("/"), a.new.rstrip("/"), u, rep), urls)))
    reciprocal(results, rep)

    rows = sorted(set(rep.rows))
    for r in rows:
        print("\t".join(str(x) for x in r))
    print(f"parity: {len(urls)} pages, {len(rows)} differences, {rep.suppressed} allowed",
          file=sys.stderr)
    return 1 if rows else 0


if __name__ == "__main__":
    sys.exit(main())
