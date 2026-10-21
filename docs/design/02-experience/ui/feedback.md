# Cumulative UI feedback and decisions

This is the durable owner-feedback record. Every later revision must preserve
these decisions unless the owner explicitly changes one. Append rounds; mark
supersession explicitly rather than deleting earlier direction. Historical Stitch
prompts/raw exports are provenance, not permission to restore rejected layouts.
Read this alongside the interaction notes before regenerating any screen.

## Earlier direction retained — D-290 / D-291

- Both portrait and landscape are required; structural review precedes final style.
- The gameplay Hand hovers below the land band, above fixed bottom controls;
  Stack/combat are central. Hold Undo invokes only Redo, also reachable explicitly
  through menu/accessibility. Timeline dragging pauses then scrubs exact events;
  Previous/Next remain grouped playback. Historical continuation does not create
  a fork until a successful mutation. Existing eligible Undo can remove the first
  action after a fork; it does not automatically restore the displaced future.
- Preserve the original source/provenance and existing behavior contracts. No
  application implementation or passed design gate is authorized.

## Round 1 — 2026-10-21 — D-292

All items below are confirmed owner feedback, not suggestions awaiting approval.

| ID | Persistent decision | Applies to |
| --- | --- | --- |
| F01 | After changes, rebuild/publish the **full** HTML document in place at `https://desktop-b2kalvm.tail27cccc.ts.net:8443/review.html`, so browser refresh continues the review. Do not move/version the URL or publish only a changed-screen extract. Verify serving; report access limitations honestly. | Every UI-document edit batch. |
| F02 | Retain feedback across arbitrarily many rounds unless explicitly changed; record supersessions and ask about genuine uncertainties. | Instructions, regeneration, review notes and captures. |
| F03 | Fullscreen: no status bar, system-navigation strip or dedicated app-title bar. | All app mockups, including gameplay. |
| F04 | App name: **Async Tabletop Wizards**. Subtitle: **Specialized tabletop simulator for asynchronous wizardry**. | Branding; no requirement to repeat branding/subtitle on every screen. |
| F05 | Portrait and landscape sign-in use the same centered, bounded login card, not a two-pane intro/form layout. | A01. Short viewports may scroll rather than shrink controls or split the card. |
| F06 | Remove unnecessary explanatory/marketing copy. In particular remove session/sign-in explanations from reset-email and new-password screens. | App-screen copy, not removal of behavior from specification notes. Keep necessary labels, errors and outcomes. |
| F07 | Post-login hamburger navigation has two clickable top-level categories: **Menu** → main menu and **Games** → games browser. Indent menu destinations under Menu and every current game (including lobby/setup states) under Games. | All authenticated screens. No presence indicators/new archive. |
| F08 | Logout is fixed at the bottom of navigation, outside its scrolling list. Landscape navigation stays visible on non-gameplay screens, not on gameplay. | Authenticated shell. Portrait/gameplay use the hamburger drawer. |
| F09 | No notification-settings link or direct Logout link in main-menu content. No redundant “two-player asynchronous…” subtitle or destination descriptions. | N01. Android notifications still use OS settings; this does not introduce a replacement in-app settings screen. |
| F10 | Combine games and lobbies in a single Games list; lobbies are setup states of games, not a separate structural list/category. Put invitations **after** Games. | P01 and navigation; retain invitation/Ready/reinvite/cancellation semantics. |

### Clarifications answered in this round

- Fullscreen removes bars, **not necessary controls**: hamburger, Back and
  gameplay phase/history remain as compact in-screen controls. System bars may
  be temporarily revealed by Android; this is not a requirement to defeat OS or
  accessibility behavior, and actual cutouts still need safe placement.
- Menu subitems are **main destinations only**: Manage decks and Contacts.
  Games has its own top-level category. Do not list import, deck-detail or
  game-context-dependent preparation screens as global menu items.
- Typographical readings: “menj” → Menu; “sane login card” → same login card.

### Superseded proposals, not superseded behavior

F03 replaces the original mock status/inset/title bars. F04 replaces the working
“Async Table” branding. F05 replaces A01's landscape intro/form split. F06 removes
the pictured reset-session helpers, not D-279/D-289's actual session rules. F07–F09
replace N01's utility links and old navigation placement. F10 replaces P01's
three-section/invitations-first composition. All D-291 gameplay direction above
and unmodified interaction contracts remain in force. Source/capture revisions
in this round are local inert design derivatives; no new cloud revision is implied.

## Round 2 — 2026-10-21 — D-293

Confirmed owner direction, including the clarification answers in this session:

| ID | Persistent decision | Applies to |
| --- | --- | --- |
| F11 | No top Back arrow on signed-out authentication screens. In-form navigation links and Android native Back suffice. | A01–A04; narrows F03's earlier general allowance for Back. |
| F12 | Put a game logo containing **ATW** above the main-menu options. A wizard hat is a suggested motif, not final approved artwork. | N01/N02; keep F09's uncluttered destinations. |
| F13 | Omit “With”/“Game with” from game-list names. Assume UI proficiency; avoid instructional filler. | Hamburger game rows and Games browser. Keep meaningful lifecycle status. |
| F14 | Give every indented navigation entry and fixed Logout a type-matching icon. Top-level Menu/Games remain text. Main-menu destinations use the same corresponding icons. | All authenticated screens; icons must actually render, not leak font ligature names. |
| F15 | Buttons almost always have a meaningful icon, with text when room permits. Space-constrained gameplay actions use icons only, retaining accessible names. | Global visual convention; does not remove state values, field labels or errors. |
| F16 | Game rows use a right chevron to open. Reinvite lives inside the existing lobby, not on the list. Long press opens a context menu: Cancel game for setup; Delete game for active play. | Games browser and navigation game entries. Explicit accessibility actions offer the same route. |
| F17 | Active-game Delete requires an extra confirmation dialog. Owner explicitly confirms it removes the **shared game for both players**, not a personal hide. | Either participant may request deletion; separate from cooperative result acceptance. See primary lifecycle contract for derived ordering/recovery safeguards. |
| F18 | Import deck is at the bottom, not the top. New import creates a new deck. Updating an existing deck goes through **Edit → Import decklist**, with **Overwrite / Merge** toggle, default **Overwrite**. | Collection, lobby picker and contextual import. Supersedes D-199's old save-new/replace selector on the generic import form. |
| F19 | Merge **adds quantities** to existing entries within each section; keep Mainboard/Sideboard separate. | Explicit clarification: 4 existing + 2 imported = 6; untouched entries remain. Atomic validation and selected-copy isolation persist. |
| F20 | Lobby mainboard/sideboard transfer is drag, not an on-screen Move button. Each row has two distinct drag handles: one copy and all copies. | L04, both directions. Native accessibility equivalent must not require a precision drag. |
| F21 | Show a small card-art thumbnail immediately after quantity in the deck inspector/list. Press it (or the row) to view full art using the gameplay-inspection presentation. | Saved/prepared deck lists and resolved import previews; lazy artwork/text fallback persists. |
| F22 | Audit **all** pictured screens for unnecessary explanation text and suggest deletions to the owner. | The packet's copy-audit table records proposals separately; do not silently apply all suggestions as approved removals. |

### Explicit supersessions and boundaries

- F11 supersedes only the authentication top-Back allowance; authenticated Back
  controls and native Back behavior remain.
- F16/F17 add active shared-game deletion; this is not a unilateral win/loss,
  return-to-lobby, archive, trash, history Undo or permanent backup-erasure promise.
  They supersede any current wording implying that only unstarted games can be
  removed. Ordinary result suggestion/acceptance remains available and unchanged.
- F18/F19 replace generic target selection and the old Re-import route; no full
  card builder or individual quantity editor is added. F20 replaces L04's
  select-row/Move-1 buttons and their two-tap budget.
- F01–F10 and D-291 remain in force except for these explicit supersessions.
  This round uses local inert derivatives; no cloud edit, application work,
  stage transition or passed UI gate is claimed.

## Round 3 — 2026-10-21 — D-294

| ID | Persistent owner direction | Applies to / explicit supersession |
| --- | --- | --- |
| F23 | Games require a name at creation; offer procedural fantasy/wizarding **adjective + thing** suggestions, e.g. **Azure Spark**, **Crackling Mist**. | New-game creation; proposed composition prefills an editable suggestion and offers reroll. Final Create/Invite, not opening the naming form, creates the game. No rename feature inferred. |
| F24 | Store a game's creation date and display it in its details beside state text. Use the game name as its title. | Browser/invitation details and lobby; game names replace opponent-only primary labels. Opponent identity remains metadata, without With/Game with filler. |
| F25 | **No app Back/home arrows anywhere**, including lobby and gameplay. | Supersedes F03's original Back allowance and F11's authentication-only scope. Android native Back and contextual exit/close actions remain, not replacement pseudo-home arrows. |
| F26 | On titled screens put hamburger and title on the **same row**. Import/editor **Exit without saving** belongs at that row's far right. Titles must remain visible in landscape too. | Compact content-control row, not restoration of a dedicated app-brand/title bar. Scroll content below the row; explicit exit is not Save. |
| F27 | Related button pairs should fill the row with equal **50/50** widths rather than occupying an arbitrary ~70%. | Save/Edit, Accept/Decline, dialog pairs and other paired actions where appropriate. Do not distort icon-only gameplay controls or force unrelated controls into pairs. |
| F28 | Dialog close X goes in the **top-right** corner, never left. | All pictured dialogs, including full-card inspection. |
| F29 | Cover selection uses the selection border, **no checkbox/checkmark** as a second selection signal. Deck-deletion explanation need only say deletion cannot be undone. | D05/D06; selected semantics remain accessible. Supersedes D06's longer copy-isolation/no-trash explanation, not copy-isolation behavior. Active-game deletion still needs its distinct both-player warning (F17). |
| F30 | Lobby title is the game name; Cancel is at the **top-right of its title row**. Reinvite goes **inside the opponent slot**, not the bottom. No Back arrow or leaving-lobby explanation beneath Ready. | L01/L02/L05/L06. Ready/Withdraw Ready remain bottom actions; leaving still preserves Ready. |
| F31 | Deck selection has no per-row Select button. Use a selection border and **one bottom confirmation**. | L03; row selection is personal draft state. Confirm alone selects/replaces the prepared copy and clears own Ready. Supersedes immediate per-row publication/return. |
| F32 | Preparation one/all drag affordances should each use about **half the old width** and a single symbol. Prefer **1 / ∞** over a separate grip if necessary. | L04: 24 px-wide markers replace 48 px-wide grip+count controls, freeing card-name space. This is an explicit compact-handle exception to the earlier per-control geometry draft; ordinary buttons remain 48 px. Native accessible one/all actions still required; no overlapping invented hit regions. |
| F33 | Remove the obsolete **empty gameplay placeholder mockup** now that gameplay is in scope. | Remove G00 from the current manifest/HTML/PDF, retain raw/history provenance outside the current packet. U01 must show a real table background, not another empty placeholder. |
| F34 | The compact **1 / ∞** controls also act as **tap buttons**, moving one/all copies to the other board; they are not drag-only. | Follow-up in the same round. Keep drag as well; one completed gesture must never produce both a tap transfer and a drag transfer. Compact sizing/scope from F32 persist. |
| F35 | Show the latest transfer as fixed, unframed text, e.g. **1× Meadow Sentinel → Sideboard**, fading over about **1–2 seconds**. No other element moves when the text appears/disappears. | Preparation feedback reserves its space even when faded; a new transfer replaces/restarts the latest message. Local visual feedback is not a new shared log or proof of server acceptance. |
| F36 | When tapping removes the last copies from a row, **quickly fade that disappearing row out and slide following rows upward into its space**. Keep transitions short but visible for clarity. | Another same-round follow-up. This intentional transfer-driven row movement is distinct from F35's feedback text, which must never shift surrounding layout. Animation does not delay requests or preserve fake game state. |

