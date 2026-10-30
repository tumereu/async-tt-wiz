# 10. Game board and controls

## Round 16 card/hand menus (rules version 19)

Card menus omit Move: non-Everyday moves join More, overflowing into More 2,
More 3, etc. The header shows only the current title, previous/next arrows
(wrapping), and a scrollable jump list on title tap; swipes and per-game memory remain.
On tight screens only the grid scrolls, keeping the header fixed and the card unobscured.
Paging the same card reserves the tallest page's rows, leaving unused rows empty;
neither the header nor the grid's top edge moves. Discard (including random) uses
the Graveyard icon; Play has a distinct card-onto-table icon.
Hand cards (in either player's Hand) instead have Hand (Inspect, Play, Reveal,
Discard, Exile, Library top/bottom, Shuffle into library) and Random (discard/
reveal/exile/library bottom) pages. Play uses the hand double-tap behavior,
atomically for multiselection.
Reveal opens the shared Reveal tray for the pressed card or selected hand cards.
Cards remain references in their source Hand: moving even back to Hand expires
their tray visibility; an empty tray closes automatically. Done (either player)
closes the tray in one event, returning all remaining references without reordering Hand.
Random actions sample one selected hand card, or the whole Hand if selection is
empty, using fresh independent 32-byte entropy and the `hand-pick-v1` domain
(`async-tt-wiz/hand-pick/v1\0`), never shuffle/reseed entropy. The event records the
seed and chosen identity; only destination/tray visibility discloses its face.

## Round 16 temporary marks (rules version 17)

Cards have independent `damage` (numeric text; empty means zero), `eotCorner`
(two-part signed P/T delta; empty means none), and `eotNote` fields. Manual edits
are available on Battlefield and real Stack cards. Everyday retains its actions
in a five-column, at-most-five-row grid and adds two-cell Damage/EOT P/T widgets
and a one-cell EOT note editor; edits apply to multiselection as one atomic batch.
The effective P/T is permanent corner plus the temporary delta, shown in teal
with an hourglass; inspect also shows the delta and “until end of turn”. EOT notes
are separate hourglass-prefixed chips. Damage is a red outlined badge beside P/T,
filled when numeric damage reaches numeric effective toughness (a hint only).
Passing to a new turn clears all three fields on every card within that same
event. Zone changes clear them too; Battlefield-to-Battlefield moves retain them.
Direct phase/step choices retain their existing same-turn semantics.

**Keyword toggles (round 19):** More's fourth row has Keywords (card note) and
EOT keywords (EOT note). Each opens a floating 4×4 toggle grid: Flying, First strike,
Double strike, Deathtouch, Lifelink, Trample, Haste, Vigilance, Reach, Menace,
Hexproof, Indestructible, Defender, Ward, Can't block, Goaded. A toggle sends the
note at once and the popup stays open. Notes are keywords joined by `, ` in that
order; reading is case-insensitive, comma-separated and trimmed. Any other token
disables that button. Multiselection: a toggle is on only if every selected card
has it; toggling edits all of them as one batch. Gates follow note/EOT note editing.

**Initial-delivery connectivity (D-182):** game edits and shared controls require
a connection. Offline use is downloaded read-only history, not offline gameplay,
Undo, Reset, new random outcomes or queued synchronization. Optimistic updates
and animations still apply online. D-215 shows a Reconnecting... dialog blocking
gameplay when disconnected. D-216 automatically recovers already-sent actions
after reconnect without manual resend or duplicate execution; exact recovery
mechanics remain open.

D-295 selects a single **Back to games list** button on that blocking dialog,
not Review downloaded data. Leaving it does not cancel uncertain requests or
change retained read-only access elsewhere; automatic recovery continues normally.

Status: gesture choices, snapping, diagonal piles, selection-aware dragging,
top-insertion drops on Library, unordered Graveyard/Exile moves, visual stacking
drops, and top/bottom context moves for Library are confirmed (D-226). Layout,
detailed hit testing and communication mechanisms below are proposals for review.
D-267 corrects the normal card mapping: **single tap opens Actions; double tap
taps/untaps outside Hand**. In Hand, double tap sends lands to the land row,
permanent-only cards to Stack then auto-resolves to Battlefield as a separate
history group, and other cards to Stack. This supersedes the opposite D-15 mapping, not the existing
selection scope, inspection, dragging, pile-control or Ping semantics. Removing
dedicated double-tap recognition in Select/Ping modes is confirmed by D-281.
The owner additionally
requires mostly text-free understandable play, optional text/messages, and
action-scoped pings. The engine is confirmed generic/rules-agnostic; the literal
move-and-ping presentation for the Stack area is defined in
[Visual action language](visual-action-language.md).
D-112–D-114 additionally require combat/Stack projections and contextual actions;
[their primary contract](combat-stack-and-projections.md) is being specified
case by case. D-115 keeps the general delivery gates; D-290/D-291 separately
authorize the limited UI pilot/gameplay design artifacts below, not application
implementation or a passed general UI/review gate.

This document is the primary contract for tabletop interaction. It supplements
the [Android experience](android-experience.md) and
[action catalog](game-actions.md); it does not implement a rules engine.

## 10.1 Confirmed interaction vocabulary

| ID | Interaction | Meaning |
| --- | --- | --- |
| B-001 | Drag a card | Move it on the table or into another area/zone. |
| B-002 | Drop | Snap to a grid location or designated slot. |
| B-003 | Pile cards | Allow overlapping cards, with underlying cards peeking out diagonally. |
| B-004 | Double tap a card normally | Outside selection-edit/Ping modes and Hand, tap/untap the selected set if the touched card is selected; otherwise clear selection and tap/untap only that card. Tapped presentation is 90 degrees clockwise, or back to untapped. In Hand: lands → land row; permanent-only cards → Stack then auto-resolve to Battlefield in a separate history group; other cards → Stack. No Actions menu. |
| B-005 | Single tap a card normally | Open Actions. A selected card binds the selected set; an unselected card binds only itself while preserving selection. Opening the menu creates no orientation/game edit (D-248/D-267). |
| B-006 | Hold a card | Show large artwork and readable card text. |
| B-007 | Special piles | Library, graveyard, and exile have dedicated pile representations. |
| B-008 | Drag scope | Drag one card unless multiple cards have been selected; an ordinary pile is not implicitly selected. |
| B-009 | Drop on a special pile | Insert at the top of Library; add to unordered Graveyard/Exile without an insertion-position choice (D-226). |
| B-010 | Drop on another ordinary card | Put the moved card onto that card's visual combined stack; no attachment or mechanical merge. |
| B-011 | Context Move | The single-tap Actions sheet offers Library top/bottom, Move to Graveyard, and Move to Exile. No order-related actions for Graveyard/Exile (D-226/D-267). |
| B-012 | Tap selected / Untap selected | In selection-edit mode, explicit commands set the selected set uniformly while single card taps edit membership. In normal mode, use the selection-aware double-tap orientation gesture (D-267). |

Gestures operate on either participant's cards. Ownership does not restrict
editing. Every completed state-changing interaction queues immediate sync and
has the specified eligible Undo/recovery path, not unconditional reversibility.
Menus, zoom, selection previews, and cancelled gestures do not create
game-state actions.

