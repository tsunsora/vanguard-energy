function Get-EnergyFileSha256 {
    param([Parameter(Mandatory = $true)][string]$LiteralPath)
    $stream = [System.IO.File]::OpenRead((Resolve-Path -LiteralPath $LiteralPath).Path)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
        $sha256.Dispose()
    }
}

function Write-EnergySdkLocation {
    param([Parameter(Mandatory = $true)][string]$SdkPath, [Parameter(Mandatory = $true)][string]$OutputPath)
    $propertyPath = $SdkPath.Replace('\', '/').Replace(':', '\:')
    [System.IO.File]::WriteAllText($OutputPath, "sdk.dir=$propertyPath`n", [System.Text.UTF8Encoding]::new($false))
}
