# 10. Game board and controls

**Initial-delivery connectivity (D-182):** game edits and shared controls require
a connection. Offline use is downloaded read-only history, not offline gameplay,
Undo, Reset, new random outcomes or queued synchronization. Optimistic updates
and animations still apply online. D-215 shows a Reconnecting... dialog blocking
gameplay when disconnected. D-216 automatically recovers already-sent actions
after reconnect without manual resend or duplicate execution; exact recovery
mechanics remain open.

Status: gesture choices, snapping, diagonal piles, selection-aware dragging,
top-insertion drops on Library, unordered Graveyard/Exile moves, visual stacking
drops, and top/bottom context moves for Library are confirmed (D-226). Layout,
detailed hit testing and communication mechanisms below are proposals for review.
The owner additionally
requires mostly text-free understandable play, optional text/messages, and
action-scoped pings. The engine is confirmed generic/rules-agnostic; the literal
move-and-ping presentation for the Stack area is defined in
[Visual action language](visual-action-language.md).
D-112–D-114 additionally require combat/Stack projections and contextual actions;
[their primary contract](combat-stack-and-projections.md) is being specified
case by case. D-115 keeps current work in stage 1, behavior specification; new
screen layouts and concrete gesture choices belong to stage 3.

This document is the primary contract for tabletop interaction. It supplements
the [Android experience](android-experience.md) and
[action catalog](game-actions.md); it does not implement a rules engine.

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
| B-009 | Drop on a special pile | Insert at the top of Library; add to unordered Graveyard/Exile without an insertion-position choice (D-226). |
| B-010 | Drop on another ordinary card | Put the moved card onto that card's visual combined stack; no attachment or mechanical merge. |
| B-011 | Context Move | The double-tap action sheet offers Library top/bottom, Move to Graveyard, and Move to Exile. No order-related actions for Graveyard/Exile (D-226). |
| B-012 | Tap selected / Untap selected | In selection-edit mode, explicit commands set the selected set uniformly while ordinary card taps edit membership. Outside that mode, use the selection-aware card-tap gesture. |

Gestures operate on either participant's cards. Ownership does not restrict
editing. Every completed state-changing interaction queues immediate sync and
has undo. Menus, zoom, selection previews, and cancelled gestures do not create
game-state actions.

D-121: no targeted action is quick-action-exclusive. Every combined targeted
shortcut and each constituent action must remain reachable through a full action
path, such as the target's context menu or an equivalent selection-aware path.
Phase/step or multiselect conditions may control prominent quick-action offerings,
not remove access to the underlying actions. Structural/applicability validation
still applies. Exact menu organization is stage 3; this does not require every
button in every menu or change existing direct-tap/selection gestures.

D-123: card actions through projections apply to the real source, including
moving that card by dragging a projection into a real destination such as
Graveyard. Use normal card-move/reset/token-removal behavior. Virtual combat slot
placement and occurrence cleanup are distinct from changing the real card;
operation disambiguation still needs case-by-case design. D-124 removes combat
projections when their source leaves the battlefield, regardless of entry point;
D-125 retains Stack effects until explicit removal/resolution, even if a move was
performed through the Stack projection itself or the source no longer exists.

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
| Shared Stack / action-linked pings                   |
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
- The shared Stack, renamed from Cast (D-113), supports actual cards and virtual
  projections. D-114 adds contextual quick actions; entry controls, ordering,
  lifecycle, and exact layout need case-by-case design, not automatic resolution.
- A temporary combat area supports projected attackers/blockers in visual paired
  groups, without individual attacker↔blocker assignments (D-112/D-139);
  projection creation preserves source positions,
  while later card edits/moves through them affect the originals (D-123).
  Its layout and lifecycle are not settled by this older conceptual sketch.
