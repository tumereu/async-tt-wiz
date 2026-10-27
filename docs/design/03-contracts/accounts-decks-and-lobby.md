# Accounts, decks, and game lobby

## Confirmed entry and navigation — D-188 / D-292

- Users must log in before reaching the main menu. Registration is available;
  guest entry to the main menu is not part of this flow.
- App name: **Async Tabletop Wizards**. Subtitle: **Specialized tabletop simulator
  for asynchronous wizardry** (D-292).
- The main menu provides **Games**, **Manage decks**, **Contacts** and **Settings**, without
  explanatory destination subtitles, a notification-settings shortcut or direct
  Logout. **Games** is the current label for the earlier **Play** destination.
- Post-login hamburger navigation has two clickable top-level categories:
  **Menu** opens the main menu, with indented **Manage decks**, **Contacts** and **Settings**;
  **Games** opens the games browser, with each current game indented beneath it.
  Contextual screens such as import/preparation are not global menu destinations.
  Settings contains Card records (moved out of Manage decks) and Log out. Debug
  account switching stays in the hamburger. Settings shows version/build commit,
  the account-local playback pause slider (100 ms–10 s, default 1.5 s), manual
  catalog re-import and debug animation-speed tuning.
  Landscape non-gameplay screens always show this navigation; gameplay in either
  orientation and portrait non-gameplay use the hamburger drawer. D-294 removes
  all app Back/home arrows; native Android Back remains. Hamburger and gameplay
  controls remain available without dedicated app-brand bars.
- Users can **search by username and save users as contacts**, without a separate
  friend request or mutual approval (D-196). D-292 selects **Contacts** as the
  destination name. Contacts can also be removed; exact search behavior remains separate.
- The **Games** browser combines in-play games and setup/lobby states into one
  Games list, followed by Invitations, in that order in both orientations.
  A lobby is a game's setup state, not a separate structural category or identity.
  Opening a row uses its current lifecycle state; invitation acceptance still
  enters the exact existing lobby under the rules below. D-292 changes presentation,
  not invitation/Ready/reinvite/cancellation behavior or archive scope.

### Compact presentation and contextual actions — D-293

- **D-294 supersedes the authentication-only scope:** no app Back/home arrow on
  any screen. Non-dialog screens have no X/close button; Android native Back
  navigates back. Only dialogs/popups may have dismiss controls; table popups
  float without close buttons. Keep necessary labels and outcomes.
- Main-menu options have an **ATW** logo above them (wizard-hat motif is provisional).
  D-297 also places that logo inside A01's centered login card in both orientations;
  short viewports scroll the same card rather than splitting/shrinking it.
  Use matching destination icons across the menu and indented navigation entries;
  Logout has its own icon. Top-level Menu/Games remain text. Button actions normally
  have meaningful icons plus text where space permits; tight gameplay controls use
  icons with accessible names. Preserve meaningful state values, not instructional filler.
- Game-list and main-menu quick-continue rows share grouped white surfaces and
  dividers: bold **game name** + whose-move chip (filled when mine), then
  “vs X · In play · <last activity>” (time if today), replacing Created. Main menu
  shows up to three in-progress games below Games: mine-to-move first, oldest
  updated first, then others newest updated first; hide empty slots. No filler,
  and a right chevron to open. Reinvite is inside the exact existing lobby, not on
  the browser row. Long press a game row (including navigation) opens contextual
  Cancel game for setup or Delete game for active play. Native accessibility exposes
  equivalent explicit actions; a hold must not also open the row or issue deletion.
- New Import deck is a bottom action in collection/picker. Existing-deck updates
  use **Edit → Import decklist** and the contextual Overwrite/Merge rules below.
- Deck-list rows place a small art thumbnail immediately after quantity. Press the
  thumbnail or row to open full card/art inspection using the gameplay-inspector
  presentation, without changing deck state. Artwork is lazy; name/text fallback
  and inspection remain usable if images are missing. Deck inspection grants no
  permission to inspect an opponent's covered in-game face.

### Title rows, contextual exits and compact selection — D-294

- Titled screens place hamburger and title on one compact content-control row;
  landscape keeps the title. Landscape persistent navigation replaces the hamburger
  as before. System Back leaves import/deck editing without saving unfinished input;
  there is no screen X/close button or publication of unresolved changes.
  Previously accepted metadata edits and auto-synced preparation are not rolled
  back by leaving: the exit discards only unfinished UI input, not server requests.
- Related button pairs fill their available row equally (50/50); compact gameplay
  controls and unrelated actions are not stretched into arbitrary pairs. Dialog
  close X is top-right. Cover selection uses a selection border, no extra checkbox;
  retain selected accessibility semantics. Saved-deck deletion copy is simply
  “Deletion cannot be undone”; selected-copy isolation still holds.
- Lobby title is its **game name**, details show state and creation date. Cancel
  game is top-right on that title row; Reinvite is inside the opponent's slot.
  Ready/Withdraw Ready remain bottom actions, with no leaving-lobby helper text.
  D-297 shows each player's actual readiness with a colored status icon: green
  checkmark for Ready, distinct non-ready/pending icon otherwise. Keep meaningful
  status text/accessibility; color alone is insufficient. Invitation pending or
  declined is not Ready or online presence. Deck changes/withdrawal/start still
  follow the existing server-authoritative rules, not client-inferred readiness.
