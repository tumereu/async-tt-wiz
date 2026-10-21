# Gameplay canvas screen proposal — live and historical modes

Captured 2026-10-21 from the owner's gameplay-layout notes and clarified with the
owner in the follow-up gameplay review batch (D-291). The owner now authorizes
extending the same Stitch project and [Markdown/HTML/PDF packet](ui/README.md)
with this screen in both orientations. G00 remains labeled as the original pilot
placeholder for provenance only; G01–G06 and H01–H03 are the current extension.
This authorizes design artifacts only, not implementation or a passed UI gate.

## Status and scope

The concrete layout below records the owner's requested design direction.
Items described as “maybe,” “perhaps” or “I think” remain tentative, as marked.
This proposal supplements the behavioral contracts. The interruption clarification
below applies the existing Undo rule, not a change allowing Undo across forks.
Trial placement and presentation remain reviewable rather than final UI approval.

Scope here: the basic canvas appearance, responsive layout, live controls and
historical timeline. Exact sizing, colors, icons and interaction details can be
continued with the owner once the initial UI draft is ready.

## 1. Battlefield/card areas

Use two subtly different bands per participant: **own green, opponent red**
(D-295 supersedes the initial both-green coloring):

- **Outer band: darker, one card row.** Intended mainly for lands.
- **Inner band: lighter, two card rows.** Intended mainly for creatures and
  other permanents/stuff.
- These are generic game/card areas, together effectively the battlefield. The
  visual convention does not restrict card types, enforce MTG rules or create
  separate semantic zones just because a card crosses a color boundary.

**D-295:** no visible battlefield slots, row separators or card-area labels.
Logical snapping is unchanged. Upright, tapped and every ordinary visual-pile
configuration use equal horizontal allocation containing the complete rotated/
corner-peek envelope; no tap-dependent margin/reflow. Card/token owner colors
remain independent of field color/placement. Default owner is creator/bringer;
Change owner in card Actions is an explicit shared field edit, not controller or
permission. P/T is bottom-right. See the primary board owner contract.

Both bands extend conceptually without a fixed left/right edge. Horizontal
navigation is bounded to **N card slots beyond the outermost card** on each side,
so the user cannot pan endlessly into empty space. N is deliberately unspecified;
this is a camera margin, unrelated to the history replay limit also called N.
The empty-board bounds and whether the outermost-card calculation is shared
between bands/participants are geometry details still to specify, not permission
to impose a finite card-placement limit.

The game area can also pan/scroll vertically when needed. Aim for a reasonable,
usable zoom level that shows the full game area vertically rather than making
vertical scrolling the normal requirement. No exact zoom or minimum card size is
selected here.

**D-296:** at any given zoom, every table card uses the same size, including
combat, Stack, hands/backs, reveals and special piles. Area/row height never
silently changes scale. Preserve uniform horizontal footprints; use pan/scroll
when necessary. The review's 40 px card height is one illustrative shared zoom,
not a selected native minimum, fixed device size or permission to miniaturize
only one area. Magnified inspection remains deliberately separate.

## 2. Participant symmetry and card facing

- Your area is below; the opponent's arrangement is reflected top-to-bottom
  across the table center, retaining the horizontal position of everything.
- Thus the opponent's darker land band is farther from the center and their
  lighter two-row band is nearer it, matching your arrangement across the table.
- Cards always render facing the viewer: do not turn the opponent's cards upside
  down or mirror their artwork/text. Ordinary tap/untap orientation still applies.

**Wording clarification:** the owner said “mirrors yours via the vertical axis”
but also explicitly retained horizontal positions. This brief uses the latter
constraint: swap top/bottom (vertical position), not left/right. It is not an
instruction to reflect x coordinates or card faces.

## 3. Responsive special-zone/life placement

| Orientation | Requested arrangement |
| --- | --- |
| Landscape | Library, Graveyard and Exile in a sidebar on the **left side of the screen**, with Stack **between the two player-info blocks** (D-296). |
| Portrait | Put those special-zone controls in the **middle**, between the participant areas. Life totals are tentatively grouped with them. |

**D-295:** life uses a player-colored square containing only its total, without
printed player names or “Life” text. Keep participant identity accessible and
zone associations clear; the opponent's
layout does not move the landscape sidebar to the right. Exact stacking/order,
sidebar width and portrait middle-strip geometry are not selected yet. Preserve
the existing zone counts, visibility and interaction rules.

