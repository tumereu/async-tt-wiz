# Project instructions

- **Implementation is authorized — D-304.** The owner explicitly authorized the
  agreed entire-R1 implementation loop; see the
  [authorization record](docs/agent/implementation-authorization.md). Stage 4 app
  code/scaffolding within the plan is now allowed. Earlier specification-only/
  not-authorized stage wording is historical, not a current coding prohibition;
  it does not approve extra product behavior or waive checks/release gates. Read the
  [design index](docs/README.md) and [session handoff](docs/agent/session-handoff.md).
- Preserve unrelated user work. Inspect Git status before editing and include
  only the files belonging to the current task when saving.
- **Authorized implementation loop — D-300–D-304.** Read the
  [task plan](docs/agent/implementation-plan.md),
  [fresh-session/review contract](docs/agent/implementation-loop.md) and
  [active progress record](docs/agent/implementation-state.json). D-301 initially
  authorized isolated environment/tooling smoke tests and planning against the
  finished UI packet as a good-enough starting point, not a perfect/frozen UI or
  application implementation; D-304 now separately authorizes implementation.
  The loop uses one fresh agent per bounded
  implement/review/fix/verify unit, saving and exiting before the next agent.
  Independent reviews follow coherent capability groups, not every task. Focus
  findings on evidenced, credible effects on real use: usability, responsiveness,
  performance, crashes, accepted-state integrity/recovery and harmful races.
  This is a casual cooperative simulator, not a mission-critical or competitive
  system. No style nitpicks, speculative hardening or superseded password-reset/
  message-order guarantees as blockers. Preserve basic security, current agreed
  outcomes and required tests; no invented behavior, silent omissions or bypasses.
  D-302 separately authorizes building/qualifying the
  [loop driver](scripts/implementation-loop.py); D-304 authorizes its application scope.
  Every driver role must use **`openai/gpt-6.1-sol#high`**, checked against session
  metadata with no fallback. See [driver operations](docs/agent/implementation-runner.md).
  **Autonomous troubleshooting — D-305:** the owner prefers spending development
  quota on solving problems, including whole/further fresh sessions, to halting
  unattended work on recoverable build, dependency, test-tool, save or orchestration
  errors. Diagnose, repair, test and continue toward the specifications; the first
  failed command is not a blocker. Use evidence-backed troubleshooting checkpoints
  for longer repairs, not metadata churn or false completion. Scoped driver/prompt
  repairs are allowed with reported provenance and passing runner regressions;
  do not expand permission grants, change the model/authorization, weaken checks,
  discard work, or bypass review/release obligations. Reviewers/verifiers still
  hand repairs to a fresh fixing session instead of certifying their own changes.
  Missing release rights/signing/operator readiness or physical hardware blocks
  only the corresponding release/measurement claim, not other implementation work.
  The application state is `active/authorized` with D-304 recorded. The operator
  supplies run/time budgets when launching; do not start another loop alongside
  one already running. Qualify actual build/device commands in I01 under the
  existing permissions, retain unavailable-resource blockers and all release gates.
  **Supervised unattended run — D-306:** the owner selects 3,000 sessions and a
  1,209,600-second per-session timeout, with a blocking controller call and no tool
  timeout. After a controller exit, inspect completion; if unfinished, diagnose,
  repair, validate/save/push, recover the owned handoff and restart rather than
  returning a recoverable halt to the sleeping owner. Adjust documented dependencies/
  child plans around evidenced forbidden/external work so all safe feasible scope
  continues; retain the original unmet obligations. Exact model, one writer, scoped
  grants, independent reviews, truthful tests/release gates and explicit stop remain.
- **Keep UI review refreshable and feedback cumulative.** After any UI-document
  edit batch, rebuild and publish a new HTML edition of the **full** review document
  at the exact existing URL: `https://desktop-b2kalvm.tail27cccc.ts.net:8443/review.html`.
  Replace the served edition in place so the owner's browser refresh continues the
  review; do not substitute a partial document or a new/versioned URL. Verify the
  published artifact. Record each round's owner feedback durably in the UI packet's
  feedback/decision record and keep applying all earlier decisions unless the owner
  explicitly changes them. Preserve provenance and mark explicit supersessions;
  never lose earlier feedback during regeneration or iterative rounds. Ask about
  genuine uncertainties instead of silently inventing or reversing decisions.
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
- **Comprehensive preparation and autonomous implementation (D-256/D-304).** During the
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
  Do not invent product behavior, bypass failed checks or expand scope. D-304 now
  supplies that explicit implementation/stage authorization; it does not launch a
  driver by itself or waive operator budgets, stop control or genuine blockers.
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
  staged changes. If a check, commit or push fails, diagnose and repair the cause
  within the authorized scope and retry the same save after validation. Escalate
  only a genuinely unresolved boundary after troubleshooting; never force push,
  bypass hooks or discard changes to make it succeed.
