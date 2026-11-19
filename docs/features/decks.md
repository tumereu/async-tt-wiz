# Decks

The deck library, the deck and cube editor, saving card lists, and preparing a deck for a game.

## How it works

Manage decks keeps your saved decks and cubes, and a read-only draft deck for every pod deck you submit. A saved list
opens in a read-only picture view; Edit opens the editor that decks and cubes share, with a Cards tab of pictures and a
Text tab holding the raw list. A list can be typed, pasted or imported from an Archidekt link, and cards are added from
a search page that understands search terms (LOB-108). Saved lists hold each card by its printing and always show
it with the current card catalog's text. Saved decks can be sorted into categories, which can hold categories of their
own. A deck holding a card Automatum can't play yet carries an amber warning and can't start a game. Before a game,
Prepare deck moves cards between Mainboard and Sideboard for that game alone; the saved deck never changes.

## Manage decks

- **LOB-054** Manage decks has Decks and Cubes tabs and an "Import deck" button at the top right; the Decks tab shows "SAVED LISTS (N)" with cover, name and "N main / M side", and pull-to-refresh only (no background refresh). (r21)
- **LOB-055** Every pod deck you submit is kept as a draft deck in its own section under Decks ("DRAFT DECKS (N / 20)"; only the newest 20 are kept). (r60)
- **LOB-210** A draft deck is read-only and playable in any game (under "Draft decks" in the deck picker); its Side holds your pod deck's Side (with its basics) and its Maybe your Build's Maybe, with no sideboard limit. "Save as deck" copies it into an ordinary deck you can edit and keep for good. (r60)
- **LOB-058** Each deck has a cover chosen from its own cards, the choices listed alphabetically; a draft deck's is chosen in Build (DRF-166). (r62)

## Deck categories

- **LOB-244** A saved deck is in at most one category, and a category can hold categories. The Decks tab lists the categories as a tree under "SAVED LISTS (N)", each a row with its name and deck count that a tap folds open or shut (shut at first), showing its subcategories, then its decks; decks without a category come after the categories. (r78)
- **LOB-245** "New category" at the top of the Decks tab makes a top-level category; a category row's ⋮ offers New subcategory, Rename, Move to… (the top level or another category, never itself or one of its own subcategories) and Delete, each a small dialog over the list. (r78)
- **LOB-246** Deleting a category asks "Delete category?" (Keep category / Delete category) and moves its decks and subcategories up one level, into the category it was in, or to the top level; no deck is deleted. (r78)
- **LOB-258** A deck's category is chosen in its editor, in a "Category" row under the name ("None" or the category's path, "Battle box › Red"), from a list of the categories as a tree; Save keeps it with the deck's other changes. Draft decks have no category. (r78)
- **LOB-259** Choosing a category anywhere (lending decks, LOB-248) always includes its subcategories. (r78)

## Saved deck and cube view

- **LOB-056** A saved deck or cube opens in the same read-only picture view (DRF-010): sections Main, Side (decks) and Maybe in their colours (SYS-054), cards grouped by type in the shared order (SYS-051), ×N badges, holding a card inspects it (a tap does nothing), and a columns control remembered per screen and orientation (LOB-051). Maybe shows only when it holds cards. (r35)
- **LOB-057** Edit is the only button above a saved deck (a cube also has Draft this cube). (r35)
- **LOB-211** A draft deck shows Delete draft deck beside Save as deck (its title shortened to "Draft…" in portrait) and a note about where it was drafted. (r35)

## The editor

- **LOB-059** Decks and cubes share one editor: the name (a deck also its cover, chosen by tapping it once the list resolves), Cards and Text tabs, and a full-width Save at the bottom; existing lists open on Cards, new ones on Text. (r35)
- **LOB-212** The editor has a red Delete deck / Delete cube at its top right, confirmed with "Deletion cannot be undone." ("Delete deck?" naming the deck, Keep deck / Delete deck; "Delete cube?", Keep cube / Delete cube), with Import from link beside it (its icon only in portrait). (r35)
- **LOB-064** Changes in either tab are kept only by Save. Saving a new import from Manage decks opens its saved deck view; leaving a deck or cube editor with unsaved changes asks "Discard unsaved changes?" (Keep editing / Discard). (r35)

