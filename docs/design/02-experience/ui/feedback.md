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
