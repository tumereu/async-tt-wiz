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

- Short phones (found 2026-11-12, C2): with the combat band open the other player's land row stays fully visible only on screens about 845 dp tall or more; shorter phones still lose part of it. Owner to say whether a shorter phone matters (their device's height decides).
- Client crash-safety pass (owner, 2026-11-11, after the WCRF3Q crash; right after the queued fixes, before the library upgrades): every `!!` (about 130 in app-ui, 27 in automatum and protocol, 48 in client-storage's Android side) and every throwing map lookup (`getValue`) in client main code either becomes provable by the types (reshape the data so the compiler knows) or gets a plain fallback that logs an error and reports it automatically (`Faults.placeholder`); behaviour otherwise unchanged. Then a build check: a new `!!` in client main code fails the build unless allow-listed with a reason. Split into fresh workers per module if large.
- Crash-safety leftovers (2026-11-11, from the client pass): `!!` are gone from client code (checkClientNotNull), but not every throwing lookup was reviewed: app-ui's `BasicTableController`, `TablePresentation.cardForActions` and the `players.single` calls (these also assume two players; fix them for 3+ with the Commander work) and other `first()`/`single()`/`getValue`/`error()`/`check()` reachable from UI state. The table scene's own fallback keeps drawing safe meanwhile. Review them with the same rule (provable by types, or fallback + `Faults.report`).
- Server and engine non-null hygiene (owner, 2026-11-11, lower priority): jOOQ code generation makes NOT NULL columns non-null types, removing most of the server's ~440 `row.get(...)!!`; the engine's ~107 `!!` are read in the owner's engine review (Needs discussion), not swept blindly.

Queue (owner, 2026-11-09 evening): after the two running lanes (catalog step 1a; game ending + 5DDSKK), in any order: the 2026-11-09 playtest notes, the P2XUER/3WWPA8 bug, the small items, the Arcanum gap lists (by-hand leftovers, foretell, copies), the misread check, the old-form cleanups, and the two items waiting on a repro (investigate). The evidence selection's running total is not wanted for now (owner).

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

Playtest notes (owner, 2026-11-12): before web step 3 (after the engine API rework). The web wireframes show the same changes.


## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Cost phrase highlight (STK-047; owner 2026-11-12, Demonmail Hauberk's "Sacrifice a creature" while picking the sacrifice): didn't reproduce on 2026-11-12 (the parser's spans and the pick bar light "a creature" as STK-047 says). Owner 2026-11-13: wait until it happens again; it may have been a misreading of the UI or a UX problem that further playtesting shows.
- Intermittent (2 full runs, passes alone 3/3): e2e `PodReopenTest.aDraftingPodOpensAfterConnectionFailures` timed out "waiting for the pick unconfirmed" with Alice on the pod showing "Can't reach the server · retrying" and "All your picks are done" while Bob still had 3 packs. Investigate when it recurs: whether the unconfirmed pick is shown before the retry finishes, or the test's network hold releases too early.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

## Needs discussion

- Owner's own review of the Arcanum engine (owner, 2026-11-11): the owner reads the engine code by hand and discusses improvements with Claude, looking for things that slipped in while unreviewed, like the per-creature `Engine.combatCost`. The public API part was discussed on 2026-11-12 and settled in the engine API rework (merged 2026-11-12). Still open: reading the engine's code itself, including its ~107 `!!`. Nothing there is changed before that discussion.


- "Can't do" explainer (owner, 2026-11-08; TBL-118 today says nothing by design): a short-lived chip or bubble rising from a shaken card, saying in one plain line why ("Costs more than you can pay"). Maybe for every shake, starting from castability's unselectable targets. Where it shows and how long are undecided; the engine's refusal reasons are technical, so the wording needs its own step. Also (report 82PG8P): warn, never block, before paying for an ability that would do nothing right now (Frodo's first ability when he is no longer a Citizen; legal, checked when it resolves, and the type may still change in response).

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

## Later (nice to have)

- Automatic answers (owner wishlist, 2026-11-12): remembered answers to "you may" choices (always yes / always no per ability), perhaps for other repeated choices too, like the remembered trigger orders below.

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as what you did comes out the same on top of their real opening (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority. Spec draft for the owner's review: `docs/specs/first-turn-during-mulligans.md` (2026-11-08).

- Choose a card's printing (art) in decks and cubes.
