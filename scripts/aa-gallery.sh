#!/usr/bin/env bash
# Builds the Automatum screenshot gallery: a static, phone-friendly page of the items listed in
# androidApp/src/test/screenshots/gallery.tsv (one representative screenshot each, its caption, catalog IDs and
# decision kinds), as committed at HEAD. The owner reviews only what's new, changed or commented since the last
# gallery review (the gallery-reviewed tag; the screenshots-approved tag if missing): those items come first with a
# badge, the rest are folded away. Comments are lines "<screenshot>\t<note>" in ~/projects/atw-cache/aa-gallery-comments.tsv.
# Tap a picture to open it full size. Complements, never replaces, scripts/review.sh (the release gate).
#   scripts/aa-gallery.sh [output dir]     default ~/projects/atw-cache/aa-gallery (outside the repository)
#   git tag -f gallery-reviewed HEAD      after a gallery review: what the owner has now seen
# Serving the page is up to the caller.
set -euo pipefail
cd "$(dirname "$0")/.."

out=${1:-$HOME/projects/atw-cache/aa-gallery}
shots=androidApp/src/test/screenshots
tag=gallery-reviewed
git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null || tag=screenshots-approved
git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null || { echo "Missing tag $tag." >&2; exit 1; }
comments=$HOME/projects/atw-cache/aa-gallery-comments.tsv

mkdir -p "$out"
rm -rf "$out/images" "$out/index.html"
mkdir -p "$out/images"
python3 -I - "$shots" "$tag" "$out" "$comments" <<'PY'
import html, os, subprocess, sys

shots, tag, out, comments_file = sys.argv[1:]
comments = {}
if os.path.exists(comments_file):
    for line in open(comments_file):
        if line.strip() and not line.startswith("#") and "\t" in line:
            shot, note = line.rstrip("\n").split("\t", 1)
            comments[shot] = note

def git(*args, text=True):
    return subprocess.run(["git", *args], capture_output=True, check=True, text=text).stdout

head = git("rev-parse", "--short", "HEAD").strip()
subject = git("log", "-1", "--format=%s", "HEAD").strip()
date = git("log", "-1", "--format=%cd", "--date=format:%Y-%m-%d %H:%M", "HEAD").strip()
reviewed = git("rev-parse", "--short", tag).strip()
changed = set(git("diff", "--name-only", tag, "HEAD", "--", shots).split())
seen = set(git("ls-tree", "-r", "--name-only", tag, "--", shots).split())

items = []
for line in git("show", f"HEAD:{shots}/gallery.tsv").splitlines()[1:]:
    if not line.strip():
        continue
    cells = line.split("\t") + [""]
    number, name, shot, ids, decisions = cells[:5]
    path = f"{shots}/{shot}"
    caption_path = path[:-4] + ".txt"
    try:
        caption = git("show", f"HEAD:{caption_path}").strip()
    except subprocess.CalledProcessError:
        caption = ""
    png = git("show", f"HEAD:{path}", text=False)
    image = f"images/{number.zfill(3)}-{os.path.basename(shot)}"
    with open(os.path.join(out, image), "wb") as f:
        f.write(png)
    status = "new" if path not in seen else "changed" if path in changed or caption_path in changed else ""
    items.append(dict(number=int(number), name=name, shot=shot, ids=ids.split(), decisions=decisions.replace(",", " ").split(),
                      caption=caption, image=image, status=status, comment=comments.get(shot, "")))

for i in items:
    i["review"] = bool(i["status"] or i["comment"])
items.sort(key=lambda i: i["number"])
review = [i for i in items if i["review"]]
rest = [i for i in items if not i["review"]]
count = lambda status: sum(i["status"] == status for i in items)
summary = f'{len(review)} to review ({count("new")} new, {count("changed")} changed, {sum(bool(i["comment"]) for i in items)} commented)'

