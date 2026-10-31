# Zones

Hand, library, graveyard, exile and Temp piles, the left card panels (Search, Look, Reveal, graveyard/exile view), mulligan, and who sees what in each zone.

### Hand

- **ZON-001** Your hand row shows your cards face up; the opponent's hand row looks just like yours but shows card backs. (r7)
- **ZON-002** A hand keeps its card order: a card dropped into a hand lands in the gap where you let go, and hand cards slide aside when cards come or go. (r17)
- **ZON-003** Holding a finger on an empty part of a hand previews nothing. (r28)
- **ZON-004** Double-tapping a hand card plays it like a drag: a land (by its front face) goes to the first free slot of the land row, anything else onto the Stack; a non-land permanent then resolves onto the battlefield by itself (STK-004). (r15)
- **ZON-005** A played hand card whose every face is a permanent goes onto the Stack and then resolves by itself onto the main battlefield row as a separate timeline step; one Undo takes back both. (r15)
- **ZON-006** Hand cards can't be tapped; a double-tap never turns them. (r12)
- **ZON-007** Tapping a hand card opens a one-page hand menu: Inspect, Play, Face down, Reveal, Battlefield, Discard, Exile, Library top, Library bottom, Shuffle into library, Temp 1–4, Discard random, Reveal random, Exile random, Random to library bottom. (r28)
- **ZON-008** "Face down" puts the hand card onto the Stack face down (the opponent sees a back) and it resolves at once onto the battlefield as a face-down 2/2. (r21)
- **ZON-009** Hand "Reveal" opens a Reveal panel of the chosen hand cards for both players; its "Done" button puts them back in the hand in their old places. (r16)
- **ZON-010** The random hand actions pick one card at random from the selected hand cards, or from the whole hand when none is selected, with a fresh random seed of their own. (r16)
- **ZON-011** Either player can use the hand menu on the opponent's hand; those cards stay face down for them, and Play picks the zone by the card's real type. (r30)

### Library

- **ZON-012** A library pile never shows a card face to either player (no "known card" marker), unless "Keep top card revealed" is on. (r10)
- **ZON-013** Tapping a library opens its menu at once; double-tapping a library draws its top card into its owner's hand. (r8)
- **ZON-014** Dragging from a library, graveyard, exile or Temp pile takes its top card; a card dropped on a library goes on top. (r1)
- **ZON-015** Library menu rows: Draw 1, Reveal 1, Look 1, Mill 1, Exile 1; the same five as "N…"; Search, Shuffle, Manifest; Mulligan N…, Mulligan −1; then the "Keep top card revealed" button. (r20)
- **ZON-016** Each "N…" opens a floating row [−][+][number][action] with the number selected, prefilled with the last number used for that action on this device. (r10)
- **ZON-017** Draw goes to the library owner's hand, Mill to the graveyard, Exile to exile, all from the top. (r8)
- **ZON-018** "Manifest" puts the library's top card onto its owner's battlefield face down, in the main row, without revealing it. (r20)
- **ZON-019** The opponent's library menu offers every action, Mulligan included; cards still go to the library owner's zones. (r10)
- **ZON-020** "Keep top card revealed" is an on/off button (eye icon) that either player can switch on any library, recorded in history; while on, the top card shows face up on the pile for both. (r13)
- **ZON-021** While it is on, the top card stays known: branch re-rolls never change it, while a real shuffle (Shuffle, closing a Search, mulligan) re-randomizes it and the new top shows at once; no auto-pause per new top card. (r13)
- **ZON-022** Dragging a library's top card onto the Stack works like "Stack" in a Search, Look or Reveal panel: it goes there, shown to both players while on the Stack. The library menu has no Stack action, and other library cards can't be dragged there. (r35)

### Library order

- **ZON-023** Library order is hidden: cards put on top or bottom keep a known place, top-end actions (draw, mill, look, reveal) take the known top first and bottom-end actions the known bottom; a shuffle makes it all unknown. (r29)
- **ZON-024** Every new branch re-randomizes the unknown parts of both libraries the same way each time: draw, undo, draw again (or branching again from the same point) gives the same card. (r29)
- **ZON-025** Two or more cards moved at once onto a library top or bottom from a panel or pile menu go in random order, unknown to both players. (r29)
- **ZON-026** You may undo your own look, scry or reveal. (r9)

### Mulligan

- **ZON-027** Mulligan N shuffles your hand into your library and draws N; Mulligan −1 draws one fewer than your current hand size; each is one event and one Undo. (r13)
- **ZON-028** Mulligan N… asks for the number, 7 by default, remembering your last number for that game. (r17)
- **ZON-029** While a player's status is the opening "Mulliganing", their quick actions are Mull N, Mull −1 and Ready. (r25)

### Graveyard and exile

- **ZON-030** A graveyard or exile pile shows its newest card on top; removing it shows the next one down, and cards arriving together land in order so the last one is on top (milling 3 puts the third on top). (r27)
- **ZON-031** Library, graveyard, exile and Temp piles show only their top card at single-card size, with a card count. (r13)
- **ZON-032** Holding a graveyard or exile pile previews its top card. (r8)
- **ZON-033** Tapping either player's graveyard or exile opens its menu: Search, All top, All bottom (random order), Shuffle all into library, All → Exile (or → Graveyard), All → Temp 1; there is no "All to hand". (r29)
- **ZON-034** Each mass pile move sends the cards to their owners' zones as one Undo step. (r29)
- **ZON-035** Double-tapping a graveyard or exile pile opens its search view, as does Search in the pile menu (also while reviewing or offline). (r29)
- **ZON-036** Graveyards and exiles are public, so looking through either player's is free: the view is personal, records no history, is never shown to the opponent, and doesn't count toward the one-panel limit. (r35)
- **ZON-037** The view groups cards under coloured type headers with counts like "Creature (3)" (non-empty types only), types in the shared order (SYS-051), names alphabetical, copies grouped ×N. (r35)
- **ZON-038** The view closes on ✕, on a tap outside it, or when its pile empties; changing the interaction mode keeps it open. (r29)

### Temp piles

- **ZON-039** Each player has up to four Temp piles after exile; they are face down and hidden from both players like a library. (r28)
- **ZON-040** Temp 1 always shows (empty: its number as a placeholder, no "0"); Temps 2–4 appear when a card moves in, and Close removes one only while it is empty. Either player can do this to either player's piles. (r31)
- **ZON-041** Temp pile menu: Shuffle, Look, Shuffle into library, Close / All top, All bottom (both in pile order), All → Graveyard, All → Exile / All → n and Top → n for each other pile. (r31)
- **ZON-042** Card, hand and panel menus have Temp 1–4, moving the cards into their owner's pile of that number and creating it if needed; tokens never go into Temp piles. (r28)
- **ZON-043** Look on a Temp pile shows all its cards; closing it never shuffles or asks where cards go, and cards put on the pile meanwhile stay on top. (r31)
- **ZON-044** Cards moved into a Temp pile from a library Search or Look stay hidden. (r31)
- **ZON-045** With more than one Temp pile and no room, the counters take their own row under the piles (above them on the opponent's side) instead of crowding the piles. (r28)
- **ZON-046** Cards moving between any piles (library, graveyard, exile, Temp; yours or the opponent's) fly across the table. (r31)

### Card panels

- **ZON-047** Search, Look and Reveal open a card panel at the left edge over the battlefield (beside a graveyard/exile view if open); header: icon, count, Select all (2+ cards) and ✕, in the opener's colour. (r29)
- **ZON-048** Only one Search, Look or Reveal panel can be open in the whole game; while one is open, every button that would open another is greyed out, with no message. (r10)
- **ZON-049** Panel cards sit in up to 3 columns (2 each when two panels show), left to right then down, position 1 = library top; a partial last row starts at the left. (r14)
- **ZON-050** Look shows the top N in order to the looker; the opponent sees a panel of backs with the count. Drag inside the panel to reorder (an insertion bar shows where). (r10)
- **ZON-051** Search shows the whole library to the searcher sorted by type then name under type headers with counts, copies grouped ×N (a drag takes one); the opponent sees backs. Its type headers follow the shared order (SYS-051). (r35)
- **ZON-052** Reveal N shows the top N face up to both players. (r8)
- **ZON-053** Reveal hand / Reveal top from a Search or Look flips the card face up in the panel for both, then moves it on as a separate timeline step. (r8)
- **ZON-054** Only the panel's opener sees a white-disc eye on the art of Look/Search cards the opponent can currently see. (r11)
- **ZON-055** Reveals don't last: a card is public only while in the revealed panel; once it goes to a hand or library it is hidden again. (r11)
- **ZON-056** Cards put on top or bottom of a library while it is searched (by anyone, from anywhere) stay there after the closing shuffle; the panel shows "After shuffle:", "N on top", "N on bottom". (r30)
- **ZON-057** Holding any face-up panel card inspects it. (r8)

### Panel card menu and gestures

- **ZON-058** Tapping a panel card opens one menu right of the panels, top row lined up with the cards: Inspect, Other side, Ping, Hand, Reveal hand, Battlefield, Tapped, Stack, Graveyard, Exile, Library top, Reveal top, Library bottom, Shuffle into library, Temp 1–4. (r29)
- **ZON-059** Buttons that don't apply are greyed: Reveal ones on cards already visible or in Reveal and graveyard/exile panels, the panel's own zone, Shuffle into library in library panels (Library top/bottom stay on). (r29)
- **ZON-060** If the tapped card is selected, the menu acts on every selected card of that panel only, as one Undo step. (r29)
- **ZON-061** Drag a panel card out to move it. Drag on empty panel space for a selection box (it takes all copies of a grouped card); scroll a panel with two fingers or double-tap-drag. (r29)
- **ZON-062** Panel cards sent to Battlefield take the first free cell of their row: lands the land row, others the main row. (?) (r34)
- **ZON-063** Either player can use any panel's cards and menu, including the opponent's private Look or Search, where the cards stay face down for them. (r30)

### Closing panels

- **ZON-064** Closing a library Look or Reveal with cards left shows one floating row at the ✕: Top, Bottom (both in panel order), Shuffle, Graveyard, Exile. (r10)
- **ZON-065** Closing a Search always shuffles the library, with no choices. (r15)
- **ZON-066** When the last card leaves a Search, Look or Reveal panel, it closes in the same event with no choices. (r11)
- **ZON-067** Either player can close any panel, with exactly the opener's choices. (r29)
- **ZON-068** Closing a panel is its own timeline step; nothing groups with it. (r29)
- **ZON-069** Search, Look and Reveal panels stay open on taps outside them. (r13)

### Moving cards between zones

- **ZON-070** A card going to a hand, library, graveyard or exile always goes to its owner's zone; a drop on the opponent's such zone goes to the owner's, and the drag preview shows that. (r5)
- **ZON-071** Leaving the battlefield untaps a card and resets notes, P/T, counters, links, face (back to front) and owner; face down survives only a move to exile. (r13)
- **ZON-072** Pile and panel mass moves leave tokens out of hands and libraries. (r29)

### Hidden information

- **ZON-073** Library cards are hidden from everyone, hand cards are seen only by their holder, and face-up graveyard, exile, battlefield and Stack cards are public; face-down cards show backs everywhere. (r30)
- **ZON-074** A hand card put on the Stack is shown to both players while it is there. (r2)
- **ZON-075** Ordinary inspects never show a face-down card's hidden front to the opponent; only the deliberate, logged "Inspect other side" does. The holder sees their own face-down card dimmed with "Face down". (r21)
- **ZON-076** Hidden cards show no values (P/T, notes, counters) in menus, but they can still be edited blind. (r30)
