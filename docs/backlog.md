# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-06 (owner: delegate the whole batch; at most two workers build at once, WSL memory): worker 1 = combat band, touch feedback, tap anywhere to watch, fewer waits; worker 2 = inspect shows changes, Automatum panels, ability inspect; worker 3 (after one of them) = catalog finishing step, prepare deck basics, navigation keys.

## Ready to build

- Catalog import's last step (owner, 2026-11-06): rename "Finishing up — verifying catalog…" (it mostly builds search data, it verifies little) and speed it up: drop the token name search (no caller since hand-made tokens went, TBL-119) and build token choices only for what still uses them (dungeons). The card search index stays. Emulator smoke 2026-11-04: the step took 341 s / 843 s, token search + avatar choices ~176 s / ~586 s of it. No entry change beyond the label's test.
- Fewer waits on the other player (owner, 2026-11-06): their priority at your end step and right after you attack are always assumed at once, whatever Assume resolves says (nothing of yours follows before their next real answer, so an assumption there never takes anything back; their playback still stops there, HIS-095); your spell or ability on top and the priority after their blocks keep following the setting. Judging their pass on their real hand (as Arena does) is not done while they are away: it would show you they hold something playable. Changes HIS-096, TRN-070.
- Combat band as part of the table (owner playtest, 2026-11-06): the band is always there between the battlefields, collapsed to the height of one row of target chips, a cream table area (the old combat area's #f5f1df) with no white floating panel, empty outside declaring attackers. When attackers are declared, one chip per attackable target fades in: crossed swords icon, round avatar (the player's, or the card's art), name cut with … after a fixed number of letters; tapping a chip makes it the active target; holding it shows the full name and highlights the card or the player's life square while held. The band opens by itself when the first attacker is staged (no tap-to-open strip, no pulse); in playback it opens only when someone actually attacked. Open, the chips are the lane headers with each lane under its chip; life/loyalty/defense and the damage preview ("20 → 17") show under the chip inside the lane. Combat cards are exactly battlefield size (no 44 dp minimum). Changes CMB-001, CMB-007, CMB-012, CMB-013, CMB-014, CMB-028, CMB-042 and what refers to the strip or panel.
- Touch feedback on glowing cards (owner playtest, 2026-11-06): while a finger is down on a card that glows (cyan can-activate, violet target or blocker, red-orange can-attack) or on a combat target chip, the glow grows a little and brightens, so it feels pressable. Changes TBL-117, STK-047, CMB-028, CMB-034.
- Tap anywhere to watch (owner playtest, 2026-11-06): with "<name> has made moves · Tap to watch" showing, any tap on the table (battlefields, combat band, cards) starts the playback; holding a card still inspects; hand and buttons keep their own taps. Changes HIS-094.
- Prepare deck basics (owner playtest, 2026-11-06): remove the basic land −/+ steppers; basics are just cards, moved between Main and Side like any card (a limited-sideboard pod uses the basics added while building). Changes LOB-052, DRF-124, LOB-049.
- Navigation keys (owner playtest, 2026-11-06): on a phone that uses button navigation, the navigation bar (back, home, recents) shows on every screen except the game, which keeps hiding it (a swipe shows it); gesture navigation unchanged; the status bar stays hidden. New entry (SYS).

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
