# Definition and saved-deck HTTP v1 — I06b1

The normal public listener implements these account-private routes. Use the existing
`Authorization: Bearer …` token from `/sessions`; no account selector, cookies,
game, official-provider lookup or server artwork download. Existing `/accounts`,
`/sessions`, `/account` and `/session` routes are unchanged. TLS/proxy configuration
remains external; plaintext is for isolated local tests only.

| Method/path | Body | Success |
| --- | --- | --- |
| `POST /v1/definitions` | `SubmitDefinitions` | 200 `DefinitionsAccepted` |
| `POST /v1/decks` | `CreateDeck` | 201 `SavedDeck` |
| `GET /v1/decks?after=UUID` | none; `after` optional | 200 `DeckList` |
| `GET /v1/decks/{id}` | none | 200 `SavedDeck` |
| `PUT /v1/decks/{id}` | `UpdateDeck` (requestId, optional name/cover) | 200 `SavedDeck`, original mutation outcome |
| `DELETE /v1/decks/{id}` | `DeleteDeck` (requestId) | 200 `DeckDeleted` (id), original deletion outcome |
| `GET /v1/decks/{id}/definitions/{digest}` | none | 200 `SubmittedDefinition`, only if attached to this owned deck |

Exact shared types, validation, canonical bytes and SHA-256 adapters are in
[`Decks.kt`](../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Decks.kt).
The complete metadata submission and subsequent atomic attachment are internal
steps of one native Save action, **not** extra user Upload/Send actions. Submission
alone does not create/change a deck. Neither route is an upload session/chunk protocol.

## Wire records and client mapping

```json
{
  "requestId": "00000000-0000-4000-8000-000000000001",
  "definitions": [
    {
      "digest": "sha256:<64 lowercase hex recomputed from the definition>",
      "definition": {
        "schemaVersion": 1,
        "name": "Example front // Example back",
        "provider": "mtgjson",
        "sourceVersion": "catalog date/version",
        "printingId": "external printing identity, not a content key",
        "layout": "transform",
        "token": false,
        "faces": [
          {
            "faceId": "00000000-0000-4000-8000-000000000002",
            "name": "Example front",
            "otherFaceIds": ["00000000-0000-4000-8000-000000000003"],
            "typeLine": "Creature",
            "manaCost": "",
            "rulesText": "Plain display text",
            "power": "2",
            "toughness": "3",
            "loyalty": "",
            "defense": "",
            "corner": "2/3",
            "scryfallId": null,
            "imageUri": null,
            "sourceData": null
          },
          {
            "faceId": "00000000-0000-4000-8000-000000000003",
            "name": "Example back",
            "otherFaceIds": ["00000000-0000-4000-8000-000000000002"]
          }
        ]
      }
    }
  ]
}
```

This illustrative digest placeholder is not a valid request. Use
`DeckJson.digest(definition)` for the actual ID, then submit distinct definitions
once. `schemaVersion=1`, layout `normal`, token `false`, absent nullable provenance/
image/source fields `null`, other-face list empty and absent display/default strings
`""` are the typed defaults. `name`, `faces`, each `faceId` and face `name` are
required. Face IDs are canonical UUIDs. Each ordered face explicitly references
exactly all other faces of this definition, with no duplicates/missing/self link.
One-face records use no links. First face/order and resolved `corner` strings are
semantic; retain all faces, even when only one is displayed. Missing optional
provider defaults remain blank, never an unresolved card or guessed statistic.

I06b2 maps I06a's complete ordered `ResolvedDeckEntry.faces`: catalog `id`, `name`,
`otherFaces`, `scryfallId`, `token` and raw provider display/default fields to these
records. Retain source/set/number/language provenance in bounded plain `sourceData`
when supplied; it is opaque text, not parsed/executed server rules. Use the existing
resolved per-face corner defaults. Public catalog/art cache entries are **not** the
accepted definition store. A reopened deck reads these exact server snapshots,
even if the public catalog has changed or disappeared.

Creation then sends:

```json
{
  "requestId": "00000000-0000-4000-8000-000000000004",
  "name": "My deck",
  "entries": [
    {"section": "MAINBOARD", "definition": "sha256:<accepted digest>", "quantity": 4},
    {"section": "SIDEBOARD", "definition": "sha256:<accepted digest>", "quantity": 2}
  ]
}
```

`SavedDeck` is `{deck:{id,name,revision,createdAt,mainboard,sideboard,cover},entries:[…]}`. Revision starts at the
decimal string `"1"`; creation time is server UTC RFC3339. Name is preserved exactly,
must be nonblank and within the ordinary text bound; duplicate names are allowed.
Entries preserve order, exact section/digest and positive 32-bit quantity. Consolidate
repeated section+digest rows before submission. No format legality, minimum mainboard,
sideboard maximum or deck/card-count rule is imposed. Definitions must already be
accepted for this account, even if equivalent content exists for another account.

