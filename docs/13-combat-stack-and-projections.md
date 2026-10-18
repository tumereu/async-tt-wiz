# 13. Combat, Stack, and projections

Status: required release scope in delivery stage 1, behavior specification
(D-115). D-112–D-121 require case-by-case design before implementation. The
proposal to stop at Untap/Draw shortcuts and ordinary controls for every other
phase is rejected. No implementation is authorized by these capability decisions.

This is the primary contract for combat/Stack projections and contextual Stack
actions. Existing gesture requirements remain in [board controls](10-board-and-controls.md);
history, automatic queuing/sync, cooperative access, and undo still apply.
Delivery order: behavior specification → adversarial review/refinement → UI
drafts/decisions → iterative implementation. User-specified interaction requirements
are recorded now; UI drafts/exact geometry remain stage 3, not current work.

## 13.1 Confirmed capabilities

1. **Temporary combat area (D-112):** present attacking and blocking cards in an
   area that makes per-attacker blocker assignment easy. Exact appearance,
   dismissal timing, assignment cardinality/order, and cleanup are open. D-116
   makes creating the area a separate action; D-117's multiselect declaration
   shortcuts also create it when absent.
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

## 13.2 Confirmed combat action model

### Separate actions (D-116)

Creating the combat area, declaring an attacker there by creating its projection,
and tapping that attacker are distinct supported actions. Plain declaration does
not implicitly tap the source. Tapping is a change to the real card's orientation,
not movement out of its original battlefield position. This replaces the earlier
proposal that every attacker projection taps its source by default.

An empty combat area and an untapped declared attacker are valid intermediate
states. These separately invoked actions keep their normal history/undo and
interruption boundaries; do not force them into an indivisible declaration.

### Multiselect declaration shortcuts (D-117)

In multiselect mode, when the phase/step is appropriate, offer both:

1. **Declare + tap:** declare the selected attackers as projections and tap their
   sources, retaining the sources' containing locations and battlefield positions.
2. **Declare without tapping:** declare their projections without changing source
   orientation; this does not mean forcibly untapping already-tapped sources.

Both automatically create the combat area if absent, without a separate manual
creation prerequisite. This is explicit composition, not inferred card text.
Exact quick-action phase/step visibility, duplicate handling, empty/mixed
selections, single-card entry when the area is absent, and the internal
action/undo/group boundaries of combined shortcuts remain behavior questions.
Do not infer a gesture or final layout beyond the specified multiselect offerings.

D-121 requires both combined declaration actions and their individual actions
to remain reachable through a full targeted-action path, such as a card context
menu. Nothing is available only as a quick action. Phase/step and multiselect
conditions govern quick offerings, not access to the underlying action; retain
normal structural/applicability validation. Exact menu presentation is stage 3.

### Blockers and adaptive spacing (D-118)

Blockers can be dragged to a specific attacker to assign them to that attacker.
They can also occupy a blank slot with no attacker for special/manual purposes;
an unassigned blocker is valid, not an invalid missing-attacker reference.
Sources remain at their original battlefield positions under the projection
model. Exact entry origin, source-versus-projection hit routing, reassignment,
cardinality/order, and removal behavior still need review.

Attackers receive more space as more blockers are assigned, keeping their blocker
groups understandable instead of forcing a fixed-width layout. This adaptive
spacing requirement is confirmed; pixel geometry, scrolling, and layout mechanics
are stage 3. Do not impose the ordinary three-actual-card pile limit on these
groups merely because blockers are visually adjacent; exact capacity is open.

### Ordered pings and projection links (D-119/D-120)

Pings show their order number among **all pings in the same grouped event**, not
a separate count per card, attacker, source, or purpose. Players can use this
sequence to communicate damage-assignment order or anything else; there is no
damage-order rules object or automatic calculation. Numbers are automatic
presentation, not user-entered labels/styles. Event scope/toggle/history rules
remain; exact ordering, removal/re-add numbering, and coalescing still need review.
See [the primary ping contract](11-visual-action-language.md#114-pings-point-they-do-not-declare-mechanics).

Links can connect projections to projections. When an endpoint projection
disappears, those links disappear with it (D-120), not get transferred to source
cards or left as live dangling lines. This is distinct from deleting a source
card or a source-to-source link. Preserve normal historical/undo/branch recovery;
exact endpoint identity, same-operation cleanup, and source-removal cases remain
behavior questions. Links with other endpoint kinds need separate review.

## 13.3 Review sequence and unresolved contracts

Review behavior in these cases separately; capability approval is not approval
of unanswered semantics or controls. UI drafts/concrete gesture choices are
stage 3; schemas/technical choices follow in the relevant implementation work:

1. **Attacker entry:** quick-action visibility steps, attack destination, single
   versus multi-source/duplicate behavior, empty/mixed selections, slot order/capacity,
   cancellation, and combined shortcut boundaries. Separate declaration/tap and
   multiselect variants are settled, not proposals.
2. **Blocker entry:** source/projection routing, exact associations/cardinality,
   multiple blockers, reassignment/order/removal, and corrections. Specific-attacker
   drops, blank unassigned slots, and adaptive attacker spacing are settled.
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

## 13.4 Next combat cleanup behavior — proposed, not approved

Removing the combat area removes its projections and their projection-to-
projection links, but leaves original cards, positions, and current orientation
unchanged. In particular, removing combat presentation does not untap attackers.
Exact removal controls and manual versus phase-triggered lifetime are not yet
chosen. Undo/history/branch recovery must preserve recorded state normally.
