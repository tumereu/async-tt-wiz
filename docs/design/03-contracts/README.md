# Tier 3 — Detailed behavior contracts

These retain the detailed existing specifications and stable requirement IDs.
Their original chapter numbers are retained for traceability, not reading order.
Confirmed choices and proposals remain distinct; this reorganization approves
neither unfinished behavior nor implementation.

| Contract | Detail |
| --- | --- |
| [Product](product.md) | Requirements, scope proposals, trust model, success criteria. |
| [Accounts, decks, and lobby](accounts-decks-and-lobby.md) | Accounts, invitations, import-only decks, lobby preparation/Ready, cancellation and undoable ending suggestions accepted by the other player before both return to lobby. |
| [Cubes, pods, and drafting](cubes-pods-and-drafting.md) | Approved 2026-10-26 feature design: reusable cubes/set sources, asynchronous 2–8-member drafting, pick/Build screens, private limited deckbuilding, and independent 1v1 challenges; short Draftmancer reference. |
| [Profiles and avatars](profiles-and-avatars.md) | Approved 2026-10-26: mandatory first-launch catalog download, card-art avatars, username/avatar change. |
| [Domain model](domain-model.md) | Explicit server/UI ownership matrix, accepted replicas versus latency-hiding predictions/recovery, card/object identity, containers, references and invariants. |
| [Board and controls](board-and-controls.md) | Touch, selection, movement, piles, menus, table controls. |
| [Game actions](game-actions.md) | Capability coverage, bulk actions, preservation and explicit exceptions. |
| [Operation composition](operation-composition.md) | Shared execution context, live input binding, ordered lowering and composite-command scope. |
| [Combat and Stack](combat-stack-and-projections.md) | Projections, spatial placement, grouping, lifecycle, shortcuts. |
| [History and Undo](history-and-undo.md) | Review, events, grouping, interruptions, recovery. |
| [Action acceptance and limited locking](optimistic-locking.md) | D-187 permissive acceptance, movement/Stack safeguards, and the superseded 25-rule review. |
| [Automatic grouping](history-grouping.md) | Confirmed timeline grouping/closure rules, tentative policies, MTG face-change rules check and remaining inventory. |
| [Communication](visual-action-language.md) | Literal action presentation, pings, links, optional text. |
| [Android experience](android-experience.md) | Proposed journeys, navigation, accessibility, device/error states. |

The [Tier 2 overview](../02-experience/behavior.md) explains the combined
experience. [Tier 4](../04-technical/README.md) proposes supporting mechanisms;
[Tier 5](../05-verification/README.md) contains expected verification outcomes.
Decision provenance is retained in the [decision log](../../decisions.md).