- Deck picker rows select a personal draft using a border, without per-row Select
  buttons. **One bottom confirmation** submits the chosen prepared-copy selection
  and returns to lobby. Selecting/highlighting rows alone does not clear Ready or
  issue a request. Native Back leaves current preparation/Ready unchanged;
  confirmation uses existing preparation-round/source guards and copy isolation.
- One/all preparation markers are about **24 px wide each**, prioritizing **1 / ∞**
  over a separate grip. Drag semantics remain D-293. This explicit compact-handle
  exception replaces the 48 px-wide handle draft, not ordinary 48 px buttons or
  native accessible one/all alternatives. Do not invent overlapping hit targets.
- **Follow-up: these are tap buttons as well as drag origins.** Tap **1** to move
  one captured copy to the other section; tap **∞** for all captured row copies.
  A recognized drag suppresses the tap activation, so release cannot send a second
  transfer. Bind card/copy identities, not a row index that may move under the finger.
  Existing preparation-round, per-card requests, partial outcomes and own-Ready
  rules apply to both input routes; no Save/Send step is added.
- Reserve a fixed-height **plain-text latest-transfer slot**, even while empty or
  invisible: e.g. **1× Meadow Sentinel → Sideboard**. Show the actual displayed
  transfer promptly, fading visually over about **1–2 seconds** (1.5 s is a timing
  proposal). A new transfer replaces/restarts the latest message. Never insert/
  remove layout space on feedback appearance/fade. Feedback is personal UI, not a
  shared history/log entry or a server-acceptance receipt. Normal provisional
  application remains immediate; reconcile partial/rejected outcomes honestly,
  without waiting for acknowledgement or claiming all copies moved if they did not.
- **Disappearing-row transition:** when a tap makes displayed quantity zero, quickly
  fade out that outgoing row and slide subsequent rows up into its vacated space.
  A roughly 150–220 ms combined transition is a technical timing proposal, not an
  exact owner-selected duration. This actual list reflow is intentional; it must
  not be triggered by feedback text appearing/fading. Rows retaining copies stay
  present with updated quantities. Presentation may retain a fading visual ghost,
  not an actionable/counted source row or extra game state. Requests/count updates
  never wait for animation. Retarget rapid transfers/reconciliation from current
  drawn positions instead of queuing stale animations; never redirect a tap to a
  different row sliding under the finger. Native reduced-motion/accessibility
  behavior must preserve understandable transfer feedback and action identity.

Registration/login/recovery and session continuity are confirmed below. These
do not revoke D-182's downloaded read-only offline access; exact interaction
between a reauthentication-required state and offline access remains open.

## Registration, login, and account privacy — D-197

- **Register with username, email, and password.** Username is the identity shown
  to other users and used for the existing contact search.
- **Log in with email and password.** This selects the user-facing credential
  flow, not an authentication provider, implementation or storage scheme.
- **Reset forgotten passwords by email.** D-279 revokes all existing sessions only
  when a valid reset actually changes the password, not when email is requested or
  sent. Failed/expired/reused links do not revoke sessions. Technical token/delivery
  details are in [authentication](../04-technical/authentication-and-sessions.md).
- **Other users see your username, not your email.** Contact search/lists,
  invitations and shared game presentation must not disclose account email.
  Cooperative access to game information does not grant access to account email.

Field validation/normalization, email verification, username changes and
account-change/recovery edge cases remain open. Do not infer a
required email-verification gate or a second login method from this decision.

**Credential normalization — D-249:** card-text trimming is not an account rule.
Preserve password whitespace and Unicode consistently through registration,
login and password reset; do not silently trim, case-fold or Unicode-normalize
secrets, including pasted boundary whitespace. Apply the same credential handling
at every entry point. Username/email validation and equivalence are separate
account/security design, never inherited from a card-text helper. Password
strength, provider, reset mechanics and storage remain technical/security work.

**Ordinary revocation boundary — D-289:** after successful reset, existing sessions
no longer authorize new requests. Already-authorized/in-flight work, including
admitted game commands, may finish normally. No special password-reset-versus-login/
game/HTTP-commit ordering guarantee is required; normal authentication and request
checks suffice. This does not weaken password hashing, valid reset-token handling
or normal game/lifecycle applicability.

## Session continuity and reauthentication — D-217

- **Stay logged in across app restarts.** Restarting alone does not require the
  user to enter credentials again while their session remains valid.
- **D-268: no routine time-based session expiry.** The owner requests permanent
  login convenience, allowing a non-expiring token or saved credentials with
  automatic reauthentication. Use the non-expiring, revocable-token route; storing
  the password is not needed. Idle time, absolute session age, app/service restarts
  or reconnects must not require login again. Explicit Logout clears this device's
  login and must not be defeated by automatic reauthentication.
- **If credentials become unavailable or the server explicitly invalidates the
  session, ask the user to log in again.** Non-expiring does not mean unrevocable,
  valid after account access is removed or recoverable after local app data/keys
  are lost. After successful
  login to the same account, **return to the same game** and recover retained
  same-process requests. D-278 restart instead synchronizes from the server without
  restoring forgotten predictions or a durable outgoing journal.
- Pending already-sent online actions retain their original request identities
  and conditions. Use D-216 automatic recovery and D-185 reconciliation: no
  duplicate execution, repeated random sampling or presumed rejection merely
  because authentication became invalid. This does not authorize offline gameplay.

Token encoding/protection/revocation mechanics, failed or cancelled reauthentication
and reauthentication-required navigation remain specification work; idle/absolute
expiry is not a remaining default to select after D-268. D-218 confirms multiple
devices; D-241 below settles explicit Logout cleanup and cross-account isolation.
Session invalidation alone is not Logout. D-278 limits pending-work recovery to
surviving process memory; D-279 selects reset-time revocation of all sessions.

### Logout and account-private downloads — D-241

- **Explicit Logout removes private downloaded content from the device.** This
  does not delete server-side decks/games or invalidate selected deck copies.
  Downloaded read-only access remains available in the account's ordinary use;
  it is not a promise to retain that content after deliberate Logout.
- **Local logout is immediate; server logout is best effort (D-289).** Clear local
  credentials/data and fence callbacks without waiting for connectivity. Once
  processed, server logout revokes that session for new requests; already-authorized
  work may finish. A lost/offline logout need not be recovered through a durable
  credential/revocation queue. Other sessions remain independent.
- **D-278 replaces protected client recovery obligations:** Logout discards volatile
  pending work/exposure reports; no crash-durable outgoing journal is required.
  This is not proof of rejection or recall of server-admitted work. Future login
  synchronizes server state and later commits normally, without recreating commands.
  Fence old callbacks before storage/display/submission; another account cannot
  read or publish the previous account's data. No offline inspection/queue is added.
- **Never expose one account's private data to another account.** Apply account
  isolation when switching accounts as well as after Logout. Recovery of the
  previous account's requests does not become the new account's gameplay.
- Exact storage protection, cleanup/re-download and outcome-recovery mechanics
  remain technical work. Server retention and account-erasure obligations remain
  privacy/service design; this decision promises neither immediate permanent
  erasure nor indefinite server retention.

## Multiple devices and account data — D-218

- **Support the same account on multiple devices**, with **saved decks, contacts
  and games synchronized between them**. These are account data, not independent
  device-only collections requiring manual re-import, re-adding contacts or new
  game invitations merely to continue on another device.
- Saved-deck synchronization includes the existing saved card lists, names and
  chosen covers. Preserve D-191/D-199 isolation: changing a saved deck does not
  replace deck copies already selected in a lobby or game.
- A device opens the account's existing games and synchronizes their current
  state/history under the existing game contracts. Another device is not another
  game participant. D-182's connected-only gameplay and D-216's no-duplicate
  recovery remain; this does not add offline gameplay or finished-game archives.
