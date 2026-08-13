[CmdletBinding()]
param(
    [string] $RegistryPath = (Join-Path $PSScriptRoot '..\gradle\minecraft-targets.json'),
    [string] $SettingsPath = (Join-Path $PSScriptRoot '..\settings.gradle'),
    [string] $RootBuildPath = (Join-Path $PSScriptRoot '..\build.gradle'),
    [string] $ForgeBuildPath = (Join-Path $PSScriptRoot '..\platform\forge\build.gradle'),
    [switch] $SelfTest
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Frozen independently from the registry. A registry edit cannot redefine the
# Phase E graph while still satisfying this validator.
$CanonicalDirectNodePaths = @(
    ':fabric:mc26_1_to_26_2',
    ':forge:mc26_1_to_26_2',
    ':neoforge:mc26_1_to_26_2',
    ':fabric:mc1_21_1',
    ':forge:mc1_21_1',
    ':neoforge:mc1_21_1',
    ':fabric:mc1_21',
    ':forge:mc1_21',
    ':neoforge:mc1_21',
    ':fabric:mc1_21_2',
    ':neoforge:mc1_21_2',
    ':fabric:mc1_21_3',
    ':forge:mc1_21_3',
    ':neoforge:mc1_21_3',
    ':fabric:mc1_21_4',
    ':forge:mc1_21_4',
    ':neoforge:mc1_21_4',
    ':fabric:mc1_21_5',
    ':forge:mc1_21_5',
    ':neoforge:mc1_21_5',
    ':fabric:mc1_21_6',
    ':forge:mc1_21_6',
    ':neoforge:mc1_21_6',
    ':fabric:mc1_21_7',
    ':forge:mc1_21_7',
    ':neoforge:mc1_21_7',
    ':fabric:mc1_21_8',
    ':forge:mc1_21_8',
    ':neoforge:mc1_21_8',
    ':fabric:mc1_21_9',
    ':forge:mc1_21_9',
    ':neoforge:mc1_21_9',
    ':fabric:mc1_21_10',
    ':forge:mc1_21_10',
    ':neoforge:mc1_21_10',
    ':fabric:mc1_21_11',
    ':forge:mc1_21_11',
    ':neoforge:mc1_21_11',
    ':fabric:mc1_20_5',
    ':fabric:mc1_20_6',
    ':forge:mc1_20_6',
    ':neoforge:mc1_20_6',
    ':fabric:mc1_20_3',
    ':forge:mc1_20_3',
    ':neoforge:mc1_20_3',
    ':fabric:mc1_20_4',
    ':forge:mc1_20_4',
    ':neoforge:mc1_20_4',
    ':fabric:mc1_20_2',
    ':forge:mc1_20_2',
    ':neoforge:mc1_20_2',
    ':fabric:mc1_20_1',
    ':forge:mc1_20_1'
)
$CanonicalLoaderCounts = @{ fabric = 19; forge = 17; neoforge = 17 }
$CanonicalJoptSimpleOverrides = @{
    ':forge:mc1_21_1' = '5.0.4'
    ':forge:mc1_21_4' = '5.0.4'
}

function Fail([string] $Message) { throw "Stonecutter direct contract: $Message" }

function Assert-ContainsOnce([string] $Text, [string] $Literal, [string] $Context) {
    $count = ([regex]::Matches($Text, [regex]::Escape($Literal))).Count
    if ($count -ne 1) { Fail "$Context must contain exactly one '$Literal'; found $count." }
}

function Get-Section([string] $Text, [string] $Start, [string] $End, [string] $Context) {
    $startIndex = $Text.IndexOf($Start, [StringComparison]::Ordinal)
    if ($startIndex -lt 0) { Fail "$Context start marker is missing." }
    $endIndex = $Text.IndexOf($End, $startIndex + $Start.Length, [StringComparison]::Ordinal)
    if ($endIndex -lt 0) { Fail "$Context end marker is missing." }
    return $Text.Substring($startIndex, $endIndex - $startIndex)
}

function Assert-SameSet([string[]] $Actual, [string[]] $Expected, [string] $Context) {
    $actualSorted = @($Actual | Sort-Object -Unique)
    $expectedSorted = @($Expected | Sort-Object -Unique)
    if ($Actual.Count -ne $actualSorted.Count) { Fail "$Context contains duplicate entries." }
    if ($actualSorted.Count -ne $expectedSorted.Count -or [string]::Join("`n", $actualSorted) -cne [string]::Join("`n", $expectedSorted)) {
        $missing = @($expectedSorted | Where-Object { $_ -cnotin $actualSorted })
        $extra = @($actualSorted | Where-Object { $_ -cnotin $expectedSorted })
        Fail "$Context differs from the frozen set; missing=[$($missing -join ', ')], extra=[$($extra -join ', ')]."
    }
}

function Invoke-DirectContractValidation($Registry, [hashtable] $Sources) {
    if ([int] $Registry.schemaVersion -ne 2) { Fail "unsupported registry schema '$($Registry.schemaVersion)'." }

    $defaultProperties = @($Registry.stonecutterBuildDefaults.PSObject.Properties)
    Assert-SameSet @($defaultProperties.Name) @('fabric', 'forge', 'neoforge') 'Stonecutter default loaders'
    foreach ($property in $defaultProperties) {
        if ($property.Value -cne 'direct') { Fail "default mode for '$($property.Name)' must be direct." }
    }

    $nodes = [Collections.Generic.List[string]]::new()
    $loaderCounts = @{}
    $joptOverrides = @{}
    foreach ($target in @($Registry.targets)) {
        foreach ($platformProperty in @($target.platforms.PSObject.Properties)) {
            $loaderId = $platformProperty.Name
            $loader = $platformProperty.Value
            $nodePath = ":${loaderId}:$($target.key)"
            $nodes.Add($nodePath)
            $loaderCounts[$loaderId] = 1 + [int] ($loaderCounts[$loaderId] ?? 0)

            $modeProperty = $loader.PSObject.Properties['stonecutterBuildMode']
            $defaultProperty = $Registry.stonecutterBuildDefaults.PSObject.Properties[$loaderId]
            $mode = if ($null -ne $modeProperty) { $modeProperty.Value } elseif ($null -ne $defaultProperty) { $defaultProperty.Value } else { $null }
            if ($mode -cne 'direct') { Fail "node '$nodePath' must resolve to direct mode; found '$mode'." }

            $joptProperty = $loader.PSObject.Properties['joptSimpleRuntimeVersion']
            if ($null -ne $joptProperty) {
                if ($loaderId -cne 'forge') { Fail "node '$nodePath' declares Forge-only joptSimpleRuntimeVersion." }
                if ($joptProperty.Value -isnot [string] -or $joptProperty.Value -notmatch '^\d+\.\d+\.\d+$') {
                    Fail "node '$nodePath' has invalid joptSimpleRuntimeVersion '$($joptProperty.Value)'."
                }
                $joptOverrides[$nodePath] = $joptProperty.Value
            }
        }
    }
    Assert-SameSet $nodes.ToArray() $CanonicalDirectNodePaths 'direct node ledger'
    if ($nodes.Count -ne 53) { Fail "direct node ledger must contain exactly 53 cells; found $($nodes.Count)." }
    foreach ($loaderId in $CanonicalLoaderCounts.Keys) {
        if ([int] $loaderCounts[$loaderId] -ne $CanonicalLoaderCounts[$loaderId]) {
            Fail "loader '$loaderId' must contain exactly $($CanonicalLoaderCounts[$loaderId]) direct cells."
        }
    }
    Assert-SameSet @($joptOverrides.Keys) @($CanonicalJoptSimpleOverrides.Keys) 'JOptSimple override nodes'
    foreach ($nodePath in $CanonicalJoptSimpleOverrides.Keys) {
        if ($joptOverrides[$nodePath] -cne $CanonicalJoptSimpleOverrides[$nodePath]) {
            Fail "node '$nodePath' must pin JOptSimple $($CanonicalJoptSimpleOverrides[$nodePath])."
        }
    }

    $settings = $Sources.Settings
    Assert-ContainsOnce $settings "def allowedStonecutterBuildModes = ['direct'] as Set" 'settings direct-only modes'
    Assert-ContainsOnce $settings 'version(targetKey, target.minecraftVersion.toString()).buildscript("../platform/${loaderId}/build.gradle")' 'settings direct leaf buildscript'
    if ($settings.Contains("['delegated', 'direct']") -or $settings.Contains("../stonecutter-build.gradle")) {
        Fail 'settings must not select the delegated buildscript for a root Stonecutter cell.'
    }

    $rootBuild = $Sources.RootBuild
    $registration = Get-Section $rootBuild 'def registerPlatformBuildTask = {' 'platformBuilds.each { platform ->' 'root public build registration'
    Assert-ContainsOnce $registration 'return tasks.register(taskName) {' 'root public build registration'
    Assert-ContainsOnce $registration 'dependsOn "${stonecutterNodePath(target, platform)}:buildStonecutterDirectNode"' 'root public build registration'
    if ($registration -match '\bExec\b|commandLine|nestedCommand|buildStonecutterDelegatedNode') {
        Fail 'root public build registration contains a delegated or nested execution path.'
    }
    if ($rootBuild -match 'nestedCommand\s*\(\s*platform\s*,\s*target\s*,\s*["'']build["'']\s*\)') {
        Fail 'root build retains a nested wrapper invocation for a public build task.'
    }
    $artifactSection = Get-Section $rootBuild 'def artifactSourceDirectory = {' 'def publishedTargetKeys =' 'root artifact source selection'
    if (-not $artifactSection.Contains('directStonecutterArtifactDirectory(target, platform)') -or $artifactSection.Contains('platform.path')) {
        Fail 'root artifact collection must read only direct Stonecutter outputs.'
    }
    Assert-ContainsOnce $rootBuild 'if (expectedDirectNodePaths != stonecutterNodePaths*.toString().sort()) {' 'root all-direct ledger guard'
    Assert-ContainsOnce $rootBuild 'def expectedForgeJoptSimpleRuntimeVersions = [mc1_21_1: "5.0.4", mc1_21_4: "5.0.4"]' 'root Forge JOptSimple registry guard'

    $aggregateSections = @(
        (Get-Section $rootBuild "tasks.register('buildStonecutterAll')" "tasks.register('verifyStonecutterAll')" 'buildStonecutterAll aggregate'),
        (Get-Section $rootBuild "tasks.register('verifyStonecutterAll')" "tasks.register('verifyStonecutterParityAll')" 'verifyStonecutterAll aggregate'),
        (Get-Section $rootBuild 'tasks.register("buildTarget")' 'tasks.register("benchmarkPackIndex"' 'buildTarget aggregate'),
        (Get-Section $rootBuild 'tasks.register("collectPlatformJars"' 'tasks.register("buildLegacySupported"' 'collectPlatformJars aggregate'),
        (Get-Section $rootBuild 'tasks.register("buildLegacySupported"' 'tasks.register("buildCurrentSupported"' 'buildLegacySupported aggregate'),
        (Get-Section $rootBuild 'tasks.register("buildCurrentSupported"' 'tasks.register("buildAllSupported"' 'buildCurrentSupported aggregate'),
        (Get-Section $rootBuild 'tasks.register("buildAllSupported"' 'tasks.register("build")' 'buildAllSupported aggregate'),
        (Get-Section $rootBuild 'tasks.register("build")' 'tasks.register("clean")' 'build aggregate')
    )
    foreach ($section in $aggregateSections) {
        if ($section -match 'buildStonecutterDelegatedNode|verifyStonecutterDirectNode|nestedCommand|\bExec\b|commandLine') {
            Fail 'a public or non-parity aggregate references delegated/parity execution.'
        }
    }
    Assert-ContainsOnce $aggregateSections[0] 'dependsOn directStonecutterBuildTaskPaths' 'buildStonecutterAll direct dependency'
    Assert-ContainsOnce $aggregateSections[1] 'dependsOn directStructuralVerificationTasks' 'verifyStonecutterAll structural dependency'
    Assert-ContainsOnce $aggregateSections[6] 'dependsOn publishedTargets.collect { buildTasksByTarget[it.key] }' 'buildAllSupported task map'
    Assert-ContainsOnce $aggregateSections[7] 'dependsOn tasks.named("buildAllSupported")' 'build task aggregate'

    $rootDelegatedReferences = ([regex]::Matches($rootBuild, [regex]::Escape('buildStonecutterDelegatedNode'))).Count
    if ($rootDelegatedReferences -ne 1 -or -not $rootBuild.Contains("currentTask.name !in ['buildStonecutterDelegatedNode', 'verifyStonecutterDirectNode']")) {
        Fail 'root delegated-task reference must remain only as the NeoForge ordering exclusion.'
    }

    $rollback = $Sources.Rollback
    Assert-ContainsOnce $rollback "tasks.register('buildStonecutterDelegatedNode', Exec)" 'rollback nested task'
    Assert-ContainsOnce $rollback "tasks.register('buildStonecutterCurrentTarget')" 'rollback current-target task'
    Assert-ContainsOnce $rollback "tasks.register('verifyStonecutterCurrentTarget')" 'rollback verification task'
    Assert-ContainsOnce $rollback "-Ppackforge_target=`${currentTarget.key}" 'rollback target argument'
    Assert-ContainsOnce $rollback "'build', '--no-daemon', '--stacktrace'" 'rollback nested build arguments'

    $parity = $Sources.Parity
    $directParityBuild = Get-Section $parity "def directBuild = tasks.register('buildStonecutterDirectNode')" "tasks.register('verifyStonecutterDirectNode')" 'direct node task'
    if ($directParityBuild.Contains('dependsOn(delegatedBuildTask)')) {
        Fail 'authoritative direct node task must not depend on the delegated rollback task.'
    }
    $verifyParity = $parity.Substring($parity.IndexOf("tasks.register('verifyStonecutterDirectNode')", [StringComparison]::Ordinal))
    Assert-ContainsOnce $verifyParity 'dependsOn(delegatedBuildTask)' 'explicit parity oracle'

    foreach ($loaderId in @('fabric', 'forge', 'neoforge')) {
        $loaderSource = $Sources["Loader:$loaderId"]
        $directNodeSection = $loaderSource.Substring($loaderSource.LastIndexOf('if (stonecutterDirectNode) {', [StringComparison]::Ordinal))
        Assert-ContainsOnce $directNodeSection 'stonecutter-build.gradle' "$loaderId rollback apply"
        Assert-ContainsOnce $directNodeSection 'delegatedBuildTaskName:' "$loaderId parity task binding"
        Assert-ContainsOnce $directNodeSection 'packforge-stonecutter-direct-parity.gradle' "$loaderId parity helper apply"
    }

    $forgeBuild = $Sources['Loader:forge']
    Assert-ContainsOnce $forgeBuild 'def joptSimpleRuntimeVersion = loaderConfig.joptSimpleRuntimeVersion?.toString()' 'Forge registry JOptSimple selection'
    Assert-ContainsOnce $forgeBuild 'runtimeOnly "net.sf.jopt-simple:jopt-simple:${joptSimpleRuntimeVersion}"' 'Forge registry JOptSimple dependency'
    Assert-ContainsOnce $forgeBuild 'resolutionStrategy.force "net.sf.jopt-simple:jopt-simple:${joptSimpleRuntimeVersion}"' 'Forge registry JOptSimple force'
    if ($forgeBuild.Contains('needsLegacyJoptSimpleModule') -or $forgeBuild.Contains('jopt-simple:5.0.4')) {
        Fail 'Forge build script must not hard-code JOptSimple target keys or version.'
    }

    return [pscustomobject]@{ DirectCells = $nodes.Count; JoptOverrides = $joptOverrides.Count }
}

function Copy-Registry($Registry) {
    return ($Registry | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function Copy-Sources([hashtable] $Sources) {
    $copy = @{}
    foreach ($key in $Sources.Keys) { $copy[$key] = $Sources[$key] }
    return $copy
}

function Assert-MutationRejected([string] $Name, [scriptblock] $Mutation, $Registry, [hashtable] $Sources) {
    $candidateRegistry = Copy-Registry $Registry
    $candidateSources = Copy-Sources $Sources
    & $Mutation $candidateRegistry $candidateSources
    try {
        Invoke-DirectContractValidation $candidateRegistry $candidateSources | Out-Null
    } catch {
        return
    }
    Fail "self-test '$Name' was accepted."
}

function Invoke-SelfTests($Registry, [hashtable] $Sources) {
    Invoke-DirectContractValidation $Registry $Sources | Out-Null
    Assert-MutationRejected 'delegated-default' { param($r, $s) $r.stonecutterBuildDefaults.forge = 'delegated' } $Registry $Sources
    Assert-MutationRejected 'missing-cell' { param($r, $s) $r.targets[0].platforms.PSObject.Properties.Remove('forge') } $Registry $Sources
    Assert-MutationRejected 'jopt-version-drift' { param($r, $s) $r.targets[1].platforms.forge.joptSimpleRuntimeVersion = '6.0.0' } $Registry $Sources
    Assert-MutationRejected 'settings-delegated-mode' { param($r, $s) $s.Settings = $s.Settings.Replace("['direct'] as Set", "['delegated', 'direct'] as Set") } $Registry $Sources
    Assert-MutationRejected 'settings-delegated-leaf' { param($r, $s) $s.Settings += "`n'../stonecutter-build.gradle'" } $Registry $Sources
    Assert-MutationRejected 'public-build-exec' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('return tasks.register(taskName) {', 'return tasks.register(taskName, Exec) {') } $Registry $Sources
    Assert-MutationRejected 'public-nested-build' { param($r, $s) $s.RootBuild += "`nnestedCommand(platform, target, 'build')" } $Registry $Sources
    Assert-MutationRejected 'aggregate-delegated-task' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('dependsOn publishedTargets.collect { buildTasksByTarget[it.key] }', "dependsOn publishedTargets.collect { buildTasksByTarget[it.key] }`n`tdependsOn 'buildStonecutterDelegatedNode'") } $Registry $Sources
    Assert-MutationRejected 'direct-task-delegates' { param($r, $s) $s.Parity = $s.Parity.Replace('dependsOn directParity.directBuildDependencies', "dependsOn directParity.directBuildDependencies`n`tdependsOn(delegatedBuildTask)") } $Registry $Sources
    Assert-MutationRejected 'forge-hard-coded-jopt' { param($r, $s) $s['Loader:forge'] = $s['Loader:forge'].Replace('${joptSimpleRuntimeVersion}', '5.0.4') } $Registry $Sources
    Write-Output 'Stonecutter direct contract self-test PASS: baseline accepted; 10 mutations rejected.'
}

$requiredPaths = @($RegistryPath, $SettingsPath, $RootBuildPath, $ForgeBuildPath,
    (Join-Path $RepositoryRoot 'platform\fabric\build.gradle'),
    (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle'),
    (Join-Path $RepositoryRoot 'stonecutter-build.gradle'),
    (Join-Path $RepositoryRoot 'gradle\packforge-stonecutter-direct-parity.gradle'))
foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "missing required file '$path'." }
}

try { $registry = Get-Content -LiteralPath $RegistryPath -Raw | ConvertFrom-Json } catch { Fail "registry is not valid JSON: $($_.Exception.Message)" }
$sources = @{
    Settings = Get-Content -LiteralPath $SettingsPath -Raw
    RootBuild = Get-Content -LiteralPath $RootBuildPath -Raw
    Rollback = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'stonecutter-build.gradle') -Raw
    Parity = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'gradle\packforge-stonecutter-direct-parity.gradle') -Raw
    'Loader:fabric' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\fabric\build.gradle') -Raw
    'Loader:forge' = Get-Content -LiteralPath $ForgeBuildPath -Raw
    'Loader:neoforge' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle') -Raw
}
$summary = Invoke-DirectContractValidation $registry $sources
if ($SelfTest) { Invoke-SelfTests $registry $sources }
Write-Output "Stonecutter direct contract PASS: $($summary.DirectCells) direct cells; $($summary.JoptOverrides) registry JOptSimple overrides; delegated wrapper isolated to parity/rollback."
