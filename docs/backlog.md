# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

Running: End turn engine speed (worker D). Gallery round (owner, 2026-11-06): worker E = combat and table items; worker F = question panels + information-rule button; worker G = timeline separators + undo points + Undo dialog, then emblem text.

## Ready to build

- End turn engine speed (owner, 2026-11-06): End turn takes ~660–700 ms on the owner's phone, ~490–540 ms of it in the engine (report KWKB3Y, "Own action what=endTurn"). Profile End turn and step passes on a realistic board (JVM benchmark/profiler; desktop numbers relative), find the biggest costs (e.g. recomputing layers at every step) and take the easy gains, keeping behaviour identical (all arcanum/protocol suites green); report before/after numbers. R5 (precomputing passes while idle) stays a later option, not now.
- Name a card (owner gallery review): a search field autocompleting every known card, cards in your own library first (marked with a card-stack icon), then cards seen this game (an eye icon), then all; no free text (CR 201.3); restrictions in the effect ("a nonland card") filter the list. Changes STK-041.
- Numbers and X (owner gallery review): the number area keeps a fixed width for up to 4 digits; tapping it opens a numeric input for any value. Also X in the cast panel. Changes STK-023, STK-030.
- Choose a player shows avatars (owner gallery review, bug): the player choice shows the players' avatars, not generic icons. E2e/screenshot first. Changes nothing.
- Drag ordering (owner gallery review): trigger order and which replacement applies first are both drag-to-reorder lists with handles, opening in a suggested order, then OK; for replacements the order is a preference list: applied in order, skipping ones no longer applicable, asked again only if a new one appears (CR 616.1). Changes STK-030 and the order/replacement entries.
- Legend rule shows every card (owner gallery review, bug): the legend rule panel shows all permanents with the same name to pick the one to keep (the screenshot shows one). E2e/screenshot first.
- Pay while resolving (owner gallery review): with Autotap on the panel opens with Autotap's sources already picked (changeable), off nothing picked; declining ("Don't pay") is always possible where the effect allows it (bug: the screenshot has no decline). Changes STK-049.
- No "plays by hand" (owner gallery review): wording for decks/cubes with unsupported cards becomes "Automatum can't play 3 cards yet: A, B, C"; a cube or set with unsupported cards can't be drafted (e2e if missing). Changes LOB-115, LOB-118 and draft entries.
- Information-rule Assume (owner gallery review): where assuming would show you hidden information or a random result (TRN-067, TRN-071), the wait's Assume button shows greyed; holding it says why ("Assuming this would show you hidden cards or a random result: wait for <name>."). E2e + gallery item. Changes TRN-067, TRN-070, TRN-071.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

(none)

## Needs discussion

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens; inspect currently clips a token's borders.
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
