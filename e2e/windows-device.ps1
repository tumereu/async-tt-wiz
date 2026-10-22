# Test-only Windows half of devices.py. Input is supplied by -EncodedCommand.
# No global Android, firewall, WSL, hypervisor or default ADB configuration changes.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
function Need($condition, $message) { if (!$condition) { throw $message } }
function Identity($number) {
    $p = Get-CimInstance Win32_Process -Filter "ProcessId = $number"
    if (!$p) { return $null }
    return @{ pid = [int]$p.ProcessId; created = $p.CreationDate.ToUniversalTime().ToString('o');
        executable = $p.ExecutablePath; command = $p.CommandLine }
}
function Same($a, $b) {
    return $a -and $b -and $a.pid -eq $b.pid -and $a.created -ceq $b.created -and
        $a.executable -ceq $b.executable -and $a.command -ceq $b.command
}
function Listeners {
    return @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
        @{ address = $_.LocalAddress; port = [int]$_.LocalPort; pid = [int]$_.OwningProcess }
    })
}
function Save {
    $state | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 -LiteralPath "$directory\state.new"
    Move-Item -Force -LiteralPath "$directory\state.new" -Destination "$directory\state.json"
}
function CheckedProcesses {
    foreach ($p in $state.processes) {
        $actual = Identity $p.pid
        Need (!$actual -or (Same $p $actual)) 'Reused or foreign Windows PID refused'
    }
    $ids = @($state.processes | ForEach-Object { $_.pid })
    foreach ($l in (Listeners)) {
        if ($l.port -in $state.ports) {
            if ($inputValue.operation -eq 'start' -and $l.pid -eq $PID -and $l.port -ne $config.adbPort) { continue }
            Need ($l.pid -in $ids) 'Foreign Windows port owner refused'
            if ($l.port -eq $config.adbPort) {
                Need ($l.address -eq '127.0.0.1') 'ADB must bind only IPv4 loopback'
            }
        }
    }
}
function Discover {
    # Record only children of a previously recorded, still identical launcher.
    foreach ($p in @($state.processes)) {
        if (Same $p (Identity $p.pid)) {
            foreach ($child in @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $($p.pid)")) {
                if ($child.ExecutablePath -in @("$($config.windowsSdk)\emulator\qemu\windows-x86_64\qemu-system-x86_64.exe", "$($config.windowsSdk)\emulator\qemu\windows-x86_64\qemu-system-x86_64-headless.exe")) {
                    Need ($child.CommandLine.Contains('-avd ' + $state.avd)) 'Unexpected emulator child'
                    if ($child.ProcessId -notin @($state.processes | ForEach-Object { $_.pid })) {
                        $state.processes += (Identity $child.ProcessId)
                        Save
                    }
                }
            }
        }
    }
}
$root = Join-Path $env:LOCALAPPDATA 'async-tt-wiz-device-controller'
New-Item -ItemType Directory -Force -Path $root | Out-Null
# Cross-operation Windows lock also protects two WSL checkouts/controllers.
$lock = [IO.File]::Open("$root\controller.lock", 'OpenOrCreate', 'ReadWrite', 'None')
try {
    $config = $inputValue.config
    Need ($inputValue.name -match '^atw-device-[a-f0-9]{32}$') 'Invalid instance name'
    $directory = Join-Path $root $inputValue.name
    $adb = "$($config.windowsSdk)\platform-tools\adb.exe"
    $emulator = "$($config.windowsSdk)\emulator\emulator.exe"
    if ($inputValue.operation -eq 'inventory') {
        @{ listeners = (Listeners); androidProcesses = @(Get-CimInstance Win32_Process | Where-Object {
            $_.Name -match '^(adb|emulator|qemu-system-x86_64)\.exe$'
        } | ForEach-Object { Identity $_.ProcessId }) } | ConvertTo-Json -Depth 12 -Compress
        exit 0
    }
    if ($inputValue.operation -eq 'start') {
        Need (!(Test-Path -LiteralPath $directory)) 'Existing Windows instance refused'
        Need ((Test-Path $adb) -and (Test-Path $emulator)) 'Explicit Windows SDK executables required'
        $image = "$($config.windowsSdk)\system-images\android-29\default\x86_64"
        Need (Test-Path "$image\system.img") 'API29 default non-GMS image required'
        $accel = (& $emulator -accel-check 2>&1 | Out-String)
        Need ($LASTEXITCODE -eq 0 -and $accel.Contains('WHPX')) 'Usable WHPX required'
        $ports = @([int]$config.adbPort, [int]$config.consolePort, ([int]$config.consolePort + 1))
        foreach ($l in (Listeners)) { Need ($l.port -notin $ports) 'Busy Windows port refused' }
        $avd = $inputValue.name
        Need (!@(Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and $_.CommandLine.Contains('-avd ' + $avd) }).Count) 'Busy AVD refused'
        # Bind every Windows port before releasing one immediately for its owner.
        $reservations = @{}
        try {
            foreach ($port in $ports) {
                $s = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $port)
                $s.Server.ExclusiveAddressUse = $true; $s.Start(); $reservations[$port] = $s
            }
            New-Item -ItemType Directory -Path "$directory\avds\$avd.avd" | Out-Null
            $state = @{ name = $inputValue.name; owner = $inputValue.owner; avd = $avd;
                ports = $ports; processes = @(); config = $config; acceleration = $accel.Trim(); launcher = (Identity $PID) }
            Save
            @"
avd.ini.encoding = UTF-8
AvdId = $avd
avd.ini.displayname = $avd
abi.type = x86_64
hw.cpu.arch = x86_64
hw.cpu.ncore = 2
hw.ramSize = 2048
hw.lcd.width = 720
hw.lcd.height = 1280
hw.lcd.density = 320
hw.keyboard = yes
hw.gpu.enabled = yes
hw.gpu.mode = software
hw.audioInput = no
disk.dataPartition.size = 6442450944
image.sysdir.1 = $image\
tag.id = default
tag.display = Default
PlayStore.enabled = false
showDeviceFrame = no
skin.dynamic = yes
skin.name = 720x1280
"@ | Set-Content -Encoding ASCII "$directory\avds\$avd.avd\config.ini"
            @"
avd.ini.encoding=UTF-8
path=$directory\avds\$avd.avd
target=android-29
"@ | Set-Content -Encoding ASCII "$directory\avds\$avd.ini"
            $env:ANDROID_AVD_HOME = "$directory\avds"
            $env:ANDROID_HOME = $config.windowsSdk
            $env:ANDROID_ADB_SERVER_PORT = [string]$config.adbPort
            $env:ADB_SERVER_SOCKET = 'tcp:127.0.0.1:' + $config.adbPort
            # Win32 children can inherit .NET sockets. Close ALL reservations
            # before ANY spawn, under the global Windows lock; then check actual
            # process/listener identity. Never carry console sockets into ADB.
            foreach ($s in $reservations.Values) { $s.Stop() }
            # Windows ADB rejects a hostname in -L. tcp:PORT defaults to loopback;
            # never pass -a, and assert the actual binding below and on every op.
            $p = Start-Process -FilePath $adb -WorkingDirectory $directory -ArgumentList @('-L', ('tcp:' + $config.adbPort), 'nodaemon', 'server') -PassThru -WindowStyle Hidden -RedirectStandardOutput "$directory\adb.out" -RedirectStandardError "$directory\adb.err"
            $identity = Identity $p.Id
            Need $identity 'ADB detached launch exited before identification'
            $state.processes += $identity; Save
            $until = [DateTime]::UtcNow.AddSeconds(15)
            while (!@((Listeners) | Where-Object { $_.port -eq $config.adbPort }).Count -and [DateTime]::UtcNow -lt $until) { Start-Sleep -Milliseconds 200 }
            CheckedProcesses
            Need (@((Listeners) | Where-Object { $_.port -eq $config.adbPort }).Count -eq 1) 'ADB startup failed'
            $args = @('-avd', $avd, '-ports', "$($config.consolePort),$([int]$config.consolePort + 1)", '-accel', 'on', '-gpu', 'software', '-no-snapshot', '-no-boot-anim', '-no-audio', '-no-window')
            $p = Start-Process -FilePath $emulator -WorkingDirectory $directory -ArgumentList $args -PassThru -WindowStyle Hidden -RedirectStandardOutput "$directory\emulator.out" -RedirectStandardError "$directory\emulator.err"
            $identity = Identity $p.Id
            Need $identity 'Emulator detached launch exited before identification'
            $state.processes += $identity; Save
            $until = [DateTime]::UtcNow.AddSeconds(15)
            do { Discover; if ($state.processes.Count -ge 3) { break }; Start-Sleep -Milliseconds 200 } while ([DateTime]::UtcNow -lt $until)
            Need ($state.processes.Count -ge 3) 'Detached emulator child not identified'
            CheckedProcesses
        } finally { foreach ($s in $reservations.Values) { $s.Stop() } }
    } else {
        if (!(Test-Path -LiteralPath $directory)) {
            Need ($inputValue.operation -in @('snapshot', 'stop')) 'Missing Windows instance'
            @{ absent = $true } | ConvertTo-Json -Compress
            exit 0
        }
        Need (Test-Path -LiteralPath "$directory\state.json") 'Missing Windows ownership record'
        $state = Get-Content -Raw -LiteralPath "$directory\state.json" | ConvertFrom-Json
        Need ($state.name -ceq $inputValue.name -and $state.owner -ceq $inputValue.owner) 'Foreign Windows instance refused'
        Need (@($state.config.PSObject.Properties).Count -eq @($config.PSObject.Properties).Count) 'Changed Windows configuration refused'
        foreach ($property in $config.PSObject.Properties) {
            Need ($state.config.($property.Name) -ceq $property.Value) 'Changed Windows configuration refused'
        }
        Discover
        CheckedProcesses
    }
    if ($inputValue.operation -eq 'stop') {
        $running = @($state.processes | Where-Object { Identity $_.pid })
        if ($running.Count -gt 1) {
            # Windows-native emulator console; Linux remote `adb emu` is not qualified.
            Need (@($running | Where-Object { $_.executable -eq $adb }).Count -eq 1) 'Dedicated ADB unavailable for native shutdown'
            Need (@((Listeners) | Where-Object { $_.port -eq $config.adbPort }).Count -eq 1) 'Dedicated ADB listener unavailable'
            $serial = 'emulator-' + $config.consolePort
            # Use a retained .NET process handle. Start-Process -PassThru can
            # lose a short command's exit code before WaitForExit observes it.
            $console = New-Object Diagnostics.Process
            $console.StartInfo.FileName = $adb
            $console.StartInfo.WorkingDirectory = $directory
            $console.StartInfo.Arguments = "-H 127.0.0.1 -P $($config.adbPort) -s $serial emu kill"
            $console.StartInfo.UseShellExecute = $false
            $console.StartInfo.CreateNoWindow = $true
            $console.StartInfo.RedirectStandardOutput = $true
            $console.StartInfo.RedirectStandardError = $true
            Need ($console.Start()) 'Native console launch failed'
            $consoleOutput = $console.StandardOutput.ReadToEndAsync()
            $consoleError = $console.StandardError.ReadToEndAsync()
            if (!$console.WaitForExit(10000)) {
                # Process object holds this exact command's handle, not a reused PID.
                $console.Kill(); $console.WaitForExit()
                throw 'Native emulator-console command timed out'
            }
            [IO.File]::WriteAllText("$directory\console.out", $consoleOutput.Result)
            [IO.File]::WriteAllText("$directory\console.err", $consoleError.Result)
            Need ($console.ExitCode -eq 0) 'Native emulator-console command failed'
            $until = [DateTime]::UtcNow.AddSeconds(30)
            do {
                $remaining = @($state.processes | Where-Object { $_.executable -ne $adb -and (Identity $_.pid) })
                if (!$remaining.Count) { break }; Start-Sleep -Milliseconds 250
            } while ([DateTime]::UtcNow -lt $until)
            Need (!$remaining.Count) 'Native emulator shutdown incomplete; exact processes retained for recovery'
        }
        # No kill-server command: stop only the checked dedicated nodaemon PID.
        foreach ($p in $state.processes) {
            $actual = Identity $p.pid
            Need (!$actual -or (Same $p $actual)) 'Reused Windows PID refused at stop'
            if ($actual) { Stop-Process -Id $p.pid -ErrorAction Stop }
        }
        $until = [DateTime]::UtcNow.AddSeconds(10)
        do {
            $remaining = @($state.processes | Where-Object { Identity $_.pid })
            if (!$remaining.Count) { break }; Start-Sleep -Milliseconds 100
        } while ([DateTime]::UtcNow -lt $until)
        Need (!$remaining.Count) 'Windows process cleanup incomplete'
        Need (!@((Listeners) | Where-Object { $_.port -in $state.ports }).Count) 'Windows ports remain busy'
        # Native emu kill spawns emulator.exe -kill QEMU_PID -sleep 20. It can
        # outlive the guest and listeners; wait for these exact-target watchdogs,
        # never kill broad emulator matches or mistake them for unrelated devices.
        $qemuIds = @($state.processes | Where-Object { $_.executable -match '\\qemu-system-x86_64(-headless)?\.exe$' } | ForEach-Object { $_.pid })
        $until = [DateTime]::UtcNow.AddSeconds(30)
        do {
            $watchdogs = @(Get-CimInstance Win32_Process -Filter "Name = 'emulator.exe'" | Where-Object {
                $candidate = $_
                $candidate.ExecutablePath -eq $emulator -and @($qemuIds | Where-Object { $candidate.CommandLine -match (' -kill ' + $_ + ' -sleep 20\s*$') }).Count
            })
            if (!$watchdogs.Count) { break }; Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $until)
        Need (!$watchdogs.Count) 'Owned native shutdown watchdog remains; retain for recovery'
        $state | Add-Member -Force NoteProperty cleaned $true
        $state | Add-Member -Force NoteProperty nativeWatchdogExitObserved $true
        Save
    }
    $logs = @{}
    foreach ($name in @('adb.out', 'adb.err', 'emulator.out', 'emulator.err')) {
        if (Test-Path "$directory\$name") { $logs[$name] = (Get-Content -Tail 40 "$directory\$name" | Out-String) }
    }
    @{ state = $state; listeners = @((Listeners) | Where-Object { $_.port -in $state.ports }); logs = $logs;
        currentProcesses = @($state.processes | ForEach-Object { Identity $_.pid }) } | ConvertTo-Json -Depth 12 -Compress
} finally { $lock.Dispose() }
