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
$CanonicalFabricNativeSharedZipTargets = @(
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
    'mc1_21_10',
    'mc1_21_11'
)
$CanonicalFabricNonNativeSharedZipTargets = @('mc1_20_1', 'mc26_1_to_26_2')
$CanonicalFabricNativeBitmapTargets = @(
    'mc1_20_1',
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
    'mc1_21_10',
    'mc1_21_11',
    'mc26_1_to_26_2'
)
$CanonicalFabricBitmapDescriptorPaths = @(
    'versions/mc1_20_1/common/src/client/resources/packforge.fabric.client.mixins.json',
    'versions/mc1_21_1/common/src/client/resources/packforge.fabric.client.mixins.json',
    'versions/mc1_21_4/common/src/client/resources/packforge.fabric.client.mixins.json',
    'versions/mc1_21_8/common/src/client/resources/packforge.fabric.client.mixins.json',
    'versions/mc1_21_9/common/src/client/resources/packforge.mc1_21_9_10.client.mixins.json',
    'versions/mc1_21_11/common/src/client/resources/packforge.fabric.client.mixins.json',
    'versions/mc26/common/src/client/resources/packforge.fabric.client.mixins.json'
)
$CanonicalFabricSharedZipDescriptorPaths = @(
    'versions/mc1_20_1/common/src/main/resources/packforge.fabric.mixins.json',
    'versions/mc1_20_2/common/src/main/resources/packforge.fabric.mixins.mc1_20_2.json',
    'versions/mc1_20_3_4/common/src/main/resources/packforge.fabric.mixins.mc1_20_3_4.json',
    'versions/mc1_20_5_6/common/src/main/resources/packforge.fabric.mixins.mc1_20_5_6.json',
    'versions/mc1_21_1/common/src/main/resources/packforge.fabric.mixins.json',
    'versions/mc1_21_4/common/src/main/resources/packforge.fabric.mixins.json',
    'versions/mc1_21_5/common/src/main/resources/packforge.mc1_21_5.mixins.json',
    'versions/mc1_21_8/common/src/main/resources/packforge.fabric.mixins.json',
    'versions/mc1_21_9/common/src/main/resources/packforge.mc1_21_9_10.mixins.json',
    'versions/mc1_21_11/common/src/main/resources/packforge.fabric.mixins.json',
    'versions/mc26/common/src/main/resources/packforge.fabric.mixins.json'
)

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

function Measure-StonecutterConditionalBlocks([string] $Text, [string] $Context) {
    $stack = [Collections.Generic.List[object]]::new()
    $blocks = [Collections.Generic.List[object]]::new()
    $reader = [IO.StringReader]::new($Text)
    $lineNumber = 0
    while (($line = $reader.ReadLine()) -ne $null) {
        $lineNumber++
        $directive = $line.Trim()
        if ($directive.StartsWith('*/', [StringComparison]::Ordinal)) { $directive = $directive.Substring(2) }
        $opener = $directive -cmatch '^//\?\s*if\b.+\{\s*$'
        $alternate = $directive -cmatch '^//\?\s*}\s*else\s*\{\s*$'
        $closer = $directive -cmatch '^//\?\s*}\s*$'
        if ($opener) {
            $stack.Add([pscustomobject]@{ Start = $lineNumber; Lines = 0; BranchLines = 0; HasElse = $false })
        } elseif ($alternate) {
            if ($stack.Count -eq 0) { Fail "$Context line $lineNumber has an orphan Stonecutter else." }
            $block = $stack[$stack.Count - 1]
            if ($block.BranchLines -eq 0) { Fail "$Context line $lineNumber has an empty Stonecutter branch before else." }
            if ($block.HasElse) { Fail "$Context line $lineNumber repeats Stonecutter else." }
            $block.HasElse = $true
            $block.BranchLines = 0
        } elseif ($closer) {
            if ($stack.Count -eq 0) { Fail "$Context line $lineNumber has an orphan Stonecutter close." }
            $block = $stack[$stack.Count - 1]
            $stack.RemoveAt($stack.Count - 1)
            if ($block.BranchLines -eq 0) { Fail "$Context line $lineNumber closes an empty Stonecutter branch." }
            $blocks.Add($block)
        } elseif ($directive.Contains('//?', [StringComparison]::Ordinal)) {
            Fail "$Context line $lineNumber contains a malformed Stonecutter directive."
        } else {
            foreach ($block in $stack) {
                $block.Lines++
                $block.BranchLines++
            }
        }
    }
    if ($stack.Count -ne 0) { Fail "$Context has unclosed Stonecutter blocks at lines $($stack.Start -join ', ')." }
    $maxLines = if ($blocks.Count -eq 0) { 0 } else { ($blocks.Lines | Measure-Object -Maximum).Maximum }
    return [pscustomobject]@{ Blocks = $blocks.Count; MaxLines = [int] $maxLines }
}

function Normalize-NativeStonecutterJava([string] $Text) {
    $body = @($Text -split '\r?\n' | Where-Object { $_.Trim() -notmatch '^//\?' }) -join ''
    return $body -replace '\s+', ''
}

