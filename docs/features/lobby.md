# Lobby

Everything around a game outside the table: accounts and sign-in, profile and avatars, navigation, the game list,
contacts and new games, the game lobby, game chat, game history and Stats, notifications and reminders, and the
cleanup of idle games.

## How it works

You sign in with one account that works the same on all your devices, and pick a card's art as your avatar. The main
menu and the side menu lead to your games, pods, decks, contacts, Stats and settings; every list of games uses one
order, with the games where it is your move first. To start a game you invite a contact or a player you search for;
the new game opens as a lobby where both players choose and prepare a deck and press Ready, and the game starts when
both are Ready. The game's creator can lend the other player their saved decks, even make the game a battle box whose
decks are dealt at random, so a new player can play without a deck of their own. When a match ends the server keeps a record of it for Stats and the game returns to its lobby for the
next one. Each game has a chat: the other player's messages pop up as bubbles on your table, and quick messages send
with one tap. Notifications tell you when a game waits for you, and the app reminds you of games you have left
waiting. Games and pods nobody touches for long are deleted by the server, with a warning first for games in play
and pods.

## Accounts and sign-in

- **LOB-001** Create an account with username, email and password; the form says "Your username is visible to players." and "Your email stays private." Other players never see your email. (r1)
- **LOB-002** Sign in with email and password; the password field has a show/hide eye button. (r1)
- **LOB-003** "Forgot password?" sends a reset link by email ("If that email is registered, a reset link will be sent."); the link opens the app to set a new password, and a bad or used link says so and offers "Request another link". (r1)
- **LOB-005** While a saved login is checked the start screen shows "Checking saved login…"; if that fails it offers Retry and Logout. (r1)
- **LOB-004** You stay signed in across restarts with no time limit; only a rejected session asks you to sign in again ("Your session is no longer valid. Please sign in again."). (r1)
- **LOB-006** The same account works on several devices: saved decks, contacts and games are the same everywhere. (r1)
- **LOB-008** The app reopens on the section you last used for that account. (r1)
- **LOB-007** Log out (Settings, red row) signs out this device at once and removes that account's private downloaded data from the phone. (r3)
- **LOB-009** Test builds have "Switch account" and "Add account" at the bottom of the side menu; normal releases do not. (r3)

## Profile and avatars

- **LOB-010** An avatar is a card's art crop in a circle; without one, a coloured circle with your username's first letter is shown. Other players see your avatar even if the card is not in their catalog. (r17)
- **LOB-014** Avatars appear next to player names everywhere: game rows, game lobby, contacts and search, invitations, pod rows and the side menu. (r17)
- **LOB-011** After the first sign-in (once per account without an avatar) "Choose your avatar" shows 6 random legendary creatures or planeswalkers, a dice reroll button, a "Search by card name" field and Skip. (r17)
- **LOB-012** While the card records are not ready, the avatar choices area says "Avatars need the card catalog." with the phase and percentage, and the choices appear by themselves when ready. (r17)
- **LOB-013** Settings → Profile shows your avatar and name with a pen button that opens one "Edit profile" dialog: smaller avatar choices without scrolling, search above them, reroll, username field, Cancel and Save (no Skip). (r18)
- **LOB-149** Avatar choices (the first-sign-in chooser, LOB-011, and Edit profile, LOB-013) show each card's name under its art (up to two lines) and, above them, what they are: "Random legendary creatures and planeswalkers", or "Cards matching “<search>”: N". (r74)
- **LOB-160** The avatar choices lie in a framed area that scrolls inside its frame; a shade at its top or bottom edge shows more lies that way. (r74)
- **LOB-161** In the desktop layout the avatar choices are larger and as many to a row as fit, the chooser keeps to a readable width and the Edit profile dialog is wider and taller (owner, 2026-11-14). (r74)

## Navigation and main menu

- **LOB-015** The main menu has big buttons for Games, Pods, Manage decks, Contacts, Stats and Settings; in portrait the ATW logo sits small at the top right of the menu-button row. (r61)
- **LOB-016** Under the Games button a panel lists up to 3 games in the shared game order (LOB-022), opening straight into the game; incoming invitations there have Accept and Decline buttons. The panel is hidden when empty. (r30)
- **LOB-017** Under the Pods button a panel lists up to 3 pods and pod invitations in the shared pod order (DRF-013), invitations with Accept/Decline. Hidden when empty. (r35)
- **LOB-018** The side menu lists Games with every game and incoming invitation as rows, then Pods in the shared pod order (DRF-013), each with its other members as a compact avatar stack, its name and a two-line status, then the menu section: Menu, Manage decks, Contacts, Stats, Settings. Landscape shows it as a permanent side area; portrait opens it from the hamburger. (r61)
- **LOB-019** A side-menu game row shows the opponent's avatar, the game name, an "Invitation" label on its own line for invitations, and "opponent · status · last activity" (the time if today, else the date) on two lines. (r35)
- **LOB-021** Tapping a side-menu game opens it; tapping an incoming invitation opens the Games screen to answer it. (r30)
- **LOB-020** Back from any section opened from the menu or side menu returns to the main menu, and Back again leaves the app; steps inside a section (Games → Choose opponent) go back one step. (r30)

## The game list

- **LOB-022** One game order everywhere (Games screen, menu panel, side menu): games where it is your move (TRN-058; longest waiting first), then invitations to you (newest first), then games waiting for your opponent and invitations you sent (newest first), then the rest. Pods follow the same shape (DRF-013). (r50)
- **LOB-023** The Games screen shows one "GAMES" card with a count and all games and invitations in one list; "No games yet." when empty. A "New game" button sits at the top right. (r30)
- **LOB-024** A game row: opponent avatar, bold game name, a move chip ("Your move" filled, "<name>'s move" plain, or "Invitation"/"Setup"; the move of TRN-058), the pod name line for pod games, then "vs <opponent> · <status> · <last activity>", the time if today, else the date, from your local head and next stop (HIS-032). (r50)
- **LOB-025** Row status texts: "In play", "Invitation pending", "Invitation declined", "Preparing"; a held pod challenge reads "Challenged you · submit your deck to accept" (Accept greyed) for you and "Waiting for <name>'s deck" for the challenger. (r30)
- **LOB-026** Holding an unstarted game's row offers "Cancel game"; holding a game in play offers its chat ("Chat", LOB-148) and "Delete game", confirmed by a dialog warning that it removes the game for both players with no restore ("Keep game" / "Delete game"); its match in play is kept as an Unfinished record (LOB-127). (r74)

## Contacts and new games

- **LOB-028** Contacts and Choose opponent share one screen with two tabs: Contacts (default, with a local "Filter contacts" field) and Search (search usernames). Empty results say "No players found"; loading shows a spinner. (r17)
- **LOB-029** Contacts tab rows have a remove button (Contacts screen) or an invite button (Choose opponent); Search rows have icon-only Add contact and, when choosing an opponent, Invite. Inviting someone also adds them as a contact. (r15)
- **LOB-032** Adding a contact is one-sided: no friend request, and it never accepts anything for the other player. (r1)
- **LOB-030** Removing a contact asks "Remove <name>?" with "Existing games and invitations will not change." (r15)
- **LOB-031** "New game" opens Choose opponent; picking a player opens the "New game" dialog ("Invite <name>") with an editable random fantasy game name (like "Azure Spark"), a reroll button, Cancel and Create; every game is played with Automatum (SYS-055). (r45)

## The game lobby

