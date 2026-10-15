#!/usr/bin/env python3
"""Serves a built site the way Vercel serves tmplayer.org, so two builds can be compared locally.

Reads vercel.json (by default the one in the served directory, else site/vercel.json) and applies
what the site relies on:

  cleanUrls + trailingSlash   any path without a dot -> 308 with a slash (before redirects), a slash after a
                              dotted last segment -> 308 without it (/changelog/1.26.0/ -> /changelog/1.26.0), /x.html -> 308 /x/, /index.html -> 308 /
  redirects                   /apk -> 307 (permanent: false) or 308 to the destination
  headers                     every rule whose source matches, in file order, later ones winning
  404                         404.html with status 404 for anything missing

Vercel's own defaults are imitated too: `Cache-Control: public, max-age=0, must-revalidate` where
no rule sets one, `Access-Control-Allow-Origin: *` on files, and `charset=utf-8` on text types.

Usage: scripts/serve-vercel.py <dir> [--port 4001] [--config path/to/vercel.json]
"""

import argparse
import json
import mimetypes
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent

mimetypes.add_type("application/manifest+json", ".webmanifest")
mimetypes.add_type("font/woff2", ".woff2")
mimetypes.add_type("image/webp", ".webp")
mimetypes.add_type("image/svg+xml", ".svg")
mimetypes.add_type("text/plain", ".ps1")
mimetypes.add_type("application/xml", ".xml")
mimetypes.add_type("application/javascript", ".js")
# Vercel adds a charset to these, and not to application/xml.
TEXTY = re.compile(r"^(text/|application/(json|javascript|manifest\+json)|image/svg\+xml)")


def content_type(path):
    ctype = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    if TEXTY.match(ctype):
        ctype += "; charset=utf-8"
    return ctype


def compile_rules(cfg):
    headers = [(re.compile("^" + r["source"] + "$"), r["headers"]) for r in cfg.get("headers", [])]
    redirects = [(re.compile("^" + r["source"] + "$"), r) for r in cfg.get("redirects", [])]
    return headers, redirects


def make_handler(root, cfg):
    headers_rules, redirect_rules = compile_rules(cfg)
    clean = cfg.get("cleanUrls", False)
    slash = cfg.get("trailingSlash", False)

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args):
            pass

        def rule_headers(self, path):
            out = {}
            # Vercel's matcher does not reach paths with a dot segment such as /.well-known/.
            if "/." in path:
                return out
            for rx, hs in headers_rules:
                if rx.match(path):
                    for h in hs:
                        out[h["key"].lower()] = h["value"]
            return out

        def send(self, status, body=b"", ctype=None, extra=None, path="/"):
            self.send_response(status)
            hs = {"cache-control": "public, max-age=0, must-revalidate"}
            if ctype:
                hs["content-type"] = ctype
            if status == 200 or status == 404:
                hs["access-control-allow-origin"] = "*"
                hs.update(self.rule_headers(path))
            hs.update(extra or {})
            ct = hs.get("content-type", "")
            if status != 308 and status != 307 and TEXTY.match(ct) and "charset" not in ct:
                hs["content-type"] = ct + "; charset=utf-8"
            hs["content-length"] = str(len(body))
            for k, v in hs.items():
                self.send_header(k, v)
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)

        def redirect(self, status, location):
            self.send(status, b"Redirecting...\n", "text/plain", {"location": location})

        def file(self, rel):
            p = (root / rel).resolve()
            if root not in p.parents and p != root:
                return None
            return p if p.is_file() else None

        def do_HEAD(self):
            self.do_GET()

        def do_GET(self):
            parts = urlsplit(self.path)
            path = unquote(parts.path)
            query = ("?" + parts.query) if parts.query else ""
            last = path.rsplit("/", 1)[-1]
            if slash and not path.endswith("/") and "." not in last:
                return self.redirect(308, path + "/" + query)
            seg = path.rstrip("/").rsplit("/", 1)[-1]
            if slash and path.endswith("/") and path != "/" and "." in seg and not seg.startswith("."):
                return self.redirect(308, path.rstrip("/") + query)  # /changelog/1.26.0/ -> /changelog/1.26.0
            for rx, r in redirect_rules:
                if rx.match(path):
                    return self.redirect(307 if not r.get("permanent", True) else 308, r["destination"])
            if clean and path.endswith(".html"):
                target = path[: -len(".html")]
                if target.endswith("/index"):
                    target = target[: -len("index")]
                elif slash:
                    target += "/"
                return self.redirect(308, target + query)
            f = self.file(path.lstrip("/"))
            if f is None and not path.endswith("/"):
                f = self.file(path.lstrip("/") + "/index.html")  # a dotted directory, served without its slash
            if f is not None and not path.endswith("/"):
                return self.send(200, f.read_bytes(), content_type(f), path=path)
            if path.endswith("/"):
                f = self.file(path.lstrip("/") + "index.html")
                if f is None and clean and path != "/":
                    f = self.file(path.strip("/") + ".html")
                if f is not None:
                    return self.send(200, f.read_bytes(), content_type(f), path=path)
            nf = self.file("404.html")
            body = nf.read_bytes() if nf else b"Not found\n"
            return self.send(404, body, "text/html; charset=utf-8", path=path)

    return Handler


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("dir")
    ap.add_argument("--port", type=int, default=4001)
    ap.add_argument("--config")
    a = ap.parse_args()
    root = Path(a.dir).resolve()
    cfg_path = Path(a.config) if a.config else (root / "vercel.json" if (root / "vercel.json").exists()
                                                else ROOT / "site" / "vercel.json")
    cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), make_handler(root, cfg))
    print(f"serving {root} on http://127.0.0.1:{a.port}/ with {cfg_path}", file=sys.stderr, flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
