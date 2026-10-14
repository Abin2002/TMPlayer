#!/usr/bin/env python3
"""One-off converter from the hand-written English pages to Astro pages (docs/ASTRO-MIGRATION.md, AM03).

It reads each page with scripts/build-site-i18n.py's own parser, so it sees the markup exactly as
the old build did, and writes src/pages/.../index.astro:

  <p data-i18n="k">English</p>          ->  <p>{t('k')}</p>
  <p data-i18n="k">Go <a href="/install/">here</a></p>
                                        ->  <p><T k="k"><a slot="a1" href={l('/install/')}>{t('k__a1')}</a></T></p>
  alt="..." data-i18n-alt="k"           ->  alt={t('k')}
  <!-- i18n:region -->                  ->  dropped (the layout and components render them)
  <head>                                ->  props for <Base> plus the page's JSON-LD and preconnects
  inline <svg>                          ->  <Icon name="..."> with the SVG in src/icons/ once

The result builds and renders the same page; tidying it into readable components is Wave 1's job.

Usage: scripts/html-to-astro.py [page ...]      page ids: home, features, ..., legal, privacy, 404
       scripts/html-to-astro.py --all
"""

import argparse
import hashlib
import importlib.util
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
SRC = SITE / "src"
spec = importlib.util.spec_from_file_location("bsi", ROOT / "scripts" / "build-site-i18n.py")
bsi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bsi)

ENGLISH_PAGES = {"legal": "legal/", "privacy": "privacy/", "security": "security/",
                 "signing": "signing/", "support": "support/", "404": None}
URL_ATTRS = ("href", "src", "srcset", "poster", "data-theme-src", "data-dark-src")
LOCALIZED_PATHS = {"/" + p for p in bsi.PAGES.values()}
STANDARD_HEAD = re.compile(
    r'^(charset|viewport|description|robots|author|theme-color|color-scheme|og:\w+(:\w+)*|twitter:\w+(:\w+)*'
    r'|article:published_time)$')


def esc_attr(v):
    return v.replace("&", "&amp;").replace('"', "&quot;").replace("<", "&lt;").replace(">", "&gt;")


def esc_text(raw):
    """Raw source text (entities kept) made safe for an Astro template: braces become literals."""
    out = []
    pos = 0
    for m in re.finditer(r"<!--.*?-->", raw, re.S):
        out.append(re.sub(r"[{}]", lambda c: "{'" + c.group(0) + "'}", raw[pos:m.start()]))
        out.append(m.group(0))
        pos = m.end()
    out.append(re.sub(r"[{}]", lambda c: "{'" + c.group(0) + "'}", raw[pos:]))
    return "".join(out)


def js_str(s):
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"


class Icons:
    def __init__(self):
        self.dir = SRC / "icons"
        self.dir.mkdir(parents=True, exist_ok=True)
        self.by_norm = {}
        self.names = set()
        for f in sorted(self.dir.glob("*.svg")):
            self.by_norm[self.norm(f.read_text())] = f.stem
            self.names.add(f.stem)

    @staticmethod
    def norm(svg):
        return " ".join(svg.split()).replace("> <", "><")

    def get(self, svg, hint):
        n = self.norm(svg)
        if n in self.by_norm:
            return self.by_norm[n]
        base = re.sub(r"[^a-z0-9]+", "-", hint.lower()).strip("-")[:40] or "icon"
        name, i = base, 2
        while name in self.names:
            name, i = f"{base}-{i}", i + 1
        lines = svg.split("\n")
        if len(lines) > 1:
            indent = min((len(x) - len(x.lstrip()) for x in lines[1:] if x.strip()), default=0)
            svg = "\n".join([lines[0]] + [x[indent - 2:] if indent >= 2 else x for x in lines[1:]])
            svg = re.sub(r"\n\s*</svg>$", "\n</svg>", svg)
        (self.dir / f"{name}.svg").write_text(svg + "\n", encoding="utf-8")
        self.by_norm[n] = name
        self.names.add(name)
        return name


