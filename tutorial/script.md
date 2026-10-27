# ATW tutorial video — plan + draft script

Status: PRODUCED (2026-10-27). The narration below matches scenes.json, which is
what the video uses; every control was checked on the emulator during capture.

## Format

- 16:9, 1080p. Phone capture on the left (portrait), explainer text + annotations
  on the right. Landscape app shots go full-width with a caption band.
- Narration = TTS of the on-screen text (captions burned in too, so it works muted).
- One video, ~10 min, YouTube chapters in the description so viewers can jump.
- English narration, local Kokoro voice. No experimental combat auto-damage.
- Annotations: circle / rounded rect / arrow / finger-gesture glyph (tap, double
  tap, hold, swipe), fading in on the beat the narration mentions them.

## Pipeline (to build once captures are stable)

1. **Capture** — two emulators (player A + B) on the WSL test env with the catalog
   mirror (real art). Shots are scripted adb sequences (`input tap/swipe`,
   `screenrecord`, `screencap`) stored as files, so any shot can be re-shot after
   a UI change with one command. "Show taps" ON for gesture clips.
2. **Scenes as data** — `scenes.json`: per scene the clip/still, trim, narration
   lines, annotations (device-px coordinates + appear time).
3. **TTS** — Kokoro-82M locally on the RTX 3060 (free, good English). One wav per
   line; the measured line durations drive scene timing.
4. **Render** — Remotion (React → MP4, frame-exact, handles video + audio layers).
   ffmpeg for final mux/loudness.

## Chapters and narration

### 0. What this is (0:00, ~30 s)

Shots: title card; a mid-game board with art.

> Async Tabletop Wizards is a shared card-wizarding table for two.
> It's a simulated table, not a rules engine: you move the cards, and
> you and your opponent decide what the rules say.
> Everything you do is saved and synced. Your opponent sees it played back when they
> open the game, so you do not need to be present at the same time to play, and games
> can be played over a long time period.
> The base setup is cooperative: any player can change anything on the table, and every action can be traced and undone.

### 1. Getting started (~45 s)

Shots: register screen; first-launch catalog download dialog; avatar chooser;
main menu.

> Install the app and create an account. On first launch the app downloads and
> imports the card catalog.
> The main menu has your games, pods for drafting, decks, and settings.
> When there's a new version, the app asks you to update before you can play.
(Not captured: the update gate needs a newer published version; the line plays over the main menu.)

### 2. Decks and starting a game (~60 s)

Shots: Manage decks → import (paste list / Archidekt link); Choose opponent
(Contacts | Search); lobby with deck pick + sideboard + Ready.

> To add a deck, paste a decklist or an Archidekt link.
> To start a game, choose an opponent.
> Game starts after both players have picked a deck in the lobby, adjusted
> the sideboard, and pressed Ready.

### 3. Reading the table (~60 s)

Shots: portrait board, annotated zone by zone; then the same in landscape.

> Your side is at the bottom. Lands go in the row nearest you, everything else
> above them. Your hand, library, graveyard and exile are along your edge.
> The purple area is the Stack.
> Two different things are shown: whose TURN it is and whose MOVE it is. The
> turn player's battlefield is tinted. The top and bottom bars take the color of
> whoever has the move, and the pill at the top says "Your move" or "Waiting".
> At the bottom: the step button, quick actions for this step, and a strip
> showing the most recent events.

### 4. Basic controls (~90 s)

Shots: gesture clips with tap glyphs, one per beat.

> Drag a card to put it anywhere.
> Tap a card to open its menu. Swipe or use the arrows to change pages: everyday
> actions, counters, and more.
> Double tap a permanent to tap or untap it.
> In your hand, double tap plays the card: a land goes to your land row, permanents
> to the battlefield and other spells to the Stack.
> Hold any card to inspect it.
> Double tap your library to draw. Tap it for more actions.
> Drag on your life total or a counter to change it.
> Pinch to zoom, two-finger drag or double-tap-drag to pan.
> Drag to select multiple cards to act on them all at once.
> You can also use the multiselect mode to make multiselections.

### 5. Turns and passing the move (~45 s)

Shots: step button advancing; "Your move" pill passing; quick actions.

> The step button moves the game to the next step, hold it to end the turn.
> Quick actions cover the common things in each step, like "Untap, draw" at the
> start of your turn.
> Passing the move is how you tell your opponent it's their turn to act.
> Turn and move tracking is informative: it may affect shown quick actions,
> but all actions are always available via some menu.

### 6. Stack (~60 s)

Shots: double tap a spell in hand → Stack; tap → five bubbles (annotate each);
mark-text drag with live preview; opponent adds a response on top; resolve top
then next (bubbles reopen on the new top); double tap → Manage Stack; ability
from a battlefield card's menu → Stack → Remove; ping a target.

> Spells and abilities go on the Stack, the purple area. Double tap a spell in
> your hand or drag it there. For an ability, choose Stack from the card's menu.
> The Stack holds linked copies, so the real card stays where it is. A card cast
> from your hand is shown to both players while it's on the Stack.
> Tap the top of the Stack for its bubbles. Battlefield and Graveyard resolve it:
> the copy leaves the Stack and the real card moves there. Exile does the same
> to exile. Remove only takes the copy off the Stack, which is what you want when
> an ability resolves.
> Drag the side handle up or down to mark which part of the text you're using:
> a mode, an ability, or one half of a split card. Your opponent sees the mark.
> To respond, put your own card on top and pass the move. Resolve from the top
> down; after each one, the next card's bubbles open by themselves.
> Double tap the Stack to see every entry, remove one from the middle, or copy one.
> Tap "Ping" on any card to point at it, for example to show a target.

