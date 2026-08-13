[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runnerPath = Join-Path $PSScriptRoot 'Run-Exact-ProductionMatrix.ps1'
$fabricSmokePath = Join-Path $PSScriptRoot 'Smoke-Fabric-Production.ps1'
$forgeSmokePath = Join-Path $PSScriptRoot 'Smoke-Forge-Production.ps1'
$neoForgeSmokePath = Join-Path $PSScriptRoot 'Smoke-NeoForge-Production.ps1'
$sourceCatalogPath = Join-Path $repositoryRoot 'gradle\compatibility-profiles.json'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("packforge-profile-runner-test-" + [guid]::NewGuid().ToString('N'))
$pwshPath = Join-Path $PSHOME 'pwsh.exe'

function Write-Catalog {
    param($Catalog, [string] $Path)

    [IO.File]::WriteAllText($Path, ($Catalog | ConvertTo-Json -Depth 100), [Text.UTF8Encoding]::new($false))
}

function Copy-Catalog {
    param($Catalog)

    return ($Catalog | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function Invoke-Runner {
    param([string[]] $Arguments)

    $output = @(& $pwshPath -NoProfile -File $runnerPath @Arguments 2>&1 | ForEach-Object { [string] $_ })
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Invoke-Script {
    param([string] $Path, [string[]] $Arguments)

    $output = @(& $pwshPath -NoProfile -File $Path @Arguments 2>&1 | ForEach-Object { [string] $_ })
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Get-BytesSha256 {
    param([byte[]] $Bytes)

    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '')
    } finally {
        $sha.Dispose()
    }
}

function Write-Bytes {
    param([string] $Path, [byte[]] $Bytes)

    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    [IO.File]::WriteAllBytes($Path, $Bytes)
}

function Write-FixtureManifest {
    param([string] $Directory, [string] $FixtureId, [byte[]] $Bytes)

    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $artifact = "$FixtureId.zip"
    $fixturePath = Join-Path $Directory $artifact
    Write-Bytes -Path $fixturePath -Bytes $Bytes
    $manifestPath = Join-Path $Directory 'compatibility-fixtures-manifest.json'
    $manifest = [ordered]@{
        schemaVersion = 1
        fixtures = @([ordered]@{
            id = $FixtureId
            filename = $artifact
            supportedMinecraft = @('1.21.1')
            sha256 = Get-BytesSha256 -Bytes $Bytes
        })
    }
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
    return $manifestPath
}

function Get-TransportRecord {
    param($Invocation, [string] $Name)

    Assert-Success $Invocation $Name
    $lines = @($Invocation.Output | Where-Object { $_ -like 'PROFILE_TRANSPORT *' })
    if ($lines.Count -ne 1) { throw "$Name did not emit exactly one PROFILE_TRANSPORT record." }
    return ($lines[0].Substring('PROFILE_TRANSPORT '.Length) | ConvertFrom-Json)
}

function Assert-Success {
    param($Invocation, [string] $Name)

    if ($Invocation.ExitCode -ne 0) {
        throw "$Name failed with exit code $($Invocation.ExitCode): $($Invocation.Output -join [Environment]::NewLine)"
    }
}

function Assert-Failure {
    param($Invocation, [string] $Name, [string] $Pattern)

    if ($Invocation.ExitCode -eq 0) { throw "$Name unexpectedly succeeded." }
    $combined = (($Invocation.Output -join ' ') -replace '\x1B\[[0-9;]*[A-Za-z]', '') -replace '\s+', ' '
    if ($combined -notmatch $Pattern) {
        throw "$Name failed without expected pattern '$Pattern': $($Invocation.Output -join [Environment]::NewLine)"
    }
}

function Assert-NoSmokeLaunch {
    param($Invocation, [string] $ResultsPath, [string] $Name)

    $combined = $Invocation.Output -join [Environment]::NewLine
    if ($combined -match '(?m)^(?:START|READY)\s') { throw "$Name entered smoke preparation or launch flow." }
    if (Test-Path -LiteralPath (Join-Path $ResultsPath 'evidence-inputs')) { throw "$Name materialized smoke evidence inputs." }
    if (@(Get-ChildItem -LiteralPath $ResultsPath -Filter '*.log' -File -ErrorAction SilentlyContinue).Count -gt 0) {
        throw "$Name created a smoke log."
    }
}

function Assert-NoNetworkOrSmoke {
    param($Invocation, [string] $Name)

    $combined = $Invocation.Output -join [Environment]::NewLine
    if ($combined -match '(?m)^(?:DOWNLOAD|START|READY)\s') {
        throw "$Name entered network, launcher preparation, or smoke flow."
    }
}

New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
try {
    $catalog = Get-Content -LiteralPath $sourceCatalogPath -Raw | ConvertFrom-Json
    $catalogPath = Join-Path $testRoot 'catalog.json'
    Write-Catalog $catalog $catalogPath

    $unknown = Invoke-Runner @('-PlanOnly', '-ProfileId', 'not-a-profile', '-CompatibilityCatalogPath', $catalogPath)
    Assert-Failure $unknown 'unknown profile rejection' 'Unknown compatibility profile ID'
    $catalogWithoutProfile = Invoke-Runner @('-PlanOnly', '-CompatibilityCatalogPath', $catalogPath)
    Assert-Failure $catalogWithoutProfile 'catalog without profile rejection' 'CompatibilityCatalogPath requires -ProfileId'
    $emptyProfile = Invoke-Runner @('-PlanOnly', '-ProfileId', '')
    Assert-Failure $emptyProfile 'empty profile rejection' 'ProfileId must be a non-empty compatibility profile ID'
    $materializeWithoutProfile = Invoke-Runner @('-MaterializeProfileOnly')
    Assert-Failure $materializeWithoutProfile 'materialize without profile rejection' 'require -ProfileId'

    $conflict = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $catalogPath, '-AdditionalModPaths', 'never-resolve.jar')
    Assert-Failure $conflict 'legacy argument conflict' 'cannot be combined with legacy compatibility argument'

    $duplicateCatalog = Copy-Catalog $catalog
    $duplicateCatalog.profiles = @($duplicateCatalog.profiles) + @((Copy-Catalog $duplicateCatalog.profiles[0]))
    $duplicateCatalogPath = Join-Path $testRoot 'duplicate.json'
    Write-Catalog $duplicateCatalog $duplicateCatalogPath
    $duplicate = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $duplicateCatalogPath)
    Assert-Failure $duplicate 'duplicate profile rejection' '(?:duplicate|duplicated)'

    $wrongCell = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $catalogPath, '-OnlyCell', '1.21.1/forge')
    Assert-Failure $wrongCell 'profile cell mismatch' "OnlyCell must be absent or exactly '1.21.1/fabric'"
    $exactCell = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $catalogPath, '-OnlyCell', '1.21.1/fabric')
    Assert-Success $exactCell 'exact profile cell selection'
    Assert-NoNetworkOrSmoke $exactCell 'exact profile cell selection'
    if (($exactCell.Output -join [Environment]::NewLine) -notmatch 'availability=AVAILABLE') {
        throw 'Exact profile cell selection did not print its declared state.'
    }

    $genericIfResults = Join-Path $testRoot 'generic-if-results'
    $genericIf = Invoke-Runner @(
        '-ProfileId', 'fabric-immediatelyfast',
        '-CompatibilityCatalogPath', $catalogPath,
        '-CompatibilityCacheRoot', (Join-Path $testRoot 'unused-if-cache'),
        '-CompatibilityFixtureManifestPath', (Join-Path $testRoot 'unused-if-fixtures.json'),
        '-MaterializeProfileOnly',
        '-OfflineProfileCache',
        '-ResultsRoot', $genericIfResults
    )
    Assert-Failure $genericIf 'ImmediatelyFast generic success marker rejection' 'HOOK_PRESERVING_COALESCED_PATH'
    Assert-NoNetworkOrSmoke $genericIf 'ImmediatelyFast generic success marker rejection'
    if (Test-Path -LiteralPath $genericIfResults) {
        throw 'ImmediatelyFast profile without path-specific evidence materialized result inputs.'
    }

    $pendingProfileId = 'fabric-sodium'
    $pendingResults = Join-Path $testRoot 'pending-results'
    $pending = Invoke-Runner @('-ProfileId', $pendingProfileId, '-CompatibilityCatalogPath', $catalogPath, '-ResultsRoot', $pendingResults)
    Assert-Success $pending 'pending profile recording'
    Assert-NoSmokeLaunch $pending $pendingResults 'pending profile recording'
    $pendingRecord = Get-Content -LiteralPath (Join-Path $pendingResults 'results.jsonl') -Raw | ConvertFrom-Json
    $pendingSummary = Get-Content -LiteralPath (Join-Path $pendingResults 'summary.json') -Raw | ConvertFrom-Json
    if ($pendingRecord.executed -ne $false -or $pendingRecord.profileId -ne $pendingProfileId -or $pendingRecord.availability -ne 'PENDING_METADATA' -or $pendingRecord.result -ne 'UNTESTED') {
        throw 'Pending results.jsonl record does not preserve the non-executed profile state.'
    }
    if ($pendingSummary.executed -ne $false -or $pendingSummary.catalogSha256 -notmatch '^[A-F0-9]{64}$') {
        throw 'Pending summary.json does not preserve execution state and catalog identity.'
    }

    $unavailableProfileId = 'fabric-resource-pack-unbounded'
    $unavailableResults = Join-Path $testRoot 'unavailable-results'
    $unavailable = Invoke-Runner @('-ProfileId', $unavailableProfileId, '-CompatibilityCatalogPath', $catalogPath, '-ResultsRoot', $unavailableResults)
    Assert-Success $unavailable 'unavailable profile recording'
    Assert-NoSmokeLaunch $unavailable $unavailableResults 'unavailable profile recording'
    $unavailableRecord = Get-Content -LiteralPath (Join-Path $unavailableResults 'results.jsonl') -Raw | ConvertFrom-Json
    if ($unavailableRecord.executed -ne $false -or $unavailableRecord.availability -ne 'UNAVAILABLE' -or $unavailableRecord.result -ne 'UNAVAILABLE') {
        throw 'Unavailable results.jsonl record does not preserve the non-executed profile state.'
    }

    $availableCatalog = Copy-Catalog $catalog
    $availableProfile = @($availableCatalog.profiles | Where-Object id -eq 'fabric-quick-pack')[0]
    $modBytes = [Text.Encoding]::UTF8.GetBytes('offline pinned compatibility mod')
    $modHash = Get-BytesSha256 -Bytes $modBytes
    $availableMod = $availableProfile.externalMods[0]
    $availableMod.coordinate = 'modrinth:quick-pack-test'
    $availableMod.sourceUrl = 'https://cdn.modrinth.com/data/quick-pack-test/versions/1.0.0/quick-pack-test.jar'
    $availableMod.version = '1.0.0'
    $availableMod.sha256 = $modHash
    $availableCatalogPath = Join-Path $testRoot 'available.json'
    Write-Catalog $availableCatalog $availableCatalogPath
    $cacheRoot = Join-Path $testRoot 'cache'
    $cachedModPath = Join-Path (Join-Path (Join-Path $cacheRoot 'sha256') $modHash) 'quick-pack-test.jar'
    Write-Bytes -Path $cachedModPath -Bytes $modBytes
    $fixtureBytes = [Text.Encoding]::UTF8.GetBytes('offline compatibility resource pack fixture')
    $fixtureManifestPath = Write-FixtureManifest -Directory (Join-Path $testRoot 'fixtures') -FixtureId 'normal-resource-pack' -Bytes $fixtureBytes
    $availableResults = Join-Path $testRoot 'available-results'
    $available = Invoke-Runner @(
        '-ProfileId', 'fabric-quick-pack',
        '-CompatibilityCatalogPath', $availableCatalogPath,
        '-CompatibilityCacheRoot', $cacheRoot,
        '-CompatibilityFixtureManifestPath', $fixtureManifestPath,
        '-MaterializeProfileOnly',
        '-OfflineProfileCache',
        '-ResultsRoot', $availableResults
    )
    Assert-Success $available 'offline available profile materialization'
    Assert-NoNetworkOrSmoke $available 'offline available profile materialization'
    $schemaPath = Join-Path $availableResults 'evidence-inputs\compatibility-profile-fabric-quick-pack.json'
    $schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
    if ([int] $schema.schema -ne 2 -or $schema.profileId -ne 'fabric-quick-pack' -or $schema.catalogSha256 -notmatch '^[A-F0-9]{64}$' -or
        $schema.loader -ne 'fabric' -or $schema.target -ne 'mc1_21_1' -or $schema.expectedPath -ne 'EXTERNALLY_OWNED_PATH') {
        throw 'Materialized schema-2 profile lost identity, cell, catalog, or expected-path metadata.'
    }
    if (@($schema.runtimeMods).Count -ne 1 -or @($schema.modIds).Count -ne 1 -or $schema.modIds[0] -ne 'quick-pack') {
        throw 'Materialized schema-2 profile lost runtime mod IDs.'
    }
    if (-not (Test-Path -LiteralPath ([string] $schema.runtimeMods[0].path) -PathType Leaf) -or
        (Get-FileHash -LiteralPath ([string] $schema.runtimeMods[0].path) -Algorithm SHA256).Hash.ToUpperInvariant() -ne $modHash) {
        throw 'Materialized schema-2 runtime mod path/hash is invalid.'
    }
    if ($schema.fixture.id -ne 'normal-resource-pack' -or -not (Test-Path -LiteralPath ([string] $schema.fixture.path) -PathType Leaf)) {
        throw 'Materialized schema-2 fixture identity/path is invalid.'
    }

    $wrongCacheRoot = Join-Path $testRoot 'wrong-cache'
    $wrongCachedPath = Join-Path (Join-Path (Join-Path $wrongCacheRoot 'sha256') $modHash) 'quick-pack-test.jar'
    Write-Bytes -Path $wrongCachedPath -Bytes ([Text.Encoding]::UTF8.GetBytes('wrong bytes'))
    $hashMismatch = Invoke-Runner @(
        '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $availableCatalogPath,
        '-CompatibilityCacheRoot', $wrongCacheRoot, '-CompatibilityFixtureManifestPath', $fixtureManifestPath,
        '-MaterializeProfileOnly', '-OfflineProfileCache', '-ResultsRoot', (Join-Path $testRoot 'hash-mismatch-results')
    )
    Assert-Failure $hashMismatch 'cached hash mismatch rejection' 'Compatibility cache SHA-256 mismatch'
    Assert-NoNetworkOrSmoke $hashMismatch 'cached hash mismatch rejection'

    $collisionCatalog = Copy-Catalog $availableCatalog
    $collisionProfile = @($collisionCatalog.profiles | Where-Object id -eq 'fabric-quick-pack')[0]
    $collisionProfile.dependencies = @([pscustomobject]@{
        id = 'quick-pack-companion'
        coordinate = 'modrinth:quick-pack-companion'
        sourceUrl = 'https://cdn.modrinth.com/data/quick-pack-companion/versions/2.0.0/quick-pack-test.jar'
        version = '2.0.0'
        sha256 = Get-BytesSha256 -Bytes ([Text.Encoding]::UTF8.GetBytes('different companion bytes'))
    })
    $collisionProfile.expectedLogMarkers = @($collisionProfile.expectedLogMarkers) + @('quick-pack-companion:true:')
    $collisionCatalogPath = Join-Path $testRoot 'collision.json'
    Write-Catalog $collisionCatalog $collisionCatalogPath
    $collision = Invoke-Runner @(
        '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $collisionCatalogPath,
        '-CompatibilityCacheRoot', (Join-Path $testRoot 'collision-cache'), '-CompatibilityFixtureManifestPath', $fixtureManifestPath,
        '-MaterializeProfileOnly', '-OfflineProfileCache', '-ResultsRoot', (Join-Path $testRoot 'collision-results')
    )
    Assert-Failure $collision 'runtime mod filename collision rejection' 'colliding runtime-mod filename'
    Assert-NoNetworkOrSmoke $collision 'runtime mod filename collision rejection'

    $dummy = 'not-used-in-profile-validation-only-mode'
    $fabricTransport = Get-TransportRecord (Invoke-Script $fabricSmokePath @(
        '-FabricClientRoot', $dummy, '-VersionName', '1.21.1-fabric-test', '-MinecraftVersion', '1.21.1',
        '-ArtifactPath', $dummy, '-AssetsRoot', $dummy, '-NativesRoot', $dummy, '-JavaPath', $dummy,
        '-CompatibilityProfilePath', $schemaPath, '-ValidateCompatibilityProfileOnly',
        '-ExpectedProfileMinecraftVersion', '1.21.1', '-ExpectedProfileTarget', 'mc1_21_1'
    )) 'Fabric schema-2 transport'
    if ($fabricTransport.schema -ne 2 -or $fabricTransport.loader -ne 'fabric' -or @($fabricTransport.modIds)[0] -ne 'quick-pack' -or
        [IO.Path]::GetFullPath([string] $fabricTransport.fixturePath) -ne [IO.Path]::GetFullPath([string] $schema.fixture.path)) {
        throw "Fabric schema-2 transport changed profile identity, mod IDs, or fixture path: $($fabricTransport | ConvertTo-Json -Compress -Depth 6)"
    }

    foreach ($loaderCase in @(
        [pscustomobject]@{ Loader = 'forge'; Script = $forgeSmokePath; RootParameter = '-ForgeClientRoot'; ProfileId = 'forge-quick-pack' },
        [pscustomobject]@{ Loader = 'neoforge'; Script = $neoForgeSmokePath; RootParameter = '-NeoForgeClientRoot'; ProfileId = 'neoforge-quick-pack' }
    )) {
        $loaderSchema = Copy-Catalog $schema
        $loaderSchema.loader = $loaderCase.Loader
        $loaderSchema.profileId = $loaderCase.ProfileId
        $loaderSchemaPath = Join-Path $testRoot "$($loaderCase.Loader)-schema.json"
        [IO.File]::WriteAllText($loaderSchemaPath, ($loaderSchema | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
        $transport = Get-TransportRecord (Invoke-Script $loaderCase.Script @(
            $loaderCase.RootParameter, $dummy, '-VersionName', '1.21.1-loader-test', '-ArtifactPath', $dummy,
            '-AssetsRoot', $dummy, '-NativesRoot', $dummy, '-JavaPath', $dummy,
            '-CompatibilityProfilePath', $loaderSchemaPath, '-ValidateCompatibilityProfileOnly',
            '-ExpectedProfileMinecraftVersion', '1.21.1', '-ExpectedProfileTarget', 'mc1_21_1'
        )) "$($loaderCase.Loader) schema-2 transport"
        if ($transport.schema -ne 2 -or $transport.loader -ne $loaderCase.Loader -or $transport.profileId -ne $loaderCase.ProfileId) {
            throw "$($loaderCase.Loader) schema-2 transport changed profile identity or loader."
        }

        $mismatchedLoaderSchema = Copy-Catalog $loaderSchema
        $mismatchedLoaderSchema.target = 'mc26_1_to_26_2'
        $mismatchedLoaderSchemaPath = Join-Path $testRoot "$($loaderCase.Loader)-mismatched-cell-schema.json"
        [IO.File]::WriteAllText($mismatchedLoaderSchemaPath, ($mismatchedLoaderSchema | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
        $mismatchedCell = Invoke-Script $loaderCase.Script @(
            $loaderCase.RootParameter, $dummy, '-VersionName', '1.21.1-loader-test', '-ArtifactPath', $dummy,
            '-AssetsRoot', $dummy, '-NativesRoot', $dummy, '-JavaPath', $dummy,
            '-CompatibilityProfilePath', $mismatchedLoaderSchemaPath, '-ValidateCompatibilityProfileOnly',
            '-ExpectedProfileMinecraftVersion', '1.21.1', '-ExpectedProfileTarget', 'mc1_21_1'
        )
        Assert-Failure $mismatchedCell "$($loaderCase.Loader) schema-2 runtime-cell mismatch rejection" 'runtime cell mismatch'
    }


    $mismatchedFabricSchema = Copy-Catalog $schema
    $mismatchedFabricSchema.minecraftVersion = '26.1'
    $mismatchedFabricSchemaPath = Join-Path $testRoot 'fabric-mismatched-cell-schema.json'
    [IO.File]::WriteAllText($mismatchedFabricSchemaPath, ($mismatchedFabricSchema | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    $mismatchedFabricCell = Invoke-Script $fabricSmokePath @(
        '-FabricClientRoot', $dummy, '-VersionName', '1.21.1-fabric-test', '-MinecraftVersion', '1.21.1',
        '-ArtifactPath', $dummy, '-AssetsRoot', $dummy, '-NativesRoot', $dummy, '-JavaPath', $dummy,
        '-CompatibilityProfilePath', $mismatchedFabricSchemaPath, '-ValidateCompatibilityProfileOnly',
        '-ExpectedProfileMinecraftVersion', '1.21.1', '-ExpectedProfileTarget', 'mc1_21_1'
    )
    Assert-Failure $mismatchedFabricCell 'Fabric schema-2 runtime-cell mismatch rejection' 'runtime cell mismatch'

    $overrideSchema = Copy-Catalog $schema
    $overrideSchema.featureOverrides = [pscustomobject]@{ atlasMipParallelEnabled = $true }
    $overrideSchema.config.overrides = [pscustomobject]@{ atlasMipParallelEnabled = $true }
    $overrideSchemaPath = Join-Path $testRoot 'override-schema.json'
    [IO.File]::WriteAllText($overrideSchemaPath, ($overrideSchema | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    $overrideBlocker = Invoke-Script $fabricSmokePath @(
        '-FabricClientRoot', $dummy, '-VersionName', '1.21.1-fabric-test', '-MinecraftVersion', '1.21.1',
        '-ArtifactPath', $dummy, '-AssetsRoot', $dummy, '-NativesRoot', $dummy, '-JavaPath', $dummy,
        '-CompatibilityProfilePath', $overrideSchemaPath, '-ValidateCompatibilityProfileOnly',
        '-ExpectedProfileMinecraftVersion', '1.21.1', '-ExpectedProfileTarget', 'mc1_21_1'
    )
    Assert-Failure $overrideBlocker 'feature override fail-closed behavior' 'refusing launch'

    Write-Output 'Exact production matrix profile self-test PASS: selection states, path-evidence fail-closed gate, offline hash cache, fixture/schema-2 materialization, hash mismatch, filename collision, exact Fabric/Forge/NeoForge cell transport, and override fail-closed paths verified without network, build, or smoke launch.'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
