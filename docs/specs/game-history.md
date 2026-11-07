# Game history, stats and cleanup (owner, 2026-11-07)

Status: **approved by the owner (2026-11-07), not started.** Roadmap item "History of played games". Building it moves
the behaviour into the catalog (new entries for history, Stats and cleanup; changes LOB-015, LOB-018, LOB-026,
LOB-035, LOB-077, DRF-121) and deletes this file.

## Why

Finished matches disappear from view: after a result both players return to the same lobby (LOB-046) and nothing
shows what happened before. The owner wants a record of who played whom and how it ended, in the game, in the pod and
on a Stats screen with graphs. At the same time old games should be deleted, to free disk space on the Pi. Records are
small and stay; games and their event data go.

Terms: a **game** is the named lobby between two players (it survives results, LOB-046); a **match** is one played
game inside it, from start to result. One game holds any number of matches.

## 1. The history record

1. One record per finished match, written by the server when the result is final: the result OK after both saw it
   (TRN-065), a concession (TRN-064) or an accepted suggestion (TRN-047). Records are never deleted, also not when the
   game is deleted, by hand (LOB-026) or by cleanup (section 4).
2. A record holds:
   - the game's name and id, the pod's name and id for pod games;
   - start and end time, the number of turns (the engine's turn number at the end), who started (on the play / on the
     draw), mulligans per player, final life per player;
   - the result (each player's win, loss or draw) and how it ended:
     - **by the rules**, with why the loser lost: life 0 or less (CR 704.5a), drawing from an empty library (CR 704.5b),
       10 or more poison counters (CR 704.5c), or a card's effect (with the card's name; CR 104.3);
     - **conceded** (CR 104.3a);
     - **agreed** (a suggestion accepted), with its reason text (TRN-046);
     - **draw**: everyone lost at once (CR 104.4a) or a loop (TRN-060, CR 104.4b);
   - each player's deck: its name, its cover card (enough to draw the art, LOB-058), its colours (the colours of its
     Main cards), its source id (saved deck or pod deck) for linking and reveals. No card list.
3. **Unfinished**: a game deleted while a match is in play (by hand, LOB-026, or by cleanup) writes a record with no
   result, marked "Unfinished", with its turns so far. Unfinished records are listed but never count in W–L, win % or
   standings.
4. The engine doesn't record why a player lost today. Arcanum gets a small API addition for it (each lost player's
   reason, as above, sealed types); the owner reviews it with the other API changes.
5. History starts empty from the release that ships this: matches that ended before it are not added.

## 2. Who sees what

1. Your records: every match you played, with both decks, subject to rule 2.
2. **The opponent's deck** (name, cover, colours) shows in your record once that deck has been shown to you: at the
   match's end (LOB-043/044, DRF-125), or later, when the opponent shows the same deck (same source id) to you in any
   game. A later reveal fills in every earlier record of yours with that deck. Once shown it stays shown, even if they
   hide it again (Claude's choice of the two the owner allowed). Until then it reads "Deck" with the placeholder
   picture (LOB-042). The server leaves out deck fields the viewer may not see.
3. **Pod members** see every match of the pod (their own and the others'), without any deck: no names, covers or
   colours, also for their own matches in that view.
4. Your own deck in a record links to it ("Open deck") while the saved deck with that id still exists; otherwise the
   record shows only its name and cover.

## 3. Where it shows

1. **Game lobby (LOB-035):** two tabs, Lobby (today's page) and History. History lists this game's matches newest
   first; "No finished matches yet." when empty.
2. **Pod Games tab (DRF-121):** after the members and Challenge buttons, a **standings** table (each member's W–L–D in
   this pod's matches, best record first), then every match of the pod, played or being played: "<name> vs <name>",
   the result or the game's status ("In play", the turn), turns and how it ended. No deck information (section 2.3).
3. **Stats screen:** a new main-menu button "Stats" (LOB-015) and a side-menu entry (LOB-018).
   - Top: a row of opponent chips, "All" first, then each opponent by number of matches (avatar and name). The chosen
     chip filters everything below.
   - Two tabs, **History** and **Graphs**.
   - History: every record of yours, newest first. A row: date, opponent's avatar and name, a result chip (Win green,
     Loss red, Draw grey, Unfinished outlined), "T9" turns, how it ended, your deck's cover and name, the opponent's
     deck (or "Deck"), and the pod name line for pod matches. Tapping a row opens its details: everything in the record,
     including mulligans, final life, duration, play/draw, the reason text, and "Open deck" (section 2.4).
   - Graphs, with summary tiles on top: matches, W–L–D, win %, current and longest streak, average turns. Then:
     - record over time: running wins per player, a line each in their colour (one opponent chosen) or your running
       W–L (All);
     - win % on the play vs on the draw;
     - how matches end (by the rules / conceded / agreed / draw);
     - match length in turns (histogram);
     - your decks: cover, name, W–L, win %;
     - your decks vs theirs: a W–L grid (one opponent chosen; hidden decks pooled as "Deck");
     - win % by your deck's colours;
     - extras: win % after a mulligan, fastest win (turns), longest match (turns), matches per week.
   - Graphs are drawn in the app (no chart library). Empty data says so plainly instead of drawing empty graphs.

## 4. Automatic cleanup

The server deletes old games and pods regularly (every hour). Deleting removes everything of a game (lobby, chat,
matches and their event data) or pod (members, packs, picks, pod decks, notes), but never the history records.

1. **Finished games:** a game whose last match ended 7 or more days ago, with no activity since, is deleted.
2. **Inactive games:** any other game (invitation, lobby, match in play) with no activity for 14 or more days is
   deleted. A match in play leaves an Unfinished record (section 1.3).
3. **Stalled pods:** a pod with no activity in the pod or its games for 14 or more days is deleted. Games already
   started from it keep following rules 1–2 (DRF-042).
4. **Activity** is any change by a player: a move or other game event, a chat message, a lobby change (Ready, deck
   choice, preparation), an invitation or its answer; for a pod also picks, member and setting changes, deck
   submissions, challenges and game starts.
5. **Warning:** 3 days before a game in play or a pod is deleted (day 11 of 14), both players / every member get a
   notification "<game> · vs <opponent>: Removed in 3 days if nobody plays" ("<pod>: Removed in 3 days if nobody picks
   or plays"); any activity resets the clock. Finished games, lobbies and invitations go without a warning.
6. The first run after the release deletes whatever already qualifies on prod; the owner has agreed to that.

## Tests

- Server: a record is written for each way a match ends (rules loss of each kind, concession, agreed, draw), and an
  Unfinished one for a deleted match in play; deck fields are left out until shown and filled in after a later reveal;
  pod members get the pod's matches without decks; cleanup deletes exactly what rules 1–4 say and keeps records
  (with an injected clock, no waiting); warning notifications at day 11.
- Engine: each loss reason is recorded (cite the CR numbers above).
- App: unit tests for the stats figures; screenshot tests for the lobby History tab, the pod standings and match list,
  Stats History, a record's details and Graphs (one opponent and All).
- E2E (overall flow): Alice and Bob finish a match by concession; both see it in the lobby History tab and on Stats with
  the right result and turns; in a pod match, the pod Games tab shows it without decks and in the standings.
- Release note (add).
