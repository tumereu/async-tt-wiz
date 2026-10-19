# Accounts, decks, and game lobby

## Confirmed entry and navigation — D-188

- Users must log in before reaching the main menu. Registration is available;
  guest entry to the main menu is not part of this flow.
- The main menu provides **Logout**, **Manage decks**, and **Play**.
- Users can add other users. A separate **Friends** menu/feature name is tentative;
  lookup, contact requests/acceptance and removal remain open. D-195 confirms
  inviting an added user, not a mandatory mutual-friend approval process.
- **Play** shows current games, including pending invitations. Basic invitation
  behavior is confirmed below; cancellation and completed-game handling are open.

Authentication method, recovery, registration details and session persistence are
not selected. This does not revoke D-182's downloaded read-only offline access;
how an existing authenticated session supports it remains to be specified.

## Inviting a player — D-195

1. Choose an **added user** to invite to a game.
2. The recipient sees the pending invitation in **Play**, with **Accept** and
   **Decline** actions.
3. **Accept** opens that game's **lobby**. It is not Ready and does not immediately
   start play; the existing deck preparation and both-player Ready flow applies.

Decline does not accept/join the lobby. Its listing/history, inviter feedback,
retry/reinvite and cancellation outcomes remain open. This confirms a path via
added users, not a decision that every other invitation method is forbidden.
Neither adding a user nor sending an invite bypasses the recipient's acceptance.

## Confirmed deck-management scope — D-189

- The deck editor currently supports **card-list import only**. A full manual
  deck-building editor, individual quantity/printing edits, and export are not
  approved release requirements by implication from older draft catalogs.
- In a game's lobby, a player chooses a deck and can manage its **mainboard and
  sideboard**. This lobby capability is distinct from a full saved-deck editor.
- Supported list syntax, card resolution/errors, saved-deck naming/replacement,
  and import replacement behavior remain open.
- **Lobby changes are game-local (D-191):** choosing/preparing a deck and changing
  its mainboard/sideboard for this game do not modify the saved deck.

The existing no-rules-referee boundary remains: these controls do not imply
format-legality validation or automated sideboarding rules.

## Confirmed lobby-to-game flow — D-190

1. A game is initially in a **lobby** state, not immediately in active play.
2. Players choose decks and manage mainboard/sideboard there.
3. Each player presses **Ready**. The game starts once **both** players are ready;
   one player's Ready alone does not start it. No separate host Start action is
   required by this flow.

**Ready follows your deck preparation (D-191):** changing your chosen deck or
mainboard/sideboard clears **your own Ready**, not the other player's. You must
press Ready again for the changed preparation before both-player readiness can
start the game. This rule applies while the game is still in the lobby; the exact
race between a lobby edit and game start remains to be specified.

This supersedes the assistant's immediate-playable-table suggestion; it was not
approved. A lobby Ready control is pregame coordination, not a gameplay Send or
publication gate. Existing gameplay mutations still synchronize immediately.

D-108 still requires choosing the starter and beginning turn 1 at Untap without
automatic card operations merely from entering that step.

## Opening hands and mulligan shortcuts — D-192

- **Do not draw opening hands automatically when the game starts.** Starting
  after both players ready does not itself draw cards.
- **Provide mulligan quick actions.** Requiring players to perform every
  mulligan operation through ordinary moves alone is not the approved design.
- **Two shuffle-and-draw actions (D-193):** one shuffles the Library and draws
  **N**, with **7 as the default**; the other shuffles and draws **current hand
  size minus one**, rather than an assumed fixed seven or automatic mulligan count.
- Both are available as **Library context actions**. They are also quick actions
  **only during Untap of turn 1**, the starting player's first turn's first step
  (D-194), not throughout Beginning or on each player's first turn. Context access
  remains available outside that quick-action window.
- **Both first return all cards in the affected hand to its Library, then shuffle
  that Library, then draw (D-194).** This is the whole hand, not other areas or
  the sideboard. The hand-size-minus-one count refers to the hand **before** its
  cards are returned: a six-card hand requests five, not minus one after emptying
  it. Hand moves use the normal Library-entry preservation/reset rules.
- Exact concurrent count-capture timing, nonpositive-count cases, hand-return
  mutation order, and any bottoming behavior remain open. Retain the ordinary
  D-49 available-card clamp for the N-card draw; do not reopen that default.
  No automatic mulligan-count tracking, keep-hand gate or card-text/format rules
  are implied. The preceding proposal's manual-bottoming step was not approved.
- The assistant's automatic draw-seven proposal was rejected. Starting shuffle
  and initial Library order were not separately approved by that correction.

The ordinary Draw/Draw N, move and Shuffle actions remain available. Any agreed
compound shortcut must preserve individual mutation nodes, immediate sync and
parent-step Undo under D-176; its exact mutation sequence still needs design.

## Remaining behavior decisions

- Readiness prerequisites, manually withdrawing Ready, and readiness effects of
  other setup changes. Deck/mainboard/sideboard changes clear the affected
  player's Ready (D-191), not an open policy.
- Invite/lobby persistence, decline presentation, leaving/cancellation, reconnect and competing Ready/
  setup changes, with no requirement that both players remain online together yet
  decided.
- Initial arrangement/values, starter-selection access, and which explicit setup
  operations occur on starting, including shuffle. Automatic opening draws are
  excluded. Mulligan counts/default, full-hand return, return → shuffle → draw
  order and Library context/turn-1 Untap quick access are settled. Remaining
  shortcut work: per-card mutation order, concurrent count timing and count edges.
- Lobby-versus-game history/Undo boundary. Saved-deck isolation is confirmed
  (D-191), not an unresolved persistence choice.

These are behavior gaps, not authorization for application code or UI drafts.
