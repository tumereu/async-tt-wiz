# History

The game timeline: the event strip, timeline mode and playback, catching up on what happened (the catch-up screen, playback stops, assumed answers, branches when an answer differs), lines of play, undo and undo requests, event texts and icons and event groups.

### Play-mode event strip

- **HIS-001** In play mode the bottom bar shows a read-only strip of the latest events up to the last one shown to you (your local head, HIS-032), laid out like the timeline: the newest event is centred in a square highlight. Nothing past your local head shows anywhere (I-D56 §7). (r48)
- **HIS-002** The strip can be panned by hand to look back; it snaps back to the newest event shown to you whenever a new one is shown, with no idle-time snap-back. Panning never changes the board. (r48)
- **HIS-003** Tapping an event on the strip shows its text in a small dark popup that fades after about 1.6 s; holding keeps it shown. No ripple on the strip. (r17)
- **HIS-004** A history icon button at the end of the strip enters timeline mode; Table actions has a "Timeline" entry that does the same. Both open timeline mode paused at the newest event shown to you; the timeline ends there. (r48)
- **HIS-005** Each event is a 20 dp icon at a 24 dp pitch, coloured by who made it (your teal, the opponent's red, system grey). A faint vertical line separates event groups. There is one line of play and no branch markers (I-D56 §8). (r48)

### Timeline mode

- **HIS-006** On engine state (I-D56 §7) coming back with the other player's events past your local head opens the catch-up screen (HIS-094) and then playback that stops at your decision points (HIS-095); only your own events since open live. Otherwise timeline mode starts only when you enter it; opponent actions, hold-inspect and menu actions keep the table live. (r48)
- **HIS-007** Timeline mode and play mode use the same bottom area height and cross-fade in about 200 ms, so the board never jumps; the hand, piles and life sit unchanged directly above the timeline panel. (r45)
- **HIS-008** In timeline mode the top and bottom bars turn grey, the battlefield is dimmed, and the board centre shows a faint "Timeline mode" label with icon and, on its own line, the playback status "Playing", "Paused" or "Rewinding" with its icon. (r31)
- **HIS-009** When playback starts or stops (by hand, by auto-pause or at the end), a large play or pause sign flashes in the board's top-right corner; while a rewind runs, a rewind sign stays there. (r31)
- **HIS-010** The timeline panel has the strip with a playback settings gear at its end, and below it Previous, the Auto button and Next. Previous/Next are plain borderless arrows. There is no game tree button. (r48)
- **HIS-011** Swiping the strip or tapping an event moves the review cursor to that exact event (also mid-group); holding an event shows its text without moving the cursor. (r33)
- **HIS-012** Previous and Next step one event group along the main line, up to your local head. The strip shows that line. (r48)
- **HIS-013** The Auto button reads "Auto: off" (green play icon) or "Auto: on" (orange pause icon); tapping toggles automatic playback. There is no "Hold to branch" line: the timeline is view-only. (r48)
- **HIS-015** Navigating to the last event by hand stays in timeline mode; only automatic playback reaching the end returns to play mode, even if the last event would auto-pause, and its animation still plays. On engine state playback stops at your decision points (HIS-095), where the table is live. (r48)
- **HIS-016** In timeline mode double tap does nothing (no error), a one-finger drag on empty table pans, and card menus do not act on the game. (r45)
- **HIS-017** Hold-inspect works on every face-up card in timeline mode too, and holding a card pauses playback. (r8)
- **HIS-018** "Return to current" in the top bar leaves timeline mode for the live game; while you are behind (events past your local head, HIS-032) it and Back play on quickly to your next decision point, never past it. (r48)
- **HIS-019** Leaving timeline mode never asks a question; the timeline has no branching action (I-D56 §8: a branch happens only when your real answer differs from an assumed one, HIS-097). (r48)
- **HIS-020** A pending win/loss/draw suggestion never blocks the timeline. It locks play only once the board shown to you (your local head) includes it (TRN-049). (r48)

### Playback

- **HIS-021** Playback advances one event group at a time and waits after each group's animation: 1 s by default, adjustable from 0.1 s to 10 s, stored on the device per account. (r29)
- **HIS-022** Rewinding steps back group by group with its own "Rewind pause", 0.1 s by default, adjustable from 0.02 s to 2 s; it also paces a take-back after a branch or a rewind (HIS-097, HIS-099). (r48)
- **HIS-023** Starting Auto or a rewind takes its first step right away; later steps keep the pause. (r31)
- **HIS-024** Auto stops after an opponent's group that matches an enabled auto-pause trigger, after that group's animation. Your own actions never auto-pause. On engine state a decision stop (HIS-095) always stops playback, with no time limit, whatever the switches say. (r48)
- **HIS-025** Auto-pause triggers and defaults: Added to Stack on, Removed from Stack off, Reveal on, Your life hit off, Your cards hit on, Move passes to you on, Attackers declared off, Opponent plays a land off, Opponent puts a nonland card onto the battlefield from hand off. (r46)
- **HIS-093** On engine state playback stops at every decision point of yours by your response mode (HIS-095), whatever the auto-pause switches say; it waits there with no time limit, and holding Auto does nothing more (no branching from the timeline). (r48)
- **HIS-094** Catch-up screen (I-D56 §7): coming back with the other player's events past your local head opens a "Catching up" dialog: "<name> played on while you were away. Their actions play back, stopping wherever you decide.", your response mode as three choices with what each means (TRN-059), and Play. It shows no count or preview of what is coming. (r48)
- **HIS-095** Playback on engine state shows the other player's events one at a time, paced by the History playback pause, and stops before an assumed answer about you wherever your response mode stops (Full control: every assumed pass; Normal: your stops and pauses; Auto-skip: those where you could respond; assumed blocks and keeps always stop). There the table is live at that point with "Your decision · <what was assumed>" above your hand: answering the same confirms it and playback goes on; anything else is a branch (HIS-097). Elsewhere showing an assumed pass confirms it. (r48)
- **HIS-096** Assumed answers (I-D56 §3, §4): at the other player's priority points the device assumes their pass ("Assume resolves"), their blocks ("Assume blockers") or their opening keep ("Assume keep") so the game can go on while they are away, never at their own main phase. Their pauses are assumed at once while they are away, and waited for while they are online (green dot) unless "Always assume resolves" is on; a wait that began while they were online turns async 60 s after they were last online. Any other decision of theirs is a real wait. An assumption whose outcome would show you a hidden card is not made (TRN-067). (r48)
- **HIS-097** Branch (I-D56 §8): when the other player's real answer differs from what was assumed, the game goes on from their answer and the line after it is discarded: they never see it; your table goes back with the rewind animation and a note "<name> responded — your later actions were taken back" ("blocked differently", "took a mulligan"). A new line starts (HIS-042). There is no other way to branch. (r48)
- **HIS-098** Free undo (I-D56 §10): free when everything after the point is your own (real inputs or assumptions not yet confirmed) or the other player's table-only events the undo rule lets stand, and none of it showed you something new: a card you couldn't see before (your draw, look, scry, search, a reveal), a random result, or a wait for the other player's choice from a source you can't see. (r48)
- **HIS-099** Undo dialog and requests (I-D56 §10): holding Undo, or an Undo that isn't free, opens "Undo": the actions shown to you, newest first, to pick the point to go back to, with a small view of the board at that point below them, then Undo (free) or, when the other player must approve, an optional message and "Ask <name>". They get "<name> asks to undo" with the message, where the game goes back to as far as they have seen ("and actions you haven't seen yet are taken back" when past that) with a small view of that board (their own newest board when the point is past what they have seen), and Deny / Accept; it never says how many events go. While a request is open neither player acts (the waiting bar says so; the requester may Cancel); looking and playback go on; a concession still ends the game. A rewind deletes the events after the point and starts a new line; a player who had seen past it sees "<name> took back …". (r49)
- **HIS-090** "Your life hit" is an opponent's change to your life or your player counters; "Your cards hit" is an opponent's action on a card you own or control (also its combat copies and Stack entries) or one giving you a card. Each device's earlier "Your life / cards hit" choice carries over to "Your cards hit". (r36)
- **HIS-026** A hand-to-battlefield move never counts as a Reveal. (r13)
- **HIS-027** "Move passes to you" pauses only if the next event is not your own. Status changes never auto-pause; opening a graveyard/exile panel is not a reveal. (r17)
- **HIS-028** Playback settings live in Settings under "Timeline playback" and in a dialog from the timeline's gear: the auto-pause switches ("Stop Auto after an opponent's matching action"), "History playback pause", "Rewind pause" and Card move speed. There are no auto-rewind switches. (r48)
- **HIS-029** Live play: when the opponent reveals cards in a panel, the table holds the face-up panel for one playback pause before moving on, without entering timeline mode. The board just before an opponent's result suggestion is likewise shown before the suggestion. (r8)
- **HIS-030** Playback and live opponent actions animate each change (cards fly, rotate, flip, fade; moves between piles fly too). Rewinds and take-backs animate backwards. (r48)
- **HIS-031** Changes show a delta tag and a number flash (about 1.5 s fade; changes in that window add up) for life, player counters, card P/T and counters, and a highlight for face and step changes; for your own and the opponent's changes, live and in playback, not on jumps. On engine state they come from the events shown (life, P/T, step and turn, face, a change of control, a library's shuffle), also when a take-back goes back; not on opening the game or in timeline mode. (r49)

