# Stitch generation briefs

Generation context for the [review packet](review.md), not approved product requirements.

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
