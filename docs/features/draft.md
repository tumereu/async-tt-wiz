# Drafting

Cubes, pods of 2–8 players drafting asynchronously, the pick and grid screens, Build, Stats, and pod games.

### Cubes

- **DRF-001** Manage decks has two tabs, Decks | Cubes; the Cubes tab lists "CUBES (N)" with a New cube button at the top right. (r21)
- **DRF-002** A cube is a saved, named, counted card list, synced across your devices; drafting never uses it up and many pods can share one cube. (r16)
- **DRF-003** The cube editor is the deck import screen: cube name and an "Include sideboard" checkbox (off by default) on one row, the card list, and one full-width Save. (r21)
- **DRF-004** A pasted cube list is checked by itself shortly after you stop typing; there is no separate "Import list" button. (r20)
- **DRF-005** Cube lists accept every Cube Cobra export (plaintext, MTGO, Forge, XMage, CSV) and bare card names (one copy per line); maybeboard rows are skipped. (r19)
- **DRF-006** A name shared by different cards does not block a cube: one is picked and listed with the other substitutions before saving; unknown cards block saving and name the bad lines. (r19)
- **DRF-007** A cube holds at most 3000 copies; the total copy count shows before saving. (r16)
- **DRF-008** The top bar's "Import from link" dialog takes a Cube Cobra link or cube ID found anywhere in pasted text (https/www optional, query ignored); its paste icon pastes and imports at once. (r20)
- **DRF-009** A Cube Cobra import takes the mainboard only and replaces the list; an unnamed cube gets the Cube Cobra ID as its name; failures say why. (r19)
- **DRF-010** The Saved cube screen shows the list with Edit at the top, a separate Rename, Delete cube (confirmed), and "Draft this cube", which opens New pod with that cube chosen. (r21)

### Pods list and navigation

- **DRF-011** The main menu has a Pods entry between Games and Manage decks; the Pods screen has a New pod button at the top right. (r21)
- **DRF-012** The Pods screen lists pod invitations with Accept and Decline at the top, then "MY PODS (N)" with each pod's avatars, name and status. (r16)
- **DRF-013** Pod statuses read "Your pick · 2 packs", "Build your deck", "Setup", "Drafting" or "Deck submitted"; pods needing you come first and "Your pick" rows are highlighted. (r16)
- **DRF-014** The main menu's Pods panel lists up to 3 pods: your pick or build first, then other pods, then invitations (answered in place); it is hidden when empty. (r17)
- **DRF-015** The side menu lists every pod under Pods with a compact stack of the other members' avatars, the name and a two-line status. (r23)

### Pod setup

- **DRF-016** New pod and pod setup use the same complete settings form; creating needs a name, a source and valid settings, with a full-width Create fixed at the bottom. (r17)
- **DRF-017** The pod name field has a shuffle button that suggests another random fantasy name. (r16)
- **DRF-018** The source shows as its name (or "No source") with an edit button opening a Source dialog with searchable Cubes | Sets tabs and a Manage cubes button. (r17)
- **DRF-019** Choosing a source closes the dialog and shows it at once; getting it ready finishes in the background, and a failure restores the previous source. (r17)
- **DRF-020** Mode chips Booster, Glimpse and Grid load that mode's default settings, keeping the sideboard limit and Librarians (Grid always has no Librarians). (r16)
- **DRF-021** Defaults: Booster 3 packs × 15 cards, keep 1, burn 0; Glimpse 9 packs × 15, keep 1, burn 2; both discard at 0, 1 pack open at once; Grid 18 grids, 1 open at once. (r16)
- **DRF-022** Settings are square tiles (small label, big value); tapping one opens a dialog that saves only valid in-range values, with a short explanation of the setting and its limits. (r29)
- **DRF-023** Limits: packs 1–30, pack size 1–30, keep at least 1, keep + burn at most the pack size, open packs up to the pack count; grids 1–60. (r16)
- **DRF-024** The pack size tile shows only for cubes and for set packs without a booster recipe; set recipes fix their own size. (r16)
- **DRF-025** Sideboard limit is a tile, default unlimited (∞), with a square ∞ toggle inside its dialog; 0 is allowed. (r17)
- **DRF-026** "Color balancing" is a square On/Off toggle tile for cube Booster/Glimpse, default on: each pack gets a card of every colour when the cube allows. (r19)
- **DRF-027** "Fresh boosters" toggle tile (default on) is always shown and greyed out unless the pod drafts grids from a set. (r29)
- **DRF-028** Grid has a "Two picks each" option, only for 2 players. (r16)
- **DRF-029** Only the creator edits name, source and settings; members see them read-only. Edits apply at once without disabling anything and save in the background. (r17)
- **DRF-030** Setup lists members and pending invitations with "Members (N/8)" (Grid: N/4); the creator invites through the shared player picker. (r17)

