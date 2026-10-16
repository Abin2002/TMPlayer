#!/usr/bin/env bash
# Fails if any Mach-O library in a macOS app bundle, loose or inside one of its jars, loads another
# library by an absolute path outside the system (/usr/lib, /System). Such a path only exists on the
# machine that built the library: the TDLib in tdl-coroutines 15.0.0 named the build machine's
# Homebrew libjvm and OpenSSL, so sign in failed on every other Mac (tdl-coroutines#163), while the
# self-test passed on a runner that happened to have them.
#
#   scripts/check-macho-portable.sh path/to/TMPlayer.app
set -euo pipefail

app="${1:?usage: $0 TMPlayer.app}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
bad=0
checked=0

check() { # <file> <label>
  local refs
  refs="$(otool -L "$1" | tail -n +2 | sed -E 's/^[[:space:]]+//; s/ \(.*$//' | grep -E '^/' | grep -vE '^/(usr/lib|System)/' || true)"
  checked=$((checked + 1))
  if [ -n "$refs" ]; then
    bad=$((bad + 1))
    echo "::error::$2 loads libraries by an absolute path:"
    echo "$refs" | sed 's/^/    /'
  fi
}

is_macho() { file -b "$1" | grep -q 'Mach-O'; }

while IFS= read -r -d '' f; do
  case "$f" in
    *.jar)
      dest="$work/$(basename "$f")"
      mkdir -p "$dest"
      unzip -qq -o "$f" '*.dylib' '*.jnilib' -d "$dest" 2>/dev/null || true
      while IFS= read -r -d '' lib; do
        is_macho "$lib" && check "$lib" "$(basename "$f")!/${lib#"$dest"/}"
      done < <(find "$dest" -type f -print0)
      ;;
    *)
      is_macho "$f" && check "$f" "${f#"$app"/}"
      ;;
  esac
done < <(find "$app" -type f -print0)

echo "checked $checked Mach-O files"
[ "$bad" -eq 0 ] || { echo "$bad of them would only load on the machine that built them"; exit 1; }
