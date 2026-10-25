# Card data and artwork

## Confirmed acquisition direction — D-270

Clients download card records and artwork from public sources and store/reuse them
locally. This refines D-243's dynamic-data boundary, not a selection of MTGJSON,
Scryfall or Cockatrice formats. No mandatory backend catalog/art proxy or bundled
licensed catalog. It does not require an entire image collection at installation,
permanent unbounded local storage, a separate updater executable or a new editor.
Exact download scope, update scheduling, metered-network behavior and cache budget
remain design work. Downloaded read-only review/text fallback and D-241's private
Logout cleanup remain; shared public assets are distinct from private game data.

Retain essential per-game definitions, resolved face defaults and recorded results
on the server under D-65/D-243/D-266. Client catalog updates cannot redefine accepted
game history or create per-device semantic variants. D-271 below selects
client-supplied definitions with structural validation, not server verification
against an official provider. Public-source fetching is not game authority (D-259).

## Lazy images and mismatched catalogs — D-271

**Confirmed:** resolve/download images lazily as needed and cache them locally.
Do not download every image with a catalog update or make artwork availability a
deck-save, Ready or gameplay prerequisite. Card records and image bytes have
different availability requirements; resolve an image from retained printing/face
references without changing the game's definition. Missing imagery uses text.

**Confirmed clarification:** the client informs the server of its prepared deck
and supplies the required definitions; these may be **arbitrary**, not necessarily
recognized by MTGJSON/Scryfall or identical to an official printing. The server
validates supported schema/structure, references, resource limits and safe handling
of untrusted content, not official-card authenticity or format legality. Retain
the accepted snapshots and send those same definitions to the opponent. This is
ordinary deck preparation before start, not a second manual Send deck/start step.
It does not approve a new card-authoring UI, arbitrary executable rules or scripts.

**Derived from D-65/D-198/D-243/D-259/D-266:** each game must supply its retained
essential definitions to either participant independently of their general local
catalog. A player lacking an opponent's card in an old catalog must receive the
accepted game definition from the service, not reject the opponent's action or
require a full public-catalog update. This also applies to a previously absent
token definition introduced during play, not just the starting decks. Catalog
content received this way is an accepted game record, not a mandatory public
catalog/image proxy and not authorization for a custom-definition editor.

Recommended protocol completion:

- At deck resolution/selection, submit or reference each distinct definition
  needed by the prepared deck; the server structurally validates and retains the
  accepted version, with no provider lookup required to prove authenticity. Fields
  include stable definition/version identity, any supplied source/printing
  provenance, names, faces/display data, resolved per-face corner defaults and
  image references. Exact schema and validation/resource bounds remain to design.
  A client-authored snapshot is authoritative for that accepted game, not an
  authenticated statement about a public provider's global catalog.
- Distinct supplied definitions may claim the same name/external printing ID yet
  contain different data. Preserve distinct accepted definition identities rather
  than overwriting the other deck's records or a global public-catalog entry. Scope
  deduplication to equivalent definition content/version; exact identity/digest
  representation remains technical design. Accepted deck/preparation edits obey
  existing copy isolation and Ready-clearing rules, not silent snapshot changes.
- Transfer the required distinct definition set with a bounded/paged game baseline,
  or via explicit definition reads before applying dependent records. Attach or
  fetch any newly introduced definitions on later deltas. Avoid repeated copies
  per card instance or full public-catalog transfer. D-276 initial/full recovery
  includes the complete retained game tree and its required definitions; an
  ordinary missing-definition update does not require retransmitting that tree.
  Exact eager-versus-on-demand definition paging is technical design, not approved
  by the owner's question about sending opponent deck data.
- Prefer the exact retained game-definition identity/version over a newer or older
  local catalog entry. Never silently overwrite existing games with updated text,
  face mappings or defaults. Different games may retain different source versions.
- If a required game definition has not arrived, retain the pending data as
  incomplete and fetch/recover it; do not fabricate an unknown card, drop its
  mutation, mark an incomplete replica caught up or rerun an accepted command.
  A text placeholder can explain loading, not substitute for missing semantic data.
- A saved-deck import needs resolved structurally supported records for every entry
  under D-198, not the latest entire catalog or every image. Try source refresh/resolution
  for unknown input, preserve the pasted text, and leave unresolved/ambiguous input
  unsaved. Optional provider values legitimately absent use the existing defaults,
  including D-65's blank corner fallback; do not call that an unresolved card.
  Already retained deck/game definitions do not require re-downloading the public
  catalog on another device or after local catalog eviction.
