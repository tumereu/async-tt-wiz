# Host-side E2E boundary

The Kotlin/JUnit host runner's current runtime test qualifies only its Java
toolchain. The **test-only** [Compose topology](compose.json) and
[host controller](environment.py) now provision the real application image,
PostgreSQL17 and the [synthetic HTTP/art/SMTP adapters](adapters/README.md).
No alternate backend/APK, account/game/action injection or production deployment
is supplied. This Gradle module intentionally has no product-module dependency.

**I01e2a/I01e2b:** fresh provision, bounded readiness, retained restart, explicit
independent-scenario reset, simultaneous Testcontainers isolation, redacted capture
and exact cleanup. **Still pending:** I01e2/I01e combined qualification;
I01f Android/remote-ADB/Appium and actual storage instrumentation; later normal-UI
account/game journeys and independent B1 review. These host checks do not pass a
full T-551/T-630/T-662 scenario, any F01–F88 UI outcome or a release gate.

## Build prerequisites and qualification

Linux, Python 3.11+ stdlib, a **local Unix-socket Docker daemon** with direct host
access to its internal bridge, and the qualified local images/manifests:

```sh
python3 scripts/build-server-image.py
python3 scripts/build-synthetic-adapters.py
python3 -m unittest discover -s tests -p test_e2e_environment.py -v
python3 scripts/check-e2e-environment.py
./scripts/gradle.sh --no-daemon --console=plain :server:composeIsolationTest --rerun-tasks
python3 -m unittest discover -s tests -p test_synthetic_adapters.py -v
python3 scripts/check-synthetic-catalogs.py
python3 -m unittest discover -s tests -p test_foundation_data.py -v
python3 scripts/coverage-ledger.py
```

The builders run the normal locked service build and fixture validation; no
registry push. Provision requires their exact local image IDs, distribution and
adapter-input hashes. PostgreSQL uses the immutable digest in
[`server/database-images.properties`](../server/database-images.properties).
No new runtime dependencies/licenses are introduced by this stdlib controller.
The [server](../server/README.md) and adapter records own base/dependency provenance.
`:server:composeIsolationTest` is an explicit image-dependent host check, separate
from ordinary `:server:test`; neither builds its own alternate application. It runs
the reset checker while continuously querying a separate real Testcontainers
PostgreSQL connection/transaction, exercises cleanup in both ownership directions
and verifies unchanged unrelated resource inventories. No new library dependency.

Qualified here: Docker **29.8.2**, Compose **v5.6.0**, Linux/WSL2 **amd64**,
PostgreSQL **17.11**, actual Spring/JVM21 server and Python **3.13.16** adapter
images. Remote/SSH/TCP Docker contexts are refused, not silently used as deployment
targets. Docker Desktop/other network backends, arm64 and Android reachability are
not inferred from this host result. Image/build caches may remain after cleanup.

## Fresh instance and retained lifecycle

Run from the repository root. Choose a new UUID path; **do not create the directory
yourself**. The controller exclusively creates it with mode 0700:

```sh
INSTANCE="/tmp/opencode/atw-e2e-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
python3 scripts/e2e-environment.py --directory "$INSTANCE" provision
python3 scripts/e2e-environment.py --directory "$INSTANCE" wait
python3 scripts/e2e-environment.py --directory "$INSTANCE" capture
python3 scripts/e2e-environment.py --directory "$INSTANCE" restart --service backend
python3 scripts/e2e-environment.py --directory "$INSTANCE" restart --service database
python3 scripts/e2e-environment.py --directory "$INSTANCE" restart --service adapters
python3 scripts/e2e-environment.py --directory "$INSTANCE" restart --service all
python3 scripts/e2e-environment.py --directory "$INSTANCE" wait
python3 scripts/e2e-environment.py --directory "$INSTANCE" capture
python3 scripts/e2e-environment.py --directory "$INSTANCE" reset
python3 scripts/e2e-environment.py --directory "$INSTANCE" wait
python3 scripts/e2e-environment.py --directory "$INSTANCE" cleanup
```

Provision rejects an existing directory (even after cleanup) or any reused/foreign
container, volume or network. A new environment gets a named internal network,
two named volumes, five explicitly named runtime/setup containers, random file
secrets and two OS-allocated loopback ports. Optional provision arguments
`--backend-port 18080 --source-port 18082` reserve explicit **unused** ports; a
collision fails rather than attaching to somebody else's listener.

The database starts first; the **same server image's explicit `migrate` command**
must succeed before the backend starts. There is no service-start migration,
automatic wipe, production/default credential or external source/SMTP relay.
`wait` checks real supported-schema/writer health, singleton epoch/advisory lock,
writable adapter/listener readiness, both catalog sources and the actual public
forwarders. Only then does it write `ready.json` and print a JSON endpoint handoff.
Call `wait` and require exit 0 **before each setup**. This timestamped handoff is
not a perpetual health lease or a replacement for later normal-app assertions.

Use top-level `--timeout 30` before the operation to set the readiness retry
deadline (1–120 seconds; default 60). A final bounded in-flight probe may finish
after that deadline; Docker operations have separate 10/15/90-second command
bounds and container shutdown at most 30 seconds. Any migration/startup/readiness
failure exits nonzero, with **no ready handoff**. `wait`/`restart` invalidate the
previous handoff before checking/changing resources. Diagnose and explicitly
retry the same operation; never ignore a failed setup. Failed provision retains
its owned resources and redacted captures for inspection, not an implicit reset.

**Restart is not reset.** It stops/starts the same containers, retaining database
rows/epochs, chosen source editions/faults and exact captured deliveries. Backend
or database restart reacquires ownership; adapter-only restart does not reset
the backend epoch. The controller refreshes its live forwarder if Docker changes
private IPs even with unchanged container IDs. No state is cleared at an
in-scenario recovery checkpoint.

