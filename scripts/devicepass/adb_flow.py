#!/usr/bin/env python3
"""Small adb driver for TMPlayer device passes. Standard library only; run with `python3 -I`.

Every adb call is an argv list handed to subprocess, never a shell string: zsh does not word-split
`$A`, so `A="adb -s X shell"; $A input tap ...` silently runs nothing (memory note, 2026-10-13).
Where the device side needs a shell (a parallel double tap), the remote command is one quoted
argument.

Safety:
- Before every input event the focused window is checked to belong to the app package. On a
  shared device a scripted key press once hit the launcher and played videos on the family TV, so
  an input with someone else's app in front raises Skip instead of being sent.
- `tap_label` refuses labels that delete, clear, remove or sign out unless allow_destructive=True.
- Rotation helpers always end with `wm user-rotation free` and accelerometer_rotation 1, which is
  how the user keeps the phone.
- Nothing here installs, uninstalls, clears app data or wipes logcat.

Typical use:

    from adb_flow import Device, run_flow
    dev = Device("emulator-5554", out_dir="out/phone")
    dev.launch()
    run_flow(dev, "bottom_bar", [("home", lambda d: d.tap_label("Home")), ...])
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable, Sequence

APP_PACKAGE = "com.tmplayer"

# Labels that change or delete user data. tap_label refuses them unless told otherwise.
DESTRUCTIVE = re.compile(
    r"\b(delete|clear|remove|sign out|log ?out|forget|uninstall|reset|archive|mute|"
    r"not the same video|wrong match|rename|mark (as )?(un)?watched)\b",
    re.I,
)

KEY = {
    "back": 4, "home": 3, "up": 19, "down": 20, "left": 21, "right": 22, "center": 23,
    "enter": 66, "menu": 82, "search": 84, "del": 67, "tab": 61, "escape": 111,
    "move_home": 122, "move_end": 123, "media_play_pause": 85,
}


class Skip(Exception):
    """A step could not run (element missing, wrong app in front). Reported as a finding."""


@dataclass
class Node:
    text: str
    desc: str
    rid: str
    cls: str
    package: str
    bounds: tuple[int, int, int, int]
    clickable: bool
    focusable: bool
    focused: bool
    selected: bool
    checked: bool
    enabled: bool
    scrollable: bool
    children: list["Node"] = field(default_factory=list, repr=False)

    @property
    def center(self) -> tuple[int, int]:
        l, t, r, b = self.bounds
        return (l + r) // 2, (t + b) // 2

    @property
    def label(self) -> str:
        return self.text or self.desc

    def all_labels(self) -> list[str]:
        out = [x for x in (self.text, self.desc) if x]
        for c in self.children:
            out.extend(c.all_labels())
        return out

    def walk(self) -> Iterable["Node"]:
        yield self
        for c in self.children:
            yield from c.walk()


_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def _parse(el: ET.Element) -> Node:
    m = _BOUNDS.match(el.get("bounds", "")) or None
    b = tuple(int(x) for x in m.groups()) if m else (0, 0, 0, 0)
    t = lambda k: el.get(k) == "true"  # noqa: E731
    node = Node(
        text=el.get("text", ""), desc=el.get("content-desc", ""), rid=el.get("resource-id", ""),
        cls=el.get("class", ""), package=el.get("package", ""), bounds=b,  # type: ignore[arg-type]
        clickable=t("clickable") or t("long-clickable"), focusable=t("focusable"),
        focused=t("focused"), selected=t("selected"), checked=t("checked"),
        enabled=el.get("enabled", "true") == "true", scrollable=t("scrollable"),
    )
    node.children = [_parse(c) for c in el if c.tag == "node"]
    return node


def _matches(value: str, want: str, exact: bool) -> bool:
    if not value:
        return False
    if exact:
        return value.strip().casefold() == want.strip().casefold()
    return want.casefold() in value.casefold()


class Device:
    def __init__(self, serial: str, out_dir: str | Path = "devicepass-out", package: str = APP_PACKAGE,
                 adb: str = "adb", guard: bool = True, verbose: bool = True):
        self.serial = serial
        self.out = Path(out_dir)
        self.out.mkdir(parents=True, exist_ok=True)
        self.package = package
        self.adb = adb
        self.guard = guard
        self.verbose = verbose
        self.findings: list[str] = []
        self._log_since: str | None = None
        self._size: tuple[int, int] | None = None
        self._check_attached()

    # ---- plumbing -------------------------------------------------------------------------
    def log(self, msg: str) -> None:
        if self.verbose:
            print(f"[{self.serial}] {msg}", flush=True)

    def finding(self, msg: str) -> None:
        self.log(f"FINDING: {msg}")
        self.findings.append(msg)

    def run(self, *args: str, timeout: float = 30, check: bool = False, binary: bool = False):
        argv = [self.adb, "-s", self.serial, *args]
        p = subprocess.run(argv, capture_output=True, timeout=timeout, text=not binary)
        if check and p.returncode != 0:
            raise RuntimeError(f"{argv} failed ({p.returncode}): {p.stderr!r}")
        return p

    def shell(self, *args: str, timeout: float = 30, check: bool = False) -> str:
        return self.run("shell", *args, timeout=timeout, check=check).stdout

    def _check_attached(self) -> None:
        p = subprocess.run([self.adb, "devices"], capture_output=True, text=True, timeout=15)
        states = dict(l.split("\t", 1) for l in p.stdout.splitlines()[1:] if "\t" in l)
        if states.get(self.serial) != "device":
            raise SystemExit(f"{self.serial} is not attached as a device ({states.get(self.serial)!r}); "
                             f"adb devices says: {states}")

    @property
    def is_emulator(self) -> bool:
        return self.serial.startswith("emulator-")

    def screen_size(self) -> tuple[int, int]:
        out = self.shell("wm", "size")
        m = re.findall(r"(\d+)x(\d+)", out)
        w, h = (int(x) for x in m[-1]) if m else (1080, 1920)
        rot = self.rotation()
        self._size = (h, w) if rot in (1, 3) and h > w else (w, h)
        return self._size

    def rotation(self) -> int:
        out = self.shell("dumpsys", "input")
        m = re.search(r"SurfaceOrientation:\s*(\d)", out) or re.search(r"orientation=(\d)", out)
        return int(m.group(1)) if m else 0

    # ---- app state ------------------------------------------------------------------------
    def focused_package(self) -> str:
        out = self.shell("dumpsys", "window")
        for line in out.splitlines():
            if "mCurrentFocus" in line or "mFocusedApp" in line:
                m = re.search(r"\s([\w.]+)/", line)
                if m:
                    return m.group(1)
        return ""

    def ensure_foreground(self) -> None:
        if not self.guard:
            return
        pkg = self.focused_package()
        if pkg != self.package:
            raise Skip(f"focused window is {pkg or 'unknown'}, not {self.package}; input not sent")

    def launch(self, tv: bool = False, wait: float = 4) -> None:
        cat = "android.intent.category.LEANBACK_LAUNCHER" if tv else "android.intent.category.LAUNCHER"
        out = self.shell("cmd", "package", "resolve-activity", "--brief", "-c", cat, self.package).strip()
        comp = out.splitlines()[-1] if "/" in out else ""
        if not comp:
            raise SystemExit(f"{self.package} has no {cat} activity on {self.serial}; is it installed?")
        self.mark_log()
        self.log(f"launch {comp}")
        self.shell("am", "start", "-W", "-n", comp, timeout=60)
        time.sleep(wait)

    def app_version(self) -> str:
        out = self.shell("dumpsys", "package", self.package)
        vn = re.search(r"versionName=(\S+)", out)
        vc = re.search(r"versionCode=(\d+)", out)
        return f"{vn.group(1) if vn else '?'} ({vc.group(1) if vc else '?'})"

    # ---- screenshots and hierarchy --------------------------------------------------------
    def screenshot(self, name: str, folder: Path | None = None) -> Path:
        folder = folder or self.out
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / f"{re.sub(r'[^A-Za-z0-9_.-]+', '_', name)}.png"
        p = self.run("exec-out", "screencap", "-p", binary=True, timeout=30)
        if p.returncode == 0 and p.stdout.startswith(b"\x89PNG"):
            path.write_bytes(p.stdout)
        else:
            self.finding(f"screenshot {name} failed")
        return path

    def dump(self) -> Node | None:
        """The current hierarchy, or None (uiautomator returns nothing while a video plays)."""
        remote = "/data/local/tmp/tmplayer_devicepass_ui.xml"
        for _ in range(2):
            self.shell("uiautomator", "dump", "--compressed", remote, timeout=30)
            xml = self.run("exec-out", "cat", remote, timeout=15).stdout
            if xml and "<hierarchy" in xml:
                try:
                    root = ET.fromstring(xml[xml.index("<hierarchy"):])
                except ET.ParseError:
                    continue
                kids = [_parse(c) for c in root if c.tag == "node"]
                top = Node("", "", "", "hierarchy", "", (0, 0, 0, 0), False, False, False, False,
                           False, True, False, kids)
                return top
            time.sleep(0.5)
        return None

    def labels(self) -> list[str]:
        root = self.dump()
        return [l for l in root.all_labels()] if root else []

    def find_all(self, *labels: str, exact: bool = False, root: Node | None = None,
                 regex: str | None = None) -> list[Node]:
        root = root or self.dump()
        if root is None:
            return []
        rx = re.compile(regex, re.I) if regex else None
        out = []
        for n in root.walk():
            if n.bounds == (0, 0, 0, 0):
                continue
            if rx and (rx.search(n.text or "") or rx.search(n.desc or "")):
                out.append(n)
                continue
            for want in labels:
                if _matches(n.text, want, exact) or _matches(n.desc, want, exact):
                    out.append(n)
                    break
        return out

    def find(self, *labels: str, exact: bool = False, root: Node | None = None,
             regex: str | None = None) -> Node | None:
        """First node whose text or content-desc matches any of labels (exact, then contains)."""
        root = root or self.dump()
        if root is None:
            return None
        if not exact and labels:
            hit = self.find_all(*labels, exact=True, root=root)
            if hit:
                return hit[0]
        hit = self.find_all(*labels, exact=exact, root=root, regex=regex)
        return hit[0] if hit else None

    def has(self, *labels: str, exact: bool = False) -> bool:
        return self.find(*labels, exact=exact) is not None

    def wait_for(self, *labels: str, timeout: float = 10, exact: bool = False) -> Node | None:
        """Wait until any label shows; the node, or None after timeout."""
        end = time.monotonic() + timeout
        while True:
            n = self.find(*labels, exact=exact)
            if n is not None or time.monotonic() > end:
                return n
            time.sleep(0.7)

    def wait_gone(self, *labels: str, timeout: float = 10, exact: bool = False) -> bool:
        """Wait until none of the labels shows; True if they went."""
        end = time.monotonic() + timeout
        while self.find(*labels, exact=exact) is not None:
            if time.monotonic() > end:
                return False
            time.sleep(0.7)
        return True

    # ---- input ----------------------------------------------------------------------------
    def tap(self, x: int, y: int) -> None:
        self.ensure_foreground()
        self.shell("input", "tap", str(x), str(y))
        time.sleep(0.6)

    def double_tap(self, x: int, y: int) -> None:
        self.ensure_foreground()
        # sendevent is blocked by SELinux; two taps in parallel 150 ms apart, in one remote shell.
        self.shell(f"input tap {x} {y} & (sleep 0.15; input tap {x} {y}) & wait")
        time.sleep(0.6)

    def long_press(self, x: int, y: int, ms: int = 1500) -> None:
        self.ensure_foreground()
        self.shell("input", "swipe", str(x), str(y), str(x), str(y), str(ms))
        time.sleep(0.6)

    def tap_node(self, n: Node, long: bool = False) -> None:
        x, y = n.center
        self.log(f"{'hold' if long else 'tap'} {n.label!r} at {x},{y}")
        (self.long_press if long else self.tap)(x, y)

    def tap_label(self, *labels: str, exact: bool = False, long: bool = False, required: bool = True,
                  allow_destructive: bool = False, timeout: float = 0) -> bool:
        """Tap the first node matching any label. Missing: Skip if required, else False."""
        for want in labels:
            if DESTRUCTIVE.search(want) and not allow_destructive:
                raise Skip(f"refusing to tap destructive label {want!r} without allow_destructive")
        n = self.wait_for(*labels, timeout=timeout, exact=exact) if timeout else self.find(*labels, exact=exact)
        if n is None:
            if required:
                raise Skip(f"no element labelled {' / '.join(labels)!r}")
            return False
        if DESTRUCTIVE.search(n.label) and not allow_destructive:
            raise Skip(f"matched destructive element {n.label!r}; not tapping")
        self.tap_node(n, long=long)
        return True

    def swipe(self, x1: int, y1: int, x2: int, y2: int, ms: int = 300) -> None:
        self.ensure_foreground()
        self.shell("input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
        time.sleep(0.5)

    def scroll(self, direction: str = "down", times: int = 1, fast: bool = False,
               region: tuple[int, int, int, int] | None = None) -> None:
        """Swipe the content. direction is where the content moves you: down = further down the list."""
        w, h = self.screen_size()
        l, t, r, b = region or (0, int(h * 0.22), w, int(h * 0.80))
        cx, cy = (l + r) // 2, (t + b) // 2
        span_y, span_x = int((b - t) * 0.35), int((r - l) * 0.35)
        ms = 120 if fast else 350
        for _ in range(times):
            if direction == "down":
                self.swipe(cx, cy + span_y, cx, cy - span_y, ms)
            elif direction == "up":
                self.swipe(cx, cy - span_y, cx, cy + span_y, ms)
            elif direction == "left":
                self.swipe(cx + span_x, cy, cx - span_x, cy, ms)
            elif direction == "right":
                self.swipe(cx - span_x, cy, cx + span_x, cy, ms)

    def scroll_to(self, *labels: str, max_swipes: int = 12, direction: str = "down",
                  exact: bool = False) -> Node | None:
        for i in range(max_swipes + 1):
            n = self.find(*labels, exact=exact)
            if n:
                return n
            if i < max_swipes:
                self.scroll(direction)
        return None

    def key(self, name_or_code: str | int, times: int = 1, pause: float = 0.35) -> None:
        self.ensure_foreground()
        code = KEY.get(str(name_or_code), name_or_code)
        for _ in range(times):
            self.shell("input", "keyevent", str(code))
            time.sleep(pause)

    def back(self, times: int = 1) -> None:
        self.key("back", times)

    def home(self) -> None:
        # Home leaves the app, so it skips the foreground guard on purpose.
        self.shell("input", "keyevent", "3")
        time.sleep(0.8)

    def type_text(self, text: str, clear_first: bool = False) -> None:
        """Type into the focused field. `input text` takes %s for a space; the rest is quoted."""
        self.ensure_foreground()
        if clear_first:
            self.key("move_end")
            self.shell("input", "keycombination", "113", "29")  # Ctrl+A
            self.key("del")
        escaped = text.replace("%", "\\%").replace(" ", "%s")
        quoted = "'" + escaped.replace("'", "'\\''") + "'"
        self.shell("input", "text", quoted)
        time.sleep(0.8)

    # ---- rotation -------------------------------------------------------------------------
    def rotate(self, orientation: str | int) -> None:
        """portrait (0), landscape (1), reverse-landscape (3), or free (follow the sensor)."""
        names = {"portrait": 0, "landscape": 1, "reverse-landscape": 3, "seascape": 3}
        if orientation == "free":
            self.restore_rotation()
            return
        r = names.get(str(orientation), orientation)
        self.shell("wm", "user-rotation", "lock", str(r))
        time.sleep(2.0)
        self._size = None

    def restore_rotation(self) -> None:
        self.shell("wm", "user-rotation", "free")
        self.shell("settings", "put", "system", "accelerometer_rotation", "1")
        self._size = None

    # ---- logcat ---------------------------------------------------------------------------
    def mark_log(self) -> None:
        """Remember the device time, so crashes() only reads what happened after it."""
        # adb joins argv with spaces for the device shell, so a value with a space is quoted there.
        out = self.shell("date", "'+%m-%d %H:%M:%S.000'").strip()
        self._log_since = out or None

    def crashes(self, since_mark: bool = True) -> list[str]:
        """FATAL EXCEPTION / ANR lines for the app since the mark (logcat is never cleared)."""
        args = ["logcat", "-d", "-b", "main,system,crash", "-v", "threadtime"]
        if since_mark and self._log_since:
            args += ["-T", f"'{self._log_since}'"]
        out = self.shell(*args, timeout=60)
        hits, grab = [], 0
        pkg = re.escape(self.package)
        for line in out.splitlines():
            if re.search(r"FATAL EXCEPTION|ANR in " + pkg + r"\b|Process: " + pkg + r"\b|"
                         r"am_crash.*" + pkg + r"|am_anr.*" + pkg, line):
                hits.append(line)
                grab = 12
            elif grab:
                hits.append(line)
                grab -= 1
        # keep only FATAL blocks that name this package
        if hits and not any(re.search(pkg + r"\b", h) for h in hits):
            return []
        return hits

    def grep_log(self, pattern: str, since_mark: bool = True) -> list[str]:
        args = ["logcat", "-d", "-v", "threadtime"]
        if since_mark and self._log_since:
            args += ["-T", f"'{self._log_since}'"]
        rx = re.compile(pattern)
        return [l for l in self.shell(*args, timeout=60).splitlines() if rx.search(l)]


Step = tuple[str, Callable[[Device], object]]


def run_flow(dev: Device, name: str, steps: Sequence[Step], shot_each: bool = True,
             stop_on_crash: bool = True) -> dict:
    """Run steps in order, a screenshot after each into <out>/<name>/NN-step.png.

    A step that raises Skip, or returns a string, becomes a finding; other exceptions are caught
    and reported too, so one missing element never ends the pass. Returns a summary dict, also
    written to <out>/<name>/report.json.
    """
    folder = dev.out / name
    folder.mkdir(parents=True, exist_ok=True)
    dev.mark_log()
    results = []
    dev.log(f"=== flow {name}")
    for i, (label, fn) in enumerate(steps, 1):
        rec: dict = {"step": label, "status": "ok"}
        try:
            res = fn(dev)
            if isinstance(res, str) and res:
                rec.update(status="finding", note=res)
                dev.finding(f"{name}/{label}: {res}")
        except Skip as e:
            rec.update(status="skipped", note=str(e))
            dev.finding(f"{name}/{label}: skipped, {e}")
        except subprocess.TimeoutExpired as e:
            rec.update(status="error", note=f"adb timed out: {e}")
            dev.finding(f"{name}/{label}: adb timed out")
        except Exception as e:  # noqa: BLE001 - the pass must go on
            rec.update(status="error", note=f"{type(e).__name__}: {e}")
            dev.finding(f"{name}/{label}: {type(e).__name__}: {e}")
        if shot_each:
            rec["screenshot"] = str(dev.screenshot(f"{i:02d}-{label}", folder))
        crash = dev.crashes()
        if crash:
            rec.update(status="crash", crash=crash[:40])
            dev.finding(f"{name}/{label}: CRASH or ANR\n    " + "\n    ".join(crash[:8]))
            (folder / f"{i:02d}-{label}-crash.txt").write_text("\n".join(crash))
            results.append(rec)
            if stop_on_crash:
                break
            dev.mark_log()
        else:
            results.append(rec)
        dev.log(f"  {i:02d} {label}: {rec['status']}{' - ' + rec['note'] if rec.get('note') else ''}")
    summary = {"flow": name, "serial": dev.serial, "steps": results,
               "ok": all(r["status"] == "ok" for r in results)}
    (folder / "report.json").write_text(json.dumps(summary, indent=2))
    return summary


def write_report(dev: Device, summaries: list[dict], title: str) -> Path:
    """One Markdown summary of every flow, beside the screenshots."""
    lines = [f"# {title}", "", f"Device {dev.serial}, {dev.package} {dev.app_version()}", ""]
    for s in summaries:
        lines.append(f"## {s['flow']} ({'ok' if s['ok'] else 'see findings'})")
        for r in s["steps"]:
            note = f": {r['note']}" if r.get("note") else ""
            lines.append(f"- {r['step']} [{r['status']}]{note}")
        lines.append("")
    lines += ["## Findings", ""] + ([f"- {f}" for f in dev.findings] or ["- none"])
    path = dev.out / "REPORT.md"
    path.write_text("\n".join(lines) + "\n")
    dev.log(f"report: {path}")
    return path


def load_strings(repo: Path | None = None) -> dict[str, str]:
    """Flattened en.json ('browse.tab_home' -> 'Home'), so flows use the app's exact words."""
    repo = repo or Path(__file__).resolve().parents[2]
    path = repo / "core/src/commonMain/resources/i18n/en.json"
    flat: dict[str, str] = {}

    def walk(o, p=""):
        if isinstance(o, dict):
            for k, v in o.items():
                walk(v, f"{p}.{k}" if p else k)
        else:
            flat[p] = str(o)
    try:
        walk(json.loads(path.read_text(encoding="utf-8")))
    except OSError:
        print(f"warning: {path} not readable, falling back to built-in labels", file=sys.stderr)
    return flat


class Strings:
    """en.json lookup with a fallback, for strings another agent may not have merged yet."""

    def __init__(self, repo: Path | None = None):
        self.flat = load_strings(repo)

    def __call__(self, key: str, fallback: str = "") -> str:
        v = self.flat.get(key, fallback)
        # A label with a placeholder is matched on its text before the first {.
        return v.split("{", 1)[0].strip(" “\"") if "{" in v else v


if __name__ == "__main__":
    import argparse

    ap = argparse.ArgumentParser(description="Quick adb_flow smoke: version, focus, labels, a screenshot.")
    ap.add_argument("--serial", required=True)
    ap.add_argument("--out", default="devicepass-out/smoke")
    ap.add_argument("--package", default=APP_PACKAGE)
    a = ap.parse_args()
    d = Device(a.serial, a.out, a.package)
    print("version:", d.app_version())
    print("focused:", d.focused_package())
    print("size:", d.screen_size(), "rotation:", d.rotation())
    print("labels:", d.labels()[:60])
    print("shot:", d.screenshot("smoke"))
    print("crashes in the last minutes:", d.crashes(since_mark=False)[-5:])
