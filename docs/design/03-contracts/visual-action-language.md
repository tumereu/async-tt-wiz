# 11. Literal action presentation and pings

Status: **confirmed:** the engine is generic and rules-agnostic. It records and
presents literal actions and action-associated pings. Players—not the software—
interpret their game meaning. Mostly text-free understanding is the goal;
optional notes, replies, and chat remain supported. Detailed UI/grouping rules
below are proposals.

## 11.1 Record what happened, not what it means

The engine knows objects, areas, containers, orientation, named values, generic
links, pings, and recorded operations. D-96/D-105 add first-class turn/phase/step
tracking but no priority state or controls. Remaining details stay under review.
It does not infer spells, legal targets, costs, triggers, combat, priority passes,
or resolution from physical actions. Explicit tracking is distinct from inferred
card rules.
D-112–D-114 also permit explicit combat assignments, source-referencing
projections, and contextual Stack actions. Their existence does not make an
ordinary tap/move/ping an inferred attack, activation, payment, or resolution.

| Recorded fact | Acceptable presentation | Forbidden inference |
| --- | --- | --- |
| Move card from Hand to Stack | Card thumbnail; Hand → Stack; actor. | “Cast this spell.” |
| Ping a participant marker, associated with that move | Ping highlight/line to participant, shown with the move. | “This spell targets that player.” |
| Rotate a card | Before/after orientation. | “Activate it,” “attack,” or “add mana.” |
| Move card from Stack to Graveyard | Stack → Graveyard. | “Resolved” or “countered.” |
| Change Life-labeled value 20 → 17 | Life: 20 → 17. | “Spell dealt 3 damage.” |

Card names and area labels may appear literally where display conventions allow.
Their presence does not authorize a semantic sentence such as “Cast Lightning
Bolt → opponent.” Even when the players find that meaning obvious, the engine
does not store or infer it.

Icons, card thumbnails, location highlights, arrows, and before/after values make
the facts understandable without mandatory narration. Labels/accessibility text
describe the actual operations. Pings are durable enough for someone reviewing
hours later; fleeting online animation cannot be the only record.

## 11.2 Stack and virtual occurrences

D-113 renames the former Cast area **Stack**. Actual cards can move there;
battlefield permanents can also be projected there to signal activated abilities
without moving their originals. The latter is an explicitly chosen virtual
occurrence, not an inferred ability or a Clone/token.

D-114 requires contextual Stack actions: actual instant/sorcery → Graveyard,
actual permanent → Battlefield, projection → remove the projection. Exact
controls, metadata interpretation, destination routing, and history summaries
need case-by-case behavior design. Invoking a shortcut records the actual move
or projection removal, not a claim that a spell legally resolved or was countered.

Changing an area's label alone does not install these operations. Explicit
configuration/occurrence data and user invocations are distinct from inferring
rules from names, generic drags, pings, or card text. No automatic priority,
payment, resolution, or legality engine is implied. See
[the primary projection contract](combat-stack-and-projections.md).

## 11.3 One visible unit, literal constituent records

D-129 distinguishes invocations, tree events and presentation groups. D-159/
D-160 make Undo one parent-node move, replacing whole-invocation/group scope.
D-131 still requires multiple effective tree events wherever MTG allows distinct
reaction points, not decorative rows over an indivisible transition. Composites
may require multiple Undo presses. Presentation split/join never changes parent
links. No runtime priority/legality inference is authorized.
D-130 confirms whole undo for advance-and-close; D-132 records it as one event
listing both the step transition and area closure, with no separate internal
reaction boundary. This does not impose a universal combined-action format.

Proposed presentation:

```text
A • [card thumbnail] • Hand → Stack
    ping: B's player marker
    ping: [another card thumbnail]
```

This is one expandable group in the game log/tree, not a generated explanation
of the players' strategy or rules actions. Its contents are the actual move and
associated pings, in recorded order. Optional user-written labels are attributed
as user text rather than presented as engine conclusions.

