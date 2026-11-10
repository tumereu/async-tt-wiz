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

Queue (owner, 2026-11-09 evening): after the two running lanes (catalog step 1a; game ending + 5DDSKK), in any order: the 2026-11-09 playtest notes, the P2XUER/3WWPA8 bug, the small items, the Arcanum gap lists (by-hand leftovers, foretell, copies), the misread check, the old-form cleanups, tokens in Inspect, and the two items waiting on a repro (investigate). The evidence selection's running total is not wanted for now (owner).

Playtest notes (owner, 2026-11-10), queued:

- Your move / opponent move flicker (owner, 2026-11-10): in live play the move colouring sometimes flips back and forth. Hypothesis: it is worked out from the board on screen at a moment, so a confirmation or the other player's event shows the server's board for a frame before the device's pending moves and assumptions are applied again; or "their move" is judged before the passes the device assumes for them (their Control and assume modes). Log every change of the move colour with its reason; an e2e that records the sequence during live back-and-forth (the other player in Control and in the assume modes) and fails on an A→B→A flip with no real event between; find the cause and derive the colour only from the settled board (confirmed board + pending own moves + assumptions). No UI change.
- Combat damage to players on the battlefield (owner, 2026-11-10: "let's try this and refine via gallery comments first"): after combat damage, each attack target's marker in the combat band (the defending player, a planeswalker, a battle) gets one red splash chip with its net total from all lanes (for a player: the life it lost to that damage minus life it gained at the same moment), and in each lane where damage reached the target (unblocked or trample) a small red splash behind the blockers with that lane's amount; the attacker's lifelink gain shows as a green +N on their own life square. Kept until combat ends, live and in catch-up, in the show-plays hold too. The life-total chips stay. Gallery: the whole combat as a captioned sequence from both phones, one image per pause, each caption saying whether it is a forced hold (show plays and combat) or a wait for the other player: attack shown, waiting for blocks, blocks shown, the damage hold with the new chips, after combat. The owner reviews them before the release that ships this.
- Fix (owner, 2026-11-10): as the defender, blockers could be dragged during the declare attackers step and that caused an Automatum error. Before your declare blockers step your own creatures can't be dragged as blockers (Assume blockers, CMB-040, is the attacker's and stays); e2e first, and find and fix the error itself.
- Ability chips (TBL-124, owner 2026-11-10): never while targeting (your own or the other player's permanents), and never on permanents you don't control unless Automatum lets you activate one of their abilities; their abilities are read in Inspect.
- When declared attackers are confirmed the combatants shift a little to the left: the empty slot that goes away must not take part in positioning the combatants.
- Multiset drafting (owner, 2026-11-10: "the pack grid"): by default one set for the whole pod as today; "Mix sets" opens the pod's pack grid, one row per round and one cell per pack of that round (the existing rounds and packs-per-round settings decide its size), each cell a set chip. Tap a cell to choose that pack's set, a row label ("Round 2") to set every pack of that round, a column label ("Pack 1") to set that position in every round. The order in a row is the order that round's packs are opened. Every set must be draftable (unsupported ones can't be chosen, as in today's picker); Sealed is one row; presets save the grid (so "Original RTR block" is made once); the other pack-opening modes (Winston, Winchester, Grid with fresh boosters) take the same grid read row by row as their list of packs. The server builds each pack from its set code (DRF-169); pods send the grid of set codes. New entries for the grid; DRF-088, DRF-090, DRF-169 and the preset entries change. Set symbols (owner, 2026-11-10): each set shows its symbol in the set picker list (DRF-088) and in every cell of the pack grid; plainest source: the catalog's set records carry MTGJSON's `keyruneCode` and the app bundles the Keyrune icon font (SIL OFL) to draw them offline. Gallery (owner): a screenshot of each phase in gallery.tsv (the set picker with symbols, the one-set default with Mix sets, the open grid, a row label chosen, a column label chosen, a single cell, Sealed's one row, a preset saved with a grid) — the owner reviews them before the release that ships multiset drafting.

Bug (owner, 2026-11-08; reports P2XUER, 3WWPA8):

