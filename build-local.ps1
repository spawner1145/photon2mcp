param(
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot',
    [string]$GameDir = 'D:\game\.minecraft\versions\1.21.1-NeoForge_21.1.252',
    [switch]$Test,
    [switch]$Install,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$taskNames = @('jar')
if ($Test) {
    $taskNames += 'test'
}
if ($Install) { $taskNames += 'installMod' }
$gradleArguments = @('-p', $PSScriptRoot, "-PgameDir=$GameDir", '--console=plain')
if ($Offline) { $gradleArguments += '--offline' }
& (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArguments @taskNames
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }
