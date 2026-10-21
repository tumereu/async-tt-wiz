# Delivery workflow

These are the existing delivery gates and agent implementation instructions,
separated from [acceptance scenarios](../design/05-verification/acceptance.md).
The original section numbers remain for stable links.

**Current stage: 4 — implementation authorized by D-304.** See the
[explicit owner record](implementation-authorization.md). The owner permits the
agreed entire-R1 plan to proceed using the current specifications/reviews/UI packet
as its baseline. Earlier stage/no-code statements and readiness checklists below
are preparation history, not a requirement to obtain the same authorization again.
This does not certify every checklist assertion, executable test or external gate
passed; actual qualification and honest blockers remain required.

## Saving edits (D-178)

After every completed edit batch, run the applicable checks and then
`./scripts/save.sh <edited paths...>` from the repository root. Do this before
replying, without waiting for another commit/push request. The helper uses the
fixed message `Update project`; custom messages are unnecessary in this phase.
Discussion without file edits does not require a commit.

Inspect status/staged changes and preserve unrelated work. Pass only task-owned
paths; with no arguments the helper commits the existing index. It skips an empty
commit but still pushes, so the same command can retry a failed push. Failures
stop the script and must be reported; do not force push or bypass hooks/checks.
This workflow automation does not authorize application implementation.

## 8.3 Specification readiness gate

D-115 supersedes the previous blanket gate requiring every UI and technical
artifact before leaving the initial design round. Readiness is staged:

**Specification review objective — D-255/D-256:** actively seek all possible
inconsistencies and missing outcomes before implementation. Audit agreement across
tiers and across request/event/group semantics, state and lifecycle transitions,
identity/reference lifetime, compound operations, concurrent interleavings,
failure/recovery and verification oracles. Record concrete counterexamples; apply
existing rules, correct ambiguous wording and retire mistaken findings. Distinguish
derived completion from a genuine owner choice under D-237. Do not stop with the
current finding list, defer observable semantics to coding or mistake a clean
documentation check for exhaustive behavior verification. The objective is
comprehensive clarity, not a claim that all conceivable defects can be ruled out.

### Stage 1 → stage 2: behavior specification complete

- [ ] Agreed release scope and all supported behavior cases are covered.
- [ ] Per-action state changes, unchanged fields, exceptions, no-ops, structural
      failures, cancellation, and recovery are defined.
- [ ] Projections, combat/blocker assignment, Stack contextual actions, and their
      source/lifecycle cases are specified, not omitted as future coding work.
- [ ] Branching, review, groups, undo/recovery, offline read-only access,
      online-only actions and connection-loss/concurrent outcomes are defined
      (D-182 excludes offline gameplay queues from initial delivery).
- [ ] Acceptance scenarios state testable outcomes for those contracts.
- [ ] The owner agrees the behavior specification is ready for review.

**Historical stage-1 prerequisite:** UI drafts and final schema/API/library/deployment choices
are not prerequisites for completing this stage. Concrete technical questions
remain tracked; they must not replace missing behavior decisions.

### Stage 2 → stage 3: adversarial review/refinement complete

- [ ] Challenge the full behavior specification with counterexamples, edge cases,
      contradictions, and complete manual-play scenarios.
- [ ] Refine the specification and acceptance outcomes to resolve findings.
- [ ] The owner agrees the reviewed behavior is ready for UI work.

### Stage 3 → stage 4+: UI decisions complete; implementation authorized

- [ ] UI drafts and decisions cover screens, controls/gestures, layout, accessible
      alternatives, and error/recovery states against the reviewed behavior.
- [ ] Previously approved interaction requirements are incorporated/reconciled.
- [x] The owner authorizes iterative implementation (D-304, 2026-10-21).

Necessary technical/schema/API/operations design is prepared for the relevant
implementation slice. This is not an additional requirement to finish every
future technical artifact globally during stage 1.

## 8.4 Confirmed delivery stages

1. **Behavior specification.** Define the whole agreed release behavior
   case by case, including the newly required combat/Stack conveniences.
2. **Specification adversarial review and refinement.** Review behavior before UI.
3. **UI drafts and decisions.** Decide presentation and concrete interactions.
4. **Implementation — current, D-304.** Develop and verify
   approved slices, refining explicit contracts as needed.

