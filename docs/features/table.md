# Table

The battlefield, how cards look on it, how you touch them, their menus, multi-select, tokens and links.

### Battlefield layout

- **TBL-001** Both battlefields and the combat band between them form one surface that pans and zooms together; hands, piles, life and counters stay fixed-size around it. (r2)
- **TBL-002** Battlefield cards are 28×40 dp at normal size on a 42 dp column pitch; tapped cards rotate in place and neighbours never overlap, in any mix of tapped cards and also mid-rotation. (r10)
- **TBL-003** Each battlefield has at least three rows and grows when cards sit lower; a row is card height × card size × zoom plus about 10% margin above and below, so cards never stick out over zone edges. (r14)
- **TBL-004** The row next to each player's own edge is the land row; the rows toward the middle hold everything else. (r34)
- **TBL-005** The battlefield feels endless: no field edge is visible anywhere you can pan, and its background covers the whole pan area on both sides. (r31)
- **TBL-006** Drags may drop a card in any column, including left of where cards normally start; automatic placement still takes the first free spot from column 0 rightward. (r31)
- **TBL-007** Cards put onto the battlefield from a menu (not dragged) go to the land row only if they are lands; everything else goes to the first free spot of the main row. (r34)
- **TBL-008** Face-down cards put onto the battlefield always go to the main row, and a menu move of several cards puts each on the first free spot of its own row. (?) (r34)
- **TBL-009** In landscape the piles, life, counters and Stack sit in a left sidebar; in portrait they sit above and below the battlefield. (r2)

### Pan and zoom

- **TBL-010** Pinch zooms the battlefield between 0.65× and 2×; a two-finger drag pans it. (r2)
- **TBL-011** Double-tap on empty battlefield and keep the finger down to pan with one finger. (r2)
- **TBL-012** A double tap on empty battlefield without dragging zooms to fit the whole table (empty columns are not counted). (r2)
- **TBL-013** You can pan up to 3 card slots past the outermost cards on the left and right. (r6)
- **TBL-014** Panning past the limit stretches like a rubber band and springs back on release without overshooting. (r3)
- **TBL-015** Each direction is limited on its own: content wider than the screen can't show past its ends, and narrower content can't be pushed partly off-screen. (r3)
- **TBL-016** A pan stays in the area where it started (battlefield, hand, side panel or Stack); pinching a scrolling area never zooms the battlefield. (r10)
- **TBL-017** Camera and zoom are personal; they never change where cards are for the other player. (r2)

### How cards look

