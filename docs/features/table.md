# Table

The battlefield, how cards look on it, how you touch them, their menus and inspecting cards. Automatum keeps the game: nothing on the table is changed by hand.

### Battlefield layout

- **TBL-001** Both battlefields and the combat band between them form one surface that pans and zooms together; hands, piles, life and counters stay fixed-size around it. (r2)
- **TBL-002** Battlefield cards are 28×40 dp at normal size on a 42 dp column pitch; tapped cards rotate in place and neighbours never overlap, in any mix of tapped cards and also mid-rotation. (r10)
- **TBL-003** Each battlefield has at least three rows and grows when cards sit lower; a row is card height × card size × zoom plus about 10% margin above and below, so cards never stick out over zone edges. (r14)
- **TBL-004** The row next to each player's own edge is the land row; the rows toward the middle hold everything else. (r34)
- **TBL-120** Each player's mana pool shows, while it isn't empty, as a small white pill of mana symbols (as drawn in card text, in WUBRG order, then colourless; more than 8 mana shows each kind once with its count, "×4") at the left of their battlefield on the line between their land row and the next row, live and in the timeline. (r47)
- **TBL-005** The battlefield feels endless: no field edge is visible anywhere you can pan, and its background covers the whole pan area on both sides. (r31)
- **TBL-006** Drags may drop a card in any column, including left of where cards normally start; automatic placement still takes the first free spot from column 0 rightward. (r31)
- **TBL-007** Cards Automatum puts onto the battlefield go to the land row only if they are lands; everything else goes to the first free spot of the main row. (r46)
- **TBL-008** Face-down cards put onto the battlefield always go to the main row. (r34)
- **TBL-009** In landscape the piles, life, counters and Stack sit in a left sidebar; in portrait they sit above and below the battlefield. (r2)

### Pan and zoom

- **TBL-010** Pinch zooms the battlefield between 0.65× and 2×; a two-finger drag pans it. (r2)
- **TBL-011** A one-finger drag that starts on empty battlefield pans it, as does a double-tap with the finger kept down; there is no selection rectangle. (r45)
- **TBL-012** A double tap on empty battlefield without dragging zooms to fit the whole table (empty columns are not counted). (r2)
- **TBL-013** You can pan up to 3 card slots past the outermost cards on the left and right. (r6)
- **TBL-014** Panning past the limit stretches like a rubber band and springs back on release without overshooting. (r3)
- **TBL-015** Each direction is limited on its own: content wider than the screen can't show past its ends, and narrower content can't be pushed partly off-screen. (r3)
- **TBL-016** A pan stays in the area where it started (battlefield, hand, side panel or Stack); pinching a scrolling area never zooms the battlefield. (r10)
- **TBL-017** Camera and zoom are personal; they never change where cards are for the other player. (r2)
- **TBL-121** In landscape the table opens zoomed out (never below 0.65×) so both battlefields' rows fit between the hands, column 0 still at the left edge; your first pan or zoom takes over from there. (r47)

### How cards look

