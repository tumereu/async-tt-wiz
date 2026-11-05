# History

The game timeline: the event strip, timeline mode and playback, catching up on what happened (the paused catch-up, playback stops, assumed answers, branches when an answer differs), lines of play, undo and undo requests, event texts and icons, and how events are grouped by turn.

### Play-mode event strip

- **HIS-001** In play mode the bottom bar shows a read-only strip of the latest events up to your local head (HIS-032), laid out like the timeline, the newest centred in a square highlight; playback shows it too (HIS-103). Nothing past your local head shows anywhere. (r51)
- **HIS-002** The strip can be panned by hand to look back; it snaps back to the newest event shown to you whenever a new one is shown, with no idle-time snap-back. Panning never changes the board. (r48)
- **HIS-003** Tapping an event on the strip shows its text in a small dark popup that fades after about 1.6 s; holding keeps it shown. No ripple on the strip. (r17)
- **HIS-004** A history icon button at the end of the strip enters timeline mode; Table actions has a "Timeline" entry that does the same. Both open timeline mode paused at the newest event shown to you; the timeline ends there. (r48)
- **HIS-005** Each event is a 20 dp icon at a 24 dp pitch, coloured by who made it (your teal, the opponent's red, system grey). A faint vertical line separates turns. There is one line of play and no branch markers. (r48)

### Timeline mode

- **HIS-006** Coming back with the other player's events past your local head starts the catch-up (HIS-094); with only your own events since, the game opens live. Otherwise timeline mode starts only when you enter it; opponent actions, hold-inspect and menu actions keep the table live. (r52)
- **HIS-007** Timeline mode and play mode use the same bottom area height and cross-fade in about 200 ms, so the board never jumps; the hand, piles and life sit unchanged directly above the timeline panel. (r45)
- **HIS-008** In timeline mode the top and bottom bars turn grey, the battlefield is dimmed, and the board centre shows a faint "Timeline mode" label with icon and, on its own line, the playback status "Playing" or "Paused" with its icon. (r49)
- **HIS-009** When playback starts or stops (by hand, by auto-pause or at the end), a large play or pause sign flashes in the board's top-right corner. (r49)
- **HIS-010** The timeline panel has the strip with a playback settings gear at its end, and below it Previous, the Auto button and Next. Previous/Next are plain borderless arrows. There is no game tree button. (r48)
- **HIS-011** Swiping the strip or tapping an event moves the review cursor to that exact event; holding an event shows its text without moving the cursor. (r33)
- **HIS-012** Previous and Next step one event along the main line, up to your local head. The strip shows that line. (r48)
- **HIS-013** The Auto button reads "Auto: off" (green play icon) or "Auto: on" (orange pause icon); tapping toggles automatic playback. There is no "Hold to branch" line: the timeline is view-only. (r52)
- **HIS-015** Navigating to the last event by hand stays in timeline mode; only automatic playback reaching the end returns to play mode, even if the last event would auto-pause, and its animation still plays. (r48)
- **HIS-016** In timeline mode double tap does nothing (no error), a one-finger drag on empty table pans, and card menus do not act on the game. (r45)
- **HIS-017** Hold-inspect works on every face-up card in timeline mode too, and holding a card pauses playback. (r8)
- **HIS-018** "Return to current" in the top bar leaves timeline mode for the live game; while you are behind (events past your local head, HIS-032) it and Back play on quickly to your next decision point, never past it. (r48)
- **HIS-019** Leaving timeline mode never asks a question; the timeline has no branching action (a branch happens only when your real answer differs from an assumed one, HIS-097). (r48)
- **HIS-020** A pending win/loss/draw suggestion never blocks the timeline. It locks play only once the board shown to you (your local head) includes it (TRN-049). (r48)

### Playback

- **HIS-021** Playback advances one event at a time. An event that changes something you can see (a card moving, cast, played, resolving, dying or drawn; tapping, life, counters or damage; attacks and blocks; the other player's choices; a new turn) keeps its animation and then waits the playback delay: 0.33 s at Card move speed 1×, scaled by it (0.11 s at 3×, 1.32 s at 0.25×). Attacks play back in the combat area (CMB-007). (r52)
- **HIS-104** In playback, passes, steps where nothing happened and declaring no attackers or no blockers go by at once (only the step indicator moves) unless your response mode is Full control, where they keep the delay; the other player's staging, card places and early answers take 0.12 s. The History playback pause (HIS-028) paces only timeline mode's Auto. (r52)
- **HIS-022** The "Rewind pause", 0.1 s by default, adjustable from 0.02 s to 2 s, paces a take-back after a branch or a rewind (HIS-097, HIS-099); there is no rewinding in timeline mode. (r49)
- **HIS-023** Starting Auto takes its first step right away; later steps keep the pause. (r31)
- **HIS-024** Auto stops after an opponent's event that matches an enabled auto-pause trigger, after that event's animation. Your own actions never auto-pause. (r48)
- **HIS-025** Auto-pause triggers and defaults: Added to Stack on, Removed from Stack off, Reveal on, Your life hit off, Your cards hit on, Move passes to you on, Attackers declared off, Opponent plays a land off, Opponent puts a nonland card onto the battlefield from hand off. (r46)
- **HIS-093** Playback stops at every decision point of yours by your response mode (HIS-095), whatever the auto-pause switches say, and waits there with no time limit; holding Auto does nothing more (no branching from the timeline). (r48)
- **HIS-094** Catching up: coming back with the other player's events past your local head shows no dialog: your table as you left it, playback paused, the event strip and Play at the bottom (HIS-103), with no count or preview. The opening (mulligans) is never played back: the other player's opening declarations show at once, up to a decision of yours or the first turn, and the pause starts after them unless you already answered a decision there. (r52)
- **HIS-103** During playback of the other player's actions (HIS-095) a play/pause button stands where Auto is: "Pause" while playing, "Play" once paused and while catching up (HIS-094), "Pausing" until it stops, with "Hold: response mode" under it. A pause goes on to the next point where Full control would stop for you, even with no decision waiting, and stops there with the table live. Holding it opens "Response mode" with Full control, Normal and Auto-skip, each explained (TRN-059). (r52)
- **HIS-095** Playback shows the other player's events one at a time (HIS-021) and stops before an assumed answer about you wherever your response mode stops (Full control: every assumed pass; Normal: your stops and pauses; Auto-skip: those where you could respond; assumed blocks and keeps always), or where Full control would stop once you paused it (HIS-103). Elsewhere, showing an assumed pass confirms it. (r52)
- **HIS-105** At a playback stop (HIS-095) the table is live at that point and looks like any point where you may act (quick actions, step arrow or Resolve), with nothing saying it was assumed. Answering the same confirms it and playback goes on (an answer that starts the same and goes on, such as passing and then the discard it leads to, confirms it and the rest is your next action); anything else is a branch (HIS-097). (r52)
- **HIS-102** With everything before them shown (your live game), the other player's passes show at once on the live table, never as playback; what they lead to (a spell resolving) animates as usual. (r52)
- **HIS-096** Assumed answers: at the other player's priority points the device assumes their pass (Assume resolves), blocks (Assume blockers) or opening keep (Assume keep) so the game goes on while they are away, never at their own main phase. Their pauses are assumed at once while they are away; while they are online (green dot) they are waited for unless "Always assume resolves" is on, turning async 60 s after they were last online. Anything else of theirs is a real wait; TRN-071 applies. (r48)
- **HIS-097** Branch: when the other player's real answer differs from what was assumed, the game goes on from their answer and the line after it is discarded, never seen by them; your table goes back with the rewind animation and a note "<name> responded — your later actions were taken back" ("blocked differently", "took a mulligan"). A new line starts (HIS-042). There is no other way to branch. (r48)
- **HIS-090** "Your life hit" is an opponent's change to your life or your player counters; "Your cards hit" is an opponent's action on a card you own or control (also its combat copies and Stack entries) or one giving you a card. Each device's earlier "Your life / cards hit" choice carries over to "Your cards hit". (r36)
- **HIS-026** A hand-to-battlefield move never counts as a Reveal. (r13)
- **HIS-027** "Move passes to you" pauses only if the next event is not your own. Status changes never auto-pause; opening a graveyard/exile panel is not a reveal. (r17)
- **HIS-028** Playback settings live in Settings under "Timeline playback" and in a dialog from the timeline's gear: the auto-pause switches ("Stop Auto after an opponent's matching action"), "History playback pause", "Rewind pause" and Card move speed. There are no auto-rewind switches. (r48)
- **HIS-030** Playback and live opponent actions animate each change (cards fly, rotate, flip, fade; also between piles). Take-backs animate backwards: an undo plays quickly on both tables (for the other player only as far as they had seen), each undone step in reverse, cards flying back the way they came (a cast spell via the Stack to the hand) at the card move speed with no playback pause, while "Undid: <action>" fades at the top (HIS-076). (r52)
- **HIS-031** Changes show a delta tag and a number flash (about 1.5 s fade; changes in that window add up) for life, player counters, card P/T and counters, and a highlight for face and step changes: your own and the opponent's, live and in playback, also when a take-back goes back; never on jumps, on opening the game or in timeline mode. They come from the events shown (life, P/T, step and turn, face, a change of control, a library's shuffle). (r49)

### Opening and returning to a game

- **HIS-032** The server keeps, per game and account, your local head: the newest event shown to you (live or in playback) or added by you; another device opens the game at it. Everything shown to you (strip, timeline, chips, result panel, game list times, notification texts) comes from it and your next stop. (r50)
- **HIS-033** Opening a game with the other player's events past your local head starts with playback paused (HIS-094), with no count or preview; Play then plays them back, stopping at your decision points (HIS-095). Playback never skips ahead (the opening shows at once, HIS-094). When everything since is your own, the game opens live. (r52)
- **HIS-035** Leaving the app pauses at the shown point at once; coming back with the other player's events past your local head starts with playback paused (HIS-094) instead of rushing through them. A return where only chat or other non-event data changed, or only your own events arrived, stays live. (r52)

### Lines of play

- **HIS-042** Every line of play (the game's start, every branch, every rewind including free undo) starts by reshuffling the unknown parts of both libraries, shown nowhere; a card whose place someone knows (put on top or bottom by a known action, looked at or revealed where it lies, a kept-revealed top) keeps it. Random results (shuffles, coins, dice, random choices) follow from the game's secret and the line: the same input on a line gives the same result, a new line new ones. (r48)
- **HIS-043** There is no branch archiving and no "Make current" action anywhere; lines left by a branch are kept for problem reports and shown to nobody. (r48)

### Events and turns

- **HIS-050** The timeline groups events by turn (a faint line between turns, HIS-005); each event is one acting point (HIS-092), and playback and Previous/Next go one event at a time. (r49)
- **HIS-058** End turn (TRN-057) is your device passing your priority until the turn ends: your passes fold into the events that reach them (HIS-092) and never pause playback, stop or notify; the other player's passes assumed on the way are events of their own, and their playback stops at those their mode stops at (HIS-095). (r49)
- **HIS-092** Each history event is one acting point: a player's input (a cast or land play, a pass, an answer, a declaration) with everything that then happens by itself folded in (automatic answers, forced passes, their device's passes by response mode, resolutions, mana, untapping and drawing, steps passed, triggers placed, state-based actions), up to the next point where someone can act. An assumed answer (HIS-096) always begins its own event and never shares it with a real input. (r48)

### Event texts

- **HIS-061** Every event has a short text: hold an icon on the strip or timeline. Texts name who acted first ("You", the other player's name) and end with what it led to (HIS-091). (r49)
- **HIS-062** Texts never reveal what the viewer could not see at that event (hand, library, face-down, the other player's look); hidden cards read "a card" or "N cards". (r27)
- **HIS-091** Event texts name the actor: "You cast Storm Spark", "Bob played Mountain", "You activated Ember Herald's ability" (STK-046), "You attacked with 2", "Bob blocked with 1"; table-only events read "Alice staged 2 attackers", "Bob arranged 1 block", "You answered early", "You moved a card", "A result was suggested". An answer you assumed reads, for you only, as theirs with "(assumed)" ("Bob: no blocks (assumed)", "Bob kept (assumed)"); they read it as their own ("You kept"), so nothing tells them it was assumed. (r52)
- **HIS-101** Passes are implied: no "passed" line appears anywhere (strip, timeline, event chip, Undo dialog, notifications), assumed or not, though passes stay in the game's record. A pass shows only as what it led to: "Storm Spark resolved" ("Ember Herald's ability resolved"), "Turn 3 → Bob" or the new step ("Combat"); one that led to none of these shows nowhere, so a turn where nothing happened shows only its step and turn changes. (r52)

### Event icons

- **HIS-070** An event's icon is its input's: a cast = layers, a land = dashboard, an activation = a click, a pass = an arrow (a check when something resolved, skip-next for a new turn), attacks and blocks = swords, a keep = a check, a mulligan = shuffle, a concession = the outlined flag, results = the trophy, staging = the crosshair, an early answer = the note, a card moved on the battlefield = the move icon, the game's start = the flag, a new line after a rewind = the restore icon. (r49)
- **HIS-074** An event the app has no text or icon for reads "<name> acted" with the click icon instead of crashing (SYS-034). (r49)

### Event text chip

- **HIS-075** A short text of what just happened shows on the table's top row (up to 2 lines, ~420 dp wide) without blocking taps; Settings "Event text" turns it off (default on). While the other player makes a choice (Automatum's choice, their blockers, their mulligan) it shows that instead in the wait's words (TRN-066), even with Event text off and without fading, and flashes once in the warning colour when something you try is refused (TBL-118). Never on the chooser's own table; timeline mode shows only events. (r52)
- **HIS-076** In timeline mode the chip shows every event shown, yours too, also when scrubbing; live it shows the newest of the events just shown (also in playback) and stays quiet on open and return. An undo says what it took back, also with Event text off: "Undid: cast Storm Spark", and "Alice undid: cast Storm Spark" for the other player when they had seen it ("End turn" for an undone End turn). No panel repeats what the table already shows you did. (r52)
- **HIS-077** The chip appears and changes at once and fades out in 450 ms after the longer of 2 s and the playback pause, on its own clock even while paused; moving to another board drops it at once. (r31)

### Undo

- **HIS-083** The Undo button sits at the left end of the bottom bar: tap = Undo (HIS-084); hold = the Undo dialog to pick any earlier point (HIS-099), with a small haptic tick. There is no Redo. (r48)
- **HIS-084** Undo goes back to just before your latest own meaningful action, with your later assumptions and every pass after it (passes are never the target, HIS-101; an End turn is one action): at once when free (HIS-087), else the Undo dialog opens with that point chosen (HIS-099). You land where you acted and your device passes nothing for you there until you act; Undo again goes back past your action before. There is no limit on how many events go. (r52)
- **HIS-085** One acting point (a cast with its choices, a declaration with its payment) undoes as one. (r48)
- **HIS-086** Undo only deletes the events after the point: the game is exactly as it was there, and no timeline or strip event ever says the game went back (the new line's reshuffle, HIS-042, happens with the next event). (r51)
- **HIS-087** Undo is free only when everything after the point is yours (real inputs, or assumptions not yet confirmed) or the other player's table-only events that stay (SYS-058), and none of it showed you something new: a card you couldn't see (your draw, look, scry, search, a reveal), a random result, or a wait for the other player's choice from a source you can't see. Otherwise it needs their approval (HIS-099). (r49)
- **HIS-099** Holding Undo, or an Undo that isn't free, opens "Undo": the actions shown to you, newest first (no passes, HIS-101; your End turn that began with a pass is one entry, "You ended the turn"), to pick the point to go back to, with a small view of the board at that point below them; then Undo (free) or, when the other player must approve, an optional message and "Ask <name>". (r52)
- **HIS-100** The other player gets "<name> asks to undo" with the message, where the game goes back to as far as they have seen ("and actions you haven't seen yet are taken back" when past that), a small view of that board (their own newest one when the point is past what they've seen) and Deny / Accept, never how many events go. It is their move until they answer or it is cancelled (LOB-072). While it is open neither player acts (the wait says so; the requester has Cancel request there); looking, playback and conceding still work. (r52)
- **HIS-089** Undo never changes a line the other player is on without their approval. A refused undo says why in plain words; an undo with nothing to undo shakes the button. (r48)
