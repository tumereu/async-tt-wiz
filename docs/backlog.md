# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

### Post-0.3.0 (owner, 2026-11-20)

Fixes and small changes:

- Special actions read like abilities: cost first, then ":", then what it does, in the card menu and wherever else they are named ("{2}{U}: Turn face up", "{3}{R}: Unlock Dusty Attic", "{2}: Foretell" instead of the full reminder sentence), for every special action (TBL-115).
- Special actions that don't use the Stack (unlocking a door, turning face up, foretell, plot, suspend …) show large in the middle of the board as abilities do, then fly into their own card instead of to the Stack, and the card does its short flash once the fly ends; one rule for all of them (HIS-112, HIS-031).

Features, in this order:

1. Hover sweep (desktop): everything that can be clicked (hand cards, Undo, the main button, Resolve …) answers the pointer with a hover look, beyond today's hand pointer (WEB-058; new entries).
2. A text log of the game's events, keeping the players' colours on names, targets and the like (HIS-091's texts; new entries).
3. Resizable panels: the large panels with scrolling content (the cast panel, choices) get a drag handle, with one size shared by all of them and remembered (STK-023, WEB-082; new entries).
4. Draft bots, in place of burns: drafters that pick by a simple heuristic (no outside pick data), plus a debug-build-only setting that shows the bot's score for each card and its pick on the draft screen, so the owner can draft as the bot and judge the deck (new entries).
6. Rules pass on set-work approximations: Wizard's Sanction, Sandyman, Arathorn, Berilac, Simic Manipulator, Oubliette and Dread of Abduction play exactly by the rules (no catalog entry; each fix with its rule test).

- Test reliability (internal; no catalog entry): the e2e harness's declare and pickBlocker taps don't wait for the main button's Automatum worker (StagingFromStackTest's declared-attackers case depends on it); and tests that fail under load but pass on a rerun: CastDroppedOnRewindTest, StableDecisionIdTest, TurnBannerTest, EmblemInspectTest, WebNotificationsTest (socket error to the local server) and the screen tests' "Compose did not get idle" (PodCubePreparationTest, CardListEditorUiTest, the desktop deck editor and table stack).

### Earlier

Desktop playtest notes (owner, 2026-11-14): high priority.

- Desktop battlefield cards at least twice today's size (the rest of the desktop default sizes is built, 2026-11-14): waits for the owner's choice, since at 4× the six battlefield rows don't fit a 1440 × 900 or 1920 × 1080 window even at the opening's 0.65× (TBL-121), so the table opens with both land rows out of view (gallery: desktop/table-battlefield-4x.png). Meanwhile, with the taller seat headers, hand band and top row, the 2× battlefield cards open smaller at 1440 × 900 than before (the rows fit: about 56 px tall, 72 before; at 1920 × 1080 about 78, 80 before). Options: the desktop opening shows your own battlefield whole (the other player's land row out of view); a lower least zoom on the desktop so the rows fit (cards then only as large as fit); fewer rows on the desktop; or the battlefield stays 2× (the Card size slider gives 4×) (WEB-022, TBL-121, TBL-010).
- Desktop timeline: hovering an event shows its text, as holding it does on the phone (HIS-011); not built (the mouse holds an event as a finger does). Left over from the web client spec.

Playtest and gallery notes (owner, 2026-11-11):

- Short phones (found 2026-11-12, C2): with the combat band open the other player's land row stays fully visible only on screens about 845 dp tall or more; shorter phones still lose part of it. Owner to say whether a shorter phone matters (their device's height decides).
- Crash-safety leftovers (2026-11-11, from the client crash-safety pass, built): `!!` are gone from client code (checkClientNotNull), but not every throwing lookup was reviewed: app-ui's `BasicTableController`, `TablePresentation.cardForActions` and the `players.single` calls (these also assume two players; fix them for 3+ with the Commander work) and other `first()`/`single()`/`getValue`/`error()`/`check()` reachable from UI state. The table scene's own fallback keeps drawing safe meanwhile. Review them with the same rule (provable by types, or fallback + `Faults.report`).
- Server and engine non-null hygiene (owner, 2026-11-11, lower priority): jOOQ code generation makes NOT NULL columns non-null types, removing most of the server's ~440 `row.get(...)!!`; the engine's ~107 `!!` are read in the owner's engine review (Needs discussion), not swept blindly.

Queue (owner, 2026-11-09 evening), in any order: the items below. The evidence selection's running total is not wanted for now (owner).


Arcanum copies, found building copiable values (2026-11-09; can be done any time):

- A token copy of a double-faced permanent that copies a multi-part card (a Room, a prepare creature) isn't double-faced (CR 707.8a): `CopiableValues` has one layout, so it can't hold two faces each with their own parts. The token has the copied layout; what it lacks is transforming between two identical faces. Holding it needs a layout per face.

Parser report decisions (owner, 2026-11-09, relayed by session async-tt-wiz-74), queued after the engine items; not built ahead of them without asking the owner:

