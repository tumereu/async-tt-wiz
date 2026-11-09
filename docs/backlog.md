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

- A player's first main phase was skipped, cause not found: in turn 13 of a beta7 game their phone's passes from main 1 to declare attackers reached the server 30.0 s after the main phase began (server 11:33:44.290 → 11:34:14.284), and they say they didn't press. It doesn't reproduce (`MainPhaseStaysTest`: the same race with the other player's End turn, then 35 s at main 1) and the device has no way of its own to do it, so what is left is a press they didn't register, made at 11:34:14 or (if the phone's uplink stalled) just as main 1 arrived. Nothing to build until it happens again: main-button presses are now in the app log with what the button said and the step, and a pass the device itself makes at your main phase is a warning (LOB-089). At the next report read those lines around the passes; a press line means the tap happened (then the Your move cue and the main phase undo point below are the answer), no press line with passes means a fault to fix; a pass at the player's own main phase with no mark on the record means the device did it.

Pod chat (owner, 2026-11-08; new entries; LOB-066, LOB-072, DRF-012), after the batches above:

- One chat per pod for its members, kept as long as the pod, with a chat button and unread dot on the pod screens; a notification on the Draft activity channel. In a pod game the chat panel has two tabs, Game and Pod. Pod messages show no bubbles on the table, but they do light the dot on the table's chat button, and a tab with a message you haven't seen is highlighted while you are on the other (dismissing a game message's bubble counts as seeing it). Needs server storage, its limits as a game chat's. No "Read" marks unless the implementor finds them plain for several members; list it under choices.

Needs discussion, owner thinking (2026-11-09):

- When the phone presses for you: a pump in combat can go straight on to damage in Quick (STK-036). Proposal: after you act, your phone doesn't pass in that step until you press; Quick still skips steps where you did nothing.

Arcanum by-hand leftovers and face-down gaps, found building special actions (2026-11-09):

- `Decision.Manual` still arises, always on a card `Engine.manualParts` names, so the card is unsupported, and is skipped and logged if ever played (`EngineRun`): the rest of a mana ability whose other step asks something (`Payment.finishManaAbility`; of every card read, Ilysian Caryatid and Rainbow Vale), mana paid on resolution with an X nothing defines (`resolution/Costs.payMana`; Azor the Lawbringer, Elenda and Azor, Hero of Leina Tower, Vigil for the Lost), and on no read card "its mana cost" paid on resolution, `Step.Manual` (the parser's dungeon room it can't read) and `CostPart.Manual`. Once those are carried out or refused, the type can go with a wipe.
- X of a morph or disguise cost isn't kept for that permanent's later abilities (CR 702.37f, 702.168e; Bane of the Living's shape: `Payment.finishSpecialAction`'s names end with the action, so a turned-up trigger reads X as 0). Found by reading, not run; the parser doesn't read that trigger's wording yet, so no supported card plays it wrong. Write a test first.
- A manifested or cloaked instant or sorcery card that an effect would turn face up is revealed and stays face down, with no turned-face-up trigger (CR 701.40g, 701.58g): `Effect.TurnFaceUp` turns it up.
- A permanent cast disguised, turned face up and later turned face down again keeps ward {2} (CR 708.2a: a plain 2/2): `Copiable.faceDown` reads the cast's `facts.alternative`, which stays. Found by reading; write a test first.

Arcanum foretell gaps, found building the mark table (2026-11-09):

- An effect that gives a foretell cost as a card becomes foretold (CR 702.143d; Ethereal Valkyrie: "It becomes foretold. Its foretell cost is its mana cost reduced by {2}"): `Effect.Mark` has no cost to give, so the card is cast only for a foretell cost it already has.
- A granted foretell whose cost depends on the card (Dream Devourer, Bohn: "Its foretell cost is equal to its mana cost reduced by {2}"): a fixed granted cost is kept as the card is foretold, but `Keyword.Foretell` can't say this one and these printed cards stay unread. Also CR 702.143a's "any foretell cost it has" for foretell granted later to a card already in exile: only the costs it had as it became foretold and its printed ones are read.

Arcanum copies, found building copiable values (2026-11-09; can be done any time):

- Static copy effects stay live, against CR 707.2c ("determined only at the time that effect first starts to apply"): `Copiable.staticCopiesOn` makes a fresh effect each time, so a permanent under one follows the original's later changes. Fixing it needs state recording, per affected object, the values as the effect first applied to it (add-only, a new field on `GameObject` or a list on `GameState`), found again by source and ability.
- A token copy of a double-faced permanent that copies a multi-part card (a Room, a prepare creature) isn't double-faced (CR 707.8a): `CopiableValues` has one layout, so it can't hold two faces each with their own parts. The token has the copied layout; what it lacks is transforming between two identical faces. Holding it needs a layout per face.
Parser report decisions (owner, 2026-11-09, relayed by session async-tt-wiz-74), queued after the engine items; not built ahead of them without asking the owner:

- Support moves set by set (LOB entries on support, DRF-161, DRF-162, to be named when built): today `AutomatumSupport` treats any card the parser reads as fully supported, about 14,000 of them unaudited, and `supported-sets.txt` is used only by tools. Wanted: a card counts as supported if it is in a listed set, by any printing, or on a list of individually supported cards: a general, easily edited mechanism for marking single cards, not a cube special case, which has to reach the app. First use: Caleb Gannon's Powered Synergy Cube, which the owner and a friend want to play: `~/projects/atw-cache/arcanum-cube-cards.json` (500 distinct names; the list goes by name, so duplicate lands need nothing), minus Ineffable Blessing and Mons's Goblin Waiters, plus Enduring Innocence and Marwyn, the Clearcutter, also 500 names. Both additions are in supported sets; 432 of the rest go on the list. Compare with the owner's own edited prod cube when this is built, in case they change anything else. Run the real support check (`AutomatumSupport.handParts`) on the whole list: reading isn't playing fully, and anything it finds goes to the owner. Support by set doesn't ship before the cube's cards are on the list. Built in step 1a: the list (`arcanum-tools/src/main/resources/arcanum/tools/supported-cards.txt`, the 432) and the catalog's marks, which the server's set pods follow; `handParts` found nothing on the 500 cube cards. Still to do: the app reading the marks (1b), the comparison with the owner's prod cube, and the games and drafts already holding such cards. This changes what players can use: catalog entries change and e2e covers it. Check how a game or a draft already holding such cards behaves, and raise that with the owner rather than deciding.
- Misreads are noted as found and another implementor checks them as a separate task. Known: Quarantine Field (one target slot, should be one target per isolation counter); "Create a Food token for each artifact sacrificed this way" reads the same as "for each creature" (a wildcard drops the word). Parser report sections 4.2 and 4.4. Found by swapping wordings, so the printed cards may still be right, and each needs checking against its printed text; a swap gave the same model for: The Eagles Are Coming! ("create two" vs "create a"); Call the Spirit Dragons, Ooze Flux, Simic Manipulator, The Earth Crystal, Thunderous Velocipede (`-1/-1` vs `+1/+1`).

Old forms only read now, found at the wipe (2026-11-09; read, not run; can be done any time after the wipe release):

- `EngineEventText.hidden`'s line-only branch (a rewind's own line start): no game holds such an event after V74 and no caller found makes one. `GameDevice.finish` and `Authority.make` don't refuse one, though: a draft sent empty while a line is due would make it, so refuse that first. Its test `anOldRewindLineStartIsShownNowhere` names HIS-086, whose entry stays as it is; agree with the owner how that test goes.
- The "card" kind in `CombatStackRules.identities`: `EngineProjection` puts only effects in the Stack, so a board from a game never has a card there; only that branch reads it, for states from before rules v35.
- `GameWire`'s `RETIRED` fields: no stored type has them, and rows written since R4d (2026-11-05) don't. Rows of games that never started survive V74 and may still hold them (`game_revision`, `game_inbox`), so first see that none are left, or wipe them with the owner's permission.

After the wipe release (owner, 2026-11-09):

- Opening asks for the bottom twice (owner problem report 5DDSKK, 2026-11-09; the owner: possibly only visual, no hotfix). After a second mulligan the engine asks for the bottom card at once, as CR 103.5 has it (bottoming is part of each mulligan), in the generic choice panel ("Put 1 card on the bottom of your library (CR 103.5)"); then Keep opens the Keep / Bottom screen again, because `AutomatumOpening.owed` counts mulligans taken, not cards still owed. OK there sends Keep and drops the second bottom (`EngineGame.keep` finds no bottom decision left), so the player keeps 6 as they should; the game goes on, but the second screen says a card is still owed. Only the free first mulligan was ever tested (`CatchUpPlaybackTest`). TRN-062 says the bottom screen opens on Keep, which the app doesn't do. Owner's choice (2026-11-09): Keep only keeps, and the bottom asked right after a mulligan uses the Keep / Bottom screen (STK-043), with TRN-062 reworded; Keep and Mulligan refused while that bottom is open. e2e first: two mulligans, one card to the bottom, Keep goes straight to Ready with 6 cards. Log the opening fully (each mulligan, bottom and keep, with the cards).
- One rule for every game ending (TRN-065, LOB-046): nobody goes to the lobby until both players have pressed OK. A concession or an agreed result is still final and recorded at once and can't be undone; only the return to the lobby waits, so the conceder or acceptor stays at the table with "Waiting for <name> to acknowledge" and can leave for other screens as usual. Today they go straight to the lobby while the other player stays at the table, which may hide races: acting in the lobby (decks, leaving, deleting) while the other's dialog depends on the game, moves in flight when the concession lands, a second device, other paths into the next game.

Small, not urgent (owner, 2026-11-08):

- Connive at once (CR 701.50c; found building explore's order, 2026-11-09): several permanents told to connive at the same time connive in object order, through the `Step.ForEach` of `KeywordExpansions`; the first player in APNAP order who controls (or last controlled) one should choose which is next, as explore now does (CR 701.44d). Needs `Effects.connive`'s per-permanent path to run for every connive, with the choice in front of it.
- X stepper gaps (STK-052; found in castability's review, the owner told by session async-tt-wiz-74): a value that can't be paid (X=1 where a reducer starts applying at X=2) reaches the payment with Cast greyed and only Undo gets out, against "an earlier choice is never a dead end": grey Cast at the X step when `finishableWith` says false, or have the stepper skip those values. `HandCasts.mostAnnounced` stops after 20 failing values in a row, a bound that answers "no": when it runs out, leave the stepper open up to `top`, since the final check is exact.
- Opponent's modes and X on the Stack, shown and tested (STK-057, STK-018; test and screenshots only, no entry change): screenshots of the other player's modal spell and an X spell, each with the battlefield line and the Stack list, one gallery.tsv entry each; a unit test that the line and the Stack list show the chosen modes (X is already covered by StackLineTest). If the app doesn't show them as the entries say, report it.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

## Needs discussion

- **The card catalog on the server (owner, 2026-11-09): agreed, build steps 1a–1c of `docs/specs/web-client.md` (section 3).** One pinned MTGJSON file, trimmed at release and deployed with the server; changes only with a release; the app installs it before anything that reads card records, with those controls greyed ("Card records updating · 43%") and placeholders where content needs them. Removes the set upload limits (Reality Fracture, The List). First pin: MTGJSON's latest build. With support marks (section 3.8–3.9): the catalog says which cards and draft sets are supported; unsupported sets can't be selected in the draft set search, unsupported cards carry a mark over their art in deck building, card search, the visual deck editor and Inspect. Includes "support moves set by set" below, with its cube list. Step 1a is built (the pin, the server catalog with its support marks, set pods by code); 1b and 1c remain.
- "Can't do" explainer (owner, 2026-11-08; TBL-118 today says nothing by design): a short-lived chip or bubble rising from a shaken card, saying in one plain line why ("Costs more than you can pay"). Maybe for every shake, starting from castability's unselectable targets. Where it shows and how long are undecided; the engine's refusal reasons are technical, so the wording needs its own step. Also (report 82PG8P): warn, never block, before paying for an ability that would do nothing right now (Frodo's first ability when he is no longer a Citizen; legal, checked when it resolves, and the type may still change in response).

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens (Inspect now shows them with normal corners).
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as what you did comes out the same on top of their real opening (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority. Spec draft for the owner's review: `docs/specs/first-turn-during-mulligans.md` (2026-11-08).

- Choose a card's printing (art) in decks and cubes.
