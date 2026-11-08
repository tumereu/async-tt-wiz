# First turn during the opponent's mulligans

Status: **draft for the owner's review (2026-11-08); not approved, not started.** Replaces the backlog's "Later" item
"Play your first turn while the opponent is still deciding on mulligans" (owner, 2026-11-05). Builds on async play
(I-D56: assumptions, local heads, branches, the information rule) and on the engine state as the game state (I-D54).
CR numbers are from the pinned Comprehensive Rules (`ComprehensiveRules.EFFECTIVE_DATE`).

Owner decisions so far (2026-11-08):
- A player's turn played during the other player's mulligans is kept exactly when it **comes out the same** on top of
  their real opening, what it showed included.
- The player deciding on a mulligan **must not see** anything of that turn before deciding: their game lags behind like
  any other game they haven't caught up on, and they watch the turn only after keeping.
- Random results come from **each player's own stream**.

## 1. Goal

The starting player (Alice in this document) keeps and plays turn 1 while the other player (Bob) is still deciding on his
opening. If Bob keeps, nothing changes. If Bob mulligans, Alice's turn **carries over** onto his real opening as far as
it comes out the same for her. Only from the first action that comes out differently is her turn taken back (an
ordinary branch, HIS-097). Bob never sees her turn before he has kept, and he never sees a part that was taken back.

Whether a turn depends on Bob's opening is never declared by card. It is found by doing the turn again on top of his
real opening and comparing (§4). So cards added later (new sets, new wordings) need nothing from their implementors (§7).

## 2. Terms

- **Assumed keep:** Alice's assumption (I-D56 §3) that Bob keeps his current hand. It is a new kind of assumption, the
  fourth in I-D56 §3's list.
- **Hold point (P):** the point where the assumed keep stands: Bob's local head when he gives his real answer there.
- **Held line:** Alice's line after P (the assumed keep and everything after it) once Bob answered something other than
  Keep at P. It is kept aside, off the main line, while Bob finishes his opening.
- **Junction:** the first point on the main line after Bob's real answer at P where nothing more in the opening is Bob's
  to decide: the open decision is Alice's, or turn 1 has begun.
- **Carry-over:** at the junction, the held line is made again, input by input, on top of Bob's real opening. Each
  event that comes out the same (§4) is kept as a new event of the main line. The first one that doesn't is where the
  game branches.
- **Comes out the same:** §4's rule, judged from Alice's side: she could not tell the two apart.

## 3. Behaviour

### 3.1 Assume keep

- Once Alice's own opening is done (her keep, and any cards she owes to the bottom), Bob's open opening declaration is
  assumable. This holds only while keeping needs nothing more from him: no cards owed to the bottom. In practice that
  means his first hand, or his hand after the free first mulligan (TRN-062).
- Alice's main button (TRN-063) then holds **"Assume keep"**, as it holds "Assume no response" elsewhere (TRN-004,
  TRN-070). Pressing it makes the assumed keep, and her turn-1 controls follow at once.
- Her phone never assumes Bob's keep by itself, whatever "Assume resolves" says (HIS-096). This keeps the e2e finding
  of 2026-11-05 fixed: a phone must not run ahead into turn 1 just because it opened the game first.
- There is no Assume keep while a hold is open (§3.4), or while Bob owes cards to the bottom.

### 3.2 Alice's turn on the assumed keep

- It is an ordinary tentative line: everything in I-D56 applies unchanged. That includes the information rule with the
  game's assume reveal settings (TRN-067, TRN-071, LOB-135); her other assumptions (passes, blocks); and the line
  ending at Bob's first stop, his turn-2 main phase at most, or at any real decision of his.
- One rules point: Bob's opening-hand actions (CR 103.6a) come after all keeps, the starting player's first. If Bob's
  kept hand holds a card that may begin the game on the battlefield, the engine asks him after the assumed keep. That
  is a real wait, as today (the hidden-source tell of I-D56 §5 is accepted). Alice's own opening-hand choice is her own
  input on the line.

### 3.3 Bob's side: his game lags behind

- Bob's local head stays at P, his opening. The assumed keep is an assumed answer about him, so his playback always
  stops before it (HIS-095 gains "an assumed keep always" next to "assumed blocks always"). His table there is his
  opening as today: his hand and his Mulligan / Keep quick actions (TRN-062). Nothing says that something was assumed
  (HIS-091).
- **Nothing past P reaches anything Bob sees** (I-D56 §7: everything shown comes from the local head and the next
  stop). This covers his table (Alice's battlefield, his life, his hand), the event strip, timeline, event chip and
  event text, the Stack, the wait texts, and his game-list row: order, status, move chip and last-activity time.
  It also covers notifications and reminders, and the Undo dialog's previews. His phone holds the events (I-D27) but
  shows none of them.
- His notifications are exactly today's for an opponent who kept while he is still deciding: "<Alice> is ready"
  (LOB-114), timed from her keep. Nothing more arrives while Alice plays.
- Accepted tells, as in paper and today's async play: Alice's presence (green dot), and her chat messages (players'
  own words).

### 3.4 Bob keeps: confirmation

His Keep at P is the same input as the assumed keep: a confirming duplicate (I-D56 §8). His local head moves past it,
and Alice's turn reaches him as catch-up ("<Alice> has made moves · Tap to watch", HIS-094), with the stops of his
response mode (HIS-095). Nothing about the carry-over happens.

### 3.5 Bob mulligans: the hold

Any other real answer of Bob's at P opens a hold: a mulligan, or a CR 103.5b action such as using a card "any time you
could mulligan".

- **Server:** Bob's event is made with parent P on the same line (no new line, no reshuffle, §5). The main line moves
  to it. Alice's line after P becomes the held line: kept, not discarded. Bob then goes through his opening as usual
  (more mulligans, the Keep / Bottom screen, STK-043; his opening-hand choice), all on the main line.
- **Alice:** her table stays exactly as it was (no rewind, nothing moves). Her phone waits with today's wait words
  "<Bob> is deciding on a mulligan" (TRN-066) and the main button empty. Her game inputs wait like at any wait for Bob:
  no actions, no Undo, no undo request (they would be on the held line). She can look, inspect, chat and concede. A
  cast she has begun (a draft) stays and goes on after a full carry-over. Otherwise the existing rule drops it with its
  note (async-play choice 15).
- **Bob:** sees only his own opening, as in §3.3.
- Inputs of Alice's that were in flight when the hold began are refused as stale, as after a branch today. Her phone
  shows the hold.

### 3.6 At the junction: carry-over

At the junction (§2) the server carries the held line over (§4) and the result is one of three:

1. **Everything comes out the same.** The held line's events are made again as new main-line events after the
   junction: same authors, same assumed inputs (still unconfirmed assumptions about Bob), each marked with the held
   event it came from. Alice's table changes only where Bob's opening differs for her: his hand and library sizes.
   There is no rewind or animation. A fading note reads "<Bob> took a mulligan · your turn stands". Alice's local head
   moves to the event made from the one it was on. The held line becomes a discarded line (kept for problem reports,
   I-D56 §8).
2. **Part comes out the same.** Events up to the first one that doesn't are kept, as in (1). That first event and
   everything after it are taken back as a branch (HIS-097, HIS-106): Alice's table rewinds to the first taken-back
   action, with the note "<Bob> took a mulligan — your Ember Augury was taken back" (HIS-097's wording: the first
   action taken back, in its event text's words). A new line starts at that point (HIS-042, §5). One branch
   notification goes out as for any branch.
3. **Nothing comes out the same**, or Bob's opening itself changed something Alice would have seen (§4.1). The branch
   point is the junction, so the whole turn is taken back, with the note naming her first action. Turn 1 begins again
   for her from the junction.

After the carry-over, Bob's local head is his last opening event. Whatever was carried reaches him as catch-up, as in
§3.4. What was taken back never reaches him.

An event is carried whole or not at all: when an input inside it comes out differently, the branch is at that event.

### 3.7 Other cases

- **Concession during the hold:** Alice's concession ends the game at once (CR 104.3a) and the held line is discarded.
  Bob's concession during his opening is today's concession while behind (TRN-064), and the held line is discarded.
- **Undo during the hold:** Bob's undo inside his opening works as today (TRN-023, HIS-087; a mulligan showed him a new
  hand, so taking it back is a request). If a rewind lands at or before P, the held line is deleted with the rest.
  Alice's own Undo waits (§3.5).
- **A result on the held line** (Alice wins in turn 1) carries over like any event. The result is then final only once
  Bob's local head reaches it (I-D56 §11, TRN-065).
- **Races:** if Alice's Assume keep and Bob's real declaration cross, whichever the server takes first stands (TRN-027,
  I-D56 §8). If Bob's answer was first, the assumed keep is stale and Alice's phone goes on from the new head (a wait).
- **Table-only events** Alice made on the held line follow async-play choice 16: made again on the new head when they
  still apply, otherwise dropped.

## 4. "Comes out the same"

### 4.1 At the junction

Compare the state the assumed keep led to with the state at the junction. The only differences allowed are in Bob's
hidden cards: which cards are in his hand and library, their order, and how many there are; his mulligan count; and the
engine's counters (§4.3). If his opening put a card anywhere else (a card that begins the game on the battlefield, a
card exiled by a CR 103.5b action), or showed Alice a card (a reveal), case 3 of §3.6 applies.

### 4.2 Input by input

From the junction, every input of the held line is performed again, in order, on the main line's state. For an
assumed keep event, that means the rest of the event after the keep: the turn's beginning. Random answers are worked
out again from the streams (§5), never copied. An input comes out the same when all of the following hold:

1. the engine accepts it;
2. its changes are equal, seen from Alice's side (§4.3);
3. the cards it shows Alice at any moment are equal, by card (`Engine.revealed`, as the information rule counts them);
4. Alice's view of the state after it is equal: every object she may know with its characteristics, status, counters,
   damage and attachments, and every player's public values (life, counters, mana pool). Bob's hand and library
   sizes are left out. A continuous effect that counts his hand still shows up, in the characteristics it changes;
5. the engine's open decision after it is equal, seen from Alice's side (§4.3).

An answer in the held line answers the decision now open: its decision id is replaced by the open decision's when (5)
held for the input before. A token, emblem, copy or Stack item named in a later input is the one created at the same
place in the remade run (§6.1).

### 4.3 Seen from Alice's side

Two runs are compared with:
- every card hidden from Alice before and after a change (Bob's hand, the unknown parts of his library) replaced by
  its owner, zone and the place Alice knows (the top, the bottom, Nth from the top, from the library's known places);
- the engine's counters numbered from the run's start: timestamps, the input count, and everything worked out from
  them (token, emblem and copy ids, Stack item ids, decision ids);
- **everything else compared as it is.** Nothing is left out because it looks irrelevant. A value the comparison
  doesn't know is compared exactly. A mistake in it can therefore only take back a turn that could have stood; it can
  never keep one that should have gone.

Examples (each one is a test, §8):

| Alice's turn 1 | Bob mulligans | Result |
| --- | --- | --- |
| Mountain, Ember Cub, a Storm Spark to Bob's face, End turn | to 7 (free) or 6 | carried: nothing reads his hand or library; his turn-2 draw is a hidden card from his library either way |
| Idle Glimmer: she draws | any | carried: her own library's cards and order don't depend on Bob (her own stream, §5) |
| "Each player draws a card" | any | carried: Bob draws a hidden card either way |
| "Deals damage to target player equal to the number of cards in their hand" | to 7 (free) | carried: 7 either way |
| the same | to 6 | branch at that cast: the damage differs |
| "Target player reveals the top two cards of their library" (allowed by LOB-135's library setting, on by default) | any | branch at that cast: other cards shown |
| a land, then the reveal | any | the land carried, branch at the reveal |
| anything | into a card that begins the game on the battlefield, and puts it there | branch at the junction (§4.1) |
| "Target opponent discards a card" | n/a | his choice is a real wait: the line stopped there before any mulligan |

This is "as Alice could tell" rather than "touches Bob's hand or library". Two reasons: Bob's turn-2 draw always
touches his library, so the stricter rule would take back Alice's End turn after every mulligan. And a draw of an
unknown card from a reshuffled library is the same game either way: the carried line is a legal game of Bob's real
opening.

## 5. Randomness: each player's own stream

- The k-th random answer **of player X** on line n comes from H(game secret, n, X, k), where X is the player the
  engine's random decision names (`Decision.Random.player`): the library's owner for a shuffle, the flipping or
  rolling player, the chooser of a random choice. The line-start reshuffle of each library's unknown parts (HIS-042)
  uses its owner's stream.
- Each event keeps, per player, the random index after it (it keeps one index today). The server checks every random
  answer against the stream named this way. Line numbers work as today (a counter on the play, a new line at every
  branch and rewind). The carry-over adds no line: its events stay on the held line's number, and its branch point
  (cases 2 and 3) starts a new one.
- Why this is needed: Bob's mulligans use up random answers. With one stream per line, every random answer of Alice's
  after them would change (her cantrip's draw after a line-start reshuffle, her shuffles), so no turn with any
  randomness could carry over. With own streams, Alice's answers are untouched by Bob's opening. The random answers
  Bob's opening reuses on line n were never shown to anyone: Bob never saw the held line, and random results on a
  tentative line are held back from Alice (TRN-067).
- Elsewhere play is unchanged: the same input on a line gives the same result, and a new line gives new ones.

## 6. Implementation

### 6.1 Arcanum (an API addition, needs the owner's approval as an integration step)

`Engine.seen(before, outcome, viewer): Seen` returns a value equal for two runs exactly when §4.2's points 2–5 hold
between them (§4.3's renaming and numbering). It also lists the objects the changes created, in order, so a host can
match a token of one run with the same token of the other. It is generic MTG ("could this player tell these two
outcomes apart?"), like `Engine.revealed`. The engine is the only place that knows which numbers are timestamps and
which ids come from them, so the renumbering has to live there.

- Built over the engine's own serialized forms of changes, decisions and the view. Masking and renumbering are applied
  to the id and counter values; everything else goes through as it is, so a new `Change` subtype or a new view field
  is compared without anyone listing it (§4.3's "everything else compared as it is").
- Engine tests: a **counter-shift test** plays seeded random games of cube cards from two bases that differ only by
  extra moves among the opponent's hidden cards (which shift every counter), and asserts equal `Seen` all the way. A
  **sensitivity test**, for every `Change` subtype, changes one non-counter field of a sample and asserts `Seen`
  differs. README and KDoc updated.

### 6.2 `protocol`

- `LineRandom`: per-player streams (§5); `EventBuilder` keeps an index per player; `startLine` reshuffles each library
  from its owner's stream.
- `CarryOver` (new, shared by server and devices): given P, the held line and the main line at the junction, returns
  the events that come out the same (made again, folding engine-automatic answers the way a device does, I-D35) and the
  index of the first one that doesn't. It applies §4.1 at the junction and `Engine.seen` per input.
- `Submission` / acceptance: the assumed keep as an assumable kind (I-D56 §3, only where §3.1 allows); Bob's
  mismatching answer at P opens a hold instead of a branch (§3.5); stale handling for Alice's inputs on a held line.
- `GameDevice`: `Offer.AssumeKeep` on `Wait.Keep` where §3.1 allows, made only by the press; a hold state (Alice waits
  with her table as it was); applying a carry-over: taking the held line back to P and applying Bob's opening and the
  carried events without animation; on cases 2 and 3, the HIS-106 rewind to the first taken-back action, then the
  carried state. Bob's playback stops before an assumed keep.
- `Attention`/next stop: nothing new is expected (Bob's next stop is the assumed keep). The e2e leak test (§8) checks
  it.

### 6.3 Server

- The play keeps the held line (its first event id) while a hold is open. Events gain `carriedFrom` (the held event an
  event was made from), used by devices to skip the animation and by tests.
- After each of Bob's opening events, if the main line has reached the junction, the server runs `CarryOver`, appends
  the carried events, discards the held line and, on a branch, moves the line counter on and sends the branch notice
  (LOB-116). The work is bounded: the held line is at most one turn plus Bob's untap, upkeep and draw. Nothing is
  replayed from the root (owner rule).
- Concession and rewind cases of §3.7.

### 6.4 App

- Main button "Assume keep" (§3.1). Wait words and notes as in §3.5–3.6. The fading carried note uses the branch
  note's chip (async-play choice 15) without the rewind.

### 6.5 Stored format

Per-player random indices change how every game's random results are worked out. **Games already started can't go on
after the update.** Automatum is in production since 0.2.0-beta5, so wiping started games needs the owner's permission
(AGENTS.md). Nothing is kept for older games (single version rule).

## 7. Protecting against future set work

Set-support implementors add cards and wordings. They should not need to know this feature exists.

1. **No per-card knowledge anywhere.** Nothing is tagged or listed as "touches the opponent's hand or library". The
   check is what the engine actually did (§4), so a new card that reads or changes Bob's hidden cards shows up in its
   changes, its reveals or Alice's view like any other.
2. **Failures land on the safe side.** `Seen` compares everything it doesn't explicitly mask or renumber (§4.3). A new
   value nobody thought of can only make a turn go back unnecessarily, never keep a turn that should have gone.
3. **The engine owns the renumbering**, with the counter-shift and sensitivity tests (§6.1). A new kind of derived id
   that isn't renumbered fails the counter-shift test in the engine's own suite. It never shows up as a mysterious
   branch in the app.
4. **A fuzz test over the supported sets**, in `arcanum-tools` (local card data, like `:arcanum-tools:setSupport`) and
   in every set worker's checklist. It plays seeded random turns with the set's cards on twin states that differ only
   in the opponent's hidden cards and their number. It asserts that whenever `Seen` is equal step by step, Alice's
   `Engine.view` of what she may see and `Engine.visible` are equal too: an independent path through the engine. That
   catches a card whose effect on Alice's side somehow escapes `Seen`. New sets are exercised without their
   implementor doing anything.
5. **The information rule stays as it is** (TRN-067, `InformationRule` on `Engine.revealed`). It already keeps hidden
   cards from being shown on a tentative line, independently of the carry-over.
6. No new AGENTS.md rule for set implementors. Engine implementors are covered by the tests in (3).

## 8. Tests

**Unit and server tests** (proportionate, each citing its catalog entry or CR):
- `LineRandom`: per-player streams; Alice's answers unchanged by any number of Bob's random answers before them.
- `CarryOver`: every row of §4.3's table with invented cards; an event carried whole or not at all; a branch at the
  junction for §4.1; ids and decision ids matched across runs.
- Server: hold opened by a mismatch at P; Bob's further opening on the main line; carry-over cases 1–3; concession by
  either player during a hold; Bob's rewind to before P deleting the held line; Alice's stale input during a hold; the
  Assume keep race; Assume keep refused where §3.1 doesn't allow it (cards owed to the bottom, a hold open, Alice's own
  opening not done).
- Engine: counter-shift and sensitivity tests (§6.1). Tools: the supported-sets fuzz (§7.4).

**End-to-end scenarios** (`e2e/`, each a new test class unless noted; Alice starts, both on Normal response mode,
default game settings; new invented cards go in `TestCards`; the deal is picked with `Deal`, which gains each player's
hand after a mulligan, worked out from the secret and the player's own stream):

1. **Assume keep is offered, never automatic** (changes `OpeningKeepTest`, TRN-027/TRN-070). Alice opens first and
   keeps while Bob is away. Long past any grace she still waits: turn 1 hasn't begun and nothing is assumed. The main
   button holds "Assume keep". She presses it and her turn-1 controls appear (Main 1 with her quick actions). With a
   variant deal where her keep needs a bottom choice first, the button appears only after her Keep / Bottom screen.
2. **Bob doesn't see Alice's turn before keeping** (the owner's must). Alice assumes Bob's keep, plays a Mountain,
   casts an Ember Cub and a Storm Spark at Bob (17 life on her table), and ends her turn. Bob is away throughout, then
   opens the game. A leak watch on Bob's phone (an extension of `LeakWatch`) asserts, at every rendered frame until his
   Keep, that nothing past his local head shows anywhere. That means: his table shows Alice's battlefield empty, his
   life 20 and his opening hand; there are no event strip or timeline entries, chips or event texts of hers; the wait
   texts are opening ones only; his game list row's status, order and last-activity time are those of her keep; and his
   notifications are exactly one, "<Alice> is ready". Bob mulligans once and keeps, and only then does the centre text
   "<Alice> has made moves · Tap to watch" appear.
3. **Bob keeps: Alice's turn confirmed.** As in 2, but Bob taps Keep. No branch: the events on Alice's line keep their
   ids, her table never moves, and Bob's playback shows her Mountain, Cub and Spark (his life 17) with his usual stops,
   up to his turn-2 main phase.
4. **Full carry-over with the hold.** Alice assumes Bob's keep and plays a Mountain and an Idle Glimmer (she draws card
   X), then the Cub, and ends her turn into Bob's turn 2 (his draw step included). Both are online. Bob taps Mulligan.
   Alice's table doesn't move; her wait text reads "<Bob> is deciding on a mulligan"; a tap on a land in her hand does
   nothing and sends nothing. Bob takes the free mulligan and keeps 7. Alice gets the note "<Bob> took a mulligan · your
   turn stands". Her battlefield, hand (card X included), library order and life are unchanged; Bob's hand size on her
   table is right; no rewind played. Bob watches her turn, including X being drawn (a hidden card to him), and his own
   turn-2 draw comes from his new library.
5. **Carry-over to a smaller hand.** Alice's turn: a Mountain and a Storm Spark at Bob. Bob mulligans twice and puts a
   card on the bottom (Keep / Bottom screen, STK-043) during the hold. Carried as in 4: Bob ends at 6 cards and 17 life
   on both tables.
6. **Partial take-back at a reveal.** Alice plays a Mountain, then casts Ember Augury at Bob ("Target player reveals
   the top two cards of their library"; allowed on the tentative line by LOB-135's default). She sees his top two
   cards. Bob mulligans and keeps. Alice's table rewinds only to the Augury, with "<Bob> took a mulligan — your Ember
   Augury was taken back". Her Mountain stays on the battlefield, Augury is back in her hand, and her library's unknown
   part is reshuffled (a new line). Bob's playback shows the Mountain and never the Augury or its revealed cards (leak
   watch on Bob's phone).
7. **Count-dependent effect, both ways.** New card "Ember Echo" ({0} Sorcery: "Ember Echo deals damage to target player
   equal to the number of cards in their hand."). Alice casts it at Bob (7 damage). (a) Bob takes the free mulligan to
   7: carried, Bob at 13. (b) A second test where Bob mulligans to 6: branch at the Echo, which is back in Alice's hand.
8. **Hidden draws don't matter.** New card "Ember Tithe" ({0} Sorcery: "Each player draws a card."). Alice casts it,
   then Bob mulligans and keeps: carried. Alice drew the same card as before. In Bob's playback he draws the top card of
   his new library.
9. **Bob's opening changes the board: whole turn taken back.** New card "Ember Vanguard" (Creature: "If this card is in
   your opening hand, you may begin the game with it on the battlefield."). The deal gives Bob no Vanguard in his first
   hand and one after a mulligan. Alice plays a Mountain and a Cub. Bob mulligans, keeps, and begins with the Vanguard
   on the battlefield. Alice's whole turn is taken back to the junction, with the note naming her Mountain. Turn 1
   begins again for her with the Vanguard on Bob's side. Bob's playback shows nothing of the first attempt.

Each scenario runs with `LeakWatch` on both phones (the existing information-rule watch on Alice's, the new lag watch
on Bob's).

## 9. Catalog entries (changed with the code)

- **TRN-027**: the starting player plays turn 1 on her own keep and Assume keep (no longer "only after the other
  player's own keep"); the carry-over and the hold in one sentence each, pointing to the new entries.
- **TRN-070**: "Assume keep" joins the main button's Assume actions; "There is no Assume keep" goes.
- **TRN-004**: the main button holds "Assume keep" during the other player's opening declaration where §3.1 allows,
  otherwise nothing, as now.
- **HIS-096**: the opening keep is assumable from Assume keep only, never by the phone itself; the rest stays a real
  wait.
- **HIS-095**: playback always stops before an assumed keep.
- **HIS-091**: "Bob kept (assumed)" for the assumer.
- **HIS-097**: a mismatch at an assumed keep holds instead of branching, and branches only where the carry-over
  stops.
- **HIS-042**: random results follow from the game's secret, the line and the player whose result it is.
- **New HIS entries**: the hold (§3.5); the carry-over with its three cases and notes (§3.6); "comes out the same"
  (§4, short); Bob's lag (§3.3: nothing past his local head reaches him before his keep).
- **LOB-114**: unchanged; the e2e test cites it.

## 10. Release note

`add: The starting player can play their first turn while the opponent is still deciding on a mulligan. If the opponent
takes a mulligan, the turn stays as long as nothing in it depended on their hand or library.`

## 11. Choices for the owner

1. **Branch point** when part of the turn depends on Bob's opening: at the first action that comes out differently
   (proposed, §3.6 case 2), or always back to the start of turn 1 (the backlog's first wording).
2. **"Comes out the same" as Alice could tell** (§4.3): hidden draws from Bob's library and his smaller hand alone
   never take her turn back. The stricter "touches his hand or library" would take back her End turn after every
   mulligan once her line reached his draw step.
3. **Bob's opening that changes or shows anything outside his hand and library** (a card beginning the game on the
   battlefield, a reveal) takes the whole turn back (§4.1), even when none of Alice's actions read it, because she
   would have seen it before turn 1.
4. **Assume keep only from the button**, never by the phone itself (§3.1).
5. **No re-assumed keep after a mulligan:** during the hold Alice waits for Bob's whole opening. She can't assume his
   keep again after his free mulligan.
6. **A note when the turn stands** ("<Bob> took a mulligan · your turn stands") rather than silence.
7. **Alice's Undo waits during the hold** (§3.5) rather than popping events off the held line.
8. **The Arcanum API addition** `Engine.seen` (§6.1). Without it, `protocol` would have to renumber timestamps and
   derived ids itself from serialized field names, which is weaker against future engine changes.
9. **Wiping games already started** for the per-player random streams (§6.5).

## 12. Not included

- Assuming Bob's bottom choice or opening-hand actions.
- Carrying over after anything other than Bob's opening (other assumptions keep branching exactly as today).
- More than two players.
