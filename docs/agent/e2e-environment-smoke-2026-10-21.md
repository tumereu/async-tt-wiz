# Local E2E environment qualification — 2026-10-21

## Outcome and authority

**The local toolchain can drive one, two and three Android instances through
Kotlin/JUnit + Appium/UiAutomator2, with Docker services reachable from Android.**
A clean repeat with three wiped/cold-booted emulators and real PostgreSQL tests
passed. No other test machine was needed.

The owner expressly authorized installing missing testing tools and running this
isolated smoke test, then waiting for the UI agent before drafting the task plan
(D-301). This is **environment qualification, not product E2E acceptance**. No
application code/scaffold, real backend, production deployment or general stage
transition was created or authorized.

## Working topology

```text
WSL2: Kotlin/JUnit / Appium Java client
  → Appium on 127.0.0.1:14723, UiAutomator2
  → Linux ADB client, remoteAdbHost=127.0.0.1, adbPort=5038
  → dedicated Windows loopback ADB server
  → Windows WHPX-accelerated Android 10 / API-29 non-GMS emulators
      → 10.0.2.2:18765 → Windows/WSL mirrored loopback
      → disposable Docker Compose HTTP fixture inside WSL

WSL2: Kotlin/JUnit / Testcontainers → separate disposable PostgreSQL 17 → JDBC
```

This host is WSL2 on an AMD Ryzen 5 5600X, with about 24 GiB available to WSL and
about 48 GiB physical Windows RAM. Linux has no `/dev/kvm` and exposes no nested
virtualization flags. **Do not assume a Linux-hosted accelerated emulator works.**
The existing Windows installation has WHPX usable without elevation or reboot;
both `emulator.exe -accel-check` and actual startup logs confirmed acceleration.
No hypervisor, firewall or WSL networking configuration was changed.

The three owned AVDs use two guest cores, 2048 MiB guest RAM and 720×1280 at
320 dpi each. CPU virtualization is accelerated; graphics use the emulator's
software renderer. These are functional test devices, not physical-phone frame
performance evidence. Each Appium session uses an explicit serial and distinct
UiAutomator2 port (18200–18202). Appium session override is not enabled.

## Executed checks

| Check | Result / evidence |
| --- | --- |
| Java 21, Docker 29.8.2, Compose 5.6.0 | Available and working. |
| Docker Compose HTTP fixture + PostgreSQL 17 | Both healthy; SQL create/insert/read succeeded. |
| Independent Compose teardown/recreation | Passed; prior probe table absent afterward. One measured reset took 4.41 s. |
| Windows → WSL Docker loopback HTTP | HTTP 200, without exposing the fixture publicly. |
| Kotlin/JUnit compilation and real Testcontainers PostgreSQL/JDBC | Passed; query result 42, automatic owned-container cleanup. |
| One Android/Appium session | Native Settings navigation by W3C touch, visible Docker page and pointer-driven marker change passed; screenshots/XML retained. Session-plus-interaction portion ~11.19 s. |
| Two simultaneous sessions | Both passed after fixture reset repair; ~12.16 s for sessions/interactions. |
| Three simultaneous sessions | All passed with concurrent interactions; ~16.35 s for sessions/interactions. |
| Clean repeat | Stopped all owned AVDs, recreated Compose, launched all three with `-wipe-data -no-snapshot`; both JUnit tests passed, including all three UI/network checks. Gradle run ~39 s; three-session portion ~22.46 s. |
| Clean-repeat Android boot | Emulator-reported boot times: 24.975 s, 24.741 s and 24.220 s. Readiness additionally checked `sys.boot_completed` and the package manager; not just a launched PID. |
| Visible evidence | Native Display screen and Docker-page/changed-marker screenshots inspected, not just successful command responses. |
| Teardown | Sessions quit; all three owned AVDs, dedicated Windows ADB and Appium stopped; Compose removed. Owned ports 5038/14723/18765 closed and no Docker containers remained. Installed tools/owned AVD definitions retained. |

The one/two/three checks keep their sessions alive together; device interactions
then run concurrently. These timings include tooling overhead, are individual
observations, and are **not** application latency budgets, p95 measurements or
proof of long-running suite stability.

## Problems found and resolved

1. **Gradle's Java socket accept failed under this WSL configuration**, logging
   `java.io.IOException: Invalid argument`. Running build/test JVMs with
   `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true` fixed it. This was verified
   by compilation and executed JDBC/Appium tests. Keep it scoped to host tooling;
   it is not a product networking restriction or a reason to alter global settings.
