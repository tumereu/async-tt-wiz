# Cumulative UI feedback and decisions

This is the durable owner-feedback record. Every later revision must preserve
these decisions unless the owner explicitly changes one. Append rounds; mark
supersession explicitly rather than deleting earlier direction. Historical Stitch
prompts/raw exports are provenance, not permission to restore rejected layouts.
Read this alongside the interaction notes before regenerating any screen.

**Current supersession:** playtests r1–r11 (2026-10-23–25), recorded at the end of
this file, override the earlier design-review rounds below. The round numbers
below refer to 2026-10-21 mockup review, not the later phone playtests.

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

## Round 5 — 2026-10-21 — D-296

| ID | Persistent owner direction | Scope / supersession |
| --- | --- | --- |
| F48 | Search results belong in the **same card/panel as their search field**, everywhere, including contact adding. | P02 merges field/results; saved contacts remain separate. Also applies to future already-in-scope contents/token searches; does not add search features. |
| F49 | Landscape gameplay Stack belongs **between the two player-info blocks in the side panel**. | Supersedes landscape central Stack placement; portrait right-of-libraries direction persists. |
| F50 | Card size is constant everywhere at the same zoom, including combat, battlefield, hands, reveals and special piles. | Supersedes per-area/per-row responsive shrinking. Use one scale; accommodate space with layout/pan/scroll, not miniaturized combat or other cards. Inspection is a deliberately magnified view, not another table scale. |
| F51 | Timeline icons use the originating player's color; faint vertical dividers mark **group changes**. | Preserve unique type icons, dense nodes and exact selection. Author color is not card owner; divider placement follows effective playback groups, not every event or manual grouping. |
| F52 | Library, Graveyard, Exile and Stack use a consistent **top-left circular total-count badge** when some cards are not shown. | Total, not hidden-only count. Replaces dock counts below icons and separate Stack count button. Badge does not reveal hidden faces; empty/singleton counts remain clear and accessible. |
| F53 | Player counters occupy a row beneath Library/Graveyard/Exile. In portrait show that row only when at least one counter is nonempty/nonzero. | Per-player row condition, including nonnumeric nonempty text. All-zero/empty hides the portrait row without deleting values; the life menu retains access. Landscape keeps its counter row. Narrow exception to D-58's always-visible-zero presentation, not counter semantics. |
| F54 | Tap life for a context menu containing life/player-counter controls. Drag up/down on life or a numeric player counter, show **+N / −N** preview, apply on release. | One relative edit per completed nonzero drag, not per-frame/per-step requests or an absolute stale-base Set. Menu/accessibility paths remain. Preview/cancel is local, normal server ordering/prediction starts on release. Nonnumeric arithmetic remains inapplicable. |
| F55 | History Play/Pause has text and fills portrait transport space. Landscape uses a similarly prominent central button with Previous/Next attached directly beside it, not at screen edges. | Explicit exception to icon-only history transport; other compact controls retain earlier rules. |
| F56 | Holding the transport while **paused** enters play/continuation from the current boundary; keep the top-corner continuation button too. No tooltip/tutorial text. | Hold on paused Play is the same explicit continuation action, consumes tap and never also starts playback. First successful gameplay mutation still creates the fork, not the hold itself. Explicit top/accessibility route preserves discoverability without timed hold. |
| F57 | No “Continuing from…” banner in armed continuation. It looks like ordinary live play but uses the selected historical state. | Supersedes the earlier visible intent banner, not explicit boundary capture, Cancel/Return, no-empty-fork rules or server authority. |

F01–F47 remain unless explicitly superseded above. New G07/G08/G08b show the
player menu and positive/negative drag-preview endpoints; these are inert
documentation, not implemented gestures. A uniform zoom may require board panning
in landscape; do not quietly restore area-specific shrinking to fit screenshots.

## Round 6 — 2026-10-21 — D-297

