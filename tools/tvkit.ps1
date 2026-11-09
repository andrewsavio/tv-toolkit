#requires -Version 7.0
<#
.SYNOPSIS
    tvkit: safe, reversible tools for Android TV and Google TV devices over adb.
.DESCRIPTION
    Read-only unless you add -Apply. Every change that -Apply makes is written to a journal first,
    so 'undo -Apply' can put it back. Nothing is removed from the device: apps are disabled per user,
    which 'undo' re-enables.

    Commands:
      help     show the command list
      detect   read the device identity and choose a profile (read only)
      status   show the current value of everything the profile would change (read only)
      plan     show what 'apply' would do, with current values (read only)
      apply    make the profile's changes (needs -Apply)
      undo     reverse the last apply from the journal (needs -Apply)
      ram      memory used by each running app, largest first (read only)
      audit    privacy audit: sensitive permissions, open connections, data sent (read only)
      boot     seconds since power-on and boot milestones (read only)
.PARAMETER Serial
    adb address of the device, for example 192.168.1.50:5555. Defaults to $env:ANDROID_SERIAL.
.PARAMETER Profile
    A profile name from profiles/ (for example tcl-beyondtv2), or a path to a .json file.
    Without it, the profile is chosen from the device's brand and model.
.PARAMETER IncludeOptional
    Also include the items the profile marks "optional".
.PARAMETER Apply
    Required for apply and undo to change anything.
.PARAMETER Journal
    Where apply writes its journal. Defaults to tools/tvkit-journal.json (git-ignored).
.EXAMPLE
    ./tools/tvkit.ps1 -Serial 192.168.1.50:5555 detect
.EXAMPLE
    ./tools/tvkit.ps1 -Serial 192.168.1.50:5555 plan -IncludeOptional
.EXAMPLE
    ./tools/tvkit.ps1 -Serial 192.168.1.50:5555 apply -Apply
.EXAMPLE
    ./tools/tvkit.ps1 -Serial 192.168.1.50:5555 undo -Apply
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help', 'detect', 'status', 'ram', 'audit', 'boot', 'plan', 'apply', 'undo')]
    [string]$Command = 'help',
    [string]$Serial = $env:ANDROID_SERIAL,
    [string]$Profile,
    [switch]$IncludeOptional,
    [switch]$Apply,
    [string]$Journal = (Join-Path $PSScriptRoot 'tvkit-journal.json')
)

$ErrorActionPreference = 'Stop'
$RepoRoot   = Split-Path -Path $PSScriptRoot -Parent
$ProfileDir = Join-Path $RepoRoot 'profiles'
$script:PkgState = @{}

# Apps whose sensitive permissions the audit reports (granted=true in dumpsys package).
$SensitivePermissions = @(
    'RECORD_AUDIO', 'CAMERA', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'READ_PHONE_STATE',
    'READ_CONTACTS', 'GET_ACCOUNTS', 'PACKAGE_USAGE_STATS', 'READ_LOGS', 'QUERY_ALL_PACKAGES',
    'SYSTEM_ALERT_WINDOW', 'WRITE_SECURE_SETTINGS', 'MANAGE_EXTERNAL_STORAGE', 'READ_SMS', 'READ_CALL_LOG'
)
# Android's standby bucket numbers, as 'am get-standby-bucket' prints them, mapped to the names 'am set-standby-bucket' takes.
$BucketName = @{ '5' = 'exempted'; '10' = 'active'; '20' = 'working_set'; '30' = 'frequent'; '40' = 'rare'; '45' = 'restricted'; '50' = 'never' }

# ---------------------------------------------------------------- adb helpers

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments)][string[]]$Rest)
    & adb -s $Serial @Rest 2>&1 | ForEach-Object { "$_" }
}

function Invoke-Shell([string]$Cmd) {
    @(Invoke-Adb shell $Cmd)
}

function Get-Prop([string]$Name) {
    ((Invoke-Shell "getprop $Name") -join '').Trim()
}

