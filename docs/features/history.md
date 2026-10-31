# History

The game timeline: the event strip, timeline mode and playback, catching up on what happened, branches and rewinds, undo/redo, event texts and icons, event groups and pings.

### Play-mode event strip

- **HIS-001** In play mode the bottom bar shows a read-only strip of the latest events, laid out like the timeline: the newest event is centred in a square highlight. (r14)
- **HIS-002** The strip can be panned by hand to look back; it snaps back to the newest event whenever a new event arrives, with no idle-time snap-back. Panning never changes the board. (r17)
- **HIS-003** Tapping an event on the strip shows its text in a small dark popup that fades after about 1.6 s; holding keeps it shown. No ripple on the strip. (r17)
- **HIS-004** A history icon button at the end of the strip enters timeline mode; Table actions has a "Timeline" entry that does the same. Both open timeline mode paused at the newest event. (r17)
- **HIS-005** Each event is a 20 dp icon at a 24 dp pitch, coloured by who made it (your teal, the opponent's red, system grey). A faint vertical line separates event groups, and a small fork line marks events that have other branches. (r6)

### Timeline mode

- **HIS-006** Timeline mode starts by itself only when a game opens (or the app returns) with unseen events; otherwise only when the player enters it. Opponent actions, hold-inspect and menu actions keep the table live. (r29)
- **HIS-007** Timeline mode and play mode use the same bottom area height and cross-fade in about 200 ms, so the board never jumps; the hand, piles, life and counters sit unchanged directly above the timeline panel. (r14)
- **HIS-008** In timeline mode the top and bottom bars turn grey, the battlefield is dimmed, and the board centre shows a faint "Timeline mode" label with icon and, on its own line, the playback status "Playing", "Paused" or "Rewinding" with its icon. (r31)
- **HIS-009** When playback starts or stops (by hand, by auto-pause or at the end), a large play or pause sign flashes in the board's top-right corner; while a rewind runs, a rewind sign stays there. (r31)
- **HIS-010** The timeline panel has the strip with a playback settings gear and a game tree button at its end, and below it Previous, the Auto button and Next. Previous/Next are plain borderless arrows. (r21)
- **HIS-011** Swiping the strip or tapping an event moves the review cursor to that exact event (also mid-group); holding an event shows its text without moving the cursor. (r33)
- **HIS-012** Previous and Next step one event group along the visible line: the live line, or the old branch being viewed from its start to its own tip. The strip shows that whole visible line. (r31)
- **HIS-013** The Auto button reads "Auto: off" (green play icon) or "Auto: on" (orange pause icon); tapping toggles automatic playback. There is no hint line under it. (r6)
- **HIS-014** Auto always heads for the live game. On an old branch, or when the opponent branched, the button shows the rewind sign and "Rewind": it first rewinds to where the lines split, then plays on. (r31)
- **HIS-015** Navigating to the last event by hand stays in timeline mode; only automatic playback reaching the end returns to play mode, even if the last event would auto-pause, and its animation still plays. (r14)
- **HIS-016** In timeline mode double tap does nothing (no error), multi-select is off, a one-finger drag on empty table pans, and card menus and bubbles do not act on the game. (r13)
- **HIS-017** Hold-inspect works on every face-up card in timeline mode too, and holding a card pauses playback. (r8)
- **HIS-018** "Return to current" in the top bar leaves timeline mode for the live game. Back leaves timeline mode at once without playing the rest back. (r6)
- **HIS-019** Leaving timeline mode never asks a question and always returns to the live game; branching needs its own action (HIS-036). (r6)
- **HIS-020** A pending win/loss/draw suggestion never blocks the timeline. In timeline mode it locks play only where the shown board already includes it; continuing from an earlier point or another branch is allowed and leaves the suggestion behind. (r27)

### Playback

- **HIS-021** Playback advances one event group at a time and waits after each group's animation: 1 s by default, adjustable from 0.1 s to 10 s, stored on the device per account. (r29)
- **HIS-022** Rewinding to a branch point steps back group by group with its own "Rewind pause", 0.1 s by default, adjustable from 0.02 s to 2 s. (r29)
- **HIS-023** Starting Auto or a rewind takes its first step right away; later steps keep the pause. (r31)
- **HIS-024** Auto stops after an opponent's group that matches an enabled auto-pause trigger, after that group's animation. Your own actions never auto-pause. (r12)
- **HIS-025** Auto-pause triggers and defaults: Added to Stack on, Removed from Stack off, Reveal on, Pings / comments on, Die / coin results off, Your life / cards hit on, Move passes to you on, Attackers declared off, Opponent plays a land off, Opponent puts a nonland card onto the battlefield from hand off. (r15)
- **HIS-026** A hand-to-battlefield move never counts as a Reveal. (r13)
- **HIS-027** "Move passes to you" pauses only if the next event is not your own. Status changes never auto-pause; opening a graveyard/exile panel is not a reveal. (r17) (?)
- **HIS-028** Playback settings live in Settings under "Timeline playback" and in a dialog from the timeline's gear: the auto-pause switches ("Stop Auto after an opponent's matching action"), "History playback pause", "Rewind pause", the auto-rewind switches and Card move speed. (r31)
- **HIS-029** Live play: when the opponent reveals cards in a panel, the table holds the face-up panel for one playback pause before moving on, without entering timeline mode. The board just before an opponent's result suggestion is likewise shown before the suggestion. (r8)
- **HIS-030** Playback and live opponent actions animate each change (cards fly, rotate, flip, fade; moves between piles fly too). Rewinds animate backwards. A jump (tree pick) shows no effects. (r31)
- **HIS-031** Changes show a delta tag and a number flash (about 1.5 s fade; changes in that window add up) for life, player counters, card P/T and counters, and a highlight for note, owner, face, link, text mark and step changes; for your own and the opponent's changes, live and in playback, not on jumps. (r13)

### Opening and returning to a game

- **HIS-032** The device remembers, per game and account, the last event shown live. Debug builds keep it across logout and account switches. (r8)
- **HIS-033** Opening a game with unseen events opens timeline mode paused at the last event you saw; nothing plays by itself, so you can play it back and react or branch. Playback never skips ahead to the latest state. (r29)
- **HIS-034** With no saved point (first open, new device) the game opens live at the newest move. (r29)
- **HIS-035** Leaving the app pauses at the shown point at once; coming back with unseen events stops there in timeline mode instead of rushing through them. A return where only chat or other non-event data changed stays live. (r28)

### Branches and rewind

- **HIS-036** In timeline mode at a past point, holding Auto, or tapping "Continue playing from here" (fork icon) in the top bar, arms a continuation at once, with no dialog. (r6)
- **HIS-037** While a continuation is armed, a fork icon shows in the top bar; tapping it cancels. The next action starts a new branch from that point and the old future is kept. No continuation is offered at the newest event. (r6)
- **HIS-038** If the opponent continued from an earlier point (on opening, on returning, or live while you watch), the table switches to timeline mode paused where you were, with an orange "Opponent branched" chip and the Auto button showing Rewind. (r33)
- **HIS-039** While "Opponent branched" is pending, Previous/Next and the strip are locked and holding Auto does not branch: you choose when the rewind starts and must watch it. During the rewind the settings and tree buttons are disabled too. (r31)
- **HIS-040** Viewing an old branch you went to yourself shows a grey "Old branch" chip while the visible line is that branch; it is not locked. (r31)
- **HIS-041** Settings "Rewind automatically when the opponent branched": "During live play" and "When opening a game", both off by default; returning to the app uses the opening setting; already paused in the timeline never auto-rewinds. (r33) (?)
- **HIS-042** Every branch start re-randomizes the unknown parts of both libraries with a seed fixed by the fork point and the forking player, so draw, undo, draw (or branching again from the same point) gives the same cards. A kept-revealed top card stays known. (r13)
- **HIS-043** There is no branch archiving and no "Make current" action anywhere. (r6)

### Game tree dialog

- **HIS-044** The game tree opens from the timeline's tree button as a full-screen dialog drawing the whole game as event groups with the same icons as the strip, branches as parallel lanes. (r4)
- **HIS-045** The tree opens centred on the event being viewed, has no legend and no bottom buttons, and shows only the selected group's text (two lines) at the bottom. (r6)
- **HIS-046** Tapping a group moves the review cursor to that group's last event and closes the tree; holding a group selects it and shows its text without closing. (r6) (?)
- **HIS-047** The tree pans and pinch-zooms (also over chips). The first event of each turn has a "T5"-style label in that player's colour. (r13)
- **HIS-048** Each branch keeps the lane it got when it was created; the current path is drawn highlighted in green, branch ends get a dot and the current event a ring. (r13)
- **HIS-049** The "Game tree ▾" menu toggles between "Show hidden branches" and "Hide simple alternatives". (r4) (?)

### Event groups

- **HIS-050** History is shown in event groups: playback, Previous/Next and auto-pause work per group, while undo works per action. A group holds one player's related actions. (r2)
- **HIS-051** A manual "pass move" joins the group before it, whoever made it (for display only); undo still undoes only the pass. (r2)
- **HIS-052** Taps group with the actions around them and lead into the next action; untaps form their own groups, joining only other untaps, and never group with taps. Pings, comments and step changes join the author's current group. (r13)
- **HIS-053** Repeated moves of the same kind (same source and destination, e.g. several draws) form one group; moving cards into a library followed by shuffling it is one group. (r13)
- **HIS-054** Casting: after "Added to Stack", the same player's text marks and notes on that Stack entry and taps (mana) join its group. (r14)
- **HIS-055** Double-tap auto-resolve from hand records the Stack entry and the move to the battlefield as separate groups, so the opponent's playback can stop at the Stack. (r15)
- **HIS-056** Revealing a card in a panel and moving it on are separate groups, so the opponent sees the flip before the card flies. Closing a Search, Look or Reveal panel is its own group that nothing joins. (r29)
- **HIS-057** Taps come first, then attacker declarations, and the step into Declare blockers joins that declaration group and ends it, so the timeline always stops at Declare blockers for the defender. (r29)
- **HIS-058** Ending the turn or skipping ahead records every step passed on the way as events (rules-accurate: the starting player's first draw step and blockers/damage without attackers are skipped). They stay in one group and one undo, and never pause playback, stop, pass the move or notify. (r33)
- **HIS-059** A group of only passed steps waits for the same player's next real action in the reached step (e.g. the draw), which leads it; "Untap, draw → main" stays two groups. (r33) (?)
- **HIS-060** The keep-top-revealed toggle and result suggestions/answers are groups of their own. (r13)

### Event texts

- **HIS-061** Every event has a short text: hold an icon on the strip or timeline, or select it in the tree. Texts put the subject first and the result last, with no actor name. (r16)
- **HIS-062** Texts never reveal what the viewer could not see at that event (hand, library, face-down, the other player's look); hidden cards read "a card" or "N cards". (r27)
- **HIS-063** Card moves read as plain sentences: "Drew …", "Took X from library", "Returned X to hand", "Put X onto the battlefield", "Cast X" (or "face down"), "Discarded …", "Milled …", "Put X into graveyard", "Exiled …", "Put X on top/the bottom of library", "Put X into Temp N". (r33)
- **HIS-064** Moves to another player's battlefield read "<receiver> gains control of X"; moves within one place read "X moved on the battlefield" / "X moved within graveyard". (r33) (?)
- **HIS-065** A player's name appears in a move text only when the zone isn't the moved card's owner's ("Drew a card into bob's hand"). (r33) (?) contradicts r27: names only for a battlefield that isn't the owner's
- **HIS-066** Random moves read e.g. "Discarded X at random", "Exiled X from hand at random", "Put … on top of library in random order"; moves made during a search add "(after search)". (r33) (?)
- **HIS-067** A group of card moves merges same-kind moves into one text, joined with "; " ("Drew Shock, Opt, 2 cards; Discarded a card"), listing every card the viewer saw; longer than 180 characters ends in "…". Other groups read as their most important event. (r33)
- **HIS-068** Other texts include "Moved to <step>", "Turn N → <player>", "Move passed → <player>", "d20 → 14", "Heads / win" / "Tails / loss", "Mulligan to N", "Status → <first 48 characters>…", "Library searched → N cards" and "Search closed → Library shuffled". (r34)
- **HIS-069** Texts are made when the event is applied and also exist for older moves; when the app lacks the board context it says only "X moved", never a guessed zone. (r30)

### Event icons

- **HIS-070** A card move's icon shows where the card went: hand = the draw (download) icon, battlefield = dashboard, Stack = layers, graveyard = sweep, exile = block, library (top or bottom) = books, Temp pile = inbox; moves within the same place keep the move icon. (r33)
- **HIS-071** Edit icons show the direction: heart / broken heart for life gained / lost, +1 / −1 / 0 for card counters, up / down arrows for player counters and P/T, a flame for damage and a bandage for removed damage. (r34)
- **HIS-072** Step changes show an arrow, a new turn a skip-next sign, closing a view an eye-off, closing combat a check mark, choosing an attack target a crosshair, and passing the move a flag. (r34)
- **HIS-073** An unchanged or non-numeric value counts as an increase, P/T reads by the sum of power and toughness, and setting a counter to empty shows the down icon (only clearing shows 0). (r34) (?)
- **HIS-074** Events keep the icon they were recorded with, so older events keep older icons; an icon the app doesn't know shows as a neutral dot instead of crashing. (r31)

### Event text chip

- **HIS-075** A short text of what just happened shows on the table's top row (up to 2 lines, max ~420 dp wide), without blocking taps on the controls below. Settings "Event text" turns it off; default on. (r29)
- **HIS-076** In timeline mode it shows for every group shown, yours too, also when scrubbing (a partial group shows its partial text); live it shows only for the opponent's new groups, and stays quiet on open, return and undo. (r29)
- **HIS-077** The chip appears and changes at once and fades out in 450 ms after the longer of 2 s and the playback pause, on its own clock even while paused; moving to another board drops it at once. (r31)

### Pings

- **HIS-078** The table mode button cycles Normal → Select → Ping. In Ping mode, tapping a card, a Stack/combat entry, a life total or counter (the player), an area or an empty spot pings it; tapping a ping removes it. The card menu has Ping too. (r3)
- **HIS-079** A ping belongs to the event group being shown and joins its author's group. Ping mode turns back to Normal when the shown group changes. (r7)
- **HIS-080** An active ping shows a numbered marker with an expanding ring loop (about 1.2 s) in the pinger's colour. Numbers always count the active pings of the group (ping a, b, unping a → b becomes 1). (r7)
- **HIS-081** When the shown group moves past a ping group, its rings linger about 2.5 s and fade out instead of vanishing; jumps never leave lingering rings. (r14)
- **HIS-082** Stack and combat entries can have their own pings, separate from the real card. (r13)

### Undo and redo

- **HIS-083** The Undo button sits at the left end of the bottom bar: tap = Undo, hold = Redo with a small haptic tick. (r3)
- **HIS-084** Undo always targets your own latest action, even if the opponent acted after it; it is refused when the opponent made more than 50 actions since then. (r6)
- **HIS-085** One invocation (a quick action, a multi-selection action, a batched double-tap cast) undoes and redoes as one. (r15)
- **HIS-086** Undo removes the event from history: the game is exactly as if it never happened, with no trace in the timeline. (r6)
- **HIS-087** Undo/redo is allowed only if every later event still changes the fields it concerns from and to exactly the same values; what players have seen never matters, so undoing your own look, scry or reveal is allowed. When unsure, it refuses. (r11)
- **HIS-088** A ping concerns only its target: an opponent's ping on your attacking copy blocks undoing that attack; a ping on the card itself does not. (r11)
- **HIS-089** Undo never changes an existing branch. A refused undo/redo shows a short reason starting "Can't undo:" / "Can't redo:", e.g. "your opponent has made too many moves since your last one." (r6)
