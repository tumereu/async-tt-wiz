# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

1. Playtest 2026-11-05 evening, batch A (cast flow): undo during an interaction, cast panel without OK/Cancel, every way to cast shown, ability chips + mana symbols, main button names.
2. Playtest 2026-11-05 evening, batch B: Stack ability look, library search panel, draft split-card pips, assume when they have no mana.
3. Battlefield placement (Arena style).
4. Hold pauses playback, Reconnecting…, remove the auto-pause switches (re-check against the built catch-up playback first: HIS-094, HIS-095).
5. Countering a spell lower on the Stack (e2e scenario).

## Ready to build

- Main button names (owner playtest 2026-11-05 evening): the main button has room for two lines; give each action a clear name: "Assume resolves" (something on the Stack), "Assume no response" (the other player may act with nothing on the Stack, e.g. at their end step), "Assume no blockers" / "Assume blockers" (combat band), and check the step names read clearly on two lines. Changes TRN-063, TRN-070.
- Stack ability look (owner playtest 2026-11-05 evening): inspecting an ability on the Stack shows the source's art panel with the ability's text under it, wrapped in a dark rounded rectangle (the current tile's look), not card-shaped; on the Stack the ability token keeps its look but drops its text and bubbles, and shows a small chip at its bottom: "T" for a triggered ability, "A" for an activated one. Changes STK-046, STK-050.
- Library search panel (owner playtest 2026-11-05 evening, a fetch land): the search shows the effect causing it (its source, as other choice panels do) and what is being searched ("Search your library for a basic land card"); the selection survives hiding and showing the panel with the eye; and the searched zone's other cards (not of the searched kind) show in a second, unselectable section ("Other cards"), which matters when searching an opponent's library. Changes the search choice's entry in turns-combat-stack.md (STK-030 or its own) and STK-044 if needed.
- Fix: draft Stats don't count both halves' pips of split cards: Road // Ruin's red pips were missing (DRF-114, DRF-116; no entry change). Check other multi-part cards (adventure, MDFC back faces per the existing rule, fuse, aftermath).
- Assume when they have no mana (owner playtest 2026-11-05 evening): "Always assume resolves" becomes a choice: Never, "When they have no mana" (the other player has no untapped mana source, no mana in their pool and no ability they could activate, judged on what the table shows), Always. Changes TRN-059, LOB-113, HIS-096.

(none)

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Build on the engine path (owner, 2026-11-05): holding a card pauses timeline playback (HIS-017, TBL-105), and "Reconnecting…" after a game action has waited 3 s for the server (SYS-016); no entry change.
- Remove the auto-pause switches, which nothing reads since the engine switch-over (owner, 2026-11-05): delete HIS-024, HIS-025, HIS-026, HIS-027, HIS-090; HIS-028 and LOB-082 keep only History playback pause, Rewind pause and Card move speed.
- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Countering a spell lower on the Stack: e2e scenario: Alice (Full control) casts spell A, keeps priority, casts B; Bob, at his pause with both on the Stack, counters A with a single-target counterspell; B resolves, A is countered. Variant: Bob offline, Alice's device assumes his passes and A and B resolve; Bob later counters A → branch and take-back (needs the branch fix above). With two or more legal Stack targets the Stack opens in place as its list with the legal entries glowing and tappable, if that is clean and possible; otherwise keep the docked picker (STK-044, STK-018). Screenshot test of the multi-target pick for the owner.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

- Battlefield placement, MTG Arena style (owner, 2026-11-05; spec `docs/specs/battlefield-placement.md`): automatic rows (creatures nearest the middle, lands and other permanents in the back row), identical permanents stacked (lands fanned, tokens ×N, tapped members rotate inside), attachments and mutate behind their card, no dragging on the battlefield; a tap on a stack or a card with attachments acts at once when it can mean only one thing, else the first tap expands the group and the second picks; the group's glow colour tells which a tap will do (changes TBL-002, TBL-003, TBL-004, TBL-006, TBL-007, TBL-008, TBL-012, TBL-053, TBL-054, TBL-101, TBL-102, TBL-111, TBL-117, TBL-121, TBL-122, TBL-124, SYS-041, SYS-074, ZON-077, STK-032). Screenshot tests for the owner.

## Specified, not started

(none)

## Needs discussion

- Catalog import's "Finishing up — verifying catalog…" step is slow (owner, 2026-11-05: why verify at all?). Found: it mostly isn't verifying; it counts records, then builds the card search index, token search, avatar choices and token choices (CatalogDatabase.stage). Options: build these during indexing, build search lazily, or only relabel. Decide with the owner.
- Which points need an assumption (owner, 2026-11-05): the other player's end step needs "Assume resolves" today, which felt excessive. Review every point where the other player gets priority and what Arena does at step starts/ends (tied to Full control?); maybe part of the finer assume setting.
- Inspect richness (owner, 2026-11-05): besides card images, show counters, temporary effects and other modifications, and attached cards (equipment, auras) as separate cards in the scrolling view, keeping hold-to-inspect / release-to-close and single-tap-to-activate; or a gesture (drag sideways) for a modifications overlay or a pinned inspect. Decide with the owner.

- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