function Assert-Device {
    if (-not $Serial) { throw 'No device given. Pass -Serial <ip:port> or set ANDROID_SERIAL. "adb devices" lists them.' }
    $state = ((& adb -s $Serial get-state 2>&1) -join ' ').Trim()
    if ($state -ne 'device') {
        throw ("Cannot reach $Serial (adb says: '$state'). Check that the TV is on and that network debugging is enabled, " +
               "then run: adb connect $Serial  and accept the prompt on the TV.")
    }
}

# ---------------------------------------------------------------- device and profile

function Get-Device {
    $features = Invoke-Shell 'pm list features'
    [pscustomobject]@{
        Serial          = $Serial
        Brand           = Get-Prop 'ro.product.brand'
        Model           = Get-Prop 'ro.product.model'
        Device          = Get-Prop 'ro.product.device'
        AndroidVersion  = Get-Prop 'ro.build.version.release'
        SdkLevel        = Get-Prop 'ro.build.version.sdk'
        SecurityPatch   = Get-Prop 'ro.build.version.security_patch'
        Fingerprint     = Get-Prop 'ro.build.fingerprint'
        Characteristics = Get-Prop 'ro.build.characteristics'
        Debuggable      = Get-Prop 'ro.debuggable'
        LeanbackFeature = [bool](@($features | Where-Object { $_ -match 'android\.software\.leanback' }).Count)
    }
}

function Get-Platform($Dev) {
    if ($Dev.LeanbackFeature -or $Dev.Characteristics -match '\btv\b') { return 'Android TV / Google TV family' }
    return 'Not declared as Android TV. See docs/SAMSUNG-AND-OTHER-PLATFORMS.md before going further.'
}

function Read-Profile([string]$Path) {
    Get-Content -Path $Path -Raw | ConvertFrom-Json
}

function Resolve-Profile($Dev) {
    if ($Profile) {
        if (Test-Path -Path $Profile -PathType Leaf) { return Read-Profile (Resolve-Path $Profile).Path }
        $named = Join-Path $ProfileDir "$Profile.json"
        if (Test-Path $named) { return Read-Profile $named }
        throw "No profile called '$Profile' in $ProfileDir, and it is not a file."
    }
    $generic = $null
    foreach ($file in (Get-ChildItem -Path $ProfileDir -Filter '*.json' | Sort-Object Name)) {
        $p = Read-Profile $file.FullName
        if ($p.name -eq 'generic-androidtv') { $generic = $p; continue }
        if ($Dev.Brand -match $p.match.brand -and $Dev.Model -match $p.match.model) { return $p }
    }
    return $generic
}

function Get-Group($Entry) {
    if ($Entry.group) { [string]$Entry.group } else { 'safe' }
}

# Turns the profile into one flat list of actions, keeping only the groups the user asked for.
function Get-Items($Prof) {
    $items = New-Object System.Collections.Generic.List[object]
    foreach ($x in @($Prof.packages_disable | Where-Object { $_ })) {
        $items.Add([pscustomobject]@{ Kind = 'package'; Pkg = $x.pkg; Group = (Get-Group $x); Why = $x.why })
    }
    foreach ($x in @($Prof.background_restrict | Where-Object { $_ })) {
        $items.Add([pscustomobject]@{ Kind = 'background'; Pkg = $x.pkg; Group = (Get-Group $x); Why = $x.why })
    }
    foreach ($x in @($Prof.jobs_cancel | Where-Object { $_ })) {
        $items.Add([pscustomobject]@{ Kind = 'job'; Pkg = $x.pkg; JobId = $x.id; Group = (Get-Group $x); Why = $x.why })
    }
    foreach ($x in @($Prof.settings | Where-Object { $_ })) {
        $items.Add([pscustomobject]@{ Kind = 'setting'; Ns = $x.ns; Key = $x.key; Value = [string]$x.value; Group = (Get-Group $x); Why = $x.why })
    }
    if ($Prof.dns) {
        $items.Add([pscustomobject]@{ Kind = 'dns'; Host = $Prof.dns.host; Group = (Get-Group $Prof.dns); Why = $Prof.dns.why })
    }
    @($items | Where-Object { $_.Group -eq 'safe' -or ($IncludeOptional -and $_.Group -eq 'optional') })
}