class Converter:
    def __init__(self, src, tree, page_dir, localized, icons):
        self.src = src
        self.tree = tree
        self.base = "/" + page_dir if page_dir is not None else "/"
        self.localized = localized
        self.icons = icons
        self.used = set()

    # ------------------------------------------------------------------ attributes

    def url_value(self, attr, value):
        if re.match(r"^(?:[a-z][a-z0-9+.-]*:|//|#|/|\?|$)", value):
            if self.localized and attr == "href" and value.startswith("/") and not value.startswith("//"):
                path, sep, rest = re.match(r"([^#?]*)([#?]?)(.*)", value).groups()
                norm = path if path.endswith("/") else path + "/"
                if norm in LOCALIZED_PATHS:
                    return "{l(" + js_str(value) + ")}"
            return None
        if attr == "srcset":
            parts = [p.strip() for p in value.split(",")]
            return '"' + esc_attr(", ".join(self.base + p if not p.startswith("/") else p for p in parts)) + '"'
        return '"' + esc_attr(self.base + value) + '"'

    def start_tag(self, node, slot=None, drop=()):
        parts = [node.tag]
        for k, v in node.attrs.items():
            if k in drop or k == "data-i18n" or k.startswith("data-i18n-"):
                continue
            key = node.get(f"data-i18n-{k}")
            if key:
                parts.append(f"{k}={{t({js_str(key)})}}")
                self.used.add("t")
                continue
            if v is None:
                parts.append(k)
                continue
            if k in URL_ATTRS:
                conv = self.url_value(k, v)
                if conv is not None:
                    if conv.startswith("{l("):
                        self.used.add("l")
                    parts.append(f"{k}={conv}")
                    continue
            parts.append(f'{k}="{esc_attr(v)}"')
        if slot:
            parts.append(f'slot="{slot}"')
        src_tag = self.src[node.start:node.open_end]
        close = " />" if src_tag.endswith("/>") and node.tag not in bsi.VOID else ">"
        return "<" + " ".join(parts) + close

    # ------------------------------------------------------------------ nodes

    def svg(self, node, slot=None):
        raw = self.src[node.start:node.end]
        if "data-i18n" in raw:
            return self.container(node, slot)
        hint = (node.get("class") or "").split()
        hint = hint[0] if hint else (node.parent.get("aria-label") or node.parent.get("class") or
                                     bsi.all_text(self.src, node.parent).strip()[:30] or "icon")
        name = self.icons.get(raw, hint)
        self.used.add("Icon")
        return f'<Icon name="{name}"' + (f' slot="{slot}"' if slot else "") + " />"

    def emit(self, node, slot=None):
        if node.tag == "svg":
            return self.svg(node, slot)
        if node.get("data-i18n"):
            return self.whole(node, node.get("data-i18n"), slot)
        if node.tag in ("script", "style"):
            tag = self.start_tag(node, slot)
            tag = tag[:-1] + " is:inline>"
            return tag + self.src[node.open_end:node.end]
        return self.container(node, slot)

    def container(self, node, slot=None):
        out = [self.start_tag(node, slot)]
        if node.open_end == node.end:
            return out[0]
        for kind, part in bsi.segments(self.src, node):
            out.append(self.text(part) if kind == "text" else self.emit(part))
        out.append(self.src[node.close_start:node.end])
        return "".join(out)

    def text(self, raw):
        if "<!-- i18n:langcount -->" in raw or "<!-- /i18n:langcount -->" in raw:
            self.used.add("LangCount")
        raw = re.sub(r"<!-- i18n:langcount -->.*?<!-- /i18n:langcount -->", "<LANGCOUNT/>", raw, flags=re.S)
        raw = re.sub(r"<!-- i18n:(\w+) -->.*?<!-- /i18n:\1 -->", "", raw, flags=re.S)
        return esc_text(raw).replace("<LANGCOUNT/>", "<LangCount />")

    def whole(self, node, key, slot=None):
        tag = self.start_tag(node, slot)
        if node.open_end == node.end:
            return tag
        return tag + self.unit(node, key) + self.src[node.close_start:node.end]

    def unit(self, node, key):
        head, tmpl, tail, children = bsi.template(self.src, node)

        def edge(seq):
            out = []
            for kind, value in seq:
                if kind == "text":
                    out.append(" " if value and not value.strip() else esc_text(value))
                else:
                    out.append(self.emit(children[value]))
            return "".join(out)

        names = re.findall(r"\{(\w+)\}", tmpl)
        if not names:
            self.used.add("t")
            body = "{t(" + js_str(key) + ")}"
        else:
            self.used.add("T")
            slots = []
            for name in names:
                child = children.get(name)
                if child is None:
                    continue
                if bsi.is_verbatim(self.src, child):
                    slots.append(self.emit(child, slot=name))
                else:
                    slots.append(self.whole(child, f"{key}__{name}", slot=name))
            body = f'<T k="{key}">' + "".join(slots) + "</T>"
        return edge(head) + body + edge(tail)


def head_info(src, tree):
    """Base props, preconnect lines and JSON-LD blocks from the page's <head>."""
    head = next(n for n in bsi.iter_nodes(tree.root) if n.tag == "head")
    props = {}
    extra = []
    comments = []
    ld = []
    for kind, part in bsi.segments(src, head):
        if kind == "text":
            for m in re.finditer(r"<!--(.*?)-->", part, re.S):
                body = m.group(1)
                if re.match(r"\s*/?i18n:", body) or "Generated by" in body:
                    continue
                comments.append(" ".join(body.split()))
            continue
        n = part
        if n.tag == "title":
            props["title"] = ("t", n.get("data-i18n")) if n.get("data-i18n") else ("s", bsi.plain(src[n.open_end:n.close_start]).strip())
        elif n.tag == "meta":
            name = n.get("name") or n.get("property") or ("charset" if n.get("charset") else "")
            if name == "description":
                key = n.get("data-i18n-content")
                props["description"] = ("t", key) if key else ("s", n.get("content"))
            elif name == "og:type":
                props["ogType"] = n.get("content")
            elif name == "og:image:alt":
                key = n.get("data-i18n-content")
                props["imageAlt"] = ("t", key) if key else ("s", n.get("content"))
            elif name == "og:image:secure_url":
                props["secureImage"] = True
            elif name == "article:published_time":
                props["publishedTime"] = n.get("content")
            elif name == "robots" and n.get("content") == "noindex":
                props["notFound"] = True
            elif not STANDARD_HEAD.match(name):
                extra.append(src[n.start:n.end])
        elif n.tag == "link":
            rel = n.get("rel")
            if rel == "alternate" and n.get("type") == "application/rss+xml":
                props["feed"] = True
            elif rel in ("canonical", "icon", "apple-touch-icon", "manifest", "stylesheet") or (
                    rel == "preload" and "/fonts/" in n.get("href", "")):
                continue
            else:
                extra.append(src[n.start:n.end])
        elif n.tag == "script":
            if n.get("type") == "application/ld+json":
                ld.append(json.loads(src[n.open_end:n.close_start]))
    return props, extra, comments, ld


def prop_src(v):
    if v is True:
        return None
    if isinstance(v, tuple):
        return "{t(" + js_str(v[1]) + ")}" if v[0] == "t" else '"' + esc_attr(v[1]) + '"'
    return '"' + esc_attr(v) + '"'


def body_parts(src, tree):
    body = next(n for n in bsi.iter_nodes(tree.root) if n.tag == "body")
    return body, [p for k, p in bsi.segments(src, body) if k == "node"]


