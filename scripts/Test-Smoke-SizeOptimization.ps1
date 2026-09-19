#requires -Version 7.0
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$smoke = Join-Path $PSScriptRoot 'Smoke-SizeOptimization.ps1'
$smokeSource = Get-Content -LiteralPath $smoke -Raw
if ($smokeSource -match "JAVA_TOOL_OPTIONS[^\r\n]*Xlog:class\+load") {
    throw 'Class logging must not be inherited through JAVA_TOOL_OPTIONS.'
}
if ($smokeSource -notmatch 'packforge_runtime_class_provenance=true') {
    throw 'Client smoke must pass the class-provenance Gradle property.'
}
if ($smokeSource -notmatch 'runtimeSmokeReloadCount=2') {
    throw 'Client smoke must retain the runtime reload-count property.'
}
$fabricBuild = Get-Content -LiteralPath (Join-Path $root 'platform/fabric/build.gradle') -Raw
if ($fabricBuild -notmatch 'runs\.client\.vmArg.*-Xlog:class\+load=info:stderr') {
    throw 'Fabric run must attach class logging through Loom runs.client.vmArg.'
}
if ($fabricBuild -notmatch 'packforge_runtime_class_provenance') {
    throw 'Fabric class logging must be gated by the smoke Gradle property.'
}
$neoForgeBuild = Get-Content -LiteralPath (Join-Path $root 'platform/neoforge/build.gradle') -Raw
if ($neoForgeBuild -notmatch 'jvmArgument.*-Xlog:class\+load=info:stderr') {
    throw 'NeoForge run must attach class logging through the public jvmArgument DSL.'
}
if ($neoForgeBuild -notmatch 'packforge_runtime_class_provenance') {
    throw 'NeoForge class logging must be gated by the smoke Gradle property.'
}

# This mode defines the smoke helpers and returns before any registry, Java,
# Gradle, client, or artifact operation is attempted.
$null = . $smoke -Platform fabric -Target mc26_1_to_26_3 -ProvenanceContractOnly

$cacheRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/io.github.llamalad7/mixinextras-fabric/0.5.5'
$cachedJar = Get-ChildItem -LiteralPath $cacheRoot -Recurse -Filter 'mixinextras-fabric-0.5.5.jar' -File |
    Where-Object { $_.Directory.Name -eq 'd1055b99c0ab08a8403fe2da3d79ca28e6340a76' } |
    Select-Object -First 1
if ($null -eq $cachedJar) { throw 'The official-hash-matching MixinExtras 0.5.5 cache artifact is missing.' }

$source = ([Uri]::new($cachedJar.FullName)).AbsoluteUri
$accepted = Assert-MixinExtrasSource -Source $source -Platform fabric -Target mc26_1_to_26_3 -LoaderVersion 0.19.5 -ExtrasVersion 0.5.5
if ($accepted.classification -ne 'official-loader-development-library' -or
    $accepted.sha1 -ne 'd1055b99c0ab08a8403fe2da3d79ca28e6340a76' -or
    $accepted.sha256 -ne '5da883dc4bfb16e4ceca3f16d8c4ad937bdfb1ce337ed5ba15472d4d81dd6242' -or
    $accepted.size -ne 727864) {
    throw 'The exact official Fabric Loader development-library provenance was not returned.'
}

function Assert-Rejected([string] $Label, [string] $Candidate) {
    $rejected = $false
    try {
        Assert-MixinExtrasSource -Source $Candidate -Platform fabric -Target mc26_1_to_26_3 -LoaderVersion 0.19.5 -ExtrasVersion 0.5.5 | Out-Null
    } catch {
        $rejected = $true
    }
    if (-not $rejected) { throw "Provenance contract accepted an invalid $Label source: $Candidate" }
}

Assert-Rejected 'wrong cache hash' ($source -replace 'd1055b99c0ab08a8403fe2da3d79ca28e6340a76', '0000000000000000000000000000000000000000')
Assert-Rejected 'PackForge build output' 'file:///C:/Users/nathaphon/IdeaProjects/PackForge/build/classes/java/main/mixinextras-fabric-0.5.5.jar'
Assert-Rejected 'unapproved module-cache coordinate' 'file:///C:/Users/nathaphon/.gradle/caches/modules-2/files-2.1/io.github.llamalad7/mixinextras-fabric/0.5.5/0000000000000000000000000000000000000000/mixinextras-fabric-0.5.5.jar'
Assert-Rejected 'PackForge artifact source' 'file:///C:/Users/nathaphon/IdeaProjects/PackForge/build/libs/packforge-fabric-1.4-beta.1-mc26.3.jar'

$neoLoader = @{ runtimeChecks = @(@{ minecraftVersion = '26.3'; version = '26.3.0.6-beta'; mixinExtrasVersion = '0.5.4' }) }
$neoExpected = Resolve-ExpectedMixinExtras -LoaderData $neoLoader -Platform neoforge `
    -NeoForgeVersionOverride '26.3.0.6-beta' -DefaultVersion '0.5.3'
if ($neoExpected.version -ne '0.5.4' -or $neoExpected.runtimeCheck.version -ne '26.3.0.6-beta') {
    throw 'NeoForge 26.3 runtimeChecks did not select MixinExtras 0.5.4.'
}
$neoBase = Resolve-ExpectedMixinExtras -LoaderData $neoLoader -Platform neoforge -DefaultVersion '0.5.3'
if ($neoBase.version -ne '0.5.3' -or $null -ne $neoBase.runtimeCheck) {
    throw 'NeoForge base runtime did not retain MixinExtras 0.5.3.'
}
$wrongVersionRejected = $false
try { Resolve-ExpectedMixinExtras -LoaderData $neoLoader -Platform neoforge -NeoForgeVersionOverride '26.3.0.7-beta' -DefaultVersion '0.5.3' | Out-Null } catch { $wrongVersionRejected = $true }
if (-not $wrongVersionRejected) { throw 'Unregistered NeoForge override was accepted.' }

$registry = Get-Content -LiteralPath (Join-Path $root 'gradle/minecraft-targets.json') -Raw | ConvertFrom-Json -AsHashtable
$targetData = @($registry.targets | Where-Object key -eq 'mc26_1_to_26_3')
if ($targetData.Count -ne 1 -or -not $targetData[0].platforms.ContainsKey('neoforge')) {
    throw 'The unified NeoForge target/runtimeChecks registry is missing.'
}
$actualNeo = Resolve-ExpectedMixinExtras -LoaderData $targetData[0].platforms.neoforge -Platform neoforge `
    -NeoForgeVersionOverride '26.3.0.6-beta' -DefaultVersion $registry.mixinExtrasPolicies.neoforge.version
if ($actualNeo.version -ne '0.5.4' -or $actualNeo.runtimeCheck.minecraftVersion -ne '26.3') {
    throw 'The current unified registry did not select NeoForge 26.3 MixinExtras 0.5.4.'
}
$actualBase = Resolve-ExpectedMixinExtras -LoaderData $targetData[0].platforms.neoforge -Platform neoforge `
    -DefaultVersion $registry.mixinExtrasPolicies.neoforge.version
if ($actualBase.version -ne '0.5.3') { throw 'The current unified registry base NeoForge version is not 0.5.3.' }

Write-Output "PASS Smoke-SizeOptimization provenance contract: coordinate=$($accepted.coordinate) source=$($accepted.sourcePath) sha256=$($accepted.sha256)"
