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
$CanonicalFabricNativeArchiveTargets = @(
    'mc1_20_2',
    'mc1_20_3',
    'mc1_20_4',
    'mc1_20_5',
    'mc1_20_6',
    'mc1_21',
    'mc1_21_1',
    'mc1_21_2',
    'mc1_21_3',
    'mc1_21_4',
    'mc1_21_5',
    'mc1_21_6',
    'mc1_21_7',
    'mc1_21_8',
    'mc1_21_9',
    'mc1_21_10'
)
$CanonicalFabricNonNativeArchiveTargets = @('mc1_20_1', 'mc1_21_11', 'mc26_1_to_26_2')

function Fail([string] $Message) { throw "Stonecutter direct contract: $Message" }

function Assert-ContainsOnce([string] $Text, [string] $Literal, [string] $Context) {
    $count = ([regex]::Matches($Text, [regex]::Escape($Literal))).Count
    if ($count -ne 1) { Fail "$Context must contain exactly one '$Literal'; found $count." }
}

function Assert-ContainsCount([string] $Text, [string] $Literal, [int] $ExpectedCount, [string] $Context) {
    $count = ([regex]::Matches($Text, [regex]::Escape($Literal))).Count
    if ($count -ne $ExpectedCount) { Fail "$Context must contain exactly $ExpectedCount '$Literal' entries; found $count." }
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
    $fabricTargetKeys = @($Registry.targets | Where-Object { $null -ne $_.platforms.PSObject.Properties['fabric'] } | ForEach-Object { $_.key })
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cin $CanonicalFabricNativeArchiveTargets }) $CanonicalFabricNativeArchiveTargets 'Fabric native archive targets'
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cnotin $CanonicalFabricNativeArchiveTargets }) $CanonicalFabricNonNativeArchiveTargets 'Fabric non-native archive targets'
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
    Assert-ContainsOnce $rootBuild 'file("fabric/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java")' 'root Fabric native archive validator input'

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

    $nativeArchive = $Sources.FabricNativeArchive
    Assert-ContainsOnce $nativeArchive '//? if >=1.20.2 && <=1.21.10 {' 'Fabric native archive outer target guard'
    Assert-ContainsCount $nativeArchive '//? if >=1.20.5 {' 2 'Fabric native archive constructor seam'
    Assert-ContainsOnce $nativeArchive '//?} else {' 'Fabric native archive legacy constructor branch'
    Assert-ContainsOnce $nativeArchive 'package com.teenkung.packforge.mixin.loader;' 'Fabric native archive package'
    Assert-ContainsOnce $nativeArchive 'public abstract class FilePackResourcesArchiveMixin {' 'Fabric native archive class'
    Assert-ContainsOnce $nativeArchive 'import net.minecraft.server.packs.PackLocationInfo;' 'Fabric native archive modern import'
    Assert-ContainsOnce $nativeArchive 'method = "<init>(Lnet/minecraft/server/packs/PackLocationInfo;Lnet/minecraft/server/packs/FilePackResources$SharedZipFileAccess;Ljava/lang/String;)V"' 'Fabric native archive modern constructor descriptor'
    Assert-ContainsOnce $nativeArchive 'PackLocationInfo location,' 'Fabric native archive modern constructor parameters'
    Assert-ContainsOnce $nativeArchive '/*@Inject(method = "<init>", at = @At("RETURN"))' 'Fabric native archive commented legacy constructor'
    Assert-ContainsOnce $nativeArchive 'String name,' 'Fabric native archive legacy name parameter'
    Assert-ContainsOnce $nativeArchive 'boolean closeOnExit,' 'Fabric native archive legacy close parameter'
    Assert-ContainsCount $nativeArchive 'holder.packforge$setArchive(bridge);' 2 'Fabric native archive shared capture behavior'
    Assert-ContainsCount $nativeArchive 'private void packforge$captureArchive(' 2 'Fabric native archive constructor implementations'
    Assert-ContainsCount $nativeArchive '//? if ' 3 'Fabric native archive conditional openers'
    Assert-ContainsCount $nativeArchive '//?}' 4 'Fabric native archive conditional closers'

    $fabricBuild = $Sources['Loader:fabric']
    $nativeTransport = Get-Section $fabricBuild 'def selectedMainJavaSources = files(selectedSources.mainJavaSources)' 'sourceSets {' 'Fabric native archive source transport'
    Assert-ContainsOnce $nativeTransport 'def compileMainJavaSources = selectedMainJavaSources' 'Fabric standalone selected-source preservation'
    Assert-ContainsOnce $nativeTransport 'def legacyArchiveSources = [] as Set' 'Fabric standalone empty archive exclusion set'
    Assert-ContainsOnce $nativeTransport 'if (stonecutterDirectNode) {' 'Fabric native archive direct-only transport'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java' 'Fabric legacy archive exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/mc1_21_shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java' 'Fabric modern archive exclusion'
    Assert-ContainsCount $nativeTransport 'new File(physicalRepositoryRoot, ' 2 'Fabric exact legacy archive exclusions'
    Assert-ContainsOnce $nativeTransport 'selectedMainJavaSources.filter { source -> source.canonicalFile !in legacyArchiveSources }' 'Fabric archive source replacement'
    Assert-ContainsOnce $nativeTransport 'stonecutter.tasks.generatedSourcesDir.dir("main/java")' 'Fabric generated Java compile root'
    if ($fabricBuild.Contains('stonecutter.tasks.configureSource(')) {
        Fail 'Fabric must reuse Stonecutter 0.9.7 automatic SourceSet registration instead of configuring main twice.'
    }

    $fabricSourceSets = Get-Section $fabricBuild 'sourceSets {' 'tasks.named("compileJava", JavaCompile)' 'Fabric native archive source set'
    Assert-ContainsOnce $fabricSourceSets 'if (stonecutterDirectNode) {' 'Fabric generated source-set direct guard'
    Assert-ContainsOnce $fabricSourceSets 'java.srcDir(stonecutter.tasks.generatedSourcesDir.dir("main/java"))' 'Fabric generated Java main source set'
    $fabricCompileJava = Get-Section $fabricBuild 'tasks.named("compileJava", JavaCompile) {' 'tasks.named("compileClientJava", JavaCompile)' 'Fabric native archive compile task'
    Assert-ContainsOnce $fabricCompileJava 'if (stonecutterDirectNode) {' 'Fabric compile generation direct guard'
    Assert-ContainsOnce $fabricCompileJava 'dependsOn(stonecutter.tasks.generate["main"])' 'Fabric compile generation dependency'
    Assert-ContainsOnce $fabricCompileJava 'setSource(compileMainJavaSources)' 'Fabric compile source replacement'
    $fabricSourcesJar = Get-Section $fabricBuild 'tasks.named("sourcesJar") {' 'if (loaderConfig.mappingMode == "named") {' 'Fabric native archive sources JAR'
    Assert-ContainsOnce $fabricSourcesJar 'if (stonecutterDirectNode) {' 'Fabric sources JAR generation direct guard'
    Assert-ContainsOnce $fabricSourcesJar 'dependsOn(stonecutter.tasks.generate["main"])' 'Fabric sources JAR generation dependency'
    Assert-ContainsOnce $fabricSourcesJar 'if (details.file.canonicalFile in legacyArchiveSources) {' 'Fabric sources JAR physical legacy-source filter'
    Assert-ContainsOnce $fabricSourcesJar 'details.exclude()' 'Fabric sources JAR legacy-source exclusion'
    Assert-ContainsCount $fabricBuild 'stonecutter.tasks.generatedSourcesDir.dir("main/java")' 2 'Fabric exact generated Java root wiring'
    if ($fabricBuild.Contains('stonecutter.tasks.generatedSourcesDir.dir("main")')) {
        Fail 'Fabric must not expose the Stonecutter source-set container as a Java root; use main/java.'
    }
    Assert-ContainsCount $fabricBuild 'dependsOn(stonecutter.tasks.generate["main"])' 2 'Fabric exact generation task wiring'
    foreach ($loaderId in @('forge', 'neoforge')) {
        if ($Sources["Loader:$loaderId"].Contains('FilePackResourcesArchiveMixin.java')) {
            Fail "$loaderId build script must not consume the Fabric native archive pilot."
        }
    }

    $forgeBuild = $Sources['Loader:forge']
    Assert-ContainsOnce $forgeBuild 'def joptSimpleRuntimeVersion = loaderConfig.joptSimpleRuntimeVersion?.toString()' 'Forge registry JOptSimple selection'
    Assert-ContainsOnce $forgeBuild 'runtimeOnly "net.sf.jopt-simple:jopt-simple:${joptSimpleRuntimeVersion}"' 'Forge registry JOptSimple dependency'
    Assert-ContainsOnce $forgeBuild 'resolutionStrategy.force "net.sf.jopt-simple:jopt-simple:${joptSimpleRuntimeVersion}"' 'Forge registry JOptSimple force'
    if ($forgeBuild.Contains('needsLegacyJoptSimpleModule') -or $forgeBuild.Contains('jopt-simple:5.0.4')) {
        Fail 'Forge build script must not hard-code JOptSimple target keys or version.'
    }

    return [pscustomobject]@{ DirectCells = $nodes.Count; JoptOverrides = $joptOverrides.Count; NativeArchiveCells = $CanonicalFabricNativeArchiveTargets.Count }
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
    Assert-MutationRejected 'native-archive-range-drift' { param($r, $s) $s.FabricNativeArchive = $s.FabricNativeArchive.Replace('<=1.21.10', '<=1.21.11') } $Registry $Sources
    Assert-MutationRejected 'native-archive-constructor-seam-drift' { param($r, $s) $s.FabricNativeArchive = $s.FabricNativeArchive.Replace('>=1.20.5', '>=1.20.6') } $Registry $Sources
    Assert-MutationRejected 'native-archive-legacy-branch-active' { param($r, $s) $s.FabricNativeArchive = $s.FabricNativeArchive.Replace('/*@Inject(method = "<init>"', '@Inject(method = "<init>"') } $Registry $Sources
    Assert-MutationRejected 'native-archive-legacy-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java', 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-archive-extra-exclusion' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace("`tlegacyArchiveSources = [", "`tlegacyArchiveSources = [`n`t`tnew File(physicalRepositoryRoot, `"versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/Extra.java`"),") } $Registry $Sources
    Assert-MutationRejected 'native-archive-generated-source-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('java.srcDir(stonecutter.tasks.generatedSourcesDir.dir("main/java"))', 'java.srcDir("src/main/java")') } $Registry $Sources
    Assert-MutationRejected 'native-archive-generated-root-too-high' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('stonecutter.tasks.generatedSourcesDir.dir("main/java")', 'stonecutter.tasks.generatedSourcesDir.dir("main")') } $Registry $Sources
    Assert-MutationRejected 'native-archive-compile-generation-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('dependsOn(stonecutter.tasks.generate["main"])', 'dependsOn(tasks.named("classes"))') } $Registry $Sources
    Assert-MutationRejected 'native-archive-sources-jar-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('details.exclude()', 'details.path') } $Registry $Sources
    Assert-MutationRejected 'native-archive-duplicate-source-registration' { param($r, $s) $s['Loader:fabric'] += "`nstonecutter.tasks.configureSource(sourceSets.main)" } $Registry $Sources
    Assert-MutationRejected 'native-archive-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java"),', '') } $Registry $Sources
    Write-Output 'Stonecutter direct contract self-test PASS: baseline accepted; 21 mutations rejected.'
}

$requiredPaths = @($RegistryPath, $SettingsPath, $RootBuildPath, $ForgeBuildPath,
    (Join-Path $RepositoryRoot 'platform\fabric\build.gradle'),
    (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle'),
    (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\FilePackResourcesArchiveMixin.java'),
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
    FabricNativeArchive = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\FilePackResourcesArchiveMixin.java') -Raw
    'Loader:fabric' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\fabric\build.gradle') -Raw
    'Loader:forge' = Get-Content -LiteralPath $ForgeBuildPath -Raw
    'Loader:neoforge' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle') -Raw
}
$summary = Invoke-DirectContractValidation $registry $sources
if ($SelfTest) { Invoke-SelfTests $registry $sources }
Write-Output "Stonecutter direct contract PASS: $($summary.DirectCells) direct cells; $($summary.NativeArchiveCells) Fabric native archive cells; $($summary.JoptOverrides) registry JOptSimple overrides; delegated wrapper isolated to parity/rollback."
