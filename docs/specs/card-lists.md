# Spec: card lists (decks and cubes), editor and search

Owner-approved 2026-10-31. Delivered as one change; when it is built, its behaviour moves into
`docs/features/` and this file is deleted.

## One model for decks and cubes

- A deck and a cube are both a named card list with sections. Decks: **Main**, **Side**, **Maybe**.
  Cubes: **Main** and **Maybe** (no Side; Cube Cobra has none).
- **Maybe** holds cards being considered. It is never dealt, drafted or played, and is not shown in
  game preparation (LOB-047). A cube's Maybe is used only by "Prepare cube" (below).
- Existing decks keep Main and Side; existing cubes become Main-only. Nothing is lost on upgrade.
- Limits count every section: a deck at most 1000 cards (LOB-063), a cube at most 3000 copies (DRF-007).
- Differences between the two stay small: decks import from Archidekt links, cubes from Cube Cobra links;
  cubes show more picture columns by default; decks are picked for games, cubes for pods.

## Saved list view (decks and cubes)

The same picture view for both (LOB-056): sections Main, Side (decks), Maybe, cards grouped in the
shared type order (SYS-051), ×N badges, tap inspects, hold previews, a columns control remembered per
screen and orientation. Buttons: Edit, Rename, Delete (confirmed); cubes also "Draft this cube"; draft
decks keep their read-only view and "Save as deck" (LOB-055, LOB-057).

## Editor (decks and cubes)

One screen with two tabs.

- **Cards**: a search bar over the local card catalog with results as card pictures, and the list
  being edited. A section selector (Main / Side / Maybe) says where tapped results go: tapping a result
  adds one copy there; holding previews. In the list, swiping a card sideways moves one copy to the next
  section (as in preparation, LOB-048); tapping gives −/+, "Move to…" and Inspect.
- **Text**: the raw decklist, editable as today (LOB-059–LOB-062), with Main/Sideboard/Maybeboard headers.
  Import from a link (Archidekt for decks, Cube Cobra for cubes) replaces the list. **Merge import is
  removed** (LOB-065).
- Changes are kept only by an explicit **Save** (both tabs). Leaving with unsaved changes asks
  "Discard unsaved changes?" (Keep editing / Discard), as LOB-064 does today.
- Importing keeps maybeboards instead of skipping them: text-list Maybeboard sections, Archidekt's
  maybeboard and Cube Cobra's maybeboard go to **Maybe** (LOB-062, DRF-005, DRF-009 change). Cube Cobra
  basics are ignored. The cube editor's "Include sideboard" checkbox goes: a pasted cube list's
  sideboard lines go to Maybe.

## Search

Scryfall-style syntax plus quick chips for the common filters (colours, card types, mana value).
Supported: name words, `t:` type, `o:` rules text, `c:`/`c=`/`c<=` colours, `id:` colour identity,
`mv`/`cmc` mana value (from the mana cost), `pow`/`tou`/`loy`, `r:` rarity, `f:` format legality,
`s:` set, `is:` layout (e.g. `is:dfc`), comparisons (`mv<=3`), `-` negation, `or`, parentheses.

- The local catalog gains rarity, format legalities and colour identity (from the same MTGJSON
  download). Players re-download their card records once; the app asks as it does for any catalog
  update.
- The query parser and matcher live in shared Kotlin code so the planned desktop web client can reuse them.

## Pod setup: Prepare cube

Before drafting starts, pod setup offers **Prepare cube**: the same picture-and-swipe screen as deck
preparation, moving cards between the cube's Main and Maybe **for this pod only** (the saved cube does
not change; DRF-002 stays). Main is what gets drafted. Whoever can edit the pod's settings can prepare
it; the supply check (DRF-050) uses the prepared Main.

## Not now

Choosing a card's printing (art) is a later nice-to-have (backlog).
