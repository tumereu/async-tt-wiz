# Stitch generation briefs

Generation context for the [review packet](review.md), not approved product requirements.

**Historical prompts below are preserved as provenance.** For current generation
or revision, first apply the cumulative [owner feedback](feedback.md), including
D-292's name/subtitle, fullscreen, same-card sign-in, removal of reset-session
helpers, Menu/Games navigation and combined games-before-invitations browser.
Those confirmed changes supersede conflicting bars/copy/navigation below. D-292
is locally revised inert design material, not an additional cloud-generation claim.

## Common prompt

```text
Design a STRUCTURAL LOW-FIDELITY Android phone UI mockup, not a web landing page and not application implementation. This is one screen in a manual two-player asynchronous tabletop app. App working title: Async Table. Style is intentionally provisional: near-white background, gray borders, dark readable text, restrained teal only for primary actions. No gradients, marketing hero, real artwork, generated illustrations, navigation invention or decorative bottom tab bar. Use real simple line icons (Material Symbols) only when requested; text buttons must remain labeled text. Body 15-16px, title 22-24px, helper no smaller than 13px. Approximate Android dp with CSS px: margins 16px, gaps 12px, top bar 56px, controls/touch targets >=48px, simple border radius 8px. Include minimal status area 24px and bottom safe inset 16px; no device chassis.

CRITICAL: Produce ONE RESPONSIVE screen whose HTML supports BOTH portrait viewport 412 x 892 and landscape viewport 892 x 412. Portrait and landscape are equally required. At width >=700px AND height <=500px, deliberately reflow into compact side-by-side panels, reduced vertical whitespace, bounded independently scrollable content, not a stretched portrait layout. Use width:100%; max-width only on form/panels, not body. Do NOT hardcode a 412px-wide body, min-height:1000px or 100vh plus extra header. Fit the visible shell to 100dvh; scroll long contents in their intended panel. Use CSS media queries, not just desktop-sized responsive assumptions. Keep all essential actions reachable and labels visible in both sizes. We will capture this same HTML at both exact viewport sizes. Do NOT show multiple miniature screens or an orientation comparison on the screen itself. Render the portrait default preview.

Content is a proposed UI review draft. No product behavior beyond the precise brief below. Synthetic usernames/card names are examples, not real account data. Return an actual editable screen design with HTML and screenshot, not explanatory prose or a document. Screen brief:
```

## Per-screen instructions

### A01 — Sign in

Signed-out entry. App title Async Table. Email and Password explicitly labeled text fields; password has an eye icon button. Primary text button Sign in. Secondary text links Create account and Forgot password?. No guest entry, Google/social login, remember-me checkbox or user data behind this screen. One small inline example error below password: Email or password not recognised. Two-pane landscape: modest intro/app-name left; compact form right. Portrait form centered with generous margins. Do not show a software keyboard in this state.

### A02 — Create account

Registration form. Back arrow icon plus title Create account. Labeled Username, Email, Password fields, a password visibility eye icon and primary Create account text button; existing-account Sign in text link. Brief helper: Your username is visible to players. Your email stays private. No email-verification gate, social login, terms-checkbox invention or password-confirmation field. Landscape use compact two-column intro/form, preserving 48 px targets; form body can scroll vertically.

### A03 — Password reset email

Password reset request AFTER submission. Back arrow icon; title Reset password. Labeled Email field containing alex@example.test. Primary Send reset link button and inline neutral success box: If this email has an account, a reset link is on its way. Secondary Back to sign in. Small explanatory line: Existing sessions stay signed in until the password is changed. Do not claim that merely sending an email logs devices out. Centered bounded form in portrait; narrow explanatory left column and form right in landscape.

### A04 — Set a new password

Valid email-reset link landing form. Back arrow and title New password. One labeled New password input with eye icon. Primary Change password button. Small helper: Changing your password signs out existing sessions. Back to sign in text action. No email field, extra account controls, verification or mandatory confirmation dialog. Portrait compact form; two-pane landscape. This screen is app UI, not an email mockup.

