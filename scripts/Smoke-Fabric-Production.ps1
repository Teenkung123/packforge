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

    [string] $FallbackLibrariesRoot,

    [ValidateRange(60, 86400)]
    [int] $TimeoutSeconds = 900,

    [ValidateRange(0, 100)]
    [int] $ReloadCount = 2,

    [switch] $AllowControlledTermination
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

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
        keybd_event(0x72, 0, 0, UIntPtr.Zero);
        try
        {
            Thread.Sleep(50);
            keybd_event(0x54, 0, 0, UIntPtr.Zero);
            try
            {
                Thread.Sleep(50);
                return true;
            }
            finally
            {
                keybd_event(0x54, 0, KeyUp, UIntPtr.Zero);
            }
        }
        finally
        {
            keybd_event(0x72, 0, KeyUp, UIntPtr.Zero);
        }
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
    $atIndex = $coordinate.LastIndexOf('@')
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

function Get-ExpectedTargetMarker {
    param([string] $ArtifactMinecraft)

    $parts = $ArtifactMinecraft.Split('-', 2)
    if ($parts.Count -eq 2) {
        return "mc$($parts[0].Replace('.', '_'))_to_$($parts[1].Replace('.', '_'))"
    }
    return "mc$($ArtifactMinecraft.Replace('.', '_'))"
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
$artifactVersionParts = $artifactMinecraft.Split('-')
if ($artifactVersionParts.Count -gt 1) {
    $matchesMinecraft = @($artifactVersionParts | Where-Object { $_ -ieq $MinecraftVersion }).Count -gt 0
} else {
    $matchesMinecraft = $artifactMinecraft -ieq $MinecraftVersion
}
if (-not $matchesMinecraft) {
    throw "Artifact Minecraft segment '$artifactMinecraft' does not cover '$MinecraftVersion'."
}
$targetMarker = Get-ExpectedTargetMarker -ArtifactMinecraft $artifactMinecraft

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

$classpath = [Collections.Generic.List[string]]::new()
$seenLibraries = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($library in @(Get-ObjectProperty -Object $metadata -Name 'libraries')) {
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

$provenancePath = Join-Path $runRoot 'artifact-provenance.json'
$provenance = [ordered]@{
    artifact = $artifactName
    sourcePath = $artifact
    stagedPath = $stagedArtifact
    sha256 = $sourceHash
    minecraftVersion = $MinecraftVersion
    fabricVersion = $VersionName
    target = $targetMarker
}
Write-Utf8NoBom -Path $provenancePath -Contents ($provenance | ConvertTo-Json -Depth 4)

$configPath = Join-Path $configRoot 'packforge.json'
Write-Utf8NoBom -Path $configPath -Contents @'
{
  "configVersion": 12,
  "reloadOptimizerEnabled": true,
  "loaderIndexEnabled": true,
  "loaderTimingsEnabled": true,
  "reloadListenerTimingsEnabled": false,
  "startupTimingsEnabled": true,
  "startupStatusOverlayEnabled": false
}
'@

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
        if ($process.HasExited) {
            $process.Refresh()
            throw "Fabric production client exited before readiness with code $($process.ExitCode)."
        }
        if ($hasCapabilities -and $hasReload -and $hasArtifact -and ($ReloadCount -eq 0 -or $minecraftWindow -ne [IntPtr]::Zero)) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw 'Fabric production client did not reach capability, reload, and exact-artifact markers before timeout.'
    }
    if ($ReloadCount -gt 0 -and $minecraftWindow -eq [IntPtr]::Zero) {
        throw 'A visible Minecraft window is required for the requested F3+T reload validation.'
    }

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
        throw 'The Minecraft window disappeared before the requested reloads completed.'
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
$finalLog = Get-LogText -Path $latestLog
if ($finalLog -notmatch $capabilityPattern) { throw 'Final Fabric log is missing the PackForge capability marker.' }
if ($finalLog.IndexOf($reloadMarker, [StringComparison]::OrdinalIgnoreCase) -lt 0) { throw 'Final Fabric log is missing the PackForge reload marker.' }
if ($finalLog -notmatch $artifactSourcePattern) { throw 'Final Fabric log is missing the exact PackForge artifact source marker.' }

Write-Output "PASS Fabric production smoke: minecraft=$MinecraftVersion version=$VersionName target=$targetMarker artifact=$artifactName sha256=$sourceHash reloads=$ReloadCount cleanExit=$($cleanExit.ToString().ToLowerInvariant()) controlledTermination=$($controlledTermination.ToString().ToLowerInvariant()) run=$runRoot provenance=$provenancePath"
