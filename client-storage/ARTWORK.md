# Lazy public artwork — I05b

Artwork is disposable display data, never a definition authority or a save/Ready
prerequisite. No images are fetched during catalog installation. Missing, unsafe,
unmapped or unavailable art remains ordinary plain text.

## Consumer boundary

- Query the actual `CatalogPrinting` through `CatalogRepository`, then construct
  `ArtworkReference.from(record, version, size)`. Use the retained source
  date/checksum for `version`, not just the MTGJSON schema version. `side` maps to
  its explicit face index. Exact Scryfall printing ID, face, size and version are
  part of every image key. Metadata is shared between those faces/instances.
- Later accepted deck/game consumers retain that reference with their immutable
  definition. Never resolve an accepted snapshot through a newer local catalog.
  A missing Scryfall mapping does not invalidate arbitrary card semantics.
- `HttpArtworkRepository` reads only provider card-object image URLs. A back face
  without its own face-level image is missing, **not** the front image. Single
  printing images are supported at face 0; there is no name search/substitution.
  Custom URLs can be supplied explicitly without changing definition semantics.
- Android consumers use `CardArtwork` / `CardArtworkRequest` and the process-wide
  `PublicArtwork` service. The native composable shows the supplied plain-text
  fallback while unavailable/loading, clears old identity immediately on rebind,
  and cancels its Coil request on disposal. Image loading is off-main, with two
  concurrent fetch/decode slots. No Activity, account token or private record is
  retained by the service. The import preview/inspector/table consumers belong to
  I06/I07/I12/I15–I19; this unit adds no showcase or new catalog browsing feature.

## Network, retries and caches

- Dedicated OkHttp client, no service client/interceptors/credentials, proxy,
  cookie jar or automatic redirect handling. Public HTTPS/443 only; reject
  credentials, local/private/link-local/metadata and special-use addresses.
  Validate every redirect (maximum three) and DNS results; OkHttp's actual
  connection DNS also uses the vetted addresses, preserving TLS hostname checks.
- One process-wide installation service throttles all requests/redirects to at
  most five/sec without bursts. Honor 429 `Retry-After` seconds/HTTP dates and
  global cooldown; persist the per-key cooldown/backoff in the encoded cache.
  Cache 404 for 24 hours, transient failures for 1 min → 5 min → 1 hour. Retries
  are demand-driven, never per animation frame. Metadata/success validators last
  24 hours; use ETag/Last-Modified on revalidation, retain good bytes on outage.
  Per-key in-flight coalescing prevents repeated metadata/image network work.
- Bound metadata to 1 MiB/depth 32, images to 10 MiB, and each fetch/redirect chain
  to a 30-second network deadline (10-second connect/read bounds). Accept only
  PNG/JPEG/WebP signatures/types, not HTML/SVG or executable content. Android
  checks bounds **before** bitmap allocation: max 4096 per dimension and
  16,000,000 pixels. Decode at the requested scene resolution and within the
  decoded cache budget, not a full-size bitmap for every thumbnail.
- Public encoded entries use one atomic file for bytes/validators and a 256-MiB
  disk LRU. Recover interrupted `.art.part` writes and trim on reopen. Filenames
  are hashed; eviction never reaches the catalog/private database. Coil's strong
  decoded-memory LRU is min(64 MiB, one-eighth app heap), with no weak cache;
  Android low-memory/background callbacks trim it. Live consumers still own
  their visible bitmap references; whole-scene workload measurement remains I33.

## Configuration and dependency provenance

Release defaults are `https://api.scryfall.com/cards/` with public-HTTPS policy.
Supply `-PatwArtContact=<operator contact URL/email>` for the identifying app
version/contact User-Agent before real provider/release use. This environment has
no owner-provided public contact and makes no real provider/rights-clearance claim.
For isolated debug tests only, `atwCatalogTestOrigin=http://127.0.0.1:<port>` and
`atwCatalogPath=/prism/` also set the separate artwork policy/base to that explicit
source's `/prism/scryfall/cards/`. Release constants never inherit that exception.
The synthetic adapter rebinds its image URLs; runtime has no fixture-role/ID logic.

Qualified pinned additions (strict locks cover both variants):

| Component | Provenance/license |
| --- | --- |
| Coil 3.3.0 (`coil-compose`, core/Android adapters) | [Published POM](https://repo.maven.apache.org/maven2/io/coil-kt/coil3/coil-core/3.3.0/coil-core-3.3.0.pom), [source](https://github.com/coil-kt/coil/tree/3.3.0), Apache-2.0 |
| OkHttp 4.12.0 | [Published POM](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.pom), [source](https://github.com/square/okhttp/tree/parent-4.12.0), Apache-2.0 |
| Okio 3.6.0 (library host)/3.15.0 (Coil app resolution) | [Published POM](https://repo.maven.apache.org/maven2/com/squareup/okio/okio-jvm/3.15.0/okio-jvm-3.15.0.pom), [source](https://github.com/square/okio), Apache-2.0 |

Coil's normal network module is intentionally **absent**. Its custom fetcher uses
the validated repository and bounded BitmapFactory decoder, so a generic Coil URL
loader cannot silently bypass the network/image policy. Existing Compose BOM,
Kotlin/JVM/Android baselines remain; transitive AndroidX/JetBrains Compose variants
are recorded in locks and qualified by the affected APK/lint/release compilation
and actual API29 loader tests. This is library provenance, not upstream-art rights.

## Checks and remaining qualification

See [the run report](../docs/agent/implementation-runs/20261022T110105Z-ae8e28e3.json).
Focused host tests exercise both synthetic catalog families/versions, both faces,
disk/restart reuse, missing/wrong mappings, unsafe bytes, retries/validators/rate,
DNS/URLs/redirects, credential isolation, coalescing and disk eviction. Actual
API29 tests exercise hosted catalog-to-art resolution and production
Coil/BitmapFactory/text/rebind/disposal, including a delayed real HTTP response.

The original native ContextWrapper callback-recursion failure and test-clock
pending-composition failure are retained diagnostics; the narrow corrections pass.
This is not a normal import/inspection journey, a whole P/T scenario pass, a
physical performance measurement, final UI comparison or independent B2 review.
I33/I34 retain realistic concurrent scene/cache pressure, broader conditional
HTTPS/DNS-rebinding transport exercises and full-suite/kill/recovery assertions;
I35/I36 retain broader compatibility/upgrades/distribution/rights and final UI.
