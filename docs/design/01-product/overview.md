# Tier 1 — Product and usability principles

## What we are building

A native Android shared tabletop for two cooperative people to play card games
manually, together or asynchronously. Magic: The Gathering is the first use case.
The app remembers and presents what happened; the players interpret the rules.

## The rules a player should be able to learn once

1. **Manipulate the table directly.** Drag to place, single tap for actions,
   double tap to tap/untap, hold to inspect (D-267). Selection applies the same
   tools to several cards. Select/Ping modes keep their own single-tap meaning.
2. **Placement follows your action.** Drags go where dropped. Quick actions use
   predictable defaults: new quick-declared attackers go to the right.
3. **Change only what was requested.** Moving normally preserves card state.
   Explicit exceptions must be visible, consistent, and recoverable, not guessed
   from card text or names.
4. **A projection is another view, not another card.** Creating one leaves the
   original in place; card edits through it affect that same original.
   Stack projections stay linked across source locations, including Graveyard
   and Exile. Only token disappearance leaves a read-only last-live snapshot;
   combat projections stay live-linked and disappear on Battlefield departure.
5. **Act, sync, undo.** Completed edits save and synchronize automatically, without
   Send, mandatory explanations, grouping chores, or routine confirmations. Each
   tree node is one mutation, such as moving or tapping one card; bulk controls
   issue an ordered sequence of individual operations, not one atomic bulk node.
   A small local send queue transmits actions concurrently; each succeeds or fails
   individually. Normal interaction does not wait for network acknowledgements.
    Clients send actions in order; the server processes them in arrival order.
    Semantic game state, including history, Undo/Redo and lifecycle controls, is
    server-authoritative. Immediate local application is a prediction, not a
    separate private game. Only non-request UI choices—camera, selection, personal
    review and open menus—are local; see the
    [ownership matrix](../03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259).
   Corrections animate only what must change, preserving accepted work and valid
   local predictions rather than visibly rewinding and replaying unaffected edits.
6. **History is inspectable and recoverable.** Step through actions, interrupt at
   a boundary, retain the old continuation, and undo mistakes. Exactly one line
   is active. Going back to continue from an earlier state requires an explicit
   choice; Undo/Redo instead edit the existing tree without branching (D-232).
   Ordinary concurrent edits
   normally apply at the current head unless impossible. Optimistic conflict
   guards are limited to moves and Stack additions: an addition sends the current
   Stack state and is rejected if it differs in the selected execution context
   (current play, or the explicit historical boundary). Rejection never
   creates a branch. The latest selective Undo/Redo design allows removing or
   reinserting a mutation only if every later mutation keeps its exact relevant
   before/after states, within a bounded suffix-work limit (D-224). This is stricter
   than live action acceptance; revealing mutations cannot be selectively undone.
   Undo targets only your latest still-applied action; Redo is last-undone-first.
   Your per-game Undo/Redo stack is shared game state across your devices, while
   remaining separate from the opponent's stack (D-235).
   Undo requests act on the server's current state; two requests count as two
   presses, including from different devices (D-236).
   Neither skips a blocked target. Undo cannot remove a branching point or reach
    back past one (D-233). A successful deliberate history switch—activation,
    interruption from the past or return—clears both Redo queues (D-258), rather
    than retaining entries across the switch. Ordinary own new play clears only
    your queue; ordinary opponent play, Undo/Redo and personal review retain their
    distinct rules. Redo never crosses a newly created fork (D-234).
   Personal viewing never changes eligibility: check all later retained events,
   even beyond an explicitly activated internal state. Redo must restore its own
   recorded relevant transition as well as preserving later ones.
   Undo removes and Redo reinserts mutations in
   the existing tree, not a new branch or a preserved original alternative.
   Personal review remains distinct from changing shared play. Explicit historical
   continuation branches to a new active head. A graphical tree explorer can activate any
   retained branch/node. Only single-player/no-exposure alternatives qualify for
   hiding behind a separate toggle; mixed-player/exposure paths stay visible.
   Event comments also keep the whole alternative visible, even without a reveal.
    UI actions send requests; the server creates events, grouped by deterministic
    logic (D-254). Groups are playback steps whose changes animate concurrently,
    not tree nodes or a menu-bound part of a request.
    Live mode applies incoming changes immediately while preserving valid local
    predictions; old acknowledgements do not rewind newer local moves (D-277).
    Return from background with unseen updates uses grouped playback, rolling back
    to the fork first if needed. Catching up or deliberately continuing play returns
    to live. Animate previous board → next board, not every same-card waypoint;
    pause/scrub still selects exact mutation boundaries inside groups.
