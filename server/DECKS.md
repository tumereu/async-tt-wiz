# Saved decks and cubes

Account-private, authenticated JSON routes on the public listener (`DeckHttp`). Types
and validation live in [`Decks.kt`](../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Decks.kt)
and [`Cubes.kt`](../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Cubes.kt).
The server never fetches public card records or artwork; card data comes from its
own card catalog (feature catalog SYS-081, SYS-084).

| Route | Body → result |
| --- | --- |
| `POST /v1/decks` | `CreateDeck` → 201 `SavedDeck` |
| `GET /v1/decks?after=UUID` | 200 `DeckList` of ordinary decks |
| `GET /v1/decks?drafts=true` | 200 `DeckList` of draft decks, newest first |
| `GET /v1/decks/{id}` | 200 `SavedDeck` |
| `PUT /v1/decks/{id}` | `UpdateDeck` → 200 original `SavedDeck` outcome |
| `DELETE /v1/decks/{id}` | `DeleteDeck` with request ID → 200 `DeckDeleted` |
| `POST /v1/decks/{id}/copy` | `CopyDeck` → 201 `SavedDeck` ("Save as deck") |
| `POST /v1/cubes`, `GET /v1/cubes?after=UUID` | `CreateCube` → `SavedCube`; `CubeList` |
| `GET`/`PUT`/`DELETE /v1/cubes/{id}` | `SavedCube`; `UpdateCube`; `DeleteCube` → `CubeDeleted` |

## Card lists

- A save is one request naming each card by printing (`CardListEntry`); the server
  checks every printing against its current card catalog (`CardListInput`) and
  stores the card by its front face's printing id with its name. Entries of the
  same card and section become one, their copies added up, in the order first met.
- Decks have Main, Side and Maybe (`SavedDeckSection`); cubes Main and Maybe. Maybe
  cards are never dealt or drafted; a deck's Side and Maybe are the player's cards
  outside the game (LOB-141).
- A card whose printing the catalog lost stays as a card without a printing (its
  name alone), kept only while its list already holds it; a new deck holds none
  (`definitions_unavailable`).
- Updates may rename, choose a cover (the printing id of one of the deck's cards)
  or supply `contents`, which always replace the whole list. A cover survives only
  while the deck holds it; otherwise the first card with a printing is used.
- Deck and cube summaries carry which cards Automatum can't play yet
  (`CardListSupport`, LOB-124), worked out on every read from the catalog's marks.
- Draft decks are saved from pod deck submissions, read-only (`draft_deck`) until
  copied; beyond `DraftDecks.LIMIT` the oldest is deleted.
- List pages have at most 100 summaries; follow `next` using `after` until null.
  Revisions are decimal strings. Names need not be unique. Creation time is server UTC.
- Deletion removes the saved entry, not prepared/game copies or original outcomes.
  Unknown/foreign/deleted targets return the same 404, never upsert.

## Identity, validation and recovery

Mutations bind a canonical UUID request ID to account, kind, scope and immutable
intent. Keep originals for uncertain same-process retries: duplicates recover the
**original** result, not a second effect, even after later edits/deletion. Changed
intent under the same ID returns 409 `request_conflict`. Read current state after
recovering an old result. Effects, identity and outcome commit in one writer-fenced
transaction; a refusal from the catalog check happens before it and is not kept.

Bodies are strict UTF-8 JSON, bounded at 16 MiB. Responses are no-store JSON.
`DeckError` exposes stable codes, among them 400 `invalid_request`/`invalid_deck`/
`invalid_cover`/`definitions_unavailable`/`invalid_definition`, 401 `authentication_required`, 404
`not_found`, 409 `request_conflict`/`draft_deck`, 413 `request_too_large`, 415
`json_required`, 429 `try_later`, and 503 `catalog_unavailable` (no card catalog
yet) or `unavailable`.
Availability/transport failure means uncertain, not rejected.

```sh
./scripts/gradle.sh :protocol:jvmTest :server:test --tests '*DeckHttpTest' --tests '*CubeHttpTest'
```