# ---------------------------------------------------------------- current state (reads only)

function Initialize-PackageState {
    $script:PkgState = @{}
    foreach ($l in (Invoke-Shell 'pm list packages')) { if ($l -match '^package:(\S+)') { $script:PkgState[$Matches[1]] = 'disabled' } }
    foreach ($l in (Invoke-Shell 'pm list packages -e')) { if ($l -match '^package:(\S+)') { $script:PkgState[$Matches[1]] = 'enabled' } }
}

function Get-PackageState([string]$Pkg) {
    if ($script:PkgState.ContainsKey($Pkg)) { $script:PkgState[$Pkg] } else { 'missing' }
}

function Get-AppOp([string]$Pkg, [string]$Op) {
    foreach ($l in (Invoke-Shell "cmd appops get $Pkg $Op")) {
        if ($l -match "^${Op}:\s*(\w+)") { return $Matches[1] }
    }
    'unknown'
}

function Get-Bucket([string]$Pkg) {
    $n = ((Invoke-Shell "am get-standby-bucket $Pkg") -join '').Trim()
    if ($BucketName.ContainsKey($n)) { $BucketName[$n] } else { $n }
}

function Get-Setting([string]$Ns, [string]$Key) {
    $v = ((Invoke-Shell "settings get $Ns $Key") -join '').Trim()
    if ($v -eq '' -or $v -eq 'null') { $null } else { $v }
}

function Get-ItemStatus($Item) {
    switch ($Item.Kind) {
        'package' {
            $now = Get-PackageState $Item.Pkg
            $after = switch ($now) { 'enabled' { 'disable for this user' } 'disabled' { 'already disabled' } default { 'skip: not installed' } }
            [pscustomobject]@{ Group = $Item.Group; Action = 'disable app'; Target = $Item.Pkg; Now = $now; After = $after; Why = $Item.Why }
        }
        'background' {
            if ((Get-PackageState $Item.Pkg) -eq 'missing') {
                return [pscustomobject]@{ Group = $Item.Group; Action = 'limit background'; Target = $Item.Pkg; Now = '-'; After = 'skip: not installed'; Why = $Item.Why }
            }
            $op = Get-AppOp $Item.Pkg 'RUN_ANY_IN_BACKGROUND'
            $bk = Get-Bucket $Item.Pkg
            [pscustomobject]@{ Group = $Item.Group; Action = 'limit background'; Target = $Item.Pkg; Now = "background=$op bucket=$bk"; After = 'background=ignore bucket=restricted'; Why = $Item.Why }
        }
        'job' {
            [pscustomobject]@{ Group = $Item.Group; Action = 'cancel job'; Target = "$($Item.Pkg)#$($Item.JobId)"; Now = '-'; After = 'cancel now (the app recreates it if it runs again)'; Why = $Item.Why }
        }
        'setting' {
            $now = Get-Setting $Item.Ns $Item.Key
            [pscustomobject]@{ Group = $Item.Group; Action = 'set setting'; Target = "$($Item.Ns)/$($Item.Key)"; Now = $(if ($null -eq $now) { '(default)' } else { $now }); After = $Item.Value; Why = $Item.Why }
        }
        'dns' {
            $mode = Get-Setting 'global' 'private_dns_mode'
            $spec = Get-Setting 'global' 'private_dns_specifier'
            [pscustomobject]@{ Group = $Item.Group; Action = 'private DNS'; Target = 'global/private_dns'; Now = "$(if ($mode) { $mode } else { '(default)' }) $spec".Trim(); After = "hostname $($Item.Host)"; Why = $Item.Why }
        }
    }
}

# ---------------------------------------------------------------- apply and undo

function Save-Journal($Entries) {
    ConvertTo-Json -InputObject @($Entries) -Depth 6 | Set-Content -Path $Journal -Encoding utf8
}

function Invoke-Apply($Items) {
    if (Test-Path $Journal) {
        throw "A journal already exists ($Journal). Run 'undo -Apply' first. Delete the file only if you are sure it is stale."
    }
    $entries = New-Object System.Collections.Generic.List[object]
    foreach ($item in $Items) {
        switch ($item.Kind) {
            'package' {
                $state = Get-PackageState $item.Pkg
                if ($state -eq 'enabled') {
                    $entries.Add([ordered]@{ kind = 'package'; pkg = $item.Pkg; old = 'enabled' })
                    Save-Journal $entries
                    $reply = (Invoke-Shell "pm disable-user --user 0 $($item.Pkg)") -join ' '
                    Write-Host "  disabled $($item.Pkg)  ($reply)"
                } else {
                    Write-Host "  skip $($item.Pkg) ($state)"
                }
            }
            'background' {
                if ((Get-PackageState $item.Pkg) -eq 'missing') {
                    Write-Host "  skip $($item.Pkg) (not installed)"
                } else {
                    $entries.Add([ordered]@{
                        kind = 'background'; pkg = $item.Pkg
                        oldRunIn  = (Get-AppOp $item.Pkg 'RUN_IN_BACKGROUND')
                        oldRunAny = (Get-AppOp $item.Pkg 'RUN_ANY_IN_BACKGROUND')
                        oldBucket = (Get-Bucket $item.Pkg)
                    })
                    Save-Journal $entries
                    Invoke-Shell "cmd appops set $($item.Pkg) RUN_IN_BACKGROUND ignore" | Out-Null
                    Invoke-Shell "cmd appops set $($item.Pkg) RUN_ANY_IN_BACKGROUND ignore" | Out-Null
                    Invoke-Shell "am set-standby-bucket $($item.Pkg) restricted" | Out-Null
                    Write-Host "  limited background: $($item.Pkg)"
                }
            }
            'job' {
                $entries.Add([ordered]@{ kind = 'job'; pkg = $item.Pkg; id = $item.JobId; reversible = $false })
                Save-Journal $entries
                Invoke-Shell "cmd jobscheduler cancel -u 0 $($item.Pkg) $($item.JobId)" | Out-Null
                Write-Host "  cancelled job $($item.Pkg)#$($item.JobId)"
            }
            'setting' {
                $entries.Add([ordered]@{ kind = 'setting'; ns = $item.Ns; key = $item.Key; old = (Get-Setting $item.Ns $item.Key) })
                Save-Journal $entries
                Invoke-Shell "settings put $($item.Ns) $($item.Key) $($item.Value)" | Out-Null
                Write-Host "  $($item.Ns)/$($item.Key) = $(Get-Setting $item.Ns $item.Key)"
            }
            'dns' {
                $entries.Add([ordered]@{
                    kind = 'dns'
                    oldMode = (Get-Setting 'global' 'private_dns_mode')
                    oldHost = (Get-Setting 'global' 'private_dns_specifier')
                })
                Save-Journal $entries
                Invoke-Shell 'settings put global private_dns_mode hostname' | Out-Null
                Invoke-Shell "settings put global private_dns_specifier $($item.Host)" | Out-Null
                Write-Host "  private DNS -> $($item.Host)"
            }
        }
    }
    Write-Host "Journal written to $Journal. Undo with: undo -Apply. Restart the TV once so every app picks up the changes."
}

function Restore-Value([string]$Ns, [string]$Key, $Old) {
    if ($null -eq $Old) { Invoke-Shell "settings delete $Ns $Key" | Out-Null }
    else { Invoke-Shell "settings put $Ns $Key $Old" | Out-Null }
}

