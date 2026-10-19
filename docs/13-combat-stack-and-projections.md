# 13. Combat, Stack, and projections

Status: required release scope in delivery stage 1, behavior specification
(D-115). D-112–D-156 require case-by-case design before implementation. The
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
D-147 fixes quick visibility to Declare attackers. D-148 records Declare + tap
as one event, including any automatic area creation. Empty/mixed selections and
remaining applicability cases remain open. D-149 also makes without-tapping one
event; D-131 supplies whole-user-action Undo for both variants.
Do not infer a gesture or final layout beyond the specified multiselect offerings.
D-145 gives each newly declared attacker its own pair for declaration shortcuts;
existing attackers retain their slots. D-146 also auto-creates the area for a
single-card declaration, without adding implicit tapping.

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
slots creates or repositions their combat occurrences. D-141 sends selected real
battlefield cards to the same target slot and side. D-142 extends this to any
dragged collection, including mixed combat roles/views, displayed side-by-side.
Other-view applicability,
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

1. **Attacker entry:** attack destination, single
   versus multi-source behavior, empty/mixed selections, slot order/capacity,
   cancellation, and combined shortcut boundaries. Separate declaration/tap and
   multiselect variants are settled, not proposals.
2. **Blocker entry:** other-view applicability, moved-collection order/removal,
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
action undo default. D-148/D-149 make both declaration variants one event. D-150
makes Untap + advance during Untap one event; D-151 makes Draw + advance during
Draw two events with one Undo. Other cases/exceptions still need review; see
[history mappings, section 3.11](03-history-and-undo.md).

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

Existing-source entry through non-drag controls and exact history/concurrency
composition still need review. D-140 settles real battlefield-card entry/
retargeting; D-142 confirms projection drags in either role direction and uniform
target-slot routing for collections. D-136/D-139
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

Ordering the moved collection itself, first/last/empty-area drop targeting,
between-pair side targeting, and any separate whole-pair reorder control
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
concurrency composition, same-target no-change records,
entry from other views/locations, absent-area entry, and final drop geometry remain
separate questions. This approval does not add attack/block legality checks or
change explicit Declare + tap behavior.

## 13.18 Confirmed selected battlefield-card drag grouping (D-141)

When several selected real battlefield cards are dragged onto one combat slot,
all join that same target slot and side. Do not automatically give each source
its own pair or spread them across different slots. Apply D-140 per source:
create its occurrence if absent in this area, or move its existing occurrence
there with identity preserved. A selection may therefore combine sources with
existing combat occurrences and sources not yet projected.

The drop side determines all resulting occurrence roles. Leave all real sources'
battlefield positions, containing locations, orientation, and unrelated card state
unchanged; no implicit tap/untap. Preserve other cards' grouping/membership/order
and remove fully emptied origin pairs normally (D-138/D-139). This is one selected
drag, not separate user invocations; D-131 supplies whole-user-action Undo. Exact
event/concurrency handling still needs review.

This initially settled selected real battlefield cards; D-142 extends common-
target routing to mixed-role combat projections and mixed real/projection drags.
Duplicate views of one source, non-battlefield/inapplicable selections, ordering
the moved collection itself, absent-area entry, and final drop geometry remain
separate cases. D-143 settles within-slot insertion versus append.
Existing selection-retention
requirements remain; do not change selection mode or create a Send gate.

## 13.19 Confirmed uniform slot drops and side-by-side contents (D-142)

