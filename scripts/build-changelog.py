#!/usr/bin/env python3
"""Builds the site's changelog page and its RSS feed from the GitHub releases.

    scripts/build-changelog.py
    scripts/build-changelog.py --input releases.json

writes site/changelog/index.html, site/changelog/feed.xml and one page per release at
site/changelog/<version>/index.html, and lists those pages in site/sitemap.xml. The release job runs it in the same
step that publishes the update feed, so the page names a release in the same commit as
site/latest.json does; it can also be run by hand after a release that went out some other way.

Where the releases come from: --input, a file holding the JSON array GitHub's list releases call
returns; otherwise `gh api` when gh is installed (it is on a GitHub runner, and locally it is
already signed in); otherwise the REST API directly, with GITHUB_TOKEN or GH_TOKEN if one is set.

Where each release's words come from. A release body on GitHub is mostly machinery: a table of
files, install links and GitHub's generated "Full Changelog" line. The sentences a person wrote
for the release are the annotated tag's message, the same text the update popup shows, so that is
what the page prints, without its "release: X.Y.Z" subject line. A plain tag has no message of its
own (git would answer with the commit's, which is the wrong text), and for those the page falls
back to whatever prose the release body opens with, before its first heading or table. A release
with neither says so and links to GitHub.

The tag's message is read from git when the tag is here and annotated, and from the API when it
is not: the release job's checkout is shallow and holds only the tag it was started by, and the
two sources give the same text, so the page does not depend on which one answered.

The page's header, footer, background art and closing scripts are lifted out of
site/download/index.html at build time rather than written down here, so a change to the site's
navigation reaches this page the next time the script runs, without anyone remembering it.

Same input, same bytes out: nothing here reads the clock, so the job only commits when a release
actually changed something.
"""

import argparse
import email.utils
import html
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO = "dracu-lah/TMPlayer"
SITE = "https://tmplayer.org"
PAGE_URL = f"{SITE}/changelog/"
FEED_URL = f"{SITE}/changelog/feed.xml"
ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "site" / "download" / "index.html"
OUT_DIR = ROOT / "site" / "changelog"
FEED_ITEMS = 20
SITEMAP = ROOT / "site" / "sitemap.xml"
# A release page's folder name, so stale ones can be told apart from anything else in OUT_DIR.
VERSION_DIR = re.compile(r"^\d+(\.\d+)+$")


def release_url(version: str) -> str:
    """Each release has a page of its own, so a search for "TMPlayer 1.25.0" lands on its notes."""
    return f"{PAGE_URL}{version}/"

TITLE = "TMPlayer changelog: every release"
DESCRIPTION = ("What changed in every TMPlayer release, newest first: Android TV, phones, "
               "Windows and Linux, all from one release.")

# Lines git adds to a message that are not part of what the release says.
TRAILER = re.compile(r"^(Co-Authored-By|Claude-Session|Signed-off-by|Change-Id):", re.I)


# ---------- Reading the releases ----------

def api_get(path: str):
    """One REST call, through gh when it is there and urllib when it is not."""
    if shutil.which("gh"):
        out = subprocess.run(["gh", "api", path], check=True, capture_output=True, text=True)
        return json.loads(out.stdout)
    request = urllib.request.Request(f"https://api.github.com/{path}")
    request.add_header("Accept", "application/vnd.github+json")
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def load_releases(input_path):
    if input_path:
        with open(input_path, encoding="utf-8") as f:
            return json.load(f)
    if shutil.which("gh"):
        # --paginate with --slurp gives one array of pages, whatever the release count.
        out = subprocess.run(
            ["gh", "api", f"repos/{REPO}/releases?per_page=100", "--paginate", "--slurp"],
            check=True, capture_output=True, text=True)
        return [r for page in json.loads(out.stdout) for r in page]
    releases, page = [], 1
    while True:
        batch = api_get(f"repos/{REPO}/releases?per_page=100&page={page}")
        releases += batch
        if len(batch) < 100:
            return releases
        page += 1


