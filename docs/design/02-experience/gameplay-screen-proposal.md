# Gameplay canvas screen proposal — live and historical modes

Captured 2026-10-21 from the owner's gameplay-layout notes. This is a standalone
continuation brief for review **after the separate initial UI draft session**.
It does not modify that session's [non-gameplay pilot](ui/README.md), replace its
gameplay placeholder, authorize implementation or declare the UI gate passed.

## Status and scope

The concrete layout below records the owner's requested design direction.
Items described as “maybe,” “perhaps” or “I think” remain tentative, as marked.
This proposal is not a synchronized replacement for the behavioral contracts;
in particular, the Undo/interruption question below must not be silently treated
as an already-applied contract change.

Scope here: the basic canvas appearance, responsive layout, live controls and
historical timeline. Exact sizing, colors, icons and interaction details can be
continued with the owner once the initial UI draft is ready.

## 1. Battlefield/card areas

For each participant, use two subtly different green bands:

- **Lower band: darker green, one card row.** Intended mainly for lands.
- **Upper band: lighter green, two card rows.** Intended mainly for creatures and
  other permanents/stuff.
- These are generic game/card areas, together effectively the battlefield. The
  visual convention does not restrict card types, enforce MTG rules or create
  separate semantic zones just because a card crosses a color boundary.

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
| Landscape | Library, Graveyard and Exile in a sidebar on the **left side of the screen**. Player life totals probably belong here too (tentative). |
| Portrait | Put those special-zone controls in the **middle**, between the participant areas. Life totals are tentatively grouped with them. |

Keep both participants' identities and zone associations clear; the opponent's
layout does not move the landscape sidebar to the right. Exact stacking/order,
sidebar width and portrait middle-strip geometry are not selected yet. Preserve
the existing zone counts, visibility and interaction rules.

## 4. Live-mode screen chrome

Reserve a **fixed bottom control area**, independent of board panning/zooming:

| Position | Proposed content | Certainty |
| --- | --- | --- |
| Bottom left | Undo button; possibly hold it for Redo. | Tentative placement/gesture. |
| Bottom center | Contextual quick actions. | Requested direction; individual contents follow existing action contracts. |
| Bottom right | Current selection/interaction mode indicator, perhaps a square showing only the mode; tap to change mode. | Tentative control shape/presentation. |

At the **top**, perhaps **top left**, show a square current game phase indicator.
Also provide a way to switch from live into **historical** mode; its exact control
and location are not settled. A phase display does not by itself specify a new
phase-changing gesture.

Redo reachability/accessibility and hold-versus-tap arbitration need design if
the hold-to-Redo idea is retained; do not interpret a hold as both Undo and Redo.
This is an interaction-design follow-up, not a new Undo eligibility rule.

## 5. Historical-mode screen chrome and timeline

Historical viewing replaces the live action chrome:

- **No gameplay actions or gameplay-edit controls** while browsing history.
- Fixed bottom row: **Back/Previous** on the left, **Play/Pause** in the center,
  **Next** on the right. These are review/playback controls, not shared Undo/Redo.
- Immediately above that row, a **left-to-right timeline** shows the current
  event-tree path being reviewed. It is not a full expanded tree diagram.
- An off-path branch can be indicated by a short **diagonal branch edge** leaving
  the path. There is no need to render the rest of that branch in this timeline.
  This is a visual simplification, not deletion or loss of retained history.
- Clearly indicate the **exact currently selected event/boundary** on the timeline;
  the marker/highlight design is not chosen yet.
- Swipe/drag the timeline horizontally to change that exact event. Board changes
  animate as the user drags rather than updating only after release.
- **Tentative:** allow timeline dragging only while paused. Whether a drag during
  playback is ignored or first pauses playback is not selected.

Exact-event scrubbing must remain distinct from grouped automatic playback. Under
the existing history contract, playback steps use automatic groups but individual
mutation boundaries remain reviewable. The exact Back/Next button granularity is
not settled by these notes. Animation follows the selected displayed endpoints,
not a queue that forces every intermediate event to finish animating.

## 6. Continue playing from a historical point

Provide an explicit **“Continue playing from here”**-type button (wording and
placement to design), separate from the timeline's Play/Pause transport.

The intended sequence is:

1. Pause/review and select an exact historical event boundary.
2. Choose to begin playing from that point, making gameplay controls available.
3. **Only if an action is subsequently performed** does this become an
   interruption/new continuation from that boundary.

Merely scrubbing, pausing, pressing timeline Play or selecting the continue
affordance does not itself create a shared-history mutation/fork. This matches
the existing explicit-continuation model: the first accepted mutation creates
and activates the continuation, not the personal review cursor alone.

**Owner's tentative Undo revision:** “It should be possible to undo up to this
interrupt action I think, even if previously specified not possible.” Preserve
this as an explicit follow-up, not as rejected because an older rule exists.

Current contracts already allow Undo of otherwise eligible latest-own mutations
strictly after a fork, including on an interruption branch, without automatically
reactivating its displaced future. They prohibit removing the fork point or
reaching back before it (D-233/D-245), and treat explicit branch return separately.
Thus undoing eligible actions **on the new branch down to its first action** may
already be covered; undoing the fork boundary/shared switch, crossing it, or
automatically returning to the displaced continuation would change behavior.
The scope of “up to this interrupt action,” including whether that action itself
is included, needs to be made precise in the continuation discussion. Do not
silently waive revealing-action, dependency, author or replay-safety checks.

## 7. Continuation checklist and existing contract anchors

Resume here once the other session's UI packet is ready:

- Review these live/historical arrangements in portrait and landscape alongside
  the initial non-gameplay draft, without assuming its placeholder is approved.
- Refine camera margin N/empty-board bounds, zoom, zone/life geometry and chrome.
- Resolve tentative hold-to-Redo, phase/mode controls, paused-only timeline drag
  and the precise requested Undo boundary at an interruption as one review batch.
- Account for the existing Hand, shared Stack and temporary combat area when
  completing the screen. Their positions are **not specified by these notes**;
  do not infer that the green bands replace or omit them.
- Keep personal review and UI state separate from server-authoritative gameplay.
  Historical mode must still receive sync; hiding action controls is not a server
  pause, branch activation or independently authoritative local game.
- If a behavior revision is agreed, synchronize the primary contracts, affected
  summaries, decision provenance and acceptance scenarios in that later batch.
  This capture intentionally changes only this separate proposal file.

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
