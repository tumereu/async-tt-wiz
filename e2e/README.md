# Host-side E2E boundary

**D-307 delivery-first operation:** [run/fix affected checks](../docs/agent/delivery-first-execution.md)
and reuse valid child evidence. Do not repeat the historical clean/full-device
qualifications below at every task/ancestor boundary. The
[paused application](../docs/agent/delivery-first-application-2026-10-22.md) retains
pending three-device scaffold qualification in B8 and records the ambient-inventory
oracle repair; one-device Appium/storage execution remains I01f2, still unperformed.

Windows/WSL **I01f1a device-service lifecycle is qualified**: see
[device commands and limits](devices-README.md). Actual owned API29/Appium
readiness, foreign-resource/mapping refusal, timeout/service-loss invalidation,
interrupted-launch recovery, native cleanup and fresh same-port cold boot pass
against a fresh locked tool installation. I01f1b1 additionally qualifies normal-APK
install/production-launcher startup capture and read-only idle storage evidence;
I01f1b2a qualifies measured public-only device-to-real-Compose transport and
fail-closed owned route lifecycle. I01f1b2b now qualifies the combined normal-scaffold
startup/environment handoff, failure recovery, host reset and retained restart;
Kotlin sessions and instrumentation remain I01f2/I01f3.
[I01f1b2 parent qualification](../docs/agent/implementation-runs/20261022T025919Z-a5658aee.json)
now checks its two children together: clean locked builds, full actual-emulator
failure/recovery/reset/retention, exact cleanup and strict unrelated inventory
pass. An initial transient-listener inventory failure is retained as diagnostic;
the unchanged fresh repeat passes. Only that parent closes, not its ancestors,
product E2E, independent B1 review or any release gate.

[I01f1b startup/routing parent qualification](../docs/agent/implementation-runs/20261022T034912Z-d4685909.json)
now checks the completed normal-APK startup and public-routing/handoff children
together: fresh actual API29 non-GMS startup/transport, fail-closed recovery,
retained restart, explicit host reset and strict inventory/owned cleanup pass,
with clean locked builds and retained evidence. The initial inventory failure
and supplemental missing-XML artifact failure remain diagnostic; their full
fresh device repeat and clean rebuild/artifact recheck pass respectively.
Only I01f1b closes; I01f1/I01f/I01, Kotlin/Appium/instrumentation, multi-device,
product E2E, independent review and release obligations remain pending.

The Kotlin/JUnit host runner's current runtime test qualifies only its Java
toolchain. The **test-only** [Compose topology](compose.json) and
[host controller](environment.py) now provision the real application image,
PostgreSQL17 and the [synthetic HTTP/art/SMTP adapters](adapters/README.md).
No alternate backend/APK, account/game/action injection or production deployment
is supplied. This Gradle module intentionally has no product-module dependency.

**I01e combined qualification:** the adapter and Compose children pass together
from fresh locked real-image builds: bounded readiness, retained restart,
repeated independent-scenario reset/failure recovery, private administration,
simultaneous Testcontainers isolation, redacted capture and exact cleanup. See the
[parent record](../docs/agent/implementation-runs/20261021T233041Z-1b76a4c6.json)
and earlier [I01e2 integration](../docs/agent/implementation-runs/20261021T223211Z-d5a50a9e.json).
All 37 service/six shared tests, 18 controller/11 adapter/21 fixture-ledger tests,
the network-isolated adapter image suite and complete Compose lifecycle pass.
A separate real Testcontainers transaction survived 2,254 probes across resets;
both cleanup directions preserve the other owner and unrelated resources.
**Still pending:** I01 combined qualification;
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

The local WSL qualification exposed an intermittent reset on a newly Docker-published
JDBC connection after PostgreSQL's ready logs; a ten-second retry alone was
insufficient. The test-only `OwnedPostgres` helper now reserves an OS-selected port
on **127.0.0.1**, publishes that exact loopback binding and verifies bounded actual
JDBC startup. Ordinary test operations still fail immediately on connection loss;
authentication/schema errors are not retried. Thirty fresh startup trials,
deterministic retry/timeout/error regressions, all 37 service tests and two complete
Compose/Testcontainers qualifications pass after this change. Failed experiments
and their actual XML remain in the integration record; this does not establish a
general Docker/WSL root cause or qualify a remote daemon. No product/runtime/build
input or dependency was changed by this repair.

The parent qualification retained one further **unexplained** failure: the real
migration subprocess returned exit 1 in a service test after database startup.
The original log was lost with JUnit's temporary directory. The test now retains
redacted migration output **before** asserting its exit code, on failure as well
as success; no retry or weaker assertion was added. All seven process tests and
the subsequent complete 37-test service suite pass, as do the Compose suites.
This does not establish a root cause or prove the earlier startup issue fixed;
the failed output/XML remains diagnostic in the parent record. If it recurs, use
the newly retained process output to investigate before accepting a handoff.

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

Only the **two loopback public/source ports** are allowed for the now-qualified
[I01f1b2a per-device ADB reverse mapping](devices-README.md#public-only-environment-routes--i01f1b2a)
(same host/device port numbers preserve art URLs). The actual API29 non-GMS
transport check passes backend public 404 and both synthetic catalogs/art, private
separation, unavailable transport/environment failure/recovery and exact cleanup.
That transport-only check is not the combined normal-APK handoff (supplied by
I01f1b2b and qualified together in I01f1b2) or a product HTTP client.
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