F34–F36 are subsequent owner feedback before this batch was saved; they supersede
any drag-only reading of F20/F32, not their one/all scope or drag support.
All F01–F22 persist except the specific supersessions above. Round 2's remaining
copy-audit suggestions are still suggestions unless this round explicitly selects
them. Generated-name prefilling/reroll geometry and exact date formatting are
review compositions/technical details, not claims of owner-selected final art or
an authorization to implement the application or advance a gate.

## Round 4 — 2026-10-21 — D-295

| ID | Persistent owner direction | Scope / supersession |
| --- | --- | --- |
| F37 | New-game naming is a dialog; touching/focusing its name field selects all existing text. | P05; supersedes the full-screen naming composition, not required name, suggestions or atomic Create & invite. Native selection behavior is specified, not executed by inert HTML. |
| F38 | Deck-deletion confirmation needs only one title. | D06; remove the redundant deletion eyebrow, retain deck identity and cannot-undo warning. |
| F39 | Reconnecting needs only a Back to games list button, not Review downloaded data. | U01; supersedes the proposed modal offline-review action. Does not revoke downloaded read-only access elsewhere or change recovery/unknown outcomes. |
| F40 | No visible card slots, row separators or area labels such as Battlefield. All battlefield cards/piles consume equal horizontal space, regardless of tapping or visual stacking configuration. | Logical snapping remains; render a common footprint containing the entire rotated/pile envelope, without orientation-dependent margins/reflow. |
| F41 | Portrait Stack is to the right of the libraries in the center. Use the same diagonal corner-peek pile display as elsewhere: top card plus at most two underneath, regardless of total cards/projections. | No semantic Stack capacity limit; count includes hidden entries, ordered contents remain reachable. Supersedes G02's side-by-side actual/effect cards. Ordinary visual-pile capacity rules are unchanged. |
| F42 | Try combat between the libraries, with Stack beside it. Combat slots have faint vertical dividers. | Placement remains tentative (owner: “maybe”); mock G02 trials it. Dividers apply only to paired combat slots, never battlefield rows. |
| F43 | Reveals go between each player's Library and battlefield. | G05 adds an explicit tray example; accepted content/disclosure/dismissal rules remain unchanged. |
| F44 | Opponent area uses a different color, red. Card/token borders signal owner; default owner is the participant creating/bringing the object into the game. Add Change owner to card context actions. | Owner is explicit shared metadata, not controller, zone association or editing permission. Moves do not silently change it. Live projections derive it from their source. G04/G06 show action/chooser. |
| F45 | In gameplay do not show player names or “Life” text: show a player-colored square with the life total inside. P/T is bottom-right on cards. | Accessible names still identify participant, life and owner. Lobby/contact/navigation identities remain. |
| F46 | Add card context-action, token and ping mocks. | G04/G05/G06 show context, token/ping/reveals and owner choice; not complete coverage of every menu/editor. Existing one-style numbered event-scoped pings persist. |
| F47 | Make history much denser. The node itself is a unique icon for its event type; different event types have different icons. | H01/H02; not one generic dot plus a separate label. Same event type reuses its icon, exact cursor/group/branch distinctions persist; no event truncation. |

F01–F36 persist except the explicit presentation supersessions above. “Infinite”
Stack means no gameplay item-count capacity, not infinite memory or unbounded
rendering. F42 is a trial, not a silently finalized placement. Exact shades,
icon artwork and density remain reviewable. All changes are local inert design
artifacts; no new cloud edit, implementation or stage/gate transition.

F44 explicitly adds a narrow card/token owner field/action despite D-241's older
blanket exclusion of association authoring. Controller/arbitrary associations
remain excluded. Existing ordinary card-field grouping/replay rules apply; owner
initialization is part of instance creation, not an additional automatic action.