- **TBL-018** Real cards are rectangles with a border in the owner's colour; tokens have cut corners in the owner's colour with the same size; Stack and combat copies have a dashed border. (r1)
- **TBL-019** Normal cards show the art crop in the art box, with the app drawing the name and P/T; custom-image cards show the whole image with no name and P/T on a small background in the corner; cards without art show a type icon. (r4)
- **TBL-020** Card names on the table shrink and clip to the card, never ending in "…". (r13)
- **TBL-021** P/T sits in the bottom-right corner; planeswalkers show loyalty there, battles their defense, and Sagas start at 1. A creature's corner shows the P/T Automatum works out instead (TBL-112). (r37)
- **TBL-022** P/T that differs from the printed value is drawn in brown (#A0522D); a creature whose P/T Automatum works out uses TBL-112's colours instead. (r49)
- **TBL-023** A card with an until-end-of-turn P/T change shows its P/T in teal with an hourglass ⌛ in front; a creature whose P/T Automatum works out keeps TBL-112's colours with the ⌛. (r49)
- **TBL-024** Damage shows as a red-outlined badge left of P/T; it fills red when damage reaches the card's toughness (a hint only). (r16)
- **TBL-027** A card that came onto the battlefield this turn shows its name in blue (#1A56DB), also after changing sides and for new tokens and clones; combat copies follow their card; full-image cards show no mark. (r29)
- **TBL-028** Face-down battlefield cards show the card back with a "?" name on a white chip, blue when the card arrived this turn. (r30)
- **TBL-029** Face-down battlefield cards' P/T and damage are visible to both players; they start as 2/2, and turning one face up resets P/T to printed. (r45)
- **TBL-030** Hidden cards (opponent's hand, face-down outside the battlefield) show no P/T. (r45)
- **TBL-031** Every tap on a card shows a short press border, so both taps of a double tap are visible; it rotates with the card, also mid-animation. (r45)
- **TBL-112** A battlefield creature (and its combat copies) shows the P/T Automatum works out from its printed values, counters and effects: green when up in one number and down in neither, red the other way round, brown when one is up and one down, the usual ink when unchanged, with ⌛ in front while an until-end-of-turn effect changes it. Loyalty, defense, Sagas and other non-creature corners keep their own values. While the board holds data Automatum can't read, cards show their own values. (r46)
- **TBL-113** What Automatum changed on a battlefield card besides P/T shows as one violet-text chip under its title (first 32 characters): a changed type line, changed colours, keywords it gained ("Flying") or lost ("no Flying"), and its counters ("+1/+1 ×2"). (r45)
- **TBL-033** Changes to a card's P/T flash a small change tag such as "+2" (changes close together add up; about 1 s, then a 0.5 s fade); owner and face changes briefly highlight the card. Both players' changes, live and in playback, never on jumps or catch-up. (r45)

### Card sizes

- **TBL-034** Settings has a Card size slider (it also scales battlefield cards, separately from zoom) and a "Customize per area" switch that shows separate Hand, Piles, Stack and Panels sliders; saved on the device. (r13)
- **TBL-035** Settings has a Card text size slider and a "Customize per text" switch with Title and "P/T & counters" sliders. (r46)
- **TBL-036** Card and text size sliders run from 0.5× to 3×; tapping the value lets you type it within those limits, and a live sample card shows the result. (r14)

### Animations

- **TBL-037** Tapping is a plain 90° clockwise turn about the card centre in 90 ms; untapping turns it back the same way. (r5)
- **TBL-038** Moving cards fly at a steady speed (1.2 dp/ms by default, easing out, at most 350 ms, so short moves arrive early); a "Card move speed" setting scales it from 0.25× to 3×. (r14)
- **TBL-039** Turning, moving, flipping and fading each take their own natural time; a scene lasts as long as its longest part. (r15)
- **TBL-040** Every card move animates, yours and the opponent's, live and in playback, including moves between piles (library, graveyard, exile) on either side; hand cards slide aside when cards join or leave. (r51)
- **TBL-125** When one board brings several steps (your action and the passes, resolutions and triggers that follow by itself), each step that moves, taps, makes or turns something plays in event order, one after another, at the card move speed with no playback pause; a token made by a resolving Stack entry flies out of it. Until your action's outcome is in, its own board shows and the panels (whose move, the waiting bar) don't change. (r51)

### Gestures

- **TBL-041** A single tap on a card acts at once (opens its menu, raises a hand card, activates a battlefield card's ability or opens its ability chips, TBL-122), never waiting for a possible double tap. (r52)
- **TBL-042** A double tap is two taps within 400 ms and 32 dp of each other; it still works when the first tap already opened a menu. (r5)
- **TBL-043** The second tap of a double tap on a battlefield card does nothing more than its first tap did (TBL-122): no shake, nothing tapped by hand (SYS-069). (r52)
- **TBL-122** One tap activates: tapping your battlefield card with exactly one ability usable now (mana abilities and special actions too) activates it as its menu row would (TBL-126): mana goes to your pool at once and you keep priority; costs, targets or choices go through the cast flow (STK-023). With several usable abilities the tap opens their chips (TBL-124); with none, or while a cast or choice is open or the board is still being worked out, the menu (TBL-115). While attackers or blockers are declared a tap only stages combat (CMB-047). (r52)
- **TBL-124** With several abilities you can activate now, tapping the card opens a row of chips above it (below it with no room above), like a hand card's (ZON-080): one white, violet-outlined chip per ability with its effect ("Add {G}", "Draw a card") and cost ("{2}{G}, {T}") in mana symbols. Tapping a chip activates it; holding one shows the card's name and the ability's whole text in a card-like popup. A tap elsewhere or on the card again closes them. (r52)
- **TBL-044** Only battlefield cards can be tapped; a card leaving the battlefield untaps, and double taps never turn hand cards or cards elsewhere. (r12)
- **TBL-047** Holding a card for 350 ms inspects it (TBL-104) without changing anything; moving the finger first starts a drag instead, and an open preview never turns into a move. (r2)
- **TBL-048** Holding empty hand space shows nothing, and holding a library never reveals its cards. (r29)

### Dragging and battlefield piles

- **TBL-051** Dragging a card does what the drag means to Automatum (ZON-077); one card is dragged at a time. (r45)
- **TBL-052** A drag shows a lifted card and highlights where it will land; a cancelled or invalid drop puts the card back and records nothing. (r45)
- **TBL-053** Dropping a card onto another battlefield card puts it on top of that card's pile; a pile holds at most 3 cards, and a drop that would make 4 shows as invalid and does nothing. (r30)
- **TBL-054** Cards in a pile peek out diagonally toward the top-left and keep their own tapped and face-down state; dragging a member onto its own pile brings it to the top, dragging it to empty space splits it off. (r2)

### Card menus

- **TBL-064** Card menus (battlefield, combat and panel cards) put their buttons in a grid of 5 columns of 48 dp cells, each an icon plus one short label; fewer buttons just make fewer rows. Labels wrap only between words: where a word or the label's two lines don't fit the cell (large system text), the label is drawn smaller instead of breaking or being cut; the quick-actions cells do the same. Holding a button shows its full name (SYS-045). (r16)
- **TBL-067** The menu opens on the side of the card with the most room, preferring above your own cards and below the opponent's. Card menus and the other table popups (piles, life, the Stack list) keep off both hands wherever they fit elsewhere: a menu of your Library, say, opens just above your hand instead of over it. (r47)
- **TBL-068** A tap outside the menu closes it and still goes through to the table (double taps and drags work); only buttons and chips count as inside, gaps do not. Back also closes it. (r11)
- **TBL-069** A press that starts within 280 ms of the menu opening does nothing, so a fast second tap cannot hit a button by accident. (r8)
- **TBL-071** Each kind of card has one fixed one-page menu (TBL-116): battlefield cards, combat copies and Graveyard or Exile panel cards; a hand card has no menu (ZON-079). Stack entries have no card menu: tapping the Stack opens the Stack list (STK-018). (r45)
- **TBL-115** A card's menu (TBL-071) starts with one full-width row with a violet icon per thing Automatum offers for it: its casts ("Cast", or "Cast · " and the way, such as a flashback or an Adventure), a land's "Play", then each activated ability by its printed text ("{T}: Add {G}."), mana abilities and special actions included. Rows allowed now are enabled, the rest greyed (TBL-123); a combat copy shows its real card's rows; "Automatum is working out what this card can do…" shows greyed while it still does. (r44)
- **TBL-123** A greyed menu row shakes when tapped; holding it shows why: "Bob holds priority now.", "Only in your main phase while the Stack is empty.", "A loyalty ability: once each turn, in your main phase while the Stack is empty.", "Already used as often as allowed this turn.", "Storm Spark can't be cast now." or "Grovewatch Druid: Automatum doesn't allow that now (a cost it can't pay, or nothing to target)."; while a cast or an Automatum choice is open, its own message (STK-025, STK-032). The other player's cards list their abilities greyed ("Only its controller can do that."). (r44)
- **TBL-126** A menu row is used like a cast: the cast panel when there is a real choice (STK-023; Autotap pays), with the ability's text and "Activate", then one step. An ability that goes on the Stack is then passed on by its player as after a cast (STK-026); a mana ability or special action without choices goes out at once and its player keeps priority (in either player's turn), unless it set off a trigger, which is passed on as after a cast. (r44)
- **TBL-116** Below the rows a card's menu has only the combat draft: a combat copy's Remove from combat (CMB-028). Battlefield and panel cards have nothing below the rows: no Inspect or Other side (holding a card inspects it, TBL-104), no Declare and no "Attack →" (CMB-048). A card whose menu would hold nothing opens no menu: the tap does nothing. The menu has one page. (r52)
- **TBL-117** Cards Automatum lets you use now (cast, play, or activate an ability other than a mana ability, special actions included) glow softly in cyan from behind, exactly as it allows at that moment: never the other player's cards, nor a permanent whose only use now is mana. Hand, battlefield and panel cards glow; nothing glows in the timeline, the opening or while a cast or choice is open. Glows fade in or out in 0.22 s and stay while the next board is worked out, so they never blink. (r51)
- **TBL-118** A gesture Automatum doesn't allow says nothing: the card, pile or life square it was made on gives a small, brief sideways shake (0.16 s, three swings of at most 3 dp; every shake in the app is this one, greyed controls too). A refusal inside an open cast panel is said in the panel (STK-024); failures that aren't Automatum's refusals (offline, still working out) keep their message. (r52)
- **TBL-128** What shakes (TBL-118): a card that can't be cast or played now (ZON-079), tapping a card by hand, a manual drag (ZON-077; a card dropped back where it was doesn't), a Library with nothing to offer (ZON-078), a tap on a non-target while targets are picked (STK-047), actions refused while your cast is open (STK-025) or while the other player chooses (STK-032), and a cast dropped because its card can't be cast or paid for now or the table changed. (r52)
- **TBL-119** There are no virtual-tabletop controls, because Automatum keeps the game: no notes, pings, text marks on Stack entries, links, colour counters on cards or players, statuses, dice or coins, tokens or combat areas made by hand, Temp piles, selection or interaction modes. Chat, holding to inspect (TBL-104), the timeline and settings stay. (r52)

### Card state rules

- **TBL-095** Damage and until-end-of-turn P/T clear from every card when the turn passes and when a card changes zone; moving between battlefields keeps them. (r46)
- **TBL-097** The owner is whose deck the card came from; the controller is whichever battlefield it is on. (r45)

### Tokens

- **TBL-101** A new token joins a pile of identical, untouched tokens (untapped, face up, no counters, not in combat) that has fewer than 3 cards, fullest first; otherwise it starts a new pile with normal placement. (r30)
- **TBL-102** New non-land tokens and copies appear in the main row; land tokens in the land row. (r30)
- **TBL-103** A token moved to a graveyard, exile or library stops existing. (r28)

### Inspecting cards

- **TBL-104** Inspect opens only by holding a card (TBL-047) or a Stack ability (STK-050), wherever cards show: the table, hands, the Stack and its list, piles and panels, choice screens, the cast panel, draft screens, deck building and preparation, search and card lists; no menu has Inspect. It shows only card images over the dimmed screen, each as large as fits (in portrait one under another, in landscape side by side), and closes on release. (r52)
- **TBL-127** While inspecting, moving the finger up or down scrolls the images at least as far as the finger moves, sped up so that a drag over 90% of the screen height crosses the whole view; movement past either end is not stored, so turning back scrolls at once. (r52)
- **TBL-105** The hold preview works on every face-up card, live and in timeline mode, and holding pauses timeline playback. (r8)
- **TBL-106** Inspect shows every face of a card you see face up (or hold in your own region): one picture per side of a double-faced card (transform, modal double-faced, meld, battle, double-faced token), the face shown on the table first; a card whose parts share one printed image (split, adventure, flip, aftermath, Room) shows it once, a flipped flip card upside down. A face whose picture isn't downloaded shows the placeholder card with its name. (r52)
- **TBL-108** Split cards, Rooms, Planes and Battles turn sideways when inspected; sideways cards are no taller than upright ones (at most 448 dp). Aftermath and flip cards show upright; Inspect has no Rotate button. (r52)
- **TBL-109** Inspecting your own face-down card shows its front dimmed with a "Face down" label; inspecting never shows a hidden side to the opponent, who sees a "Covered card" tile. (r52)
- **TBL-111** Holding a card that lies on top of other cards in a battlefield pile (TBL-053) inspects it and then each card under it, in pile order, in the same scrolling view (one under another in portrait, side by side in landscape); each shows only what the table shows of it (a face-down card stays hidden from the opponent). (r52)
