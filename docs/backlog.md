# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Booster and Glimpse become built-in presets of one pack-draft mode named "Booster": mode chips Booster and Grid; the built-ins are listed first in the same preset list as your own presets and can't be edited or deleted; a pod's mode always reads "Booster". (DRF-020, DRF-032) Approved text: DRF-020 "Mode chips Booster and Grid load that mode's default settings, keeping the sideboard limit and Librarians (Grid always has no Librarians). Glimpse is a built-in preset of Booster, and a pod's mode always reads "Booster", also for pods created as Glimpse. (r35)" DRF-031 "A Presets button on New pod and on the creator's pod setup opens a dialog listing the built-in presets Booster and Glimpse first, then your saved presets, with search; there are no preset chips. (r35)" DRF-032 "A preset stores the draft settings and source under a name; rows read like "Booster · My cube · 3×15", and a deleted cube shows "Cube missing". Built-in presets hold settings only and read "Glimpse · Built-in · 9×15"; choosing one keeps the pod's current source. (r35)" DRF-033 ""Save current settings as preset…" asks a name (up to 60 characters); an existing name asks "Replace preset"; rows can be renamed and deleted (confirmed). Built-in rows can't be renamed, replaced or deleted. (r35)"

## Specified, not started

- Limited engines: Rochester (simultaneous packs), Rotisserie, Winston, Winchester and Sealed, a Picks view of opponents' picks, per-engine player limits. Spec: `docs/specs/limited-engines.md`. (DRF-016–DRF-032, DRF-071, DRF-079–DRF-087, DRF-136)
- Card lists: decks and cubes share one model (Main/Side/Maybe; cubes Main/Maybe), one saved view, one editor with catalog search, and pods get "Prepare cube". Spec: `docs/specs/card-lists.md`. (LOB-047, LOB-054–LOB-065, DRF-001–DRF-010, DRF-050)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