D-253 corrects D-121: every **individual constituent action** remains reachable
through a full action path outside its quick window. Combined shortcuts can be
quick-only; advance-and-close is End-of-combat-only. Explicitly confirmed Library
context mulligans remain available. Structural/applicability validation still
applies. Exact menus are stage 3; this does not require every button everywhere.

D-123: card actions through projections apply to the real source, including
moving that card by dragging a projection into a real destination such as
Graveyard. Use normal card-move/reset/token-removal behavior. Virtual combat slot
placement and occurrence cleanup are distinct from changing the real card;
operation disambiguation still needs case-by-case design. D-124 removes combat
projections when their source leaves the battlefield, regardless of entry point;
D-125 retains Stack effects after independent source moves or disappearance.
A normal Stack drag is an exception: only the top entry is draggable, and its
zone drop removes that projection as well as moving the real source if needed.
D-242 keeps Stack sources linked across locations, including Graveyard/Exile
sources and creation there. Only token disappearance freezes last-live source
information for read-only inspection. Do not offer source-card edits on the frozen
copy or recreate the token; occurrence layout/removal remains available.

## 10.2 Proposed board areas

**Current layout (playtests r2–r11):** the battlefields and center combat band are
one zoom/pan surface. Hands, piles, life and counters are fixed-size HUD, with
piles below your hand and above the opponent's. Portrait Stack is fixed-size at
the right edge, vertically centered; landscape keeps its sidebar. Battlefield
backgrounds extend indefinitely sideways. Camera bounds allow three card slots
beyond the outermost cards; overscroll rubber-bands and springs back. The
battlefield feels infinite: panning is limited only by distance from placed cards,
no field edge is visible inside the pan area, and drags may place cards anywhere in
it, including negative columns (automatic placement still starts at column 0). Clamp each
axis independently: wide content cannot expose space beyond its ends, and narrow
content cannot be pushed partly off-screen. Empty-space double tap fits the table;
double tap then drag pans one-handed; plain empty drag rectangle-selects, showing
would-be selections during the drag. Pans stay in their starting region (panel,
field, hand or Stack). Battlefield cards are 28×40dp at base scale with 42dp column
pitch. Peeks go top-left; adjacent cards/piles never overlap at any tapped angle,
including mid-animation. Selection borders rotate with the card throughout.

Real cards are rectangular, tokens have owner-colored chamfered corners with the
same footprint, and projections have dashed borders. Standard cards use Scryfall
`art_crop` inside app-rendered name/P/T; custom-image cards show the whole image
without an app name and with corner P/T on a background. Inspect uses full images.
Opponent hand background matches your own, not the opponent's battlefield tint.
Only the turn player's field is tinted; the other is neutral grey. Both top and
bottom table bars show whose MOVE it is (“Your move” / “Waiting · X”), not whose
turn it is. Timeline mode bars are gray. There is no green your-move screen frame.

The older layout paragraphs below, up to “Uniform card scale”, are superseded by
this layout. They are historical mockup direction, not requirements.

**Current gameplay review direction — D-291:** both portrait and landscape are
required. The [gameplay layout brief](../02-experience/gameplay-screen-proposal.md)
and [review packet](../02-experience/ui/review.md) (G01)
replace the older portrait-only sketch below as the current trial composition,
not approved final geometry. D-295 updates the colors: own green, opponent red,
each with an outer darker one-row band and an inner lighter two-row band. Bands suggest lands versus
other permanents but do not restrict types or create new semantic zones. Opponent
geometry reflects top/bottom only, retaining horizontal positions and viewer-facing
card text. Library/Graveyard/Exile controls for both players stay on the left in
landscape and between participants in portrait. Counts remain visible.

The owner selects a floating own Hand below the land band and above screen-fixed
bottom controls in portrait, independent of board panning, and central combat.
D-297 puts the landscape own Hand in the bottom quick-actions row instead.
D-296 keeps
Stack central in portrait but between player info in the landscape left sidebar.
The opponent's covered Hand in the existing top control row in landscape, a
separate top row in portrait, and life grouped with special zones remain draft
placement proposals. Both orientations must retain usable controls; dense boards
may need navigation rather than a promise that all cards fit at readable size.
Horizontal camera bounds stop a still-unspecified card-slot margin beyond the
outermost cards, not a finite placement boundary. Empty-board/cross-band bounds
and exact zoom remain geometry completion.

**D-295 presentation refinements:** no visible battlefield card slots, row
separators or area labels. Logical snapping/placement is unchanged. Allocate the
same horizontal footprint to each card/ordinary pile, containing the complete
upright, tapped or mixed-orientation diagonal-peek envelope. Rotation, pile
configuration or ownership must not change adjacent horizontal spacing. Combat
alone uses faint vertical paired-slot dividers. Portrait Stack sits right of
central libraries; combat-between-libraries with Stack beside it is a tentative
trial, not final geometry. Reveals sit between the relevant Library and field.
Life displays as a player-colored square with only its total, no printed names or
“Life” text; accessible descriptions retain identity/meaning. Card P/T is at its
bottom-right. Owner-colored borders are distinct from field color and projection
dash/selection/token/ping signals. See G01–G06 and H01–H03 in the review packet.

### Uniform card scale and pile totals — D-296

**Scale portion superseded by the current layout above:** only the world surface
zooms; HUD representations keep their fixed size even when on-screen card sizes
differ. The total-count badge/accessibility requirements below remain current.

All table card representations use one card size at the same zoom: battlefield,
combat/Stack projections and real cards, Hand/covered Hand, reveals, Library,
Graveyard and Exile. Do not fit an area by shrinking only its cards or derive card
size from its row height. Rotation/piles keep the equal horizontal envelope above.
This equal allocation governs battlefield cards/piles; D-297 compacts Hand gaps
and combat-side margins without changing the cards' actual dimensions.
Pan/scroll/reflow when space runs out; deliberately magnified inspection remains
separate. Dock/Hand anchors can remain screen-fixed while their card drawings use
the common scale. Camera movement never changes shared placement or pile order.

Landscape Stack sits between the two player-info blocks in the left sidebar,
superseding the central landscape Stack. Portrait right-of-libraries Stack remains.
Use a top-left **circular total-count badge** consistently on Library, Graveyard,
Exile and Stack when entries are not all displayed. It counts all entries, not
only hidden ones. Empty/singleton states remain identifiable/accessibly counted;
no face disclosure, guessed identity, item loss or new membership limit follows.
The badge and pile are one contents affordance, not precision badge-only targets.

Across already-in-scope contents/token searches, the field, results and loading/
empty feedback belong inside the same card/panel (D-296), not a separate
results card. Reserve loading space or use a full-area placeholder; real errors
use the floating transient error, never layout-pushing inline text. Search remains local browsing where already permitted and does not
silently issue a new logged disclosure or add a new search capability.
D-297 makes the results heading a small subordinate label, not a new title.

### Compact hands and direct card actions — D-297

**Card-menu bullets below are superseded by the current card menu in the next
section.** Compact, pannable hands and captured target IDs remain current.

