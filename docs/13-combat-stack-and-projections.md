# 13. Combat, Stack, and projections

Status: required release scope in delivery stage 1, behavior specification
(D-115). D-112–D-140 require case-by-case design before implementation. The
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
   area that makes visual attacker/blocker grouping easy. D-139 refines earlier
   per-attacker assignment wording to paired-slot membership, with no individual
   attacker↔blocker assignments inside a pair. Exact appearance/order are open.
   D-122 fixes removal effects; D-127
   requires explicit closure and a combined advance-out-of-combat + close
   shortcut. D-116 makes creation separate; D-117's shortcuts also create it.
   D-135 permits at most one projection per real source in each combat area;
   dragging between attacker and blocker positions moves that occurrence.
   D-137 organizes the area into pairs of opposing attacker/blocker slots, each
   side supporting multiple projections; see section 13.15 for pending refinements.
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
   ability without moving the original card. D-134 allows unlimited independent
   Stack projections, including several from the same source. This is an explicit
   player action, not ability detection, automatic cost payment, or priority tracking.
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
Exact quick-action phase/step visibility, empty/mixed
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

Blockers can be dragged to an attacker's opposing slot to join that visual combat
group. D-139 refines D-118's earlier per-attacker assignment wording: there is no
separate individual attacker↔blocker association within a pair. Blockers can also
occupy a blank slot with no attacker for special/manual purposes; a blocker-only
pair is valid, not an invalid missing-attacker reference.
Creating/grouping blocker projections leaves sources at their battlefield
positions; later card-directed actions through projections affect the source
(D-123). D-140 confirms dragging real battlefield sources into attacker/blocker
slots creates or repositions their combat occurrences. Multiselect routing,
gesture disambiguation, within-side order, and removal controls still need review.

Attackers receive more space as more blockers are assigned, keeping their blocker
groups understandable instead of forcing a fixed-width layout. This adaptive
spacing requirement is confirmed; pixel geometry, scrolling, and layout mechanics
are stage 3. Do not impose the ordinary three-actual-card pile limit on these
groups merely because blockers are visually adjacent; exact capacity is open.
D-126 keeps surviving blockers in their slot rather than moving them into a side
collection or another slot. D-138 replaces literal fixed positions with stable
slot membership/order while allowing visual reflow.
D-136 extends former-attacker cleanup to an attacker dragged into a blocker role.
D-137 adds paired slots with multiple cards on either side and visual resizing;
D-139 settles multi-attacker grouping without per-card assignments. D-138 confirms fully-empty-pair
deletion and between-pair insertion, with visual reflow but no automatic transfer
of surviving cards between pairs.

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
   versus multi-source behavior, empty/mixed selections, slot order/capacity,
   cancellation, and combined shortcut boundaries. Separate declaration/tap and
   multiselect variants are settled, not proposals.
2. **Blocker entry:** multiselect/other-view routing, within-side order/removal,
   and corrections. D-140 settles real battlefield-card drag entry/retargeting.
   Opposing-slot drops, blocker-only pairs, and
   adaptive spacing are settled. D-139 requires visual paired grouping rather
   than individual attacker↔blocker assignments.
3. **Combat lifecycle:** advance-and-close missing-area/out-of-combat handling,
   combined boundaries, repeated combat/area reuse, and historical rendering.
   D-127 requires explicit closure; D-128 fixes End of combat quick visibility
   and Postcombat main destination. Source departure removes combat projections
   (D-124); D-126 keeps surviving blockers unassigned in place.
4. **Stack entry:** real card versus projection controls; ability-source context;
   placement/order, labels, and optional communication. D-134 settles unlimited
   independent projections, including repeated occurrences from one source.
   No ability picker/text entry is approved by implication.
5. **Stack exit:** concrete buttons/gestures, metadata and multi-face categories,
   ambiguous/missing data, participant/destination placement, and manual overrides.
   Projection removal must be distinguished from source-card Remove/move.
6. **Shared correctness:** projection/slot identities and membership, reference lifetime,
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

## 13.6 Confirmed attacker-removal cleanup and slot stability (D-126/D-138)

When an attacker projection disappears, keep its surviving blocker projections
in their existing slot. If other attackers remain, the remaining cards stay grouped;
if the final attacker leaves, retain a valid blocker-only pair (D-139). Do not move them to a side
collection or transfer them into different pairs. Their real cards remain
unchanged. If a blocker's own source also leaves the battlefield, D-124 still
removes that blocker's projection. No individual assignment cleanup is needed:
"unassigned" here means that the pair's attacker side is empty.

