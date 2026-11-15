# First turn during the opponent's mulligans

Status: **approved by the owner (2026-11-15), queued.** Replaces the backlog's "Later" item "Play your first turn while
the opponent is still deciding on mulligans" (owner, 2026-11-05). Builds on async play (I-D56: assumptions, local heads,
branches, the information rule) and on the engine state as the game state (I-D54). CR numbers are from the pinned
Comprehensive Rules (`ComprehensiveRules.EFFECTIVE_DATE`).

Owner decisions (2026-11-08, 2026-11-15):
- A player's turn played during the other player's opening stands exactly as far as it **comes out the same** on top of
  their real opening, as the player could tell. From the first action that doesn't, it is taken back (a branch).
- The player deciding on a mulligan **must not see** anything of that turn before keeping: their game lags behind like
  any other game they haven't caught up on, and they watch the turn after keeping.
- Random results come from **each player's own stream**. Games already started are wiped by the update.
- **No set-aside turn.** At each of the other player's opening events, the turn is re-applied on top of it at once; the
  player never waits for the other player's opening to end, and their Undo works as anywhere else.
- **Each player's mulligans are their own**: taken as soon as declared, in the order the players make them, not in rounds.
- **No new top-level Engine function.** The comparison reuses the engine's existing id-free position key.

## 1. Goal

The starting player (Alice) keeps and plays turn 1 while the other player (Bob) is still deciding on his opening. If
Bob keeps, nothing changes. Each time Bob makes an opening move instead (a mulligan, a CR 103.5b action, a keep with a
different bottom choice), Alice's turn is taken back, Bob's move put in its place, and her turn made again on top of it,
action by action. As far as it comes out the same for her she goes on as if nothing happened (she may see Bob's hand
count change). From the first action that comes out differently, her turn is taken back as an ordinary branch (HIS-097).
Bob never sees her turn before he has kept, and never sees a part that was taken back.

Whether a turn depends on Bob's opening is never declared by card. It is found by making the turn again and comparing
(§4), so cards added later need nothing from their implementors (§7).

## 2. Terms

- **Assumed keep:** Alice's assumption (I-D56 §3) that Bob keeps his current hand. A new kind of assumption, the fourth
  in I-D56 §3's list. When keeping needs cards on the bottom (from Bob's second mulligan on), the assumed keep includes
  a stand-in bottom choice (§3.2).
- **Base (P):** the point Alice's line rests on: Bob's last real opening event before her assumed keep.
- **Re-apply:** when Bob's real opening event at P isn't the assumed keep, the server makes Bob's event the new base and
  makes Alice's line again on top of it, input by input. Each input that comes out the same (§4) becomes a new event of
  the main line, marked with the event it was made from; the first that doesn't is where the game branches.
- **Comes out the same:** §4's rule, judged from Alice's side: she could not tell the two apart.

## 3. Behaviour

### 3.1 Mulligans are each player's own

Both players begin "Mulliganing" at the same time (TRN-027, TRN-062). A player's Mulligan is taken as soon as they
declare it: their hand goes back, their library is shuffled from their own random stream (§5) and they draw their new
hand, without waiting for the other player's declaration. A Keep (with its bottom choice) likewise ends that player's
opening at once. The events appear in the order the players made them. Because each player's shuffles and draws come
only from their own stream, the hands and libraries come out as a CR 103.5 order of declarations would give them; only
who decides knowing what differs, which the host variant already accepts (`Setup.simultaneousMulligans`: today
declarations may come in any order but the mulligans are taken per round; it changes to taking each at once). Turn 1
begins once every player has kept (or is assumed to have kept, §3.2) and the opening-hand actions (CR 103.6) are done.
Correct for any number of players.

### 3.2 Assume keep

- Once Alice's own opening is done (her keep and its bottom choice), Bob's open opening is assumable. Alice's main button
  (TRN-063) then holds **"Assume keep"**, as it holds "Assume pass" elsewhere (TRN-004, TRN-070). Pressing it makes the
  assumed keep, and her turn-1 controls follow at once.
- Her phone never makes the first assumed keep by itself, whatever "Assume resolves" says (HIS-096). This keeps the e2e
  finding of 2026-11-05 fixed: a phone must not run ahead into turn 1 just because it opened the game first. After a
  re-apply (§3.5) the assumed keep is part of the line that is made again, so no new press is needed.
