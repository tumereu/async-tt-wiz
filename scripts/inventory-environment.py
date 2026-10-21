#!/usr/bin/env python3
"""Read-only local foundation inventory; never boots devices or starts ADB/Docker.

Unavailable resources are reported, not silently turned into qualification passes.
The normal-APK runtime/three-device adapter is qualified separately in I01f.
"""
import os
from pathlib import Path
import platform
import shutil
import socket
import subprocess


def probe(command, timeout=60):
    print("\n$ " + " ".join(command), flush=True)
    if not shutil.which(command[0]):
        print("UNAVAILABLE executable")
        return
    try:
        p = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, errors="replace", timeout=timeout)
        print(p.stdout.rstrip())
        print(f"exitCode={p.returncode}")
    except (subprocess.TimeoutExpired, OSError) as error:
        print(f"UNAVAILABLE: {error}")


def main():
    print("Host:", platform.platform(), platform.machine())
    print("Linux /dev/kvm:", Path("/dev/kvm").exists())
    for key in ["JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT"]:
        print(key, os.environ.get(key, "<unset>"))
    for command in [
        ["java", "-version"], ["javac", "-version"], ["adb", "version"],
        ["docker", "version", "--format", "{{.Client.Version}} / {{.Server.Version}}"],
        ["docker", "compose", "version"],
        ["docker", "info", "--format", "{{.OSType}} {{.Architecture}} CPUs={{.NCPU}} Memory={{.MemTotal}}"],
        ["docker", "buildx", "inspect"], ["docker", "ps", "--format", "{{.Names}} {{.Image}}"],
        ["node", "--version"], ["appium", "--version"],
    ]:
        probe(command)
    sdk = Path(os.environ.get("ANDROID_HOME", "<unset>"))
    for pattern in ["platforms/*/source.properties", "build-tools/*/source.properties"]:
        for path in sorted(sdk.glob(pattern)):
            print("\nSDK package:", path.relative_to(sdk))
            print(path.read_text().rstrip())
    for port in [5037, 5038]:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=2):
                print(f"ADB TCP port {port}: open; inventory existing server only")
            probe(["adb", "-H", "127.0.0.1", "-P", str(port), "devices", "-l"])
        except OSError:
            print(f"ADB TCP port {port}: closed; NOT starting server")
    probe(["powershell.exe", "-NoProfile", "-Command", r"""
$ErrorActionPreference = 'Stop'
Write-Output 'Windows SDK emulator version / acceleration'
& C:\Android\emulator\emulator.exe -version
& C:\Android\emulator\emulator.exe -accel-check
Write-Output 'Installed Windows system images'
Get-ChildItem C:\Android\system-images -Filter source.properties -Recurse | ForEach-Object {
    Write-Output $_.FullName; Get-Content $_.FullName
}
Write-Output 'Owned API29 smoke AVD configurations (definitions, NOT boot qualification)'
$owned = Join-Path $env:LOCALAPPDATA 'async-tt-wiz-e2e-smoke'
Get-ChildItem $owned -Filter config.ini -Recurse | ForEach-Object {
    Write-Output $_.FullName
    Get-Content $_.FullName | Select-String '^(AvdId|abi.type|image.sysdir.1|hw.lcd.width|hw.lcd.height|hw.lcd.density|hw.ramSize|hw.cpu.ncore)\s*='
}
Write-Output 'Present Android/ADB USB devices (no serials)'
@(Get-CimInstance Win32_PnPEntity | Where-Object { $_.Name -match 'Android|ADB' } | Select-Object Name, Status) | Format-Table
"""], timeout=120)
    print("\nSigning inputs: release key/distribution NOT supplied by this task; no secret discovery.")
    print("Debug test APK signing verified separately; this is NOT a release/upgrade-signing pass.")
    print("API29 non-GMS: existing Windows image/AVD definitions; actual app device qualification I01f.")
    print("Current-Android/tablet/physical reference/ARM64: interpret measured probes; no presumed runtime pass.")
    print("Inventory complete. Missing resources remain limitations; external release gates unchanged.")


if __name__ == "__main__":
    main()
