# Project instructions

- This project is specification-only. Do not create application code or scaffolding
  or advance delivery stages without the owner's approval. Read the
  [design index](docs/README.md) and [session handoff](docs/agent/session-handoff.md).
- Preserve unrelated user work. Inspect Git status before editing and include
  only the files belonging to the current task when saving.
- **Design only true gaps/edge cases (D-237).** Apply current agreed rules before
  asking the owner. A question must identify a concrete missing outcome/scope
  choice or a genuine ambiguity/contradiction those rules cannot resolve. Do not
  ask for approval of their consequences or treat every old "open" label as a
  gap. Separate contract/test completion, technical work and later UI decisions
  from owner decisions; use the [pruned gap list](docs/design/02-experience/gaps.md).
  Prefer one reviewable batch of identified edge cases with spec-derived
  resolutions and their sources, so the owner can revise or accept together.
  Clearly distinguish unresolved choices/proposals from those derived outcomes.
  Do not manufacture a next question when no true gap has been established.
- **Actively clarify ambiguous specifications (D-255).** Even when intended policy
  is settled, find and correct ambiguous wording, contradictions and mistaken
  assumptions. Distinguish documentation defects from genuinely undecided behavior;
  make derived outcomes explicit so the eventual long implementation loop need not
  guess. D-237 limits unnecessary policy questions, not useful consistency repairs.
- **Prepare comprehensively now; execute autonomously later (D-256).** During the
  specification phase, systematically seek inconsistencies and missing outcomes
  across all tiers, feature interactions, state/lifecycle transitions, concurrency,
  failure/recovery and verification. Do not stop at the existing finding list or
  leave ambiguity for coding; correct false findings too. Aim for exhaustive
  coverage without claiming that a review proves every possible defect absent.
  Once the owner explicitly authorizes the implementation loop after the design
  gates, continue through the entire agreed scope and completion criteria without
  routine per-slice approval or "continue?" checkpoints: design within approved
  constraints, implement, test, review, fix, validate, save and select the next
  slice. Resolve spec-derived/technical details autonomously; escalate only genuine
  blockers or owner decisions, continuing independent approved work where safe.
  Do not invent product behavior, bypass failed checks or expand scope. This future
  autonomy does not authorize implementation or a stage transition now.
- **Server authority is the default for game state (D-259).** Shared board,
  history/control, Undo/Redo, communication and lifecycle state change through
  requests to the server, with provisional client application for latency hiding.
  Only non-request UI state is personal/local. A device-held replica, prediction,
  cache or recovery queue is not an independently authoritative local game. Use
  the [state ownership matrix](docs/design/03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259)
  rather than inventing per-device variants of shared behavior.
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
