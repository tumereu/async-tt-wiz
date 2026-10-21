# Isolated synthetic protocol adapters — I01e1

These **test-only** services are separate from the real backend and normal APK.
They supply real HTTP catalog/art responses and a real, non-forwarding SMTP sink.
They contain no downloader, accounts, password-reset sender/outbox, game engine,
initialization helper or gameplay/action endpoint. I01e2 owns Compose/reset of the
complete environment; I01f owns actual Android reachability and normal-APK tests.

## Build and qualify locally

From the repository root (Linux; Python 3.11+ and Docker):

```sh
python3 scripts/build-synthetic-adapters.py
python3 -m unittest discover -s tests -p test_synthetic_adapters.py -v
python3 scripts/check-synthetic-adapters-image.py
python3 scripts/check-synthetic-catalogs.py
python3 -m unittest discover -s tests -p test_foundation_data.py -v
python3 scripts/coverage-ledger.py
```

The builder pulls an immutable official Python **3.13.16 / Debian bookworm** index
`sha256:73aa5faee130c60da030e105ec971ccf57473fbd22ce73bd6fc9ba99a40b838d`
and builds only a local amd64 image: no registry publication/deployment. It uses
Python stdlib only, no pip dependencies or networked `RUN` commands. The qualified
base has SQLite 3.40.1 and zlib 1.2.13. Python's PSF-2.0 license and bundled notices,
SQLite's public-domain notice, zlib's notices and Debian package copyright files
stay in the image. The build manifest retains exact versions, package inventory,
license-file hashes, image ID and source/fixture hashes at
`e2e/build/adapters/image.json`. Original fixture provenance is
[CC0-1.0](../../tests/fixtures/catalogs/README.md).
This test infrastructure does not clear production provider/art rights or other
external release gates. arm64 execution remains unqualified, not inferred from
the multi-platform base index.

The image checker executes the socket suite against **the installed image payload**
as UID/GID 10001 with read-only root and **network=none** (loopback only). It also
runs the real image entrypoint as PID 1 with an independently owned retained volume:
bounded SIGTERM, same-container restart, selected source/delivery retention, two
empty resets and content-safe logs. It creates no published ports. It captures
owned logs before exact labeled cleanup and verifies unrelated Docker container,
volume and network inventories are unchanged. Image/build caches are retained.
The fixture and 875-item coverage checks are not normal-app acceptance passes.

## Local host invocation

Create a **new isolated instance directory**, never an existing application/private
path. Keep the generated harness token outside Git and out of app/backend config:

```sh
python3 -c 'from pathlib import Path; import secrets; p=Path("/tmp/opencode/my-adapters"); p.mkdir(mode=0o700); (p/"harness-token").write_text(secrets.token_hex(32)+"\n"); (p/"harness-token").chmod(0o600)'
python3 e2e/adapters/adapter.py --fixtures tests/fixtures/catalogs --origin http://127.0.0.1:18080 --state-dir /tmp/opencode/my-adapters/state --instance my-adapters --token-file /tmp/opencode/my-adapters/harness-token --http-port 18080 --admin-port 18081 --smtp-port 12525
```

The second command remains in the foreground; stop its exact owned PID with
SIGTERM/Ctrl-C. All three listeners default to `127.0.0.1`. Choose distinct owned
ports/instance directories for concurrent environments. The source origin is an
explicit **device-facing** HTTP(S) origin without a path, credentials or trailing
slash; it is not inferred from request Host. Host loopback in this example is **not
proof of Android reachability**. I01f qualifies measured host/ADB-reverse routes.

For containers use the built image ID with `--read-only --cap-drop=ALL
--security-opt=no-new-privileges`, a private persistent state volume writable by
10001:10001 and a read-only token-file mount readable by that UID. Required args:
`--origin`, `--state-dir`, `--instance`, `--token-file`. `--fixtures` is already
provided by the image entrypoint. Explicit `--bind 0.0.0.0 --smtp-bind 0.0.0.0
--admin-bind 0.0.0.0` permits the isolated container network to reach each separate
listener. **Do not publish administration or SMTP to devices/public interfaces.**
I01e2 must restrict admin to harness-owned access (host publication, if needed,
loopback only), SMTP to the isolated backend network, source to the measured test
device endpoint, and prevent external egress. Never mount a Docker socket, private
host directories or production secrets. This is not production orchestration.

## Ordinary source routes

For dataset `prism` or `quilt`:

| HTTP GET/HEAD | Content |
| --- | --- |
| `/<dataset>/Meta.json` | Active edition's ordinary MTGJSON v5 metadata |
| `/<dataset>/AllPrintings.json[.gz]` | Exact checked-in catalog/gzip bytes |
| `/<dataset>/AllPrintings.json.gz.sha256` | Plain hexadecimal gzip checksum |
| `/<dataset>/scryfall/cards/<printing UUID>` | Ordinary Scryfall-shaped card object; `.json` alias also works |
| `/<dataset>/v1/...` or `/<dataset>/v2/...` | Pinned edition of any route above |
| `/<dataset>/v<edition>/art/<face-art UUID>.png` | Original valid PNG, ordinary 404 for missing art, or intentionally broken bytes with `image/png` |

Configure a normal MTGJSON adapter's base as `/<dataset>/` and its Scryfall adapter's
base as `/<dataset>/scryfall/`; no app fixture-role/ID parser is needed. Two clients
can choose different families or pinned editions through source configuration.
Active versions default independently to v1. Catalog metadata has normal dates;
changing v1→v2 changes content date without pretending the provider schema changed.
The response shape, exact printing/face identities and each face's art URI are the
[I01b fixtures](../../tests/fixtures/catalogs/README.md), not a second catalog.

