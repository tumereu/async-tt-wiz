# Turns, combat and Stack

Turn and step tracking, whose move it is, the top and bottom game bars, life, player counters, statuses, dice and coins, game start and result suggestions (`TRN`), the combat area (`CMB`) and the Stack (`STK`).

### Turn structure

- **TRN-001** A turn has 12 positions: Untap, Upkeep, Draw, Main 1, Beginning of combat, Declare attackers, Declare blockers, Combat damage, End of combat, Main 2, End step, Cleanup. Players move through them by hand; nothing advances by itself. (r2)
- **TRN-002** Turn numbers count both players' turns, so turn 1 is only the starting player's first turn. Next step from Cleanup, or End turn, passes the turn to the other player's Untap. (r33)
- **TRN-003** Passing the turn clears every card's damage, until-end-of-turn P/T and until-end-of-turn note at once. (r16)
- **TRN-004** Either player may change the step or end the turn, also on the opponent's turn; the opponent's version reads "End opponent's turn". (r30)

### Top bar

- **TRN-005** The top bar's first line reads "Your turn · N" or "<name>'s turn · N" in the turn player's colour; only the name shortens on a narrow screen. (r11)
- **TRN-006** Under it, the move pill: a solid green "Your move" when you hold the move, otherwise an outlined "Waiting · <name>". Tapping the pill passes the move to the other player; anyone can pass, and it always flips to the other player. It does nothing in timeline mode or while disconnected. (r11)
- **TRN-007** The step button in the top bar is a square in the current phase's colour, showing the step's icon and short name (no border colour, no turn number). It flashes on a step change and opens the phase dialog. (r13)
- **TRN-008** The phase dialog is a real dialog, not a floating popup. Its title is the step with its phase (e.g. "Combat · Declare attackers"), then the turn player ("Your turn" / "<name>'s turn"); phase labels sit above each row of step squares, and the current step is outlined in the turn player's colour. Step names are never cut off on a phone, using at most two lines. (r16)
- **TRN-009** The phase dialog has Next step and End turn buttons. Choosing a later step records the steps passed on the way; choosing an earlier step is one single correction event. (r33)
- **TRN-010** The top bar's Table actions button opens the Table actions dialog: Create combat area or Close combat area (only the one that applies), Create token, Roll die…, Flip coin, Timeline, and Suggest game end (or Accept result while the opponent's suggestion is waiting). (r15)
- **TRN-011** Opening a game shows "Loading table…" until the table loads; "Table unavailable" only when it really fails. (r13)

### Whose turn and whose move

- **TRN-012** Whose TURN shows on the battlefields: only the turn player's battlefield is tinted (your own green-ish, the opponent's red-ish, the land band a little darker); the other battlefield is neutral grey. (r11)
- **TRN-013** Whose MOVE shows on the bars: the top and bottom bars take the light colour of the player holding the move. In timeline mode both bars are grey. (r15)
- **TRN-014** Every step change gives the move to the turn player, except moving to Declare blockers, which gives it to the defender. This change is part of the step event; only a manual pass creates its own event, and there are no other automatic passes. Steps passed on the way inside a multi-step advance leave the move where it is. (r33)

### Bottom bar and quick actions

