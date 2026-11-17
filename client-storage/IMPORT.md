# Deck text import

`DeckTextParser` preserves exact input and 1-based source lines. It accepts positive
quantity/name entries (including `4x Name`), optional `(SET) collector-number`,
Deck/Mainboard/Sideboard/Maybeboard headings (Commander/Companion join the main deck,
metadata sections are skipped) and `SB:`, plus Forge, XMage and CSV exports. Without
explicit headings, exactly two blank-separated blocks are mainboard and sideboard.
Invalid quantities, unreadable entries and empty lists produce actionable errors, not
partial decks. A list holds at most `DeckValidation.MAX_CARDS` cards in all sections.

`DeckImportResolver` works off-main against one locked catalog generation. Names
match case-insensitively with outer whitespace trimmed; punctuation, internal
whitespace and multi-face names are not fuzzily rewritten. Set codes ignore case;
collector numbers are exact. Several printings of one card take a default printing;
different cards sharing a name require an explicit suffix.
Reciprocal faces retain side order and full provider data. Duplicate entries add
quantities within a section. Any error prevents a usable preview.

`DeckSave` (app-ui) makes one save request naming each card by its printing: a new
deck, or the whole new list of an existing deck. The server builds the cards from its
card catalog; there is no separate Upload action. See [server deck behavior](../server/DECKS.md).

Drafts, previews and unchanged retry identities survive rotation in the ViewModel,
not process death. Changed intent gets new IDs. A failed/uncertain save preserves
input; an unchanged retry recovers original outcomes. Success reads current server
state and attached definitions, independent of public catalog/art availability.
Logout clears private memory and fences queued callbacks. No authoritative local
deck or crash-durable outgoing journal is created.

```sh
./scripts/gradle.sh :client-storage:testAndroidHostTest --tests '*DeckImportTest' \
  :androidApp:testDebugUnitTest --tests '*DeckImportControllerTest'
```
