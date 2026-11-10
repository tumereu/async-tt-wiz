#!/usr/bin/env python3
"""Writes <plan>/helpers.md so set workers don't grep for helper names: one line per test helper (signature only) from the
parser's shared card DSL, the most recently worked set's helper file and the engine's scenario API (Scenario.kt,
ScenarioPlayer.kt with casts/activates/battlefield/answers..., Expectations.kt, and Cards.kt with Cards.invent/inventFaces),
plus every shared driver of the engine's set packages (`fun Scenario.resolveAll()`, `playOut`, `ownAlternative`,
`grantedWay`... in the sets' non-test files such as <Code>Support.kt or <Code>Scenarios.kt), each listed once with the package
to import it from and the file and line of its first definition found (files in path order). The most recently worked set's
helper file is the set helper file (arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/<code>/<Code>Cards.kt) most
recently changed in the worktree's git history, or the one of <set code> when given.
Usage: helper-index.py <worktree> <plan> [<set code>]."""
import glob, os, re, subprocess, sys
if len(sys.argv) not in (3, 4): sys.exit('Usage: helper-index.py <worktree> <plan dir> [<set code>]')
wt, plan = sys.argv[1], sys.argv[2]
sets_dir = 'arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets'
engine = 'arcanum-engine/src/commonTest/kotlin/arcanum/engine'
if len(sys.argv) == 4:
    code = sys.argv[3].lower()
    helper = f'{sets_dir}/{code}/{code.capitalize()}Cards.kt'
    if not os.path.isfile(os.path.join(wt, helper)): sys.exit(f'helper-index.py: set {code} has no helper file {helper} in {wt}')
else:
    def changed(rel):
        out = subprocess.run(['git', '-C', wt, 'log', '-1', '--format=%ct', '--', rel], capture_output=True, text=True, check=True).stdout.strip()
        return int(out) if out else 0
    candidates = [os.path.relpath(p, wt) for p in glob.glob(os.path.join(wt, sets_dir, '*', '*Cards.kt'))]
    dated = sorted(((changed(rel), rel) for rel in candidates), reverse=True)
    if not dated or dated[0][0] == 0: sys.exit(f'helper-index.py: no set helper file {sets_dir}/*/*Cards.kt committed in {wt}')
    helper = dated[0][1]
# where Cards.invent / Cards.inventFaces live in the engine tests
cards_kt = next((os.path.relpath(p, wt) for p in sorted(glob.glob(os.path.join(wt, engine, '**', '*.kt'), recursive=True))
                 if re.search(r'^\s+fun inventFaces\(', open(p, encoding='utf8').read(), re.M)), None)
files = [('Parser card DSL (import arcanum.parser.cube.*)', 'arcanum-parser/src/commonTest/kotlin/arcanum/parser/cube/CubeCard.kt'),
         ("Most recently worked set's helpers (the set's first chunk creates <Code>Cards.kt in its set package with the generic "
          "helpers it needs copied from this file, not this set's set-specific ones; later chunks add to it)", helper),
         ('Engine scenarios', f'{engine}/scenario/Scenario.kt'),
         ('Engine scenario players (alice, bob...: battlefield, hand, casts, activates, attacks, answers...)', f'{engine}/scenario/ScenarioPlayer.kt'),
         ('Engine expectations (inside expect { })', f'{engine}/scenario/Expectations.kt'),
         ('Engine test cards (Cards.invent, Cards.inventFaces, face)', cards_kt)]
SIG = re.compile(r'(internal )?(infix )?(fun|val|var|class|object|data class) '   # top level
                 r'|    (infix )?fun '                                                # members
                 r'|    val ([A-Za-z<>]+\.\w+|\w+: .* get\(\))')                     # member and extension properties
def signature(lines, i, limit):
    """The declaration starting at lines[i], joined over lines until its parentheses close, up to its body (` = ` or `{`)."""
    text, depth, j = '', 0, i
    while j < len(lines):
        part = lines[j].strip()
        for k, ch in enumerate(part):
            if ch in '([': depth += 1
            elif ch in ')]': depth -= 1
            elif depth == 0 and (part[k:k + 3] == ' = ' or (ch == '{' and k > 0 and part[k - 1] == ' ')):
                return (text + part[:k]).strip()[:limit]
        text += part + ('' if part.endswith('(') else ' ')
        if depth <= 0: break
        j += 1
    return text.strip()[:limit]

out = ['# Test helpers: signatures only (open the file at the line for the body)', '']
for title, rel in files:
    if not rel or not os.path.exists(os.path.join(wt, rel)): continue
    out += [f'## {title}: {rel}']
    lines = open(os.path.join(wt, rel), encoding='utf8').read().split('\n')
    for i, l in enumerate(lines):
        if SIG.match(l) and 'private' not in l:
            out.append(f'{i + 1}: {signature(lines, i, 400)}')
    out.append('')

# shared drivers: top-level extension functions on Scenario/ScenarioPlayer in the sets' non-test files, first definition only
DRIVER = re.compile(r'^(internal )?fun (Scenario|ScenarioPlayer)\.(\w+)')
drivers = {}
for p in sorted(glob.glob(os.path.join(wt, engine, 'sets', '*', '*.kt'))):
    if p.endswith('Test.kt'): continue
    rel, pkg = os.path.relpath(p, wt), None
    lines = open(p, encoding='utf8').read().split('\n')
    for i, l in enumerate(lines):
        if l.startswith('package '): pkg = l.split()[1]
        m = DRIVER.match(l)
        if m and m.group(3) not in drivers: drivers[m.group(3)] = (pkg, rel, i + 1, signature(lines, i, 250))
out += ['## Shared scenario drivers in set packages: import the one listed (`import <package>.<name>`), never copy it']
for name, (pkg, rel, n, sig) in sorted(drivers.items()):
    out.append(f'{name}: {pkg} ({rel}:{n}) {sig}')
out.append('')
open(os.path.join(plan, 'helpers.md'), 'w').write('\n'.join(out) + '\n')
print(f"{len(out)} lines; most recently worked set's helpers: {helper}; {len(drivers)} shared drivers")
