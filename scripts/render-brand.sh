#!/bin/sh
# Regenerates the two brand PNGs from their SVG sources.
#
# These two files stay PNG on purpose while every screenshot in the repo is WebP: og.png is fetched
# by link unfurlers such as WhatsApp and LinkedIn, which cannot be relied on to decode WebP and
# would show no picture at all, and icon-512.png is the PWA icon, where installers expect PNG.
#
# Run it from anywhere: paths are resolved against the repository root.
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

# The PWA icon is the plain mark. A density of 512 over the 108 unit viewBox renders the vector at
# its final size rather than upscaling a small raster.
magick -background none -density 512 "$root/site/logo.svg" \
    -resize 512x512 -strip "$root/site/icon-512.png"

# The social card is drawn in HTML from the site's own stylesheet, so the TV and phone are the same
# CSS frames as the home page hero around the real screenshots. Chromium renders it at 1200 by 630,
# and the PNG is forced to 8 bit RGB: ImageMagick wrote this card as 16 bit when it rasterised an SVG, and a 16 bit PNG
# is a file some link preview crawlers refuse, so the link arrives with no picture.
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
chromium-browser --headless=new --disable-gpu --no-sandbox --hide-scrollbars \
    --allow-file-access-from-files --force-device-scale-factor=1 --window-size=1200,630 \
    --virtual-time-budget=6000 --screenshot="$tmp/og.png" "file://$root/scripts/og-card.html" 2>/dev/null
magick "$tmp/og.png" -strip -depth 8 -define png:color-type=2 "$root/site/og.png"

echo "wrote site/icon-512.png and site/og.png"
