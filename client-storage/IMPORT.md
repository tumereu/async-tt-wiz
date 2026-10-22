# Deck text import and new saved decks — I06a/I06b2

`DeckTextParser` preserves the exact input string and 1-based source lines. It
accepts positive integer quantity/name entries (also `4x Name`), optional exact
`(SET) collector-number`, Deck/Mainboard/Sideboard headings and `SB:`. A blank line
after mainboard entries starts sideboard only when there are no explicit headings.
Unsupported sections, missing/invalid/overflowing quantities and empty lists are
actionable errors. Counts use the existing signed 32-bit quantity representation,
not a format-legality or deck-size rule; section totals use 64-bit arithmetic.

`DeckImportResolver` runs off-main against one locked installed catalog generation.
Names match case-insensitively with outer whitespace trimmed; interior whitespace,
punctuation and full multi-face names are not fuzzily rewritten. Set codes are
case-insensitive, collector numbers exact. Explicit reciprocal faces form one
printing and are retained in side order with their complete original provider JSON,
including optional/missing display defaults. Distinct matching printings require
an explicit suffix rather than an invented default. Duplicate section/printing
entries add quantities; mainboard and sideboard remain distinct. Any error returns
no usable preview payload, never a partial deck.

The normal Manage decks bottom action opens the native input/error/preview route.
Lookup is debounced or explicitly retried, and catalog acquisition remains available
without discarding text. Artwork uses the existing public loader; missing/loading
images keep the name visible. Read-only quantities/sections, personal deck name,
Edit pasted list, Exit and native Back survive ordinary rotation. The Activity's
ViewModel owns the volatile draft; Logout/reset clears it synchronously and fences
old lookup callbacks. Nothing is written as an authoritative local saved deck.
Process death may lose unfinished personal input; rotation does not.

**Save deck now uses the real authenticated service.** `DeckSavePlan` maps every
ordered face, explicit links, raw provider provenance and plain display/default
strings into shared content-addressed definitions on the import worker. One Save
submits complete metadata and atomically creates the named server deck without an
extra Upload action. Name/text/preview stay editable after failure; same-process
unchanged retries retain both immutable requests and IDs. Changing input or name
creates new identities. No crash-durable outgoing queue is added.

Success opens a server-read saved detail. The minimal collection follows all list
pages and reads the exact attached definitions, independent of public catalog/art
availability. Restart fetches accepted decks again; it does not recreate an uncertain
draft. Logout clears draft/list/detail memory immediately and fences queued work
and late callbacks. Already-authorized in-flight work may finish under D-289.
I07 adds collection/inspector management; I08 adds contextual Overwrite/Merge.
The full suite, broad device matrix and final mock comparison remain I34–I36/B8.

Focused host checks:

```sh
./scripts/gradle.sh --no-daemon --console=plain \
  :client-storage:testDebugUnitTest --tests org.asyncttwiz.storage.DeckImportTest \
  :androidApp:testDebugUnitTest --tests org.asyncttwiz.android.DeckImportControllerTest
```

The [run record](../docs/agent/implementation-runs/20261022T114326Z-f8a9eff1.json)
retains actual native Appium input/error/correction/preview/rotation/Exit/Logout
evidence and the incremental builds, with diagnostics kept separate from passes.
The [save run](../docs/agent/implementation-runs/20261022T124301Z-3bc7cb2f.json)
adds focused mapping/HTTP/controller/private-read tests and normal API29 Appium
save/failure/retry/restart/public-catalog-eviction/account-isolation evidence.
