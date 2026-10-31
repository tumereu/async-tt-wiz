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

## Section colours

Each section has one colour used everywhere it appears: section headers, the destination buttons, the
×N chips and the small other-section counts. **Main green** and **Side blue**, as the preparation
headers already are (DRF-126), and **Maybe yellow** (a darker amber where yellow text would be hard to
read on a light background).

## Saved list view (decks and cubes)

The same picture view for both (LOB-056): sections Main, Side (decks), Maybe, cards grouped in the
shared type order (SYS-051), ×N badges, tap inspects, hold previews, a columns control remembered per
screen and orientation. Buttons: Edit, Rename, Delete (confirmed); cubes also "Draft this cube"; draft
decks keep their read-only view and "Save as deck" (LOB-055, LOB-057).

## Editor (decks and cubes)

One screen with two tabs, **Cards** and **Text**, and one full-width **Save** at the bottom.

**Cards tab (the list)**
- The list as pictures by section (decks Main / Side / Maybe, cubes Main / Maybe), grouped in the shared
  type order (SYS-051) with ×N badges.
- Swipe a card sideways: move one copy to the next section (as in preparation, LOB-048).
- Tap a card: a small menu with +1, −1, Remove all, Move to…, Inspect. Hold: preview (SYS-049).
- A round **Add cards** button at the bottom left opens search.

**Search page** (full screen, from Add cards)
- Top: the search field and quick filter chips (colours, card types, mana value). Middle: results as
  pictures. Bottom, within thumb reach: a **Main | Side | Maybe** button group (cubes: Main | Maybe)
  choosing where added cards go, then **Done · +N cards**.
- Each result shows an **×N chip** with its copies in the chosen section, plus small counts for other
  sections that have copies (e.g. "S2").
- Tap a result: add one copy to the chosen section (its chip counts up with a short pulse). Tap the ×N
  chip: remove one copy from that section. Hold: preview.
- Search as often as you like and switch the destination at any time; the destination is remembered for
  the rest of the edit session and starts at Main each time the editor opens. Done or Back returns to the
  list with every change kept.

**Text tab**
- The raw decklist, editable as today (LOB-059–LOB-062), with Main/Sideboard/Maybeboard headers. Import
  from a link (Archidekt for decks, Cube Cobra for cubes) replaces the list. **Merge import is removed**
  (LOB-065).

**Saving**
- Changes in either tab are kept only by **Save**. Leaving with unsaved changes asks "Discard unsaved
  changes?" (Keep editing / Discard), as LOB-064 does today.
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