- **TRN-015** The bottom bar shows quick actions only on your turn, or on the opponent's turn while you hold the move (that step's actions). Otherwise it shows a faded "Opponent's turn". (r13)
- **TRN-016** Quick actions are equal-width buttons in the popup style (icon plus small black label, 48dp tall) filling the width left of the step arrow, with press feedback. A greyed quick action stays in place. (r14)
- **TRN-017** The step arrow sits in the bottom-right corner, arrow-shaped, about 72dp wide, with the next step's icon above its short name ("End turn" when the next step passes the turn). Tap = Next step; hold = End turn, with a small haptic tick. (r16)
- **TRN-018** Step arrow style: your turn and your move = filled with your colour; opponent's turn and your move = white with an opponent-coloured border; not your move = grey border. (r13)
- **TRN-019** Untap step: "Untap all", "Untap + next" and "Untap, draw → main" (untap, draw in the Draw step, then Main 1). Upkeep: "Draw + main". Draw: "Draw one" and "Draw one + next". (r33)
- **TRN-020** Main 1: "Select attackers" (moves to Declare attackers and turns on multi-select) and "Skip combat" (moves to End step). (r16)
- **TRN-021** Main 2 and End step: a visible "End turn" quick action. (r3)
- **TRN-022** On the starting player's turn 1 no quick action draws: Untap, Upkeep and Draw offer "Go to main" instead. (r25)
- **TRN-023** Each quick action is one Undo step, however many changes it makes. (r15)
- **TRN-024** There are no quick actions for tapping/untapping the selection or for the graveyard, and no automatic combat-damage maths outside the experimental damage buttons. (r3)
- **TRN-025** While you have a Stack entry's card menu open, the quick actions add "Move to Graveyard" (instant or sorcery), "Move to Battlefield" (permanent) or "Remove this effect" (ability marker). (r1) (?)

### Game start and mulligans

- **TRN-026** The game setup's Starting player box offers Random or either player, plus an "Alternate" switch in its top-right corner, on by default: after each play ends, the player who did NOT start it is set to start the next. You can still pick by hand. (r28)
- **TRN-027** A game begins with both players in the status "Mulliganing". While yours is, your quick actions are "Mull N", "Mull −1" and "Ready"; Ready clears the status. The non-starting player sees only those three, with no step arrow. (r25)
- **TRN-028** The starting player's normal turn-1 quick actions stay hidden while either player's status is exactly "Mulliganing"; any manually set or edited status counts as cleared. (r25)
- **TRN-029** There is no Turn 0: both players mulligan during turn 1, while their opening status is set. (r25)

### Life and player counters

- **TRN-030** Each player starts at 20 life. Tapping a life square opens a floating Life panel with life and status but no counters: the life row (heart, −, value, +) sits on the panel edge facing the life square, also in landscape, where the panel opens beside the square. (r26)
- **TRN-031** Life and player counters can be changed by either player, by − / + steppers, by tapping the value to type a number, or by dragging the value. (r30)
- **TRN-032** Value drags: the dominant axis counts; right is +, left is −; vertically, toward the other player is −, toward your own edge is +. The first 3 units cost 33.6dp each, then it speeds up (about 30 units at 288dp). A bubble anchored to the value (above it, below near the top edge) shows e.g. "+3 → 23". (r31)
- **TRN-033** Each player has six coloured counters: red, blue, green, yellow, purple and grey. 0 and empty are the same everywhere. (r15)
- **TRN-034** Portrait: each player's row shows packed piles plus an always-visible 3×2 counter grid (a faint outline when a counter is empty). Tapping the grid opens a Counters popup with all six counters and no life. (r15)
- **TRN-035** Landscape: empty counters are left off the table, and the Life panel then has a Counters button to reach them. (r15)
- **TRN-036** Set/edit values everywhere use "− [value] +" with one square value field. (r13)

### Status bubbles

