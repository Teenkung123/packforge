[CmdletBinding()]
param(
    [switch] $PlanOnly,

    [switch] $PrepareOnly,

    [switch] $Resume,

    [string] $ResultsRoot,

    [string[]] $OnlyCell,

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
        if ([string] $record.status -eq 'PASS') {
            $passes[[string] $record.cell] = $record
        }
    }
    return $passes
}

$registry = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json
if ([int] $registry.schemaVersion -ne 2) { throw "Unsupported registry schema $($registry.schemaVersion)." }
$targets = @{}
foreach ($target in $registry.targets) { $targets[[string] $target.key] = $target }
$selectedCells = @($OnlyCell | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) })

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
        if ($selectedCells.Count -gt 0 -and $cellId -notin $selectedCells) { continue }
        $coordinate = Get-ExactLoaderCoordinate -Target $target -Release ([string] $cell.id) -Loader $loader
        $artifact = Get-ArtifactPath -Target $target -Loader $loader -ModVersion $modVersion
        $java = Resolve-RequiredFile -Path $javaPaths[[string] $cell.javaVersion] -Description "Java $($cell.javaVersion) runtime"
        [void] $rows.Add([pscustomobject]@{
            Cell = $cellId
            Release = [string] $cell.id
            Loader = $loader
            Target = [string] $target.key
            Coordinate = $coordinate
            Java = $java
            Artifact = $artifact
            ArtifactHash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToUpperInvariant()
        })
    }
}
if ($selectedCells.Count -eq 0 -and $rows.Count -ne 62) {
    throw "Expected 62 exact production cells, resolved $($rows.Count)."
}

$uniqueArtifactCount = @($rows | ForEach-Object { $_.Artifact } | Sort-Object -Unique).Count
Write-Output "MATRIX resolved=$($rows.Count) artifacts=$uniqueArtifactCount reloads=$ReloadCount"
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
$resultsPath = Join-Path $ResultsRoot 'results.jsonl'
$summaryPath = Join-Path $ResultsRoot 'summary.json'
$priorPasses = if ($Resume.IsPresent) { Get-PriorPasses -Path $resultsPath } else { @{} }
$passed = 0
$failed = 0
$skipped = 0
$index = 0

foreach ($row in $rows) {
    $index++
    $prior = $priorPasses[$row.Cell]
    if ($null -ne $prior -and [string] $prior.artifactHash -eq $row.ArtifactHash) {
        $skipped++
        Write-Output "SKIP $index/$($rows.Count) $($row.Cell) matchingHash=$($row.ArtifactHash)"
        continue
    }

    $supportProfile = $fabricProfiles[$row.Release]
    $profile = if ($row.Loader -eq 'fabric') { $supportProfile } else { $loaderProfiles[$row.Cell] }
    $logName = $row.Cell.Replace('/', '-') + '.log'
    $logPath = Join-Path $ResultsRoot $logName
    $smokeScript = if ($row.Loader -eq 'fabric') {
        Join-Path $PSScriptRoot 'Smoke-Fabric-Production.ps1'
    } elseif ($row.Loader -eq 'forge') {
        Join-Path $PSScriptRoot 'Smoke-Forge-Production.ps1'
    } else {
        Join-Path $PSScriptRoot 'Smoke-NeoForge-Production.ps1'
    }
    $arguments = [Collections.Generic.List[string]]::new()
    $arguments.AddRange([string[]] @('-NoProfile', '-File', $smokeScript))
    if ($row.Loader -eq 'fabric') {
        $arguments.AddRange([string[]] @(
            '-FabricClientRoot', [string] $profile.Root,
            '-VersionName', [string] $profile.VersionName,
            '-MinecraftVersion', [string] $row.Release,
            '-ArtifactPath', [string] $row.Artifact,
            '-AssetsRoot', (Join-Path $supportProfile.Root 'assets'),
            '-NativesRoot', [string] $supportProfile.NativesRoot,
            '-JavaPath', [string] $row.Java,
            '-FallbackLibrariesRoot', (Join-Path $supportProfile.Root 'libraries')
        ))
    } else {
        $clientRootParameter = if ($row.Loader -eq 'forge') { '-ForgeClientRoot' } else { '-NeoForgeClientRoot' }
        $arguments.AddRange([string[]] @(
            $clientRootParameter, [string] $profile.Root,
            '-VersionName', [string] $profile.VersionName,
            '-ArtifactPath', [string] $row.Artifact,
            '-AssetsRoot', (Join-Path $supportProfile.Root 'assets'),
            '-NativesRoot', [string] $supportProfile.NativesRoot,
            '-JavaPath', [string] $row.Java,
            '-FallbackLibrariesRoot', (Join-Path $supportProfile.Root 'libraries'),
            '-ResourcePackPath', (Resolve-ResourcePackFixture -Target $row.Target -PreferredLoader $row.Loader)
        ))
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
        reloads = $ReloadCount
        cleanExit = $cleanExit
        controlledTermination = $controlledTermination
        exitCode = $exitCode
        durationSeconds = $durationSeconds
        status = $status
        passLine = if ($passLine.Count -eq 1) { $passLine[0] } else { $null }
        log = $logPath
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
    $latestRecords[[string] $record.cell] = $record
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
    results = $resultsPath
}
[IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
Write-Output "SUMMARY requested=$($rows.Count) passed=$passed failed=$failed skipped=$skipped cumulative=$cumulativePassed/$($latestValues.Count) cumulativeFailed=$cumulativeFailed results=$ResultsRoot"
if ($failed -gt 0) { throw "$failed exact production cells failed. See $summaryPath." }
