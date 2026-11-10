#!/usr/bin/env python3
"""Weighted token cost of one set's work, by API price ratios (input 1, output 5, cache read 0.1, cache creation 1.25), from
the local Claude Code transcripts: every subagent whose prompt starts 'SET-WORK <code>' (or the older 'SET-TRIAL <code>'),
one row per agent and the total. Transcripts are read from ~/.claude/projects/<folder>/*/subagents/, for every folder
named after this repository's root or one of its worktrees (Claude Code names a folder after its path, each character
other than a letter or digit replaced by '-'). Usage: set-cost.py <code>"""
import glob, json, os, re, subprocess, sys

if len(sys.argv) != 2: sys.exit('Usage: set-cost.py <code>')
code = sys.argv[1].lower()
here = os.path.dirname(os.path.abspath(__file__))

def git(*args):
    return subprocess.run(['git', '-C', here, *args], capture_output=True, text=True, check=True).stdout

common = git('rev-parse', '--path-format=absolute', '--git-common-dir').strip()
roots = {os.path.dirname(common)} | {l[9:] for l in git('worktree', 'list', '--porcelain').splitlines() if l.startswith('worktree ')}
prefixes = {re.sub(r'[^A-Za-z0-9]', '-', r) for r in roots}
base = os.path.expanduser('~/.claude/projects')
folders = [d for d in glob.glob(base + '/*/') if any(os.path.basename(d.rstrip('/')).startswith(p) for p in prefixes)]
files = sorted({f for d in folders for f in glob.glob(d + '*/subagents/**/*.jsonl', recursive=True)})
start = re.compile(r'SET-(?:WORK|TRIAL) ' + re.escape(code) + r'(?:[ \t]+([^\n]*))?(?:\n|$)', re.I)

def first_prompt(lines):
    for l in lines[:5]:
        try: e = json.loads(l)
        except ValueError: continue
        if e.get('type') != 'user': continue
        c = (e.get('message') or {}).get('content')
        if isinstance(c, list): c = '\n'.join(b.get('text', '') for b in c if isinstance(b, dict))
        return (c or '').lstrip()
    return ''

rows = []
for f in files:
    with open(f, encoding='utf8') as h: lines = h.readlines()
    m = start.match(first_prompt(lines))
    if not m: continue
    label = (m.group(1) or '').strip()[:30] or os.path.basename(f)[:20]
    # A transcript repeats one API message on several lines (one per content block), each with the same usage: count it once.
    msgs = {}
    for l in lines:
        try: e = json.loads(l)
        except ValueError: continue
        if e.get('type') != 'assistant': continue
        msg = e.get('message') or {}; u = msg.get('usage') or {}
        msgs[msg.get('id') or e.get('uuid')] = [u.get(k, 0) or 0 for k in
            ('input_tokens', 'output_tokens', 'cache_read_input_tokens', 'cache_creation_input_tokens')]
    i = o = r = c = peak = 0
    for a, b, d, g in msgs.values():
        i += a; o += b; r += d; c += g; peak = max(peak, a + d + g)
    rows.append((label, len(msgs), o, r, c, int(i + 5 * o + .1 * r + 1.25 * c), peak))
if not rows: sys.exit(f'No agent with a prompt starting SET-WORK {code} in {len(folders)} transcript folders under {base}')
print(f"{'agent':<30} {'turns':>6} {'output':>9} {'cache read':>12} {'cache create':>12} {'weighted':>11} {'peak ctx':>9}")
for x in sorted(rows, key=lambda x: -x[5]): print(f'{x[0]:<30} {x[1]:>6} {x[2]:>9} {x[3]:>12} {x[4]:>12} {x[5]:>11} {x[6]:>9}')
print(f"{'TOTAL (' + str(len(rows)) + ' agents)':<30} {sum(x[1] for x in rows):>6} {sum(x[2] for x in rows):>9} {sum(x[3] for x in rows):>12} "
      f'{sum(x[4] for x in rows):>12} {sum(x[5] for x in rows):>11}')
