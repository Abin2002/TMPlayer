#!/usr/bin/env python3
"""Writes the site's release data from the GitHub releases.

    scripts/release-data.py
    scripts/release-data.py --input releases.json

writes site/src/data/releases.json, which the Astro site reads for the changelog, one page per
release, the RSS feed and the release entries in the sitemap. The release job runs it in the same
step that publishes the update feed, so the site names a release in the same commit as latest.json
does; it can also be run by hand after a release that went out some other way.

Where the releases come from: --input, a file holding the JSON array GitHub's list releases call
returns; otherwise `gh api` when gh is installed (it is on a GitHub runner, and locally it is
already signed in); otherwise the REST API directly, with GITHUB_TOKEN or GH_TOKEN if one is set.

Where each release's words come from. A release body on GitHub is mostly machinery: a table of
files, install links and GitHub's generated "Full Changelog" line. The sentences a person wrote
for the release are the annotated tag's message, the same text the update popup shows, so that is
what the site prints, without its "release: X.Y.Z" subject line. A plain tag has no message of its
own (git would answer with the commit's, which is the wrong text), and for those the site falls
back to whatever prose the release body opens with, before its first heading or table. A release
with neither has empty notes, and the pages say so and link to GitHub.

The tag's message is read from git when the tag is here and annotated, and from the API when it
is not: the release job's checkout is shallow and holds only the tag it was started by, and the
two sources give the same text, so the data does not depend on which one answered.

Each release also carries how many times its app files were downloaded from GitHub (the APKs and
the desktop packages, not the checksums, notices or source archives). The home page adds them up
for its stats row when the build cannot ask GitHub itself (site/src/lib/stats.ts). The in-app
updater fetches the same files, so this counts downloads, not people.

The notes stay markdown source; site/src/lib/releases.ts turns them into HTML. Same input, same
bytes out: nothing here reads the clock. The download counts do move between runs, which is fine
because the job runs this once per release.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = "dracu-lah/TMPlayer"
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "site" / "src" / "data" / "releases.json"

# The files a person installs, as opposed to checksums, notices and source archives.
APP_FILE = re.compile(r"^tmplayer(?!.*-source\.)(?:[-_.].*)?\.(apk|msi|dmg|appimage|deb|rpm|flatpak|zip|tar\.xz|tar\.gz)$", re.I)

# Lines git adds to a message that are not part of what the release says.
TRAILER = re.compile(r"^(Co-Authored-By|Claude-Session|Signed-off-by|Change-Id):", re.I)


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
            # GitHub's own form, 2026-10-14T09:12:00Z: the pages and the feed take the day,
            # the full time and the RFC 822 date from it.
            "published": r["published_at"],
            "url": r.get("html_url") or f"https://github.com/{REPO}/releases/tag/{tag}",
            "notes": notes,
            "downloads": sum(a.get("download_count", 0) for a in r.get("assets") or []
                             if APP_FILE.match(a.get("name", ""))),
        })
    # Newest first by version, then by date, so a patch to an old line sits with its line.
    releases.sort(key=lambda r: (version_key(r["version"]), r["published"]), reverse=True)
    return releases


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--input", help="a JSON file of GitHub releases, instead of asking GitHub")
    parser.add_argument("--out", default=str(OUT), help="the file to write")
    args = parser.parse_args()

    releases = collect(load_releases(args.input))
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(releases, indent=2, ensure_ascii=False) + "\n",
                   encoding="utf-8", newline="\n")
    print(f"Wrote {len(releases)} releases to {out}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