- A player's first main phase was skipped, cause not found: in turn 13 of a beta7 game their phone's passes from main 1 to declare attackers reached the server 30.0 s after the main phase began (server 11:33:44.290 → 11:34:14.284), and they say they didn't press. It doesn't reproduce (`MainPhaseStaysTest`: the same race with the other player's End turn, then 35 s at main 1) and the device has no way of its own to do it, so what is left is a press they didn't register, made at 11:34:14 or (if the phone's uplink stalled) just as main 1 arrived. Nothing to build until it happens again: main-button presses are now in the app log with what the button said and the step, and a pass the device itself makes at your main phase is a warning (LOB-089). At the next report read those lines around the passes; a press line means the tap happened (then the Your move cue and the main phase undo point below are the answer), no press line with passes means a fault to fix; a pass at the player's own main phase with no mark on the record means the device did it.

Needs discussion, owner thinking (2026-11-09):

- When the phone presses for you: a pump in combat can go straight on to damage in Quick (STK-036). Proposal: after you act, your phone doesn't pass in that step until you press; Quick still skips steps where you did nothing.

Arcanum copies, found building copiable values (2026-11-09; can be done any time):

- A token copy of a double-faced permanent that copies a multi-part card (a Room, a prepare creature) isn't double-faced (CR 707.8a): `CopiableValues` has one layout, so it can't hold two faces each with their own parts. The token has the copied layout; what it lacks is transforming between two identical faces. Holding it needs a layout per face.
Parser report decisions (owner, 2026-11-09, relayed by session async-tt-wiz-74), queued after the engine items; not built ahead of them without asking the owner:

- Support moves set by set (LOB entries on support, DRF-161, DRF-162, to be named when built): today `AutomatumSupport` treats any card the parser reads as fully supported, about 14,000 of them unaudited, and `supported-sets.txt` is used only by tools. Wanted: a card counts as supported if it is in a listed set, by any printing, or on a list of individually supported cards: a general, easily edited mechanism for marking single cards, not a cube special case, which has to reach the app. First use: Caleb Gannon's Powered Synergy Cube, which the owner and a friend want to play: `~/projects/atw-cache/arcanum-cube-cards.json` (500 distinct names; the list goes by name, so duplicate lands need nothing), minus Ineffable Blessing and Mons's Goblin Waiters, plus Enduring Innocence and Marwyn, the Clearcutter, also 500 names. Both additions are in supported sets; 432 of the rest go on the list. Compare with the owner's own edited prod cube when this is built, in case they change anything else. Run the real support check (`AutomatumSupport.handParts`) on the whole list: reading isn't playing fully, and anything it finds goes to the owner. Support by set doesn't ship before the cube's cards are on the list. Built in step 1a: the list (`arcanum-tools/src/main/resources/arcanum/tools/supported-cards.txt`, the 432) and the catalog's marks, which the server's set pods follow; `handParts` found nothing on the 500 cube cards. The app reads the marks (step 1b, built). Still to do: the comparison with the owner's prod cube, and the games and drafts already holding such cards. This changes what players can use: catalog entries change and e2e covers it. Check how a game or a draft already holding such cards behaves, and raise that with the owner rather than deciding.

Old forms only read now, found at the wipe (2026-11-09; read, not run; can be done any time after the wipe release):

- `EngineEventText.hidden`'s line-only branch (a rewind's own line start): no game holds such an event after V74 and no caller found makes one. `GameDevice.finish` and `Authority.make` don't refuse one, though: a draft sent empty while a line is due would make it, so refuse that first. Its test `anOldRewindLineStartIsShownNowhere` names HIS-086, whose entry stays as it is; agree with the owner how that test goes.

Playtest notes (owner, 2026-11-09), queued after the two running lanes:

- Tokens in Inspect (TBL-104, TBL-018; owner): Inspect shows a token's picture as it is, with normal corners, so the cut corners no longer hide its P/T and text; the battlefield keeps the cut corners.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Intermittent (2 full runs, passes alone 3/3): e2e `PodReopenTest.aDraftingPodOpensAfterConnectionFailures` timed out "waiting for the pick unconfirmed" with Alice on the pod showing "Can't reach the server · retrying" and "All your picks are done" while Bob still had 3 packs. Investigate when it recurs: whether the unconfirmed pick is shown before the retry finishes, or the test's network hold releases too early.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

## Needs discussion

- "Can't do" explainer (owner, 2026-11-08; TBL-118 today says nothing by design): a short-lived chip or bubble rising from a shaken card, saying in one plain line why ("Costs more than you can pay"). Maybe for every shake, starting from castability's unselectable targets. Where it shows and how long are undecided; the engine's refusal reasons are technical, so the wording needs its own step. Also (report 82PG8P): warn, never block, before paying for an ability that would do nothing right now (Frodo's first ability when he is no longer a Citizen; legal, checked when it resolves, and the type may still change in response).

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as what you did comes out the same on top of their real opening (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority. Spec draft for the owner's review: `docs/specs/first-turn-during-mulligans.md` (2026-11-08).

- Choose a card's printing (art) in decks and cubes.