### Presets

- **DRF-031** A Presets button on New pod and on the creator's pod setup opens a dialog listing saved presets, with search; there are no preset chips. (r31)
- **DRF-032** A preset stores the draft settings and source under a name; rows read like "Booster · My cube · 3×15", and a deleted cube shows "Cube missing". (r31)
- **DRF-033** "Save current settings as preset…" asks a name (up to 60 characters); an existing name asks "Replace preset"; rows can be renamed and deleted (confirmed). (r31)
- **DRF-034** Presets are saved on the server, so they appear on all your devices; up to 200 presets. (r31)

### Starting, leaving and cancelling

- **DRF-035** The creator's "Start draft" needs at least 2 accepted members, settings that fit them and a source with enough cards; there is no Ready step. (r16)
- **DRF-036** While invitations are pending the button is "Start when everyone accepts"; armed, it reads "Starts when N more accept · Cancel" and members see "Starts automatically when everyone accepts". (r21)
- **DRF-037** Auto-start begins the draft when the last invitee accepts; a decline or a failed start turns it off with a setup line like "Bob declined; auto-start is off" and a notification. (r21)
- **DRF-038** The pod ⋮ menu offers "Start now" while invitations are pending (confirmed; pending invitations are cancelled), Cancel draft while drafting (creator), and Destroy (creator) or Leave. (r21)
- **DRF-039** Leaving during the draft warns "This cancels the draft for all N players and discards all picks." and returns the pod to setup; the creator's Cancel draft does the same. (r16)
- **DRF-040** Cancelling keeps source, settings and remaining members; starting again deals a fresh shuffle from the same source. One completed draft per pod. (r16)
- **DRF-041** Leaving after the draft withdraws your unaccepted challenges; when only one member is left the pod is destroyed. Destroy is confirmed and removes the pod for everyone. (r16)
- **DRF-042** Games already started from a pod survive any leave, cancel or destroy and stay in the normal Games list. (r16)

### Draft rules (Booster and Glimpse)

- **DRF-043** Seating is random. Each round opens "open packs" packs per player; a round's packs all pass one way and the direction flips each round. (r16)
- **DRF-044** A pick takes "keep" cards into your pool and removes "burn" cards from the draft; burned cards go to nobody. A confirmed pick cannot be undone. (r16)
- **DRF-045** A pack with keep cards or fewer left is given to its receiver automatically; with keep + burn or fewer you choose only the keeps and the rest burn. (r16)
- **DRF-046** After a pick leaves "discard" cards or fewer in the pack, the rest are thrown away; 0 means never. (r16)
- **DRF-047** You open your next round's packs as soon as no pack of your opened rounds can come back to you for a pick. (r20)
- **DRF-048** You never see a next-round pack while a pack of your current round can still reach you; such packs wait hidden and send no "your pick". (r21)
- **DRF-049** Your queue is ordered by round, then fresher packs (fewer picks taken) first, then arrival; an arrival never replaces the pack you are looking at. (r22)
- **DRF-050** Cube cards are drawn without replacement for the whole draft; a cube too small for the settings blocks Start, and using every card exactly is fine. (r16)

### Cogwork Librarian

- **DRF-051** Booster and Glimpse have a "Librarians" tile, 0–10, default 0: each player starts with that many Cogwork Librarians in Side, not in packs. (r18)
- **DRF-052** The keep tray ends with a Librarian chip showing how many are left; tapping it adds a dashed, badged keep slot and puts that Librarian into the pack as a card. (r29)
- **DRF-053** A spent Librarian shows a book badge; you may keep it back, burn it or leave it to pass on, also on a pack's last pick; Refund in its menu (or tapping its slot) takes it back. (r29)
- **DRF-054** Spending a Librarian never changes the tray layout; with Librarians the keep tray is always at least 2 cards wide and Confirm reads "Confirm · 1 Librarian". (r19)
- **DRF-055** An unspent or later-drafted Librarian is an ordinary card for deckbuilding and can be spent again; automatic awards never spend one. (r21)

### Pick screen

