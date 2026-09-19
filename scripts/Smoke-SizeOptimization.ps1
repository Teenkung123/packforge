#requires -Version 7.0
<#
.SYNOPSIS
Smoke a final PackForge JAR in a fresh game directory with loader-provided MixinExtras.
.DESCRIPTION
Supports official-mapped Fabric and NeoForge. Legacy Fabric final JARs require the
production launcher harness because Loom's named development runtime cannot load
their intermediary bytecode. Never modifies an existing game profile. Generates
the existing deterministic fixture, verifies three resolved-resource hashes and
two controller reloads, and records exact artifact and MixinExtras provenance.
Configuration-screen access, icon appearance and font/overlay appearance still
require interactive verification. Run cells serially to avoid Gradle node races.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateSet('fabric', 'neoforge')] [string] $Platform,
    [Parameter(Mandatory)] [ValidatePattern('^[A-Za-z0-9_.-]+$')] [string] $Target,
    [string] $ArtifactPath,
    [ValidatePattern('^[0-9][0-9A-Za-z.+_-]*$')] [string] $MinecraftVersionOverride,
    [ValidatePattern('^[0-9][0-9A-Za-z.+_-]*$')] [string] $NeoForgeVersionOverride,
    [switch] $WithModMenu,
    [string] $ModMenuPath,
    [string] $FabricApiPath,
    [string] $JavaPath = 'C:\Program Files\Java\jdk-25\bin\java.exe',
    [ValidateRange(60, 3600)] [int] $TimeoutSeconds = 900,
    [switch] $ValidateOnly,
    [switch] $ProvenanceContractOnly
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-RequiredFile([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required file is missing: $Path" }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Read-ZipText($Zip, [string] $Name) {
    $entry = $Zip.GetEntry($Name)
    if ($null -eq $entry) { throw "ZIP entry is missing: $Name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Assert-FabricMod([string] $Path, [string] $ExpectedId) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $metadata = Read-ZipText $zip 'fabric.mod.json' | ConvertFrom-Json
        if ($metadata.id -ne $ExpectedId) { throw "Expected $ExpectedId in $Path; found $($metadata.id)" }
    } finally { $zip.Dispose() }
}

function Assert-MixinExtrasSource {
    param(
        [Parameter(Mandatory)] [string] $Source,
        [Parameter(Mandatory)] [string] $Platform,
        [Parameter(Mandatory)] [string] $Target,
        [Parameter(Mandatory)] [string] $LoaderVersion,
        [Parameter(Mandatory)] [string] $ExtrasVersion
    )

    # Fabric Loader 0.19.5 publishes this exact development library in its
    # official fabric-installer.json. Loom exposes that loader-owned library
    # through Gradle's module cache during the development smoke, so the class
    # log legitimately points at files-2.1. Keep this exception bound to the
    # official coordinate, cache layout, filename, and published hashes.
    $officialLoaderLibrary = $Platform -eq 'fabric' -and $Target -in @('mc26_3', 'mc26_1_to_26_3') -and
        $LoaderVersion -eq '0.19.5' -and $ExtrasVersion -eq '0.5.5'
    if ($officialLoaderLibrary) {
        $expected = [ordered]@{
            coordinate = 'io.github.llamalad7:mixinextras-fabric:0.5.5'
            loaderVersion = '0.19.5'
            extrasVersion = '0.5.5'
            filename = 'mixinextras-fabric-0.5.5.jar'
            size = 727864
            sha1 = 'd1055b99c0ab08a8403fe2da3d79ca28e6340a76'
            sha256 = '5da883dc4bfb16e4ceca3f16d8c4ad937bdfb1ce337ed5ba15472d4d81dd6242'
            sha512 = '854e8fdc79699835253cb1d98ff10c6ef88586f11a1a279a198e8c6784ff7477dceb6d8d8562fafb458b2c43648f6dc4853e42a3d606ba495d1c3eafa02d953a'
            sourceArtifactUrl = 'https://maven.fabricmc.net/io/github/llamalad7/mixinextras-fabric/0.5.5/mixinextras-fabric-0.5.5.jar'
            pomUrl = 'https://maven.fabricmc.net/io/github/llamalad7/mixinextras-fabric/0.5.5/mixinextras-fabric-0.5.5.pom'
            installerMetadataUrl = 'https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar!/fabric-installer.json'
            loaderProfileUrl = 'https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/profile/json'
        }
        try { $uri = [Uri]::new($Source) } catch { throw "MixinExtras source is not a file URI: $Source" }
        if ($uri.Scheme -ne 'file') { throw "Loader-owned MixinExtras must come from a local file URI: $Source" }
        $localPath = $uri.LocalPath
        $normalizedPath = $localPath.Replace('/', '\')
        $expectedPath = '(?i)[\\/]caches[\\/]modules-2[\\/]files-2\.1[\\/]io\.github\.llamalad7[\\/]mixinextras-fabric[\\/]0\.5\.5[\\/]d1055b99c0ab08a8403fe2da3d79ca28e6340a76[\\/]mixinextras-fabric-0\.5\.5\.jar$'
        if ($normalizedPath -notmatch $expectedPath) {
            throw "MixinExtras source is not the official Fabric Loader 0.19.5 development coordinate: $Source"
        }
        if (-not (Test-Path -LiteralPath $localPath -PathType Leaf)) { throw "MixinExtras source file is missing: $localPath" }
        $actual = [ordered]@{
            size = (Get-Item -LiteralPath $localPath).Length
            sha1 = (Get-FileHash -LiteralPath $localPath -Algorithm SHA1).Hash.ToLowerInvariant()
            sha256 = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash.ToLowerInvariant()
            sha512 = (Get-FileHash -LiteralPath $localPath -Algorithm SHA512).Hash.ToLowerInvariant()
        }
        foreach ($name in @('size', 'sha1', 'sha256', 'sha512')) {
            if ($actual[$name].ToString() -ne $expected[$name].ToString()) {
                throw "Loader-owned MixinExtras $name mismatch: expected $($expected[$name]); found $($actual[$name])"
            }
        }
        return [ordered]@{
            provider = 'loader'
            classification = 'official-loader-development-library'
            source = $Source
            sourcePath = $localPath
            coordinate = $expected.coordinate
            loaderVersion = $expected.loaderVersion
            extrasVersion = $expected.extrasVersion
            size = $actual.size
            sha1 = $actual.sha1
            sha256 = $actual.sha256
            sha512 = $actual.sha512
            sourceArtifactUrl = $expected.sourceArtifactUrl
            pomUrl = $expected.pomUrl
            installerMetadataUrl = $expected.installerMetadataUrl
            loaderProfileUrl = $expected.loaderProfileUrl
        }
    }
    if ($Source -match '[/\\]build[/\\]classes|packforge-|files-2\.1[/\\]') {
        throw "MixinExtras leaked from a direct development dependency or PackForge: $Source"
    }
    if ($Source -notmatch "fabric-loader|mixinextras[-.]$Platform") {
        throw "Cannot establish the loader MixinExtras provider from class source: $Source"
    }
    return [ordered]@{
        provider = 'loader'
        classification = 'legacy-loader-source-pattern'
        source = $Source
        loaderVersion = $LoaderVersion
        extrasVersion = $ExtrasVersion
    }
}

function Resolve-ExpectedMixinExtras {
    param(
        [Parameter(Mandatory)] $LoaderData,
        [Parameter(Mandatory)] [string] $Platform,
        [string] $NeoForgeVersionOverride,
        [Parameter(Mandatory)] [string] $DefaultVersion
    )
    if (-not $NeoForgeVersionOverride) {
        return [ordered]@{ version = $DefaultVersion; runtimeCheck = $null }
    }
    if ($Platform -ne 'neoforge') { throw 'NeoForge runtime version checks require the NeoForge platform.' }
    if (-not $LoaderData.ContainsKey('runtimeChecks')) {
        throw "The target has no runtimeChecks registry for NeoForge override $NeoForgeVersionOverride."
    }
    $matches = @($LoaderData.runtimeChecks | Where-Object { $_.version -eq $NeoForgeVersionOverride })
    if ($matches.Count -ne 1) {
        throw "NeoForge override $NeoForgeVersionOverride must match exactly one target runtimeChecks.version."
    }
    $check = $matches[0]
    if (-not $check.mixinExtrasVersion) {
        throw "NeoForge runtime check $NeoForgeVersionOverride has no MixinExtras version."
    }
    return [ordered]@{
        version = [string]$check.mixinExtrasVersion
        runtimeCheck = [ordered]@{
            minecraftVersion = [string]$check.minecraftVersion
            version = [string]$check.version
            mixinExtrasVersion = [string]$check.mixinExtrasVersion
        }
    }
}

# Focused contract tests dot-source this script to exercise the provenance
# helper without touching the registry, Gradle, Java, or a client process.
if ($ProvenanceContractOnly) { return }

function Invoke-SmokeGradle([string[]] $Arguments, [string] $Label, [switch] $Client) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $script:java
    $start.WorkingDirectory = $script:root
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    # Isolate inherited Java agents/options and keep Windows AF_UNIX socket paths short.
    $start.Environment.Remove('JDK_JAVA_OPTIONS') | Out-Null
    $start.Environment.Remove('_JAVA_OPTIONS') | Out-Null
    $start.Environment['JAVA_TOOL_OPTIONS'] = '-Djava.io.tmpdir="' + $script:tempRoot.Replace('\', '/') + '"'
    $start.Environment['TEMP'] = $script:tempRoot
    $start.Environment['TMP'] = $script:tempRoot
    $start.Environment['PACKFORGE_RUNTIME_RESOURCE_HASH'] = if ($Client) { 'true' } else { 'false' }
    if ($Client) {
        # Keep the reload controller property and temp isolation inherited by all
        # Gradle probes. Class logging is attached only to the actual game run
        # through the platform run configuration below.
        $start.Environment['JAVA_TOOL_OPTIONS'] += ' -Dpackforge.runtimeSmokeReloadCount=2'
    }
    foreach ($argument in @('-classpath', (Join-Path $script:root 'gradle/wrapper/gradle-wrapper.jar'),
        'org.gradle.wrapper.GradleWrapperMain') + $Arguments + @('--configure-on-demand', '--no-daemon',
        '--max-workers=2', '--console=plain', '--stacktrace')) {
        $start.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    $started = $false
    $stdout = $null
    $stderr = $null
    try {
        $started = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        while (-not $process.HasExited -and [DateTime]::UtcNow -lt $script:deadline) {
            Start-Sleep -Milliseconds 500
        }
        if (-not $process.HasExited) { throw "$Label timed out; evidence: $script:runRoot" }
        if ($process.ExitCode -ne 0) { throw "$Label exited $($process.ExitCode); evidence: $script:runRoot" }
    } finally {
        if ($started) {
            # Kill only the process tree we started; never enumerate/kill other Java sessions.
            if (-not $process.HasExited) {
                $process.Kill($true)
                if (-not $process.WaitForExit(10000)) { throw "Owned $Label process tree did not exit after termination." }
            }
            if ($null -ne $stdout) { [IO.File]::WriteAllText((Join-Path $script:runRoot "$Label.stdout.log"), $stdout.GetAwaiter().GetResult()) }
            if ($null -ne $stderr) { [IO.File]::WriteAllText((Join-Path $script:runRoot "$Label.stderr.log"), $stderr.GetAwaiter().GetResult()) }
        }
        $process.Dispose()
    }
}

function Get-FixtureResourceHash([string] $Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    $digest = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        $metadata = Read-ZipText $zip 'pack.mcmeta' | ConvertFrom-Json -AsHashtable
        $prefixes = [Collections.Generic.List[string]]::new()
        $prefixes.Add('')
        # The generator emits only the target's explicitly supported overlay range.
        if ($metadata.ContainsKey('overlays')) {
            foreach ($overlay in $metadata.overlays.entries) { $prefixes.Add("$($overlay.directory)/") }
        }
        $resources = [Collections.Generic.SortedDictionary[string, IO.Compression.ZipArchiveEntry]]::new([StringComparer]::Ordinal)
        foreach ($prefix in $prefixes) {
            $pattern = '^' + [regex]::Escape($prefix) + 'assets/(example|generated[^/]*?)/(textures/.+)$'
            foreach ($entry in $zip.Entries) {
                if ($entry.Name -and $entry.FullName -cmatch $pattern) {
                    $resources["$($Matches[1]):$($Matches[2])"] = $entry
                }
            }
        }
        if ($resources.Count -eq 0) { throw 'The fixture contains no hashable resources.' }
        $buffer = [byte[]]::new(65536)
        foreach ($resource in $resources.GetEnumerator()) {
            $digest.AppendData([Text.Encoding]::UTF8.GetBytes($resource.Key))
            $digest.AppendData([byte[]]@(0))
            $inputStream = $resource.Value.Open()
            try {
                while (($count = $inputStream.Read($buffer, 0, $buffer.Length)) -gt 0) { $digest.AppendData($buffer, 0, $count) }
            } finally { $inputStream.Dispose() }
            $digest.AppendData([byte[]]@(255))
        }
        return @{ entries = $resources.Count; sha256 = [Convert]::ToHexString($digest.GetHashAndReset()).ToLowerInvariant() }
    } finally { $digest.Dispose(); $zip.Dispose() }
}

function Assert-SmokeEvidence([string] $Log, [string] $ClassLog, [string] $ArtifactName,
    [string] $ExpectedTarget, [string] $ExtrasVersion, [string] $LoaderVersion, [string] $Loader, $ExpectedResources) {
    if ($Log -match 'Critical injection failure|Mixin apply failed|MixinTransformerError|InvalidInjectionException|NoClassDefFoundError|ExceptionInInitializerError|PackForge runtime smoke failure|PackForge failed to hash') {
        throw 'Fatal client diagnostic; inspect the retained logs.'
    }
    if ($Log -notmatch "PackForge capabilities:.*target=$([regex]::Escape($ExpectedTarget))(?:\s|,|$)" -or
        $Log -notmatch 'PackForge runtime smoke complete: reloads=2(?:\s|$)' -or
        [regex]::Matches($Log, 'PackForge runtime smoke reload requested:').Count -ne 2) {
        throw 'Missing capability, two-reload, or completion proof.'
    }
    if ($Log -notmatch "PackForge runtime source:[^\r\n]*$([regex]::Escape($ArtifactName))") {
        throw 'PackForge was not observed loading from the final artifact.'
    }
    if ($Log -notmatch "Initializing MixinExtras[^\r\n]*\(version=$([regex]::Escape($ExtrasVersion))\)") {
        throw "The expected MixinExtras $ExtrasVersion service did not initialize."
    }
    $sources = @([regex]::Matches($ClassLog, 'com\.llamalad7\.mixinextras\.service\.MixinExtrasServiceImpl source: ([^\r\n]+)') |
        ForEach-Object { $_.Groups[1].Value.Trim() } | Select-Object -Unique)
    if ($sources.Count -ne 1) { throw 'Expected exactly one observed MixinExtras service class source.' }
    $source = $sources[0]
    $provenance = Assert-MixinExtrasSource -Source $source -Platform $Loader -Target $ExpectedTarget `
        -LoaderVersion $LoaderVersion -ExtrasVersion $ExtrasVersion
    $hashes = [regex]::Matches($Log, 'PackForge resolved-resource hash: id=(\d+) entries=(\d+) sha256=([0-9a-f]{64})')
    if ($hashes.Count -ne 3 -or @($hashes | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique).Count -ne 3) {
        throw 'Expected exactly three distinct completed resource-hash reports (startup plus two reloads).'
    }
    foreach ($hash in $hashes) {
        if ([int]$hash.Groups[2].Value -ne $ExpectedResources.entries -or $hash.Groups[3].Value -ne $ExpectedResources.sha256) {
            throw 'Resolved resources differ from the independently hashed fixture.'
        }
    }
    return [ordered]@{ source = $source; provenance = $provenance }
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$registry = Get-Content -LiteralPath (Join-Path $root 'gradle/minecraft-targets.json') -Raw | ConvertFrom-Json -AsHashtable
$targetData = @($registry.targets | Where-Object key -eq $Target)
if ($targetData.Count -ne 1 -or -not $targetData[0].platforms.ContainsKey($Platform)) { throw 'Unknown target/loader cell.' }
$targetData = $targetData[0]
$loaderData = $targetData.platforms[$Platform]
if ($Platform -eq 'fabric' -and $loaderData.mappingMode -ne 'official') {
    throw 'Legacy Fabric intermediary JARs require Smoke-Fabric-Production.ps1, not Loom runClient.'
}
if ($NeoForgeVersionOverride -and $Platform -ne 'neoforge') { throw 'NeoForgeVersionOverride requires NeoForge.' }
if ($MinecraftVersionOverride -and $Platform -ne 'fabric') { throw 'MinecraftVersionOverride is for Fabric; use NeoForgeVersionOverride for NeoForge.' }
if ($WithModMenu -and $Platform -ne 'fabric') { throw 'Mod Menu is Fabric-only.' }
if (-not $WithModMenu -and ($ModMenuPath -or $FabricApiPath)) { throw 'Optional mod paths require -WithModMenu.' }
$extras = if ($loaderData.ContainsKey('mixinExtras')) { $loaderData.mixinExtras } else { $registry.mixinExtrasPolicies[$Platform] }
if ($extras.provider -ne 'loader') { throw 'This harness checks loader-provided MixinExtras only.' }
$extrasRuntime = Resolve-ExpectedMixinExtras -LoaderData $loaderData -Platform $Platform `
    -NeoForgeVersionOverride $NeoForgeVersionOverride -DefaultVersion $extras.version
$expectedExtrasVersion = $extrasRuntime.version
$modVersion = ((Get-Content -LiteralPath (Join-Path $root 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }) -split '=', 2)[1]
$artifactName = "packforge-$Platform-$modVersion$($loaderData.versionSuffix)-mc$($targetData.artifactMinecraft).jar"
if (-not $ArtifactPath) { $ArtifactPath = Join-Path $root "build/libs/$artifactName" }
$artifact = Get-RequiredFile $ArtifactPath
if ([IO.Path]::GetFileName($artifact) -cne $artifactName) { throw "Expected exact final artifact filename: $artifactName" }
$java = Get-RequiredFile $JavaPath
$optionalMods = @()
if ($WithModMenu) {
    $optionalMods = @((Get-RequiredFile $ModMenuPath), (Get-RequiredFile $FabricApiPath))
    Assert-FabricMod $optionalMods[0] 'modmenu'
    Assert-FabricMod $optionalMods[1] 'fabric-api'
}
$artifactZip = [IO.Compression.ZipFile]::OpenRead($artifact)
try { if (@($artifactZip.Entries | Where-Object FullName -Like '*.jar').Count -ne 0) { throw 'Provided-policy artifact still contains a nested library.' } }
finally { $artifactZip.Dispose() }
$artifactHash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
$tempRoot = Join-Path $root '.gradle/codex-plan-temp'
$cell = "$Platform-$Target" + $(if ($MinecraftVersionOverride) { "-$MinecraftVersionOverride" }) + $(if ($NeoForgeVersionOverride) { "-$NeoForgeVersionOverride" }) + $(if ($WithModMenu) { '-modmenu' })
$runRoot = Join-Path $root "build/size-optimization/runtime/$cell-$([guid]::NewGuid().ToString('N'))"
$arguments = @("-Ppackforge_target=$Target", "-Ppackforge_run_directory=$runRoot", '-Ppackforge_artifact_smoke=true')
if ($MinecraftVersionOverride) { $arguments += "-Ppackforge_minecraft_version_override=$MinecraftVersionOverride" }
if ($NeoForgeVersionOverride) { $arguments += "-Ppackforge_neoforge_version_override=$NeoForgeVersionOverride" }
$report = [ordered]@{ platform = $Platform; target = $Target; artifact = $artifact; sha256 = $artifactHash;
    minecraftOverride = $MinecraftVersionOverride; neoforgeOverride = $NeoForgeVersionOverride;
    mixinExtrasVersion = $expectedExtrasVersion; mixinExtrasRuntimeCheck = $extrasRuntime.runtimeCheck;
    provider = 'loader'; modMenu = [bool]$WithModMenu; run = $runRoot }
if ($ValidateOnly) { $report | ConvertTo-Json; return }
New-Item -ItemType Directory -Path $runRoot | Out-Null
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
try {
    $mods = New-Item -ItemType Directory -Path (Join-Path $runRoot 'mods')
    New-Item -ItemType Directory -Path (Join-Path $runRoot 'config'), (Join-Path $runRoot 'resourcepacks') | Out-Null
    $staged = Join-Path $mods.FullName $artifactName
    Copy-Item -LiteralPath $artifact -Destination $staged
    if ((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifactHash) { throw 'Copied artifact hash mismatch.' }
    foreach ($mod in $optionalMods) { Copy-Item -LiteralPath $mod -Destination $mods.FullName }
    Invoke-SmokeGradle -Arguments (@(":${Platform}:${Target}:benchmarkPackIndex") + $arguments) -Label 'fixture'
    $fixture = Get-RequiredFile (Join-Path $root "build/nodes/$Platform/$Target/benchmark/deterministic-large-pack.zip")
    Copy-Item -LiteralPath $fixture -Destination (Join-Path $runRoot 'resourcepacks/deterministic-large-pack.zip')
    $expectedResources = Get-FixtureResourceHash $fixture
    $report.fixtureSha256 = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
    $report.resourceHash = $expectedResources
    [IO.File]::WriteAllText((Join-Path $runRoot 'options.txt'), 'resourcePacks:["vanilla","file/deterministic-large-pack.zip"]' + "`n" + 'incompatibleResourcePacks:[]' + "`n")
    # Current defaults exercise ZIP, font, atlas and status functionality.
    [IO.File]::WriteAllText((Join-Path $runRoot 'config/packforge.json'), '{"reloadOptimizerEnabled":true,"loaderIndexEnabled":true,"loaderZipPoolEnabled":false,"largeAtlasFixerEnabled":false,"cpuMipPreparationEnabled":true,"loadingStatusOverlayEnabled":true,"reloadSummaryToastEnabled":true}')
    $clientArguments = @(":${Platform}:${Target}:runClient") + $arguments + @('-Ppackforge_runtime_class_provenance=true')
    Invoke-SmokeGradle -Arguments $clientArguments -Label 'client' -Client
    $log = [IO.File]::ReadAllText((Get-RequiredFile (Join-Path $runRoot 'logs/latest.log')))
    $classLog = [IO.File]::ReadAllText((Join-Path $runRoot 'client.stderr.log'))
    $runtimeLoaderVersion = if ($Platform -eq 'fabric') { $loaderData.loaderVersion }
        elseif ($NeoForgeVersionOverride) { $NeoForgeVersionOverride } else { $loaderData.version }
    $mixinExtrasEvidence = Assert-SmokeEvidence $log $classLog $artifactName $Target $expectedExtrasVersion `
        $runtimeLoaderVersion $Platform $expectedResources
    if ($Platform -eq 'fabric') {
        $hasModMenu = $log -match '(?m)^\s*- modmenu\s'
        if ($hasModMenu -ne [bool]$WithModMenu) { throw 'Unexpected Mod Menu presence/absence in loader mod list.' }
    }
    $report.mixinExtrasSource = $mixinExtrasEvidence.source
    $report.mixinExtrasProvenance = $mixinExtrasEvidence.provenance
    $report.reloads = 2
    $report.cleanExit = $true
    $report.passed = $true
} catch {
    $report.passed = $false
    $report.error = $_.Exception.Message
    throw
} finally {
    [IO.File]::WriteAllText((Join-Path $runRoot 'result.json'), ($report | ConvertTo-Json -Depth 6))
}
$report | ConvertTo-Json -Depth 6
