# Public catalog acquisition

`CatalogRepository` stores replaceable public generations in a separate directory,
never in the private accepted-replica database. `AndroidCatalogStorage` uses the
production SQLDelight Android driver; host tests use file-backed SQLite through
the SQLDelight JDBC driver. No private-schema migration is needed.

`HttpCatalogSource` defaults to MTGJSON v5 HTTPS metadata, gzip and SHA-256 sidecar.
Its own connections never use the account API client or bearer token. Redirects
are limited to three and the same configured origin. The isolated debug allowance
accepts only an explicitly configured IPv4 loopback origin; release ignores it.
There are no bundled records, provider calls in automated tests or backend proxy.

`MtgJsonCatalog` streams sets/records, bounds nesting/strings/records and rejects
duplicate fields/identities, unsupported schema, incomplete/truncated input and
invalid reciprocal face references. Printing UUID, set/collector, language, side,
Scryfall identity, tokens and exact original display/default JSON are retained.
Indexed exact-name/printing reads return ambiguity rather than substitute a match.
The installer verifies compressed checksum before streaming into an unpublished
disk index. A completed/validated closed generation is published by atomic pointer
replacement; a failed/interrupted update leaves the old generation readable.
Compressed input, decompressed input and the SQLite index each have a 4-GiB bound.
Free-space preflight estimates index space plus compressed input; ongoing checks
reserve 64 MiB. Limits fail explicitly, never install partial data. Query helpers
are ready for I06; accepted definitions remain independently owned by later tasks.

The normal app checks small metadata off the UI thread on foreground entry, at
most daily after success, with persisted 15-minute/1-hour/6-hour failure backoff
and a ten-second metadata deadline. Unchanged metadata does not trigger bulk work.
Manage decks offers source date, download size/network-cost notice, progress,
failure/retry and installed status. Initial acquisition always needs consent;
changed installed catalogs refresh on the public worker when unmetered, including
network changes while that process remains alive. Metered refresh needs consent.
The process may die without completing background work; the next foreground entry
uses persisted check/candidate metadata and retries normally. Navigation/login
never waits for acquisition, and public downloads carry no account credentials.

## Reproduce the affected checks

```sh
./scripts/gradle.sh --no-daemon --console=plain :client-storage:testDebugUnitTest --tests org.asyncttwiz.storage.CatalogTest
./scripts/gradle.sh --no-daemon --console=plain :androidApp:testDebugUnitTest :androidApp:lint :client-storage:lint verifyModuleBoundaries
```

Build the **normal** debug APK for an owned synthetic environment using its exact
public origins (not private database/administration ports):

```sh
./scripts/gradle.sh --no-daemon --console=plain -PatwApiOrigin=http://127.0.0.1:46745 -PatwCatalogTestOrigin=http://127.0.0.1:35691 -PatwCatalogPath=/prism/ :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
```

The numbers above are this run's ephemeral allocation, not production defaults.
`CatalogProductionTest` checks the real Android network/parser/driver using isolated
public files. `NativeCatalogTest` uses UI-created accounts and native controls for
consent, checksum failure/retry, both orientations, retained restart and v1-to-v2
update/recovery. Its host-only source control changes availability/edition only;
it does not create product records. See the
[I05a report](../docs/agent/implementation-runs/20261022T101044Z-1d047a5f.json)
for exact commands, initial diagnostics, retained-scenario rechecks and identities.

Gson 2.13.2 (Apache-2.0), already pinned in the version catalog for host tooling,
is now also an explicitly locked client runtime dependency. It supplies a strict
streaming JSON reader, not reflection-based application deserialization. Existing
SQLDelight/SQLite dependencies are unchanged; affected API29 builds/lint and actual
non-GMS Android execution qualify this addition. Public-provider/art rights are
still an external release gate.

I05b owns artwork and its stricter untrusted-URL/DNS/decoder/cache boundary. I06-I08
own import and immutable saved definitions; later lobby/game/history tasks own
stale-opponent and accepted-state journeys. I34/I35 retain full-suite, supported
device/upgrade and realistic large-catalog resource qualification; I36/B8 retains
the complete interface-to-mock comparison. No whole cross-feature scenario is
claimed passed by this records-only delivery.
