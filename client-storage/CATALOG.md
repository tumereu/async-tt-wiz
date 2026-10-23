# Public catalog

`CatalogRepository` stores replaceable public generations separately from the
private accepted replica. `AndroidCatalogStorage` uses the Android SQLDelight
driver; host tests use file-backed JDBC SQLite.

`HttpCatalogSource` downloads MTGJSON v5 HTTPS metadata, gzip and SHA-256 sidecar
using its own unauthenticated client. Redirects are limited to three within the
configured origin. Tests use synthetic local files, not live provider requests.
The debug-only `atwCatalogTestOrigin` accepts explicit IPv4 loopback HTTP; release
builds always use the provider's HTTPS origin.

The streaming parser bounds records/strings/nesting and rejects duplicate fields,
unsupported schema, truncated input and invalid reciprocal face references. It
retains printing UUIDs, set/collector/language, face order, Scryfall IDs, tokens and
exact provider display/default data. Queries report ambiguity instead of guessing
a printing. Token search cursors are bound to a catalog generation.

Installation verifies compressed checksums, builds an unpublished index, validates
it and atomically publishes the generation pointer. Failure leaves the previous
catalog readable. Compressed input, decompressed input and index each have a 4 GiB
bound; free-space checks reserve 64 MiB. Accepted deck/game definitions remain
independent snapshots, never resolved again through an updated catalog.

The app checks metadata off-main on foreground entry, at most daily after success,
with persisted failure backoff. Initial acquisition requires consent; installed
catalog updates may run on unmetered networks, with metered consent. Navigation
and login never wait for downloads. Restart can retry interrupted public work.

```sh
./scripts/gradle.sh :client-storage:testDebugUnitTest --tests '*CatalogTest'
```
