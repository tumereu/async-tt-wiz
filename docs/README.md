# Design documentation — high level to low level

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

### UI review pilot

The owner has authorized a limited **[UI review packet](design/02-experience/ui/README.md)**
using Stitch, with portrait and landscape mockups, interaction notes and compiled
HTML/PDF review editions: the original non-gameplay pilot (D-290) and the
[gameplay-screen extension](design/02-experience/gameplay-screen-proposal.md) (D-291).
This authorizes these design artifacts, not application implementation, a passed
review/UI gate or approval of the proposed layouts.

## Separate agent records

[Agent records](agent/README.md) contain decision history, session handoffs,
review worklists, specification-audit notes and delivery instructions. They are
kept outside `design/` so process/history does not become the product description.

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
  duplication or wording drift is fixed. See the [audit](agent/specification-audit.md).

## How to read normative language

- **Confirmed**: explicitly requested or selected by the project owner.
- **Proposed**: a concrete design for review; not yet an approved requirement.
- **Open**: unresolved and potentially architecture-changing.
- **Must / must not**: an obligation within the stated confirmed or proposed
  design. A proposed design using “must” is still subject to approval.
- **Example**: illustrative, not a separate requirement.

Unless marked confirmed, the detailed behavior in these documents is proposed.
Do not silently promote a proposal to an approved requirement during coding.

## Current status

**Documentation only. The architecture review has been discussed and its agreed
simplifications/repairs applied (D-285–D-289), following the D-278–D-284 technical
batch. This does not certify every defect absent or pass the review/UI gates.
No application implementation or general stage transition is authorized; the
limited UI pilot above is separately authorized.** See
[review disposition](design/02-experience/gaps.md#architecture-review-disposition--d-285d-288).
The [follow-up disposition](design/02-experience/gaps.md#follow-up-architecture-review-disposition--d-289)
records the simpler UUID, authorization and ordinary HTTP definition-submission rules.

The confirmed delivery order remains: behavior specification → adversarial review
and refinement → UI decisions → explicitly authorized iterative implementation.
Finishing behavior does not require every technical detail or final screen draft.
The full [delivery gates](agent/delivery-workflow.md) are agent/process material,
not additional user-facing rules.
