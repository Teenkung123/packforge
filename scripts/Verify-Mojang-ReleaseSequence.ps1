param(
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\gradle\minecraft-targets.json'),
    [string]$ManifestPath,
    [switch]$SkipChecksum
)

$ErrorActionPreference = 'Stop'

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
if (-not $SkipChecksum -and $actualSha256 -ne $recordedSha256.ToLowerInvariant()) {
    throw "Mojang manifest checksum changed: expected $recordedSha256, actual $actualSha256. Refresh the recorded provenance deliberately."
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
# newest, so each subsequent required release must occur earlier in the feed.
for ($index = 1; $index -lt $expected.Count; $index++) {
    if ($positions[$expected[$index]] -ge $positions[$expected[$index - 1]]) {
        throw "Mojang stable release sequence is not contiguous/ordered at $($expected[$index - 1]) -> $($expected[$index])."
    }
}

[pscustomobject]@{
    status = 'PASS'
    releases = $expected.Count
    sha256 = $actualSha256
    source = if ($ManifestPath) { (Resolve-Path -LiteralPath $ManifestPath).Path } else { [string]$registry.mojangStableReleaseManifest.url }
} | ConvertTo-Json -Compress