`DeckList` is `{decks:[DeckSummary…],next:UUID|null}`. Follow `next` using `after`
until null: pages contain at most 100 summaries in ascending unsigned UUID order.
This is normal read pagination, not a deck-count cap. Do not treat a first page as
the complete collection. Details retain metadata and entries; attached-definition
reads include both `digest` and the complete `definition`.

## Collection management — I07a

`UpdateDeck` is `{requestId,name:null|string,cover:null|digest}`. Omitted/null fields
stay unchanged; at least one field is required. Names use the creation text rules.
Cover must be an exact definition attached to this deck, in either section; no URL,
upload or independent card edit is accepted. Metadata changes take that deck's row
lock and increment its revision once when a value changes. They never modify card
entries or immutable definitions. `DeleteDeck` removes the saved collection entry
and its entries only, retaining definitions/account grants and the original outcome.
Unknown, foreign or already-deleted targets return the same 404, never an upsert.
No future prepared/game copy is linked with a cascade to its saved source.

Update/delete canonical intent includes the captured path deck ID. They use the
same global request/account/kind/intent identity and atomic writer-fenced
effect/outcome transaction as creation. A lost-response retry returns the original
result, even after newer metadata or deletion; it does not replay the old effect.
Clients read current state afterward rather than install an old outcome over it.
New requests against deleted decks cannot recreate them. An unavailable response
is still unknown: retain the unchanged identity for same-process retry.

Summary counts are exact 64-bit section totals, not legality limits. `cover` is
`{definition,name,scryfallId,imageUri}` for the first retained face, or null for an
empty deck. The initial presentation fallback is the first retained entry until an
explicit in-deck choice; missing artwork remains the selected card's text rather
than another card. List/read metadata and entries are read under shared deck row
locks, so an update/delete cannot split their accepted snapshot. V5 explicitly adds
the nullable `cover_digest`; populated V4 decks/entries/definitions/outcomes survive
without reset. No dependencies or permission changes were needed.

The normal Android collection has cover/name/count rows and a fixed bottom Import;
details are read-only Mainboard/Sideboard tabs. Edit offers Rename, Choose cover and
Delete saved deck. Cover choice is a border-only personal draft with selectable
accessibility semantics and one bottom confirmation. Rename/cover/confirmation and
section survive rotation in the existing ViewModel. Delete uses one title, top-right
X, “Deletion cannot be undone” and equal Keep/Delete buttons. X/Back/Keep do not send
a deletion. Accepted edits are not rolled back when leaving. I08 adds contextual
Import decklist; I07b adds full inspection, not quantity editing or trash/restore.

While Manage decks is composed, a five-second bounded refresh and foreground
refresh fetch accepted account data. Dialog drafts/section/scroll remain personal;
remote metadata does not overwrite unfinished input. A remote deletion exits an
open detail to the collection with a safe notice. Reads preserve last accepted
content on ordinary failure; retry keeps context. Epoch checks fence stale reads,
queued requests and private callbacks after navigation/Logout. Relaunch reads the
collection from the server, not an authoritative local deck or outgoing journal.

Affected evidence is retained in the
[I07a run](../docs/agent/implementation-runs/20261022T134014Z-ca978b34.json).
The normal two-instance journey is evidenced across diagnostic core/tail segments
and a passing persisted-deletion tail, **not** one wholly green original command.
The first pagination 503 remains unexplained diagnostic; its unchanged-assertion
focused recheck passes. No transient failure is declared fixed from that recheck.
I34 retains final whole-journey/harness/full-suite qualification; I07b/I08 and
I10/I12 selected-copy assertions, independent B2 review and I35/I36 gates remain.

## Validation, identity and recovery

- Bodies: strict UTF-8 `application/json` (optional UTF-8 charset), unknown typed
  fields rejected, at most **16 MiB** per complete POST. This is an ordinary HTTP
  resource bound, not a game/deck-size policy. No partial installation on excess,
  malformed/truncated body, interrupted transfer, validation error or SQL failure.
- Each definition: supported schema 1, at most **256 KiB canonical metadata**,
  1–64 closed faces and **64 KiB UTF-8 per text field**. Reject unpaired surrogates
  and NUL (not retainable in PostgreSQL text). Names must be nonblank. Arbitrary
  definitions need not be known/approved by a provider; text is never HTML/scripts.
