#!/usr/bin/env python3
"""Brings the Arch Linux PKGBUILD to a release.

    scripts/aur-bump.py 1.27.1
    scripts/aur-bump.py 1.27.1 --feed "$RUNNER_TEMP/latest.json"

sets pkgver, resets pkgrel to 1 and writes the tarball's SHA-256 into
desktop/packaging/aur/PKGBUILD. The hash comes from the update feed's linux-x64-tarball entry when
--feed is given (the release job has just written it), otherwise from the release's own asset
digest on GitHub, through gh. An older version than the PKGBUILD already names is left alone, so a
patch to an old line never moves the package back. The release job runs this in the same commit as
the update feed, and publishes the result to the AUR when it has the AUR_SSH_PRIVATE_KEY secret.
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKGBUILD = ROOT / "desktop" / "packaging" / "aur" / "PKGBUILD"
REPO = "dracu-lah/TMPlayer"


def version_key(v: str):
    return tuple(int(x) for x in re.findall(r"\d+", v))


def sha_from_feed(path: str, version: str) -> str:
    feed = json.loads(Path(path).read_text(encoding="utf-8"))
    if feed.get("version") != version:
        raise SystemExit(f"{path} names {feed.get('version')}, not {version}")
    return feed["assets"]["linux-x64-tarball"]["sha256"]


def sha_from_github(version: str) -> str:
    name = f"TMPlayer-{version}-linux-x64.tar.xz"
    out = subprocess.run(
        ["gh", "api", f"repos/{REPO}/releases/tags/v{version}",
         "--jq", f'.assets[] | select(.name == "{name}") | .digest'],
        check=True, capture_output=True, text=True).stdout.strip()
    if not out.startswith("sha256:"):
        raise SystemExit(f"v{version} has no digest for {name}")
    return out.removeprefix("sha256:")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("version", help="the release, such as 1.27.1")
    parser.add_argument("--feed", help="latest.json for this release, instead of asking GitHub")
    args = parser.parse_args()

    text = PKGBUILD.read_text(encoding="utf-8")
    current = re.search(r"^pkgver=(\S+)$", text, re.M).group(1)
    if version_key(args.version) < version_key(current):
        print(f"The PKGBUILD names {current}, newer than {args.version}; leaving it.")
        return 0

    sha = sha_from_feed(args.feed, args.version) if args.feed else sha_from_github(args.version)
    if not re.fullmatch(r"[0-9a-f]{64}", sha):
        raise SystemExit(f"not a SHA-256: {sha}")
    text = re.sub(r"^pkgver=\S+$", f"pkgver={args.version}", text, flags=re.M)
    text = re.sub(r"^pkgrel=\S+$", "pkgrel=1", text, flags=re.M)
    text = re.sub(r"^sha256sums=\('[0-9a-f]+'\)$", f"sha256sums=('{sha}')", text, flags=re.M)
    PKGBUILD.write_text(text, encoding="utf-8", newline="\n")
    print(f"PKGBUILD: {current} -> {args.version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