**D-296:** Library/Graveyard/Exile/Stack use a top-left circular **total** badge
when entries are not all shown, not counts underneath icons or a separate Stack
count button. Player counters are on a row beneath their special zones; portrait
hides a player's row only when every counter is empty/numeric zero. Values/menu
access remain. Tap life for life/counter actions. Drag up/down on numeric life/
counter values previews +N/−N locally and commits one relative edit on release;
cancel/zero delta commits nothing. See the primary player-value contract for
capture, nonnumeric/absent values and concurrent server arithmetic.

## 4. Live-mode screen chrome

Reserve a **fixed bottom control area**, independent of board panning/zooming:

| Position | Proposed content | Certainty |
| --- | --- | --- |
| Bottom left | Tap Undo; hold for Redo, never both. Explicit Redo is also available in the controls menu and accessibility actions. | Owner-selected first draft (D-291); timings remain to design. |
| Bottom center | Contextual quick actions. | Requested direction; individual contents follow existing action contracts. |
| Bottom right | Current selection/interaction mode indicator, perhaps a square showing only the mode; tap to change mode. | Tentative control shape/presentation. |

At the **top**, perhaps **top left**, show a square current game phase indicator.
Also provide a way to switch from live into **historical** mode; its exact control
and location are not settled. A phase display does not by itself specify a new
phase-changing gesture.

Recognizing a hold consumes the touch: do not invoke Undo on press-down and then
Redo, or invoke Undo on release after a recognized hold. Exact timing, feedback
and native accessibility presentation remain design work. Disabled operations
must explain their actual eligibility; the shortcut does not bypass Undo checks.

### Hands and central lanes — D-291 first draft

- Put the owner's Hand below the darker land band in initial framing, hovering
  immediately above the fixed bottom controls. It is an expandable row/fan and
  stays screen-fixed when the board pans/zooms; it is not another battlefield row.
- The draft places the opponent's covered Hand at the top outer edge as a
  **proposal**, not a separately confirmed fixed-top requirement. Ordinary hidden
  face/inspection rules still apply; no routine name leaks through backs.
- Use central temporary combat; Stack is central in portrait and between player
  info in the landscape left sidebar (D-296). Combat has blockers
  above attackers; projection entry leaves real battlefield sources in place.
  Combat is shown only when its shared area exists, including an explicitly open
  empty area. Stack supports both actual cards and independent effect projections.
- **D-295:** portrait Stack is right of the central libraries, with the same
  diagonal corner-peek vocabulary as other piles: top card plus at most two
  underneath, full count/all ordered entries retained and no gameplay item limit.
  Try combat between the library rows, Stack beside it (tentative placement).
  Only combat has faint vertical paired-slot dividers. Reveals sit between each
  player's Library and battlefield. G04–G06 add context/owner and token/ping/reveal
  examples; all previous gesture/disclosure/source rules persist.
- Life grouped with each participant's special zones, mode square opening an
  explicit Normal/Select/Ping chooser, and compact phase control at top left are
  **draft presentation choices**. The existing phase-tap tracking picker remains;
  a phase tap does not itself execute a shortcut or enforce rules.
- Dense combat, expanded hands and short landscape height may require zoom/pan.
  Prefer initial full-height framing, but do not shrink controls or claim that
  every populated table fits at a universally readable card size. Review captures
  are a starting composition, not final camera/hit-target geometry.

## 5. Historical-mode screen chrome and timeline

Historical viewing replaces the live action chrome:

- **No gameplay actions or gameplay-edit controls** while browsing history.
- Fixed bottom row: **Previous group / Play-Pause / Next**. D-296 gives Play/Pause
  visible text and the remaining portrait width; landscape uses a similarly sized
  center button with Previous/Next directly attached, not at screen edges. These
  are review/playback controls, not shared Undo/Redo.
- Immediately above that row, a **left-to-right timeline** shows the current
  event-tree path being reviewed. It is not a full expanded tree diagram.
- An off-path branch can be indicated by a short **diagonal branch edge** leaving
  the path. There is no need to render the rest of that branch in this timeline.
  This is a visual simplification, not deletion or loss of retained history.
- Clearly indicate the **exact currently selected event/boundary** on the timeline;
  the marker/highlight design is not chosen yet.
- **D-295:** make the timeline dense; each node itself is its event-type icon.
  Distinct types use distinct icons, repeated types reuse theirs; selection must
  not replace the type icon. Windowing/scrolling never omits semantic events.
