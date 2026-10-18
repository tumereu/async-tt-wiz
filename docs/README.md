# Specification index

Status: **delivery stage 1: behavior specification**. The engine records literal
actions and explicit manual-play conveniences, not inferred card rules. Required
combat/Stack projection behavior is being designed case by case.

## Confirmed delivery sequence (D-115)

1. **Behavior specification — current stage.** Cover the entire agreed release
   behavior, including exceptions, failure/recovery, and acceptance outcomes.
2. **Adversarial review and refinement of that specification.** Challenge and
   resolve behavioral gaps before proceeding to UI drafts.
3. **UI drafts and decisions.** Work out screens, layouts, concrete controls,
   gestures, and accessibility against the reviewed behavior. Existing approved
   interaction requirements are inputs, not permission to skip this stage.
4. **Implementation in an iterative loop.** Design the necessary technical slice,
   test, implement, verify, and refine through explicit specification decisions.

No implementation is currently authorized. Stage 1 does not require final UI
drafts or every technical schema/API/library decision. Earlier proposed rounds
and blanket all-technical-details-first gates are superseded by this sequence.

## Reading order

1. [Product contract](01-product.md): goals, confirmed requirements, scope.
2. [Domain model](02-domain-model.md): cards, zones, state, identities, invariants.
3. [History, review, and undo](03-history-and-undo.md): the central async protocol.
4. [Game action catalog](04-game-actions.md): generic tabletop capabilities and
   manual MTG use-case coverage.
5. [Android experience](05-android-experience.md): screens and interaction flows.
6. [Synchronization and storage](06-sync-and-storage.md): offline and concurrency.
7. [Architecture and operations](07-architecture-and-operations.md): proposed
   system boundaries and operational requirements.
8. [Verification and delivery](08-verification-and-delivery.md): acceptance
   scenarios and the specification-to-implementation process.
9. [Decisions and open questions](09-decisions.md): what is agreed and what must
   still be decided.
10. [Game board and controls](10-board-and-controls.md): drag/snap, card gestures,
    diagonal piles, proposed areas, and async intent/ping communication.
11. [Visual action language](11-visual-action-language.md): literal action records,
     action-scoped pings, and grouping without interpreting game mechanics.
12. [Step-by-step touch action review](12-touch-action-review.md): capability
    worklist and pending small-group touch decisions; approved behavior stays in
     the primary contracts above.
13. [Combat, Stack, and projections](13-combat-stack-and-projections.md): required
    temporary combat area, virtual card occurrences, blocker assignment, and
     contextual Stack actions; case-by-case interaction review in progress.
14. [Session handoff](14-session-handoff.md): current pause/resume context,
    confirmed latest decisions, unresolved behavior, and a suggested next question.

## How to read normative language

- **Confirmed**: explicitly requested or selected by the project owner.
- **Proposed**: a concrete design for review; not yet an approved requirement.
- **Open**: unresolved and potentially architecture-changing.
- **Must / must not**: an obligation within the stated confirmed or proposed
  design. A proposed design using “must” is still subject to approval.
- **Example**: illustrative, not a separate requirement.

Unless marked confirmed, the detailed behavior in these documents is proposed.
Do not silently promote a proposal to an approved requirement during coding.

## Source of truth and changes

- Requirements use stable IDs so implementation and tests can link to them.
- `09-decisions.md` records owner decisions and outstanding questions.
- Each behavior belongs in one primary document; other documents link to it.
- When a decision changes, update its primary contract, examples, tests, and
  decision record together. Git preserves earlier specification versions.
- The repository contains documentation only until explicit implementation
  approval. Examples of payload fields are design notation, not executable code.
- Continue each design exchange with the next contextualized question or small
  numbered batch; the owner should not need to prompt for the next item (D-57).
- Commit and push documentation at natural review checkpoints, not necessarily
  after every small answer. Clearly marked proposals may remain in a checkpoint;
  a commit/push is not full-specification or implementation approval (D-57).

## What “fully specified” means here

It does not mean predicting every future feature. Stage 1 must specify all
agreed release behavior: supported actions and relationships, state changes,
exceptions/no-ops/errors, cancellation/recovery, offline/concurrent outcomes,
branch/review/undo semantics, and testable acceptance outcomes. Required combat/
Stack cases are part of that scope, not optional later implementation discoveries.

Stage 2 challenges/refines those contracts. Stage 3 makes their UI decisions.
Technical schemas, APIs, migrations, libraries, and deployment plans remain
tracked design work; determine them for the relevant implementation slices,
without confusing them with unfinished behavior or silently changing the spec.
See [delivery gates and loop](08-verification-and-delivery.md#83-specification-readiness-gate).

The behavior specification is still incomplete. No later stage has started.
