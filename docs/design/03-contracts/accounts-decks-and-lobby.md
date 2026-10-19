# Accounts, decks, and game lobby

## Confirmed entry and navigation — D-188

- Users must log in before reaching the main menu. Registration is available;
  guest entry to the main menu is not part of this flow.
- The main menu provides **Logout**, **Manage decks**, and **Play**.
- Users can **search by username and save users as contacts**, without a separate
  friend request or mutual approval (D-196). A separate **Friends** menu/feature
  name is tentative. Contact removal and exact search behavior remain open.
- **Play** shows current games, including pending invitations. Basic invitation
  behavior is confirmed below; cancellation and completed-game handling are open.

Registration/login/recovery behavior is confirmed below. Session persistence
remains open; this does not revoke D-182's downloaded read-only offline access.
How an existing authenticated session supports it remains to be specified.

## Registration, login, and account privacy — D-197

- **Register with username, email, and password.** Username is the identity shown
  to other users and used for the existing contact search.
- **Log in with email and password.** This selects the user-facing credential
  flow, not an authentication provider, implementation or storage scheme.
- **Reset forgotten passwords by email.** Exact reset link/code, expiry, delivery
  failure and session-revocation behavior remain to be designed.
- **Other users see your username, not your email.** Contact search/lists,
  invitations and shared game presentation must not disclose account email.
  Cooperative access to game information does not grant access to account email.

Field validation/normalization, email verification, username changes, session
persistence and account-change/recovery edge cases remain open. Do not infer a
required email-verification gate or a second login method from this decision.

## Adding users as contacts — D-196

- Search by username and add the selected user to **your contacts**. Adding is a
  saved contact choice, not a friend request requiring the other user's approval.
- It does not automatically add you to their contacts or accept a game invitation
  on their behalf. Game invitations still use recipient Accept/Decline (D-195).
- Exact username/search grammar, contact removal, discoverability/privacy and
  persistence details remain open. D-197 separately selects registration fields,
  email/password login, email password reset and email privacy.

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
- D-198 specifies common MTG text import and unresolved-card handling below.
  Saved-deck naming/replacement and import replacement behavior remain open.
- **Lobby changes are game-local (D-191):** choosing/preparing a deck and changing
  its mainboard/sideboard for this game do not modify the saved deck.

The existing no-rules-referee boundary remains: these controls do not imply
format-legality validation or automated sideboarding rules.

## Deck-list import compatibility and errors — D-198

Use the **common MTG plain-text decklist format** exported by existing tools,
not a project-specific syntax. The compatibility target is Arena/MTGO-style
**text lists**, not a claim to support every tool's proprietary export.

- Paste one `quantity Card Name` entry per line, such as `4 Lightning Bolt`.
- Accept the common optional printing suffix `(SET) collector-number`, such as
  `3 Verdant Catacombs (MH2) 260`; users need not strip exported printing details.
- Preserve mainboard/sideboard separation: support `Deck`/`Sideboard` sections,
  `SB:` sideboard entries, and the conventional blank-line sideboard separator in
  headerless MTGO-style text. Blank spacing in an explicitly sectioned list does
  not replace its section labels. A plain mainboard-only list also works.
- This is **syntax compatibility**, not an Arena-only card pool or format-legality
  restriction. Do not filter out otherwise supported paper cards merely because
  they are absent from Arena.
- **Flag unknown or ambiguous card names; never silently substitute cards.** Keep
  the pasted text available for correction, and require resolving import errors
  before saving. Do not save a partial deck with unresolved entries dropped.

Example of the supported common shape:

```text
Deck
4 Tarmogoyf
3 Verdant Catacombs (MH2) 260

Sideboard
2 Surgical Extraction
```

The real-world basis is documented by
[ManaBox's import/export guide](https://manabox.app/guides/decks/import-export/),
which identifies quantity/name with optional set/number as the common format used
by Arena and websites, and
[Wizards' Arena import guide](https://mtgarena-support.wizards.com/hc/en-us/articles/360049857771-Importing-a-Deck),
which demonstrates plain quantity/name lists. These are compatibility references,
not adoption of those apps' collection, legality, editor or confirmation flows.

Exact less-common dialects, name/face normalization, printing defaults/fallbacks,
quantity validation/limits and extra-section mappings (Commander/Companion/etc.)
remain open. Do not silently discard an unhandled section or its cards. CSV,
XML `.dek`, direct URL fetching and a card-data provider are not selected merely
by accepting this shared text format. The error-resolution control itself is not
yet a full deck-building editor.

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
