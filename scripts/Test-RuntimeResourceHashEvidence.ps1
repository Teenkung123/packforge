[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$helperPath = Join-Path $PSScriptRoot 'RuntimeResourceHashEvidence.ps1'
if (-not (Test-Path -LiteralPath $helperPath -PathType Leaf)) {
    throw "Runtime resource hash evidence helper is missing: $helperPath"
}
. $helperPath

function Assert-Throws {
    param(
        [string] $Name,
        [scriptblock] $Action,
        [string] $Pattern
    )

    try {
        & $Action
    } catch {
        if ($_.Exception.Message -notmatch $Pattern) {
            throw "$Name failed with an unexpected message: $($_.Exception.Message)"
        }
        return
    }
    throw "$Name did not fail."
}

function Assert-SourceContract {
    param(
        [string] $Path,
        [string[]] $Patterns
    )

    $text = Get-Content -LiteralPath $Path -Raw
    foreach ($pattern in $Patterns) {
        if ($text -notmatch $pattern) {
            throw "Runtime resource hash source contract is missing '$pattern' in $Path."
        }
    }
}

$firstHash = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
$secondHash = 'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789'
$validLog = @"
[12:00:00] [Worker-Main/INFO] [packforge/]: PackForge resolved-resource hash: id=41 entries=7 sha256=$firstHash
[12:00:01] [Worker-Main/INFO] [packforge/]: PackForge resolved-resource hash: id=42 entries=7 sha256=$firstHash
"@

$records = @(Get-PackForgeResolvedResourceHashRecords -Text $validLog)
if ($records.Count -ne 2 -or $records[0].ReloadId -ne 41L -or $records[0].Entries -ne 7) {
    throw 'Exact resolved-resource marker parsing changed.'
}
$resolved = Resolve-PackForgeResolvedResourceSha256 -Text $validLog -MinimumCount 2 -Context 'self-test'
if ($resolved -cne $firstHash.ToUpperInvariant()) {
    throw "Resolved-resource SHA-256 normalization changed: $resolved"
}

foreach ($loaderDisplay in @('Fabric', 'Forge', 'NeoForge')) {
    $passLine = "PASS $loaderDisplay production smoke: artifact=packforge-test.jar sha256=$('A' * 64) resolvedResourceSha256=$resolved reloads=10 cleanExit=true"
    $parsed = Get-PackForgePassResolvedResourceSha256 -PassLine $passLine -Required
    if ($parsed -cne $resolved) {
        throw "$loaderDisplay PASS token parsing changed."
    }
}

Assert-Throws 'missing marker' {
    Resolve-PackForgeResolvedResourceSha256 -Text 'no marker' -Context 'missing marker'
} 'too few'
Assert-Throws 'malformed SHA' {
    Resolve-PackForgeResolvedResourceSha256 -Text 'PackForge resolved-resource hash: id=1 entries=1 sha256=abcd' -Context 'malformed SHA'
} 'malformed'
Assert-Throws 'uppercase producer SHA' {
    Resolve-PackForgeResolvedResourceSha256 -Text "PackForge resolved-resource hash: id=1 entries=1 sha256=$($firstHash.ToUpperInvariant())" -Context 'uppercase SHA'
} 'malformed'
Assert-Throws 'zero entries' {
    Resolve-PackForgeResolvedResourceSha256 -Text "PackForge resolved-resource hash: id=1 entries=0 sha256=$firstHash" -Context 'zero entries'
} 'no fixture entries'
Assert-Throws 'too few markers' {
    Resolve-PackForgeResolvedResourceSha256 -Text "PackForge resolved-resource hash: id=1 entries=1 sha256=$firstHash" -MinimumCount 2 -Context 'too few markers'
} 'too few'
Assert-Throws 'duplicate reload ID' {
    Resolve-PackForgeResolvedResourceSha256 -Text @"
PackForge resolved-resource hash: id=1 entries=1 sha256=$firstHash
PackForge resolved-resource hash: id=1 entries=1 sha256=$firstHash
"@ -MinimumCount 2 -Context 'duplicate reload ID'
} 'duplicate.*reload IDs'
Assert-Throws 'nondeterministic digest' {
    Resolve-PackForgeResolvedResourceSha256 -Text @"
PackForge resolved-resource hash: id=1 entries=1 sha256=$firstHash
PackForge resolved-resource hash: id=2 entries=1 sha256=$secondHash
"@ -MinimumCount 2 -Context 'nondeterministic digest'
} 'changed across reloads'
Assert-Throws 'missing PASS token' {
    Get-PackForgePassResolvedResourceSha256 -PassLine 'PASS Fabric production smoke: cleanExit=true' -Required
} 'omitted'
Assert-Throws 'lowercase PASS token' {
    Get-PackForgePassResolvedResourceSha256 -PassLine "PASS Fabric production smoke: resolvedResourceSha256=$firstHash" -Required
} 'invalid'
Assert-Throws 'duplicate PASS token' {
    Get-PackForgePassResolvedResourceSha256 -PassLine "PASS Fabric production smoke: resolvedResourceSha256=$resolved resolvedResourceSha256=$resolved" -Required
} 'contains 2'
Assert-Throws 'malformed duplicate PASS token' {
    Get-PackForgePassResolvedResourceSha256 -PassLine "PASS Fabric production smoke: resolvedResourceSha256=$resolved resolvedResourceSha256=bad" -Required
} 'contains 2'

$fabricPath = Join-Path $PSScriptRoot 'Smoke-Fabric-Production.ps1'
$forgePath = Join-Path $PSScriptRoot 'Smoke-Forge-Production.ps1'
$neoForgePath = Join-Path $PSScriptRoot 'Smoke-NeoForge-Production.ps1'
$matrixPath = Join-Path $PSScriptRoot 'Run-Exact-ProductionMatrix.ps1'
Assert-SourceContract -Path $fabricPath -Patterns @(
    'RuntimeResourceHashEvidence\.ps1',
    '\[int\]\s+\$ReloadCount\s*=\s*10',
    "PACKFORGE_RUNTIME_RESOURCE_HASH.+true",
    'deterministic-large-pack\.zip',
    'Resolve-PackForgeResolvedResourceSha256',
    'resolvedResourceSha256=\$resolvedResourceSha256'
)
Assert-SourceContract -Path $forgePath -Patterns @(
    'RuntimeResourceHashEvidence\.ps1',
    '\[int\]\s+\$ReloadCount\s*=\s*10',
    "PACKFORGE_RUNTIME_RESOURCE_HASH.+true",
    'Resolve-PackForgeResolvedResourceSha256',
    'resolvedResourceSha256=\$resolvedResourceSha256'
)
Assert-SourceContract -Path $neoForgePath -Patterns @(
    '\[int\]\s+\$ReloadCount\s*=\s*10',
    "Loader\s*=\s*'neoforge'",
    'Smoke-Forge-Production\.ps1',
    'AllowControlledTermination'
)
Assert-SourceContract -Path $matrixPath -Patterns @(
    'RuntimeResourceHashEvidence\.ps1',
    '\[ValidateRange\(10,\s*10\)\]',
    '\[int\]\s+\$ReloadCount\s*=\s*10',
    'Resolve-ResourcePackFixture.*PreferredLoader.*\$loader',
    'runtimeResourceHashHelperPath',
    'Get-PackForgePassResolvedResourceSha256',
    'resolvedResourceSha256\s*=\s*\$resolvedResourceSha256',
    'AllowControlledTermination'
)

Write-Output 'Runtime resource hash evidence self-test PASS: exact marker parsing, positive entries, deterministic digest, cross-loader PASS token, and script wiring verified.'