- **TBL-018** Real cards are rectangles with a border in the owner's colour; tokens have cut corners in the owner's colour with the same size; Stack and combat copies have a dashed border. (r1)
- **TBL-019** Normal cards show the art crop in the art box, with the app drawing the name and P/T; custom-image cards show the whole image with no name and P/T on a small background in the corner; cards without art show a type icon. (r4)
- **TBL-020** Card names on the table shrink and clip to the card, never ending in "…". (r13)
- **TBL-021** P/T sits in the bottom-right corner; planeswalkers show loyalty there, battles their defense, and Sagas start at 1. (r16)
- **TBL-022** P/T that differs from the printed value is drawn in brown (#A0522D). (r15)
- **TBL-023** A card with an until-end-of-turn P/T change shows its P/T in teal with an hourglass ⌛ in front. (r16)
- **TBL-024** Damage shows as a red-outlined badge left of P/T; it fills red when damage reaches the card's toughness (a hint only). (r16)
- **TBL-025** A card note shows as a white chip with its first 32 characters on the art; an until-end-of-turn note is a separate chip starting with ⌛ in teal. (r16)
- **TBL-026** Card counters show as round badges in their colour (red, blue, green, yellow, purple, gray), centred over the art and in two rows when there are more than two; zero or empty shows nothing. (r15)
- **TBL-027** A card that came onto the battlefield this turn shows its name in blue (#1A56DB), also after changing sides and for new tokens and clones; combat copies follow their card; full-image cards show no mark. (r29)
- **TBL-028** Face-down battlefield cards show the card back with a "?" name on a white chip, blue when the card arrived this turn. (r30)
- **TBL-029** Face-down battlefield cards' P/T, counters, notes and damage are visible to both players; they start as 2/2, and turning one face up resets P/T to printed. (r19)
- **TBL-030** Hidden cards (opponent's hand, face-down outside the battlefield) show no counters, notes or P/T. (r19)
- **TBL-031** Every tap on a card shows a short press border, so both taps of a double tap are visible; the selection border rotates with the card, also mid-animation. (r11)
- **TBL-032** Linked cards (and linked Stack or combat copies) are joined by a gray line. (r12)
- **TBL-033** Changes to a card's P/T or counters flash a small change tag such as "+2" (changes close together add up; about 1 s, then a 0.5 s fade); note, owner, face and link changes briefly highlight the card. Both players' changes, live and in playback, never on jumps or catch-up. (r14)

### Card sizes

- **TBL-034** Settings has a Card size slider (it also scales battlefield cards, separately from zoom) and a "Customize per area" switch that shows separate Hand, Piles, Stack and Panels sliders; saved on the device. (r13)
- **TBL-035** Settings has a Card text size slider and a "Customize per text" switch with Title, Note and "P/T & counters" sliders. (r13)
- **TBL-036** Card and text size sliders run from 0.5× to 3×; tapping the value lets you type it within those limits, and a live sample card shows the result. (r14)

### Animations

- **TBL-037** Tapping is a plain 90° clockwise turn about the card centre in 90 ms; untapping turns it back the same way. (r5)
- **TBL-038** Moving cards fly at a steady speed (1.2 dp/ms by default, easing out, at most 350 ms, so short moves arrive early); a "Card move speed" setting scales it from 0.25× to 3×. (r14)
- **TBL-039** Turning, moving, flipping and fading each take their own natural time; a scene lasts as long as its longest part. (r15)
- **TBL-040** Every card move animates, yours and the opponent's, live and in playback, including moves between piles (library, graveyard, exile, Temp) on either side; hand cards slide aside when cards join or leave. (r31)

### Gestures

- **TBL-041** A single tap on a card opens its menu at once, never waiting for a possible double tap. (r8)
- **TBL-042** A double tap is two taps within 400 ms and 32 dp of each other; it still works when the first tap already opened a menu. (r5)
- **TBL-043** Double-tapping a battlefield card taps or untaps it; if it is selected, the whole selection turns to the opposite of the touched card's state and stays selected; if not, the selection clears and only that card turns. (r12)
- **TBL-044** Only battlefield cards can be tapped; a card leaving the battlefield untaps, and double taps on cards elsewhere never tap them. (r12)
- **TBL-045** Double-tapping a hand card plays it like a drag: a land (front face) goes to the land row; a card that is a permanent on every face goes to the Stack and then resolves to the main row as a separate timeline step; anything else goes to the Stack. (r15)
- **TBL-046** Double-tapping a library draws its top card; double-tapping a graveyard or exile pile opens its search panel. (r29)
- **TBL-047** Holding a card for 350 ms opens its preview without changing anything; moving the finger first starts a drag instead, and an open preview never turns into a move. (r2)
- **TBL-048** Holding empty hand space shows nothing, and holding a library never reveals its cards. (r29)
- **TBL-049** In timeline mode double taps do nothing (no error) and the selection is cleared with multi-select off. (r13)
- **TBL-050** Back first closes menus, dialogs and timeline mode, then clears the selection; on the bare table it takes a second Back within 2 s to leave, with the hint "Press back again to leave". (r4)

### Dragging and battlefield piles

- **TBL-051** Dragging a card moves it; dragging a selected card moves the whole selection, which stays selected after the move; dragging an unselected card clears the selection and moves only that card. (r2)
- **TBL-052** A drag shows a lifted card and highlights where it will land; a cancelled or invalid drop puts everything back, selection included, and records nothing. (r2)
- **TBL-053** Dropping a card onto another battlefield card puts it on top of that card's pile; a pile holds at most 3 cards, and a drop that would make 4 shows as invalid and does nothing. (r30)
- **TBL-054** Cards in a pile peek out diagonally toward the top-left and keep their own tapped and face-down state; dragging a member onto its own pile brings it to the top, dragging it to empty space splits it off. (r2)
- **TBL-055** Dropping a card on the other player's hand, library, graveyard or exile sends it to its owner's zone instead, and the drop preview shows that; either battlefield is fine. (r5)

### Multi-select

- **TBL-056** The interaction mode button sits at the end of your own hand row (48 dp × hand size, at least 40 dp); a tap cycles Normal → Select → Ping with no chooser. (r29)
- **TBL-057** A one-finger drag starting on empty table space draws a selection box; any card whose visible part it touches is selected, and the cards it would select light up while drawing. (r3)
- **TBL-058** In Normal mode a new box replaces the selection; in Select mode it adds to it. (r31)
- **TBL-059** In Select mode a single tap adds or removes that card from the selection. (r2)
- **TBL-060** Tapping empty table or empty hand space clears the selection without changing the mode. (r2)
- **TBL-061** In Select mode, double-tapping a selected card taps or untaps the selection, then clears it and returns to Normal mode. (?) code differs from spec D-281: no double tap in Select mode. (r16)
- **TBL-062** A menu opened on a selected card acts on the whole selection and shows the count; on an unselected card it acts on that card only and keeps the selection. The targets are fixed when the menu opens. (r12)

### Card menus

- **TBL-063** Every card menu is one floating popup: square, solid, bordered buttons with a light shadow on the board, with no sheet, title or close button. (r10)
- **TBL-064** Card menus (battlefield, hand, Stack, combat and panel cards) put their buttons in a grid of 5 columns of 48 dp cells, each an icon plus one short label; a menu with fewer buttons just has fewer rows. Holding a button shows its full name above it (below near the top edge), never under the finger. (r16)
- **TBL-065** Paged menus have a "◀ title ▶" header (arrows wrap; tapping the title lists the pages); swiping also changes page, and the last page is remembered per game. (r16)
- **TBL-066** A paged menu keeps room for its tallest page, so the header and buttons never jump when you change page. (r16)
- **TBL-067** The menu opens on the side of the card with the most room, preferring above your own cards and below the opponent's. (r4)
- **TBL-068** A tap outside the menu closes it and still goes through to the table (double taps and drags work); only buttons and chips count as inside, gaps do not. Back also closes it. (r11)
- **TBL-069** A press that starts within 280 ms of the menu opening does nothing, so a fast second tap cannot hit a button by accident. (?) (r8)
- **TBL-070** Menu buttons are always shown and greyed out when they don't apply; a button only changes when it has a twin for the card's state (Face down ↔ Face up, Hand ↔ Remove token, Declare ↔ Attack →). (r28)
- **TBL-071** Each kind of card has one fixed layout: battlefield cards have three pages (Everyday, Counters, More); combat copies and hand cards have one page each. Stack entries have no card menu: tapping one shows its Stack bubbles (STK-010), and Manage Stack holds their other actions (STK-019). (r35)
- **TBL-072** Menus offer the same actions on your opponent's cards as on yours; moves always go to the card owner's zones and labels show no player names. (r30)

### Everyday page

- **TBL-073** Everyday holds, in order: Inspect, Note, P/T, Stack, Declare, Face down/up, Switch face, Graveyard, Exile, Clone, Ping, Hand, Library top, Library bottom, Shuffle into library, EOT P/T, Battlefield. (r34)
- **TBL-074** "Stack" puts the card on the Stack; "Declare" declares it in combat without tapping it, and becomes "Attack →" once the card is a declared attacker. (r28)
- **TBL-075** On tokens, Hand becomes "Remove token", and Library top, Library bottom and Shuffle into library remove the token. (r28)
- **TBL-076** "Switch face" turns to the next face and works only on truly two-faced cards (transform, modal double-faced, meld, battle); adventure, split, flip and aftermath cards have one face. Switching either way also sets the corner to the new face's printed value (P/T, loyalty for planeswalkers, defense for battles, 1 for Sagas, empty when the face has none); a face-down card keeps its corner. One Undo takes back both. (r35)
- **TBL-077** Clone makes a token copy of the card; a non-land copy appears in the main row. (r30)

### Counters page

- **TBL-078** Counters has three rows, each with two colour counter controls and then Note, EOT note and Reset counters in the last column. (r18)
- **TBL-079** P/T, damage, EOT P/T and each counter are wide controls: − on the left, the value in the middle (tap to type it, drag to change it), + on the right; all six colours are there even with no counters. (r5)
- **TBL-080** Dragging a value control changes it along the stronger direction of the drag, with a bubble like "+3 → 23"; the first three steps need 33.6 dp each, then it speeds up; letting go sends one change. (r31)
- **TBL-081** Typing a value uses one square value field between − and + buttons. (r13)

### More page

- **TBL-082** More holds: Owner, Other side, Link, Unlink, single P/T step buttons, Reset P/T, until-end-of-turn P/T step buttons, Keywords, EOT keywords, Temp 1–4, Deal to target, Deal to target or creatures, and Set damage last. (r34)
- **TBL-083** P/T step buttons read "+/·", "−/·", "·/+", "·/−" (· = side unchanged); the until-end-of-turn ones carry a small hourglass; all stay visible, greyed out on cards without P/T. (r27)
- **TBL-084** Owner shows the owner's colour and is enabled only for battlefield tokens; tapping it gives the token to the other player. (r28)
- **TBL-085** Keywords and EOT keywords open a toggle grid of Flying, First strike, Double strike, Deathtouch, Lifelink, Trample, Haste, Vigilance, Reach, Menace, Hexproof, Indestructible, Defender, Ward, Can't block, Goaded; each toggle saves at once and the grid stays open. (r19)
- **TBL-086** Keyword toggles write the note as keywords joined by ", " (e.g. "Flying, Trample"); any other note text greys them out; with several cards a toggle is on only if all have it. They are greyed out on hidden cards. (r19)
- **TBL-087** Link is enabled only with 2 or more cards selected and links every pair of them, with no target picker; Unlink lists the card's links with a Remove link button on each. (r12)
- **TBL-088** "Other side" (Inspect other side) deliberately shows a card's hidden side and is recorded in the history; it is the only way an opponent sees a face-down card's front. (r21)

### Hand, combat and Stack menus

- **TBL-089** The hand card menu is one page: Inspect, Play, Face down, Reveal, Battlefield, Discard, Exile, Library top, Library bottom, Shuffle into library, Temp 1–4, Discard random, Reveal random, Exile random, Random to library bottom. (r28)
- **TBL-090** Hand "Play" does the same as double-tapping the card; "Face down" puts it on the Stack face down and it resolves straight onto the battlefield as a face-down 2/2. (r21)
- **TBL-091** The combat copy menu has Inspect, Note, Ping, Attack →, Remove from combat, Graveyard, Exile, Hand, Deal to target, Deal to target or creatures, Link and Links, and nothing that changes the real card (tap the real card for that). (r28)

### Card state rules

- **TBL-093** A card leaving the battlefield loses its notes, P/T changes, counters, links and owner changes, turns to its front face and untaps; face-down survives only a move to exile. (r13)
- **TBL-094** Buttons that change a card are greyed out outside the battlefield. Stack entries and combat copies can have their own notes and pings; combat copies also set damage and the until-end-of-turn marks on the real card (CMB-003). (r35)
- **TBL-095** Damage, until-end-of-turn P/T and until-end-of-turn notes clear from every card when the turn passes and when a card changes zone; moving between battlefields keeps them. (r16)
- **TBL-096** On hidden cards the menu shows "—" instead of values, but notes, damage and marks can still be edited without seeing them. (r30)
- **TBL-097** The owner is whose deck the card came from; the controller is whichever battlefield it is on; only the Owner button on tokens changes the owner. (r5)

### Tokens

- **TBL-098** Create token (from Table actions) lists all tokens alphabetically, Blank included; each row has small art, the name and a second line with P/T and keywords; hold a row to inspect it, and a search field filters the list. (r16)
- **TBL-099** Favourite tokens are saved on the server per account and listed first; until you choose your own they are Treasure, Food, Clue, Blood, Map and Gold. (r12)
- **TBL-100** The token list is prepared with the card records, so it opens and filters instantly; if favourites can't load it says "Favorites are offline; using saved favorites." (r13)
- **TBL-101** A new token joins a pile of identical, untouched tokens (untapped, face up, no counters, marks or links, not in combat) that has fewer than 3 cards, fullest first; otherwise it starts a new pile with normal placement. (r30)
- **TBL-102** New non-land tokens and copies appear in the main row; land tokens in the land row. (r30)
- **TBL-103** A token moved to a graveyard, exile or library stops existing. (r28)

### Inspecting cards

- **TBL-104** The hold preview is compact with no title; moving the finger up and down scrolls its text, mana and tap symbols are drawn as symbols, and it closes on release. (r3)
- **TBL-105** The hold preview works on every face-up card, live and in timeline mode, and holding pauses timeline playback. (r8)
- **TBL-106** Hold preview and Inspect open on the face the card shows on the table; a flipped flip card is turned upside down so its flipped half reads upright. (r31)
- **TBL-107** Inspect from the menu shows the full card image, with tidy "‹ n/m ›" buttons to switch faces on multi-faced cards. (r31)
- **TBL-108** Split cards, Rooms, Planes and Battles turn sideways when inspected; aftermath and flip cards get a Rotate button in Inspect only; sideways cards are no taller than upright ones (at most 448 dp). (r30)
- **TBL-109** Inspecting your own face-down card shows its front dimmed with a "Face down" label; ordinary holds and inspects never show a hidden side to the opponent. (r21)
- **TBL-110** Inspect shows the card's current P/T as "X/Y", and until-end-of-turn changes and notes marked "(until end of turn)". (r16)
