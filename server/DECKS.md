# Definitions and saved decks

Account-private, authenticated JSON routes on the public listener. Types,
validation and canonical SHA-256 identities live in
[`Decks.kt`](../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Decks.kt).
The server never fetches public card records or artwork.

| Route | Body → result |
| --- | --- |
| `POST /v1/definitions` | `SubmitDefinitions` → 200 `DefinitionsAccepted` |
| `POST /v1/decks` | `CreateDeck` → 201 `SavedDeck` |
| `GET /v1/decks?after=UUID` | 200 `DeckList` |
| `GET /v1/decks/{id}` | 200 `SavedDeck` |
| `PUT /v1/decks/{id}` | `UpdateDeck` → 200 original `SavedDeck` outcome |
| `DELETE /v1/decks/{id}` | `DeleteDeck` with request ID → 200 `DeckDeleted` |
| `GET /v1/decks/{id}/definitions/{digest}` | 200 exact attached `SubmittedDefinition` |

## Definitions and collection behavior

- Native Save automatically submits complete metadata, then creates/updates the
  deck; submission alone changes no deck. Definitions retain ordered faces,
  reciprocal face UUID links, exact display text and optional provider provenance.
  `DeckJson.digest()` computes the content identity; names/printing IDs are not keys.
- Each account must have its own grant to submitted definitions. Accepted snapshots
  are immutable and independent of replaceable public catalog/art caches. Reopening
  a deck reads its server definitions, not today's provider record.
- Entries preserve order, `MAINBOARD`/`SIDEBOARD`, digest and positive 32-bit
  quantity. Consolidate identical section+digest rows before creation. No format
  legality, minimum size or sideboard limit is enforced; totals are 64-bit.
- List pages have at most 100 summaries in unsigned UUID order; follow `next`
  using `after` until null. Revisions are decimal strings, starting at `"1"`.
  Names need not be unique. Creation time is server UTC.
- Updates may rename, choose an attached definition as cover, or supply `contents`.
  `OVERWRITE` replaces both sections (omitted sideboard becomes empty); `MERGE`
  adds quantities by section+definition digest, retaining existing order and
  appending new entries. Overflow rejects atomically with `quantity_overflow`.
  A retained cover survives only while attached; otherwise the first entry is used.
- Deletion removes the saved collection entry, not immutable definitions, original
  outcomes or independent prepared/game copies. Unknown/foreign/deleted targets
  return the same 404, never upsert.

## Identity, validation and recovery

Mutations bind a canonical UUID request ID to account, kind, scope and immutable
intent. Use distinct IDs for definition submission and deck mutation. Keep originals
for uncertain same-process retries: duplicates recover the **original** result,
not a second effect, even after later edits/deletion. Changed intent under the same
ID returns 409 `request_conflict`. Read current state after recovering an old result.
Effects, identity and non-secret outcome commit in one writer-fenced transaction.

Bodies are strict UTF-8 JSON, bounded at 16 MiB. Definitions support schema 1,
1–64 closed faces, at most 256 KiB canonical metadata and 64 KiB per text field.
Malformed input never installs partial data. Text is not HTML or executable rules.
Optional image references must be credential-free public HTTPS references; accepting
a URL does not authorize a downloader to skip DNS/redirect/size checks.

Responses are no-store JSON. `DeckError` exposes stable codes: 400 invalid input/
definition/deck/cover or unavailable definitions, 401 authentication required,
404 not found, 409 request conflict, 413 too large, 415 JSON required, 429 try later,
503 unavailable. Availability/transport failure means uncertain, not rejected.

```sh
./scripts/gradle.sh :protocol:jvmTest :server:test --tests '*DeckHttpTest'
```