## Text tab and imports

- **LOB-213** The Text tab is the raw list (full width, no counts, a paste icon, Deck / Sideboard / Maybeboard headers); problem lines are underlined and red, with their messages and substitutions in a fixed 96 dp area under it and a red error icon by the tab's label while problems remain. (r35)
- **LOB-060** The list is looked up about 0.4 s after you stop typing; Save works only for a named, fully resolved list. Unknown or ambiguous names are flagged with line numbers and never replaced silently. (r21)
- **LOB-061** "Import from link" in the top bar (icon only on narrow phones) opens a dialog for Archidekt links or `archidekt:ID`; a matching link on the clipboard is prefilled, the paste icon pastes and imports at once, typed text waits for Import. An import replaces the list. (r35)
- **LOB-062** Common list formats work: "4 Name", "4x", optional "(SET) number", Deck/Main/Sideboard/Commander/Companion headers (also with # or // and counts), "SB:", a blank line before the sideboard, comments, Forge, XMage and CSV exports; Maybeboard sections (and Archidekt's maybeboard) go to Maybe. (r35)
- **LOB-063** A deck holds at most 1000 cards, counting every section. (r35)
- **LOB-151** A list's lines and search find a card by the name a printing gives it too (LOB-150): "1 Thunder Lance" imports that printing (a card's own Oracle name comes before a printing's name for another card, which comes before a back face's name), and a name search (`!name` too) finds the card shown as that printing. (r76)
- **LOB-214** The Text tab writes a card found by a printing's name (LOB-151) by its Oracle name with that printing's set and number. (r76)

## Cards tab

