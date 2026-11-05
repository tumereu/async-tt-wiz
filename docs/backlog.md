# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

1. Quick fixes batch 2 (running): Stack-ability hold flake, TRN-060, TRN-046, copy-token art, YRVZNM.
2. Catch-up playback + main button + assumptions by setting (with the "Rest → <name>" label fix).
3. Analysis (undo/branch/assumption review), then the Undo item.
4. Choice panels show their source card.
5. Combat band.
6. Battlefield placement (Arena style).
7. Hold pauses playback, Reconnecting…, remove the auto-pause switches (re-check against step 2 first).
8. Countering a spell lower on the Stack (e2e scenario).

## Ready to build

- Analysis (owner, 2026-11-04): adversarial review of the event-based state changes on device and server — undo/rewind, branches, assumptions, re-made and same-decision actions, anything cached per line/event/decision id (autotap plans, early answers), line reseeding — for inconsistency bugs like "cast → undo → recast refused" (decision ids include the engine change-log size; the new line's reshuffle rides in the next event); reproduce each in e2e, propose simplifications (stable decision identity) before building them.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Catch-up playback, the main button, assumptions by setting only (owner, 2026-11-05; spec `docs/specs/catch-up-playback.md`): live / catch-up playback (looks live, centre "Tap to watch", Auto-skip + a Full control toggle, live at stops, tap pauses) / read-only timeline; Resolve, Assume and Assume blockers move into a wider main button in the step arrow's place; the other player's presence no longer decides assuming, only "Always assume resolves" (changes HIS-006, HIS-008, HIS-013, HIS-035, HIS-094, HIS-095, HIS-096, HIS-103, HIS-104, HIS-105, HIS-106, STK-027, STK-028, STK-029, STK-036, STK-037, TRN-016, TRN-059, TRN-063, TRN-066, TRN-070, CMB-040).
- Build on the engine path (owner, 2026-11-05): holding a card pauses timeline playback (HIS-017, TBL-105), and "Reconnecting…" after a game action has waited 3 s for the server (SYS-016); no entry change.
- Remove the auto-pause switches, which nothing reads since the engine switch-over (owner, 2026-11-05): delete HIS-024, HIS-025, HIS-026, HIS-027, HIS-090; HIS-028 and LOB-082 keep only History playback pause, Rewind pause and Card move speed.
- Combat band (changes CMB-001, CMB-007, CMB-033, CMB-040, STK-044, TBL-117): the combat area opens from the centre as a band between the battlefields, pushing them apart and scaling with the battlefield zoom like the old yellow area (TBL-001), not a panel floating over them. While no attacker is staged it stays a thin strip that pulses; tapping it opens it, and so does staging the first attacker (planeswalker lanes cost that extra tap; judge in playtest). While attackers are being declared, the creatures that can attack glow red-orange (cyan stays "can activate"). Assume blockers is edited on the lanes and divisions of combat damage open in the band; the go-on actions (Attack (N), No blocks, Assume blockers, OK) are the main button in the step arrow's place (`docs/specs/catch-up-playback.md` §4, owner 2026-11-05). No panel over the hand during combat. Screenshot tests for the owner's review.
- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change).
- Fix: the "Rest → <target>" quick action names the defending player as "the player" instead of their name ("Rest → Bob"); found by an implementor, owner OK 2026-11-05 (CMB-048; no entry change). The "declares the attack" item above did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Undo, AFTER the Analysis item above (prerequisite: undo logic may hide more bugs): (1) an undo applies its whole result at once, then animates once from the start board to the end board, with no in-between texts, boards, step-button or quick-action states; (2) an Undo tapped while your own action is still being worked out (e.g. right after Attack) waits for it and then takes it back, instead of being refused (owner problem report on 2026-11-05: attack then quick undo). Changes HIS-084/HIS-086 (add entries for both).
- Countering a spell lower on the Stack: e2e scenario: Alice (Full control) casts spell A, keeps priority, casts B; Bob, at his pause with both on the Stack, counters A with a single-target counterspell; B resolves, A is countered. Variant: Bob offline, Alice's device assumes his passes and A and B resolve; Bob later counters A → branch and take-back (needs the branch fix above). With two or more legal Stack targets the Stack opens in place as its list with the legal entries glowing and tappable, if that is clean and possible; otherwise keep the docked picker (STK-044, STK-018). Screenshot test of the multi-target pick for the owner.
- Choice panels name their source by the card, not by text: titles say only the choice ("Choose a target"), and the source sits at the panel's right as an inspectable card (an ability as its Stack ability token, STK-046), the same place in every Automatum panel and target bar, the cast panel included (changes STK-023, STK-030, STK-044). Screenshot tests.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

- Battlefield placement, MTG Arena style (owner, 2026-11-05; spec `docs/specs/battlefield-placement.md`): automatic rows (creatures nearest the middle, lands and other permanents in the back row), identical permanents stacked (lands fanned, tokens ×N, tapped members rotate inside), attachments and mutate behind their card, no dragging on the battlefield; a tap on a stack or a card with attachments acts at once when it can mean only one thing, else the first tap expands the group and the second picks; the group's glow colour tells which a tap will do (changes TBL-002, TBL-003, TBL-004, TBL-006, TBL-007, TBL-008, TBL-012, TBL-053, TBL-054, TBL-101, TBL-102, TBL-111, TBL-117, TBL-121, TBL-122, TBL-124, SYS-041, SYS-074, ZON-077, STK-032). Screenshot tests for the owner.

## Specified, not started

(none)

## Needs discussion

- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
