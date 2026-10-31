# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Pods and games use one list order everywhere (screens, menu panels, side menu): yours to act on first (your move; for pods your pick or build), then invitations to you, then the rest. (LOB-022, DRF-012, DRF-013, DRF-014, DRF-015)
- Pods get the same app-side reminders as games while a pick or build waits for you. (LOB-076, DRF-136)
- Remove any code that keeps older app versions working. (SYS-033)
- Cut the server's fixed cost per request, which the Pi pays on every call: measure on the Pi first, then trim the extra database round trips (two transactions per request, each with lock and connection checks) and, for pods, the one-by-one member lookups and the full state decode plus ~50 KB reply on every fetch. Nothing a player sees may change except speed. (SYS-025)
- Production asks each player once to opt in to automatic error and performance reports (crashes, slow actions over 20 ms or waiting on the server, stutters, reconnects, as test builds send today); the choice can be changed later in Settings. The server keeps every report kind bounded (newest per account and in total, older ones deleted) so the Pi never fills up. This replaces timing checks in tests. (SYS-030, LOB-089, LOB-090)
- The saved cube screen shows the cards in the same picture view as saved decks, with more columns by default (number to decide). (DRF-010, LOB-056)

## Specified, not started

- Card lists: decks and cubes share one model (Main/Side/Maybe; cubes Main/Maybe), one saved view, one editor with catalog search, and pods get "Prepare cube". Spec: `docs/specs/card-lists.md`. (LOB-047, LOB-054–LOB-065, DRF-001–DRF-010, DRF-050)

## Needs discussion

- Booster and Glimpse are the same engine; turn them into built-in presets of one mode? (DRF-020, DRF-032)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
