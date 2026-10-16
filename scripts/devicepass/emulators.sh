#!/usr/bin/env bash
# Phone and TV emulators for the device pass. Prints every command before it runs.
#
#   scripts/devicepass/emulators.sh list
#   scripts/devicepass/emulators.sh start phone|tv     # foreground: run it through run_in_background
#   scripts/devicepass/emulators.sh wait phone|tv      # until sys.boot_completed is 1
#   scripts/devicepass/emulators.sh install phone|tv|<serial> <apk>   # adb install -r -g
#   scripts/devicepass/emulators.sh status
#   scripts/devicepass/emulators.sh stop phone|tv|all  # only emulators this script started
#
# Notes from memory:
# - AVDs: tmplayer_phone_api36 (emulator-5554) and tmplayer_tv_api36 (emulator-5556) carry a real
#   Telegram login in the debug build com.tmplayer. Keep it: never -wipe-data, never uninstall,
#   update only with install -r of a debug APK (a release APK has another key and wipes the login).
# - They segfault on a cold boot with swiftshader or lavapipe: -gpu host -no-window.
# - `nohup emulator &` dies when the calling shell returns; start runs in the foreground, so from
#   Claude Code start it with Bash run_in_background, from a terminal in its own tab.
# - Sway sends emulator windows to workspace 10; -no-window has none anyway.
# - One emulator set at a time, and the win11 VM only after the emulators are stopped.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
EMULATOR="$SDK/emulator/emulator"
ADB="$(command -v adb || echo "$SDK/platform-tools/adb")"
STATE="${XDG_RUNTIME_DIR:-/tmp}/tmplayer-devicepass"
mkdir -p "$STATE"

PHONE_AVD="${PHONE_AVD:-tmplayer_phone_api36}"
TV_AVD="${TV_AVD:-tmplayer_tv_api36}"
PHONE_PORT=5554
TV_PORT=5556

say() { printf '>> %s\n' "$*" >&2; }
run() { printf '$ %s\n' "$*" >&2; "$@"; }

avd_of() { case "$1" in phone) echo "$PHONE_AVD" ;; tv) echo "$TV_AVD" ;; *) echo "unknown target $1" >&2; exit 2 ;; esac; }
port_of() { case "$1" in phone) echo "$PHONE_PORT" ;; tv) echo "$TV_PORT" ;; *) echo "unknown target $1" >&2; exit 2 ;; esac; }
serial_of() {
  case "$1" in phone|tv) echo "emulator-$(port_of "$1")" ;; *) echo "$1" ;; esac
}

cmd_list() {
  run "$EMULATOR" -list-avds
  run "$ADB" devices -l
}

cmd_status() {
  "$ADB" devices -l
  for t in phone tv; do
    s=$(serial_of "$t")
    if [[ -f "$STATE/$t.started" ]]; then echo "$t ($s): started by this script"; else echo "$t ($s): not started by this script"; fi
  done
  if podman ps --format '{{.Names}}' 2>/dev/null | grep -qx win11; then
    say "note: the win11 VM is running too; the notes say one VM or emulator set at a time"
  fi
}

cmd_start() {
  local t="$1" avd port serial
  avd=$(avd_of "$t"); port=$(port_of "$t"); serial=$(serial_of "$t")
  if ! "$EMULATOR" -list-avds | grep -qx "$avd"; then
    echo "AVD $avd does not exist; emulator -list-avds shows:" >&2; "$EMULATOR" -list-avds >&2; exit 1
  fi
  if "$ADB" devices | grep -q "^$serial"; then
    say "$serial is already attached; not starting a second one (and not marking it as ours)"
    exit 0
  fi
  if podman ps --format '{{.Names}}' 2>/dev/null | grep -qx win11; then
    say "warning: the win11 VM is running; stop it first if the machine gets slow (vm.sh stop)"
  fi
  say "starting $avd on port $port as $serial, headless, -gpu host, login kept (no -wipe-data)"
  date +%s > "$STATE/$t.started"
  # Foreground on purpose: the emulator lives as long as this process.
  local rc=0
  run "$EMULATOR" -avd "$avd" -port "$port" -no-window -no-audio -gpu host -no-snapshot-load -no-boot-anim || rc=$?
  rm -f "$STATE/$t.started"
  say "$avd exited with $rc"
  return "$rc"
}

cmd_wait() {
  local serial; serial=$(serial_of "$1")
  local limit="${2:-300}" waited=0
  say "waiting up to ${limit}s for $serial to boot"
  run "$ADB" -s "$serial" wait-for-device
  until [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 3; waited=$((waited + 3))
    if (( waited >= limit )); then echo "$serial did not finish booting in ${limit}s" >&2; exit 1; fi
  done
  # Keep the screen awake for screenshots (emulator only).
  run "$ADB" -s "$serial" shell svc power stayon true
  run "$ADB" -s "$serial" shell input keyevent 82
  say "$serial booted"
}

apk_info() {
  local apk="$1" aapt
  aapt=$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)
  if [[ -n "$aapt" ]]; then
    "$aapt" dump badging "$apk" 2>/dev/null | grep -E "^package:" || true
  fi
}

cmd_install() {
  local serial apk
  serial=$(serial_of "$1"); apk="$2"
  [[ -f "$apk" ]] || { echo "no such APK: $apk" >&2; exit 1; }
  say "APK: $(apk_info "$apk")"
  say "installed now: $("$ADB" -s "$serial" shell dumpsys package com.tmplayer 2>/dev/null | grep -m2 -E 'versionName|versionCode' | tr -s ' ' | tr '\n' ' ')"
  if [[ "$serial" != emulator-* ]]; then
    case "$apk" in
      *debug*) echo "refusing: a debug APK on a real device would replace the release signing and wipe the Telegram login" >&2; exit 1 ;;
    esac
    echo "$serial is a real device the family uses. Re-run with CONFIRM_REAL=1 once the user has said yes." >&2
    [[ "${CONFIRM_REAL:-}" == 1 ]] || exit 1
    run "$ADB" -s "$serial" install -r "$apk"
  else
    case "$apk" in
      *release*) say "warning: a release APK over the emulator's debug build has another key; install fails or the login is lost" ;;
    esac
    run "$ADB" -s "$serial" install -r -g "$apk"
  fi
  say "installed: $("$ADB" -s "$serial" shell dumpsys package com.tmplayer | grep -m2 -E 'versionName|versionCode' | tr -s ' ' | tr '\n' ' ')"
}

cmd_stop() {
  local targets=("$1"); [[ "$1" == all ]] && targets=(phone tv)
  for t in "${targets[@]}"; do
    local serial; serial=$(serial_of "$t")
    if [[ ! -f "$STATE/$t.started" ]]; then
      say "$t ($serial) was not started by this script; leaving it running"
      continue
    fi
    run "$ADB" -s "$serial" emu kill || true
    rm -f "$STATE/$t.started"
  done
}

case "${1:-}" in
  list) cmd_list ;;
  status) cmd_status ;;
  start) cmd_start "${2:?phone or tv}" ;;
  wait) cmd_wait "${2:?phone or tv}" "${3:-300}" ;;
  install) cmd_install "${2:?phone, tv or a serial}" "${3:?path to the APK}" ;;
  stop) cmd_stop "${2:?phone, tv or all}" ;;
  *) sed -n '2,10p' "$0"; exit 2 ;;
esac