- **DRF-056** Pack cards scroll in a grid, cards per row 1–15 (default 3 portrait, 5 landscape, saved per device and orientation); keep/burn trays and a full-width Confirm stay fixed below. (r17)
- **DRF-057** The first line above the cards reads "Round 2/6 · Pick 2 · from Matti", or "· your pack" before its first pick, and scrolls away with them. (r22)
- **DRF-058** Swipe right = keep, left = burn: the card stays put while a 70% see-through copy follows the finger sideways, tilting up to 8° and tinting green/red. (r17)
- **DRF-059** Only releasing at 30% of the card width or more marks the card; the copy then fades in place in 150 ms (no snap-back) and the card flashes and gets its border and badge in 200 ms. (r17)
- **DRF-060** Swiping a marked card the same way unmarks it, the other way switches it; marking more than allowed drops the earliest mark of that kind; with burn 0 left swipes do nothing. (r16)
- **DRF-061** Marked cards keep their place with a green/red border hugging the art on all sides and a ✓/🔥 badge; swiped cards draw above their neighbours. (r18)
- **DRF-062** Tapping a card opens a small menu: Keep, Burn, Unmark (as applicable), Refund Librarian, Inspect; there is no double-tap shortcut. (r30)
- **DRF-063** Holding a card still for 150 ms shows its big picture (the front only) until release; moving within 300 ms of it appearing closes it and continues the swipe or scroll. (r30)
- **DRF-064** Only one card menu or preview is open at a time across Pick, Grid and Build; the latest tap or hold wins, also with several fingers. (r19)
- **DRF-065** The keep and burn trays sit on one row when they fit, sharing spare width in proportion, with an icon + "x/y" chip in the corner; they pan sideways when full; tapping a tray card unmarks it. (r19)
- **DRF-066** The burn tray hides when no burns are needed; automatic burns near a pack's end appear only after all keeps are marked. (r17)
- **DRF-067** Confirm is enabled only when the counts are exactly right; after it the next queued pack shows at once while the pick sends in the background. (r16)
- **DRF-068** A refused pick restores that pack and its marks with a short message; an unconfirmed one offers Retry. (r16)
- **DRF-069** Your keep, burn and Librarian marks are kept when you switch tabs or close the app (saved on the server about half a second after the last change). (r22)
- **DRF-070** Card pictures for the whole shown pack and the next queued pack start downloading at once; missing pictures keep a named placeholder and retry forever. (r19)
- **DRF-071** With no pack to pick, a faded centred text names who you wait for, then "N packs will come back to you" or "All your picks are done", then "Round x of y", each on its own line (Grid: "Waiting for <names> · Round x of y"). (r22)
- **DRF-072** Under the waiting text a borderless "Main menu" link leaves the pod; there is no Build button there. (r20)

### Pod header and tabs

- **DRF-073** A started pod is a play screen: a 48 dp header with a hamburger opening the side menu as a modal (never a permanent side area), a tiny pod title and 28 dp member avatars under it. (r20)
- **DRF-074** While drafting each avatar has a badge: a green ✓ when that member can't pick, otherwise how many picks they can make in a row (next-round packs they would open included), "9+" above 9. (r32)
- **DRF-075** A cogwheel at the header's end opens a floating panel with every view setting, applied live: "Draft · cards per row" (not in Grid), "Build · columns", "Build · cards side by side". (r21)
- **DRF-076** Tabs are Draft · Build · Stats while drafting and Build · Stats · Games afterwards, with the pod ⋮ menu as a narrow last tab; Back leaves the pod. (r18)
- **DRF-077** A pod opens on Draft while drafting, on Build until your deck is submitted, then on Games. (r17)
- **DRF-078** Pod screens refresh quietly in the background (about every 10 s while open), with no loaders over shown data. (r17)

### Grid draft

- **DRF-079** Grid is Draftmancer's: 2–4 players, 3×3 grids; on your turn take every card of one row or column; the starter rotates each grid; with 3–4 players the first pick's line is refilled. (r16)
- **DRF-080** All grids are dealt at the start with their own refill cards; you open your next grids as soon as none of your open grids can come back to you; grids of unopened rounds stay hidden. (r30)
- **DRF-081** Every grid you have seen shows as dealt with cards in place: your current pick first, then other open grids, then finished grids (latest first). (r30)
- **DRF-082** Each grid has a header: "Your pick · Grid 4/18", "Your pick next · Grid 6/18", "Waiting for <name> · Grid 5/18" or "Finished · Grid 3/18". (r30)
- **DRF-083** Only the grid you pick from has arrows beside each row and above each column; tapping one tints that line, slightly dims the other cards, and Confirm takes it. (r31)
- **DRF-084** Tapping a grid card inspects it; holding shows the same 150 ms release-to-close preview as the pick screen. (r30)
- **DRF-085** Each pick shows as a coloured band along its row or column (yours in your table colour, others in opponent colour, then blue and amber) plus the picker's small avatar at the top left of the card art. (r34) (?)
- **DRF-086** Discarded cards of a finished grid are dimmed and refill cards are marked "New"; a refilled grid shows its "Before refill" and "After refill" boards framed together. (r34) (?)
- **DRF-087** Grid boards and everyone's picks of grids you have seen are public to the pod. (r16)