- **TRN-037** A player sets their own status in their Life panel: presets OK?, Yes, No, Wait, Thinking…, Go ahead and GG, plus a pen and a Clear button, in a 3×3 grid. Clear and Hide are always shown, greyed when there is no status. (r26)
- **TRN-038** The pen opens a Status dialog prefilled with the current text, selected, keyboard up; up to 512 characters, wrapped, with a "n/512" count; Enter or Save sets it. A preset, Clear or Save closes the panel. (r30)
- **TRN-039** A status shows as a speech bubble by the life square (under the opponent's, above your own), wrapping over up to four lines. The Life panel shows the full status, scrolling after four lines. Statuses stay until cleared. (r30)
- **TRN-040** Setting and clearing a status are game events (in history, undoable) and never change whose move it is. (r25)
- **TRN-041** Each viewer can Hide a player's status bubble (until that player sets a new status) or Mute it for this game (it becomes a small dot, the full text stays in the Life panel). Show and Unmute replace them. These choices are local to the device; they don't silence notifications. (r25)

### Opponent presence

- **TRN-042** The opponent's life square has a small dot in its corner: green when they have this game open and touched it in the last 2 minutes, yellow when open but idle, grey when away. It is hidden while you are disconnected. (r29)
- **TRN-043** The opponent's Life panel has an info line with the same dot: "Online", "Idle 7 min", "Last seen 12 min ago", "Last seen 3 h ago", "Last seen Oct 27" or "Not seen yet". (r29)

### Dice and coins

- **TRN-044** Roll die… asks for the number of sides (6 by default, any whole number ≥ 1); Flip coin flips at once. Results are random; nobody can pick them. (r12)
- **TRN-045** A result chip appears over the roller's half of the table ("d20 → 14", "Heads / win", "Tails / loss") for about 2.5 s and then fades, for both players, live and in playback but not on jumps or catch-up. In timeline mode it stays while you are stopped on that event group. The timeline icon shows no value badge. (r34)

### Result suggestions

- **TRN-046** Suggest game end opens a chooser My win / My loss / Draw game, then a confirm dialog ("Suggest my win?", "Suggest my loss?" or "Suggest draw?") with an optional one-line "Reason", at most 200 characters (placeholder "Infinite combo"). (r24)
- **TRN-047** A waiting suggestion shows as a panel on the table, not a dialog: "<name> proposed their win." (or loss, or "a draw"; "You proposed your win." for the author) with the reason in quotes. The opponent gets Decline and Accept, the author Withdraw. (r24)
- **TRN-048** Decline and Withdraw are their own events, not undos. (r18)
- **TRN-049** While a suggestion waits, every other game action is blocked for both players; chat, timeline browsing, hold-preview and Inspect still work. (r27)
- **TRN-050** In the timeline the lock applies only when the shown board already includes the suggestion: from an earlier point or another branch you can continue and play on, leaving the suggestion behind on the old branch. (r27)
- **TRN-051** The suggestion panel appears in playback once its event is reached; its buttons work only at the live end, with playback paused. (r18)

### Combat area

- **CMB-001** Combat uses a temporary combat area between the two battlefields. It is created from Table actions, or by itself when the first attacker is declared. It stays open through step changes and closes only on purpose: Close combat area, or a quick action that closes it. (r16)
- **CMB-002** Attackers and blockers are combat copies of real cards: the real card stays where it is, and anything done to a copy's card applies to the real card. A card has at most one copy per combat area. (r1)
- **CMB-003** Combat copies are always upright and can't be tapped. Tapping one opens its own one-page Combat menu, not the normal card menu: Inspect, Note, Ping / Unping, Attack →, Remove from combat, Graveyard, Exile, Hand, Deal to target, Deal to target or creatures, Link, Links. It has no tap, counter, flip or other battlefield controls (use the real card for those); Graveyard, Exile and Hand move the real card on purpose (CMB-004). Double-tap does nothing; hold inspects. (r28)
- **CMB-004** Remove from combat removes only the copy (the real card is not untapped). Graveyard, Exile and Hand move the real card to its owner's zone (Hand is greyed for a token's copy). (r28)
- **CMB-005** Combat copies can have their own notes and pings, separate from the real card. (r13)
- **CMB-006** When the real card leaves the battlefield, all its combat copies disappear; surviving blockers of a removed attacker stay in place. Closing the area removes all copies but leaves real cards and their state unchanged. (r1)
- **CMB-007** Rows for the attacking player: target label row on top, then blockers, then attackers. The defender sees it mirrored: attackers on top, their blockers below, target names at the bottom. (r28)
- **CMB-008** Pairs are packed groups with single faint dividers (no per-column borders), centred as a group and re-centred when added; cards sit centred in their slot (one attacker over two blockers is centred), with about two card-border widths of margin. (r7)
- **CMB-009** Dragging a battlefield card into the combat area makes its copy there. Dropping left or right of all groups makes a new pair at that end; near a divider, a new pair there. While dragging, a faint shorter divider shows where it lands; reordering works the same both ways. Dragging a copy between rows changes its role. (r7)
- **CMB-010** Dragging a combat copy to a real zone (e.g. graveyard) moves the real card there. (r1)