### Opening and returning to a game

- **HIS-032** On engine state the server keeps, per game and account, your local head: the newest event shown to you (live or in playback) or added by you; a second device opens at it. Everything shown to you (strip, timeline, chips, result panel, move pill and bar colours, game list, notifications) comes from it and your next stop (I-D56 §7). (r48)
- **HIS-033** Opening a game with the other player's events past your local head shows the catch-up screen first (HIS-094), with no count or preview of what is coming; Play then plays them back, stopping at your decision points (HIS-095). Playback never skips ahead. When everything since is your own, the game opens live. (r48)
- **HIS-034** A new device opens a game at your local head kept on the server (HIS-032). (r48)
- **HIS-035** Leaving the app pauses at the shown point at once; coming back with the other player's events past your local head shows the catch-up screen (HIS-094) instead of rushing through them. A return where only chat or other non-event data changed, or only your own events arrived, stays live. (r48)

### Lines of play

- **HIS-042** Every line of play starts by reshuffling the unknown parts of both libraries (shown nowhere): the game's start, every branch (HIS-097) and every rewind, free undo included (HIS-099). A card whose place someone knows (put on top or bottom by a known action, looked at or revealed where it lies, a kept-revealed top) keeps its place. Random results (shuffles, coin flips, dice, random choices) are worked out from the game's secret and the line, so the same input on a line gives the same result and a new line gives new results (I-D56 §9). (r48)
- **HIS-043** There is no branch archiving and no "Make current" action anywhere; lines left by a branch are kept for problem reports and shown to nobody. (r48)

