#!/usr/bin/env python3
"""Lists the candidate sets of the pinned AllPrintings (~/projects/atw-cache/AllPrintings.json.gz) newest first: release date,
code, type, card count, base set size and name, with SUPPORTED on the sets in the repository's supported-sets.txt and NEXT on
the set to take: the newest one not supported. Candidates: expansion, core, masters and draft_innovation sets that are
released (release date today or earlier), have cards, aren't online-only and have at least 150 cards in their base set
(MTGJSON's baseSetSize), so Modern Horizons counts and small supplements and bonus sheets don't. At most 40 sets, plus the
NEXT one if it is older. Usage: next-set.py"""
import datetime, gzip, json, os, sys

if len(sys.argv) != 1: sys.exit('Usage: next-set.py (no arguments)')
here = os.path.dirname(os.path.abspath(__file__))
supported_file = os.path.join(here, '..', '..', 'src', 'main', 'resources', 'arcanum', 'tools', 'supported-sets.txt')
mtgjson = os.path.expanduser('~/projects/atw-cache/AllPrintings.json.gz')
if not os.path.isfile(mtgjson): sys.exit(f'No {mtgjson}: run arcanum-tools/set-work/bin/setup.sh first')
supported = {l.split()[0] for l in open(supported_file, encoding='utf8') if l.strip() and not l.startswith('#')}
with gzip.open(mtgjson, 'rt', encoding='utf8') as f: d = json.load(f)
today = datetime.date.today().isoformat()
rows = []
for code, s in d['data'].items():
    if s.get('type') not in ('expansion', 'core', 'masters', 'draft_innovation') or s.get('isOnlineOnly'): continue
    date, cards, base = s.get('releaseDate', ''), len(s.get('cards', [])), s.get('baseSetSize') or 0
    if not date or date > today or cards == 0 or base < 150: continue
    rows.append((date, code, s.get('type'), cards, base, s.get('name', '')))
rows.sort(key=lambda r: (r[0], r[1]), reverse=True)
nxt = next((r[1] for r in rows if r[1] not in supported), None)
print(f"MTGJSON {d['meta'].get('version')}; released by {today}, base set of 150+ cards; newest first (date code type cards base name)")
for i, (date, code, kind, cards, base, name) in enumerate(rows):
    if i >= 40 and code != nxt: continue
    mark = '  SUPPORTED' if code in supported else '  NEXT' if code == nxt else ''
    print(f"{date} {code:<5} {kind:<16} {cards:>4} {base:>4}  {name}{mark}")
print(f'NEXT: {nxt}' if nxt else 'NEXT: none (every candidate set is supported)')