### Attack targets

- **CMB-012** While combat is open, every attackable target always has its own lane in the same attacker/blocker rows: the defending player first, then each face-up planeswalker and battle in battlefield order, including your own Sieges (protected by the opponent). An empty lane is one card slot wide. (r27)
- **CMB-013** Lane labels sit in their own label row, never covered: the player lane shows the defending player's round avatar and name, card lanes tiny art and name; a hidden target shows only "→ Face-down card". Names are always shown in full at card-title size; lanes widen to fit and a too-wide area pans with the table. (r31)
- **CMB-014** Lane separators are thicker than pair dividers (2dp, fairly dark, scaling with card size), run through the label row, and keep about 2dp of space around cards. (r31)
- **CMB-015** Dropping an attacker into another lane, also an empty one, retargets its whole pair (blockers stay attached); dropping into the blocker row makes it a blocker. (r31)
- **CMB-016** "Attack →" opens one row per target, each with a small image (avatar or art) and the full name on an opaque background; the current target is marked and greyed. The icon is a crosshair. On a declared attacker's own card menu, Attack → replaces Declare and retargets its copy. (r34)

### Declaring and blocking

- **CMB-017** Declare attackers with a selection: "Declare + tap" and "Declare" (without tapping). New attackers go to the right of existing ones; the selection clears after declaring. Without a selection: "Select attackers" ("Select more attackers" once attackers exist). (r16)
- **CMB-018** Declare attackers with nobody declared: "No attack" closes the empty combat area and goes to Main 2. With attackers declared: "Assume no blocks" goes to Declare blockers and then to Combat damage as two separate steps, so the opponent can go back to Declare blockers and block. (r28)
- **CMB-019** Declare + tap puts the taps first, and the step into Declare blockers joins the attack, so the timeline always stops at Declare blockers for the defender. (r29)
- **CMB-020** The defender at Declare blockers gets "No blocks + next". Blockers are placed by dragging own cards into a pair's blocker row. (r3)
- **CMB-021** End of combat: "Close + next" moves on to Main 2 and closes the combat area. (r16)

### Combat damage (experimental)

- **CMB-022** At Combat damage the turn player gets "Deal combat damage" (or "First strike damage" and "Regular damage" when any combatant has first or double strike) plus "Close + next" (closes combat, goes to Main 2). The damage buttons deal for every combat copy on both sides, then clear the selection. (r16)
- **CMB-023** Damage buttons grey out once their damage was dealt in this combat (first strike after one use, regular after two); undoing the damage enables them again. (r28)
- **CMB-024** Assignment follows the shown left-to-right order: lethal damage first (using current and until-end-of-turn P/T and damage already marked), the rest to the last blocker, or to the attack target with trample. Unblocked attackers hit the player's life or the planeswalker/battle number. A blocked attacker whose blockers are gone deals nothing unless it has trample. (r16)
- **CMB-025** Keywords come from the printed card (none for face-down cards) and whole words in its note or until-end-of-turn note: trample, deathtouch, first strike, double strike and lifelink. Infect, wither or unknown numbers skip that card with an "assign manually" note. Deaths are never handled. (r19)
- **CMB-026** Copy menus offer "Deal to target" (attackers only) and "Deal to target or creatures" for the pressed or selected copies; on a real card's menu they act on its combat copies. Each press is one Undo. (r28)

### Putting cards on the Stack

