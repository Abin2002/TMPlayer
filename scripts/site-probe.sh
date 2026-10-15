#!/usr/bin/env bash
# Prints the headers that matter for each path in a probe list, sorted per response, so two hosts
# can be diffed: scripts/site-probe.sh <base-url> <probe-list>
set -euo pipefail
base=$1
while read -r u; do
  echo "== $u"
  curl -sSI "$base$u" | tr -d '\r' \
    | grep -iE '^(HTTP|location|content-type|cache-control|access-control|x-content|referrer|x-frame|permissions)' \
    | sed -E 's/^(HTTP\/[0-9.]+) ([0-9]+).*/HTTP \2/; s/ +$//' | sort -f
done < "$2"
