# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Landscape counters (TRN-035, ZON-045): "Landscape: each player's six counters always show in the sidebar as in portrait (a faint outline when empty), in a row under the life square and piles (above them on the opponent's side), with that player's Temp piles at the left of the row before the counters; when they don't fit, the counters move to their own row (ZON-045). Tapping a counter opens the Counters popup." The Life panel's Counters button goes away.
- Search section squares (LOB-106, LOB-107): "Each result shows up to three small squares in a row at its bottom left: Main, Side and Maybe in that order and in their section colours (SYS-054), each with that section's copy count and shown only when it holds copies; tapping a square removes one copy from that section." Replaces the ×N chip and the "S2"/M/S/Mb counts.
- Search chips (LOB-107): "Colour chips are the mana symbols as drawn in card text, mana value chips are generic mana symbols with the number; chips are 32 dp tall, 4 dp apart."
- Picker avatar (DRF-085): "…plus the picker's small avatar in the card's top left corner, over the name."
- Screenshot tests (internal, no catalog change): every test card shows one shared placeholder art picture, and table fixtures use real game ids so Temp 1 (ZON-040) shows. Build after the items above (it re-records nearly every screenshot).

## Specified, not started

(nothing right now)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
