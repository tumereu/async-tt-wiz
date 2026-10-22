# Windows/WSL device controller — services and normal scaffold startup

**Device-service foundation only, not product E2E.** The test-only
[`devices.py`](devices.py) / [`windows-device.ps1`](windows-device.ps1) controller
has actually booted API29 default non-GMS Windows WHPX guests, checked their AVD
identity, boot completion and package manager, and checked an owned loopback
Appium process/listener/status. Controlled owned-Appium loss fails setup and
removes the ready handoff. Actual Windows-native console shutdown, dedicated ADB
shutdown and native watchdog exit pass, including a fresh same-port lifecycle.
The [completed child record](../docs/agent/implementation-runs/20261022T003723Z-932550a0.json)
retains checks/failures and the earlier
[repair checkpoint](../docs/agent/implementation-runs/20261022T000413Z-fd60bcea.json).

Actual qualification covers live foreign-port/existing-instance/lock refusal,
both real ADB mapping directions, stale Windows process identity refusal before
shutdown, nonresponsive/lost Appium failure, interrupted caller recovery while the
detached Windows guest survives, native cleanup and a fresh same-port cold boot.
All **33 focused / 83 total Python tests** pass, including separate Android-boot,
package-manager and Appium readiness timeouts, detached-launch errors, mismatched
AVD/serial/API/GMS, logical reservations and reused Linux/Windows identities.
Those mocked stage-specific tests are not claimed as actual induced Android boot
failures. The actual device checks run sequentially with other socket tests stopped,
so their unrelated-listener baseline is stable. An earlier concurrent regression
run closed its own mirrored Linux test sockets during the device inventory check;
that diagnostic failed, and the complete sequential rerun passes without relaxing
the unchanged-listener assertion. This is still not a complete scenario environment.
I01f1b1 now qualifies normal-APK launch/capture as described below. I01f1b2 still
owns actual public-only Compose routing and the combined environment handoff;
I01f2 owns Kotlin/Appium sessions and the 14 actual storage instrumentation tests.
No application, Gradle, permission, authorization or global networking change is
made by these helpers. No default ADB or historical private tool archive is reused.

## Normal-APK install, retained relaunch and capture — I01f1b1

The [startup child record](../docs/agent/implementation-runs/20261022T010348Z-d9861514.json)
retains an actual fresh API29 non-GMS WHPX lifecycle with the ordinary debug APK,
production `org.asyncttwiz.android.MainActivity`, readable 720×1280 screenshot,
native hierarchy and API29-valid signer/package/version identities. Visible title
and subtitle, absence of storage-opening/error copy, foreground activity/process
and a read-only copy of the real `no_backup/client-storage/replica.db` corroborate
completed storage startup. The foundation schema is version 2 with four **empty**
tables: no account/game/fixture records were seeded. Read-only inspection is an
infrastructure diagnostic, **not** the 14 pending storage instrumentation tests.
This branded scaffold is deliberately replaced by ordinary account entry in I02.

From the repository root, after a successful `provision` with the explicit tools
below (use a fresh synthetic guest, never a personal installation):

```sh
./scripts/gradle.sh --no-daemon --console=plain --no-build-cache \
  :client-storage:testDebugUnitTest :client-storage:verifySqlDelightMigration \
  :core:jvmTest :protocol:jvmTest :e2e:test \
  :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest \
  :client-storage:lintDebug :androidApp:lintDebug verifyModuleBoundaries
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> \
  install-app --apk /absolute/repository/androidApp/build/outputs/apk/debug/androidApp-debug.apk
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> launch-app
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> capture-app
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> capture
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> cleanup
python3 -m unittest discover -s tests -p test_device_app.py -v
python3 scripts/check-device-app.py --directory /tmp/opencode/atw-device-<fresh-32-hex> \
  --config /tmp/opencode/device-config.json \
  --apk androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --test-apk androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
```

`install-app` verifies a **regular absolute** normal debug APK with the configured
SDK's `build-tools/36.0.0/aapt2` and `apksigner --min-sdk-version 29 --print-certs`.
It refuses another package/launcher, min/target SDK mismatch, test-only or
instrumentation artifacts and invalid/unsigned bytes. It snapshots and re-verifies
the input before bounded `adb install -r`, then reads back the actual installed
base APK, verifies its hash/signer/version and resolves its normal launcher. A new
verified `app-installed.json` is published only after success. Failed install
invalidates prior installation/startup markers, never silently falling back to an
older APK. Reinstall is **not clear-data** and never uninstalls to fix an error.

