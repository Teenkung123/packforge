[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $FabricClientRoot,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+\-]*$')]
    [string] $VersionName,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+\-]*$')]
    [string] $MinecraftVersion,

    [Parameter(Mandatory = $true)]
    [string] $ArtifactPath,

    [Parameter(Mandatory = $true)]
    [string] $AssetsRoot,

    [Parameter(Mandatory = $true)]
    [string] $NativesRoot,

    [Parameter(Mandatory = $true)]
    [string] $JavaPath,

    [string] $ResourcePackPath,

    [string[]] $AdditionalModPaths,

    [string[]] $ExpectedLogMarkers,

    [string[]] $ForbiddenLogMarkers,

    [string] $CompatibilityProfilePath,

    [switch] $ValidateCompatibilityProfileOnly,

    [string] $ExpectedProfileTarget,

    [string] $ExpectedProfileMinecraftVersion,

    [string] $FallbackLibrariesRoot,

    [ValidateRange(60, 86400)]
    [int] $TimeoutSeconds = 900,

    [ValidateRange(0, 100)]
    [int] $ReloadCount = 10,

    [ValidateSet('repeat', 'cancel-in-flight', 'forced-resource-failure', 'retry-success', 'retry-exhaustion')]
    [string] $RuntimeSmokeScenario = 'repeat',

    [switch] $AllowControlledTermination
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$compatibilityConfigHelperPath = Join-Path $PSScriptRoot 'CompatibilityProfileConfig.ps1'
if (-not (Test-Path -LiteralPath $compatibilityConfigHelperPath -PathType Leaf)) {
    throw "Compatibility profile config helper is missing: $compatibilityConfigHelperPath"
}
. $compatibilityConfigHelperPath

$runtimeResourceHashHelperPath = Join-Path $PSScriptRoot 'RuntimeResourceHashEvidence.ps1'
if (-not (Test-Path -LiteralPath $runtimeResourceHashHelperPath -PathType Leaf)) {
    throw "Runtime resource hash evidence helper is missing: $runtimeResourceHashHelperPath"
}
. $runtimeResourceHashHelperPath

$heavyFixtureEvidenceHelperPath = Join-Path $PSScriptRoot 'HeavyFixtureEvidence.ps1'
if (-not (Test-Path -LiteralPath $heavyFixtureEvidenceHelperPath -PathType Leaf)) {
    throw "Heavy fixture evidence helper is missing: $heavyFixtureEvidenceHelperPath"
}
. $heavyFixtureEvidenceHelperPath

$runtimeSmokeScenarioEvidenceHelperPath = Join-Path $PSScriptRoot 'RuntimeSmokeScenarioEvidence.ps1'
if (-not (Test-Path -LiteralPath $runtimeSmokeScenarioEvidenceHelperPath -PathType Leaf)) {
    throw "Runtime smoke scenario evidence helper is missing: $runtimeSmokeScenarioEvidenceHelperPath"
}
. $runtimeSmokeScenarioEvidenceHelperPath

if (-not ('PackForgeFabricProductionSmokeNative' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class PackForgeFabricProductionSmokeNative
{
    private const int SwRestore = 9;
    private const uint KeyUp = 0x0002;
    private const uint WmClose = 0x0010;

    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
    [DllImport("user32.dll")]
    private static extern bool IsWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int maxCount);
    [DllImport("user32.dll")]
    private static extern int GetWindowTextLength(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int command);
    [DllImport("user32.dll")]
    private static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    private static extern bool PostMessage(IntPtr hWnd, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")]
    private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);
    [DllImport("user32.dll")]
    private static extern uint SendInput(uint inputCount, Input[] inputs, int inputSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort virtualKey;
        public ushort scanCode;
        public uint flags;
        public uint time;
        public UIntPtr extraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint type;
        public KeyboardInput keyboard;
    }

    public static IntPtr FindMinecraftWindow(int processId)
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

    public static bool IsVisibleAndValid(IntPtr handle)
    {
        return handle != IntPtr.Zero && IsWindow(handle) && IsWindowVisible(handle);
    }

    public static bool Activate(IntPtr handle)
    {
        if (!IsVisibleAndValid(handle)) return false;
        ShowWindow(handle, SwRestore);
        BringWindowToTop(handle);
        for (int attempt = 0; attempt < 6; attempt++)
        {
            if (GetForegroundWindow() == handle || SetForegroundWindow(handle) && GetForegroundWindow() == handle) return true;
            Thread.Sleep(150);
        }
        return GetForegroundWindow() == handle;
    }

    public static bool SendF3T(IntPtr handle)
    {
        if (!Activate(handle)) return false;
        Thread.Sleep(150);
        var inputs = new[]
        {
            new Input { type = 1, keyboard = new KeyboardInput { virtualKey = 0x72 } },
            new Input { type = 1, keyboard = new KeyboardInput { virtualKey = 0x54 } },
            new Input { type = 1, keyboard = new KeyboardInput { virtualKey = 0x54, flags = KeyUp } },
            new Input { type = 1, keyboard = new KeyboardInput { virtualKey = 0x72, flags = KeyUp } }
        };
        if (SendInput((uint) inputs.Length, inputs, Marshal.SizeOf(typeof(Input))) == inputs.Length) return true;

        keybd_event(0x72, 0, 0, UIntPtr.Zero);
        try
        {
            Thread.Sleep(75);
            keybd_event(0x54, 0, 0, UIntPtr.Zero);
            try { Thread.Sleep(75); }
            finally { keybd_event(0x54, 0, KeyUp, UIntPtr.Zero); }
        }
        finally { keybd_event(0x72, 0, KeyUp, UIntPtr.Zero); }
        return true;
    }

    public static bool Close(IntPtr handle)
    {
        return IsVisibleAndValid(handle) && PostMessage(handle, WmClose, IntPtr.Zero, IntPtr.Zero);
    }
}
'@
}