## Independent-scenario reset (host only)

Before **every independent scenario**, explicitly `reset` an existing owned
instance (or `provision` a new one), require exit 0, and require fresh `wait` before
handing the endpoints to the scenario. Reset is intentionally destructive only
within that exact synthetic instance. It keeps its project/configuration, immutable
images, synthetic fixtures, file secrets and two allocated port numbers, not prior
accounts/data/deliveries. It does not adopt foreign resources or revive a cleaned
instance. No reset route is added to the app/backend/public source API.

The controller captures the old scenario, removes its ready handoff/forwarder,
stops **all** implemented producer/setup containers, verifies stopped PIDs, removes
their exact container IDs and network namespaces, then verifies no old volume
users remain before deleting the two owned mutable volumes. It recreates them,
runs the same image's explicit migration, starts normal services and checks the
implemented empty baseline before publishing a new handoff. This replaces whole
stores rather than truncating known tables while workers can repopulate them.
Real tests hold PostgreSQL and SMTP DATA connections across reset and prove they
terminate without late writes/delivery into the new stores.

Current baseline: normal Flyway V1 history and service-writer singleton with newly
acquired epoch, no infrastructure probe or product records; source **prism v1 and
quilt v1**, zero captured deliveries and default `ok` source/mail controls. Fixtures
are the pinned immutable image data. The exact current store/schema inventory is
checked, not a claim that future account/game/outbox workers already exist. Those
features must extend baseline/readiness/reset checks as they are implemented.

`reset-pending.json` records an incomplete reset. A failed rebuilding reset exits
nonzero, retains redacted diagnostics and issues **no ready handoff or public forwarder**;
`wait` and `restart` refuse to bless it. Repair the diagnosed configuration/tool
failure, then explicitly retry `reset` (or collect evidence and `cleanup`). Do not
remove the marker or ignore setup failure. Successful reset writes
`reset-completed.json` with removed process identities and checked baseline, then
clears the pending marker. A stale completed record is not readiness evidence.
Ownership/target refusal happens before shutdown: it invalidates the old handoff
but leaves the refused scenario's resources untouched, never issuing a new one.

I01f must separately ensure **fresh Android app data** between independent tests;
this host reset never touches devices, ADB mappings or Appium. Preserve both host
and device data at all in-scenario recovery/restart checkpoints.

## Network and device handoff

All service containers join only the named **internal** bridge; no service has a
Docker-published port. Tests measure blocked external IP connections and DNS
from each real database/backend/adapter network namespace. On this Docker29 host,
an explicit `-p 127.0.0.1:port:8080` on an internal network is discarded: host
publication is unreachable, while direct private-bridge HTTP works. We do **not**
solve this by granting a noninternal/egress network.

Instead, an exact PID/start-time/command-owned stdlib host process inherits two
reserved **127.0.0.1-only** sockets and forwards exclusively to backend public
8080 and source 8080. It does not interpret/redirect HTTP, expose a proxy/CONNECT
API, read credentials or log payloads. `ready.json` gives these backend/source
origins; source printing/face image URIs use that same source loopback origin.
Private database 5432, backend health 8081, adapter admin 8081 and SMTP 2525 are
not forwarded or published. Host administration uses their direct private bridge
IPs and the separate adapter token-file interface; normal client paths return
404 for health/admin/reset/mail inspection, and private adapter admin rejects
missing tokens. Co-resident host administrators are trusted, not sandboxed.

Only the **two loopback public/source ports** are candidates for I01f's measured
per-device ADB reverse mapping (same host/device port numbers preserve art URLs).
Do not bind them to 0.0.0.0, expose private admin/DB/SMTP, change production TLS
trust or claim physical/emulator reachability from these host HTTP tests. The
normal backend currently returns 404 on public routes because product APIs are
not implemented yet; its separate real health/ownership boundary is what is tested.

## Evidence, ownership and future workers

`capture` writes a private `capture-<id>.json`: exact image/input identities,
safe container state, bounded redacted logs, applied command results, epoch and
served-copy source hashes. It **never** reads mailbox bodies, environment dumps
or raw Docker inspect/config secrets. File credentials are redacted. Preserve
needed redacted outputs in the owning run's committed evidence before cleanup;
an ignored/external capture alone is not durable run evidence.

Cleanup captures diagnostics first, checks exact ownership labels/project names,
refuses shared/extra resources and foreign/reused relay PIDs, stops the forwarder,
then runs scoped Compose down with volumes. It verifies disappearance of owned
containers/network/volumes, rebindable ports and removed temporary secret files,
and leaves `cleaned.json` plus private diagnostic metadata (not a reusable target).
No global prune, unrelated shutdown or recursive deletion is performed. The real
qualification also checks unchanged unrelated inventories and foreign-target
refusal. If cleanup fails, repair its concrete cause and retry; do not force or
discard another owner's resources. An instance lock excludes concurrent commands.

Only the writer-ownership scheduler/singleton reload exists in the backend.
There are **no account/game/inbox/outbox/mail-sender/push workers** to certify.
Future implemented workers/stores must join readiness, retained recovery,
quiescence/reset and safe capture before their routes/scenarios are admitted.
Reset stops and verifies old implemented producers/connections before replacing
mutable stores, not merely clearing the mailbox while a sender can repopulate it. Keep
Testcontainers integration databases separately owned; never point both owners at
this Compose environment. Any later pre-scenario initialization stays limited to
validated users/saved decks, never game/lifecycle/history/action state. Gameplay
journeys use both players' normal Android UIs.
