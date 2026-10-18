# 10. Game board and controls

Status: gesture choices, snapping, diagonal piles, selection-aware dragging,
top-insertion drops on special piles, visual stacking drops, and top/bottom
context moves for library/graveyard/exile are confirmed. Layout, detailed hit testing, and
communication mechanisms below are proposals for review. The owner additionally
requires mostly text-free understandable play, optional text/messages, and
action-scoped pings. The engine is confirmed generic/rules-agnostic; the literal
move-and-ping presentation for an area labeled Cast is defined in
[Visual action language](11-visual-action-language.md).

This document is the primary contract for tabletop interaction. It supplements
the [Android experience](05-android-experience.md) and
[action catalog](04-game-actions.md); it does not implement a rules engine.

## 10.1 Confirmed interaction vocabulary

| ID | Interaction | Meaning |
| --- | --- | --- |
| B-001 | Drag a card | Move it on the table or into another area/zone. |
| B-002 | Drop | Snap to a grid location or designated slot. |
| B-003 | Pile cards | Allow overlapping cards, with underlying cards peeking out diagonally. |
| B-004 | Single tap a card | Outside selection-edit mode, tap/untap the selected set if the touched card is selected; otherwise clear selection and tap/untap only that card. Tapped presentation is 90 degrees clockwise, or back to untapped. No mechanical effect. |
| B-005 | Double tap a card | Show all available actions for that card. |
| B-006 | Hold a card | Show large artwork and readable card text. |
| B-007 | Special piles | Library, graveyard, and exile have dedicated pile representations. |
| B-008 | Drag scope | Drag one card unless multiple cards have been selected; an ordinary pile is not implicitly selected. |
| B-009 | Drop on a special pile | Insert at the top of the configured library, graveyard, or exile pile. |
| B-010 | Drop on another ordinary card | Put the moved card onto that card's visual combined stack; no attachment or mechanical merge. |
| B-011 | Context Move | The double-tap action sheet offers at least top and bottom destinations for library, graveyard, and exile. |
| B-012 | Tap selected / Untap selected | In selection-edit mode, explicit commands set the selected set uniformly while ordinary card taps edit membership. Outside that mode, use the selection-aware card-tap gesture. |

Gestures operate on either participant's cards. Ownership does not restrict
editing. Every completed state-changing interaction queues immediate sync and
has undo. Menus, zoom, selection previews, and cancelled gestures do not create
game-state actions.

## 10.2 Proposed board areas

Portrait-first conceptual layout, not an approved pixel wireframe:

This is an MTG-oriented naming/layout preset on generic areas and named values.
The engine does not interpret those labels.

```text
+----------------------------------------------------+
| Branch / sync / review context                      |
| Opponent: life, mana, counters, turn/phase           |
| Opponent hand (covered by default)                  |
| [Library] [Graveyard] [Exile] [Command / extra]       |
| Opponent battlefield area                           |
|                                                    |
| Shared area labeled Cast / action-linked pings       |
|                                                    |
| Your battlefield area                               |
| [Library] [Graveyard] [Exile] [Command / extra]       |
| Your hand                                           |
| You: life, mana, counters                           |
| Undo | Previous / Next action | Activity / History   |
+----------------------------------------------------+
```

- The two participant play areas are generic board regions. Their labels do not
  install a battlefield model or infer ownership/control. Movement changes only
  the explicitly selected container/placement fields.
- Each participant has labeled library, graveyard, and exile slots. Their card
  counts remain visible even when empty or covered.
- A shared area labeled Cast is visually distinct and can use configured slots
  or pile ordering. It displays actual cards and action-linked pings. There is
  no inferred stack protocol, spell/ability object, target model, or resolution
  order. Another shared area may be labeled Stack without changing the engine.
- Hands use an expandable fan/row rather than consuming the whole battlefield.
- Extra labeled areas remain reachable through an expandable dock; a manual
  MTG layout may name some Command, Sideboard, or Outside game.
- Player controls and review/undo stay reachable while the table pans/zooms.
- Exact sizing, dock placement, large-screen layouts, and whether both zone
  docks fit at the default phone zoom require annotated wireframes.

The layout is not an ownership permission map. Either participant can manipulate
either side. Perspective changes are local and cannot reorder a library or
change whose turn it is.

## 10.3 Gesture arbitration

Each pointer sequence produces at most one of: single tap, double tap, hold,
drag/drop, or cancellation.

| Gesture | Proposed detailed behavior |
| --- | --- |
| Single tap | In selection-edit mode, edit membership. Outside it, tap/untap the selected set when the addressed card is selected; otherwise clear selection and tap/untap that card only. No inferred game effect. |
| Double tap | Open the action sheet without performing either single-tap action. |
| Hold | Open a large-art/text inspector without tapping the card, moving it, or changing shared reveal state. |
| Drag | Begin after movement exceeds touch slop; show a lifted preview and destination highlight. Commit only on a valid drop. |
| Cancel/interrupted touch | Restore the pre-gesture state; no shared move or tap. |

Confirmed orientation scope (D-35, correcting D-32): outside selection-edit mode,
tapping a selected card retains the selection and taps/untaps every selected
card. Tapping an unselected card clears the selection and taps/untaps only that
card, without adding it to the former set. With no selection, affect only the
addressed card. Pile membership alone does not expand the affected set.
Tapped presentation is a 90-degree clockwise rotation from untapped, and
untapping returns to the untapped presentation rather than rotating another
quarter-turn. Confirmed mixed-orientation behavior (D-36): follow the touched
card's pre-tap presentation. If it is untapped, set all selected cards tapped;
if it is tapped, set all selected cards untapped. Do not invert each member
independently. Preserve the selected set and unrelated fields.