### Event groups

- **HIS-050** History is shown in event groups: playback, Previous/Next and auto-pause work per group, while undo works per action. A group holds one player's related actions. (r2)
- **HIS-051** A manual "pass move" joins the group before it, whoever made it (for display only); undo still undoes only the pass. (r2)
- **HIS-052** Taps group with the actions around them and lead into the next action; untaps form their own groups, joining only other untaps, and never group with taps. Step changes join the author's current group. (r46)
- **HIS-053** Repeated moves of the same kind (same source and destination, e.g. several draws) form one group; moving cards into a library followed by shuffling it is one group. (r13)
- **HIS-054** Casting: after "Added to Stack", the same player's taps (mana) join its group. (r46)
- **HIS-056** Revealing a card in a panel and moving it on are separate groups, so the opponent sees the flip before the card flies. Closing a Search, Look or Reveal panel is its own group that nothing joins. (r29)
- **HIS-057** Taps come first, then attacker declarations, and the step into Declare blockers joins that declaration group and ends it, so the timeline always stops at Declare blockers for the defender. (r29)
- **HIS-058** Ending the turn or skipping ahead records every step passed on the way as events (rules-accurate: the starting player's first draw step and blockers/damage without attackers are skipped). They stay in one group and one undo, and never pause playback, stop or notify. On engine state the other player's passes assumed on the way are events of their own (HIS-092), and the other player's playback stops at those their mode stops at (HIS-095). (r48)
- **HIS-059** A group of only passed steps waits for the same player's next real action in the reached step (e.g. the draw), which leads it; "Untap, draw → main" stays two groups. (r33)
- **HIS-060** The keep-top-revealed toggle and result suggestions/answers are groups of their own. (r13)
- **HIS-092** History events are natural points where a player can act: on engine state each event is one acting point: the player's input (a cast or land play, a pass, an answer, a declaration) and everything that then happens by itself (engine-automatic answers, forced passes, the player's own device's passes by their response mode, resolutions, mana made and spent, untapping and drawing, steps passed on the way, triggers placed, state-based actions) folded into it, up to the next point where someone can act. An assumed answer (HIS-096) always begins its own event; assumed and real inputs never share one. (r48)

### Event texts

- **HIS-061** Every event has a short text: hold an icon on the strip or timeline, or select it in the tree. Texts put the subject first and the result last, with no actor name. (r16)
- **HIS-062** Texts never reveal what the viewer could not see at that event (hand, library, face-down, the other player's look); hidden cards read "a card" or "N cards". (r27)
- **HIS-063** Card moves read as plain sentences: "Drew …", "Took X from library", "Returned X to hand", "Put X onto the battlefield", "Cast X" (or "face down"), "Discarded …", "Milled …", "Put X into graveyard", "Exiled …", "Put X on top/the bottom of library", "Put X into Temp N". (r33)
- **HIS-064** Moves to another player's battlefield read "<receiver> gains control of X"; moves within one place read "X moved on the battlefield" / "X moved within graveyard". (r33)
- **HIS-065** A player's name appears in a move text only when the zone isn't the moved card's owner's ("Drew a card into bob's hand"). (r33)
- **HIS-066** Random moves read e.g. "Discarded X at random", "Exiled X from hand at random", "Put … on top of library in random order"; moves made during a search add "(after search)". (r33)
- **HIS-067** A group of card moves merges same-kind moves into one text, joined with "; " ("Drew Shock, Opt, 2 cards; Discarded a card"), listing every card the viewer saw; longer than 180 characters ends in "…". Other groups read as their most important event. (r33)
- **HIS-068** Other texts include "Moved to <step>", "Turn N → <player>", "Move passed → <player>", "d20 → 14", "Heads / win" / "Tails / loss", "Mulligan to N", "Status → <first 48 characters>…", "Library searched → N cards" and "Search closed → Library shuffled". (r34)
- **HIS-091** On engine state an assumed answer reads with the other player as its actor and "(assumed)": "Bob passed (assumed)", "Bob: no blocks (assumed)", "Bob kept (assumed)" (the strip, the event chip and the Undo dialog's list, HIS-099); other events read plainly ("You cast Storm Spark", "Bob played Mountain", "You attacked with 2", "Bob blocked with 1"). A pass says what it led to: "You passed · Storm Spark resolved", "· Turn 3 → Bob" or the new step ("· Combat"). Table-only events read "Alice staged 2 attackers", "Bob arranged 1 block", "You answered early", "You moved a card", "A result was suggested"; a rewind's new line "The game went back". Hidden cards read "a card" (HIS-062). An emblem reads like a status: "Emblem → " and the first 48 characters of its text. (r49)
- **HIS-069** Texts are made when the event is applied and also exist for older moves; when the app lacks the board context it says only "X moved", never a guessed zone. (r30)

### Event icons

- **HIS-070** A card move's icon shows where the card went: hand = the draw (download) icon, battlefield = dashboard, Stack = layers, graveyard = sweep, exile = block, library (top or bottom) = books; moves within the same place keep the move icon. On engine state an event's icon is its input's: a cast = layers, a land = dashboard, an activation = a click, a pass = an arrow (a check when something resolved, skip-next for a new turn), attacks and blocks = swords, a keep = a check, a mulligan = shuffle, a concession = the outlined flag, results = the trophy, staging = the crosshair, an early answer = the note, a card moved on the battlefield = the move icon, the game's start = the flag, a new line after a rewind = the restore icon. (r49)
- **HIS-071** Edit icons show the direction: heart / broken heart for life gained / lost, +1 / −1 / 0 for card counters, up / down arrows for player counters and P/T, a flame for damage and a bandage for removed damage. (r34)
- **HIS-072** Step changes show an arrow, a new turn a skip-next sign, closing a view an eye-off, closing combat a check mark, choosing an attack target a crosshair, and passing the move a flag. (r34)
- **HIS-073** An unchanged or non-numeric value counts as an increase, P/T reads by the sum of power and toughness, and setting a counter to empty shows the down icon (only clearing shows 0). (r34)
- **HIS-074** Events keep the icon they were recorded with, so older events keep older icons; an icon the app doesn't know shows as a neutral dot instead of crashing. (r31)

### Event text chip

- **HIS-075** A short text of what just happened shows on the table's top row (up to 2 lines, max ~420 dp wide), without blocking taps on the controls below. Settings "Event text" turns it off; default on. (r29)
- **HIS-076** In timeline mode it shows for every group shown, yours too, also when scrubbing (a partial group shows its partial text); live it shows only for the opponent's new groups, and stays quiet on open, return and undo. (r29)
- **HIS-077** The chip appears and changes at once and fades out in 450 ms after the longer of 2 s and the playback pause, on its own clock even while paused; moving to another board drops it at once. (r31)

### Undo

- **HIS-083** The Undo button sits at the left end of the bottom bar: tap = Undo (HIS-084); hold = the Undo dialog to pick any earlier point (HIS-099), with a small haptic tick. There is no Redo. (r48)
- **HIS-084** On engine state Undo goes back to just before your latest own action (your later assumed answers go with it): at once when it is free (HIS-098), else it opens the Undo dialog with that point chosen, to ask the other player (HIS-099). There is no limit on how many events go back. (r48)
- **HIS-085** One acting point (a cast with its choices, a declaration with its payment) undoes as one. (r48)
- **HIS-086** Undo removes the event from history: the game is exactly as if it never happened, with no trace in the timeline. (r6)
- **HIS-087** Undo is free only if no later event of the other player's changed what it changed (the table-only events the undo rule lets stand stay) and nothing after the point showed you something new; otherwise it needs the other player's approval (HIS-098, HIS-099). (r48)
- **HIS-089** Undo never changes a line the other player is on without their approval. A refused undo says why in plain words; an undo with nothing to undo shakes the button. (r48)