### Set boosters

- **DRF-088** The Sets tab lists draftable sets only (expansion, core, masters, draft innovation), already released, newest first, searchable by name, code or release date. (r17)
- **DRF-089** Choosing a set downloads its card file once and keeps it; sets from the last ~60 days refresh automatically and every downloaded set has a Redownload button. (r16)
- **DRF-090** Set packs follow the set's Play Booster, else Draft Booster, else default recipe; without one they are approximate (10 commons, 3 uncommons, 1 rare or mythic, 1 any). Set packs never run out. (r16)
- **DRF-091** With Fresh boosters on, every grid from a set gets its own booster and nine random cards from it. (r29)

### Build

- **DRF-092** Build is one scrolling page with Main, Side and Trash sections (green, blue and red headers with counts); nothing sticks to the top, the deck name and headers scroll away. (r22)
- **DRF-093** Build works during the draft too: new picks land in Main without moving anything else, and every change is saved on the server. (r16)
- **DRF-094** The Main header reads "Main X + Y = Z" (nonland cards + all lands incl. basics); Side uses the same split once it holds lands. (r23)
- **DRF-095** Columns sit side by side filling the width: Lands, then mana value 0, 1, 2 … with the last column "and above"; 2–10 columns, default 7, saved per device and orientation. (r16)
- **DRF-096** "Build · cards side by side" puts 1–3 cards next to each other in every column, default 1 in both orientations. (r22)
- **DRF-097** Column labels show the column's card count on a line under the label; columns are faintly tinted, cards whole (never overlapped), 1 dp apart, with wide side margins. (r23)
- **DRF-098** Duplicates combine into one card with a ×N badge; actions move one copy. Within a column cards sort W U B R G, multicolour, colourless, then name. (r16)
- **DRF-099** Swipes: Main right→Side, left→Trash; Side right→Main, left→Trash; Trash right→Main, left→Side. (r20)
- **DRF-100** While swiping the card stays put and a 70% see-through tilted copy tinted in the destination colour follows the finger; under 20 dp it springs back. (r20)
- **DRF-101** A moved card fades out where it was and in at its new place while others slide (~250 ms); a brief chip like "→ Side" confirms the move. (r20)
- **DRF-102** Tapping a card opens a menu with its two destinations and Inspect; after submitting the moves are disabled but Inspect works. Holding previews the card like the pick screen. (r30)
- **DRF-103** A W/U/B/R/G basics row under Main (and one under Side) opens one dialog with five −/+ steppers and Done; basics are unlimited and not drafted. (r23)
- **DRF-104** New drafts with an unlimited sideboard start with 17 of each basic in Side; Side basics count toward a finite sideboard limit. (r23)
- **DRF-105** The deck name row has a pen button; the name (default "<pod name> deck") is private to you and can be changed even after submitting. (r17)
- **DRF-106** A fixed bottom bar holds an Undo button (always there, dimmed when empty) and "Submit deck (N)"; Undo takes back card moves and basic changes one at a time, during and after the draft. (r23)

### Build review (one by one)

- **DRF-107** Tapping a column label (like "2" or "Lands") or a Main/Side header ("Review ›") opens a full-screen one-by-one review of those cards; Trash has no review. (r22)
- **DRF-108** In the review, swipe right keeps the card where it is and left moves it one tier down (Main → Side, Side → Trash), committing at 30% of the card width; tapping flips double-faced cards. (r22)
- **DRF-109** The review shows "Main · MV 2"-style position and the Main count, Undo at the top left and a close button at the top right; system Back also closes it. (r24)
- **DRF-110** After the last card the review closes itself and a summary chip like "Kept 3 · 2 to Side" shows on Build for about 2 s; review moves can be undone with Build's Undo. (r24)

### Stats