### N01 — Main menu

Authenticated main menu for illustrative username alex. Top bar app name Async Table; text Signed in as alex. Three clear large icon-plus-text destinations: Play with subtitle Current games and invitations; Manage decks with subtitle Import and organise saved lists; Contacts with subtitle Find players by username. Smaller text action Android notification settings, and visibly separate Logout text action. No avatar editor, win statistics, social feed, settings suite, presence or unread-message count. In portrait use stacked destination rows/cards; landscape compact navigation panel left and destination cards right, no giant empty hero.

### P01 — Games and invitations

Play list. Back arrow and title Play; top-right New game text button. Show three clearly labeled sections. Invitations: row From riley with Accept and Decline text buttons, no expiry. Current games: row Game with morgan, Active game, Open text button, no score or presence. Lobbies: row With sam, Invitation declined, Reinvite text button and Open lobby action. Landscape two scrollable columns Invitations / Current games and lobbies. Portrait stacked sections. Do not use Finished games, win history, online dots, turn ownership or unread badges. Accept opens the same lobby; never label Accept and start.

### P02 — Contacts and new game

Contacts screen used by New game as well as main menu. Back arrow icon and title Contacts. Labeled Search usernames input with search icon and text Search button. Saved contacts list riley and morgan, each row with Invite text button. Separate Search results example sam with Add contact button, never Friend request or Accept friend. Only usernames, no emails, online/presence dots, removal controls or social permissions. Helper: Adding a contact needs no approval. Invite creates a waiting game lobby. Landscape search/results panel left, saved contacts right; portrait one vertical scrollable list. No extra selection-confirmation step before Invite.

### D01 — Saved decks

Manage decks screen. Back arrow and title Manage decks; prominent Import deck text button with paste icon. Three saved deck rows with abstract monochrome cover placeholders, names Training deck, Woodland practice, Starter list and mainboard/sideboard counts e.g. 60 / 15. Opening a row goes to read-only deck details. Small notice: Saved decks sync across your devices. No full deckbuilder, individual quantity edit, card search-to-add, format legality badge, Export, trash folder or win rates. Portrait stacked rows; landscape restrained two-column deck grid. Counts are descriptive, not legality.

### D02 — Saved deck details

Read-only saved deck detail for Training deck. Back arrow and title Deck details. Labeled deck name text with adjacent Rename text action; abstract card-cover tile and Choose cover button. Explicit Re-import button and secondary Delete saved deck button. Mainboard 60 / Sideboard 15 tabs display quantity/name rows (synthetic names: 4 Meadow Sentinel, 4 Ember Adept, 22 Training Land) with no +/- quantity editors or Add card action. Helper: Editing or deleting this saved deck will not change copies already chosen in games. Portrait metadata above scrolling list; landscape metadata/actions left and read-only list right. No undo/trash/version history controls.

### D03 — Import with unresolved names

Import deck screen in ERROR state. Back arrow and title Import deck. Multiline text area labeled Deck list showing exactly: Deck / 4 Meadow Sentinel / 4 Unknownn Card / 22 Training Land / Sideboard / 2 Ember Adept. These are synthetic illustrative catalog entries. Error box: Line 3: Unknownn Card could not be resolved. Correct the pasted text; nothing has been saved. Primary Review import visibly disabled; text Retry lookup optionally available as secondary, not mandatory catalog refresh. Keep full input visible/editable. No partial save, silent substitution, format rules, manual card-add editor or Upload step. Portrait textarea above error; landscape textarea left, issues/actions right. The text area must not push the error/action completely off viewport.

### D04 — Resolved import and save choice