function Invoke-Undo {
    if (-not (Test-Path $Journal)) { Write-Host 'Nothing to undo: no journal file.'; return }
    $entries = @(Get-Content -Path $Journal -Raw | ConvertFrom-Json)
    for ($i = $entries.Count - 1; $i -ge 0; $i--) {
        $e = $entries[$i]
        switch ($e.kind) {
            'package' {
                Write-Host "  enable $($e.pkg)"
                if ($Apply) { Invoke-Shell "pm enable --user 0 $($e.pkg)" | Out-Null }
            }
            'background' {
                Write-Host "  restore background limits for $($e.pkg)"
                if ($Apply) {
                    if ($e.oldRunIn -ne 'unknown') { Invoke-Shell "cmd appops set $($e.pkg) RUN_IN_BACKGROUND $($e.oldRunIn)" | Out-Null }
                    if ($e.oldRunAny -ne 'unknown') { Invoke-Shell "cmd appops set $($e.pkg) RUN_ANY_IN_BACKGROUND $($e.oldRunAny)" | Out-Null }
                    if ($e.oldBucket -and $e.oldBucket -notmatch '^\d+$') { Invoke-Shell "am set-standby-bucket $($e.pkg) $($e.oldBucket)" | Out-Null }
                }
            }
            'job' { Write-Host "  job $($e.pkg)#$($e.id) cannot be restored; the app recreates it the next time it runs." }
            'setting' {
                Write-Host "  $($e.ns)/$($e.key) back to $(if ($null -eq $e.old) { '(default)' } else { $e.old })"
                if ($Apply) { Restore-Value $e.ns $e.key $e.old }
            }
            'dns' {
                Write-Host "  private DNS back to $(if ($e.oldMode) { $e.oldMode } else { '(default)' })"
                if ($Apply) {
                    Restore-Value 'global' 'private_dns_mode' $e.oldMode
                    Restore-Value 'global' 'private_dns_specifier' $e.oldHost
                }
            }
        }
    }
    if ($Apply) {
        Remove-Item -Path $Journal
        Write-Host 'Undone. Restart the TV once.'
    } else {
        Write-Host 'Dry run: nothing changed. Add -Apply to undo.'
    }
}

# ---------------------------------------------------------------- ram, audit, boot

function Show-Ram {
    $mem = Invoke-Shell 'cat /proc/meminfo'
    $ids = @()
    foreach ($l in (Invoke-Shell 'ps -A -o PID,RSS,NAME')) { if ($l -match '^\s*(\d+)\s+') { $ids += $Matches[1] } }
    $loop = 'for p in ' + (($ids | Select-Object -Unique) -join ' ') + '; do dumpsys meminfo -s $p 2>/dev/null | grep -E "MEMINFO in pid|TOTAL PSS"; done'
    $rows = New-Object System.Collections.Generic.List[object]
    $name = $null
    foreach ($l in (Invoke-Shell $loop)) {
        if ($l -match 'MEMINFO in pid \d+ \[(.+?)\]') { $name = $Matches[1] }
        elseif ($name -and $l -match 'TOTAL PSS:\s+(\d+)\s+TOTAL RSS:\s+\d+\s+TOTAL SWAP PSS:\s+(\d+)') {
            $rows.Add([pscustomobject]@{ App = $name; PssMB = [math]::Round([int]$Matches[1] / 1024, 1); SwapMB = [math]::Round([int]$Matches[2] / 1024, 1) })
            $name = $null
        }
    }
    $total = ($mem | Where-Object { $_ -match '^MemTotal' }) -replace '\s+', ' '
    $avail = ($mem | Where-Object { $_ -match '^MemAvailable' }) -replace '\s+', ' '
    Write-Host "$total`n$avail"
    $rows | Sort-Object PssMB -Descending | Select-Object -First 40 | Format-Table App, PssMB, SwapMB -AutoSize
    Write-Host ('Listed processes together use {0:N0} MB (PSS, shared pages counted once per app).' -f (($rows | Measure-Object -Property PssMB -Sum).Sum))
}

