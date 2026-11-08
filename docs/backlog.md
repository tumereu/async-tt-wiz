# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-08, one at a time: (1) built: branch stop, landscape hand, View deck; (2) running: presets, set Redownload, Winchester tables, Stats mana sources; (3) catalog review follow-ups.

## Ready to build

Owner notes after beta5 (2026-11-08):

- Winchester tables (DRF-158, DRF-159, DRF-021–DRF-023): a "Tables" setting, default 1 (like Booster's packs open at once): the cards split into independent Winchester tables, each with its own stack and piles and turn order offset so players can pick at different tables at the same time. The pick screen has a chip row ("Table 1 · Your pick", "Table 2 · Bob picks") showing one table's piles at a time, opening on a table where it's your pick; piles keep following "Draft · cards per row".
- Stats mana sources (DRF-114, DRF-115): sources come from Arcanum's parsed mana abilities (CR 605) instead of text matching, counting nonland permanents too; rituals and Treasure makers are left out. Each colour row shows a bar, solid part lands and a lighter part other producers, with numbers ("7 lands + 2 others") beside the pips; a card producing several colours (one at a time, or a colour chosen as it enters) counts 1 for each colour it can make; a total line under the rows ("Sources: 17 lands, 3 others").

Catalog review follow-ups (owner, 2026-11-08; the catalog was cleaned on w/catalog-cleanup, these change code):

- Undo shown locally (SYS-025; the entry stays, the app is fixed): a free Undo shows its result on your table at once, before the server answers, as every other action does; today the app waits for the server's rewind (a queued undo about two round trips). A test shows the undone board while the server's answer is still held back (no timing asserts), plus e2e for the flow.
- Refusals shake (SYS-012, SYS-053, SYS-069, TRN-057): drop the "That wasn't taken: the game didn't accept it. …" note: such a refusal shakes what it was made on (or the main button) and the board updates. Check whether "The game changes only by the rules." and "The turn moves on through Automatum: use Next step or End turn." can still show; if so, a shake instead. The "moved on first" note stays.
- Combat drags (CMB-002, CMB-006, CMB-009, CMB-015, CMB-027): combat drags only stage attackers into a lane (CMB-028), retarget across lanes and place blockers (CMB-034). Check on an emulator what the hand-moved drags still do (new pairs at dividers, reordering pairs, swapping within a pair, a role change by dropping into the blocker row), remove their code, and remove those parts of the entries (CMB-006's "closing the area", CMB-002's "anything done to a copy's card").
- Dead card-placement path (internal, no entry change): the battlefield card place is gone from the catalog (nothing is placed by hand, SYS-069) but its code stays: TableChange.Placed, the same-battlefield MoveCard branch in EngineTable.local, the "moved a card" text and move icon in EngineHistory, its server handling and tests. Remove it.
- "My loss" leftover (internal, TRN-046 unchanged): ResultScreen.kt still offers "My loss" although Suggest game end offers only My win / Draw game; remove it, or the screen if nothing uses it.
- Single version (AGENTS.md): remove code that keeps anything older working (old stored fields decoding, old HTTP reads, the deck-save old retry, old handshake formats, old-data refreshes) and with it LOB-112, the "downloaded again once" parts of LOB-110 and LOB-125, LOB-113's reading of settings from before Normal, LOB-135's "Games from before these settings have the defaults" and DRF-079's "Pods started before this change keep plain rotation". Ask the owner before anything that deletes or invalidates existing games.

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
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
