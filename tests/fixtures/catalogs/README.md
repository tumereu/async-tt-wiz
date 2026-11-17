# Original synthetic catalogs and art — fixture v1

Two identity-distinct families, **prism** and **quilt**, each have **v1/v2** source
editions. They are test inputs, never licensed bundles in the normal app/backend.
All names, plain descriptive text and abstract RGB mosaics were generated originally
for this repository by `scripts/synthetic-catalogs.py` (since removed); no real card
records, logos, frames, fonts or external images were used. These fixture data/art are dedicated
under **CC0-1.0** ([CC0 text](https://creativecommons.org/publicdomain/zero/1.0/legalcode)).
This does not clear production provider/art rights or any external release gate.

## Provider-shaped delivery

Each edition contains MTGJSON v5 `Meta.json`, `AllPrintings.json`, deterministic
`AllPrintings.json.gz` and the gzip's plain hexadecimal `.sha256` sidecar. Set/card/
token records use ordinary names, printing UUIDs, sets, collector numbers, types,
optional power/toughness/loyalty/defense and explicit reciprocal face UUIDs. The
Scryfall-shaped `scryfall/cards/<printing UUID>.json` objects expose normal
`image_uris` or `card_faces[].image_uris`; both transformed faces share one printing
object with distinct face art. The HTTP adapters (`scripts/dev-catalog.sh` for the dev
stack, `tests/support/synthetic-adapter.py` for server tests) route normal card object
requests to these files and use ordinary image content types/statuses.

`manifest.json` and `import-cases.json` are **harness inputs, not provider payload
fields**. Roles must never enter application parsing or become special-case IDs.

The checked-in synthetic origin is `http://synthetic-source:8080`. `scripts/dev-catalog.sh`
serves prism v1 at `http://127.0.0.1:18081/api/v5/`, rewriting that origin only in the
Scryfall-shaped metadata as it serves it; the files themselves stay unchanged.
Use the source's explicitly configured test-origin allowance in the real downloader;
never weaken production HTTPS/private-network/credential/redirect validation.
Scryfall-shaped image metadata contains absolute configured-origin URLs. Catalog
bytes/IDs remain unchanged by origin rebinding; metadata/manifest hashes change and
must be retained from the actual served copy. Do not patch compressed payloads by
string substitution. Serving missing artwork returns ordinary 404; broken artwork
returns the deliberately invalid `.png` bytes. The adapter must not invent a valid
image or translate a fixture role inside the application.

## Coverage and expected use

- Ordinary Creature, Artifact, Enchantment, Instant, Sorcery, Land, Planeswalker and
  Battle categories, plus absent/unclassified data. Numeric/slash/symbolic/mixed and
  absent defaults; no rules scripts or inferred actions.
- Two transformed faces with different default strings and distinct original art;
  explicit face relationships, not name-based linking.
- Multiple printings of one name share its oracle identity but have distinct
  printing/set/collector IDs/art. Similar punctuated names remain distinct. Two
  intentionally arbitrary definitions share an exact name but have different oracle
  identities/defaults: name-only import is genuinely ambiguous, explicit printing
  resolves. No prefix/fuzzy substitution is licensed by these fixtures.
- Normal catalog token and **Blank** token; a v2-only new token supports stale-catalog
  recipient/post-start token journeys. Blank's conventional label is the sole shared
  name across otherwise identity/name/art-disjoint datasets.
- Every edition has valid original PNGs, a missing URL and broken image bytes. PNGs
  are 96×128 abstract tile mosaics, not copyrighted card layouts.
- v2 retains existing UUIDs, changes the ordinary creature's text/default `2/3` to
  `5/3` and its art, and adds one token. Other art is byte-identical for cache reuse.
  Existing accepted game definitions must retain v1 data. Publishing v2/source outage,
  interrupted/corrupt updates and stale-opponent journeys are future environment/
  downloader/normal-app checks, **not fixture-validation passes**.
- Import cases include common plain/explicit-printing, Deck/Sideboard, `SB:` and
  headerless blank separator formats, unknown/ambiguous names and missing printing.
  Expected text/partitions are inputs to the real parser tests (`DeckImportTest`), not a
  second parser.

## Pinned files

The generator and its independent validator were removed; the files stay as
committed, and `manifest.json` records the generated files' SHA-256 hashes. Retain
pinned artifact hashes; investigate/version an intentional change rather than
silently rewriting previously qualified fixtures.

No normal downloader, import parser, HTTP service, game fixture injection or Android
acceptance is implemented by these files. Follow the E2E strategy: users/decks-only
pre-scenario helpers, all measured actions through both normal UIs, reset only
between independent tests and retain state within recovery journeys.
