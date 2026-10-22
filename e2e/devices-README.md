# Windows/WSL device services — I01f1a repair checkpoint

**Work in progress, not completed I01f1a or product E2E.** The test-only
[`devices.py`](devices.py) / [`windows-device.ps1`](windows-device.ps1) controller
has actually booted API29 default non-GMS Windows WHPX guests, checked their AVD
identity, boot completion and package manager, and checked an owned loopback
Appium process/listener/status. Controlled owned-Appium loss fails setup and
removes the ready handoff. Actual Windows-native console shutdown, dedicated ADB
shutdown and native watchdog exit pass, including a fresh same-port lifecycle.
The run report retains failures as well as the tested repairs:
[I01f1a checkpoint](../docs/agent/implementation-runs/20261022T000413Z-fd60bcea.json).

The **full Windows failure/foreign-resource matrix is still pending**. In
particular, finish explicit forward/reverse-mapping refusal, detached-launch and
boot/package-manager/Appium timeout regressions, live listener/instance collision
checks and complete interruption/recovery qualification before marking this child
implemented. Do not use this partial handoff as a completed scenario environment.
I01f1b still owns normal-APK launch/capture and actual public-only Compose routing;
I01f2 owns Kotlin/Appium sessions and the 14 actual storage instrumentation tests.
No application, Gradle, permission, authorization or global networking change is
made by these helpers. No default ADB or historical private tool archive is reused.

## Explicit tools and configuration

This checkpoint installed **Appium 3.8.0 / UiAutomator2 8.7.0** in a new isolated
`/tmp/opencode/atw-device-tools` prefix/home, not a global npm/Appium installation:

```sh
npm install --prefix /tmp/opencode/atw-device-tools --save-exact appium@3.8.0
APPIUM_HOME=/tmp/opencode/atw-device-tools/appium-home node \
  /tmp/opencode/atw-device-tools/node_modules/appium/build/lib/main.js \
  driver install uiautomator2@8.7.0
```

Use a fresh owned prefix for installation; do not overwrite somebody else's tool
home. The actual package/lock hashes, package manifests/licenses and installation
output are retained in this run. Direct versions are pinned; a checked-in
reproducible transitive tool-installation mechanism remains qualification work,
not an inferred guarantee from these commands. The libraries are test-only.

Supply a JSON configuration with **every** field; there is no PATH/default-server
fallback. Example values are machine-specific, not portable defaults:

```json
{
  "node": "/absolute/path/to/node",
  "appiumMain": "/tmp/opencode/atw-device-tools/node_modules/appium/build/lib/main.js",
  "appiumHome": "/tmp/opencode/atw-device-tools/appium-home",
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
The full foreign-mapping and interrupted-launch recovery obligations above remain.

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
