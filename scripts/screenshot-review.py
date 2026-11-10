"""Writes data.js for scripts/screenshot-review.html from `git diff --name-status` lines on stdin
(run by scripts/review.sh screenshots from the repository root). Needs numpy and ImageMagick.

Each changed screenshot gets boxes around the areas whose pixels changed. A re-recorded screenshot whose
pixels are unchanged is left out. A change already shown in an earlier screenshot (same pixels before and
after) is marked as seen, so screenshots holding only seen changes can be skipped."""
import hashlib, json, os, struct, subprocess, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "lib"))
from shotdiff import boxes, pixels

tag, shots, out = sys.argv[1:]

def git(*args):
    r = subprocess.run(["git", *args], capture_output=True)
    return r.stdout if r.returncode == 0 else None

items = {}
for line in sys.stdin:
    status, *paths = line.rstrip("\n").split("\t")
    old, new = (paths[0], paths[-1])
    if not new.endswith((".png", ".txt")):
        continue
    stem, old_stem = new[:-4], old[:-4]
    if new.endswith(".txt") and status[0] != "M":
        continue  # a caption added to or dropped from an unchanged image is not a change to review
    items.setdefault(stem, {"old": old_stem})
    if new.endswith(".png"):
        items[stem]["old"] = old_stem

result, shown, unchanged = [], set(), 0
for stem, item in sorted(items.items()):
    name = stem[len(shots) + 1:] + ".png"
    before_png = git("show", f"{tag}:{item['old']}.png")
    after_png = open(f"{stem}.png", "rb").read() if os.path.exists(f"{stem}.png") else None
    caption = open(f"{stem}.txt", encoding="utf-8").read().strip() if os.path.exists(f"{stem}.txt") else None
    old_caption = git("show", f"{tag}:{item['old']}.txt")
    old_caption = old_caption.decode("utf-8").strip() if old_caption else None
    status = "A" if before_png is None else "D" if after_png is None else "M"
    new_caption = old_caption is not None and caption != old_caption  # a first caption is no change
    regions, size = [], None
    if status == "M":
        a, b = pixels(before_png), pixels(after_png)
        size = [b.shape[1], b.shape[0]]
        if a.shape != b.shape:
            regions = [{"box": [0, 0, *size], "seen": False}]
        else:
            mask = (a != b).any(axis=2)
            if not mask.any() and not new_caption:
                unchanged += 1
                continue  # re-recorded with the same pixels
            for x, y, w, h in boxes(mask):
                key = hashlib.sha1(a[y:y + h, x:x + w].tobytes() + b[y:y + h, x:x + w].tobytes() + bytes(f"{w}x{h}", "ascii")).digest()
                regions.append({"box": [x, y, w, h], "seen": key in shown})
                shown.add(key)
    else:
        size = list(struct.unpack(">II", (after_png or before_png)[16:24]))
    before = None
    if before_png is not None:
        before = f"before/{name}"
        os.makedirs(os.path.dirname(f"{out}/{before}"), exist_ok=True)
        open(f"{out}/{before}", "wb").write(before_png)
    commits = git("log", "--format=%h %s", f"{tag}..HEAD", "--", f"{stem}.png").decode("utf-8").splitlines()
    result.append({"name": name, "status": status, "before": before,
                   "after": f"../../{stem}.png" if after_png is not None else None, "size": size,
                   "caption": caption, "oldCaption": old_caption, "regions": regions,
                   "repeat": status == "M" and not new_caption and bool(regions) and all(r["seen"] for r in regions),
                   "commits": commits})
open(f"{out}/data.js", "w", encoding="utf-8").write(
    f"window.SHOTS_KEY = {json.dumps(git('rev-parse', tag).decode().strip())};\n"
    f"window.SHOTS = {json.dumps(result, ensure_ascii=False, indent=1)};\n")
repeats = sum(s["repeat"] for s in result)
print(f"{len(result)} changed screenshot(s), {repeats} of them only repeat changes shown in another"
      + (f"; {unchanged} re-recorded with the same pixels left out" if unchanged else "") + ".")