- **D-296:** icons retain originating-player color, not affected-card owner color.
  Faint vertical dividers separate different effective playback groups; they do
  not change grouping or add a separator between every event.
- Swipe/drag the timeline horizontally to change that exact event. Board changes
  animate as the user drags rather than updating only after release.
- **D-291:** touching/dragging the timeline during playback pauses first, then
  scrubs exact mutation boundaries with board animation during the drag.

Exact-event scrubbing must remain distinct from grouped automatic playback. Under
the existing history contract, playback steps use automatic groups but individual
mutation boundaries remain reviewable. Previous/Next step groups, consistent with
the existing history contract; timeline scrubbing selects individual mutations.
Animation follows the selected displayed endpoints,
not a queue that forces every intermediate event to finish animating.

## 6. Continue playing from a historical point

Provide an explicit **“Continue playing from here”**-type button (wording and
placement to design), separate from the timeline's Play/Pause transport. D-296
retains the top-corner button and adds holding **Play while paused** as an
equivalent shortcut. Recognized hold consumes tap and cannot also start playback.
No tooltip/tutorial text; keep the explicit accessible route too.

The intended sequence is:

1. Pause/review and select an exact historical event boundary.
2. Choose to begin playing from that point, making gameplay controls available.
3. **Only if an action is subsequently performed** does this become an
   interruption/new continuation from that boundary.

Merely scrubbing, pausing, pressing timeline Play or selecting the continue
affordance does not itself create a shared-history mutation/fork. This matches
the existing explicit-continuation model: the first accepted mutation creates
and activates the continuation, not the personal review cursor alone.

**Owner clarification — D-291:** the concern was correcting accidental branch
creation by undoing the action immediately after the branch point. The owner is
content with the current rule if it already permits that. It does:

Given old `A → B → C` and new `A → X → Y`, eligible latest-own Undo can remove Y
and then X, leaving the current boundary at A. X is a child of the fork, not the
fork point A. Undo cannot remove A while it is a branching point, or cross an
actual retained fork. The old B→C
continuation is retained, **not automatically reactivated**; explicit return is
separate. Revealing, dependency, author, replay-work and later-fork restrictions
still apply: this is not unconditional cancellation/recall of a sent action.
After each accepted Undo, recalculate from the remaining tree; a former fork does
not impose a permanent lock if its last extra continuation has been removed.
Any remaining future still participates in normal replay/eligibility checks.

**D-296 supersedes the initial banner:** armed pre-action UI looks like ordinary
live play using the selected historical state, without “Continuing from…” copy.
Cancel/Return remain through normal contextual navigation/accessibility. Cancel
merely leaves this personal intent; no branch needs
deleting because none exists yet. Undo does not become cursor-relative just
because the displayed board is historical; Undo/Redo edit the existing tree rather
than supply continuation intent. A failed/no-change first action must
not create an empty fork or pretend the continuation succeeded.

## 7. Continuation checklist and existing contract anchors

Review the gameplay extension alongside the original pilot:

- Review these live/historical arrangements in portrait and landscape alongside
  the initial non-gameplay draft, without assuming its placeholder is approved.
- Refine camera margin N/empty-board bounds, zoom, zone/life geometry and chrome.
- Hold-to-Redo, pause-then-scrub and the Undo boundary were clarified above.
  Review actual phase/mode presentation and hand/central-lane spacing in captures.
- Keep personal review and UI state separate from server-authoritative gameplay.
  Historical mode must still receive sync; hiding action controls is not a server
  pause, branch activation or independently authoritative local game.
- The board/history contracts, summary and verification notes carry the clarified
  interactions. Exact camera margin N, empty-board bounds, touch timing and dense
  geometry remain UI completion, not new product behavior selected by a mockup.

Relevant sources for that follow-up:

- [Board and controls](../03-contracts/board-and-controls.md): generic areas,
  special piles, gestures, fixed controls and selection modes.
- [History and Undo](../03-contracts/history-and-undo.md): D-179 explicit historical
  intent, D-224–D-245 selective Undo/fork safety, D-258 shared-switch Redo clearing
  and D-277 live/playback behavior.
- [History grouping](../03-contracts/history-grouping.md): grouped playback versus
  individual mutation boundaries.
- [Combat/Stack contract](../03-contracts/combat-stack-and-projections.md): existing
  projection areas and their behavior, not newly laid out here.
- [State ownership](../03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259)
  and [prediction/playback](../04-technical/prediction-and-playback.md): personal
  camera/review state, ongoing synchronization and endpoint animation.
