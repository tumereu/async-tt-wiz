# Lobby, accounts and app

Everything outside the game table: accounts, profile, navigation, game list, game setup, decks, chat, notifications, settings, updates and downloads.

### Accounts and sign-in

- **LOB-001** Create an account with username, email and password; the form says "Your username is visible to players." and "Your email stays private." Other players never see your email. (r1)
- **LOB-002** Sign in with email and password; the password field has a show/hide eye button. (r1)
- **LOB-003** "Forgot password?" sends a reset link by email ("If that email is registered, a reset link will be sent."); the link opens the app to set a new password, and a bad or used link says so and offers "Request another link". (r1)
- **LOB-004** You stay signed in across restarts with no time limit; only a rejected session asks you to sign in again ("Your session is no longer valid. Please sign in again."). (r1)
- **LOB-005** While a saved login is checked the start screen shows "Checking saved login…"; if that fails it offers Retry and Logout. (r1)
- **LOB-006** The same account works on several devices: saved decks, contacts and games are the same everywhere. (r1)
- **LOB-007** Log out (Settings, red row) signs out this device at once and removes that account's private downloaded data from the phone. (r3)
- **LOB-008** The app reopens on the section you last used for that account. (r1)
- **LOB-009** Test builds have "Switch account" and "Add account" at the bottom of the side menu; normal releases do not. (r3)

### Profile and avatars

- **LOB-010** An avatar is a card's art crop in a circle; without one, a coloured circle with your username's first letter is shown. Other players see your avatar even if the card is not in their catalog. (r17)
- **LOB-011** After the first sign-in (once per account without an avatar) "Choose your avatar" shows 6 random legendary creatures or planeswalkers, a dice reroll button, a "Search by card name" field and Skip. (r17)
- **LOB-012** While the card records are not ready, the avatar choices area says "Avatars need the card catalog." with the phase and percentage, and the choices appear by themselves when ready. (r17)
- **LOB-013** Settings → Profile shows your avatar and name with a pen button that opens one "Edit profile" dialog: smaller avatar choices without scrolling, search above them, reroll, username field, Cancel and Save (no Skip). (r18)
- **LOB-014** Avatars appear next to player names everywhere: game rows, game lobby, contacts and search, invitations, pod rows and the side menu. (r17)

### Navigation and main menu

- **LOB-015** The main menu has big buttons for Games, Pods, Stats, Manage decks, Contacts and Settings; in portrait the ATW logo sits small at the top right of the menu-button row. (r58)
- **LOB-016** Under the Games button a panel lists up to 3 games in the shared game order (LOB-022), opening straight into the game; incoming invitations there have Accept and Decline buttons. The panel is hidden when empty. (r30)
- **LOB-017** Under the Pods button a panel lists up to 3 pods and pod invitations in the shared pod order (DRF-013), invitations with Accept/Decline. Hidden when empty. (r35)
- **LOB-018** The side menu lists Menu, Stats, Manage decks, Contacts, Settings, then Games with every game and incoming invitation as rows, then Pods with each pod's other members as a compact avatar stack. Landscape shows it as a permanent side area; portrait opens it from the hamburger. (r58)
- **LOB-019** A side-menu game row shows the opponent's avatar, the game name, an "Invitation" label on its own line for invitations, and "opponent · status · last activity" (the time if today, else the date) on two lines. (r35)
- **LOB-020** Back from any section opened from the menu or side menu returns to the main menu, and Back again leaves the app; steps inside a section (Games → Choose opponent) go back one step. (r30)
- **LOB-021** Tapping a side-menu game opens it; tapping an incoming invitation opens the Games screen to answer it. (r30)

### Game list and rows