- **STK-001** Every way of putting a card on the Stack (from battlefield, hand, graveyard, exile or library) adds a Stack entry pointing at the real card; the real card stays where it is. Several entries may come from the same card. (r17)
- **STK-002** A hand card's entry is shown face up to both players while it is on the Stack. (r2)
- **STK-003** Stack cards are always upright and can't be tapped. (r1)
- **STK-004** A hand card double-tapped onto the Stack (ZON-004) that is a non-land permanent on all its faces resolves onto the main battlefield row by itself; play and resolve are one Undo. (r15)
- **STK-005** "Face down" in the hand menu puts the card on the Stack face down, and it resolves straight onto the battlefield, still face down (2/2). (r21)
- **STK-006** Adding an entry shows a ghost card flying from the card to the Stack; removing one flies back. An entry leaving to the graveyard, battlefield or exile flies to that destination. Undo reverses the flight. (r14)

### Stack display

- **STK-007** Portrait: the Stack is a fixed-size HUD pinned at the right edge, vertically mid-screen. Landscape: it sits centred in the side area. (r17)
- **STK-008** The Stack area has a ~3dp very dark purple border and a soft shadow (floating look), a fixed 12dp inset, and its count on the top-left corner of the border. Only the top entry is shown, at single-card size. (r14)
- **STK-009** Notes are allowed on Stack cards, and Stack entries can have their own notes and pings, separate from the real card, set from Manage Stack (STK-019). (r35)

### Stack bubbles

- **STK-010** Tapping the top Stack entry (and right after you put a card on the Stack) shows speech-bubble actions around it that animate in; tapping elsewhere dismisses them. (r3)
- **STK-011** Bubble above: "Put to battlefield". It removes the entry; a real card not on the battlefield goes to its owner's battlefield, the land row if it is a land, otherwise the main row, at the first free spot. If it is already there, only the entry is removed. (r34)
- **STK-012** Bubble below: "Put to graveyard". It removes the entry and moves the real card to its owner's graveyard, even for a permanent's entry. (r1)
- **STK-013** Corner bubbles: "Remove from Stack" (the card stays where it is), with its tail pointing diagonally at the Stack, and "Put to exile" in the bottom-left corner. (r13)
- **STK-014** After a bubble action, the new top entry opens its bubbles as if tapped. Drags and Manage Stack removals don't do this. (r7)
- **STK-015** Normal drags may only pull the top entry. Dragging it to a zone moves the real card and removes the entry; dropping it into the card's current zone only removes the entry. (r3)

### Text marks

- **STK-016** The bubble toward the centre is a "Mark text" handle: dragging up or down cycles through the card's text options, showing the selected text live; releasing sets one shared mark. Options are the rules-text paragraphs; for split, adventure, double-faced and similar cards the part titles come first. Nothing checks which text is legal. (r1)
- **STK-017** A Stack card shows all its marks as coloured numbered dots in its corner (8 dark colours), also listed on inspect. Marks clear when the card leaves the Stack and never show on the original card. Hidden cards offer no text options. (r7)

### Manage Stack

- **STK-018** Double-tapping the Stack (its card or count) opens "Manage Stack · N" with every entry, top first ("Top", "2", …), each with art, name and its marks. (r8) (?) code differs from r1: double-tap opens Manage Stack, not the full card menu.
- **STK-019** Each entry has square icon buttons: Remove, Battlefield, Graveyard (same as the bubbles), Copy (a new entry for the same card on top of the Stack), Change controller (of the entry only; the real card's owner is unchanged), Note and Ping (both on that entry only, not its real card). Battlefield and Graveyard are greyed on copies; Note is greyed on a real card lying on the Stack. (r35)
- **STK-020** Each entry lists its text options as toggle chips, so one entry can hold several marks. (r7)
- **STK-021** The Stack order can't be changed: there is no reordering. Holding an entry inspects it above the Manage Stack view. (r11)
