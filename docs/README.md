# Design documentation — high level to low level

**Current behaviour lives in the [feature catalog](features/README.md)**; the
direction is in the [roadmap](roadmap.md), and approved changes not built yet are in the
[backlog](backlog.md). The tiered design below is older and
partly superseded: where it disagrees with the catalog, the catalog wins.

Start with **[Tier 1: product and usability principles](design/01-product/overview.md)**.
You should not need decision logs or an agent handoff to understand the app.

## Design tiers

| Tier | Read here | Purpose |
| --- | --- | --- |
| **1. Product** | [Principles and boundaries](design/01-product/overview.md) | What the app is; the few consistent rules users should learn. |
| **2. Experience** | [User-facing behavior](design/02-experience/behavior.md) · [Scope and gaps](design/02-experience/gaps.md) | How the whole app behaves, including approved exceptions; what remains unspecified. |
| **3. Contracts** | [Detailed contract index](design/03-contracts/README.md) | Precise feature behavior, invariants, state changes, recovery and stable requirement IDs. |
| **4. Technical** | [Technical design index](design/04-technical/README.md) | Proposed storage, sync, architecture, APIs, security and operations. |
| **5. Verification** | [Acceptance and test coverage](design/05-verification/README.md) | Expected outcomes and correctness/failure tests; not an implementation claim. |

These are **levels of explanation**, not implementation stages. Reading farther
down should add precision, not introduce a new usability model.

## Current status

R1 is implemented in the Android app and backend.
The tiered design documents are the spec, not a test execution report.
Decision history is retained in [docs/decisions.md](decisions.md).
See the [self-hosting guide](../deploy/README.md) to play with another person.

The [cubes, pods, and drafting design](design/03-contracts/cubes-pods-and-drafting.md)
and [profiles and avatars](design/03-contracts/profiles-and-avatars.md) are implemented
feature additions (2026-10-26). They cover collection cubes,
MTG set boosters, asynchronous 2–8-player drafting, deckbuilding, independent 1v1
games within a pod, and player avatars.

## How the tiers fit together

- Tier 1 expresses the general rules. Tier 2 describes their combined user-facing
  behavior. Tier 3 owns the detailed contracts; higher tiers summarize and link
  rather than independently inventing feature rules.
- Lower levels must not quietly contradict the higher-level model. Existing
  approved exceptions are explicit in Tier 2 and detailed in Tier 3.
- If summaries, older contracts and decision history disagree, flag the mismatch,
  establish the current approved behavior, and synchronize the relevant tiers.
  Do not silently choose whichever wording is convenient.
- Technical proposals and acceptance scenarios cannot approve a feature or fill
  a missing product decision by themselves.
- Each detailed behavior has a primary contract. A change to approved behavior
  updates that contract, the affected summaries, verification outcomes and decision
  provenance together. Requirement/scenario IDs remain stable across file moves.
- The tier split preserves the existing detail; it is not a claim that all old
  duplication or wording drift is fixed.

## How to read normative language

- **Confirmed**: explicitly requested or selected by the project owner.
- **Proposed**: a concrete design for review; not yet an approved requirement.
- **Open**: unresolved and potentially architecture-changing.
- **Must / must not**: an obligation within the stated confirmed or proposed
  design. A proposed design using “must” is still subject to approval.
- **Example**: illustrative, not a separate requirement.

Unless marked confirmed, the detailed behavior in these documents is proposed.
Do not silently promote a proposal to an approved requirement during coding.
