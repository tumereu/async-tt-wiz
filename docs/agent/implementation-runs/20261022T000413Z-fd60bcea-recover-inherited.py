"""One-shot recovery of THIS run's failed inherited-socket launch.

Exact state/identity/parent checks precede stopping either recorded process.
No default ADB or broker process is targeted. Retain actual output.
"""
import base64
import subprocess

source = r'''
$ErrorActionPreference = 'Stop'
$d = Join-Path $env:LOCALAPPDATA 'async-tt-wiz-device-controller\atw-device-f66dc58d08154120ac0bd317c1a7879f'
$s = Get-Content -Raw "$d\state.json" | ConvertFrom-Json
if ($s.owner -cne '3517d104b06e416288211bbc2135dca0ead9d036f8cee9e962ebbf352a035305') { throw 'Owner mismatch' }
if ($s.processes.Count -ne 2) { throw 'Unexpected process set' }
if (Get-CimInstance Win32_Process -Filter 'ProcessId = 15748') { throw 'Launcher PID reused' }
$before = @(Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in $s.ports })
foreach ($l in $before) { if ($l.OwningProcess -notin @(15748,5228)) { throw 'Foreign port owner' } }
$checked = @()
foreach ($p in $s.processes) {
    $actual = Get-CimInstance Win32_Process -Filter "ProcessId = $($p.pid)"
    if (!$actual -or $actual.ParentProcessId -ne 15748 -or $actual.ExecutablePath -cne $p.executable -or
        $actual.CommandLine -cne $p.command -or $actual.CreationDate.ToUniversalTime().ToString('o') -cne $p.created) { throw 'Process mismatch' }
    $checked += $actual
}
foreach ($child in @(Get-CimInstance Win32_Process | Where-Object { $_.ParentProcessId -in @(3520,5228) })) {
    if ($child.ExecutablePath -ceq 'C:\WINDOWS\system32\conhost.exe') { continue }
    if ($child.ParentProcessId -ne 3520 -or $child.ExecutablePath -cne 'C:\Android\emulator\qemu\windows-x86_64\qemu-system-x86_64-headless.exe' -or
        !$child.CommandLine.Contains('-avd ' + $s.avd + ' -ports 5564,5565 ')) { throw 'Unexpected live children' }
    $checked = @($child) + $checked
}
Get-Content "$d\adb.err"
Get-Content "$d\emulator.out" -Head 50
$checked | Select-Object ProcessId,ParentProcessId,ExecutablePath,CommandLine | ConvertTo-Json
foreach ($p in $checked) { Stop-Process -Id $p.ProcessId -ErrorAction Stop }
Start-Sleep -Seconds 2
if (@(Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -in @($checked | ForEach-Object { $_.ProcessId }) }).Count) { throw 'Processes remain' }
if (@(Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in $s.ports }).Count) { throw 'Ports remain' }
Write-Output 'PASS: exact failed launch processes stopped; inherited reservations closed. No guest-readiness/native-shutdown acceptance claimed.'
'''
result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-EncodedCommand',
                         base64.b64encode(source.encode('utf-16le')).decode()],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
print(result.stdout.decode(errors='replace'))
raise SystemExit(result.returncode)