| ID | Persistent owner direction | Scope / supersession |
| --- | --- | --- |
| F58 | Put the ATW logo on the login screen too. | A01 retains the same centered bounded card in both orientations; short height scrolls. Existing hat artwork remains provisional. |
| F59 | Search-results headings are small, label-like, not another screen/section title. | All already-in-scope searches; retains F48's same-panel field/results. |
| F60 | Show readiness for each lobby player with colored status icons; Ready uses a green checkmark. | Keep a distinct non-ready/pending icon and accessible/text status. Invitation status is not readiness or online presence. |
| F61 | Reduce gaps between Hand cards and allow left/right panning when they do not fit. | Both hands; one table card scale persists. Pan is local navigation, never a card move or implicit disclosure. |
| F62 | In landscape put own Hand on the quick-actions row to save vertical space. Try opponent Hand somewhere that does not take battlefield space. | Supersedes landscape hovering-over-field geometry. This round trials covered opponent Hand in the existing top control row; that precise opponent placement is a proposal, not a final owner choice. Portrait own-Hand placement persists. |
| F63 | Combat pairs use the minimum width needed for their cards plus tiny margins; an empty insertion slot is one-card width. Always show empty drop slots next to active pairs so a drag can create a new pair. | Show insertion targets before/between/after active pairs, or one when combat is open and empty. These are UI targets, **not retained empty semantic pairs**: D-138's fully-empty-pair removal still applies. Wider sides determine pair width; no equal-width stretching or per-area card shrinking. |
| F64 | Player counters beneath libraries are circles, evenly distributed across the whole row, not left-aligned squares. | Retains all F53 portrait visibility/value/menu rules. |
| F65 | Keep the selected timeline event exactly centered; its selection square stays still while the path slides. At either endpoint the unused half stays empty. | Exact-event scrub, grouped transport and playback use the same anchoring. Do not clamp the cursor toward an edge, fabricate events or extend a path line into the empty half. H04/H05 show endpoints. |
| F66 | Add a separate game-chat button, opening a panel, with an unread bubble. | Direct entry, not hidden in overflow. Bubble represents personal unread state, not a shared read receipt/presence signal. G09 shows the panel. |
| F67 | Card actions are immediately available as grouped **square icon-only buttons**, no action-category submenus; e.g. corner/P/T and counters have direct +, − and Set. | Set is the owner's correction of the truncated “seg”. Accessible action names, current values, applicability and captured target scope remain. A value editor or target-picking interaction required to complete an action is not a category submenu. Owner choices are inline, replacing G06's separate chooser. |
| F68 | A card-action **dialog is acceptable**, with the card visibly showing its current state in a title corner because some actions keep it open. Respect outside-tap and native Back dismissal. | Latest follow-up supersedes the same-round initial preference for floating non-dialog buttons; do not treat dialog geometry as irrevocably finalized. G04/G06 trial a persistent preview with scrollable direct actions. Existing repeated-edit retention and successful Graveyard-move dismissal remain; no universal dismissal or new confirmation. |

F01–F57 persist except the specific presentation supersessions above. G06 is now
the retained action dialog after an owner/corner/counter edit, not a second-level
owner chooser; earlier source editions remain in Git. G09, G10 and H04/H05 add
chat, overflowing hands and endpoint examples. These are local inert mockups,
not implemented interaction handlers, cloud edits or a stage/gate transition.

## Round 7 — 2026-10-21 — D-298

