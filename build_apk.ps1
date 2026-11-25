# Build script for CoolReader Android
param(
    [string]$BuildType = "Debug",
    [switch]$Public
)

$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\JetBrains\PyCharm Community Edition 2024.3.3\jbr"
$env:ANDROID_HOME = "E:\AndroidStudioSDK"
$env:ANDROID_SDK_ROOT = "E:\AndroidStudioSDK"

$gradleBat = "E:\coolreader\android\gradlew.bat"
$projectDir = "E:\coolreader\android"

$extraArgs = @()
if ($Public) {
    $extraArgs += "-PPUBLIC_BUILD=true"
    Write-Host "Building CoolReader ($BuildType, PUBLIC - no local OPDS presets)..." -ForegroundColor Cyan
} else {
    Write-Host "Building CoolReader ($BuildType, LOCAL)..." -ForegroundColor Cyan
}
Set-Location $projectDir

if ($BuildType -eq "Release") {
    & $gradleBat :app:assembleRelease -x lint @extraArgs
} else {
    & $gradleBat :app:assembleDebug -x lint @extraArgs
}

$outputDir = "E:\coolreader\android\app\build\outputs\apk\$($BuildType.ToLower())"
Write-Host "`nBuild complete! APK files located in ${outputDir}:" -ForegroundColor Green
Get-ChildItem -Path $outputDir -Filter "*.apk" | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize
