[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ForgeClientRoot,

    [ValidateSet('forge', 'neoforge')]
    [string] $Loader = 'forge',

    [Parameter(Mandatory = $true)]
    [string] $VersionName,

    [Parameter(Mandatory = $true)]
    [string] $ArtifactPath,

    [Parameter(Mandatory = $true)]
    [string] $AssetsRoot,

    [Parameter(Mandatory = $true)]
    [string] $NativesRoot,

    [string] $ResourcePackPath,

    [Parameter(Mandatory = $true)]
    [string] $JavaPath,

    [string[]] $AdditionalModPaths,

    [string[]] $ExpectedLogMarkers,

    [string[]] $ForbiddenLogMarkers,

    [string] $CompatibilityProfilePath,

    [switch] $ValidateCompatibilityProfileOnly,

    [string] $ExpectedProfileTarget,

    [string] $ExpectedProfileMinecraftVersion,

    [string] $FallbackLibrariesRoot,

    [ValidateRange(60, 3600)]
    [int] $TimeoutSeconds = 900,

    [ValidateRange(0, 10)]
    [int] $ReloadCount = 2,

    [switch] $AllowControlledTermination
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

if (-not ('PackForgeProductionSmokeNative' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class PackForgeProductionSmokeNative
{
    private const uint KeyUp = 0x0002;
    private const uint WmClose = 0x0010;

    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int maxCount);
    [DllImport("user32.dll")]
    private static extern int GetWindowTextLength(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern bool PostMessage(IntPtr hWnd, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")]
    private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

    public static IntPtr FindWindow(int processId)
    {
        IntPtr found = IntPtr.Zero;
        EnumWindowsProc callback = delegate(IntPtr handle, IntPtr ignored)
        {
            if (!IsWindowVisible(handle)) return true;
            uint owner;
            GetWindowThreadProcessId(handle, out owner);
            if (owner != processId) return true;
            int length = GetWindowTextLength(handle);
            if (length <= 0) return true;
            var title = new StringBuilder(length + 1);
            GetWindowText(handle, title, title.Capacity);
            if (title.ToString().IndexOf("Minecraft", StringComparison.OrdinalIgnoreCase) < 0) return true;
            found = handle;
            return false;
        };
        EnumWindows(callback, IntPtr.Zero);
        return found;
    }

    public static bool SendReload(IntPtr handle)
    {
        if (handle == IntPtr.Zero || !SetForegroundWindow(handle)) return false;
        keybd_event(0x72, 0, 0, UIntPtr.Zero);
        keybd_event(0x54, 0, 0, UIntPtr.Zero);
        keybd_event(0x54, 0, KeyUp, UIntPtr.Zero);
        keybd_event(0x72, 0, KeyUp, UIntPtr.Zero);
        return true;
    }

    public static bool Close(IntPtr handle)
    {
        return handle != IntPtr.Zero && PostMessage(handle, WmClose, IntPtr.Zero, IntPtr.Zero);
    }
}
'@
}

function Resolve-RequiredPath {
    param([string] $Path, [string] $Description, [switch] $Directory)

    try {
        $resolved = [IO.Path]::GetFullPath($Path)
    } catch {
        throw "$Description is not a valid path: $Path"
    }
    $pathType = if ($Directory) { 'Container' } else { 'Leaf' }
    if (-not (Test-Path -LiteralPath $resolved -PathType $pathType)) {
        throw "$Description is missing: $resolved"
    }
    return $resolved
}

function Get-ObjectProperty {
    param($Object, [string] $Name)
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Import-CompatibilityProfile {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) { return $null }
    $resolved = Resolve-RequiredPath -Path $Path -Description 'Compatibility profile input'
    try {
        $profile = Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
    } catch {
        throw "Compatibility profile input is not valid JSON: $resolved"
    }
    if ([int] $profile.schema -notin @(1, 2)) {
        throw "Unsupported compatibility profile input schema: $($profile.schema)"
    }
    return $profile
}

function Get-CompatibilityProfileStrings {
    param($Profile, [string] $Name)

    if ($null -eq $Profile) { return @() }
    $property = $Profile.PSObject.Properties[$Name]
    if ($null -eq $property) { return @() }
    $values = [Collections.Generic.List[string]]::new()
    foreach ($value in @($property.Value)) {
        if ($value -isnot [string]) { throw "Compatibility profile '$Name' values must be strings." }
        [void] $values.Add([string] $value)
    }
    return @($values)
}

function Assert-Schema2CompatibilityProfile {
    param($Profile, [string] $ExpectedLoader)

    if ([string] $Profile.profileId -notmatch '^[a-z0-9][a-z0-9._-]{0,127}$') {
        throw "Schema-2 compatibility profile has unsafe profileId '$($Profile.profileId)'."
    }
    if ([string] $Profile.catalogSha256 -notmatch '^[A-F0-9]{64}$') {
        throw 'Schema-2 compatibility profile has invalid catalogSha256.'
    }
    if ([string] $Profile.loader -cne $ExpectedLoader) {
        throw "Schema-2 compatibility profile loader '$($Profile.loader)' does not match '$ExpectedLoader'."
    }
    if ([string] $Profile.expectedPath -notin @('FULL_OPTIMIZED_PATH', 'HOOK_PRESERVING_COALESCED_PATH', 'SAFE_ORIGINAL_PATH', 'EXTERNALLY_OWNED_PATH')) {
        throw "Schema-2 compatibility profile has unsupported expectedPath '$($Profile.expectedPath)'."
    }
    $runtimeMods = @($Profile.runtimeMods)
    if ($runtimeMods.Count -eq 0) { throw 'Schema-2 compatibility profile has no runtimeMods.' }
    $seenIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $seenArtifacts = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($runtimeMod in $runtimeMods) {
        $id = [string] $runtimeMod.id
        $artifact = [string] $runtimeMod.artifact
        $sha256 = [string] $runtimeMod.sha256
        if ($id -notmatch '^[a-z0-9][a-z0-9._-]{0,127}$' -or -not $seenIds.Add($id)) {
            throw "Schema-2 compatibility profile has unsafe or duplicate runtime mod ID '$id'."
        }
        if ($artifact -notmatch '^[A-Za-z0-9][A-Za-z0-9._+\-]*\.jar$' -or -not $seenArtifacts.Add($artifact)) {
            throw "Schema-2 compatibility profile has unsafe or colliding runtime mod artifact '$artifact'."
        }
        $resolvedMod = Resolve-RequiredPath -Path ([string] $runtimeMod.path) -Description "Schema-2 runtime mod '$id'"
        if ([IO.Path]::GetFileName($resolvedMod) -cne $artifact) {
            throw "Schema-2 runtime mod '$id' path does not match artifact '$artifact'."
        }
        $actualHash = (Get-FileHash -LiteralPath $resolvedMod -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($sha256 -notmatch '^[A-F0-9]{64}$' -or $actualHash -cne $sha256) {
            throw "Schema-2 runtime mod '$id' SHA-256 mismatch: expected=$sha256 actual=$actualHash"
        }
    }
    $declaredModIds = @(Get-CompatibilityProfileStrings -Profile $Profile -Name 'modIds')
    $expectedModIds = @($runtimeMods | ForEach-Object { [string] $_.id } | Sort-Object)
    if (($declaredModIds -join ',') -cne ($expectedModIds -join ',')) {
        throw 'Schema-2 compatibility profile modIds do not exactly match sorted runtimeMods IDs.'
    }
    $fixture = $Profile.fixture
    if ($null -eq $fixture -or [string] $fixture.id -notmatch '^[a-z0-9][a-z0-9._-]{0,127}$') {
        throw 'Schema-2 compatibility profile has invalid fixture identity.'
    }
    $resolvedFixture = Resolve-RequiredPath -Path ([string] $fixture.path) -Description "Schema-2 fixture '$($fixture.id)'"
    if ([IO.Path]::GetFileName($resolvedFixture) -cne [string] $fixture.artifact) {
        throw "Schema-2 fixture path does not match artifact '$($fixture.artifact)'."
    }
    $fixtureHash = (Get-FileHash -LiteralPath $resolvedFixture -Algorithm SHA256).Hash.ToUpperInvariant()
    if ([string] $fixture.sha256 -notmatch '^[A-F0-9]{64}$' -or $fixtureHash -cne [string] $fixture.sha256) {
        throw "Schema-2 fixture SHA-256 mismatch: expected=$($fixture.sha256) actual=$fixtureHash"
    }
    if ($null -eq $Profile.config -or $null -eq $Profile.config.overrides -or $null -eq $Profile.featureOverrides) {
        throw 'Schema-2 compatibility profile omitted featureOverrides/config transport.'
    }
    if (($Profile.config.overrides | ConvertTo-Json -Compress -Depth 10) -cne ($Profile.featureOverrides | ConvertTo-Json -Compress -Depth 10)) {
        throw 'Schema-2 compatibility profile config overrides differ from featureOverrides.'
    }
    if (@($Profile.featureOverrides.PSObject.Properties).Count -gt 0) {
        throw "Schema-2 compatibility profile '$($Profile.profileId)' declares featureOverrides, but executable override-key validation is not implemented; refusing launch."
    }
    [void] (Get-CompatibilityProfileStrings -Profile $Profile -Name 'expectedLogMarkers')
    [void] (Get-CompatibilityProfileStrings -Profile $Profile -Name 'forbiddenLogMarkers')
}

function Assert-Schema2RuntimeCell {
    param($Profile, [string] $ExpectedMinecraftVersion, [string] $ExpectedTarget)

    if ([string]::IsNullOrWhiteSpace($ExpectedMinecraftVersion) -or [string]::IsNullOrWhiteSpace($ExpectedTarget)) {
        throw 'Schema-2 compatibility runtime-cell validation requires Minecraft version and artifact target.'
    }
    if ([string] $Profile.minecraftVersion -cne $ExpectedMinecraftVersion -or [string] $Profile.target -cne $ExpectedTarget) {
        throw "Schema-2 compatibility profile runtime cell mismatch: declared=$($Profile.minecraftVersion)/$($Profile.target) actual=$ExpectedMinecraftVersion/$ExpectedTarget"
    }
}

$compatibilityProfile = Import-CompatibilityProfile -Path $CompatibilityProfilePath
if ($null -ne $compatibilityProfile) {
    if ([int] $compatibilityProfile.schema -eq 2) {
        Assert-Schema2CompatibilityProfile -Profile $compatibilityProfile -ExpectedLoader $Loader
        if (@($AdditionalModPaths | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) }).Count -gt 0) {
            throw 'Schema-2 compatibility profile cannot be combined with direct AdditionalModPaths.'
        }
        $AdditionalModPaths = @($AdditionalModPaths | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) }) + @($compatibilityProfile.runtimeMods | ForEach-Object { [string] $_.path })
        $declaredFixturePath = [IO.Path]::GetFullPath([string] $compatibilityProfile.fixture.path)
        if (-not [string]::IsNullOrWhiteSpace($ResourcePackPath) -and
            -not [string]::Equals([IO.Path]::GetFullPath($ResourcePackPath), $declaredFixturePath, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Direct ResourcePackPath differs from schema-2 compatibility fixture path.'
        }
        $ResourcePackPath = $declaredFixturePath
    } else {
        $AdditionalModPaths = @($AdditionalModPaths | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) }) + @(Get-CompatibilityProfileStrings -Profile $compatibilityProfile -Name 'additionalModPaths')
    }
    $ExpectedLogMarkers = @($ExpectedLogMarkers | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) }) + @(Get-CompatibilityProfileStrings -Profile $compatibilityProfile -Name 'expectedLogMarkers')
    $ForbiddenLogMarkers = @($ForbiddenLogMarkers | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) }) + @(Get-CompatibilityProfileStrings -Profile $compatibilityProfile -Name 'forbiddenLogMarkers')
}
if ($ValidateCompatibilityProfileOnly.IsPresent) {
    if ($null -eq $compatibilityProfile) { throw '-ValidateCompatibilityProfileOnly requires -CompatibilityProfilePath.' }
    if ([int] $compatibilityProfile.schema -eq 2) {
        Assert-Schema2RuntimeCell `
            -Profile $compatibilityProfile `
            -ExpectedMinecraftVersion $ExpectedProfileMinecraftVersion `
            -ExpectedTarget $ExpectedProfileTarget
    }
    $transport = [ordered]@{
        schema = [int] $compatibilityProfile.schema
        profileId = [string] (Get-ObjectProperty -Object $compatibilityProfile -Name 'profileId')
        loader = $Loader
        modIds = if ([int] $compatibilityProfile.schema -eq 2) { [string[]] @($compatibilityProfile.modIds) } else { [string[]] @() }
        additionalModPaths = [string[]] @($AdditionalModPaths)
        fixturePath = $ResourcePackPath
        expectedLogMarkers = [string[]] @($ExpectedLogMarkers)
        forbiddenLogMarkers = [string[]] @($ForbiddenLogMarkers)
    }
    Write-Output ("PROFILE_TRANSPORT " + ($transport | ConvertTo-Json -Compress -Depth 6))
    return
}

function Test-RuleSet {
    param($Rules)

    if ($null -eq $Rules) { return $true }
    $allowed = $false
    foreach ($rule in @($Rules)) {
        $matches = $true
        $os = Get-ObjectProperty -Object $rule -Name 'os'
        if ($null -ne $os) {
            $osName = Get-ObjectProperty -Object $os -Name 'name'
            $osArch = Get-ObjectProperty -Object $os -Name 'arch'
            $osVersion = Get-ObjectProperty -Object $os -Name 'version'
            if ($null -ne $osName -and [string] $osName -ne 'windows') { $matches = $false }
            if ($null -ne $osArch -and [string] $osArch -notin @('x86_64', 'amd64')) { $matches = $false }
            if ($null -ne $osVersion -and $matches) {
                $matches = [Environment]::OSVersion.VersionString -match [string] $osVersion
            }
        }
        if ($null -ne (Get-ObjectProperty -Object $rule -Name 'features')) { $matches = $false }
        if ($matches) { $allowed = [string] (Get-ObjectProperty -Object $rule -Name 'action') -eq 'allow' }
    }
    return $allowed
}

function Expand-Arguments {
    param($Arguments)

    $expanded = [Collections.Generic.List[string]]::new()
    foreach ($argument in @($Arguments)) {
        if ($argument -is [string]) {
            $expanded.Add($argument)
            continue
        }
        if (Test-RuleSet -Rules (Get-ObjectProperty -Object $argument -Name 'rules')) {
            foreach ($value in @((Get-ObjectProperty -Object $argument -Name 'value'))) { $expanded.Add([string] $value) }
        }
    }
    return $expanded
}

function Get-LibraryPath {
    param($Library, [string] $LibrariesRoot, [string] $FallbackRoot)

    if ([string] (Get-ObjectProperty -Object $Library -Name 'name') -match ':natives-windows-(?:arm64|aarch64|x86)$') {
        return $null
    }
    if (-not (Test-RuleSet -Rules (Get-ObjectProperty -Object $Library -Name 'rules'))) { return $null }
    $includeInClasspath = Get-ObjectProperty -Object $Library -Name 'include_in_classpath'
    if ($null -ne $includeInClasspath -and -not [bool] $includeInClasspath) { return $null }
    $downloads = Get-ObjectProperty -Object $Library -Name 'downloads'
    $downloadArtifact = Get-ObjectProperty -Object $downloads -Name 'artifact'
    $downloadPath = Get-ObjectProperty -Object $downloadArtifact -Name 'path'
    if ($null -ne $downloadPath) {
        $relativePath = ([string] $downloadPath).Replace('/', [IO.Path]::DirectorySeparatorChar)
    } else {
        $parts = ([string] $Library.name).Split(':')
        if ($parts.Count -lt 3) { throw "Unsupported library coordinate: $($Library.name)" }
        $groupPath = $parts[0].Replace('.', [IO.Path]::DirectorySeparatorChar)
        $classifier = if ($parts.Count -gt 3) { "-$($parts[3])" } else { '' }
        $relativePath = Join-Path $groupPath (Join-Path $parts[1] (Join-Path $parts[2] "$($parts[1])-$($parts[2])$classifier.jar"))
    }

    $primaryPath = Join-Path $LibrariesRoot $relativePath
    if (Test-Path -LiteralPath $primaryPath -PathType Leaf) { return $primaryPath }
    if (-not [string]::IsNullOrWhiteSpace($FallbackRoot)) {
        $fallbackPath = Join-Path $FallbackRoot $relativePath
        if (Test-Path -LiteralPath $fallbackPath -PathType Leaf) { return $fallbackPath }
    }
    return $primaryPath
}

function Get-LogText {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    return [string] (Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue)
}

function Assert-NoFatalLog {
    param([string] $Text, [string] $Context)
    $fatal = '(?im)(Critical injection failure|Mixin apply failed|MixinTransformerError|InvalidInjection(?:Exception|PointException)?|InjectionError|IllegalClassLoadError|NoClassDefFoundError|ExceptionInInitializerError|(?:^|\s)LinkageError:|Minecraft has crashed|A critical error occurred|---- Minecraft Crash Report ----|Shutdown failure!|PackForge.*(?:ERROR|Exception|FATAL))'
    if ($Text -match $fatal) { throw "Fatal PackForge/Mixin signature found during ${Context}: $($Matches[0])" }
}

function Get-RunText {
    param([string] $GameRoot, [string[]] $Paths)

    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($path in $Paths) {
        [string] $contents = Get-LogText -Path $path
        if (-not [string]::IsNullOrEmpty($contents)) { [void] $parts.Add($contents) }
    }
    $crashRoot = Join-Path $GameRoot 'crash-reports'
    if (Test-Path -LiteralPath $crashRoot -PathType Container) {
        foreach ($report in @(Get-ChildItem -LiteralPath $crashRoot -Filter '*.txt' -File -ErrorAction SilentlyContinue)) {
            [void] $parts.Add("---- Minecraft Crash Report ----`n$($report.FullName)`n$(Get-LogText -Path $report.FullName)")
        }
    }
    return [string]::Join([Environment]::NewLine, $parts)
}

function Get-MarkerCount {
    param([string] $Text, [string] $Marker)
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    return [regex]::Matches($Text, [regex]::Escape($Marker), [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
}

function ConvertTo-WindowsCommandLineArgument {
    param([string] $Value)

    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
    $builder = [Text.StringBuilder]::new()
    [void] $builder.Append('"')
    $slashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') {
            $slashes++
            continue
        }
        if ($character -eq '"') {
            [void] $builder.Append(('\' * (($slashes * 2) + 1)))
            [void] $builder.Append('"')
        } else {
            if ($slashes -gt 0) { [void] $builder.Append(('\' * $slashes)) }
            [void] $builder.Append($character)
        }
        $slashes = 0
    }
    if ($slashes -gt 0) { [void] $builder.Append(('\' * ($slashes * 2))) }
    [void] $builder.Append('"')
    return $builder.ToString()
}

$loaderDisplay = if ($Loader -eq 'neoforge') { 'NeoForge' } else { 'Forge' }
$clientRoot = Resolve-RequiredPath -Path $ForgeClientRoot -Description "$loaderDisplay client root" -Directory
$artifact = Resolve-RequiredPath -Path $ArtifactPath -Description 'PackForge production artifact'
$assets = Resolve-RequiredPath -Path $AssetsRoot -Description 'Minecraft assets root' -Directory
$natives = Resolve-RequiredPath -Path $NativesRoot -Description 'Minecraft natives root' -Directory
$java = Resolve-RequiredPath -Path $JavaPath -Description 'Java executable'
$libraries = Resolve-RequiredPath -Path (Join-Path $clientRoot 'libraries') -Description "$loaderDisplay libraries root" -Directory
$fallbackLibraries = $FallbackLibrariesRoot
if ([string]::IsNullOrWhiteSpace($fallbackLibraries)) {
    $inferredFallback = Join-Path (Split-Path -Parent $assets) 'libraries'
    if (Test-Path -LiteralPath $inferredFallback -PathType Container) { $fallbackLibraries = $inferredFallback }
} else {
    $fallbackLibraries = Resolve-RequiredPath -Path $fallbackLibraries -Description 'Fallback Minecraft libraries root' -Directory
}

if ([IO.Path]::GetPathRoot($clientRoot).TrimEnd('\') -eq $clientRoot.TrimEnd('\')) {
    throw 'ClientRoot must not be a drive root.'
}

function Assert-ProfileLogMarkers {
    param(
        [string] $Text,
        [string] $Context,
        [string[]] $Expected,
        [string[]] $Forbidden
    )

    foreach ($marker in @($Expected)) {
        if ([string]::IsNullOrWhiteSpace($marker)) { continue }
        if ($Text.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            throw "Compatibility profile is missing expected log marker during ${Context}: $marker"
        }
    }
    foreach ($marker in @($Forbidden)) {
        if ([string]::IsNullOrWhiteSpace($marker)) { continue }
        if ($Text.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw "Compatibility profile contains forbidden log marker during ${Context}: $marker"
        }
    }
}

function Write-Utf8NoBom {
    param([string] $Path, [string] $Contents)

    [IO.File]::WriteAllText($Path, $Contents, [Text.UTF8Encoding]::new($false))
}

function Compare-NumericVersion {
    param([string] $Left, [string] $Right)

    $leftParts = @($Left.Split('.') | ForEach-Object { [int] $_ })
    $rightParts = @($Right.Split('.') | ForEach-Object { [int] $_ })
    $count = [Math]::Max($leftParts.Count, $rightParts.Count)
    for ($index = 0; $index -lt $count; $index++) {
        $leftPart = if ($index -lt $leftParts.Count) { $leftParts[$index] } else { 0 }
        $rightPart = if ($index -lt $rightParts.Count) { $rightParts[$index] } else { 0 }
        if ($leftPart -lt $rightPart) { return -1 }
        if ($leftPart -gt $rightPart) { return 1 }
    }
    return 0
}

function Test-ArtifactMinecraftCoverage {
    param([string] $ArtifactMinecraft, [string] $MinecraftVersion)

    $parts = @($ArtifactMinecraft.Split('-', 2))
    if ($parts.Count -eq 1) { return $parts[0] -ieq $MinecraftVersion }
    return (Compare-NumericVersion -Left $MinecraftVersion -Right $parts[0]) -ge 0 `
        -and (Compare-NumericVersion -Left $MinecraftVersion -Right $parts[1]) -le 0
}

function Get-ArtifactTargetMarker {
    param([string] $ArtifactPath)

    $archive = [IO.Compression.ZipFile]::OpenRead($ArtifactPath)
    try {
        $entry = $archive.GetEntry('packforge-capabilities.properties')
        if ($null -eq $entry) { throw 'Artifact is missing packforge-capabilities.properties.' }
        $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8, $true)
        try { $contents = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $match = [regex]::Match($contents, '(?m)^target=(?<target>mc[0-9A-Za-z_]+)\s*$')
        if (-not $match.Success) { throw 'Artifact capability metadata is missing a valid target marker.' }
        return $match.Groups['target'].Value
    } finally {
        $archive.Dispose()
    }
}
if ($VersionName -notmatch '^[A-Za-z0-9][0-9A-Za-z.+_-]*$') {
    throw "Unexpected $loaderDisplay production version name: $VersionName"
}
$artifactMatch = [regex]::Match([IO.Path]::GetFileName($artifact), '^packforge-(forge|neoforge)-.+-mc([0-9.]+(?:-[0-9.]+)?)\.jar$')
if (-not $artifactMatch.Success) {
    throw "Artifact is not a PackForge $loaderDisplay production JAR: $artifact"
}
$artifactLoader = $artifactMatch.Groups[1].Value
if ($artifactLoader -ne $Loader) {
    throw "PackForge artifact loader '$artifactLoader' does not match requested loader '$Loader'."
}
$artifactMinecraft = $artifactMatch.Groups[2].Value
$targetMarker = Get-ArtifactTargetMarker -ArtifactPath $artifact
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resourcePackSource = $ResourcePackPath
if ([string]::IsNullOrWhiteSpace($resourcePackSource)) {
    $resourcePackSource = Join-Path $repositoryRoot "platform\$Loader\run\$targetMarker\resourcepacks\deterministic-large-pack.zip"
}

$childJsonPath = Resolve-RequiredPath -Path (Join-Path $clientRoot "versions\$VersionName\$VersionName.json") -Description "$loaderDisplay version metadata"
$child = Get-Content -LiteralPath $childJsonPath -Raw | ConvertFrom-Json
if ([string] $child.id -ne $VersionName) {
    throw "$loaderDisplay metadata identity mismatch in $childJsonPath"
}

$parentName = [string] (Get-ObjectProperty -Object $child -Name 'inheritsFrom')
$minecraftVersion = if ([string]::IsNullOrWhiteSpace($parentName)) { $VersionName } else { $parentName }
if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    Assert-Schema2RuntimeCell -Profile $compatibilityProfile -ExpectedMinecraftVersion $minecraftVersion -ExpectedTarget $targetMarker
}
if (-not (Test-ArtifactMinecraftCoverage -ArtifactMinecraft $artifactMinecraft -MinecraftVersion $minecraftVersion)) {
    throw "Artifact Minecraft segment '$artifactMinecraft' does not cover '$minecraftVersion'."
}
$parent = $null
if ([string]::IsNullOrWhiteSpace($parentName)) {
    $clientJar = Resolve-RequiredPath -Path (Join-Path $clientRoot "versions\$VersionName\$VersionName.jar") -Description 'Minecraft client JAR'
    $assetMetadata = $child
    $allLibraries = @($child.libraries)
    $allJvmArguments = @($child.arguments.jvm)
    $allGameArguments = @($child.arguments.game)
} else {
    $parentVersionRoot = Join-Path $clientRoot "versions\$parentName"
    New-Item -ItemType Directory -Path $parentVersionRoot -Force | Out-Null
    $parentJsonPath = Join-Path $parentVersionRoot "$parentName.json"
    if (-not (Test-Path -LiteralPath $parentJsonPath -PathType Leaf)) {
        $manifest = Invoke-RestMethod -Uri 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
        $version = @($manifest.versions | Where-Object { [string] $_.id -eq $parentName })
        if ($version.Count -ne 1) { throw "Mojang manifest does not contain $parentName." }
        Invoke-WebRequest -Uri ([string] $version[0].url) -OutFile $parentJsonPath
    }
    $parent = Get-Content -LiteralPath $parentJsonPath -Raw | ConvertFrom-Json
    $clientJar = Resolve-RequiredPath -Path (Join-Path $parentVersionRoot "$parentName.jar") -Description 'Minecraft client JAR'
    $assetMetadata = $parent
    $allLibraries = @($child.libraries) + @($parent.libraries)
    $allJvmArguments = @($parent.arguments.jvm) + @($child.arguments.jvm)
    $allGameArguments = @($parent.arguments.game) + @($child.arguments.game)
}

