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

- Saved deck **card-list editing is import-only**. Naming/renaming and selecting
  cover art are separately supported (D-199). A full manual
  deck-building editor, individual quantity/printing edits, and export are not
  approved release requirements by implication from older draft catalogs.
- In a game's lobby, a player chooses a deck and can manage its **mainboard and
  sideboard**. This lobby capability is distinct from a full saved-deck editor.
- D-198 specifies common MTG text import and unresolved-card handling below.
  D-199 specifies saved-deck naming, covers and re-import behavior below.
- **Lobby changes are game-local (D-191):** choosing/preparing a deck and changing
  its mainboard/sideboard for this game do not modify the saved deck.

The existing no-rules-referee boundary remains: these controls do not imply
format-legality validation or automated sideboarding rules.

## Saved decks, covers, and re-import — D-199

- Save **named decks**, and allow **naming/renaming** a deck.
- Select **one of that deck's cards as its cover art**. This is saved-deck
  presentation metadata, not a card edit, face change or additional game object.
- Re-import offers **Replace** an existing saved deck or **Save as new**. Replace
  updates the chosen saved deck; Save as new creates a separate saved deck rather
  than overwriting the original. D-198's unresolved-error rule applies before
  either save; an invalid re-import must not replace a valid saved list.
- **Already selected lobby/game copies remain unchanged when the saved deck
  changes.** A selected deck is game-local preparation, not a live reference whose
  card list updates behind a player's back. This complements D-191: lobby edits
  do not change the saved deck either. Selecting/reselecting a deck is still an
  explicit lobby action subject to the existing Ready-clearing rule.

Exact naming/default/duplicate-name rules, deletion, default cover selection,
cover fallback when its card is removed by re-import or imagery is unavailable,
and metadata retention across Replace/Save as new remain open. No full manual
card-list editor, art upload/cropping tool or saved-deck version-history feature
is approved by these controls.

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
   D-202 defines the initial placement and lobby-only Sideboard below.
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

## Initial placement and lobby-only Sideboard — D-202

- In the **lobby**, place each selected deck's **mainboard in Library** and its
  **sideboard in a separate Sideboard area**; all other card areas start empty.
  Players can prepare the mainboard/sideboard there under D-189/D-191.
- **Freely move cards between mainboard and sideboard in the lobby (D-203).**
  Allow either direction without enforcing format legality, board-size limits or
  one-for-one exchanges. This changes only the game's prepared deck and clears
  the editing player's Ready under D-191; it does not change the saved deck or
  grant access to Sideboard during play. Exact controls belong to later UI design.
- When play starts, use the prepared mainboard as the Library, with the automatic
  shuffle from D-200 and no automatic opening draw (D-192).
- **Sideboard is not available at all during the game.** Do not expose an in-game
  Sideboard area, contents browser, or card/action path to view, edit or move cards
  into/from that sideboard. This is not merely a covered pile or a collapsed dock.
- This does not delete the saved deck's sideboard or merge those cards into the
  Library. The saved-deck isolation rules remain. Exact retention of lobby setup
  data and any later rematch/return-to-lobby flow remain open.

This supersedes older proposals for an in-game Sideboard/extra-container access
path. It is a product lifecycle boundary, not card-text or sideboarding-legality
enforcement, and does not approve additional configurable gameplay areas.

## Lobby starter selection — D-201

- Choose the **starting player in the lobby**, offering **either player** and
  **Random**. Random selects one of the two participants; it is not a separate
  player or an automatic opening-hand action.
- Use the selected starter (or recorded random result) as the turn player for
  **turn 1 at Beginning/Untap** (D-108). Retain random outcomes for retries/review
  rather than choosing a different starter when recovering the same operation.
- Initial selection/default, when Random resolves, who can change the selection,
  and re-randomization remain open. Starter selection does not
  replace the existing both-player Ready gate or add a host Start action.
- **Changing the starter selection clears only the changing player's Ready
  (D-204), preserving the other player's Ready.** The changing player must ready
  again before play can start. This replaces the assistant's proposal to clear
  both players. It does not separately approve a manual Withdraw Ready control;
  that remains open, as do start-versus-edit races and Random-resolution timing.

## Game-start shuffle and life — D-200

- **Automatically shuffle each player's Library when the game starts.** This is
  part of the lobby-to-game transition, not something that waits for a player to
  use an opening-hand shortcut.
- **Each player starts at 20 life.** A starting-life setting/editor is not
  required now. This does not remove the existing manual life edits during play.
- **Do not draw automatically** (D-192). The explicit opening-hand/mulligan
  actions still return the whole hand, shuffle again and draw when invoked.
- Starting a game is distinct from entering Untap through tracking or resuming
  the app. Those activities do not repeat initial shuffles or reset life. Retain
  recorded shuffle orders for review/delivery retries rather than sampling again.

This replaces the assistant's no-automatic-shuffle recommendation and editable
lobby starting-life proposal. D-202 separately confirms initial placement and
lobby-only Sideboard; D-201 confirms starter selection with either player or Random.
Exact initialization/history sequencing also remains to be designed.

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
- The assistant's automatic draw-seven proposal was rejected. D-200 separately
  confirms the automatic game-start shuffle, not an automatic draw.

The ordinary Draw/Draw N, move and Shuffle actions remain available. Any agreed
compound shortcut must preserve individual mutation nodes, immediate sync and
parent-step Undo under D-176; its exact mutation sequence still needs design.

## Remaining behavior decisions

- Readiness prerequisites, manually withdrawing Ready, and readiness effects of
  other setup changes. Deck/mainboard/sideboard changes clear the affected
  player's Ready (D-191); starter-selection changes clear only the changing
  player's Ready (D-204). Both preserve the other player's Ready.
- Invite/lobby persistence, decline presentation, leaving/cancellation, reconnect and competing Ready/
  setup changes, with no requirement that both players remain online together yet
  decided.
- Further setup values/optional areas and starter-selection defaults, edit
  access and random-resolution timing. D-201 settles lobby
  placement and either-player/Random options. D-202 settles initial mainboard/
  sideboard placement, empty other card areas and no in-game Sideboard access.
  D-200 settles automatic game-start Library shuffles and 20 starting life, with
  no pregame life setting required. Automatic opening draws are excluded.
  Mulligan counts/default, full-hand return, return → shuffle → draw
  order and Library context/turn-1 Untap quick access are settled. Remaining
  shortcut work: per-card mutation order, concurrent count timing and count edges.
- Lobby-versus-game history/Undo boundary. Saved-deck isolation is confirmed
  (D-191), not an unresolved persistence choice.

These are behavior gaps, not authorization for application code or UI drafts.
