# Project instructions

- This project is specification-only. Do not create application code or scaffolding
  or advance delivery stages without the owner's approval. Read the
  [design index](docs/README.md) and [session handoff](docs/agent/session-handoff.md).
- Preserve unrelated user work. Inspect Git status before editing and include
  only the files belonging to the current task when saving.
- **After every completed edit batch, validate, commit and push before replying.**
  Do not wait for a separate request or a later checkpoint. No commit/push is
  needed for discussion that changes no files.
- Use `./scripts/save.sh <edited paths...>` with paths relative to the repository
  root. It stages those paths, checks staged whitespace, commits with the fixed
  message `Update project` when needed, and pushes normally. With no paths, it
  uses already-staged changes; inspect them first. Do not spend time composing
  commit messages during this project phase.
- Run the applicable documentation checks before saving. Do not include unrelated
  staged changes. If a check, commit or push fails, report the blocker; never force
  push, bypass hooks or discard changes to make it succeed.
