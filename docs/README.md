# Specification index

Status: **draft, design round 1; corrected to a generic, rules-agnostic engine
recording literal board actions and pings, not inferred game mechanics**.

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

It does not mean predicting every future feature. It means all behavior within
the agreed first-release scope has an implementable, testable contract, with no
unresolved decisions needed to build that release.

Before implementation, specify and approve:

- Product boundaries and every supported user journey, including cancellation,
  failure, recovery, empty, and loading states.
- Generic action semantics, permissions, area/container behavior, and manual
  use-case coverage without game-specific rule semantics.
- Branching, reviewing, grouping, randomization, and complete undo semantics.
- Every persistent entity, identifier, schema, invariant, and migration rule.
- Exact API operations, payloads, errors, concurrency conditions, retry rules,
  authentication, and protocol compatibility.
- Android navigation, interaction states, accessibility, and visual wireframes.
- Card data, images, decks, legal attribution, and cache behavior.
- Hosting, security, backup, restore, monitoring, and support procedures.
- Quantified performance and reliability targets and an acceptance-test matrix.
- The implementation plan, including how deviations return to specification.

This first draft establishes the behavioral model and exposes remaining gaps.
It deliberately does **not** claim to satisfy the entire list yet.
