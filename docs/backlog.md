# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Printed keywords for combat damage are read from the card's rules text (bug: the catalog keeps no keyword list, so only notes worked). (CMB-025) Approved text: CMB-025 "Keywords come from the printed rules text (none for face-down cards) and from whole words in the card's note or until-end-of-turn note: trample, deathtouch, first strike, double strike and lifelink. In rules text only a line made of keywords alone counts ("Flying, first strike", "Lifelink (Damage dealt by this creature also causes you to gain that much life.)"); a keyword inside a sentence ("Equipped creature has first strike") does not. Infect, wither or unknown numbers skip that card with an "assign manually" note. Deaths are never handled. (r36)"
- Combat copies and Stack entries without their own note show the real card's note. (CMB-005, STK-009) Approved text: CMB-005 "Combat copies can have their own notes and pings, separate from the real card. A copy without a note of its own shows the real card's note chip. (r36)" STK-009 "Stack entries can have their own notes and pings, separate from the real card, set from Manage Stack (STK-019). An entry without a note of its own shows the real card's note chip. (r36)"
- Combat drops with at least half-card overlap go beside that card (join its pair or swap within its own pair), in both rows. (new CMB entry) Approved text: new CMB entry (number 027) "A card dragged into the combat area (from the battlefield or from another combat slot) that covers at least half the width of a card in the row it is dropped in goes beside that card, on the side the dragged card's centre is closer to: it joins that card's pair, or over a card of its own pair it changes places within the pair. With less overlap the gaps of CMB-009 apply. This wins over divider drops and reordering pairs, in both rows. (r36)"
- Status bubbles are never narrower than the life square. (TRN-039) Approved text: TRN-039 "A status shows as a speech bubble by the life square (under the opponent's, above your own), wrapping over up to four lines and never narrower than the life square, so its tail always meets a straight edge. The Life panel shows the full status, scrolling after four lines. Statuses stay until cleared. (r36)"
- Auto-pause "Your life / cards hit" splits into "Your life hit" (off) and "Your cards hit" (on). (HIS-025, new HIS entry) Approved text: HIS-025 "Auto-pause triggers and defaults: Added to Stack on, Removed from Stack off, Reveal on, Pings / comments on, Die / coin results off, Your life hit off, Your cards hit on, Move passes to you on, Attackers declared off, Opponent plays a land off, Opponent puts a nonland card onto the battlefield from hand off. (r36)" new HIS entry (number 090) ""Your life hit" is an opponent's change to your life or your player counters; "Your cards hit" is an opponent's action on a card you own or control (also its combat copies and Stack entries) or one giving you a card. Each device's earlier "Your life / cards hit" choice carries over to "Your cards hit". (r36)"
- Notification when your opponent accepts your result suggestion. (new LOB entry) Approved text: new LOB entry (number 111) "When your opponent accepts your result suggestion, you get a Game activity notification "<game> · vs <opponent>" reading "Game over · <opponent> accepted your win" ("your loss", "the draw"), timed like a move notification (LOB-073); tapping it opens the game. (r36)"
- Draft Build bottom bar spans the full width. (DRF-106) Approved text: DRF-106 "A fixed bottom bar across the full screen width (not inset by the columns' side margins) holds an Undo button at its left edge (always there, dimmed when empty) and "Submit deck (N)"; Undo takes back card moves and basic changes one at a time, during and after the draft. (r36)"

## Specified, not started

(nothing right now)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