In selection-edit mode, card taps still add/remove selection membership instead
of rotating cards. The previously approved explicit Tap selected / Untap selected
commands remain for that mode and set the selected set uniformly. The proposed
availability of those commands outside the mode was rejected: there, card taps
provide selection-aware tapping. Exact command placement and bulk action
boundaries remain open. No tap/untap infers activation, mana, combat, or any
other game meaning.

Double-tap recognition requires a bounded single-tap decision window. Show
immediate pressed feedback, but do not synchronize a speculative tap that later
needs to be undone merely because the gesture becomes a double tap. This
gesture-recognition interval is not a batching delay after a completed action.
Exact timing/slop and accessibility behavior remain to be approved.

Movement past drag slop before hold recognition cancels tap/hold recognition.
Once the hold inspector opens, the same touch cannot accidentally become a card
move. The current owner-suggested G-02 mapping (P-16) distinguishes direct
rectangle selection from editing an arbitrary selection:

- A one-finger drag starting on empty table space draws a selection rectangle
  without a mode-entry tap. Releasing selects cards without moving or rotating
  them. A selection need not remain rectangular after subsequent editing.
- Two-finger drag pans and pinch zooms in normal interaction and during
  selection editing (D-34). Navigation changes only the local viewport.
- A separate multi-select/selection-edit mode lets taps add or remove individual
  cards from the current selection instead of rotating them. This allows
  disconnected sets and excluding individual cards inside the original
  rectangle. Outside this mode, the confirmed single-tap card gesture remains
  tap/untap; selection existence alone does not silently change its meaning.
- Dragging a selected card moves the selected set. Show selected cards, their
  count, and whether selection-edit mode is active clearly.
- Confirmed mode control (D-31): a visible Select toggle enters/exits
  selection-edit mode. Turning it off preserves the selected set and restores
  normal card taps to tap/untap. Tapping empty table space clears selection
  separately (D-30). Neither toggling the mode nor clearing selection is a
  publication/Send step or a shared board action. A separate Clear button is
  not implied by this decision.

Confirmed selection rule (D-25): completing a new selection rectangle replaces
the existing selection rather than adding to or toggling it. Individual
add/remove adjustments use selection-edit mode. This replacement rule also
applies if a rectangle is drawn while that mode is active. Selection changes
are local presentation, not synchronized board actions.

Confirmed inclusion rule (D-26): any overlap between the selection rectangle
and a card selects that card; full enclosure is not required. Even a small
overlapped portion qualifies.

Confirmed visual-pile rule (D-27): evaluate each card's visible portion, not its
full obscured footprint or the pile as a unit. A card is selected only if the
rectangle overlaps that visible portion. An exposed diagonal edge can qualify;
a fully obscured card cannot. Overlap with only the top card does not select
underlying members. Covered card backs can qualify without exposing identity.
Exact visible-card geometry and pixel-boundary handling remain to be specified.

Confirmed clearing rule (D-30): tap empty table space to clear the current
selection without changing whether selection-edit mode is active. This works
in normal interaction and in selection-edit mode. It changes no card fields and
creates no shared board action. Distinguish the completed tap from a drag
starting on empty space, which draws a replacement rectangle instead.

The remaining mapping details are under review. This working mapping supersedes
the earlier one-finger-background-pan mapping and the requirement to enter a mode before
drawing a rectangle. Card dragging never unexpectedly becomes a background pan
or rectangle mid-gesture. Exact geometry and second-finger gesture arbitration
remain open.
Accessible explicit selection and navigation alternatives still need specification.

Confirmed consistency defaults (D-34; items 1–4 of the grouped review):

1. Pinch zoom complements two-finger pan in either interaction mode.
2. Hold and double tap retain Inspect and Actions in selection-edit mode, without
   toggling membership, rotating cards, or changing the selection. Recognize each
   as one gesture rather than also performing its constituent single taps.
3. A successful selected-set move leaves selection-edit mode in its current
   on/off state, in addition to preserving the selected set under D-29.
4. Cancelling a drag or dropping at an invalid destination restores both card
   placement and the pre-drag selection, including a selection cleared when an
   unselected card was picked up. No shared board edit is committed.

Item 5 (explicit selected-set tap commands available with the mode off) was
rejected and replaced by D-35's selection-aware single-tap gesture above.

For a covered individual card, hold uses the agreed full-access inspection
convention; it does not reveal it to the shared table. Whether to log private
inspection is still Q-06. A special pile's back/header is a pile control, not an
individual card to rotate; see special-pile controls below.

TalkBack and alternative controls expose explicit Tap/Untap, Move, Actions,
and Inspect commands without requiring timing-sensitive gestures or dragging.

### Inspection, face-down presentation, and printed faces

Confirmed context controls (D-37):

- Turn face down / Turn face up change shared face-covered presentation for one
  card or an explicitly selected set. Use these clearer labels rather than
  Cover / Uncover. The generic backing image is not another playable face.
- Switch printed face is a context action for a card with multiple defined
  playable faces. An ordinary card has a playable front and generic backing;
  a double-faced card has playable art/text on both physical sides. The engine
  does not infer when a face should change or what that change means.
- Turning face up/down and switching printed face are separate from tapping
  sideways, which retains the selection-aware single-tap controls. Switching
  printed face does not implicitly turn a face-down card face up.
- Face/cover edits preserve orientation, counters, notes, identity, placement,
  selection, and links; only explicitly requested face/cover fields change.
  Never infer a zone move or reset other fields. Exact picker layout, bulk face
  switching across different definitions, and bulk action boundaries remain open.

Confirmed inspector behavior (D-38):

- Hold-to-inspect automatically shows the card's available face art and readable
  text even when its shared presentation is face down. For a multi-faced card,
  automatically show both printed faces and their text; do not require an extra
  uncover or face-switch action to inspect the other side. Inspector layout on
  different screen sizes still needs a wireframe.