def card(i):
    badge = f'<span class="badge {i["status"]}">{i["status"]}</span>' if i["status"] else ""
    if i["comment"]:
        badge += '<span class="badge commented">commented</span>'
    note = f'<p class="note">Your comment: {html.escape(i["comment"])}</p>' if i["comment"] else ""
    ids = " ".join(f'<span class="id">{html.escape(x)}</span>' for x in i["ids"])
    kinds = " ".join(f'<span class="kind">{html.escape(x)}</span>' for x in i["decisions"])
    return f'''<article class="item{' review' if i['review'] else ''}" id="item-{i['number']}">
  <h2><span class="num">{i['number']}</span> {html.escape(i['name'])} {badge}</h2>{note}
  <a class="shot" href="{html.escape(i['image'])}"><img loading="lazy" src="{html.escape(i['image'])}" alt="{html.escape(i['name'])}"></a>
  <p class="caption">{html.escape(i['caption'])}</p>
  <p class="meta">{ids} {kinds} <span class="file">{html.escape(i['shot'])}</span></p>
</article>'''

page = f'''<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Automatum gallery · {html.escape(head)}</title>
<style>
  :root {{ --ink: #263e39; --soft: #5d6f6b; --line: #d7dfdc; --violet: #7a4fc4; --amber: #a86200; --bg: #f3f6f5; }}
  * {{ box-sizing: border-box; }}
  body {{ margin: 0; font: 15px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: var(--ink); background: var(--bg); }}
  header {{ position: sticky; top: 0; z-index: 1; background: #fff; border-bottom: 1px solid var(--line); padding: 10px 14px; }}
  header h1 {{ margin: 0; font-size: 17px; }}
  header p {{ margin: 2px 0 0; color: var(--soft); font-size: 13px; }}
  main {{ display: grid; gap: 14px; padding: 14px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 1fr)); }}
  .item {{ background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px 12px; }}
  .item.review {{ border-color: var(--amber); box-shadow: 0 0 0 1px var(--amber); }}
  h2 {{ margin: 0 0 8px; font-size: 15px; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }}
  .num {{ display: inline-block; min-width: 26px; padding: 1px 6px; border-radius: 6px; background: var(--ink); color: #fff; font-size: 12px; text-align: center; }}
  .badge {{ padding: 1px 7px; border-radius: 999px; background: var(--amber); color: #fff; font-size: 11px; font-weight: 600; }}
  .badge.new {{ background: #24489c; }}
  .badge.commented {{ background: var(--violet); }}
  .note {{ margin: 0 0 8px; padding: 4px 8px; border-left: 3px solid var(--violet); background: #f4effb; font-size: 13px; }}
  details {{ padding: 0 14px 14px; }}
  summary {{ cursor: pointer; padding: 8px 0; color: var(--soft); font-weight: 600; }}
  details main {{ padding: 0; }}
  .shot {{ display: block; background: repeating-conic-gradient(#eef1f0 0 25%, #fff 0 50%) 0 0 / 16px 16px; border-radius: 6px; text-align: center; }}
  .shot img {{ max-width: 100%; max-height: 70vh; display: inline-block; vertical-align: middle; }}
  .caption {{ margin: 8px 0 6px; }}
  .meta {{ margin: 0; display: flex; flex-wrap: wrap; gap: 4px; font-size: 12px; color: var(--soft); }}
  .id, .kind {{ padding: 0 6px; border-radius: 4px; border: 1px solid var(--line); }}
  .kind {{ border-color: var(--violet); color: var(--violet); }}
  .file {{ flex-basis: 100%; }}
</style>
</head>
<body>
<header>
  <h1>Automatum screenshot gallery</h1>
  <p>{summary} since {html.escape(tag)} ({html.escape(reviewed)}) · {len(items)} items · commit {html.escape(head)} ({html.escape(date)}): {html.escape(subject)}</p>
</header>
<main>
{chr(10).join(card(i) for i in review) or '<p>Nothing new, changed or commented.</p>'}
</main>
<details><summary>Already reviewed, unchanged ({len(rest)})</summary>
<main>
{chr(10).join(card(i) for i in rest)}
</main>
</details>
</body>
</html>
'''
with open(os.path.join(out, "index.html"), "w") as f:
    f.write(page)
print(f"{out}/index.html: {len(items)} items, {summary} since {tag} {reviewed}, commit {head}")
PY
