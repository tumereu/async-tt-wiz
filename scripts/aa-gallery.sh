#!/usr/bin/env bash
# Builds the Automatum screenshot gallery: a static, phone-friendly page of the items listed in
# androidApp/src/test/screenshots/gallery.tsv (one representative screenshot each, its caption, catalog IDs and
# decision kinds), as committed at HEAD. Items whose screenshot changed since the screenshots-approved tag come first
# with a "changed" badge; a tap on a picture opens it full size. Complements, never replaces, scripts/review.sh.
#   scripts/aa-gallery.sh [output dir]     default ~/projects/atw-cache/aa-gallery (outside the repository)
# Serving the page is up to the caller.
set -euo pipefail
cd "$(dirname "$0")/.."

out=${1:-$HOME/projects/atw-cache/aa-gallery}
shots=androidApp/src/test/screenshots
tag=screenshots-approved
git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null || { echo "Missing tag $tag." >&2; exit 1; }

mkdir -p "$out"
rm -rf "$out/images" "$out/index.html"
mkdir -p "$out/images"
python3 -I - "$shots" "$tag" "$out" <<'PY'
import html, os, subprocess, sys

shots, tag, out = sys.argv[1:]

def git(*args, text=True):
    return subprocess.run(["git", *args], capture_output=True, check=True, text=text).stdout

head = git("rev-parse", "--short", "HEAD").strip()
subject = git("log", "-1", "--format=%s", "HEAD").strip()
date = git("log", "-1", "--format=%cd", "--date=format:%Y-%m-%d %H:%M", "HEAD").strip()
changed = set(git("diff", "--name-only", tag, "HEAD", "--", shots).split())

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
    is_changed = path in changed or caption_path in changed
    items.append(dict(number=int(number), name=name, shot=shot, ids=ids.split(), decisions=decisions.replace(",", " ").split(),
                      caption=caption, image=image, changed=is_changed))

items.sort(key=lambda i: (not i["changed"], i["number"]))
count_changed = sum(i["changed"] for i in items)

def card(i):
    badge = '<span class="badge">changed</span>' if i["changed"] else ""
    ids = " ".join(f'<span class="id">{html.escape(x)}</span>' for x in i["ids"])
    kinds = " ".join(f'<span class="kind">{html.escape(x)}</span>' for x in i["decisions"])
    return f'''<article class="item{' changed' if i['changed'] else ''}" id="item-{i['number']}">
  <h2><span class="num">{i['number']}</span> {html.escape(i['name'])} {badge}</h2>
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
  .item.changed {{ border-color: var(--amber); box-shadow: 0 0 0 1px var(--amber); }}
  h2 {{ margin: 0 0 8px; font-size: 15px; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }}
  .num {{ display: inline-block; min-width: 26px; padding: 1px 6px; border-radius: 6px; background: var(--ink); color: #fff; font-size: 12px; text-align: center; }}
  .badge {{ padding: 1px 7px; border-radius: 999px; background: var(--amber); color: #fff; font-size: 11px; font-weight: 600; }}
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
  <p>{len(items)} items · {count_changed} changed since {html.escape(tag)} · commit {html.escape(head)} ({html.escape(date)}): {html.escape(subject)}</p>
</header>
<main>
{chr(10).join(card(i) for i in items)}
</main>
</body>
</html>
'''
with open(os.path.join(out, "index.html"), "w") as f:
    f.write(page)
print(f"{out}/index.html: {len(items)} items, {count_changed} changed since {tag}, commit {head}")
PY
