[CmdletBinding()]
param(
    [switch] $PlanOnly,

    [switch] $PrepareOnly,

    [switch] $Resume,

    [string] $ResultsRoot,

    [string[]] $OnlyCell,

    [string] $ProfileId,

    [string] $CompatibilityCatalogPath,

    [string[]] $AdditionalModPaths,

    [string[]] $ExpectedLogMarkers,

    [string[]] $ForbiddenLogMarkers,

    [ValidateRange(60, 3600)]
    [int] $TimeoutSeconds = 900,

    [ValidateRange(1, 10)]
    [int] $ReloadCount = 2
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$registryPath = Join-Path $repositoryRoot 'gradle\minecraft-targets.json'
$artifactsRoot = Join-Path $repositoryRoot 'build\libs'
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$sharedRoots = @{
    fabric = Join-Path $tempRoot 'packforge-fabric-prod-final'
    forge = Join-Path $tempRoot 'packforge-forge-prod-final'
    neoforge = Join-Path $tempRoot 'packforge-neoforge-prod-final'
}
$javaPaths = @{
    '17' = Join-Path $tempRoot 'packforge-temurin17\bin\java.exe'
    '21' = 'C:\Program Files\Java\jdk-21\bin\java.exe'
    '25' = 'C:\Program Files\Java\jdk-25\bin\java.exe'
}

function Get-PropertyValue {
    param($Object, [string] $Name)

    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Resolve-RequiredFile {
    param([string] $Path, [string] $Description)

    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "$Description is missing: $resolved"
    }
    return $resolved
}

function Resolve-CompatibilityProfileMods {
    param([string[]] $Paths)

    $resolvedMods = [Collections.Generic.List[object]]::new()
    $seenNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($path in @($Paths)) {
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        $resolved = Resolve-RequiredFile -Path $path -Description 'Compatibility profile mod'
        $artifact = [IO.Path]::GetFileName($resolved)
        if ([string]::IsNullOrWhiteSpace($artifact) -or $artifact -notmatch '^[A-Za-z0-9][A-Za-z0-9._+\-]*\.jar$') {
            throw "Compatibility profile mod must be a safe JAR filename: $artifact"
        }
        if (-not $seenNames.Add($artifact)) {
            throw "Compatibility profile contains colliding additional-mod filename: $artifact"
        }
        [void] $resolvedMods.Add([pscustomobject]@{
            artifact = $artifact
            sourcePath = $resolved
            sha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToUpperInvariant()
        })
    }
    return @($resolvedMods)
}

function Get-NonEmptyMarkers {
    param([string[]] $Markers)

    return @($Markers | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) } | ForEach-Object { [string] $_ })
}

function Get-Sha256Text {
    param([string] $Text)

    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
        return ([BitConverter]::ToString($sha256.ComputeHash($bytes))).Replace('-', '')
    } finally {
        $sha256.Dispose()
    }
}

function Get-ExactLoaderCoordinate {
    param($Target, [string] $Release, [string] $Loader)

    $requiredByRelease = Get-PropertyValue -Object $Target -Name 'requiredExactSmokeLoaderVersions'
    $exact = Get-PropertyValue -Object $requiredByRelease -Name $Release
    $coordinate = Get-PropertyValue -Object $exact -Name $Loader
    if ([string]::IsNullOrWhiteSpace([string] $coordinate)) {
        $coordinate = Get-PropertyValue -Object (Get-PropertyValue -Object $Target -Name 'loaderBuildVersions') -Name $Loader
    }
    if ([string]::IsNullOrWhiteSpace([string] $coordinate)) {
        throw "Missing $Loader loader coordinate for $Release / $($Target.key)."
    }
    return [string] $coordinate
}

function Get-ArtifactPath {
    param($Target, [string] $Loader, [string] $ModVersion)

    $platform = Get-PropertyValue -Object $Target.platforms -Name $Loader
    if ($null -eq $platform) { throw "Target $($Target.key) has no $Loader platform metadata." }
    $artifactMinecraft = [string] (Get-PropertyValue -Object $platform -Name 'artifactMinecraft')
    if ([string]::IsNullOrWhiteSpace($artifactMinecraft)) {
        $artifactMinecraft = [string] $Target.artifactMinecraft
    }
    $versionSuffix = [string] (Get-PropertyValue -Object $platform -Name 'versionSuffix')
    $artifactName = "packforge-$Loader-$ModVersion$versionSuffix-mc$artifactMinecraft.jar"
    return Resolve-RequiredFile -Path (Join-Path $artifactsRoot $artifactName) -Description 'Final PackForge artifact'
}

function Get-ProfileRoots {
    param([string] $Loader)

    $roots = [Collections.Generic.List[string]]::new()
    if (Test-Path -LiteralPath $sharedRoots[$Loader] -PathType Container) {
        [void] $roots.Add([IO.Path]::GetFullPath($sharedRoots[$Loader]))
    }
    foreach ($directory in Get-ChildItem -LiteralPath $tempRoot -Directory -ErrorAction SilentlyContinue) {
        if ($directory.Name -notlike "packforge-$Loader-prod*") { continue }
        $resolved = [IO.Path]::GetFullPath($directory.FullName)
        if (-not $roots.Contains($resolved)) { [void] $roots.Add($resolved) }
    }
    return @($roots)
}

