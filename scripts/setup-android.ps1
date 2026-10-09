$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-tools.ps1')
$workspaceRoot = Split-Path $PSScriptRoot -Parent
$toolsRoot = Join-Path $workspaceRoot '.tools'
New-Item -ItemType Directory -Force $toolsRoot | Out-Null
$release = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
$package = $release[0].binary.package
if (-not (Test-Path "$toolsRoot\jdk")) {
    & curl.exe -L --fail --silent --show-error $package.link -o "$toolsRoot\jdk.zip"
    if ($LASTEXITCODE -ne 0) { throw 'JDK download failed' }
    if ((Get-EnergyFileSha256 -LiteralPath "$toolsRoot\jdk.zip") -ne $package.checksum) { throw 'JDK checksum mismatch' }
    New-Item -ItemType Directory -Force "$toolsRoot\jdk" | Out-Null
    & tar.exe -xf "$toolsRoot\jdk.zip" -C "$toolsRoot\jdk" --strip-components=1
}
if (-not (Test-Path "$toolsRoot\sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    & curl.exe -L --fail --silent --show-error 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -o "$toolsRoot\sdk.zip"
    if ($LASTEXITCODE -ne 0) { throw 'Android tools download failed' }
    if ((Get-EnergyFileSha256 -LiteralPath "$toolsRoot\sdk.zip") -ne '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a') { throw 'Android tools checksum mismatch' }
    New-Item -ItemType Directory -Force "$toolsRoot\sdk\cmdline-tools\latest" | Out-Null
    & tar.exe -xf "$toolsRoot\sdk.zip" -C "$toolsRoot\sdk\cmdline-tools\latest" --strip-components=1
}
$env:JAVA_HOME = "$toolsRoot\jdk"
$env:ANDROID_HOME = "$toolsRoot\sdk"
1..30 | ForEach-Object { 'y' } | & "$toolsRoot\sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$toolsRoot\sdk\cmdline-tools\latest\bin\sdkmanager.bat" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK setup failed' }
Write-EnergySdkLocation -SdkPath $env:ANDROID_HOME -OutputPath "$workspaceRoot\local.properties"
Write-Output 'Android build tools ready.'
