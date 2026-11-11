# Test cards for the card catalog

A small, invented MTGJSON AllPrintings file (no real card data) and its support lists, built into a card catalog
(`:catalog:catalogCommand build --no-pin`) for the catalog's own tests, the server tests and the e2e stack.

- `TST` Test Set: supported cards, a token maker with its token, a Play Booster recipe.
- `TWL` Thornwall Crossing: a meld pair (problem report 7ZZC89) and twenty commons, as `MeldSetSourceTest` uses.
- `BAD` Broken Set: listed, but one card's text Automatum can't read, so it can't be drafted.
- `OFF` Unlisted Set: not listed and without a booster recipe (approximate packs); two cards on the single-card list (one
  of them double-faced), a reprint of Test Bear, a Japanese printing.
- `BON` Bonus Sheet Set: listed; three commons, a reversible printing of Test Bear on a sheet of its own, and a
  special-guest sheet holding Plain Bear from `OFF` (unsupported).
- `DCK` Test Promos: a kind of set the Sets tab doesn't list, whose recipe the catalog leaves out.
- `E2E` End-to-End Test Cards and `M21` Test Basic Lands: every card the e2e and server tests save in decks and cubes
  (`e2e/.../TestCards.kt`), M21 holding the five basic lands the server supplies with pod decks. Both are a kind of set
  the Sets tab doesn't list. Generated from `TestCards`: after changing it, run
  `./scripts/gradle.sh :androidApp:testDebugUnitTest -PtestCards=record --tests '*TestCardsFixtureTest*'`
  (the test fails while the file and `TestCards` differ).

The other sets were generated once by hand-written code from these descriptions; edit the JSON directly when a test needs
more.