### 7. Combat (~60 s)

Shots: Main 1 → Select attackers (multiselect on) → pick creatures → declare;
combat area with "→ Opponent" lane label; attacker menu → Attack → → pick a
planeswalker → new lane "→ <planeswalker>" (annotate the thick lane divider);
drag an attacker across lanes; Declare blockers (move pill flips to defender);
defender drags a blocker under an attacker, two blockers on one attacker;
damage marks + life drag + loyalty corner number; Close + next.

> Combat happens in its own area in the middle of the table. Press Select
> attackers, pick your creatures, and declare them. Like on the Stack, these are
> linked copies; the real cards stay in place.
> Each attacker sits in a lane that shows what it's attacking. By default that's
> your opponent. To attack a planeswalker or a battle, tap the attacker, choose
> Attack, and pick the card. It moves into a new lane labeled with that target.
> You can also drag an attacker from one lane to another.
> When the game moves to blockers, the move passes to the defender. They drag
> their creatures onto the attackers they block.
> Damage is up to you: mark it on creatures, lower life totals, and lower a
> planeswalker's loyalty, the number in its corner. Then close the combat.

### 8. Library tools and tokens (~45 s)

Shots: search panel (left side), look at top N, reveal, token list + favorites.

> Search, Look and Reveal open a panel on the left side. Drag cards out, or tap
> them for quick moves. Closing a search shuffles your library.
> Reveals show to both players; looking and searching are private.
> Tokens come from the table actions menu, and you can favorite the ones you use
> often.

### 9. History, undo and branching (~2.5 min) — the key chapter

Shots: opening a game → catch-up playback; timeline mode (scrub, prev/next,
auto); Undo; then the branching example on two devices:
(A) catch-up shows B's turn: B casts Opt (scry: looks at top card, keeps it,
draws it), then attacks; (A) steps back to B's upkeep → Continue from here →
casts Shock on the creature; (B) "Opponent branched" → rollback → Shock plays →
B casts Opt again and sees a different top card (annotate old vs new card side
by side); game tree dialog showing both lines.

> Every action is recorded. When you open a game, the app plays back what your
> opponent did since you last looked, so you see what happened instead of only
> where things ended up. Playback pauses on important moments, like a spell
> going on the Stack or a reveal. You choose which moments in Settings.
> Tap the timeline button to enter timeline mode. Step back and forth through
> events, or let it run automatically. Hold a card at any point to read it.
> Made a mistake? Undo takes back your latest action. That only works while
> your opponent's later actions would still play out exactly the same. If undoing
> would change what they did, the app refuses, and you fix things by hand.
> Branching is for "wait, I wanted to respond to that."
> Example: you open the game and watch your opponent's turn. They cast Opt,
> looked at their top card, drew it, and then attacked with a creature. You
> wanted to Shock that creature at the start of their turn, before all of that.
> Step back in the timeline to their upkeep, choose Continue from here, and cast
> Shock. That starts a new branch, and it becomes the game.
> Nothing is lost. The old line still exists in the game tree.
> On your opponent's phone, the game shows "Opponent branched", rewinds to where
> you went back, and plays your Shock. Now they play their turn again.
> When they cast Opt this time, they probably see a different card. A new branch
> reshuffles every library card whose position nobody knows. What your opponent
> saw in the old line tells them nothing about the new one. Cards with a known
> position stay put, like a card someone put on top before the branch point.
> This only happens when a new branch starts. Undoing a draw and drawing again
> gives the same card, and branching again from the same point gives the same
> order, so going back can't be used to fish for a better draw.
> A tip that follows from this: when your spell would reveal cards from your
> opponent's hand or a face-down card, don't look yourself. Cast it and pass the
> move. Your opponent gets the chance to respond first. And if they go back to
> respond, you haven't already seen their cards.

### 10. Drafting (~2 min)

Shots: Pods → create (cube from Cube Cobra / set) → settings tiles → invite;
pick screen swipes; hold preview; Confirm; Librarian chip `[r18-librarian]`;
Build columns + basics + stats; Submit; Challenge → lobby.

> Pods are for drafting together. Create one from a cube, for example a Cube
> Cobra link, or from a set's boosters. Choose a format: classic boosters,
> Glimpse, or Grid. Then invite the other players. All packs open at once, so nobody waits
> for a pack to be passed.
> To pick, swipe a card right to keep it or left to burn it, then confirm. Hold
> a card to see it big.
> If the pod has Cogwork Librarians, tap the book to keep one extra card from
> that pack.
> When the draft is done, build your deck: cards are sorted into columns by mana
> value, basic lands are added from the counters at the top, and the Stats tab
> shows your curve and colors. Submit, then challenge another pod member. The
> game starts from the usual lobby.

### 11. Ending a game and wrap-up (~30 s)

Shots: Table actions → Suggest game end → outcome; the other player's
accept/decline element; back to lobby / rematch; chat.

> When the game is over, suggest the result from Table actions. Your opponent
> accepts or declines; until then, the game waits.

## Capture prerequisites (done 2026-10-27)

- r18-game follow-up: game-end suggestion element (chapter 10).
- r18-librarian: pick-screen Librarian chip (chapter 9).
- r18-update: forced update flow (chapter 1).
- r18-notify: notification content (optional shot, chapter 5).
- First prod release (new signing, DB wipe) does not affect capture.
- Capture decks: A needs Shock (+ lands, a creature, a split card for the
  mark-text shot); B needs Opt, a creature to attack with, a planeswalker for A to attack, and a library order
  where the reshuffled Opt visibly shows a different card (pick a seed/retake).
