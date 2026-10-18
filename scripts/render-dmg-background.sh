#!/bin/sh
# Renders the macOS DMG window's background from scripts/dmg-background.html, at 1x and 2x, into
# desktop/packaging/macos/. The release job joins the two into one TIFF on the Mac runner
# (tiffutil), so a Retina screen gets the sharp copy.
#
# Served over a local web server, because Chromium will not load web fonts into a file:// page.
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
out="$root/desktop/packaging/macos"
port=8721
tmp=$(mktemp -d)
python3 -m http.server "$port" --bind 127.0.0.1 --directory "$root" >/dev/null 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null; rm -rf "$tmp"' EXIT
sleep 1
for scale in 1 2; do
    chromium-browser --headless=new --disable-gpu --no-sandbox --hide-scrollbars \
        --force-device-scale-factor="$scale" --window-size=660,400 \
        --virtual-time-budget=6000 --screenshot="$tmp/bg$scale.png" \
        "http://127.0.0.1:$port/scripts/dmg-background.html" 2>/dev/null
done
magick "$tmp/bg1.png" -strip -depth 8 -define png:color-type=2 "$out/dmg-background.png"
magick "$tmp/bg2.png" -strip -depth 8 -define png:color-type=2 "$out/dmg-background@2x.png"
echo "wrote $out/dmg-background.png and dmg-background@2x.png"
