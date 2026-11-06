# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-06 (owner: delegate the whole batch; at most two workers build at once, WSL memory): worker 1 = combat band, touch feedback, tap anywhere to watch, fewer waits; worker 2 = inspect shows changes, Automatum panels, ability inspect; worker 3 (after the first of them merges) = catalog finishing step, prepare deck basics, navigation keys, ability text and tap symbol, cast panel title, no timeline strip; worker 1 follow-up after its merge = damage division panel.

## Ready to build

- Catalog import's last step (owner, 2026-11-06): rename "Finishing up — verifying catalog…" (it mostly builds search data, it verifies little) and speed it up: drop the token name search (no caller since hand-made tokens went, TBL-119) and build token choices only for what still uses them (dungeons). The card search index stays. Emulator smoke 2026-11-04: the step took 341 s / 843 s, token search + avatar choices ~176 s / ~586 s of it. No entry change beyond the label's test.
- Prepare deck basics (owner playtest, 2026-11-06): remove the basic land −/+ steppers; basics are just cards, moved between Main and Side like any card (a limited-sideboard pod uses the basics added while building). Changes LOB-052, DRF-124, LOB-049.
- Navigation keys (owner playtest, 2026-11-06): on a phone that uses button navigation, the navigation bar (back, home, recents) shows on every screen except the game, which keeps hiding it (a swipe shows it); gesture navigation unchanged; the status bar stays hidden. New entry (SYS).
- Ability text and tap symbol (owner playtest, 2026-11-06): abilities read cost first, then effect, as on a card ("{2}{G}, {T}: Draw a card") in chips, menu rows, the cast panel's ability line and hold texts; {T} is drawn as a turning-arrow icon instead of a "T" badge, {Q} as the same arrow mirrored. Changes TBL-124, ZON-080 and entries quoting ability texts.
- Cast panel title (owner playtest, 2026-11-06): "Cast <card>", "Play <land>", "Activate <source>" (e.g. "Cast Storm Spark", "Activate Ember Herald") instead of "Cast a spell" / "Play a land" / "Activate an ability"; other choice panels keep unnamed titles ("Scry 2"). Changes STK-023, STK-044 (the cast panel names its card).
- Damage division panel (owner playtest, 2026-11-06): each row's damage shows as a chip with the number, red with ☠ when lethal (deathtouch: 1 is lethal), grey when not, instead of "lethal ☠"; with trample a last, unmovable row for the attacked player, planeswalker or battle with its own damage chip; the dealing creature at the panel's top left as a card with its power and chips for the keywords that change damage (deathtouch, trample, lifelink, infect, wither, first/double strike); title stays "Divide N damage"; In order / Free stays. Screenshot tests with trample and with deathtouch. Changes CMB-033, STK-033.
- No timeline strip (owner playtest, 2026-11-06): the bottom bar has no event strip in live play or playback, its room goes to the board; timeline mode opens from Table actions → Timeline only (tapping an event for its text goes with the strip); timeline mode itself unchanged. Changes HIS-001, HIS-002, HIS-003, HIS-004 and entries naming the strip (HIS-058, HIS-091, HIS-101, HIS-094, TRN-010 …).

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

(none)

## Needs discussion

- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