- **DRF-111** Stats covers Main including basics: a mana curve 0–6 and 7+ of nonland cards stacked creature/noncreature with totals, and "Creature / Noncreature / Land" counts. (r18)
- **DRF-112** A type row counts Main cards per word of the front type line (e.g. Legendary, Creature, Basic, Land), showing only words present, most common first. (r32)
- **DRF-113** Type row details: hidden when Main is empty, words as printed, each a grey pill with a bold count. (r32) (?)
- **DRF-114** A table shows Pips and Land sources per colour W U B R G as plain numbers (half pips like "4½"), plus "Lands without a color: N". (r23)
- **DRF-115** Land sources count only lands: basic land types and the lands' "Add" text; nonland sources are never counted. (r23)
- **DRF-116** Under it, cards are grouped by their coloured mana symbols ("3× {R}{R}"), with "N× ∅" for cards without coloured pips. (r26)
- **DRF-117** A Colors donut shows Main nonland cards by colour (W U B R G, multicolour, colourless) with percentages. (r18)
- **DRF-118** Stats has private deck notes (up to 2000 characters) and eight tally counters (W U B R G, colourless, gold, grey; 0–99), saved for this draft and visible only to you. (r23)

### Submitting and pod games

- **DRF-119** Submit is possible once the whole draft is done: Main at least 40 cards incl. basics, Side within the sideboard limit, at most 1000 cards; "Submit deck?" is confirmed. (r16)
- **DRF-120** Submitting locks Main, Side and basics; the bar then shows "✓ Submitted". Trash is left out of pod games. Members submit independently. (r17)
- **DRF-121** The Games tab lists members as "deck submitted" or "building" and, once yours is submitted, a crossed-swords Challenge button beside every other member, then this pod's games. (r24)
- **DRF-122** Challenging a member who is still building is allowed: they see the invitation at once and can decline it, but can accept only after submitting; you can prepare and get Ready meanwhile. (r30)
- **DRF-123** A challenge is a normal game invitation; incoming ones are answered in place in the Games tab. Any two members may play any number of games. (r30)
- **DRF-124** In a pod game's lobby each deck is fixed to its pod deck (no saved-deck choice); Main and Side each get basic land steppers; Ready checks 40 Main cards and the sideboard limit. (r23)
- **DRF-125** A pod deck's name is shown only to its owner unless they switch on "Show deck name & art" for that game. (r31)

### Game preparation

- **DRF-126** Preparing a deck shows the cards as pictures on one scrolling page: green Mainboard and blue Sideboard headers with counts, cards grouped by type. (r24)
- **DRF-127** Type groups go Creatures, Planeswalkers, Battles, Instants, Sorceries, Artifacts, Enchantments, Lands, Other; empty groups are left out, cards sorted by name, ×N badge for copies. (r24)
- **DRF-128** Preparation has a columns setting 1–10, default 3, saved per device and orientation. (r31)
- **DRF-129** Swiping a card sideways (20 dp) moves one copy to the other section; tap opens "Move one to …", "Move all N to …"/"Move to …" and Inspect; hold previews until release. (r24)
- **DRF-130** A short fading line reports each move ("2× Opt → Sideboard"); when disconnected it reads "Disconnected · preparation is read-only". (r23)
- **DRF-131** A round floating Undo at the bottom left, shown only when there is something to undo, takes back moved cards and basic changes. (r23)

### Draft decks

- **DRF-132** Every pod deck you submit is saved as a draft deck under Decks ("DRAFT DECKS (N / 20)"); only the newest 20 are kept. (r31)
- **DRF-133** A draft deck is read-only and playable in any game (listed under "Draft decks" in the deck picker); its Side holds the rest of your pool, Trash included, with no sideboard limit. (r32)
- **DRF-134** "Save as deck" copies a draft deck into an ordinary deck you can edit and keep for good. (r31)

### Notifications

- **DRF-135** Draft notifications use their own "Draft activity" channel, one per pod, updated rather than stacked. (r16)
- **DRF-136** "Your pick — <pod name>" comes only when a pack (Grid: your turn) arrives while none were waiting; more packs only update the counts. (r16)
- **DRF-137** Other pod notifications: "New pod invitation", "Draft complete: build your deck", "Decks ready — <pod name>" (to everyone but the last submitter), "Draft cancelled", "Pod destroyed", "Auto-start off — <pod name>". (r21)
- **DRF-138** A pod's notification clears, and none is posted, while that pod is open in the app; pod invitation notifications likewise while the Pods list is shown. (r19)

### Privacy

- **DRF-139** Each member sees only their own packs, pool, build and pod deck; others see status and counts only. The creator has no extra view. (r16)
- **DRF-140** The server owns seating, shuffling, packs, queues, awards and progress; retried requests never double a pick, submit, start, leave or challenge. (r16)