Import review after all names resolve. Back arrow and title Review import. Summary Resolved: 60 mainboard / 15 sideboard. Labeled Deck name field Training deck. Compact read-only list of synthetic card names. Two explicit save modes: Save as new selected; Replace saved deck unselected with existing deck picker Training deck. One primary Save deck text button, secondary Edit pasted list. Helper: Existing game copies stay unchanged. Both save routes require a completely resolved list. No format-legality approval, art download requirement, manual quantity edits, extra Send/Upload or forced cover-picking step. Portrait summary then modes; landscape list left and name/save mode panel right.

### D05 — Choose a deck cover

Cover selection for Training deck. Back arrow and title Choose cover. Helper Choose one card from this deck. Grid of six simple grayscale mock card thumbnails with readable synthetic card names Meadow Sentinel, Ember Adept, Training Land, River Warden, Stone Guide, Quiet Grove. One tile outlined and checkmarked as selected. Primary Use selected card button, secondary Cancel. Do not fetch real artwork, use generated artwork, offer upload/crop, select outside the deck, or imply cover changes edit cards. Portrait 2-column grid, landscape grid left plus small preview/action panel right. Visible text fallback if art is not loaded.

### D06 — Delete saved deck confirmation

Modal confirmation over a dimmed Manage decks screen, showing Training deck behind it. Dialog title Delete saved deck? Body: Training deck will leave your saved collection. Copies already selected in lobbies and games stay unchanged. This cannot be restored from a trash folder. Two explicit text buttons Keep deck (secondary) and Delete saved deck (destructive). Standard close/back cancels without deletion. No Undo restoration or impact on active game. Portrait centered dialog max width 360 px; landscape centered dialog about 440 px wide, enough room for body and 48 px actions. No gameplay board visible.

### L01 — Lobby awaiting invitation acceptance

Inviter's waiting lobby. Back arrow and title Lobby with riley. Status Waiting for riley to accept. Opponent panel riley / Invitation pending. Own panel alex / Training deck selected / Mainboard 60 / Sideboard 15; Change deck and Prepare deck text actions. Starting player choice three buttons alex / riley / Random with Random selected as EXAMPLE not fixed product default. Ready primary enabled because own deck selected, even before opponent accepts. Separate Cancel game text action; helper Leaving this screen keeps the lobby. No host Start, expiry countdown, acceptance on behalf of opponent, life editor or auto draw. Portrait panels stacked; landscape own preparation left, opponent/status/starter right, action footer.

### L02 — Accepted lobby before own deck selection

Accepted lobby for recipient alex with riley. Back arrow and title Lobby with riley. Own panel alex / No deck selected, large Choose deck text button. Opponent riley / Ready with check icon and text (not color alone). Starting player options alex / riley / Random, Random selected only illustrative. Own Ready button disabled with explicit helper Select your deck to get ready. Separate Cancel game text action. Helper Accepting the invitation did not ready you. No opponent-deck-size validation, host Start, cancel by leaving, online requirement or auto card draw. Portrait stack; landscape own prep area left, opponent/setup right with visible footer.

### L03 — Select a game-local deck copy

Choose deck screen opened from lobby. Back arrow and title Choose deck. List saved Training deck, Woodland practice, Starter list with abstract cover blocks and mainboard/sideboard counts; each row has explicit Select text button. Helper Selecting makes a copy for this game. Saved decks stay unchanged. Small Import deck text action if collection needs one; do not force import if existing list. No format-legality checks, deck size gate, extra Send confirmation or automatic Ready. Portrait rows; landscape two-column grid of rows. Choice immediately returns to lobby with selected copy and clears only own Ready.

### L04 — Lobby mainboard and sideboard preparation

Lobby deck preparation. Back arrow and title Prepare deck; subtitle Game copy — Training deck. Separate labeled Mainboard / Library (60) and Sideboard (15) lists. Synthetic quantity/name rows. Select one Meadow Sentinel row on Mainboard; explicit Move 1 to sideboard text button. On Sideboard show selected Ember Adept row with Move 1 to mainboard button. These are direct per-card quantity-one moves, not saved deck quantity editors. Small note Changes sync automatically and clear only your Ready. No Save preparation / Done to send / one-for-one restriction / deck size legality / live-game Sideboard. Back returns to lobby without cancelling or undoing changes. Portrait two stacked panels with vertical scroll; landscape two equal independently scrollable columns. Minimum 48 px action targets.