- **D-235:** each player's per-game Undo/Redo stack is shared game state across
  their devices, not a local pending-request queue. Undo on one device can be
  Redone on another, with unchanged own-action targeting, ordinary player-specific clearing
  and eligibility checks. See the [history contract](history-and-undo.md#selective-mutation-undoredo--d-224).
  D-258 additionally clears both queues on a successful deliberate shared-history
  switch. This is ordinary server-owned game control state under D-259, not a
  separate device-transfer feature; personal UI state remains local.

**D-236:** concurrent gameplay from the account's devices is supported through
normal server ordering and action acceptance, without requiring one active device
or special collision UX. Two distinct Undo requests count as two Undo presses,
not a same-target collision to suppress; delivery retries still execute only once.
See the [history contract](history-and-undo.md#selective-mutation-undoredo--d-224).

Concurrent deck/contact change conflicts, remaining shared-control races,
device/session revocation and sync timing remain open. Do not infer cross-device
transfer of unfinished input, local pending-request queues, navigation/review
cursors, settings or all cached downloads from this account-data decision.

## Adding users as contacts — D-196

- Search by username and add the selected user to **your contacts**. Adding is a
  saved contact choice, not a friend request requiring the other user's approval.
- **D-296 search presentation:** results/loading/empty feedback belong in
  the same card/panel as their search field, not a separate results card. This
  applies across already-in-scope searches, not just contacts; saved contacts may
  remain a separate collection. It changes no matching/privacy/request semantics
  and does not authorize additional search tools. Reserve loading space or use a
  full-area placeholder; real errors use the floating transient error, not inline text.
  D-297 makes the results header a small subordinate label, not another title.
- It does not automatically add you to their contacts or accept a game invitation
  on their behalf. Game invitations still use recipient Accept/Decline (D-195).
- Contacts can be removed from your saved list. Exact username/search grammar and discoverability/privacy
  remain open; D-218 confirms account contacts synchronize across devices.
  D-197 separately selects registration fields,
  email/password login, email password reset and email privacy.

## Inviting a player and the waiting lobby — D-195/D-208

1. **New game** opens **Choose opponent**, listing contacts only. Choose an
   **added user** to invite, then name the new game under D-294 below.
   Confirm Create & invite to create the game and first invitation together.
2. **For the inviter, the invitation is already an open game lobby (D-208).**
   They can select/prepare their deck and press Ready while waiting for acceptance.
   They do not need a separate outgoing-invitation cancellation action:
   **Cancel game cancels the pending invitation as well as that game.**
3. The recipient sees the pending invitation in **Play**, with **Accept** and
   **Decline** actions.
4. **Accept** opens that same game's **lobby for the recipient**. It is not Ready
   and does not immediately start play, even if the inviter is already ready;
   the recipient still prepares their deck and presses Ready. Acceptance does
   not replace the lobby or reset the inviter's preparation/readiness.
5. **Decline does not destroy or cancel the lobby (D-208).** The inviter keeps
   their lobby, preparation and accepted Ready and can **reinvite** the recipient
   to that same lobby. Decline is not acceptance or Ready; reinviting does not
   bypass the recipient's acceptance.
6. **Pending invitations never expire automatically (D-209).** They remain pending
   until explicitly accepted, declined or cancelled with their game.
7. **Declined lobbies remain available to the inviter in current games (D-209).**
   Decline does not remove the inviter's lobby from that list. **Cancelled games
   leave the current-games list**, including their cancelled pending invitations.

Exact recipient-side declined-invitation listing/history, inviter feedback,
duplicate handling, cancellation races and retention remain open. This
permits invitations only to contacts. Neither adding a user nor sending an invite bypasses acceptance.
No replacement-opponent or open matchmaking feature is implied by reinviting.

**Create versus reinvite — D-287:** creating a game with its first invitation and
inviting to an existing game are distinct requests/parameters. Reinvite names that
exact existing lobby and never falls through to creation. If cancellation processes
first, discard reinvitation because the game is no longer available; if reinvitation
processes first, subsequent cancellation removes that game and its pending invitation
from current listings. Both orders leave no current game/invitation, with no revival
or replacement. This does not require physical erasure of recovery/control records.
Retrying creation recovers its original IDs/outcome, not a new game after cancellation.

### Required game name and creation date — D-294

**D-295 presentation:** naming is a bounded dialog, not a full screen. Touching/
focusing its name field selects all existing text for replacement, including
suggestions and an already edited value; retain normal subsequent text editing
and accessible/keyboard selection. Selection alone never rerolls or submits.
Close/Cancel/native Back creates nothing; Create & invite retains the atomic
request below. Saved-deck deletion similarly uses one title, not an extra
“Confirm deletion” eyebrow; the cannot-undo warning and copy isolation remain.

**Confirmed:** name each game when creating it. Offer random procedural
**adjective + thing** suggestions themed around fantasy/wizardry, such as **Azure
Spark** or **Crackling Mist**. A proposed compact form prefills an editable suggestion
and offers a reroll icon; the user may accept it or type a name. A name containing
only whitespace is not valid. Opening/cancelling the form or rerolling a suggestion
does not create a game/invitation. The final create request binds the chosen name,
recipient and original request ID; retries never regenerate/rename the game.

The server records the creation instant once when the persistent game and first
invitation are accepted. Game rows display last activity, not Created; the gameplay
top and bottom bars show whose MOVE it is, while battlefield tint identifies whose
turn it is; timeline bars are gray, with no green move frame. Elsewhere the name
identifies the game and opponent remains metadata. Creation date belongs to the
persistent game, not each play incarnation:
acceptance, reinvite, Ready/start and result-return/fresh start preserve name/date.
They synchronize to the recipient and other own devices. Names are labels, never
identities or ordering keys; the stable game ID distinguishes repeated suggestions
or matching names, without inventing a uniqueness constraint or collision suffix.

Name suggestions are personal pre-creation UI randomness, not gameplay random
events, shared history or a reason to invoke Shuffle/seed protocols. Server creation
time is not the device's draft timestamp and retry cannot move it. Do not infer a
post-creation rename UI, name-generation service, age expiry or archive. Exact word
lists/date formatting are presentation/technical detail, not unresolved game policy.

## Confirmed deck-management scope — D-189

Deck browser refresh is manual pull-to-refresh with the standard indicator, never
a background refresh. Background game loads are silent. Lifecycle-interruption
errors show nothing; real errors use a floating snackbar, not inline red text.
Loading reserves space or uses a full-area placeholder; resuming gameplay never
inserts a loader or shifts/bounces the table layout. Deck-import validation uses
the fixed inline error area specified below.

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
- **D-293 supersedes the generic Replace / Save as new selector.** Import deck
  from the collection/picker creates a new saved entry. Updating an existing deck
  requires opening that deck, then **Edit → Import decklist**, with **Overwrite /
  Merge** toggle defaulting to **Overwrite** on a new invocation. The target is
  that deck, not an arbitrary existing-deck selector on the new-import form.
- **Overwrite** installs the fully resolved imported mainboard and sideboard.
  **Merge adds quantities** for matching resolved card entries within each section,
  retains untouched entries, and adds new entries. Existing 4 + imported 2 = 6.
  Mainboard/Sideboard stay separate; a mainboard-only overwrite empties the sideboard,
  while a mainboard-only merge preserves it. Matching uses the import's resolved
  entry/printing identity, not artwork availability or a guessed name match.
  D-198 applies to both modes: any unresolved/ambiguous entry blocks the entire save.
  Invalid/failed import never partially changes a saved deck. The mode and target
  remain bound to the original save request; retry cannot add Merge quantities twice.
- **Already selected lobby/game copies remain unchanged when the saved deck
  changes.** A selected deck is game-local preparation, not a live reference whose
  card list updates behind a player's back. This complements D-191: lobby edits
  do not change the saved deck either. Selecting/reselecting a deck is still an
  explicit lobby action subject to the existing Ready-clearing rule.

Exact naming/default/duplicate-name rules, default cover selection,
cover fallback when its card is removed by re-import or imagery is unavailable,
  and metadata retention across contextual import remain open. No full manual
card-list editor, art upload/cropping tool or saved-deck version-history feature
is approved by these controls.

## Saved-deck deletion and copy isolation — D-221

- **Deleting a saved deck removes it from your saved-deck collection**, but
  **preserves copies already selected in lobbies or games**.
- The owner explicitly confirmed **editing works the same way**: saved-deck
  edits do not alter already selected lobby/game copies, reaffirming D-199.
  Preserve their game-local mainboard/sideboard preparation and any active play;
  do not require a replacement deck merely because its saved source was deleted.
- This is saved-deck management, not deletion of in-game cards or history.
  The account's saved collection remains synchronized under D-218; exact
  concurrent delete-versus-edit/select handling across devices remains open.

**D-241 excludes a trash/restore feature for deleted saved decks.** Keep selected
lobby/game copies intact; there is no saved-deck gameplay-Undo path. Exact
confirmation presentation, server retention and backup erasure belong to UI or
privacy/service/technical design, not an unresolved in-app restoration promise.

## Deck-list import compatibility and errors — D-198

Use the **common MTG plain-text decklist format** exported by existing tools,
not a project-specific syntax. The compatibility target is Arena/MTGO-style
**text lists**, not a claim to support every tool's proprietary export.

- Paste one `quantity Card Name` entry per line, such as `4 Lightning Bolt`.
- Import has **From URL | Paste list** tabs. On the phone, From URL fetches
  Archidekt public JSON, MTGGoldfish text plus its public page name, or Moxfield
  public JSON, then uses the same resolution/errors/save pipeline as pasted text.
  Keep the site's name and mainboard/sideboard; commanders enter mainboard,
  maybeboard is omitted. Provider failures suggest **Paste list**.
  Copied deck URLs (including share queries/fragments) select the provider;
  `archidekt:ID`, `mtggoldfish:ID`, and `moxfield:ID` also work. A bare Moxfield
  public ID is recognizable; ambiguous numeric IDs require the site prefix.
  Read clipboard once when the URL tab first opens in an import session, prefill
  only a matching supported URL/ID, and never fetch without pressing Import.
- Use a paste area plus **Save**, with import errors inline in a fixed reserved
  area. There is no separate review step; after save, the deck view is the review.
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

**Catalog availability clarification — D-271:** the resolved-record requirement
already follows from D-198; it is not a new deck-legality rule. D-271 permits
arbitrary client-supplied card definitions, subject to supported structure and
resource/security validation, without official-provider authenticity checks. This
does not add a card-authoring UI or permit saving an unresolved name-only import.
A new import must resolve every entry to a definition before save, but need not
have the newest entire catalog or downloaded artwork. Missing optional corner metadata
uses D-65's blank fallback, not an unknown-card error. Refresh/source resolution
may supply a missing record without discarding the input or guessing an ambiguous
match. Once definitions are retained with a selected deck/game, another device's
stale/empty general catalog cannot invalidate that accepted copy or reject an
opponent's card: receive the game's definitions from the service under the
[domain contract](domain-model.md#game-definitions-and-local-catalog-independence--d-271).
Submitting deck definitions is part of ordinary saved-deck/lobby preparation before
start, not an additional gameplay Send or host-start control. Server acceptance
retains the supplied game data; later preparation changes preserve copy isolation
and own-Ready clearing rather than silently rewriting an accepted live game.

D-289 uses one ordinary HTTP POST for the needed definition metadata, with normal
network-library handling, not an application-level chunked/resumable upload protocol.
An invalid/interrupted body cannot install a partial definition set or replace a
valid deck. This is automatic data submission, not a new user Upload/Send step.

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
start the game. This rule applies while the game is still in the lobby; D-205
settles start-versus-edit ordering below.

This supersedes the assistant's immediate-playable-table suggestion; it was not
approved. A lobby Ready control is pregame coordination, not a gameplay Send or
publication gate. Existing gameplay mutations still synchronize immediately.

D-108 still requires choosing the starter and beginning turn 1 at Untap without
automatic card operations merely from entering that step.

## Ready, persistence, and competing start changes — D-205

- **Ready requires your own selected deck.** The other player need not already
  have selected theirs for you to ready. Do not enforce format legality or deck
  size as a Ready prerequisite.
- **You can manually withdraw Ready until the game starts.** Preserve the other
  player's Ready. Existing deck/preparation and starter-selection changes still
  clear only the changing player's Ready (D-191/D-204).
- **Accepted Ready persists when you leave the lobby or disconnect.** Both players
  need not be online simultaneously; the second accepted Ready can start play
  while the first player is away. Leaving the screen/disconnecting is not a
  withdrawal or lobby cancellation. This does not authorize making Ready or other
  shared changes offline.
- **Process competing lobby changes and start in server arrival order.** If an
  edit or withdrawal arrives first while still in the lobby, apply it before
  checking readiness. A cleared Ready must be supplied again; do not start using
  that player's previous Ready. If play has already started, reject the late
  lobby edit/withdrawal without changing the started game or reopening its lobby.
- **Apply each game's start only once.** Retries recover that start and recorded
  shuffle outcomes, not a second start, new shuffle or life reset (D-200).
  A deliberately started fresh game after returning to lobby is a new start
  under D-213, not a retry of the ended game's start.

Starter defaults/access and the lobby-versus-game history/Undo boundary need
completion; D-274 resolves Random at actual start. These rules do not introduce a separate host
Start action or require online presence from both participants.

## Leaving the lobby versus cancelling the game — D-206

- **Leaving the lobby and cancelling the game are separate actions.** Leaving
  alone must not cancel the game; cancellation requires its own explicit action.
- Preserve D-205: leaving the lobby screen does not withdraw accepted Ready or
  require both players to remain online.
- **Either player can cancel an unstarted game without the other's approval
  (D-207).** This is not restricted to the inviter and does not require a
  cancellation vote or an opponent response.
- **While waiting for acceptance, the inviter uses Cancel game to cancel the
  pending invite (D-208).** Recipient Decline is different: it retains the lobby
  and permits reinvitation, rather than cancelling the game.
- **Cancelled games leave current games (D-209), with no trash/restore feature
  for cancelled lobbies (D-241).** This does not promise permanent server erasure
  or alter restoration of archived branches within a current game.
- Cancellation-versus-start contracts and cancelled-game server retention need
  completion in their proper lanes. D-207 does not define cancellation of a
  started game or its result.
  The assistant's earlier proposal that leaving an
  accepted lobby cancels the game is rejected. Its other invitation-lifecycle
  recommendations were not approved by this correction.

## Explicit active-game deletion — D-293

**Confirmed:** either participant can long press an active game, choose **Delete
game**, then confirm in an **extra dialog**. This removes the **shared game for
both players**, not just the actor's listing. The dialog names the opponent and
warns about removal for both players and no restore; Keep game/native Back dismisses
without sending a deletion. Setup still uses Cancel game under D-206/D-207.

Derived consequences of that selected scope and D-259 authority:

- Deletion is an explicit connected server lifecycle request, serialized with
  ordinary work for that game. It is not a gameplay mutation, Undo target, result
  suggestion/acceptance, score result or automatic return to a reusable lobby.
  Neither opponent consent nor a new deletion-vote feature is required.
- Bind the captured game ID and active incarnation; a stale confirmation after
  return to lobby or another start must fail normally, not delete a different
  incarnation or silently become lobby cancellation. No global-head guard is added.
- At successful processing, remove both current-game/navigation entries and any
  invitation; stop access to its table, history/branches and lobby. Open clients
  leave the deleted context for Games on learning the accepted lifecycle state.
  Previously processed actions remain ordered before deletion; later mutations,
  Ready, reinvite, result acceptance and new reads cannot revive it. A request
  ordered before deletion may finish, but cannot restore access afterward.
- Preserve the original request ID and normal uncertain-outcome/dedup recovery.
  A retry recovers the same deletion, never a new game or second destructive action.
  Already-deleted contexts do not trigger replacement creation. Report failure or
  unknown outcome honestly, not successful deletion on a timeout.
- No trash/restore, finished-session archive, gameplay Undo or physical-erasure
  guarantee is added. Required tombstone/outcome records follow existing retention;
  backup/legal erasure remains the external operator's responsibility. On receipt,
  invalidate the game's local replica/read-only access; this is not a promise to
  remotely erase knowledge or reach a disconnected device before it reconnects.

This adds a destructive removal path beside, **not in place of**, D-211's normal
cooperative result ending. Older unstarted-only cancellation statements still govern
Cancel game, not a prohibition of this separately confirmed Delete game action.

## Result suggestion, acceptance, and return to lobby — D-211

- **Suggest your win, your loss, or a draw.** The suggestion communicates a proposed
  ending to the other player; it is not an immediate unilateral end to play.
  A proposed win/loss is relative to the proposing player, so both players can
  understand the same proposed outcome.
- **The suggestion is shared, undoable board state.** Record it through the normal
  mutation/history model; Undo uses the current selective eligibility rules
  (D-224 onward).
  Undoing an unaccepted suggestion does not return the players to the lobby.
- **The suggestion never groups with another event (D-257).** It is a strict
  singleton playback group, with boundaries before and after. Preparatory taps,
  closures and random runs do not join it; neither do subsequent pings,
  tracking or ordinary edits. This is stronger isolation than a Stack anchor,
  which can still have companions. The server creates the suggestion event and
  deterministic grouping applies this rule; the UI does not define the group.
- **Show the final board before the acceptance dialog (D-257).** While catching
  up by groups, present the preceding board-state group before advancing to the
  singleton suggestion and its acceptance dialog. Receipt of a pending suggestion
  must not put that dialog over earlier unreviewed playback and hide the final
  board. Synchronization continues immediately and is not held for playback.
  A historical/removed suggestion is not a new actionable acceptance prompt;
  ordinary current-suggestion/incarnation validation still applies. This is the
  existing acceptance interaction, not an additional confirmation or review vote.
- **The other player must accept the suggestion.** The proposer cannot substitute
  their own confirmation for the other player's acceptance. Without acceptance,
  the suggestion alone does not end play or return either player to the lobby.
- **Acceptance returns both players to the lobby.** This is cooperative ending
  coordination, not scorekeeping: no win-history, standings or result-statistics
  feature is required. Ordinary board history is distinct from such a feature.
- **Acceptance is not undoable (D-212).** Once accepted, Undo cannot reverse the
  acceptance/return-to-lobby transition or reopen that ended play session. This
  exception applies to acceptance, not the pending suggestion.
- D-211 supersedes D-210's unilateral End game/confirmation-dialog/non-undoable
  design. Do not retain that blanket Undo prohibition or infer an extra confirmation
  dialog on top of proposal/acceptance. End turn, leaving a screen and cancelling
  an unstarted game remain separate actions.
- **Concurrent result actions use normal server ordering/applicability (D-239).**
  If both players submit a result suggestion concurrently, the first applicable
  request processed by the server becomes the pending suggestion. The later
  incompatible request cannot apply and is discarded; it neither replaces the
  first suggestion nor creates another pending suggestion or branch. The first
  suggestion still needs the other player's acceptance under D-211.
  Likewise, acceptance cannot apply if its suggestion has already been undone
  or the play session has already ended. Preserve the first accepted transition;
  do not retarget a failed request or retry it as a new action. Delivery retries
  recover the original outcome under D-216. This is ordinary arrival-order
  processing (D-184), not a separate replacement policy or a whole-game-head lock:
  compatible concurrent actions still apply under their normal rules (D-187).
- Finished-session history review is optional future scope, not R1 (D-214).
  D-213 defines preparation and readiness
  below. Do not infer automatic restarting, permanent history deletion or a score archive.

## Returned-lobby preparation and a fresh game — D-213

- **Restore each player's last lobby-prepared deck and mainboard/sideboard
  choices.** Use the game-local preparation used for the game that just ended,
  not the final positions/state of cards during play or an automatic refresh
  from the saved deck. Saved-deck isolation remains unchanged (D-191/D-199).
- **Clear both players' Ready when returning after accepted game end.** This
  lifecycle reset is distinct from the own-Ready clearing for lobby preparation/
  starter edits (D-191/D-204), and from merely leaving the lobby (D-205).
- **Both players must ready again before a fresh game starts.** Do not restart
  automatically on returning or carry previous readiness into the next game.
  Players can prepare/change decks in the lobby through the existing controls.
- A fresh start uses the ordinary setup rules: prepared mainboards in Library,
  automatic fresh shuffles, 20 life each, other card areas empty, turn 1 at Untap
  and no automatic opening draws. Sideboard is again unavailable during play.
  Retrying that same fresh start reuses its recorded outcomes, not another start.
- Exact starter-option retention across games remains separate specification work.
  D-274 resolves Random once at each actual fresh start if that option is selected;
  retrying that start never resamples it. Finished-session history review is deferred
  below (D-214). This does not approve
  win-history/scorekeeping, reopening ended play or deletion of its history.

## Optional future feature: finished-session history review — D-214

- **Optional/additional development, excluded from R1.** R1 does not require
  retaining finished sessions as a user-browsable archive or providing access to
  their action histories from the lobby or a later game.
- The future feature would retain finished games' action histories for read-only
  review, separate from each fresh game's own history. Reviewing an ended session
  would not reopen it or affect the current game. This is not win/loss statistics.
- **Current-game history remains in R1.** Mutation-level history, retained branches,
  Undo, exposure preservation and downloaded read-only access within that
  game are not deferred by this scope decision.
- This defers the feature, not an instruction to delete finished-session data.
  No permanent archive/retention/deletion guarantee or future delivery commitment
  is selected; storage details remain later design work.

## Initial placement and lobby-only Sideboard — D-202

- In the **lobby**, place each selected deck's **mainboard in Library** and its
  **sideboard in a separate Sideboard area**; all other card areas start empty.
  Players can prepare the mainboard/sideboard there under D-189/D-191.
- **Freely move cards between mainboard and sideboard in the lobby (D-203).**
  **D-293/D-294 select direct one/all transfer controls:** tap to the other section
  or drag to its drop target. Capture concrete source entries/copies at gesture
  start; “all” is the
  captured row's copies, not subsequently arriving copies. A cancelled drag or drop
  outside the other section sends nothing. On release, submit the existing per-card
  move requests under ordinary preparation/Ready/lifecycle guards, not per-frame
  edits or an invented atomic bulk transaction. Partial failures show actual state;
  do not substitute copies, retry with new intent or require Save/Send. Provide
  equivalent native accessibility actions; the compact count buttons replace the
  old select-row/Move-button bar, not the direct tap route.
  Allow either direction without enforcing format legality, board-size limits or
  one-for-one exchanges. This changes only the game's prepared deck and clears
  the editing player's Ready under D-191; it does not change the saved deck or
  grant access to Sideboard during play. Exact handle glyphs/drop feedback remain
  UI design; the one/all drag interaction is selected by D-293.
- When play starts, use the prepared mainboard as the Library, with the automatic
  shuffle from D-200 and no automatic opening draw (D-192).
- **Sideboard is not available at all during the game.** Do not expose an in-game
  Sideboard area, contents browser, or card/action path to view, edit or move cards
  into/from that sideboard. This is not merely a covered pile or a collapsed dock.
- This does not delete the saved deck's sideboard or merge those cards into the
  Library. The saved-deck isolation rules remain. D-213 restores the last
  lobby-prepared mainboard/sideboard when returning after an accepted ending;
  finished-session history review is optional post-R1 scope (D-214).

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
- **D-274: resolve Random at the actual game-start transition**, not when merely
  selecting/opening the option in the lobby. The server fairly chooses one of the
  two participants and records that result with initial shuffle outcomes. No extra
  client connection/confirmation is required after readiness has been accepted.
  A genuinely new start with Random selected gets a fresh result; retry/reconnect
  of the same start reuses its result, even if the repeated choice would look valid.
- Initial selection/default, edit access and option retention across games remain
  separate completion work. No pre-start reroll control is approved. Starter selection does not
  replace the existing both-player Ready gate or add a host Start action.
- **Changing the starter selection clears only the changing player's Ready
  (D-204), preserving the other player's Ready.** The changing player must ready
  again before play can start. This replaces the assistant's proposal to clear
  both players. D-205 separately approves manual withdrawal and start-versus-edit
  ordering; D-274 now settles Random-resolution timing at game start.

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
- D-274 uses server-acquired independent fresh inputs for both initial Libraries
  through the shared versioned shuffle implementation; retain both resulting
  orders with initialization and resolve Random starter in that same start.
  Readiness/start semantics and no automatic opening draw remain unchanged.

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
- Bind hand identities and pre-return count at invocation; use max(0, hand size
  minus one), displayed hand order for returns, then Shuffle of accepted current
  Library membership and guarded top-first draws under the
  [composite contract](operation-composition.md#composite-commands-and-quick-action-scope).
  Retain the ordinary D-49 available-card clamp for the N-card draw.
  No automatic mulligan-count tracking, keep-hand gate or card-text/format rules
  are implied. The preceding proposal's manual-bottoming step was not approved.
- The assistant's automatic draw-seven proposal was rejected. D-200 separately
  confirms the automatic game-start shuffle, not an automatic draw.

The ordinary Draw/Draw N, move and Shuffle actions remain available. Any agreed
compound shortcut must preserve individual mutation nodes, immediate sync and
eligible latest-own Undo under D-224/D-231; individual conflicts retain their
actual partial outcomes, not an atomic invocation or replacement draw.

### Persistent lobby and fresh play identities

Each fresh start creates a distinct play incarnation/root with fresh player
Undo/Redo stacks. Gameplay/control/exposure requests and result references retain
their original incarnation through recovery. A late session-1 End turn cannot
advance session 2, and an old committed result cannot overwrite its board. Preserve
server outcomes and surviving-memory context; D-278 permits client loss on restart,
not retargeting old work or discarding already-admitted server records.
Reinvitation also preserves each request's original attempt/outcome: retrying a
past Accept/Decline is not a response to the new invitation. This derives from
fresh-start and no-retarget/idempotency requirements, not a new duplicate-invite
policy; exact identity/fencing representation is technical work (R-12).

Each lobby-preparation round is also a distinct intent lifetime. Returning from
ended play creates a fresh preparation round even though the lobby/deck IDs are
unchanged. Ready, withdrawal, selected-deck/mainboard/sideboard/starter changes
and Cancel game bind their originating round. A first delivery from an old round
cannot change a later round or satisfy its fresh Ready requirement. A committed
old request recovers its original result without reapplying or overwriting the
returned lobby. This is lifecycle fencing, not a whole-lobby revision lock:
compatible edits within one round still use normal arrival-order applicability.
Exact generations/IDs are technical work (S-02).

## Remaining behavior decisions

- Readiness effects of any further approved setup controls. D-205 settles the
  selected-deck prerequisite without legality/size checks, manual withdrawal,
  Ready persistence without simultaneous online presence, server arrival-order
  start-versus-change handling and one-time start recovery. D-191/D-204 still
  clear only the changing player's Ready for preparation/starter changes.
- Remaining invitation/lobby lifecycle: recipient-side decline presentation,
  inviter feedback, pending duplicates, cancellation races and retention/archive
  behavior. D-209 settles no automatic expiry, cancelled games leaving current
  games and declined lobbies remaining available to the inviter. D-208 confirms
  inviter preparation/Ready before acceptance, Cancel game cancelling a pending invite,
  and the lobby surviving Decline for reinvitation. Leaving the screen or
  disconnecting does not withdraw accepted Ready (D-205); cancellation is a
  separate explicit action (D-206/D-207).
- Starter-selection defaults and edit access. D-274 resolves Random at actual
  game start using a recorded server result, not an extra pre-start roll. Additional
  areas/setup authoring beyond confirmed scope are excluded by D-241. D-201 settles
  either-player/Random starter options. D-202 settles initial mainboard/
  sideboard placement, empty other card areas and no in-game Sideboard access.
  D-200 settles automatic game-start Library shuffles and 20 starting life, with
  no pregame life setting required. Automatic opening draws are excluded.
  Mulligan counts/default, full-hand return, return → shuffle → draw
  order and Library context/turn-1 Untap quick access are settled. Remaining
  shortcut representation/feedback follows the shared composition contract.
- Lobby-versus-game history/Undo boundary. Saved-deck isolation is confirmed
  (D-191), not an unresolved persistence choice.
- Starter-option retention across games; actual Random resolution is settled by
  D-274 for every start that selects it. Concurrent result
  actions use ordinary server ordering/applicability (D-239), not a separate gap.
  Finished-session
  history review is optional future work outside R1 (D-214), not a release gap.
  D-211 confirms undoable suggestions, other-player acceptance, return
  of both players to lobby and no required win-history feature, superseding D-210.
  D-212 makes acceptance/return to lobby non-undoable, not the pending suggestion.
  D-213 restores the last lobby-prepared decks/mainboard/sideboard, clears both
  Ready and requires both to ready again for a fresh game.

This is classified remaining contract/technical/UI work, not an automatic queue
of owner questions or authorization for application code/UI drafts.
