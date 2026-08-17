param(
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\gradle\minecraft-targets.json'),
    [string]$ManifestPath,
    [switch]$SkipChecksum,
    [switch]$RequireChecksum
)

$ErrorActionPreference = 'Stop'

if ($SkipChecksum -and $RequireChecksum) {
    throw 'SkipChecksum and RequireChecksum cannot be used together.'
}

$registry = Get-Content -LiteralPath $RegistryPath -Raw | ConvertFrom-Json
if ($registry.schemaVersion -ne 2) {
    throw "Unsupported PackForge target registry schema: $($registry.schemaVersion)."
}

$expected = @($registry.releaseSequence | ForEach-Object { [string]$_ })
if ($expected.Count -eq 0) {
    throw 'The registry releaseSequence is empty.'
}

if ($ManifestPath) {
    $manifestBytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $ManifestPath))
} else {
    $uri = [string]$registry.mojangStableReleaseManifest.url
    if ([string]::IsNullOrWhiteSpace($uri)) {
        throw 'The registry does not declare a Mojang stable release manifest URL.'
    }
    $client = [System.Net.Http.HttpClient]::new()
    try {
        $manifestBytes = $client.GetByteArrayAsync($uri).GetAwaiter().GetResult()
    } finally {
        $client.Dispose()
    }
}

$actualSha256 = ([System.BitConverter]::ToString(
    [System.Security.Cryptography.SHA256]::Create().ComputeHash($manifestBytes)
)).Replace('-', '').ToLowerInvariant()
$recordedSha256 = [string]$registry.mojangStableReleaseManifest.sha256
$checksumMatches = $actualSha256 -eq $recordedSha256.ToLowerInvariant()
if (-not $checksumMatches -and -not $SkipChecksum) {
    $message = "Mojang manifest checksum changed: expected $recordedSha256, actual $actualSha256."
    if ($RequireChecksum) {
        throw "$message Refresh the recorded provenance deliberately."
    }
    Write-Warning "$message Continuing because the required stable-release ledger is validated structurally."
}

$manifest = [System.Text.Encoding]::UTF8.GetString($manifestBytes) | ConvertFrom-Json
$releaseIds = @($manifest.versions | Where-Object { $_.type -eq 'release' } | ForEach-Object { [string]$_.id })
$positions = @{}
for ($index = 0; $index -lt $releaseIds.Count; $index++) {
    if (-not $positions.ContainsKey($releaseIds[$index])) {
        $positions[$releaseIds[$index]] = $index
    }
}

$missing = @($expected | Where-Object { -not $positions.ContainsKey($_) })
if ($missing.Count -gt 0) {
    throw "Mojang stable release manifest is missing required releases: $($missing -join ', ')."
}

# Mojang publishes newest releases first. The checked-in sequence is oldest to
# newest, so each subsequent required release must occur exactly one release
# earlier in the feed. This catches a newly inserted stable release that has not
# yet been added to PackForge's exact support ledger.
for ($index = 1; $index -lt $expected.Count; $index++) {
    $older = $expected[$index - 1]
    $newer = $expected[$index]
    $olderPosition = [int]$positions[$older]
    $newerPosition = [int]$positions[$newer]
    if ($newerPosition -ge $olderPosition) {
        throw "Mojang stable release sequence is not ordered at $older -> $newer."
    }
    if ($olderPosition -ne ($newerPosition + 1)) {
        $unexpected = @()
        if (($olderPosition - $newerPosition) -gt 1) {
            $unexpected = @($releaseIds[($newerPosition + 1)..($olderPosition - 1)])
        }
        $detail = if ($unexpected.Count -gt 0) { " Missing registry releases between them: $($unexpected -join ', ')." } else { '' }
        throw "Mojang stable release sequence is not contiguous at $older -> $newer.$detail"
    }
}

[pscustomobject]@{
    status = 'PASS'
    releases = $expected.Count
    sha256 = $actualSha256
    checksum = if ($SkipChecksum) { 'SKIPPED' } elseif ($checksumMatches) { 'MATCH' } else { 'CHANGED' }
    source = if ($ManifestPath) { (Resolve-Path -LiteralPath $ManifestPath).Path } else { [string]$registry.mojangStableReleaseManifest.url }
} | ConvertTo-Json -Compress
