#!/usr/bin/env python3
"""Lists the candidate sets of the pinned AllPrintings (~/projects/atw-cache/AllPrintings.json.gz) newest first: release date,
code, type, card count, base set size and name, with SUPPORTED on the sets in the repository's supported-sets.txt and NEXT on
the set to take: the newest one not supported. Candidates: sets players draft, i.e. expansion and core sets, plus other
sets sold in draft or play boosters (masters, Modern Horizons), that are released (release date today or earlier), have
cards, aren't online-only and have at least 150 cards in their base set (MTGJSON's baseSetSize), so Jumpstart, small
supplements and bonus sheets don't count. They are sets of tournament-legal cards: at least 90% of their cards are legal or
restricted in Vintage (MTGJSON's legalities.vintage) and the set type is never funny or memorabilia, so Un-sets, collector
editions and playtest grab-bags aren't candidates. At most 40 sets, plus the NEXT one if it is older; --all lists every candidate. Usage: next-set.py [--all]"""
import datetime, fcntl, gzip, json, os, sys

if sys.argv[1:] not in ([], ["--all"]): sys.exit("Usage: next-set.py [--all]")
limit = 10**6 if sys.argv[1:] == ["--all"] else 40
here = os.path.dirname(os.path.abspath(__file__))
supported_file = os.path.join(here, '..', '..', 'src', 'main', 'resources', 'arcanum', 'tools', 'supported-sets.txt')
mtgjson = os.path.expanduser('~/projects/atw-cache/AllPrintings.json.gz')
if not os.path.isfile(mtgjson): sys.exit(f'No {mtgjson}: run arcanum-tools/set-work/bin/setup.sh first')
supported = {l.split()[0] for l in open(supported_file, encoding='utf8') if l.strip() and not l.startswith('#')}
# about 4.6 GB outside the build's memory cap: one AllPrintings load at a time on this machine
lock = open(os.path.expanduser('~/projects/atw-cache/locks/allprintings.lock'), 'w'); fcntl.flock(lock, fcntl.LOCK_EX)
with gzip.open(mtgjson, 'rt', encoding='utf8') as f: d = json.load(f)
lock.close()
today = datetime.date.today().isoformat()
rows = []
for code, s in d['data'].items():
    boosters = s.get('booster') or {}
    drafted = s.get('type') in ('expansion', 'core') or 'draft' in boosters or 'play' in boosters
    if not drafted or s.get('isOnlineOnly'): continue
    date, cards, base = s.get('releaseDate', ''), len(s.get('cards', [])), s.get('baseSetSize') or 0
    if not date or date > today or cards == 0 or base < 150 or s.get('type') in ('funny', 'memorabilia'): continue
    vintage = sum(1 for c in s['cards'] if (c.get('legalities') or {}).get('vintage') in ('Legal', 'Restricted')) / cards
    if vintage < 0.9: continue
    rows.append((date, code, s.get('type'), cards, base, s.get('name', '')))
rows.sort(key=lambda r: (r[0], r[1]), reverse=True)
nxt = next((r[1] for r in rows if r[1] not in supported), None)
print(f"MTGJSON {d['meta'].get('version')}; released by {today}, base set of 150+ cards, 90%+ Vintage-legal; newest first (date code type cards base name)")
for i, (date, code, kind, cards, base, name) in enumerate(rows):
    if i >= limit and code != nxt: continue
    mark = '  SUPPORTED' if code in supported else '  NEXT' if code == nxt else ''
    print(f"{date} {code:<5} {kind:<16} {cards:>4} {base:>4}  {name}{mark}")
print(f'NEXT: {nxt}' if nxt else 'NEXT: none (every candidate set is supported)')