### L05 — Lobby with own Ready accepted

Lobby with riley after own Ready accepted. Back arrow and title Lobby with riley. Own alex / Ready with check icon and text; Training deck / 60 mainboard / 15 sideboard; Change deck and Prepare deck still available. Opponent riley / Not ready. Starting player choices alex / riley / Random, Random selected illustrative. Primary Withdraw Ready text button. Helper Your Ready stays set when you leave. The game starts automatically when both players are ready. Separate Cancel game text action. No host Start button, both-online gate, auto opening draw, timer, stats or read receipt. Portrait stacked panels; landscape own prep left, opponent/setup right; keep footer controls in viewport.

### L06 — Cancel unstarted game confirmation

Modal proposal over dimmed waiting lobby with riley. Dialog title Cancel this game? Body: This removes the unstarted game and its pending invitation from current games. Leaving the lobby normally does not cancel it. Text buttons Keep game and Cancel game, latter destructive. No opponent approval, restore/trash, cancelled-game archive, unilateral ending of started play or delete player data. Standard Back/close keeps game. Portrait centered dialog max width 360 px, landscape about 460 px; 48 px button targets. Background shows only non-gameplay lobby, no board.

### G00 — Gameplay intentionally omitted

Placeholder ONLY, not a gameplay UI design. Back arrow icon and title Game with riley. Most screen is a large empty light-gray outlined rectangle with centered text Gameplay area intentionally omitted from this pilot. Small caption Portrait and landscape gameplay layouts will be designed separately. No cards, zones, hands, life counters, timeline, combat, Stack, action controls, chat, gameplay sideboard or implied final board geometry. Portrait rectangle fills available tall area; landscape rectangle fills available wide area. Keep orientation label outside the rectangle in small text.

### U01 — Reconnecting and downloaded read-only review

Game shell with EMPTY gray gameplay placeholder only, all board design omitted. Header Game with riley. Centered modal titled Reconnecting… with simple status icon and text Game actions are unavailable while disconnected. Previously sent actions will recover automatically. Text button Review downloaded data and secondary Back to games. Small read-only label on background. Review action closes modal into read-only navigation; it does NOT enable actions, Ready, Undo, activation or new hidden-information inspection, and it does not manually resend unknown requests. No cards, game controls or actual board geometry. Portrait and landscape adapt dialog max width 360/460 px and retain 48 px actions. This is proposed presentation of the existing offline behavior, not a new recovery center.

## Export normalization

The manifest records screen-specific corrections. Exporting removed executable scripts, event handlers and external artwork, froze styles/fonts locally, normalized trailing whitespace and enforced 48px action/field targets in both orientations. A generated turn-based marketing phrase was corrected where present; the app does not enforce a turn-bound action model. Source copies ending in `.stitch.html.txt` retain the service output (apart from whitespace), and `.html` files are inert normalized review sources. Raw source copies are text, not runnable pages.

## Gameplay extension common prompt — D-291

```text
Create one STATIC RESPONSIVE gameplay screen design in the existing Async Table project, consistent with its low-fidelity gray/teal Android UI. This is design material, NOT application implementation. No real artwork, no external images, no gradients. Use synthetic abstract card rectangles, readable control labels, Material Symbols only for icons. Produce HTML and an editable Stitch screen.
Support BOTH 412x892 portrait AND 892x412 landscape in the SAME HTML using explicit media queries. Body 100dvh, width 100%, overflow hidden, no hardcoded portrait body width. Minimal system inset 20px; compact top bar 48px; bottom safe inset 12px. Actual buttons >=48x48px; compact card thumbnails are zoomable board objects, not tiny toolbar buttons. Fit shell; allow board viewport to pan/scroll if necessary, not page-level scrolling. Do not stretch a portrait screenshot. Structural layout is more important than style.

BOARD: own player alex at bottom; opponent riley above. For each player there is ONE generic battlefield region visually split into a DARKER muted green OUTER band with ONE card row mainly for lands and a LIGHTER muted green INNER band with TWO card rows for other permanents. Thus top-to-bottom: riley dark single row, riley light two rows, center lanes, alex light two rows, alex dark single row. Indicate all six rows subtly even when sparsely occupied. Crossing green colors is NOT a semantic zone move or card-type restriction. Both sides retain matching horizontal alignment; never reverse x positions, mirror text or turn opponent faces upside down. Tapped cards alone rotate clockwise 90 degrees. Use few synthetic cards and spare slots; thumbnails can have abbreviated labels; inspection provides readable faces later.

SPECIAL CONTROLS: landscape LEFT sidebar with BOTH players' labeled life and Library/Graveyard/Exile counts; never put opponent controls on right. In portrait these become a central strip BETWEEN battlefields, with riley/alex associations explicit. Counts visible at zero. Life 20 each, Library 50 each, Graveyard 2 each, Exile 0 each are illustrative only. Landscape sidebar can use three labeled compact 48px-high rows per player, each spanning available width. Portrait two participant rows, three zone buttons each and life label; do NOT put all six controls in one overflowing row. The shared STACK lane is between battlefields, with a separate two-row COMBAT lane only when open. Blockers above attackers. These are NOT the real battlefield cards moving to combat; use dashed borders for projection occurrences.

HAND AND CHROME: own face-up hand is a hovering compact fan/row fixed to screen just ABOVE bottom controls, below own dark land band in initial framing; it does not move when board pans. In landscape reserve compact hand height so controls remain reachable. Opponent covered hand is a compact row at TOP, outside the battlefield, with a count and no leaked names. This top-hand location is a draft assumption. Cards, Stack and combat remain inside the board viewport; hands, top bar, special-zone dock and bottom controls are separate fixed UI. Avoid large headers/branding. No online presence, priority, legal actions, game engine rules, mana system, Sideboard, score or mandatory Send. Fixed chrome and board are distinct.

LIVE TOP: Back icon, small phase square labeled Main 1 (or specified step), History text button, overflow icon. Phase tap opens tracking controls, never silently advances; no phase enforcement. LIVE BOTTOM: Undo icon/text at far LEFT with concise hold hint (hold=Redo, not Undo+Redo); contextual quick action center if specified; mode square at RIGHT labeled Normal or Select. Mode tap opens Normal/Select/Ping chooser, NOT an unlabeled mystery cycle. Explicit Redo exists in overflow and accessibility, no separate Redo button unless showing overflow. Chat is reachable via overflow.

HISTORY: same board geometry at the selected historical boundary and ongoing sync, but NO gameplay controls, Undo, quick actions, mode editing, live phase editor or hand edit controls. Top phase is read-only. Bottom has Previous LEFT, Play/Pause CENTER, Next RIGHT; those step automatic groups, NOT Undo/Redo. A left-to-right horizontal timeline immediately ABOVE transport has individual event dots, exact selected event marker, small diagonal off-path stubs, not expanded tree. Scrubbing selects exact events; touching timeline during playback pauses then scrubs. Include explicit Continue playing from here action separate from Play/Pause and Back to live review navigation. No fork until subsequent accepted gameplay mutation. Personal review is NOT branch activation.

Return ONE screen for the state below, no explanatory document or comparisons. Do not include product features not requested. STATE:
```

### G01 — Live table and fixed hand

Live normal mode, combat CLOSED, Stack empty but labeled. Sparse cards show dark one-row and light two-row arrangement. alex hand three face-up synthetic cards (Meadow, River, Guide), riley covered hand 3. Main 1 phase. Bottom center can show a neutral Actions menu button (not an invented phase-specific shortcut). History and overflow visible. No timeline in live mode.