- When Bob's keep needs cards on the bottom, the assumed keep puts a stand-in choice there: the last cards of his hand in
  hand order. It is hidden from Alice like his hand (never shown, never in her view or the event texts), and his real
  choice replaces it at his keep through a re-apply.
- Alice's turn on the assumed keep is an ordinary tentative line: everything in I-D56 applies unchanged, including the
  information rule with the game's assume reveal settings (TRN-067, TRN-071, LOB-135), her other assumptions (passes,
  blocks), and the line ending at Bob's first stop, his turn-2 main phase at most, or at any real decision of his.
- Opening-hand actions (CR 103.6a) come after all keeps, the starting player's first. If Bob's kept hand holds a card
  that may begin the game on the battlefield, the engine asks him after the assumed keep: a real wait, as today (the
  hidden-source tell of I-D56 §5 is accepted).

### 3.3 Bob's side: his game lags behind

- Bob's local head stays in his opening. The assumed keep is an assumed answer about him, so his playback always stops
  before it (HIS-095 gains "an assumed keep always" next to "assumed blocks always"). His table there is his opening as
  today: his hand and his Mulligan / Keep quick actions (TRN-062). Nothing says that something was assumed (HIS-091).
- **Nothing past his local head reaches anything Bob sees** (I-D56 §7): his table (Alice's battlefield, his life, his
  hand), the event strip, timeline, event chip and event text, the Stack, the wait texts, his game-list row (order,
  status, move chip, last-activity time), notifications and reminders, and the Undo dialog's previews. His phone holds
  the events (I-D27) but shows none of them.
- His notifications are today's for an opponent who kept while he is still deciding: "<Alice> is ready" (LOB-114),
  timed from her keep. Nothing more arrives while Alice plays.
- Accepted tells, as in paper and today's async play: Alice's presence (green dot) and her chat messages.

### 3.4 Bob keeps as assumed

His Keep at P is the same input as the assumed keep (with no bottom choice, or the same one): a confirming duplicate
(I-D56 §8). His local head moves past it, and Alice's turn reaches him as catch-up ("<Alice> has made moves · Tap to
watch", HIS-094), with the stops of his response mode (HIS-095).

### 3.5 Bob's other opening moves: re-apply

Any other real answer of Bob's at P re-applies Alice's line: a mulligan, a keep with a different bottom choice than the
stand-in, a CR 103.5b action ("any time you could mulligan").

- **Server:** Bob's event is made with parent P on the same line (no new line, no reshuffle, §5) and becomes the base.
  Alice's line after P is made again on top of it (§4), at once. Three outcomes:
  1. **Everything comes out the same.** The re-made events follow Bob's event: same authors, same assumed inputs, each
     marked with the event it was made from (`carriedFrom`). Alice's table changes only where Bob's move differs for
     her: his hand and library counts, with his hand's mulligan animation. No rewind. A fading note reads "<Bob> took a
     mulligan · your turn stands" (only for a mulligan; a different bottom choice or a CR 103.5b action that changes
     nothing she sees shows nothing). Her local head moves to the event made from the one it was on. The old events
     become a discarded line (kept for problem reports, I-D56 §8).
  2. **Part comes out the same.** Events up to the first one that doesn't are kept as in (1). That first event and
     everything after it are taken back as a branch (HIS-097, HIS-106): Alice's table rewinds to the first taken-back
     action, with "<Bob> took a mulligan — your Ember Augury was taken back" (HIS-097's wording). A new line starts at
     that point (HIS-042, §5). One branch notification goes out as for any branch.
  3. **Nothing comes out the same** (including when Bob's move itself changed something she sees, such as a card put
     onto the battlefield or revealed: then her first input already differs). The whole turn is taken back with the note
     naming her first action, and turn 1 begins again for her from Bob's event.
- **Alice keeps playing throughout.** There is no wait for Bob's opening to end: after an outcome 1 or 2 her line again
  rests on an assumed keep, now of Bob's new hand, and she goes on. Each of Bob's further opening moves re-applies again.
- **Alice's inputs in flight** (made on the old events while the re-apply ran) are refused as stale by the server. Her
  phone makes each again on the matching re-made event when it still applies there (the same decision, by `carriedFrom`),
  and drops it with the existing note otherwise (async-play choice 15). Table-only events follow async-play choice 16.
- **Alice's Undo** works as anywhere else: it acts on her line as it is now, and a race with a re-apply is settled by
  which the server takes first (TRN-027, I-D56 §8).
- After Bob's keep, whatever stands reaches him as catch-up (§3.4). What was taken back never reaches him.
- An event is re-made whole or not at all: when an input inside it comes out differently, the branch is at that event.

### 3.6 Other cases

- **Concession:** Alice's concession ends the game at once (CR 104.3a). Bob's concession during his opening is today's
  concession while behind (TRN-064).
- **Bob's undo** inside his opening works as today (TRN-023, HIS-087; a mulligan showed him a new hand, so taking it back
  is a request). A rewind of his to before P re-applies Alice's line onto the point it lands on, as any move of his does.
