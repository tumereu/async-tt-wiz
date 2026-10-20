# Delivery workflow

These are the existing delivery gates and agent implementation instructions,
separated from [acceptance scenarios](../design/05-verification/acceptance.md).
The original section numbers remain for stable links.

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

**Current stage: 1.** UI drafts and final schema/API/library/deployment choices
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
- [ ] The owner authorizes iterative implementation after these stages.

Necessary technical/schema/API/operations design is prepared for the relevant
implementation slice. This is not an additional requirement to finish every
future technical artifact globally during stage 1.

## 8.4 Confirmed delivery stages

1. **Behavior specification — current.** Define the whole agreed release behavior
   case by case, including the newly required combat/Stack conveniences.
2. **Specification adversarial review and refinement.** Review behavior before UI.
3. **UI drafts and decisions.** Decide presentation and concrete interactions.
4. **Implementation, looping/iterative from here onward.** Develop and verify
   approved slices, refining explicit contracts as needed.

Stages 1–3 are documentation/design only; no prototype or implementation is
currently authorized. Earlier alternate design-round order is superseded.

## 8.5 Later specification-driven implementation loop

During stage 4+, after the preceding stages and implementation authorization:

**Autonomous through completion — D-256:** once this loop is authorized and begins,
continue through the entire agreed scope without asking for routine slice approval
or a new "continue" after each milestone. Establish the agreed scope and observable
completion criteria before starting; a finished slice is not the end of the loop.
Select the next slice, carry out the cycle below and repeat until all those criteria
are satisfied. Progress updates are not approval gates. This does not authorize
starting implementation during the current specification phase.

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

Proposed order: deterministic state/history core; local Android table and online
publication/request recovery; automatic shared sync; review/branch/undo; full generic
tabletop toolkit and manual MTG coverage; notifications/catalog/decks; operational
hardening. This is an unapproved candidate order, not a settled delivery plan or
current implementation authorization. Plan actual slices after stages 1–3.
