# Async play: assumptions, waiting, branching and undo (I-D56)

Status: **approved (I-D56, 2026-11-03); built through the switch-over (steps A–D2, §18–§23, 2026-11-04): every game is
played this way.** The choices made while building (§16, §18–§23) wait for the owner's confirmation. Builds on
I-D52 (Automatum always on) and I-D54 (`engine-state-architecture.md`: engine state is the game state, the server is
authoritative, clients send inputs). Replaces I-D40, I-D41, I-D55 and free branching; §17 lists every entry it changes.
CR numbers are from the pinned Comprehensive Rules (`ComprehensiveRules.EFFECTIVE_DATE`).

## 1. Goal

Play two-player games well both live and asynchronously with as few **async rounds** per game as possible (a KPI the
server measures, §15), while keeping play mistake-free and the interface clear. Players don't decide for each other;
the acting player may **assume** a few of the opponent's answers where that is crucial for progressing asynchronously.
An assumption is never confirmed on the opponent's behalf: the opponent's real answer confirms it or branches the game.
We trust the (unmodified) client, but not the players: no one can see more than the rules allow, by playing ahead or by
undoing.

## 2. Terms

- **Decider:** the player who makes a decision by the rules, as the engine reports it (`Engine.decider`). A player
  controlling another player (CR 723.5) makes that player's decisions as their own real inputs; nothing is assumed.
- **Main line, head:** the server's current line of events and its newest event.
- **Local head** (per player, kept by the server per account): the newest main-line event shown to that player. It moves
  forward when one of their devices shows the next event (live or in playback) or when they add an event, and falls back
  when a rewind or a branch removes events past it. Everything shown to the player comes from it (§7).
