# Deck text import

`DeckTextParser` preserves exact input and 1-based source lines. It accepts positive
quantity/name entries (including `4x Name`), optional `(SET) collector-number`,
Deck/Mainboard/Sideboard headings and `SB:`. Without explicit headings, a blank
line after mainboard entries starts sideboard. Invalid/overflowing quantities,
unsupported sections and empty lists produce actionable errors, not partial decks.
Quantities are signed 32-bit; section totals use 64-bit arithmetic, not legality caps.

`DeckImportResolver` works off-main against one locked catalog generation. Names
match case-insensitively with outer whitespace trimmed; punctuation, internal
whitespace and multi-face names are not fuzzily rewritten. Set codes ignore case;
collector numbers are exact. Ambiguous printings require an explicit suffix.
Reciprocal faces retain side order and full provider data. Duplicate entries add
quantities within a section. Any error prevents a usable preview.

`DeckSavePlan` maps resolved faces/provenance into immutable, content-addressed
definitions. Native Save submits definitions and creates the server deck without
a separate Upload action. Contextual import updates an existing deck using
Overwrite or Merge; see [server deck behavior](../server/DECKS.md).

Drafts, previews and unchanged retry identities survive rotation in the ViewModel,
not process death. Changed intent gets new IDs. A failed/uncertain save preserves
input; an unchanged retry recovers original outcomes. Success reads current server
state and attached definitions, independent of public catalog/art availability.
Logout clears private memory and fences queued callbacks. No authoritative local
deck or crash-durable outgoing journal is created.

```sh
./scripts/gradle.sh :client-storage:testDebugUnitTest --tests '*DeckImportTest' \
  :androidApp:testDebugUnitTest --tests '*DeckImportControllerTest'
```
