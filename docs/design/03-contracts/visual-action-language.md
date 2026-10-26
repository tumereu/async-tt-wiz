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

D-176 distinguishes invocations, individual mutation nodes and playback groups.
Each node is one deterministically reversible change, such as moving or tapping
one card. Bulk/combined controls issue ordered individual mutations, immediately
published; D-176 supersedes older combined-node mappings such as D-132.
Undo removes one eligible latest-own mutation, never a whole playback group
(D-224/D-231/D-232).

D-177 makes a group one navigation/playback step with concurrent animation.
Pause playback and drag the horizontal bottom timeline to select an exact
mutation, including within a group. A client's displayed state points to that
node, never a group. On return after an interruption, animate rollback from the
last local state to the fork, then playback of the new continuation. See
[history section 3.12](history-and-undo.md#312-resume-playback-and-exact-timeline-navigation-d-177).
D-277 uses previous displayed board → next group-end board animation, not queued
intermediate moves of one card. Live remote updates instead apply immediately with
prediction rebasing; return from background with unseen updates uses grouped
playback until caught up or deliberate gameplay continuation. Animation batching
does not erase constituent records, pings/references or exact mutation navigation.

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
Send or Done; annotation additions do not rewrite earlier move payloads. Preserve
individual effective boundaries, undo, and interruption/review behavior. The
unit is a presentation grouping, not one inferred atomic game-mechanic event.

Automatic grouping may use explicit action association, author/session context,
and contiguous operations. It must not infer casting/activation/payment from
card metadata, area names, nearby taps, ping destinations, or elapsed time.
The [category contract](history-grouping.md) and
[scenario matrix](../05-verification/grouping-matrix.md) define current move/tap
pairings and prefix behavior; later playtest revisions update those together.

If later pings are added to an older action, link them without moving
unrelated operations into a non-contiguous group or changing its old state.
Historical review indicates when the addition became effective; future markers
do not appear retroactively at an earlier boundary.

### Recording context versus referenced context (R-08)

Every annotation distinguishes **where/when it was recorded** (play incarnation,
current accepted path/boundary and its effective revision) from **what it refers
to** (the selected historical event/group, object/occurrence or location and its
original context). These contexts may differ; never replace one with the other.

On shared `E → F → G`, submitting a ping about E while reviewing E
records the addition at the current shared head, not inserted into E and not a
fork at E. Ping changes retain
their durable effective records. Ordinary grouping uses the recording sequence
and author; the reference to E does not merge non-contiguous groups or place
the addition before F/G. Explicit historical continuation remains a separate
deliberate action under D-179.

The current recording event shows the annotation with a route to E's original
context. Reviewing E's original boundary never overlays a future ping as though
it already existed there. Later-added references may be listed separately as
“added later,” with a route to their effective recording event; historical
snapshots and playback before that effective revision omit them. Viewing the
annotation's own recorded event can inspect its referenced E board/location.
The same applies when E belongs to an inactive alternative; no activation or
automatic branch is required, and no replacement object/context is substituted.

For pings, the owning event/numbering sequence is the recording group determined
from server-created events by the deterministic grouping rules (D-254). A UI
request does not define or bind that group. Capture destination identities and
retain any selected historical referenced context separately; referencing E does
not assign the new event to E's group. Process Ping/Unping in the server's current
derived recording context, preserving the requested operation rather than
reinterpreting Unping as Ping or removing a preceding group's marker. Once
recorded, its owning context remains part of the event's history. Re-add is a new
effective change, not retroactive restoration at E.
First/root additions use a leading recording group under author separation,
without an owner picker. Number pings in recorded action order within that group;
delivery retries do not add numbers. Exact compact rendering remains later UI work.

Durable ping changes retain their own
actor/effective before/after context and ordinary eligibility. Later dependent
annotations protect referenced mutations under D-224, including retained-future
validation; an unrelated personal cursor does not. Do not delete/rebind references
to permit removal. No event-comments UI or manual group editing is added.

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
Existing event scope, toggles, and historical review remain. Numbering follows
the [stable-context contract](history-grouping.md#categories-keys-and-stable-ping-meaning):
number currently active pings, not historical additions. Ping A/B then unping A
makes B=1; re-added A is 2. Retries add nothing. Active pings loop an expanding ring
in the pinger's color about every 1.2 s. For Undo/Redo a ping concerns ONLY its
target card, projection or player, not its recording position/ancestry or display
group; a projection ping still concerns that projection. Display groups recompute.

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
D-172 groups persistent-link creation/removal with ordinary card/player edits.
Exact event mappings and historical placement still need detailed review.

## 11.5 Branches, history, and recovery

- A ping keeps its original group/event and action/context references across
  branch changes. Show its overlays only in its own event context, never carried
  into a later event merely because the source event is on the same path.
- If its referenced object moves, the historical view still shows where the ping
  was made. If the object is absent, offer the original context rather than
  silently pointing at a replacement object.
- Display only applicable path/context records when reviewing a branch, with a
  route to messages/pings on retained alternatives.
- **Authoritative owner revision, 2026-10-25:** selective mutation removal/reinsertion
  preserves every later event's exact concerned-field `(before, after)` tuple
  across the complete retained future. Eligibility is allowed iff every such event
  can still apply with that identical tuple. The rule makes eligibility
  depend only on game-state/event concerned tuples, never information gained or
  seen; concerned fields include reads/dependencies as well as edits. D-231 restricts
  Undo to the latest own action. **D-232 edits the existing tree**, not creates
  replacement branches or preserves an untouched original path. References/grouping
  follow the stable-context rules; cache/ID encoding remains technical. D-238
  settles cursor fallback; D-310 supersedes D-233's blanket fork refusal.
  Do not present this as appended
  compensation, require an explorer branch for removed mutations, or silently
  reattach/delete pings to force eligibility.
- A graphical explorer can explicitly activate retained branch/nodes. Only
  single-player alternatives without exposure qualify for hiding;
  mixed-player/exposure alternatives remain visible in full. Undo itself
  does not create a retained pre-edit copy (D-232).
  Undo/remove/edit pings
  through recoverable records; a ping is
  not automatically classified as information-gaining merely because durable.
- Adding a communicative ping while inspecting history does not activate a
  continuation. Recording/reference contexts above determine placement and
  effective rendering; encoding is technical, not permission to change those outcomes.
- “Seen,” group closure, ping disappearance, and response markers never imply
  rules approval, priority passing, or permission to move another piece.

## 11.6 Optional text

Game chat is the only comment channel; no event-comments UI, contextual event
Note/Reply action or Event details dialog. Card notes and pings remain separate.
Players can explain unusual situations or write their own interpretation. Keep
that text visibly user-authored. Do not parse it into automatic state changes.

D-94 makes submitted game-wide chat non-undoable. **D-240 confirms simple chat
with no planned expansions:** participants can send/read messages, but cannot
edit or delete submitted messages. Correct a message by sending another message.
This is the selected scope, not a feature deferred to a planned later release;
do not add a chat expansion roadmap. It does not decide account erasure or data
retention, nor change ordinary unfinished text editing.

Unsent-chat delivery handling still needs contract completion; it does not add
event-linked comments or a separate comment correction workflow.

Common physical operations and pings need no explanatory text or mandatory
labels. A participant may create a generic note/token with a label such as
“ability” or “trigger” using approved Blank/token text tools; it is not a special
rules object. Arbitrary piece/named-value editors are excluded from R1 (D-241).

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