2. **WSL launching a Windows emulator can keep the launcher waiting for its
   descendant**, even after PowerShell prints a PID. Detach the launcher with
   redirected input/output, then use bounded actual Android readiness checks.
   Windows paths in Python/PowerShell must be raw/escaped; an initial path-escape
   mistake was corrected before qualification.
3. **The first two-session attempt restored the fixture browser's old DOM** on
   the already-used device. The stock API-29 WebView shell did not reload it as
   expected on a repeated VIEW intent. Preserve that failure under `evidence/two`.
   The repaired independent test terminates the fixture browser before opening a
   fresh URL; it still requires initial marker → real touch → changed marker.
   It does not accept stale success text or relax the assertion. Two/three and
   clean-repeat runs then passed.
4. **Cross-host Linux `adb emu` returned no expected console result.** Use ordinary
   remote Linux ADB for Appium/shell traffic, but Windows-native ADB for owned
   emulator-console shutdown. Check the API-29 `ro.kernel.qemu.avd_name` first;
   never stop an unrelated serial or use broad process/Docker cleanup.
5. **Tool installation details:** the new Android CLI uses slash-separated SDK
   package names; its failed Windows architecture selection created an empty
   emulator package record. The stable Windows emulator ZIP was installed from
   Google's repository with its repository checksum checked. Testcontainers needs
   `postgres@sha256:…`, not the Compose-style combined tag-and-digest spelling,
   for this pinned image's name parsing. No check was bypassed.

Linux-only Appium Doctor reported the missing **Linux** emulator, plus optional
bundletool/GStreamer omissions; do not claim that doctor passed. External Windows
AVD management plus actual remote ADB/Appium sessions qualified the topology used
here. APK tests do not require bundletool or streaming video.

## Installed tools and retained evidence

Tool directory: `~/.local/share/async-tt-wiz-e2e-tools/`.

- Appium **3.8.0**, isolated `appium-home` with UiAutomator2 **8.7.0**.
- Gradle **9.8.2**, downloaded distribution SHA-256 verified.
- Smoke build: Java **21.0.12.1**, Kotlin **2.4.0**, JUnit Jupiter **6.1.3**,
  Appium Java client **11.0.0**, Selenium **4.51.0**, Testcontainers **2.0.5**,
  PostgreSQL JDBC **42.7.8**. These qualify this fixture, not the still-unbuilt
  complete Android/KMP/Spring dependency set.
- Linux SDK `~/Android/Sdk`: existing command-line/platform tools plus installed
  `platforms/android-36` and `build-tools/36.0.0`.
- Windows SDK `C:\Android`: Emulator **37.2.12**, API-29 default non-GMS x86_64
  system image **revision 8**; existing platform tools **37.0.1**.
- Owned Windows AVDs/logs:
  `C:\Users\Tuomas\AppData\Local\async-tt-wiz-e2e-smoke`.
- Digest-pinned Docker nginx/PostgreSQL images; exact digests are in the retained
  Compose fixture, not mutable `latest` tags.

The source fixture and evidence are archived outside Git at
`~/.local/share/async-tt-wiz-e2e-tools/smoke-20261021/`. Its README gives startup,
environment variables, test and cleanup commands. It includes Kotlin tests,
Gradle files, Compose/static HTTP fixture, Python AVD helpers, Appium logs,
screenshots, XML UI hierarchies, JUnit reports, failed-attempt evidence and clean
boot logs. `SHA256SUMS.json` inventories 258 files; its SHA-256 is
`07e4fd64da49f39a4dec2baad87b65a9ddc4ae745e6fc7ce2b90f0dda6485053`.
The original scratch directory was `/tmp/opencode/async-tt-wiz-e2e-smoke-20261021`.

No licensed card dataset, paid service, production credential or user database was
used. Raw local logs are not uploaded or published with the UI review document.

## What this does not pass

No normal product APK/Spring backend exists yet. Early implementation must still
qualify normal-app Compose/Canvas accessibility and actual tap/double-tap/hold/
drag/pan/pinch/animation targeting, synthetic catalog downloads, full two-player
UI journeys, reset isolation and failure controls. A browser fixture is **not** a
replacement Android client or an accepted gameplay test.

API-36/current-Android, tablet, signed upgrade/mixed-client, physical-phone frame
metrics, ARM64 application images, full-suite duration/stability and production
TLS/authentication are not qualified by this test. Keep those tasks/gates visible;
do not infer unsupported minimum-OS changes, production readiness or a waived
performance requirement from an emulator smoke pass.
