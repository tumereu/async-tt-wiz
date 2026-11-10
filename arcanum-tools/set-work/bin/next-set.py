#!/usr/bin/env python3
"""Lists paper sets of the pinned AllPrintings (~/projects/atw-cache/AllPrintings.json.gz) newest first: release date, code,
type, card count and name, with SUPPORTED on the sets in the repository's supported-sets.txt. Set types: expansion, core,
masters, draft_innovation; online-only sets are left out. At most 40 sets. Usage: next-set.py"""
import gzip, json, os, sys

if len(sys.argv) != 1: sys.exit('Usage: next-set.py (no arguments)')
here = os.path.dirname(os.path.abspath(__file__))
supported_file = os.path.join(here, '..', '..', 'src', 'main', 'resources', 'arcanum', 'tools', 'supported-sets.txt')
mtgjson = os.path.expanduser('~/projects/atw-cache/AllPrintings.json.gz')
if not os.path.isfile(mtgjson): sys.exit(f'No {mtgjson}: run arcanum-tools/set-work/bin/setup.sh first')
supported = {l.split()[0] for l in open(supported_file, encoding='utf8') if l.strip() and not l.startswith('#')}
with gzip.open(mtgjson, 'rt', encoding='utf8') as f: d = json.load(f)
rows = []
for code, s in d['data'].items():
    if s.get('type') in ('expansion', 'core', 'masters', 'draft_innovation') and not s.get('isOnlineOnly'):
        rows.append((s.get('releaseDate', ''), code, s.get('type'), len(s.get('cards', [])), s.get('name', '')))
rows.sort(key=lambda r: (r[0], r[1]), reverse=True)
print(f"MTGJSON {d['meta'].get('version')}; newest first (date code type cards name)")
for date, code, kind, cards, name in rows[:39]:
    print(f"{date} {code:<5} {kind:<16} {cards:>4}  {name}{'  SUPPORTED' if code in supported else ''}")
