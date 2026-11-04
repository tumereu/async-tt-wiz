# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Fix (owner bug, 2026-11-04, report YRVZNM, 0.1.12): after a spell of connection failures (Pi tunnel dropping, 12:47–12:50 UTC: `/v1/pods/<id>` timeouts and "Retrying after PodFailure"), tapping a drafting pod on the Pods list did nothing and the app stayed on the list, even after the connection came back and with the pod fine on the server; only a force stop helped. Find the stuck state, fix it, and add an e2e test that fails before the fix (pod loads failing, then recovering → the pod opens). No catalog change (DRF-012 already expects the pod to open).

## Specified, not started

Spec `docs/specs/engine-responsiveness.md` (architecture check, 2026-11-04), in this order:
- R1: measure an own action properly: the probe splits engine work, information rule, board building, frame shown and the computed board's arrival (SYS-030).
- R2: work out the layers once per state: the engine hands back / accepts a view, `GameModel` keeps view and visible sets (no entry changes).
- R3: build the board once, on the engine thread: no repeated board, no repeated tick searches, computed values and plays published with the board instead of rebuilt from `PlayState` (SYS-025 holds again).
- R4: a render model instead of `PlayState`, then delete the old model (engine-state-architecture §14 items 1–5; SYS-004 and SYS-010 go (the old model's other text/batch entries are already gone); engine-path replacements for tests citing LOB-066, HIS-018–HIS-020).
- R6 (after R2): server refreshes only the reporting seat on `EngineSeen`, warm capacity from a measured heap, fewer round trips when opening a game (no entry changes).
- R7 (any time, docs only): close out the built specs `engine-state-architecture.md` and `async-play.md`, keeping only the choices still waiting for the owner.

## Needs discussion

- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
