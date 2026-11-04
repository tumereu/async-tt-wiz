# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- E2E framework (owner, 2026-11-04, AGENTS "End-to-end coverage"): build `e2e/` as a JVM full stack — real server + Postgres, two app instances running the real app code over real sockets, UI driven via Compose tests; move the interim two-device app-sim regression tests (EngineCoreLoopTest, EnginePlaybackStopTest, SameDecisionTapTest, StackAbilityScenarioTest, EnginePassesAndUndoTest, EngineCatchUpPlaybackTest…) into it; one test per owner-reported bug so far; full run = prod release gate, the two-emulator smoke (`scripts/smoke-emulators.sh`) its short shrunk-build half (no entry changes).
- Analysis (owner, 2026-11-04): adversarial review of the event-based state changes on device and server — undo/rewind, branches, assumptions, re-made and same-decision actions, anything cached per line/event/decision id (autotap plans, early answers), line reseeding — for inconsistency bugs like "cast → undo → recast refused" (decision ids include the engine change-log size; the new line's reshuffle rides in the next event); reproduce each in e2e, propose simplifications (stable decision identity) before building them.
- Inspect shows every face (owner, 2026-11-04; refined 2026-11-05): Inspect opens only by holding a card (or an ability's entry on the Stack) and shows only card images: a card's full image, every face of a multi-face card (double-faced, flip, split, adventure…) — portrait: stacked vertically, landscape: side by side; a finger drag scrolls — with no oracle text block and no lines on why Automatum refuses something (TBL-118's Inspect sentence goes; refusals are shown by the shake and the event text); a Stack ability (a triggered or activated ability's entry) shows the same way: first a card-shaped tile of the same size with the source's name, the ability's text and its chosen targets/modes, then its source card's image(s), in the same stack/side-by-side scroll (Claude's proposal, owner to confirm in playtest). Everywhere hold-to-inspect exists (table, hand, Stack, piles, draft, deck builder, search, card lists). "Inspect" leaves every menu (TBL-116, TBL-107, CMB-003, ZON-058, DRF-062, DRF-102, LOB-048 and the like) and "Other side" leaves the card menus (TBL-115).
- Declaring attackers (owner, 2026-11-04): while you choose attackers no abilities can be activated — taps only stage/unstage (CMB-028, as now; one-tap activation TBL-122 never applies there); Full control gives a stop at the beginning of combat for actions before attacks; the attack target choice lives only in the declaration UI (the combat area's lanes), so "Attack →" leaves the battlefield card menu (TBL-115, CMB entries).
- Fix (owner bug, 2026-11-04, report YRVZNM, 0.1.12): after a spell of connection failures (Pi tunnel dropping, 12:47–12:50 UTC: `/v1/pods/<id>` timeouts and "Retrying after PodFailure"), tapping a drafting pod on the Pods list did nothing and the app stayed on the list, even after the connection came back and with the pod fine on the server; only a force stop helped. Find the stuck state, fix it, and add an e2e test that fails before the fix (pod loads failing, then recovering → the pod opens). No catalog change (DRF-012 already expects the pod to open).
- Opponent's choice shown (owner, 2026-11-05): while the other player makes a choice (nobody holds priority: resolving, targets/modes, blockers, a mulligan), the event text (HIS-075, HIS-076) shows "<name> is choosing…" in TRN-066's words, without fading, until the choice is made; anything you try meanwhile flashes that text briefly in a warning colour together with the usual shake (TBL-118). The same on both devices; STK-032 loses its "Automatum is waiting for a choice" message (refusals say nothing, I-D46 b). Shown even with the Event text switch off (LOB-081), as it is a status, not an event.

## Specified, not started

Spec `docs/specs/engine-responsiveness.md` (architecture check, 2026-11-04), in this order:
- R6 (after R2): server refreshes only the reporting seat on `EngineSeen`, warm capacity from a measured heap, fewer round trips when opening a game (no entry changes).
- R7 (any time, docs only): close out the built specs `engine-state-architecture.md` and `async-play.md`, keeping only the choices still waiting for the owner.

## Needs discussion

- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