- **LOB-022** One game order everywhere (Games screen, menu panel, side menu): games where it is your move (TRN-058; longest waiting first), then invitations to you (newest first), then games waiting for your opponent and invitations you sent (newest first), then the rest. Pods follow the same shape (DRF-013). (r50)
- **LOB-023** The Games screen shows one "GAMES" card with a count and all games and invitations in one list; "No games yet." when empty. A "New game" button sits at the top right. (r30)
- **LOB-024** A game row: opponent avatar, bold game name, a move chip ("Your move" filled, "<name>'s move" plain, or "Invitation"/"Setup"; the move of TRN-058), the pod name line for pod games, then "vs <opponent> · <status> · <last activity>", the time if today, else the date, from your local head and next stop (HIS-032). (r50)
- **LOB-025** Row status texts: "In play", "Invitation pending", "Invitation declined", "Preparing"; a held pod challenge reads "Challenged you · submit your deck to accept" (Accept greyed) for you and "Waiting for <name>'s deck" for the challenger. (r30)
- **LOB-026** Holding an unstarted game's row offers "Cancel game"; holding a game in play offers "Delete game", confirmed by a dialog warning that it removes the game for both players with no restore ("Keep game" / "Delete game"); its match in play is kept as an Unfinished record (LOB-127). (r58)
- **LOB-027** Background loading of games never shows a spinner or message; only real errors appear, as a floating message at the bottom. (r3)

### Contacts and new games

- **LOB-028** Contacts and Choose opponent share one screen with two tabs: Contacts (default, with a local "Filter contacts" field) and Search (search usernames). Empty results say "No players found"; loading shows a spinner. (r17)
- **LOB-029** Contacts tab rows have a remove button (Contacts screen) or an invite button (Choose opponent); Search rows have icon-only Add contact and, when choosing an opponent, Invite. Inviting someone also adds them as a contact. (r15)
- **LOB-030** Removing a contact asks "Remove <name>?" with "Existing games and invitations will not change." (r15)
- **LOB-031** "New game" opens Choose opponent; picking a player opens the "New game" dialog ("Invite <name>") with an editable random fantasy game name (like "Azure Spark"), a reroll button, Cancel and Create; every game is played with Automatum (SYS-055). (r45)
- **LOB-032** Adding a contact is one-sided: no friend request, and it never accepts anything for the other player. (r1)

### Game lobby

- **LOB-033** For the inviter the new game is already a lobby: they can choose and prepare a deck and press Ready before the invitation is answered. (r1)
- **LOB-034** The recipient answers with Accept or Decline. Accept opens the same lobby (not Ready). Decline keeps the inviter's lobby, which shows a "Reinvite" button. Invitations never expire. (r1)
- **LOB-035** The lobby screen shows the game name and two tabs, Lobby and History (LOB-129). Lobby shows "<status> · Created <date>", your card (avatar, Ready/Not ready, deck cover, deck name, "N main / M side", Choose/Change deck, Prepare deck), the opponent's card and the Starting player card; landscape puts them side by side. (r58)
- **LOB-036** The game starts when both players are Ready; Ready stays on when you leave or go offline. A full-width Ready button turns into "Withdraw Ready"; a line above it says why Ready is not possible ("Select your deck to get ready."). (r1)
- **LOB-037** Changing your deck, its preparation or the starting player clears only your own Ready. (r1)
- **LOB-038** Starting player offers Random or either player; an "Alternate" switch at its top right (on by default) makes the player who did not start the last game start the next one. Random is decided when the game starts. (r28)
- **LOB-039** "Cancel game" (header button or row hold) removes an unstarted game and its pending invitation: the inviter can use it anytime in setup, the recipient after accepting. (r1)
- **LOB-040** Choose deck lists your saved decks, then your draft decks under a "Draft decks" heading, with cover and "N main / M side"; it has pull-to-refresh, "Import deck" and "Use selected deck". (r31)
- **LOB-041** Importing a deck from Choose deck returns you to Choose deck with the new deck selected. (r21)
- **LOB-042** The opponent's deck shows as "Deck" with a placeholder picture, unless they chose to show it; your own pod deck's private name is visible only to you. (r30)
- **LOB-043** Your card has a "Show deck name & art" switch for this game, starting from the Deck visibility setting (LOB-079). Pressing it at all makes it your own choice for that game that nothing overrides; picking another deck resets it. (r34)
- **LOB-044** With "Show after first game", your deck is shown to an opponent once you finish a game with that deck against them, also in the rematch and in other lobbies already waiting with that deck; games in play are not changed. (r34)
- **LOB-046** After an accepted game result both players return to the same lobby with their last prepared decks restored and both Ready cleared. (r1)

### Deck preparation for a game

- **LOB-047** "Prepare deck" opens one scrolling page with green Mainboard · N and blue Sideboard · N headers, cards as pictures grouped by type in the shared order (SYS-051; empty groups left out, cards sorted by name) and ×N badges for copies. A deck's Maybe cards are not shown there and never reach a game. (r35)
- **LOB-048** Swipe a card sideways (20 dp) to move one copy to the other section; hold to inspect it; tap for "Move one to…" and "Move all N to…". (r52)
- **LOB-049** A round floating Undo button at the bottom left, shown only when there is something to undo, takes back moved cards one at a time. (r55)
- **LOB-050** A short fading line under the title says what you last moved ("2× Opt → Sideboard"); while disconnected it reads "Disconnected · preparation is read-only". (r21)
- **LOB-051** The prepare screen and saved deck view have a columns control (1–10, 3 by default), remembered per screen, device and orientation. (r31)
- **LOB-052** In pod games the basic lands are cards like any other: the ones added while building (and an unlimited sideboard's Side basics, DRF-104) show as tiles and move between Main and Side as other cards do (no basic land steppers); Ready is blocked with a reason if Main has fewer than 40 cards including basics or Side is over the pod's sideboard limit. (r55)
- **LOB-053** Lobby deck changes never change the saved deck, and saved deck edits or deletion never change a deck already chosen in a lobby or game. (r1)

### Deck library

- **LOB-054** Manage decks has Decks and Cubes tabs and an "Import deck" button at the top right; the Decks tab shows "SAVED LISTS (N)" with cover, name and "N main / M side", and pull-to-refresh only (no background refresh). (r21)
- **LOB-055** Every pod deck you submit is kept as a draft deck in its own section under Decks ("DRAFT DECKS (N / 20)"; only the newest 20 are kept). A draft deck is read-only and playable in any game (under "Draft decks" in the deck picker); its Side holds the rest of your pool, Trash included, with no sideboard limit. "Save as deck" copies it into an ordinary deck you can edit and keep for good. (r31)
- **LOB-056** A saved deck or cube opens in the same read-only picture view (DRF-010): sections Main, Side (decks) and Maybe in their colours (SYS-054), cards grouped by type in the shared order (SYS-051), ×N badges, holding a card inspects it (a tap does nothing), and a columns control remembered per screen and orientation (LOB-051). Maybe shows only when it holds cards. (r35)
- **LOB-057** Edit is the only button above a saved deck (a cube also has Draft this cube). The editor (LOB-059) has a red Delete deck / Delete cube at its top right, confirmed with "Deletion cannot be undone." ("Delete deck?" naming the deck, Keep deck / Delete deck; "Delete cube?", Keep cube / Delete cube), with Import from link beside it (its icon only in portrait). A draft deck shows Delete draft deck beside Save as deck (its title shortened to "Draft…" in portrait) and a note about where it was drafted. (r35)
- **LOB-058** Each deck has a cover chosen from its own cards. (r1)
- **LOB-112** Once per account, in the background after sign-in with card records installed, saved decks and cubes with cards saved before card data kept split, adventure and flip halves or a face's colour indicator get those cards' data rebuilt from the card records, as an import would. Names, sections, quantities and covers stay; other lists and draft decks are untouched; a card the records don't have stays as it was; nothing is deleted. A failed run is retried on a later return to the app. (r36)
- **LOB-115** A deck Automatum can't play fully by the rules shows an amber warning icon wherever it is listed or chosen (Manage decks' Decks tab, draft decks too; the saved deck view; the deck editor, for the list being edited; Choose deck; your own deck in a game lobby); holding it names the cards ("Automatum can't play 3 cards yet: A, B, C", at most 12 names then "and N more"): those whose rules text Automatum can't do every part of, worked out on the phone from the cards themselves. (r57)
- **LOB-124** A deck's Automatum result (LOB-115) is worked out when it is saved and kept with it on the server; a deck without a current one gets it in the background when you return to the app (at most every 15 minutes) and shows no icon until then. (r49)
- **LOB-118** A deck with LOB-115's warning can't start a game: Choose deck's "Use selected deck" (with it highlighted) and a lobby's Ready (with it chosen) are greyed with "Automatum can't play every card of this deck yet. Hold the warning sign to see which." ("Checking which cards Automatum can play…" until the phone knows). The server refuses choosing, readying or starting with a deck whose kept result names cards, and a start when any card's data can't be read. A step Automatum can't do while playing is skipped (STK-030). (r49)
- **LOB-117** A deck that ventures into the dungeon or takes the initiative plays it by the rules: with the Ready the phone sends the dungeons of its card records that Automatum plays fully (each once; a printing with a reminder back as its dungeon side only; any with a room Automatum can't do left out), which start outside the game as that player's own, never part of the deck. A plain venture chooses among the dungeons other than the Undercity, which only "venture into Undercity" and the initiative enter. (r50)
- **LOB-123** A card that ventures or takes the initiative with no playable dungeon for it counts as a card Automatum can't play (LOB-115), and the server refuses a Ready missing the dungeons its deck needs. Dungeons aren't companions: they never make the game ask about revealing one. (r50)

### Deck and cube editor

- **LOB-059** Decks and cubes share one editor: the name (a deck also its cover, chosen by tapping it once the list resolves), Cards and Text tabs, and a full-width Save at the bottom; existing lists open on Cards, new ones on Text. The Text tab is the raw list (full width, no counts, a paste icon, Deck / Sideboard / Maybeboard headers); problem lines are underlined and red, with their messages and substitutions in a fixed 96 dp area under it and a red error icon by the tab's label while problems remain. (r35)
- **LOB-060** The list is looked up about 0.4 s after you stop typing; Save works only for a named, fully resolved list. Unknown or ambiguous names are flagged with line numbers and never replaced silently. (r21)
- **LOB-061** "Import from link" in the top bar (icon only on narrow phones) opens a dialog for Archidekt links or `archidekt:ID`; a matching link on the clipboard is prefilled, the paste icon pastes and imports at once, typed text waits for Import. An import replaces the list. (r35)
- **LOB-062** Common list formats work: "4 Name", "4x", optional "(SET) number", Deck/Main/Sideboard/Commander/Companion headers (also with # or // and counts), "SB:", a blank line before the sideboard, comments, Forge, XMage and CSV exports; Maybeboard sections (and Archidekt's maybeboard) go to Maybe. (r35)
- **LOB-063** A deck holds at most 1000 cards, counting every section. (r35)
- **LOB-064** Changes in either tab are kept only by Save. Saving a new import from Manage decks opens its saved deck view; leaving a deck or cube editor with unsaved changes asks "Discard unsaved changes?" (Keep editing / Discard). (r35)
- **LOB-105** The Cards tab shows the list as pictures by section (decks Main / Side / Maybe, cubes Main / Maybe), empty sections too, grouped in the shared type order (SYS-051) with ×N badges. Swipe a card sideways to move one copy to the next section (LOB-048); tap it for +1, −1, Remove all and Move to…; hold to inspect (SYS-049). Each section header ends in a + in its section's colour that opens search with that section chosen. (r35)
- **LOB-120** A round floating Undo button at the bottom left of the Cards tab (not on the search page), shown only when there is something to undo, takes back Cards tab and search changes one at a time (as in preparation, LOB-049), up to 50, bringing back a cover the change removed; it starts over after a Text tab edit or a link import. (r35)
- **LOB-106** A section's + opens a full-screen search page: the field and quick filter chips (LOB-119) on top, results as pictures, and at the bottom a Main | Side | Maybe group (cubes Main | Maybe) choosing where added cards go, then "Done · +N cards". Tap a result to add one copy there (its square counts up with a short pulse, LOB-121); hold to inspect. Search starts at the section whose + was tapped; Done or Back returns to the list with every change kept. (r35)
- **LOB-121** Each search result shows up to three small squares in a row at its bottom left: Main, Side and Maybe in that order and in their section colours (SYS-054), each with that section's copy count and shown only when it holds copies; tapping a square removes one copy from that section. (r35)
- **LOB-107** Cards tab details: a swipe either way moves one copy to the next section (Maybe wraps to Main); "Move to…" lists the other sections and moves every copy; a line like "60 main · 15 side · 2 maybe" sits beside a columns control (decks 3, cubes 5 in portrait and 8 in landscape); while the list is looked up or has problems the tab says so ("Fix them in the Text tab") and the + buttons are off; a Cards change rewrites the Text tab in section order. (r35)
- **LOB-119** Search chips are W U B R G C, mana value 0–6+ and Creature, Planeswalker, Instant, Sorcery, Artifact, Enchantment, Land: within a row any chosen chip matches, and rows and typed text all apply. Colour chips are the mana symbols as drawn in card text, mana value chips generic mana symbols with the number; chips are 32 dp tall, 4 dp apart. (r35)
- **LOB-122** Search results follow about 0.35 s after typing, sorted by name, 3 columns (5 in landscape), loading more while scrolling; with nothing typed or chosen the page shows a hint. Another printing of a card already in the section adds to it; Done counts this visit's change ("Done" when none); adding past the limit says "A deck holds at most 1000 cards."; the search is kept for the next visit in the same edit session. (r35)
- **LOB-108** Search understands Scryfall-style syntax: name words, `t:` type, `o:` rules text, `c:`/`c=`/`c<=` colours, `id:` colour identity, `mv`/`cmc` mana value (from the mana cost), `pow`/`tou`/`loy`, `r:` rarity, `f:` format legality, `s:` set, `is:` layout (e.g. `is:dfc`), comparisons (`mv<=3`), `-` negation, `or` and parentheses. (r35)
- **LOB-109** Search rules not taken from Scryfall's documentation: */X/? power or toughness counts as 0; a double-faced card's colours count per face; `!name` also matches a face name; rarities order common < uncommon < rare < special < mythic < bonus; `f:` counts restricted cards as legal; planes, schemes and vanguards show only with `include:extras`; Alchemy cards are included. (r35)

### Game chat

- **LOB-066** A chat button on the table's top bar opens "Game chat" as a side panel; a small dot on the button shows unread messages. (r16)
- **LOB-067** Messages show author and text, your own tinted; the newest stays at the bottom and the list scrolls by itself; the keyboard shrinks the list instead of pushing it up. (r13)
- **LOB-068** The keyboard's Send key sends like the Send button. A message being sent says "Checking delivery…", a failed one "Not delivered". (r24)
- **LOB-069** Unread counts are kept on the server per player and game, so they match on every device. (r16)
- **LOB-070** A typed but unsent message is kept per game; a draft from an earlier play must be cleared before writing a new one. (r16)
- **LOB-071** Chat stays usable while a game result suggestion is waiting. (r18)

### Game history and Stats

- **LOB-126** When a match ends with a final result (OK after both players saw it, a concession, or an accepted suggestion) the server keeps a record of it: the game's and pod's names, start and end, turns (the turn number at the end), who started, each player's mulligans and final life, the result and how it ended: by the rules with the loser's reason (life 0, drawing from an empty library, 10 poison counters, or a card's effect with the card's name), conceded, agreed (with its reason), or a draw (everyone lost at once, or a loop); and each deck's name, cover and colours (of its Main cards), no card list. Records are never deleted, also not with their game; matches that ended before records existed aren't listed. (r58)
- **LOB-127** A game deleted while a match is in play (by hand, LOB-026, or by the cleanup, LOB-133) leaves an Unfinished record of that match with the turn it reached; Unfinished records are listed but never count in W–L, win %, graphs or standings. (r58)
- **LOB-128** In your records the opponent's deck (name, cover, colours) shows once that deck was shown to you in any game (the match's end with LOB-043/LOB-044, DRF-125, or a later game with the same deck), which also fills in your earlier records with it; once shown it stays shown, even if they hide it again. Until then it reads "Deck" with the placeholder picture. Your own deck shows "Open deck" in a record's details while that saved deck exists. (r58)
- **LOB-129** A game lobby's History tab lists this game's matches newest first, as rows like Stats' (LOB-130), tapping one opens its details (LOB-131); "No finished matches yet." when there are none. (r58)
- **LOB-130** The Stats screen has a row of opponent chips on top ("All" first, then each opponent by number of matches, with avatar and name; the chosen chip filters everything below) and two tabs, History and Graphs. History lists every record of yours newest first: the opponent's avatar and name, "<date> · T<turns> · <how it ended>", a result chip (Win green, Loss red, Draw grey, Unfinished outlined), your deck's cover and name "vs" the opponent's deck (or "Deck"), and the pod's name for pod matches. (r58)
- **LOB-131** Tapping a record opens its details: the game name, the result chip, date, turns and duration, how it ended in full (who and why, an agreement's reason in quotes), the pod's name, and for each player whether they were on the play or the draw, mulligans, final life and deck, with "Open deck" (Stats only) for your own deck while it is saved; Close dismisses it. (r58)
- **LOB-132** The Graphs tab, drawn in the app, shows tiles (matches, W–L–D, win %, current streak, longest winning streak, average turns), then: record over time (your running wins and the opponent's with one opponent chosen, your running wins and losses with All, a line each), win % on the play vs on the draw, how matches end (by the rules, conceded, agreed, draw), match length in turns (a histogram of three-turn ranges), your decks (cover, name, W–L, win %), your decks vs theirs (a W–L grid, one opponent chosen, hidden decks pooled as "Deck"), win % by your deck's colours, and extras (win % after a mulligan, fastest win, longest match, matches per week for the last 8 weeks). Without a finished match it says so instead of drawing graphs. (r58)
- **LOB-133** The server cleans up every hour: a finished game (back in its lobby) whose last match ended 7 or more days ago with no activity since is deleted; any other game (invitation, lobby, match in play) after 14 days without activity; a pod after 14 days without activity in it or its games (games started from it go by these rules, DRF-042). Activity is any change by a player: a move or other game event, a chat message, a lobby change (Ready, deck choice, preparation), an invitation or its answer; for a pod also picks, member and setting changes, deck submissions, challenges and game starts. A device only reporting what its player saw, or a player's private play settings, is not activity. Deleting removes everything of the game or pod but never the history records; a game list on another device drops a deleted game when it next refreshes. (r58)

### Notifications and reminders

- **LOB-072** Three notification channels: Invitations, Game activity and Draft activity. A game notification reads "<game> · vs <opponent>" with "Your move", "<opponent>'s move" or "<opponent> is waiting for you", then where you would resume (the turn and step you last saw, in the turn square's words: "Turn 5, Alex's turn, End step"; "Mulliganing" in the opening) and " · new message" after chat; your deck's name is in its header and its time is your next stop's event. Every notification shows the app logo as a one-colour status-bar icon. (r49)
- **LOB-073** A move notification comes only when the game waits for you (TRN-058: your turn's stops, a decision only you can make, an information-rule stop, your opponent waiting for you instead of assuming, their line running out without you, an unseen result, an undo request), never while your opponent plays on assumptions about you, and not if they go on before it was sent. It comes 10 s after your next stop appeared when new to you (60 s otherwise), timed from that event, never while the game is open. (r49)
- **LOB-074** Leaving with the Home button still notifies; after you open a game and close it, your opponent's next moves notify with sound again. (r29)
- **LOB-075** One reminder "<opponent> is waiting for you" comes 5 minutes after your next stop appeared while the game waits for you (LOB-073), if you have not moved since; just opening the game does not count, and it waits until 5 minutes after you leave. (r49)
- **LOB-076** If no notice reached you, the app itself reminds you of games waiting for you (LOB-073; also a game you played on assumptions and left with nothing waiting for your opponent, who then gets no reminder) and pods waiting for your pick or deck build: 15 min, 30 min, 1 h, 2 h, 4 h, 8 h and 16 h after it went to the background, then daily until you open it. A notification you swipe away stays away until something new waits for you (for a pod: a new pick or the draft's end). (r49)
- **LOB-077** You get a notification when a game you are Ready for starts; pod notices cover a new pod invitation, "Your pick — <pod>", "Draft complete: build your deck", "Decks ready — <pod>", "Draft cancelled", "Pod destroyed", "Auto-start off — <pod>" and "<pod>: Removed in 3 days if nobody picks or plays" (LOB-134). (r58)
- **LOB-111** When the game ends you get a Game activity notification "<game> · vs <opponent>": "Game over · <opponent> accepted your win" ("your loss", "the draw"), "Game over · <opponent> conceded", or "Game over · You won" / "You lost" / "Draw" once a result the rules gave was confirmed after both of you saw it (TRN-065); timed like a move notification (LOB-073); tapping it opens the game. (r48)
- **LOB-134** Three days before the cleanup (LOB-133) deletes a game in play or a pod (day 11 of 14 without activity), both players get a Game activity notification "<game> · vs <opponent>": "Removed in 3 days if nobody plays", or every member a pod notification "<pod>: Removed in 3 days if nobody picks or plays"; once per stretch without activity (activity resets the clock and allows a later warning); tapping it opens the game or pod. Finished games, lobbies and invitations go without a warning. (r58)
- **LOB-114** When your opponent kept while your own opening is still "Mulliganing" (TRN-027), their game notification reads "<opponent> is ready" instead of "Your move" or "<opponent>'s move", from the point shown to you. It is timed like a move notification (LOB-073), always alerts, and like any game notification adds " · new message" after unread chat (LOB-072). (r48)
- **LOB-116** When the other player answers differently from what you assumed (a branch, HIS-097), you get one Game activity notification "<game> · vs <opponent>": "<opponent> responded differently · your later actions were taken back"; like every game notification it replaces the game's earlier one, and a game that ended with that answer tells only the result (LOB-111). (r49)
- **LOB-078** Tapping a notification opens that game or pod; a game's or pod's notification clears while you have it open. Android 13+ asks for notification permission once, on the Games list or Pods screen. (r19)

### Settings

- **LOB-079** Settings (main menu and side menu) is grouped into Profile, Games, Table, Timeline playback, Automatum, Card records and About & account, with dividers and compact rows. (r41)
- **LOB-080** Games → "Deck visibility": Visible, Hidden or "Show after first game" (default), saved on your account and used as the start value of "Show deck name & art" in new games. (r31)
- **LOB-081** Table → Card move speed (0.25–3×, default 1×), the Event text switch (on by default; the other player's open choice still shows, HIS-075), and Card size and Card text size (0.5–3×) with "Customize per area"/"Customize per text", tap-to-type values and a live sample; these are stored on the device. (r52)
- **LOB-082** Timeline playback → History playback pause (default 1 s, 0.1–10 s) and Rewind pause (default 0.1 s). (r54)
- **LOB-113** Automatum → "Response mode in new games" (Control, Normal or Quick, the default, each with what it means), "Assume resolves in new games" (Never, Normal, the default, When they have no mana or Always, each with what it means, HIS-096; a setting kept from before Normal existed reads on as Always and off (Never then) as Normal, in your account and in each game) and "Autotap mana sources" (on by default, STK-024): kept on the server for your account (every device shows the same) and given to each new game; each game's own are changed from its quick actions (TRN-059). (r57)
- **LOB-083** About & account → What's new, Report a problem, Send error reports (LOB-104), the version line "Version <name> (<code>) <type> · commit <hash>" (selectable), and Log out. (r35)

### App updates

- **LOB-084** When a newer version is published, or a game needs a newer app, the app shows only an "Update required" screen with the version and an Update button; it can't be used until updated. (r18)
- **LOB-085** The update downloads (from GitHub first) with a progress bar "x / y MB", keeps the screen on, continues where an interrupted download stopped and is checked before the installer opens. (r28)
- **LOB-086** A failed update says why in plain words and offers "Download from server" and "Download from GitHub" with install instructions, plus "Send problem report". (r28)

### What's new

- **LOB-087** After an update, "What's new" opens on the app's first frame with that update's notes grouped as New, Changed and Fixed, newest first; a fresh install shows nothing. (r22)
- **LOB-088** What's new never pops up over an open game or pod or when a notification is opening one; it can be reread any time from Settings. (r21)

### Problem and crash reports

- **LOB-089** "Report a problem" (Settings, or the update screen) sends an optional description with the app's recent log, the last game's reproduction bundle (its card rules, its events from a recent point and the latest log entries, which may include hidden cards) and the device model, but no passwords or email addresses, and shows "Report sent — code N". The server keeps the newest 20 per account and 200 in total. (r40)
- **LOB-090** Test builds always, and production builds when reports are on (LOB-104), capture crashes (with the last game's log as in LOB-089) and upload them at the next start, and also upload slow actions, stutters and reconnect episodes, with no passwords or email addresses. The server keeps per account the newest 20 crash, 60 slow-action/reconnect and 20 stutter reports, 500 in total and nothing older than 30 days. (r40)
- **LOB-104** Production asks once per device, after sign-in on the main menu or Games list (never over a game, What's new or another prompt), "Send error reports?" with No thanks / Send reports; closing it means No. Settings → About & account → "Send error reports" changes it at any time; turning it off deletes reports not yet sent. Test builds show the switch on and locked, "Always on in test builds". (r35)

### Card records

- **LOB-091** On first launch, before sign-in, "Download card records?" explains the app needs the card catalog, shows the size and metered/unmetered connection, and offers only Exit or Download. Sign-in works while it downloads, with a progress bar at the top. A failed check or download is tried once more by itself before the error shows. (r17, r50)
- **LOB-092** Automatic updates only fetch new sets and sets released in the last 60 days, and only on unmetered connections; on a metered one the app asks first ("Update card records?" with Not now). (r9)
- **LOB-093** Settings → Card records shows the installed date and record count, progress while working, "Check for updates" and "Refresh full catalog" (confirm dialog; the only way to re-download everything). (r9)
- **LOB-094** Progress reads "Downloading card records: x / y MB" (KB for small updates), "Indexing card records: x / y MB processed · N cards", then "Finishing up — preparing card search…"; the bar shows real progress where the size is known and moves without a fixed position while finishing or when the total is unknown. (r55)
- **LOB-095** If the app needs a new catalog format, it deletes the old catalog and downloads it again at startup. (r17)
- **LOB-096** While the first catalog downloads, deck and game screens show its progress in a bar at the bottom, with the screen laid out above it so the bar never covers the screen's buttons; the menu, Settings and an open game do not. A failed download is tried once more by itself before the player is asked to retry. (r17)
- **LOB-110** Card records include rarity, format legalities and colour identity for search (LOB-108); records downloaded before search are downloaded again once, asked as for any card records update. (r35)
- **LOB-125** Card records include which tokens each card makes, for token pictures (SYS-072); records downloaded without them are downloaded again once, asked as for any card records update, and kept in use until the new ones are installed; every phone with records from an earlier version downloads them again once. (r57)

### Card pictures

- **LOB-097** Card pictures come from Scryfall when first needed and are kept on the phone; a deck's pictures start downloading when you save it and when a game opens. (r21)
- **LOB-098** A picture on screen keeps retrying until it loads; on-screen pictures go before background downloads. (r21)
- **LOB-099** Inspecting a card whose picture is still loading shows the placeholder card with its name and "Downloading artwork X of Y", or "Artwork unavailable". (r52)

### Screens and dialogs

- **LOB-100** Screen titles are one line in the same place on every screen; New/Import buttons sit at the top right of Games, Pods and Manage decks. (r21)
- **LOB-101** Dialog buttons sit in one row on the right with the main action last and delete actions in red. Every button in dialogs, the lobby, decks and cubes and drafts shows an icon before its text (a cross on the dismiss button, a check, a bin on delete actions, or the action's own icon), never text alone; a button showing only an icon or symbol (the draft grid's arrows, a basic land's − and +) says what it does when held. (r47)
- **LOB-102** The keyboard's Enter key does what the screen's button does: sign in, search, send a chat message, save a name or a value. Where search runs as you type, it just closes the keyboard. (r24)