- **Response mode** (per player per game, private, §7): where your own device stops for you.
  - **Full control:** at every point where you get priority; after casting a spell or activating an ability you keep
    priority.
  - **Normal:** at your **stops** (your own main phases with an empty Stack) and your **pauses** (STK-036: the other
    player's spell or ability on top of the Stack, their attack, blocks, their end step).
  - **Auto-skip:** Normal, but your device passes at a pause where you can't respond, and answers your questions
    that have only one answer (today's "Skip when I have no choice"). Default for new games. Your stops are never
    passed for you, in any mode, even with nothing to do (owner, smoke 2026-11-04: a turn is never passed away
    without its player; §6 (a)'s automatic passes leave them out too).
- **Live / async:** the opponent is live while TRN-042 shows their green dot (this game open, touched in the last
  2 minutes); otherwise async, with the grace of §4.
- **Assumed input:** an input of the opponent's that the acting player's device submits, flagged "assumed by
  <player>". Only §3's kinds.
- **Tentative:** a main-line event after an unconfirmed assumed input. An assumed input about Bob is **confirmed** when
  Bob's local head moves past it (§7, §8).
- **Engine-automatic answer:** an answer any device may make because it decides nothing for anyone (§6).
- **Line** (for libraries and randomness): starts at the game's start and at every branch and rewind (§9).

## 3. What can be assumed, and where

Kinds (more only by owner decision, each added to this list):
1. **A priority pass** ("Assume resolves"): one pass at one point.
2. **Blocks** ("Assume blockers"): the defender's declaration, set by the attacker on a screen that opens with "No
   blocks". Only blocks without a cost to block (CR 509.1d: the defending player determines and pays those); the engine
   checks restrictions and requirements as usual (CR 509.1b–c).
3. **An opening keep** ("Assume keep"): the opponent's keep while keeping needs nothing more from them (no card owed
   to the bottom), offered only to the starting player after their own keep.

Where: only where the opponent is the decider of a decision of these kinds, at any point **except their stops**:
passing in their own main phase with an empty Stack is skipping their turn, not letting something resolve. Priority in
their upkeep, draw step, combat and end step stays assumable.

A tentative line ends (the device waits for the opponent's real input) at the first of:
- the opponent's next stop;
- any other decision of theirs: "may"s, choices on resolution, their triggers' targets, modes and order, discards,
  divisions of combat damage that aren't automatic (§6), mulligans and bottom choices, simultaneous choices
  (CR 101.4), the legend rule and other state-based choices, the loop choice (TRN-060);
- an information-rule stop (§5);
- a pause where the device waits because the opponent is live (§4).

So a line never passes the opponent's next main phase: "End turn" reaches their first main phase at most.

## 4. When the acting device assumes

At the opponent's priority points that §3 allows and that aren't engine-automatic (§6):
- **Their pauses**, judged as if their Auto-skip were off, so it never depends on their hidden cards:
  - opponent async: assumed at once;
  - opponent live: the device **waits**, with no timeout. The waiting bar offers "Assume resolves" (this one pass). The
    personal setting **"Always assume resolves"** (off by default) assumes at once even when the opponent is live.
- **Every other point** where they get priority (the ones only Full control stops at, such as your upkeep, your
  beginning of combat, their own trigger on top): assumed at once, never a wait. If they play with Full control and act
  there, that is an ordinary branch (rare).
- **Blocks:** opponent async: declaring attackers opens the Assume blockers screen with "No blocks"; OK assumes it (one
  tap), the attacker may edit first, Back leaves it a wait. Opponent live: the waiting bar offers "Assume blockers".
- **Keep:** opponent async: assumed at once after the starting player's keep. Live: "Assume keep" on the waiting bar.
- **Grace:** a wait that began while the opponent was live stays a wait until you press an Assume button or they have
  not been live for 60 s; then the device goes on as for async. The waiting bar shows their presence (TRN-043 words).
- The Assume buttons show only at assumable points, never at a real wait or an information-rule stop.

## 5. Information rule

- While your line is tentative, an event is refused when its whole outcome (the input and everything the engine does up
  to the next decision) shows you, at any moment, a card identity that wasn't in `Engine.visible(parent, you)` —
  **except cards from your own library** (your library is protected by the reseed, §9). "At any moment" counts what the
  outcome only shows in passing (a look at a hand, a card revealed and put back, the cards of your next decision), not
  only what is still visible at its end; identities are compared by card, not by object (a card changing zones isn't
  new).
- What counts: the opponent's hand and library, face-down cards (also revealed as they leave the battlefield,
  CR 708.9), cards exiled face down (CR 406.3), random discards, anything else hidden from you. There is no game
  setting for libraries: the opponent's always counts, your own never does.
- It covers an assumed input's own outcome: an assumed pass in declare blockers whose combat damage kills a face-down
  creature is refused like an assumed pass that lets a "look at target opponent's hand" resolve.
- The device then doesn't send the event and waits for the opponent's real input at that point. Your own input that
  would reveal is refused until the line is confirmed (the card shakes; holding it says "Wait for Bob to catch up: this
  would show you hidden cards"). The waiting bar reads "Waiting for Bob · going on would show you hidden cards", never
  naming what. The server checks the same rule (refusal `revealsHidden`).
- Accepted tells, written down because they are rare or as in paper and MTG Arena: a wait caused by a hidden source
  (an opponent's "you may reveal this from your hand" trigger) shows that something hidden exists; whether going on
  would reveal can occasionally depend on hidden cards; live timing (an opponent whose Auto-skip passes at once,
  I-D18), and its async form: how far the opponent's playback has confirmed (the assumer's indicator, §14) shows where
  their Auto-skip stopped; chat about a tentative line (the players' own words). Undo can't turn a hidden-source wait
  into a free probe (§10).

## 6. Engine-automatic answers, and who may submit what

The server gate (`WhoActs`) and the device follow the same rules:
- **Your own real inputs:** where you are the decider, at your local head (§8). Conceding: at any time (§11). The passes
  your own device makes for you by your response mode are your real inputs too; like today's passes at points without
  a stop or pause (STK-026) they fold into the event that reached them.
- **Assumed inputs:** only §3's kinds, for the opponent as decider at points §3 allows, at the head, by the other player
  while their own local head is the head (so never by a player who is behind). The server checks the kind, the point
  and the information rule.
- **Engine-automatic answers** are made by whichever device runs the step and folded into its event (I-D35). They are
  not assumed, never compared and never branch:
  - (a) a pass of a player who could only pass, judged **without cards only they can see** (no cards in hand and no other
    hidden cards they could use: STK-026's "Skip off" judgement);
  - (b) a choice with one legal answer, judged the same way; and an order-free division of combat damage (CMB-033),
    answered with the suggestion for every player;
  - (c) the decider's own early answer (I-D33), applied when its decision opens;
  - (d) later: stored answers (`arcanum-automatum.md` §7.10).
- A device never uses cards only the other player can see to decide what is automatic, so event splits, waits and the
  assumption counter are the same whatever their hand holds. Their Auto-skip runs only on their own device (§7).
- **Table-only events** (placing your own cards on your battlefield): added at the head while it still applies there,
  else dropped; they never confirm or branch anything. Early answers are offered only while your local head is the head.
  Staging at a decision stop behind the head (your blocks, CMB-034) stays on your device and goes out only as the
  declaration itself (§12).
- Nothing else: no passing, choosing, dividing, declaring or ending the turn for the other player.

## 7. The opponent's side: local head, playback, confirmation

- **Response mode is private:** stored on the server for that player and game only, set from the quick control, the
  catch-up screen or Settings (the default for new games); never an event, never in the table state, never sent to the
  opponent. It may change at any time, also during playback; it applies to the points not yet played back.
- **Buffer:** the device keeps received events past the local head unshown (`GameSession`).
- **Playback** (live as events arrive, or catching up) shows the events in order and stops at each of your decision
  points **before** showing anything after it, with no time limit:
  - a real wait for you;
  - an assumed input about you at a point your mode stops at (Full control: every assumed pass; Normal: your stops and
    pauses; Auto-skip: those where you could respond, judged by your own device with your own cards); assumed blocks
    and an assumed keep always stop.
  Elsewhere your device confirms the assumed pass without asking and plays on. At a stop the table is live at that point
  and you answer as usual (§8). Auto-pause switches (HIS-024) remain viewing comfort; a decision stop always wins.
  (Not applied on engine state yet: playback and timeline Auto ignore them.)
- **Coming back** with opponent events past your local head: a catch-up screen first (what each mode means, the mode
  control, Play), with no count or preview of what is coming; then playback.
- **"Return to current" and Back** while behind play on quickly to your next decision point; never a jump past it.
- **The timeline** shows the main line up to your local head; looking back is free, nothing beyond it is reachable.
- **Everything shown to a player comes from their local head** and their **next stop** (the first point after it where
  their playback would stop, worked out by the server with their private mode): the event strip, timeline, chip, result
  panel, move pill and bar colours, game list order, move chip, status and last-activity time, notifications and
  reminders. "Your move" means you have a next stop; its time is that event's time; move notifications and "<opponent>
  is waiting for you" are timed from that event (LOB-073, LOB-075), not from the opponent's last touch. Nothing about
  the line beyond your next stop (its length, events, timing or result) reaches you.
- **Devices:** the local head is per account; a second device opens at it.

## 8. Confirmation and branching

The server takes Bob's real input made at his local head P:
- the next main-line event after P is an assumed input about Bob:
  - **the same input** (the engine input equal, compared canonically, e.g. blocks as a set; table parts ignored):
    confirmed: Bob's local head moves past it and the submission is dropped as a duplicate;
  - **any other input**, including a mana ability or a special action first (CR 605.3a, 116.2b): a **branch**: the new
    event's parent is P, the main line moves to it, the old line after P is discarded, and a new line starts (§9);
- P is the head: appended as usual;
- otherwise: `stale`, as today.

Invariant, checked by the server: a player adds real inputs only at their local head, so unconfirmed assumptions on the
main line are always about one player and there is never more than one tentative line.

Expected race (live play): at the opponent's priority points that are assumed at once (§4), the assumer's assumed pass
and the decider's own pass by their response mode may both go out. Whichever the server takes first stands: the later
real pass is a confirming duplicate; the later assumed pass is `stale`, and the assumer's device goes on from the new
head (its pending inputs dropped, I-D54 §3). Neither is shown as a refusal.

After a branch:
- **Bob** plays on from his own event; he never sees the discarded line or that it existed.
- **Alice (the assumer):** her table goes back to P with the rewind animation (Rewind pause, HIS-022), with the note
  "Bob responded to Storm Spark — your later actions were taken back" ("Bob blocked differently", "Bob took a
  mulligan"), and one Game activity notification per branch, collapsed per game. Her inputs still on the old line are
  refused as stale; an open cast is worked out again or dropped (STK-025).
- **Discarded lines** are kept in storage for problem reports and shown to nobody, the assumer included.
- There is no other way to branch: no branching from the timeline, no armed continuation, no late reaction.

## 9. Libraries and randomness

- **Known places** are part of the I-D54 state: per library, the cards whose place any player knows since that
  library's last shuffle (ZON-023: put on top, bottom or Nth from top by a known action, ordered groups, a top kept
  revealed, and a card looked at or revealed where it lies and left there); the rest is the unknown part. A reshuffle
  never moves a card a player legitimately knows the place of.
- **Every line starts by reshuffling the unknown parts of both libraries**, as the first step of its first event (shown
  nowhere). A line starts at the game's start, at every branch (§8) and at every rewind, free undo included (§10). A
  rewind adds no event of its own (owner, 2026-11-04): it moves the play's line counter on, and the next event made at
  its target (by either player, or the server's re-added table-only events, §18.10) starts the new line.
- **Random answers are deterministic per line:** the k-th random answer of a line (its reshuffle, shuffles, mulligans,
  coin flips, dice, random choices) comes from H(game secret, line number, k).
- **Where the seed lives:** the server makes the game secret when the play starts, stores it with the play and sends it
  to both devices with the game (I-D27: data a device holds isn't shown; the unmodified app never shows anything worked
  out from it before it happens). The line number and the line's random index are kept on each event, so nothing walks
  back; line numbers come from a counter on the play (0 at the start, each new line takes the next), never from the
  parent's line, so a branch or rewind never reuses the stream of a discarded or deleted line (whose draws the assumer
  may have seen). The server checks each random answer against them.
- So the same input on a line gives the same result, a new line gives new results, and nothing seen can be re-rolled:
  undoing past a random result or a seen card needs the opponent's approval (§10).

## 10. Undo and rewind

- **Undo (tap)** goes back to just before your latest own real input (your later assumed inputs go with it; your
  device's own automatic steps after it, its passes by your mode or End turn's, are not real inputs); when that
  undo isn't free it opens the rewind dialog with that target chosen. **Holding Undo** opens the rewind dialog to pick
  any earlier point of the main line.
- **Free** (done at once) when everything after the target is your own (real inputs, or assumed inputs not yet
  confirmed) or the opponent's table-only events the undo rule lets stand (I-D54 `UndoRule`), and none of it showed you
  something new (as §5 counts it, at any moment): a card identity you couldn't see before (your draw, look, scry,
  surveil, search, a reveal; your own library counts here), a random result (coin flip, die, random choice), or a wait
  for the opponent's decision whose source you can't see (§5's hidden-source tell).
- **Otherwise an undo request:** the rewind dialog (the timeline strip by event group as the target picker, with the
  board at the target; built so far as a list of event texts, newest first, `engine-state-architecture.md` §13), an optional message, "Ask Bob". Bob gets the request dialog with the message and the target
  board, shown only up to his local head (a target past it shows his local head's board and "and actions you haven't
  seen yet"), and Accept / Deny; it never says how many events go. While a request is open neither player sends game
  inputs: answers at a decision stop (confirming or branching) wait too. Looking and playback go on, and playback still
  moves the local head (confirming the passes it doesn't stop at); the requester may cancel; a concession is still
  taken and ends the request.
- **A rewind** (free or approved) only pops events off the tree (owner, 2026-11-04): the events after the target are
  deleted (HIS-086) and nothing is added for the rewind itself, so no timeline event ever says the game went back; the
  new line (§9) is kept as the line counter and starts with the next event. The undoer lands at the target and their
  device makes no automatic step of theirs there until they act (playtest C9PVLM: their mode's passes carried the game
  straight on). A player whose local head was past the target falls back to it with the fading note "Alice undid".
  (Branching is unchanged: the discarded line stays in the tree, seen only by the player who made it, §8.)
- **Redo is removed.** There is no event-count window: any earlier point (checkpoint plus at most 63 events, I-D54).
- Undo stays a convenience (SYS-006): no flow depends on a request being accepted. Recovering from an engine bug past
  the opponent's input needs their approval; while they are away the requester waits or cancels.

## 11. Results, concession and suggestions

- **A result the engine reports on a tentative line** is final only once every earlier assumption is confirmed. The
  assumer sees "You won · waiting for Bob to catch up" with OK greyed; OK is enabled once both local heads have
  reached the result, then either player may press it (TRN-065). The other player's result notification (LOB-111) goes
  out only after that.
- **Conceding while behind** is your real input at your local head, taken at any time (CR 104.3a): when the main line
  goes on past your local head, the game first branches there (the line beyond is discarded), so the result never
  depends on what you haven't seen. The assumer's own concession is at the head and final at once.
- **Suggestions** (TRN-046) are made at the head. The lock (TRN-049) applies to a player only once their local head
  includes the suggestion; an earlier real input of the other player that branches leaves it behind on the discarded
  line.

## 12. Rules points

- Only the exact assumed input matches (§8); anything the opponent does first is a branch.
- The attacker's Assume blockers screen is local and sent only as the assumed input itself, never as shared block
  staging; the defender's own staging (CMB-034) is theirs: shared as today when they block at the head, kept on their
  device when they block at a decision stop behind it (playing back assumed blocks), and sent only as their
  declaration.
- A defender's division of combat damage for a blocker blocking several attackers (CR 510.1d) is a real wait unless it
  is engine-automatic (§6 b, c).
- The opponent's triggers in your turn go on the Stack first (APNAP, CR 603.3b): their targets, modes, order and "may"
  are real waits; their priority with their own trigger on top is assumable (not a pause).
- Extra combat phases (CR 500.8) and extra turns follow the same rules point by point.

## 13. Event model, storage and order of work (with I-D54)

- `EngineStep.assumedBy: player?` marks an assumed input. An assumed input always begins its own event: assumed and real
  inputs never share an event; engine-automatic answers fold as before (I-D35, HIS-092).
- **Local heads:** a server table per play and player (event id), moved by the devices' reports (throttled) and by own
  submissions (a submission at P first moves its author's local head to P, so a report still in flight never refuses
  an answer); not part of any event. **Confirmation** is the local head past the assumed input, so events stay
  immutable and event digests and checkpoints are unchanged.
- **Discarded lines** stay in `engine_event` as branches off the main line; problem reports include them (I-D43).
  Rewinds delete events as Undo does.
- Each event keeps its line number and random index; the play keeps the game secret and the line counter (§9).
  Response modes, "Always assume resolves" and Autotap mana are per-player settings on the server, not table or engine
  state: the shared engine state keeps every player's `skipWhenNoChoice` off (so `Engine.shouldPassFor` gives §6 (a)'s
  public judgement), and a device judges its own player's Auto-skip on a local copy with it on.
- **Server:** `WhoActs` per §6; `Acceptance` per §8 (a non-head parent only for a mismatch at the submitter's local head
  or a concession); the information rule; the async-round measure (§15).
- **Device:** `GameSession` buffers events past the local head; on a branch the assumer's device reverts to the branch
  point, bounded because a line never passes the opponent's next main phase (else reload from a checkpoint).
- **Order:** land this, the known library places and the deterministic randomness **before the I-D54 wipe**, so the
  stored format changes once. Build it on the I-D54 model only; today's I-D41 machinery (`AutomatumPauseDriver`,
  `HistoryPlayback.holdPause`, `TableHistoryController.holdAt`, `PauseController`) goes with the old model. Phases, on
  the I-D54 branch (its switch-over list, items 1–6):
  1. *Stored format* (with I-D54 item 5): `assumedBy`; line number and random index per event; the play's secret and
     line counter; per-line random answers (`answerRandoms` seeded by H); known places in the engine state and the
     line-start reshuffle; the local-head table; discarded lines kept as branches; private settings out of
     `TableState`/the engine state (`PlaySettings.fullControl`, `Act.SkipWhenNoChoice`). The frozen sample is
     re-recorded once.
  2. *Server rules* (protocol + server, no UI): `WhoActs` (§6), `Acceptance` (§8: duplicate, branch, stale, the
     local head moved by submissions), the information rule as one function shared by device and server, free undo
     and rewinds (new line), undo requests and the input block, concession while behind, result OK and the suggestion
     lock per local head, each player's next stop and `Attention` per viewer (lobby rows, notifications, reminders),
     the async-round log. Two-device simulations with both oracles.
  3. *Device core* (`GameSession`): the buffer, playback stops by mode, local-head reports, the assumer's
     assume/wait/grace logic, revert on a branch.
  4. *App* (with I-D54 item 2, which replaces the I-D41 UI anyway): waiting bar, Assume blockers, quick controls,
     catch-up screen, rewind and request dialogs, notes, indicator, event texts; the tree dialog and redo go. Catalog
     entries change with the code.
  5. *Switch-over* (I-D54 item 6): the wipe, deleting the old model, then the adversarial review of two-device races.
- **Tests:** two-device simulations with seeded randomness, plus two oracles: *equivalence* (with no branch, the final
  main line equals synchronous play with the same answers, event for event; with branches it is a legal game of the real
  answers) and *leak* (in every rendered frame, the table, strip, chip, notifications and game list show nothing outside
  `Engine.visible` at the viewer's local head). Scenario tests for each review finding, citing the CR where a rule is
  involved.

## 14. UI (initial; MTG Arena as the reference)

- **Assumer's indicator** while assumptions are unconfirmed: "N assumed · M actions since"; only the assumer sees it.
- **Waiting bar** above the hand (STK-044), with the opponent's presence line and no timeout: "Bob may respond to Storm
  Spark · Assume resolves", "Bob may act · End step · Assume resolves", "Bob is declaring blockers · Assume blockers",
  "Bob is deciding on a mulligan · Assume keep", "Bob is choosing · <card>" (no button), "Waiting for Bob · going on
  would show you hidden cards" (no button).
- **Assume blockers screen:** like the defender's staging (CMB-034) but on the attacker's device only, starting with "No
  blocks"; OK assumes, Back waits.
- **Quick controls:** response mode (Full control / Normal / Auto-skip), "Always assume resolves", Autotap mana.
  Settings → Automatum holds their defaults.
- **Step arrow:** only your own actions; while the opponent must act it is hidden in either player's turn (the waiting
  bar holds the Assume buttons).
- **Events:** the assumer sees an assumed input in the opponent's colour with a dashed outline and "Bob passes
  (assumed)" / "Bob: no blocks (assumed)" until confirmed, then plain ("Bob passed"). The opponent sees their confirmed
  passes as their own.
- **Undo:** the rewind dialog and the incoming request dialog (§10). **Notes:** the branch note and notification (§8),
  the take-back note (§10). **Catch-up screen** (§7).
- **The game tree dialog goes:** with discarded lines shown to nobody there is only one line, which the timeline shows.

## 15. KPI

- The server counts, per game, **async rounds**: waits (the main line needs a player's real input) that begin while that
  player isn't live, each logged with its kind (opponent's stop, real choice, information rule, blocks, keep, pause).
  Playtests compare it with today's build per game and per turn.
- Not now (roadmap): **rebase** — when the opponent's real answer differs, re-apply the assumer's later inputs on the
  new branch where they are still legal and independent of what changed (first case: keep the assumer's turn 1 when the
  opponent mulligans and nothing touched the opponent's hand); **stored answers** (§7.10) as engine-automatic answers;
  the responder's own "pass for me after N s" in live play; the opponent's Auto-skip answers to one-answer questions
  applied by the server, if those waits turn out to matter.

## 16. Choices made in this revision (owner to confirm) and open points

Choices (plainest or most vision-aligned where the answers didn't say):
1. Random results count as "something new shown to you" for free undo (with every rewind reseeding, this is what stops
   re-rolls).
2. The game secret is held by both devices (I-D27), answering the I-D54 note on where the seed lives.
3. Order-free divisions are automatic for every player, without CMB-033's Skip condition (the mode is now private).
4. One-answer questions that depend on the opponent's hidden cards are real waits for the acting device (no inference
   from hidden cards); the opponent's own device answers them under Auto-skip.
5. Assume keep: only for the starting player; automatic when the opponent is async.
6. Live = the green presence dot; 60 s grace before a live wait turns async.
7. Discarded lines are viewable by nobody; the game tree dialog goes.
8. Response mode per player per game, default Auto-skip from Settings; "Always assume resolves" off by default.
9. A concession while behind discards the line beyond the conceder's local head.
10. While an undo request is open, answers at decision stops (confirming or branching) wait; playback, and the
    confirmations it makes without asking, go on.
11. Rewinds have no event-count limit.

Added by the second review (plainest readings; owner to confirm):
12. Autotap mana is private like the response mode (it shapes only your own casts), answering I-D54's open point 3
    the other way: not a shared table setting.
13. A tap on Undo that isn't free opens the rewind dialog with its target chosen.
14. A wait for the opponent's decision from a source you can't see makes undo past it a request (no free probing).
15. Accepted tell: the assumer's indicator shows how far the opponent's playback confirmed, so where their Auto-skip
    stopped (the async form of I-D18's live timing).
16. Your device's passes for you by your response mode are your real inputs, folded into the event that reached them.
17. Passing the move by hand (TRN-006) goes: the move is no longer state (I-D54 keeps none) but each viewer's next
    stop (§7); the move pill only shows it.

Open: the live definition (playtesting); whether async rounds drop compared with today (§15).

## 17. Entries to change

*Done with steps C–D2 (the catalog and the specs below were changed); kept as the record.*

Implementation updates these in the same commits as the code (AGENTS.md). "Remove" deletes the entry; "Change" rewrites
it as stated.

### Feature catalog

**history.md**
- Remove: HIS-014 (Rewind to the opponent's branch), HIS-036, HIS-037 (armed continuation), HIS-038, HIS-039
  ("Opponent branched"; replaced by the assumer's take-back, §8), HIS-040 (Old branch chip), HIS-041 (auto-rewind
  settings), HIS-044–HIS-048 (game tree dialog).
- Change: HIS-001, HIS-002, HIS-004 (the strip and timeline end at your local head); HIS-005 (no branch marker);
  HIS-006, HIS-033, HIS-034, HIS-035 (unseen = past your server-side local head; catch-up screen, then playback that
  stops at your decision points; no "no saved point" case); HIS-010 (no tree button); HIS-012 (only the main line, up to
  your local head); HIS-013 (no "Hold to branch" line); HIS-015, HIS-018, HIS-019 (playback turns live at your decision
  point; Return to current/Back play on to it, never jump; no branching action); HIS-020 (suggestions per local head,
  §11); HIS-022 (Rewind pause also paces take-backs and rewinds); HIS-024, HIS-093 (Auto stops at every decision point
  by your response mode, no time limit, no branching by holding Auto); HIS-028 (no auto-rewind switches); HIS-030 (no
  tree jump); HIS-032 (local head per account on the server); HIS-042 (reshuffle of unknown parts at every line start:
  branch, free undo, approved rewind; random answers deterministic per line; "draw, undo, draw gives the same card"
  goes); HIS-083 (hold = rewind dialog, not Redo); HIS-084 (§10's free undo or request, no 50-action limit); HIS-085
  (undoes as one; no redo); HIS-087 (the concern rule stays; seeing something new makes undo a request); HIS-089 (no
  Redo, no branches; new refusal reasons); HIS-091 ("alice passed for bob" goes; assumed-input texts, §14); HIS-092 (an
  assumed input is its own event; only engine-automatic answers and your device's own passes fold); HIS-058 (an End
  turn's assumed passes are their own events, and the other player's playback stops at those their mode stops at).

**zones.md**
- Change: ZON-024 (as HIS-042); ZON-026 (undoing your own look, scry or reveal needs the opponent's approval).

**turns-combat-stack.md**
- Remove: TRN-061 (acting-for confirmations), STK-031 (Choose for them), STK-038 (the acting device passes for the
  other player after the bar), STK-039 (Hold and late-reaction branch).
- Change: TRN-004, TRN-063 (the step arrow acts only for you; no "Pass" for the other player; hidden while they must
  act); TRN-006 (the pill shows the move from your local head and next stop; no passing the move by hand, §16 choice
  17); TRN-013, TRN-014 (the bars' colour and the move are worked out per viewer, not part of any event); TRN-015
  (also hidden in your own turn while the opponent must act); TRN-027, TRN-028, TRN-062 (the starting player may play
  turn 1 on an assumed keep); TRN-047, TRN-049, TRN-050 (suggestions and the lock per local head); TRN-057 (Next step
  and End turn pass only for you; the opponent's points are assumed, automatic or waited for); TRN-058 (each player's
  move and bar colours come from the point shown to them); TRN-059 (toggles: response mode, Always assume resolves,
  Autotap mana); TRN-060 (the loop choice only by the player holding priority); TRN-064 (concede while behind, §11);
  TRN-065 (OK once both local heads reached the result; Undo per §10); CMB-028, CMB-034 (staging only by the declaring
  player; the attacker's assumed blocks are a separate local screen); CMB-029 (no "Pass" for the other player of the
  turn); CMB-033 (no choosing for them; order-free divisions automatic for every player, answered by the device
  running the step); STK-026 (automatic passes for the other player only when forced on public information; otherwise
  assumed or waited for; your own device passes for you by your response mode); STK-027 (no Hold); STK-028 (the bar
  offers Assume resolves; no Pass for Bob); STK-029 (Resolve passes only your own priority); STK-030 (no "Choose for
  them" button); STK-034 (early answers only your own; applied when the division opens by the device running the
  step); STK-036 (stops and pauses become the Normal response mode, Full control and Auto-skip, run by the player's
  own device and playback); STK-037 (no bar, Response time or Hold: you decide with no time limit); STK-044 (no Hold
  button; the bars of the other player's response or choice become the waiting bar, §14).

**rules.md**
- Remove: SYS-062 (branch inside a flow).
- Change: SYS-005 (personal but kept on the server per account: the local head and the response mode; never shared
  with the other player); SYS-007, SYS-025 (Undo and undo requests; no Redo; a request's answer can't be local);
  SYS-012 (the main line changes only by a mismatch branch, a rewind, or a concession while behind; new stale
  message); SYS-026 (example: rewinds use checkpoints, no 50-event window); SYS-036, SYS-037, SYS-038 (gameplay: only
  your own inputs plus §3's assumed inputs; undo of others' inputs by request); SYS-057 (a player's Full control or
  Skip setting is no longer part of the board); SYS-058 (no redo stamps); SYS-063, SYS-071 (Auto-skip and Full control
  are the private response mode, run only by the player's own device; Full control never an event); SYS-069, SYS-073
  (no passing the move by hand; Undo, undo requests and rewinds; response mode no longer a game setting; early answers
  your own; assumed inputs); SYS-072 (no redo).

**lobby.md**
- Change: LOB-022, LOB-024, LOB-072, LOB-073, LOB-075, LOB-076, LOB-111, LOB-114 (computed from your local head and next
  stop, §7, §11); LOB-082 (no auto-rewind switches); LOB-113 (no Response time and "When the opponent can react"; the
  Full control switch becomes "Response mode" for new games (Full control / Normal / Auto-skip, default Auto-skip);
  "Always assume resolves" added; these and Autotap stored per account on the server).

**New entries:** response modes and their privacy; the local head and what derives from it; playback stops and
confirmation; the catch-up screen; assumed passes (pauses, other points, grace, Always assume resolves); Assume
blockers; Assume keep; the waiting bar; the information-rule stop; engine-automatic answers; the assumer's indicator;
branch note and notification; free undo and its limits; undo requests (send, receive, cancel, the input block); rewinds
and reshuffles; results and concession on tentative lines; the async-round measure.

### `arcanum-atw-integration.md`

- Add the I-D56 row (this spec). Superseded: I-D40 (acting for the other player asks first), I-D41 (pause points and
  response windows), I-D55 (by I-D56).
- Change: I-D3, I-D18 (automatic passing becomes Auto-skip on the player's own device; other devices judge only on
  public information); I-D11 ("either may answer" goes); I-D17 (its rationale of answering for an offline player goes);
  I-D20 (the undo window is replaced by §10); I-D22 (an assumed pass is shown as its own event); I-D23 (moot: branches
  start only at a decision point); I-D26 (redo goes); I-D29 ("either player may accept for the other" goes); I-D33
  (early answers applied as engine-automatic answers, §6 c); I-D34 (the move per viewer); I-D35 (an assumed input begins
  its own event); I-D46 (c) (the toggles); I-D49 (assume keep); I-D52 (the recovery path is undo and rewind requests,
  not branching); I-D53 (OK after both local heads; Undo per §10).
- Build notes to mark historical: §2 step 5 ("Choose for him"); "Built (I-D40, I-D42)"; "Built (I-D41, pause points)";
  the pause-point review fixes A (late-reaction branch) and C (hold signal); "UI review round 2" (1) (the arrow's
  Resolve for the other player).

### `engine-state-architecture.md` (I-D54)

- §2: "undo, redo, branching and timeline playback work exactly as now" (→ §8, §10); per-player Full control, Skip and
  Autotap in the shared table state (→ private settings, §13).
- §3: "Branching from an older point stays an explicit action" (→ branches only at a mismatch, §8); "the server uses
  [random answers] as given" (→ checked against the line's stream, §9); a refused input's exceptions (a confirming
  duplicate, a mismatch branch, §8).
- §6: the kept "timeline/history/branching UX" and "pause points (I-D41)" (→ this spec).
- §8: attack staging and block draft stay shared, except the attacker's assumed blocks (§12).
- §9 phase 2: `TableState.PlaySettings` Full control; `EngineStep` with a null input for "Skip when I have no choice".
- §9 phase 3–4 notes (on the I-D54 branch): `WhoActs.Anyone` (→ §6); `Acceptance`'s "deliberate branch" (→ §8);
  `GameSession.received` applying another player's event at once (→ the buffer, §7); `TableChange.Settings` sharing Full
  control; `Act.SkipWhenNoChoice` (→ private, §13); `Attention.of(state)` at the head (→ per viewer from the local
  head and next stop, §7); the engine state's `skipWhenNoChoice` (kept off, §13); switch-over item 2's "pause points",
  "settings as table changes" and "Autotap becomes a shared table setting" (→ §7, §13); switch-over item 5 is now
  specified by §9 and §13 (the seed's place: §9; undo no longer replays the same stream: every line is new).

### `arcanum-automatum.md`

- Change: D4, D11, P7, §7.9 (deciding for another player goes; assumptions only, I-D56), the §8 "Pending decisions" row
  ("or on their behalf" goes), §9 (live play is decided here), §11 "Player access" (decisions only by their player,
  plus §3's assumptions) and "History, undo, branches" (→ §8, §10).
- Keep: §7.10 stored answers, now the next KPI step (§15).

### Other documents

- `docs/pre-release-review.md` (owner's list): the "Undo/redo" item names redo and the "Keep top card revealed" item
  names branch reseeds; the owner updates them (every line reseeds, §9; no redo, §10).

## 18. Built (step A, 2026-11-03)

Step A of §13 (phases 1–2: stored format and server rules) is built behind the I-D54 seam, with I-D54's remaining
server items; see `engine-state-architecture.md` §10 for what and where. Nothing a player sees changed yet: every game
still starts on today's model, so no catalog entry of §17 changes in this step (they change with steps C and D).

Decided while building (the most vision-aligned reading; owner to confirm):
1. **Seed:** the game's secret is part of the shared state both devices hold (`TableState.secret`, owner's decision);
   line numbers come from one counter on the play (`engine_play.lines`). A device starting a branch names the next
   number (`Act.NewLine`); the server refuses any other as `stale`, so it never reuses a stream.
2. **Where a line starts:** a branch's first step is the reshuffle, inside the branching event. A rewind (free or
   approved) moves the line counter on and the next event at its target starts the line (superseded 2026-11-04: it
   used to add the server's own line-start event, "The game went back").
3. **Known places** live in the engine (`PlayerState.libraryPlaces`). Looks make places known through `Change.Shown`
   (a scry, a look, a search, a reveal), cards put at a place through the zone change, random-order puts become one
   group; a reorder by itself marks nothing (it always follows a look). Cards a player may look at any time are fixed
   when a line reshuffles.
4. **Engine-automatic (b)** is only the order-free combat damage division answered with the suggestion: the engine
   already makes one-answer choices that depend on nothing hidden itself (P5). **(c)** early answers match by
   decision kind, player and source (`EarlyAnswer.keyOf`).
5. **Assume keep:** the starting player is the setup's active player; allowed after their own Kept event while the
   opponent has no Mulliganed event (no card owed to the bottom).
6. **Random results for free undo:** coin flips, die rolls and random choices count; a shuffle's order doesn't (it is
   hidden, and the rewind's new line reshuffles anyway).
7. **Confirmation** compares only the decider's first input with the assumed one; the rest of a confirming
   submission is dropped (the device plays the line back).
8. **Local heads** start at the game's start event for both players; reports only move a head forward along the main
   line (the unmodified client is trusted to stop at its decision points; the server doesn't enforce them).
9. **Discarded lines** stay in `engine_event`; a rewind deletes the main line's events after its target (and their
   checkpoints); a discarded line hanging below a deleted event stays as it is, for problem reports.
10. **The other player's table-only events** a rewind lets stand are added again as new events (new ids) after the
    line-start event while they still apply, else dropped.
11. **Notifications:** a player is notified ("your move") only when their next stop is new, timed from that event;
    whose move a game is reads the viewer's own next stop first, then the other player's.
12. **Async rounds** are logged with kinds keep, choice, stop, blocks, pause, priority; live = the green dot
    (presence ONLINE). An information-rule stop shows on the server as an ordinary wait at the head.
13. **Results:** a concession ends the play at once (while behind, after its branch); any other engine result ends it
    once a player confirms it after both local heads reached it.
14. **Settings changes** make a revision that carries no settings (the other player sees only that the revision
    number moved).
15. **Cards the server can't read** start as unknown objects; the readiness check (I-D52) already keeps such decks
    out of games.

## 19. Built (step B, 2026-11-04)

Step B of §13 (phase 3, the device core) is built behind the seam, in `protocol` `game` (not wired into the app yet;
nothing a player sees changed, so no catalog entry of §17 changes in this step): `GameSession` (the data: the main line
from a recent point to the head, the shown state at the local head, the buffer past it, pending events, drafts, branches
and rewinds), `GameDevice` (the policy: playback stops by mode, local-head reports, the device's own passes and folds,
assume/wait/grace, the information-rule stop, confirmations, undo and requests, table-only events, suggestions, results;
it speaks only `GameAction`s and engine revisions) and `GameRepro` (I-D43 on the new model). See
`engine-state-architecture.md` §11.

Decided while building (the most vision-aligned reading; owner to confirm):
1. **Loading:** the snapshot also carries the state at the earliest local head and the main line's events after it
   (`EngineSnapshot.baseId/base/events`), so a device opens at its own local head with the rest buffered and can go back
   to the other player's (where a branch starts) without loading again.
2. **Confirming at a stop:** the device sends its real answer as a submission at its local head (the server confirms it
   as a duplicate, §8) and plays on at once; a later local-head report confirms it anyway if that submission is lost.
3. **Table-only events while behind** are made at the head and shown when playback reaches them; they don't move the
   author's local head on the server (a server rule added: they confirm nothing). After a `stale` refusal they are made
   again at the new head with the values there; a place for a card no longer on the battlefield, or a suggestion or result
   confirmation the head has changed, is dropped.
4. **The information rule on the device** is judged before anything is sent: an assumption is probed once per point
   (its whole outcome with the automatic answers folded after it; the probe is taken back, never shown), and the
   player's own events while their line is tentative are checked before sending.
5. **Pauses are judged as Normal's** (`Stops.pause`), never with the other player's own cards; a point is a live wait
   only when it is first met while they are live (green presence), and stays one while they are live or until 60 s after
   their last live moment; "Always assume resolves" covers passes only, not blocks or keeps.
6. **Blocks are never assumed by the device alone:** async opens the Assume blockers screen with "No blocks" (one tap
   assumes); Back leaves a wait with "Assume blockers" on the bar.
7. **Simultaneous mulligans:** the non-starting player's own declaration is their move while the starter's is open.
8. **End turn** is the device's intent for the rest of the turn: it passes the player's priority (declaring no
   attackers); the other player's points are assumed, automatic or waited for as usual (HIS-058's separate events).
9. **Auto-skip's one-answer questions** are answered by the player's own device as their own inputs, folded into the
   event that reached them; Autotap stays in the cast flow (step C).
10. **Playback is paced by the UI** (one event per step); local-head reports go out at once at a stop or the head and
    at most every 2 s while playback runs.
11. **What a device keeps:** 64 events before the earlier local head; an undo target or request preview further back is
    treated as needing a request (the server checks again).
12. **Undo's target** is just before the player's latest own event that isn't an assumption (a real input, or a
    table-only event of theirs). The request preview shows the board at the target when the viewer has seen it, else
    their local head's board with "and actions you haven't seen yet".
13. **Reloading:** a device that can't follow exactly (a revert more than 4 turns back, a branch or rewind while its own
    branch is pending, a refused branch) asks to load the game again.
14. **The reproduction bundle** (`GameRepro`, header `atw-engine-bundle/1 gzip+base64`): the state at the line's start,
    the line, the pending events, local heads, settings and the last 400 log entries; `check()` makes every event again
    through the server's gate and compares.

## 20. Built (step C, 2026-11-04)

Step C of §13 (phase 4, the app) is built behind a debug switch (Settings → Automatum → "Engine games (test)", debug
builds only; off by default): with it on, a game whose play the server runs on engine state is played on the device
core; every other game still plays on today's model. See `engine-state-architecture.md` §12. The catalog entries of
§17 are changed (they describe games on engine state; `docs/features/README.md` says so), and the features §16/§17
remove are gone from both paths where they were UI only: Redo, the game tree dialog, branching from the timeline and
the auto-rewind switches. Today's model keeps its I-D41 pauses, passing for an away player and Hold until step D.

Decided while building (the most vision-aligned reading; owner to confirm):
1. **Rendering:** the table keeps its whole UI by drawing a board made from the engine's state
   (`EngineProjection`: the play's own containers and card ids, hidden cards covered by `Engine.visible`, places from the
   table state or the first free cell of the card's row); the board carries the engine state, so every existing engine
   reader (computed P/T, card menus, the cast panel, choices, combat) works on the engine's own state, never rebuilding
   one. A spell's card stays in the zone it was cast from (STK-001), found in the engine's log.
2. **Sending:** the cast panel and the choice panels work as before and send only the player's own inputs of the flow
   (the cast, its answers, the payment); the device answers the randomness and folds the passes that follow.
3. **The step arrow** passes only the viewer's own priority, declares what the lanes hold, or (held) End turn; the phase
   dialog is view-only. It shows only while the viewer may act (their move or a decision stop).
4. **Combat staging** is the declaring player's own on engine state (kept on their device, sent as the declaration),
   not the shared staging of I-D54 §8: plainest with I-D56 §12 and needs no table change; showing it to the other player
   can come later (a list item for step D). *(Superseded by §22.1: shared staging.)*
5. **The waiting bar** is one line above the hand with the presence line and the Assume button (Assume resolves, Assume
   blockers, Assume keep); it also says when an undo request or a suggestion holds the game.
6. **Assume blockers screen:** "No blocks" only (OK assumes it, Back waits); editing assumed blocks on it is not built
   (step D list). *(Built in D1, §22.10.)*
7. **The assumer's indicator** is a small violet chip at the left edge of the table, "N assumed · M actions since".
8. **Playback** steps one event at a time with the History playback pause; Back and Return to current play on to the
   next decision point.
9. **Undo dialog:** the main line up to the local head as a list of short event texts, newest first, rather than the
   timeline strip; it says when the other player must approve, with an optional message. The incoming request shows the
   message and where the game goes back to (turn and step, "and actions you haven't seen yet"), not a board preview.
   *(Board previews added in D1, §22.11.)*
10. **Opening:** the engine's simultaneous mulligans, with today's Mulligan/Keep controls and Keep / Bottom screen; the
    server's start gives the first mulligan free as the game's rules say (TRN-062).
11. **Defaults for new games** (response mode, Always assume resolves, Autotap) are kept on the device per account and
    given to a game once by the device that opens it first (step D1 moved them to the server: §22 choice 12).
12. **Notes** (branch, take-back) show once as a dark chip at the top for 6 s.

## 21. Fixed after the adversarial review of steps A and B (2026-11-04)

The review (`~/projects/atw-cache/ab-review-2026-11-04.md`, 14 findings; reproductions in `AbReviewTest` and
`EngineAbReviewTest`) is fixed in `protocol` `game`, the server and the engine table; catalog SYS-012, SYS-057, TRN-064,
TRN-066, TRN-067, HIS-095 and HIS-096 follow. Decided while fixing (plainest reading; owner to confirm):
1. **Table-only events commute** (#1, #2): an input made on an event followed only by other table-only events that
   touch none of the table parts its own table changes touch (`Acceptance.commutes`) is made on the head instead (the
   same engine state); the device slips such an event in under its pending events and open draft by the same rule.
   Anything else still makes it stale. A player's own refused action is told ("That wasn't taken: the game moved on
   first."); the device's own passes and assumptions lost in the expected races stay quiet (§8).
2. **Drafts** (#2) survive events appended past them (buffered while behind, commuting at the head); a branch, a
   rewind or a non-commuting event at the head drops them, told once (`DRAFT_DROPPED`). A draft at a decision stop is
   made on the main line as it is there (no new line yet): sent, it confirms when its first input is the assumed one,
   else it is made again as a branch with its new line first.
3. **Confirmation** (#3) is one rule, `Acceptance.confirms`: only the first input is compared; what else the answer holds
   is the player's next action after the confirmed event (at the head, or compared with the next assumption about them,
   whatever their mode).
4. **Concession** (#4): the server starts the line itself for a concession made without one at a local head the main
   line has gone past (CR 104.3a).
5. **Random results** (#5, #9): a random result in an event that starts a line makes undo past it a request; an outcome
   with a random result counts for the device's information rule like a hidden card (an assumption isn't made, an own
   input while tentative waits), so the waiting bar never depends on a result before it happens. The server's check
   stays on cards (a modified client gains nothing it couldn't by waiting: undo past the result is a request).
6. **Server per viewer** (#6, #7): a real wait going on across table-only events keeps its stop and time (no new
   notification); move notifications carry the stop's time; the last-activity time a viewer sees is their next stop's,
   else their local head's.
7. **Work per request** (#8, #10): the main line between two events is read in two statements (ids, then the events not
   decoded yet); each play's definitions and up to 256 decoded events are kept across requests; a refusal keeps the
   play warm. The next stops are still worked out per request from each local head (in memory; table-only events cost
   little), bounded by the engine events since.
8. **Server checks** (#11): an assumed input only from its author's own local head; a result confirmed only by a player
   who has seen it; a place only for a permanent of one's own.
9. **Waiting bar** (#12): `Wait.Choosing.source` is null unless the viewer can see the card.
10. **Reproduction bundle** (#14): it holds the full-information state and the game secret; fine per I-D27 and I-D43
    (developer-only problem reports, never shown or copied to a player). No change.
11. **Simulations** (#13): the leak oracle is the simulation's own (`Engine.visible` per step and what is shown or
    revealed in passing), an equivalence oracle replays the main line's inputs on the bare engine, and the seeded games
    make drafts (with deliveries in the middle), table-only events and concessions at any moment.

## 22. Built (step D1, 2026-11-04)

Feature parity of the engine path, still behind the debug switch (see `engine-state-architecture.md` §13 for what and
where). The catalog entries describe it (HIS-031, HIS-070, HIS-091, HIS-099, CMB-028, CMB-034, CMB-036–CMB-038, CMB-040,
STK-034, LOB-113, new LOB-116; the README's note on the two paths).

Decided while building (the most vision-aligned reading, MTG Arena as the reference; owner to confirm):
1. **Shared staging** (I-D54 §8 over step C's choice §20.4): every staging change is a table-only event of its own
   that the other player's table shows; playback shows such events (and card places, early answers) quickly (0.12 s)
   instead of at the playback pause. The declaration clears it in its own event. Behind the head (a decision stop) the
   staging stays on the device (§12). A draft left over (End turn passing the step) shows nowhere outside its step.
2. **Attack choices** (enlist, optional costs, bands) travel inside the attack draft as the engine's own `Attack`s, so
   the defender sees them as the attacker makes them.
3. **Early answers** are table changes keyed as the engine will open the division (`Divide:<player>:<creature>`), valid
   for the turn they are given in; the key doesn't tell the two combat damage steps apart.
4. **Event texts** name the actor ("You", the other player's name) as HIS-091 does; a pass adds what it led to (what
   resolved, a new turn, a new step). Texts are made once per event with the state when first shown.
5. **Strip groups** are turns (the faint line between them): each event is one acting point already (HIS-092).
6. **Timeline mode** shows any event the device holds (from the trimmed line's start up to the local head and the
   player's own pending events), one event per step; the async panels hide while it shows (an incoming request still
   opens); no flashes there, card motion only. Playback while behind counts as timeline mode for the grey bars and
   "Return to current".
7. **Flashes** come from comparing the boards before and after the shown events (life, P/T from the engine's view, step
   and turn, face, control) plus the events' shuffles; none on opening the game.
8. **The event chip** shows live only for the other player's newest event among those just shown (also in playback,
   where they are all theirs); in timeline mode for every event shown.
9. **Token pictures** use the engine's log for the token's maker (this and last turn); a device keeps a token's picture
   once found, so a device opening the game later may pick one by the token alone.
10. **Assume blockers** (CMB-040): under each attacker the defender's untapped creatures as small cards, tap to toggle a
    block, hold to inspect; the waiting bar's Assume blockers (defender online) opens the same screen instead of assuming
    "No blocks" at once.
11. **Undo dialogs' board** is the whole table laid out at a phone's size and drawn small (300 dp high): a small table
    would lay itself out differently. The request dialog shows the viewer's own newest board when the point is past
    what they have seen.
12. **New-game defaults** are part of the account settings (`playDefaults`); Settings' "Autotap mana sources" sets the
    engine games' default too. The server gives a new game's seats their defaults; the device no longer gives them.
13. **Branch notification** (LOB-116): "<opponent> responded differently · your later actions were taken back", one per
    branch, collapsed per game as every game notification; a branch that ended the game (a concession while behind)
    tells only the result.
14. **Problem reports** carry the open engine game's bundle instead of today's Automatum bundle, cut to fit by halving
    the log, then dropping the oldest events (and the base). A device that can no longer reach its line's start exactly
    starts the bundle at the earliest event it can reach.
15. **No release notes**: nothing of it reaches a release build before the switch-over (the debug switch).

## 23. Built (step D2, 2026-11-04): the switch-over

Every game is played on engine state with async play; games started before were removed (V59). See
`engine-state-architecture.md` §14 for what and where, and what is left to delete.

Decided while building (the most vision-aligned reading; owner to confirm):
1. **One commit per step:** the switch and the wipe together, then the server's old path and the old Automatum
   settings, then the engine thread; the app's and protocol's old model stay where the engine path still uses them
   (§14's list).
2. **Old play actions** (`Play`, `PlayBatch`, `Undo`, `Redo`, `AcceptResult`, `Concede`) are refused as `inapplicable`
   rather than removed from the protocol, so an old app gets a plain refusal (one compatible set is maintained anyway).
3. **The game-over notice (LOB-111)** reads how an engine play ended from the ended play's row, in the form an accepted
   suggestion had: a concession is the conceder's own loss ("<opponent> conceded"), an agreed result is the suggestion
   author's, the rules' result is told from each player's side. It was missing on engine state (the notice said
   nothing of the end).
4. **Settings → Automatum** keeps only the defaults of new games (response mode, Always assume resolves, Autotap mana
   sources), all kept on the server; the I-D41 Response time, When the opponent can react and Full control are gone.
5. **Engine thread:** one thread for all games; the player's own action waits for its local result there (so it never
   runs on a stale state), everything after it doesn't hold the main thread. Until an engine game first loaded, the
   table shows its loading state.
6. **Request costs:** an engine head reads its snapshot; opening a game costs 43 database round trips instead of 31,
   a head refresh 14 instead of 10 (`RequestCostTest`).

Found while building (fixed in §25): LOB-114 ("<opponent> is ready" while you are still Mulliganing) was not sent on
engine state.

## 24. Notifications (owner, 2026-11-04 — I-D57)

- **Two kinds of waiting.** (a) The opponent did something you *could* react to but is still playing on assumptions — **no notification**; you can still open the game, look and react (a branch). (b) **You need to act**: the game can't go on without you — your turn/main phase, a decision only you can make (non-assumable), an information-rule stop, the acting player's real wait (they chose not to assume), or the acting player's line reached the end of what they can do without you.
- **Who is waiting for whom** is computed from the game state and assumptions, however that state came about (normal play, branch, undo/rewind). Only a player someone is actually waiting on gets a "Your move" notification, with the existing timing (10 s after it appeared when you're offline/idle, 60 s otherwise; never while the game is open).
- **The acting player who assumed and left** (app to background with nothing waiting on the opponent) is the one still expected to act: they get the reminder notifications (15 min, 30 min, 1 h, …), the opponent gets nothing.
- **Content:** game notifications show the game name, the opponent's name, the step/turn **you would resume at** (your own local head — never the true head, so assumptions don't leak), and your deck name. Draft/pod notifications include the set or cube name.
- **No duplicates:** if you had a challenge queued for a pod, the "pod ready / decks ready" notification is folded into the challenge's notification (one notification per thing that needs you).

## 25. Fixed after the switch-over review (2026-11-04)

Findings #2–#9 and #12 of the review (`SwitchReviewTest` in server, protocol and app; #1, #10, #11 separately):
- **Next stops** (`NextStop.of`): a result reached on the main line past a player's local head (rules' or agreed) is
  their stop at the result's event, so it is their move and they are notified (TRN-065); in the opening a player's own
  mulligan declared alongside the other player's is their move (TRN-027).
- **Revisions without an engine part** (chat, the start) carry the per-player moves and activity from `engine_seat` as
  engine revisions do; the chat and start notices read the recipient's own move.
- **Moves that end without the other player being told** (a confirmation, playback past a stop, an undo request):
  `Outcome.endedStops`; the player's notice row says it isn't their move, and their reminder stretch is answered
  (`ActivityService.moveEnded`: the other player's reminder is re-armed when they have the move, else both close).
- **LOB-114** from the keep in the event (`Outcome.ready`); **agreed results** recorded from the main line's last engine
  event (`AgreedResult`), not from the confirming request.
- **Unreadable cards:** the start is refused (`automatumUnsupported`) when the server's own parse leaves any card
  without a definition, whatever support was stored.
- **Stale taps** (app): every own action checks the board shown against the engine thread's last published one and is
  refused with SYS-012's note when it is older (SYS-011); conceding is always taken. Owner, 2026-11-04: a pass, a cast or
  any other engine input, and an opening declaration, is made on the newer board instead when the game there is the same
  apart from its record of what happened (`GameDevice.sameDecision`): the same decision still waits for the player.
  Likewise a move of the player's own the server refused as stale because the other player's event came first
  (`GameDevice.lostMoves`/`remake`, either reply order): once that event is shown and the device's own steps are done,
  each lost move is made again when `sameDecision` holds against the state it was made on, else SYS-012's note, once.

Decided for the owner (to confirm): **an undo request is the other player's move** (#12): while open, the requester has
no next stop and the other player has one (at least at the head, timed from the request): listed as their move and
notified "Your move" like any move (LOB-072, LOB-075's reminder included); answering or cancelling ends it.

## 26. Built (I-D57, 2026-11-04): notifications by who is waiting for whom

`NextStop.waitsFor`: the game waits for a player with a next stop when the main line's head is their decision
(`Attention`), their opening declaration, a result they haven't seen, or an undo request to answer. The server keeps it
per seat (`engine_seat.waited`, V60) after every change, with the point the player would resume at
(`engine_seat.resume_at`: their local head's turn and step). LOB-072, LOB-073, LOB-075, LOB-076, TRN-058, DRF-135, DRF-137.

Decided while building (the plainest reading; owner to confirm):
1. **Only notifications follow it**: the game list, move pill and bar colours still show "your move" for any next stop
   (TRN-058), so a player can still open the game and answer an assumption.
2. **Withdrawal:** when the other player goes on before the notice went out (an assumption right after a real input,
   cancelling an undo request) the unseen notice and its push are withdrawn and the stretch closes; one already shown
   stays on the phone. A notice that also tells a new message or a branch stays, no longer as "Your move".
3. **Who played on and left** gets the app's own reminders (LOB-076) with their usual text ("<opponent> is waiting for
   you"); the server's 5-minute reminder (LOB-075) and a branch notice's reminder arm only while the game waits for the
   recipient.
4. **Content:** "<status> · Turn 5, Alex's turn, End step" (the turn square's words; "Mulliganing" in the opening; none
   in a game-over notice), the recipient's deck (their private pod deck name, else the selected deck's) in the
   notification's header; a pod notification's text line is its set or cube name.
5. **Queued challenge:** a pod game still in its lobby, never played, its invitation pending or accepted; both the
   challenger and the challenged skip "Decks ready".

## 27. Playback pausing (owner, 2026-11-04)

- Playback pauses only on events that change something visible: a card moving (cast, play, resolve, dies, draw count), life or counter changes, attacks/blocks, the opponent's choices (targets, modes), a turn change. These keep their animation plus the usual short pause at the animation speed setting.
- Passes, step changes where nothing happened and repeated priority passes apply instantly (only the step indicator moves).
- Playback stops for good where you must decide.
- **Play/pause button** (bottom, where Auto was): pausing stops playback at the **next point where full control would stop** (a point where you could have acted), even with no decision waiting for you; there you can react (cast, activate) or turn on full control, and playing again continues. Holding the button opens the full control / auto-skip dialog.

Built (2026-11-04, with the owner's playtest notes of that day): `GameDevice.pausing` (a pause makes playback stop where
Full control would, `Status.Stopped.paused`; the player's answer there, Play, Return to current or reaching the head ends
it); the app's `EnginePlaybackPace.quiet` (only priority passes and changes nothing visible: no zone, status, face,
counter, damage, life, Stack, attachment, control, combat, loss or shuffle change, no new turn) goes by at once, the
History playback pause follows every other event. HIS-001, HIS-013, HIS-021, HIS-095.
Decided while building (owner to confirm): the button reads "Pausing" between the tap and the stop; at a stop the pause
made the playback panel stays (strip and Play), while a stop by the response mode shows the live controls.

## 28. Built (owner, 2026-11-04): "Your move" everywhere follows who is waiting for whom

Replaces §26 choice 1. The game list, the move pill, the bar colours and the heads and revisions the server sends say
"Your move" only when the game really waits for the player (`engine_seat.waited`, the record notifications use), and
"<opponent>'s move" when it waits for them; while one player plays on assumptions the acting player has the move for
both. The server's per-viewer move reads `waited` (`EngineGames`, `LobbyService`); the device works the same out on the
main line's head as it knows it (`GameDevice.move`, shown as `BoardState.move`), so a decision stop in playback is still
answered from the table while the pill says the other player's move. TRN-006, TRN-013, TRN-014, TRN-058, TRN-047,
LOB-022, LOB-024, HIS-032.

Decided while building (the plainest reading; owner to confirm):
1. **A waiting result suggestion** (TRN-047, TRN-049: it locks the game for both) is the other player's move like an
   undo request, so it is also listed, shown and notified as theirs.
2. **When the game waits for nobody** (both have seen a result not yet confirmed) the list keeps "Move not available";
   the table's pill and bars keep the board's own holder (the turn player's).


## 29. Playtest decisions (owner, 2026-11-04 evening)

1. **Catching up** shows no dialog: playback starts paused with the play/pause button at the bottom (tap = play, hold = the full control / auto-skip dialog). During the opening (mulligans) there is no playback: the current state shows directly.
2. **Passes are implied:** "X passed" never appears in the event strip, timeline, event chips or notifications (passes stay in the data). Undo targets the last meaningful own action; a turn where nothing happened shows only as step/turn changes.
3. **Playback delay** between events is about a third of the previous default, scaled by the animation speed setting (stops already happen exactly where you could react).
4. **Undo animates backwards** quickly (reverse flights, no pauses) with a fading top text ("Undid: cast <card>"); the opponent who had seen the undone events sees the same.
5. **One tap activates:** a permanent with exactly one ability you can activate now activates it on tap (a land adds its mana); with several, tap opens the same chip menu as hand cards; hold = Inspect; in declare attackers/blockers a tap stages combat instead; unused mana floats in the pool.
6. **Combat in playback** uses the middle combat-area design (I-D38); playback skips steps where nothing happened (combat included) unless full control is on.
7. Pass / End turn latency: revisit after R1–R3 (no separate fix now).

Built (1, 3, 6; 2026-11-04): `GameDevice.catchUp` holds playback paused only past the opening (`Status.CatchUp`, the
playback panel's Play, no dialog); in the opening `step` shows every event up to a stop or the first turn at once; any
submission of the player's ends the catch-up. The playback delay is `PLAYBACK_DELAY_MS` (0.33 s) divided by the Card move
speed; `EnginePlaybackPace.quiet` also takes declarations of no attackers or blockers and combat changes with no
attacker or block, and quiet events keep the delay under Full control. HIS-006, HIS-013, HIS-021, HIS-033, HIS-035,
HIS-094, SYS-071.
Decided while building (owner to confirm): the History playback pause no longer paces live playback, only timeline
mode's Auto; answering a decision in the opening (e.g. a keep the other player assumed) ends the catch-up, so playback goes
on after it without a paused start; an attack already showed in the combat area in playback (now covered by a test).

Built (2026-11-04): **2** (HIS-091, HIS-084, HIS-099): a pass event reads only as what it led to, one that led to
nothing is hidden like an old line start (`EngineEventText.hidden`); `GameDevice.undoTarget` skips every pass but an End
turn's first (`endedTurn`), and the Undo dialog lists the same (`undoChoices`, "You ended the turn"). **4** (HIS-030,
HIS-076, HIS-099): a rewind that took back events shown on a device is a transition of its own (`EngineGame.Transition.undone`)
whose steps are the undone events' motion boards in reverse; its text is posted to the event chip even with Event text
off, replacing the "<name> undid" note.
Decided while building (owner to confirm): the other player reads "Alice undid: cast Storm Spark"; an undone End turn
reads "Undid: End turn"; the undo text shows with Event text off.