function ConvertFrom-ProcIp([string]$Hex) {
    if ($Hex.Length -eq 8) {
        $b = [Convert]::ToUInt32($Hex, 16)
        return '{0}.{1}.{2}.{3}' -f ($b -band 255), (($b -shr 8) -band 255), (($b -shr 16) -band 255), (($b -shr 24) -band 255)
    }
    if ($Hex.Substring(0, 24) -eq '0000000000000000FFFF0000') { return ConvertFrom-ProcIp $Hex.Substring(24) }
    "ipv6:$($Hex.Substring(0, 8))"
}

function Show-Audit($Prof) {
    $uidName = @{}
    $allNames = @()
    foreach ($l in (Invoke-Shell 'pm list packages -U')) {
        if ($l -match '^package:(\S+) uid:(\d+)') {
            $uid = [int]$Matches[2]
            if (-not $uidName.ContainsKey($uid)) { $uidName[$uid] = $Matches[1] }
            $allNames += $Matches[1]
        }
    }
    $prefixes = @($Prof.vendor_prefixes | Where-Object { $_ })
    $scope = if ($prefixes.Count) {
        @($allNames | Where-Object { $n = $_; @($prefixes | Where-Object { $n.StartsWith($_) }).Count -gt 0 })
    } else { @($allNames) }
    Write-Host "Auditing $($scope.Count) package(s)$(if ($prefixes.Count) { ' (vendor prefixes: ' + ($prefixes -join ', ') + ')' })"

    Write-Host "`n== Sensitive permissions granted"
    $grants = foreach ($pkg in $scope) {
        $dump = Invoke-Shell "dumpsys package $pkg"
        $hits = @(foreach ($perm in $SensitivePermissions) { if (@($dump | Where-Object { $_ -match "android\.permission\.${perm}: granted=true" }).Count) { $perm } })
        if ($hits.Count) { [pscustomobject]@{ Package = $pkg; Permissions = ($hits -join ', ') } }
    }
    if ($grants) { $grants | Format-Table -AutoSize -Wrap } else { Write-Host '  none' }

    Write-Host "`n== Internet connections open right now (remote addresses outside your LAN)"
    $conns = foreach ($l in (Invoke-Shell 'cat /proc/net/tcp /proc/net/tcp6')) {
        $f = @($l.Trim() -split '\s+')
        if ($f.Count -lt 8 -or $f[3] -ne '01') { continue }
        $ip = ConvertFrom-ProcIp $f[2].Split(':')[0]
        if ($ip -match '^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|0\.0\.0\.0|ipv6:0000)') { continue }
        $uid = [int]$f[7]
        [pscustomobject]@{ Package = $(if ($uidName.ContainsKey($uid)) { $uidName[$uid] } else { "uid $uid" }); Remote = "$ip`:$([Convert]::ToInt32($f[2].Split(':')[1], 16))" }
    }
    if ($conns) { $conns | Sort-Object Package, Remote -Unique | Format-Table -AutoSize } else { Write-Host '  none right now (a snapshot; see docs/PRIVACY-AUDIT.md)' }

    Write-Host "`n== Data sent per app since the stats began (top 15 by upload)"
    $bytes = @{}
    $uid = $null
    foreach ($l in (Invoke-Shell 'dumpsys netstats detail')) {
        if ($l -match 'uid=(-?\d+) set=\w+ tag=0x0') { $uid = [int]$Matches[1]; continue }
        if ($null -ne $uid -and $l -match '^\s+st=\d+ rb=(\d+) rp=\d+ tb=(\d+)') {
            if (-not $bytes.ContainsKey($uid)) { $bytes[$uid] = @([int64]0, [int64]0) }
            $bytes[$uid][0] += [int64]$Matches[1]
            $bytes[$uid][1] += [int64]$Matches[2]
        }
    }
    $bytes.GetEnumerator() | Sort-Object { $_.Value[1] } -Descending | Select-Object -First 15 | ForEach-Object {
        [pscustomobject]@{
            Package = $(if ($uidName.ContainsKey($_.Key)) { $uidName[$_.Key] } else { "uid $($_.Key)" })
            DownMB  = [math]::Round($_.Value[0] / 1MB, 1)
            UpMB    = [math]::Round($_.Value[1] / 1MB, 1)
        }
    } | Format-Table -AutoSize
    Write-Host 'Read the limits in docs/PRIVACY-AUDIT.md: permissions show access, not use; connections are a snapshot.'
}