def tag_message(tag: str) -> str:
    """The annotated tag's message without its subject, or "" for a plain or missing tag."""
    ref = f"refs/tags/{tag}"
    kind = subprocess.run(["git", "-C", str(ROOT), "cat-file", "-t", ref],
                          capture_output=True, text=True).stdout.strip()
    if kind == "tag":
        return subprocess.run(
            ["git", "-C", str(ROOT), "tag", "-l", "--format=%(contents:body)", tag],
            check=True, capture_output=True, text=True).stdout
    if kind == "commit":
        return ""  # A plain tag, here: its "message" would be the commit's.
    try:
        obj = api_get(f"repos/{REPO}/git/ref/tags/{tag}")["object"]
        if obj.get("type") != "tag":
            return ""
        message = api_get(f"repos/{REPO}/git/tags/{obj['sha']}").get("message", "")
    except (subprocess.CalledProcessError, urllib.error.URLError, KeyError, ValueError):
        return ""
    # The same split git makes: the subject is the first paragraph, the body is the rest.
    parts = re.split(r"\n\s*\n", message.strip("\n"), maxsplit=1)
    return parts[1] if len(parts) > 1 else ""


def clean_notes(text: str) -> str:
    text = text.replace("\r\n", "\n")
    text = re.sub(r"-----BEGIN PGP SIGNATURE-----.*", "", text, flags=re.S)
    lines = [line.rstrip() for line in text.split("\n")]
    while lines and (not lines[-1] or TRAILER.match(lines[-1])):
        lines.pop()
    return "\n".join(lines).strip("\n")


def body_prose(body: str) -> str:
    """The prose a release body opens with, up to its first heading, table or generated line."""
    kept = []
    for line in (body or "").replace("\r\n", "\n").split("\n"):
        stripped = line.strip()
        if (stripped.startswith(("#", "|", "**Full Changelog**", "<!--"))
                or stripped.startswith(("Installed copies update themselves",
                                      "Installed copies offer each update"))):
            break
        kept.append(line)
    return "\n".join(kept).strip()


# ---------- A little markdown, safely ----------

INLINE_CODE = re.compile(r"`([^`]+)`")
LINK = re.compile(r"\[([^\]]+)\]\((https?://[^\s)]+)\)")
BOLD = re.compile(r"\*\*(.+?)\*\*")


def inline(text: str) -> str:
    """Escapes everything first, then turns back on code, links and bold, and nothing else."""
    codes = []

    def keep_code(m):
        codes.append(f"<code>{html.escape(m.group(1), quote=False)}</code>")
        return f"\x00{len(codes) - 1}\x00"

    text = INLINE_CODE.sub(keep_code, text)
    links = []

    def keep_link(m):
        links.append((m.group(1), m.group(2)))
        return f"\x01{len(links) - 1}\x01"

    text = LINK.sub(keep_link, text)
    text = html.escape(text, quote=False)
    text = BOLD.sub(r"<strong>\1</strong>", text)

    def put_link(m):
        label, url = links[int(m.group(1))]
        label = BOLD.sub(r"<strong>\1</strong>", html.escape(label, quote=False))
        return f'<a href="{html.escape(url)}" rel="noopener">{label}</a>'

    text = re.sub(r"\x01(\d+)\x01", put_link, text)
    return re.sub(r"\x00(\d+)\x00", lambda m: codes[int(m.group(1))], text)


def markdown(text: str, indent: str) -> str:
    """Paragraphs, bullet lists and small headings. Tables are skipped: they are file lists."""
    out = []
    for block in re.split(r"\n\s*\n", text.strip()):
        lines = [line for line in block.split("\n") if line.strip()]
        if not lines or all(line.lstrip().startswith("|") for line in lines):
            continue
        if all(re.match(r"\s*[-*] ", line) or line.startswith("  ") for line in lines) \
                and re.match(r"\s*[-*] ", lines[0]):
            items = []
            for line in lines:
                m = re.match(r"\s*[-*] (.*)", line)
                if m:
                    items.append(m.group(1).strip())
                else:
                    items[-1] += " " + line.strip()
            out.append(f"{indent}<ul>")
            out += [f"{indent}  <li>{inline(item)}</li>" for item in items]
            out.append(f"{indent}</ul>")
        elif re.match(r"#{1,6} ", lines[0]) and len(lines) == 1:
            out.append(f"{indent}<h4>{inline(lines[0].lstrip('#').strip())}</h4>")
        else:
            out.append(f"{indent}<p>{inline(' '.join(line.strip() for line in lines))}</p>")
    return "\n".join(out)