- This is the existing large-art/text inspector (called hover-inspect in review),
  not a requirement for touch hardware to support hover. It works in either
  normal or selection-edit mode, for either participant's cards.
- Inspection is local presentation: do not turn up, rotate, move, switch the
  shared printed face, or change selection merely to display inspector content.
  Shared inspection activity logging remains Q-06; this decision does not resolve
  whether a separate activity record is shown.

## 10.4 Dragging and snapping

Confirmed movement rules (D-21–D-24, D-28–D-29, D-34):

- A card drag moves one card unless multiple cards have been selected. Sharing a
  visual pile does not itself select the other cards. Dragging a selected card
  moves the selected set. Dragging an unselected card clears the old selection
  and moves only the addressed card; it neither adds that card to the old set
  nor moves the old set. Selection clearing is local presentation, not an
  additional board action. Cancelling or dropping invalidly restores the
  pre-drag placement and selection (D-34).
- After successfully moving a selected set, keep those cards selected (D-29).
  Completing a move does not automatically clear selection. It remains until
  explicitly cleared or replaced, including replacement by a new rectangle or
  clearing when an unselected card is dragged or tapped. Successful movement
  leaves selection-edit mode on/off as it was (D-34).
- Dropping onto a library, graveyard, or exile pile inserts at its top. Bottom
   insertion is available through Context Move; a drop does not ask top/bottom.
- D-55/D-56 add destination-entry resets for configured Graveyard, Exile, and
  Library: remove the entering card's counters and restore the database's default
  corner text. For creatures the default is printed P/T as a string. This applies
  across move entry points, not only context commands. Exact defaults for other
  card types/overlapping types, source-version mapping, and same-container reorder
  behavior remain open. D-65 fixes blank fallback and retained per-face defaults.
  Preserve stable
  card identity and all other unrelated fields; do not infer further cleanup.
- Dropping onto another ordinary card puts the moved card on top of its visual
  combined stack, creating a visual pile or joining the existing one. The
  destination card is not moved to another container, and neither card becomes
  a rules attachment, copy, or mechanically merged object. Special container
  slots retain their configured top-insertion behavior.
- The double-tap action sheet includes Move with at least six destination
  choices: top/bottom of Library, top/bottom of Graveyard, and top/bottom of
  Exile. Either participant's corresponding containers are accessible; the
  exact destination-picker layout remains open. These are ordered-placement
  operations, not inferred draw/discard/etc. commands.
- Multi-card selection permits dragging several cards, but within-set order,
  destination ordering, and atomic/sequential boundaries remain unspecified.

Top insertion is generic configured-pile behavior in the initial layout, not a
special consequence of the strings Library, Graveyard, or Exile. Relabeling the
same configured pile does not change its drop behavior. Moves retain the
existing unrelated-field preservation, immediate queue/sync, and undo contracts.

Proposed drag lifecycle:

1. Pick up the addressed card (or explicitly selected set) with one continuous
   drag. A one-card move needs no selection mode, long-press prerequisite, or
   confirmation step.
2. Show the source vacancy and a preview of the destination zone/slot/grid cell.
3. On release, commit one move action, snap visibly, and synchronize immediately.
4. Offer persistent Undo. An invalid/outside drop returns to its origin without
   a history action; a drop at the unchanged location is a no-op.

Destination classes:

- **Empty free-grid location:** change placement only.
- **Existing battlefield pile target:** add to that visual pile, with a clear
  before-drop highlight. Dropping on an ordinary card forms/joins its visual
  stack; do not accidentally attach, mechanically merge, or copy cards.
- **Container slot:** change container with explicit insertion ordering, leaving
  unrelated object fields intact. Library/graveyard/exile drops insert on top.
  Show its label, not just a colored rectangle.
- **Other labeled area:** perform the configured generic placement operation.
  Dropping into Cast cannot create a spell, targets, payment, or resolution.

Logical layout stores area, grid coordinates, pile membership, and within-pile
display order, not device pixels. Both players see a consistent arrangement
adapted to their viewport. Camera/zoom remains personal. Layout changes are
undoable and visible in replay, with compact grouping to reduce history noise.

Proposed drop priority: an explicitly highlighted zone slot, then an intentionally
highlighted pile target, then nearest grid location in the current area. A pile
acceptance region must be distinct from its surrounding free-grid drop region.
Never select an obscured destination the user cannot see. A tapped card retains
its orientation on an ordinary battlefield reposition.

Still required: grid dimensions/scaling, snap radius, pile acceptance region,
collision handling, edge autoscroll, zoom anchoring, orientation footprints,
empty-area bounds, multi-card order, and insertion defaults for other ordered
containers. The three special-pile top-drop defaults are confirmed. No unreviewed
pixel constants should determine these behaviors during implementation.

## 10.5 Ordinary battlefield piles

An ordinary pile is a **visual arrangement within a zone**, not a special zone,
not a rules attachment, not an MTG merged permanent, and not an action group.

Confirmed drag scope: move only the addressed card unless multiple cards have
been explicitly selected. Dropping on an ordinary card forms/joins the visual
stack as specified in section 10.4.

Confirmed tap scope (D-35): pile membership alone does not make a single tap
affect other pile members. Outside selection-edit mode, tapping a selected card
affects the selected set and retains selection; tapping an unselected card
clears selection and affects only that card. See section 10.3 for mode-specific
commands and touched-card-directed orientation changes.

Confirmed capacity and overflow (D-39):

- An ordinary visual stack contains at most three actual member cards, not an
  unlimited pile displaying only three. This is a layout/placement constraint,
  not a card-game rule. It does not cap special container piles
  (Library/Graveyard/Exile) or the number of individually selected cards across
  the table.
- A drop that would exceed three shows an invalid destination preview. Releasing
  restores the full pre-drag placement and selection under D-34. Do not silently
  split the stack, replace a member, or move only part of a selected set. The
  invalid drop creates no shared board action.