function Show-Boot {
    $up = ((Invoke-Shell 'cat /proc/uptime') -join ' ').Split(' ')[0]
    Write-Host "Seconds since power-on: $up"
    $ev = Invoke-Shell "logcat -b events -d | grep -E 'boot_progress_(system_run|ams_ready|enable_screen)'"
    if ($ev) { $ev } else { Write-Host 'No boot milestones in the event log (it rotates; run this right after a restart).' }
}

# ---------------------------------------------------------------- main

function Show-Header($Dev, $Prof) {
    Write-Host "Device  : $($Dev.Brand) $($Dev.Model) (Android $($Dev.AndroidVersion), patch $($Dev.SecurityPatch))"
    Write-Host "Platform: $(Get-Platform $Dev)"
    Write-Host "Profile : $($Prof.name) [$($Prof.status)] $($Prof.description)"
}

function Show-Actions($Items) {
    if (-not $Items.Count) { Write-Host '(this profile has nothing to do on this device)'; return }
    $rows = foreach ($i in $Items) { Get-ItemStatus $i }
    $rows | Format-Table Group, Action, Target, Now, After -AutoSize -Wrap
    foreach ($r in $rows) { Write-Host "  - $($r.Target): $($r.Why)" }
}

try {
    if ($Command -eq 'help') {
        @'
tvkit: safe, reversible tools for Android TV / Google TV over adb.
  detect   what the device is and which profile applies            (read only)
  status   current value of everything a profile would change      (read only)
  plan     what 'apply' would do, with current values              (read only)
  apply    make the profile's changes; needs -Apply; writes a journal
  undo     reverse the last apply from the journal; needs -Apply
  ram      memory used per app                                     (read only)
  audit    privacy audit: permissions, open connections, data sent (read only)
  boot     seconds since power-on and boot milestones              (read only)
Options: -Serial <ip:port>  -Profile <name|file>  -IncludeOptional  -Apply
'@ | Write-Host
        return
    }

    Assert-Device
    $dev = Get-Device

    switch ($Command) {
        'detect' {
            $dev | Format-List
            $prof = Resolve-Profile $dev
            Write-Host "Platform: $(Get-Platform $dev)"
            Write-Host "Profile : $($prof.name) [$($prof.status)]"
        }
        'ram'   { Show-Ram }
        'boot'  { Show-Boot }
        'audit' { Show-Audit (Resolve-Profile $dev) }
        'status' {
            $prof = Resolve-Profile $dev
            Show-Header $dev $prof
            Initialize-PackageState
            Show-Actions (Get-Items $prof)
        }
        'plan' {
            $prof = Resolve-Profile $dev
            Show-Header $dev $prof
            Initialize-PackageState
            Show-Actions (Get-Items $prof)
            if (-not $Apply) { Write-Host "`nDry run: nothing changed. Add -Command apply -Apply to make these changes." }
        }
        'apply' {
            $prof = Resolve-Profile $dev
            Show-Header $dev $prof
            Initialize-PackageState
            $items = Get-Items $prof
            Show-Actions $items
            if ($Apply) { Write-Host "`nApplying..."; Invoke-Apply $items }
            else { Write-Host "`nDry run: nothing changed. Add -Apply to make these changes." }
        }
        'undo' {
            Write-Host "Device: $($dev.Brand) $($dev.Model)  Journal: $Journal"
            Invoke-Undo
        }
    }
}
catch {
    Write-Error $_
    exit 1
}