- **LOB-033** For the inviter the new game is already a lobby: they can choose and prepare a deck and press Ready before the invitation is answered. (r1)
- **LOB-034** The recipient answers with Accept or Decline. Accept opens the same lobby (not Ready). Decline keeps the inviter's lobby, which shows a "Reinvite" button. Invitations have no expiry of their own; the cleanup removes them once they have been idle long enough (LOB-133). (r1)
- **LOB-035** The lobby screen shows the game name and two tabs, Lobby and History (LOB-129). Lobby shows "<status> · Created <date>", your card (avatar, Ready/Not ready, deck cover, deck name, "N main / M side", Choose/Change deck, Prepare deck), the opponent's card, the Starting player card and the Game settings card (LOB-135); landscape puts them side by side. (r58)
- **LOB-040** Choose deck lists your saved decks, then your draft decks under a "Draft decks" heading, with cover and "N main / M side"; it has pull-to-refresh, "Import deck" and "Use selected deck". (r31)
- **LOB-041** Importing a deck from Choose deck returns you to Choose deck with the new deck selected. (r21)
- **LOB-043** Your card has a "Show deck name & art" switch for this game, starting from the Deck visibility setting (LOB-079). Pressing it at all makes it your own choice for that game that nothing overrides; picking another deck resets it. (r34)
- **LOB-044** With "Show after first game", your deck is shown to an opponent once you finish a game with that deck against them, also in the rematch and in other lobbies already waiting with that deck; games in play are not changed. (r34)
- **LOB-042** The opponent's deck shows as "Deck" with a placeholder picture, unless they chose to show it; your own pod deck's private name is visible only to you. (r30)
- **LOB-038** Starting player offers Random or either player; an "Alternate" switch at its top right (on by default) makes the player who did not start the last game start the next one. Random is decided when the game starts. (r28)
- **LOB-036** The game starts when both players are Ready; Ready stays on when you leave or go offline. A full-width Ready button turns into "Withdraw Ready"; a line above it says why Ready is not possible ("Select your deck to get ready."). (r1)
- **LOB-037** Changing your deck, its preparation or the starting player clears only your own Ready. (r1)
- **LOB-039** "Cancel game" (header button or row hold) removes an unstarted game and its pending invitation: the inviter can use it anytime in setup, the recipient after accepting; not while an OK on the last result is still owed (TRN-065). (r1)
- **LOB-046** When a game's result is final the game returns to the same lobby with both players' last prepared decks restored and both Ready cleared; both players get there together, only once both OKs on the result dialog stand, whatever the ending (TRN-065), and nothing in that lobby can be done before (the next game included). (r1)

## Lending decks

