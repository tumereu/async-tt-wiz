# Pre-release review

Things to revisit before release. Entries are added only with the owner's approval.

- Undo/redo: every undo and redo follows the general undo rule (game state only: every later event keeps identical before/after values for the fields it concerns).
- Hidden/revealed information: what is shown to whom, and for how long (today: only while in the reveal/look panel).
- Memory: measure client and server memory for a long, realistic game tree (10k+ events), in case recorded event details (e.g. concerned-field sets) cost too much.
- Keep top card revealed: the revealed top card is always known, so branch reseeds (and other re-rolls of the unknown order) never change it, while a real shuffle (also search and mulligan) re-randomizes it. Check the automated tests still cover this.