Confirmed compact controls (D-40):

- No separate ordinary-stack browser and no Select stack context action. Use
  ordinary rectangle selection, including its visible-portion rule, to select
  members; selection-edit mode can refine that set. Drag a selected member to
  move the selected cards together.
- Reordering and splitting are ordinary drags, not separate stack commands.
  Drag an individual member onto its current stack to bring it to the top,
  preserving other members' relative order and unrelated fields. Evaluate the
  final unique membership for capacity; this does not add a fourth member.
- Drag an individual member to an empty grid cell to split it out; remaining
  members stay stacked. Existing selection-aware drag scope and undo apply.
  No new dedicated whole-stack drag handle is specified; normal selection/drag
  is the supported path.

The earlier proposed browser, Select stack action, browser reorder handles, and
special whole-stack handle are not part of this design. Direct gestures address
the visible card under the touch, including exposed diagonal portions. Exact
fan offsets, minimum exposed hit targets, zoom behavior, and collision handling
still need specification; do not rely on an unapproved browser fallback.

Piles preserve per-card face-up/down and tapped state. A diagonal peek must not
expose a covered card's identity. Movement/undo preserves IDs and unrelated
values/links. A layout-only pile contains objects in the same zone/area, not a
pile straddling containers.

Examples: pile several lands for compactness, or pile identical tokens while
retaining each token's identity and counters. A token-count shorthand must not
silently replace multiple stateful objects with an unrelated single object.

Whole-pile movement must not be inferred from dragging an unselected single
card. It operates on multiple cards only after ordinary explicit selection.

## 10.6 Special piles: library, graveyard, exile

Special piles represent container contents, unlike the layout-only piles above.

Confirmed incoming movement: dropping a card onto any of these three configured
piles inserts it at the top. Context Move offers both top and bottom for each;
see section 10.4.

Confirmed pile entry (D-41): a single tap on the pile control opens its contents
and exposes its pile context actions together, rather than requiring a separate
double tap or a second menu-opening step. Exact header/count/body hit regions
and the combined panel layout remain to be specified; individual-card gestures
must remain distinct from pile-control gestures.

| Pile | Default presentation | Proposed direct controls |
| --- | --- | --- |
| Library | Configured covered pile and count; logical top/bottom exist, but the full contents browser must not show library order (D-42). | Confirmed: contents + actions on tap; View/Reveal top or bottom N, Draw one/N, Move top/bottom N, and Shuffle all (D-43/D-46/D-48). No top/bottom shuffle shortcuts. Direct pile-card drag and exact reorder controls remain proposed. |
| Graveyard | Latest/top card and diagonal edges for underlying cards, count. | Confirmed: tap pile control for contents + actions. Proposed: direct visible-card drag and ordered contents presentation. |
| Exile | Similar pile layout, respecting covered presentation; optional labeled subsets. | Confirmed: tap pile control for contents + actions. Proposed: direct visible-card drag, ordered contents, and optional labeled subsets. |

These are explicit display/container settings in a preset, not behaviors derived
from the names. A pile with any other label can use exactly the same settings.

A special pile's slot/header is never rotated by a tap. Individual cards shown
in its expanded browser retain Tap/Untap, Actions, and Inspect where structurally
representable; the browser does not replace the single-tap card contract with
an unrelated action. An explicit selection control serves multi-select.

Dragging the top card from a pile labeled Library to an area labeled Hand
records that literal move, not “draw.” Moving it anywhere else uses the same
operation. A browser changes actual container order only through an explicit
reorder operation; sorting/filtering the displayed list is local presentation.

Top/bottom refer to logical order, not screen orientation. Insertion order for
multiple cards and display subsets still need specification as generic
container behavior. Empty piles remain valid drop targets
and retain their label and count. No conventionally covered
pile leaks names through diagonal edges, drag ghosts, or routine notifications.
Explicit inspection is allowed under the cooperative trust model.

Confirmed full-library browsing (D-42): display contents without exposing their
underlying library order. Do not show actual position numbers, sequence, or
top/bottom indicators on individual full-browser entries. This does not shuffle
or reorder the library. D-50 selects card-type category columns for contents
browsers; D-51 supplies initial column/sort defaults, with exact category mapping
still open. This is an interface
convention, not an opponent information-access restriction. Explicit top-N
viewing/revealing is ordered (D-45); it is distinct from the orderless full view.
The earlier proposal to show every pile's actual order initially is rejected
for the full-library browser.

Confirmed library capabilities (D-43): expose View top N and Reveal top N among
the pile actions. Cards shown by reveal support their own context actions and
the ordinary hold inspector, including a route to existing top/bottom movement
commands. View/reveal presentation must remain distinct from an actual card
move. Draw N is now confirmed by D-46 as a literal preset move shortcut.

Confirmed View/Reveal distinction (D-44/D-45):

- View is not private/local-only browsing: explicit viewing is recorded in the
  shared log, including library contents viewing. The participant initiating
  View sees the card faces; the other participant sees backs by default. This
  is based on who performed View, not who is associated with the source pile.
- Reveal is also recorded in the shared log and shows card faces to both
  participants. The distinction is presentation, not whether the operation is
  logged/synchronized. Both are durable shared records, not live presence alone.
- View top N and Reveal top N show those cards in actual source order, starting
  with the library's top card. Opponent-facing backs in a View display retain
  that sequence. Full-library contents still hide the overall order under D-42.
- Ordinary opponent-facing View summaries must not inadvertently show card names
  instead of the specified backs. This does not revoke cooperative full access:
  explicit inspection remains available under D-07/D-38. Whether ordinary
  hold-inspection itself adds another log entry remains Q-06.