`launch-app` checks ownership/services and the installed APK again, force-stops the
normal package and launches its explicit production component with `am start -W`.
It never clears data. `capture-app` performs the same checks/observation **without
launching**. Both require bounded actual branded foreground UI with storage-open
complete; no stale screenshot or just a successful launcher exit suffices.
`--timeout 1..300` bounds each services-readiness and UI-observation phase; individual
APK/install/hierarchy commands have separate 30/60/120-second bounds, so the whole
operation can exceed that phase timeout by bounded in-flight work. No driver
session, input action, instrumentation, alternate activity or failure hook is used.

Each attempt has a private `app-<id>` evidence directory, with actual XML/PNG,
installed APK copy, read-only idle database copy and timestamped `observed.json`.
`app-progress.json` points to the latest attempt even on failure; `app-ready.json`
exists only after successful observation, and failures/cleanup remove it. It is a
**snapshot**, not an enduring health lease: `wait` still certifies services only;
require new `capture-app` or `launch-app` for fresh startup evidence. Capture uses
only a UUID-like exact temporary guest XML path and removes only that file. Binary
transfer logs retain actual exit/error/size/hash, never decode APK/DB/PNG as text.
Regular service `capture` includes safe app status and does not require a healthy
app, so it remains usable before cleanup after failure.

Only the idle scaffold supports the raw no-backup DB diagnostic: active nonempty
WAL/journal is refused instead of claiming a coherent copy during future feature
writes. Host SQLite opens the copied file **read-only/immutable** and reports
integrity/version/table counts, inode and logical-content digest, not payloads.
This is not a future live-database export interface. An ordinary Android reopen
advances SQLite header change counters: retained raw hashes differ, while the
same device inode, schema/content digest, version/counts and all bytes outside the
two counter words agree. The initial overly strong raw-hash assertion and precise
diagnosis are retained; no product check was weakened to hide data loss.

Actual invalid and wrong-package artifact refusal, stopped-app capture timeout,
explicit recovery/reinstall and retained force-stop/relaunch pass. Cleanup captures
before exact native guest/ADB/Appium/watchdog shutdown, verifies ports/launchers
gone and preserves unrelated Android processes/listeners. Run actual device checks
**sequentially**, not alongside tests creating transient mirrored-loopback sockets.
Committed synthetic screenshot/hierarchy and command outputs are in the run;
large APK/DB/AVD artifacts remain private with hashes. Never publish raw future
account/token/message/DB/log captures without checking/redacting private content.

No normal backend connection, routing, Kotlin/Appium UI scenario, real touch,
instrumentation, downloader/account/gameplay/Canvas acceptance, physical frame
measurement, parent/group review or release gate is passed by startup observation.
I01f1b2/I01f2/I01f3 and the later parent qualifications retain those obligations.

## Explicit tools and configuration

Install **Appium 3.8.0 / UiAutomator2 8.7.0** into a new isolated prefix/home,
not a global npm/Appium installation. The two checked-in
[`device-tools`](device-tools/) npm lockfiles pin the full test-tool graphs:

```sh
python3 scripts/install-device-tools.py \
  --directory /tmp/opencode/atw-device-tools-<unique-id> \
  --node /absolute/path/to/node --npm /absolute/path/to/npm
```

The installer refuses an existing prefix, runs ordinary `npm ci` for both locks,
checks the input bytes remain unchanged, and verifies normal Appium driver
discovery. It does not synthesize Appium extension metadata. Its
`qualified.json` and retained command output record package versions/licenses and
manifest/input hashes. No product dependency is added. This run actually installed
`/tmp/opencode/atw-device-tools-932550a0` and ran the entire device qualification
against it. Prior checkpoint tools remain untouched. A failed installation retains
its prefix/diagnostics, never a qualified marker; choose a fresh prefix after repair.

Supply a JSON configuration with **every** field; there is no PATH/default-server
fallback. Example values are machine-specific, not portable defaults:

```json
{
  "node": "/absolute/path/to/node",
  "appiumMain": "/tmp/opencode/atw-device-tools-932550a0/appium/node_modules/appium/build/lib/main.js",
  "appiumHome": "/tmp/opencode/atw-device-tools-932550a0/driver",
  "androidHome": "/absolute/path/to/Android/Sdk",
  "linuxAdb": "/absolute/path/to/Android/Sdk/platform-tools/adb",
  "windowsSdk": "C:\\Android",
  "powershell": "/mnt/c/WINDOWS/System32/WindowsPowerShell/v1.0/powershell.exe",
  "adbPort": 15038,
  "consolePort": 5564,
  "appiumPort": 14753,
  "systemPort": 18210
}
```