- **LOB-105** The Cards tab shows the list as pictures by section (decks Main / Side / Maybe, cubes Main / Maybe), empty sections too, grouped in the shared type order (SYS-051) with ×N badges. Each section header ends in a + in its section's colour that opens search with that section chosen. (r62)
- **LOB-215** On the Cards tab, swipe a card to move one copy to another section: right one section up, left one down, wrapping (SYS-083; a cube's two sections either way to the other); tap it for +1, −1, Remove all and Move to…, each with its icon; hold to inspect (SYS-049). (r62)
- **LOB-216** On the Cards tab "Move to…" lists the other sections and moves every copy; a swipe moves one copy by the swipe rule (LOB-215, SYS-083). (r35)
- **LOB-107** A line like "60 main · 15 side · 2 maybe" sits beside the Cards tab's columns control (decks 3, cubes 5 in portrait and 8 in landscape); while the list is looked up or has problems the tab says so ("Fix them in the Text tab") and the + buttons are off; a Cards change rewrites the Text tab in section order. (r35)
- **LOB-120** A round floating Undo button at the bottom left of the Cards tab (not on the search page), shown only when there is something to undo, takes back Cards tab and search changes one at a time (as in preparation, LOB-049), up to 50, bringing back a cover the change removed; it starts over after a Text tab edit or a link import. (r35)
- **LOB-142** The first thing above the Cards tab's pictures, scrolling away with them, is a filter: a field ("Filter cards, e.g. t:goblin mv<=2", with an ✕ to clear it) and the chips of search (LOB-108, LOB-119), starting empty for each edit and staying through the search page and the tabs. (r61)
- **LOB-217** While the filter holds anything, every section keeps its header and +, showing and counting only the cards that match ("No matching cards" when none does); text that can't be read says why under the chips and filters nothing. (r61)
- **LOB-218** The line of section counts and everything saved are still the whole list: swipes, the card menu, Undo and Save work on it, whatever the filter shows. Cards the card records don't hold match the filter by their saved data (no rarity or legality). (r61)

## Card search

- **LOB-106** A section's + opens a full-screen search page: the field and quick filter chips (LOB-119) on top, results as pictures, and at the bottom a Main | Side | Maybe group (cubes Main | Maybe) choosing where added cards go, then "Done · +N cards". (r35)
- **LOB-219** Tap a search result to add one copy to the chosen section (its square counts up with a short pulse, LOB-121); hold to inspect. Search starts at the section whose + was tapped; Done or Back returns to the list with every change kept. (r35)
- **LOB-121** Each search result shows up to three small squares in a row at its bottom left: Main, Side and Maybe in that order and in their section colours (SYS-054), each with that section's copy count and shown only when it holds copies; tapping a square removes one copy from that section. (r35)
- **LOB-122** Search results follow about 0.35 s after typing, sorted by name, 3 columns (5 in landscape), loading more while scrolling; with nothing typed or chosen the page shows a hint. (r35)
- **LOB-220** Another printing of a card already in the section adds to it; Done counts this visit's change ("Done" when none); adding past the limit says "A deck holds at most 1000 cards."; the search is kept for the next visit in the same edit session. (r35)
- **LOB-119** Search chips are W U B R G C, mana value 0–6+ and Creature, Planeswalker, Instant, Sorcery, Artifact, Enchantment, Land: within a row any chosen chip matches, and rows and typed text all apply. Colour chips are the mana symbols as drawn in card text, mana value chips generic mana symbols with the number; chips are 32 dp tall, 4 dp apart. (r35)
- **LOB-108** Search understands these terms: name words, `t:` type, `o:` rules text, `c:`/`c=`/`c<=` colours, `id:` colour identity, `mv`/`cmc` mana value (from the mana cost), `pow`/`tou`/`loy`, `r:` rarity, `f:` format legality, `s:` set, `is:` layout (e.g. `is:dfc`), comparisons (`mv<=3`), `-` negation, `or` and parentheses. (r35)
- **LOB-109** Search rules not taken from Scryfall's documentation: */X/? power or toughness counts as 0; a double-faced card's colours count per face; `!name` also matches a face name; rarities order common < uncommon < rare < special < mythic < bonus; `f:` counts restricted cards as legal; planes, schemes and vanguards show only with `include:extras`; Alchemy cards are included. (r35)

## Saving and card data

- **SYS-084** Saving a deck or cube is one request naming each card by its printing, and the server keeps the list so; it makes a card's data from its card catalog (SYS-081) when a deck is chosen for a game or a cube becomes a pod's source, the same card data the phone makes of that printing.
- **SYS-200** Saved decks and cubes hold their cards by printing, and the phone and the server build each card from the current card catalog whenever the list is shown, edited, chosen or made a pod's source, so a deck saved earlier shows the current catalog's text.
- **SYS-201** A card whose printing the catalog doesn't have is found again by its name (the server checks at every start); failing that it stays as a card without a printing: its name alone, no picture, a card Automatum can't play (LOB-115), kept by Cards tab and search edits (a Text tab edit resolves it like any typed line).
- **SYS-202** The server also supplies the basic lands of pod decks (LOB-052; English M21, the lowest collector number), a pod's Cogwork Librarians (DRF-051) and the dungeons a Ready brings (LOB-117).

## Cards Automatum can't play

- **LOB-115** A deck Automatum can't play fully by the rules (one card it can't play yet is enough, in Main, Side or Maybe alike) shows an amber warning icon wherever it is listed or chosen (Manage decks' Decks tab, draft decks too; the saved deck view; the deck editor, for the list being edited; Choose deck; your own deck in a game lobby). (r57)
- **LOB-221** Holding a deck's warning icon (LOB-115) names the cards ("Automatum can't play 3 cards yet: A, B, C", at most 12 names then "and N more"): those the card catalog doesn't mark as supported (SYS-082), and a card without a printing. (r57)
- **LOB-124** A deck's Automatum result (LOB-115) is worked out by the server from its card catalog's marks (SYS-082) whenever it reads the deck, so it is known at once, after every save and for every deck; nothing is kept with the deck and nothing runs in the background. (r49)
- **LOB-144** A card Automatum can't play yet (the card catalog's marks, SYS-082) shows an amber warning triangle stamped over its picture's art box in card search, the deck and cube editor's pictures, the saved cube view's pictures and Inspect (not on tokens); holding it says "Automatum can't play this card yet", and taps and holds still reach the card under it. (r62)
- **LOB-222** The card's warning triangle (LOB-144) is the triangle alone, no background, as big as the art box's smaller side and centred in it, a little see-through like a stamp, with a thin dark outline and exclamation mark so it reads on light and dark art. (r62)
- **LOB-118** A deck with LOB-115's warning can't start a game: Choose deck's "Use selected deck" (with it highlighted) and a lobby's Ready (with it chosen) are greyed with "Automatum can't play every card of this deck yet. Hold the warning sign to see which."; Ready also waits, saying nothing, the moment the phone looks the deck up in the card catalog's marks. (r49)
- **LOB-223** The server refuses choosing a deck whose result (LOB-124) names cards, and readying or starting with a prepared deck holding a card the catalog's marks don't support (or one without a printing), and a start when any card's data can't be read. A step Automatum can't do while playing is skipped (STK-030). (r49)
- **LOB-117** A deck that ventures into the dungeon or takes the initiative plays it by the rules: with its Ready the server brings the dungeons of its card catalog (SYS-081) that Automatum plays fully, which start outside the game as that player's own, never part of the deck. (r50)
- **LOB-224** The dungeons a Ready brings (LOB-117) come each once; a printing with a reminder back comes as its dungeon side only; any with a room Automatum can't do is left out. (r50)
- **LOB-225** A plain venture chooses among the dungeons other than the Undercity, which only "venture into Undercity" and the initiative enter. (r50)
- **LOB-123** A card that ventures or takes the initiative with no playable dungeon for it counts as a card Automatum can't play (LOB-115; the card catalog marks it so, SYS-082), and the server refuses a Ready missing the dungeons its deck needs. Dungeons aren't companions: they never make the game ask about revealing one. (r50)

