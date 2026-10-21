# I01c3 result — implemented, not independently reviewed

The real normal service and explicit `migrate` command now share one local OCI
image. `server/Dockerfile`, `scripts/build-server-image.py`,
`tests/server/image_lifecycle.py` and [server instructions](../../../../server/README.md#local-application-image--i01c3)
provide the reproducible build/configuration/test interfaces. No image was pushed
to a registry, deployed or used for production orchestration.

## Actual identity and checks

- Qualified runtime base: Eclipse Temurin **21.0.12.1+1 JRE / Ubuntu 24.04**,
  immutable index `sha256:35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c`;
  amd64 platform manifest `sha256:da0f0d242bec39e3fe5c1ac4c415840f03be9cef848c243d723166708e02148c`.
  Upstream source revision/Dockerfile and actual Java/release/legal inventory are
  retained; redistribution/legal clearance is **not** claimed.
- Qualified application OCI index:
  `sha256:0b4a32636dfee7e9d3404edde4449e1997c40cda435a770574c59053fbe4ee54`.
  Two independently clean, build-cache-disabled, strictly locked Gradle builds
  regenerate the real PostgreSQL/Flyway/jOOQ schema. Runtime JARs/generated source
  hashes match byte-for-byte; distribution digest:
  `028abe92dfd0b42ca137efe25c137869674a47b45672a8dd790f40ae3fea3563`.
  BuildKit's changing attestation changes the outer index ID, not the matching
  runtime manifest/config/payload. Both exact built identities are retained.
- Two complete **four-scenario** image suites pass with fresh independently owned
  PostgreSQL/network/volume state. The actual nonroot/read-only image exports
  exactly the locked runtime JAR hashes; no application source, Gradle/JDK compiler
  executable, generator/Testcontainers/JUnit dependencies or secret is packaged.
  Only public upstream base layers precede the application payload.
- Actual image HTTP checks prove live/ready/public-port separation, detail-free
  responses, no-store headers, empty/unversioned/checksum/future/missing-singleton
  refusal without service migration/repair, explicit clean/retained migration,
  file-secret/configuration failures and redacted normal/error logs. The real
  image UID cannot read the intentionally unreadable secret.
- Two live image instances demonstrate ownership competition, dedicated-session
  takeover, persisted epoch advance, real DB stop/start, recovery schema
  revalidation/reload and no dual ready writers. Actual PID1 JVM SIGTERM exits
  below 30 seconds, including **10.374s** with PostgreSQL still paused in the
  repeated run. The same stopped service container and retained database restart
  preserve/advance the implemented singleton epoch without recreating storage.
- Shared JVM tests, all **34 server/database tests** (zero failures/errors/skips),
  independent byte-identical jOOQ regeneration and module boundaries pass. Retained
  JUnit XML/artifact output matches the packaged server JAR. Dependency/license
  provenance, 875-row source-pinned coverage accounting and Python checks pass.
- Failure logs precede each exact resource's cleanup. Every owned container,
  internal network, explicit/anonymous volume and temporary secret is removed.
  The settled final inventory exactly matches the pre-existing unrelated resources;
  no installed service JVM remains. Local built/base image caches are intentionally
  retained, never globally pruned. Compose was not used.

## Retained development diagnostics and repair

The first image suite failed because Docker29 suppresses port publication on an
internal bridge, and BuildKit's cache did not make the pinned base inspectable by
name. The controlled private-bridge experiment proves real host-to-internal-network
HTTP without publication/egress; tests now use that measured local Linux/WSL path.
The build explicitly pulls the same immutable base/platform for inventory. Database
readiness probes use TCP to exclude the entrypoint's Unix-only temporary bootstrap.
No runtime application change, weaker assertion or network-egress expansion was
needed. The original failure and exact cleanup remain diagnostic, not passing.
A one-line architecture inventory command's unmatched bracket is separately retained
as diagnostic; its corrected command passes. The raw Docker shell probe was denied
by the existing command profile; authorized Python/build/test commands qualified
the real daemon without any grant change.

## Remaining scope

Only local **amd64** is executed. The host/daemon are x86_64 and no ARM emulator
binfmt handler/native/remote arm64 runner is supplied. I35 must still qualify arm64
execution and full compatibility. Selecting its base manifest is not a pass.
Only ownership recovery/singleton reload exists: no account/game/inbox/outbox/mail/
push worker is invented or claimed recovered. This supplies foundation portions of
P-001/T-630/T-662, **not** complete product scenarios or UI feedback acceptance.
I01c/I01 combined qualification, storage/Compose/device work, independent B1 review,
all later tasks and all external release gates remain open. Only I01c3 advances.
