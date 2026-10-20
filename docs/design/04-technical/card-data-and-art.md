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
game history or create per-device semantic variants. How the backend validates and
retains a resolved definition without mandatory client download proxying remains
technical protocol work; public-source fetching is not game authority (D-259).

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

## Recommendation for this project — not yet selected

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