Only Scryfall image-URI metadata is rebound to the configured origin at startup.
Catalog, gzip, checksum and art bytes remain unchanged. Source files are hash-checked
then served from an in-memory allowlist. No directory/file server, redirects, HTML,
query-string administration, harness manifest/import cases or host-file routes.
Normal source requests never expose captured mail. Responses include MIME, exact
length and SHA-256 ETag; `no-store` ensures harness faults/edition switches are not
hidden by an intermediary (the future client still owns its normal asset cache).

## Harness-only administration

On the **separate admin port**, every route, including health, requires
`Authorization: Bearer <token-file contents>`. The 32-byte hex harness token is
never accepted in query strings and is never printed in routine logs. Reset/control
additionally require the exact stored `instance`, otherwise 409. A second process
cannot open the same state directory; a different owner cannot reuse/reset it.

```sh
python3 e2e/adapters/admin.py --url http://127.0.0.1:18081 --token-file /tmp/opencode/my-adapters/harness-token wait
python3 e2e/adapters/admin.py --url http://127.0.0.1:18081 --token-file /tmp/opencode/my-adapters/harness-token state
python3 e2e/adapters/admin.py --url http://127.0.0.1:18081 --token-file /tmp/opencode/my-adapters/harness-token inventory
python3 e2e/adapters/admin.py --url http://127.0.0.1:18081 --token-file /tmp/opencode/my-adapters/harness-token --instance my-adapters control --prism-version 2 --quilt-version 1
python3 e2e/adapters/admin.py --url http://127.0.0.1:18081 --token-file /tmp/opencode/my-adapters/harness-token --instance my-adapters reset
```

| Route | Contract |
| --- | --- |
| GET `/health/live` | Process/store responsive; detail-free status, no configuration |
| GET `/health/ready` | Three actual listeners, writable SQLite store, loaded hash-checked source and no selected source/mail fault; 503 otherwise |
| GET `/admin/state` | Owner/generation, selected editions/faults, delivery and SMTP connection counts |
| GET `/admin/inventory` | Fixture-manifest hash, configured origin and every **actually served** provider-file SHA-256; retain this for scenario evidence |
| GET `/admin/messages` | Ordered captured envelope sender/recipients plus exact DATA bytes as base64; **sensitive synthetic mail/token inspection only**, never routine CI logging |
| POST `/admin/control` | JSON `instance` and optional complete `versions: {prism: 1 or 2, quilt: 1 or 2}`, `sourceFault`, `mailFault` |
| POST `/admin/reset` | JSON with only `instance`; returns empty v1/no-fault baseline and incremented generation |

Source faults are `ok`, `unavailable` (503/Retry-After), `corrupt-gzip` (invalid bytes,
unchanged checksum) and `truncate-gzip` (advertised full length, partial transfer then
close). Mail faults are `ok`, `temporary` (451 at DATA) and `permanent` (550 at DATA).
CLI `control --source-fault ...` / `--mail-fault ...` selects them. Source failure
does not stop SMTP, nor mail failure source HTTP. Controls never change the backend.
CLI `wait` has a 15s default deadline (`--timeout`, maximum 120s) and **nonzero exit**
on unready/unauthorized/unreachable setup; all admin failures are nonzero. Never
ignore these exits or proceed with possibly dirty state. Direct HTTP clients also
need bounded deadlines. Do not publish the token or captured bodies in evidence;
retain served-copy inventory and safe counts/statuses instead.

## SMTP and lifecycle/reset boundary

SMTP defaults to 2525, with EHLO/HELO, MAIL/RCPT/DATA, SIZE, 8BITMIME, RSET/NOOP/QUIT.
This is a plaintext **isolated synthetic sink**, not production SMTP/TLS/credential
configuration. No AUTH, STARTTLS, VRFY/EXPN, DNS lookup, remote connection, forwarding
or relay exists. Any syntactically valid recipient is stored locally only; even an
external-looking recipient cannot cause a network call. Message bodies preserve
UTF-8 and dot-unstuffed CRLF bytes; capture commits before 250. Session 120s, individual
line 10s, 8 MiB DATA and 100-recipient limits bound malformed harness input. HTTP has
a 15s request deadline and 64 KiB administration body limit. Errors/normal logs do
not echo request targets, envelopes, body/token/credential values or host paths.

Fresh instance state is empty. SQLite commits retain deliveries, selected editions
and fault controls through normal stop/restart and abrupt process loss. **Restart
does not reset.** Reset commits a new generation, empty delivery store, v1 editions
and cleared faults without yielding to other request handlers; it closes old SMTP
and source connections. Old buffered/in-flight DATA must also pass its captured
generation before a store commit, so it cannot repopulate the baseline. Tests hold
a real delivery at DATA across reset, confirm its refusal and drained connection
count, and then prove a new-generation delivery works. A failed store/source open
refuses startup, never wipes/repairs a prior store or manufactures readiness.

**The complete-scenario harness must stop/quiesce the real backend and every other
old producer before adapter reset**; closing existing SMTP sessions cannot prevent
an old backend worker from opening a *new* connection afterward. I01e2 owns that
ordering plus database/other-worker reset, required empty-state checks, fresh app
state, independent Testcontainers isolation and targeted environment cleanup.
Preserve this store and backend/app state within recovery checkpoints. Future
account/mail/game/push workers must join full-environment readiness/reset when
implemented; this adapter does not claim their acceptance or recovery.