- Exact history-node/activity placement, historical-branch behavior, dismissal controls,
  and the temporary display's live/snapshot schema remain Q-28. Do not infer
  that the action is unlogged merely because it does not move a card.

Confirmed reference displays and Draw N (D-46, formerly P-18):

- Both View top N and Reveal top N use a shared temporary board display/tray,
  with the participant-specific face/back presentation above. Display existing
  card references, not copies or a new containing zone. Merely showing the cards
  leaves their library membership/order and unrelated state intact.
- Hold/context actions operate on the referenced real cards. An explicit Move
  to bottom/top or another destination changes the underlying card once, and
  its views update consistently. D-47 specifies fixed membership and location
  updates after moves. Deletion/removal, branch changes, and precise undo/schema
  behavior still need specification.
- Draw N is an explicitly chosen convenience for moving the top N
  cards of the selected configured library into its associated Hand container.
  The preset supplies those endpoints; the generic engine need not recognize a
   draw mechanic. No inferred extra effects or resets. D-49 fixes N-input,
   single-source relative order, and quantity clamping. Empty-source behavior,
   precise validation, and action boundaries remain open.
- Shared View/Reveal displays persist until explicitly dismissed, rather
  than disappear on a timer or application close. Its async/offline review,
  precise dismissal/undo behavior, and display-state schema require a separate
  contract; dismissal scope and attribution are fixed by D-47 below.

Confirmed display behavior (D-47):

1. A tray remains associated with the original shown card IDs and their recorded
   source order. It does not continuously track/refill from the library's current
   top N or reveal newly arriving top cards automatically.
2. After an explicit move, keep that card's tray entry with an updated location
   indicator rather than removing it. The entry still references the original
   card; other fields and the original display record are not overwritten.
3. Dismissing a shared tray is logged and removes only the active display, not
   the cards or its historical record. It does not put cards back or undo moves.
4. Either participant can dismiss a shared tray. Exact UI and historical undo
   behavior remain to be specified.

Confirmed additional library shortcuts (D-48):

1. A quick Draw one action alongside Draw N.
2. View bottom N / Reveal bottom N using the same shared logged reference-display
   model and face/back conventions. D-49 fixes bottom-subset display direction.
3. Move top N / Move bottom N to an explicitly chosen destination, using generic
   ordered-card movement. D-49 fixes relative destination order; bulk boundaries
   remain open.
4. Shuffle all is the only library shuffle shortcut. Do not offer Shuffle top N
   or Shuffle bottom N. No automatic shuffle merely because a pile was viewed.
   Stored shuffle outcomes and their undo/redo contract remain governed by the
   history/randomness specifications.

Confirmed N-card input/ordering defaults (D-49):

1. Show bottom-N subsets in their normal source top-to-bottom order, with the
   bottommost card marked; do not reverse the subset to bottom-first.
2. N-input starts at one and offers +/- plus numeric entry. Quick Draw one skips
   that picker; completing the required inputs executes without a separate Send.
3. Clamp a requested N to the source's available card count rather than rejecting
   an oversized request. This applies to quantity-based View/Reveal/Draw/Move
   commands; clamping alone does not authorize skipping selected IDs. D-67
   separately permits action-inapplicability skipping. Empty-source
   behavior, exact count feedback, and stale/offline boundary validation remain
   to be specified.
4. Multi-card moves from one ordered source preserve the cards' relative source
   order at the destination, whether inserted on top or bottom. Do not reverse
   their order through repeated insertion. Existing destination members keep
   their relative order. Cross-source selections and bulk action boundaries
   remain separate decisions.

Confirmed pile interactions and browser layout (D-50):

1. Drag from a special pile's top-card surface to move that actual top card;
   tapping the pile still opens contents and actions. Exact card/control hit
   regions and drag-versus-tap thresholds remain to be designed.
2. Contents browsers show cards in type-category columns like Cockatrice, not
   the proposed top-to-bottom Graveyard/Exile list. This is display grouping
   only, not a change to stored pile order. Full-library browsing still hides
   source order; explicit top/bottom-N displays retain their ordered presentation
   rather than regrouping by type. D-51 supplies initial column defaults;
   category definitions, multi-type precedence, and exact layout/gestures remain
   to be specified.
3. Contents browsers support name search/filtering and hold inspection. Search
   and filtering change only the browser presentation, never stored pile order
   or card locations. Explicit opening/View remains shared/logged; per-keystroke
   logging and exact search controls are not settled by this decision.

Approved initial-version browser-column defaults (D-51):

The owner approved these as a starting point, with possible later adjustment.
They are current defaults, not an unapproved proposal or a permanent layout
constraint. Revisit them during wireframe review/usability testing and update the
contract and acceptance scenarios together if changed.

1. Each card appears once, under one main type category supplied by the catalog
   or preset; unknown/unclassified cards have an Other column. Exact category
   precedence for multi-type cards remains a separate contract, not game rules.
2. Sort cards alphabetically by name within each column, independently of source
   pile order. Duplicate-name tie-breaking must not expose library positions.
3. On narrow screens, scroll sideways between columns and vertically within a
   column instead of shrinking cards until unreadable. Accessible navigation and
   gesture arbitration still need exact design.

Opponent-facing covered View layout, including whether category labels/grouping
would disclose card metadata, remains open. Do not treat category columns as
permission to expose faces or names contrary to D-44.

Still open: exact special-pile top-card drag hit regions, other visible-card
dragging, browser selection controls, column details, other bulk
conveniences, actual reorder controls, and exact gesture
arbitration. No blanket approval of those earlier proposals is implied by the
library-specific decisions above.

## 10.7 Communication and literal actions

A brief animation alone is insufficient for asynchronous review. Proposed layers:

