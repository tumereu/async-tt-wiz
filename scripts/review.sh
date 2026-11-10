#!/usr/bin/env bash
# Owner review gate. Releases are blocked until the owner has approved every catalog change since the last approval
# (scripts/prep-release.sh checks). Screenshots are reviewed in the gallery (scripts/aa-gallery.sh), not here (owner,
# 2026-11-10: the gallery replaces the screenshot gate).
#   scripts/review.sh catalog        diff of catalog changes since the last approval (difftastic)
#   scripts/review.sh approve        owner only: mark the committed catalog (HEAD) approved
#   scripts/review.sh check          exit 1 if anything is unapproved (used by prep-release.sh)
set -euo pipefail
cd "$(dirname "$0")/.."

CATALOG_TAG=catalog-approved
CATALOG=docs/features

die() { echo "$*" >&2; exit 1; }
git rev-parse --quiet --verify "refs/tags/$CATALOG_TAG" >/dev/null || die "Missing tag $CATALOG_TAG."

# Releases are cut from commits, so the gate compares the approved tag with HEAD.
pending() { git diff --quiet "$1" HEAD -- "$2"; }

case "${1:-}" in
catalog)
    git -c diff.external='difft --display=inline' diff "$CATALOG_TAG" -- "$CATALOG"
    ;;
approve)
    git tag -f "$CATALOG_TAG" HEAD >/dev/null; echo "Approved the catalog at $(git rev-parse --short HEAD)."
    ;;
check)
    pending "$CATALOG_TAG" "$CATALOG" || { echo "Unapproved catalog changes: scripts/review.sh catalog"; exit 1; }
    echo "The catalog is approved."
    ;;
*) die "Usage: $0 catalog | approve | check" ;;
esac