- Hands use an expandable fan/row rather than consuming the whole battlefield.
- Extra labeled areas, such as Command or Outside game, remain a proposed
  expandable-dock capability. **Sideboard is lobby-only (D-202)**, not an in-game
  extra area: no dock, contents browser, or view/edit/move path exposes it during
  play. Initial lobby placement is mainboard in Library, sideboard in its separate
  Sideboard area, and all other card areas empty; see the
  [setup contract](accounts-decks-and-lobby.md#initial-placement-and-lobby-only-sideboard--d-202).
  D-203 permits free mainboard/sideboard transfers in that lobby without legality,
  size or one-for-one restrictions; own Ready clears and saved decks stay unchanged.
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
provide selection-aware tapping. Exact command placement remains open; D-176
records individual card-orientation mutations. No tap/untap infers activation, mana, combat, or any
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
convention; it does not reveal it to the shared table. D-89 records this covered-
face exposure; exact triggers/coalescing remain Q-31. A pile's back/header is a control, not an
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
  switching across different definitions remain open. D-176 fixes individual
  target mutations for bulk edits.

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
  D-89 later confirms exposure logging for covered-face inspection, not ordinary
  already-visible-face holds; exact event placement/coalescing remains Q-31.

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
- Dropping onto Library inserts at its top; bottom insertion is available through
  Context Move. Graveyard/Exile drops add membership without an insertion index.
  None of these drops asks top/bottom (D-226).
  D-84/D-85 override insertion for tokens: a token moved into those configured
  destinations is removed from current state, with recoverable history.
- D-55/D-56 add destination-entry resets for configured Graveyard, Exile, and
  Library: remove the entering card's counters and restore the database's default
  corner text. For creatures the default is printed P/T as a string. This applies
  across move entry points, not only context commands. Exact defaults for other
  card types/overlapping types, source-version mapping, and same-container reorder
  behavior remain open. D-65 fixes blank fallback and retained per-face defaults.
  Preserve stable
  card identity and all other unrelated fields for retained instances; D-84
  separately removes token instances moved into those destinations (D-85).
- Dropping onto another ordinary card puts the moved card on top of its visual
  combined stack, creating a visual pile or joining the existing one. The
  destination card is not moved to another container, and neither card becomes
  a rules attachment, copy, or mechanically merged object. Special container
  slots retain their configured ordered or unordered movement behavior.
- The double-tap action sheet includes Move to Library top/bottom, Graveyard,
  and Exile. Either participant's corresponding containers are accessible; the
  exact destination-picker layout remains open. Graveyard/Exile have no top/bottom
  variants or other order-related actions (D-226). These are literal moves,
  not inferred draw/discard/etc. commands.
- Multi-card selection permits dragging several cards; D-176 records individual
  card moves in a sensible order. Existing source/spatial order defaults apply;
  remaining ordering cases do not permit an atomic bulk history node.

Ordered versus unordered movement is configured behavior in the initial layout,
not a special consequence of the strings Library, Graveyard, or Exile. Relabeling
the same configured pile does not change its drop behavior. Moves retain the
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
- **Container slot:** change container, with insertion ordering only for an ordered
  destination, leaving unrelated object fields intact. Library drops insert on
  top; Graveyard/Exile drops add membership without ordering.
  Show its label, not just a colored rectangle.
- **Other labeled area:** perform the configured generic placement operation.
  A generic move into Stack cannot infer targets, payment, or resolution.
  Explicit projection entry is a separate required capability under D-113;
  its gesture and state changes remain under review.

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
containers. Library top-drop and unordered Graveyard/Exile membership defaults
are confirmed (D-226). No unreviewed pixel constants should determine these
behaviors during implementation.

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

**D-225/D-226:** Library is ordered; Graveyard and Exile are unordered and have
**no order-related actions**, including top/bottom insertion or selection, reorder,
or shuffle. Do not keep these as visual-placement actions. Context Move offers
one destination per Graveyard/Exile, without an insertion position. Browser
sorting/filtering remains personal presentation, not container reordering.
Specific-card moves from Graveyard/Exile may still choose Library top/bottom as
their destination; that orders Library, not the source. For selective Undo/Redo,
Library operations preserve their relevant ordering dependencies; independent
Graveyard/Exile arrivals do not depend on unrelated contents/absolute indices. See
[selective Undo/Redo](history-and-undo.md#selective-mutation-undoredo--d-224).

Incoming drops insert at Library's top or add to Graveyard/Exile membership;
see section 10.4. This supersedes the earlier all-three-piles top/bottom contract.

Confirmed pile entry (D-41): a single tap on the pile control opens its contents
and exposes its pile context actions together, rather than requiring a separate
double tap or a second menu-opening step. Exact header/count/body hit regions
and the combined panel layout remain to be specified; individual-card gestures
must remain distinct from pile-control gestures.

| Pile | Default presentation | Proposed direct controls |
| --- | --- | --- |
| Library | Configured covered pile and count; logical top/bottom exist, but the full contents browser must not show library order (D-42). | Confirmed: contents + actions on tap; View/Reveal top or bottom N, Draw one/N, Move top/bottom N, and Shuffle all (D-43/D-46/D-48). No top/bottom shuffle shortcuts. Direct pile-card drag and exact reorder controls remain proposed. |
| Graveyard | Unordered contents and count; exact collapsed-pile display remains open. | Confirmed: contents + actions on tap; move specific cards, with no order-related actions (D-226). Visible-card drag must identify the actual card, not a logical top position. |
| Exile | Unordered contents and count, respecting covered presentation; optional labeled subsets remain proposed. | Confirmed: contents + actions on tap; move specific cards, with no order-related actions (D-226). Visible-card drag must identify the actual card, not a logical top position. |

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

For ordered containers, top/bottom refer to logical order, not screen orientation.
Insertion order for multiple cards and display subsets still need specification
as generic container behavior. Empty piles remain valid drop targets
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
  explicit inspection remains available under D-07/D-38. D-89 covers inspection
  of otherwise-covered faces; deduplication with View records remains Q-31.
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
   ordered-card movement. D-49 fixes relative destination order; D-176 records
   individual card-move nodes.
4. Standalone Shuffle all shuffles the whole Library; D-193 also adds combined
   shuffle-and-draw actions. Do not offer Shuffle top N
   or Shuffle bottom N. No automatic shuffle merely because a pile was viewed.
   Stored shuffle outcomes and their undo/redo contract remain governed by the
   history/randomness specifications.

Confirmed N-card input/ordering defaults (D-49):

1. Show bottom-N subsets in their normal source top-to-bottom order, with the
   bottommost card marked; do not reverse the subset to bottom-first.
2. N-input starts at one and offers +/- plus numeric entry, except D-193's
   shuffle-and-draw N action defaults to seven. Quick Draw one skips
   that picker; completing the required inputs executes without a separate Send.
3. Clamp a requested N to the source's available card count rather than rejecting
   an oversized request. This applies to quantity-based View/Reveal/Draw/Move
   commands; clamping alone does not authorize skipping selected IDs. D-67
   separately permits action-inapplicability skipping. Empty-source
   behavior, exact count feedback, and stale/offline boundary validation remain
   to be specified.
4. Multi-card moves from one ordered source into an ordered destination preserve
   the cards' relative source order, whether inserted on top or bottom. Do not
   reverse their order through repeated insertion. Existing destination members keep
   their relative order. Graveyard/Exile destinations add membership without a
   destination order (D-226); individual mutations still have recorded execution
   order. Cross-source selections and bulk action boundaries
   remain separate decisions.

Confirmed pile interactions and browser layout (D-50):

1. Drag from Library's top-card surface to move that actual top card. For unordered
   Graveyard/Exile, a visible-card drag addresses that specific displayed card,
   not a logical top position (D-226);
   tapping the pile still opens contents and actions. Exact card/control hit
   regions and drag-versus-tap thresholds remain to be designed.
2. Contents browsers show cards in type-category columns like Cockatrice, not
   the proposed top-to-bottom Graveyard/Exile list. This is display grouping
   only, not a container reorder or membership change. Full-library browsing still
   hides source order; explicit top/bottom-N displays retain their ordered presentation
   rather than regrouping by type. D-51 supplies initial column defaults;
   category definitions, multi-type precedence, and exact layout/gestures remain
   to be specified.
3. Contents browsers support name search/filtering and hold inspection. Search
   and filtering change only the browser presentation, never Library order
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

Still open: exact Library top-card drag hit regions, other visible-card
dragging, browser selection controls, column details, other bulk
conveniences, actual reorder controls for ordered containers, and exact gesture
arbitration. No blanket approval of those earlier proposals is implied by the
library-specific decisions above.

## 10.7 Communication and literal actions

A brief animation alone is insufficient for asynchronous review. Proposed layers:

| Layer | Examples | Persistence and effects |
| --- | --- | --- |
| Live presence | Dragging a card, browsing a pile, positioning a ping. | Ephemeral, online-only, best effort; no board edit, branch, or game-rule meaning. |
| Explicit View/Reveal | View library contents or View/Reveal top N. | Shared logged/synchronized operation, not merely presence; faces versus backs follow D-44, and top-N source order follows D-45. Exact record/display schema remains open. |
| Group/event-scoped ping | Point to an object, participant marker, area, or location; one standard marker, no label/style picker (D-77). | Durable, actor-attributed record linked to one group/event; visible only in that context, including event-tree review (D-75). No mechanical side effects. |
| Literal board edit | Move/rotate a card, create a labeled piece, change a named value, add a generic line. | Explicit generic state transition with ordinary history, branching, and undo. |

Presence never reveals inspected hidden-card identities, search terms, or private
choices by default. It expires after disconnect/crash and cannot block play.
Exact expiry/throttling are still required protocol decisions. Offline work does
not replay a backlog of obsolete live cursor/searching signals on reconnect.

Durable pings have readable visual/accessibility descriptions, not only animation
or color; D-77 requires no typing or dedicated label/style picker. D-75 scopes
their display to one group/event. Advancing beyond that context hides its ping overlays from
the later board, even if unseen; this is not deletion. They remain viewable when
reviewing that step in the event tree, including offline for downloaded games.
They are not persistent table markers or acknowledgment-gated reminders.

A ping is a communication record, not a rules target, activated ability,
declaration, or priority pass. Creating one while inspecting history does not
itself select a new active board-state continuation. Players interpret the
associated move, ping destination, and other literal edits themselves. Marker
appearance/accessibility descriptions must not create inferred mechanical effects.

Markers live in contextual communication records with versioned edit/delete
history. Every durable ping belongs to one group/event, retaining source-action
and effective-boundary context and optional stable object/participant IDs.
Moving a card does not create a rules incarnation or redirect its old pings.
If the card no longer exists at the viewed boundary, show the original context
link rather than pointing to an unrelated object.

On another branch, show only markers whose context belongs to that path, plus
an explicit route to inactive-line messages. Never migrate a response request
to a new continuation as if its author endorsed it. “Seen” or “acknowledged” is
not a rules approval. A persistent response request is a social signal, not a
permission lock on a cooperative table.

Confirmed ping interaction direction (D-74): pinging is a persistent interaction
mode, analogous to tap/selection-edit interaction, not a one-shot command beside
an action. Entering the local mode is not itself a completed ping or shared edit.
Durable pings retain recorded context; D-75 clarifies that pinging is a toggleable
state within one event and pings belong only to its group/event. They do not
carry forward onto later events. Here group/event means the user's visible
grouped history unit; exact mapping to atomic actions remains later review.
Exact association/defaults, mode controls, and gesture arbitration remain open.
General conversation remains supported through text/chat.

D-119 adds automatic ping order numbers across all pings in the same grouped
event, not per target/source/attacker. The [primary ping contract](visual-action-language.md#114-pings-point-they-do-not-declare-mechanics)
keeps numbering separate from user labels/styles and damage rules. Exact order
editing/removal/re-add/coalescing behavior remains under review.

Confirmed optional communication direction (D-94/D-95):

1. A table Chat control opens the game-wide conversation. Posting a message is
   ordinary text submission, separate from gameplay publication; play never
   waits for a message or a gameplay Send step.
2. A group/event context Note/Reply action opens a multiline editor linked to
   that event. Keep these optional text records distinct from card notes and
   pings; text is not required to explain an action. Trim completed input under
   D-59 without publishing unfinished text. Exact threads, editing/removal,
   submission controls, and historical/branch association remain later review.

D-94 excludes submitted game-wide chat from Undo; edit/delete and unsent-delivery
policies remain open. D-95 makes event comments ordinary nodes on the current
tree path, retained through the comment when undone. That path is already a
branch; no separate comment-only fork or activation control is needed. Apply
normal tree/base rules. D-160 keeps the whole abandoned continuation visible
if it contains a comment, without a cutoff; D-223 confirms this even for a
single-player/no-exposure continuation. Exact comment/group association and
revisions remain later work. Card
notes, event pings, and ordinary editor drafts keep their distinct behavior.

Confirmed event-scoped toggle details (D-76):

1. Within the event, tapping an already-pinged destination toggles that ping off;
   tapping an unpinged destination toggles it on. This is distinct from binary
   persistent Link, whose repeated creation is not a toggle (D-72). Completed
   ping changes queue/sync without Send; exact revision/history remains open.
2. Moving to a different event turns Ping mode off; the new event starts without
   inherited pings. Returning to the old event shows its recorded pings without
   automatically enabling input mode. Record visibility follows D-75 regardless
   of the local toggle.

Repeated destination taps toggle presence under D-76, rather than placing
duplicates. Exact location matching/hit tolerance remains open.

Confirmed mode interaction/marker controls (D-77):

1. Ping and selection-edit modes are mutually exclusive. Switching modes retains
   the selected set; a ping tap affects only the touched destination, not the
   whole selection.
2. In Ping mode, destination taps do not also tap/untap cards, edit selection,
   clear selection on the background, or open ordinary player controls. Other
   gestures and accessible equivalents still require exact arbitration.
3. Initially use one standard ping marker, with no dedicated label/style picker
   or required typing. Optional notes/chat remain separate communication tools.

Confirmed additional entry (D-78): offer a Ping context action alongside Ping
mode. This does not replace the mode or change event scope. D-79/D-80 fix the
direct action and selection scope below; menu lifetime remains open.

Confirmed context-Ping behavior (D-79, selected-set rule revised by D-80):

1. On an individual card/player, context Ping directly toggles that destination's
   ping in the current event without entering/exiting Ping mode or requiring a
   second destination tap. Preserve unrelated state and selection.
2. For a card context menu scoped to a selection, display only Ping or Unping
   according to the card used to open that menu: unpinged -> Ping; pinged ->
   Unping. Apply that displayed action uniformly to all selected cards under
   D-66 parity, retaining selection. Do not independently invert members or
   choose the action from whether any/all selected cards are pinged (D-80).

Earlier required action-adjacent one-shot and P-15 drag-handle entry proposals
are replaced by the confirmed mode direction; D-78 adds a context shortcut, not
those earlier controls. Exact editing/
removal revisions, historical-action targeting, and history boundaries follow
separately; this batch does not settle grouping/playback.

## 10.8 Additional controls needed for complete play

These are proposed essentials, not additional mandatory actions per card move:

1. **Card text/counters:** D-52 confirms bottom-right card text and separate
   colored text-bearing counters on cards/players; see the contract below.
   Exact controls and remaining tracker layouts still need review.
2. **Generic pieces/piles:** create/duplicate/remove labeled objects, move them,
   reorder piles, and inspect recorded changes. D-112–D-114 separately require
   projections and contextual Stack actions, not inferred card-rule execution.
3. **Visual links:** binary persistent relationships with one standard connector,
   no labels/style choices initially (D-71); no target/combat/attachment meaning.
4. **Turn/phase/step:** D-96/D-105 require first-class engine tracking, distinct
   from optional generic table markers. No priority state/UI/legality validation.
   Remaining controls and progression details are under review.
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
These resets describe retained instances. D-84/D-85 remove tokens moved into
these configured destinations instead; preserve their prior fields in history
for undo rather than requiring a live reset token in the pile.

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
unfinished input. See [the domain contract](domain-model.md#21-separation-of-state).

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

D-186/D-187: +/- applies to the current numeric counter value, so concurrent +1s
both count. Set writes its requested value without a previous-value comparison,
even if the counter changed meanwhile. Nonnumeric data still makes arithmetic
inapplicable. Record actual accepted before/after values for history and Undo.
Ordinary card edits likewise do not check previous orientation/text. D-187 limits
optimistic conflict guards to moves and Stack additions, superseding the earlier
[25-rule proposal](optimistic-locking.md) rather than approving its other details.

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
  or automatic counter creation. See [default metadata proposals](domain-model.md#23-card-and-object-state).
  D-65 fixes current-face Reset and missing-data fallback. Overlapping types,
  custom-definition authoring, and exact source-version mapping remain open.

Confirmed default-data behavior (D-65):

1. Unavailable metadata produces blank editable corner text without blocking
   manual play or guessing values.
2. Retain resolved defaults with game definitions for provider-independent Reset
   and stability across later catalog updates. Reset requires a game-service
   connection in initial delivery (D-182).
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
   D-71 confirms selection fan-out/removal and D-73 excludes separate attachment/
   follow-movement tools from the initial release.

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

Confirmed initial attachment/follow-movement scope (D-73): no separate Attach
or follow-group tool. Binary links remain visual only; use existing multi-
selection to move related cards together. Dragging one linked card alone does
not move its neighbors. No attachment is inferred from card type, overlap, or
link creation. Explicit anchor/follower grouping is outside initial scope, not a
remaining first-release implementation decision.

Confirmed G-09 token creation (D-81, simplified by D-82):

1. Table Create -> Token opens a searchable catalog of token definitions. Pick
   a definition explicitly; no card text automatically creates tokens or chooses
   their definition. This does not select a provider or approve a general editor.
2. Choosing a definition creates exactly one token with a fresh stable ID and
   automatically places it on the table. No quantity control, placement preview,
   manual placement step, or extra confirmation/Send. Use ordinary drag afterward
   if the player wants a different position.
3. Clone creates token instances from either ordinary cards or tokens (D-83/D-84),
   using the field-copy/selection contract below; no card-text copy rules.

D-82 supersedes D-81 quantity selection and rejects the later placement-preview/
picker proposal. Exact automatic location selection, initialization, missing/
offline catalog data, custom definitions, removal details, and history
boundaries remain separate review items. Placement must not bypass ordinary
stack capacity or displace unrelated cards. D-83 removes Repeat last token;
Clone supplies extra instances.

Confirmed Clone behavior (D-83/D-84):

1. Clone copies the source's current printed face, covered/orientation state,
   corner text, counters, and notes into one independent new token instance with its
   retained defaults. Do not copy persistent links or event pings; source state
   stays unchanged. Place automatically, without a preview/picker.
2. Selection-scoped Clone creates one copy per selected card under D-66 parity;
   no quantity picker. Exact context routing and action boundaries remain open.
3. Omit the separate Repeat last token shortcut initially; use Clone for more
   instances and Create -> Token to choose another definition.

Confirmed token destination removal (D-84/D-85): moving a token into configured
Library, Graveyard, or Exile removes it from current table state rather than
retaining it in that destination. This applies to catalog tokens and Clone
outputs, including copies of ordinary cards. Ordinary cards still move with
their approved destination resets. Token status is explicit instance metadata,
not inferred from printed type, card text, a user label, or being in a stack.
Removal is queued/synchronized and recoverable in history/undo, not deletion of
the historical instance. Do not require intermediate destination occupancy or
discard the token's recoverable fields. Exact move/removal records and handling
of links/trays/selections referring to removed objects remain open.

Confirmed removal controls (D-85, restricted by D-174):

1. Apply the same token disappearance to context Move and other explicit move
   controls targeting Library/Graveyard/Exile, not only drags. For mixed selected
   sets, move ordinary cards normally and remove tokens; do not skip tokens as
   inapplicable or discard unrelated selected cards. Exact boundaries stay open.
2. Offer Remove for tokens, including token-selection use. Remove explicitly
   from current state without moving to a pile, automatically queue/sync, and
   support undo/history without a routine confirmation dialog.
3. Non-token cards have no Remove/delete action: they remain in the game and
   move between locations. Bulk actions and actions through projections must not
   delete them. Exact mixed-selection offering/routing remains open; it cannot
   authorize non-token deletion. Combat/Stack projection removal remains
   occurrence-only and never deletes a non-token source card (D-174).

Confirmed blank-token scope (D-86): include one Blank token definition in the
normal token catalog, not a separate Create -> Custom token form. Choosing it
creates one automatically placed token using the same flow and lifecycle as
other catalog tokens. Players can then use existing corner-text, counter, and
note controls, Clone, and Remove. No custom name/corner editor is required before
creation; no dedicated custom-token/image-authoring flow is approved. Exact blank
presentation and initialization remain detailed design work.

Confirmed G-10 basic randomness controls (D-87):

1. Table Roll die creates one result, defaulting to a six-sided die; an optional
   sides control supports other dice. No dice-count or game-specific roll mode
   initially. Exact sides picker, bounds, and remembered defaults remain open.
2. Table Flip coin creates one heads/tails result with no configuration step.
3. Show actor, requested die/coin, and actual result in shared history; completed
   rolls/flips automatically queue/sync. Review/redo reuse the recorded result,
   not a fresh random draw. New invocations require a connection (D-182).
   Source/algorithm, exact display and action boundaries remain later review;
   results change no cards or values.

D-92 simplifies new die/coin invocations to fresh random draws, including after
undo, superseding D-88 and the pending seed/reset/draw-position proposal. No
opponent action is needed for a fresh draw. Results may repeat by chance. History,
review, redo, and retries of the same operation still reuse its stored result.
Exact generator/source and online concurrency remain later review; D-182 excludes
new offline random actions.

D-93 confirms every explicit new Shuffle samples a fresh order, including after
undo; review/replay/redo and retransmission reuse the recorded order. A fresh
shuffle may reproduce a prior permutation by chance. D-93 itself adds no shuffle
shortcuts and does not imply that covered order was exposed; D-193 later adds
explicit shuffle-and-draw compositions using the same recorded-randomness model.

**D-224 revision:** selective Undo/Redo removes/reinserts a mutation only if all
later mutations retain identical relevant-state `(from, to)` tuples, within a
hard suffix-work cap N. The relevant state for a tap must allow a prior independent
move of the same card to be removed; two edits of the same counter may instead
block each other. Selective Undo excludes revealing targets. Per-mutation state,
N and pointer/control/Redo-queue integration remain open. The following earlier
rewind/exposure summary does not override that selective rule.

Earlier D-159/D-222 rewind model: Undo moves the shared active pointer one parent back and
remembers the last active head for Redo toward it. Retain all nodes; changed play
creates a new active branch, while a graphical explorer can activate any retained
branch/node. Only single-player/no-exposure inactive continuations qualify for
hiding behind Show hidden branches; mixed-player/exposure continuations stay
visible. D-223 also keeps event-comment continuations visible in full, even with
one author and no exposure; those paths do not qualify for hiding.
Exposure coverage includes
library View/Reveal, covered-hand/card-face viewing including covered-face holds,
and die/coin results, but not every visible-card hold or search keystroke. Ordinary
summaries do not automatically disclose viewed card identities. See the
[primary undo-branch contract](history-and-undo.md#information-gaining-actions-and-undo-branches).

D-160 supersedes D-91's visible-path cutoff: keep trailing ordinary nodes visible
too when the abandoned continuation contains exposure/comment. D-91's actual-
exposure classification remains, including draws/moves/face changes, not only
View/hold. Exact event placement/coalescing, forward/pre-fork presentation, non-Undo
branch controls and read-only offline exposure handling remain Q-31. Stale Undo itself
silently discards its specific transition under D-161. This is not a persistent ping or
access restriction.
D-96/D-105 reject omitting turn/phase/step tracking or substituting blank tokens,
notes, and counters. Provide first-class engine state integrated with history,
undo, branches, online-only actions, and automatic synchronization. Exact controls,
state fields, and transitions are refined below; remaining details stay open.
D-105 removes priority tracking and controls entirely. Generic player values
remain separate.

D-97 fixes MTG phases in code: Beginning → Precombat main → Combat → Postcombat
main → Ending. No configurable sequence, generic phase-definition system, or
phase editor is required. This replaces the proposed configurable MTG preset.

D-98 confirms individual MTG steps, not only phases; the standard sequence is
in [the domain model](domain-model.md#25-participanttable-values-and-markers).
D-99 requires quick-action offerings to vary with phase/step. Entering a step
does not by itself execute a shortcut. D-100 confirms the first shortcuts below;
other mappings remain under review. Do not infer card rules from these controls.

Confirmed first shortcuts (D-100):

1. Untap step: Untap all for the turn player's battlefield cards.
2. Draw step: Draw one from the turn player's Library to their Hand, using the
   already-approved literal draw/move behavior.
3. Also offer Untap all + advance and Draw one + advance variants. One press
   performs the corresponding card operation and advances tracking; no second
   press or gameplay Send step is needed.

Standalone versions leave tracking unchanged. Both forms require invocation,
not automatic execution on step entry. Actual edits/progression use normal
history, undo, queue, and sync behavior. D-110 excludes skip-untap flags. Exact
participant association, remaining no-op/error records, atomic/group boundaries,
and other step controls remain later work.
D-111 settles advancement for valid no-change/empty-source combined shortcuts.

D-101 clarifies advancement to the next step, not the next whole phase:
Untap all + advance goes Untap → Upkeep; Draw one + advance goes Draw → Precombat
main. Do not skip Upkeep/Draw after Untap or automatically execute another card
operation on arrival. There is no priority/pass state under D-105.

Confirmed progression controls (D-102):

1. A standalone Next step control advances tracking without card edits, so
   players can finish a step without using its card-operation shortcut.
2. Tapping the phase/step indicator opens a direct phase/step choice for skips
   or corrections, without replaying intermediate card operations.

Exact action/group boundaries and touch layout remain open.

These controls edit shared tracking; they are not history-review navigation.
Normal undo, branch, online-only action and automatic-sync contracts apply. Direct
phase/step choice does not itself choose a different turn/player; those controls
and effects still require review.

Confirmed turn tracking (D-103):

1. Track and display whose turn it is and the turn number.
2. Next step from Cleanup starts the other player's Untap step and increments
   the turn number, without automatically untapping cards or drawing.

Normal next-turn transitions increment the number per player's turn, not round.
D-109 accepts an approximate count when players repeat steps manually; do not
require it to account for every rules-level extra turn.
Cleanup → next turn updates shared tracking under normal history/undo/branch/
queue/sync behavior; simply reviewing Cleanup does not start another turn.
D-106 adds End turn from any phase/step and excludes manual turn-number editing.
End turn uses the same transition as Next step from Cleanup: other player's
Beginning/Untap, turn number +1, with no automatic untapping/drawing or skipped
step card operations. It changes tracking, not game completion. Normal undo,
history, branching, online-only actions, and automatic sync apply. Undo may restore
the recorded previous number; no separate turn-number editor is required.
Exact touch layout remains open; D-108 settles initial turn context and D-109
settles manual extra-turn handling without dedicated controls or accurate counts.

Confirmed D-105: remove priority tracking and UI from this release, superseding
D-104 and the priority portion of D-96. No priority indicator, Pass action,
holder/default/reset/correction picker, pass records, or resolving/no-holder
tracking. Players decide legal MTG timing and interrupt the recorded action path
when they want to respond. The engine neither gates interruption on priority nor
infers it from phases, cards, or an action queue. Existing structural validation,
cooperative access, and branch/history behavior remain intact.

The earlier priority-default/tap/correction proposals are withdrawn. MTG's
no-priority intervals (rules 117.2e/117.3a/514.3a, [rules reference](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt))
remain game knowledge for participants, not engine state or requirements.

The earlier turn-indicator player/number editor proposal is withdrawn; D-106
explicitly rejects editing the number. D-109 also rejects dedicated extra-turn
controls; do not add a player/count editor to compensate for manual step repeats.

Confirmed End turn touch control (D-107): keep End turn visible beside Next step,
invoking it in one tap without routine confirmation; Undo handles mistakes.
No menu, hold, or extra confirmation step is required. Exact geometry and hit
targets remain later work without weakening the approved one-tap interaction.

Confirmed setup (D-108/D-201): choose the starting player **in the lobby**, offering
either participant or **Random**; begin turn 1 at Beginning/Untap with that starter.
Entering the step initializes tracking, not opening-hand draws or a rules-driven
setup procedure. Exact control layout, defaults, edit access, random-resolution
timing/reselection and change/recovery boundaries remain open. Changing the starter
selection clears only the changing player's Ready, preserving the other player's
(D-204), not both players' Ready as the assistant proposed.
Retain the random starter result for retries/review of the same operation.

D-192 additionally excludes automatic opening draws on game start. D-193/D-194
require two Library context actions: return the whole hand → shuffle Library →
draw N (default seven), or draw the pre-return hand size minus one. Both are also
quick actions **only during turn 1's Untap step**. Context access remains outside
that window. Per-card sequencing/concurrent count timing/count edges remain open in the
[setup contract](accounts-decks-and-lobby.md#opening-hands-and-mulligan-shortcuts--d-192).
D-200 separately requires automatic game-start Library shuffling and 20 starting
life, without a required pregame life editor. This is not a shuffle on every
Untap entry or an automatic opening draw; ordinary in-game life edits remain.
No rules-driven mulligan gate is approved.

D-109 rejects the proposed Start another turn shortcut and any extra-turn
scheduler. Players handle extra turns through existing manual phase/step controls.
Those changes preserve turn player/number and do not infer a new turn. An
inaccurate displayed turn count is acceptable; no automatic reconciliation or
manual number editor is needed. Normal End turn and Next step from Cleanup keep
their approved other-player/number +1 transition.

Confirmed D-110: Untap all remains literal for the turn player's battlefield
cards, without a dedicated skip-untap flag. Players re-tap exceptions manually;
do not infer exclusions from card text, notes, or markers. The same scope applies
to standalone Untap all and Untap all + advance. Preserve unrelated card state.

Confirmed shortcut edge cases (D-111):

1. Untap all + advance still advances to Upkeep when all affected cards are
   already untapped or there are no affected cards.
2. Draw one + advance with an empty Library draws nothing but still advances
   to Precombat main; no automatic loss or other inferred rule effect.

These rules cover valid no-change/empty sources, not missing IDs or other
structural failures. Record/sync the actual step transition even when no card
changes. Do not invent a drawn card or card-change record. Standalone variants
still leave tracking unchanged. Exact no-op history records, feedback, and
atomic/group boundaries remain later review.

The proposal to omit further phase-specific conveniences is rejected. D-112–D-114
require temporary combat, attacker/blocker projections and assignment, Stack
ability projections, and contextual Stack actions. Review each case in
[combat, Stack, and projections](combat-stack-and-projections.md) before
implementation; do not substitute generic controls as a reason to skip design.
Current work is stage 1 behavior specification (D-115); UI drafts follow stage 2
adversarial behavior review. D-116–D-121 further settle separate combat actions,
multiselect declaration variants, assigned/unassigned blockers/adaptive spacing,
numbered pings, projection-link lifetime, and full-path action access. Next:
combat/source lifecycle behavior, not a final screen layout. D-122 confirms area
removal clears its projections/links without changing originals' current state;
D-123 makes card edits/moves through projections affect the real card. D-124
removes combat projections on source departure; D-125 retains Stack effects
until explicit removal/resolution. D-126/D-139 keep surviving cards in their slots
when an attacker leaves: grouped with other attackers, or in a blocker-only pair
after the final attacker leaves. D-138 permits visual reflow
while preserving surviving slot membership/order, replacing literal fixed
positions; never automatically transfer survivors to other pairs. D-127 requires explicit closure, with an advance out of
combat + close shortcut; ordinary phase/step changes and End turn leave the area
open. D-128 offers the quick action only at End of combat → Postcombat main,
not earlier combat steps. Separate advance/close and full non-quick access remain
available (D-121). D-159/D-160 make Undo one parent-node move regardless of author;
composites may require multiple presses. D-176 makes each node one individual
mutation, including bulk/combined commands, superseding combined-node mappings
such as D-132. D-177 makes groups concurrent-animation playback steps with exact
mutation selection on the paused bottom timeline. D-133 reuses existing attacker projections on repeated
declaration and adds only newly declared sources; the chosen tap variant still
applies. D-134 permits unlimited independent Stack projections, including several
from one source, each explicitly removed/resolved separately without removing
siblings or the source. D-135 limits each source to one projection per combat area
across attacker/blocker roles; dragging an attacker to a blocker position moves
that same occurrence without moving/editing the source. D-136/D-139 retain the
remaining group's membership, with real sources unchanged. D-137 requires paired
opposing slots supporting multiple attackers/blockers on their respective sides,
visually growing/shrinking with card count. D-138 removes fully empty pairs
immediately, closing gaps, but retains pairs with either side occupied. Between-pair
drops insert a new pair with the moved cards, preserving other pairs' contents and
relative order; fully empty origins disappear by the same rule. Combat stays open
with no pairs. D-139 makes each pair one visual group without individual attacker↔
blocker assignments; placement/optional pings communicate finer details without
an inferred blocking rules graph. D-140 makes real battlefield-card drags into
attacker/blocker slots create their projections or retarget existing occurrences
without duplicating/recreating them. Preserve originals' position/location and
orientation; no implicit tap/untap. D-141 routes selected real battlefield cards
to that same slot and side, creating/reusing each source's occurrence rather than
one pair per card. D-142 makes any dragged collection, including mixed attacker/
blocker projections and real/combat-projection views, join the chosen slot side-
by-side, taking its role. Do not preserve/split prior roles or form an overlapping
ordinary pile. Single projection drags work in either role direction. D-143 inserts
at between-card positions inside a slot or appends on unused-space drops, keeping
other cards' relative order. Mixed-view ordering, other-view applicability,
mixed/empty-source cases, and missing-source Stack
presentation/actions remain separate. See the [session handoff](../../agent/session-handoff.md)
for the continuation point.

D-144 requires a **Remove from combat** context action for an individual combat
projection or selected combat projections. This removes occurrences/incident
links, not real cards or Stack effects; apply fully-empty-pair cleanup without
closing the area. D-176 issues individual occurrence-removal mutations, with
per-node Undo and retained slot/role/order/link context. Non-token source cards have no Remove action (D-174); token removal
is distinct from occurrence removal. Exact menu layout and access
through real sources or mixed real/projection selections remain open.

D-145 requires declaration shortcuts to give each new attacker its own pair by
default, retaining existing attackers' slots/grouping/order. This does not alter
same-slot collection drags. D-146 auto-creates an absent combat area for single-
card declaration, without a separate creation action, implicit tapping, or source
movement. Reuse existing areas without clearing/replacing them; separate manual
creation remains available. D-147 shows both multiselect declaration quick actions
only during Declare attackers, preserving full context-action access at other
steps. No implicit execution or legality/turn-player access restriction follows.
Order among multiple new pairs, absent-area drag targets, and additional single-card quick
offerings remain open.
D-176 supersedes D-148/D-149/D-150's combined-node mappings: area creation,
each occurrence change, each source tap/untap and tracking advancement are
individual mutations, immediately recorded/published. Draw + advance likewise
records actual card movement followed by advancement; empty draws fabricate no
mutation. Each node has its own Undo/navigation boundary, regardless of group
animation. No extra user tap/Send or runtime priority enforcement; see history
sections 3.11–3.12.
D-152 makes declaration shortcuts convert existing blockers by moving their same
occurrences into individual new attacker pairs, preserving identities/links and
normal variant tapping. Existing attackers and other origin cards retain grouping;
fully empty origins disappear normally. Keep D-176's individual mutation nodes;
order among multiple new pairs and exact concurrency remain open.
D-153 fixes new declaration pairs after surviving existing pairs, preserving
existing pair contents/order. D-154 preserves the pre-drag order of cards moved
together from one slot, including subsets/same-slot reorder; selection-tap order
does not reorder them. Insert/append the sequence under D-143 without copies or
source edits. Multiple-new-pair and mixed-view ordering remain open.
D-155 fixes two horizontal combat rows: blockers above, attackers below, with
each pair's blocker slot above its attacker slot. Keep side-by-side slot contents
and existing grouping/cleanup. D-156 separately fixes cross-slot combat drag order:
selected blockers left-to-right, then selected attackers left-to-right, preserving
each slot's order. Insert/append that sequence together; every moved card takes
the destination role. Exact geometry remains stage 3.
D-157 uses pre-drag visual order for real battlefield cards dragged together into
combat: top-to-bottom, then left-to-right, not selection-tap order. Create/reuse
occurrences and insert/append that sequence in the chosen slot without changing
originals. Mixed-view, multiple-new-pair, and irregular/overlapping battlefield
layout ordering remain open.
D-158 anchors these controls in the spatial model: the separate combat area sits
between the player's and opponent's play areas. Battlefield drags create/reuse
projections where dropped, leaving originals in place; declaration quick actions
add new attackers to the right of existing combat cards/pairs. Collection-order
defaults do not override the drop target. No special projections-first rule for
mixed drags is approved or required as a separate owner decision.

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
- **Move and ping:** move a card to Stack, then ping a participant/card associated
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
gestures, generic ping context controls/association, live presence privacy/expiry,
ping schemas/lifecycle, full wireframes, and interaction budgets. The conceptual
layout and examples above do not close those readiness gaps by themselves.