- Reduce Hand inter-card gaps. Both hands pan horizontally in either direction
  when content exceeds available width, without shrinking their card scale,
  clipping unreachable cards or moving/reordering them. Keep leftmost and rightmost
  entries reachable. Landscape own Hand shares the fixed bottom row with quick
  actions; Undo/mode and quick controls remain usable. Trial covered opponent Hand
  in the existing top control row so neither hand takes battlefield height/area.
  Portrait placement persists. Native accessible navigation must not require a
  precision swipe; pan-versus-card-drag arbitration retains captured identities.
- Card Actions exposes applicable actions immediately as square **icon-only**
  buttons grouped in rows, without category submenus. Corner text exposes direct
  +/−/Set, including left/right/both slash-pair adjustments; every counter color
  has direct +/−/Set, including absent counters. Owner choices appear inline, not
  behind Change owner → chooser. Current text/values and captured scope/count stay
  visible; all buttons have unambiguous accessible action/target names. Nonnumeric
  arithmetic stays disabled, not replaced by an initialization route. Existing
  normal-mode orientation mapping and per-source/occurrence applicability remain.
- A required Set text editor, inspector or Link endpoint picker completes the
  chosen action; it is not permission to add intermediate action-category menus.
  Scrolling the direct controls is permitted when they cannot all fit, especially
  in landscape/large text; do not shrink ordinary buttons to avoid scrolling.
- The current review trials a dialog (owner explicitly accepts this alternative
  to the initial floating-buttons request). Show the context card in a title
  corner, with its **current permitted visual state**, including orientation,
  face/cover, owner, corner text and counters. It stays visible while actions
  scroll and updates with prediction/reconciliation/accepted remote changes.
  This is a magnified state preview, not a smaller table scale, additional
  authoritative card or automatic hidden-side inspection. Conceal names/art/text
  whenever the applicable viewed region/disclosure rules require it.
- Preserve D-248's captured target IDs: previewing the context card does not
  retarget a selection after sync/selection changes. Outside tap or
  native Back dismiss without a game edit or cancelling an already-sent edit.
  Completed corner/counter +/- and Set retain the dialog for repeated edits;
  successful context Move to Graveyard still dismisses. Other existing per-action
  lifetimes are not replaced with a universal close/stay rule. D-298 supersedes
  the title/close-X band with a compact top-left preview and packed grid; outside
  tap/native Back remain. G04/G06 show before
  and retained updated states, not a working editor or authorization to implement.

### Compact action layout and source relationships — D-298

**Current card menu and controls (r3–r10):** a stationary 4×4 grid of 48dp cells
(icon plus one short label) occupies one reserved square of about 200dp. Pages
never move the square; edge tabs and swipes switch Everyday / Counters / More /
Move pages. Anchor on the side with most room, usually above own cards and below
opponent cards. Everyday includes P/T, Library top/bottom, Graveyard and Exile;
other zone moves use Move. No “Move to pile (col,row)” entries; drag onto a card
still piles. P/T and each counter use a 2×1 widget: minus left, value center
(tap edit / drag adjust), plus right. Counters has P/T and all six colors;
More retains split +/• controls. Hold a cell for its full action-name tooltip.
Outside dismissal/pass-through follows section 10.3.

Mode is a floating 48dp square at the board's bottom-right; tap cycles
Normal → Select → Ping without a chooser. Next step occupies the bottom-right
corner, with bordered, non-filled contextual squares right-aligned beside it.
Hold Next step ends the turn. Table actions opens its dialog directly with its
own icon: no intermediate controls menu, Redo, Return to current, View·N or
retained-display reopening. There are no table/timeline overflow, three-dot or
“History options” menus, Event details dialog, event-comments UI or manual
Download history action. Actions have direct buttons or live in Table actions;
sync/recovery is automatic. Table toggles are normal buttons whose color/icon
shows state, never label-plus-switch rows.

The older quick-allocation and menu-geometry bullets below (through the counter
triplets) are superseded by this contract. Relationship emphasis remains current.

- Center fitting Hand cards in their allocated display viewport. If they overflow,
  preserve compact spacing and left/right reach to both ends; centering must not
  create negative/unreachable overflow. This is personal layout, not reordering.
- Trial **four fixed portrait quick positions**: three contextual positions and
  one reserved progression position. Unused positions stay allocated without
  drawn fake buttons; extra contextual offerings pan in the three-position rail.
  A fixed allocation is not a gameplay/action-count cap. The complete situation,
  icon, scope and full-path inventory is in the UI review's quick-action catalog;
  G02 alone is sufficient to illustrate positioning. Do not invent shortcuts for
  unlisted phases or auto-target the top Stack entry without a captured target.
- **Tap the progression square for Next step; hold for End turn.** Recognition
  consumes the tap: never dispatch Next step on press-down or after the hold.
  Cancel before recognition sends nothing; one completed gesture sends once.
  Explicit End turn remains in the phase picker/Table actions and native
  accessibility, including the opponent's turn. This supersedes D-107's two
  separate one-tap controls; it adds no priority/pass state. The four-slot example
  therefore has three contextual positions, not the initially proposed two.
- Card Actions has **no separate title or close X**. Pack square icon buttons
  around the top-left current-state card details (trial two vertically stacked
  grid positions). Include captured scope/count with the card details, not an
  oversized title band. Outside tap/native Back dismiss as before; repeated edits
  retain the menu, without a new universal per-action lifetime rule. A long menu
  may scroll rather than shrink ordinary buttons or drop existing action paths.
- Slash-pair corner text uses seven squares on one row in this order: current
  value/Set, +/+, −/−, •/+, •/−, +/•, −/•. A centered dot denotes the unchanged
  side, not zero or a decimal. Each is the existing relative edit to the same
  one string. Single-integer text uses Set/+1/−1; other text uses Set with arithmetic
  unavailable. Do not parse expressions or invent P/T fields. The value square
  never widens: shrink/wrap long values within it; full text remains accessible
  and in the Set editor, with bounded truncation if it cannot remain legible.
  At widths where seven ordinary squares cannot fit, pan the single row rather
  than making overlapping/undersized targets or silently dropping controls. The
  412 px review demonstrates the fitting trial, not every device/large-text state.
- Card counters occupy three rows, two triplets per row, with a vertical divider
  between triplets. Each triplet is current-value circle/Set, +, −; all six colors
  remain reachable even when absent. Player-value menus use the same paired
  triplets and life value/Set,+,−, without a popup title or close button. Landscape
  card Actions puts general actions left and corner/counter controls right.
- **Relationship emphasis:** focusing/highlighting a visible card or projection,
  including a selected view,
  highlights the real source and all its other visible projections at the same
  viewed boundary. Match stable source identity, not name, owner, clone or field
  position. Preserve selection/command target IDs: this emphasis does not select
  every occurrence, merge independent Stack effects, or alter removal scope.
  Expose equivalent relationship descriptions to accessibility. Offscreen views
  may be navigated normally; do not leak concealed faces/names or fabricate a live
  source for a frozen effect whose token disappeared. No request is sent merely
  for highlighting. Stack and combat projections always render upright, regardless
  of source orientation. Combat tap opens Undeclare / Graveyard / Ping bubbles;
  double tap does nothing and hold inspects. Undeclare never untaps the source.

### Player values: row, life menu and drag preview — D-296