- Treat supplied text/image references as untrusted data, never executable content.
  Safe URL schemes/origins, redirects, sizes and credential isolation need explicit
  downloader validation; arbitrary card definitions are not permission for server
  SSRF or sending session credentials to a card-image host. Public assets and
  account-private accepted snapshots retain separate storage/Logout boundaries.

The [domain contract](../03-contracts/domain-model.md#game-definitions-and-local-catalog-independence--d-271)
owns these derived definition-availability outcomes; the
[import contract](../03-contracts/accounts-decks-and-lobby.md#deck-list-import-compatibility-and-errors--d-198)
owns unresolved-entry behavior. Definition download is not a player inspection or
review/read-receipt event. Private game snapshots obey account/Logout isolation.

**Submission simplification — D-289:** the client sends the needed card metadata
as one ordinary JSON HTTP POST, including before any game exists. This is small
definition data, not artwork or the entire public catalog. Use normal networking
library handling; no application-level chunking, upload sessions or separate
completion step. Validate/retain the complete account-private set, then attach its
content-addressed IDs to the saved deck/lobby under existing atomic copy/Ready rules.
No additional user Upload/Send action is introduced. See
[the HTTP contract](protocol-v1.md#reads-full-transfer-and-versioning).

## Selected providers and startup update — D-280

The owner selects **MTGJSON records plus Scryfall lazy artwork**, including the
lightweight startup update check. The earlier Scryfall-for-both recommendation is
not selected. This does not adopt Cockatrice's formats/token source wholesale or
grant content rights.

Automatic incremental updates fetch MTGJSON `SetList.json`, then per-set files for
new sets and sets released in roughly the last 60 days. A changing daily build
date never schedules a full AllPrintings import. Full re-import is MANUAL ONLY
from Settings, except when the index format changes. Store compact records with
only app-used fields, not full raw MTGJSON records or oversized deck sourceData.
Existing games never wait for catalog refresh; accepted game definitions remain
the correctness mechanism for differing catalogs. Stage and validate updates,
retain the last good catalog on failure, and preserve unresolved import input.

### Technical downloader profile — D-284

- Initial/manual full import fetches MTGJSON v5 `AllPrintings.json.gz` with checksum metadata;
  stream-decompress/import to a staging catalog generation, not an in-memory JSON
  tree. Retain source date/version/checksum, set/printing UUID, language, face identity,
  Scryfall identifiers and exact display/default strings. Combine related faces by
  MTGJSON's explicit relationships, never names alone. Include supported token
  records in that source; missing token mappings remain unresolved, not a silent
  dependency on a second GitHub XML source. Blank tokens remain available.
- Incremental checks use SetList/per-set files, never automatic daily AllPrintings.
  Bound/cache/throttle checks across rapid launches with timeout/backoff and never
  block existing games. Initial/missing-catalog and metered bulk downloads need
  explicit consent showing network cost. Full re-import remains manual unless
  the index format changes, even on unmetered Wi-Fi.
  Lazy needed art can use the current connection, never prefetch all imagery.
- Validate supported source schema, checksums, relationships, field bounds and
  import completeness before an atomic catalog-generation switch. Stage on disk
  with free-space check; failure retains last good catalog. Full payload decoded
  budget initially 4 GiB on disk, nesting 32, per-record bounds from runtime profile;
  explicit failure if exceeded, never partial catalog installation. Reassess this
  engineering budget when provider datasets grow, not silently truncate printings.
- After import show a separate **Finishing up — verifying catalog…** phase for
  validation, `PRAGMA quick_check` and token-search build; cut redundant count scans.
- Cache image resolution by exact Scryfall printing UUID + face + image size/version.
  Use Scryfall card-object image URLs (face-level where appropriate); fetch small
  metadata once when the MTGJSON identifier alone does not supply a safe image URL.
  No guessed name/first-face substitution. Missing mapping/art gives text fallback.
  Public Scryfall requests at most 5/sec/installation, User-Agent identifying app
  version/contact and normal Accept headers, 429/Retry-After honored; avoid API
  traffic per animation frame/card instance. Cache 404 for 24 h; transient errors
  retry 1 min/5 min/1 h. Use HTTPS, ETag/Last-Modified where supported.
- Standard board cards use Scryfall `art_crop` in the app's art box with app name
  and P/T. A custom deck `imageUri` renders the whole card image, no app name,
  with P/T on a background in the corner. Inspection uses the full card image.
  Tokens have owner-colored chamfered corners/same footprint; projections retain
  dashed borders and real cards remain rectangular.
- Normal art hosts are Scryfall API/CDN. Custom definition URLs remain untrusted:
  allow only HTTPS public Internet hosts, no credentials, local/private/link-local
  or metadata IPs, unsafe ports or redirect downgrade. Revalidate DNS/each redirect,
  max 3 redirects, never forward service auth/cookies, no server-side art fetch.
  Tests configure their explicit synthetic-source origin separately, without
  weakening production origin checks. Unsupported custom URLs retain text fallback,
  not rejected arbitrary card semantics. Enforce byte/pixel bounds before decoding.
- Text is plain display data, not HTML/WebView/card scripts. Store per-game accepted
  content-addressed snapshots separately from the public catalog. New definitions
  accompany a revision before dependent objects become usable; initial/full transfer
  fetches every required definition. No public-provider lookup authenticates a deck.

Provider terms/attribution and upstream artwork permission remain a **release rights
check**, not an undecided provider or permission inferred from dynamic downloads.
Synthetic adapters use the same importer/validator/image path in tests. Exact
provider dataset values/checksums are acquired at runtime; never bundle licensed
card records or depend on live public services for automated acceptance.

## Where Cockatrice obtains its data — research, 2026-10-20

Inspected upstream `master`, commit
[`829152f`](https://github.com/Cockatrice/Cockatrice/commit/829152f38871ff47c572a358888a1e147ef91ec9).
Installed releases/custom configurations may differ; this is source research, not
a dependency on Cockatrice or its server.

| Content | Current default source and local handling |
| --- | --- |
| Card/set records | Oracle Importer downloads MTGJSON v5 `AllPrintings.json`, preferring `.xz` or `.zip` when supported, and checks `Meta.json` for source version information. It converts records into Cockatrice's local card database; it can also load a compatible local file/custom source. |
| Token records | Oracle has a separate default `tokens.xml` download from `Cockatrice/Magic-Token` on GitHub. This is not the same as the main MTGJSON import. |
| Card images | Default URL order starts with Scryfall's `cards.scryfall.io` CDN, then Scryfall card API image endpoints, then Wizards' Gatherer image endpoints. Local picture lookup/cache is separate from the record import; there is no need to fetch every image with the card database. |

Primary source references:
[Oracle URLs/import pages](https://github.com/Cockatrice/Cockatrice/blob/829152f38871ff47c572a358888a1e147ef91ec9/oracle/src/pages.cpp),
[Oracle record conversion](https://github.com/Cockatrice/Cockatrice/blob/829152f38871ff47c572a358888a1e147ef91ec9/oracle/src/oracleimporter.cpp),
[default image URLs](https://github.com/Cockatrice/Cockatrice/blob/829152f38871ff47c572a358888a1e147ef91ec9/libcockatrice_settings/libcockatrice/settings/download_settings.cpp),
[local image lookup](https://github.com/Cockatrice/Cockatrice/blob/829152f38871ff47c572a358888a1e147ef91ec9/cockatrice/src/interface/card_picture_loader/card_picture_loader_local.cpp).

The configured Gatherer fallbacks are observed code defaults, not proof that every
historical endpoint still works. Do not copy Cockatrice's card-text-derived
automation or normalization into our rules-agnostic engine.

## Earlier provider recommendation — alternative, not selected

Consider **Scryfall for both records and image URLs** rather than needing MTGJSON
plus a second identity/image provider. Download a suitable bulk record dataset to
the client, stream-import into local SQLDelight tables, and fetch/cache images as
needed using the supplied per-printing/per-face image URLs. A Scryfall card object
and image URI are not a downloaded image; bulk metadata is not an artwork bundle.
Choose dataset scope against printing/language needs and Android storage/memory,
not merely the smallest file. MTGJSON records plus Scryfall images is also viable.

Stage and validate a new catalog before replacing the previous usable version;
failed updates must not corrupt the existing local catalog or pending requests.
Provider failure with an existing download should reuse that data; absent records
cannot be fabricated, and absent images retain text fallback. Catalog refresh and
disposable-art eviction do not delete essential accepted game definitions/history.
Exact schema, integrity checks, retry/rate limits, cache keys and scheduling remain
technical design; the client-download direction does not finalize these mechanisms.

Public access is not public-domain ownership. Review provider terms and Wizards'
underlying content policy before release. Scryfall's published rules address free
access, attribution/representation and image modification; MTGJSON's published MIT
license does not itself establish ownership of underlying Wizards artwork. Direct
client downloads do not remove those obligations.

Provider documentation checked 2026-10-20:
[MTGJSON downloads](https://mtgjson.com/downloads/all-files/),
[MTGJSON license](https://mtgjson.com/license/),
[Scryfall API/use rules](https://scryfall.com/docs/api),
[Scryfall bulk data](https://scryfall.com/docs/api/bulk-data),
[Scryfall image/face URLs](https://scryfall.com/docs/api/images).
