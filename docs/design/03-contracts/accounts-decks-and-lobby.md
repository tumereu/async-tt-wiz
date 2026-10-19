# Accounts, decks, and game lobby

## Confirmed entry and navigation — D-188

- Users must log in before reaching the main menu. Registration is available;
  guest entry to the main menu is not part of this flow.
- The main menu provides **Logout**, **Manage decks**, and **Play**.
- Users can add other users. A separate **Friends** menu/feature name is tentative;
  lookup, requests/acceptance, removal, and invitation routing remain open.
- **Play** shows current games, including pending invitations. Detailed invite
  creation, acceptance/decline, cancellation and completed-game handling are open.

Authentication method, recovery, registration details and session persistence are
not selected. This does not revoke D-182's downloaded read-only offline access;
how an existing authenticated session supports it remains to be specified.

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
- Exact shortcut offerings and operations remain open: hand return, shuffle,
  redraw count, repeated mulligans, and any player-chosen bottoming. No automatic
  mulligan-count tracking, keep-hand gate or card-text/format rules are implied.
- The assistant's automatic draw-seven proposal was rejected. Starting shuffle
  and initial Library order were not separately approved by that correction.

The ordinary Draw/Draw N, move and Shuffle actions remain available. Any agreed
compound shortcut must preserve individual mutation nodes, immediate sync and
parent-step Undo under D-176; its exact mutation sequence still needs design.

## Remaining behavior decisions

- Readiness prerequisites, manually withdrawing Ready, and readiness effects of
  other setup changes. Deck/mainboard/sideboard changes clear the affected
  player's Ready (D-191), not an open policy.
- Invite/lobby persistence, leaving/cancellation, reconnect and competing Ready/
  setup changes, with no requirement that both players remain online together yet
  decided.
- Initial arrangement/values, starter-selection access, and which explicit setup
  operations occur on starting, including shuffle. Automatic opening draws are
  excluded; exact mulligan quick actions and opening-draw access remain open.
- Lobby-versus-game history/Undo boundary. Saved-deck isolation is confirmed
  (D-191), not an unresolved persistence choice.

These are behavior gaps, not authorization for application code or UI drafts.
