# 13. Combat, Stack, and projections

Status: required release scope in delivery stage 1, behavior specification
(D-115). D-112–D-125 require case-by-case design before implementation. The
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
   dismissal timing and assignment cardinality/order are open. D-122 fixes
   removal effects without choosing automatic/manual lifetime. D-116 makes
   creation a separate action; D-117's multiselect shortcuts also create it.
2. **Projections (D-112/D-123):** creating a virtual card occurrence leaves the
   actual card at its battlefield position. Attackers/blockers use this
   representation. A projection references the same source card; it is not a
   Clone, token, duplicate card instance, or second actual containing location.
   Card state is live/shared and card actions write through to the source,
   including subsequent moves. D-124/D-125 distinguish combat and Stack lifetime
   after source departure. Exact identity/schema and missing-source presentation
   remain open, not live-versus-snapshot or independent-state routing.
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
Creating/assigning blocker projections leaves sources at their battlefield
positions; later card-directed actions through projections affect the source
(D-123). Exact entry origin, gesture disambiguation, reassignment,
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
exact endpoint identity and same-operation cleanup remain behavior questions.
D-124/D-125 settle which projections disappear on source departure. Links with
other endpoint kinds need separate review.

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
   repeated combat, End turn, assignments after an attacker disappears, and
   historical rendering. Source departure removes its combat projections (D-124).
4. **Stack entry:** real card versus projection controls; ability-source context;
   multiple projections from one source; placement/order, labels, and optional
   communication. No ability picker/text entry is approved by implication.
5. **Stack exit:** concrete buttons/gestures, metadata and multi-face categories,
   ambiguous/missing data, participant/destination placement, and manual overrides.
   Projection removal must be distinguished from source-card Remove/move.
6. **Shared correctness:** projection/assignment identities, reference lifetime,
   occurrence-versus-source operation disambiguation, cover/inspection/exposure,
   geometry/selection, action and group boundaries, undo, branches, offline
   persistence, retries, and concurrent source/reference edits. Missing-source
   Stack presentation/actions still need design. Do not reuse Clone/removal
   semantics blindly.

Every behavior case must be covered in stages 1–2 and its UI decided in stage 3.
No required behavior is silently omitted or postponed until coding; technical
design for implementation slices is distinct from this behavior completeness.

## 13.4 Confirmed cleanup and shared card state

D-122: removing the combat area removes its projections and the projection-to-
projection links incident to them, but leaves real cards and their current state
unchanged. It does not move or untap originals or undo edits already applied
through projections. Other areas' projections are not removed merely by this
area cleanup; a cross-area projection link disappears if its endpoint here does.
Exact removal controls, automatic/manual lifetime, and action/undo composition
remain open. Normal history/branch recovery still applies.

D-123: card actions through a projection always apply to its real source card.
Projections are live views, not editable snapshots. Tap/untap, corners, counters,
notes, faces/cover, and other card-state changes refer to the same shared card
state, whether invoked through the source or a projection. No separate local
copy of those card fields is introduced for combat or Stack occurrences.

Dragging a projection into Graveyard or another real destination moves the actual
card there, using the same stable identity, validation, resets, and token-removal
exceptions as an ordinary move. The source's position is not permanently pinned
by having a projection. Virtual slot placement/combat assignment and projection
removal are distinct occurrence operations; do not turn combat-area cleanup or
the approved Stack Remove projection into source-card deletion. Exact card-Remove
versus occurrence-Remove entry and link/ping endpoint routing under shared edits
remain behavior questions. D-124/D-125 settle lifetime after source departure;
missing-source Stack presentation/actions still require review.

## 13.5 Confirmed area-specific lifetime (D-124/D-125)

Do not apply a universal remove-the-dragged-projection policy. Lifetime depends
on the occurrence's role, not on which view was used to edit/move the card.

**Combat (D-124):** anything that removes the real card from the battlefield
removes all its combat projections, whether attackers or blockers. This includes
a move through the original, a combat projection, or a Stack projection, and
explicit source-card Remove/token disappearance. Incident projection links
disappear under D-120. Moving/repositioning within the battlefield does not meet
this departure condition. There is no extra manual projection-cleanup step.
Assignments involving a removed attacker still need a behavior decision; do not
silently remove the real blocking cards or assume their projections disappear.

**Stack (D-125):** the source leaving the battlefield, moving elsewhere, or being
removed does not remove its Stack projections. Each effect occurrence remains
until explicitly removed/resolved, including when a move was performed through
that Stack projection itself. Removing/resolving the occurrence removes its
incident projection links, not the source card, and does not apply card-text
effects automatically. Links between surviving projections are not removed just
because their sources changed location. No priority or automatic resolution is
introduced.

If the real source no longer exists in current state (for example a removed
token), preserve the Stack effect occurrence. Its displayed reference/inspection
and applicability of source-card actions still need design; do not invent a new
actual card or silently remove the effect. Normal history/undo/branch/offline
recovery applies to both lifecycle rules; exact operation composition/concurrent
reference handling remain under review.

## 13.6 Next attacker-removal assignment behavior — proposed, not approved

When an attacker projection disappears, keep its surviving blocker projections
in blank/unassigned combat slots, without moving or changing their real cards.
This would reuse the valid unassigned-blocker state (D-118). It does not decide
phase-triggered combat-area cleanup or missing-source Stack presentation.