def plain(text: str) -> str:
    """The notes as one line of plain text, for a feed reader's summary."""
    text = INLINE_CODE.sub(r"\1", text)
    text = LINK.sub(r"\1", text)
    text = BOLD.sub(r"\1", text)
    text = re.sub(r"^\s*[-*] ", "", text, flags=re.M)
    return " ".join(text.split())


# ---------- The page ----------

def lift(template: str, pattern: str, what: str) -> str:
    m = re.search(pattern, template, flags=re.S)
    if not m:
        sys.exit(f"Could not find the {what} in {TEMPLATE.relative_to(ROOT)}")
    return m.group(0)


def shared_parts(template: str) -> dict:
    header = lift(template, r'<header class="site-header">.*?</header>', "site header")
    # This page is not in the menu as the current page; the download page's marker goes.
    header = header.replace(' aria-current="page"', "")
    # The changelog is English only, so the language picker, which points at the download page
    # in each language, goes too. The footer's language list links to home pages and can stay.
    header = re.sub(r"<!-- i18n:picker -->.*?<!-- /i18n:picker -->\n?[ \t]*", "", header, flags=re.S)
    return {
        "bg": lift(template, r'<!-- Decorative background art.*?<div class="bg-art".*?</div>',
                   "background art"),
        "header": header,
        "notice": lift(template, r"<!-- i18n:notice -->.*?<!-- /i18n:notice -->", "Keep Android Open notice"),
        "to_top": lift(template, r'<button class="to-top".*?</button>', "back to top button"),
        "footer": lift(template, r'<footer class="site-footer">.*?</footer>', "site footer"),
        "tail": lift(template, r'<script src="/app\.js"></script>.*?(?=</body>)', "closing scripts"),
    }


def human_date(d: datetime) -> str:
    return f"{d.day} {d.strftime('%B %Y')}"


def entry(r: dict) -> str:
    v, d = r["version"], r["date"]
    notes = markdown(r["notes"], " " * 12) if r["notes"] else (
        ' ' * 12 + '<p class="muted">No notes were written for this release. '
        f'<a href="{r["url"]}" rel="noopener">The release on GitHub</a> lists its files.</p>')
    return f"""        <article class="changelog-entry" id="v{v}" aria-labelledby="v{v}-h">
          <header class="changelog-entry-head">
            <h2 id="v{v}-h"><a href="/changelog/{v}/">TMPlayer {v}</a></h2>
            <p class="changelog-meta"><time datetime="{d.strftime('%Y-%m-%d')}">{human_date(d)}</time><span class="sep" aria-hidden="true">&middot;</span><a href="{r["url"]}" rel="noopener">On GitHub</a></p>
          </header>
          <div class="changelog-notes">
{notes}
          </div>
        </article>"""


def head(title: str, desc: str, url: str, ld: list, og_type: str = "website", extra: str = "") -> str:
    """Everything above <body>: the same tags, icons, fonts and theme script as the other pages."""
    t, d = html.escape(title), html.escape(desc)
    blocks = "\n".join(f'<script type="application/ld+json">\n{json.dumps(x, indent=2, ensure_ascii=False)}\n</script>'
                       for x in ld)
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{t}</title>
<meta name="description" content="{d}">
<link rel="canonical" href="{url}">
<meta name="robots" content="index, follow, max-image-preview:large, max-snippet:-1">
<meta name="author" content="dracu-lah">
<meta name="theme-color" content="#FFFFFF" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0B0C0E" media="(prefers-color-scheme: dark)">
<meta name="color-scheme" content="light dark">

<meta property="og:type" content="{og_type}">
<meta property="og:site_name" content="TMPlayer">
<meta property="og:title" content="{t}">
<meta property="og:description" content="{d}">
<meta property="og:url" content="{url}">
<meta property="og:image" content="{SITE}/og.png?v=3">
<meta property="og:image:type" content="image/png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:locale" content="en_GB">
{extra}
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{t}">
<meta name="twitter:description" content="{d}">
<meta name="twitter:image" content="{SITE}/og.png?v=3">

<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="icon" href="/icon-512.png" type="image/png" sizes="512x512">
<link rel="apple-touch-icon" href="/icon-512.png">
<link rel="manifest" href="/site.webmanifest">
<link rel="alternate" type="application/rss+xml" title="TMPlayer releases" href="/changelog/feed.xml">

<link rel="preload" href="/fonts/poppins-400.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/fonts/poppins-600.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="/styles.css">

<!-- The stored theme, applied before the first paint. With no stored choice nothing is set and
     the stylesheet follows prefers-color-scheme, which is the system theme. -->
<script>
(function () {{
  /* Scripting is on, so the header can collapse into a menu. Without this class the
     stylesheet leaves the links where they are, which is the version that still works. */
  document.documentElement.className += ' js';
  try {{
    var t = localStorage.getItem('tm-theme');
    if (t === 'light' || t === 'dark') {{ document.documentElement.setAttribute('data-theme', t); }}
    // The Keep Android Open notice, once closed, stays closed without a flash on the next page.
    if (localStorage.getItem('tm-kao-closed')) {{ document.documentElement.className += ' kao-off'; }}
  }} catch (e) {{}}
}})();
</script>

{blocks}
</head>
<body>

<!--
  Written by scripts/build-changelog.py from the GitHub releases; the release job regenerates it.
  Edit the script, not this file: a hand edit here is gone after the next release.
-->

<a class="skip" href="#main">Skip to content</a>
"""


def crumbs(*trail) -> dict:
    return {
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        "itemListElement": [{"@type": "ListItem", "position": i + 1, "name": name, "item": url}
                            for i, (name, url) in enumerate(trail)],
    }


def shell(top: str, parts: dict, body: str) -> str:
    return f"""{top}{parts["notice"]}

{parts["bg"]}

{parts["header"]}

<main id="main">
{body}
</main>

{parts["to_top"]}

{parts["footer"]}

{parts["tail"]}</body>
</html>
"""


WAVE = ('<svg class="wave" viewBox="0 0 1440 60" preserveAspectRatio="none" aria-hidden="true" '
        'focusable="false"><path fill="currentColor" d="M0 30c180-40 360-40 540 0s360 40 540 0 '
        '300-30 360-20v50H0Z"/></svg>')

RSS_ICON = ('<svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true" focusable="false">'
            '<path fill="currentColor" d="M5 3a1 1 0 0 0 0 2 14 14 0 0 1 14 14 1 1 0 1 0 2 0A16 16 0 0 0 '
            '5 3Zm0 6a1 1 0 0 0 0 2 8 8 0 0 1 8 8 1 1 0 1 0 2 0A10 10 0 0 0 5 9Zm1.5 7a2.5 2.5 0 1 0 0 '
            '5 2.5 2.5 0 0 0 0-5Z"/></svg>')


# The coffee and sponsor offer, in the download page's words. A release page is read by people who
# just got the update, which is the moment the offer makes the most sense.
SPONSOR_ICON = ('<svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true" focusable="false">'
                '<path fill="currentColor" d="M12 20.6 10.7 19.4C6 15.2 3 12.4 3 9a4.8 4.8 0 0 1 4.9-4.9c1.6 0 '
                '3.1.7 4.1 1.9a5.5 5.5 0 0 1 4.1-1.9A4.8 4.8 0 0 1 21 9c0 3.4-3 6.2-7.7 10.4L12 20.6Z"/></svg>')
COFFEE_ICON = ('<svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true" focusable="false">'
               '<path fill="currentColor" d="M4 8h13v2h1.5a3.5 3.5 0 0 1 0 7H16.6A6 6 0 0 1 11 21H9a5 5 0 0 '
               '1-5-5V8Zm13 4v3h1.5a1.5 1.5 0 0 0 0-3H17ZM7 2.5a1 1 0 0 1 1 1V6H6V3.5a1 1 0 0 1 1-1Zm3.5-1a1 '
               '1 0 0 1 1 1V6h-2V2.5a1 1 0 0 1 1-1Zm3.5 1a1 1 0 0 1 1 1V6h-2V3.5a1 1 0 0 1 1-1Z"/></svg>')


def give(lead: str) -> str:
    return f"""
      <div class="support support-slim">
        <div class="support-more support-more-solo" id="sponsor">
          <h2 class="release-notes-h">Support TMPlayer</h2>
          <p>{lead} There are no ads of our own and no paid tier, so a coffee or a monthly sponsorship is what pays for the time each release takes. It is voluntary and unlocks nothing.</p>
          <div class="support-actions support-give">
            <a class="btn btn-filled" href="https://github.com/sponsors/dracu-lah" rel="noopener">
              {SPONSOR_ICON}
              Sponsor on GitHub
            </a>
            <a class="btn btn-outline" href="https://buymeacoffee.com/nevil.dev" rel="noopener">
              {COFFEE_ICON}
              Buy me a coffee
            </a>
          </div>
        </div>
      </div>
"""


def summary(r: dict, limit: int = 155) -> str:
    """The notes cut at a word to a search snippet's length, for the description tag."""
    text = plain(r["notes"])
    if not text:
        return f"TMPlayer {r['version']}, released {human_date(r['date'])}: the files and notes on GitHub."
    if len(text) <= limit:
        return text
    return text[:limit].rsplit(" ", 1)[0].rstrip(",;:.") + "..."


def page(releases: list, parts: dict) -> str:
    entries = "\n\n".join(entry(r) for r in releases) or (
        '        <p class="muted">No releases yet.</p>')
    blog = {
        "@context": "https://schema.org",
        "@type": "Blog",
        "name": "TMPlayer releases",
        "url": PAGE_URL,
        "description": DESCRIPTION,
        "inLanguage": "en",
        "blogPost": [{"@type": "BlogPosting", "headline": f"TMPlayer {r['version']}",
                      "url": release_url(r["version"]),
                      "datePublished": r["date"].strftime("%Y-%m-%d")} for r in releases],
    }
    top = head(TITLE, DESCRIPTION, PAGE_URL,
               [crumbs(("Home", f"{SITE}/"), ("Changelog", PAGE_URL)), blog])
    body = f"""
  <section class="page-head">
    <div class="wrap page-head-wrap">
      <p class="crumbs"><a href="/">Home</a><span aria-hidden="true">/</span>Changelog</p>
      <h1>Every <em>release</em></h1>
      <p class="page-lede">What changed in each TMPlayer release, newest first. Installed copies offer each update, so this is where to find out what it brings.</p>
      <div class="page-actions">
        <a class="btn btn-filled" href="/download/">Download the latest</a>
        <a class="btn btn-outline" href="/changelog/feed.xml" type="application/rss+xml">
          {RSS_ICON}
          Follow by RSS
        </a>
      </div>
    </div>
  </section>

{WAVE}

  <section class="band band-tonal" aria-label="Releases">
    <div class="wrap">
      <div class="changelog">
{entries}
      </div>
{give("Every release on this page was made in spare time.")}
      <p class="muted small section-note changelog-foot">Releases from before {releases[-1]["version"] if releases else "the first"} are on <a href="https://github.com/{REPO}/tags" rel="noopener">GitHub as tags</a>. Every release links the source tree it was built from.</p>
    </div>
  </section>
"""
    return shell(top, parts, body)


