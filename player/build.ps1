<#
.SYNOPSIS
    Compiles the player (Media3 ExoPlayer, Live TV, Continue Watching, Dolby badge) into classesN.dex files.
.DESCRIPTION
    1. Downloads each library in libs.lock.json and refuses it if its SHA-256 does not match.
    2. Extracts classes.jar from each AAR.
    3. Compiles src/ with javac --release 8 and converts it to dex with d8 (Android SDK build-tools 34.0.0).
    The dex files are then added to an APK you own (see docs/METHODS.md, "Adding the player to an app").
.EXAMPLE
    ./player/build.ps1
.EXAMPLE
    ./player/build.ps1 -FirstDexIndex 6
#>
param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [int]$FirstDexIndex = 5
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$bt   = Join-Path $Sdk 'build-tools\34.0.0'
$jar  = Join-Path $Sdk 'platforms\android-34\android.jar'
foreach ($need in @((Join-Path $bt 'lib\d8.jar'), $jar)) {
    if (-not (Test-Path $need)) { throw "Missing $need. Install build-tools 34.0.0 and platform android-34 with the SDK manager." }
}
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'javac not found. Install a JDK (17 or newer) and put it on PATH.' }

$deps = Join-Path $here 'build\deps'
New-Item -ItemType Directory -Force $deps | Out-Null
$lock = Get-Content (Join-Path $here 'libs.lock.json') -Raw | ConvertFrom-Json
$jars = @()
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($lib in $lock.libraries) {
    $file = Join-Path $deps ([IO.Path]::GetFileName($lib.url))
    if (-not (Test-Path $file)) { Invoke-WebRequest -Uri $lib.url -OutFile $file -UseBasicParsing }
    $hash = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $lib.sha256) { Remove-Item $file -Force; throw "SHA-256 mismatch for $($lib.name): got $hash" }
    if ($lib.type -eq 'aar') {
        $target = Join-Path $deps ($lib.name + '.jar')
        $zip = [IO.Compression.ZipFile]::OpenRead($file)
        try { [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('classes.jar'), $target, $true) }
        finally { $zip.Dispose() }
        $jars += $target
    } else {
        $jars += $file
    }
}

$classes = Join-Path $here 'build\player-classes'
$out     = Join-Path $here 'build\player-dex'
foreach ($d in @($classes, $out)) {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    New-Item -ItemType Directory -Force $d | Out-Null
}

$sources = @(Get-ChildItem (Join-Path $here 'src') -Recurse -Filter *.java | ForEach-Object FullName)
$cp = (@($jar) + $jars) -join ';'
javac --release 8 -nowarn -classpath $cp -d $classes @sources
if ($LASTEXITCODE) { throw 'javac failed' }

$inputs = @(Get-ChildItem $classes -Recurse -Filter *.class | ForEach-Object FullName) + $jars
java -cp (Join-Path $bt 'lib\d8.jar') com.android.tools.r8.D8 --release --min-api 26 --lib $jar --output $out @inputs
if ($LASTEXITCODE) { throw 'd8 failed' }

# d8 writes classes.dex, classes2.dex, ...; rename them to classes<FirstDexIndex>.dex and up.
$dex = @(Get-ChildItem $out -Filter 'classes*.dex' | Sort-Object { if ($_.Name -eq 'classes.dex') { 0 } else { [int]($_.BaseName -replace '\D', '') } })
$i = $FirstDexIndex
foreach ($f in $dex) { Rename-Item $f.FullName ("renamed_{0}.dex" -f $i); $i++ }
Get-ChildItem $out -Filter 'renamed_*.dex' | ForEach-Object { Rename-Item $_.FullName ($_.Name -replace '^renamed_', 'classes') }
Get-ChildItem $out -Filter 'classes*.dex' | Select-Object Name, @{ n = 'KB'; e = { [math]::Round($_.Length / 1KB) } }
