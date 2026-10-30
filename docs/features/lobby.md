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

- **LOB-015** The main menu has big buttons for Games, Pods, Manage decks, Contacts and Settings; in portrait the ATW logo sits small at the top right of the menu-button row. (r17)
- **LOB-016** Under the Games button a panel lists up to 3 games in the shared game order (LOB-022), opening straight into the game; incoming invitations there have Accept and Decline buttons. The panel is hidden when empty. (r30)
- **LOB-017** Under the Pods button a panel lists up to 3 pods: your pick or deck building first, then other pods, pod invitations last (with Accept/Decline). Hidden when empty. (r17)
- **LOB-018** The side menu lists Menu, Manage decks, Contacts, Settings, then Games with every game and incoming invitation as rows, then Pods with each pod's other members as a compact avatar stack. Landscape shows it as a permanent side area; portrait opens it from the hamburger. (r23)
- **LOB-019** A side-menu game row shows the opponent's avatar, the game name, an "Invitation" label on its own line for invitations, and "opponent · status · date" on two lines. (?) code differs from r5: the side menu shows the creation date, while other game rows show last activity. (r30)
- **LOB-020** Back from any section opened from the menu or side menu returns to the main menu, and Back again leaves the app; steps inside a section (Games → Choose opponent) go back one step. (r30)
- **LOB-021** Tapping a side-menu game opens it; tapping an incoming invitation opens the Games screen to answer it. (r30)

### Game list and rows

- **LOB-022** One game order everywhere (Games screen, menu panel, side menu): games where it is your move (longest waiting first), then invitations to you (newest first), then games waiting for your opponent and invitations you sent (newest first), then the rest. (r30)
- **LOB-023** The Games screen shows one "GAMES" card with a count and all games and invitations in one list; "No games yet." when empty. A "New game" button sits at the top right. (r30)
- **LOB-024** A game row: opponent avatar, bold game name, a move chip ("Your move" filled, "<name>'s move" plain, or "Invitation"/"Setup"), the pod name line for pod games, then "vs <opponent> · <status> · <last activity>" with the time if today, else the date. (r15)
- **LOB-025** Row status texts: "In play", "Invitation pending", "Invitation declined", "Preparing"; a held pod challenge reads "Challenged you · submit your deck to accept" (Accept greyed) for you and "Waiting for <name>'s deck" for the challenger. (r30)
- **LOB-026** Holding an unstarted game's row offers "Cancel game"; holding a game in play offers "Delete game", confirmed by a dialog warning that it removes the game for both players with no restore ("Keep game" / "Delete game"). (r1)
- **LOB-027** Background loading of games never shows a spinner or message; only real errors appear, as a floating message at the bottom. (r3)

### Contacts and new games

- **LOB-028** Contacts and Choose opponent share one screen with two tabs: Contacts (default, with a local "Filter contacts" field) and Search (search usernames). Empty results say "No players found"; loading shows a spinner. (r17)
- **LOB-029** Contacts tab rows have a remove button (Contacts screen) or an invite button (Choose opponent); Search rows have icon-only Add contact and, when choosing an opponent, Invite. Inviting someone also adds them as a contact. (r15)
- **LOB-030** Removing a contact asks "Remove <name>?" with "Existing games and invitations will not change." (r15)
- **LOB-031** "New game" opens Choose opponent; picking a player opens the "New game" dialog ("Invite <name>") with an editable random fantasy game name (like "Azure Spark"), a reroll button, Cancel and Create. (r15)
- **LOB-032** Adding a contact is one-sided: no friend request, and it never accepts anything for the other player. (r1)

### Game lobby