def release_page(r: dict, newer, older, latest: bool, parts: dict) -> str:
    v, d = r["version"], r["date"]
    url, iso = release_url(v), d.strftime("%Y-%m-%dT%H:%M:%SZ")
    title = f"TMPlayer {v} release notes: what's new"
    desc = summary(r)
    post = {
        "@context": "https://schema.org",
        "@type": "BlogPosting",
        "headline": f"TMPlayer {v}",
        "description": desc,
        "url": url,
        "mainEntityOfPage": url,
        "datePublished": iso,
        "dateModified": iso,
        "inLanguage": "en",
        "image": f"{SITE}/og.png?v=3",
        "author": {"@type": "Person", "name": "dracu-lah", "url": "https://github.com/dracu-lah"},
        "publisher": {"@type": "Organization", "name": "TMPlayer", "url": f"{SITE}/",
                      "logo": {"@type": "ImageObject", "url": f"{SITE}/icon-512.png"}},
        "isPartOf": {"@type": "Blog", "name": "TMPlayer releases", "url": PAGE_URL},
        "about": {"@type": "SoftwareApplication", "name": "TMPlayer", "softwareVersion": v,
                  "applicationCategory": "MultimediaApplication",
                  "operatingSystem": "Android, Android TV, Windows, Linux", "url": f"{SITE}/"},
    }
    top = head(title, desc, url,
               [crumbs(("Home", f"{SITE}/"), ("Changelog", PAGE_URL), (f"TMPlayer {v}", url)), post],
               og_type="article",
               extra=f'<meta property="article:published_time" content="{iso}">\n')
    notes = markdown(r["notes"], " " * 8) if r["notes"] else (
        ' ' * 8 + '<p class="muted">No notes were written for this release. '
        f'<a href="{r["url"]}" rel="noopener">The release on GitHub</a> lists its files.</p>')
    if latest:
        get = ('This is the newest release. Installed copies offer it on their own; a new install starts '
               'from the download page.')
        cta = '<a class="btn btn-filled" href="/download/">Download TMPlayer</a>'
    else:
        get = (f'A newer release is out. Installed copies move straight to it, and its files are the ones '
               f'the download page offers. This release\'s own files stay on GitHub.')
        cta = '<a class="btn btn-filled" href="/download/">Download the latest</a>'
    nav = []
    if older:
        nav.append(f'<a class="release-nav-older" href="/changelog/{older["version"]}/" rel="prev">'
                   f'<span class="muted small">Older</span>TMPlayer {older["version"]}</a>')
    if newer:
        nav.append(f'<a class="release-nav-newer" href="/changelog/{newer["version"]}/" rel="next">'
                   f'<span class="muted small">Newer</span>TMPlayer {newer["version"]}</a>')
    nav_html = ("\n      <nav class=\"release-nav\" aria-label=\"Other releases\">\n        "
                + "\n        ".join(nav) + "\n      </nav>\n") if nav else ""
    body = f"""
  <section class="page-head">
    <div class="wrap page-head-wrap">
      <p class="crumbs"><a href="/">Home</a><span aria-hidden="true">/</span><a href="/changelog/">Changelog</a><span aria-hidden="true">/</span>{v}</p>
      <h1>TMPlayer <em>{v}</em></h1>
      <p class="page-lede">Released <time datetime="{d.strftime('%Y-%m-%d')}">{human_date(d)}</time>, for Android TV, Android phones, Windows and Linux.</p>
    </div>
  </section>

{WAVE}

  <section class="band band-tonal" aria-label="Release notes">
    <div class="wrap">
      <article class="changelog-entry release-article">
        <h2 class="release-notes-h">What changed</h2>
        <div class="changelog-notes">
{notes}
        </div>
      </article>

      <div class="changelog-entry release-get">
        <h2 class="release-notes-h">Get it</h2>
        <p class="changelog-notes">{get}</p>
        <div class="page-actions">
          {cta}
          <a class="btn btn-outline" href="{r["url"]}" rel="noopener">The files on GitHub</a>
          <a class="btn btn-outline" href="/changelog/feed.xml" type="application/rss+xml">{RSS_ICON} Follow by RSS</a>
        </div>
      </div>
{give(f"Glad {v} reached you.")}{nav_html}
      <p class="muted small section-note changelog-foot"><a href="/changelog/">Every release</a>, newest first.</p>
    </div>
  </section>
"""
    return shell(top, parts, body)


# ---------- The sitemap ----------

def sitemap(text: str, releases: list) -> str:
    """Lists every release page after the changelog's own entry, which carries the newest date."""
    text = re.sub(r"\n  <url>\n    <loc>" + re.escape(PAGE_URL) + r"\d[^<]*</loc>.*?</url>", "",
                  text, flags=re.S)
    if not releases:
        return text
    newest = releases[0]["date"].strftime("%Y-%m-%d")
    own = re.search(r"  <url>\n    <loc>" + re.escape(PAGE_URL) + r"</loc>.*?</url>", text, flags=re.S)
    if not own:
        sys.exit(f"{SITEMAP.relative_to(ROOT)} has no entry for {PAGE_URL}")
    block = re.sub(r"<lastmod>.*?</lastmod>", f"<lastmod>{newest}</lastmod>", own.group(0))
    pages = "".join(f"""
  <url>
    <loc>{release_url(r["version"])}</loc>
    <lastmod>{r["date"].strftime("%Y-%m-%d")}</lastmod>
    <changefreq>yearly</changefreq>
    <priority>0.5</priority>
  </url>""" for r in releases)
    return text[:own.start()] + block + pages + text[own.end():]


