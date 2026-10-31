# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.

## Ready to build

- Under the Auto button, show a "Hold to branch" hint whenever it does not show Rewind, and remove the top bar's "Continue playing from here" button, so holding Auto is the only way to branch. The top bar's cancel button while a continuation is armed stays. (HIS-013, HIS-036)
- Pods and games use one list order everywhere (screens, menu panels, side menu): yours to act on first (your move; for pods your pick or build), then invitations to you, then the rest. (LOB-022, DRF-012, DRF-013, DRF-014, DRF-015)
- Pods get the same app-side reminders as games while a pick or build waits for you. (LOB-076, DRF-136)
- Every place that groups cards by type uses the shared type order, starting with library search. (SYS-051, ZON-051)
- The saved cube screen shows the cards in the same picture view as saved decks, with more columns by default (number to decide). (DRF-010, LOB-056)

## Needs discussion

- Cube sideboards: today a cube is one list, and "Include sideboard" only adds a pasted list's sideboard lines to it. Should cubes have a real sideboard, imported from Cube Cobra, with a prepare step when creating a pod? (DRF-003, DRF-009)
- Booster and Glimpse are the same engine; turn them into built-in presets of one mode? (DRF-020, DRF-032)