- Keep each player's six counters always visible. In portrait use a **3×2 grid
  on the player row next to packed Library/Graveyard/Exile**, never a conditional
  row beneath the piles or a full-strip row. Empty and numeric-zero counters show
  as faint outlines, with accessible color/participant names and usable controls.
  Numeric zero (`0`, `+0`, `-0`, including leading zeros) is the same as empty
  everywhere, including card counters and Set controls; do not draw a `0` badge.
  Smaller counter drawings must not create overlapping interaction targets.
- Tap the colored life square for the existing participant context menu, now
  explicitly containing life and all color -1/+1/Set controls. Either participant
  may edit either player. Empty/zero colors remain accessible
  here without a new Add/Initialize step. Completed +/- and Set retain the menu
  as before; field trimming/empty removal and nonnumeric applicability persist.
- Life/counter drags use the dominant axis, one step per 10dp, without acceleration:
  right increases, left decreases, toward the other player decreases, toward your
  own edge increases. Card counters use the controller's side. Bind the field at
  gesture start and show an anchored bubble such as **+3 → 23**, above the counter
  (below near the top edge). Preview is local; release sends one relative edit.
- On a valid release, issue **one relative delta-N request** for that field if N
  is nonzero. Apply normal provisional prediction immediately then; no extra
  confirmation and no N separate +1/-1 requests. Server execution applies that
  delta to its actual current numeric value, so compatible concurrent increments
  both count. Record actual accepted before/after for normal history/grouping/
  eligible Undo. Do not send an absolute press-time total or add a previous-value
  lock. Existing structural/numeric/resource checks still apply.
- A recognized drag consumes tap, so release does not also open the menu. Cancel,
  zero-delta release, lost target, or disconnection before release sends no new
  edit. Sync continues during preview; it cannot retarget the captured field.
  Same-process uncertainty after an actual release uses original-request recovery,
  not a new drag invocation. No requests on movement/animation frames or offline
  action creation. Counter arithmetic remains unavailable for nonnumeric text;
  absent-counter arithmetic uses D-68's zero starting point without an intermediate
  stored zero. Existing explicit menu/accessibility equivalents remain usable.
  A value updating during sync is not loss of its semantic target: retain
  the captured participant/color, never the control now beneath the finger. Normal
  release validation still decides an absent/nonnumeric/invalid target outcome.
- Historical review shows read-only values, not actionable drag/menu edits. Enter
  explicit play/continuation first. Preview/selection is personal state, distinct
  from the shared request/prediction created on release.

### Card/token owner metadata — D-295

- Each real card/token has an explicit **owner participant**. Default to the
  participant bringing that card into the game (their selected deck copies), or
  creating a new instance/token/Clone. Creating a projection does not create a
  new owner: live occurrences display their real source's owner. A frozen token
  effect retains the source's last-live owner with its other frozen information.
- **Change owner** exists only for tokens and selects one of the two participants.
  Either participant may perform it; existing captured-selection scope applies.
  Opening/closing the chooser is personal UI; choosing a changed value is an
  ordinary connected server-authoritative card-field mutation with immediate
  prediction. A same-value edit is a no-op. Multi-card scope issues individual
  edits under D-176, not a new atomic multi-card command.
- Ownership is neither controller nor permission nor a rules assertion. Moving
  between fields, zones, piles or combat/Stack does not infer an owner change.
  Resetting other card fields does not silently change owner; only explicit owner
  editing does. Keep owner through retained state/history/recovery and ordinary
  eligible selective Undo/Redo; token disappearance still removes the real token.
- Hand, Library, Graveyard and Exile are owner-only destinations; either battlefield
  is allowed. An opponent-zone drop redirects to the owner's zone, including its
  preview. Menus omit other-player destinations and player names. Both players can
  still manipulate either side; a card's controller is its battlefield side, not
  its owner. Moving a token to Graveyard deletes it regardless of owner and does
  not concern its owner field for Undo/Redo.
- Relevant `(from,to)` for an owner edit is that source's owner value. Different
  card fields remain independent unless their own contract reads owner. Accepted
  moves retain their original destination, not a destination recomputed from owner.
  Concurrent edits use ordinary server ordering/current-state validation, not a
  new previous-owner lock. Borders update on all live views without changing
  location, orientation, disclosure, counters or links. Historical rendering uses
  the owner at that boundary, never today's owner over every past state.
- Color is not the sole accessible owner signal: inspectors/actions/semantic
  descriptions identify owner, including on peeks and projections, without
  restoring persistent printed player-name clutter in the gameplay canvas.

Earlier conceptual sketch retained for context, not a competing current layout:

This is an MTG-oriented sketch using the confirmed areas and life/counter tools,
not an R1 area/named-value authoring editor. The engine does not interpret labels.

```text
+----------------------------------------------------+
| Branch / sync / review context                      |
| Opponent: life, counters, turn/phase                 |
| Opponent hand (covered by default)                  |
| [Library] [Graveyard] [Exile]                        |
| Opponent battlefield area                           |
|                                                    |
| Shared Stack / action-linked pings                   |
|                                                    |
| Your battlefield area                               |
| [Library] [Graveyard] [Exile]                        |
| Your hand                                           |
| You: life, counters                                 |
| Undo | Previous / Next action | Activity / History   |
+----------------------------------------------------+
```

- The two participant play areas are generic board regions. Their labels do not
  install a battlefield model or infer ownership/control. Movement changes only
  the explicitly selected container/placement fields.
- Each participant has labeled library, graveyard, and exile slots. Their card
  counts remain visible even when empty or covered.
- Every Stack entry is a projection; sources may be in Battlefield, Hand,
  Graveyard or Exile, never Library. A Hand source is public while projected.
- A temporary combat area supports projected attackers/blockers in visual paired
  groups, without individual attacker↔blocker assignments (D-112/D-139);
  projection creation preserves source positions,
  while later card edits/moves through them affect the originals (D-123).
  Its layout and lifecycle are not settled by this older conceptual sketch.
