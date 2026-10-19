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
   Send, mandatory explanations, grouping chores, or routine confirmations.
6. **History is inspectable and recoverable.** Step through actions, interrupt at
   a boundary, retain the old continuation, and undo mistakes. Exactly one line
   is active; conflicts preserve alternatives rather than overwrite work. Undo
   moves the shared active pointer one parent node back, regardless of author.
   Retained alternatives permit forward return; ordinary abandoned continuations
   hide behind a toggle, while exposure/comment continuations stay fully visible.
7. **Cooperation, not enforced secrecy or legality.** Either participant can edit
   either side and inspect hidden information. Covering is a presentation
   convention. The app does not infer costs, targets, damage, or resolution.

## Boundaries

- Two players, native Android, manual play, asynchronous review, offline access
  to downloaded games, and queued actions are confirmed requirements.
- Turn/phase/step tracking, explicit combat/Stack projections, and common-action
  shortcuts are included conveniences. There is no priority tracker or rules
  referee.
- Existing exceptions include ordinary piles capped at three cards, counter/text
  resets on entry to configured Library/Graveyard/Exile, token disappearance in
  those destinations, and retained information-exposure history after Undo.
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
