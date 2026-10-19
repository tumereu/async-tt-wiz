# Tier 3 — Detailed behavior contracts

These retain the detailed existing specifications and stable requirement IDs.
Their original chapter numbers are retained for traceability, not reading order.
Confirmed choices and proposals remain distinct; this reorganization approves
neither unfinished behavior nor implementation.

| Contract | Detail |
| --- | --- |
| [Product](product.md) | Requirements, scope proposals, trust model, success criteria. |
| [Domain model](domain-model.md) | Card/object identity, state, containers, references, invariants. |
| [Board and controls](board-and-controls.md) | Touch, selection, movement, piles, menus, table controls. |
| [Game actions](game-actions.md) | Capability coverage, bulk actions, preservation and explicit exceptions. |
| [Combat and Stack](combat-stack-and-projections.md) | Projections, spatial placement, grouping, lifecycle, shortcuts. |
| [History and Undo](history-and-undo.md) | Review, events, grouping, interruptions, recovery. |
| [Optimistic-locking review batch](optimistic-locking.md) | All action-category lock proposals in one numbered review; only explicitly confirmed portions are requirements. |
| [Automatic grouping](history-grouping.md) | Confirmed timeline grouping/closure rules, tentative policies, MTG face-change rules check and remaining inventory. |
| [Communication](visual-action-language.md) | Literal action presentation, pings, links, optional text. |
| [Android experience](android-experience.md) | Proposed journeys, navigation, accessibility, device/error states. |

The [Tier 2 overview](../02-experience/behavior.md) explains the combined
experience. [Tier 4](../04-technical/README.md) proposes supporting mechanisms;
[Tier 5](../05-verification/README.md) contains expected verification outcomes.
Decision provenance and worklists are separate under [agent records](../../agent/README.md).
