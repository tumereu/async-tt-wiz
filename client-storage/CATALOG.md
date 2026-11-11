# Public catalog

`CatalogRepository` stores replaceable public generations of the card records separately from the private accepted
replica. `AndroidCatalogStorage` uses the Android SQLDelight driver; host tests use file-backed JDBC SQLite.

The card records are the ATW server's card catalog (docs/specs/web-client.md section 3), the one the app was built for
(`BuildConfig.CATALOG_CHECKSUM`; local and test builds without it take the server's current one). `HttpCatalogSource`
reads `GET /v1/catalog` and `GET /v1/catalog/{checksum}` from the app's API origin, unauthenticated, no redirects.

`CatalogInstaller` downloads the gzip file, indexes it record by record into an unpublished generation (each printing as
the record the app keeps, its search traits, the sets with their support marks and the supported printings), checks the
file's checksum while reading, validates the index and atomically publishes the generation pointer. A failure leaves
the previous records readable. Accepted deck/game definitions remain independent snapshots, never resolved again
through newer records.

The app (`CatalogController`) compares the installed records with its catalog on start and installs it when they
differ, trying a failed download once more by itself before offering Retry. Nothing waits for it but the controls that
read card records.

Tests index invented MTGJSON fixtures with the catalog build's MTGJSON import (`:catalog`'s `src/mtgjson`, compiled into
the tests only).

```sh
./scripts/gradle.sh :client-storage:testAndroidHostTest --tests '*CatalogTest'
```