### G02 — Live combat and populated Stack

Live Select mode in Declare attackers. Combat OPEN: a clearly paired dashed blocker occurrence above attacker occurrence, sources still visible in green battlefield. Shared Stack has one actual card Ember and one dashed effect projection Guide; distinguish real vs projection. Bottom center quick actions Declare + tap and Without tap, horizontally scrollable within their bounded toolbar area if needed. Show selection count 2. Landscape may need vertical board navigation rather than clipping controls; no hidden extra button below page. alex floating hand still visible.

### H01 — Historical review paused at an exact event

Historical mode PAUSED. Exact selected event X is INSIDE a playback group; timeline labels A, X, Y, Z, selected X outlined with vertical marker. Small diagonal branch stub at A indicates retained old line; no expanded tree. Top read-only Main 1; label Reviewing X. Footer Previous / Play / Next, timeline directly above, Continue playing from here separate and visible. No Undo, mode chooser, quick actions or gameplay editing. Hand and zones are historical read-only content.

### H02 — Grouped playback running

Historical mode PLAYING. Footer Previous / Pause / Next. Timeline has selected Y marker and branch stub at A; concise status Playing grouped events and hint Drag timeline to pause. Continue playing from here remains separate; it pauses at the exact displayed selected boundary before arming gameplay, never forks on press. Top Back to live is personal navigation. No gameplay action controls or editable phase.

### H03 — Historical continuation armed before first action

Gameplay mode armed after Continue playing from here at historical event A, but NO action yet, so NO new branch exists. Show compact conspicuous banner Continuing from A · no action yet with Cancel text button. Original displaced future is retained, no actual fork created on entering. Live controls Undo/Actions/Normal return, but do not imply Undo is relative to review cursor: actual Undo eligibility/target still use shared current history. Include Return to live text navigation. All new gameplay actions bind explicit historical intent at A; first successful mutation creates/activates continuation. No timeline transport. Keep fixed floating own hand.

### G03 — Explicit Redo and mode chooser routes

Live table with top-right overflow OPEN as bounded popover with explicit labeled Redo, Chat, Game actions, Close menu, each >=48px tall; no giant full-page settings. Include explanatory small footer hint Hold Undo for Redo. Bottom-right mode square labeled Normal. Do NOT show mode chooser simultaneously; the document will explain its three mutually exclusive choices Normal/Select/Ping. Actual background remains the live table, not placeholder. Redo and Undo are server requests; do not assert unconditional eligibility. Keep all popover entries reachable in landscape, scrolling popover if needed.

The original G00/U01 exports above are retained as pilot provenance, not the current gameplay composition. Gameplay review derivatives are substantially recomposed locally using G01's generated gray/teal card-and-chrome vocabulary, not pixel-identical cloud exports. Repairs expose both battlefield halves and fixed history controls, remove invented actions, harmonize the six states and keep historical controls read-only. H01/H02 depict sparse closed-combat boundaries; G02 depicts populated combat. Raw Stitch output remains separately retained; the manifest records original screen IDs and full repair/derivation notes. Both orientations are recaptured from the inert derivatives with HTTP(S) blocked.

## Corrected editable cloud revisions

Each of the six gameplay states was subsequently sent to Stitch `edit_screens`
with its complete repaired reference HTML and the shared `gameplay-review.css`
inlined. Instructions required faithful state/label/layout correction at both
viewports, all six battlefield rows, screen-fixed own Hand, central lanes,
48 px controls, read-only historical chrome, distinct continuation intent and
no invented Pass/concession/automatic-resolution behavior. This is design
revision, not application implementation. The manifest's `stitchRevisionScreen`
and `revisionOriginalSource` retain each returned cloud revision and its raw text.
These revisions are editable in the existing project; validated packet captures
still come from the local inert derivatives, not an unverified pixel-parity claim.
