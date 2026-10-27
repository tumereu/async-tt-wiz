param(
    [ValidateSet('up', 'down', 'status')][string]$Action,
    [string]$Distro,
    [string]$PidFile
)
$ErrorActionPreference = 'Stop'
# systemd services do not keep WSL alive. A hidden Windows-owned wsl.exe session
# does, without changing the user's global idle timeout or networking settings.
$marker = 'atw-test-keepalive'
$process = $null
if (Test-Path -LiteralPath $PidFile) {
    $processId = (Get-Content -LiteralPath $PidFile -Raw).Trim()
    if ($processId -match '^\d+$') {
        $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
        if ($candidate -and $candidate.Name -eq 'wsl.exe' -and
            $candidate.CommandLine.Contains($marker) -and
            $candidate.CommandLine.Contains("-d $Distro -e")) {
            $process = $candidate
        }
    }
}
switch ($Action) {
    'up' {
        if (-not $process) {
            # Start-Process joins ArgumentList; keep bash's command one quoted argument.
            $process = Start-Process -WindowStyle Hidden -PassThru -FilePath "$env:WINDIR\System32\wsl.exe" `
                -ArgumentList @('-d', $Distro, '-e', '/bin/bash', '-c', '"exec -a atw-test-keepalive sleep infinity"')
            [IO.File]::WriteAllText($PidFile, [string]$process.Id)
            Start-Sleep -Seconds 1
            if ($process.HasExited) { throw 'WSL keep-alive exited immediately' }
        }
    }
    'down' {
        if ($process) { Stop-Process -Id $process.ProcessId -Force }
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    }
    'status' {
        if ($process) { Write-Output "WSL keep-alive: running ($($process.ProcessId))" }
        else { Write-Output 'WSL keep-alive: stopped' }
    }
}
