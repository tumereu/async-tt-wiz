#!/usr/bin/env python3
"""Prints the model to give each chunk of a set plan, one line per chunk: number, cards, unread, how many of its cards are in
difficult.md's "## Cards" section, test-only ("mechanical") or not, theme (the chunk file's first line) and the suggested
model. Rule: test-only chunks -> sonnet; otherwise the default model if more than 5 unread, or more than 2 cards in
"## Cards", or a rules-heavy theme (conditions, effects, copies, replacements, layers, storm, meld); otherwise sonnet.
Usage: chunk-models.py <plan dir>"""
import glob, os, re, sys
if len(sys.argv) != 2: sys.exit('Usage: chunk-models.py <plan dir>')
plan = sys.argv[1]
chunk_files = sorted(glob.glob(os.path.join(plan, 'chunks', '*.md')))
if not chunk_files: sys.exit(f'chunk-models.py: no chunk files in {plan}/chunks')

difficult, in_cards = [], False
for l in open(os.path.join(plan, 'difficult.md'), encoding='utf8'):
    if l.startswith('## '): in_cards = l.strip() == '## Cards'
    elif in_cards and l.startswith('- '): difficult.append(l[2:].rstrip('\n'))
HEAVY = re.compile(r'condition|effect|cop(y|ies)|replacement|layer|storm|meld', re.I)

print('chunk cards unread difficult mechanical model    theme')
for f in chunk_files:
    lines = open(f, encoding='utf8').read().split('\n')
    theme = re.sub(r'^# Chunk \S+: ', '', lines[0])
    m = re.match(r'(\d+) cards, (\d+) unread', lines[1] if len(lines) > 1 else '')
    cards, unread = (int(m.group(1)), int(m.group(2))) if m else (0, 0)
    mechanical = 'mechanical' in (lines[1] if len(lines) > 1 else '')
    names = [l[3:].strip() for l in lines if l.startswith('## ')]
    hard = sum(1 for n in names if any(d.startswith(n + ': ') for d in difficult))
    heavy = unread > 5 or hard > 2 or bool(HEAVY.search(theme))
    model = 'sonnet' if mechanical or not heavy else 'default'
    number = os.path.basename(f)[:-3]
    print(f"{number:<5} {cards:>5} {unread:>6} {hard:>9} {'yes' if mechanical else 'no':<10} {model:<8} {theme[:100]}")
