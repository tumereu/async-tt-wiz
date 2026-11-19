#!/usr/bin/env bash
# The card catalog's pin (docs/specs/web-client.md section 3.1): catalog/pin.properties names the MTGJSON AllPrintings
# build everything reads. The raw file is kept only on this machine, at $ATW_ALLPRINTINGS (default
# ~/projects/atw-cache/AllPrintings.json.gz); it is never committed.
#   verify             checks the local raw file against the pin
#   build <dir>        verifies it and builds the catalog into <dir> (<checksum>.jsonl.gz, and `current` naming that
#                      checksum, which the release builds into the app)
#   compare-support <old> <new> [count]
#                      lists the cards catalog <old> (a .jsonl.gz file or a built directory) supports and <new> doesn't;
#                      exits 3 when there are any, unless count is exactly their number (the release's guard)
#   move               downloads MTGJSON's current AllPrintings over the local file and pins it (a task of its own: run
#                      setSupport and parserDiff against it and fix or accept their changes before it ships)
set -euo pipefail
cd "$(dirname "$0")/.."
FILE=${ATW_ALLPRINTINGS:-$HOME/projects/atw-cache/AllPrintings.json.gz}
export ATW_ALLPRINTINGS=$FILE
MTGJSON=https://mtgjson.com/api/v5

catalog_command() { ./scripts/gradle.sh -q :catalog:catalogCommand --args="$*"; }

case "${1:-}" in
verify) (( $# == 1 )) || exit 2; catalog_command verify ;;
build) (( $# == 2 )) || { echo "Usage: $0 build <dir>" >&2; exit 2; }
    mkdir -p "$2"; catalog_command build --out "$(cd "$2" && pwd)" ;;
compare-support) (( $# == 3 || $# == 4 )) || { echo "Usage: $0 compare-support <old> <new> [count]" >&2; exit 2; }
    catalog_command compare-support --old "$(realpath -- "$2")" --new "$(realpath -- "$3")" --allow "${4:-0}" ;;
move)
    (( $# == 1 )) || exit 2
    mkdir -p "$(dirname "$FILE")"
    part=$FILE.part
    trap 'rm -f -- "$part"' EXIT
    echo "Downloading MTGJSON's current AllPrintings.json.gz..."
    curl --fail --silent --show-error --location --max-time 1800 -o "$part" "$MTGJSON/AllPrintings.json.gz"
    expected=$(curl --fail --silent --show-error --location --max-time 60 "$MTGJSON/AllPrintings.json.gz.sha256" | cut -c1-64)
    [[ $(sha256sum "$part" | cut -d' ' -f1) == "$expected" ]] || { echo "The download doesn't match MTGJSON's checksum." >&2; exit 1; }
    mv -- "$part" "$FILE"
    sha256sum "$FILE" | cut -d' ' -f1 > "$FILE.sha256"
    catalog_command move
    echo "Now run setSupport and parserDiff against the new file, and commit catalog/pin.properties." ;;
*) echo "Usage: $0 verify | build <dir> | compare-support <old> <new> [count] | move" >&2; exit 2 ;;
esac