- Image references are optional. Scryfall IDs are canonical UUIDs. Supplied URLs
  must be credential-free HTTPS public-hostname references without explicit ports,
  local/IP literals or fragments; omit unsupported references and use text fallback.
  This server never resolves/fetches them. A downloader still must revalidate actual
  DNS, redirects and byte/pixel bounds; syntactic acceptance is not network safety
  clearance or an art-availability promise.
- `DeckJson.definitionBytes` uses tag `async-tt-wiz:definition:1` plus LF and typed
  RFC8785-subset JSON: all defaults/nulls expanded, object keys sorted by UTF-16,
  face-link sets sorted, face order retained, exact text preserved. The ID is
  `sha256:` + lowercase SHA-256. Different contents claiming the same external
  name/printing ID never overwrite one another. Equivalent snapshots reuse content
  IDs, but account-access grants remain private.
- Use **different canonical UUID request IDs** for metadata submission and creation.
  Retain each immutable request and ID through same-process unknown/lost-response
  retries. Definition-set ordering/link ordering normalize; entry order is semantic.
  Request identity binds account, kind, account scope and domain-tagged SHA-256 of
  the canonical typed intent, excluding auth/transport. Changed account/kind/intent
  under an existing ID returns a generic conflict, never another owner's result.
- Complete validation precedes any definition retention. Deck creation, exact
  entries and final non-secret outcome commit in one writer-epoch-fenced PostgreSQL
  transaction. Concurrent/repeated IDs recover the original status/body/created ID,
  not a new deck or a revalidation against newer data. Structurally parsed semantic
  rejections are retained too. Malformed transport/schema/request-ID errors never
  establish a business identity; database/availability failure is unknown, not a
  retained rejection. SQL failure rolls back effects **and** identity/outcome.
- No business-outcome age expiry, tokens/passwords in replay, durable client outgoing
  queue, authoritative local deck or special reset/admitted-work ordering. New private
  requests check the existing session/epoch; already-authorized work may finish.

All responses are JSON, `Cache-Control: no-store`, no cookie session or reflected
inputs. `DeckError` is `{code,field:null|string}`:

| Status | Codes / handling |
| --- | --- |
| 400 | `invalid_request` (including malformed/cursor/request ID), `invalid_definition`, `invalid_deck`, `definitions_unavailable`, `invalid_cover`; preserve draft and correct/resubmit with new IDs for changed intent |
| 401 | `authentication_required`; no private result/read-back, sign in normally |
| 404 | `not_found`; same for absent/foreign deck or unattached definition |
| 409 | `request_conflict`; do not retry changed intent under an old ID |
| 405 / 413 / 415 | `method_not_allowed` (+Allow), `request_too_large`, `json_required` |
| 429 | `try_later`, Retry-After 60; bounded process-local 600/min/account small API requests |
| 503 / 500 | `unavailable` / fixed `internal_error`; retain uncertain identity, retry unchanged, never assume rejection |

V4 adds `definition`, `account_definition`, `saved_deck`, `deck_entry`, global
`request_identity` and non-secret `account_request`. Normal generation uses real
PostgreSQL/Flyway/jOOQ; no handwritten generated bindings. Run the installed explicit
`server migrate` before starting the upgraded service. Startup does not self-migrate;
unsupported/unmigrated schemas remain unready. No new background worker is required.

## Focused checks and remaining delivery

```sh
./scripts/gradle.sh --no-daemon --console=plain :protocol:jvmTest \
  :server:test --tests org.asyncttwiz.server.decks.DeckHttpTest verifyModuleBoundaries
```

The [I06b1 run](../docs/agent/implementation-runs/20261022T122241Z-22388f73.json)
retains actual shared golden/validation tests, real authenticated HTTP/PostgreSQL
creation/read/privacy/concurrent-original-outcome/restart tests, interruption and
transactional rollback, installed V3→V4 migration, affected account/foundation
regressions, Android shared-artifact assembly/lint and exact owned cleanup evidence.
No public catalog/art endpoint is needed by those service checks.

The [I06b2 run](../docs/agent/implementation-runs/20261022T124301Z-3bc7cb2f.json)
wires native Save/failure/retry/success/restart and private callback fences to these
unchanged service interfaces, including server-definition reopening without an
installed public catalog. I07 owns collection metadata/cover/delete/
full inspection/synchronization, I08 extends **this same** service with contextual
Overwrite/Merge and full conflict/copy-isolation checks. I06b/I06 parents and B2
independent review remain open. I34 retains final full-suite/fault assertions, I35
compatibility/upgrade/device qualification and I36/B8 the full final UI comparison.
All external release gates remain unverified; no deployment/release is claimed.
