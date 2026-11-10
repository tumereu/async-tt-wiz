#!/usr/bin/env bash
# One-time setup for Arcanum set work (arcanum-tools/set-work/README.md). Fills the local data folder ~/projects/atw-cache,
# outside the repository: the lock and plan folders, the pinned Comprehensive Rules text, the pinned MTGJSON AllPrintings
# and all-card-names.txt. What is already there is only checked, never replaced. One status line per item.
# Run from the repository root:   arcanum-tools/set-work/bin/setup.sh
set -u
if [ ! -x ./scripts/gradle.sh ] || [ ! -f catalog/pin.properties ]; then
  echo "setup.sh: run it from the repository root (no ./scripts/gradle.sh or catalog/pin.properties in $PWD)" >&2
  exit 2
fi
C=$HOME/projects/atw-cache
failed=0

mkdir -p "$C/locks" "$C/set-plans" && echo "folders: $C/locks and $C/set-plans ready"

# The Comprehensive Rules text of the pinned version.
date=$(sed -n 's/.*EFFECTIVE_DATE: String = "\([0-9-]*\)".*/\1/p' arcanum-api/src/commonMain/kotlin/arcanum/api/ComprehensiveRules.kt)
if [ -z "$date" ]; then
  echo "rules: can't read ComprehensiveRules.EFFECTIVE_DATE from arcanum-api"; failed=1
else
  ymd=${date//-/}; rules=$C/MagicCompRules-$ymd.txt
  if [ -s "$rules" ]; then
    echo "rules: $rules present"
  else
    url="https://media.wizards.com/${date:0:4}/downloads/MagicCompRules%20$ymd.txt"
    part=$(mktemp "$C/MagicCompRules.XXXXXX.part")
    if curl --fail --silent --show-error --location --max-time 300 -o "$part" "$url" && grep -q "Comprehensive Rules" "$part"; then
      mv -n -- "$part" "$rules"; echo "rules: downloaded $rules"
    else
      rm -f -- "$part"; failed=1
      echo "rules: download of $url failed; get the $date text file from magic.wizards.com/en/rules and save it as $rules"
    fi
  fi
fi

# The MTGJSON AllPrintings named by catalog/pin.properties. An existing file is never replaced or deleted.
ap=$C/AllPrintings.json.gz
pin=$(sed -n 's/^allprintings\.sha256=//p' catalog/pin.properties)
pinver=$(sed -n 's/^mtgjson\.version=//p' catalog/pin.properties)
if [ -e "$ap" ]; then
  if out=$(./scripts/catalog-pin.sh verify 2>&1); then echo "AllPrintings: $ap matches the pin ($pinver)"
  else
    failed=1; echo "AllPrintings: scripts/catalog-pin.sh verify failed (the file is left as it is):"
    echo "$out" | grep -v '^\s*$' | tail -5 | sed 's/^/  /'
  fi
else
  part=$(mktemp "$C/AllPrintings.XXXXXX.part")
  echo "AllPrintings: downloading MTGJSON's current AllPrintings.json.gz (about 200 MB)..."
  if ! curl --fail --silent --show-error --location --max-time 1800 -o "$part" https://mtgjson.com/api/v5/AllPrintings.json.gz; then
    rm -f -- "$part"; failed=1; echo "AllPrintings: download failed; run setup.sh again later"
  elif [ "$(sha256sum "$part" | cut -d' ' -f1)" = "$pin" ]; then
    mv -n -- "$part" "$ap"; echo "$pin" > "$ap.sha256"; echo "AllPrintings: downloaded $ap, matching the pin ($pinver)"
  else
    rm -f -- "$part"; failed=1
    echo "AllPrintings: MTGJSON's current file is newer than the pin ($pinver) and was deleted. Moving the pin is a task of its own:"
    echo "  scripts/catalog-pin.sh move, then :arcanum-tools:setSupport and :arcanum-tools:parserDiff against the new file, before any set work."
  fi
fi

# Every real card name and face name, one per line, so tests' invented names can be checked against it.
names=$C/all-card-names.txt
if [ -s "$names" ]; then
  echo "card names: $names present"
elif [ ! -e "$ap" ]; then
  failed=1; echo "card names: needs $ap first"
else
  python3 -I - "$ap" "$names" <<'EOF'
import gzip, json, os, sys
with gzip.open(sys.argv[1], 'rt', encoding='utf8') as f: data = json.load(f)['data']
names = {n for s in data.values() for c in s.get('cards', []) for n in (c.get('name'), c.get('faceName')) if n}
tmp = sys.argv[2] + '.part'
with open(tmp, 'w', encoding='utf8') as f: f.write(''.join(n + '\n' for n in sorted(names)))
os.replace(tmp, sys.argv[2])
print(f'card names: wrote {len(names)} names to {sys.argv[2]}')
EOF
  [ $? = 0 ] || { failed=1; echo "card names: generating $names failed"; }
fi
exit $failed