| ID | Persistent owner direction | Scope / supersession |
| --- | --- | --- |
| F69 | Center the Hand within its display area. | Center fitting contents; overflowing contents remain pannable to both ends without unreachable negative overflow. Retains compact gaps/common scale. |
| F70 | Reserve a fixed portrait quick-action allocation, trial **four slots**, and document every situational quick action and icon. One layout example suffices. | Four is a geometry trial, not a capacity limit. The initial two-contextual/two-progression composition was superseded by the owner's F82 follow-up below: three contextual positions plus one combined progression position. Full paths remain. |
| F71 | Combat shows only faint vertical slot dividers, **no drawn empty card spaces**. Combat cards may be tapped. | Supersedes F63's drawn empty-box/+ treatment, not empty insertion targeting, narrow adaptive widths or D-138 cleanup. Preserve tapped source/projection state and full rotated envelopes. |
| F72 | Highlighting a card or projection also highlights its real card and other projections so identities are evident. | Match stable source identity at the viewed boundary, not name/owner. Local relationship emphasis is distinct from selection/command scope and does not disclose concealed faces or select every occurrence. |
| F73 | Player counters must actually span the entire library-area row in portrait; make them smaller in both orientations. | Repairs the prior capture's library-column-only alignment. Trial 28 px visible circles inside separate 48 px interaction allocations; span the full portrait middle strip, including the width beside Stack. Retain per-player hide/zero/menu rules. |
| F74 | Phase/step indicator is icon + phase color, **no phase/step text**. All steps of a phase share color; icon identifies exact step. | Keep turn number as a distinct numeric badge and accessible full meaning; no priority or game-rule inference. Provide the full phase/step legend in the review. |
| F75 | Add the missing tree-viewer mockup and explain going back/navigating other branches. | H06/H07 trial graphical review, explicit continuation versus shared activation, displaced-line return, hidden-branch filter and reversible archive/restore. Existing tree/Redo/exposure rules govern; no automatic return or archive via Undo. |
| F76 | Card actions: no title or close X; card details at top-left taking about two square positions, remaining square actions packed around them. Add a decipherable icon/grouping legend. | Supersedes card-dialog title/X geometry in F68/F28 only for this panel. Outside/native Back still dismiss. Trial a two-position vertical card preview and compact surrounding grid, not an oversized title band. Landscape keeps general actions left and field controls right. |
| F77 | Try one seven-button P/T row: current-value square **Set**, +/+, −/−, •/+, •/−, +/•, −/•. Center the unchanged-part dots attractively. | Values shrink/wrap within the same square if long, never widen it. Same one corner string and numeric-pattern applicability; no additional P/T fields. |
| F78 | Card counters use **three rows of six buttons**: two groups per row, each current-value circle/Set then + and −; divider between groups. | Replaces the separate value labels/Set icons and tall six-row layout. All six colors/absent controls remain direct; no submenus. |
| F79 | Use the same compact principle in player-value controls; title and X may remain there. | Life value/Set, +, − and paired counter triplets. Existing retention, trimming and arithmetic behavior remains. |
| F80 | Explain what the generic “Game actions” menu entry contains. | Rename the pictured entry **Table actions** and add G12/full inventory: tracking, standalone turn-player card operations, combat area, token, die/coin and ending controls. Not a general authoring/settings suite. Library/object actions keep their existing direct routes. |
| F81 | Trial a phase picker with one row per phase and horizontally arranged steps, selectable directly, plus bottom End turn (End opponent's turn when appropriate). Use active-player border on picker/indicator; trial brighter active and faded inactive board colors. | Direct phase choice preserves player/turn number; End turn changes to other player/next number. F82 replaces the former separate direct progression buttons. Border/board emphasis is turn tracking, not ownership, presence or permission; only background shades fade, not readable cards. Exact palette/brightness remains a proposal. |
| F82 | **Next step and End turn share one quick-action slot: tap Next step, hold to pass/end the turn.** | Follow-up during this round explicitly supersedes D-107's separate one-tap End turn placement and F70's initial two-slot progression plan. Recognized hold consumes tap: never advance a step before also ending the turn. Reserve three contextual positions plus this one progression position; turn-one's four contextual choices pan within three visible positions. Explicit End turn remains in phase picker/Table actions and native accessibility. “Pass” means End turn here, not a new priority/pass state. |

All earlier decisions persist except these explicit presentation supersessions.
New H06/H07/G11/G12 and the icon/catalog sections are local inert design material,
not functional tree/phase/gesture handlers or permission to implement the app.

## Round 8 — 2026-10-21 — D-299

| ID | Persistent owner direction | Scope / supersession |
| --- | --- | --- |
| F83 | A player counter without a value displays **empty**, not a dash. | Both table row and player-value menu; zero still displays 0 and absent controls remain accessible. Card-counter dash presentation is not changed by this player-specific request. |
| F84 | End turn / End opponent's turn uses player-color **fill**. | Fill identifies the tracked player whose turn is being ended, not the next player/actor. Applies to explicit buttons and the combined progression square; preserve contrast and existing transition. |
| F85 | Holding an icon-only button with **no assigned hold action** shows a short action-name tooltip—the text the button would have—not an explanation. | Tooltip recognition consumes click; no action on release, request or preview edit. Existing hold commands (Undo→Redo, progression→End turn, paused Play→continue, game-row context) take precedence and do not also show a tooltip. Normal movement/cancel dismisses. This supersedes the earlier blanket no-tooltip reading, not the ban on shortcut tutorials. |
| F86 | Compact pannable group tree. **Activation UI superseded by playtest r6b / D-311.** | Full-screen, centered on review cursor, same dense chips/icons as timeline. Tap last event and close; no Make current, legend or bottom buttons. [Current history](../../03-contracts/history-and-undo.md#36-review-progress). |
| F87 | Significance-ranked icons. **Superseded by playtest r6b / D-311.** | Use LAST event's icon/color, not significance rank; older JSON rank fixture is historical only. [Current grouping](../../03-contracts/history-grouping.md#r1-representative-group-icons--d-299). |
| F88 | Earlier withdrawal of H07 UI only. **Superseded by playtest r6b / D-311.** | Branch archiving is now removed EVERYWHERE: client, actions/state and server SQL/migration. Historical captures remain provenance. [Current history §3.8](../../03-contracts/history-and-undo.md#38-deleting-an-active-interruption). |

Earlier unsuperseded feedback remains. H06 replaces the oversized D-298 graph;
G13 supplies a short-name tooltip snapshot. Retired H07's captures and source
revision remain provenance. These are inert design artifacts, not app behavior,
a new cloud generation or permission to advance a delivery gate.

## Playtests r1–r11 — 2026-10-23–25 — D-311

These are decided behavior, not the orchestrator's implementation-status notes.
The following explicit supersessions retain earlier unrelated feedback:

- F08–F09: Settings in main menu/hamburger holds Card records, Log out,
  version/commit and playback pause; debug switching remains hamburger.
- F24: shared white grouped/divided game rows show bold name/move chip and
  “vs X · In play · <last activity>” (time today), not Created. Main menu has up to
  three active shortcuts, own-move oldest first then others newest first, no blanks.
- F41/F43/F49/F50/F61/F62: fixed HUD hands/piles/values/Stack; only battlefield+
  center band zoom/pan. Portrait Stack right-edge/mid-screen. Left panels replace
  reference trays, fewest columns up to three; shared-left/local-right max two each,
  hands shrink sideways. Three-slot camera margins, per-axis rubber-band clamps.
- F44: Change owner ONLY for tokens; hidden piles/Hand always owner destinations,
  either battlefield allowed; opponent drops/preview redirect to owner. Owner is
  distinct from controller. Only turn-player field tinted, other neutral grey.
- F54/F56/F57/F84: dominant-axis 10dp value drag, right+/left−, own edge+/opponent−,
  card controller side, anchored “+3 → 23”, no acceleration. Non-filled progression.
  Hold transport branches immediately even running; fork icon cancels. Only
  history-toggle-off after cursor movement asks Continue/Latest, outside stays in
  history; at head none, Back exits review directly. Timeline overlays with intact
  HUD above it, dim field/dark bar; Auto off/on and “Tap: toggle · Hold: branch”.
- F63/F71: packed centered combat groups, no wide insertion gaps, faint dividers,
  symmetric reorder/insertion preview; all projections UPRIGHT. Combat taps show
  Undeclare/Graveyard/Ping, double tap nothing, hold inspect, no normal menu.
- F67–F79: stationary 4×4/48dp paged card grid with tabs/swipe and 2×1 minus/value/
  plus widgets, not unpaged icon-only/seven-square/triplet/dialog layouts. Everyday
  has P/T and Library top/bottom/Graveyard/Exile; counters page P/T+six colors;
  More split edits; Move fourth page. No Move-to-pile coordinates.
- Every tap menu is immediate and uses the same opaque bordered/shadowed floating
  button/chip component, no sheet/title/Close; outside dismisses AND passes through.
  r11: gaps/empty cells also outside. Phase picker is EXEMPT and stays a dialog,
  with phase+step/player title and lane labels; trigger icon+short code/no turn N.
  Double tap 400ms/32dp, both taps press feedback; own Library double tap Draw 1;
  hold inspect 350ms. Tooltips above/below, never under finger, mutating-hold tick,
  press state on all custom buttons. Hover inspect compact/titleless/scrollable.
- Library: Draw/Reveal/Look/Mill/Exile 1 and N…, Search/Shuffle, Mulligan 7/−1.
  Remember N per action, −/+/field/confirm/select-all. ONE shared panel game-wide,
  only grey all opening controls/confirm, no error; plus ONE local searchless pile
  view/viewer. Search type/name/×N, one-card drag, closing shuffles one group.
  Look/Reveal reorder; initiator rest Top/Bottom (ordered), Shuffle/Graveyard/Exile.
  Nine-cell menu: Hand/Reveal+Hand/Battlefield/Battlefield tapped/Graveyard/Exile/
  Top/Reveal+Top/Bottom. Reveal+move is separate events AND groups. Last card leaves
  and closes in SAME event, no rest chooser; empty local view also closes.
- r11 reveal overrides r8 known-face retention: public ONLY inside revealed panel,
  hidden again on leaving for Hand/Library. Eye only for author on public Look/
  Search cards, never Reveal/Hand/piles; centered white disc over art. Library
  always back, no known marker. Semantic known edges/unknown middle are separate.
- F74/F81: turn header “Your turn · N” / “X's turn · N”; MOVE is solid green
  “Your move” plus green once-pulsed frame, else outlined “Waiting · X”, no frame.
- Stack all projections, top-only drag, uniform destination/remove/mark bubbles,
  shared multi-marks; Manage Remove/Battlefield/Graveyard/Copy/Controller, inspection
  above dialog, copy destinations disabled, NO reorder. Bubble actions only open
  next-top bubbles. Tokens cut-corner/owner-color, real rectangular, projections
  dashed; art_crop with app labels, custom whole-card image, full-image inspect.
- Deck pull-refresh only; background game loads/lifecycle interruptions silent,
  real errors floating snackbar, no resume layout bounce. Back closes overlays then
  review, bare table double Back within 2s. Tap/untap 90ms centered rotation,
  selection border rotates throughout. Quick inventory follows r3, no tap/untap/
  Graveyard selected quick controls; a multi-action invocation is one Undo move.
- Playback pauses AFTER each animation, default 1.5s, account-local Settings
  100ms–10s. Open behind head only auto-enters timeline; live updates/inspect/menu/
  resume stay live. Uncapped playback from local game/account bookmark or hidden
  empty root; Branched rollback then forward, every step incremental.
- Debug automatic crash/perf/reconnect/jank reports: no jank daily quota, at most
  3 uploads/process; server retains latest 20/account, deleting older reports.

The primary [board](../../03-contracts/board-and-controls.md),
[Stack](../../03-contracts/combat-stack-and-projections.md),
[history](../../03-contracts/history-and-undo.md) and
[runtime](../../04-technical/runtime-and-verification-profile.md) contracts define
the current requirements; old captures/HTML/PDF are historical, not contrary oracles.