Combat is paired **slots**, one for attackers and one for blockers ("defenders"
in the owner's clarification). Drag any collection of cards onto a slot and all
its resulting combat occurrences go into that one slot, **side-by-side**. The
destination determines their role, not previous attacker/blocker status, source
view, or inferred game characteristics. Do not split a mixed-role selection
across the two sides, preserve previous roles contrary to the target, create one
pair per card, or turn the contents into an ordinary overlapping card pile.

This applies to existing combat projections, including a single blocker dragged
to an attacker slot or vice versa, selected real battlefield sources under
D-140/D-141, and mixtures of these views. Move/reuse each source's existing combat
occurrence or create one if absent, retaining one occurrence per source per area
(D-135). Role/slot changes alone leave real source position/location/orientation/
other fields unchanged. Remaining cards retain grouping/order, and fully empty
origins disappear normally (D-138/D-139). No legality or damage engine is implied.

The proposed special rule preserving roles in mixed attacker/blocker drags is
rejected. D-143 settles within-slot insertion versus append while preserving the
other cards' order. D-154 preserves moved order from one slot; D-156 settles
cross-slot combat order. Mixed-view/battlefield ordering and spacing remain open. Geometry
is stage 3. Non-battlefield/other-view applicability,
duplicate-source view selection handling beyond occurrence uniqueness, between-
pair side targeting, absent-area entry, no-change records, and event/concurrency
composition remain open. Normal whole-user-action Undo and history/branch/offline
recovery still apply; no new selection mode or gameplay Send is introduced.

## 13.20 Confirmed within-slot insertion and append (D-143)

Dropping between existing cards inside a combat slot inserts the dragged cards
at that position. Dropping into unused space in the slot appends them to its
existing contents. Preserve the relative order of the other cards; do not sort,
reverse, or regroup them. This remains an ordinary slot drop under D-142: the
whole dragged collection joins that slot side-by-side with its destination role.

This is insertion within one slot, distinct from a between-pair drop creating a
new pair under D-138. Creating/reusing occurrences, source-state preservation,
empty-origin cleanup, and normal whole-user-action Undo/history recovery remain
unchanged. Retain the prior slot/order context for recovery. Ordering the moved
collection itself across different sources/views, exact drop hit regions,
same-target no-change records, and event/concurrency composition remain open.

## 13.21 Confirmed Remove from combat context action (D-144)

Provide **Remove from combat** as a context action for one combat projection or
several selected combat projections. Remove only the targeted occurrences, not
their real source cards. Leave source positions, orientation, counters, notes,
faces/cover, and other card state unchanged. Do not remove Stack effects or
unselected combat occurrences merely because they share the area.

Remove incident projection links normally (D-120). Remaining cards retain their
paired-slot membership/order; a final attacker leaving retains the blocker-only
pair. Remove a pair immediately only if both sides become empty, with permitted
visual reflow; the combat area remains open even with no pairs (D-138/D-139).

One invocation uses whole-user-action Undo (D-131), restoring the removed
occurrences and prior slots/roles/order/incident links, including any pairs removed
by this cleanup. Normal dependency, branch, offline, and sync contracts still
apply; exact event/concurrency composition remains open. This occurrence-only
action is distinct from source-card Remove. Its availability through a real source
or mixed real/projection selection, source-Remove entry, drag-back-to-source
behavior, and final menu layout remain separate cases. Context-action access is
a confirmed interaction requirement now, not a new UI drafting stage.

## 13.22 Confirmed default declaration placement (D-145)

For declaration shortcuts rather than dragging, give each newly declared attacker
its own slot pair by default, on that pair's attacker side. Keep already-declared
attackers in their existing slots with identities/order/grouping preserved under
D-133; do not split or relocate an existing multi-attacker group on repetition.
Mixed existing/new declarations retain existing attackers and create separate
pairs only for newly added attackers. A plain declaration does not tap; explicit
Declare + tap still taps its selected sources, and without-tapping preserves
orientation (D-116/D-117).

This differs deliberately from dragging a collection onto one slot, which places
the whole collection there (D-142). D-153 appends new declaration pairs after
surviving existing pairs. Order among multiple new pairs, empty/inapplicable
selections, and exact concurrency remain open. D-152 settles existing blockers; D-148/D-149
settle each declaration variant's one-event mapping.
Do not duplicate a source's combat occurrence while resolving those cases.

## 13.23 Confirmed single-card declaration auto-creation (D-146)

Declaring a single card when the combat area is absent automatically creates the
area and declares the attacker, with its own pair under D-145. No separate manual
area-creation prerequisite or extra user action is required, just as for approved
multiselect declaration shortcuts. Plain declaration leaves source orientation/
position/location unchanged; auto-creation does not add implicit tap/untap.

Separate manual area creation remains supported (D-116), and an existing area is
reused rather than replaced, cleared, or duplicated. Normal whole-user-action
Undo/history/branch/offline recovery applies; exact event/concurrency composition
remains open. This does not settle absent-area drag targeting, authorize new
single-card quick offerings. D-147 separately fixes declaration quick visibility.

## 13.24 Confirmed declaration quick visibility (D-147)

Offer both **Declare + tap** and **Declare without tapping** as multiselect quick
actions only during the **Declare attackers** step. Do not offer them as prominent
quick actions at Beginning of combat, Declare blockers, combat damage, End of
combat, or other phases/steps. Retain the established multiselect condition;
this does not approve additional single-card quick offerings.

This is a quick-visibility condition, not an underlying action-access or legality
restriction. Both combinations and their constituent actions remain reachable
through full context-action paths at other steps (D-121), subject to structural/
applicability validation. Neither phase entry nor quick-action visibility executes
anything automatically. Cooperative editing rights are unchanged; do not add
participant/turn-player locks or runtime priority/attack legality checks.

Default pairs, occurrence reuse, auto-area creation, and tap/without-tapping
semantics remain as approved. Empty/inapplicable-selection feedback, new single-
card offerings, and per-action event/concurrency mappings remain separate cases.

## 13.25 Confirmed Declare + tap event mapping (D-148)

Record one **Declare + tap** invocation as **one tree event** showing all its
effects: declaring the selected attackers as projections, tapping their real
sources, and automatically creating the combat area if absent. Include new slot
pairs under D-145, preserve already-declared attackers under D-133, and record
actual source orientation changes rather than inventing changes for tapped cards.

There is no separate MTG response point between declaring attackers and tapping
them, nor from creating the presentation area. Review/interruption is before or
after this event, not between its effects. Do not represent it as separately
interruptible events merely collapsed into one row. This specifies history
boundaries, not runtime timing, priority, or legality adjudication.

One Undo reverses the whole invocation under D-131: restore prior source
orientations, occurrence/slot/grouping/order context, and whether the combat area
existed. Do not undo unrelated earlier state or remove pre-existing attackers/
groups. Normal dependency, branch, exposure/comment retention, offline, and sync
contracts still apply; exact concurrency handling remains open.

Separately invoking declaration and tapping retains separate user actions/events
under D-116. D-149 separately settles without-tapping; D-148 is not a universal
one-event rule for combined actions.

## 13.26 Confirmed Declare without tapping event mapping (D-149)

Record Declare without tapping as one tree event showing declaration and any
automatic combat-area creation together. Preserve source orientation rather than
untapping already-tapped sources. Apply established occurrence reuse/default
pairs; review/interruption is before/after, not between area creation/declaration.

One Undo restores the prior occurrences/slots/grouping/order and area existence,
preserving real source state and unrelated prior groups (D-131). Separate manual
area creation/declaration remain separate invocations when used separately. No
runtime timing/legality adjudication is added. Exact concurrency and remaining
selection/no-change presentation cases remain open.

## 13.27 Confirmed declaring an existing blocker as an attacker (D-152)

If a declaration shortcut includes a source currently projected as a blocker,
move that existing occurrence into its own new attacker pair, changing its role
to attacker. Do not leave it as a blocker, copy it, or delete/recreate it. Preserve
occurrence identity and one combat occurrence per source per area (D-135).

Apply the selected variant normally: Declare + tap taps its real source; Declare
without tapping preserves its current orientation, not forcibly untaps it. Apart
from explicit +tap, this role/placement change leaves source state/location/
battlefield position unchanged. Already-declared attackers in the same selection
keep their existing slots/grouping/order; fresh sources receive their own new
pairs under D-133/D-145.

Remaining origin cards stay grouped in their slots; remove an origin pair only
if both sides become empty, allowing visual reflow (D-138/D-139). Moving an
occurrence without removing it does not remove its incident generic projection
links. Each variant retains its one-event/one-Undo mapping under D-148/D-149;
Undo restores prior roles/slots/grouping/order, cleaned-up pairs, and source
orientation as applicable. D-153 appends the new attacker pairs after surviving
existing pairs. Order among multiple new pairs, empty/inapplicable-source feedback,
duplicate-source views, and exact concurrency remain separate cases.

## 13.28 Confirmed append placement and single-slot moved order (D-153/D-154)

**Declaration pairs (D-153):** append newly created declaration pairs after the
existing pairs, rather than inserting among or before them. This applies to fresh
attackers and existing blockers converted under D-152. Preserve surviving existing
pairs' relative order and contents; fully empty origin cleanup still applies.
Already-declared attackers remain in their existing slots, not appended again.
Order among several new pairs created together remains a separate question.

**Collection from one slot (D-154):** when several cards are dragged together from
one combat slot, preserve their pre-drag relative order within the moved collection.
For a subset, retain its source-slot sequence, not the order cards were selected.
Insert that sequence together at a between-card drop position or append it on an
unused-space drop (D-143). This also permits reordering within the same slot:
move existing occurrences rather than copying them. Other cards retain their
relative order; all moved cards still take the destination slot's role (D-142).

Source-card state, occurrence identities/links, remaining grouping, and normal
whole-user-action Undo/history recovery stay unchanged. D-156 settles collections
drawn from several combat slots. Mixed-view/real-battlefield ordering, ordering multiple new
declaration pairs among themselves, no-change presentation, and exact concurrent
handling remain open. Exact pixel layout/drop hit regions remain stage 3.

## 13.29 Confirmed two-row combat layout (D-155)

Present combat in two horizontal rows: blockers in the upper row, attackers in
the lower row. Each existing opposing slot pair spans those rows, keeping its
blocker slot above its attacker slot. Multiple cards still sit side-by-side
within their slot; pair grouping, one-sided pairs, and fully-empty-pair cleanup
remain unchanged (D-138/D-139/D-142).

This specifies spatial behavior, not exact geometry or a UI draft. D-156 separately
settles row-first cross-slot drag ordering; the earlier pair-by-pair, attacker-first
proposal was not approved. Single-slot order remains D-154 and destination role
remains D-142.

## 13.30 Confirmed cross-slot combat drag order (D-156)

When dragging selected combat projections from several slots, use their pre-drag
row order: selected blockers left-to-right across the upper row, then selected
attackers left-to-right across the lower row. Preserve each slot's card order
(D-154), filtering out unselected cards. Do not alternate attacker/blocker slots
pair-by-pair or use selection-tap order.

Insert the resulting sequence together at the chosen between-card position, or
append on an unused-space drop (D-143). Every moved card takes the destination
slot's role (D-142), regardless of its original row. This orders the collection;
it does not preserve or split its former roles. Reuse occurrence identities,
preserve source-card state/links and other cards' relative order/grouping, and
remove only fully empty origin pairs. Normal whole-user-action Undo restores the
prior roles/slots/order and cleaned-up pairs.

Mixed real/projection-view ordering, real battlefield collection order, ordering
multiple new declaration pairs, duplicate-source views, and exact concurrency
remain separate cases. No implementation or exact layout geometry is authorized.