Each completed operation synchronizes immediately. The unit can grow without
Send or Done; existing immutable move payloads are not rewritten. Preserve
individual effective boundaries, undo, and interruption/review behavior. The
unit is a presentation grouping, not one inferred atomic game-mechanic event.

Automatic grouping may use explicit action association, author/session context,
and contiguous operations. It must not infer casting/activation/payment from
card metadata, area names, nearby taps, ping destinations, or elapsed time.
Exact rules for which subsequent moves join a group are still open.

If later pings/comments are added to an older action, link them without moving
unrelated operations into a non-contiguous group or changing its old state.
Historical review indicates when the addition became effective; future markers
do not appear retroactively at an earlier boundary.

## 11.4 Pings point; they do not declare mechanics

**Confirmed (D-17, refined by D-75):** each durable ping belongs to one recorded
group/event, with source-action/boundary context retained. It displays only in
that context, not as a persistent overlay on later events. Reviewing that step
in the event tree shows its recorded pings again, including downloaded offline
review. Hiding an old event's overlays does not delete its ping records.

Proposed data includes actor, owning group/event ID, source action ID, effective
boundary/path context, and a referenced object/participant/area or board location.
D-77 uses one standard marker with no user label/style picker initially;
descriptive accessibility text does not give a ping mechanical effects.

D-119 requires each ping to show its order number among all pings in that single
grouped event. Use one event-wide sequence, not separate counts per target,
source, attacker, or semantic purpose. Players may use the sequence to communicate
damage-assignment order; the engine does not interpret or apply damage from it.
The ordinal is automatic presentation, not a user-entered label/style choice.
Existing event scope, toggles, and historical review remain. Exact order capture,
numbering after Unping/re-add, mass-Ping ordering, group split/join/coalescing,
and projection-target routing still require behavior decisions. Do not infer
their answers from this display requirement.

A ping to a card is just a ping to that card. It cannot change another object's
“targets,” create a rules link, pay a cost, move/rotate a card, or resolve an
effect. Multiple pings are simply multiple recorded pings; the engine does not
interpret target slots, legal counts, or damage division.

D-74 confirms a persistent **Ping mode**, analogous to tap/selection-edit
interaction, instead of the earlier proposed drag handle or one-shot command.
Entering the local mode alone creates no ping/shared edit. D-75 scopes this
toggleable state and its pings to one event. D-76 makes destination taps toggle
ping presence, exits Ping mode on event change, and keeps input mode off when
revisiting recorded pings. D-77 makes Ping/selection-edit mutually exclusive,
retains selection on switching, and consumes ordinary tap effects when pinging
only the touched destination. It also confirms one standard marker without
label/style controls or required typing. Exact geometry and other gesture
arbitration remain open; accessible destination picking is still required.
D-78/D-79 additionally offer context Ping: directly toggle the individual
card/player destination without changing interaction mode or a second target
tap. D-80 makes the menu-trigger card determine the sole Ping/Unping action:
unpinged -> Ping all selected, pinged -> Unping all selected. Do not use an
any/all-selected test or independently invert mixed states. Preserve
selection/unrelated state. Exact context routing and menu lifetime remain open.

D-168 clarifies the user model: a ping is placed on the timeline without a new
group-start marker, not assigned through an additional “owner action” picker.
The selected timeline context and effective ping boundary remain visible; adding
a historical ping must not silently change a different boundary or make it appear
retroactively. D-170 makes an author change start a new group even for a ping;
the no-ping-marker rule never overrides author separation. Exact first/root
context remains open.
Free-standing discussion goes in Chat, not a guessed grouping/ownership workflow.

Persistent generic links are confirmed by D-70: card context Link -> pick a
card/player endpoint, with no incidental target tap/untap or player-menu action.
Under D-71 render one standard connector with no editable labels/styles: linked
or not. Follow endpoint positions without moving other linked cards or changing
their fields. These are separate explicit
board relationships: a ping or ordinary stack does not create/remove one
automatically. Selection Link creates one relationship from each selected source
to the chosen endpoint; the Links context list supports removal without card
edits. D-72 makes links undirected and unique per endpoint pair; repeating Link
preserves existing pairs, with removal explicit rather than a toggle. Creation/
removal are shared and recoverable. Exact rendering/no-op history remain under
review in G-08. D-77 separately simplifies ping appearance to one standard marker
without label/style controls.