function Find-Profile {
    param([string] $Loader, [string] $Release, [string] $Coordinate)

    foreach ($root in Get-ProfileRoots -Loader $Loader) {
        $versionsRoot = Join-Path $root 'versions'
        if (-not (Test-Path -LiteralPath $versionsRoot -PathType Container)) { continue }

        if ($Loader -eq 'fabric') {
            $versionName = "$Release-fabric-$Coordinate"
            $metadataPath = Join-Path $versionsRoot "$versionName\$versionName.json"
            if (Test-Path -LiteralPath $metadataPath -PathType Leaf) {
                $isolatedNatives = Join-Path $root (Join-Path 'natives' $versionName)
                return [pscustomobject]@{
                    Root = $root
                    VersionName = $versionName
                    NativesRoot = if (Test-Path -LiteralPath $isolatedNatives -PathType Container) {
                        $isolatedNatives
                    } else {
                        Join-Path $root 'natives'
                    }
                }
            }
            continue
        }

        $coordinateTail = if ($Loader -eq 'forge' -and $Coordinate.StartsWith("$Release-")) {
            $Coordinate.Substring($Release.Length + 1)
        } else {
            $Coordinate
        }
        foreach ($versionDirectory in Get-ChildItem -LiteralPath $versionsRoot -Directory -ErrorAction SilentlyContinue) {
            $versionName = $versionDirectory.Name
            if ($Loader -eq 'forge') {
                if ($versionName -notmatch '(?i)forge' -or $versionName -match '(?i)neoforge') { continue }
            } elseif ($versionName -notmatch '(?i)neoforge') {
                continue
            }
            if ($versionName -notlike "*$coordinateTail*") { continue }
            $metadataPath = Join-Path $versionDirectory.FullName "$versionName.json"
            if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) { continue }
            try {
                $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
            } catch {
                continue
            }
            $inheritsFrom = [string] (Get-PropertyValue -Object $metadata -Name 'inheritsFrom')
            if ($inheritsFrom -eq $Release) {
                return [pscustomobject]@{ Root = $root; VersionName = $versionName }
            }
        }
    }
    return $null
}

function Ensure-LauncherProfileFile {
    param([string] $Root)

    New-Item -ItemType Directory -Path $Root -Force | Out-Null
    $profilesPath = Join-Path $Root 'launcher_profiles.json'
    if (-not (Test-Path -LiteralPath $profilesPath -PathType Leaf)) {
        [IO.File]::WriteAllText($profilesPath, '{"profiles":{}}', [Text.UTF8Encoding]::new($false))
    }
}

