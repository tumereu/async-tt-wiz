# 13. Combat, Stack, and projections

Status: required release scope in delivery stage 1, behavior specification
(D-115). D-112–D-131 require case-by-case design before implementation. The
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
   assignment cardinality/order are open. D-122 fixes removal effects; D-127
   requires explicit closure and a combined advance-out-of-combat + close
   shortcut. D-116 makes creation separate; D-117's shortcuts also create it.
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
event/interruption/group mapping of combined shortcuts remain behavior questions;
D-131 supplies whole-user-action undo unless an exception is approved.
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
D-126 constrains removal-time layout: keep surviving blockers in place rather
than compacting/recentering them after an attacker or other blocker disappears.

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
3. **Combat lifecycle:** advance-and-close missing-area/out-of-combat handling,
   combined boundaries, repeated combat/area reuse, and historical rendering.
   D-127 requires explicit closure; D-128 fixes End of combat quick visibility
   and Postcombat main destination. Source departure removes combat projections
   (D-124); D-126 keeps surviving blockers unassigned in place.
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
Exact removal controls and action/undo composition remain open. D-127 requires
explicit closure; ordinary phase/turn changes do not close the area. Normal
history/branch recovery still applies.

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
D-126 keeps surviving blocker projections unassigned in place when their attacker
disappears; do not remove or move their real source cards.

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

## 13.6 Confirmed attacker-removal assignment and spatial stability (D-126)

When an attacker projection disappears, keep its surviving blocker projections
in their existing physical/tabletop positions and clear their attacker assignment.
They now occupy valid blank/unassigned slots (D-118); do not move them to a side
collection, pack them into different slots, or recenter them merely because their
attacker disappeared. Their real cards remain unchanged. If a blocker's own
source also leaves the battlefield, D-124 still removes that blocker's projection.

As blockers move to Graveyard or are otherwise removed, the area may shrink, but
that shrinkage must preserve the remaining blockers' tabletop positions rather
than automatically compact/reflow them. Spatial stability is a behavior requirement
now, not an optional stage-3 detail. Exact area bounds/geometry and rendering are
stage 3; this does not pin device pixels across ordinary pan/zoom or prescribe an
algorithm for D-118's spacing growth when blockers are added.

Preserve assignment and placement context for normal history/undo/branch/offline
review. D-127 separately fixes explicit area closure; missing-source Stack
presentation remains open.

## 13.7 Confirmed explicit closure and advance shortcut (D-127)

The combat area stays open until explicitly closed. Ordinary Next step, direct
phase/step changes, and End turn do not close it merely because tracking leaves
combat or starts a new turn. Individual source departures still remove their
own combat projections (D-124), without implicitly closing the area if empty.

Provide a quick action combining **advance out of combat + close combat area**.
Its closure uses D-122: remove combat projections/incident projection links,
preserve real cards' current positions/orientation/other state, and do not remove
Stack effects. This is an explicit combined action, not a phase-change listener
or automatic damage/resolution. Closing alone and advancing alone remain
available; the combined action also has a full non-quick path under D-121.

Handling outside combat/with no area, event-tree representation, and internal
atomic/interruption composition remain behavior questions. D-128 fixes the
destination/quick visibility; D-130 makes the combination one undoable unit.
UI wording/geometry follows stage 3.

## 13.8 Confirmed advance-and-close destination and quick visibility (D-128)

The quick action is offered only at **End of combat**, the last combat step,
not earlier in combat. It advances to **Postcombat main** and closes the combat
area under D-122/D-127, without changing turn player/number or real cards.
Do not offer it as an early prominent skip of remaining combat steps.

This phase/step condition governs quick visibility, not full action access
(D-121). Keep the non-quick combined path and separate phase advancement/closure
available. Ordinary Next step at End of combat advances to Postcombat main but
does not close the area. Exact missing-area behavior and full-path behavior
outside combat remain open; do not infer a later-phase rewind policy.

## 13.9 Confirmed combined-action decision policy and undo scope (D-129/D-130)

D-129 distinguishes invocation, undo unit, tree event/group, and atomic boundary.
D-131 refines this with a general default: **one user action, including its whole
combined group, is one Undo**. Tree events must remain separate wherever MTG
permits reactions at distinct points. Specify mappings and exceptions per action;
do not require fresh undo-scope approval for every combination. These are
designed event boundaries, not runtime priority or legality enforcement.

D-130: **advance out of combat + close combat area** is one undoable unit. One
Undo restores the prior phase/step and the complete combat area/projections/
assignments/incident links/placements, rather than requiring a separate undo for
each effect. Normal history/branch/recovery and dependent-work rules apply.
This does not yet choose one versus multiple tree events or approve collapsing
required response points. Untap/Draw + advance and declaration combinations also
use D-131's whole-user-action undo default; their reaction-point event mappings
and any exceptions still need review.

## 13.10 Next advance-and-close tree representation — proposed, not approved

Show advance-and-close as one tree event listing both the phase/step transition
and combat-area closure: closing a presentation area does not itself add a
distinct MTG reaction point. This mapping remains proposed, not approved; it must
meet D-131 rather than just hide multiple meaningful boundaries in one row. Its
whole-combination undo scope is already confirmed.
