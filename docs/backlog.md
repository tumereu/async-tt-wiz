# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Landscape counters (TRN-035, ZON-045): "Landscape: each player's six counters always show in the sidebar as in portrait (a faint outline when empty), in a row under the life square and piles (above them on the opponent's side), with that player's Temp piles at the left of the row before the counters; when they don't fit, the counters move to their own row (ZON-045). Tapping a counter opens the Counters popup." The Life panel's Counters button goes away.
- Editor section adds (LOB-105, LOB-106): "Every section header has a + at its right end that opens search with that section chosen; all of the list's sections show, even when empty." Replaces the round Add cards button; search no longer starts at Main.
- Editor undo (LOB-105): "A round floating Undo button at the bottom left, shown only when there is something to undo, takes back Cards tab and search changes one at a time (as in preparation, LOB-049)."
- Deck and cube buttons (LOB-057, DRF-010, LOB-059): "Edit is the only button above a saved deck or cube." "The editor of a saved deck or cube has a red Delete deck / Delete cube at the top right, confirmed with 'Deletion cannot be undone.'" "A draft deck shows Delete draft deck beside Save as deck at the top right." Rename goes away (the editor's name field renames).
- Text tab (LOB-059): "The Text tab's box is full width with no counts. Lines with problems are underlined in red, their messages show in a fixed area under the box, and the Text tab shows a red error icon while problems remain."
- Screenshot tests (internal, no catalog change): every test card shows one shared placeholder art picture, and table fixtures use real game ids so Temp 1 (ZON-040) shows. Build after the items above (it re-records nearly every screenshot).

## Specified, not started

(nothing right now)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