Stages 1–3 were documentation/design only. D-304 now explicitly authorizes stage-4
implementation against the agreed baseline; earlier no-code prohibitions are
superseded for authorization, not blanket approval of unrelated proposals.

## 8.5 Later specification-driven implementation loop

During stage 4+, after the preceding stages and implementation authorization:

**Preparation update — D-300–D-302:** the owner requests a thin outer loop that
launches a fresh agent for one bounded implementation, grouped behavioral review,
fix or targeted verification unit; it validates/saves/pushes and exits before the
next agent. Independent review is after coherent capabilities, **not each task**;
tests and relevant validation still accompany every task. The
[implementation plan](implementation-plan.md), [execution/review contract](implementation-loop.md)
and [active state](implementation-state.json) define the work/dependency
and handoff details. This sequencing refines the generic cycle below; its
per-slice checks are not mandatory independent reviewer sessions after each slice.

Review prioritizes actual casual cooperative use: usability, responsiveness,
realistic performance, crashes, accepted-state loss/duplication, broken recovery
and harmful races. Require concrete evidence/credible triggers and player impact;
do not invent competitive/mission-critical guarantees or resurrect D-289's
withdrawn reset/message-order machinery. Current functional/security obligations
and tests remain. UI is the owner's good-enough planning baseline, not perfect or
frozen. The separately authorized
[local environment smoke](e2e-environment-smoke-2026-10-21.md) passed isolated
one/two/three-device checks; normal-app/backend acceptance is still unimplemented.
D-302 separately authorized the [driver and isolated qualification](implementation-runner.md),
which passed 25 automated tests and a six-session real-agent cycle with every role
pinned to `openai/gpt-6.1-sol#high`. This qualifies tooling, not application behavior.
Those preparation decisions alone did not grant implementation or a stage change.
D-304 now separately grants that authorization and the real state is active/
authorized. Actual build/test permission qualification, resource evidence and
explicit operator-selected run-count/wall-time budgets remain required.

**Autonomous through completion — D-256:** once this loop is authorized and begins,
continue through the entire agreed scope without asking for routine slice approval
or a new "continue" after each milestone. Establish the agreed scope and observable
completion criteria before starting; a finished slice is not the end of the loop.
Select the next slice, carry out the cycle below and repeat until all those criteria
are satisfied. Progress updates are not approval gates. This does not authorize
starting work outside D-304's approved scope or bypassing actual verification.

1. Select an approved vertical slice and its requirement/test IDs.
2. Complete necessary technical design for that slice (schema/API/storage/etc.)
   and write executable acceptance/model tests matching the specified contract.
   Follow the [full-stack E2E strategy](../design/05-verification/e2e-strategy.md):
   dynamic synthetic data/art, running real backend and normal Android app,
   reset between independent tests, setup-only user/deck helpers and otherwise
   UI-only user actions (D-243/D-244).
3. Implement the slice without broadening scope.
4. Check behavior, failure recovery, accessibility, and compatibility.
5. If a documentation gap or technical detail is resolved by approved rules and
   constraints, make the derivation explicit and update contracts/tests/code
   together autonomously. If an actual product choice or blocker remains, preserve
   the evidence, pause only the affected work and obtain the necessary decision or
   report the blocker. Continue independent approved work where safe. Never invent
   behavior, broaden scope, bypass failed checks or hide validation/save failures.
6. Record requirement-to-code/test traceability, validate and save completed batches
   under the saving rules, then proceed to the next approved slice automatically.
7. Finish only when the agreed scope and completion criteria are met, with required
   verification passing and no concealed blockers or unfinished in-scope work.
   Report actual completion or the genuine blocker preventing it, not merely that
   one slice or one coding pass is complete.

The older candidate order (core → local table → sync → history → toolkit →
entry/catalog) is superseded by the source-linked B1–B8 plan, now authorized under
D-304. It gets normal entry/decks/lobby
and real-UI test wiring in place before claiming gameplay E2E; shared server
authority is the default from the first shared behavior, never a local game to
retrofit later. The plan can be refined within approved scope during execution.