Windows prerequisites: usable WHPX, emulator **37.2.12**, platform tools
**37.0.1**, installed `system-images/android-29/default/x86_64` revision 8.
Linux: Python 3.11+, Node **24.21.0**, matching SDK ADB **37.0.1**, WSL interop
(`wslpath`, explicitly configured PowerShell). Linux has no KVM here. This is not
physical-phone performance, current Android/tablet, signed upgrade or ARM64
qualification. No release gate changes.

## Commands and retained state

Run from the repository root with a fresh UUID path; let the controller create it:

```sh
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> \
  provision --config /tmp/opencode/device-config.json
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> wait
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> capture
python3 scripts/e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> cleanup
python3 -m unittest discover -s tests -p test_e2e_devices.py -v
python3 scripts/check-e2e-devices.py --directory /tmp/opencode/atw-device-<32-hex> \
  --config /tmp/opencode/device-config.json
```

`--timeout 1..300` goes before the operation and bounds actual readiness (default
180 seconds); individual ADB/HTTP probes and Windows operations have additional
bounded command timeouts. A ready descriptor records the exact serial, AVD,
remote ADB, Appium and reserved future UiAutomator2 port. It explicitly claims
**services only**, not a normal-APK assertion or even an Appium driver session.
`systemPort` is a logical reservation, not a fake running UiAutomator2 server.
Fresh instances currently get fresh dedicated AVD definitions/data under
`%LOCALAPPDATA%\async-tt-wiz-device-controller\atw-device-<32-hex>\avds`.
No historical smoke AVD/data is adopted. Cold boot uses `-no-snapshot`; there is
no wipe during a retained recovery checkpoint. Cleaned instances are not reused.

The Linux directory is private (0700), with an instance owner token, configuration,
baseline, process identities, command/version logs and capture/ready/cleaned
records. Windows records PID, creation time, executable and full command, including
the actual headless qemu child. Linux Appium records PID/start ticks/command and
its loopback listener's socket ownership. Controller locks exclude concurrent
operations. Cleanup captures first, refuses changed identities and never runs
global `kill-server`, broad emulator termination or network reconfiguration.
Temporary AVD/tool files are retained for diagnosis, not removed recursively.
This service-only controller creates **no ADB mappings** and refuses any existing
forward/reverse mapping before a ready handoff or shutdown. It never removes
foreign routes. I01f1b's routing extension and I01f2's driver-session ownership must
record their exact mappings and extend that check, not disable it or run
`--remove-all`. A preflight refusal owns no busy foreign ports: cleanup can close
its empty metadata instance without demanding another owner's listener disappear.
Duplicate provision and lock refusal do not invalidate an already running owner's
handoff; actual failed readiness does. Always require a fresh successful `wait`.

For a caller interrupted during detached Windows launch, retain its directory,
allow the bounded Windows helper to release its controller lock, then run explicit
`capture` and `cleanup`. Windows process identity/discovery and native shutdown
remain mandatory even if Python never wrote `started-windows.json` or launched
Appium. Use a **new** instance for the next cold boot. Do not delete ownership
records, adopt an orphan by PID alone or terminate unrelated broker/ADB processes.

## Tested host-specific repairs and remaining caution

- Windows ADB **rejects a hostname** in `-L tcp:127.0.0.1:PORT`; `-L tcp:PORT`
  binds loopback by default. The controller omits `-a` and checks the actual
  `127.0.0.1` listener, rather than trusting arguments.
- Do not hold probe/reservation sockets across Windows interop/child launch.
  Early Linux probes left broker-owned Windows loopback listeners; early .NET
  console reservations were inherited by Windows children and blocked boot.
  The repaired code uses read-only Linux listener inventory and closes Windows
  reservations before spawning any child, under its global Windows lock.
  Earlier broker-owned listeners are retained visibly in evidence; no authority
  to stop DllHost/WSL or change networking is inferred. All actual controller
  services/guests were cleaned. Fresh same-port reuse passes after the repair.
- WSL can keep a launcher alive until its Windows descendants exit **after**
  PowerShell has already printed its result. The controller detaches with
  redirected streams and waits for a complete JSON success/error reply, not the
  Linux proxy's exit. The helper is read through its exact checked-in WSL UNC
  path to avoid Windows' 32,767-character encoded-command limit.
- Use a Windows working directory and discover
  `qemu-system-x86_64-headless.exe`, not only the windowed executable.
- Use Windows-native ADB `emu kill`. Its shutdown watchdog
  (`emulator -kill QEMU_PID -sleep 20`) can outlive the guest/listeners; wait for
  those exact-target helpers too. A retained .NET command handle gives a bounded
  native command and its actual exit code; a short `Start-Process -PassThru`
  command had lost that exit status. The failed check and passing rerun are both
  retained. Do not weaken cleanup by ignoring the watchdog or native exit code.
