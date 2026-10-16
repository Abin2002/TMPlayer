#!/usr/bin/env python3
"""Scripted phone flows for the device pass (CHECKLIST.md, sections P and E).

    python3 -I scripts/devicepass/flows_phone.py --serial emulator-5554 --out /tmp/pass/phone-avd
    python3 -I scripts/devicepass/flows_phone.py --serial d3f7da23 --real-device --out /tmp/pass/poco \
        --query "love under" --chat Cinema_paradasio --orientations portrait,landscape

Every flow runs in each orientation given, screenshots each step under <out>/<orientation>/<flow>/,
and writes <out>/<orientation>/REPORT.md. A missing button is a finding, never a crash. Nothing
here deletes, clears, renames or marks anything; the sort and grid toggles are put back as found.
Labels come from core/src/commonMain/resources/i18n/en.json, with fallbacks for keys still on an
agent's branch.
"""
from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adb_flow import Device, Skip, Strings, run_flow, write_report  # noqa: E402

S = Strings()

HOME = S("browse.tab_home", "Home")
CHATS = S("browse.tab_chats", "Chats")
SEARCH = S("common.search", "Search")
PROFILE = S("nav.profile", "Your account, Favourites, Downloads and Settings")
DRAWER = S("browse.open_navigation", "Open navigation")
POPOVER = [
    S("browse.tab_favourites", "Favourites"),
    S("nav.downloads", "Downloads"),
    S("browse.tab_history", "History"),
    S("browse.tab_watch_later", "Watch later"),
    S("nav.settings", "Settings"),
    S("about.title_short", "About"),
]
UPDATE = S("nav.update", "Update")
SEARCH_HINT = [S("search.field_hint", "Search videos and chats"), S("browse.search_all_chats", "Search chats and videos")]
RECENT = [S("browse.recent_searches", "Recent searches"), S("search.clear_recent", "Clear all")]
VIDEOS_HEAD = S("search.videos_heading", "Videos")
CHATS_HEAD = S("search.chats_heading", "Chats")
SEARCHING = [S("search.searching", "Searching every chat"), S("search.still_searching", "Still searching"),
             S("browse.searching", "Searching")]
FILTERS = S("search.filters", "Filters")
SEARCH_SORTS = [S("search.sort_best", "Best match"), S("search.sort_newest", "Newest"),
                S("search.sort_largest", "Largest"), S("search.sort_quality", "Best quality")]
COPY_FROM = S("detail.copy_from", "from")
COPY_CHANGE = S("detail.copy_change", "Change")
COPIES_TITLE = S("detail.copies_title", "Copies of this video")
FINDING = [S("browse.finding_videos", "Finding videos"), S("browse.loading_chats", "Loading your chats")]
AS_ROWS = [S("browse.show_as_rows", "Show as rows"), S("browse.as_rows", "As rows")]
AS_TILES = [S("browse.show_as_tiles", "Show as tiles"), S("browse.as_tiles", "As tiles")]
SORT = S("grid.sort", "Sort")
CHAT_SORTS = [S("grid.sort_newest", "Newest"), S("grid.sort_oldest", "Oldest"), S("grid.sort_name", "Name A to Z"),
              S("grid.sort_size", "Size"), S("grid.sort_year", "Year"), S("grid.sort_unwatched", "Unwatched first")]
BACK_TO_TOP = S("common.back_to_top", "Back to top")
ALL_CHIP = S("browse.filter_all", "All")
OLD_SETUP_STEP = S("setup.library_folders", "Only my folders")
OFFLINE = S("search.offline", "Offline. Showing the videos")


# ---- helpers --------------------------------------------------------------------------------
def to_home(d: Device) -> None:
    """Back to the Home tab without ever pressing Back on Home itself (that leaves the app)."""
    if d.focused_package() != d.package:
        d.log("app not in front, launching it again")
        d.launch()
    for _ in range(5):
        if d.tap_label(HOME, exact=True, required=False):
            return
        d.back()
    raise Skip("could not get back to the bottom bar's Home")