- **A result on Alice's line** (she wins in turn 1) is re-made like any event and is final only once Bob's local head
  reaches it (I-D56 §11, TRN-065).
- **Races:** if Alice's Assume keep and Bob's real declaration cross, whichever the server takes first stands. If Bob's
  was first, the assumed keep is stale and Alice's phone goes on from the new head.

## 4. "Comes out the same"

From Bob's new event, every input of Alice's line is performed again, in order, on the new state. Random answers are
worked out again from the streams (§5), never copied. An input comes out the same when all of the following hold:

1. the engine accepts it;
2. the position after it is equal as Alice could tell (§4.1), which includes the open decision and the changes the
   input made that she could see;
3. the cards it shows Alice are equal, by card (`Host.revealed`, as the information rule counts them).

An answer in Alice's line answers the decision now open; an object or decision it names is the one in the same place
of the new run (§6.1).

### 4.1 As Alice could tell

Compared with the engine's position key (`Host.loopKey`, today used to find loops: the state with object ids renamed by
their place and timestamps, counters and history left out), taken for Alice as viewer:
- every card hidden from her (Bob's hand, the unknown parts of his library) appears only as its owner, zone and the
  place she knows (top, bottom, Nth from the top, the library's known places), so Bob's hand and library contents and
  order never count, and neither do their counts on their own: a continuous effect that counts his hand still shows up
  in the characteristics it changes;
- the changes of the input she could see are part of the key, so two runs that end equal by different visible steps
  differ;
- **everything else is compared as it is.** Nothing is left out because it looks irrelevant, so a mistake can only take
  back a turn that could have stood, never keep one that should have gone.

Examples (each one is a test, §8):

| Alice's turn 1 | Bob's move | Result |
| --- | --- | --- |
| Mountain, Ember Cub, a Storm Spark to Bob's face, End turn | mulligan to 7 (free) or 6 | stands: nothing reads his hand or library; his turn-2 draw is a hidden card either way |
| Idle Glimmer: she draws | any | stands: her library doesn't depend on Bob (her own stream, §5) |
| "Each player draws a card" | any | stands: Bob draws a hidden card either way |
| "Deals damage to target player equal to the number of cards in their hand" | mulligan to 7 (free) | stands: 7 either way |
| the same | mulligan to 6 | branch at that cast |
| "Target player reveals the top two cards of their library" (LOB-135's default allows it) | any | branch at that cast: other cards shown |
| a land, then the reveal | any | the land stands, branch at the reveal |
| anything | a keep with a card that begins the game on the battlefield, put there | whole turn back (case 3) |
| anything that doesn't read his library's bottom | a keep with another bottom choice than the stand-in | stands |
| "Target opponent discards a card" | n/a | his choice is a real wait: the line stopped there |

This is "as Alice could tell" rather than "touches Bob's hand or library": Bob's turn-2 draw always touches his library,
so the stricter rule would take back Alice's End turn after every mulligan.

## 5. Randomness: each player's own stream

- The k-th random answer **of player X** on line n comes from H(game secret, n, X, k), where X is the player the
  engine's random decision names (`Decision.Random.player`): the library's owner for a shuffle, the flipping or rolling
  player, the chooser of a random choice. The line-start reshuffle of each library's unknown parts (HIS-042) uses its
  owner's stream.
- Each event keeps, per player, the random index after it. The server checks every random answer against the stream
  named this way. A re-apply adds no line: re-made events stay on the line's number; a branch point (outcomes 2 and 3)
  starts a new one.
- Why: Bob's mulligans use up random answers. With one stream per line every random answer of Alice's after them would
  change, so no turn with randomness could stand, and the two players' openings would depend on each other (§3.1).
- Elsewhere play is unchanged: the same input on a line gives the same result, a new line gives new ones.

## 6. Implementation

### 6.1 Arcanum (an integration step the owner approves)

No new Engine function (owner, 2026-11-15). The existing `Host.loopKey(state)` (Loops.key: object ids renamed by place,
timestamps, counters and history left out) gains an optional viewer, which masks what that player can't know (via the
same knowledge as `Host.visible`) and keeps the changes since the last input that the viewer could see. `LoopKey` also
carries the place naming it used, so a host can find, for an object or decision named in an input, the one in the same
place of another run. Loop detection keeps calling it without a viewer and is unchanged.

- Engine tests: a **counter-shift test** plays seeded random games of cube cards from two bases that differ only by
  extra moves among the opponent's hidden cards (which shift every counter), and asserts equal viewer keys all the way.
  A **sensitivity test**, for every `Change` subtype, changes one non-counter field of a sample and asserts the viewer
  key differs. README and KDoc updated.
- `Setup.simultaneousMulligans` (§3.1): each player's mulligan is taken when declared; correct for 3+ players.

### 6.2 `protocol`

- `LineRandom`: per-player streams (§5); `EventBuilder` keeps an index per player; `startLine` reshuffles each library
  from its owner's stream.
- `Reapply` (new, shared by server and devices): given P, Bob's new event and Alice's line after P, returns the events
  that come out the same (made again, folding engine-automatic answers the way a device does, I-D35) and the index of
  the first one that doesn't. It uses the viewer key and `Host.revealed` per input.
- `Submission` / acceptance: the assumed keep as an assumable kind (only where §3.2 allows) with its stand-in bottom
  choice; Bob's mismatching answer at P re-applies instead of a plain branch (§3.5).
- `GameDevice`: `Offer.AssumeKeep` on `Wait.Keep`, made only by the press; applying a re-apply (moving the local head
  to the re-made event, Bob's hand animation only; on outcomes 2 and 3 the HIS-106 rewind to the first taken-back
  action); re-making refused in-flight inputs (§3.5). Bob's playback stops before an assumed keep.

### 6.3 Server

- Events gain `carriedFrom`. After each of Bob's opening events at P the server runs `Reapply`, appends the re-made
  events, discards the old ones and, on a branch, moves the line counter on and sends the branch notice (LOB-116). The
  work is bounded by one turn plus Bob's untap, upkeep and draw. Nothing is replayed from the root (owner rule).
- Mulligans taken per player (§3.1).

### 6.4 App

Main button "Assume keep" (§3.2). Notes as in §3.5; the "turn stands" note uses the branch note's chip without a rewind.

### 6.5 Stored format

Per-player random indices change how every game's random results are worked out: **games already started can't go on
after the update and are wiped** (owner, 2026-11-15). Nothing is kept for older games (single version rule). If it ships
in the same release as the V85 wipe, that is one wipe for the players.

## 7. Protecting against future set work

Set-support implementors should not need to know this feature exists.

1. **No per-card knowledge.** Nothing is tagged as "touches the opponent's hand or library"; the check is what the
   engine actually did (§4).
2. **Failures land on the safe side.** The viewer key compares everything it doesn't mask or rename, so a value nobody
   thought of can only take a turn back unnecessarily.
3. **The engine owns the renaming**, with the counter-shift and sensitivity tests (§6.1): a new derived id that isn't
   renamed fails in the engine's own suite.
4. **A fuzz test over the supported sets** in `arcanum-tools` (local card data, like `:arcanum-tools:setSupport`) and in
   every set worker's checklist: seeded random turns with the set's cards on twin states that differ only in the
   opponent's hidden cards and their number; whenever the viewer keys are equal step by step, Alice's `Engine.view` of
   what she may see and `Host.visible` must be equal too.
5. **The information rule stays as it is** (TRN-067, `InformationRule` on `Host.revealed`).
6. No new AGENTS.md rule for set implementors.

## 8. Tests

**Unit and server tests** (each citing its catalog entry or CR):
- `LineRandom`: per-player streams; Alice's answers unchanged by any number of Bob's random answers before them.
- `Reapply`: every row of §4.1's table with invented cards; an event re-made whole or not at all; objects and decision
  ids matched across runs.
- Engine: counter-shift and sensitivity tests; mulligans taken per player for 2 and 3 players (CR 103.5 host variant).
- Server: re-apply at each of Bob's opening moves (outcomes 1–3); several re-applies in a row; the stand-in bottom
  choice replaced by Bob's; Alice's stale input re-made or dropped; Alice's undo racing a re-apply; concession by either
  player; Bob's rewind to before P; the Assume keep race; Assume keep refused where §3.2 doesn't allow it.
- Tools: the supported-sets fuzz (§7.4).

**End-to-end scenarios** (`e2e/`; Alice starts, both on Normal response mode, default settings; new invented cards in
`TestCards`; `Deal` gains each player's hand after a mulligan, worked out from the secret and the player's own stream).
Each runs with `LeakWatch` on both phones (the information-rule watch on Alice's, a new lag watch on Bob's).

1. **Independent mulligans** (TRN-027, TRN-062). Both open the game. Alice mulligans twice while Bob hasn't declared:
   each new hand shows at once. Bob mulligans once; the events read in the order made.
2. **Assume keep is offered, never automatic** (changes `OpeningKeepTest`). Alice keeps while Bob is away; long past any
   grace turn 1 hasn't begun. The main button holds "Assume keep"; pressing it brings her turn-1 controls.
3. **Bob doesn't see Alice's turn before keeping.** Alice assumes his keep, plays a Mountain, an Ember Cub and a Storm
   Spark at Bob, and ends her turn. Bob opens the game; the lag watch asserts at every frame until his Keep that nothing
   past his local head shows (his table, life 20, strip, timeline, wait texts, game row, one notification "<Alice> is
   ready"). Bob mulligans and keeps; only then "<Alice> has made moves · Tap to watch".
4. **Bob keeps: confirmed.** As in 3 but Bob keeps: no re-apply, event ids unchanged, Bob's playback shows her turn.
5. **She plays on through his mulligans.** Alice assumes Bob's keep, plays a Mountain and an Idle Glimmer (draws card X).
   Bob mulligans (free): Alice's table doesn't rewind, his hand count animates, the note "<Bob> took a mulligan · your
   turn stands" fades. She casts the Cub without waiting. Bob mulligans again to 6, then keeps with a bottom choice
   other than the stand-in: her turn still stands; her hand (X included) and life unchanged; Bob ends with 6 cards.
6. **Input in flight during a re-apply.** Alice's land play crosses Bob's mulligan: the land ends up played once, on the
   new events, with no note.
7. **Undo while Bob decides.** Alice casts the Cub, Bob mulligans (stands), Alice undoes the Cub as usual (free undo
   rules), then Bob keeps.
8. **Partial take-back at a reveal.** Alice plays a Mountain, then Ember Augury at Bob (sees his top two). Bob
   mulligans. Alice's table rewinds only to the Augury with "<Bob> took a mulligan — your Ember Augury was taken back";
   the Mountain stays. Bob's playback never shows the Augury or its cards.
9. **Count-dependent effect, both ways.** "Ember Echo" ({0} Sorcery: "Ember Echo deals damage to target player equal to
   the number of cards in their hand.") at Bob (7). (a) free mulligan to 7: stands. (b) mulligan to 6: branch at Echo.
10. **Bob's opening changes the board.** "Ember Vanguard" (Creature: "If this card is in your opening hand, you may begin
    the game with it on the battlefield."). Bob's hand after a mulligan has one; he keeps and puts it onto the
    battlefield. Alice's whole turn is taken back, note naming her first action; turn 1 begins again with the Vanguard.

## 9. Catalog entries (changed with the code)

- **TRN-027**: mulligans are each player's own (§3.1); the starting player plays turn 1 on Assume keep; re-apply in one
  sentence, pointing to the new entries.
- **TRN-062**: a Mulligan is taken at once, without waiting for the other player's declaration.
- **TRN-070, TRN-004**: "Assume keep" joins the main button's Assume actions.
- **HIS-096**: the opening keep is assumable from Assume keep only (re-made after a re-apply), never by the phone itself.
- **HIS-095**: playback always stops before an assumed keep.
- **HIS-091**: "Bob kept (assumed)" for the assumer.
- **HIS-097**: a mismatch at an assumed keep re-applies, and branches only where it stops coming out the same.
- **HIS-042**: random results follow from the game's secret, the line and the player whose result it is.
- **New HIS entries**: re-apply with its three outcomes and notes (§3.5); "comes out the same" (§4, short); Bob's lag
  (§3.3).

## 10. Release note

`add: The starting player can play their first turn while the opponent is still deciding on a mulligan. If the opponent
takes a mulligan, the turn stays as long as nothing in it depended on their hand or library.`
`mod: Each player's mulligans happen at once, without waiting for the other player.`

## 11. Not included

- Assuming Bob's opening-hand actions (CR 103.6a): a real wait.
- Re-applying after anything other than Bob's opening (other assumptions keep branching exactly as today).
