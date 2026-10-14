#!/usr/bin/env bash
# Static files and headers of the new build against the baseline (docs/ASTRO-MIGRATION.md, AM02).
#
#   1. every static file of the baseline is in site/dist/ with the same bytes (sha256sum -c)
#   2. the headers of every probe path are the same on both local servers
#
# Usage: scripts/site-parity-static.sh <baseline-dir-state> <old-url> <new-url>
#   <baseline-dir-state> holds static-baseline.sha and probe.txt (written in AM00)
set -uo pipefail
state=$1 old=$2 new=$3
here=$(cd "$(dirname "$0")" && pwd)
dist="$here/../site/dist"
fail=0
# sitemap.xml and the feed are compared as XML by site-parity-xml.py; vercel.json is config.
grep -vE '\./(sitemap\.xml|vercel\.json|changelog/feed\.xml)$' "$state/static-baseline.sha" > "$state/static-check.sha"
if ! out=$(cd "$dist" && sha256sum --quiet -c "$state/static-check.sha" 2>&1); then
  echo "$out" | sed 's/^/static\t/'
  fail=1
fi
diff <("$here/site-probe.sh" "$old" "$state/probe.txt") <("$here/site-probe.sh" "$new" "$state/probe.txt") \
  | sed 's/^/headers\t/' && echo "static: $(wc -l < "$state/static-check.sha") files, $(wc -l < "$state/probe.txt") probes, no differences" >&2 || fail=1
exit $fail