D-138 explicitly replaces D-126's earlier literal fixed-tabletop-position
requirement with stable slot membership and order. Resizing, insertion, or removal
may visually shift/reflow pairs and their cards, including closing gaps. Preserve
surviving pairs' contents and relative order rather than automatically regrouping
cards. Exact area bounds/geometry and rendering are stage 3.

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

Handling outside combat/with no area remains a behavior question. D-128 fixes
destination/quick visibility; D-130 makes the combination one undoable unit;
D-132 records both effects as one event. UI wording/geometry follows stage 3.

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
D-132 additionally confirms one event for this particular combination.
Untap/Draw + advance and declaration combinations also use D-131's whole-user-
action undo default; their reaction-point event mappings and any exceptions
still need review.

## 13.10 Confirmed advance-and-close tree representation (D-132)

Show advance-and-close as one tree event listing both the phase/step transition
and combat-area closure: closing a presentation area does not itself add a
distinct MTG reaction point. Record one transition with both effects, not two
separately interruptible events merely collapsed under one row. Review/interruption
can occur before or after it, not between its advancement and area cleanup.
One Undo restores both effects under D-130. This is specific to advance-and-close;
do not use it to hide other actions' required reaction boundaries (D-131).

## 13.11 Confirmed repeated attacker declaration (D-133)

If selected sources include already-declared attackers, keep those existing
attacker projections and add projections only for newly declared sources, rather
than duplicating attackers. Retain existing projections' identities, placements,
and associations rather than replacing/recreating them. Declare + tap still taps
its selected sources; without-tapping preserves orientation. Exact no-change
record handling remains open. D-135 separately settles one occurrence per source
per combat area and drag-based role changes. Stack multiplicity is settled
separately by D-134, not this duplicate-attacker policy.
This does not infer attack legality or change normal selected-set validation.

## 13.12 Confirmed unlimited independent Stack projections (D-134)

Allow any number of Stack projections, including multiple simultaneous effect
occurrences from the same real card. There is no per-source or total Stack-
projection count limit; the ordinary three-card visual-pile limit does not apply.
Repeated explicit projection creation adds a new independent occurrence rather
than reusing or replacing an existing one, unlike repeated attacker declaration.

Each occurrence can be explicitly removed/resolved separately. Removing one
removes only that occurrence and its incident projection links, leaving sibling
Stack occurrences and the real source unchanged (D-114/D-120/D-125). Independent
effect lifetime does not mean independent card state: while the source exists,
all projections still share its live card fields and write-through actions under
D-123. Source departure/removal does not remove any of its Stack effects (D-125).

Normal history/undo/branch/offline recovery applies. Placement/order, duplicate-
source multiselection, missing-source presentation/actions, and exact concurrent
operation handling remain separate behavior questions. No ability picker,
automatic activation, cost payment, resolution, or legality checking is added.

## 13.13 Confirmed one combat projection per source per area (D-135)

Each real card has at most one projection in a given combat area, across attacker
and blocker roles together. It cannot have separate attacker and blocker
occurrences, or duplicate blocker occurrences, in that same area. This is a UX
constraint, not an attack/block legality rule or a limit on the total number of
different sources in combat. It does not limit independent Stack projections
(D-134) or decide how repeated combats/area reuse work.

Dragging an attacker projection to a blocker position moves the existing
occurrence and changes its role; it does not create a second occurrence or
delete/recreate the first. Preserve its occurrence identity. Such combat-area
placement/role changes alone do not move or edit the actual source card (D-123).

Existing-source entry through non-drag controls, projection-drag reverse role
changes, and exact history/concurrency composition still need review. D-140
settles real battlefield-card drag entry/retargeting to either role. D-136/D-139
settle remaining-group cleanup on attacker departure. The previous
proposal allowing simultaneous attacker and blocker occurrences from the same
source is rejected.

## 13.14 Confirmed former-attacker cleanup on role change (D-136)

When an attacker in a pair containing blockers is dragged into a blocker position,
the other cards remain in their existing slots. D-139 refines D-136's earlier
"become unassigned" wording: if other attackers remain, the group persists; if
the final attacker leaves, the origin becomes a blocker-only pair. There are no
individual attacker↔blocker assignments to clear. D-138 permits visual reflow,
not automatic transfer to other pairs. Leave their real source cards unchanged. The
dragged occurrence itself moves/changes role under D-135; it is not duplicated
or removed/recreated. Preserve the prior role, assignments, and placement for
normal undo/history/branch recovery, interpreting earlier assignment terminology
as slot membership under D-139.