$classpath = [Collections.Generic.List[string]]::new()
$seenLibraries = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($library in $allLibraries) {
    $coordinateParts = ([string] $library.name).Split(':')
    $libraryKey = "$($coordinateParts[0]):$($coordinateParts[1])"
    if ($coordinateParts.Count -gt 3) { $libraryKey += ":$($coordinateParts[3])" }
    $libraryPath = Get-LibraryPath -Library $library -LibrariesRoot $libraries -FallbackRoot $fallbackLibraries
    if ($null -eq $libraryPath) { continue }
    if (-not $seenLibraries.Add($libraryKey)) { continue }
    $libraryPath = Resolve-RequiredPath -Path $libraryPath -Description "Library $($library.name)"
    $classpath.Add($libraryPath)
}
$classpath.Add($clientJar)
$classpathText = [string]::Join([IO.Path]::PathSeparator, $classpath)

$runId = [datetime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$gameRoot = Join-Path $clientRoot "packforge-smoke\$VersionName\$runId"
$modsRoot = Join-Path $gameRoot 'mods'
$configRoot = Join-Path $gameRoot 'config'
$logsRoot = Join-Path $gameRoot 'logs'
New-Item -ItemType Directory -Path $modsRoot, $configRoot, $logsRoot -Force | Out-Null
$artifactName = [IO.Path]::GetFileName($artifact)
$stagedArtifact = Join-Path $modsRoot $artifactName
$sourceHash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToUpperInvariant()
Copy-Item -LiteralPath $artifact -Destination $stagedArtifact -Force
$stagedHash = (Get-FileHash -LiteralPath $stagedArtifact -Algorithm SHA256).Hash.ToUpperInvariant()
if ($sourceHash -ne $stagedHash) {
    throw "Staged production artifact SHA-256 mismatch: source=$sourceHash staged=$stagedHash"
}

$stagedAdditionalMods = [Collections.Generic.List[object]]::new()
$stagedModNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
[void] $stagedModNames.Add($artifactName)
foreach ($additionalModPath in @($AdditionalModPaths)) {
    if ([string]::IsNullOrWhiteSpace($additionalModPath)) { continue }
    $additionalMod = Resolve-RequiredPath -Path $additionalModPath -Description 'Additional production profile mod'
    $additionalName = [IO.Path]::GetFileName($additionalMod)
    if ([string]::IsNullOrWhiteSpace($additionalName) -or $additionalName -notmatch '^[A-Za-z0-9][A-Za-z0-9._+\-]*\.jar$') {
        throw "Additional production profile mod must be a safe JAR filename: $additionalName"
    }
    if (-not $stagedModNames.Add($additionalName)) {
        throw "Additional production profile mod collides with an already staged mod: $additionalName"
    }

    $additionalDestination = Join-Path $modsRoot $additionalName
    Copy-Item -LiteralPath $additionalMod -Destination $additionalDestination -Force
    $additionalSourceHash = (Get-FileHash -LiteralPath $additionalMod -Algorithm SHA256).Hash.ToUpperInvariant()
    $additionalStagedHash = (Get-FileHash -LiteralPath $additionalDestination -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($additionalSourceHash -ne $additionalStagedHash) {
        throw "Staged additional mod SHA-256 mismatch: source=$additionalSourceHash staged=$additionalStagedHash"
    }
    [void] $stagedAdditionalMods.Add([ordered]@{
        kind = if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
            [string] (@($compatibilityProfile.runtimeMods | Where-Object { [IO.Path]::GetFullPath([string] $_.path) -eq [IO.Path]::GetFullPath($additionalMod) })[0].kind)
        } else { $null }
        id = if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
            [string] (@($compatibilityProfile.runtimeMods | Where-Object { [IO.Path]::GetFullPath([string] $_.path) -eq [IO.Path]::GetFullPath($additionalMod) })[0].id)
        } else { $null }
        artifact = $additionalName
        sourcePath = $additionalMod
        stagedPath = $additionalDestination
        sha256 = $additionalSourceHash
    })
}
$provenancePath = Join-Path $gameRoot 'artifact-provenance.json'
$provenance = [ordered]@{
    artifact = $artifactName
    sourcePath = $artifact
    stagedPath = $stagedArtifact
    sha256 = $sourceHash
    additionalMods = @($stagedAdditionalMods)
    minecraftVersion = $minecraftVersion
    loader = $Loader
    versionName = $VersionName
    target = $targetMarker
}
$configPath = $null
if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    $configPath = Join-Path $configRoot 'packforge.json'
    $profileConfig = [ordered]@{
        configVersion = 12
        reloadOptimizerEnabled = $true
        loaderIndexEnabled = $true
        loaderTimingsEnabled = $true
        reloadListenerTimingsEnabled = $false
        startupTimingsEnabled = $true
        startupStatusOverlayEnabled = $false
    }
    Write-Utf8NoBom -Path $configPath -Contents ($profileConfig | ConvertTo-Json)
}

