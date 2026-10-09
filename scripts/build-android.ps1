param([switch]$Development)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-tools.ps1')
$workspaceRoot = Split-Path $PSScriptRoot -Parent
if (Test-Path "$workspaceRoot\.tools\jdk") { $env:JAVA_HOME = "$workspaceRoot\.tools\jdk" }
if (Test-Path "$workspaceRoot\.tools\sdk") { $env:ANDROID_HOME = "$workspaceRoot\.tools\sdk" }
if ($env:ANDROID_HOME) { Write-EnergySdkLocation -SdkPath $env:ANDROID_HOME -OutputPath "$workspaceRoot\local.properties" }
if (-not $Development -and -not $env:ENERGY_KEYSTORE_PATH -and -not (Test-Path "$env:USERPROFILE\.android\debug.keystore")) {
    throw 'Existing signing key is missing. Restore it or set ENERGY_KEYSTORE_PATH and its signing credentials before building an update.'
}
$variant = if ($Development) { 'debug' } else { 'release' }
$buildTask = if ($Development) { ':androidApp:assembleDebug' } else { ':androidApp:assembleRelease' }
$apkName = if ($Development) { 'energy-tracker-development.apk' } else { 'energy-tracker.apk' }
Push-Location $workspaceRoot
try {
    .\gradlew.bat :shared:jvmTest $buildTask --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android build or tests failed' }
    New-Item -ItemType Directory -Force releases | Out-Null
    Copy-Item -LiteralPath "androidApp\build\outputs\apk\$variant\androidApp-$variant.apk" -Destination "releases\$apkName"
    $hash = Get-EnergyFileSha256 -LiteralPath "releases\$apkName"
    "$hash  $apkName" | Set-Content -Encoding ascii "releases\$apkName.sha256"
    Write-Output "APK: $workspaceRoot\releases\$apkName"
} finally { Pop-Location }
