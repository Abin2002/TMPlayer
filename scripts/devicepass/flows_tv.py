#!/usr/bin/env python3
"""Scripted Android TV flows for the device pass (CHECKLIST.md, section T). Emulator only.

    python3 -I scripts/devicepass/flows_tv.py --serial emulator-5556 --out /tmp/pass/tv

Driven with the D-pad like a remote. Never point this at the real Mi TV stick: the user said TV
testing happens on the emulator only, and a scripted key once reached the launcher and played
videos on the family TV. Every key is sent only while com.tmplayer has the focus.
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adb_flow import Device, Node, Skip, Strings, run_flow, write_report  # noqa: E402

S = Strings()

HOME = S("browse.tab_home", "Home")
CHATS = S("browse.tab_chats", "Chats")
SEARCH = S("common.search", "Search")
GROUP_TELEGRAM = S("nav.telegram", "Telegram")
GROUP_FOLDERS = S("nav.folders", "Folders")
SETTINGS = S("nav.settings", "Settings")
VOICE = [S("browse.search_by_voice", "Search by voice"), S("browse.voice_search", "Voice search"),
         S("search.voice_keyboard", "Type or dictate with the remote")]
SEARCH_HINT = [S("search.field_hint", "Search videos and chats"), S("browse.search_all_chats", "Search chats and videos")]
SEARCHING = [S("search.searching", "Searching every chat"), S("search.still_searching", "Still searching")]
VIDEOS_HEAD = S("search.videos_heading", "Videos")
COPY_CHANGE = S("detail.copy_change", "Change")
COPIES_TITLE = S("detail.copies_title", "Copies of this video")
ALL_CHIP = S("browse.filter_all", "All")
BACK_TO_TOP = S("common.back_to_top", "Back to top")
SORT = S("grid.sort", "Sort")
FINDING = [S("browse.finding_videos", "Finding videos")]


def focused(d: Device) -> Node | None:
    root = d.dump()
    if root is None:
        return None
    hits = [n for n in root.walk() if n.focused]
    return hits[-1] if hits else None


def focused_labels(d: Device) -> list[str]:
    n = focused(d)
    return n.all_labels() if n else []


def move_focus_to(d: Device, label: str, direction: str, max_moves: int = 12, exact: bool = True) -> bool:
    """Press a D-pad direction until the focused element carries label."""
    for _ in range(max_moves + 1):
        labs = focused_labels(d)
        if any((l.casefold() == label.casefold()) if exact else (label.casefold() in l.casefold()) for l in labs):
            return True
        d.key(direction)
    return False


def to_rail(d: Device) -> None:
    """Into the side rail: Left until a rail entry (Home or Chats) has the focus."""
    if d.focused_package() != d.package:
        d.launch(tv=True)
    for _ in range(6):
        labs = [l.casefold() for l in focused_labels(d)]
        if HOME.casefold() in labs or CHATS.casefold() in labs or SEARCH.casefold() in labs:
            return
        d.key("left")
    raise Skip("could not reach the side rail with Left")


def rail_go(d: Device, label: str) -> None:
    to_rail(d)
    if not (move_focus_to(d, label, "down", 10) or move_focus_to(d, label, "up", 14)):
        raise Skip(f"rail has no {label!r}")
    d.key("center")
    time.sleep(2)


def flow_launch(a) -> list:
    def start(d: Device):
        d.launch(tv=True)
        if d.wait_for(HOME, timeout=30, exact=True) is None:
            return "Home not visible 30 s after launch"

    def rail(d: Device):
        to_rail(d)
        labs = d.labels()
        notes = []
        for want in (HOME, CHATS, SEARCH):
            if not any(l.casefold() == want.casefold() for l in labs):
                notes.append(f"rail lacks {want!r}")
        for gone in (GROUP_TELEGRAM, GROUP_FOLDERS):
            if any(l.casefold() == gone.casefold() for l in labs):
                notes.append(f"rail still shows the {gone!r} group heading")
        return "; ".join(notes) or None

    return [("launch", start), ("rail", rail)]


def flow_chats(a) -> list:
    def go(d: Device):
        rail_go(d, CHATS)
        if not d.wait_for(ALL_CHIP, timeout=10, exact=True):
            return "Chats page without the All chip"

    def chips(d: Device):
        root = d.dump()
        allc = d.find(ALL_CHIP, exact=True, root=root) if root else None
        if allc is None:
            raise Skip("no All chip")
        row = sorted({n.label for n in root.walk() if n.label and abs(n.center[1] - allc.center[1]) < 40})
        d.log(f"chips: {row}")

    def open_chat(d: Device):
        d.key("down", 2)
        n = d.find(a.chat)
        for _ in range(15):
            if n is not None and n.label and any(a.chat.casefold() in l.casefold() for l in focused_labels(d)):
                break
            d.key("down")
            n = d.find(a.chat)
        else:
            raise Skip(f"could not focus chat {a.chat!r} with Down")
        d.key("center")
        d.wait_gone(*FINDING, timeout=30)
        time.sleep(2)

    def no_back_to_top(d: Device):
        d.key("down", a.scrolls, pause=0.15)
        d.key("up", 3)
        time.sleep(0.8)
        if d.find(BACK_TO_TOP):
            return "Back to top shows on TV (the batch leaves TV without it)"

    def sort(d: Device):
        d.key("up", a.scrolls + 6, pause=0.1)
        if d.find(SORT, exact=True) is None:
            return "no Sort control visible at the top of the chat"
        if not move_focus_to(d, SORT, "up", 6) and not move_focus_to(d, SORT, "right", 8):
            return "Sort is shown but D-pad cannot reach it"
        d.key("center")
        time.sleep(1)
        d.screenshot("sort-menu", d.out / "chats")
        d.back()

    return [("open_chats", go), ("chips", chips), ("open_chat", open_chat),
            ("no_back_to_top", no_back_to_top), ("sort_menu", sort)]


def flow_search(a) -> list:
    def go(d: Device):
        rail_go(d, SEARCH)
        time.sleep(1)
        if not d.find(*VOICE):
            return "Search page shows no voice entry"
        if not d.find(*SEARCH_HINT):
            return "Search page shows no field hint"

    def type_(d: Device):
        field = d.find(*SEARCH_HINT)
        if field and not field.focused:
            move_focus_to(d, field.label, "up", 4)
        d.key("center")
        time.sleep(1)
        d.type_text(a.query)
        d.key("enter")
        d.wait_gone(*SEARCHING, timeout=40)

    def results(d: Device):
        if d.find(VIDEOS_HEAD, exact=True) is None:
            return f"no {VIDEOS_HEAD!r} heading for {a.query!r}"

    def open_result(d: Device):
        for _ in range(6):
            d.key("down")
            labs = focused_labels(d)
            if labs and not any(h.casefold() in (l.casefold() for l in labs) for h in SEARCH_HINT):
                break
        d.key("center")
        time.sleep(3)
        if d.find(COPY_CHANGE, exact=True) is None:
            return "details page has no copy line with Change (single copy, or missing)"

    def copies(d: Device):
        if not move_focus_to(d, COPY_CHANGE, "down", 8) and not move_focus_to(d, COPY_CHANGE, "up", 10):
            raise Skip("Change not reachable with the D-pad")
        d.key("center")
        if d.wait_for(COPIES_TITLE, timeout=5) is None:
            return "Change did not open the copies list"
        d.back()

    def back_out(d: Device):
        d.back(2)
        time.sleep(1)
        if d.focused_package() != d.package:
            return "Back from search details left the app"

    return [("open_search", go), ("type_query", type_), ("results", results),
            ("open_result", open_result), ("copies_list", copies), ("back_out", back_out)]


FLOWS = {"launch": flow_launch, "chats": flow_chats, "search": flow_search}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", required=True, help="the TV emulator's serial, e.g. emulator-5556")
    ap.add_argument("--out", required=True)
    ap.add_argument("--package", default="com.tmplayer")
    ap.add_argument("--query", default="love under")
    ap.add_argument("--chat", default="Cinema_paradasio")
    ap.add_argument("--scrolls", type=int, default=25, help="Down presses for the long list check")
    ap.add_argument("--only", default="")
    a = ap.parse_args()

    if not a.serial.startswith("emulator-"):
        print("TV flows run on the TV emulator only, never on the real stick.", file=sys.stderr)
        return 2
    names = [n for n in (a.only.split(",") if a.only else FLOWS) if n]
    unknown = [n for n in names if n not in FLOWS]
    if unknown:
        print(f"unknown flows {unknown}; known: {list(FLOWS)}", file=sys.stderr)
        return 2
    dev = Device(a.serial, a.out, a.package)
    dev.log(f"{a.package} {dev.app_version()}")
    if "launch" not in names:
        dev.launch(tv=True)
    summaries = [run_flow(dev, n, FLOWS[n](a)) for n in names]
    write_report(dev, summaries, "TV pass")
    print(f"done: {len(dev.findings)} findings, {Path(a.out) / 'REPORT.md'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
