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
- Side menu game rows show the last activity like every other game list, not the creation date. (LOB-019, LOB-024)
- Booster and Glimpse become built-in presets of one pack-draft mode named "Booster": mode chips Booster and Grid; the built-ins are listed first in the same preset list as your own presets and can't be edited or deleted; a pod's mode always reads "Booster". (DRF-020, DRF-032)

## Specified, not started

- Limited engines: Rochester (simultaneous packs), Rotisserie, Winston, Winchester and Sealed, a Picks view of opponents' picks, per-engine player limits. Spec: `docs/specs/limited-engines.md`. (DRF-016–DRF-032, DRF-071, DRF-079–DRF-087, DRF-136)
- Card lists: decks and cubes share one model (Main/Side/Maybe; cubes Main/Maybe), one saved view, one editor with catalog search, and pods get "Prepare cube". Spec: `docs/specs/card-lists.md`. (LOB-047, LOB-054–LOB-065, DRF-001–DRF-010, DRF-050)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