def open_chat(d: Device, chat: str) -> None:
    to_home(d)
    d.tap_label(CHATS, exact=True)
    d.tap_label(ALL_CHIP, exact=True, required=False)  # the chips keep the last filter
    n = d.scroll_to(chat, max_swipes=15)
    if n is None:
        raise Skip(f"chat {chat!r} not found in Chats")
    d.tap_node(n)
    d.wait_gone(*FINDING, timeout=30)
    time.sleep(1.5)


def close_search(d: Device) -> None:
    """Back out of search (keyboard first, then the overlay), never past the bottom bar."""
    for _ in range(3):
        if bar_visible(d) and not d.find(*SEARCH_HINT):
            return
        d.back()
        time.sleep(0.6)


def bar_visible(d: Device) -> bool:
    return d.find(HOME, exact=True) is not None and d.find(SEARCH, exact=True) is not None


def first_below(d: Device, heading: str) -> object:
    """The first tappable node under a section heading (a search result)."""
    root = d.dump()
    head = d.find(heading, exact=True, root=root) if root else None
    if root is None or head is None:
        return None
    y0 = head.bounds[3]
    cands = [n for n in root.walk() if n.clickable and n.bounds[1] >= y0 and n.bounds != (0, 0, 0, 0)
             and (n.bounds[3] - n.bounds[1]) > 60]
    cands.sort(key=lambda n: (n.bounds[1], n.bounds[0]))
    return cands[0] if cands else None


def selected_option(d: Device, options: list[str]) -> str | None:
    for n in d.find_all(*options, exact=True):
        if n.selected or n.checked:
            return n.label
    return None


# ---- flows ----------------------------------------------------------------------------------
def flow_launch(a) -> list:
    def start(d: Device):
        d.launch()
        if d.wait_for(HOME, timeout=25, exact=True) is None:
            return "Home tab not visible 25 s after launch (setup step, sign in, or no bottom bar?)"
        if d.find(OLD_SETUP_STEP):
            return f"the removed setup step {OLD_SETUP_STEP!r} still shows"

    def no_drawer(d: Device):
        if d.find(DRAWER):
            return f"a drawer button ({DRAWER!r}) is still on screen; the bottom bar should replace it"

    return [("launch", start), ("no_drawer", no_drawer)]


def flow_bottom_bar(a) -> list:
    def home(d: Device):
        d.tap_label(HOME, exact=True)

    def chats(d: Device):
        d.tap_label(CHATS, exact=True)
        if not d.wait_for(ALL_CHIP, timeout=8, exact=True):
            return "Chats opened without an All chip"

    def search(d: Device):
        d.tap_label(SEARCH, exact=True)
        if not d.wait_for(*SEARCH_HINT, timeout=6):
            return "Search opened without its field hint"

    def search_close(d: Device):
        close_search(d)  # the first Back may only drop the keyboard
        if not bar_visible(d):
            return "after closing search the bottom bar is not back"

    def profile(d: Device):
        d.tap_label(PROFILE)
        missing = [r for r in POPOVER if d.wait_for(r, timeout=4, exact=True) is None]
        if d.find(UPDATE, exact=True):
            d.log("popover shows Update (an update is waiting)")
        if missing:
            return f"profile popover is missing {missing}"

    def profile_close(d: Device):
        d.back()
        if d.find(POPOVER[-1], exact=True) and d.find(POPOVER[0], exact=True):
            return "Back did not close the profile popover"

    return [("home", home), ("chats", chats), ("search_open", search), ("search_close", search_close),
            ("profile_open", profile), ("profile_close", profile_close)]


def flow_profile_entries(a) -> list:
    steps = []
    for row in POPOVER:
        def go(d: Device, row=row):
            to_home(d)
            d.tap_label(PROFILE)
            d.tap_label(row, exact=True, timeout=4)
            time.sleep(1.5)
            if d.focused_package() != d.package:
                return f"{row} left the app ({d.focused_package()})"

        def back(d: Device, row=row):
            d.back()
            time.sleep(0.8)
            if not bar_visible(d):
                d.back()
                if not bar_visible(d):
                    return f"Back from {row} does not return to the bottom bar (dead end?)"
        steps += [(f"open_{row}", go), (f"back_from_{row}", back)]
    return steps


