# 13. Combat, Stack, and projections

Status: required release scope in delivery stage 1, behavior specification
(D-115). D-112–D-114 require case-by-case design before implementation. The
proposal to stop at Untap/Draw shortcuts and ordinary controls for every other
phase is rejected. No implementation is authorized by these capability decisions.

This is the primary contract for combat/Stack projections and contextual Stack
actions. Existing gesture requirements remain in [board controls](10-board-and-controls.md);
history, automatic queuing/sync, cooperative access, and undo still apply.
Delivery order: behavior specification → adversarial review/refinement → UI
drafts/decisions → iterative implementation. New gesture/layout proposals below
belong to stage 3, not the current behavior decisions.

## 13.1 Confirmed capabilities

1. **Temporary combat area (D-112):** present attacking and blocking cards in an
   area that makes per-attacker blocker assignment easy. Exact appearance,
   creation/dismissal timing, assignment cardinality/order, and cleanup are open.
2. **Projections (D-112):** a card can virtually occupy an area/slot while its
   actual card remains at its original battlefield position. Attackers/blockers
   use this representation. A projection references a source card; it is not a
   Clone, token, duplicate card instance, or second actual containing location.
   Exact identity/schema and live-versus-snapshot presentation remain open.
3. **Stack naming and ability projections (D-113):** rename the former Cast area
   to Stack. Battlefield permanents can be projected there to signal an activated
   ability without moving the original card. This is an explicit player action,
   not ability detection, automatic cost payment, or priority tracking.
4. **Contextual Stack actions (D-114):** offer quick actions according to what is
   there: actual instant/sorcery card → Graveyard; actual permanent card →
   Battlefield; projection → remove that projection, not its source card. These
   are user-invoked conveniences, not automatic resolution or legality checks.
   Exact category/default routing and interaction must be designed case by case.

These explicit combat associations and projection roles refine the earlier
blanket no-mechanics-entities wording. They do not authorize inferred attack/
block legality, damage assignment/calculation, triggered abilities, payment,
resolution, or priority enforcement. Generic links and event pings keep their
existing meaning; blocker assignment is not silently inferred from either.

Ordinary three-card visual piles, special-pile containers, View/Reveal reference
trays, and projections are distinct concepts. Do not assume their capacity,
gesture, or lifecycle contracts are interchangeable.

## 13.2 Review sequence and unresolved contracts

Review behavior in these cases separately; capability approval is not approval
of unanswered semantics or controls. UI drafts/concrete gesture choices are
stage 3; schemas/technical choices follow in the relevant implementation work:

1. **Attacker entry:** creation gesture, source tapping, multi-selection, slot
   placement/ordering, duplicate projections, and attack-without-tapping.
2. **Blocker entry:** picking the attacker, projection creation, clear visual
   association, multiple blockers, reassignment/order/removal, and corrections.
3. **Combat lifecycle:** when the area appears/disappears, phase changes/skips,
   repeated combat, End turn, source moves/removal, and historical rendering.
4. **Stack entry:** real card versus projection controls; ability-source context;
   multiple projections from one source; placement/order, labels, and optional
   communication. No ability picker/text entry is approved by implication.
5. **Stack exit:** concrete buttons/gestures, metadata and multi-face categories,
   ambiguous/missing data, participant/destination placement, and manual overrides.
   Projection removal must be distinguished from source-card Remove/move.
6. **Shared correctness:** projection/assignment identities, reference updates,
   field-edit routing, cover/inspection/exposure, geometry/selection, action and
   group boundaries, undo, branches, offline persistence, retries, and concurrent
   source/reference edits. Do not reuse Clone/removal semantics blindly.

Every behavior case must be covered in stages 1–2 and its UI decided in stage 3.
No required behavior is silently omitted or postponed until coding; technical
design for implementation slices is distinct from this behavior completeness.

## 13.3 Next attacker behavior — proposed, not approved

Creating an attacker projection also taps the source by default, with a separate
without-tapping variant for exceptions. Neither option interprets card text to
decide whether tapping is required. Original containing location/position stays
unchanged under the already-required projection model.

Source tapping/variant behavior is the current question. Exact drag/tap/menu
entry and naming remain for stage 3. Multi-source behavior, duplicate projections,
capacity, lifecycle, blocker assignment, and action boundaries still need stage 1
behavior decisions; no drag gesture or final layout is approved by implication.