- Support set by set, the rest (SYS-082 is built: the catalog's marks count a card as supported by a printing in a supported set or on the single-card list, `arcanum-tools/src/main/resources/arcanum/tools/supported-cards.txt`, first the owner's Powered Synergy Cube; the server's set pods and the app follow them). Still to do: compare the list with the owner's own edited prod cube, in case they changed anything else; and check how a game or a draft already holding cards that are no longer supported behaves, raising that with the owner rather than deciding. This changes what players can use: catalog entries change and e2e covers it.

Old forms only read now, found at the wipe (2026-11-09; read, not run; can be done any time after the wipe release):

- `EngineEventText.hidden`'s line-only branch (a rewind's own line start): no game holds such an event after V74 and no caller found makes one. `GameDevice.finish` and `Authority.make` don't refuse one, though: a draft sent empty while a line is due would make it, so refuse that first. Its test `anOldRewindLineStartIsShownNowhere` names HIS-086, whose entry stays as it is; agree with the owner how that test goes.

## Waiting for a repro or more detail (owner playtests, from 2026-11-05)

Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Cost phrase highlight (STK-047; owner 2026-11-12, Demonmail Hauberk's "Sacrifice a creature" while picking the sacrifice): didn't reproduce on 2026-11-12 (the parser's spans and the pick bar light "a creature" as STK-047 says). Owner 2026-11-13: wait until it happens again; it may have been a misreading of the UI or a UX problem that further playtesting shows.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).
- Parked until the owner has tested 0.3.0: the bubbles saying why something shook may get in the way; maybe the shake alone is enough (TBL-212).
- Parked until the owner has tested 0.3.0: a stack whose cards are all tapped counts "0/3" (TBL-054).
- Parked until the owner has tested 0.3.0: the desktop quick look works on every draft card (every mode's cards and the Picks dialog), not only the pack you pick from (WEB-117).

## Specified, not started

- Booster randomness check (owner 2026-11-13; lowest priority, after all other queued work, for peace of mind): a sealed DFT pod in prod ("Vroom vroooomm!", 9 packs) gave one pool Lifecraft Engine ×3, Demonic Junker ×2, Full Throttle ×2. The recipe and sheet weights were checked (MTGJSON play booster, rares weight 8, mythics 4, seed from SecureRandom through RandomV1's SHA-256 stream), and the pattern is about 1 in 230 for a pool of 12 rares, so nothing points at a bug. Check the real generator statistically anyway: generate about 100,000 DFT sealed pools (9 packs) with `SetBoosters.generate` from the pinned catalog's DFT recipe, test each sheet's card frequencies against its weights (chi-squared per sheet, rare-or-mythic split, wildcard and foil slot rarities), test independence between packs and slots (no correlation between consecutive draws), and report where the observed pool falls in the distribution of repeats. A JVM tool or test reading the local pinned MTGJSON file (no real card data committed); report the numbers to the owner in plain words. No code change unless the check finds a fault.

## Needs discussion

- Owner's own review of the Arcanum engine (owner, 2026-11-11): the owner reads the engine code by hand and discusses improvements with Claude, looking for things that slipped in while unreviewed, like the per-creature `Engine.combatCost`. The public API part was discussed on 2026-11-12 and settled in the engine API rework (merged 2026-11-12). Still open: reading the engine's code itself, including its ~107 `!!`. Nothing there is changed before that discussion.


- Warn before a useless ability (report 82PG8P): warn, never block, before paying for an ability that would do nothing right now (Frodo's first ability when he is no longer a Citizen; legal, checked when it resolves, and the type may still change in response).

- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains (board wipe, big turn changes, 40–80 ms on the Pi), and on a tentative line nothing may be shown before the whole outcome passed the information rule; if the own-action measurements (SYS-030) still show chains as the slow cases, a plain callback on `perform` handing over changes as they are recorded rather than a channel. Owner to decide.
- The step arrow's pass worked out ahead of time (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide on the own-action measurements (SYS-030).

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

- Rule references the compiler can check (owner, 2026-11-15; parked, to think about, not now): replace plain-text `// CR 613.4c` citations with annotations naming the rule number, store each cited rule's text or a checksum of it, and have a build job list which rules the code relies on and which of them changed when the Comprehensive Rules pin moves, so a renumbered or reworded rule can't go unnoticed (today's citations are comments the compiler doesn't check; the 2026-11-15 pin move uses a one-off citation delta tool in arcanum-tools instead).

## Needs design

- Stats colours (owner, 2026-11-19): separate stats for cast costs and for cast costs + abilities (colour identity), instead of today's one view by colour identity (DRF-230, DRF-117).
- Multicolour cards in the Stats colours (owner, 2026-11-19): show split colours, or add a multicolour card to each of its colours' stats, instead of one Multicolor bucket (DRF-230, DRF-117).

## Later (nice to have)

- Repeating a loop ("infinite" combos; owner 2026-11-14): record a sequence of actions as a macro and repeat it N times, with the engine handling large numbers (mana, life, damage, counters) and many tokens (a counted token pile instead of a million objects). **Don't build until properly specified** (owner: "needs more design").

- Automatic answers (owner wishlist, 2026-11-12): remembered answers to "you may" choices (always yes / always no per ability), perhaps for other repeated choices too, like the remembered trigger orders below.

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".

- Choose a card's printing (art) in decks and cubes.
