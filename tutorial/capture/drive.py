"""Drive an Android emulator for tutorial capture.

Coordinates are screen pixels of the 1080x2340 override display. Touches go
through Inject.java (run with app_process) for exact timing, so double taps,
holds, drags and pinches behave like a real finger. Build it with build-inject.sh.
"""
import math
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from contextlib import contextmanager
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
W, H = 1080, 2340
REMOTE = "/data/local/tmp"
FRAME_MS = 12


def _raw(x, y):
    return round(x, 1), round(y, 1)


class Device:
    def __init__(self, serial):
        self.serial = serial
        self._recorder = None
        self.adb("push", str(RAW / "inject.jar"), f"{REMOTE}/inject.jar", quiet=True)

    # --- plumbing -------------------------------------------------------
    def adb(self, *args, quiet=False, check=True):
        r = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, text=True)
        if check and r.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip() or r.stdout.strip()}")
        return r.stdout

    def sh(self, cmd, check=True):
        return self.adb("shell", cmd, check=check)

    def _run(self, ops):
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
            f.write("\n".join(ops) + "\n")
        self.adb("push", f.name, f"{REMOTE}/g.txt", quiet=True)
        Path(f.name).unlink()
        self.sh(f"CLASSPATH={REMOTE}/inject.jar app_process / Inject {REMOTE}/g.txt")

    # --- gestures -------------------------------------------------------
    @staticmethod
    def _path(slot, x1, y1, x2, y2, ms, ease=True):
        steps = max(1, ms // FRAME_MS)
        ops = []
        for i in range(1, steps + 1):
            t = i / steps
            if ease:
                t = t * t * (3 - 2 * t)
            ops += [f"m {slot} {' '.join(map(str, _raw(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t)))}", "s",
                    f"w {FRAME_MS}"]
        return ops

    def tap(self, x, y, ms=60):
        self._run([f"d 0 {' '.join(map(str, _raw(x, y)))}", "s", f"w {ms}", "u 0", "s"])

    def double_tap(self, x, y, gap=110):
        p = " ".join(map(str, _raw(x, y)))
        self._run([f"d 0 {p}", "s", "w 50", "u 0", "s", f"w {gap}", f"d 0 {p}", "s", "w 50", "u 0", "s"])

    def hold(self, x, y, ms=1200):
        self._run([f"d 0 {' '.join(map(str, _raw(x, y)))}", "s", f"w {ms}", "u 0", "s"])

    def drag(self, x1, y1, x2, y2, ms=700, press=60, release=120):
        """Finger down, rest briefly, move, rest, lift (a deliberate drag, not a fling)."""
        self.path([(x1, y1), (x2, y2)], ms=ms, press=press, release=release)

    def path(self, points, ms=700, press=60, release=120):
        ops = [f"d 0 {' '.join(map(str, _raw(*points[0])))}", "s", f"w {press}"]
        legs = list(zip(points, points[1:]))
        total = sum(math.dist(a, b) for a, b in legs) or 1
        for a, b in legs:
            ops += self._path(0, *a, *b, max(FRAME_MS, round(ms * math.dist(a, b) / total)), ease=len(legs) == 1)
        ops += [f"w {release}", "u 0", "s"]
        self._run(ops)

    def double_tap_drag(self, x1, y1, x2, y2, ms=700):
        p = " ".join(map(str, _raw(x1, y1)))
        ops = [f"d 0 {p}", "s", "w 50", "u 0", "s", "w 110", f"d 0 {p}", "s", "w 120"]
        ops += self._path(0, x1, y1, x2, y2, ms) + ["w 80", "u 0", "s"]
        self._run(ops)

    def two_finger(self, a1, a2, b1, b2, ms=800):
        """Two fingers from a1/b1 to a2/b2 (pinch when they converge/diverge, pan when parallel)."""
        ops = [f"d 0 {' '.join(map(str, _raw(*a1)))}", f"d 1 {' '.join(map(str, _raw(*b1)))}", "s", "w 80"]
        steps = max(1, ms // FRAME_MS)
        for i in range(1, steps + 1):
            t = i / steps
            t = t * t * (3 - 2 * t)
            for slot, (p, q) in enumerate(((a1, a2), (b1, b2))):
                ops.append(f"m {slot} {' '.join(map(str, _raw(p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)))}")
            ops += ["s", f"w {FRAME_MS}"]
        ops += ["w 80", "u 0", "u 1", "s"]
        self._run(ops)

    def pinch(self, cx, cy, d1, d2, ms=800, angle=60):
        dx, dy = math.cos(math.radians(angle)) / 2, math.sin(math.radians(angle)) / 2
        self.two_finger((cx - dx * d1, cy - dy * d1), (cx - dx * d2, cy - dy * d2),
                        (cx + dx * d1, cy + dy * d1), (cx + dx * d2, cy + dy * d2), ms)

    def text(self, s):
        self.sh("input text " + "'" + s.replace("'", r"'\''").replace(" ", "%s") + "'")

    def key(self, code):
        self.sh(f"input keyevent {code}")

    def back(self):
        self.key("KEYCODE_BACK")

    # --- screen ---------------------------------------------------------
    def screencap(self, path):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with open(path, "wb") as f:
            subprocess.run(["adb", "-s", self.serial, "exec-out", "screencap", "-p"], stdout=f, check=True)
        return path

    def look(self, path, width=540):
        """Screenshot plus a downscaled copy for quick viewing; returns the small copy."""
        self.screencap(path)
        small = str(Path(path).with_suffix(".small.png"))
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", path, "-vf", f"scale={width}:-1", small], check=True)
        return small

    def record_start(self, name):
        """Start screenrecord detached on the device (survives this Python process; max 180 s)."""
        self.sh("pkill -INT screenrecord; rm -f /sdcard/rec.mp4", check=False)
        self.sh(f"echo {name} > /sdcard/rec.name; nohup screenrecord --bit-rate 20000000 /sdcard/rec.mp4 "
                ">/dev/null 2>&1 &")
        time.sleep(1.2)

    def record_stop(self, out_dir):
        time.sleep(0.6)
        self.sh("pkill -INT screenrecord", check=False)
        for _ in range(40):
            if not self.sh("pidof screenrecord", check=False).strip():
                break
            time.sleep(0.25)
        name = self.sh("cat /sdcard/rec.name").strip()
        out = Path(out_dir) / f"{name}.mp4"
        out.parent.mkdir(parents=True, exist_ok=True)
        self.adb("pull", "/sdcard/rec.mp4", str(out))
        return out

    @contextmanager
    def recording(self, name, out_dir):
        self.record_start(name)
        try:
            yield
        finally:
            self.record_stop(out_dir)

    # --- UI tree --------------------------------------------------------
    def nodes(self):
        self.sh("uiautomator dump /sdcard/ui.xml >/dev/null", check=False)
        xml = self.sh("cat /sdcard/ui.xml", check=False)
        try:
            root = ET.fromstring(xml[xml.index("<?xml"):] if "<?xml" in xml else xml)
        except (ET.ParseError, ValueError):
            return []
        out = []
        for n in root.iter("node"):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                out.append({"text": n.get("text", ""), "desc": n.get("content-desc", ""), "bounds": (x1, y1, x2, y2),
                            "center": ((x1 + x2) // 2, (y1 + y2) // 2), "clickable": n.get("clickable") == "true"})
        return out

    def find(self, label, exact=False, timeout=8.0):
        deadline = time.time() + timeout
        while True:
            for n in self.nodes():
                for value in (n["text"], n["desc"]):
                    if value and (value == label if exact else label.lower() in value.lower()):
                        return n
            if time.time() > deadline:
                raise LookupError(f"{self.serial}: no node matching {label!r}")
            time.sleep(0.5)

    def tap_label(self, label, exact=False, timeout=8.0):
        n = self.find(label, exact, timeout)
        self.tap(*n["center"])
        return n