if ($AllowControlledTermination.IsPresent -or ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2)) {
    $resourcePackSource = Resolve-RequiredPath -Path $resourcePackSource -Description 'Deterministic production resource pack'
    $resourcePackRoot = Join-Path $gameRoot 'resourcepacks'
    New-Item -ItemType Directory -Path $resourcePackRoot -Force | Out-Null
    $resourcePackArtifact = if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
        [string] $compatibilityProfile.fixture.artifact
    } else {
        'deterministic-large-pack.zip'
    }
    $stagedFixture = Join-Path $resourcePackRoot $resourcePackArtifact
    Copy-Item -LiteralPath $resourcePackSource -Destination $stagedFixture -Force
    $optionsPath = Join-Path $gameRoot 'options.txt'
    Set-Content -LiteralPath $optionsPath -Encoding utf8 -Value @(
        "resourcePacks:[`"vanilla`",`"file/$resourcePackArtifact`"]"
        'incompatibleResourcePacks:[]'
    )
    if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
        $stagedFixtureHash = (Get-FileHash -LiteralPath $stagedFixture -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($stagedFixtureHash -cne [string] $compatibilityProfile.fixture.sha256) {
            throw "Staged schema-2 fixture SHA-256 mismatch: expected=$($compatibilityProfile.fixture.sha256) actual=$stagedFixtureHash"
        }
        $provenance.compatibilityProfile = [ordered]@{
            schema = 2
            profileId = [string] $compatibilityProfile.profileId
            catalogSha256 = [string] $compatibilityProfile.catalogSha256
            loader = [string] $compatibilityProfile.loader
            minecraftVersion = [string] $compatibilityProfile.minecraftVersion
            target = [string] $compatibilityProfile.target
            modIds = @($compatibilityProfile.modIds)
        }
        $provenance.fixture = [ordered]@{
            id = [string] $compatibilityProfile.fixture.id
            sourcePath = $resourcePackSource
            stagedPath = $stagedFixture
            sha256 = $stagedFixtureHash
        }
        $provenance.config = [ordered]@{
            path = $configPath
            sha256 = (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash.ToUpperInvariant()
            overrides = $compatibilityProfile.config.overrides
        }
    }
}
Write-Utf8NoBom -Path $provenancePath -Contents ($provenance | ConvertTo-Json -Depth 8)

