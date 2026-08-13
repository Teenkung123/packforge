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
$CanonicalFabricNativeReloadManagerTargets = @(
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
    'mc1_21_11'
)
$CanonicalFabricNonNativeReloadManagerTargets = @('mc26_1_to_26_2')
$CanonicalFabricNativeRuntimeResourceHashTargets = @(
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
    'mc1_21_10'
)
$CanonicalFabricNonNativeRuntimeResourceHashTargets = @('mc1_21_11', 'mc26_1_to_26_2')
$CanonicalFabricNativeLoadingToastTargets = @(
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
$CanonicalFabricNonNativeLoadingToastTargets = @(
    'mc1_20_1',
    'mc1_20_2',
    'mc1_20_3',
    'mc1_20_4',
    'mc26_1_to_26_2'
)
$CanonicalFabricReloadManagerDescriptorPaths = @(
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
$CanonicalFabricLoadingToastDescriptorPaths = @(
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

function Assert-InOrder([string] $Text, [string[]] $Literals, [string] $Context) {
    $cursor = -1
    foreach ($literal in $Literals) {
        $next = $Text.IndexOf($literal, $cursor + 1, [StringComparison]::Ordinal)
        if ($next -lt 0) { Fail "$Context is missing ordered literal '$literal'." }
        $cursor = $next
    }
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

function Get-StonecutterGuardBody([string] $Text, [string] $Opener, [string] $Context) {
    $pattern = '(?ms)^' + [regex]::Escape($Opener) + '\r?\n(?<body>.*?)^//\?\}\s*$'
    $matches = [regex]::Matches($Text, $pattern)
    if ($matches.Count -ne 1) { Fail "$Context must contain exactly one complete '$Opener' block; found $($matches.Count)." }
    return $matches[0].Groups['body'].Value
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
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cin $CanonicalFabricNativeReloadManagerTargets }) $CanonicalFabricNativeReloadManagerTargets 'Fabric native reload-manager targets'
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cnotin $CanonicalFabricNativeReloadManagerTargets }) $CanonicalFabricNonNativeReloadManagerTargets 'Fabric non-native reload-manager targets'
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cin $CanonicalFabricNativeRuntimeResourceHashTargets }) $CanonicalFabricNativeRuntimeResourceHashTargets 'Fabric native runtime-resource-hash targets'
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cnotin $CanonicalFabricNativeRuntimeResourceHashTargets }) $CanonicalFabricNonNativeRuntimeResourceHashTargets 'Fabric non-native runtime-resource-hash targets'
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cin $CanonicalFabricNativeLoadingToastTargets }) $CanonicalFabricNativeLoadingToastTargets 'Fabric native loading-toast targets'
    Assert-SameSet @($fabricTargetKeys | Where-Object { $_ -cnotin $CanonicalFabricNativeLoadingToastTargets }) $CanonicalFabricNonNativeLoadingToastTargets 'Fabric non-native loading-toast targets'
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
    $reloadManagerRoutes = @($Registry.sharedJavaSources | Where-Object { $_.id -ceq 'reload-manager-pre-26' })
    if ($reloadManagerRoutes.Count -ne 1) { Fail "registry must contain one frozen pre-26 reload-manager route; found $($reloadManagerRoutes.Count)." }
    $reloadManagerRoute = $reloadManagerRoutes[0]
    if (($reloadManagerRoute.path -cne 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java') -or
        ($reloadManagerRoute.sourceSet -cne 'main') -or
        ($null -ne $reloadManagerRoute.PSObject.Properties['platforms'])) {
        Fail 'registry pre-26 reload-manager route path/sourceSet/platform scope drifted.'
    }
    Assert-SameSet @($reloadManagerRoute.targets) $CanonicalFabricNativeReloadManagerTargets 'registry pre-26 reload-manager route targets'
    $runtimeResourceHashRoutes = @($Registry.sharedJavaSources | Where-Object { $_.id -ceq 'runtime-resource-hash-pre-1.21.11' })
    if ($runtimeResourceHashRoutes.Count -ne 1) { Fail "registry must contain one frozen pre-1.21.11 runtime-resource-hash route; found $($runtimeResourceHashRoutes.Count)." }
    $runtimeResourceHashRoute = $runtimeResourceHashRoutes[0]
    if (($runtimeResourceHashRoute.path -cne 'versions/shared/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java') -or
        ($runtimeResourceHashRoute.sourceSet -cne 'main') -or
        ($null -ne $runtimeResourceHashRoute.PSObject.Properties['platforms'])) {
        Fail 'registry pre-1.21.11 runtime-resource-hash route path/sourceSet/platform scope drifted.'
    }
    Assert-SameSet @($runtimeResourceHashRoute.targets) $CanonicalFabricNativeRuntimeResourceHashTargets 'registry pre-1.21.11 runtime-resource-hash route targets'
    $commonLoadingToastRoutes = @($Registry.sharedJavaSources | Where-Object { $_.id -ceq 'loading-overlay-toast-1.20.5-through-1.21.5' })
    if ($commonLoadingToastRoutes.Count -ne 1) { Fail "registry must contain one frozen common loading-toast route; found $($commonLoadingToastRoutes.Count)." }
    $commonLoadingToastRoute = $commonLoadingToastRoutes[0]
    if (($commonLoadingToastRoute.path -cne 'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java') -or
        ($commonLoadingToastRoute.sourceSet -cne 'client') -or
        ($null -ne $commonLoadingToastRoute.PSObject.Properties['platforms'])) {
        Fail 'registry common loading-toast route path/sourceSet/platform scope drifted.'
    }
    Assert-SameSet @($commonLoadingToastRoute.targets) @($CanonicalFabricNativeLoadingToastTargets | Select-Object -First 8) 'registry common loading-toast route targets'
    $modernLoadingToastRoutes = @($Registry.sharedJavaSources | Where-Object { $_.id -ceq 'loading-overlay-toast-1.21.6-through-1.21.11' })
    if ($modernLoadingToastRoutes.Count -ne 1) { Fail "registry must contain one frozen modern loading-toast route; found $($modernLoadingToastRoutes.Count)." }
    $modernLoadingToastRoute = $modernLoadingToastRoutes[0]
    if (($modernLoadingToastRoute.path -cne 'versions/shared/modern/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java') -or
        ($modernLoadingToastRoute.sourceSet -cne 'client') -or
        ($null -ne $modernLoadingToastRoute.PSObject.Properties['platforms'])) {
        Fail 'registry modern loading-toast route path/sourceSet/platform scope drifted.'
    }
    Assert-SameSet @($modernLoadingToastRoute.targets) @($CanonicalFabricNativeLoadingToastTargets | Select-Object -Skip 8) 'registry modern loading-toast route targets'
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
    Assert-ContainsOnce $rootBuild 'file("fabric/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java")' 'root Fabric native reload-manager validator input'
    Assert-ContainsOnce $rootBuild 'file("fabric/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java")' 'root Fabric native runtime-resource-hash validator input'
    Assert-ContainsOnce $rootBuild 'file("fabric/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root Fabric native bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("fabric/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java")' 'root Fabric native loading-toast validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java")' 'root retained common loading-toast validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/modern/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java")' 'root retained modern loading-toast validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc1_20_1/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java")' 'root retained lower loading-toast validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java")' 'root retained mc26 loading-toast validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root retained shared bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root retained 1.21.11 bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java")' 'root retained mc26 bitmap-provider validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java")' 'root retained SharedZip validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java")' 'root mc26 SharedZip validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessAccessor.java")' 'root mc26 SharedZip accessor validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/common/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java")' 'root retained reload-manager validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java")' 'root mc26 reload-manager validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/shared/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java")' 'root retained shared runtime-resource-hash validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc1_21_11/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java")' 'root retained 1.21.11 runtime-resource-hash validator input'
    Assert-ContainsOnce $rootBuild 'file("versions/mc26/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java")' 'root retained mc26 runtime-resource-hash validator input'
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

    $nativeRuntimeResourceHash = $Sources.FabricNativeRuntimeResourceHash
    Assert-ContainsOnce $nativeRuntimeResourceHash '//? if >=1.20.1 && <=1.21.10 {' 'Fabric native runtime-resource-hash outer target guard'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'package com.teenkung.packforge.loader;' 'Fabric native runtime-resource-hash package'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'import net.minecraft.resources.ResourceLocation;' 'Fabric native runtime-resource-hash identifier type'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'public final class RuntimeResourceHash {' 'Fabric native runtime-resource-hash class'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'public static void report(ReloadableResourceManager manager, long reloadId) {' 'Fabric native runtime-resource-hash report signature'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'RuntimeResourceHashReporter.reportAsync(reloadId, () -> snapshot(manager));' 'Fabric native runtime-resource-hash reporter delegation'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'Map<ResourceLocation, Resource> resources = manager.listResources("textures", RuntimeResourceHash::isFixtureResource);' 'Fabric native runtime-resource-hash resource lookup'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'resources.forEach((location, resource) -> snapshot.put(location.toString(), resource::open));' 'Fabric native runtime-resource-hash snapshot capture'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'private static boolean isFixtureResource(ResourceLocation location) {' 'Fabric native runtime-resource-hash fixture predicate'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'String namespace = location.getNamespace();' 'Fabric native runtime-resource-hash namespace lookup'
    Assert-ContainsOnce $nativeRuntimeResourceHash 'return namespace.equals("example") || namespace.startsWith("generated");' 'Fabric native runtime-resource-hash namespace filter'
    Assert-ContainsCount $nativeRuntimeResourceHash '//? if ' 1 'Fabric native runtime-resource-hash conditional openers'
    Assert-ContainsCount $nativeRuntimeResourceHash '//?}' 1 'Fabric native runtime-resource-hash conditional closers'
    if ((Normalize-NativeStonecutterJava $nativeRuntimeResourceHash) -cne (Normalize-NativeStonecutterJava $Sources.LegacyRuntimeResourceHash)) {
        Fail 'Fabric native runtime-resource-hash body must remain exactly equal to retained standalone/rollback implementation after directive and whitespace normalization.'
    }
    $nativeRuntimeResourceHashMetrics = Measure-StonecutterConditionalBlocks $nativeRuntimeResourceHash 'Fabric native runtime-resource-hash'
    if ($nativeRuntimeResourceHashMetrics.Blocks -ne 1) { Fail "Fabric native runtime-resource-hash must contain one Stonecutter block; found $($nativeRuntimeResourceHashMetrics.Blocks)." }
    if ($nativeRuntimeResourceHashMetrics.MaxLines -gt 40) { Fail "Fabric native runtime-resource-hash Stonecutter block spans $($nativeRuntimeResourceHashMetrics.MaxLines) lines; maximum is 40." }

    $mc12111RuntimeResourceHash = $Sources.Mc12111RuntimeResourceHash
    if ((Normalize-NativeStonecutterJava $nativeRuntimeResourceHash) -ceq (Normalize-NativeStonecutterJava $mc12111RuntimeResourceHash)) {
        Fail 'Minecraft 1.21.11 runtime-resource-hash must retain its raw Predicate/String API seam.'
    }
    Assert-ContainsOnce $mc12111RuntimeResourceHash 'import java.util.function.Predicate;' 'Minecraft 1.21.11 runtime-resource-hash Predicate import'
    Assert-ContainsOnce $mc12111RuntimeResourceHash '@SuppressWarnings({"rawtypes", "unchecked"})' 'Minecraft 1.21.11 runtime-resource-hash raw API warning'
    Assert-ContainsOnce $mc12111RuntimeResourceHash 'Predicate fixtureResource = location -> isFixtureResource(String.valueOf(location));' 'Minecraft 1.21.11 runtime-resource-hash raw predicate'
    Assert-ContainsOnce $mc12111RuntimeResourceHash 'Map<?, Resource> resources = manager.listResources("textures", fixtureResource);' 'Minecraft 1.21.11 runtime-resource-hash wildcard result'
    Assert-ContainsOnce $mc12111RuntimeResourceHash 'private static boolean isFixtureResource(String location) {' 'Minecraft 1.21.11 runtime-resource-hash String predicate'
    Assert-ContainsOnce $mc12111RuntimeResourceHash 'String namespace = separator < 0 ? "minecraft" : location.substring(0, separator);' 'Minecraft 1.21.11 runtime-resource-hash namespace parsing'

    $mc26RuntimeResourceHash = $Sources.Mc26RuntimeResourceHash
    if ((Normalize-NativeStonecutterJava $nativeRuntimeResourceHash) -ceq (Normalize-NativeStonecutterJava $mc26RuntimeResourceHash)) {
        Fail 'mc26 runtime-resource-hash must retain its Identifier API seam.'
    }
    Assert-ContainsOnce $mc26RuntimeResourceHash 'import net.minecraft.resources.Identifier;' 'mc26 runtime-resource-hash Identifier import'
    Assert-ContainsOnce $mc26RuntimeResourceHash 'Map<Identifier, Resource> resources = manager.listResources("textures", RuntimeResourceHash::isFixtureResource);' 'mc26 runtime-resource-hash Identifier resource lookup'
    Assert-ContainsOnce $mc26RuntimeResourceHash 'private static boolean isFixtureResource(Identifier location) {' 'mc26 runtime-resource-hash Identifier predicate'
    foreach ($caller in @('LegacyReloadManager', 'FabricNativeReloadManager', 'Mc26ReloadManager')) {
        Assert-ContainsCount $Sources[$caller] 'RuntimeResourceHash.report(manager, context.reloadId());' 1 "runtime-resource-hash caller '$caller'"
    }

    $nativeReloadManager = $Sources.FabricNativeReloadManager
    Assert-ContainsOnce $nativeReloadManager '//? if >=1.20.1 && <=1.21.11 {' 'Fabric native reload-manager outer target guard'
    Assert-ContainsOnce $nativeReloadManager 'package com.teenkung.packforge.mixin.observe;' 'Fabric native reload-manager package'
    Assert-ContainsOnce $nativeReloadManager '@Mixin(ReloadableResourceManager.class)' 'Fabric native reload-manager target'
    Assert-ContainsOnce $nativeReloadManager '@WrapMethod(method = "createReload")' 'Fabric native reload-manager hook'
    Assert-ContainsOnce $nativeReloadManager 'CompletableFuture<Unit> initialStage, List<PackResources> packs, Operation<ReloadInstance> original) {' 'Fabric native reload-manager signature'
    Assert-ContainsCount $nativeReloadManager 'ReloadLifecycle.startReload();' 1 'Fabric native reload-manager lifecycle start'
    Assert-ContainsCount $nativeReloadManager 'ReloadExecutionContext.bind(context)' 1 'Fabric native reload-manager context bind'
    Assert-ContainsCount $nativeReloadManager 'RuntimeResourceHash.report(manager, context.reloadId());' 1 'Fabric native reload-manager runtime hash'
    Assert-ContainsCount $nativeReloadManager 'ReloadLifecycle.finishReload(context, error);' 2 'Fabric native reload-manager lifecycle finish paths'
    Assert-InOrder $nativeReloadManager @(
        'ReloadExecutionContext context = ReloadLifecycle.startReload();',
        'ReloadableResourceManager manager = (ReloadableResourceManager) (Object) this;',
        'try {',
        'try (ReloadExecutionContext.Scope ignored = ReloadExecutionContext.bind(context)) {',
        'instance = original.call(preparationExecutor, reloadExecutor, initialStage, packs);',
        'instance.done().whenComplete((result, error) -> {',
        'if (error == null && ReloadExecutionContext.isCurrent(context)) {',
        'RuntimeResourceHash.report(manager, context.reloadId());',
        'ReloadLifecycle.finishReload(context, error);',
        'return instance;',
        '} catch (RuntimeException | Error error) {',
        'ReloadLifecycle.finishReload(context, error);',
        'throw error;'
    ) 'Fabric native reload-manager lifecycle order'
    Assert-ContainsCount $nativeReloadManager '//? if ' 1 'Fabric native reload-manager conditional openers'
    Assert-ContainsCount $nativeReloadManager '//?}' 1 'Fabric native reload-manager conditional closers'
    if ((Normalize-NativeStonecutterJava $nativeReloadManager) -cne (Normalize-NativeStonecutterJava $Sources.LegacyReloadManager)) {
        Fail 'Fabric native reload-manager body must remain exactly equal to retained standalone/rollback implementation after directive and whitespace normalization.'
    }
    $nativeReloadManagerMetrics = Measure-StonecutterConditionalBlocks $nativeReloadManager 'Fabric native reload-manager'
    if ($nativeReloadManagerMetrics.Blocks -ne 1) { Fail "Fabric native reload-manager must contain one Stonecutter block; found $($nativeReloadManagerMetrics.Blocks)." }
    if ($nativeReloadManagerMetrics.MaxLines -gt 40) { Fail "Fabric native reload-manager Stonecutter block spans $($nativeReloadManagerMetrics.MaxLines) lines; maximum is 40." }

    $mc26ReloadManager = $Sources.Mc26ReloadManager
    if ((Normalize-NativeStonecutterJava $nativeReloadManager) -ceq (Normalize-NativeStonecutterJava $mc26ReloadManager)) {
        Fail 'mc26 reload-manager must retain its startup status/timing behavior seam.'
    }
    Assert-ContainsOnce $mc26ReloadManager 'import com.teenkung.packforge.startup.StartupStatus;' 'mc26 reload-manager startup status import'
    Assert-ContainsOnce $mc26ReloadManager 'import com.teenkung.packforge.startup.StartupTimings;' 'mc26 reload-manager startup timings import'
    Assert-ContainsOnce $mc26ReloadManager 'long startupStartNs = System.nanoTime();' 'mc26 reload-manager startup timer'
    Assert-ContainsOnce $mc26ReloadManager 'StartupStatus.update("Loading", "client resources");' 'mc26 reload-manager loading status'
    Assert-ContainsOnce $mc26ReloadManager 'StartupTimings.event("resource_reload_start");' 'mc26 reload-manager start event'
    Assert-ContainsOnce $mc26ReloadManager 'StartupStatus.update(error == null ? "Finishing" : "Failed", "client resources");' 'mc26 reload-manager completion status'
    Assert-ContainsCount $mc26ReloadManager 'StartupTimings.recordDuration("resource_reload_wall", System.nanoTime() - startupStartNs);' 2 'mc26 reload-manager duration paths'
    Assert-ContainsOnce $mc26ReloadManager 'StartupTimings.event(error == null ? "resource_reload_complete" : "resource_reload_failed");' 'mc26 reload-manager completion event'
    Assert-ContainsOnce $mc26ReloadManager 'StartupTimings.event("resource_reload_failed");' 'mc26 reload-manager synchronous failure event'
    Assert-ContainsCount $mc26ReloadManager 'ReloadLifecycle.finishReload(context, error);' 2 'mc26 reload-manager lifecycle finish paths'

    try { $reloadManagerDescriptors = $Sources.FabricReloadManagerDescriptors | ConvertFrom-Json -AsHashtable } catch { Fail "Fabric reload-manager descriptor proof is invalid JSON: $($_.Exception.Message)" }
    Assert-SameSet @($reloadManagerDescriptors.Keys) $CanonicalFabricReloadManagerDescriptorPaths 'Fabric reload-manager descriptor paths'
    foreach ($descriptorPath in $CanonicalFabricReloadManagerDescriptorPaths) {
        $reloadManagerCount = @($reloadManagerDescriptors[$descriptorPath].mixins | Where-Object { $_ -ceq 'observe.ReloadableResourceManagerMixin' }).Count
        if ($reloadManagerCount -ne 1) { Fail "Fabric descriptor '$descriptorPath' must register one reload-manager mixin; found $reloadManagerCount." }
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

    $nativeLoadingToast = $Sources.FabricNativeLoadingToast
    $commonLoadingToastGuard = '//? if >=1.20.5 && <=1.21.5 {'
    $modernLoadingToastGuard = '//? if >=1.21.6 && <=1.21.11 {'
    Assert-ContainsOnce $nativeLoadingToast $commonLoadingToastGuard 'Fabric native common loading-toast guard'
    Assert-ContainsOnce $nativeLoadingToast $modernLoadingToastGuard 'Fabric native modern loading-toast guard'
    Assert-ContainsCount $nativeLoadingToast '//? if ' 2 'Fabric native loading-toast conditional openers'
    Assert-ContainsCount $nativeLoadingToast '//?}' 2 'Fabric native loading-toast conditional closers'
    $commonLoadingToastBody = Get-StonecutterGuardBody $nativeLoadingToast $commonLoadingToastGuard 'Fabric native common loading-toast'
    $modernLoadingToastBody = Get-StonecutterGuardBody $nativeLoadingToast $modernLoadingToastGuard 'Fabric native modern loading-toast'
    if ((Normalize-NativeStonecutterJava $commonLoadingToastBody) -cne (Normalize-NativeStonecutterJava $Sources.LegacyCommonLoadingToast)) {
        Fail 'Fabric native common loading-toast block must remain exactly equal to retained shared/common implementation.'
    }
    if ((Normalize-NativeStonecutterJava $modernLoadingToastBody) -cne (Normalize-NativeStonecutterJava $Sources.LegacyModernLoadingToast)) {
        Fail 'Fabric native modern loading-toast block must remain exactly equal to retained shared/modern implementation.'
    }
    $nativeLoadingToastMetrics = Measure-StonecutterConditionalBlocks $nativeLoadingToast 'Fabric native loading-toast'
    if ($nativeLoadingToastMetrics.Blocks -ne 2) { Fail "Fabric native loading-toast must contain two independent Stonecutter blocks; found $($nativeLoadingToastMetrics.Blocks)." }
    if ($nativeLoadingToastMetrics.MaxLines -gt 40) { Fail "Fabric native loading-toast Stonecutter block spans $($nativeLoadingToastMetrics.MaxLines) lines; maximum is 40." }
    Assert-ContainsOnce $Sources.LegacyLowerLoadingToast 'float partialTick, CallbackInfo ci' 'Fabric lower loading-toast render signature seam'
    Assert-ContainsOnce $Sources.Mc26LoadingToast '@Mixin(value = LoadingOverlay.class, priority = 1100)' 'Fabric mc26 loading-toast priority seam'
    Assert-ContainsOnce $Sources.Mc26LoadingToast '@Inject(method = "tick", at = @At("HEAD"))' 'Fabric mc26 loading-toast tick seam'
    if ((Normalize-NativeStonecutterJava $commonLoadingToastBody) -ceq (Normalize-NativeStonecutterJava $Sources.LegacyLowerLoadingToast)) {
        Fail 'Fabric lower loading-toast physical adapter seam must remain distinct from native common block.'
    }
    if ((Normalize-NativeStonecutterJava $modernLoadingToastBody) -ceq (Normalize-NativeStonecutterJava $Sources.Mc26LoadingToast)) {
        Fail 'Fabric mc26 loading-toast physical tick/priority seam must remain distinct from native modern block.'
    }
    try { $loadingToastDescriptors = $Sources.FabricLoadingToastDescriptors | ConvertFrom-Json -AsHashtable } catch { Fail "Fabric loading-toast descriptor proof is invalid JSON: $($_.Exception.Message)" }
    Assert-SameSet @($loadingToastDescriptors.Keys) $CanonicalFabricLoadingToastDescriptorPaths 'Fabric loading-toast descriptor paths'
    foreach ($descriptorPath in $CanonicalFabricLoadingToastDescriptorPaths) {
        $loadingToastCount = @($loadingToastDescriptors[$descriptorPath].client | Where-Object { $_ -ceq 'ui.LoadingOverlayToastMixin' }).Count
        if ($loadingToastCount -ne 1) { Fail "Fabric descriptor '$descriptorPath' must register one loading-toast mixin; found $loadingToastCount." }
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
    Assert-ContainsOnce $nativeTransport 'def legacyReloadManagerSource = stonecutterDirectNode' 'Fabric legacy reload-manager source identity'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java' 'Fabric legacy reload-manager source path'
    Assert-ContainsOnce $nativeTransport 'def generatedReloadManagerSource = stonecutterDirectNode' 'Fabric generated reload-manager source identity'
    Assert-ContainsOnce $nativeTransport 'stonecutter.tasks.generatedSourcesDir.file("main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java").get().asFile.canonicalFile' 'Fabric generated reload-manager source path'
    Assert-ContainsOnce $nativeTransport 'def nativeReloadManagerActive = false' 'Fabric native reload-manager default state'
    if ($nativeTransport.Contains('versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java')) {
        Fail 'Fabric native transport must preserve the physical mc26 reload-manager source.'
    }
    Assert-ContainsOnce $nativeTransport 'def legacyRuntimeResourceHashSource = stonecutterDirectNode' 'Fabric legacy runtime-resource-hash source identity'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java' 'Fabric legacy runtime-resource-hash source path'
    Assert-ContainsOnce $nativeTransport 'def generatedRuntimeResourceHashSource = stonecutterDirectNode' 'Fabric generated runtime-resource-hash source identity'
    Assert-ContainsOnce $nativeTransport 'stonecutter.tasks.generatedSourcesDir.file("main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java").get().asFile.canonicalFile' 'Fabric generated runtime-resource-hash source path'
    Assert-ContainsOnce $nativeTransport 'def nativeRuntimeResourceHashActive = false' 'Fabric native runtime-resource-hash default state'
    if ($nativeTransport.Contains('versions/mc1_21_11/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java') -or
        $nativeTransport.Contains('versions/mc26/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java')) {
        Fail 'Fabric native transport must preserve physical 1.21.11 and mc26 runtime-resource-hash sources.'
    }
    Assert-ContainsOnce $nativeTransport 'def legacyLoadingOverlayToastSources = stonecutterDirectNode' 'Fabric legacy loading-toast source identities'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java' 'Fabric legacy common loading-toast source path'
    Assert-ContainsOnce $nativeTransport 'versions/shared/modern/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java' 'Fabric legacy modern loading-toast source path'
    Assert-ContainsOnce $nativeTransport 'def generatedLoadingOverlayToastSource = stonecutterDirectNode' 'Fabric generated loading-toast source identity'
    Assert-ContainsOnce $nativeTransport 'stonecutter.tasks.generatedSourcesDir.file("client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java").get().asFile.canonicalFile' 'Fabric generated loading-toast source path'
    Assert-ContainsOnce $nativeTransport 'def nativeLoadingOverlayToastActive = false' 'Fabric native loading-toast default state'
    Assert-ContainsOnce $nativeTransport 'legacyNativeClientSources.addAll(legacyLoadingOverlayToastSources)' 'Fabric exact loading-toast exclusion-set merge'
    Assert-ContainsOnce $nativeTransport 'nativeLoadingOverlayToastActive = selectedClientJavaSources.files.any { source -> source.canonicalFile in legacyLoadingOverlayToastSources }' 'Fabric registry-selected loading-toast activation'
    if ($nativeTransport.Contains('versions/mc1_20_1/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java') -or
        $nativeTransport.Contains('versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java')) {
        Fail 'Fabric native transport must preserve physical lower-adapter and mc26 loading-toast sources.'
    }
    Assert-ContainsOnce $nativeTransport 'if (stonecutterDirectNode) {' 'Fabric native direct-only transport'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java' 'Fabric legacy archive exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/mc1_21_shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java' 'Fabric modern archive exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java' 'Fabric legacy SharedZip exclusion'
    Assert-ContainsCount $nativeTransport 'new File(physicalRepositoryRoot, ' 10 'Fabric exact legacy native exclusions'
    Assert-ContainsOnce $nativeTransport 'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java' 'Fabric legacy shared bitmap-provider exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java' 'Fabric legacy 1.21.11 bitmap-provider exclusion'
    Assert-ContainsOnce $nativeTransport 'versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java' 'Fabric legacy mc26 bitmap-provider exclusion'
    Assert-ContainsOnce $nativeTransport 'nativeSharedZipActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacySharedZipSource }' 'Fabric registry-selected SharedZip activation'
    Assert-ContainsOnce $nativeTransport 'nativeReloadManagerActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacyReloadManagerSource }' 'Fabric registry-selected reload-manager activation'
    Assert-ContainsOnce $nativeTransport 'nativeRuntimeResourceHashActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacyRuntimeResourceHashSource }' 'Fabric registry-selected runtime-resource-hash activation'
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
    Assert-ContainsOnce $fabricSourcesJar '!nativeReloadManagerActive && details.file.canonicalFile == generatedReloadManagerSource' 'Fabric inactive generated reload-manager filter'
    Assert-ContainsOnce $fabricSourcesJar '!nativeRuntimeResourceHashActive && details.file.canonicalFile == generatedRuntimeResourceHashSource' 'Fabric inactive generated runtime-resource-hash filter'
    Assert-ContainsOnce $fabricSourcesJar '!nativeLoadingOverlayToastActive && details.file.canonicalFile == generatedLoadingOverlayToastSource' 'Fabric inactive generated loading-toast filter'
    Assert-ContainsOnce $fabricSourcesJar 'details.exclude()' 'Fabric sources JAR legacy-source exclusion'
    Assert-ContainsCount $fabricBuild 'stonecutter.tasks.generatedSourcesDir.dir("main/java")' 2 'Fabric exact generated Java root wiring'
    Assert-ContainsCount $fabricBuild 'stonecutter.tasks.generatedSourcesDir.dir("client/java")' 2 'Fabric exact generated client Java root wiring'
    if ($fabricBuild.Contains('stonecutter.tasks.generatedSourcesDir.dir("main")')) {
        Fail 'Fabric must not expose the Stonecutter source-set container as a Java root; use main/java.'
    }
    Assert-ContainsCount $fabricBuild 'dependsOn(stonecutter.tasks.generate["main"])' 1 'Fabric exact main compile generation wiring'
    Assert-ContainsCount $fabricBuild 'stonecutter.tasks.generate["client"]' 2 'Fabric exact client generation wiring'
    foreach ($loaderId in @('forge', 'neoforge')) {
        if ($Sources["Loader:$loaderId"].Contains('FilePackResourcesArchiveMixin.java') -or
            $Sources["Loader:$loaderId"].Contains('SharedZipFileAccessMixin.java') -or
            $Sources["Loader:$loaderId"].Contains('BitmapProviderDefinitionMixin.java') -or
            $Sources["Loader:$loaderId"].Contains('fabric/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java') -or
            $Sources["Loader:$loaderId"].Contains('fabric/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java') -or
            $Sources["Loader:$loaderId"].Contains('fabric/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java')) {
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

    return [pscustomobject]@{ DirectCells = $nodes.Count; JoptOverrides = $joptOverrides.Count; NativeArchiveCells = $CanonicalFabricNativeArchiveTargets.Count; NativeSharedZipCells = $CanonicalFabricNativeSharedZipTargets.Count; NativeReloadManagerCells = $CanonicalFabricNativeReloadManagerTargets.Count; NativeRuntimeResourceHashCells = $CanonicalFabricNativeRuntimeResourceHashTargets.Count; NativeBitmapCells = $CanonicalFabricNativeBitmapTargets.Count; NativeLoadingToastCells = $CanonicalFabricNativeLoadingToastTargets.Count }
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
    Assert-MutationRejected 'native-reload-manager-registry-route-drift' { param($r, $s) $route = @($r.sharedJavaSources | Where-Object { $_.id -ceq 'reload-manager-pre-26' })[0]; $route.targets = @($route.targets | Where-Object { $_ -cne 'mc1_21_11' }) } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-lower-range-drift' { param($r, $s) $s.FabricNativeReloadManager = $s.FabricNativeReloadManager.Replace('>=1.20.1', '>=1.20.2') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-upper-range-missing' { param($r, $s) $s.FabricNativeReloadManager = $s.FabricNativeReloadManager.Replace(' && <=1.21.11', '') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-lifecycle-drift' { param($r, $s) $s.FabricNativeReloadManager = $s.FabricNativeReloadManager.Replace('RuntimeResourceHash.report(manager, context.reloadId());', 'RuntimeResourceHash.report(manager, 0L);') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-rollback-parity-drift' { param($r, $s) $s.LegacyReloadManager = $s.LegacyReloadManager.Replace('RuntimeResourceHash.report(manager, context.reloadId());', 'RuntimeResourceHash.report(manager, 0L);') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-mc26-seam-missing' { param($r, $s) $s.Mc26ReloadManager = $s.Mc26ReloadManager.Replace('StartupTimings.event("resource_reload_start");', 'StartupTimings.event("missing");') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-descriptor-registration-missing' { param($r, $s) $s.FabricReloadManagerDescriptors = $s.FabricReloadManagerDescriptors.Replace('observe.ReloadableResourceManagerMixin', 'observe.MissingReloadableResourceManagerMixin') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-registry-route-drift' { param($r, $s) $route = @($r.sharedJavaSources | Where-Object { $_.id -ceq 'runtime-resource-hash-pre-1.21.11' })[0]; $route.targets = @($route.targets | Where-Object { $_ -cne 'mc1_21_10' }) } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-lower-range-drift' { param($r, $s) $s.FabricNativeRuntimeResourceHash = $s.FabricNativeRuntimeResourceHash.Replace('>=1.20.1', '>=1.20.2') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-upper-range-missing' { param($r, $s) $s.FabricNativeRuntimeResourceHash = $s.FabricNativeRuntimeResourceHash.Replace(' && <=1.21.10', '') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-behavior-drift' { param($r, $s) $s.FabricNativeRuntimeResourceHash = $s.FabricNativeRuntimeResourceHash.Replace('namespace.equals("example")', 'false') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-rollback-parity-drift' { param($r, $s) $s.LegacyRuntimeResourceHash = $s.LegacyRuntimeResourceHash.Replace('namespace.equals("example")', 'false') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-1.21.11-seam-missing' { param($r, $s) $s.Mc12111RuntimeResourceHash = $s.Mc12111RuntimeResourceHash.Replace('import java.util.function.Predicate;', 'import java.util.function.Function;') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-mc26-seam-missing' { param($r, $s) $s.Mc26RuntimeResourceHash = $s.Mc26RuntimeResourceHash.Replace('import net.minecraft.resources.Identifier;', 'import net.minecraft.resources.ResourceLocation;') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-registry-route-drift' { param($r, $s) $route = @($r.sharedJavaSources | Where-Object { $_.id -ceq 'bitmap-provider-definition-through-1.21.10' })[0]; $route.sourceSet = 'main' } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-lower-range-drift' { param($r, $s) $s.FabricNativeBitmap = $s.FabricNativeBitmap.Replace('>=1.20.1', '>=1.20.2') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-upper-range-missing' { param($r, $s) $s.FabricNativeBitmap = $s.FabricNativeBitmap.Replace(' && <=26.1', '') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-behavior-drift' { param($r, $s) $s.FabricNativeBitmap = $s.FabricNativeBitmap.Replace('FontBitmapProviderCache.enabled()', 'false') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-retained-parity-drift' { param($r, $s) $s.Mc26Bitmap = $s.Mc26Bitmap.Replace('FontBitmapProviderCache.enabled()', 'false') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-descriptor-registration-missing' { param($r, $s) $s.FabricBitmapDescriptors = $s.FabricBitmapDescriptors.Replace('font.BitmapProviderDefinitionMixin', 'font.MissingBitmapProviderDefinitionMixin') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-common-route-drift' { param($r, $s) $route = @($r.sharedJavaSources | Where-Object { $_.id -ceq 'loading-overlay-toast-1.20.5-through-1.21.5' })[0]; $route.targets = @($route.targets | Where-Object { $_ -cne 'mc1_21_5' }) } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-modern-route-drift' { param($r, $s) $route = @($r.sharedJavaSources | Where-Object { $_.id -ceq 'loading-overlay-toast-1.21.6-through-1.21.11' })[0]; $route.sourceSet = 'main' } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-common-range-drift' { param($r, $s) $s.FabricNativeLoadingToast = $s.FabricNativeLoadingToast.Replace('>=1.20.5 && <=1.21.5', '>=1.20.6 && <=1.21.5') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-modern-range-drift' { param($r, $s) $s.FabricNativeLoadingToast = $s.FabricNativeLoadingToast.Replace('>=1.21.6 && <=1.21.11', '>=1.21.6') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-common-body-drift' { param($r, $s) $s.FabricNativeLoadingToast = $s.FabricNativeLoadingToast.Replace('ReloadSummaryToast.showPending();', 'return;') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-modern-body-drift' { param($r, $s) $s.FabricNativeLoadingToast = $s.FabricNativeLoadingToast.Replace('ReloadStatus.consumeSummaryToast();', 'null;') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-lower-seam-drift' { param($r, $s) $s.LegacyLowerLoadingToast = $s.LegacyLowerLoadingToast.Replace('float partialTick', 'float tickDelta') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-mc26-seam-drift' { param($r, $s) $s.Mc26LoadingToast = $s.Mc26LoadingToast.Replace('priority = 1100', 'priority = 1000') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-descriptor-registration-missing' { param($r, $s) $s.FabricLoadingToastDescriptors = $s.FabricLoadingToastDescriptors.Replace('ui.LoadingOverlayToastMixin', 'ui.MissingLoadingOverlayToastMixin') } $Registry $Sources
    Assert-MutationRejected 'native-archive-legacy-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java', 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-legacy-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java', 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/MissingSharedZip.java') } $Registry $Sources
    Assert-MutationRejected 'native-source-extra-exclusion' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace("`tlegacyNativeSources = [", "`tlegacyNativeSources = [`n`t`tnew File(physicalRepositoryRoot, `"versions/shared/common/src/main/java/com/teenkung/packforge/mixin/loader/Extra.java`"),") } $Registry $Sources
    Assert-MutationRejected 'native-archive-generated-source-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('java.srcDir(stonecutter.tasks.generatedSourcesDir.dir("main/java"))', 'java.srcDir("src/main/java")') } $Registry $Sources
    Assert-MutationRejected 'native-archive-generated-root-too-high' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('stonecutter.tasks.generatedSourcesDir.dir("main/java")', 'stonecutter.tasks.generatedSourcesDir.dir("main")') } $Registry $Sources
    Assert-MutationRejected 'native-archive-compile-generation-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('dependsOn(stonecutter.tasks.generate["main"])', 'dependsOn(tasks.named("classes"))') } $Registry $Sources
    Assert-MutationRejected 'native-archive-sources-jar-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('details.exclude()', 'details.path') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-activation-drift' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('nativeSharedZipActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacySharedZipSource }', 'nativeSharedZipActive = true') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-inactive-generated-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('|| (!nativeSharedZipActive && details.file.canonicalFile == generatedSharedZipSource)', '') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-legacy-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java', 'versions/shared/common/src/main/java/com/teenkung/packforge/mixin/observe/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-mc26-exclusion-leak' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace("`tlegacyNativeSources = [", "`tlegacyNativeSources = [`n`t`tnew File(physicalRepositoryRoot, `"versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java`"),") } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-activation-drift' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('nativeReloadManagerActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacyReloadManagerSource }', 'nativeReloadManagerActive = true') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-inactive-generated-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('|| (!nativeReloadManagerActive && details.file.canonicalFile == generatedReloadManagerSource)', '') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-legacy-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java', 'versions/shared/common/src/main/java/com/teenkung/packforge/loader/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-physical-exclusion-leak' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace("`tlegacyNativeSources = [", "`tlegacyNativeSources = [`n`t`tnew File(physicalRepositoryRoot, `"versions/mc1_21_11/common/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java`"),") } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-activation-drift' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('nativeRuntimeResourceHashActive = selectedMainJavaSources.files.any { source -> source.canonicalFile == legacyRuntimeResourceHashSource }', 'nativeRuntimeResourceHashActive = true') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-inactive-generated-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('|| (!nativeRuntimeResourceHashActive && details.file.canonicalFile == generatedRuntimeResourceHashSource)', '') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-client-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java', 'versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/font/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-generated-client-root-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('stonecutter.tasks.generatedSourcesDir.dir("client/java")', 'files()') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-client-generation-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('dependsOn(stonecutter.tasks.generate["client"])', 'dependsOn(tasks.named("classes"))') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-sources-jar-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('|| details.file.canonicalFile in legacyNativeClientSources', '') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-common-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java', 'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/ui/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-modern-exclusion-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('versions/shared/modern/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java', 'versions/shared/modern/src/client/java/com/teenkung/packforge/client/mixin/ui/Missing.java') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-lower-exclusion-leak' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('legacyNativeClientSources.addAll(legacyLoadingOverlayToastSources)', 'legacyNativeClientSources.add(new File(physicalRepositoryRoot, "versions/mc1_20_1/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java"))') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-mc26-exclusion-leak' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('legacyNativeClientSources.addAll(legacyLoadingOverlayToastSources)', 'legacyNativeClientSources.add(new File(physicalRepositoryRoot, "versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java"))') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-activation-drift' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('nativeLoadingOverlayToastActive = selectedClientJavaSources.files.any { source -> source.canonicalFile in legacyLoadingOverlayToastSources }', 'nativeLoadingOverlayToastActive = true') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-inactive-generated-filter-missing' { param($r, $s) $s['Loader:fabric'] = $s['Loader:fabric'].Replace('|| (!nativeLoadingOverlayToastActive && details.file.canonicalFile == generatedLoadingOverlayToastSource)', '') } $Registry $Sources
    Assert-MutationRejected 'native-archive-duplicate-source-registration' { param($r, $s) $s['Loader:fabric'] += "`nstonecutter.tasks.configureSource(sourceSets.main)" } $Registry $Sources
    Assert-MutationRejected 'native-archive-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/main/java/com/teenkung/packforge/mixin/loader/FilePackResourcesArchiveMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-shared-zip-mc26-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("versions/mc26/common/src/main/java/com/teenkung/packforge/mixin/loader/SharedZipFileAccessMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-bitmap-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/client/java/com/teenkung/packforge/client/mixin/font/BitmapProviderDefinitionMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-reload-manager-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/main/java/com/teenkung/packforge/mixin/observe/ReloadableResourceManagerMixin.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-runtime-resource-hash-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/main/java/com/teenkung/packforge/loader/RuntimeResourceHash.java"),', '') } $Registry $Sources
    Assert-MutationRejected 'native-loading-toast-validator-input-missing' { param($r, $s) $s.RootBuild = $s.RootBuild.Replace('file("fabric/src/client/java/com/teenkung/packforge/client/mixin/ui/LoadingOverlayToastMixin.java"),', '') } $Registry $Sources
    Write-Output 'Stonecutter direct contract self-test PASS: baseline accepted; 89 mutations rejected.'
}

$requiredPaths = @($RegistryPath, $SettingsPath, $RootBuildPath, $ForgeBuildPath,
    (Join-Path $RepositoryRoot 'platform\fabric\build.gradle'),
    (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle'),
    (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\FilePackResourcesArchiveMixin.java'),
    (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java'),
    (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\observe\ReloadableResourceManagerMixin.java'),
    (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java'),
    (Join-Path $RepositoryRoot 'fabric\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'fabric\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java'),
    (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessAccessor.java'),
    (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\mixin\observe\ReloadableResourceManagerMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\observe\ReloadableResourceManagerMixin.java'),
    (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java'),
    (Join-Path $RepositoryRoot 'versions\mc1_21_11\common\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java'),
    (Join-Path $RepositoryRoot 'versions\shared\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc1_21_11\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java'),
    (Join-Path $RepositoryRoot 'versions\shared\common\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java'),
    (Join-Path $RepositoryRoot 'versions\shared\modern\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc1_20_1\common\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java'),
    (Join-Path $RepositoryRoot 'versions\mc26\common\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java'),
    (Join-Path $RepositoryRoot 'stonecutter-build.gradle'),
    (Join-Path $RepositoryRoot 'gradle\packforge-stonecutter-direct-parity.gradle')) + @(
        $CanonicalFabricSharedZipDescriptorPaths | ForEach-Object { Join-Path $RepositoryRoot $_ }
    ) + @(
        $CanonicalFabricBitmapDescriptorPaths | ForEach-Object { Join-Path $RepositoryRoot $_ }
    ) + @(
        $CanonicalFabricReloadManagerDescriptorPaths | ForEach-Object { Join-Path $RepositoryRoot $_ }
    ) + @(
        $CanonicalFabricLoadingToastDescriptorPaths | ForEach-Object { Join-Path $RepositoryRoot $_ }
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
$reloadManagerDescriptorProof = [ordered]@{}
foreach ($relativePath in $CanonicalFabricReloadManagerDescriptorPaths) {
    try {
        $reloadManagerDescriptorProof[$relativePath] = Get-Content -LiteralPath (Join-Path $RepositoryRoot $relativePath) -Raw | ConvertFrom-Json
    } catch {
        Fail "Fabric reload-manager descriptor '$relativePath' is invalid JSON: $($_.Exception.Message)"
    }
}
$loadingToastDescriptorProof = [ordered]@{}
foreach ($relativePath in $CanonicalFabricLoadingToastDescriptorPaths) {
    try {
        $loadingToastDescriptorProof[$relativePath] = Get-Content -LiteralPath (Join-Path $RepositoryRoot $relativePath) -Raw | ConvertFrom-Json
    } catch {
        Fail "Fabric loading-toast descriptor '$relativePath' is invalid JSON: $($_.Exception.Message)"
    }
}
$sources = @{
    Settings = Get-Content -LiteralPath $SettingsPath -Raw
    RootBuild = Get-Content -LiteralPath $RootBuildPath -Raw
    Rollback = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'stonecutter-build.gradle') -Raw
    Parity = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'gradle\packforge-stonecutter-direct-parity.gradle') -Raw
    FabricNativeArchive = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\FilePackResourcesArchiveMixin.java') -Raw
    FabricNativeSharedZip = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java') -Raw
    FabricNativeReloadManager = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\mixin\observe\ReloadableResourceManagerMixin.java') -Raw
    FabricNativeRuntimeResourceHash = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java') -Raw
    FabricNativeBitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    FabricNativeLoadingToast = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'fabric\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java') -Raw
    LegacySharedZip = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java') -Raw
    Mc26SharedZip = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessMixin.java') -Raw
    Mc26SharedZipAccessor = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\loader\SharedZipFileAccessAccessor.java') -Raw
    LegacyReloadManager = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\mixin\observe\ReloadableResourceManagerMixin.java') -Raw
    Mc26ReloadManager = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\mixin\observe\ReloadableResourceManagerMixin.java') -Raw
    LegacyRuntimeResourceHash = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\common\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java') -Raw
    Mc12111RuntimeResourceHash = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc1_21_11\common\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java') -Raw
    Mc26RuntimeResourceHash = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\main\java\com\teenkung\packforge\loader\RuntimeResourceHash.java') -Raw
    LegacyBitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    Mc12111Bitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc1_21_11\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    Mc26Bitmap = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\client\java\com\teenkung\packforge\client\mixin\font\BitmapProviderDefinitionMixin.java') -Raw
    LegacyCommonLoadingToast = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\common\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java') -Raw
    LegacyModernLoadingToast = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\shared\modern\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java') -Raw
    LegacyLowerLoadingToast = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc1_20_1\common\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java') -Raw
    Mc26LoadingToast = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'versions\mc26\common\src\client\java\com\teenkung\packforge\client\mixin\ui\LoadingOverlayToastMixin.java') -Raw
    FabricSharedZipDescriptors = $sharedZipDescriptorProof | ConvertTo-Json -Depth 20 -Compress
    FabricReloadManagerDescriptors = $reloadManagerDescriptorProof | ConvertTo-Json -Depth 20 -Compress
    FabricBitmapDescriptors = $bitmapDescriptorProof | ConvertTo-Json -Depth 20 -Compress
    FabricLoadingToastDescriptors = $loadingToastDescriptorProof | ConvertTo-Json -Depth 20 -Compress
    'Loader:fabric' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\fabric\build.gradle') -Raw
    'Loader:forge' = Get-Content -LiteralPath $ForgeBuildPath -Raw
    'Loader:neoforge' = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'platform\neoforge\build.gradle') -Raw
}
$summary = Invoke-DirectContractValidation $registry $sources
if ($SelfTest) { Invoke-SelfTests $registry $sources }
Write-Output "Stonecutter direct contract PASS: $($summary.DirectCells) direct cells; $($summary.NativeArchiveCells) Fabric native archive cells; $($summary.NativeSharedZipCells) Fabric native SharedZip cells; $($summary.NativeReloadManagerCells) Fabric native reload-manager cells; $($summary.NativeRuntimeResourceHashCells) Fabric native runtime-resource-hash cells; $($summary.NativeBitmapCells) Fabric native bitmap-provider cells; $($summary.NativeLoadingToastCells) Fabric native loading-toast cells; registry physical routes preserved for standalone/rollback; canonical replacement direct-only; $($summary.JoptOverrides) registry JOptSimple overrides."
