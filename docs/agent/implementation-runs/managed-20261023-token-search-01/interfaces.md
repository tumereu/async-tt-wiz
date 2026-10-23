# Installed token search: integrator interface

Owned boundary: `CatalogDatabase.kt` and `TokenActions.kt`. No TableToolkit mounting,
account/replica schema, download policy, shared lifecycle or authoritative-state edits.

## Storage (worker-only)

```kotlin
repository.searchTokens(query, cursor = null, limit = 40): CatalogTokenPage
repository.tokenGeneration(): String?
```

`CatalogTokenPage(generation, metadata, tokens, next)` contains bounded exact
`CatalogToken(printing, faces)` snapshots. Faces include explicit IDs, links and
unchanged provider JSON from that **same** generation. `next` is an opaque-to-UI
`CatalogTokenCursor`; pass it with the same query. A switched generation raises
`CatalogGenerationChangedException`, never silently appends another generation.
Absent catalog raises `CatalogNotInstalledException`. Limits are 1–100.

Search is literal substring matching using the existing ROOT lowercase normalization
and trimmed query, stable `(normalized, id)` order. Empty query browses tokens. No
SQL wildcard grammar. Only explicit `token=1` records participate. Public-generation
1/2/3-character postings and a token/name index avoid full catalog query scans.
Old generations receive the same transactional bounded-batch index backfill on the
catalog worker; no clearing, replacement of printing/face records or private migration.

## Native panel

Existing call remains source-compatible; integrator should use:

```kotlin
TokenCatalogDialog(
    controller = tokenLinks,
    definitions = retainedTokenDefinitions,
    loading = catalogDownloadLoading,
    catalogError = catalogDownloadError,
    enabled = sharedControlsReady,
    catalogIdentity = accountGamePlayAndCatalogGenerationKey,
)
```

**Supply an immutable identity key including account, game/play incarnation and the
installed-generation/lifecycle epoch.** Changing it disposes pending searches and
selection. `enabled=false` fences callbacks during disconnect/logout/not-ready states.
The default identity preserves old call sites, but is not a substitute for this host
identity boundary. The default source opens `AndroidCatalogStorage` on IO, maps
retained exact faces through the existing deck definition adapter, and checks actual
generation again before publishing or submitting downloaded choices.

The same panel contains `Search tokens`, loading, results, empty/error/Retry and
`Load more`. Blank is an ordinary choice, including without installed card records.
An installed back-face match remains selectable even when its name differs from the
canonical front definition; the panel does not apply a second front-name filter.
One selection calls the existing `TokenLinkController.create` request seam exactly
once; no local token mutation, quantity, preview, placement or extra Send.

For host testing/injection, `TokenSearchSource` exposes suspend `search`/`isCurrent`,
`RepositoryTokenSearchSource { repository }` supplies the real IO implementation,
and `TokenSearchController` owns personal panel state in a UI coroutine scope.
All sources must keep disk/CPU work off main; `isActive` must fence current identity.

Qualification: the early API compiled and was saved at `2cab743`. Affected storage
and native host checks, including the back-face-name follow-up, pass; exact commands,
the initial test-fixture compiler failure and retained JUnit are in the run report.
This is implementation evidence, not independent verification. Full toolkit
mounting, native I19 journey, B5, independent verification and final gates belong to
their existing owners.
