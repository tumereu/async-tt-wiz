#!/usr/bin/env bash
# Owner review gates. Releases are blocked until the owner has approved every catalog
# and screenshot change (scripts/prep-release.sh checks both).
#   scripts/review.sh catalog        diff of catalog changes since the last approval (difftastic)
#   scripts/review.sh screenshots    page with every changed screenshot, before and after
#   scripts/review.sh approve        owner only: mark the committed catalog and screenshots (HEAD) approved
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

case "${1:-}" in
catalog)
    git -c diff.external='difft --display=inline' diff "$CATALOG_TAG" -- "$CATALOG"
    ;;
screenshots)
    out=build/screenshot-review
    rm -rf "$out"; mkdir -p "$out/before"
    rows=""
    while IFS=$'\t' read -r status path rest; do
        [[ $status == R* ]] && { old=$path; path=$rest; } || old=$path
        [[ $path == *.png ]] || continue
        name=${path#"$SHOTS"/}
        before="" after=""
        if [[ $status != A ]]; then
            mkdir -p "$out/before/$(dirname "$name")"
            git show "$SHOTS_TAG:$old" > "$out/before/$name"
            before="<img src=\"before/$name\">"
        fi
        [[ -f $path ]] && after="<img src=\"../../$path\">"
        rows+="<tr><td colspan=2 class=name>${status:0:1} $name</td></tr><tr><td>${before:-<i>new</i>}</td><td>${after:-<i>removed</i>}</td></tr>"
    done < <(git diff --name-status "$SHOTS_TAG" -- "$SHOTS"; git ls-files --others --exclude-standard -- "$SHOTS" | sed 's/^/A\t/')
    [[ -n $rows ]] || { echo "No screenshot changes since the last approval."; exit 0; }
    cat > "$out/index.html" <<HTML
<!doctype html><meta charset=utf-8><title>Screenshot review</title>
<style>body{font-family:sans-serif;background:#222;color:#eee}td{vertical-align:top;padding:4px}
img{max-width:48vw;border:1px solid #555}.name{padding-top:24px;font-weight:bold}</style>
<h1>Screenshots changed since the last approval</h1><p>Left: approved. Right: now.</p><table>$rows</table>
HTML
    echo "$out/index.html"
    command -v explorer.exe >/dev/null && explorer.exe "$(wslpath -w "$out/index.html")" || true
    ;;
approve)
    git tag -f "$CATALOG_TAG" HEAD >/dev/null
    git tag -f "$SHOTS_TAG" HEAD >/dev/null
    echo "Approved catalog and screenshots at $(git rev-parse --short HEAD)."
    ;;
check)
    ok=1
    pending "$CATALOG_TAG" "$CATALOG" || { echo "Unapproved catalog changes: scripts/review.sh catalog"; ok=0; }
    pending "$SHOTS_TAG" "$SHOTS" || { echo "Unapproved screenshot changes: scripts/review.sh screenshots"; ok=0; }
    (( ok )) || exit 1
    echo "Catalog and screenshots are approved."
    ;;
*) die "Usage: $0 catalog | screenshots | approve | check" ;;
esac
