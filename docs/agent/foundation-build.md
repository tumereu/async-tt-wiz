# Initial build foundation — I01a

Qualified 2026-10-21. This is an implementation/build record, not B1 review or R1
acceptance. See the [run report](implementation-runs/20261021T170500Z-fc15d7bd.json)
for exact commands, retained outputs and failed development attempts.

## What exists

| Module | Initial responsibility / boundary |
| --- | --- |
| `:core` | Kotlin Multiplatform common semantics; canonical UUID value type only. Separate JVM21 and Android JVM17 artifacts; no generated/database/UI/framework model types. No game processor yet. |
| `:protocol` | Shared protocol-v1 scalar codecs; UUID strings and nonnegative signed-64-bit revision strings, including values beyond JavaScript's exact number range. No command envelopes, canonical-object hashing or networking yet. |
| `:client-storage` | Empty Android adapter boundary, consumed by the app; real SQLDelight storage/migrations are I01d. No persistence or outgoing journal is claimed. |
| `:androidApp` | Normal package `org.asyncttwiz.android`, versionCode 1 / versionName 0.1.0; branded fullscreen Compose launcher, not a second test application. Account UI begins in I02. |
| `:server` | JVM21 shared-module consumer with a real classpath test. Spring/database/image/health implementation remains I01c; no fake running service or ready endpoint. |
| `:e2e` | Kotlin/JUnit JVM21 host boundary; its current test checks the host runtime only, not product E2E. No product-module dependency or injected game actions. |

The Android instrumentation source uses the **normal launcher activity**. Its APK
assembles here; execution and one/two/three-device Appium qualification remain I01f.
There is no account, local authoritative game, licensed dataset, backend deployment
or gameplay capability in this scaffold.

## Reproduce

Prerequisites: JDK21 (`JAVA_HOME`), Android SDK (`ANDROID_HOME`) with
`platforms;android-36` and `build-tools;36.0.0`, accepted SDK licenses, network
access to the wrapper/plugin/Maven repositories on initial dependency acquisition.
No machine-specific `local.properties` is committed. Builds run with JDK21 while
Android bytecode targets Java17; shared JVM/server/host artifacts target Java21.

```sh
./gradlew --version
./scripts/gradle.sh --no-daemon --console=plain clean \
  :core:jvmTest :protocol:jvmTest :server:test :e2e:test \
  :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest \
  :androidApp:lintDebug verifyModuleBoundaries
python3 scripts/check-foundation.py
python3 scripts/inventory-environment.py
python3 scripts/check-foundation-licenses.py
```

`scripts/gradle.sh` scopes `java.net.preferIPv4Stack=true` to tooling JVMs **on WSL
only**, matching the measured host socket limitation. It does not change Android,
server defaults, global Java configuration or the permission profile. It invokes
the checked-in standard wrapper. No system Gradle installation is required.

To retain a command's combined output without losing CRs/trailing spaces:

```sh
python3 scripts/capture-check.py /tmp/opencode/build-evidence.json -- \
  ./scripts/gradle.sh --no-daemon --console=plain :core:jvmTest
```

The JSON contains UTF-8 `output` (or `outputBase64`), its SHA-256 and actual exit
code; decoding recovers the exact bytes. The helper returns the command's exit
code. Run-owned committed evidence uses the same format. APKs/Gradle working files
remain ignored; APK hashes, manifests, signer checks and executed JUnit XML are
retained in the run's evidence instead. Debug keys make APK hashes machine-specific,
not a claim of bit-identical signed APKs across machines.

## Dependency qualification

- Standard **Gradle 9.7.0** wrapper, HTTPS distribution SHA-256 pinned in
  `gradle/wrapper/gradle-wrapper.properties`; generated wrapper JAR independently
  compared with Gradle's published wrapper checksum.
- Kotlin/KMP/Compose compiler **2.4.21**; Android Gradle Plugin **9.3.3** and the
  supported `com.android.kotlin.multiplatform.library` plugin, not deprecated KMP
  `com.android.library` integration. AGP 9.3 requires Gradle ≥9.5; Kotlin's published
  2.4.21 compatibility range reaches Gradle 9.7 / AGP 9.3, with this patch pairing
  qualified by the actual clean Android/JVM build.
- Serialization **1.12.0**, Compose BOM **2026.06.01** (Compose 1.11.4 / Material3
  1.4.0), Activity Compose **1.13.0**, AndroidX Test runner **1.7.0** / ext JUnit
  **1.3.0**, host JUnit **6.1.3**. Exact transitive/tool versions are in the generated
  module locks and root plugin-classpath lock; the version catalog owns direct
  selections. The newer BOM 2026.09.00 failed compile-SDK metadata checks because it
  needs API37; the passing SDK36-compatible line was selected instead of raising SDK.