$replacements = @{
    '${auth_player_name}' = 'PackForgeSmoke'
    '${version_name}' = $VersionName
    '${game_directory}' = $gameRoot
    '${assets_root}' = $assets
    '${assets_index_name}' = [string] $assetMetadata.assetIndex.id
    '${auth_uuid}' = '00000000000000000000000000000001'
    '${auth_access_token}' = '0'
    '${clientid}' = '0'
    '${auth_xuid}' = '0'
    '${user_type}' = 'legacy'
    '${version_type}' = 'release'
    '${natives_directory}' = $natives
    '${launcher_name}' = 'PackForgeProductionSmoke'
    '${launcher_version}' = '1'
    '${classpath}' = $classpathText
    '${classpath_separator}' = [string] [IO.Path]::PathSeparator
    '${library_directory}' = $libraries
    '${resolution_width}' = '1280'
    '${resolution_height}' = '720'
}

function Expand-Token {
    param([string] $Value)
    foreach ($entry in $replacements.GetEnumerator()) { $Value = $Value.Replace([string] $entry.Key, [string] $entry.Value) }
    if ($Value -match '\$\{[^}]+\}') { throw "Unresolved launcher token in argument: $Value" }
    return $Value
}

$javaArguments = [Collections.Generic.List[string]]::new()
$javaArguments.Add('-Xms512m')
$javaArguments.Add('-Xmx2048m')
$javaArguments.Add("-Djava.library.path=$natives")
$javaArguments.Add("-DlibraryDirectory=$libraries")
foreach ($argument in (Expand-Arguments -Arguments $allJvmArguments)) {
    $expandedArgument = Expand-Token -Value $argument
    if ($expandedArgument.StartsWith('-DignoreList=', [StringComparison]::Ordinal) -and
        $expandedArgument.IndexOf("$([IO.Path]::GetFileName($clientJar))", [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        $expandedArgument += ",$([IO.Path]::GetFileName($clientJar))"
    }
    $javaArguments.Add($expandedArgument)
}
$javaArguments.Add([string] $child.mainClass)
if ($Loader -eq 'neoforge') {
    # FML consumes --mods before the launch target; arguments appended after it
    # are forwarded to Minecraft's main class instead of being loaded.
    $javaArguments.Add('--mods')
    $javaArguments.Add($stagedArtifact)
}
foreach ($argument in (Expand-Arguments -Arguments $allGameArguments)) {
    $javaArguments.Add((Expand-Token -Value $argument))
}
$javaArguments.Add('--width')
$javaArguments.Add('1280')
$javaArguments.Add('--height')
$javaArguments.Add('720')

$stdoutPath = Join-Path $logsRoot 'launcher.stdout.log'
$stderrPath = Join-Path $logsRoot 'launcher.stderr.log'
$latestLog = Join-Path $logsRoot 'latest.log'
$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $java
$startInfo.WorkingDirectory = $gameRoot
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.Arguments = [string]::Join(' ', @($javaArguments | ForEach-Object { ConvertTo-WindowsCommandLineArgument -Value $_ }))
if ($AllowControlledTermination.IsPresent) {
    $runtimeSmokeOption = "-Dpackforge.runtimeSmokeReloadCount=$ReloadCount"
    $existingJavaToolOptions = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'Process')
    $startInfo.Environment['JAVA_TOOL_OPTIONS'] = if ([string]::IsNullOrWhiteSpace($existingJavaToolOptions)) {
        $runtimeSmokeOption
    } else {
        "$existingJavaToolOptions $runtimeSmokeOption"
    }
    $startInfo.Environment['PACKFORGE_RUNTIME_RESOURCE_HASH'] = 'true'
}
if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    $startInfo.Environment['PACKFORGE_COMPAT_PROFILE_ID'] = [string] $compatibilityProfile.profileId
    $startInfo.Environment['PACKFORGE_COMPAT_MOD_IDS'] = [string]::Join(',', @($compatibilityProfile.modIds))
}

