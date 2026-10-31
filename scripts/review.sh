#!/usr/bin/env bash
# Owner review gates. Releases are blocked until the owner has approved every catalog
# and screenshot change and no catalog entry is still marked (?) (scripts/prep-release.sh checks).
#   scripts/review.sh catalog        diff of catalog changes since the last approval (difftastic),
#                                    then every entry still marked (?), changed or not
#   scripts/review.sh screenshots    viewer: changed screenshots one at a time, approved | now, with captions
#   scripts/review.sh approve [catalog|screenshots]
#                                    owner only: mark the committed catalog and/or screenshots (HEAD) approved
#   scripts/review.sh check          exit 1 if anything is unapproved (used by prep-release.sh)
set -euo pipefail
cd "$(dirname "$0")/.."

CATALOG_TAG=catalog-approved
SHOTS_TAG=screenshots-approved
CATALOG=docs/features
SHOTS=androidApp/src/test/screenshots

die() { echo "$*" >&2; exit 1; }
for tag in $CATALOG_TAG $SHOTS_TAG; do
    git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null || die "Missing tag $tag."
done

# Releases are cut from commits, so the gate compares the approved tag with HEAD.
pending() { git diff --quiet "$1" HEAD -- "$2"; }
# Entries still holding a decision the owner hasn't confirmed (docs/features/README.md).
open_questions() { grep -n '^- \*\*[A-Z]\{3\}-[0-9]\{3\}\*\*.*(?)' "$CATALOG"/*.md || true; }

case "${1:-}" in
catalog)
    git -c diff.external='difft --display=inline' diff "$CATALOG_TAG" -- "$CATALOG"
    questions=$(open_questions)
    if [[ -n $questions ]]; then
        echo
        echo "Still marked (?) — these block a release until answered ($(wc -l <<< "$questions") entries):"
        echo "$questions"
    fi
    ;;
screenshots)
    # One screenshot at a time: approved | now, with the caption the test writes next to each image
    # (foo.png → foo.txt). A changed caption alone also counts as a change.
    out=build/screenshot-review
    rm -rf "$out"; mkdir -p "$out"
    cp scripts/screenshot-review.html "$out/index.html"
    { git diff --name-status "$SHOTS_TAG" -- "$SHOTS"; git ls-files --others --exclude-standard -- "$SHOTS" | sed 's/^/A\t/'; } |
        python3 -I scripts/screenshot-review.py "$SHOTS_TAG" "$SHOTS" "$out" || exit 1
    echo "$out/index.html"
    command -v explorer.exe >/dev/null && explorer.exe "$(wslpath -w "$out/index.html")" || true
    ;;
approve)
    what=${2:-both}
    [[ $what == both || $what == catalog || $what == screenshots ]] || die "Usage: $0 approve [catalog|screenshots]"
    [[ $what == screenshots ]] || { git tag -f "$CATALOG_TAG" HEAD >/dev/null; echo "Approved the catalog at $(git rev-parse --short HEAD)."; }
    [[ $what == catalog ]] || { git tag -f "$SHOTS_TAG" HEAD >/dev/null; echo "Approved the screenshots at $(git rev-parse --short HEAD)."; }
    [[ -z $(open_questions) || $what == screenshots ]] || echo "Entries marked (?) still block a release: scripts/review.sh catalog lists them."
    ;;
check)
    ok=1
    pending "$CATALOG_TAG" "$CATALOG" || { echo "Unapproved catalog changes: scripts/review.sh catalog"; ok=0; }
    pending "$SHOTS_TAG" "$SHOTS" || { echo "Unapproved screenshot changes: scripts/review.sh screenshots"; ok=0; }
    [[ -z $(git grep -n '^- \*\*[A-Z]\{3\}-[0-9]\{3\}\*\*.*(?)' HEAD -- "$CATALOG") ]] ||
        { echo "Catalog entries still marked (?): scripts/review.sh catalog"; ok=0; }
    (( ok )) || exit 1
    echo "Catalog and screenshots are approved."
    ;;
*) die "Usage: $0 catalog | screenshots | approve [catalog|screenshots] | check" ;;
esac
