# Zones

Hand, library, graveyard and exile piles and their view, moving cards between zones, and hidden information.

## How it works

Each player has a hand, a library, a graveyard and an exile pile. Your hand row shows your cards face up and keeps
their order; the other player's hand shows only card backs and a count. The piles show only their top card with a
count, and a library never shows a card face to anyone. Graveyards and exiles are public: a tap opens a search view of
either player's that you may look through freely, without the other player knowing. Nobody moves cards between zones by
hand: Automatum moves them as the rules say, and a drag only means what it means to Automatum. Your own library's menu
also lets you look through your deck as you play it, which changes nothing in the game.

## Hands

- **ZON-001** Your hand row shows your cards face up. The other player's hand shows card backs. (r64)
- **ZON-125** In portrait the other player's hand is a small fan of card backs in their life and pile row, after their piles, with its count at its right; it has no row of its own (the battlefields take that room). (r64)
- **ZON-126** In landscape (phone layout) the other player's hand is their hand row in the top bar, looking just like yours (TBL-009); on the desktop table it is the same small fan as in portrait, with its count in their seat header, after their piles (WEB-016). (r64)
- **ZON-127** Holding one of the other player's hand cards inspects it as a covered card (TBL-109); cards of their hand that are revealed or looked at open in a panel (HIS-110). (r64)
- **ZON-002** A hand keeps its card order: hand cards slide aside when cards come or go, and a hand is never reordered (ZON-081). (r45)
- **ZON-081** Your hand row keeps every card at its full size and never reorders: a drag that starts on it and first moves sideways pans the hand, one that first moves up or down drags the card (ZON-077). (r44)
- **ZON-128** A hand wider than its row pans between its ends, stretching past an end like a rubber band and springing back on release (TBL-014); a hand that fits springs back to the middle. (r44)
- **ZON-003** Holding a finger on an empty part of a hand previews nothing. (r28)

## Piles on the table

- **ZON-031** Library, graveyard and exile piles show only their top card at single-card size, with a card count. (r45)
- **ZON-012** A library pile never shows a card face to either player (no "known card" marker). (r45)
- **ZON-030** A graveyard or exile pile shows its newest card on top; removing it shows the next one down, and cards arriving together land in order so the last one is on top (milling 3 puts the third on top). (r27)
- **ZON-092** An exile pile holding a card with counters (a suspended card's time counters, say) carries a small marker at its top-right corner, opposite its count: a violet dot ringed in white with a white centre. (r58)
- **ZON-032** Holding a graveyard or exile pile previews its top card. (r8)

## Library menu

- **ZON-078** Tapping a library opens its menu; double-tapping one does nothing (nothing is drawn by hand, SYS-069). (r45)
- **ZON-129** The Library menu shows what Automatum offers for the library's top card (playing or casting it from the top, as a card that lets you play lands from the top of your library allows), one row each with the card's name ("Play · Forest", "Cast · Storm Spark"), used as in a card menu (TBL-126). (r45)
- **ZON-130** Your own Library's menu also always has the View deck cell (ZON-093), the other player's never. When Automatum offers nothing for the other player's Library, tapping it opens no menu and it shakes (TBL-118). (r45)

## View deck

- **ZON-093** View deck, a cell in your own Library's menu (greyed only when you have no cards in the game), opens a panel "Deck" with your deck as you play it: your own cards in the game (no tokens; the deck you play with, no sideboard). Looking only: nothing in the game changes and no event is made. (r60)
- **ZON-131** The Deck panel is a sheet as the Stack list is, closed by a borderless X at its top right as the Source dialog is. (r60)
- **ZON-132** The Deck panel shows the cards as pictures in the deck views' type groups (SYS-051, "Creatures · 12"; cards by name, ×N for copies) at the Panels card size (TBL-034) as every card panel's, as many per row as fit. Holding a card inspects it. (r60)
- **ZON-133** Two chips at the Deck panel's top switch between "All · N" (the whole deck, the start) and "Still in library · N" (the deck without the cards you have seen leave your library: those you can see now in your hand, on the battlefield, in a graveyard or exile; a card that left face down unseen stays in it), never in library order. (r60)

## Library order

- **ZON-023** Library order is hidden: a card put on top or bottom of a library, or looked at and left in place, keeps that place until the library is shuffled, so draws and other top-end actions take it first. Nothing on the table shows which places are known (ZON-012). (r53)
- **ZON-024** Every line of play reshuffles the unknown parts of both libraries, known places kept (HIS-042): drawing, undoing and drawing again gives a new card. (r48)

## Graveyard and exile

- **ZON-033** Tapping either player's graveyard or exile opens its search view at once, also while reviewing the timeline or while a result is pending (looking has no game effect); another tap on it closes the view, and an empty pile shakes instead. Nothing moves a pile's cards by hand. (r61)
- **ZON-035** Double-tapping a graveyard or exile pile leaves its search view open: the first tap opened it, the second does nothing more. (r61)
- **ZON-036** Graveyards and exiles are public, so looking through either player's is free: the view is personal, records no history and is never shown to the opponent. (r45)

## The graveyard and exile view

- **ZON-037** The view groups cards under coloured type headers with counts like "Creature (3)" (non-empty types only), types in the shared order (SYS-051), names alphabetical, copies grouped ×N. (r35)
- **ZON-049** The graveyard/exile view's cards sit in up to 3 columns, left to right then down; a partial last row starts at the left. (r45)
- **ZON-061** A drag on empty space in the view, a double-tap-drag anywhere in it or two fingers scroll it; there is no selection box. (r45)
- **ZON-058** Tapping a card in the graveyard/exile view opens its card menu (TBL-116): what Automatum offers for it (a flashback cast, say) as rows, nothing else; a card Automatum offers nothing for opens no menu. Holding it inspects it (TBL-104). (r52)
- **ZON-094** A card in the exile view shows its state as a word on its chip before its counters, and as a line in Inspect (TBL-104): "Plotted", "Foretold" (also on a card exiled face down: that it is foretold is public, what it is stays hidden) or "Warped"; copies with a different state keep their own places. (r60)
- **ZON-091** A card in the exile view shows its counters, any kind, as a battlefield card does: bubbles on its art (TBL-135, a plain "3" for three time counters), sized with the card; copies with different counters keep their own places. Holding it lists them (TBL-104). (r71)
- **ZON-038** The view closes on ✕, on a tap outside it, or when its pile empties. (r45)

## Moving cards

- **ZON-077** A drag is what it means to Automatum: a hand card dropped on the Battlefield or the Stack is played or cast as by a double-tap (ZON-004), one card at a time; a creature dropped in the combat area joins the declaration (CMB-028). (r53)
- **ZON-134** Nothing is placed by hand (TBL-004): a drag that starts on a battlefield card pans the table unless the combat area is there to take it, and a card dropped back on its own Battlefield or into its hand returns without a shake. (r53)
- **ZON-135** Any other drag (to another zone, from a pile or panel, the Stack's entry, several hand cards, a creature that can't join) is refused: the card goes back and shakes (TBL-118). (r53)
- **ZON-046** Cards moving between any piles (library, graveyard, exile; yours or the opponent's) fly across the table. (r45)
- **ZON-071** Leaving the battlefield untaps a card and resets its P/T changes, counters and face (back to front); face down survives only a move to exile. (r46)

## Hidden information

- **ZON-073** Library cards are hidden from everyone, hand cards are seen only by their holder, and face-up graveyard, exile, battlefield cards and Stack entries are public; face-down cards show backs everywhere. (r35)