## 13.15 Confirmed paired-slot model (D-137/D-138)

The combat area consists of N pairs of opposing slots, with an attacker side and
a blocker side. Each side can hold one or more projections, not just one attacker
opposed by several blockers. A side may also be empty: unassigned blockers and
attackers without blockers remain valid. D-135's one occurrence per source per
combat area still applies, and the ordinary three-card visual-pile limit does not
apply to these slot groups.

Slot size grows/shrinks as cards are added/removed. This is visual accommodation,
not an inferred game effect, legality check, card-state edit, or automatic damage
assignment. Exact geometry/layout remains stage 3. D-138 permits visual reflow
while preserving surviving slot membership/order; literal fixed positions are
no longer required. This does not permit automatic regrouping into other slots.

D-139 settles multiple-attacker semantics: each pair is one visual combat group,
not a collection of individual attacker↔blocker assignments. See section 13.16.

### Confirmed empty-pair removal and insertion (D-138)

- Remove a slot pair immediately when **both sides are empty**, closing its visual
  gap. Keep a pair with cards on either side, including attacker-only/blocker-only
  pairs. If the last pair disappears, combat remains open until explicitly closed
  (D-127); exact empty-area presentation is stage 3.
- Dropping between pairs inserts a new pair at that position containing the
  dragged projections. Move those occurrences, not copies or an entire existing
  pair merely because one of its cards was dragged. Preserve other pairs' contents
  and relative order. If the origin becomes fully empty, remove it by the same
  rule; if either side still has cards, retain it.
- Allow visual shifts/reflow from resizing, insertion, and removal. Except for the
  explicit moved set, preserve surviving cards' slot membership and order rather
  than automatically transferring them between pairs. Source-card state is unchanged
  by this layout alone; normal undo/history must retain the prior slot/ordering
  context. Exact event/concurrency composition remains open.

Side routing for mixed-role selections, within-side insertion/order controls,
first/last/empty-area drop targeting, and any separate whole-pair reorder control
remain behavior questions. D-138 approves the described behavior, not final screen
geometry or an implementation-stage transition.

## 13.16 Confirmed visual combat groups without per-card assignments (D-139)

Each paired attacker/blocker slot is one visual combat group. Record membership
on the respective sides, not individual attacker↔blocker assignment edges inside
the pair. This refines earlier per-attacker assignment/cardinality/cleanup wording
in D-112/D-118/D-126/D-136; it does not add an all-to-all blocking rules object.
Players interpret the grouping and may use placement and optional event pings to
communicate finer details. Do not require pings or infer legality, damage, or
resolution from them.

When one of several attackers leaves the pair, including by role change, leave
the remaining cards grouped in their slots. When the final attacker leaves,
retain the blocker-only pair. The reciprocal attacker-only state is also valid;
remove the pair only when both sides are empty (D-138). Apart from explicit moved
or removed occurrences, preserve membership/order; visual reflow remains allowed.
None of this edits real source cards as a layout side effect.

History/undo/branch/offline recovery retains the paired-slot contents, roles, and
order, not a now-rejected hidden per-card assignment graph. Incident generic links
still follow their own endpoint lifecycle (D-120), and pings retain event scope.
Exact operation/event/concurrency handling remains under review.

## 13.17 Confirmed real battlefield-card drag entry and retargeting (D-140)

Dragging a real battlefield card into an attacker or blocker slot creates its
combat projection at that destination, leaving the original's battlefield
position, containing location, and orientation unchanged. The target side selects
the occurrence's combat role; the drag does not implicitly tap or untap the source.
Projection creation is not an ordinary real-card move into combat or a Clone.

If that source already has a projection in this combat area, move the existing
occurrence to the drop target instead of creating another. Preserve occurrence
identity and use the destination role/slot; do not delete/recreate or duplicate it.
The original still stays in place with unchanged orientation. Other cards retain
their paired grouping/membership/order (D-139); remove a fully emptied origin
pair under D-138. This is explicit retargeting by drag, unlike repeating a
declaration shortcut that retains an existing attacker's placement (D-133).

Normal history/undo/branch/offline recovery retains prior occurrence/slot/role
context, and source-card fields remain live/shared (D-123). Exact operation/event/
concurrency composition, same-target no-change records, multiselect routing,
entry from other views/locations, absent-area entry, and final drop geometry remain
separate questions. This approval does not add attack/block legality checks or
change explicit Declare + tap behavior.
