# Build script for CoolReader Android
param(
    [string]$BuildType = "Debug"
)

$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\JetBrains\PyCharm Community Edition 2024.3.3\jbr"
$env:ANDROID_HOME = "E:\AndroidStudioSDK"
$env:ANDROID_SDK_ROOT = "E:\AndroidStudioSDK"

$gradleBat = "C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3\bin\gradle.bat"
$projectDir = "E:\coolreader\android"

Write-Host "Building CoolReader ($BuildType)..." -ForegroundColor Cyan
Set-Location $projectDir

if ($BuildType -eq "Release") {
    & $gradleBat :app:assembleRelease -x lint
} else {
    & $gradleBat :app:assembleDebug -x lint
}

$outputDir = "E:\coolreader\android\app\build\outputs\apk\$($BuildType.ToLower())"
Write-Host "`nBuild complete! APK files located in ${outputDir}:" -ForegroundColor Green
Get-ChildItem -Path $outputDir -Filter "*.apk" | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize

