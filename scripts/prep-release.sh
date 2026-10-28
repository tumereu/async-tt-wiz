#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

die() { echo "$*" >&2; exit 1; }
(( $# <= 1 )) || die "Usage: $0 [versionName]   (default: next patch version)"
VERSION_FILE=release/version.json

branch=$(git symbolic-ref --quiet --short HEAD) || die "Check out a branch first; HEAD is detached."
git diff --quiet HEAD -- "$VERSION_FILE" || die "$VERSION_FILE has uncommitted changes."

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
