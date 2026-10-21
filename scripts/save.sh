#!/usr/bin/env bash
set -euo pipefail

# Paths are relative to the repository root. Without paths, use the current index.
repo_root="$(git -C "$(dirname -- "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)"
cd "$repo_root"

if (( $# > 0 )); then
  git --literal-pathspecs add --all -- "$@"
  # Apply newly added/changed .gitattributes even to previously staged files.
  # Limit renormalization to the supplied owned paths; preserve all other work.
  # Deleted paths are already removed from the index and must not be re-added.
  git --literal-pathspecs ls-files -z -- "$@" |
    git --literal-pathspecs add --renormalize --pathspec-from-file=- --pathspec-file-nul
fi

if git diff --cached --check; then
  :
else
  status=$?
  cat >&2 <<'EOF'
Save stopped before commit/push: staged whitespace validation failed.
Fix the reported content or repository .gitattributes, then re-stage only the
owned paths and retry this save. Generated Windows wrappers need an explicit
text/eol rule; do not disable whitespace checks or discard the staged work.
EOF
  exit "$status"
fi
if ! git diff --cached --quiet; then
  git commit -m "Update project"
fi

# Also retry a previous push when there is nothing new to commit.
git push
