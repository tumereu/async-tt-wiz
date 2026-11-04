# Zones

Hand, library, graveyard and exile, the graveyard/exile view, and who sees what in each zone.

### Hand

- **ZON-001** Your hand row shows your cards face up; the opponent's hand row looks just like yours but shows card backs. (r7)
- **ZON-002** A hand keeps its card order: hand cards slide aside when cards come or go, and a hand is never reordered (ZON-081). (r45)
- **ZON-003** Holding a finger on an empty part of a hand previews nothing. (r28)
- **ZON-004** Double-tapping a hand card casts it through Automatum (STK-023), its first tap raising it and its second casting it the way its cost chips say (ZON-079, ZON-080): a land is played at once onto its owner's battlefield, and a trigger the land sets off (landfall) is passed on by its player as after a cast (STK-026); a card Automatum doesn't allow now (a card in the other player's hand too) stays in the hand and shakes, without a message (TBL-118). (r45)
- **ZON-006** Hand cards can't be tapped; a double-tap never turns them. (r12)
- **ZON-077** A drag is what it means to Automatum: a hand card dragged onto the Battlefield or the Stack is played or cast as with a double-tap (ZON-004, STK-023), one card at a time; a creature dragged into the combat area joins the declaration Automatum waits for (CMB-028); a card moved within its own Battlefield only changes place, and one dropped back into its own hand goes back where it was without a shake (no reordering, ZON-081); the raised hand card is cast the way its chips say (ZON-080). Every other drag that would move a card by hand (to another zone, from a pile or panel, the Stack's top entry), several hand cards dragged at once, and a creature Automatum doesn't let join the declaration are refused: the card goes back and shakes, without a message (TBL-118). Menus have no manual moves (TBL-116). (r44)
- **ZON-078** The Library menu shows only what Automatum offers for the library's top card (playing or casting it from the top, as a card that lets you play lands from the top of your library allows), one row each with the card's name ("Play · Forest", "Cast · Storm Spark"), used as in a card menu (TBL-115); when it offers nothing, tapping the Library opens no menu and the Library shakes (TBL-118); the Graveyard and Exile pile menus are only Search. (r45)
- **ZON-079** Your hand has no menu: tapping one of your hand cards raises it a little above the row, and the mana sources Autotap would tap to cast it are outlined in amber on your battlefield (none for a land or a cast your mana pool pays); tapping the raised card again casts or plays it the way its cost chips say (ZON-080), as a double-tap does (ZON-004). Tapping anywhere else, or another hand card (which is raised instead), lowers it, as do casting it and the card leaving your hand; when the board changes, its chips and outlined sources are worked out again. A card Automatum doesn't let you cast or play now isn't raised: a tap only shakes it (lowering a raised card), with no chips and no sources outlined; Inspect says why (TBL-118). Raising and lowering a card moves it smoothly, as fast as a card moving within the hand at your Card move speed (HIS-028). Holding a hand card inspects it. A card in the other player's hand does nothing when tapped. (r51)
- **ZON-080** Chips sit in a row just above a raised hand card (ZON-079), fading and rising in as it rises and out as it goes down, as fast as the card, one per way to cast it, even when there is only one, each its name and its mana cost drawn as mana symbols: first the ways to cast it (white with a violet outline, the chosen one filled violet: the regular "Cast" first and chosen, or "Play" for a land, then the card's other ways such as another face or Adventure, "Flashback" or "Evoke"); tapping a way chooses it and tapping the chosen way again casts the card that way. Then the extras (amber outline, with "+" before the name and a check box: kicker, buyback, offspring, gift and other optional additional costs): tapping one turns it on or off and never casts; one that can be paid several times (multikicker) counts up with each tap, showing "×N", and has a small "−". A chip whose choice your mana can't pay now is greyed and does nothing when tapped. The outlined sources follow the chosen way and extras. Holding a chip shows its details in a card-like popup: its name and cost, its printed text and, for the chosen way, the total cost with its extras. The cast then asks only for targets, modes and X (STK-023). (r51)
- **ZON-081** Your hand row keeps every card at its full size and never reorders: a drag that starts on it and first moves sideways pans the hand, one that first moves up or down drags the card (ZON-077). When the hand is wider than its row it pans between its ends, and pulling past an end stretches like a rubber band and springs back on release, as the battlefield does (TBL-014); a hand that fits springs back to the middle. (r44)

### Library

- **ZON-012** A library pile never shows a card face to either player (no "known card" marker). (r45)
- **ZON-013** Tapping a library opens its menu only when Automatum offers something for its top card (ZON-078), otherwise the library shakes; double-tapping a library does nothing (nothing is drawn by hand, SYS-069). (r45)

### Library order

- **ZON-023** Library order is hidden: cards put on top or bottom keep a known place, top-end actions (draw, mill, look, reveal) take the known top first and bottom-end actions the known bottom; a shuffle makes it all unknown. (r29)
- **ZON-024** Every line of play (the game's start, a branch, a rewind or free undo) reshuffles the unknown parts of both libraries; cards whose place someone knows keep it (HIS-042). Drawing, undoing and drawing again gives a new card. (r48)
- **ZON-026** Undoing your own look, scry or reveal needs the other player's approval (an undo request, HIS-098). (r48)

### Graveyard and exile

- **ZON-030** A graveyard or exile pile shows its newest card on top; removing it shows the next one down, and cards arriving together land in order so the last one is on top (milling 3 puts the third on top). (r27)
- **ZON-031** Library, graveyard and exile piles show only their top card at single-card size, with a card count. (r45)
- **ZON-032** Holding a graveyard or exile pile previews its top card. (r8)
- **ZON-033** Tapping either player's graveyard or exile opens its menu, whose only button is Search (looking has no game effect); nothing moves a pile's cards by hand. (r45)
- **ZON-035** Double-tapping a graveyard or exile pile opens its search view, as does Search in the pile menu (also while reviewing or offline). (r29)
- **ZON-036** Graveyards and exiles are public, so looking through either player's is free: the view is personal, records no history and is never shown to the opponent. (r45)
- **ZON-037** The view groups cards under coloured type headers with counts like "Creature (3)" (non-empty types only), types in the shared order (SYS-051), names alphabetical, copies grouped ×N. (r35)
- **ZON-038** The view closes on ✕, on a tap outside it, or when its pile empties. (r45)

### Moving between piles

- **ZON-046** Cards moving between any piles (library, graveyard, exile; yours or the opponent's) fly across the table. (r45)

### Graveyard and exile view

- **ZON-049** The graveyard/exile view's cards sit in up to 3 columns, left to right then down; a partial last row starts at the left. (r45)
- **ZON-057** Holding any face-up panel card inspects it. (r8)
- **ZON-058** Tapping a card in the graveyard/exile view opens its card menu (TBL-116): what Automatum offers for it (a flashback cast, say) as rows, then Inspect and Other side. (r45)
- **ZON-061** A drag on empty space in the view, a double-tap-drag anywhere in it or two fingers scroll it; there is no selection box. (r45)

### Moving cards between zones

- **ZON-071** Leaving the battlefield untaps a card and resets P/T, counters and face (back to front); face down survives only a move to exile. (r46)

### Hidden information

- **ZON-073** Library cards are hidden from everyone, hand cards are seen only by their holder, and face-up graveyard, exile, battlefield cards and Stack entries are public; face-down cards show backs everywhere. (r35)
- **ZON-074** A hand card put on the Stack is shown to both players while it is there. (r2)
- **ZON-075** Ordinary inspects never show a face-down card's hidden front to the opponent; only the deliberate, logged "Inspect other side" does. The holder sees their own face-down card dimmed with "Face down". (r21)