7. **Cooperation, not enforced secrecy or legality.** Either participant can edit
   either side and inspect hidden information. Covering is a presentation
   convention. The app does not infer costs, targets, damage, or resolution.

## Boundaries

- Entry requires login, with registration available. Manage decks currently
  imports card lists only. Play includes current games and pending invites;
  games begin in a lobby with deck selection and mainboard/sideboard preparation,
  and start when both players press Ready. Sideboard is lobby-only and is not
  available during play.
- Two players, native Android, manual play and asynchronous review are confirmed.
  **Initial delivery requires a connection for game actions (D-182).** Downloaded
   games remain readable offline; offline action creation and later queue replay
   are not included. Optimistic local updates still hide latency while online.
    Offline inspection cannot create an event either: explicit logged hidden-side
    inspection needs a connection. Own-area hold inspection shows downloaded real
    faces without an event; opponent-area holds do not expose hidden information.
- Game/card data and artwork load dynamically rather than shipping a licensed
  catalog inside the app/backend. Automated tests use synthetic data and art;
  production data rights still require review.
- Turn/phase/step tracking, explicit combat/Stack projections, and common-action
   shortcuts are included conveniences. There is no priority tracker or rules
   referee.
- Individual actions remain accessible outside quick-action windows; combined
  shortcuts can be quick-only. A tap starts a new group unless the current group
  contains only taps/pings; its next substantive action joins that tap run, except
  an ending suggestion, which always stands alone (D-257).
- Existing exceptions include ordinary piles capped at three cards, counter/text
  resets on entry to configured Library/Graveyard/Exile, token disappearance in
  those destinations, and retained information-exposure history after Undo.
  To end play, suggest a win/loss/draw as undoable board state; the other player
  accepts before both return to lobby. **Acceptance is not undoable**; pending
  suggestions still are. Each suggestion is a group by itself, so group playback
  shows the final board before its acceptance dialog. No win-history or scorekeeping
  is required.
- Finished-game action-history archives/review are optional future development,
  **not R1**. Current-game history, Undo and branching remain included.
  [Tier 2](../02-experience/behavior.md) describes them; they are not extra rules
  to infer for other situations.
- Broader setup/import/authoring/completion/platform scope still has proposals
  mixed into the detailed drafts. See [scope and gaps](../02-experience/gaps.md).
  Competitive services, multiplayer, non-Android gameplay clients, and automatic
  rules execution are outside the documented release direction.

## How this design should stay simple

Crash recovery stays deliberately simple (D-278): restart from synchronized server
state, not a durable outgoing replay queue. Local uncommitted actions/predictions
and unreported inspections can be lost; server-admitted work still finishes and
arrives normally. A living process retains normal ordered retry/prediction behavior.
Server-retained exposure records govern controls even if an unreported view was lost.

The lower tiers explain these rules; they must not quietly invent a different
interaction model for each feature. Prefer one shared behavior over many special
cases. Record actual unresolved outcomes as gaps, not a question for every minor
detail. Do not silently remove already-approved exceptions to make the summary
look simpler.

**Next:** [Tier 2 — user-facing behavior](../02-experience/behavior.md).
For traceability, see the [detailed product contract](../03-contracts/product.md).

**Status:** specification only; no application implementation is authorized.
