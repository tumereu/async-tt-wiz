# Tier 1 — Product and usability principles

## What we are building

A native Android shared tabletop for two cooperative people to play card games
manually, together or asynchronously. Magic: The Gathering is the first use case.
The app remembers and presents what happened; the players interpret the rules.

## The rules a player should be able to learn once

1. **Manipulate the table directly.** Drag to place, tap to tap, double tap for
   actions, hold to inspect. Selection applies the same tools to several cards.
2. **Placement follows your action.** Drags go where dropped. Quick actions use
   predictable defaults: new quick-declared attackers go to the right.
3. **Change only what was requested.** Moving normally preserves card state.
   Explicit exceptions must be visible, consistent, and recoverable, not guessed
   from card text or names.
4. **A projection is another view, not another card.** Creating one leaves the
   original in place; card edits through it affect that same original.
5. **Act, sync, undo.** Completed edits save and synchronize automatically, without
   Send, mandatory explanations, grouping chores, or routine confirmations. Each
   tree node is one mutation, such as moving or tapping one card; bulk controls
   issue an ordered sequence of individual operations, not one atomic bulk node.
   A small local send queue transmits actions concurrently; each succeeds or fails
   individually. Normal interaction does not wait for network acknowledgements.
   Clients send actions in order; the server processes them in arrival order.
   Corrections animate only what must change, preserving accepted work and valid
   local predictions rather than visibly rewinding and replaying unaffected edits.
6. **History is inspectable and recoverable.** Step through actions, interrupt at
   a boundary, retain the old continuation, and undo mistakes. Exactly one line
   is active. Going back to continue from an earlier state requires an explicit
   choice; Undo supplies that intent automatically. Ordinary concurrent edits
   normally apply at the current head unless impossible. Optimistic conflict
   guards are limited to moves and Stack additions: an addition sends the current
   Stack state and is rejected if it differs on the server. Rejection never
   creates a branch. Undo moves the shared active pointer one parent node back,
   regardless of author.
   Retained alternatives permit forward return; ordinary abandoned continuations
   hide behind a toggle, while exposure/comment continuations stay fully visible.
   Groups are playback steps whose changes animate concurrently, not tree nodes.
   On return after an interruption, animate from the last local state back to
   the fork, then forward along the new continuation. Pause automatic playback
   and scrub the horizontal bottom timeline to choose an exact mutation boundary.
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
- Turn/phase/step tracking, explicit combat/Stack projections, and common-action
  shortcuts are included conveniences. There is no priority tracker or rules
  referee.
- Existing exceptions include ordinary piles capped at three cards, counter/text
  resets on entry to configured Library/Graveyard/Exile, token disappearance in
  those destinations, and retained information-exposure history after Undo.
  To end play, suggest a win/loss/draw as undoable board state; the other player
  accepts before both return to lobby. **Acceptance is not undoable**; pending
  suggestions still are. No win-history or scorekeeping is required.
- Finished-game action-history archives/review are optional future development,
  **not R1**. Current-game history, Undo and branching remain included.
  [Tier 2](../02-experience/behavior.md) describes them; they are not extra rules
  to infer for other situations.
- Broader setup/import/authoring/completion/platform scope still has proposals
  mixed into the detailed drafts. See [scope and gaps](../02-experience/gaps.md).
  Competitive services, multiplayer, non-Android gameplay clients, and automatic
  rules execution are outside the documented release direction.

## How this design should stay simple

The lower tiers explain these rules; they must not quietly invent a different
interaction model for each feature. Prefer one shared behavior over many special
cases. Record actual unresolved outcomes as gaps, not a question for every minor
detail. Do not silently remove already-approved exceptions to make the summary
look simpler.

**Next:** [Tier 2 — user-facing behavior](../02-experience/behavior.md).
For traceability, see the [detailed product contract](../03-contracts/product.md).

**Status:** specification only; no application implementation is authorized.