def flow_chat_chips(a) -> list:
    def chips(d: Device):
        to_home(d)
        d.tap_label(CHATS, exact=True)
        root = d.dump()
        allc = d.find(ALL_CHIP, exact=True, root=root)
        if allc is None:
            return "no All chip"
        row = [n for n in root.walk() if (n.text or n.desc) and abs(n.center[1] - allc.center[1]) < 40]
        row.sort(key=lambda n: n.bounds[0])
        names = []
        for n in row:
            if n.label not in names:
                names.append(n.label)
        d.log(f"chips: {names}")
        return f"chips seen (compare with Telegram's tabs by hand): {names}" if a.report_chips else None

    def swipe_chips(d: Device):
        allc = d.find(ALL_CHIP, exact=True)
        if allc is None:
            raise Skip("no All chip")
        w, _ = d.screen_size()
        y = allc.center[1]
        d.swipe(int(w * 0.8), y, int(w * 0.2), y)
        d.swipe(int(w * 0.2), y, int(w * 0.8), y)

    return [("chips", chips), ("chips_scroll", swipe_chips)]


def flow_search(a) -> list:
    q = a.query

    def open_(d: Device):
        to_home(d)
        d.tap_label(SEARCH, exact=True)
        if not d.wait_for(*SEARCH_HINT, timeout=6):
            return "no search field"
        if not d.find(*RECENT):
            d.log("no recent searches block (fine on a first run)")

    def type_(d: Device):
        field = d.find(*SEARCH_HINT)
        if field:
            d.tap_node(field)
        d.type_text(q)
        d.key("enter")

    def results(d: Device):
        d.wait_gone(*SEARCHING, timeout=40)
        time.sleep(1)
        notes = []
        if d.find(OFFLINE):
            notes.append("offline banner shown")
        if not d.find(VIDEOS_HEAD, exact=True):
            notes.append(f"no {VIDEOS_HEAD!r} heading for {q!r}")
        copies = d.find_all(regex=r"\b\d+ cop(y|ies)\b")
        d.log(f"result rows mentioning copies: {[n.label for n in copies][:6]}")
        return "; ".join(notes) or None

    def open_result(d: Device):
        n = first_below(d, VIDEOS_HEAD)
        if n is None:
            raise Skip("no video result to open")
        d.tap_node(n)
        time.sleep(2.5)

    def details_copy(d: Device):
        if not d.find(COPY_CHANGE, exact=True):
            return f"details page shows no '{COPY_FROM} <chat> · {COPY_CHANGE}' line (single copy, or missing)"
        frm = d.find_all(regex=r"^" + re.escape(COPY_FROM) + r"\s")
        d.log(f"copy line: {[n.label for n in frm][:2]}")

    def copies_list(d: Device):
        if not d.tap_label(COPY_CHANGE, exact=True, required=False):
            raise Skip("no Change to open the copies list")
        if not d.wait_for(COPIES_TITLE, timeout=5):
            return f"Change did not open {COPIES_TITLE!r}"

    def back_out(d: Device):
        d.back()
        time.sleep(0.6)
        d.back()
        time.sleep(0.8)
        if not (d.find(*SEARCH_HINT) or d.find(q)):
            return "Back from details did not return to the search results (query lost?)"

    def recent(d: Device):
        close_search(d)
        to_home(d)
        d.tap_label(SEARCH, exact=True)
        time.sleep(1)
        if not d.find(q):
            return f"{q!r} not in recent searches (or Remember searches is off)"

    def close(d: Device):
        close_search(d)

    return [("open", open_), ("type_query", type_), ("results", results), ("open_result", open_result),
            ("details_copy_line", details_copy), ("copies_list", copies_list), ("back_to_results", back_out),
            ("recent_searches", recent), ("close", close)]