- **LOB-247** The lobby's Game settings card ends with a Decks group: Lend my decks, Only lent decks, Same deck allowed, Deck choice and New random decks on rematch. Both players see them; only the game's creator changes them, in the lobby, and changing one clears both players' Ready. (r78)
- **LOB-248** Lend my decks: Off (the default), All, or chosen categories (one or several, in a dialog listing the creator's categories as a tree); draft decks are never lent. While it is Off the other Decks settings are greyed and do nothing. The other player sees "<creator> lends decks" with the value. (r78)
- **LOB-249** Only lent decks (off by default): both players may use only lent decks, nothing from outside them, the creator's own other decks included. Such a game is a battle box. (r78)
- **LOB-254** Same deck allowed (on by default): both players may use the same lent deck; off, the server refuses a deck the other player already uses ("Both players can't use the same deck in this game"). (r78)
- **LOB-250** Deck choice: Each picks (the default) or Random. With Random neither player chooses: Choose deck is gone, the deck reads "Dealt at random when the game starts", and when both are Ready the server deals each player a lent deck, different ones unless Same deck allowed. (r78)
- **LOB-251** New random decks on rematch (off by default, greyed unless Random): on, every rematch deals new decks as it starts; off, both players keep the decks they were dealt (LOB-046). (r78)
- **LOB-257** A Decks change puts back a chosen deck the new settings no longer allow (a borrowed deck no longer lent, any deck outside the lent ones with Only lent decks, the other player's copy of the same deck without Same deck allowed, every deck when Random starts); that player chooses again. (r78)
- **LOB-255** For the other player Choose deck lists "<creator>'s decks" first, the lent ones under their categories' names (uncategorised ones last), then "Your decks"; with Only lent decks your own decks (and, for the creator, the decks not lent) are greyed with "Only <creator>'s lent decks can be used in this game." (r78)
- **LOB-256** Choosing a lent deck makes the game's own copy of it (LOB-053): Prepare deck works, the lender's saved deck never changes and nothing is saved to your account. Its name and art always show to both players: your card reads "from <lender>" and its Show deck switch stays on, greyed. (r78)
- **LOB-260** A lent deck with Automatum's warning (LOB-115) can't be chosen (LOB-118) and is never dealt; with Random a Ready is refused while too few lent decks can be dealt ("There aren't enough lent decks Automatum can play to deal one to each player."). (r78)
- **LOB-252** An invitation whose creator lends decks says so: a line "<creator> lends you decks" on its rows (Games screen, menu panel, side menu) and as its notification's text, on phones and in browsers. Turning lending on while the invitation waits sends its notification again. (r78)

## Game chat

- **LOB-066** A chat button on the table's top bar opens "Game chat" as a side panel (in a pod game with Game and Pod tabs, LOB-145). (r74)
- **LOB-162** On the desktop table the chat button unfolds the chat dock, ready to type, on the tab with unread messages, and folds it away again while it is open: the desktop table has no chat panel (WEB-017). (r74)
- **LOB-163** A small dot on the chat button shows unread messages: it clears once every message of the other player's is read, seen in the chat or tapped as its bubble (LOB-136), and in a pod game also every message of the pod's chat (DRF-170). (r74)
- **LOB-067** Messages show author and text, your own tinted, and a small time at the row's bottom right ("14:05", the device's time zone; none while a message is on its way); a message's text can be selected and copied. The newest stays at the bottom and the list scrolls by itself; the keyboard shrinks the list instead of pushing it up. (r79)
- **LOB-164** Above the first chat message of each day lies a centred date line, "Today", "Yesterday" or "Sat 14 Nov" ("Wed 31 Dec 2025" in an earlier year). (r74)
- **LOB-068** The keyboard's Send key sends like the Send button; in the web's desktop layout (WEB-013) Enter sends and Shift+Enter or Ctrl+Enter starts a new line, in every chat (the table's, the games list's, LOB-148, and the pod's, DRF-170). A message being sent says "Checking delivery…", a failed one "Not delivered". (r76)
- **LOB-070** A typed but unsent message is kept per game; a draft from an earlier play must be cleared before writing a new one. (r16)
- **LOB-069** Unread counts and what each player has read are kept on the server per player and game, so they match on every device: a bubble read on one of your devices is gone on the other. (r58)
- **LOB-138** Your message shows a small "Read" under it in the chat once the other player has read it (tapped its bubble or seen it in their chat), at once while you have the game open. (r58)
- **LOB-145** In a pod game the chat panel has two tabs, Game and Pod (the pod's chat, DRF-170); it opens on the tab you last used in that game (kept while the app runs), and before you have used either on Game, or on Pod when only the pod's chat holds unread messages; Write (LOB-172) and holding a bubble open Game (which is then the tab last used). (r70)
- **LOB-169** A chat tab with a message you haven't seen shows a dot after its name while you are on the other tab; tapping a game message's bubble counts as seeing it. (r70)
- **LOB-170** Pod messages show no bubbles on the table. There is no Pod tab once you have left the pod or it is gone. (r70)
- **LOB-171** While the table is open the pod's chat is read again as often as on the pod screen (DRF-170). (r70)
- **LOB-148** A game's chat opens from the games list (Games screen, menu panel; on the desktop by right-click): holding a game in play opens its menu, whose "Chat" button reads "Chat · 2 unread" while the other player has messages you haven't read (counted as the menu opens). (r74)
- **LOB-173** The games list's "Chat" button opens the game's chat (the Game chat alone, also in a pod game) as a side panel over the list without opening the game: its messages are read as they show (LOB-069, LOB-138) and you can write and send as in the table's chat; its ✕ or Back closes it. (r74)

## Chat bubbles and quick messages

- **LOB-136** A chat message of the other player's shows on your table as a speech bubble by their life square, always right under it (in landscape under the top bar, where their square sits), over whatever lies there, hand cards included (only the screen's edges move it; the bars' controls and open panels lie over it). (r61)
- **LOB-165** A chat bubble has the old status bubbles' look: white, outlined and tailed in the other player's colour, the text centred over up to four lines and cut with "…", never narrower than the life square. (r61)
- **LOB-166** A chat bubble stays until you tap it (on the desktop table it also goes by itself, WEB-138); a tap reads that message and touches nothing under it. Holding the bubble opens the chat at that message. (r61)
- **LOB-167** With several unread the bubble shows the oldest with "1/3" beside its text; each tap reads it and shows the next ("2/3"), and the last tap leaves no bubble. (r61)
- **LOB-168** Chat bubbles show in play, in timeline mode and when you open the game (the unread ones), never as part of the game's history. (r61)
- **LOB-137** A message you send shows always right above your own life square (in landscape above the bottom bar, where your square sits), over whatever lies there, as a bubble in your colour for about 3 s once the table is in sight (in portrait after the chat closes), then fades in 0.4 s; it takes no taps. (r61)
- **LOB-139** Quick messages are in your own Life panel (TRN-030), not in the chat: chips "Thinking…", "One sec", "Your move?", "Got a response", "No responses" and "GG" (greyed while a message can't go), each sends its text with one tap as a message like any other (your bubble, LOB-137) and closes the panel; a typed draft stays as it is. (r61)
- **LOB-172** After the quick-message chips an icon-only Write button (a pencil; holding it says "Write a message", SYS-045) opens the chat with its input focused and the soft keyboard up, ready to type. (r61)

## Match records

- **LOB-126** When a match ends with a final result (both players' OKs on a result the rules gave, a concession, or an accepted suggestion, TRN-065) the server keeps a record of it. Records are never deleted, also not with their game. (r58)
- **LOB-174** A match record holds the game's and pod's names, start and end, turns (the turn number at the end), who started, each player's mulligans and final life, the result and how it ended (LOB-175), and each deck's name, cover and colours (of its Main cards), no card list. (r58)
- **LOB-175** How a match ended is one of: by the rules with the loser's reason (life 0, drawing from an empty library, 10 poison counters, or a card's effect with the card's name), conceded, agreed (with its reason), or a draw (everyone lost at once, or a loop). (r58)
- **LOB-127** A game deleted while a match is in play (by hand, LOB-026, or by the cleanup, LOB-133) leaves an Unfinished record of that match with the turn it reached; Unfinished records are listed but never count in W–L, win %, graphs or standings. (r58)
- **LOB-128** In your records the opponent's deck (name, cover, colours) shows once that deck was shown to you in any game (the match's end with LOB-043/LOB-044, DRF-125, or a later game with the same deck), which also fills in your earlier records with it; once shown it stays shown, even if they hide it again. Until then it reads "Deck" with the placeholder picture. Your own deck shows "Open deck" in a record's details while that saved deck exists. (r58)

## Game history and Stats

- **LOB-129** A game lobby's History tab lists this game's matches newest first, as rows like Stats' (LOB-176), tapping one opens its details (LOB-131); "No finished matches yet." when there are none. (r58)
- **LOB-130** The Stats screen has a row of opponent chips on top ("All" first, then each opponent by number of matches, with avatar and name; the chosen chip filters everything below) and two tabs, History and Graphs. (r58)
- **LOB-176** Stats' History tab lists every record of yours newest first: the opponent's avatar and name, "<date> · T<turns> · <how it ended>", a result chip (Win green, Loss red, Draw grey, Unfinished outlined), your deck's cover and name "vs" the opponent's deck (or "Deck"), and the pod's name for pod matches. (r58)
- **LOB-131** Tapping a record opens its details: the game name, the result chip, date, turns and duration, how it ended in full (who and why, an agreement's reason in quotes), the pod's name, and for each player whether they were on the play or the draw, mulligans, final life and deck, with "Open deck" (Stats only) for your own deck while it is saved; Close dismisses it. (r58)
- **LOB-132** The Graphs tab, drawn in the app, starts with tiles filling the whole width in equal columns, two rows of three or, with room, one row of six (matches, W–L–D, win %, current streak, longest winning streak, average turns). Without a finished match it says so instead of drawing graphs. (r61)
- **LOB-177** After the tiles the Graphs tab shows record over time: your running wins and the opponent's with one opponent chosen, your running wins and losses with All, a line each. (r61)
- **LOB-178** Then win % on the play vs on the draw, how matches end (by the rules, conceded, agreed, draw), and match length in turns (a histogram of four-turn ranges: 1-4, 5-8, up to 29-32, then 33+). (r61)
- **LOB-179** Then your decks (cover, name, W–L, win %; the most recently played first, by its newest finished match), your decks vs theirs (a W–L grid, one opponent chosen, hidden decks pooled as "Deck"), and win % by your deck's colours (as mana symbols). (r61)
- **LOB-253** A borrowed deck counts for the player who played it, named "<deck> (from <lender>)" in their records and graphs (and as the opponent's deck in the lender's); the lender's own decks' stats don't change. (r78)
- **LOB-180** The Graphs tab ends with extras: win % after a mulligan, fastest win, longest match, matches per week for the last 8 weeks. (r61)

## Notifications and reminders

- **LOB-072** Three notification channels: Invitations, Game activity and Draft activity. Every notification shows the app logo as a one-colour status-bar icon. (r70)
- **LOB-183** A game notification reads "<game> · vs <opponent>" with "Your move", "<opponent>'s move" or "<opponent> is waiting for you", then where you would resume (the turn and step you last saw, in the turn square's words: "Turn 5, Alex's turn, End step"; "Mulliganing" in the opening); your deck's name is in its header and its time is your next stop's event. (r70)
- **LOB-184** A game notification adds " · new message" while the game's chat holds a message of the opponent's you haven't read (opening the game doesn't read it; reading it in the chat or tapping its bubble does, LOB-136). (r70)
- **LOB-185** An invitation's notification reads "<name> invited you · <game>", a pod challenge's "<pod>: <name> challenged you" (DRF-122); a pod chat message posts none (DRF-170). (r70)
- **LOB-073** A move notification comes only when the game waits for you (TRN-058: your turn's stops, a decision only you can make, an information-rule stop, your opponent waiting for you instead of assuming, their line running out without you, a result you haven't pressed OK on (TRN-065), an undo request), never while your opponent plays on assumptions about you, and not if they go on before it was sent. (r49)
- **LOB-186** A move notification comes 10 s after your next stop appeared when new to you (60 s otherwise), timed from that event, never while the game is open. (r49)
- **LOB-114** When your opponent kept while your own opening is still "Mulliganing" (TRN-027), their game notification reads "<opponent> is ready" instead of "Your move" or "<opponent>'s move", from the point shown to you. It is timed like a move notification (LOB-186), always alerts, and like any game notification adds " · new message" after unread chat (LOB-184). (r48)
- **LOB-116** When the other player answers differently from what you assumed (a branch, HIS-097), you get one Game activity notification "<game> · vs <opponent>": "<opponent> responded differently · your later actions were taken back"; like every game notification it replaces the game's earlier one, and a game that ended with that answer tells only the result (LOB-111). (r49)
- **LOB-111** When the game ends you get a Game activity notification "<game> · vs <opponent>": "Game over · <opponent> accepted your win" ("the draw"), "Game over · <opponent> conceded", or "Game over · You won" / "You lost" / "Draw" once both OKs stand on a result the rules gave, to the player who pressed first (TRN-065); timed like a move notification (LOB-186); tapping it opens the game. (r60)
- **LOB-077** You get a notification when a game you are Ready for starts. Pod notifications: DRF-136, DRF-137 and the cleanup warning (LOB-134). (r58)
- **LOB-074** Leaving with the Home button still notifies; after you open a game and close it, your opponent's next moves notify with sound again. (r29)
- **LOB-075** One reminder "<opponent> is waiting for you" comes 5 minutes after your next stop appeared while the game waits for you (LOB-073), if you have not moved since; just opening the game does not count, and it waits until 5 minutes after you leave. (r49)
- **LOB-076** (Android) If no notice reached you, the app itself reminds you of games waiting for you (LOB-073; also a game you played on assumptions and left with nothing waiting for your opponent, who then gets no reminder) and pods waiting for your pick or deck build: 15 min, 30 min, 1 h, 2 h, 4 h, 8 h and 16 h after it went to the background, then daily until you open it. A notification you swipe away stays away until something new waits for you (for a pod: a new pick or the draft's end). (r49)
- **LOB-078** Tapping a notification opens that game or pod (a game already open keeps its table as it was, so what came meanwhile still shows from where you left it, HIS-094, HIS-106, HIS-108); a game's or pod's notification clears while you have it open, and everywhere once nothing waits for you (LOB-242). (r58)
- **LOB-242** A game's, pod's or invitation's notification clears on every device of yours, phones and browsers, once nothing waits for you there any more, however you dealt with it: a game once you've seen what it told on any device and it isn't your move, an invitation once answered, a pod once your pick is made (your queue's included) or your deck submitted. (r77)
- **LOB-243** The server decides what still waits: with push a device clears it within seconds, without push at its next check (LOB-076's or its regular one, or opening the app). Other pod notices ("Decks ready", "Draft cancelled" …) clear on every device once you have opened that pod on any one (a "Pod destroyed" notice: once you have shown the Pods list) (DRF-138). (r80)
- **LOB-188** Android 13+ asks for notification permission once, on the Games list or Pods screen. (r58)

## Cleanup of idle games and pods

- **LOB-133** The server cleans up every hour: a finished game (back in its lobby) whose last match ended 7 or more days ago with no activity since is deleted; any other game (invitation, lobby, match in play) after 14 days without activity; a pod after 14 days without activity in it or its games (games started from it go by these rules, DRF-042). (r62)
- **LOB-181** For the cleanup, activity is any change by a player: a move or other game event, a chat message, a lobby change (Ready, deck choice, preparation), an invitation or its answer; for a pod also picks, member and setting changes, deck submissions, challenges, game starts and its chat messages. A device only reporting what its player saw, or a player's private play settings, is not activity. (r62)
- **LOB-182** The cleanup's deleting removes everything of the game or pod but never the history records; a game list on another device drops a deleted game when it next refreshes. (r62)
- **LOB-134** Three days before the cleanup (LOB-133) deletes a game in play or a pod (day 11 of 14 without activity), both players get a Game activity notification "<game> · vs <opponent>": "Removed in 3 days if nobody plays", or every member a pod notification "<pod>: Removed in 3 days if nobody picks or plays"; tapping it opens the game or pod. (r58)
- **LOB-187** The cleanup warning comes once per stretch without activity (activity resets the clock and allows a later warning). Finished games, lobbies and invitations go without a warning. (r58)
- **LOB-261** Nightly (at 01:00 UTC) the server deletes the answered game requests older than 30 days: the requests it stored (every move, chat message or lobby change a device sends, kept with its answer so a resent request isn't done twice) whose answer came 30 or more days ago; a device resends a request only until it learns the answer (SYS-014). A request still waiting for its answer, or for the answer to reach its device, is kept, and so is one a chat message or a match's start or end is stored under. Nothing a player sees changes. (r78)