- Hands use an expandable fan/row rather than consuming the whole battlefield.
- Extra labeled areas such as Command or Outside game are excluded from R1
  (D-241), not a proposed required dock. **Sideboard is lobby-only (D-202)**, not an in-game
  extra area: no dock, contents browser, or view/edit/move path exposes it during
  play. Initial lobby placement is mainboard in Library, sideboard in its separate
  Sideboard area, and all other card areas empty; see the
  [setup contract](accounts-decks-and-lobby.md#initial-placement-and-lobby-only-sideboard--d-202).
  D-203 permits free mainboard/sideboard transfers in that lobby without legality,
  size or one-for-one restrictions; own Ready clears and saved decks stay unchanged.
- Player controls and review/undo stay reachable while the table pans/zooms.
- D-291's first draft uses bottom-left tap Undo / hold Redo; a recognized hold
  emits only Redo, never an earlier Undo or a release-time Undo. Redo is absent
  from touch menus; accessible Redo remains available.
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
| Single tap | Normal mode: open Actions with D-248 binding, no orientation edit. Select mode: edit membership. Ping mode: perform its explicit ping action instead of normal Actions (D-267). |
| Double tap | Normal mode outside Hand: one selection-aware tap/untap operation without Actions. Hand uses the land/Stack shortcut in B-004. Select/Ping have no double-tap recognizer: two independent mode taps (D-281). |
| Hold | Open a large-art/text inspector without tapping the card, moving it, or changing shared reveal state. |
| Drag | Begin after movement exceeds touch slop; show a lifted preview and destination highlight. Commit only on a valid drop. |
| Cancel/interrupted touch | Restore the pre-gesture state; no shared move or tap. |

Confirmed orientation scope (D-35/D-36, gesture corrected by D-267): in normal mode,
double tapping a selected card outside Hand retains the selection and taps/untaps every selected
card. Double tapping an unselected card clears the selection and taps/untaps only that
card, without adding it to the former set. With no selection, affect only the
addressed card. Pile membership alone does not expand the affected set.
Tapped presentation is a 90-degree clockwise rotation from untapped, and
untapping returns to the untapped presentation rather than rotating another
quarter-turn. Animate either direction about the card center in 90 ms, without a
morph; the selection border rotates with it throughout. Other card travel uses
minimum velocity initially ~2.5dp/ms, ease-out and a 180ms scene-duration cap;
short moves arrive early and wait, with debug speed tuning in Settings.
Confirmed mixed-orientation behavior (D-36): follow the touched
card's pre-operation presentation. If it is untapped, set all selected cards tapped;
if it is tapped, set all selected cards untapped. Do not invert each member
independently. Preserve the selected set and unrelated fields.

In selection-edit mode, card taps still add/remove selection membership instead
of rotating cards. The previously approved explicit Tap selected / Untap selected
commands remain for that mode and set the selected set uniformly. The proposed
availability of those commands outside the mode was rejected: there, card double taps
provide selection-aware tapping. Exact command placement remains open; D-176
records individual card-orientation mutations. No tap/untap infers activation, mana, combat, or any
other game meaning.

Single-tap context menus open immediately, including Library. Double tap uses a
400 ms window and 32dp distance; both taps visibly register a press border. A
recognized double tap performs the card's zone-specific action even if the first tap opened a menu.
Double-tapping your Library draws one. Inspect hold is 350 ms. Menus must never
block table double taps or drags.

All tap menus use one reusable floating popup for anchoring, animation, cells,
dismissal and input arbitration: opaque square bordered buttons/chips with a light
shadow, no sheet background, title or Close button. Outside tap dismisses and
passes through; only buttons/chips count as inside, not empty cells or gaps.
Back dismisses. The phase/step picker is exempt and remains a dialog.

**Confirmed mode behavior — D-281:** Select/Ping modes have no dedicated double-tap
recognizer; each single tap performs its mode action immediately. Two quick taps
on the same target would therefore perform two independent membership/ping toggles,
not open Actions, rotate a card or coalesce into one toggle. This confirms the
former D-267 proposal, replacing D-34's double-tap Actions shortcut in selection
mode. Hold inspection, dragging and explicit
accessible Actions remain available under their existing rules; exact UI placement
is not selected. Do not silently apply normal-mode double-tap orientation in a
mode that consumes those taps. T-620 is now an unconditional acceptance scenario.

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
  rectangle. Outside this and Ping mode, a single tap opens Actions and a double
  tap taps/untaps; selection changes scope, not this gesture distinction (D-267).
- Dragging a selected card moves the selected set. Show selected cards, their
  count, and whether selection-edit mode is active clearly.
- Confirmed mode control (D-31): a visible Select toggle enters/exits
  selection-edit mode. Turning it off preserves the selected set and restores
  normal single-tap Actions/double-tap tap/untap. Tapping empty table space clears selection
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
2. Hold retains Inspect without selection/orientation changes. D-34's old
   double-tap Actions shortcut in selection mode is replaced by D-281's
   independent immediate single-tap mode actions as detailed above.
   Explicit accessible Actions still uses the same captured-target binding.
3. A successful selected-set move leaves selection-edit mode in its current
   on/off state, in addition to preserving the selected set under D-29.
4. Cancelling a drag or dropping at an invalid destination restores both card
   placement and the pre-drag selection, including a selection cleared when an
   unselected card was picked up. No shared board edit is committed.

**Context binding — D-248:** opening Actions on a selected card binds the menu
to that selection; opening it on an unselected card binds only that card without
clearing/adding to the selection. Capture the context card and target IDs when
opening the menu, including an inline Set editor; later selection changes cannot
redirect it. Show that captured scope/count. Accessible Actions uses the same
binding. Projection/source normalization follows the operation's identity domain
in [bulk targeting](game-actions.md#target-identity-normalization).
This captures targets, not a group: UI actions send requests; server-created
events are grouped deterministically (D-254). A Ping menu does not reserve or
bind its predicted recording group. Explicit historical references do not assign
the new event's group either.

Recheck current applicability/existence when each operation is processed. Skip
individually disabled targets only under D-67; a vanished bound target does not
become whichever card is now selected. Close/disable an unusable editor/menu
rather than substitute a target; exact feedback is later UI work. Context Ping/
Unping still chooses its uniform action from the captured trigger card (D-80).

Item 5 (explicit selected-set tap commands available with the mode off) was
rejected and replaced by D-35's selection-aware orientation gesture above, now a
normal-mode double tap under D-267.

For a covered individual card, use the side-sensitive inspection rules below.
A pile's back/header is a control, not an individual card to rotate; holding it
does not expose Library order or secretly invoke View/Reveal.

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
   sideways, which uses selection-aware normal-mode double taps (D-267). Switching
  printed face does not implicitly turn a face-down card face up.
- Face/cover edits preserve orientation, counters, notes, identity, placement,
  selection, and links; only explicitly requested face/cover fields change.
  Never infer a zone move or reset other fields. Exact picker layout, bulk face
  switching across different definitions remain open. D-176 fixes individual
  target mutations for bulk edits.

### Side-sensitive inspection — D-252

Inspect hold is 350 ms. Hover inspection is compact and titleless, with no
enlarge-art affordance and a tight art/text gap; finger up/down scrolls its info.
Render mana/tap symbols natively in rules text. Full inspection keeps full card
images. Tooltips appear above their button (below near the top edge), never under
the finger. Every custom button app-wide has press feedback. Mutating holds give
a tiny haptic tick; inspection does not.

D-252 revises D-38/D-89's blanket covered-face hold behavior:

- **Own-area hold/hover inspection** shows the real face art/text despite covering,
  including the previously supported printed faces. It is local and creates **no
  game event**, including no exposure log or Undo/Redo/grouping change.
- **Opponent-area hold inspection** enlarges only permitted visible content; it
  never automatically exposes a covered card or hidden printed side. Showing a
  back does not replace it with a name/text leak through accessibility or menus.
- There are distinct context actions: **Inspect other side** shows the hidden
  side in the inspector with a logged viewing event; **Flip** changes shared
  face presentation through the existing explicit face/cover controls. Inspection
  does not flip/uncover, move, rotate or alter selection. Flip is not inspection;
  preserve the separate printed-face choice versus face-up/down fields above.
  Exact labels/layout remain UI work; both actions have accessible equivalents.
- “Own area” means the configured participant region containing the card in the
  viewed state, not original deck ownership or inferred control. Projection card
  inspection follows its source's applicable viewed region; a shared-area card
  without an own-region context receives no automatic hidden-face exception.
  Explicit inspection remains available. Frozen sources use their recorded context,
  not a fabricated live card or a replacement source.
- Own-area hold may read downloaded faces offline because it creates no event.
  Explicit logged Inspect other side/View/Reveal still needs a connection under
  D-246. Neither cached hidden data nor another participant's recorded View grants
  automatic opponent-area disclosure. Missing downloaded content shows a limitation.

This remains the ordinary large-art/text inspector, not a hover-hardware
requirement. D-286 uses ordinary request acceptance/reconciliation: temporary extra
information from a rejected/different prediction needs no separate exposure report
or reconstructed old context. D-278 permits unadmitted inspection-request loss on
restart/Logout; accepted server records remain durable;
see the [exposure lifecycle](history-and-undo.md#exposure-lifecycle-and-offline-inspection--d-246).

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
- The single-tap Actions sheet includes Move to Library top/bottom, Graveyard,
  and Exile, always the source card's owner destinations, without player names or
  opponent alternatives. Graveyard/Exile have no top/bottom
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

Confirmed orientation scope (D-35/D-267): pile membership alone does not make a
double tap affect other pile members. In normal mode, double tapping a selected
card affects the selected set and retains selection; double tapping an unselected card
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

This whole-selection rule is prevalidation, not an atomic reservation against
later concurrent requests. Once a valid drop emits individual moves, each has its
own server result; retain accepted moves and correct only failures. See
[bulk conflicts](game-actions.md#45-bulk-actions-and-selections), including Y/W/Z.

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

**Temp pile (r19).** A player's Temp pile appears after Exile only while it holds
cards (portrait dock; in landscape beside Exile in the sidebar counter row). The
counters give way (narrower, scrollable) so Library/Graveyard/Exile never move.
Card menu More page: "Temp pile" (single card or selection, one batch). Tapping the
pile: Shuffle, Look, All to library top, All to library bottom. Card panels
(Look/Reveal/Search/local Graveyard/Exile) have a header "Select all" toggle.

**Numbered Temp piles (rules v29).** Temp 1 is always shown (an empty pile is its
outline with its number; a filled one is a card back with a small number), then
Temp 2–4 while on, all after Exile in number order. Portrait: the counter grid
shrinks; with more than one pile and no room left (< 96dp) the counters move to a
row of six under the own pile row and above the opponent's pile row instead of
panning. Landscape: the piles sit right-aligned under Exile in the counter row;
with more than one pile and a counter value that would need panning, the counters
get their own full-width row outside the Temp row. Card menus offer Temp 1–4
(disabled only when no card can move there); the pile menu adds All to Temp N and
on/off toggles for piles 2–4 (off disabled while the pile has cards).

**Current panel/Library contract — playtests r8–r11.** The older D-41–D-51
inventory, trays, dismissal and category-column UI below are superseded in full
by this section's current contract. Unordered Graveyard/Exile membership and
ordinary movement/reset rules are unchanged.

- Library always shows a back, for either viewer, without a known-card marker.
  Tap opens immediately: Draw / Reveal / Look / Mill / Exile for 1, then the same
  five N… actions, Search / Shuffle, and Mulligan 7 / Mulligan −1. No bottom-N,
  Top/Bottom toggle, Move N row or Reveal all. Each N… dialog remembers that
  action's last local quantity and shows [−][+][field][action confirm]; field focus
  selects all. Oversized N clamps to available cards. Double tap own Library draws.
  Either participant can use all actions, including Mulligan, on either Library;
  actual card destinations obey owner-only zone routing.
- Look, Reveal and Search use ONE shared, server-authoritative left-edge panel
  model. Cards have per-card visibility: by default private faces show only to
  the initiator and backs/count to the other viewer, who can still act on them
  blind; Reveal starts all face up. At most ONE such panel is open in the whole
  game. While open, both libraries' Look/Reveal/Search controls, including N…
  confirm, are only greyed out—no error/snackbar. The server
  enforces the invariant as a safety net. Continuous top-card reveals are excluded.
- Header: icon, title, total count, ✕. Cards are Stack-card size in row-major order
  (left→right, top→bottom; position 1 is Library top). Use the fewest columns that
  fit all cards without scrolling, up to three in either orientation; then scroll.
  The panel spans the life rows; hands shrink sideways while any panel is open.
  Search shows faces only to the initiator by default, sorted by type then name,
  copies grouped BY NAME as ×N; dragging takes one. Closing Search shuffles and
  records one display group.
  Look/Reveal retain top-N order and allow in-panel insertion reordering; Search
  does not reorder. Drag outside moves the card; an insertion bar previews reorder.
- Tap a panel card for one fixed 3×3 floating menu to the panel's right, aligned
  to its top card row: Hand, Reveal+Hand, Battlefield, Battlefield tapped,
  Graveyard, Exile, Top, Reveal+Top, Bottom. Use icons and readable labels;
  already-revealed cards grey out the reveal variants rather than removing cells.
  Reveal+Hand/Top flips inside the same panel in one event/group, then moves in a
  SEPARATE event/group; ordinary playback pacing provides viewing time, no special
  dwell or second panel. Hold-inspect works on every face-up card live or in the
  timeline; holding pauses playback without entering timeline mode from live.
- Either player resolves Look/Reveal (see the
  [both-players rule](../01-product/overview.md#the-rules-a-player-should-be-able-to-learn-once)).
  Manual ✕ with cards remaining opens one floating rest row at ✕: Top, Bottom
  (both preserve panel order), Shuffle, Graveyard, Exile. No Bottom(random). The last card leaving Look/Reveal/Search
  closes the panel in the SAME event, without a rest chooser. No retained display
  can be reopened. Reveals do NOT persist: leaving the revealed panel for Hand or
  Library hides the card again. No retained eye/knowledge badge in hands or piles.
  Only the panel author sees the eye on currently public cards in Look/Search,
  never Reveal: centered on the art in a white disc.
- Tap either Graveyard/Exile for ONE local, opponent-invisible view per viewer,
  with no history or game-panel-limit contribution. Type headers and types are
  alphabetical, cards alphabetical within type, reusing Search's type/×N grouping;
  no name search, no reorder and no rest chooser. It uses the same 3×3 card menu,
  drag-out and inspect. Tap the pile or ✕ to close; empty pile auto-closes. When
  both panels are open, the shared panel stays left and the local view sits to its
  right, with at most two columns each (otherwise up to three).

### Superseded pile/browser specification (D-41–D-51)

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
representable; in normal mode its cards follow single-tap Actions/double-tap
tap/untap rather than an unrelated gesture mapping. An explicit selection control
serves multi-select, whose single taps edit membership.

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
- D-286 resolves the requested source/count at server execution. If an opponent's
  Draw happens first, the accepted View/Reveal may show different cards than the
  local prediction; reconcile to it, accepting any extra glimpse without a separate
  report. This does not refill a previously accepted tray or substitute Draw targets.
- Ordinary opponent-facing View summaries must not inadvertently show card names
  instead of the specified backs. This does not revoke cooperative full access:
   explicit Inspect other side remains available under D-252 and logs hidden
   viewing rather than exposing it automatically through an opponent-area hold.
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
   updates after moves. The [reference matrix](domain-model.md#28-reference-lifetime-and-reachability)
   distinguishes reachable moves, historical absence and invariant-excluded token
   tray removal; physical encoding remains technical work.
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

1. A tray remains associated with the original **server-accepted** card IDs and their
   recorded source order. It does not continuously track/refill from the library's current
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
| Live presence | Sharing drag/browser/cursor activity. | Excluded from R1 (D-241); no transport/UI/expiry deliverable. Local gesture previews are not shared presence. |
| Explicit View/Reveal | View library contents or View/Reveal top N. | Shared logged/synchronized operation, not merely presence; faces versus backs follow D-44, and top-N source order follows D-45. Exact record/display schema remains open. |
| Group/event-scoped ping | Point to an object, participant marker, area, or location; one standard marker, no label/style picker (D-77). | Durable, actor-attributed record linked to one group/event; visible only in that context, including event-tree review (D-75). No mechanical side effects. |
| Literal board edit | Move/tap a card, create a catalog/Blank token, edit card text or supported life/counters, add a link. | Explicit in-scope transition with history and eligible selective Undo; no general piece/named-value editor (D-241). |

Do not add a presence channel to meet this older inventory. Inspection exposure
records are durable events, not transient browsing/search signals; ordinary local
gesture previews and search input do not authorize hidden-identity disclosure.

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
an explicit route to inactive-line messages. Never migrate an annotation to a
different continuation as if its author endorsed it. No response-request/read-
receipt feature in R1 (D-241); viewing is never a rules approval or permission lock.

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
   D-297 gives Chat a **separate directly available button** and a panel, not an
   overflow-only route. Show a bubble for unviewed incoming game-chat messages.
   Read state is private server state per (game, account), shared across that
   account's devices. Advance its monotonic cursor to the newest message actually
   presented in the panel, never merely on opening; positions through that cursor
   are read. Count only other authors after it, including unloaded messages. Coalesce
   cursor reports and retry on reconnect. While connected, failed cursor writes
   retain the newest presented message and wait at least one second before retry;
   visibility callbacks and heads cannot bypass that deadline. Poll private cursors
   only for the currently opened game or pending writes, never each Games-list row
   (the list has no unread indicator). Own sent echoes do not create unread
   incoming messages. Do not publish read receipts to the other player,
   presence or gameplay mutations for opening/closing/viewing chat, and do not
   change the selected historical boundary or fork intent. Existing immutable
   sent-chat/no-Undo rules persist; this adds no Edit/Delete, mute or alert category.
   `GET /v1/games/{gameId}/chat/read` returns the caller's cursor and incoming
   unread count; `PUT` with `ReadChat(messageId)` advances it (older reports are
   ignored; a message from another game is invalid). Chat pages include the same
   private state. This does not increment the game revision or create history events.
2. Game chat is the only comment channel. There is no event Note/Reply action,
   event-comments UI or Event details dialog. Card notes and pings remain distinct.

D-94 excludes submitted game-wide chat from Undo. D-240 also excludes Edit/Delete
for sent chat: corrections use another message, with no planned expansions.
Unsent-delivery handling remains separate. Card
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
2. **Confirmed cards/tokens/piles:** catalog/Blank token creation, Clone, token-only
   Remove, ordinary movement/layout and recorded review. General labeled-piece
   authoring is excluded by D-241. Projections and contextual Stack actions remain
   separately supported, without inferred card-rule execution.
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

Historical control exclusions below are superseded by the 4×4 menu and value
widgets in section 10.2; numeric/text semantics remain unchanged.

This does not approve the previous always-visible tracker +/- controls,
double-tap -> Counters submenu, or tap-number-to-edit proposal. D-267's single tap
opens card Actions; D-61 confirms inline
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
3. Numeric zero is the same as empty everywhere; empty/zero counter affordances
   are faint outlines, not `0` badges. All six player counters stay visible in
   portrait's 3×2 grid beside packed special piles. A completed edit to empty/zero text removes
   the counter; temporary incomplete input does not publish removal by itself.
   D-59 trims text first: whitespace-only completed text removes the counter.
   Whether to offer a separate Remove shortcut remains open.
   Graveyard/Exile/Library entry still removes card counters as agreed.

Confirmed text trimming (D-59/D-249): completed card text and counter input is edge-trimmed on
completion, not only counter text. Preserve internal spaces/newlines. Pattern
matching and empty-value handling use the trimmed text: ` 2/3 ` becomes `2/3`,
` 0 ` is treated as empty, and whitespace-only counter input removes it.
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

1. Put counter controls in the Counters page, each as a 2×1 minus/value/plus
   widget; tap the value to Set or drag to adjust. Keep action availability separate
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
- D-248 fixes selected/unselected context-card targeting and captured scope above.
  Mixed-value rendering remains later UI work. Existing card tap/untap selection
  rules are unchanged; bulk operations retain individual mutation boundaries.

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

Round 16: Blank sorts by displayed name within the ordinary favorite/non-favorite
groups, without a pinned row. Descriptions use the shared card-text symbol renderer.
Include catalog Card/Dungeon designations (Monarch, City's Blessing, Undercity /
Initiative, Day / Night), retaining physical faces for flipping. Android lowers
creation plus placement into one atomic, undoable invocation: Land tokens use row
0; all others (including Blank/designations) use the free non-land row 1. Existing
recorded creation/placement events and shared rules are unchanged.

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
   stays unchanged. D-295 initializes the new token's owner to the participant
   invoking Clone, not the source's owner. Place automatically, without a preview/picker.
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
discard the token's recoverable fields. The
[reference matrix](domain-model.md#28-reference-lifetime-and-reachability) fixes
incident-link cleanup, absent selection/editor handling and historical contexts.
Tokens cannot be Library tray members; exact record encoding remains technical.

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
   initially. Runtime v1 defines positive sides up to 2147483647; picker layout/
   remembered presentation preferences remain UI work, not new roll modes.
2. Table Flip coin creates one heads/tails result with no configuration step.
3. Show actor, requested die/coin, and actual result in shared history; completed
   rolls/flips automatically queue/sync. Review/redo reuse the recorded result,
   not a fresh random draw. New invocations require a connection (D-182).
   D-261 permits client-side sampling for connected invocations, with the same
   sample supplied to shared client/server processing and recorded on acceptance,
   not independently rerolled by the server. Capture it in memory before display;
   D-278 permits loss before server admission on process death, no durable journal.
   Exact generator/encoding, display and action boundaries remain technical/UI
   work; results change no cards or values.

D-92 simplifies new die/coin invocations to fresh random draws, including after
undo, superseding D-88 and the pending seed/reset/draw-position proposal. No
opponent action is needed for a fresh draw. Results may repeat by chance. History,
review, redo, and retries of the same operation still reuse its stored result.
Local sampling does not confer authority or bypass input/applicability checks;
rejection does not automatically create a replacement roll. Exact generator/entropy
source and online protocol remain technical work; D-182 excludes new offline
random actions.

D-93 confirms every explicit new Shuffle samples a fresh order, including after
undo; review/replay/redo and retransmission reuse the recorded order. A fresh
shuffle may reproduce a prior permutation by chance. D-93 itself adds no shuffle
shortcuts and does not imply that covered order was exposed; D-193 later adds
explicit shuffle-and-draw compositions using the same recorded-randomness model.

**Authoritative owner revision, 2026-10-25:** selective Undo/Redo is allowed iff all
later game-tree events can apply with identical concerned-field `(before, after)`
tuples; work limits do not redefine eligibility. The relevant state for a tap must allow a prior independent
move of the same card to be removed; two edits of the same counter may instead
block each other. Information exposure never restricts Undo/Redo; apply the
authoritative concerned-field tuple rule. Per-mutation state,
N and pointer/control/Redo-queue integration remain open. The following earlier
rewind/exposure summary does not override that selective rule.

Undo/Redo remove/reinsert eligible own mutations in the tree, without original
alternatives or automatic post-Undo branching (D-232). The graphical explorer
can activate any genuinely retained branch/node. Only single-player/no-exposure inactive continuations qualify for
hiding behind Show hidden branches; mixed-player/exposure continuations stay
visible.
Exposure coverage includes
library View/Reveal, explicit logged hidden-side inspection (not D-252 own-area holds),
and die/coin results, but not every visible-card hold or search keystroke. Ordinary
summaries do not automatically disclose viewed card identities. See the
[primary undo-branch contract](history-and-undo.md#information-gaining-actions-and-undo-branches).

D-160 supersedes D-91's visible-path cutoff: keep trailing ordinary nodes visible
too when the abandoned continuation contains exposure. D-91's actual-
exposure classification remains, including draws/moves/face changes, not only
View/hold. Exact event placement/coalescing, forward/pre-fork presentation, non-Undo
branch controls remain specification work. D-246 permits no offline event-producing
inspection; recorded visible content remains readable. D-236 processes distinct
Undo requests as server-ordered presses, not D-161 captured transitions. Exposure
is not a persistent board marker or secrecy boundary.
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

Current shortcuts (playtest r3; supersedes D-100/D-101's inventory below):

- Untap: Untap, draw → main = untap + draw + Main 1, one Undo.
- Upkeep: Draw + go to Main 1. Draw: Draw one + Main 1.
- Main 1: Go to attacks → Declare attackers and Select mode; Skip combat → End step.
- Declare attackers without selection: No attack → Main 2; with selection retain
  Declare + tap / Declare without tapping.
- Declare blockers: defender gets No blocks + next.
- Main 2 / End step: visible End turn. No automatic combat-damage math.
- Show step actions only to the relevant turn player/defender. No tap/untap-selected
  or Graveyard quick actions; double tap/drag cover those. Underlying actions stay
  available through their full paths. A multi-action invocation is one Undo move;
  display grouping alone never combines separate invocations into an Undo.

Historical shortcut inventory (superseded):

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

Round 16: picker squares share the indicator's short step vocabulary (e.g. Begin
combat, End combat, Main 1), centered on up to two lines. The narrower advance
arrow stacks its icon above that short destination label (End turn when passing
the turn), preserving tap Next step / hold End turn. Multiselect uses overlapping
checked cards; Hand destinations use a three-card fan. Library/Graveyard/Exile
count bubbles are 16dp, centered on the pile card's top-left corner in both layouts.

The picker remains a dialog, exempt from floating menus. Its title shows phase
and step name (e.g. “Combat · Declare attackers”) plus turn player, with turn number
least prominent. Lane labels sit above steps. Header trigger is step icon + short
code, no turn number. Anyone can pass whose-move: the button flips its holder to
the other player, so a non-holder takes it. A step change sets move to the turn
player except Declare blockers → defender; bundle that invisibly in the step
event. Only a manual pass creates its own event, grouped backward for DISPLAY
only; Undo undoes just the pass. There are no other auto-passes or priority rules.

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

**D-298 supersedes D-107's separate-control placement:** one reserved quick square
invokes Next step by tap and End turn by hold, consuming tap with no double dispatch.
Keep explicit End turn in the phase picker/Table actions and native accessibility,
without routine confirmation. Undo handles eligible mistakes under ordinary rules.
This changes touch access, not the next-turn transition or cooperative permission.

**D-298 phase presentation:** show the exact step as an icon and its phase's color,
without phase/step words on the table indicator. Every step in the same phase uses
the same background color; a distinct icon identifies the precise step. Turn number
remains a separate numeric badge with full accessible player/turn/phase/step names.
Tap opens a picker with one row per fixed phase and horizontally laid-out step
icons. Choosing one directly changes only phase/step, preserving turn player and
number and performing no intermediate card operations. Bottom End turn reads
**End opponent's turn** when applicable; Next step remains explicitly available
alongside it inside the picker. Use the tracked turn player's border color on
indicator and picker. Trial brighter active/faded inactive battlefield **backgrounds**
only, not reduced card/text contrast. Owner borders, selection, visibility and both
players' editing rights remain independent. Exact palette/brightness is a UI trial.

D-299 fills every End turn / End opponent's turn control, including the combined
progression square, with the **currently tracked turn player's color** and
contrasting foreground. This is the player whose turn ends, not the next player
or requester. Update it with the displayed tracking context, including an armed
historical view; no extra request, permission or transition follows from color.

Confirmed setup (D-108/D-201): choose the starting player **in the lobby**, offering
either participant or **Random**; begin turn 1 at Beginning/Untap with that starter.
Entering the step initializes tracking, not opening-hand draws or a rules-driven
setup procedure. D-274 resolves Random once at actual server game start and retains
its chosen participant with the initial shuffle outcomes; option selection alone
does not roll. Exact control layout, defaults, edit access and option retention
remain separate completion work. Changing the starter
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
not earlier combat steps. Separate advance/close retain full paths; the combination
can be quick-only (D-253). Undo removes one eligible latest-own mutation (D-231/D-232);
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
mixed/empty-source cases and record mappings remain separate. D-242 settles Stack
token-disappearance inspection and source-action applicability; exact presentation
is later UI work.

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
- **Tap a card:** double tap the visible card outside Hand in normal mode. One orientation/tapped-presentation
  change; no activation, attack, or mana is inferred.
- **Read card:** hold it. Your area shows real faces without logging, including
  downloaded content offline; opponent-area holds do not expose hidden sides.
  Context Inspect other side is logged/online and does not Flip the shared card.
- **Inspect actions:** single tap in normal mode. The Actions sheet opens without
  changing orientation; a recognized double tap instead rotates without opening it.
  The full manual action catalog remains reachable through the approved paths.
- **Compact lands:** drag a land onto a highlighted battlefield pile. Its zone
  and other values do not change; diagonal peeks show the pile's members. The
  engine only knows that cards were arranged together.
- **Move from pile:** drag the Library-labeled pile's top card to Hand. Record
  the move literally and respect configured cover presentation; no “draw” inference.
- **Move and ping:** move a card to Stack, then ping a participant/card associated
  with that move. Both sync and appear together without a generated “cast at”
  sentence, typed rules target, completion step, or Send.
- **Represent an unusual action:** create a catalog/Blank token, edit its ordinary
  note/corner text, position it and add contextual pings. No generic piece editor.
- **Async question:** send a game-chat message about the reviewed action.
  No event-comment UI, dedicated response-request
  feature, inferred consent or automatic resolution (D-241).

## 10.10 Outstanding detailed design

Before implementation, resolve gesture timings, layout measurements, snap/drop
rules, pile hit targets/order, card-versus-pile controls, library inspection
gestures, generic ping context controls/association,
ping schemas/lifecycle, full wireframes, and interaction budgets. The conceptual
layout and examples above do not close those readiness gaps by themselves.
