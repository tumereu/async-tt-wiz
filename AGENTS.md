# Project guide

- **Deliver the entire agreed R1 (D-304/D-309).** Implementation is authorized;
  historical specification-only restrictions are superseded, not product scope.
  [Design index](docs/README.md) → applicable contracts and
  [task plan](docs/agent/implementation-plan.md). Resolve technical details and
  wording defects autonomously; ask only about genuine unresolved product choices.
  Do not invent behavior, restore superseded guarantees or silently omit requirements.
- **Execution has one canonical [loop contract](docs/agent/implementation-loop.md).**
  Units use the compact [prompt](docs/agent/implementation-agent.md), selected task,
  current state/findings and relevant sources—not the historical decision archive.
  Supervisors resume from the [handoff](docs/agent/session-handoff.md) and use
  [orchestrator instructions](docs/agent/implementation-orchestrator.md).
  No competing old loop or live-state migration from a preparation worktree.
- **Optimize elapsed delivery time.** Implement usable capabilities, run/fix affected
  checks, save and move on. Reuse valid child evidence; check missing integration
  seams, not repeated green full suites/clean builds/fault matrices. Final full-suite
  fixes, independent capability reviews, fresh fix verification and release gates
  remain mandatory. See [delivery-first policy](docs/agent/delivery-first-execution.md).
- **Keep authority and safety.** Shared game/history/communication/lifecycle changes
  are server requests with provisional client prediction; only personal UI is local
  ([ownership matrix](docs/design/03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259)).
  Preserve security/privacy, accepted-state integrity, scoped grants and truthful
  evidence. No paid/production/public deployment, secret acquisition or unrelated work.
  Exact model **`openai/gpt-6.1-sol#high`**, no fallback; original remaining budget,
  at most five launches before supervision assessment, 86,400 seconds per session.
- **Preserve work.** Inspect Git status/index before editing; own only assigned paths.
  Never discard unrelated changes, force push, bypass hooks or weaken failed checks.
  Diagnose recoverable tool/build/save failures and continue within authorization;
  preserve unresolved work and actual external blockers, never fake completion.
- **UI documentation only:** preserve cumulative feedback and explicit supersessions.
  After edits rebuild/publish the **full** review at the existing exact URL
  `https://desktop-b2kalvm.tail27cccc.ts.net:8443/review.html`, replace in place and
  verify served bytes. No partial/versioned substitute.
- **Every completed edit batch:** run applicable checks, inspect staged paths, then
  `./scripts/save.sh <owned repository-relative paths...>` to validate whitespace,
  commit (`Update project`) and push **before replying**. No paths uses the existing
  index: inspect it first. Repair failures and retry the same save, not implementation.
  Discussion without edits needs no save. Never certify a dirty/unpushed handoff.
