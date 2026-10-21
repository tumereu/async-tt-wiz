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