- Java used: Temurin **21.0.12.1+1-LTS**. SDK36 platform revision **2** and build-tools
  **36.0.0**. Metadata/manifests, API29-compatible APK signatures, classfile majors
  and Android lint pass. These are **build-time** API29 compatibility evidence,
  not a claim of runtime qualification or a complete phone/tablet matrix.
- Direct upstream artifact POMs declare Apache/EPL licenses; no commercial edition
  selected. Gradle is Apache-2.0. This is not complete redistribution notices or
  provider/art rights clearance. Spring, SQLDelight, jOOQ/Flyway/PostgreSQL, Ktor,
  Coil and Appium dependencies are added/locked/qualified as their actual modules
  begin using them in the recorded later tasks, not guessed into an unused catalog.

Ordinary builds use strict dependency locking and never rewrite locks. Intentional
dependency maintenance (review its diff and rerun the clean qualification):

```sh
./scripts/gradle.sh --no-daemon --console=plain --write-locks resolveAndLockAll
```

This resolves **all dependency graphs**, including release, test and tooling
configurations, refusing unresolved dependencies. It intentionally does not resolve
untyped Android artifacts: AGP uses typed artifact views, and raw file resolution
is ambiguous. Actual APK/lint tasks exercise those views. The root plugin classpath
is locked too. Do not edit generated lockfiles manually or use `--write-locks` as
an ordinary workaround for a failing build.

Official qualification references:
[Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html),
[AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes),
[Android KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin).

## Measured resource inventory and remaining limitations

See this run's `environment-final.json`; availability is not a test pass.

| Resource | Measured availability / remaining owner |
| --- | --- |
| Local JVM/build | Linux x86_64 WSL, JDK21, SDK36; wrapper, builds and checks executed under the unchanged session permissions. |
| Docker | Client/server 29.8.2, Compose 5.6.0; daemon x86_64, 12 CPUs / ~24 GiB. No containers present; none started by I01a. Real-image/Testcontainers work remains I01c/I01e. |
| API29 non-GMS | Windows emulator 37.2.12, WHPX installed/usable; default x86_64 API29 system image revision8 and three existing 720×1280/320dpi/2-core/2GiB AVD definitions. Not booted or installed in this unit; I01f qualifies the normal APK adapter. Linux has no `/dev/kvm`. |
| ADB | Linux client37.0.1. Existing port5037 server has no attached devices; dedicated5038 is closed. Inventory did not start a server or stop the unrelated existing server. |
| Appium host adapter | Node24.21.0 available; `appium` is not on this session's PATH. The earlier isolated installation is historical evidence, not requalified here. I01f must locate/qualify its explicit executable and isolated driver home. |
| Current Android / tablet | No current-API system image, tablet AVD or attached device available in measured SDK/device inventory. Not qualified; retain I35 resource limitation, without a hardware waiver. |
| Reference physical phone | No attached ADB device or present Windows Android/ADB USB device. Not available in this session; physical frame measurements and exact release gate remain I33. Emulator timings cannot replace them. |
| Signing / upgrades | Application and instrumentation use AGP's local debug signer and verify for API29. No release signing key/host was supplied or searched for. Stable external test/release signing and retained-data upgrades remain I35/operator gates. |
| amd64 / arm64 service | Docker builder advertises amd64/v2/v3 only, no arm64 worker/emulation. amd64 application image is not built yet (I01c); arm64 execution unavailable/unqualified (I35). No binfmt install, privileged container or Pi-capacity claim. |

`commonTest`, `jvmTest`, host `test`, and app `androidTest` are **actual** tested or
compiled source paths, checked against unchanged `runner.reviewTestPaths`.
`androidInstrumentedTest` is also an approved representative path for later shared
instrumentation; no such test ran here. New-plugin `androidHostTest`/
`androidDeviceTest` paths are not implicitly authorized. KMP Android host tests are
intentionally not enabled (AGP reports that warning); shared common tests execute
on JVM21, while actual device tests live under app `androidTest`. No required
test was skipped and called passed. Empty server/storage runtime sources and the
host-runtime-only E2E test are explicit, not application acceptance.

Lint retained **0 errors / 4 warnings**: newer compile/target SDK37 (SDK36 is the
agreed baseline), Android12+ data-extraction rules (there is no stored data yet;
the account/storage tasks must qualify private-data backup exclusion), and a
missing launcher icon (the initial launcher has a label, not a finalized icon).
No lint failure was suppressed or baselined. The library-symbol strip warning
packages the original AndroidX native library unchanged; it is not a failed test.

B1 review still follows I01–I04 together. I01b–I01f and the I01 parent integration
check remain open. No coverage ledger, UI mock comparison, performance or external
release gate is closed by this foundation.