def flow_search_filters(a) -> list:
    def open_(d: Device):
        to_home(d)
        d.tap_label(SEARCH, exact=True)
        field = d.wait_for(*SEARCH_HINT, timeout=6)
        if field:
            d.tap_node(field)
        d.type_text(a.query)
        d.key("enter")
        d.wait_gone(*SEARCHING, timeout=40)

    def filters(d: Device):
        if not d.tap_label(FILTERS, required=False):
            return f"no {FILTERS!r} control"
        time.sleep(1)

    def filters_close(d: Device):
        d.back()

    def sorts(d: Device):
        if not d.tap_label(S("search.sort", "Sort"), exact=True, required=False):
            return "no Sort control in search"
        missing = [s for s in SEARCH_SORTS if not d.find(s, exact=True)]
        d.back()
        if missing:
            return f"search sort menu lacks {missing}"

    def close(d: Device):
        close_search(d)

    return [("open", open_), ("filters", filters), ("filters_close", filters_close), ("sorts", sorts),
            ("close", close)]


def flow_chat_grid(a) -> list:
    state: dict = {}

    def open_(d: Device):
        open_chat(d, a.chat)

    def toggle_rows(d: Device):
        if d.tap_label(*AS_ROWS, required=False):
            state["toggled"] = "rows"
        elif d.tap_label(*AS_TILES, required=False):
            state["toggled"] = "tiles"
        else:
            return "no grid / rows toggle found (it may be in a menu)"
        time.sleep(1.2)

    def toggle_back(d: Device):
        t = state.get("toggled")
        if not t:
            raise Skip("nothing toggled")
        d.tap_label(*(AS_TILES if t == "rows" else AS_ROWS))
        time.sleep(1.2)

    def sort_menu(d: Device):
        if not d.tap_label(SORT, exact=True, required=False):
            return "no Sort control in the chat"
        missing = [s for s in CHAT_SORTS if d.wait_for(s, timeout=2, exact=True) is None]
        state["sort_was"] = selected_option(d, CHAT_SORTS)
        d.log(f"sort was {state['sort_was']}")
        if missing:
            return f"chat sort menu lacks {missing}"

    def sort_name(d: Device):
        if not d.find(CHAT_SORTS[2], exact=True):
            d.tap_label(SORT, exact=True)
        d.tap_label(CHAT_SORTS[2], exact=True)
        time.sleep(1.5)

    def sort_restore(d: Device):
        d.tap_label(SORT, exact=True)
        d.tap_label(state.get("sort_was") or CHAT_SORTS[0], exact=True, timeout=3)
        time.sleep(1)

    return [("open_chat", open_), ("toggle_layout", toggle_rows), ("toggle_back", toggle_back),
            ("sort_menu", sort_menu), ("sort_name", sort_name), ("sort_restore", sort_restore)]


def flow_back_to_top(a) -> list:
    def open_(d: Device):
        open_chat(d, a.chat)

    def far_down(d: Device):
        d.scroll("down", times=a.scrolls, fast=True)
        time.sleep(0.5)
        if d.find(BACK_TO_TOP):
            return "Back to top shows while scrolling down (should only come on scrolling up)"

    def up_a_bit(d: Device):
        d.scroll("up", times=1)
        if d.wait_for(BACK_TO_TOP, timeout=3) is None:
            return "Back to top did not appear after scrolling up past a screenful"

    def tap_it(d: Device):
        if not d.tap_label(BACK_TO_TOP, required=False):
            raise Skip("no Back to top to tap")
        time.sleep(2)
        if d.find(BACK_TO_TOP):
            return "Back to top is still shown at the top"

    def idle_hides(d: Device):
        d.scroll("down", times=4, fast=True)
        d.scroll("up", times=1)
        if d.wait_for(BACK_TO_TOP, timeout=3) is None:
            raise Skip("button did not come up this time")
        if not d.wait_gone(BACK_TO_TOP, timeout=10):
            return "Back to top did not go away after idling"

    return [("open_chat", open_), ("scroll_far_down", far_down), ("scroll_up", up_a_bit),
            ("tap_back_to_top", tap_it), ("idle_hides", idle_hides)]