# ---------- The feed ----------

def xml(text: str) -> str:
    return html.escape(text, quote=True)


def feed(releases: list) -> str:
    items = []
    for r in releases[:FEED_ITEMS]:
        link = release_url(r["version"])
        summary = plain(r["notes"]) or "No notes were written for this release."
        body = markdown(r["notes"], "") if r["notes"] else f"<p>{xml(summary)}</p>"
        items.append(f"""    <item>
      <title>TMPlayer {xml(r["version"])}</title>
      <link>{xml(link)}</link>
      <guid isPermaLink="false">tmplayer-v{xml(r["version"])}</guid>
      <pubDate>{email.utils.format_datetime(r["date"], usegmt=True)}</pubDate>
      <description>{xml(body)}</description>
    </item>""")
    built = (email.utils.format_datetime(releases[0]["date"], usegmt=True) if releases else
             "Thu, 01 Jan 1970 00:00:00 GMT")
    joined = "\n".join(items)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>TMPlayer releases</title>
    <link>{PAGE_URL}</link>
    <description>{xml(DESCRIPTION)}</description>
    <language>en-gb</language>
    <lastBuildDate>{built}</lastBuildDate>
    <atom:link href="{FEED_URL}" rel="self" type="application/rss+xml"/>
{joined}
  </channel>
</rss>
"""


# ---------- Putting it together ----------

def version_key(v: str):
    return tuple(int(x) for x in re.findall(r"\d+", v))


def collect(raw: list) -> list:
    releases = []
    for r in raw:
        if r.get("draft") or r.get("prerelease") or not r.get("published_at"):
            continue
        tag = r["tag_name"]
        notes = clean_notes(tag_message(tag)) or clean_notes(body_prose(r.get("body") or ""))
        releases.append({
            "version": tag[1:] if tag.startswith("v") else tag,
            "date": datetime.strptime(r["published_at"], "%Y-%m-%dT%H:%M:%SZ")
                    .replace(tzinfo=timezone.utc),
            "url": r.get("html_url") or f"https://github.com/{REPO}/releases/tag/{tag}",
            "notes": notes,
        })
    # Newest first by version, then by date, so a patch to an old line sits with its line.
    releases.sort(key=lambda r: (version_key(r["version"]), r["date"]), reverse=True)
    return releases


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--input", help="a JSON file of GitHub releases, instead of asking GitHub")
    parser.add_argument("--out", default=str(OUT_DIR), help="the directory to write into")
    args = parser.parse_args()

    releases = collect(load_releases(args.input))
    parts = shared_parts(TEMPLATE.read_text(encoding="utf-8"))
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for name, text in (("index.html", page(releases, parts)), ("feed.xml", feed(releases))):
        (out / name).write_text(text, encoding="utf-8", newline="\n")
    # One page per release; a folder left from a release that was since deleted goes with it.
    wanted = {r["version"] for r in releases}
    for old in out.iterdir():
        if old.is_dir() and VERSION_DIR.match(old.name) and old.name not in wanted:
            shutil.rmtree(old)
    for i, r in enumerate(releases):
        newer = releases[i - 1] if i > 0 else None
        older = releases[i + 1] if i + 1 < len(releases) else None
        folder = out / r["version"]
        folder.mkdir(exist_ok=True)
        (folder / "index.html").write_text(release_page(r, newer, older, i == 0, parts),
                                           encoding="utf-8", newline="\n")
    if out == OUT_DIR:
        SITEMAP.write_text(sitemap(SITEMAP.read_text(encoding="utf-8"), releases),
                           encoding="utf-8", newline="\n")
    print(f"Wrote the changelog with {len(releases)} releases, and a page for each, to {out}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
