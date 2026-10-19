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

1. Select an approved vertical slice and its requirement/test IDs.
2. Complete necessary technical design for that slice (schema/API/storage/etc.)
   and write executable acceptance/model tests matching the specified contract.
   Follow the [full-stack E2E strategy](../design/05-verification/e2e-strategy.md):
   dynamic synthetic data/art, running real backend and normal Android app,
   reset between independent tests, setup-only user/deck helpers and otherwise
   UI-only user actions (D-243/D-244).
3. Implement the slice without broadening scope.
4. Check behavior, failure recovery, accessibility, and compatibility.
5. If a gap is discovered, stop that behavior's implementation, propose a spec
   amendment, obtain the necessary decision, then update tests and code together.
6. Record requirement-to-code/test traceability and proceed to the next slice.

Proposed order: deterministic state/history core; local Android table and online
publication/request recovery; automatic shared sync; review/branch/undo; full generic
tabletop toolkit and manual MTG coverage; notifications/catalog/decks; operational
hardening. This is an unapproved candidate order, not a settled delivery plan or
current implementation authorization. Plan actual slices after stages 1–3.
