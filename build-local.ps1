param(
    [string]$JavaHome = 'D:\Java\jdk-25.0.2',
    [string]$GameDir = 'D:\game\.minecraft\versions\26.2-NeoForge_26.2.0.88',
    [switch]$Test,
    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$taskNames = @('jar')
if ($Test) {
    $tooling = Join-Path $PSScriptRoot 'build\tooling'
    New-Item -ItemType Directory -Force $tooling | Out-Null
    $junit = Join-Path $tooling 'junit-platform-console-standalone-1.10.0.jar'
    if (-not (Test-Path -LiteralPath $junit)) {
        Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar' -OutFile $junit
    }
    $taskNames += 'test'
}
if ($Install) { $taskNames += 'installMod' }
& (Join-Path $PSScriptRoot 'gradlew.bat') -p $PSScriptRoot -PlocalGameBuild "-PgameDir=$GameDir" --offline --console=plain @taskNames
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }
