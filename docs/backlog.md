# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-08: beta6 and beta7 batches all built (gameplay notes, draft notes, catalog review follow-ups, draft history / Winston tables / Rotisserie queue, Side + Maybe, single-version rest, invitation notifications).

2026-11-08 beta7 playtest notes: start after the KX8GYS hotfix is deployed. Bug first, then the table batch; the draft stats and card states batches each have their own implementor. All merged, full suites and e2e green → prod release (owner approved in advance).

## Ready to build

Bug (owner, 2026-11-08; reports P2XUER, 3WWPA8):

- A player's first main phase was skipped, cause not found: in turn 13 of a beta7 game their phone's passes from main 1 to declare attackers reached the server 30.0 s after the main phase began (server 11:33:44.290 → 11:34:14.284), and they say they didn't press. It doesn't reproduce (`MainPhaseStaysTest`: the same race with the other player's End turn, then 35 s at main 1) and the device has no way of its own to do it, so what is left is a press they didn't register, made at 11:34:14 or (if the phone's uplink stalled) just as main 1 arrived. Nothing to build until it happens again: main-button presses are now in the app log with what the button said and the step, and a pass the device itself makes at your main phase is a warning (LOB-089). At the next report read those lines around the passes; a press line means the tap happened (then the Your move cue and the main phase undo point below are the answer), no press line with passes means a fault to fix.

Table batch (owner, 2026-11-08):

- Turn banner (new entry): at a turn change "Your turn" / "<name>'s turn" shows at the screen's centre and fades in about 1.2 s, taking no touches, live and in catch-up playback, never in timeline mode; in order with plays shown (HIS-112).
- Your move cue (TRN-058, TRN-063): when the move passes to you from the other player, one haptic tick and one main-button pulse in your colour; never for your own chain of steps.
- Leaving your own main phase is an undo point (HIS-084, HIS-099, HIS-101, HIS-005): tapping the main button out of your main phase is an event on the timeline ("You went to combat") that one Undo returns to, so a mistaken tap needs no rewind into the other player's turn.
- Timeline exit (HIS-010, HIS-015): at the last event the Auto/Pause button reads "Exit timeline" and a tap leaves timeline mode at once; Next stays a plain arrow.
- Piles (TBL-053, TBL-129, TBL-027): tapped members lie on top of a stack, re-sorted only when stacks merge; entered this turn splits only creatures, so other permanents join their stack at once (the "2 sick" idea below stays separate).
- Combat stays readable (HIS-104, HIS-112, TBL-125, LOB-082): a combat that goes by itself holds your view (never the game, as plays shown does) on the attackers shown and on the damage, for the "Show the other player's plays" time (the same setting, 0 = no hold), a tap skipping it. Declaring no attackers holds nothing.
- Chat bubbles (LOB-136, LOB-137, LOB-140): bubbles always start at the life square and may cover anything, hand cards included; the rule finding other room goes. Write (LOB-139) focuses the chat's input and opens the keyboard.

Card states batch (owner, 2026-11-08; own implementor):

- Audit what the rules state holds and the table doesn't show, then show it (TBL-113, TBL-104, TRN-053). Card states such as Ring-bearer, goaded, monstrous, renowned, suspected, saddled: a word on the chip, a line in Inspect, the hold explanation (TBL-132). Player states such as monarch, initiative, city's blessing, speed, day/night, the Ring's level: chips by the life square, like emblems. Restrictions such as "can't block", "can't attack", "can't be blocked", "doesn't untap": lines in Inspect, adding what Arcanum's public view needs (owner reviews the API addition with the results).

Castable-spell review leftovers (owner, 2026-11-08, report KX8GYS; reductions that depend on a choice are fixed and tested in `CostFallsAfterChoiceOptionsTest`), each to decide or fix (TBL-117, STK-047):

- A mana ability that itself costs mana (Signet, filter land) isn't counted ahead: the card doesn't glow or raise until its mana is floated by tapping (TBL-122).
- A required choice of two or more tries only the first candidates (`Options.some`), so a spell whose first pair is refused isn't listed though another pair works; with more than 40 candidates the look-ahead stops and lists the cast though none may be payable; a mana search over 3,000 steps gives up (`Mana.search`).
- "Up to one" and "None" stay possible where the reduction needs a pick (harmonize tapping nothing, with too little mana): the cost can then not be paid and the cast is dropped.

Small, not urgent (owner, 2026-11-08):

- Opponent's modes and X on the Stack, shown and tested (STK-057, STK-018; test and screenshots only, no entry change): screenshots of the other player's modal spell and an X spell, each with the battlefield line and the Stack list, one gallery.tsv entry each; a unit test that the line and the Stack list show the chosen modes (X is already covered by StackLineTest). If the app doesn't show them as the entries say, report it.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

## Needs discussion

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens (Inspect now shows them with normal corners).
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as what you did comes out the same on top of their real opening (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority. Spec draft for the owner's review: `docs/specs/first-turn-during-mulligans.md` (2026-11-08).

- Choose a card's printing (art) in decks and cubes.
