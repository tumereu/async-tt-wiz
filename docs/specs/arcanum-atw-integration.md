# Arcanum Automatum in ATW: integration draft

Draft notes on how ATW will host the Arcanum libraries, collected while reviewing the
card definition API. Nothing here is approved for building; library-side decisions
are in `arcanum-automatum.md` (D1–D21). Each item becomes a rules-version change with
catalog entries the owner approves when its phase is built.

## Switch

- One **Automatum on/off** per game (D20). While it is on, some manual actions may be
  restricted (e.g. manually created Stack entries) where they can't be kept in line
  with automation. To decide when built: what happens to stored effect objects and
  Stack entries when the switch changes.

## Shared state ATW stores for the engine

- **Zone-change count per card** (D21). The engine's object id is the ATW card id
  plus this count; it goes up on every move between zones. Stored, not derived from
  history (SYS-026).
- **Ledgers** (D22): hidden engine bookkeeping at game, player and card-instance
  scope (cards drawn, creatures died, mana spent, kicked, …); key names and values
  come from the engine. Events change them by increments and set changes so undo
  works. An Automatum debug menu shows them. Undo that removes the cause of a later
  automated result is fixed by hand.
- **Stack items** (D19): engine-defined data stored as-is, shown through the engine's
  description of them.
- **Effect objects** (D19): engine-defined data stored as-is; Inspect shows them on
  the cards they affect, "(until end of turn)" as today (TBL-110). Today's per-card
  EOT marks (TurnMarks) stay for manual play.

## Decisions the engine asks for

- **Random results** (D25): the client sending the batch answers them with ATW's own
  randomness (seeded actions, coin flips) and records them in the batch.
- **Shuffles** (D25): the engine asks ATW to shuffle; ATW uses its existing shuffle
  and unknown library order.
- **Secret choices**: manual until ATW can store an answer one player can't read.
- **Sequences** (D31): the current resolution or cast sequence is game state, one
  committed step per event batch, undoable per step. A choice opens a blocking
  dialog that can't be dismissed until completed; no other changes meanwhile. The
  opponent sees it greyed out ("P1 is choosing") with "Choose for them"; viewing
  hidden cards that way needs extra confirmation. Look/Reveal steps use today's
  server fill of hidden library cards.
- **Who sends** (D31, D12): the client whose player's choice or action started the
  step sends everything up to the next decision in one batch; after a server fill
  of hidden cards, the same client continues.
- **Blocking** (D32): with Automatum on, other changes are blocked during a
  sequence and rule-breaking moves may be refused (exception to SYS-036); switching
  Automatum off is always allowed and is the escape from a stuck sequence.
