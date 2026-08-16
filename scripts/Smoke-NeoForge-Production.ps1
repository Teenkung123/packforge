[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $NeoForgeClientRoot,

    [Parameter(Mandatory = $true)]
    [string] $VersionName,

    [Parameter(Mandatory = $true)]
    [string] $ArtifactPath,

    [Parameter(Mandatory = $true)]
    [string] $AssetsRoot,

    [Parameter(Mandatory = $true)]
    [string] $NativesRoot,

    [Parameter(Mandatory = $true)]
    [string] $JavaPath,

    [string] $ResourcePackPath,

    [string] $FallbackLibrariesRoot,

    [string[]] $AdditionalModPaths,

    [string[]] $ExpectedLogMarkers,

    [string[]] $ForbiddenLogMarkers,

    [string] $CompatibilityProfilePath,

    [switch] $ValidateCompatibilityProfileOnly,

    [string] $ExpectedProfileTarget,

    [string] $ExpectedProfileMinecraftVersion,

    [ValidateRange(60, 3600)]
    [int] $TimeoutSeconds = 900,

    [ValidateRange(0, 10)]
    [int] $ReloadCount = 10,

    [ValidateSet('repeat', 'cancel-in-flight', 'forced-resource-failure', 'retry-success', 'retry-exhaustion')]
    [string] $RuntimeSmokeScenario = 'repeat',

    [switch] $AllowControlledTermination
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$arguments = @{
    ForgeClientRoot = $NeoForgeClientRoot
    Loader = 'neoforge'
    VersionName = $VersionName
    ArtifactPath = $ArtifactPath
    AssetsRoot = $AssetsRoot
    NativesRoot = $NativesRoot
    JavaPath = $JavaPath
    TimeoutSeconds = $TimeoutSeconds
    ReloadCount = $ReloadCount
    RuntimeSmokeScenario = $RuntimeSmokeScenario
}
if (-not [string]::IsNullOrWhiteSpace($ResourcePackPath)) {
    $arguments.ResourcePackPath = $ResourcePackPath
}
if (-not [string]::IsNullOrWhiteSpace($FallbackLibrariesRoot)) {
    $arguments.FallbackLibrariesRoot = $FallbackLibrariesRoot
}
if (@($AdditionalModPaths).Count -gt 0) {
    $arguments.AdditionalModPaths = @($AdditionalModPaths)
}
if (@($ExpectedLogMarkers).Count -gt 0) {
    $arguments.ExpectedLogMarkers = @($ExpectedLogMarkers)
}
if (@($ForbiddenLogMarkers).Count -gt 0) {
    $arguments.ForbiddenLogMarkers = @($ForbiddenLogMarkers)
}
if (-not [string]::IsNullOrWhiteSpace($CompatibilityProfilePath)) {
    $arguments.CompatibilityProfilePath = $CompatibilityProfilePath
}
if ($ValidateCompatibilityProfileOnly.IsPresent) {
    $arguments.ValidateCompatibilityProfileOnly = $true
    $arguments.ExpectedProfileTarget = $ExpectedProfileTarget
    $arguments.ExpectedProfileMinecraftVersion = $ExpectedProfileMinecraftVersion
}
if ($AllowControlledTermination.IsPresent) {
    $arguments.AllowControlledTermination = $true
}

& (Join-Path $PSScriptRoot 'Smoke-Forge-Production.ps1') @arguments
$nativeExitCode = Get-Variable -Name LASTEXITCODE -ValueOnly -ErrorAction SilentlyContinue
if ($null -ne $nativeExitCode -and $nativeExitCode -ne 0) {
    exit $nativeExitCode
}