| Layer | Examples | Persistence and effects |
| --- | --- | --- |
| Live presence | Dragging a card, browsing a pile, positioning a ping. | Ephemeral, online-only, best effort; no board edit, branch, or game-rule meaning. |
| Explicit View/Reveal | View library contents or View/Reveal top N. | Shared logged/synchronized operation, not merely presence; faces versus backs follow D-44, and top-N source order follows D-45. Exact record/display schema remains open. |
| Action-associated ping | Point to an object, participant marker, area, or location; optional style/label. | Durable, actor-attributed, linked to the recorded action/group and context; no mechanical side effects. |
| Literal board edit | Move/rotate a card, create a labeled piece, change a named value, add a generic line. | Explicit generic state transition with ordinary history, branching, and undo. |

Presence never reveals inspected hidden-card identities, search terms, or private
choices by default. It expires after disconnect/crash and cannot block play.
Exact expiry/throttling are still required protocol decisions. Offline work does
not replay a backlog of obsolete live cursor/searching signals on reconnect.

Durable pings have readable visual/accessibility descriptions and optional text,
not only animation or color. They surface next to the relevant object and in the
activity/review feed. Quick presets reduce typing. Their expiration/acknowledgment
policy is explicit; unseen markers do not disappear merely because time elapsed.

A ping is a communication record, not a rules target, activated ability,
declaration, or priority pass. Creating one while inspecting history does not
itself select a new active board-state continuation. Players interpret the
associated move, ping destination, and other literal edits themselves. Optional
ping labels/icons must not create inferred mechanical effects.

Markers live in contextual communication records with versioned edit/delete
history. Every durable ping names its specific source action, optional enclosing
group, effective context boundary, and optional stable object/participant IDs.
Moving a card does not create a rules incarnation or redirect its old pings.
If the card no longer exists at the viewed boundary, show the original context
link rather than pointing to an unrelated object.

On another branch, show only markers whose context belongs to that path, plus
an explicit route to inactive-line messages. Never migrate a response request
to a new continuation as if its author endorsed it. “Seen” or “acknowledged” is
not a rules approval. A persistent response request is a social signal, not a
permission lock on a cooperative table.

Proposed entry points: a ping handle/control on the current action, ping entries
in the double-tap sheet, and the currently selected log action. A table
request control uses a visible action context, not an unanchored marker. General
conversation remains supported through text/chat. Exact placement and tap
budgets need wireframes.

## 10.8 Additional controls needed for complete play

These are proposed essentials, not additional mandatory actions per card move:

1. **Card text/counters:** D-52 confirms bottom-right card text and separate
   colored text-bearing counters on cards/players; see the contract below.
   Exact controls and remaining tracker layouts still need review.
2. **Generic pieces/piles:** create/duplicate/remove labeled objects, move them,
   reorder piles, and inspect recorded changes. No engine Cast/Resolve command.
3. **Visual links:** binary persistent relationships with one standard connector,
   no labels/style choices initially (D-71); no target/combat/attachment meaning.
4. **Table markers:** optional labeled participant/status markers and explicit
   communication cues; no turn/priority state machine or permissions.
5. **Bulk operations:** multi-select, rotate selected, move top N to an explicit
   destination, edit selected counters, and manage overlapping pieces.
6. **History/recovery:** persistent undo, action stepping, branch indicator,
   quick return to displaced line, and pending-sync feedback.
7. **Navigation/inspection:** background pan, zoom, zone browser, text search,
   full card text, and direct route back from an inspector to the same boundary.

Ping/link tools must not require precision arrow dragging as their only entry
method. Accessible object/location pickers are also supported. Remembered UI
preferences may reduce work, but cannot introduce inferred game mechanics.

Confirmed G-07 model (D-52), replacing the earlier unapproved tracker-control
batch:

1. A card can have text in its bottom-right corner. P/T and loyalty are example
   uses, not engine-defined meanings; arbitrary text is also supported. Card
   context actions offer quick increment/decrement/set. Exact numeric and
   slash-pair adjustment behavior remains to be decided.
2. Counters are distinct colored markers with attached text. Offer a few
   selectable colors; D-58 selects the initial palette below. Both cards and
   players can have these counters, at most one of each color per target.
   Their text need not be numeric.
3. Numeric counter text supports quick increment/decrement/set. Non-numeric text
   remains editable. Neither counter edits nor corner-text edits calculate
   card characteristics, affect other fields, or cause automatic cleanup.

This does not approve the previous always-visible tracker +/- controls,
double-tap -> Counters submenu, or tap-number-to-edit proposal. Double tap still
opens card actions under the existing gesture contract; D-61 confirms inline
counter controls and player-panel entry. Exact hit regions, numeric
limits/recognition, styling, placement, bulk changes, and history/grouping remain
open.

Confirmed menu lifetime (D-54): counter increment/decrement actions keep the
context menu open for repeated adjustments. Each completed edit still queues
and synchronizes without dismissing the menu or requiring Send. Other actions
can dismiss it: a successful context Move to Graveyard closes the menu. Do not
apply a universal dismiss-after-action rule. D-61 confirms counter Set retention,
and D-63 confirms corner +/- and Set retention. D-67 removes the former Add
action. Other actions still need explicit lifetime decisions.

Confirmed reset exception (D-55/D-56): entering configured Graveyard, Exile, or
Library removes card counters and restores database-default corner text. This
supersedes blanket move
preservation only for the requested fields/destinations; it is a declared
generic move/reset composition, not a rules incarnation or card-text effect.
The repeated destination was clarified as Library. Card database definitions
provide the default corner string; for creatures it is printed P/T. A reset
replaces the current corner string, including a manual override, with that
default. D-64 conditionally approves loyalty/defense defaults when supplied by
the eventual source. D-65 fixes blank fallback and retained per-face defaults;
exact source/custom-definition mapping and same-container behavior remain Q-29.

Text-based quick actions:

