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
  and whether lobby mainboard/sideboard changes affect the saved deck remain open.

The existing no-rules-referee boundary remains: these controls do not imply
format-legality validation or automated sideboarding rules.

## Confirmed lobby-to-game flow — D-190

1. A game is initially in a **lobby** state, not immediately in active play.
2. Players choose decks and manage mainboard/sideboard there.
3. Each player presses **Ready**. The game starts once **both** players are ready;
   one player's Ready alone does not start it. No separate host Start action is
   required by this flow.

This supersedes the assistant's immediate-playable-table suggestion; it was not
approved. A lobby Ready control is pregame coordination, not a gameplay Send or
publication gate. Existing gameplay mutations still synchronize immediately.

D-108 still requires choosing the starter and beginning turn 1 at Untap without
automatic card operations merely from entering that step. This decision does not
choose initial pile order, shuffling, opening-hand dealing or mulligan behavior.

## Remaining behavior decisions

- Readiness prerequisites, withdrawing Ready, changes after Ready, and the exact
  configuration to which readiness applies.
- Invite/lobby persistence, leaving/cancellation, reconnect and competing Ready/
  setup changes, with no requirement that both players remain online together yet
  decided.
- Initial arrangement/values, starter-selection access, and which explicit setup
  operations occur on starting, including shuffle and opening hands.
- Lobby-versus-game history/Undo boundary and saved-deck isolation.

These are behavior gaps, not authorization for application code or UI drafts.
