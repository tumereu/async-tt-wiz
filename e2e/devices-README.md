# Windows/WSL device services — I01f1a qualified

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
I01f1b still owns normal-APK launch/capture and actual public-only Compose routing;
I01f2 owns Kotlin/Appium sessions and the 14 actual storage instrumentation tests.
No application, Gradle, permission, authorization or global networking change is
made by these helpers. No default ADB or historical private tool archive is reused.

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