D-73 excludes separate Attach/follow-group controls initially. Binary links
stay visual; use explicit multi-selection for joint card movement. G-08's core
mode/context controls are confirmed through D-80; destination location matching,
other gesture arbitration, menu lifetime, and revisions remain open.
Exact action/group/history placement still needs later detailed review.

## 11.5 Branches, history, and recovery

- A ping keeps its original group/event and action/context references across
  branch changes. Show its overlays only in its own event context, never carried
  into a later event merely because the source event is on the same path.
- If its referenced object moves, the historical view still shows where the ping
  was made. If the object is absent, offer the original context rather than
  silently pointing at a replacement object.
- Display only applicable path/context records when reviewing a branch, with a
  route to messages/pings on retained alternatives.
- Undo moves the shared active pointer one parent back (D-159). Retain every
  descendant. After replacement play, ordinary abandoned continuations hide
  behind a toggle; exposure/comment continuations stay visible in full (D-160),
  with no last-exposure cutoff. Exact pre-fork/forward presentation remains open.
  Undo/remove/edit pings
  through recoverable records; a ping is
  not automatically classified as information-gaining merely because durable.
- Adding a communicative ping while inspecting history does not by itself select
  a new active game-state continuation. Exact ping revision/history placement
  remains a protocol decision; it cannot mutate the associated move payload.
- “Seen,” group closure, ping disappearance, and response markers never imply
  rules approval, priority passing, or permission to move another piece.

## 11.6 Optional text

Support action/group notes, contextual replies/questions, and general game chat.
Players can explain unusual situations or write their own interpretation. Keep
that text visibly user-authored. Do not parse it into automatic state changes.

D-94 makes submitted game-wide chat non-undoable. D-95 makes an event comment
another node on the current tree path, already a branch; undo moves back while
retaining that existing path through the comment. No separate comment-only fork
or activation workflow. Normal tree/base rules apply, and comments are retention
visibility qualifiers for the whole abandoned continuation under D-160. Card
notes/pings stay distinct. Exact group association,
revisions/edit/delete, and unsent-chat delivery policies remain open.

Common physical operations and pings need no explanatory text or mandatory
labels. A participant may create a generic note/token with a label such as
“ability” or “trigger”; the engine treats it as a labeled piece, not a special
rules object. Generic labeled values capture any other state the players need.

## 11.7 Worked example without game inference

1. A moves a card from Hand to Stack. The log shows the move and thumbnail.
2. A adds a ping to B's player marker, associated with that move. They appear
   together in one unit; no “targets B” statement is generated.
3. A rotates another card and changes a named counter. Each is a literal edit;
   grouping depends on the approved generic interaction rules, not payment logic.
4. A moves the first card from Stack to Graveyard, then changes a named value.
5. Hours later, B reviews those facts and interprets them according to their game.
6. B interrupts before a chosen move, creates another continuation, and can undo
   it to return. No step required the engine to understand what was being played.

## 11.8 Remaining design decisions

- Area layout/container settings and named presets, without inferred game-rule
  effects. D-55/D-56's Graveyard/Exile/Library counter removal and database-default
  corner-text restoration must be visible in literal review and recoverable;
  D-64/D-65 fix conditional type metadata, blank fallback, and retained per-face
  defaults. Exact source/custom mapping remains open.
- Ping gestures, exact action association, appearance, and revision controls;
  group/event-only overlay lifetime and retained historical review are D-75.
- Generic grouping heuristics, boundaries, later linked pings, and historical
  visibility/revision contracts.
- Literal log layouts, covered-card presentation, optional text flows, wireframes,
  accessibility, and whole-journey interaction budgets.