Confirmed underlying principle (D-53): corner text is one plain text field.
Match its current content against patterns to offer appropriate quick actions;
those actions rewrite the same string, not separate P/T or loyalty fields.
Editing to a different shape changes the available actions. Counter numeric
conveniences likewise operate on its text rather than a separately authoritative
numeric value. D-62 fixes the initial pattern families/action sets; exact
matching/formatting details and numeric limits remain open.

Confirmed text-pattern quick actions (D-62):

1. A signed whole-number corner/counter string uses +/-1 and exact Set. For
   example, `4` -> `5`, or `0` -> `-1`. Do not clamp to zero or infer game limits.
2. Corner text matching two signed whole numbers separated by `/`, such as
   `2/3`, offers +/-1 for the left number, right number, or both, plus Set.
   Both +1 changes `2/3` -> `3/4`. Rewrite the same string; no P/T entity.
3. Other text, including decimals and symbolic/mixed pairs such as `*/*` or
   `2/*`, has only Set usable initially; arithmetic controls are disabled for that
   target under D-67. Do not evaluate expressions or infer arithmetic
   from card text. Exact matching/format preservation and numeric limits remain
   specification work.

Confirmed counter appearance/lifecycle (D-58):

1. Start with red, blue, green, yellow, purple, and gray; counter text must remain
   readable against the color. Exact styling/accessibility details remain open.
2. At most one counter of each color on a single card/player. The proposed
   multiple same-color counters on one target were rejected. Different targets
   may each have their own counter of that color.
3. Keep numeric counter text `0` visible. A completed edit to empty text removes
   the counter; temporary incomplete input does not publish removal by itself.
   D-59 trims text first: whitespace-only completed text removes the counter.
   Whether to offer a separate Remove shortcut remains open.
   Graveyard/Exile/Library entry still removes card counters as agreed.

