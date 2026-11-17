# Lazy public artwork

Artwork is disposable display data, never definition authority or a Save/Ready
prerequisite. Catalog installation fetches no images. Missing/unsafe/unavailable
art remains plain text, without substituting a different card or face.

## Consumer API

Construct `ArtworkReference` from the exact catalog/accepted snapshot. Printing ID,
face, size and source version/date/checksum form the image identity. Never resolve
an accepted definition through a newer catalog. `HttpArtworkRepository` reads
Scryfall card-object URLs without name search; a missing back-face image stays
missing. Explicit custom image references do not change card semantics.

Android uses `CardArtwork`/`CardArtworkRequest` through process-wide `PublicArtwork`.
Loading is off-main with two fetch/decode slots; identity changes clear stale art,
disposal cancels requests, and text remains visible. Coil's generic network loader
is intentionally absent so it cannot bypass the validated repository/decoder.

## Network and caches

- Dedicated unauthenticated OkHttp client: no account interceptors, cookies, proxy
  or automatic redirects. Public HTTPS/443 only; vet DNS and every redirect (max
  three), rejecting private/local/link-local/metadata/special-use addresses. Actual
  connection DNS uses the vetted addresses with normal TLS hostname verification.
- Provider API (metadata) requests spaced 100 ms apart, at most six downloads at once,
  coalesced in-flight requests and persisted cooldown/backoff. Honor `Retry-After`; a
  failure (404 included) waits 1 second, doubling up to 30 seconds. Retry on demand,
  not per frame.
- Metadata max 1 MiB/depth 32, image max 10 MiB, network chain deadline 30 seconds.
  Accept PNG/JPEG/WebP signatures, not HTML/SVG. Check max 4096 pixels/dimension
  and 16 million pixels before allocation; decode at requested scene size.
- Atomic encoded bytes use a 1 GiB disk LRU; interrupted writes are cleaned on
  reopen. Art once downloaded is kept as it is, never requested again. Coil decoded
  memory is bounded to min(64 MiB, one-eighth heap) and trimmed on memory/background
  callbacks. Eviction never reaches private data.

Default source: `https://api.scryfall.com/cards/`. Supply
`-PatwArtContact=<operator contact URL/email>` for the app's identifying User-Agent
before real provider use. Debug synthetic artwork uses `atwCatalogTestOrigin` and
`atwCatalogPath`; release does not inherit the loopback exception. Operators remain
responsible for provider terms and artwork rights.

```sh
./scripts/gradle.sh :client-storage:testAndroidHostTest --tests '*Artwork*'
```
