# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

1. Countering a spell lower on the Stack (e2e scenario).

## Ready to build

(none)

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Countering a spell lower on the Stack: e2e scenario: Alice (Full control) casts spell A, keeps priority, casts B; Bob, at his pause with both on the Stack, counters A with a single-target counterspell; B resolves, A is countered. Variant: Bob offline, Alice's device assumes his passes and A and B resolve; Bob later counters A → branch and take-back (needs the branch fix above). With two or more legal Stack targets the Stack opens in place as its list with the legal entries glowing and tappable, if that is clean and possible; otherwise keep the docked picker (STK-044, STK-018). Screenshot test of the multi-target pick for the owner.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

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
