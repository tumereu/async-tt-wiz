"""Resolves a merge conflict in docs/backlog.md, used by scripts/merge-gate.sh. Branches delete the items they built, so
two branches deleting neighbouring items clash. The file must hold diff3-style markers (git checkout --conflict=diff3):
in each conflict a line stays when both sides kept it or one side added it; a line either side deleted goes.
Fails (exit 1) on markers without the base part."""
import re, sys

path = sys.argv[1]
text = open(path).read()
conflict = re.compile(r'<<<<<<< [^\n]*\n(.*?)\|\|\|\|\|\|\| [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n', re.S)

def resolve(m):
    ours, base, theirs = (part.splitlines(True) for part in m.groups())
    kept = [line for line in ours if line not in base or line in theirs]
    kept += [line for line in theirs if line not in base and line not in ours]
    return ''.join(kept)

text = conflict.sub(resolve, text)
if re.search(r'^(<<<<<<<|=======|>>>>>>>)', text, re.M):
    sys.exit("backlog.md: a conflict without its base part")
open(path, 'w').write(text)
