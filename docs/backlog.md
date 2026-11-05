# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

1. Combat band.
2. Battlefield placement (Arena style).
3. Hold pauses playback, Reconnecting…, remove the auto-pause switches (re-check against the built catch-up playback first: HIS-094, HIS-095).
4. Countering a spell lower on the Stack (e2e scenario).

## Ready to build

(none)

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Build on the engine path (owner, 2026-11-05): holding a card pauses timeline playback (HIS-017, TBL-105), and "Reconnecting…" after a game action has waited 3 s for the server (SYS-016); no entry change.
- Remove the auto-pause switches, which nothing reads since the engine switch-over (owner, 2026-11-05): delete HIS-024, HIS-025, HIS-026, HIS-027, HIS-090; HIS-028 and LOB-082 keep only History playback pause, Rewind pause and Card move speed.
- Combat band (changes CMB-001, CMB-007, CMB-033, CMB-040, STK-044, TBL-117): the combat area opens from the centre as a band between the battlefields, pushing them apart and scaling with the battlefield zoom like the old yellow area (TBL-001), not a panel floating over them. While no attacker is staged it stays a thin strip that pulses; tapping it opens it, and so does staging the first attacker (planeswalker lanes cost that extra tap; judge in playtest). While attackers are being declared, the creatures that can attack glow red-orange (cyan stays "can activate"). Assume blockers is edited on the lanes and divisions of combat damage open in the band; the go-on actions (Attack (N), No blocks, Assume blockers, OK) are the main button (TRN-063, owner 2026-11-05). No panel over the hand during combat. Screenshot tests for the owner's review.
- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Countering a spell lower on the Stack: e2e scenario: Alice (Full control) casts spell A, keeps priority, casts B; Bob, at his pause with both on the Stack, counters A with a single-target counterspell; B resolves, A is countered. Variant: Bob offline, Alice's device assumes his passes and A and B resolve; Bob later counters A → branch and take-back (needs the branch fix above). With two or more legal Stack targets the Stack opens in place as its list with the legal entries glowing and tappable, if that is clean and possible; otherwise keep the docked picker (STK-044, STK-018). Screenshot test of the multi-target pick for the owner.
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
