#!/usr/bin/env bash
set -euo pipefail

# Paths are relative to the repository root. Without paths, use the current index.
repo_root="$(git -C "$(dirname -- "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)"
cd "$repo_root"

if (( $# > 0 )); then
  git add --all -- "$@"
fi

git diff --cached --check
if ! git diff --cached --quiet; then
  git commit -m "Update project"
fi

# Also retry a previous push when there is nothing new to commit.
git push
