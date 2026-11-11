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

Playtest and gallery notes (owner, 2026-11-11): one batch right after web-client step 2 (shared UI module), before the library upgrade step and web step 3. The web wireframes show the same changes.

- Attack and block costs (CMB-044): a cost with an X the board defines ("pays {X} for each creature attacking you, where X is …") shows its amount on the chip ("{3}"), worked out again whenever the staging or the board changes it, never the letter X; the defender sees the same. New gallery entry for the cost chips (none exists).
- Mix sets (DRF-177, DRF-178, DRF-032): the set button itself opens today's set list; the pen icon beside it becomes a toggle between one set and mixed packs. One set: the chosen set (or cube) fills every pack, also packs added later. Mixed: the pack grid shows, its cells chosen one by one as today. Back in one set after mixing, the button says "Mixed" until a set is chosen there, which then fills every pack. Cubes have no mixed mode.
- Drafted cards' printings (DRF-090, the pool, Build and the saved deck): a set pod's cards are printings of the pack's own set: the printing its booster recipe drew (variants of that set included, so the same card may differ in art), and in an approximate pack one of that set's printings of the card at random; never another set's printing. Cube pods keep the cube's printings.
- Draft history directions (DRF-165, DRF-043): round headers name where the packs went ("Round 1 · passed to Bob"), with "clockwise" / "counter-clockwise" only with three or more players; a seat ring of the pod's avatars at the top of the history shows the seating with the round's direction as an arrow.
- Draft history's "Returning packs only" chip (DRF-165) is remembered per pod on the device.
- Main button icons and destinations (owner gallery note 22; TRN-063, STK-029, whose play icon for Resolve the app doesn't show today): an icon per kind (play for Resolve, swords for To combat / Attack, shield for Block, a turn arrow for End turn, a tick for OK) and step moves naming where they go ("To draw step" for Next, "To end step" for Pass). Owner judges it in the gallery and in a game; it may change after.
- "Your move" sound (owner; Android now, the web client gets it too): a short soft sound ("plip") with TRN-075's cue, whenever the move passes to you and you must press. Settings → a "Your move sound" row: Off and a few sounds, each with a play button to listen; kept on the account; on by default (owner). Plays while the game's table is open (on the web also in a background tab). Our own sounds, made for us (no copied game sounds).

Queue (owner, 2026-11-09 evening): after the two running lanes (catalog step 1a; game ending + 5DDSKK), in any order: the 2026-11-09 playtest notes, the P2XUER/3WWPA8 bug, the small items, the Arcanum gap lists (by-hand leftovers, foretell, copies), the misread check, the old-form cleanups, tokens in Inspect, and the two items waiting on a repro (investigate). The evidence selection's running total is not wanted for now (owner).

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
- Intermittent (1 full run 2026-11-11 on c0de1683a, passes alone): e2e `CardStatesTest.aStateShowsOnTheCardAndOnThePlayerOnBothTables` hung after Alice played her Mountain: neither table's Compose ever went idle again ("did not get idle after 9021621 attempts in 60 SECONDS" on Bob's), and Robolectric's `ShadowTrace` history filled the heap (OutOfMemoryError, 244 s). Like CombatHoldTest's "performMeasureAndLayout called during measure layout" (2026-11-09). Investigate when it recurs: what keeps requesting frames or layout.
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
