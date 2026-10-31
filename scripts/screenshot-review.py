"""Writes data.js for scripts/screenshot-review.html from `git diff --name-status` lines on stdin
(run by scripts/review.sh screenshots from the repository root)."""
import json, os, subprocess, sys
tag, shots, out = sys.argv[1:]
def show(rev_path):
    r = subprocess.run(["git", "show", rev_path], capture_output=True)
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
result = []
for stem, item in sorted(items.items()):
    name = stem[len(shots) + 1:] + ".png"
    before_png = show(f"{tag}:{item['old']}.png")
    before = None
    if before_png is not None:
        before = f"before/{name}"
        os.makedirs(os.path.dirname(f"{out}/{before}"), exist_ok=True)
        open(f"{out}/{before}", "wb").write(before_png)
    after = f"../../{stem}.png" if os.path.exists(f"{stem}.png") else None
    caption = open(f"{stem}.txt", encoding="utf-8").read().strip() if os.path.exists(f"{stem}.txt") else None
    old_caption = show(f"{tag}:{item['old']}.txt")
    status = "A" if before is None else "D" if after is None else "M"
    result.append({"name": name, "status": status, "before": before, "after": after, "caption": caption,
                   "oldCaption": old_caption.decode("utf-8").strip() if old_caption else None})
open(f"{out}/data.js", "w", encoding="utf-8").write(
    f"window.SHOTS_KEY = {json.dumps(subprocess.run(['git', 'rev-parse', tag], capture_output=True, text=True).stdout.strip())};\n"
    f"window.SHOTS = {json.dumps(result, ensure_ascii=False, indent=1)};\n")
print(f"{len(result)} changed screenshot(s).")