function Ensure-FabricProfile {
    param([string] $Release, [string] $Coordinate)

    $profile = Find-Profile -Loader 'fabric' -Release $Release -Coordinate $Coordinate
    if ($PlanOnly.IsPresent) { return $profile }

    $root = if ($null -eq $profile) { $sharedRoots.fabric } else { [string] $profile.Root }
    $isolatedNatives = Join-Path $root (Join-Path 'natives' "$Release-fabric-$Coordinate")
    $nativesReady = Test-Path -LiteralPath $isolatedNatives -PathType Container
    if ($null -eq $profile -or -not $nativesReady) {
        Write-Host "PREPARE Fabric $Release / $Coordinate"
        & (Join-Path $PSScriptRoot 'Prepare-Fabric-ProductionRoot.ps1') `
            -MinecraftVersion $Release `
            -LoaderVersion $Coordinate `
            -ClientRoot $root | Out-Host
    }
    $profile = Find-Profile -Loader 'fabric' -Release $Release -Coordinate $Coordinate
    if ($null -eq $profile) { throw "Fabric profile preparation did not create $Release / $Coordinate." }
    if (-not (Test-Path -LiteralPath $profile.NativesRoot -PathType Container)) {
        throw "Fabric profile preparation did not create isolated natives for $Release / $Coordinate."
    }
    return $profile
}

function Resolve-ResourcePackFixture {
    param([string] $Target, [string] $PreferredLoader)

    foreach ($loader in @($PreferredLoader, 'fabric', 'forge', 'neoforge') | Select-Object -Unique) {
        $candidate = Join-Path $repositoryRoot "platform\$loader\run\$Target\resourcepacks\deterministic-large-pack.zip"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    throw "No deterministic production resource-pack fixture exists for target $Target."
}

function Copy-ImmutableEvidenceInput {
    param(
        [string] $SourcePath,
        [string] $ExpectedHash,
        [string] $DestinationRoot,
        [string] $Description
    )

    $source = Resolve-RequiredFile -Path $SourcePath -Description $Description
    $actualSourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($actualSourceHash -ne $ExpectedHash) {
        throw "$Description changed after profile resolution: expected=$ExpectedHash actual=$actualSourceHash"
    }
    $hashRoot = Join-Path $DestinationRoot $ExpectedHash
    New-Item -ItemType Directory -Path $hashRoot -Force | Out-Null
    $destination = Join-Path $hashRoot ([IO.Path]::GetFileName($source))
    if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) {
        Copy-Item -LiteralPath $source -Destination $destination
    }
    $destinationHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($destinationHash -ne $ExpectedHash) {
        throw "Immutable $Description hash mismatch: expected=$ExpectedHash actual=$destinationHash"
    }
    return [IO.Path]::GetFullPath($destination)
}

function Test-ProfileProvenance {
    param(
        [string] $Path,
        $Row,
        [object[]] $ExpectedAdditionalMods
    )

    if ([string]::IsNullOrWhiteSpace($Path)) { return 'Smoke PASS line omitted compatibility-profile provenance.' }
    try {
        $resolved = Resolve-RequiredFile -Path $Path -Description 'Compatibility profile provenance'
        $provenance = Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
    } catch {
        return $_.Exception.Message
    }
    if ([string] $provenance.artifact -ne [IO.Path]::GetFileName([string] $Row.Artifact)) {
        return 'Compatibility profile provenance names a different PackForge artifact.'
    }
    if ([string] $provenance.sha256 -ne [string] $Row.ArtifactHash) {
        return 'Compatibility profile provenance has a different PackForge artifact hash.'
    }
    try {
        $stagedArtifact = Resolve-RequiredFile -Path ([string] $provenance.stagedPath) -Description 'Staged PackForge artifact'
        $stagedArtifactHash = (Get-FileHash -LiteralPath $stagedArtifact -Algorithm SHA256).Hash.ToUpperInvariant()
    } catch {
        return $_.Exception.Message
    }
    if ($stagedArtifactHash -ne [string] $Row.ArtifactHash) {
        return 'Staged PackForge artifact has the wrong SHA-256.'
    }
    if ([string] $provenance.loader -ne [string] $Row.Loader -or [string] $provenance.target -ne [string] $Row.Target) {
        return 'Compatibility profile provenance has a different loader or target.'
    }
    $actualAdditionalMods = @($provenance.additionalMods)
    if ($actualAdditionalMods.Count -ne $ExpectedAdditionalMods.Count) {
        return 'Compatibility profile provenance has a different additional-mod count.'
    }
    foreach ($expected in $ExpectedAdditionalMods) {
        $matches = @($actualAdditionalMods | Where-Object {
            [string] $_.artifact -eq [string] $expected.artifact -and [string] $_.sha256 -eq [string] $expected.sha256
        })
        if ($matches.Count -ne 1) {
            return "Compatibility profile provenance is missing $($expected.artifact) with SHA-256 $($expected.sha256)."
        }
        try {
            $staged = Resolve-RequiredFile -Path ([string] $matches[0].stagedPath) -Description "Staged compatibility mod $($expected.artifact)"
            $stagedHash = (Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash.ToUpperInvariant()
        } catch {
            return $_.Exception.Message
        }
        if ($stagedHash -ne [string] $expected.sha256) {
            return "Staged compatibility mod $($expected.artifact) has the wrong SHA-256."
        }
    }
    return $null
}

function Ensure-InstallerProfile {
    param(
        [string] $Loader,
        [string] $Release,
        [string] $Coordinate,
        [string] $JavaPath
    )

    $profile = Find-Profile -Loader $Loader -Release $Release -Coordinate $Coordinate
    if ($null -ne $profile) { return $profile }
    if ($PlanOnly.IsPresent) { return $null }

    $root = $sharedRoots[$Loader]
    Ensure-LauncherProfileFile -Root $root
    if ($Loader -eq 'forge') {
        $installerCoordinate = if ($Coordinate.StartsWith("$Release-")) { $Coordinate } else { "$Release-$Coordinate" }
        $installerName = "forge-$installerCoordinate-installer.jar"
        $installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$installerCoordinate/$installerName"
    } else {
        $installerName = "neoforge-$Coordinate-installer.jar"
        $installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$Coordinate/$installerName"
    }
    $installerPath = Join-Path $root $installerName
    if (-not (Test-Path -LiteralPath $installerPath -PathType Leaf)) {
        Write-Output "DOWNLOAD $Loader installer $Coordinate"
        Invoke-WebRequest -Uri $installerUrl -OutFile $installerPath -UseBasicParsing
    }

    Write-Output "PREPARE $Loader $Release / $Coordinate"
    Push-Location $root
    try {
        & $JavaPath -jar $installerPath --installClient $root
        if ($LASTEXITCODE -ne 0) { throw "$Loader installer exited with code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
    $profile = Find-Profile -Loader $Loader -Release $Release -Coordinate $Coordinate
    if ($null -eq $profile) { throw "$Loader installation did not create $Release / $Coordinate." }
    return $profile
}

function Get-PriorPasses {
    param([string] $Path)

    $passes = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $passes }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $record = $line | ConvertFrom-Json } catch { continue }
        $evidenceProperty = $record.PSObject.Properties['evidenceFingerprint']
        if ($null -eq $evidenceProperty -or [string]::IsNullOrWhiteSpace([string] $evidenceProperty.Value)) { continue }
        $key = "$([string] $record.cell)|$([string] $evidenceProperty.Value)"
        if ([string] $record.status -eq 'PASS') { $passes[$key] = $record } else { [void] $passes.Remove($key) }
    }
    return $passes
}

$registry = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json
if ([int] $registry.schemaVersion -ne 2) { throw "Unsupported registry schema $($registry.schemaVersion)." }
$targets = @{}
foreach ($target in $registry.targets) { $targets[[string] $target.key] = $target }
$selectedCells = @($OnlyCell | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) } | ForEach-Object { ([string] $_).Trim() })
$selectedCellSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($selectedCell in $selectedCells) {
    if (-not $selectedCellSet.Add($selectedCell)) {
        throw "Duplicate -OnlyCell selection: $selectedCell"
    }
}

$profileIdProvided = $PSBoundParameters.ContainsKey('ProfileId')
$catalogPathProvided = $PSBoundParameters.ContainsKey('CompatibilityCatalogPath')
if ($profileIdProvided -and [string]::IsNullOrWhiteSpace($ProfileId)) {
    throw '-ProfileId must be a non-empty compatibility profile ID.'
}
if ($catalogPathProvided -and -not $profileIdProvided) {
    throw '-CompatibilityCatalogPath requires -ProfileId.'
}
if ($profileIdProvided) {
    $legacyProfileArguments = @('AdditionalModPaths', 'ExpectedLogMarkers', 'ForbiddenLogMarkers')
    $conflictingArguments = @($legacyProfileArguments | Where-Object { $PSBoundParameters.ContainsKey($_) })
    if ($conflictingArguments.Count -gt 0) {
        throw "-ProfileId cannot be combined with legacy compatibility argument(s): -$($conflictingArguments -join ', -')."
    }

    $resolvedCatalogPath = if ($catalogPathProvided) {
        Resolve-RequiredFile -Path $CompatibilityCatalogPath -Description 'Compatibility profile catalog'
    } else {
        Resolve-RequiredFile -Path (Join-Path $repositoryRoot 'gradle\compatibility-profiles.json') -Description 'Compatibility profile catalog'
    }
    $catalogValidatorPath = Resolve-RequiredFile `
        -Path (Join-Path $PSScriptRoot 'Validate-CompatibilityProfileCatalog.ps1') `
        -Description 'Compatibility profile catalog validator'
    & $catalogValidatorPath -CatalogPath $resolvedCatalogPath -RegistryPath $registryPath | Out-Null

    try {
        $compatibilityCatalog = Get-Content -LiteralPath $resolvedCatalogPath -Raw | ConvertFrom-Json
    } catch {
        throw "Compatibility profile catalog is not valid JSON: $($_.Exception.Message)"
    }
    $selectedProfiles = @($compatibilityCatalog.profiles | Where-Object { [string] $_.id -ceq $ProfileId })
    if ($selectedProfiles.Count -eq 0) {
        throw "Unknown compatibility profile ID '$ProfileId'."
    }
    if ($selectedProfiles.Count -ne 1) {
        throw "Compatibility profile ID '$ProfileId' is duplicated $($selectedProfiles.Count) times."
    }
    $selectedProfile = $selectedProfiles[0]
    $profileRelease = [string] $selectedProfile.minecraftVersion
    $profileLoader = [string] $selectedProfile.loader
    $profileTargetKey = [string] $selectedProfile.packForgeArtifact.targetKey
    $profileReleaseCells = @($registry.releaseCells | Where-Object {
        [string] $_.id -eq $profileRelease -and
        [string] $_.targetKey -eq $profileTargetKey -and
        $profileLoader -in @($_.loaderAvailability)
    })
    if ($profileReleaseCells.Count -ne 1) {
        throw "Compatibility profile '$ProfileId' must resolve exactly one registry release/loader cell; resolved $($profileReleaseCells.Count)."
    }
    $profileCell = "$profileRelease/$profileLoader"
    if ($selectedCells.Count -gt 1 -or ($selectedCells.Count -eq 1 -and -not [string]::Equals($selectedCells[0], $profileCell, [StringComparison]::OrdinalIgnoreCase))) {
        throw "-OnlyCell must be absent or exactly '$profileCell' when -ProfileId '$ProfileId' is selected."
    }

    $catalogSha256 = (Get-FileHash -LiteralPath $resolvedCatalogPath -Algorithm SHA256).Hash.ToUpperInvariant()
    $availability = [string] $selectedProfile.availability
    $declaredResult = if ($availability -eq 'UNAVAILABLE') { 'UNAVAILABLE' } else { 'UNTESTED' }
    $reason = [string] $selectedProfile.reason
    Write-Output "PROFILE id=$ProfileId cell=$profileCell availability=$availability result=$declaredResult catalogSha256=$catalogSha256 reason=$reason"

    if ($availability -in @('PENDING_METADATA', 'UNAVAILABLE')) {
        if ($PlanOnly.IsPresent) { return }
        if ([string]::IsNullOrWhiteSpace($ResultsRoot)) {
            $runId = [datetime]::UtcNow.ToString('yyyyMMdd-HHmmss')
            $ResultsRoot = Join-Path $repositoryRoot "build\production-matrix\$runId"
        }
        $ResultsRoot = [IO.Path]::GetFullPath($ResultsRoot)
        New-Item -ItemType Directory -Path $ResultsRoot -Force | Out-Null
        $resultsPath = Join-Path $ResultsRoot 'results.jsonl'
        $summaryPath = Join-Path $ResultsRoot 'summary.json'
        $timestampUtc = [datetime]::UtcNow.ToString('o')
        $record = [ordered]@{
            timestampUtc = $timestampUtc
            executed = $false
            profileId = $ProfileId
            cell = $profileCell
            release = $profileRelease
            loader = $profileLoader
            target = $profileTargetKey
            catalogSha256 = $catalogSha256
            availability = $availability
            result = $declaredResult
            reason = $reason
        }
        [IO.File]::AppendAllText($resultsPath, (($record | ConvertTo-Json -Compress) + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
        $summary = [ordered]@{
            completedUtc = $timestampUtc
            executed = $false
            profileId = $ProfileId
            cell = $profileCell
            catalogSha256 = $catalogSha256
            availability = $availability
            result = $declaredResult
            reason = $reason
            results = $resultsPath
        }
        [IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
        Write-Output "PROFILE_RESULT id=$ProfileId executed=false result=$declaredResult results=$ResultsRoot"
        return
    }
    if ($availability -eq 'AVAILABLE') {
        throw "Compatibility profile '$ProfileId' is AVAILABLE, but executable schema-2 profile materialization is not implemented in this bounded runner slice."
    }
    throw "Compatibility profile '$ProfileId' has unsupported availability '$availability'."
}

$modVersionLine = Select-String -LiteralPath (Join-Path $repositoryRoot 'gradle.properties') -Pattern '^mod_version=(.+)$'
if ($null -eq $modVersionLine -or $modVersionLine.Matches.Count -ne 1) { throw 'gradle.properties has no unique mod_version.' }
$modVersion = $modVersionLine.Matches[0].Groups[1].Value.Trim()

$rows = [Collections.Generic.List[object]]::new()
foreach ($cell in $registry.releaseCells) {
    $target = $targets[[string] $cell.targetKey]
    if ($null -eq $target) { throw "Release $($cell.id) refers to missing target $($cell.targetKey)." }
    foreach ($loaderValue in $cell.loaderAvailability) {
        $loader = [string] $loaderValue
        $cellId = "$($cell.id)/$loader"
        if ($selectedCellSet.Count -gt 0 -and -not $selectedCellSet.Contains($cellId)) { continue }
        $coordinate = Get-ExactLoaderCoordinate -Target $target -Release ([string] $cell.id) -Loader $loader
        $artifact = Get-ArtifactPath -Target $target -Loader $loader -ModVersion $modVersion
        $java = Resolve-RequiredFile -Path $javaPaths[[string] $cell.javaVersion] -Description "Java $($cell.javaVersion) runtime"
        $fixture = if ($loader -eq 'fabric') { $null } else { Resolve-ResourcePackFixture -Target ([string] $target.key) -PreferredLoader $loader }
        [void] $rows.Add([pscustomobject]@{
            Cell = $cellId
            Release = [string] $cell.id
            Loader = $loader
            Target = [string] $target.key
            Coordinate = $coordinate
            Java = $java
            Artifact = $artifact
            ArtifactHash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToUpperInvariant()
            Fixture = $fixture
            FixtureHash = if ($null -eq $fixture) { 'fabric-active-pack-stack-v1' } else { (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToUpperInvariant() }
        })
    }
}
if ($selectedCells.Count -eq 0 -and $rows.Count -ne 62) {
    throw "Expected 62 exact production cells, resolved $($rows.Count)."
}
if ($selectedCellSet.Count -gt 0) {
    $resolvedCellSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($row in $rows) { [void] $resolvedCellSet.Add([string] $row.Cell) }
    $unknownCells = @($selectedCells | Where-Object { -not $resolvedCellSet.Contains($_) })
    if ($unknownCells.Count -gt 0) {
        throw "Unknown -OnlyCell selection(s): $($unknownCells -join ', ')"
    }
    if ($rows.Count -ne $selectedCellSet.Count) {
        throw "Requested $($selectedCellSet.Count) unique cells but resolved $($rows.Count)."
    }
}
$profileAdditionalMods = @(Resolve-CompatibilityProfileMods -Paths $AdditionalModPaths)
$profileExpectedMarkers = @(Get-NonEmptyMarkers -Markers $ExpectedLogMarkers)
$profileForbiddenMarkers = @(Get-NonEmptyMarkers -Markers $ForbiddenLogMarkers)
$compatibilityProfileRequested = $profileAdditionalMods.Count -gt 0 -or $profileExpectedMarkers.Count -gt 0 -or $profileForbiddenMarkers.Count -gt 0
$profileIdentity = [ordered]@{
    schema = 1
    additionalMods = @($profileAdditionalMods | Sort-Object artifact | ForEach-Object {
        [ordered]@{ artifact = $_.artifact; sha256 = $_.sha256 }
    })
    expectedLogMarkers = @($profileExpectedMarkers | Sort-Object)
    forbiddenLogMarkers = @($profileForbiddenMarkers | Sort-Object)
}
$profileFingerprint = if ($compatibilityProfileRequested) {
    Get-Sha256Text -Text ($profileIdentity | ConvertTo-Json -Compress -Depth 4)
} else {
    'base-v1'
}

$uniqueArtifactCount = @($rows | ForEach-Object { $_.Artifact } | Sort-Object -Unique).Count
Write-Output "MATRIX resolved=$($rows.Count) artifacts=$uniqueArtifactCount reloads=$ReloadCount"
if ($compatibilityProfileRequested) {
    Write-Output "PROFILE fingerprint=$profileFingerprint additionalMods=$($profileAdditionalMods.Count) expectedMarkers=$($profileExpectedMarkers.Count) forbiddenMarkers=$($profileForbiddenMarkers.Count)"
}
foreach ($row in $rows) {
    Write-Output ("PLAN {0,-20} target={1,-16} coordinate={2,-24} artifact={3}" -f $row.Cell, $row.Target, $row.Coordinate, [IO.Path]::GetFileName($row.Artifact))
}
if ($PlanOnly.IsPresent) { return }

$fabricProfiles = @{}
$loaderProfiles = @{}
foreach ($releaseGroup in $rows | Group-Object Release) {
    $releaseCell = @($registry.releaseCells | Where-Object { [string] $_.id -eq $releaseGroup.Name })
    if ($releaseCell.Count -ne 1 -or 'fabric' -notin @($releaseCell[0].loaderAvailability)) {
        throw "Release $($releaseGroup.Name) does not have exactly one Fabric support profile."
    }
    $supportTarget = $targets[[string] $releaseCell[0].targetKey]
    $fabricCoordinate = Get-ExactLoaderCoordinate -Target $supportTarget -Release $releaseGroup.Name -Loader 'fabric'
    $fabricProfiles[$releaseGroup.Name] = Ensure-FabricProfile -Release $releaseGroup.Name -Coordinate $fabricCoordinate
    foreach ($row in $releaseGroup.Group | Where-Object Loader -ne 'fabric') {
        $loaderProfiles[$row.Cell] = Ensure-InstallerProfile `
            -Loader $row.Loader `
            -Release $row.Release `
            -Coordinate $row.Coordinate `
            -JavaPath $row.Java
    }
}

Write-Output "READY profiles=$($rows.Count)"
if ($PrepareOnly.IsPresent) { return }

if ([string]::IsNullOrWhiteSpace($ResultsRoot)) {
    $runId = [datetime]::UtcNow.ToString('yyyyMMdd-HHmmss')
    $ResultsRoot = Join-Path $repositoryRoot "build\production-matrix\$runId"
}
$ResultsRoot = [IO.Path]::GetFullPath($ResultsRoot)
New-Item -ItemType Directory -Path $ResultsRoot -Force | Out-Null
$evidenceInputsRoot = Join-Path $ResultsRoot 'evidence-inputs'
$materializedProfileMods = [Collections.Generic.List[object]]::new()
foreach ($additionalMod in $profileAdditionalMods) {
    $immutablePath = Copy-ImmutableEvidenceInput `
        -SourcePath ([string] $additionalMod.sourcePath) `
        -ExpectedHash ([string] $additionalMod.sha256) `
        -DestinationRoot (Join-Path $evidenceInputsRoot 'mods') `
        -Description "Compatibility profile mod $($additionalMod.artifact)"
    [void] $materializedProfileMods.Add([pscustomobject]@{
        artifact = [string] $additionalMod.artifact
        sha256 = [string] $additionalMod.sha256
        path = $immutablePath
    })
}
foreach ($row in $rows) {
    $immutableArtifact = Copy-ImmutableEvidenceInput `
        -SourcePath ([string] $row.Artifact) `
        -ExpectedHash ([string] $row.ArtifactHash) `
        -DestinationRoot (Join-Path $evidenceInputsRoot 'artifacts') `
        -Description "PackForge artifact for $($row.Cell)"
    $immutableFixture = if ($null -eq $row.Fixture) {
        $null
    } else {
        Copy-ImmutableEvidenceInput `
            -SourcePath ([string] $row.Fixture) `
            -ExpectedHash ([string] $row.FixtureHash) `
            -DestinationRoot (Join-Path $evidenceInputsRoot 'fixtures') `
            -Description "Deterministic fixture for $($row.Cell)"
    }
    $row | Add-Member -NotePropertyName ImmutableArtifact -NotePropertyValue $immutableArtifact
    $row | Add-Member -NotePropertyName ImmutableFixture -NotePropertyValue $immutableFixture
}
$profileInputPath = $null
if ($compatibilityProfileRequested) {
    $profileInputPath = Join-Path $evidenceInputsRoot "compatibility-profile-$profileFingerprint.json"
    $profileInput = [ordered]@{
        schema = 1
        additionalModPaths = @($materializedProfileMods | ForEach-Object { $_.path })
        expectedLogMarkers = @($profileExpectedMarkers)
        forbiddenLogMarkers = @($profileForbiddenMarkers)
    }
    [IO.File]::WriteAllText($profileInputPath, ($profileInput | ConvertTo-Json -Depth 3), [Text.UTF8Encoding]::new($false))
}
$resultsPath = Join-Path $ResultsRoot 'results.jsonl'
$summaryPath = Join-Path $ResultsRoot 'summary.json'
$priorPasses = if ($Resume.IsPresent) { Get-PriorPasses -Path $resultsPath } else { @{} }
$passed = 0
$failed = 0
$skipped = 0
$index = 0
$expectedEvidenceFingerprints = @{}

foreach ($row in $rows) {
    $index++
    $useForgeBackend = $row.Loader -eq 'neoforge' -and $compatibilityProfileRequested
    $smokeScript = if ($row.Loader -eq 'fabric') {
        Join-Path $PSScriptRoot 'Smoke-Fabric-Production.ps1'
    } elseif ($row.Loader -eq 'forge' -or $useForgeBackend) {
        Join-Path $PSScriptRoot 'Smoke-Forge-Production.ps1'
    } else {
        Join-Path $PSScriptRoot 'Smoke-NeoForge-Production.ps1'
    }
    $harnessScripts = [Collections.Generic.List[string]]::new()
    [void] $harnessScripts.Add([IO.Path]::GetFullPath($PSCommandPath))
    [void] $harnessScripts.Add([IO.Path]::GetFullPath($smokeScript))
    if ($row.Loader -eq 'neoforge' -and -not $useForgeBackend) {
        [void] $harnessScripts.Add([IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'Smoke-Forge-Production.ps1')))
    }
    $harnessIdentity = @($harnessScripts | Select-Object -Unique | ForEach-Object {
        [ordered]@{
            artifact = [IO.Path]::GetFileName($_)
            sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToUpperInvariant()
        }
    })
    $evidenceIdentity = [ordered]@{
        schema = 3
        cell = [string] $row.Cell
        coordinate = [string] $row.Coordinate
        artifactHash = [string] $row.ArtifactHash
        fixtureHash = [string] $row.FixtureHash
        profileFingerprint = $profileFingerprint
        reloads = $ReloadCount
        harnesses = $harnessIdentity
    }
    $evidenceFingerprint = Get-Sha256Text -Text ($evidenceIdentity | ConvertTo-Json -Compress)
    $expectedEvidenceFingerprints[[string] $row.Cell] = $evidenceFingerprint
    $prior = $priorPasses["$($row.Cell)|$evidenceFingerprint"]
    if ($null -ne $prior) {
        $skipped++
        Write-Output "SKIP $index/$($rows.Count) $($row.Cell) evidence=$evidenceFingerprint"
        continue
    }

    $supportProfile = $fabricProfiles[$row.Release]
    $profile = if ($row.Loader -eq 'fabric') { $supportProfile } else { $loaderProfiles[$row.Cell] }
    $logName = $row.Cell.Replace('/', '-') + '.log'
    $logPath = Join-Path $ResultsRoot $logName
    $arguments = [Collections.Generic.List[string]]::new()
    $arguments.AddRange([string[]] @('-NoProfile', '-File', $smokeScript))
    if ($row.Loader -eq 'fabric') {
        $arguments.AddRange([string[]] @(
            '-FabricClientRoot', [string] $profile.Root,
            '-VersionName', [string] $profile.VersionName,
            '-MinecraftVersion', [string] $row.Release,
            '-ArtifactPath', [string] $row.ImmutableArtifact,
            '-AssetsRoot', (Join-Path $supportProfile.Root 'assets'),
            '-NativesRoot', [string] $supportProfile.NativesRoot,
            '-JavaPath', [string] $row.Java,
            '-FallbackLibrariesRoot', (Join-Path $supportProfile.Root 'libraries')
        ))
    } else {
        $clientRootParameter = if ($row.Loader -eq 'forge' -or $useForgeBackend) { '-ForgeClientRoot' } else { '-NeoForgeClientRoot' }
        $arguments.AddRange([string[]] @(
            $clientRootParameter, [string] $profile.Root,
            '-VersionName', [string] $profile.VersionName,
            '-ArtifactPath', [string] $row.ImmutableArtifact,
            '-AssetsRoot', (Join-Path $supportProfile.Root 'assets'),
            '-NativesRoot', [string] $supportProfile.NativesRoot,
            '-JavaPath', [string] $row.Java,
            '-FallbackLibrariesRoot', (Join-Path $supportProfile.Root 'libraries'),
            '-ResourcePackPath', [string] $row.ImmutableFixture
        ))
        if ($useForgeBackend) {
            $arguments.AddRange([string[]] @('-Loader', 'neoforge'))
        }
    }
    if ($compatibilityProfileRequested) {
        $arguments.AddRange([string[]] @('-CompatibilityProfilePath', $profileInputPath))
    }
    $arguments.AddRange([string[]] @(
        '-TimeoutSeconds', [string] $TimeoutSeconds,
        '-ReloadCount', [string] $ReloadCount,
        '-AllowControlledTermination'
    ))

    Write-Output "START $index/$($rows.Count) $($row.Cell) version=$($profile.VersionName)"
    $started = [datetime]::UtcNow
    $lines = [Collections.Generic.List[string]]::new()
    & (Join-Path $PSHOME 'pwsh.exe') @arguments 2>&1 | ForEach-Object {
        $line = [string] $_
        [void] $lines.Add($line)
        Write-Host $line
    }
    $exitCode = $LASTEXITCODE
    [IO.File]::WriteAllLines($logPath, @($lines), [Text.UTF8Encoding]::new($false))
    $passLine = @($lines | Where-Object { $_ -match '^PASS .+ production smoke:' } | Select-Object -Last 1)
    $cleanExit = $passLine.Count -eq 1 -and $passLine[0] -match 'cleanExit=true'
    $controlledTermination = $passLine.Count -eq 1 -and $passLine[0] -match 'controlledTermination=true'
    $status = if ($exitCode -eq 0 -and $cleanExit) { 'PASS' } else { 'FAIL' }
    $durationSeconds = [math]::Round(([datetime]::UtcNow - $started).TotalSeconds, 1)
    $provenancePath = $null
    if ($passLine.Count -eq 1 -and $passLine[0] -match '(?:^|\s)provenance=(?<path>.+)$') {
        $provenancePath = $Matches['path']
    }
    $profileValidationError = $null
    if ($status -eq 'PASS') {
        $profileValidationError = Test-ProfileProvenance `
            -Path $provenancePath `
            -Row $row `
            -ExpectedAdditionalMods @($profileAdditionalMods)
        if ($null -ne $profileValidationError) {
            $status = 'FAIL'
            $exitCode = 1
            $validationLine = "PROFILE_VALIDATION_FAILED $profileValidationError"
            [void] $lines.Add($validationLine)
            Write-Host $validationLine
            [IO.File]::WriteAllLines($logPath, @($lines), [Text.UTF8Encoding]::new($false))
        }
    }
    $record = [ordered]@{
        timestampUtc = [datetime]::UtcNow.ToString('o')
        cell = $row.Cell
        release = $row.Release
        loader = $row.Loader
        target = $row.Target
        coordinate = $row.Coordinate
        versionName = [string] $profile.VersionName
        profileRoot = [string] $profile.Root
        artifact = [IO.Path]::GetFileName($row.Artifact)
        artifactHash = $row.ArtifactHash
        fixtureHash = $row.FixtureHash
        reloads = $ReloadCount
        profileFingerprint = $profileFingerprint
        evidenceFingerprint = $evidenceFingerprint
        cleanExit = $cleanExit
        controlledTermination = $controlledTermination
        exitCode = $exitCode
        durationSeconds = $durationSeconds
        status = $status
        passLine = if ($passLine.Count -eq 1) { $passLine[0] } else { $null }
        profileValidationError = $profileValidationError
        log = $logPath
    }
    if ($compatibilityProfileRequested) {
        $record.compatibilityProfile = [ordered]@{
            additionalMods = @($profileAdditionalMods | ForEach-Object {
                [ordered]@{ artifact = $_.artifact; sha256 = $_.sha256 }
            })
            expectedLogMarkers = @($profileExpectedMarkers)
            forbiddenLogMarkers = @($profileForbiddenMarkers)
            provenance = $provenancePath
        }
    }
    [IO.File]::AppendAllText($resultsPath, (($record | ConvertTo-Json -Compress) + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
    if ($status -eq 'PASS') {
        $passed++
    } else {
        $failed++
    }
    Write-Output "RESULT $index/$($rows.Count) $($row.Cell) status=$status seconds=$durationSeconds"
}

$latestRecords = @{}
foreach ($line in Get-Content -LiteralPath $resultsPath) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $record = $line | ConvertFrom-Json } catch { continue }
    $cell = [string] $record.cell
    if (-not $expectedEvidenceFingerprints.ContainsKey($cell)) { continue }
    $evidenceProperty = $record.PSObject.Properties['evidenceFingerprint']
    if ($null -eq $evidenceProperty -or [string] $evidenceProperty.Value -ne [string] $expectedEvidenceFingerprints[$cell]) { continue }
    $latestRecords[$cell] = $record
}
$latestValues = @($latestRecords.Values)
$cumulativePassed = @($latestValues | Where-Object { [string] $_.status -eq 'PASS' }).Count
$cumulativeFailed = @($latestValues | Where-Object { [string] $_.status -ne 'PASS' }).Count
$summary = [ordered]@{
    completedUtc = [datetime]::UtcNow.ToString('o')
    requested = $rows.Count
    passed = $passed
    failed = $failed
    skippedMatchingPass = $skipped
    cumulativeUniqueCells = $latestValues.Count
    cumulativePassed = $cumulativePassed
    cumulativeFailed = $cumulativeFailed
    reloadsPerCell = $ReloadCount
    profileFingerprint = $profileFingerprint
    results = $resultsPath
}
[IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
Write-Output "SUMMARY requested=$($rows.Count) passed=$passed failed=$failed skipped=$skipped cumulative=$cumulativePassed/$($latestValues.Count) cumulativeFailed=$cumulativeFailed results=$ResultsRoot"
if ($failed -gt 0) { throw "$failed exact production cells failed. See $summaryPath." }
