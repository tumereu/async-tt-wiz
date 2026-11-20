"""Resolves a merge conflict in gallery.tsv, used by scripts/merge-gate.sh: both sides' rows kept, ordered by number.
Fails (exit 1, the clash on stderr) when both sides give one number to different rows, which needs a person."""
import re, sys

path = sys.argv[1]
text = open(path).read()
conflict = re.compile(r'<<<<<<< [^\n]*\n(.*?)(?:\|\|\|\|\|\|\| [^\n]*\n.*?)?=======\n(.*?)>>>>>>> [^\n]*\n', re.S)
text = conflict.sub(lambda m: m.group(1) + m.group(2), text)
head, *rows = [line for line in text.split('\n') if line]
by_number = {}
for row in rows:
    number = row.split('\t', 1)[0]
    if not number.isdigit():
        sys.exit(f"gallery.tsv: a row without a number: {row[:80]}")
    if by_number.get(number, row) != row:
        sys.exit(f"gallery.tsv: both sides use row {number}: '{by_number[number][:60]}' and '{row[:60]}'")
    by_number[number] = row
open(path, 'w').write('\n'.join([head] + [by_number[n] for n in sorted(by_number, key=int)]) + '\n')