$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$started = $false
$passed = $false
$stdoutTask = $null
$stderrTask = $null
$window = [IntPtr]::Zero
$cleanExit = $false
$controlledTermination = $false
$reloadMarkers = @('minecraft:textures/atlas/mob_effects.png-atlas', 'minecraft:textures/atlas/gui.png-atlas')
$deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
try {
    $started = $process.Start()
    if (-not $started) { throw 'Java process did not start.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()

    $ready = $false
    while ([datetime]::UtcNow -lt $deadline) {
        [string] $logText = Get-LogText -Path $latestLog
        Assert-NoFatalLog -Text $logText -Context 'production startup'
        $window = [PackForgeProductionSmokeNative]::FindWindow($process.Id)
        $hasArtifact = $logText.IndexOf([IO.Path]::GetFileName($artifact), [StringComparison]::OrdinalIgnoreCase) -ge 0
        $hasCapabilities = $logText -match "PackForge capabilities:.*target=$([regex]::Escape($targetMarker))"
        $hasReload = $false
        foreach ($reloadMarker in $reloadMarkers) {
            if ($logText.IndexOf($reloadMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                $hasReload = $true
                break
            }
        }
        $hasRuntimeReady = (-not $AllowControlledTermination.IsPresent) -or
            $logText.IndexOf('PackForge runtime smoke ready:', [StringComparison]::OrdinalIgnoreCase) -ge 0
        $hasResourceHash = (-not $AllowControlledTermination.IsPresent) -or
            $logText.IndexOf('PackForge resolved-resource hash:', [StringComparison]::OrdinalIgnoreCase) -ge 0
        if ($process.HasExited) { throw "Production Forge exited before readiness with code $($process.ExitCode)." }
        if ($hasArtifact -and $hasCapabilities -and $hasReload -and $hasRuntimeReady -and $hasResourceHash `
            -and ($AllowControlledTermination.IsPresent -or $ReloadCount -eq 0 -or $window -ne [IntPtr]::Zero)) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Production Forge did not reach its exact-artifact capability and final-atlas markers before timeout.' }

    if ($AllowControlledTermination.IsPresent) {
        $expectedReloadCount = $ReloadCount + 1
        $controllerDeadline = [datetime]::UtcNow.AddSeconds(240)
        if ($controllerDeadline -gt $deadline) { $controllerDeadline = $deadline }
        $controllerComplete = $false
        while ([datetime]::UtcNow -lt $controllerDeadline) {
            [string] $logText = Get-LogText -Path $latestLog
            Assert-NoFatalLog -Text $logText -Context 'controller reload'
            $controllerReloadCount = Get-MarkerCount -Text $logText -Marker 'PackForge reload session:'
            $controllerHashCount = Get-MarkerCount -Text $logText -Marker 'PackForge resolved-resource hash:'
            $controllerComplete = $logText.IndexOf('PackForge runtime smoke complete:', [StringComparison]::OrdinalIgnoreCase) -ge 0
            if ($controllerReloadCount -ge $expectedReloadCount -and
                $controllerHashCount -ge $expectedReloadCount -and $controllerComplete) {
                break
            }
            if ($process.HasExited) { break }
            Start-Sleep -Seconds 2
        }
        [string] $logText = Get-LogText -Path $latestLog
        $controllerReloadCount = Get-MarkerCount -Text $logText -Marker 'PackForge reload session:'
        $controllerHashCount = Get-MarkerCount -Text $logText -Marker 'PackForge resolved-resource hash:'
        $controllerComplete = $logText.IndexOf('PackForge runtime smoke complete:', [StringComparison]::OrdinalIgnoreCase) -ge 0
        if ($controllerReloadCount -lt $expectedReloadCount -or
            $controllerHashCount -lt $expectedReloadCount -or -not $controllerComplete) {
            throw "Runtime smoke controller did not complete: reloads=$controllerReloadCount/$expectedReloadCount hashes=$controllerHashCount/$expectedReloadCount complete=$controllerComplete."
        }

        if (-not $process.WaitForExit(90000)) { throw 'Production Forge did not exit after the runtime smoke controller completed.' }
        if ($process.ExitCode -ne 0) { throw "Production Forge controller exit code was $($process.ExitCode)." }
        $cleanExit = $true
        $controlledTermination = $true
    } elseif ($ReloadCount -gt 0) {
        for ($reload = 1; $reload -le $ReloadCount; $reload++) {
            [string] $before = Get-LogText -Path $latestLog
            $beforeCount = @($reloadMarkers | ForEach-Object { Get-MarkerCount -Text $before -Marker $_ } | Measure-Object -Sum).Sum
            if (-not [PackForgeProductionSmokeNative]::SendReload($window)) { throw "Could not send F3+T for reload $reload." }
            $reloadDeadline = [datetime]::UtcNow.AddSeconds(180)
            if ($reloadDeadline -gt $deadline) { $reloadDeadline = $deadline }
            $reloaded = $false
            while ([datetime]::UtcNow -lt $reloadDeadline) {
                [string] $logText = Get-LogText -Path $latestLog
                Assert-NoFatalLog -Text $logText -Context "production reload $reload"
                $currentCount = @($reloadMarkers | ForEach-Object { Get-MarkerCount -Text $logText -Marker $_ } | Measure-Object -Sum).Sum
                if ($currentCount -gt $beforeCount) {
                    $reloaded = $true
                    break
                }
                if ($process.HasExited) { throw "Production Forge exited during reload $reload with code $($process.ExitCode)." }
                Start-Sleep -Seconds 2
            }
            if (-not $reloaded) { throw "Production reload $reload did not complete before timeout." }
        }
    }

    if ($AllowControlledTermination.IsPresent) {
        # The controller requested a clean client stop and the process was verified above.
    } elseif ($ReloadCount -eq 0) {
        Stop-Process -Id $process.Id -Force
        [void] $process.WaitForExit(30000)
        $controlledTermination = $true
    } else {
        if (-not [PackForgeProductionSmokeNative]::Close($window)) { throw 'Could not request a clean Minecraft window close.' }
        if (-not $process.WaitForExit(90000)) { throw 'Production Forge did not exit after its window was closed.' }
        if ($process.ExitCode -ne 0) { throw "Production Forge clean-close exit code was $($process.ExitCode)." }
        $cleanExit = $true
    }
    $passed = $true
} finally {
    if ($started) {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        if ($null -ne $stdoutTask) { Set-Content -LiteralPath $stdoutPath -Value $stdoutTask.GetAwaiter().GetResult() -Encoding utf8 }
        if ($null -ne $stderrTask) { Set-Content -LiteralPath $stderrPath -Value $stderrTask.GetAwaiter().GetResult() -Encoding utf8 }
        $process.Dispose()
    }
}

if (-not $passed) { throw "Production $loaderDisplay smoke failed." }
$finalText = Get-RunText -GameRoot $gameRoot -Paths @($latestLog, $stdoutPath, $stderrPath)
Assert-NoFatalLog -Text $finalText -Context 'production shutdown'
Assert-ProfileLogMarkers -Text $finalText -Context 'production shutdown' -Expected $ExpectedLogMarkers -Forbidden $ForbiddenLogMarkers
if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    $runtimeEvidencePath = Join-Path $gameRoot 'compatibility-runtime-evidence.log'
    Write-Utf8NoBom -Path $runtimeEvidencePath -Contents $finalText
    $provenance.runtimeEvidence = [ordered]@{
        logPath = $runtimeEvidencePath
        sha256 = (Get-FileHash -LiteralPath $runtimeEvidencePath -Algorithm SHA256).Hash.ToUpperInvariant()
    }
    Write-Utf8NoBom -Path $provenancePath -Contents ($provenance | ConvertTo-Json -Depth 8)
}
Write-Output "PASS $loaderDisplay production smoke: version=$VersionName artifact=$artifactName sha256=$sourceHash additionalMods=$($stagedAdditionalMods.Count) reloads=$ReloadCount cleanExit=$($cleanExit.ToString().ToLowerInvariant()) controlledTermination=$($controlledTermination.ToString().ToLowerInvariant()) run=$gameRoot provenance=$provenancePath"
