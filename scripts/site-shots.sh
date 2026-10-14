#!/usr/bin/env bash
# Screenshots of both builds side by side, compared pixel by pixel (docs/ASTRO-MIGRATION.md, AM02).
#
# Headless Chromium shoots each page from the old and the new server at phone and desktop width,
# light and dark, fanned out over every core; `magick compare -metric AE -fuzz 2%` counts the
# pixels that differ. A pair above the limit fails and leaves old/new/diff PNGs in the out dir.
#
# Usage: scripts/site-shots.sh <old-url> <new-url> <out-dir> [path ...]
#   default paths: the 21 English pages, then ar and fa (RTL), zh, ja, hi, ml, de home and features
set -uo pipefail
old=${1%/} new=${2%/} out=$3
shift 3
LIMIT=${SHOTS_LIMIT:-200}
mkdir -p "$out"
if [ $# -eq 0 ]; then
  set -- / /features/ /download/ /install/ /android-tv/ /fire-tv/ /faq/ /formats/ /translate/ \
    /guides/telegram-videos-on-android-tv/ /guides/telegram-videos-on-fire-tv/ \
    /guides/telegram-on-tv-without-casting/ /changelog/ /changelog/1.26.0/ /legal/ /privacy/ \
    /security/ /signing/ /support/ /no-such-page/ \
    /ar/ /ar/download/ /fa/ /fa/features/ /zh/ /ja/ /hi/ /ml/ /de/ /de/features/
fi
shoot() {  # side path size scheme
  local side=$1 path=$2 size=$3 scheme=$4 base
  [ "$side" = old ] && base=$old || base=$new
  local name; name=$(printf '%s' "$path" | tr '/' '_')
  local flags=()
  [ "$scheme" = dark ] && flags+=(--force-dark-mode)
  timeout -k 5 60 chromium-browser --headless=new --disable-gpu --no-sandbox --hide-scrollbars --mute-audio \
    --no-first-run --disable-extensions --force-prefers-reduced-motion --user-data-dir="$(mktemp -d)" "${flags[@]}" \
    --window-size="$size" --virtual-time-budget=4000 \
    --screenshot="$out/${name}_${size}_${scheme}_${side}.png" "$base$path" >/dev/null 2>&1
}
export -f shoot; export old new out
for p in "$@"; do for s in 390,3200 1440,2400; do for c in light dark; do for side in old new; do
  echo "$side $p $s $c"; done; done; done; done | xargs -P"$(nproc)" -L1 bash -c 'shoot "$@"' _
count() {  # old.png -> differing pixels, diff written beside
  local f=$1 px
  px=$(magick compare -metric AE -fuzz 2% "$f" "${f%_old.png}_new.png" "${f%_old.png}_diff.png" 2>&1 >/dev/null | cut -d' ' -f1)
  px=${px%%.*}
  [[ ${px:-x} =~ ^[0-9]+$ ]] && echo "$px" || echo 999999
}
fail=0 n=0
for f in "$out"/*_old.png; do
  n=$((n+1))
  px=$(count "$f")
  if [ "$px" -gt "$LIMIT" ]; then
    # Font loading can land a frame apart; a real change survives a second pair of shots.
    meta=$(basename "${f%_old.png}") path=${meta%_*_*}
    rest=${meta#"$path"_} size=${rest%%_*} scheme=${rest#*_}
    path=$(printf '%s' "$path" | tr '_' '/')
    shoot old "$path" "$size" "$scheme"; shoot new "$path" "$size" "$scheme"
    px=$(count "$f")
  fi
  if [ "$px" -gt "$LIMIT" ]; then
    printf 'shots\t%s\t<=%s px\t%s px\n' "$(basename "${f%_old.png}")" "$LIMIT" "$px"; fail=1
  else rm -f "${f%_old.png}_diff.png" "$f" "${f%_old.png}_new.png"; fi
done
echo "shots: $n pairs compared, limit $LIMIT px" >&2
exit $fail