Confirmed text trimming (D-59): all user-entered text is edge-trimmed on
completion, not only counter text. Preserve internal spaces/newlines. Pattern
matching and empty-value handling use the trimmed text: ` 2/3 ` becomes `2/3`,
` 0 ` remains visible as `0`, and whitespace-only counter input removes it.
Trimming does not settle blank-value behavior for unrelated fields or publish
unfinished input. See [the domain contract](02-domain-model.md#21-separation-of-state).

Confirmed counter action vocabulary (D-67, superseding D-60's initialization
interaction):

1. Each color has +1 color, -1 color, and Set color actions. There is no separate
   Add/Initialize action or prerequisite initialization step.
2. Arithmetic on existing whole-number text is relative; it is inapplicable to
   nonnumeric text. Individual unusable actions are disabled. D-68 makes absent
   counters applicable: +1 creates `1` and -1 creates `-1` directly.
3. No recolor action in the initial release. Counter uniqueness, trimmed-empty
   removal, visible zero, and menu retention remain unchanged.

Confirmed counter-menu touch behavior (D-61, updated by D-67):

1. Put counter controls directly in the card context menu rather than requiring
   a separate Counters submenu. Each color exposes -1/+1/Set, e.g.
   `[ -1 Red ] Red: 3 [ +1 Red ] [ Set Red ]`. Keep action availability separate
   from the field's plain text: nonnumeric text makes arithmetic unusable, not
   a reason to replace it with Add/Initialize.
2. Tapping a participant's player panel opens the equivalent counter controls;
   either participant can edit either panel. Exact hit regions remain open.
3. Completed Set returns to/retains the context menu, like counter +/-.
   Empty trimmed Set removes the counter, not the color's action controls.
   Existing Move to Graveyard dismissal stays unchanged.

Confirmed corner-text editing (D-63):

1. Corner-text +/- shortcuts and completed Set retain/return to the context menu,
   just like counter edits, for repeated adjustments without reopening it.
2. Completing Set with empty trimmed corner text leaves the corner overlay
   blank; do not silently restore the database default. This does not change
   printed card art or remove the card.
3. Offer Reset corner text to restore the database default without changing
   counters or other card state. For example, a default `2/3` edited to `5/6`
   returns to `2/3`. Destination-entry resets still clear counters as separately
   agreed. Exact Reset menu lifetime remains open.

Conditionally approved database-default mappings (D-64):

- Creatures already use printed P/T under D-56. Also populate default corner
  text with printed starting loyalty for planeswalkers and printed defense for
  battles when supplied by the source; cards without an applicable printed value
  default to an empty string. Provider choice is not approved by these mappings.
- These are descriptive database/import mappings, not engine-defined card types
  or automatic counter creation. See [default metadata proposals](02-domain-model.md#23-card-and-object-state).
  D-65 fixes current-face Reset and missing-data fallback. Overlapping types,
  custom-definition authoring, and exact source-version mapping remain open.

Confirmed default-data behavior (D-65):

1. Unavailable metadata produces blank editable corner text without blocking
   manual play or guessing values.
2. Retain resolved defaults with game definitions for offline Reset and stability
   across later catalog updates.
3. Use per-face defaults for multi-faced cards; Reset uses the selected printed
   face. Switching face alone preserves the current corner text.

Confirmed multi-selection control parity (D-66):

- When acting on a mass selection, offer the exact same card-action controls as
  for an individual card, with the action targeting every selected card. Do not
  replace them with a separate reduced bulk-action menu. Show the selected
  count/scope so the affected set is understandable.
- Apply the chosen edit to each card's own state: compatible numeric +/- changes
  each value relatively, Set supplies the same entered string, and Reset corner
  text uses each card's own retained current-face default. Other supported card
  context actions also target the selected set, with their normal unrelated-field
  guarantees and declared destination-entry resets.
- D-67 resolves applicability: apply the chosen action to each selected card
  where usable; skip cards where its individual action would be disabled. The
  mass action is disabled only if no selected card is applicable. For example,
  +1 Red increments a red `2` to `3` while leaving a red `flying` unchanged.
  This supersedes the prior blanket all-compatible gating proposal. D-68 resolves
  missing-counter arithmetic. Exact eligibility/error tables and affected/skipped
  rendering remain Q-30. Do not confuse applicability with invalid IDs or a
  structurally invalid drop that retains its established all-or-nothing behavior.
- Single-card versus selection targeting when opening an unselected card's
  context menu, exact mixed-value rendering, and bulk interruption/grouping
  boundaries remain open. Existing card tap/untap selection rules are unchanged.

Confirmed absent-counter outcomes (D-68):

1. +1 color on an absent counter creates it with text `1`.
2. -1 color on an absent counter creates it with text `-1`.
3. Set color with nonempty trimmed text creates/updates that color; empty text
   removes it where present and leaves absence unchanged.

Under rules 1/2, absence is treated as zero only as an arithmetic starting
point; do not create/display an intermediate zero counter or add initialization.

Confirmed card-note/annotation support (D-69):

1. Offer Edit note in the card context menu, opening an optional multiline text
   editor. Notes are persistent card text, distinct from corner text, counters,
   and action-linked messages/pings. Existing mass-selection control parity
   applies: one entered note can be applied to every selected card.
2. Show short note text directly on the card, not merely a note-present icon.
   For longer text, the initial presentation can truncate with an ellipsis;
   always show the complete stored note in the ordinary hold inspector/editor.
   Exact placement, fitting/truncation thresholds, and hit regions remain open
   for wireframe review. Truncate only display, never stored text.
3. Empty trimmed note text removes the note/its display, not the card. Moving the
   card retains notes, including on Library/Graveyard/Exile entry under existing
   unchanged-field rules. Notes never enforce their contents, e.g. a reminder
   saying not to untap does not prevent untapping.

Confirmed persistent-link touch behavior (D-70):

1. A card's Link context action enters endpoint picking; tap another card or
   player to create the link. Picking an endpoint must not also tap/untap that
   card or open the ordinary player menu. Accessible endpoint picking is also
   required; exact hit regions/cancellation remain open.
2. D-71 simplifies the original line/arrow/label proposal to a binary linked/not
   linked relationship with one standard connector, no labels or style controls.
   A link represents the players' relationship without engine interpretation.
   It is separate from an action-associated ping or an ordinary visual stack.
3. Link endpoints follow their referenced objects visually when moved, but
   linking alone does not move the other endpoint card or change counters/text.
   D-71 confirms selection fan-out and link removal below; attachment/follow-
   movement options remain separate review.

Confirmed simple-link selection/removal (D-71):

1. With several cards selected, Link -> one endpoint creates a separate link
   from each selected card to that endpoint. For example, selected A/B -> C
   creates A-C and B-C, not a merged card/group object. D-72 fixes duplicate-pair
   behavior; self-link presentation and interruption boundaries remain open.
2. A card's Links context entry lists its connections and offers removal.
   No label editing, style picking, or other link metadata editor is required.
   Do not require tapping a thin line as the only removal route. Endpoint
   presentation still follows covered-information conventions.
3. Removing a link removes only that relationship, never a card or its counters,
   corner text, notes, or placement. Link creation/removal are shared recorded edits
   with ordinary undo/recovery, not ephemeral presence or inferred game actions.

Confirmed binary-link semantics (D-72):

1. Treat links as undirected and unique per endpoint pair: A-B is the same
   relationship as B-A, with no duplicates.
2. Link on an existing pair leaves it linked rather than toggling it off; remove
   via the explicit removal control. A mixed selection links only missing pairs
   and leaves already-linked pairs unchanged. Exact no-op history remains open.

Next attachment/follow-movement scope question, not yet approved:

- Recommended initial scope: no separate Attach/follow-group tool. Binary links
  remain visual only; use existing multi-selection to move related cards together.
- Alternative: add an explicitly created placement/follow relationship so dragging
  an anchor also moves followers. This would be distinct from binary links and
  would require its own creation/removal, chain, destination, bulk, and undo
  contracts. No attachment is inferred from card type, overlap, or link creation.

## 10.9 Worked interaction examples

- **Move a card:** drag from the Hand-labeled row to a free grid cell. One
  gesture, one move, automatic sync; the engine does not label it playing a land.
- **Tap a card:** single tap the visible card. One orientation/tapped-presentation
  change; no activation, attack, or mana is inferred.
- **Read card:** hold it. Large art/text opens; no tap is sent and no branch is
  created. Inspecting another player's card works identically.
- **Inspect actions:** double tap. The action sheet opens; neither tap toggles
  the tapped state. The full manual action catalog remains reachable.
- **Compact lands:** drag a land onto a highlighted battlefield pile. Its zone
  and other values do not change; diagonal peeks show the pile's members. The
  engine only knows that cards were arranged together.
- **Move from pile:** drag the Library-labeled pile's top card to Hand. Record
  the move literally and respect configured cover presentation; no “draw” inference.
- **Move and ping:** move a card to Cast, then ping a participant/card associated
  with that move. Both sync and appear together without a generated “cast at”
  sentence, typed rules target, completion step, or Send.
- **Represent an unusual action:** create a generic labeled note/piece if needed,
  position it, and add action-associated pings. The players decide what it means.
- **Async question:** add “Response wanted” to the selected review action. The
  opponent sees its context hours later; the ping does not itself resolve or
  counter anything.

## 10.10 Outstanding detailed design

Before implementation, resolve gesture timings, layout measurements, snap/drop
rules, pile hit targets/order, card-versus-pile controls, library inspection
gestures, generic ping styles/association, live presence privacy/expiry,
ping schemas/lifecycle, full wireframes, and interaction budgets. The conceptual
layout and examples above do not close those readiness gaps by themselves.