## Preparing a deck for a game

- **LOB-047** "Prepare deck" opens one scrolling page with green Mainboard · N and blue Sideboard · N headers, cards as pictures grouped by type in the shared order (SYS-051; empty groups left out, cards sorted by name) and ×N badges for copies. (r70)
- **LOB-226** In a pod game the Prepare deck header shows your deck's private name with its cover thumbnail and a pen at the header's end: a tap on the cover lists the deck's Main and Side cards (basics not among them) to choose the cover from, the pen renames it, as in Build and also after submitting (DRF-105, DRF-166). (r70)
- **LOB-048** Swipe a card sideways (20 dp), either way, to move one copy to the other section (SYS-083); hold to inspect it; tap for "Move one to…" and "Move all N to…". (r62)
- **LOB-049** A round floating Undo button at the bottom left, shown only when there is something to undo, takes back moved cards one at a time. (r55)
- **LOB-050** A short fading line under the title says what you last moved ("2× Opt → Sideboard"); while disconnected it reads "Disconnected · preparation is read-only". (r21)
- **LOB-051** The prepare screen and saved deck view have a columns control (1–10, 3 by default), remembered per screen, device and orientation. (r31)
- **LOB-052** In pod games the basic lands are cards like any other: the ones added while building (and an unlimited sideboard's Side basics, DRF-104) show as tiles and move between Main and Side as other cards do (no basic land steppers); Ready is blocked with a reason if Main has fewer than 40 cards including basics or Side is over the pod's sideboard limit. (r55)
- **LOB-227** A deck's Maybe cards are not shown in Prepare deck; they come along to the game unchanged, outside the game (LOB-141). (r70)
- **LOB-141** In every game, constructed and pod, a player's cards outside the game (what wishes, learn and the like can fetch) are their deck's Side and Maybe, never part of the library. Maybe cards count like Side cards for LOB-115's warning and LOB-118's refusals. (r60)
- **LOB-053** Lobby deck changes never change the saved deck, and saved deck edits or deletion never change a deck already chosen in a lobby or game. (r1)
