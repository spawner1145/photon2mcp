param(
    [string]$GameDir = 'D:\game\.minecraft\versions\1.21.1-NeoForge_21.1.252',
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot',
    [string]$WorldDir,
    [int]$Port = 8766,
    [string]$PhotonVersion = '2.2.8',
    [string]$LDLibVersion = '2.2.42',
    [string]$ModsDir
)

$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$testDir = Join-Path $project 'run-integration'
$qaDir = Join-Path $project 'build\qa'
$natives = Join-Path $testDir 'natives'
$instanceName = Split-Path $GameDir -Leaf
$minecraftDir = Split-Path (Split-Path $GameDir -Parent) -Parent
$metadata = Get-Content -Raw -LiteralPath (Join-Path $GameDir "$instanceName.json") | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path $qaDir, $natives, "$testDir\mods", "$testDir\config" | Out-Null
$modJar = Join-Path $project 'build\libs\photon-mcp-neoforge-1.21.1-0.2.1.jar'
if (-not $ModsDir) { $ModsDir = Join-Path $GameDir 'mods' }
$testMods = @(
    (Join-Path $ModsDir "photon-neoforge-1.21.1-$PhotonVersion-all.jar"),
    (Join-Path $ModsDir "ldlib2-neoforge-1.21.1-$LDLibVersion-all.jar"),
    $modJar
)
foreach ($testMod in $testMods) {
    if (-not (Test-Path -LiteralPath $testMod)) { throw "Missing test mod: $testMod" }
}
# Preserve previous test dependencies while preventing duplicate mod IDs after an upgrade.
$previousMods = @(Get-ChildItem -LiteralPath "$testDir\mods" -Filter '*.jar')
if ($previousMods.Count) {
    $backupDir = Join-Path $qaDir ('mods-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $backupDir | Out-Null
    $previousMods | Move-Item -Destination $backupDir
}
$testMods | Copy-Item -Destination "$testDir\mods"
@{host = '127.0.0.1'; port = $Port} | ConvertTo-Json | Set-Content -LiteralPath "$testDir\config\photon-mcp.json" -Encoding utf8NoBOM
'renderDistance:4', 'simulationDistance:5', 'maxFps:60', 'pauseOnLostFocus:false', 'guiScale:2' | Set-Content -LiteralPath "$testDir\options.txt" -Encoding utf8NoBOM

if ($WorldDir) {
    $worldTarget = Join-Path $testDir 'saves\Photon MCP Integration Test'
    if (-not (Test-Path -LiteralPath $worldTarget)) {
        New-Item -ItemType Directory -Force -Path $worldTarget | Out-Null
        Get-ChildItem -LiteralPath $WorldDir -Force | Where-Object { $_.Name -ne 'session.lock' } | Copy-Item -Destination $worldTarget -Recurse
    }
}

function Test-PlatformRules($Entry) {
    if (-not $Entry.rules) { return $true }
    $allowed = $false
    foreach ($rule in $Entry.rules) {
        if ($rule.features) { continue }
        if ($rule.os.name -and $rule.os.name -ne 'windows') { continue }
        if ($rule.os.arch -and $rule.os.arch -ne 'amd64') { continue }
        $allowed = $rule.action -eq 'allow'
    }
    return $allowed
}

$libraries = @($metadata.libraries | Where-Object { Test-PlatformRules $_ } | ForEach-Object {
    if ($_.downloads.artifact.path) {
        $library = Join-Path "$minecraftDir\libraries" $_.downloads.artifact.path
        if (-not (Test-Path -LiteralPath $library)) { throw "Missing game library: $library" }
        $library
    }
})
$libraries += Join-Path $GameDir "$instanceName.jar"
$libraries = @($libraries | Select-Object -Unique)
foreach ($library in $libraries | Where-Object { $_ -like '*-natives-windows.jar' }) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($library)
    try {
        foreach ($entry in $archive.Entries | Where-Object { $_.Name -like '*.dll' }) {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $natives $entry.Name), $true)
        }
    } finally { $archive.Dispose() }
}

$substitutions = @{
    natives_directory = $natives
    library_directory = "$minecraftDir\libraries"
    classpath = $libraries -join ';'
    classpath_separator = ';'
    launcher_name = 'PhotonMcpTest'
    launcher_version = '1'
    version_name = $instanceName
}
$arguments = @('-Xms1G', '-Xmx4G')
foreach ($argument in $metadata.arguments.jvm) {
    $values = if ($argument -is [string]) { @($argument) } elseif (Test-PlatformRules $argument) { @($argument.value) } else { @() }
    foreach ($value in $values) {
        foreach ($key in $substitutions.Keys) { $value = $value.Replace('${' + $key + '}', $substitutions[$key]) }
        $arguments += $value
    }
}
$arguments += @($metadata.mainClass, '--username', 'PhotonMcpTest', '--version', $instanceName, '--gameDir', $testDir,
    '--assetsDir', "$minecraftDir\assets", '--assetIndex', $metadata.assetIndex.id,
    '--uuid', 'f4bdb3fced27346192cf60aa0b0b3141', '--accessToken', '0', '--userType', 'legacy', '--versionType', 'release',
    '--width', '1600', '--height', '900', '--fml.neoForgeVersion', '21.1.252', '--fml.fmlVersion', '4.0.44',
    '--fml.mcVersion', '1.21.1', '--fml.neoFormVersion', '20240808.144430', '--launchTarget', 'forgeclient')
if (Test-Path -LiteralPath "$testDir\saves\Photon MCP Integration Test\level.dat") {
    $arguments += @('--quickPlaySingleplayer', 'Photon MCP Integration Test')
}
$argumentFile = Join-Path $qaDir 'launch.args'
$arguments | ForEach-Object { '"' + $_.Replace('\', '/').Replace('"', '\"') + '"' } | Set-Content -LiteralPath $argumentFile -Encoding utf8NoBOM
$process = Start-Process -FilePath "$JavaHome\bin\java.exe" -ArgumentList ('"@' + $argumentFile + '"') -WorkingDirectory $testDir -WindowStyle Hidden -PassThru -RedirectStandardOutput "$qaDir\game-stdout.log" -RedirectStandardError "$qaDir\game-stderr.log"
"Test game process: $($process.Id); MCP port: $Port; instance: $testDir"