def flow_offline(a) -> list:
    """Emulator only: airplane mode on, search shows the offline note, airplane mode off."""
    def on(d: Device):
        if not d.is_emulator:
            raise Skip("offline flow only runs on an emulator")
        d.shell("cmd", "connectivity", "airplane-mode", "enable")
        time.sleep(3)

    def search(d: Device):
        if not d.is_emulator:
            raise Skip("emulator only")
        to_home(d)
        d.tap_label(SEARCH, exact=True)
        field = d.wait_for(*SEARCH_HINT, timeout=6)
        if field:
            d.tap_node(field)
        d.type_text(a.query)
        d.key("enter")
        time.sleep(4)
        if not d.find(OFFLINE):
            return "no offline note in search while in airplane mode"

    def off(d: Device):
        if not d.is_emulator:
            raise Skip("emulator only")
        d.shell("cmd", "connectivity", "airplane-mode", "disable")
        time.sleep(4)
        close_search(d)

    return [("airplane_on", on), ("search_offline", search), ("airplane_off", off)]


FLOWS = {
    "launch": flow_launch, "bottom_bar": flow_bottom_bar, "profile_entries": flow_profile_entries,
    "chat_chips": flow_chat_chips, "search": flow_search, "search_filters": flow_search_filters,
    "chat_grid": flow_chat_grid, "back_to_top": flow_back_to_top, "offline": flow_offline,
}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", required=True, help="adb serial, e.g. emulator-5554 or d3f7da23")
    ap.add_argument("--out", required=True, help="folder for screenshots and REPORT.md")
    ap.add_argument("--package", default="com.tmplayer")
    ap.add_argument("--query", default="love under", help="search text that hits a video posted several times")
    ap.add_argument("--chat", default="Cinema_paradasio", help="a chat with many videos (for scrolling and sort)")
    ap.add_argument("--orientations", default="portrait,landscape")
    ap.add_argument("--only", default="", help=f"comma list of flows; default all of {list(FLOWS)}")
    ap.add_argument("--skip", default="", help="comma list of flows to leave out")
    ap.add_argument("--scrolls", type=int, default=10, help="fast swipes for the back to top check")
    ap.add_argument("--report-chips", action="store_true", help="list the folder chips as a finding")
    ap.add_argument("--real-device", action="store_true",
                    help="required for a non-emulator serial: the user shares the phone, so confirm it is free")
    ap.add_argument("--no-guard", action="store_true", help="do not check the app is in front before inputs")
    a = ap.parse_args()

    if not a.serial.startswith("emulator-") and not a.real_device:
        print(f"{a.serial} is a real device. Ask the user first, then pass --real-device.", file=sys.stderr)
        return 2
    names = [n for n in (a.only.split(",") if a.only else FLOWS) if n and n not in a.skip.split(",")]
    unknown = [n for n in names if n not in FLOWS]
    if unknown:
        print(f"unknown flows {unknown}; known: {list(FLOWS)}", file=sys.stderr)
        return 2

    base = Path(a.out)
    total_findings = 0
    dev = None
    try:
        for orient in [o for o in a.orientations.split(",") if o]:
            dev = Device(a.serial, base / orient, a.package, guard=not a.no_guard)
            dev.log(f"{a.package} {dev.app_version()}, orientation {orient}")
            dev.rotate(orient)
            if "launch" not in names:
                dev.launch()
            summaries = [run_flow(dev, n, FLOWS[n](a)) for n in names]
            write_report(dev, summaries, f"Phone pass, {orient}")
            total_findings += len(dev.findings)
    finally:
        if dev is not None:
            dev.restore_rotation()
            if dev.is_emulator and "offline" in names:
                dev.shell("cmd", "connectivity", "airplane-mode", "disable")
    print(f"done: {total_findings} findings, reports under {base}/<orientation>/REPORT.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