function Get-ObjectProperty {
    param(
        $Object,
        [string] $Name
    )

    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Resolve-RequiredPath {
    param(
        [string] $Path,
        [string] $Description,
        [switch] $Directory
    )

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
    $configContract = Get-Schema2ConfigContract -Profile $Profile
    [void] (Get-CompatibilityProfileStrings -Profile $Profile -Name 'expectedLogMarkers')
    [void] (Get-CompatibilityProfileStrings -Profile $Profile -Name 'forbiddenLogMarkers')
    return $configContract
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
$compatibilityProfileConfigContract = $null
if ($null -ne $compatibilityProfile) {
    if ([int] $compatibilityProfile.schema -eq 2) {
        $compatibilityProfileConfigContract = Assert-Schema2CompatibilityProfile -Profile $compatibilityProfile -ExpectedLoader 'fabric'
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
        loader = 'fabric'
        modIds = if ([int] $compatibilityProfile.schema -eq 2) { [string[]] @($compatibilityProfile.modIds) } else { [string[]] @() }
        additionalModPaths = [string[]] @($AdditionalModPaths)
        fixturePath = $ResourcePackPath
        expectedLogMarkers = [string[]] @($ExpectedLogMarkers)
        forbiddenLogMarkers = [string[]] @($ForbiddenLogMarkers)
        featureOverrides = if ([int] $compatibilityProfile.schema -eq 2) { $compatibilityProfileConfigContract.Overrides } else { $null }
        effectiveConfig = if ([int] $compatibilityProfile.schema -eq 2) { $compatibilityProfileConfigContract.Effective } else { $null }
        configSha256 = if ([int] $compatibilityProfile.schema -eq 2) { $compatibilityProfileConfigContract.Sha256 } else { $null }
    }
    Write-Output ("PROFILE_TRANSPORT " + ($transport | ConvertTo-Json -Compress -Depth 6))
    return
}

$scenarioMode = $RuntimeSmokeScenario -ne 'repeat'
if ($scenarioMode -and -not $AllowControlledTermination.IsPresent) {
    throw 'Non-repeat runtime smoke scenarios require -AllowControlledTermination.'
}

$heavyFixtureId = if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    [string] $compatibilityProfile.fixture.id
} else {
    $null
}
$heavyFixtureProfile = Test-PackForgeHeavyFixtureId -FixtureId $heavyFixtureId

function Resolve-SafeRelativePath {
    param(
        [string] $Root,
        [string] $RelativePath,
        [string] $Description,
        [switch] $Directory
    )

    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        throw "$Description has an empty relative path."
    }

    $relative = $RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
    if ([IO.Path]::IsPathRooted($relative)) {
        throw "$Description must be relative to its metadata root: $RelativePath"
    }

    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $candidate = [IO.Path]::GetFullPath((Join-Path $Root $relative))
    if (-not $candidate.StartsWith($rootFull, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description escapes its metadata root: $RelativePath"
    }

    $pathType = if ($Directory) { 'Container' } else { 'Leaf' }
    if (-not (Test-Path -LiteralPath $candidate -PathType $pathType)) {
        throw "$Description is missing: $candidate"
    }
    return $candidate
}

function Resolve-SafeCandidatePath {
    param(
        [string] $Root,
        [string] $RelativePath,
        [string] $Description
    )

    $relative = $RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
    if ([IO.Path]::IsPathRooted($relative)) {
        throw "$Description must be relative to its metadata root: $RelativePath"
    }
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $candidate = [IO.Path]::GetFullPath((Join-Path $Root $relative))
    if (-not $candidate.StartsWith($rootFull, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description escapes its metadata root: $RelativePath"
    }
    return $candidate
}

function Get-ProcessArchitecture {
    $raw = [Environment]::GetEnvironmentVariable('PROCESSOR_ARCHITEW6432')
    if ([string]::IsNullOrWhiteSpace($raw)) {
        $raw = [Environment]::GetEnvironmentVariable('PROCESSOR_ARCHITECTURE')
    }

    switch ($raw.ToLowerInvariant()) {
        'amd64' { return 'x86_64' }
        'x86_64' { return 'x86_64' }
        'x86' { return 'x86' }
        'arm64' { return 'arm64' }
        'aarch64' { return 'arm64' }
        default { return $raw.ToLowerInvariant() }
    }
}

function Test-RuleSet {
    param(
        $Rules,
        [hashtable] $FeatureValues
    )

    if ($null -eq $Rules) { return $true }
    $rulesArray = @($Rules)
    if ($rulesArray.Count -eq 0) { return $true }

    $architecture = Get-ProcessArchitecture
    $osVersion = [Environment]::OSVersion.Version.ToString()
    $allowed = $false
    $matched = $false
    foreach ($rule in $rulesArray) {
        $matches = $true
        $os = Get-ObjectProperty -Object $rule -Name 'os'
        if ($null -ne $os) {
            $name = Get-ObjectProperty -Object $os -Name 'name'
            $arch = Get-ObjectProperty -Object $os -Name 'arch'
            $version = Get-ObjectProperty -Object $os -Name 'version'
            if ($null -ne $name -and [string] $name -ine 'windows') { $matches = $false }
            if ($null -ne $arch) {
                $requestedArchitecture = [string] $arch
                $architectureMatches = switch ($requestedArchitecture.ToLowerInvariant()) {
                    'amd64' { $architecture -ieq 'x86_64' }
                    'x86_64' { $architecture -ieq 'x86_64' }
                    'aarch64' { $architecture -ieq 'arm64' }
                    default { $architecture -ieq $requestedArchitecture }
                }
                if (-not $architectureMatches) { $matches = $false }
            }
            if ($null -ne $version -and $matches -and $osVersion -notmatch [string] $version) { $matches = $false }
        }

        $features = Get-ObjectProperty -Object $rule -Name 'features'
        if ($null -ne $features -and $matches) {
            foreach ($feature in $features.PSObject.Properties) {
                $actual = $false
                if ($FeatureValues.ContainsKey($feature.Name)) {
                    $actual = [bool] $FeatureValues[$feature.Name]
                }
                if ($null -eq $feature.Value) {
                    if ($actual) { $matches = $false }
                } elseif ($actual -ne [bool] $feature.Value) {
                    $matches = $false
                }
            }
        }

        if (-not $matches) { continue }
        $matched = $true
        $action = [string] (Get-ObjectProperty -Object $rule -Name 'action')
        if ($action -ieq 'allow') {
            $allowed = $true
        } elseif ($action -ieq 'disallow') {
            $allowed = $false
        } else {
            throw "Unsupported launcher rule action '$action'."
        }
    }

    if (-not $matched) { return $false }
    return $allowed
}

function Expand-LauncherArguments {
    param(
        $Arguments,
        [hashtable] $FeatureValues
    )

    $expanded = [Collections.Generic.List[string]]::new()
    foreach ($argument in @($Arguments)) {
        if ($argument -is [string]) {
            [void] $expanded.Add([string] $argument)
            continue
        }

        $rules = Get-ObjectProperty -Object $argument -Name 'rules'
        if (-not (Test-RuleSet -Rules $rules -FeatureValues $FeatureValues)) { continue }
        $value = Get-ObjectProperty -Object $argument -Name 'value'
        if ($null -eq $value) { continue }
        foreach ($part in @($value)) {
            [void] $expanded.Add([string] $part)
        }
    }
    return $expanded.ToArray()
}

function Get-LibraryRelativePath {
    param($Library)

    $downloads = Get-ObjectProperty -Object $Library -Name 'downloads'
    $artifact = Get-ObjectProperty -Object $downloads -Name 'artifact'
    $downloadPath = Get-ObjectProperty -Object $artifact -Name 'path'
    if (-not [string]::IsNullOrWhiteSpace([string] $downloadPath)) {
        return ([string] $downloadPath).Replace('/', [IO.Path]::DirectorySeparatorChar)
    }

    $coordinate = [string] (Get-ObjectProperty -Object $Library -Name 'name')
    if ([string]::IsNullOrWhiteSpace($coordinate)) {
        throw 'A Fabric version library has neither a download path nor a Maven coordinate.'
    }

    $extension = 'jar'
    $atIndex = $coordinate.LastIndexOf([char] 64)
    if ($atIndex -ge 0) {
        $extension = $coordinate.Substring($atIndex + 1)
        $coordinate = $coordinate.Substring(0, $atIndex)
    }

    $parts = $coordinate.Split(':')
    if ($parts.Count -lt 3) {
        throw "Unsupported Fabric library coordinate: $coordinate"
    }
    $group = $parts[0]
    $artifactName = $parts[1]
    $version = $parts[2]
    $classifier = if ($parts.Count -gt 3) { "-$($parts[3])" } else { '' }
    $fileName = "$artifactName-$version$classifier.$extension"
    return (Join-Path ($group.Replace('.', [IO.Path]::DirectorySeparatorChar)) (Join-Path $artifactName (Join-Path $version $fileName)))
}

function Get-LibraryPath {
    param(
        $Library,
        [string] $LibrariesRoot,
        [string] $FallbackRoot,
        [hashtable] $FeatureValues
    )

    $rules = Get-ObjectProperty -Object $Library -Name 'rules'
    if (-not (Test-RuleSet -Rules $rules -FeatureValues $FeatureValues)) { return $null }

    $includeInClasspath = Get-ObjectProperty -Object $Library -Name 'include_in_classpath'
    if ($null -ne $includeInClasspath -and -not [bool] $includeInClasspath) { return $null }

    $relativePath = Get-LibraryRelativePath -Library $Library
    $description = "Fabric library $([string] (Get-ObjectProperty -Object $Library -Name 'name'))"
    $primary = Resolve-SafeCandidatePath -Root $LibrariesRoot -RelativePath $relativePath -Description $description
    if (Test-Path -LiteralPath $primary -PathType Leaf) { return $primary }

    if (-not [string]::IsNullOrWhiteSpace($FallbackRoot)) {
        $fallback = Resolve-SafeCandidatePath -Root $FallbackRoot -RelativePath $relativePath -Description "$description fallback"
        if (Test-Path -LiteralPath $fallback -PathType Leaf) { return $fallback }
    }

    return $primary
}

function ConvertTo-WindowsCommandLineArgument {
    param([string] $Value)

    if ($null -eq $Value) { $Value = '' }
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }

    $builder = [Text.StringBuilder]::new()
    [void] $builder.Append('"')
    $backslashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') {
            $backslashes++
            continue
        }
        if ($character -eq '"') {
            [void] $builder.Append(('\' * (($backslashes * 2) + 1)))
            [void] $builder.Append('"')
        } else {
            if ($backslashes -gt 0) { [void] $builder.Append(('\' * $backslashes)) }
            [void] $builder.Append($character)
        }
        $backslashes = 0
    }
    if ($backslashes -gt 0) { [void] $builder.Append(('\' * ($backslashes * 2))) }
    [void] $builder.Append('"')
    return $builder.ToString()
}

function Get-NativeSubdirectory {
    param(
        [string] $Root,
        [string] $Name
    )

    $candidate = Join-Path $Root $Name
    if (Test-Path -LiteralPath $candidate -PathType Container) { return $candidate }
    return $Root
}

function Expand-LauncherToken {
    param(
        [string] $Value,
        [hashtable] $Replacements
    )

    foreach ($key in @($Replacements.Keys | Sort-Object Length -Descending)) {
        $Value = $Value.Replace([string] $key, [string] $Replacements[$key])
    }
    if ($Value -match '\$\{[^}]+\}') {
        throw "Unresolved launcher token in argument: $Value"
    }
    return $Value
}

function Get-LogText {
    param([string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    try {
        return [string] (Get-Content -LiteralPath $Path -Raw -ErrorAction Stop)
    } catch {
        return ''
    }
}

function Get-RunText {
    param(
        [string] $GameRoot,
        [string] $LatestLog,
        [string] $StdoutPath,
        [string] $StderrPath
    )

    $parts = [Collections.Generic.List[string]]::new()
    foreach ($path in @($LatestLog, $StdoutPath, $StderrPath)) {
        $text = Get-LogText -Path $path
        if ($null -ne $text -and $text.Length -gt 0) { [void] $parts.Add([string] $text) }
    }
    $crashRoot = Join-Path $GameRoot 'crash-reports'
    if (Test-Path -LiteralPath $crashRoot -PathType Container) {
        foreach ($report in @(Get-ChildItem -LiteralPath $crashRoot -Filter '*.txt' -File -ErrorAction SilentlyContinue)) {
            $text = Get-LogText -Path $report.FullName
            if ($null -ne $text -and $text.Length -gt 0) { [void] $parts.Add([string] $text) }
        }
    }
    return [string]::Join([Environment]::NewLine, $parts)
}

function Assert-NoFatalLog {
    param(
        [string] $Text,
        [string] $Context
    )

    $fatalPattern = '(?im)(Critical injection failure|Mixin apply failed|MixinTransformerError|InvalidInjection(?:Exception|PointException)?|InjectionError|IllegalClassLoadError|NoClassDefFoundError|ExceptionInInitializerError|(?:^|\s)LinkageError:|Minecraft has crashed|A critical error occurred|---- Minecraft Crash Report ----|Shutdown failure!|(?:ERROR|FATAL)[^\r\n]{0,240}PackForge|PackForge[^\r\n]{0,240}(?:ERROR|Exception|FATAL))'
    $match = [regex]::Match($Text, $fatalPattern)
    if ($match.Success) {
        throw "Fatal Fabric/Mixin/PackForge marker found during ${Context}: $($match.Value)"
    }
}

function Get-LogMarkerCount {
    param(
        [string] $Text,
        [string] $Marker
    )

    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    return [regex]::Matches($Text, [regex]::Escape($Marker), [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
}

function Stop-OwnedProcessTree {
    param([Diagnostics.Process] $Process)

    if ($null -eq $Process) { return }
    try { $Process.Refresh() } catch { return }
    if ($Process.HasExited) { return }

    try {
        $systemRoot = [Environment]::GetEnvironmentVariable('SystemRoot')
        $taskKill = Join-Path $systemRoot 'System32\taskkill.exe'
        if (Test-Path -LiteralPath $taskKill -PathType Leaf) {
            $killer = Start-Process -FilePath $taskKill `
                -ArgumentList @('/PID', [string] $Process.Id, '/T', '/F') `
                -WindowStyle Hidden -PassThru -Wait
            $killer.Dispose()
        }
    } catch {
        try { Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue } catch { }
    }

    try { [void] $Process.WaitForExit(30000) } catch { }
    try {
        $Process.Refresh()
        if (-not $Process.HasExited) { Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue }
    } catch { }
}

function Write-Utf8NoBom {
    param(
        [string] $Path,
        [string] $Contents
    )

    $encoding = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText($Path, $Contents, $encoding)
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

function Get-LibraryIdentity {
    param($Library)

    $coordinate = [string] (Get-ObjectProperty -Object $Library -Name 'name')
    if ([string]::IsNullOrWhiteSpace($coordinate)) {
        return "path:$((Get-LibraryRelativePath -Library $Library).ToLowerInvariant())"
    }

    $extension = 'jar'
    $atIndex = $coordinate.LastIndexOf([char] 64)
    if ($atIndex -ge 0) {
        $extension = $coordinate.Substring($atIndex + 1)
        $coordinate = $coordinate.Substring(0, $atIndex)
    }

    $parts = $coordinate.Split(':')
    if ($parts.Count -lt 3) {
        throw "Unsupported Fabric library coordinate: $coordinate"
    }
    $classifier = if ($parts.Count -gt 3) { $parts[3] } else { '' }
    return '{0}:{1}:{2}@{3}' -f $parts[0], $parts[1], $classifier, $extension
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

$clientRoot = Resolve-RequiredPath -Path $FabricClientRoot -Description 'Fabric client metadata root' -Directory
$librariesRoot = Resolve-RequiredPath -Path (Join-Path $clientRoot 'libraries') -Description 'Fabric libraries root' -Directory
$assets = Resolve-RequiredPath -Path $AssetsRoot -Description 'Minecraft assets root' -Directory
$natives = Resolve-RequiredPath -Path $NativesRoot -Description 'Minecraft natives root' -Directory
$java = Resolve-RequiredPath -Path $JavaPath -Description 'Java executable'
$artifact = Resolve-RequiredPath -Path $ArtifactPath -Description 'PackForge Fabric production artifact'

if ([IO.Path]::GetPathRoot($clientRoot).TrimEnd('\') -eq $clientRoot.TrimEnd('\')) {
    throw 'FabricClientRoot must not be a drive root.'
}
if ($VersionName -notmatch '^[A-Za-z0-9][A-Za-z0-9._+\-]*$') {
    throw "Unexpected Fabric version name: $VersionName"
}
if ($MinecraftVersion -notmatch '^[A-Za-z0-9][A-Za-z0-9._+\-]*$') {
    throw "Unexpected Minecraft version: $MinecraftVersion"
}
if ($VersionName -ne $MinecraftVersion -and -not $VersionName.StartsWith("$MinecraftVersion-", [StringComparison]::OrdinalIgnoreCase)) {
    throw "Fabric version '$VersionName' is not based on Minecraft '$MinecraftVersion'."
}

$fallbackLibraries = $null
if (-not [string]::IsNullOrWhiteSpace($FallbackLibrariesRoot)) {
    $fallbackLibraries = Resolve-RequiredPath -Path $FallbackLibrariesRoot -Description 'Fallback Fabric libraries root' -Directory
}

$artifactName = [IO.Path]::GetFileName($artifact)
$artifactMatch = [regex]::Match($artifactName, '^packforge-fabric-[0-9A-Za-z.+_\-]+-mc(?<minecraft>[0-9A-Za-z.+_\-]+)\.jar$')
if (-not $artifactMatch.Success) {
    throw "Artifact is not a final PackForge Fabric JAR: $artifactName"
}
$artifactMinecraft = [string] $artifactMatch.Groups['minecraft'].Value
if (-not (Test-ArtifactMinecraftCoverage -ArtifactMinecraft $artifactMinecraft -MinecraftVersion $MinecraftVersion)) {
    throw "Artifact Minecraft segment '$artifactMinecraft' does not cover '$MinecraftVersion'."
}
$targetMarker = Get-ArtifactTargetMarker -ArtifactPath $artifact
if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    Assert-Schema2RuntimeCell -Profile $compatibilityProfile -ExpectedMinecraftVersion $MinecraftVersion -ExpectedTarget $targetMarker
}

$versionDirectory = Resolve-SafeRelativePath `
    -Root $clientRoot `
    -RelativePath (Join-Path 'versions' $VersionName) `
    -Description 'Fabric version directory' `
    -Directory
$versionJsonPath = Resolve-SafeRelativePath `
    -Root $versionDirectory `
    -RelativePath "$VersionName.json" `
    -Description 'Fabric version metadata'

try {
    $metadata = Get-Content -LiteralPath $versionJsonPath -Raw | ConvertFrom-Json
} catch {
    throw "Could not parse Fabric version metadata '$versionJsonPath': $($_.Exception.Message)"
}
if ([string] (Get-ObjectProperty -Object $metadata -Name 'id') -ne $VersionName) {
    throw "Fabric metadata identity mismatch in $versionJsonPath"
}

$clientDownload = Get-ObjectProperty -Object (Get-ObjectProperty -Object $metadata -Name 'downloads') -Name 'client'
$clientRelativePath = Get-ObjectProperty -Object $clientDownload -Name 'path'
if ([string]::IsNullOrWhiteSpace([string] $clientRelativePath)) {
    $clientRelativePath = "$VersionName.jar"
}
$clientJar = Resolve-SafeRelativePath `
    -Root $versionDirectory `
    -RelativePath ([string] $clientRelativePath) `
    -Description 'Minecraft client JAR'
$expectedClientSize = Get-ObjectProperty -Object $clientDownload -Name 'size'
if ($null -ne $expectedClientSize -and [int64] $expectedClientSize -ne (Get-Item -LiteralPath $clientJar).Length) {
    throw "Minecraft client JAR size mismatch: expected $expectedClientSize, actual $((Get-Item -LiteralPath $clientJar).Length)."
}

$assetIndex = Get-ObjectProperty -Object $metadata -Name 'assetIndex'
$assetIndexId = [string] (Get-ObjectProperty -Object $assetIndex -Name 'id')
if ([string]::IsNullOrWhiteSpace($assetIndexId)) {
    $assetIndexId = [string] (Get-ObjectProperty -Object $metadata -Name 'assets')
}
if ([string]::IsNullOrWhiteSpace($assetIndexId) -or $assetIndexId -notmatch '^[A-Za-z0-9._+\-]+$') {
    throw "Fabric metadata does not provide a usable asset index id."
}
[void] (Resolve-SafeRelativePath -Root $assets -RelativePath (Join-Path 'indexes' "$assetIndexId.json") -Description 'Minecraft asset index')

$logging = Get-ObjectProperty -Object $metadata -Name 'logging'
$clientLogging = Get-ObjectProperty -Object $logging -Name 'client'
$logConfigPath = $null
if ($null -ne $clientLogging) {
    $logConfigFile = Get-ObjectProperty -Object (Get-ObjectProperty -Object $clientLogging -Name 'file') -Name 'id'
    if ([string]::IsNullOrWhiteSpace([string] $logConfigFile) -or [IO.Path]::GetFileName([string] $logConfigFile) -ne [string] $logConfigFile) {
        throw "Fabric logging metadata has an invalid client log configuration id."
    }
    $logConfigCandidates = @(
        (Join-Path $clientRoot (Join-Path 'log_configs' ([string] $logConfigFile))),
        (Join-Path (Split-Path -Parent $assets) (Join-Path 'log_configs' ([string] $logConfigFile)))
    )
    foreach ($candidate in $logConfigCandidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $logConfigPath = [IO.Path]::GetFullPath($candidate)
            break
        }
    }
    if ($null -eq $logConfigPath) {
        throw "Fabric client log configuration is missing: $logConfigFile"
    }
}

$featureValues = @{
    has_custom_resolution = $true
    is_demo_user = $false
    has_quick_plays_support = $false
    is_quick_play_singleplayer = $false
    is_quick_play_multiplayer = $false
    is_quick_play_realms = $false
}

$effectiveLibraries = [Collections.Generic.List[object]]::new()
$effectiveLibraryIndexes = [Collections.Generic.Dictionary[string, int]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($library in @(Get-ObjectProperty -Object $metadata -Name 'libraries')) {
    $rules = Get-ObjectProperty -Object $library -Name 'rules'
    if (-not (Test-RuleSet -Rules $rules -FeatureValues $featureValues)) { continue }
    $includeInClasspath = Get-ObjectProperty -Object $library -Name 'include_in_classpath'
    if ($null -ne $includeInClasspath -and -not [bool] $includeInClasspath) { continue }

    $identity = Get-LibraryIdentity -Library $library
    if ($effectiveLibraryIndexes.ContainsKey($identity)) {
        $effectiveLibraries[$effectiveLibraryIndexes[$identity]] = $library
    } else {
        $effectiveLibraryIndexes[$identity] = $effectiveLibraries.Count
        [void] $effectiveLibraries.Add($library)
    }
}

$classpath = [Collections.Generic.List[string]]::new()
$seenLibraries = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($library in $effectiveLibraries) {
    $libraryPath = Get-LibraryPath `
        -Library $library `
        -LibrariesRoot $librariesRoot `
        -FallbackRoot $fallbackLibraries `
        -FeatureValues $featureValues
    if ($null -eq $libraryPath) { continue }

    $libraryPath = Resolve-RequiredPath -Path $libraryPath -Description "Fabric library $([string] (Get-ObjectProperty -Object $library -Name 'name'))"
    if ($seenLibraries.Add([IO.Path]::GetFullPath($libraryPath))) {
        [void] $classpath.Add($libraryPath)
    }
}
[void] $classpath.Add($clientJar)
$classpathText = [string]::Join([IO.Path]::PathSeparator, $classpath)

$runId = [datetime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$runRoot = $null
$runBase = $null
$runCandidates = @(
    (Join-Path 'C:\tmp' 'PackForge-Fabric-Production'),
    (Join-Path $clientRoot 'packforge-smoke')
)
foreach ($candidateBase in $runCandidates) {
    try {
        New-Item -ItemType Directory -Path $candidateBase -Force | Out-Null
        $candidateRoot = Join-Path $candidateBase (Join-Path "$MinecraftVersion-$VersionName" $runId)
        New-Item -ItemType Directory -Path $candidateRoot -Force | Out-Null
        $runBase = [IO.Path]::GetFullPath($candidateBase)
        $runRoot = [IO.Path]::GetFullPath($candidateRoot)
        break
    } catch {
        continue
    }
}
if ($null -eq $runRoot) {
    throw 'Could not create an isolated production smoke directory under C:\tmp or FabricClientRoot.'
}

$modsRoot = Join-Path $runRoot 'mods'
$configRoot = Join-Path $runRoot 'config'
$logsRoot = Join-Path $runRoot 'logs'
$tempRoot = Join-Path $runRoot 'tmp'
$homeRoot = Join-Path $runRoot 'home'
New-Item -ItemType Directory -Path $modsRoot, $configRoot, $logsRoot, $tempRoot, $homeRoot -Force | Out-Null

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

$provenancePath = Join-Path $runRoot 'artifact-provenance.json'
$provenance = [ordered]@{
    artifact = $artifactName
    sourcePath = $artifact
    stagedPath = $stagedArtifact
    sha256 = $sourceHash
    additionalMods = @($stagedAdditionalMods)
    loader = 'fabric'
    minecraftVersion = $MinecraftVersion
    fabricVersion = $VersionName
    target = $targetMarker
}

$configPath = Join-Path $configRoot 'packforge.json'
$profileConfig = if ($null -ne $compatibilityProfileConfigContract) {
    $compatibilityProfileConfigContract.Effective
} else {
    [ordered]@{
        configVersion = 12
        reloadOptimizerEnabled = $true
        loaderIndexEnabled = $true
        loaderTimingsEnabled = $true
        reloadListenerTimingsEnabled = $false
        startupTimingsEnabled = $true
        startupStatusOverlayEnabled = $false
    }
}
Write-Utf8NoBom -Path $configPath -Contents ($profileConfig | ConvertTo-Json)

if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    $resourcePackSource = Resolve-RequiredPath -Path $ResourcePackPath -Description 'Schema-2 compatibility resource-pack fixture'
    $resourcePackRoot = Join-Path $runRoot 'resourcepacks'
    New-Item -ItemType Directory -Path $resourcePackRoot -Force | Out-Null
    $stagedFixture = Join-Path $resourcePackRoot ([string] $compatibilityProfile.fixture.artifact)
    Copy-Item -LiteralPath $resourcePackSource -Destination $stagedFixture -Force
    $stagedFixtureHash = (Get-FileHash -LiteralPath $stagedFixture -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($stagedFixtureHash -cne [string] $compatibilityProfile.fixture.sha256) {
        throw "Staged schema-2 fixture SHA-256 mismatch: expected=$($compatibilityProfile.fixture.sha256) actual=$stagedFixtureHash"
    }
    Write-Utf8NoBom -Path (Join-Path $runRoot 'options.txt') -Contents @"
resourcePacks:["vanilla","file/$([string] $compatibilityProfile.fixture.artifact)"]
incompatibleResourcePacks:[]
"@
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
        overrides = $compatibilityProfileConfigContract.Overrides
    }
} elseif ($AllowControlledTermination.IsPresent) {
    $resourcePackSource = Resolve-RequiredPath -Path $ResourcePackPath -Description 'Deterministic production resource-pack fixture'
    $resourcePackRoot = Join-Path $runRoot 'resourcepacks'
    New-Item -ItemType Directory -Path $resourcePackRoot -Force | Out-Null
    $stagedFixture = Join-Path $resourcePackRoot 'deterministic-large-pack.zip'
    Copy-Item -LiteralPath $resourcePackSource -Destination $stagedFixture -Force
    $stagedFixtureHash = (Get-FileHash -LiteralPath $stagedFixture -Algorithm SHA256).Hash.ToUpperInvariant()
    Write-Utf8NoBom -Path (Join-Path $runRoot 'options.txt') -Contents @(
        'resourcePacks:["vanilla","file/deterministic-large-pack.zip"]'
        'incompatibleResourcePacks:[]'
    )
    $provenance.fixture = [ordered]@{
        id = 'deterministic-large-pack'
        sourcePath = $resourcePackSource
        stagedPath = $stagedFixture
        sha256 = $stagedFixtureHash
    }
}
Write-Utf8NoBom -Path $provenancePath -Contents ($provenance | ConvertTo-Json -Depth 8)

$nativeJavaPath = Get-NativeSubdirectory -Root $natives -Name 'java'
$nativeJnaPath = Get-NativeSubdirectory -Root $natives -Name 'jna'
$nativeLwjglPath = Get-NativeSubdirectory -Root $natives -Name 'lwjgl'
$nativeNettyPath = Get-NativeSubdirectory -Root $natives -Name 'netty'
$replacements = @{
    '${natives_directory}/java' = $nativeJavaPath
    '${natives_directory}/jna' = $nativeJnaPath
    '${natives_directory}/lwjgl' = $nativeLwjglPath
    '${natives_directory}/netty' = $nativeNettyPath
    '${auth_player_name}' = 'PackForgeProductionSmoke'
    '${version_name}' = $VersionName
    '${game_directory}' = $runRoot
    '${assets_root}' = $assets
    '${assets_index_name}' = $assetIndexId
    '${auth_uuid}' = '00000000000000000000000000000001'
    '${auth_access_token}' = '0'
    '${clientid}' = '0'
    '${auth_xuid}' = '0'
    '${user_type}' = 'legacy'
    '${version_type}' = 'release'
    '${natives_directory}' = $natives
    '${launcher_name}' = 'PackForgeFabricProductionSmoke'
    '${launcher_version}' = '1'
    '${classpath}' = $classpathText
    '${classpath_separator}' = [string] [IO.Path]::PathSeparator
    '${library_directory}' = $librariesRoot
    '${resolution_width}' = '1280'
    '${resolution_height}' = '720'
    '${quickPlayPath}' = (Join-Path $runRoot 'quickPlay.json')
    '${quickPlaySingleplayer}' = ''
    '${quickPlayMultiplayer}' = ''
    '${quickPlayRealms}' = ''
}
if ($null -ne $logConfigPath) {
    $replacements['${path}'] = $logConfigPath
}

$javaArguments = [Collections.Generic.List[string]]::new()
[void] $javaArguments.Add('-Xms512m')
[void] $javaArguments.Add('-Xmx2048m')
[void] $javaArguments.Add("-Djava.io.tmpdir=$tempRoot")
[void] $javaArguments.Add("-Duser.home=$homeRoot")
$controllerMode = $AllowControlledTermination.IsPresent
$requiresHeavyFixtureEvidence = $controllerMode -and $heavyFixtureProfile
$requiresRuntimeScenarioEvidence = $controllerMode -and $scenarioMode
if ($controllerMode) {
    [void] $javaArguments.Add("-Dpackforge.runtimeSmokeReloadCount=$ReloadCount")
    [void] $javaArguments.Add("-Dpackforge.runtimeSmokeScenario=$RuntimeSmokeScenario")
}

$metadataJvmArguments = @(Expand-LauncherArguments `
    -Arguments (Get-ObjectProperty -Object $metadata -Name 'arguments' | ForEach-Object { Get-ObjectProperty -Object $_ -Name 'jvm' }) `
    -FeatureValues $featureValues)
foreach ($argument in $metadataJvmArguments) {
    [void] $javaArguments.Add((Expand-LauncherToken -Value ([string] $argument) -Replacements $replacements))
}

if ($null -ne $clientLogging) {
    $loggingArgument = [string] (Get-ObjectProperty -Object $clientLogging -Name 'argument')
    if (-not [string]::IsNullOrWhiteSpace($loggingArgument)) {
        [void] $javaArguments.Add((Expand-LauncherToken -Value $loggingArgument -Replacements $replacements))
    }
}

$hasClasspathArgument = $false
foreach ($argument in $javaArguments) {
    if ($argument -ieq '-cp' -or $argument -ieq '-classpath') {
        $hasClasspathArgument = $true
        break
    }
}
if (-not $hasClasspathArgument) {
    [void] $javaArguments.Add('-cp')
    [void] $javaArguments.Add($classpathText)
}

$mainClass = [string] (Get-ObjectProperty -Object $metadata -Name 'mainClass')
if ([string]::IsNullOrWhiteSpace($mainClass)) { throw 'Fabric version metadata has no mainClass.' }
[void] $javaArguments.Add($mainClass)

$gameArguments = [Collections.Generic.List[string]]::new()
$metadataGameArguments = @(Expand-LauncherArguments `
    -Arguments (Get-ObjectProperty -Object $metadata -Name 'arguments' | ForEach-Object { Get-ObjectProperty -Object $_ -Name 'game' }) `
    -FeatureValues $featureValues)
foreach ($argument in $metadataGameArguments) {
    [void] $gameArguments.Add((Expand-LauncherToken -Value ([string] $argument) -Replacements $replacements))
}
if (-not ($gameArguments -contains '--width')) {
    [void] $gameArguments.Add('--width')
    [void] $gameArguments.Add('1280')
}
if (-not ($gameArguments -contains '--height')) {
    [void] $gameArguments.Add('--height')
    [void] $gameArguments.Add('720')
}

$javaArguments.AddRange($gameArguments)
$stdoutPath = Join-Path $logsRoot 'launcher.stdout.log'
$stderrPath = Join-Path $logsRoot 'launcher.stderr.log'
$latestLog = Join-Path $logsRoot 'latest.log'
$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $java
$startInfo.WorkingDirectory = $runRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.Arguments = [string]::Join(' ', @($javaArguments | ForEach-Object { ConvertTo-WindowsCommandLineArgument -Value ([string] $_) }))
foreach ($environmentOption in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')) {
    if ($startInfo.EnvironmentVariables.ContainsKey($environmentOption)) {
        $startInfo.EnvironmentVariables[$environmentOption] = ''
    }
}
if ($controllerMode) {
    $startInfo.EnvironmentVariables['PACKFORGE_RUNTIME_RESOURCE_HASH'] = 'true'
    if ($requiresHeavyFixtureEvidence) {
        $startInfo.EnvironmentVariables['PACKFORGE_RUNTIME_HEAVY_EVIDENCE'] = 'true'
    }
}
if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    $startInfo.EnvironmentVariables['PACKFORGE_COMPAT_PROFILE_ID'] = [string] $compatibilityProfile.profileId
    $startInfo.EnvironmentVariables['PACKFORGE_COMPAT_MOD_IDS'] = [string]::Join(',', @($compatibilityProfile.modIds))
}

$capabilityPattern = "PackForge capabilities:[^\r\n]*\btarget=$([regex]::Escape($targetMarker))\b"
$reloadMarker = 'PackForge reload complete:'
$artifactSourcePattern = "PackForge runtime source:[^\r\n]*$([regex]::Escape($artifactName))"
$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$stdoutTask = $null
$stderrTask = $null
$started = $false
$passed = $false
$cleanExit = $false
$controlledTermination = $false
$minecraftWindow = [IntPtr]::Zero
$deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)

try {
    $started = $process.Start()
    if (-not $started) { throw 'Java process did not start.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()

    $ready = $false
    while ([datetime]::UtcNow -lt $deadline) {
        $logText = Get-LogText -Path $latestLog
        Assert-NoFatalLog -Text $logText -Context 'Fabric production startup'
        $minecraftWindow = [PackForgeFabricProductionSmokeNative]::FindMinecraftWindow($process.Id)
        $hasCapabilities = $logText -match $capabilityPattern
        $hasReload = $logText.IndexOf($reloadMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0
        $hasArtifact = $logText -match $artifactSourcePattern
        $hasRuntimeReady = (-not $controllerMode) -or
            $logText.IndexOf('PackForge runtime smoke ready:', [StringComparison]::OrdinalIgnoreCase) -ge 0
        $hasResourceHash = (-not $controllerMode) -or @(Get-PackForgeResolvedResourceHashRecords -Text $logText).Count -ge 1
        $hasHeavyFixtureEvidence = (-not $requiresHeavyFixtureEvidence) -or @(Get-PackForgeHeavyFixtureEvidenceRecords -Text $logText).Count -ge 1
        if ($process.HasExited) {
            $process.Refresh()
            throw "Fabric production client exited before readiness with code $($process.ExitCode)."
        }
        if ($hasCapabilities -and $hasReload -and $hasArtifact -and $hasRuntimeReady -and $hasResourceHash -and $hasHeavyFixtureEvidence -and ($ReloadCount -eq 0 -or $minecraftWindow -ne [IntPtr]::Zero)) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "Fabric production client did not reach capability, reload, runtime-ready, exact-artifact, resolved-resource hash, and heavy-fixture markers before timeout: runtimeReady=$hasRuntimeReady resourceHash=$hasResourceHash heavyFixture=$hasHeavyFixtureEvidence."
    }
    if ($ReloadCount -gt 0 -and $minecraftWindow -eq [IntPtr]::Zero) {
        throw 'A visible Minecraft window is required for the requested F3+T reload validation.'
    }

    if ($controllerMode) {
        $beforeText = Get-LogText -Path $latestLog
        $expectedReloadCount = (Get-LogMarkerCount -Text $beforeText -Marker $reloadMarker) + $ReloadCount
        $expectedResourceHashCount = @(Get-PackForgeResolvedResourceHashRecords -Text $beforeText).Count + $ReloadCount
        $expectedHeavyFixtureEvidenceCount = if ($requiresHeavyFixtureEvidence) {
            @(Get-PackForgeHeavyFixtureEvidenceRecords -Text $beforeText).Count + $ReloadCount
        } else {
            0
        }
        $reloadDeadline = [datetime]::UtcNow.AddSeconds(300)
        if ($reloadDeadline -gt $deadline) { $reloadDeadline = $deadline }
        while ([datetime]::UtcNow -lt $reloadDeadline) {
            $reloadText = Get-LogText -Path $latestLog
            Assert-NoFatalLog -Text $reloadText -Context 'Fabric controlled reloads'
            if ($requiresRuntimeScenarioEvidence) {
                if (@(Get-PackForgeRuntimeSmokeScenarioPassRecords -Text $reloadText).Count -ge 1) { break }
                if ($process.HasExited) { break }
                Start-Sleep -Seconds 2
                continue
            }
            $currentReloadCount = Get-LogMarkerCount -Text $reloadText -Marker $reloadMarker
            $currentResourceHashCount = @(Get-PackForgeResolvedResourceHashRecords -Text $reloadText).Count
            $currentHeavyFixtureEvidenceCount = @(Get-PackForgeHeavyFixtureEvidenceRecords -Text $reloadText).Count
            if ($currentReloadCount -ge $expectedReloadCount -and $currentResourceHashCount -ge $expectedResourceHashCount -and $currentHeavyFixtureEvidenceCount -ge $expectedHeavyFixtureEvidenceCount) { break }
            if ($process.HasExited) {
                $process.Refresh()
                throw "Fabric production client exited during controlled reloads with code $($process.ExitCode)."
            }
            Start-Sleep -Seconds 2
        }
        $finalReloadText = Get-LogText -Path $latestLog
        $finalReloadCount = Get-LogMarkerCount -Text $finalReloadText -Marker $reloadMarker
        $finalResourceHashCount = @(Get-PackForgeResolvedResourceHashRecords -Text $finalReloadText).Count
        $finalHeavyFixtureEvidenceCount = @(Get-PackForgeHeavyFixtureEvidenceRecords -Text $finalReloadText).Count
        if (-not $requiresRuntimeScenarioEvidence -and ($finalReloadCount -lt $expectedReloadCount -or $finalResourceHashCount -lt $expectedResourceHashCount -or $finalHeavyFixtureEvidenceCount -lt $expectedHeavyFixtureEvidenceCount)) {
            throw "Fabric controlled reloads did not emit the required completion/hash/heavy-fixture markers before timeout: reloads=$finalReloadCount/$expectedReloadCount hashes=$finalResourceHashCount/$expectedResourceHashCount heavyFixture=$finalHeavyFixtureEvidenceCount/$expectedHeavyFixtureEvidenceCount."
        }
        if ($requiresRuntimeScenarioEvidence) {
            Resolve-PackForgeRuntimeSmokeScenarioEvidence `
                -Text $finalReloadText `
                -ExpectedScenario $RuntimeSmokeScenario `
                -Context 'Fabric runtime smoke scenario' | Out-Null
            if ($finalResourceHashCount -lt 2) {
                throw "Fabric runtime smoke scenario did not retain startup and recovery resource hashes: actual=$finalResourceHashCount required=2."
            }
            if ($requiresHeavyFixtureEvidence -and $finalHeavyFixtureEvidenceCount -lt 2) {
                throw "Fabric runtime smoke scenario did not retain startup and recovery heavy-fixture evidence: actual=$finalHeavyFixtureEvidenceCount required=2."
            }
        }
    } else {
        for ($reload = 1; $reload -le $ReloadCount; $reload++) {
            $minecraftWindow = [PackForgeFabricProductionSmokeNative]::FindMinecraftWindow($process.Id)
            if ($minecraftWindow -eq [IntPtr]::Zero) {
                throw "No visible Minecraft window was available for Fabric reload $reload."
            }
            $beforeText = Get-LogText -Path $latestLog
            $previousReloadCount = Get-LogMarkerCount -Text $beforeText -Marker $reloadMarker
            if (-not [PackForgeFabricProductionSmokeNative]::SendF3T($minecraftWindow)) {
                throw "Could not send F3+T for Fabric reload $reload."
            }

            $reloadDeadline = [datetime]::UtcNow.AddSeconds(180)
            if ($reloadDeadline -gt $deadline) { $reloadDeadline = $deadline }
            $reloadReady = $false
            while ([datetime]::UtcNow -lt $reloadDeadline) {
                $reloadText = Get-LogText -Path $latestLog
                Assert-NoFatalLog -Text $reloadText -Context "Fabric reload $reload"
                if ((Get-LogMarkerCount -Text $reloadText -Marker $reloadMarker) -gt $previousReloadCount) {
                    $reloadReady = $true
                    break
                }
                if ($process.HasExited) {
                    $process.Refresh()
                    throw "Fabric production client exited during reload $reload with code $($process.ExitCode)."
                }
                Start-Sleep -Seconds 2
            }
            if (-not $reloadReady) {
                throw "Fabric reload $reload did not emit a new completion marker before timeout."
            }
        }
    }

    $minecraftWindow = [PackForgeFabricProductionSmokeNative]::FindMinecraftWindow($process.Id)
    $process.Refresh()
    if ($process.HasExited) {
        if ($process.ExitCode -ne 0) {
            throw "Fabric production client exited after readiness with code $($process.ExitCode)."
        }
        $cleanExit = $true
    } elseif ([PackForgeFabricProductionSmokeNative]::IsVisibleAndValid($minecraftWindow)) {
        if (-not [PackForgeFabricProductionSmokeNative]::Close($minecraftWindow)) {
            if (-not $AllowControlledTermination) { throw 'Could not request a clean close for the Minecraft window.' }
            Stop-OwnedProcessTree -Process $process
            $controlledTermination = $true
        } else {
            $closeDeadline = [datetime]::UtcNow.AddSeconds(90)
            if ($closeDeadline -gt $deadline) { $closeDeadline = $deadline }
            while ([datetime]::UtcNow -lt $closeDeadline) {
                if ($process.HasExited -and -not [PackForgeFabricProductionSmokeNative]::IsVisibleAndValid($minecraftWindow)) { break }
                if ($process.HasExited -and [PackForgeFabricProductionSmokeNative]::IsVisibleAndValid($minecraftWindow)) {
                    throw 'Fabric client exited while its Minecraft window remained visible.'
                }
                Start-Sleep -Seconds 2
            }
            if ([PackForgeFabricProductionSmokeNative]::IsVisibleAndValid($minecraftWindow) -or -not $process.HasExited) {
                if (-not $AllowControlledTermination) { throw 'Fabric client did not complete a clean window close before timeout.' }
                Stop-OwnedProcessTree -Process $process
                $controlledTermination = $true
            } else {
                $process.Refresh()
                $process.WaitForExit()
                if ($process.ExitCode -ne 0) {
                    throw "Fabric client clean-close exit code was $($process.ExitCode)."
                }
                $cleanExit = $true
            }
        }
    } elseif ($ReloadCount -gt 0) {
        $exitGraceDeadline = [datetime]::UtcNow.AddSeconds(15)
        if ($exitGraceDeadline -gt $deadline) { $exitGraceDeadline = $deadline }
        while ([datetime]::UtcNow -lt $exitGraceDeadline) {
            $process.Refresh()
            if ($process.HasExited) { break }
            Start-Sleep -Milliseconds 500
        }
        $process.Refresh()
        if (-not $process.HasExited) {
            throw 'The Minecraft window disappeared after the requested reloads, but the client did not complete shutdown.'
        }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "Fabric client window disappeared during shutdown with exit code $($process.ExitCode)."
        }
        $cleanExit = $true
    } elseif ($AllowControlledTermination) {
        Stop-OwnedProcessTree -Process $process
        $controlledTermination = $true
    } else {
        throw 'No visible Minecraft window was available for clean shutdown; rerun with -AllowControlledTermination for marker-only startup smoke.'
    }

    $passed = $true
} finally {
    if ($started) {
        try {
            $process.Refresh()
            if (-not $process.HasExited) { Stop-OwnedProcessTree -Process $process }
        } catch { }

        try {
            if ($null -ne $stdoutTask) {
                Write-Utf8NoBom -Path $stdoutPath -Contents ([string] $stdoutTask.GetAwaiter().GetResult())
            }
            if ($null -ne $stderrTask) {
                Write-Utf8NoBom -Path $stderrPath -Contents ([string] $stderrTask.GetAwaiter().GetResult())
            }
        } catch {
            Write-Warning "Could not persist captured Fabric launcher output: $($_.Exception.Message)"
        }
        $process.Dispose()
    }
}

if (-not $passed) { throw 'Fabric production smoke failed.' }
$finalText = Get-RunText -GameRoot $runRoot -LatestLog $latestLog -StdoutPath $stdoutPath -StderrPath $stderrPath
Assert-NoFatalLog -Text $finalText -Context 'Fabric production shutdown'
Assert-ProfileLogMarkers -Text $finalText -Context 'Fabric production shutdown' -Expected $ExpectedLogMarkers -Forbidden $ForbiddenLogMarkers
$finalLog = Get-LogText -Path $latestLog
if ($finalLog -notmatch $capabilityPattern) { throw 'Final Fabric log is missing the PackForge capability marker.' }
if ($finalLog.IndexOf($reloadMarker, [StringComparison]::OrdinalIgnoreCase) -lt 0) { throw 'Final Fabric log is missing the PackForge reload marker.' }
if ($finalLog -notmatch $artifactSourcePattern) { throw 'Final Fabric log is missing the exact PackForge artifact source marker.' }
$resolvedResourceSha256 = if ($controllerMode) {
    Resolve-PackForgeResolvedResourceSha256 `
        -Text $finalLog `
        -MinimumCount $(if ($scenarioMode) { 2 } else { $ReloadCount + 1 }) `
        -Context 'Fabric production smoke'
} else {
    $null
}
$heavyFixtureEvidenceRecords = if ($requiresHeavyFixtureEvidence) {
    @(Resolve-PackForgeHeavyFixtureEvidence `
        -Text $finalLog `
        -FixtureId $heavyFixtureId `
        -MinimumCount $(if ($scenarioMode) { 2 } else { $ReloadCount + 1 }) `
        -Context 'Fabric production smoke')
} else {
    @()
}

if ($null -ne $compatibilityProfile -and [int] $compatibilityProfile.schema -eq 2) {
    $runtimeEvidencePath = Join-Path $runRoot 'compatibility-runtime-evidence.log'
    Write-Utf8NoBom -Path $runtimeEvidencePath -Contents $finalText
    $provenance.runtimeEvidence = [ordered]@{
        logPath = $runtimeEvidencePath
        sha256 = (Get-FileHash -LiteralPath $runtimeEvidencePath -Algorithm SHA256).Hash.ToUpperInvariant()
    }
    Write-Utf8NoBom -Path $provenancePath -Contents ($provenance | ConvertTo-Json -Depth 8)
}

$resolvedResourceToken = if ($null -eq $resolvedResourceSha256) { '' } else { " resolvedResourceSha256=$resolvedResourceSha256" }
$heavyFixtureToken = if ($requiresHeavyFixtureEvidence) { " heavyFixtureEvidence=$($heavyFixtureEvidenceRecords.Count)" } else { '' }
$scenarioToken = if ($scenarioMode) { " scenario=$RuntimeSmokeScenario scenarioEvidence=true" } else { ' scenario=repeat' }
Write-Output "PASS Fabric production smoke: minecraft=$MinecraftVersion version=$VersionName target=$targetMarker artifact=$artifactName sha256=$sourceHash$resolvedResourceToken$heavyFixtureToken$scenarioToken additionalMods=$($stagedAdditionalMods.Count) reloads=$ReloadCount cleanExit=$($cleanExit.ToString().ToLowerInvariant()) controlledTermination=$($controlledTermination.ToString().ToLowerInvariant()) run=$runRoot provenance=$provenancePath"
