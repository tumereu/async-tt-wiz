#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

die() { echo "$*" >&2; exit 1; }
(( $# <= 1 )) || die "Usage: $0 [versionName]   (default: next patch version)"
VERSION_FILE=release/version.json

branch=$(git symbolic-ref --quiet --short HEAD) || die "Check out a branch first; HEAD is detached."
git diff --quiet HEAD -- "$VERSION_FILE" || die "$VERSION_FILE has uncommitted changes."
scripts/review.sh check || die "The owner has not approved every catalog change yet."
# An early warning only (owner, 2026-11-19: we don't plan to drop support for cards): sets or cards taken off the
# support lists since the last release. scripts/prod-release.sh compares the built catalogs and stops on any drop.
if last_tag=$(git describe --tags --abbrev=0 --match 'v[0-9]*' HEAD 2>/dev/null); then
    lists=arcanum-tools/src/main/resources/arcanum/tools
    for list in supported-sets.txt supported-cards.txt; do
        removed=$(comm -23 <(git show "$last_tag:$lists/$list" 2>/dev/null | grep -v '^#' | sort -u) \
            <(grep -v '^#' "$lists/$list" | sort -u) | grep -v '^[[:space:]]*$' || true)
        [[ -z $removed ]] || printf 'WARNING: taken off %s since %s (prod-release will refuse dropped card support):\n%s\n' \
            "$list" "$last_tag" "$removed" >&2
    done
fi

# Append the next allocation in place so the file keeps its hand-written layout.
read -r code name < <(python3 - "$VERSION_FILE" "${1:-}" <<'PY'
import json, pathlib, re, sys
path, requested = pathlib.Path(sys.argv[1]), sys.argv[2]
text = path.read_text()
last = json.loads(text)["allocations"][-1]
code = last["versionCode"] + 1
if requested:
    name = requested
else:
    major, minor, patch = re.fullmatch(r"([0-9]+)\.([0-9]+)\.([0-9]+)", last["versionName"]).groups()
    name = f"{major}.{minor}.{int(patch) + 1}"
assert re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?", name), f"Invalid versionName {name}"
entry = f'{{ "versionCode": {last["versionCode"]}, "versionName": "{last["versionName"]}" }}'
assert text.count(entry) == 1, f"Cannot locate the last allocation {entry}"
text = text.replace(entry, f'{entry},\n    {{ "versionCode": {code}, "versionName": "{name}" }}')
assert json.loads(text)["allocations"][-1] == dict(versionCode=code, versionName=name)
path.write_text(text)
print(code, name)
PY
)
tag=v$name
if git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null; then
    git checkout -- "$VERSION_FILE"
    die "Tag $tag already exists."
fi

git commit --quiet -m "Allocate versionCode $code ($name) for release" -- "$VERSION_FILE"
git tag -a "$tag" -m "Release $name (versionCode $code)"
# Atomic: the tag is never published without the branch commits it points into.
git push --atomic origin "HEAD:refs/heads/$branch" "refs/tags/$tag" ||
    die "Push failed; $tag exists locally. Retry with: git push --atomic origin HEAD:refs/heads/$branch refs/tags/$tag"
echo "Prepared $tag ($(git rev-parse HEAD)). Release with: scripts/prod-release.sh release $name"