function Normalize-BitmapProviderMixin([string] $Text) {
    $body = @($Text -split '\r?\n' | Where-Object { $_.Trim() -notmatch '^//\?' }) -join ''
    $body = [regex]::Replace($body, '\bresourceManager\b', 'manager')
    $body = $body -replace '\s+', ''
    return $body.Replace('if(cached!=null){returncached;}', 'if(cached!=null)returncached;')
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
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cin $CanonicalFabricNativeSharedZipTargets }) $CanonicalFabricNativeSharedZipTargets 'Fabric native SharedZip targets'
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cnotin $CanonicalFabricNativeSharedZipTargets }) $CanonicalFabricNonNativeSharedZipTargets 'Fabric non-native SharedZip targets'
    # This physical registry route remains the standalone/rollback implementation;
    # the Fabric build replaces it only inside its direct Stonecutter guard.
    $sharedZipRoutes = @($Registry.sharedJavaSources | Where-Object { $_.id -ceq 'shared-zip-access-1.20.2-through-1.21.11' })
    if ($sharedZipRoutes.Count -ne 1) { Fail "registry must contain one frozen pre-26 SharedZip route; found $($sharedZipRoutes.Count)." }
    $sharedZipRoute = $sharedZipRoutes[0]
    if (($sharedZipRoute.path -cne 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java') -or
        ($sharedZipRoute.sourceSet -cne 'main') -or
        ($null -ne $sharedZipRoute.PSObject.Properties['platforms'])) {
        Fail 'registry pre-26 SharedZip route path/sourceSet/platform scope drifted.'
    }
    Assert-SameSet @($sharedZipRoute.targets) $CanonicalFabricNativeSharedZipTargets 'registry pre-26 SharedZip route targets'
    Assert-SameSet $fabricTargetKeys $CanonicalFabricNativeBitmapTargets 'Fabric native bitmap-provider targets'
    $bitmapRoutes = @($Registry.sharedJavaSources | Where-Object { $_.id -ceq 'bitmap-provider-definition-through-1.21.10' })
    if ($bitmapRoutes.Count -ne 1) { Fail "registry must contain one frozen bitmap-provider route; found $($bitmapRoutes.Count)." }
    $bitmapRoute = $bitmapRoutes[0]
    if (($bitmapRoute.path -cne 'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java') -or
        ($bitmapRoute.sourceSet -cne 'client') -or
        ($null -ne $bitmapRoute.PSObject.Properties['platforms'])) {
        Fail 'registry bitmap-provider route path/sourceSet/platform scope drifted.'
    }
    Assert-SameSet @($bitmapRoute.targets) @($CanonicalFabricNativeBitmapTargets | Where-Object { $_ -cnotin @('mc1_21_11', 'mc26_1_to_26_2') }) 'registry bitmap-provider shared route targets'
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
    Assert-ContainsOnce $rootBuild 'file("fabric/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java")' 'root Fabric native SharedZip validator input'
    Assert-ContainsOnce $rootBuild 'file("fabric/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root Fabric native bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root retained shared bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root retained 1.21.11 bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root retained mc26 bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java")' 'root retained SharedZip validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java")' 'root mc26 SharedZip validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessAccessor.java")' 'root mc26 SharedZip accessor validator input'
    Assert-ContainsOnce $rootBuild 'fileTree("versions") { include "**/packforge*.mixins*.json" }' 'root SharedZip descriptor validator inputs'

    $metricsSources = Get-Section $rootBuild 'def handwrittenProductionJava = files(' 'def sourceMetricsBuildJson =' 'source-metrics roots'
    foreach ($loaderId in @('fabric', 'forge', 'neoforge')) {
        $rootPattern = '(?ms)fileTree\("' + [regex]::Escape($loaderId) + '"\)\s*\{\s*include "src/main/java/\*\*/\*\.java"\s*include "src/client/java/\*\*/\*\.java"\s*\}'
        $rootCount = ([regex]::Matches($metricsSources, $rootPattern)).Count
        if ($rootCount -ne 1) { Fail "source metrics must contain one exact $loaderId canonical production-source root; found $rootCount." }
    }
    Assert-ContainsCount $rootBuild 'relativePath ==~ /(?:fabric|forge|neoforge)\/src\/.*/' 2 'canonical loader source metrics classification'
    Assert-ContainsOnce $rootBuild 'canonical loader roots (`fabric`, `forge`, and `neoforge`)' 'source metrics classification report'

    $metricsParser = Get-Section $rootBuild 'int conditionalBlocks = 0' 'def duplicateReductionPercent =' 'source-metrics Stonecutter parser'
    foreach ($literal in @(
        'def blockStack = []',
        'def directive = line.trim().replaceFirst(/^\*\//, "")',
        'boolean opener = directive ==~ /\/\/\?\s*if\b.+\{\s*/',
        'boolean alternate = directive ==~ /\/\/\?\s*}\s*else\s*\{\s*/',
        'boolean closer = directive ==~ /\/\/\?\s*}\s*/',
        'blockStack << [start: index + 1, lines: 0, branchLines: 0, hasElse: false]',
        'blockStack.last().branchLines = 0',
        'blockStack.each { block ->',
        'block.lines++',
        'block.branchLines++',
        'directive.contains("//?")',
        'contains a malformed Stonecutter directive.',
        'contains unclosed Stonecutter conditional blocks opened at lines'
    )) {
        Assert-ContainsOnce $metricsParser $literal "source-metrics Stonecutter parser '$literal'"
    }

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
    Assert-ContainsCount $nativeArchive 'this.packforge$setArchive(zipFileAccess);' 2 'Fabric native archive constructor delegation'
    Assert-ContainsOnce $nativeArchive '@Unique' 'Fabric native archive shared helper uniqueness'
    Assert-ContainsOnce $nativeArchive 'private void packforge$setArchive(Object zipFileAccess) {' 'Fabric native archive shared capture helper'
    Assert-ContainsOnce $nativeArchive 'holder.packforge$setArchive(bridge);' 'Fabric native archive shared capture behavior'
    Assert-ContainsCount $nativeArchive 'private void packforge$captureArchive(' 2 'Fabric native archive constructor implementations'
    Assert-ContainsCount $nativeArchive '//? if ' 3 'Fabric native archive conditional openers'
    Assert-ContainsCount $nativeArchive '//?}' 4 'Fabric native archive conditional closers'
    $nativeArchiveMetrics = Measure-StonecutterConditionalBlocks $nativeArchive 'Fabric native archive'
    if ($nativeArchiveMetrics.Blocks -ne 3) { Fail "Fabric native archive must contain three Stonecutter blocks; found $($nativeArchiveMetrics.Blocks)." }
    if ($nativeArchiveMetrics.MaxLines -gt 40) { Fail "Fabric native archive Stonecutter block spans $($nativeArchiveMetrics.MaxLines) lines; maximum is 40." }

    $nativeSharedZip = $Sources.FabricNativeSharedZip
    Assert-ContainsOnce $nativeSharedZip '//? if >=1.20.2 && <=1.21.11 {' 'Fabric native SharedZip outer target guard'
    Assert-ContainsOnce $nativeSharedZip 'package com.teenkung.packforge.mixin.loader;' 'Fabric native SharedZip package'
    Assert-ContainsOnce $nativeSharedZip '@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess")' 'Fabric native SharedZip target'
    Assert-ContainsOnce $nativeSharedZip 'public abstract class SharedZipFileAccessMixin implements SharedZipFileAccessBridge {' 'Fabric native SharedZip class'
    Assert-ContainsOnce $nativeSharedZip '@Shadow @Final File file;' 'Fabric native SharedZip file shadow'
    Assert-ContainsOnce $nativeSharedZip '@Shadow abstract ZipFile getOrCreateZipFile();' 'Fabric native SharedZip ZIP accessor shadow'
    Assert-ContainsOnce $nativeSharedZip '@Unique private PackArchiveState packforge$state;' 'Fabric native SharedZip state'
    Assert-ContainsOnce $nativeSharedZip '@Inject(method = "<init>(Ljava/io/File;)V", at = @At("RETURN"))' 'Fabric native SharedZip constructor hook'
    Assert-ContainsOnce $nativeSharedZip 'this.packforge$state = new PackArchiveState();' 'Fabric native SharedZip state creation'
    Assert-ContainsOnce $nativeSharedZip '@Override @Unique public File packforge$archiveFile() { return this.file; }' 'Fabric native SharedZip file bridge'
    Assert-ContainsOnce $nativeSharedZip '@Override @Unique public ZipFile packforge$getOrCreateZipFile() { return this.getOrCreateZipFile(); }' 'Fabric native SharedZip ZIP bridge'
    Assert-ContainsOnce $nativeSharedZip '@Override @Unique public PackArchiveState packforge$archiveState() { return this.packforge$state; }' 'Fabric native SharedZip state bridge'
    Assert-ContainsOnce $nativeSharedZip '@Inject(method = "close", at = @At("HEAD"))' 'Fabric native SharedZip close hook'
    Assert-ContainsOnce $nativeSharedZip 'this.packforge$state.close();' 'Fabric native SharedZip close behavior'
    Assert-ContainsOnce $nativeSharedZip 'PackForge.LOGGER.warn("Failed to close PackForge ZIP state for {}; vanilla ZIP close will continue", this.file, exception);' 'Fabric native SharedZip close fallback'
    Assert-ContainsCount $nativeSharedZip '//? if ' 1 'Fabric native SharedZip conditional openers'
    Assert-ContainsCount $nativeSharedZip '//?}' 1 'Fabric native SharedZip conditional closers'
    if ($nativeSharedZip.Contains('SharedZipFileAccessAccessor')) { Fail 'Fabric native SharedZip pre-26 source must not consume the mc26 accessor seam.' }
    if ((Normalize-NativeStonecutterJava $nativeSharedZip) -cne (Normalize-NativeStonecutterJava $Sources.LegacySharedZip)) {
        Fail 'Fabric native SharedZip body must remain exactly equal to the retained standalone/rollback implementation after directive and whitespace normalization.'
    }
    $nativeSharedZipMetrics = Measure-StonecutterConditionalBlocks $nativeSharedZip 'Fabric native SharedZip'
    if ($nativeSharedZipMetrics.Blocks -ne 1) { Fail "Fabric native SharedZip must contain one Stonecutter block; found $($nativeSharedZipMetrics.Blocks)." }
    if ($nativeSharedZipMetrics.MaxLines -gt 40) { Fail "Fabric native SharedZip Stonecutter block spans $($nativeSharedZipMetrics.MaxLines) lines; maximum is 40." }

    $mc26SharedZip = $Sources.Mc26SharedZip
    $mc26SharedZipAccessor = $Sources.Mc26SharedZipAccessor
    Assert-ContainsOnce $mc26SharedZip '((SharedZipFileAccessAccessor) (Object) this).packforge$file()' 'mc26 SharedZip file accessor seam'
    Assert-ContainsOnce $mc26SharedZip '((SharedZipFileAccessAccessor) (Object) this).packforge$invokeGetOrCreateZipFile()' 'mc26 SharedZip ZIP accessor seam'
    Assert-ContainsOnce $mc26SharedZipAccessor '@Accessor("file")' 'mc26 SharedZip file accessor'
    Assert-ContainsOnce $mc26SharedZipAccessor '@Invoker("getOrCreateZipFile")' 'mc26 SharedZip ZIP invoker'

    try { $sharedZipDescriptors = $Sources.FabricSharedZipDescriptors | ConvertFrom-Json -AsHashtable } catch { Fail "Fabric SharedZip descriptor proof is invalid JSON: $($_.Exception.Message)" }
    Assert-SameSet @($sharedZipDescriptors.Keys) $CanonicalFabricSharedZipDescriptorPaths 'Fabric SharedZip descriptor paths'
    foreach ($descriptorPath in $CanonicalFabricSharedZipDescriptorPaths) {
        $descriptor = $sharedZipDescriptors[$descriptorPath]
        $sharedZipCount = @($descriptor.mixins | Where-Object { $_ -ceq 'loader.SharedZipFileAccessMixin' }).Count
        $accessorCount = @($descriptor.mixins | Where-Object { $_ -ceq 'loader.SharedZipFileAccessAccessor' }).Count
        if ($descriptorPath.Contains('/mc1_20_1/')) {
            if ($sharedZipCount -ne 0 -or $accessorCount -ne 0) { Fail 'Fabric 1.20.1 descriptor must not register SharedZip mixins.' }
        } elseif ($descriptorPath.Contains('/mc26/')) {
            if ($sharedZipCount -ne 1 -or $accessorCount -ne 1) { Fail 'Fabric mc26 descriptor must register one physical SharedZip mixin and accessor.' }
        } elseif ($sharedZipCount -ne 1 -or $accessorCount -ne 0) {
            Fail "Fabric descriptor '$descriptorPath' must register one pre-26 SharedZip mixin without the mc26 accessor."
        }
    }

    $nativeBitmap = $Sources.FabricNativeBitmap
    Assert-ContainsOnce $nativeBitmap '//? if >=1.20.1 && <=26.1 {' 'Fabric native bitmap-provider outer target guard'
    Assert-ContainsOnce $nativeBitmap 'package com.teenkung.packforge.client.mixin.font;' 'Fabric native bitmap-provider package'
    Assert-ContainsOnce $nativeBitmap '@Mixin(BitmapProvider.Definition.class)' 'Fabric native bitmap-provider target'
    Assert-ContainsOnce $nativeBitmap '@WrapMethod(method = "load")' 'Fabric native bitmap-provider load hook'
    Assert-ContainsOnce $nativeBitmap 'private GlyphProvider packforge$loadCached(ResourceManager manager, Operation<GlyphProvider> original) throws Exception {' 'Fabric native bitmap-provider signature'
    Assert-ContainsOnce $nativeBitmap 'if (!FontBitmapProviderCache.enabled()) {' 'Fabric native bitmap-provider feature guard'
    Assert-ContainsOnce $nativeBitmap 'GlyphProvider cached = FontBitmapProviderCache.get(epoch, manager, definition);' 'Fabric native bitmap-provider lookup'
    Assert-ContainsOnce $nativeBitmap 'return loaded == null ? null : FontBitmapProviderCache.cache(epoch, manager, definition, loaded);' 'Fabric native bitmap-provider cache behavior'
    Assert-ContainsCount $nativeBitmap '//? if ' 1 'Fabric native bitmap-provider conditional openers'
    Assert-ContainsCount $nativeBitmap '//?}' 1 'Fabric native bitmap-provider conditional closers'
    $nativeBitmapMetrics = Measure-StonecutterConditionalBlocks $nativeBitmap 'Fabric native bitmap-provider'
    if ($nativeBitmapMetrics.Blocks -ne 1) { Fail "Fabric native bitmap-provider must contain one Stonecutter block; found $($nativeBitmapMetrics.Blocks)." }
    if ($nativeBitmapMetrics.MaxLines -gt 40) { Fail "Fabric native bitmap-provider Stonecutter block spans $($nativeBitmapMetrics.MaxLines) lines; maximum is 40." }
    $bitmapBaseline = Normalize-BitmapProviderMixin $nativeBitmap
    foreach ($variant in @('LegacyBitmap', 'Mc12111Bitmap', 'Mc26Bitmap')) {
        if ((Normalize-BitmapProviderMixin $Sources[$variant]) -cne $bitmapBaseline) {
            Fail "Fabric native bitmap-provider behavior differs from retained variant '$variant' after narrow semantic normalization."
        }
    }
    try { $bitmapDescriptors = $Sources.FabricBitmapDescriptors | ConvertFrom-Json -AsHashtable } catch { Fail "Fabric bitmap-provider descriptor proof is invalid JSON: $($_.Exception.Message)" }
    Assert-SameSet @($bitmapDescriptors.Keys) $CanonicalFabricBitmapDescriptorPaths 'Fabric bitmap-provider descriptor paths'
    foreach ($descriptorPath in $CanonicalFabricBitmapDescriptorPaths) {
        $bitmapCount = @($bitmapDescriptors[$descriptorPath].client | Where-Object { $_ -ceq 'font.BitmapProviderDefinitionMixin' }).Count
        if ($bitmapCount -ne 1) { Fail "Fabric descriptor '$descriptorPath' must register one bitmap-provider mixin; found $bitmapCount." }
    }

    $fabricBuild = $Sources['Loader:fabric']
    $nativeTransport = Get-Section $fabricBuild 'def selectedMainJavaSources = files(selectedSources.mainJavaSources)' 'sourceSets {' 'Fabric native source transport'
    Assert-ContainsOnce $nativeTransport 'def compileMainJavaSources = selectedMainJavaSources' 'Fabric standalone selected-source preservation'
    Assert-ContainsOnce $nativeTransport 'def selectedClientJavaSources = files(selectedSources.clientJavaSources)' 'Fabric selected client sources'
    Assert-ContainsOnce $nativeTransport 'def compileClientJavaSources = selectedClientJavaSources' 'Fabric standalone client-source preservation'
    Assert-ContainsOnce $nativeTransport 'def legacyNativeSources = [] as Set' 'Fabric standalone empty native exclusion set'
    Assert-ContainsOnce $nativeTransport 'def legacyNativeClientSources = [] as Set' 'Fabric standalone empty client-native exclusion set'
    Assert-ContainsOnce $nativeTransport 'def legacySharedZipSource = stonecutterDirectNode' 'Fabric legacy SharedZip source identity'
    Assert-ContainsOnce $nativeTransport 'def generatedSharedZipSource = stonecutterDirectNode' 'Fabric generated SharedZip source identity'
    Assert-ContainsOnce $nativeTransport 'stonecutter.tasks.generatedSourcesDir.file("main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java").get().asFile.canonicalFile' 'Fabric generated SharedZip source path'
    Assert-ContainsOnce $nativeTransport 'def nativeSharedZipActive = false' 'Fabric native SharedZip default state'
    Assert-ContainsOnce $nativeTransport 'if (stonecutterDirectNode) {' 'Fabric native direct-only transport'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java' 'Fabric legacy archive exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/mc1_21_shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java' 'Fabric modern archive exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java' 'Fabric legacy SharedZip exclusion'
    Assert-ContainsCount $nativeTransport 'new File(physicalRepositoryRoot, ' 6 'Fabric exact legacy native exclusions'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java' 'Fabric legacy shared bitmap-provider exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java' 'Fabric legacy 1.21.11 bitmap-provider exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java' 'Fabric legacy mc26 bitmap-provider exclusion'
    Assert-ContainsOnce $nativeTransport 'nativeSharedZipActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacySharedZipSource }' 'Fabric registry-selected SharedZip activation'
    Assert-ContainsOnce $nativeTransport 'selectedMainJavaSources.filter { source -> source.canonicalFile !in legacyNativeSources }' 'Fabric native source replacement'
    Assert-ContainsOnce $nativeTransport 'stonecutter.tasks.generatedSourcesDir.dir("main/java")' 'Fabric generated Java compile root'
    Assert-ContainsOnce $nativeTransport 'selectedClientJavaSources.filter { source -> source.canonicalFile !in legacyNativeClientSources }' 'Fabric native client source replacement'
    Assert-ContainsOnce $nativeTransport 'stonecutter.tasks.generatedSourcesDir.dir("client/java")' 'Fabric generated client Java compile root'
    if ($fabricBuild.Contains('stonecutter.tasks.configureSource(')) {
        Fail 'Fabric must reuse Stonecutter 0.9.7 automatic SourceSet registration instead of configuring main twice.'
    }

    $fabricSourceSets = Get-Section $fabricBuild 'sourceSets {' 'tasks.named("compileJava", JavaCompile)' 'Fabric native archive source set'
    Assert-ContainsCount $fabricSourceSets 'if (stonecutterDirectNode) {' 2 'Fabric generated source-set direct guards'
    Assert-ContainsOnce $fabricSourceSets 'java.srcDir(stonecutter.tasks.generatedSourcesDir.dir("main/java"))' 'Fabric generated Java main source set'
    Assert-ContainsOnce $fabricSourceSets 'java.srcDir(stonecutter.tasks.generatedSourcesDir.dir("client/java"))' 'Fabric generated Java client source set'
    $fabricCompileJava = Get-Section $fabricBuild 'tasks.named("compileJava", JavaCompile) {' 'tasks.named("compileClientJava", JavaCompile)' 'Fabric native archive compile task'
    Assert-ContainsOnce $fabricCompileJava 'if (stonecutterDirectNode) {' 'Fabric compile generation direct guard'
    Assert-ContainsOnce $fabricCompileJava 'dependsOn(stonecutter.tasks.generate["main"])' 'Fabric compile generation dependency'
    Assert-ContainsOnce $fabricCompileJava 'setSource(compileMainJavaSources)' 'Fabric compile source replacement'
    $fabricCompileClientJava = Get-Section $fabricBuild 'tasks.named("compileClientJava", JavaCompile) {' 'tasks.named("compileTestJava", JavaCompile)' 'Fabric native bitmap-provider compile task'
    Assert-ContainsOnce $fabricCompileClientJava 'if (stonecutterDirectNode) {' 'Fabric client compile generation direct guard'
    Assert-ContainsOnce $fabricCompileClientJava 'dependsOn(stonecutter.tasks.generate["client"])' 'Fabric client compile generation dependency'
    Assert-ContainsOnce $fabricCompileClientJava 'setSource(compileClientJavaSources)' 'Fabric client compile source replacement'
    $fabricSourcesJar = Get-Section $fabricBuild 'tasks.named("sourcesJar") {' 'if (loaderConfig.mappingMode == "named") {' 'Fabric native archive sources JAR'
    Assert-ContainsOnce $fabricSourcesJar 'if (stonecutterDirectNode) {' 'Fabric sources JAR generation direct guard'
    Assert-ContainsOnce $fabricSourcesJar 'dependsOn(stonecutter.tasks.generate["main"], stonecutter.tasks.generate["client"])' 'Fabric sources JAR generation dependencies'
    Assert-ContainsOnce $fabricSourcesJar 'if (details.file.canonicalFile in legacyNativeSources' 'Fabric sources JAR physical legacy-source filter'
    Assert-ContainsOnce $fabricSourcesJar 'details.file.canonicalFile in legacyNativeClientSources' 'Fabric sources JAR physical client-source filter'
    Assert-ContainsOnce $fabricSourcesJar '!nativeSharedZipActive && details.file.canonicalFile == generatedSharedZipSource' 'Fabric inactive generated SharedZip filter'
    Assert-ContainsOnce $fabricSourcesJar 'details.exclude()' 'Fabric sources JAR legacy-source exclusion'
    Assert-ContainsCount $fabricBuild 'stonecutter.tasks.generatedSourcesDir.dir("main/java")' 2 'Fabric exact generated Java root wiring'
    Assert-ContainsCount $fabricBuild 'stonecutter.tasks.generatedSourcesDir.dir("client/java")' 2 'Fabric exact generated client Java root wiring'
    if ($fabricBuild.Contains('stonecutter.tasks.generatedSourcesDir.dir("main")')) {
        Fail 'Fabric must not expose the Stonecutter source-set container as a Java root; use main/java.'
    }
    Assert-ContainsCount $fabricBuild 'dependsOn(stonecutter.tasks.generate["main"])' 1 'Fabric exact main compile generation wiring'
    Assert-ContainsCount $fabricBuild 'stonecutter.tasks.generate["client"]' 2 'Fabric exact client generation wiring'
    foreach ($loaderId in @('forge', 'neoforge')) {
        if ($Sources["Loader:$loaderId"].Contains('FilePackResourcesArchiveMixin.java') -or $Sources["Loader:$loaderId"].Contains('SharedZipFileAccessMixin.java') -or $Sources["Loader:$loaderId"].Contains('BitmapProviderDefinitionMixin.java')) {
            Fail "$loaderId build script must not consume a Fabric native source pilot."
        }
    }

    $forgeBuild = $Sources['Loader:forge']
    Assert-ContainsOnce $forgeBuild 'def joptSimpleRuntimeVersion = loaderConfig.joptSimpleRuntimeVersion?.toString()' 'Forge registry JOptSimple selection'
    Assert-ContainsOnce $forgeBuild 'runtimeOnly "net.sf.jopt-simple:jopt-simple:${joptSimpleRuntimeVersion}"' 'Forge registry JOptSimple dependency'
    Assert-ContainsOnce $forgeBuild 'resolutionStrategy.force "net.sf.jopt-simple:jopt-simple:${joptSimpleRuntimeVersion}"' 'Forge registry JOptSimple force'
    if ($forgeBuild.Contains('needsLegacyJoptSimpleModule') -or $forgeBuild.Contains('jopt-simple:5.0.4')) {
        Fail 'Forge build script must not hard-code JOptSimple target keys or version.'
    }

    return [pscustomobject]@{ DirectCells = $nodes.Count; JoptOverrides = $joptOverrides.Count; NativeArchiveCells = $CanonicalFabricNativeArchiveTargets.Count; NativeSharedZipCells = $CanonicalFabricNativeSharedZipTargets.Count; NativeBitmapCells = $CanonicalFabricNativeBitmapTargets.Count }
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
    Assert-MutationRejected 'native-shared-zip-registry-route-drift' { param($r, $s) $route = @($r.sharedJavaSources | Where-Object { $_.id -ceq 'shared-zip-access-1.20.2-through-1.21.11' })[0]; $route.targets = @($route.targets | Where-Object { $_ -cne 'mc1_21_11' }) } $Registry $Sources
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
    Assert-MutationRejected 'native-archive-shared-helper-missing' { param($r, $s) $s.FabricNativeArchive = $s.FabricNativeArchive.Replace('private void packforge$setArchive(Object zipFileAccess) {', 'private void packforge$setMissing(Object zipFileAccess) {') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-range-drift' { param($r, $s) $s.FabricNativeSharedZip = $s.FabricNativeSharedZip.Replace('<=1.21.11', '<=1.21.10') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-constructor-drift' { param($r, $s) $s.FabricNativeSharedZip = $s.FabricNativeSharedZip.Replace('<init>(Ljava/io/File;)V', '<init>') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-close-drift' { param($r, $s) $s.FabricNativeSharedZip = $s.FabricNativeSharedZip.Replace('@Inject(method = "close", at = @At("HEAD"))', '@Inject(method = "close", at = @At("RETURN"))') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-accessor-leak' { param($r, $s) $s.FabricNativeSharedZip += "`nSharedZipFileAccessAccessor" } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-rollback-parity-drift' { param($r, $s) $s.LegacySharedZip = $s.LegacySharedZip.Replace('this.packforge$state.close();', 'this.packforge$state = null;') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-mc26-seam-missing' { param($r, $s) $s.Mc26SharedZip = $s.Mc26SharedZip.Replace('SharedZipFileAccessAccessor', 'MissingAccessor') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-mc26-accessor-missing' { param($r, $s) $s.Mc26SharedZipAccessor = $s.Mc26SharedZipAccessor.Replace('@Accessor("file")', '@Accessor("missing")') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-descriptor-registration-missing' { param($r, $s) $s.FabricSharedZipDescriptors = $s.FabricSharedZipDescriptors.Replace('loader.SharedZipFileAccessMixin', 'loader.MissingSharedZipMixin') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-mc26-descriptor-accessor-missing' { param($r, $s) $s.FabricSharedZipDescriptors = $s.FabricSharedZipDescriptors.Replace('loader.SharedZipFileAccessAccessor', 'loader.MissingSharedZipAccessor') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-registry-route-drift' { param($r, $s) $route = @($r.sharedJavaSources | Where-Object { $_.id -ceq 'bitmap-provider-definition-through-1.21.10' })[0]; $route.sourceSet = 'main' } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-lower-range-drift' { param($r, $s) $s.FabricNativeBitmap = $s.FabricNativeBitmap.Replace('>=1.20.1', '>=1.20.2') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-upper-range-missing' { param($r, $s) $s.FabricNativeBitmap = $s.FabricNativeBitmap.Replace(' && <=26.1', '') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-behavior-drift' { param($r, $s) $s.FabricNativeBitmap = $s.FabricNativeBitmap.Replace('FontBitmapProviderCache.enabled()', 'false') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-retained-parity-drift' { param($r, $s) $s.Mc26Bitmap = $s.Mc26Bitmap.Replace('FontBitmapProviderCache.enabled()', 'false') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-descriptor-registration-missing' { param($r, $s) $s.FabricBitmapDescriptors = $s.FabricBitmapDescriptors.Replace('font.BitmapProviderDefinitionMixin', 'font.MissingBitmapProviderDefinitionMixin') } $Registry $Sources
    Assert-MutationRejected 'native-archive-legacy-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java', 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-legacy-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java', 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/MissingSharedZip.java') } $Registry $Sources
    Assert-MutationRejected 'native-source-extra-exclusion' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace("`tlegacyNativeSources = [", "`tlegacyNativeSources = [`n`t`tnew File(physicalRepositoryRoot, `"versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/Extra.java`"),") } $Registry $Sources
    Assert-MutationRejected 'native-archive-generated-source-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('java.srcDir(stonecutter.tasks.generatedSourcesDir.dir("main/java"))', 'java.srcDir("src/main/java")') } $Registry $Sources
    Assert-MutationRejected 'native-archive-generated-root-too-high' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('stonecutter.tasks.generatedSourcesDir.dir("main/java")', 'stonecutter.tasks.generatedSourcesDir.dir("main")') } $Registry $Sources
    Assert-MutationRejected 'native-archive-compile-generation-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('dependsOn(stonecutter.tasks.generate["main"])', 'dependsOn(tasks.named("classes"))') } $Registry $Sources
    Assert-MutationRejected 'native-archive-sources-jar-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('details.exclude()', 'details.path') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-activation-drift' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('nativeSharedZipActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacySharedZipSource }', 'nativeSharedZipActive = true') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-inactive-generated-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('|| (!nativeSharedZipActive && details.file.canonicalFile == generatedSharedZipSource)', '') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-client-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java', 'versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-generated-client-root-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('stonecutter.tasks.generatedSourcesDir.dir("client/java")', 'files()') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-client-generation-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('dependsOn(stonecutter.tasks.generate["client"])', 'dependsOn(tasks.named("classes"))') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-sources-jar-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('|| details.file.canonicalFile in legacyNativeClientSources', '') } $Registry $Sources
    Assert-MutationRejected 'native-archive-duplicate-source-registration' { param($r, $s) $s['Loader:fabric'] += "`nstonecutter.tasks.configureSource(sourceSets.main)" } $Registry $Sources
    Assert-MutationRejected 'native-archive-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-mc26-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java"),', '') } $Registry $Sources
    Write-Output 'Stonecutter direct contract self-test PASS: baseline accepted; 49 mutations rejected.'
}

$requiredPaths = @($RegistryPath, $SettingsPath, $RootBuildPath, $ForgeBuildPath,
    (Join-Path $RepositoryRoot 'platform\fabric\build.gradle'),
    (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle'),
    (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\FilePackResourcesArchiveMixin.java'),
    (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java'),
    (Join-Path $RepositoryRoot 'fabric\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessAccessor.java'),
    (Join-Path $RepositoryRoot 'versions\shared\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc1_21_11\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'stonecutter-build.gradle'),
    (Join-Path $RepositoryRoot 'gradle\packforge-stonecutter-direct-parity.gradle')) + @(
        $CanonicalFabricSharedZipDescriptorPaths | ForEach-Object { Join-Path $RepositoryRoot $_ }
    ) + @(
        $CanonicalFabricBitmapDescriptorPaths | ForEach-Object { Join-Path $RepositoryRoot $_ }
    )
foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "missing required file '$path'." }
}

try { $registry = Get-Content -LiteralPath $RegistryPath -Raw | ConvertFrom-Json } catch { Fail "registry is not valid JSON: $($_.Exception.Message)" }
$sharedZipDescriptorProof = [ordered]@{}
foreach ($relativePath in $CanonicalFabricSharedZipDescriptorPaths) {
    try {
        $sharedZipDescriptorProof[$relativePath] = Get-Content -LiteralPath (Join-Path $RepositoryRoot $relativePath) -Raw | ConvertFrom-Json
    } catch {
        Fail "Fabric SharedZip descriptor '$relativePath' is invalid JSON: $($_.Exception.Message)"
    }
}
$bitmapDescriptorProof = [ordered]@{}
foreach ($relativePath in $CanonicalFabricBitmapDescriptorPaths) {
    try {
        $bitmapDescriptorProof[$relativePath] = Get-Content -LiteralPath (Join-Path $RepositoryRoot $relativePath) -Raw | ConvertFrom-Json
    } catch {
        Fail "Fabric bitmap-provider descriptor '$relativePath' is invalid JSON: $($_.Exception.Message)"
    }
}
$sources = @{
    Settings = Get-Content -LiteralPath $SettingsPath -Raw
    RootBuild = Get-Content -LiteralPath $RootBuildPath -Raw
    Rollback = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'stonecutter-build.gradle') -Raw
    Parity = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'gradle\packforge-stonecutter-direct-parity.gradle') -Raw
    FabricNativeArchive = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\FilePackResourcesArchiveMixin.java') -Raw
    FabricNativeSharedZip = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java') -Raw
    FabricNativeBitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    LegacySharedZip = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java') -Raw
    Mc26SharedZip = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java') -Raw
    Mc26SharedZipAccessor = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessAccessor.java') -Raw
    LegacyBitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    Mc12111Bitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc1_21_11\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    Mc26Bitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    FabricSharedZipDescriptors = $sharedZipDescriptorProof | ConvertTo-Json -Depth 20 -Compress
    FabricBitmapDescriptors = $bitmapDescriptorProof | ConvertTo-Json -Depth 20 -Compress
    'Loader:fabric' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\fabric\build.gradle') -Raw
    'Loader:forge' = Get-Content -LiteralPath $ForgeBuildPath -Raw
    'Loader:neoforge' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle') -Raw
}
$summary = Invoke-DirectContractValidation $registry $sources
if ($SelfTest) { Invoke-SelfTests $registry $sources }
Write-Output "Stonecutter direct contract PASS: $($summary.DirectCells) direct cells; $($summary.NativeArchiveCells) Fabric native archive cells; $($summary.NativeSharedZipCells) Fabric native SharedZip cells; $($summary.NativeBitmapCells) Fabric native bitmap-provider cells; registry physical routes preserved for standalone/rollback; canonical replacement direct-only; $($summary.JoptOverrides) registry JOptSimple overrides."
