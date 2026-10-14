#!/usr/bin/env bash
# The one review every site change runs (docs/ASTRO-MIGRATION.md, AM02): build the Astro site,
# serve the baseline and the new build side by side the way Vercel does, and compare them.
#
#   original  $SITE_BASELINE (default ../TMPlayer-baseline/site, the old site at f71701c)  :4001
#   new       site/dist with site/vercel.json                                              :4002
#
# Checks: pages (SEO head, OG, JSON-LD, hreflang, locales, text, elements, assets), sitemap + feed
# + robots, static files + headers, screenshots. Prints one summary line per check; the details
# go to $OUT (default: a temp dir) as TSV. Exit 1 if any check fails.
#
# Usage: scripts/site-review.sh [--no-build] [--no-shots] [--page id ...] [--lang xx ...]
#        [--shots "/path/ /path2/"]
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/.." && pwd)
baseline=${SITE_BASELINE:-$repo/../TMPlayer-baseline/site}
state=$here/parity-baseline
out=${OUT:-$(mktemp -d)}
build=1 shots=1 filter=() shot_paths=""
OLD_PORT=${OLD_PORT:-4001} NEW_PORT=${NEW_PORT:-4002}
while [ $# -gt 0 ]; do
  case $1 in
    --no-build) build=0 ;;
    --no-shots) shots=0 ;;
    --page|--lang) filter+=("$1" "$2"); shift ;;
    --shots) shot_paths=$2; shift ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done
[ -d "$baseline" ] || { echo "no baseline at $baseline (git worktree add ../TMPlayer-baseline f71701c)" >&2; exit 2; }
if [ $build = 1 ]; then
  pnpm -C "$repo/site" build > "$out/build.log" 2>&1 || { tail -30 "$out/build.log"; echo "build: FAILED"; exit 1; }
  echo "build: ok ($(find "$repo/site/dist" -name '*.html' | wc -l) html files)"
fi
python3 "$here/serve-vercel.py" "$baseline" --port "$OLD_PORT" --config "$baseline/vercel.json" 2>/dev/null & p1=$!
python3 "$here/serve-vercel.py" "$repo/site/dist" --port "$NEW_PORT" --config "$repo/site/vercel.json" 2>/dev/null & p2=$!
trap 'kill $p1 $p2 2>/dev/null' EXIT
for _ in $(seq 50); do curl -s -o /dev/null "http://127.0.0.1:$NEW_PORT/" && curl -s -o /dev/null "http://127.0.0.1:$OLD_PORT/" && break; sleep 0.1; done
old=http://127.0.0.1:$OLD_PORT new=http://127.0.0.1:$NEW_PORT
fail=0
run() {  # name, command...
  local name=$1; shift
  if "$@" > "$out/$name.tsv" 2> "$out/$name.err"; then
    echo "$name: ok   $(tail -1 "$out/$name.err")"
  else
    fail=1
    echo "$name: FAIL $(tail -1 "$out/$name.err")  ($out/$name.tsv)"
    cut -f2 "$out/$name.tsv" | sort | uniq -c | sort -rn | head -8 | sed 's/^/        /'
    head -5 "$out/$name.tsv" | cut -c1-240 | sed 's/^/        /'
  fi
}
run pages python3 "$here/site-parity.py" "$old" "$new" --urls "$state/urls-baseline.txt" --new-dir "$repo/site/dist" "${filter[@]}"
if [ ${#filter[@]} -eq 0 ]; then
  run xml python3 "$here/site-parity-xml.py" "$old" "$new"
  run static "$here/site-parity-static.sh" "$state" "$old" "$new"
fi
if [ $shots = 1 ]; then
  # shellcheck disable=SC2086
  run shots "$here/site-shots.sh" "$old" "$new" "$out/shots" $shot_paths
fi
echo "details: $out"
exit $fail