- **LOB-033** For the inviter the new game is already a lobby: they can choose and prepare a deck and press Ready before the invitation is answered. (r1)
- **LOB-034** The recipient answers with Accept or Decline. Accept opens the same lobby (not Ready). Decline keeps the inviter's lobby, which shows a "Reinvite" button. Invitations never expire. (r1)
- **LOB-035** The lobby screen shows the game name, "<status> · Created <date>", your card (avatar, Ready/Not ready, deck cover, deck name, "N main / M side", Choose/Change deck, Prepare deck), the opponent's card and the Starting player card; landscape puts them side by side. (r21)
- **LOB-036** The game starts when both players are Ready; Ready stays on when you leave or go offline. A full-width Ready button turns into "Withdraw Ready"; a line above it says why Ready is not possible ("Select your deck to get ready."). (r1)
- **LOB-037** Changing your deck, its preparation or the starting player clears only your own Ready. (r1)
- **LOB-038** Starting player offers Random or either player; an "Alternate" switch at its top right (on by default) makes the player who did not start the last game start the next one. Random is decided when the game starts. (r28)
- **LOB-039** "Cancel game" (header button or row hold) removes an unstarted game and its pending invitation: the inviter can use it anytime in setup, the recipient after accepting. (r1)
- **LOB-040** Choose deck lists your saved decks, then your draft decks under a "Draft decks" heading, with cover and "N main / M side"; it has pull-to-refresh, "Import deck" and "Use selected deck". (r31)
- **LOB-041** Importing a deck from Choose deck returns you to Choose deck with the new deck selected. (r21)
- **LOB-042** The opponent's deck shows as "Deck" with a placeholder picture, unless they chose to show it; your own pod deck's private name is visible only to you. (r30)
- **LOB-043** Your card has a "Show deck name & art" switch for this game, starting from the Deck visibility setting (LOB-079). Pressing it at all makes it your own choice for that game that nothing overrides; picking another deck resets it. (?) (r34)
- **LOB-044** With "Show after first game", your deck is shown to an opponent once you finish a game with that deck against them, also in the rematch and in other lobbies already waiting with that deck; games in play are not changed. (?) (r34)
- **LOB-045** Lobby buttons stay enabled while the app reconnects; a tap waits for the connection and then goes through. (r21)
- **LOB-046** After an accepted game result both players return to the same lobby with their last prepared decks restored and both Ready cleared. (r1)

### Deck preparation for a game

- **LOB-047** "Prepare deck" opens one scrolling page with Mainboard · N and Sideboard · N headers, cards as pictures grouped by type (Creatures, Planeswalkers, Battles, Instants, Sorceries, Artifacts, Enchantments, Lands, Other) and ×N badges for copies. (r24)
- **LOB-048** Swipe a card sideways to move one copy to the other section; hold to preview it; tap for "Move one to…", "Move all N to…" and "Inspect". (r24)
- **LOB-049** A round floating Undo button at the bottom left, shown only when there is something to undo, takes back moved cards and basic land changes one at a time. (r23)
- **LOB-050** A line under the title briefly says what you last moved; while disconnected it reads "Disconnected · preparation is read-only". (r21)
- **LOB-051** The prepare screen and saved deck view have a columns control (3 by default), remembered per screen and orientation. (r31)
- **LOB-052** In pod games each section has basic land −/+ steppers (W U B R G); Ready is blocked with a reason if Main has fewer than 40 cards including basics or Side is over the pod's sideboard limit. (r23)
- **LOB-053** Lobby deck changes never change the saved deck, and saved deck edits or deletion never change a deck already chosen in a lobby or game. (r1)

### Deck library

- **LOB-054** Manage decks has Decks and Cubes tabs and an "Import deck" button at the top right; the Decks tab shows "SAVED LISTS (N)" with cover, name and "N main / M side", and pull-to-refresh only (no background refresh). (r21)
- **LOB-055** Every submitted pod deck is kept as a draft deck in its own section under Decks (newest 20 kept); it can be played in any game, and "Save as deck" makes an editable copy that is kept. (r31)
- **LOB-056** A saved deck opens as a read-only picture view with Mainboard and Sideboard sections like the prepare screen: tap inspects, hold previews. (r31)
- **LOB-057** The saved deck screen has "Edit" (opens the import editor with its list) and "Delete saved deck" at the bottom, confirmed with "Deletion cannot be undone." A draft deck instead shows "Save as deck", a note about where it was drafted, and "Delete draft deck". (r31)
- **LOB-058** Each deck has a cover chosen from its own cards. (r1)

### Deck import

- **LOB-059** Import is one screen: cover and deck name on one row (tap the cover to choose it once the list resolves), a full-height list field with a paste icon, a fixed area for problems, and one full-width Save at the bottom. (r21)
- **LOB-060** The list is looked up about 0.4 s after you stop typing; Save works only for a named, fully resolved list. Unknown or ambiguous names are flagged with line numbers and never replaced silently. (r21)
- **LOB-061** "Import from link" in the top bar (icon only on narrow phones) opens a dialog for Archidekt links or `archidekt:ID`; a matching link on the clipboard is prefilled, the paste icon pastes and imports at once, typed text waits for Import. (r20)
- **LOB-062** Common list formats work: "4 Name", "4x", optional "(SET) number", Deck/Main/Sideboard/Commander/Companion headers (also with # or // and counts), "SB:", a blank line before the sideboard, comments, Forge, XMage and CSV exports; Maybeboard is skipped. (r19)
- **LOB-063** A deck holds at most 1000 cards. (r15)
- **LOB-064** Saving a new import from Manage decks opens its saved deck view; leaving with unsaved changes asks "Discard unsaved changes?" (Keep editing / Discard). (r15)
- **LOB-065** Importing into an existing deck offers Overwrite (default) or Merge, where Merge adds quantities to matching cards. (r1)

### Game chat

- **LOB-066** A chat button on the table's top bar opens "Game chat" as a side panel; a small dot on the button shows unread messages. (r16)
- **LOB-067** Messages show author and text, your own tinted; the newest stays at the bottom and the list scrolls by itself; the keyboard shrinks the list instead of pushing it up. (r13)
- **LOB-068** The keyboard's Send key sends like the Send button. A message being sent says "Checking delivery…", a failed one "Not delivered". (r24)
- **LOB-069** Unread counts are kept on the server per player and game, so they match on every device. (r16)
- **LOB-070** A typed but unsent message is kept per game; a draft from an earlier play must be cleared before writing a new one. (r16)
- **LOB-071** Chat stays usable while a game result suggestion is waiting. (r18)

### Notifications and reminders

- **LOB-072** Three notification channels: Invitations, Game activity and Draft activity. A game notification reads "<game> · vs <opponent>" with "Your move", "<opponent>'s move" or "<opponent> is waiting for you", plus " · new message" after chat, and shows the time of the last event. (r28)
- **LOB-073** A move notification comes 10 s after your opponent's last touch when it is your move (60 s otherwise), never while you have the game open; any input counts as activity (pan, inspect, chat). (r28)
- **LOB-074** Leaving with the Home button still notifies; after you open a game and close it, your opponent's next moves notify with sound again. (r29)
- **LOB-075** One reminder "<opponent> is waiting for you" comes 5 minutes after your opponent moved and went idle, if you have not moved since; just opening the game does not count, and it waits until 5 minutes after you leave. (r28)
- **LOB-076** If no notice reached you, the app itself reminds you of games waiting for your move: 15 min, 30 min, 1 h, 2 h, 4 h, 8 h and 16 h after it went to the background, then daily until you open it. A notification you swipe away stays away until your opponent does something new. (?) (r34)
- **LOB-077** You get a notification when a game you are Ready for starts; pod notices cover a new pod invitation, "Your pick — <pod>", "Draft complete: build your deck", "Decks ready", "Draft cancelled", "Pod destroyed" and "Auto-start off". (r25)
- **LOB-078** Tapping a notification opens that game or pod; a game's or pod's notification clears while you have it open. Android 13+ asks for notification permission once, on the Games list or Pods screen. (r19)

### Settings

- **LOB-079** Settings (main menu and side menu) is grouped into Profile, Games, Table, Timeline playback, Card records and About & account, with dividers and compact rows. (r31)
- **LOB-080** Games → "Deck visibility": Visible, Hidden or "Show after first game" (default), saved on your account and used as the start value of "Show deck name & art" in new games. (r31)
- **LOB-081** Table → Card move speed (0.25–3×, default 1×), the Event text switch (on by default), and Card size and Card text size (0.5–3×) with "Customize per area"/"Customize per text", tap-to-type values and a live sample; these are stored on the device. (r29)
- **LOB-082** Timeline playback → the auto-pause switches, History playback pause (default 1 s, 0.1–10 s), Rewind pause (default 0.1 s), and "Rewind automatically when the opponent branched" with "During live play" and "When opening a game" (both off by default). (r33)
- **LOB-083** About & account → What's new, Report a problem, the version line "Version <name> (<code>) <type> · commit <hash>" (selectable), and Log out. (r31)

### App updates

- **LOB-084** When a newer version is published, or a game needs a newer app, the app shows only an "Update required" screen with the version and an Update button; it can't be used until updated. (r18)
- **LOB-085** The update downloads (from GitHub first) with a progress bar "x / y MB", keeps the screen on, continues where an interrupted download stopped and is checked before the installer opens. (r28)
- **LOB-086** A failed update says why in plain words and offers "Download from server" and "Download from GitHub" with install instructions, plus "Send problem report". (r28)

### What's new

- **LOB-087** After an update, "What's new" opens on the app's first frame with that update's notes grouped as New, Changed and Fixed, newest first; a fresh install shows nothing. (r22)
- **LOB-088** What's new never pops up over an open game or pod or when a notification is opening one; it can be reread any time from Settings. (r21)

### Problem and crash reports

- **LOB-089** "Report a problem" (Settings, or the update screen) sends an optional description with the app's recent log and the device model, but no passwords or email addresses, and shows "Report sent — code N". (r28)
- **LOB-090** Test builds capture crashes and upload them at the next start, and also upload slow actions, stutters and reconnect episodes; the server keeps the latest 20 stutter reports per account. (r11)

### Card records

- **LOB-091** On first launch, before sign-in, "Download card records?" explains the app needs the card catalog, shows the size and metered/unmetered connection, and offers only Exit or Download. Sign-in works while it downloads, with a progress bar at the top. (r17)
- **LOB-092** Automatic updates only fetch new sets and sets released in the last 60 days, and only on unmetered connections; on a metered one the app asks first ("Update card records?" with Not now). (r9)
- **LOB-093** Settings → Card records shows the installed date and record count, progress while working, "Check for updates" and "Refresh full catalog" (confirm dialog; the only way to re-download everything). (r9)
- **LOB-094** Progress reads "Downloading card records: x / y MB" (KB for small updates), "Indexing card records…", then "Finishing up — verifying catalog…", with a bar that fills steadily. (r30)
- **LOB-095** If the app needs a new catalog format, it deletes the old catalog and downloads it again at startup. (r17)
- **LOB-096** While the first catalog downloads, deck and game screens show a busy overlay with its progress; the menu, Settings and an open game do not. (r17)

### Card pictures

- **LOB-097** Card pictures come from Scryfall when first needed and are kept on the phone; a deck's pictures start downloading when you save it and when a game opens. (r21)
- **LOB-098** A picture on screen keeps retrying until it loads; on-screen pictures go before background downloads. (r21)
- **LOB-099** Inspecting a card whose picture is still loading shows "Downloading artwork X of Y", or "Artwork unavailable". (r21)

### Screens and dialogs

- **LOB-100** Screen titles are one line in the same place on every screen; New/Import buttons sit at the top right of Games, Pods and Manage decks. (r21)
- **LOB-101** Dialog buttons sit in one row on the right with the main action last and delete actions in red. (r21)
- **LOB-102** The keyboard's Enter key does what the screen's button does: sign in, search, send a chat message, save a name or a value. (r24)
- **LOB-103** First loads show a full-area placeholder (with Retry on failure), errors appear as a floating message at the bottom, and screens keep their layout when states change. (r15)