def convert(page_id, icons):
    localized = page_id in bsi.PAGES
    page_dir = bsi.PAGES[page_id] if localized else ENGLISH_PAGES[page_id]
    if page_id == "404":
        path = SITE / "404.html"
        src = path.read_text(encoding="utf-8")
    elif localized:
        path, _, src = bsi.load_page(page_id)
    else:
        path = SITE / page_dir / "index.html"
        src = bsi.strip_regions(path.read_text(encoding="utf-8"))
    tree = bsi.Tree(src, str(path))
    conv = Converter(src, tree, page_dir if page_dir is not None else "", localized, icons)
    props, extra, comments, ld = head_info(src, tree)
    body, nodes = body_parts(src, tree)

    used_components = set()
    lines = []
    for kind, part in bsi.segments(src, body):
        if kind == "text":
            t = conv.text(part)
            lines.append(t)
            continue
        n = part
        cls = n.classes()
        if n.tag == "a" and "skip" in cls:
            lines.append("{/* skip link: Base */}")
        elif n.tag == "aside" and "kao" in cls:
            continue
        elif n.tag == "div" and "bg-art" in cls:
            used_components.add("BgArt")
            lines.append("<BgArt />")
        elif n.tag == "header" and "site-header" in cls and localized:
            used_components.add("Header")
            extra_attr = " brandSvg" if page_id == "home" else ""
            lines.append(f'<Header page="{page_id}"{extra_attr} />')
        elif n.tag == "footer" and localized:
            used_components.add("Footer")
            lines.append("<Footer home />" if page_id == "home" else "<Footer />")
        elif n.tag == "button" and "to-top" in cls:
            used_components.add("ToTop")
            lines.append("<ToTop />")
        elif n.tag == "script":
            if n.get("src") in ("/app.js", "/_vercel/insights/script.js"):
                lines.append("")
                continue
            lines.append(conv.emit(n))
        else:
            lines.append(conv.emit(n))
    body_src = "".join(lines)
    # Base writes the scripts and their comments after the page; drop the copies left here.
    body_src = re.sub(r"<!--(?:(?!-->).)*?(Vercel Web Analytics|same script the rest of the site loads)(?:(?!-->).)*?-->", "",
                      body_src, flags=re.S)
    body_src = re.sub(r"\{/\* skip link: Base \*/\}\n?", "", body_src)
    body_src = re.sub(r"\n{3,}", "\n\n", body_src).strip("\n") + "\n"

    used = conv.used | used_components
    depth = 0
    if localized:
        out_path = SRC / "pages" / "[...lang]" / bsi.PAGES[page_id] / "index.astro"
        depth = 2 + bsi.PAGES[page_id].count("/")
    elif page_id == "404":
        out_path = SRC / "pages" / "404.astro"
        depth = 1
    else:
        out_path = SRC / "pages" / page_dir / "index.astro"
        depth = 1 + page_dir.count("/")
    rel = "../" * depth

    fm = []
    if comments:
        fm += ["// " + c for c in comments]
        fm.append("")
    fm.append(f"import Base from '{rel}layouts/Base.astro';")
    for comp in ("BgArt", "Header", "Footer", "ToTop", "T", "Icon", "JsonLd", "LangCount"):
        if comp in used or (comp == "JsonLd" and ld):
            fm.append(f"import {comp} from '{rel}components/{comp}.astro';")
    if localized:
        fm.append(f"import {{ langPaths }} from '{rel}i18n/paths';")
        fm.append(f"import {{ text }} from '{rel}i18n/t';")
        fm.append(f"import {{ l as link }} from '{rel}i18n/links';")
        fm.append("")
        fm.append("export const getStaticPaths = langPaths;")
        fm.append("const { lang } = Astro.props;")
        fm.append("Astro.locals.lang = lang;")
        fm.append("const t = (k: string) => text(lang, k);")
        fm.append("const l = (href: string) => link(lang, href);")
    else:
        fm.append(f"import {{ ENGLISH }} from '{rel}i18n/langs';")
        if "t" in used or any(isinstance(v, tuple) and v[0] == "t" for v in props.values()):
            fm.append(f"import {{ text }} from '{rel}i18n/t';")
            fm.append("")
            fm.append("Astro.locals.lang = ENGLISH;")
            fm.append("const t = (k: string) => text(ENGLISH, k);")
        else:
            fm.append("")
            fm.append("Astro.locals.lang = ENGLISH;")
    for i, block in enumerate(ld):
        fm.append("")
        fm.append(f"const ld{i} = " + json.dumps(block, ensure_ascii=False, indent=2) + ";")

    attrs = []
    if localized:
        attrs.append(f'page="{page_id}"')
    elif page_id != "404":
        attrs.append(f'path="/{page_dir}"')
    for k in ("title", "description", "ogType", "imageAlt", "secureImage", "feed", "publishedTime", "notFound"):
        if k in props:
            v = prop_src(props[k])
            if k == "ogType" and props[k] == "website":
                continue
            attrs.append(k if v is None else f"{k}={v}")
    if page_id == "404":
        attrs.append("notice={false}")
    open_tag = "<Base " + " ".join(attrs) + ">"

    head_bits = []
    if extra:
        head_bits.append('  <Fragment slot="preconnect">\n' + "\n".join("    " + e for e in extra) + "\n  </Fragment>")
    if ld:
        head_bits.append('  <Fragment slot="head">\n' + "\n".join(f"    <JsonLd data={{ld{i}}} />" for i in range(len(ld)))
                         + "\n  </Fragment>")
    page = "---\n" + "\n".join(fm) + "\n---\n" + open_tag + "\n" + "\n".join(head_bits) + ("\n" if head_bits else "") \
        + body_src + "</Base>\n"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(page, encoding="utf-8")
    left = re.findall(r"data-i18n", page)
    return out_path, len(left)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("pages", nargs="*")
    ap.add_argument("--all", action="store_true")
    a = ap.parse_args()
    pages = list(bsi.PAGES) + list(ENGLISH_PAGES) if a.all else a.pages
    icons = Icons()
    bad = 0
    for p in pages:
        out, left = convert(p, icons)
        print(f"{out.relative_to(ROOT)}" + (f"  ({left} data-i18n left)" if left else ""))
        bad += left
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
