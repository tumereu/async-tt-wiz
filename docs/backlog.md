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
- The saved cube screen shows the cards in the same picture view as saved decks, with more columns by default (number to decide). (DRF-010, LOB-056)
- Pod rows everywhere (main menu Pods panel, Pods screen, side menu) show the side menu's compact stack of the other members' avatars (up to two plus "+N"; only yourself when alone). (DRF-012, DRF-014, DRF-015)
- Notifications use a proper single-colour app icon (the logo's silhouette) as their status-bar icon instead of Android's generic info icon. (LOB-072)

## Specified, not started

- Card lists: decks and cubes share one model (Main/Side/Maybe; cubes Main/Maybe), one saved view, one editor with catalog search, and pods get "Prepare cube". Spec: `docs/specs/card-lists.md`. (LOB-047, LOB-054–LOB-065, DRF-001–DRF-010, DRF-050)

## Needs discussion

- Booster and Glimpse are the same engine; turn them into built-in presets of one mode? (DRF-020, DRF-032)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
