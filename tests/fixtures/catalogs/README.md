# Original synthetic catalogs and art — fixture v1

Two identity-distinct families, **prism** and **quilt**, each have **v1/v2** source
editions. They are test inputs, never licensed bundles in the normal app/backend.
All names, plain descriptive text and abstract RGB mosaics were generated originally
for this repository by `scripts/synthetic-catalogs.py`; no real card records, logos,
frames, fonts or external images were used. These fixture data/art are dedicated
under **CC0-1.0** ([CC0 text](https://creativecommons.org/publicdomain/zero/1.0/legalcode)).
This does not clear production provider/art rights or any external release gate.

## Provider-shaped delivery

Each edition contains MTGJSON v5 `Meta.json`, `AllPrintings.json`, deterministic
`AllPrintings.json.gz` and the gzip's plain hexadecimal `.sha256` sidecar. Set/card/
token records use ordinary names, printing UUIDs, sets, collector numbers, types,
optional power/toughness/loyalty/defense and explicit reciprocal face UUIDs. The
Scryfall-shaped `scryfall/cards/<printing UUID>.json` objects expose normal
`image_uris` or `card_faces[].image_uris`; both transformed faces share one printing
object with distinct face art. The HTTP adapter in **I01e** must route normal card
object requests to these files and use ordinary image content types/statuses.

`manifest.json` and `import-cases.json` are **harness inputs, not provider payload
fields**. Roles must never enter application parsing or become special-case IDs.
The validator checks the subset of provider fields used by these test inputs; it
does not claim validation against an entire upstream provider schema or execution
of the as-yet-unimplemented normal importer/Android image decoder.

The checked-in synthetic origin is `http://synthetic-source:8080`. I01e configures
the isolated source, and I01f qualifies device reachability. To materialize a served
copy at a measured device-reachable origin without changing IDs or app binaries:

```sh
python3 scripts/synthetic-catalogs.py --write --output /tmp/opencode/catalog-source --origin http://127.0.0.1:18081
python3 scripts/check-synthetic-catalogs.py --directory /tmp/opencode/catalog-source
```

This only writes local fixture files; **it starts no server, deployment or device**.
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
  are 96×128 abstract tile mosaics, not copyrighted card layouts. Real image fetching,
  byte/pixel bounds, text fallback and credential isolation remain I05 obligations.
- v2 retains existing UUIDs, changes the ordinary creature's text/default `2/3` to
  `5/3` and its art, and adds one token. Other art is byte-identical for cache reuse.
  Existing accepted game definitions must retain v1 data. Publishing v2/source outage,
  interrupted/corrupt updates and stale-opponent journeys are future environment/
  downloader/normal-app checks, **not fixture-validation passes**.
- Import cases include common plain/explicit-printing, Deck/Sideboard, `SB:` and
  headerless blank separator formats, unknown/ambiguous names and missing printing.
  Expected text/partitions are inputs to I06's real parser tests, not a second parser.

## Reproduce pinned files

```sh
python3 scripts/synthetic-catalogs.py
python3 scripts/check-synthetic-catalogs.py
python3 -m unittest discover -s tests -p 'test_foundation_data.py' -v
```

Generation is offline Python stdlib only: UUIDv5 namespace + dataset/role, sorted
UTF-8 JSON, fixed dates, gzip mtime zero, SHA-256 content manifests and integer RGB
tile art encoded as PNG with CRCs. `--write` regenerates; default checks bytes and
rejects unexpected files without deleting them. Retain pinned artifact hashes if
future compression-library versions produce different bytes; investigate/version an
intentional change rather than silently rewriting previously qualified fixtures.
The independent validator decodes PNG chunks/pixels and checks relationships,
defaults, identities, update deltas and file/content hashes. Mutation regressions
reject tampering and malformed reciprocal relationships even after rehashing.

No normal downloader, import parser, HTTP service, game fixture injection or Android
acceptance is implemented by these files. Follow the
[E2E strategy](../../../docs/design/05-verification/e2e-strategy.md): users/decks-only
pre-scenario helpers, all measured actions through both normal UIs, reset only
between independent tests and retain state within recovery journeys.
