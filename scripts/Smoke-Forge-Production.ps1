[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ForgeClientRoot,

    [Parameter(Mandatory = $true)]
    [string] $VersionName,

    [Parameter(Mandatory = $true)]
    [string] $ArtifactPath,

    [Parameter(Mandatory = $true)]
    [string] $AssetsRoot,

    [Parameter(Mandatory = $true)]
    [string] $NativesRoot,

    [Parameter(Mandatory = $true)]
    [string] $JavaPath,

    [string] $FallbackLibrariesRoot,

    [ValidateRange(60, 3600)]
    [int] $TimeoutSeconds = 900,

    [ValidateRange(0, 10)]
    [int] $ReloadCount = 2
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

    $resolved = [IO.Path]::GetFullPath($Path)
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

    if (-not (Test-RuleSet -Rules (Get-ObjectProperty -Object $Library -Name 'rules'))) { return $null }
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
    $fatal = '(?im)(Critical injection failure|Mixin apply failed|InvalidInjectionException|InjectionError|Minecraft has crashed|A critical error occurred|PackForge.*(?:ERROR|Exception|FATAL))'
    if ($Text -match $fatal) { throw "Fatal PackForge/Mixin signature found during ${Context}: $($Matches[0])" }
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

$clientRoot = Resolve-RequiredPath -Path $ForgeClientRoot -Description 'Forge client root' -Directory
$artifact = Resolve-RequiredPath -Path $ArtifactPath -Description 'PackForge production artifact'
$assets = Resolve-RequiredPath -Path $AssetsRoot -Description 'Minecraft assets root' -Directory
$natives = Resolve-RequiredPath -Path $NativesRoot -Description 'Minecraft natives root' -Directory
$java = Resolve-RequiredPath -Path $JavaPath -Description 'Java executable'
$libraries = Resolve-RequiredPath -Path (Join-Path $clientRoot 'libraries') -Description 'Forge libraries root' -Directory
$fallbackLibraries = $FallbackLibrariesRoot
if ([string]::IsNullOrWhiteSpace($fallbackLibraries)) {
    $inferredFallback = Join-Path (Split-Path -Parent $assets) 'libraries'
    if (Test-Path -LiteralPath $inferredFallback -PathType Container) { $fallbackLibraries = $inferredFallback }
} else {
    $fallbackLibraries = Resolve-RequiredPath -Path $fallbackLibraries -Description 'Fallback Minecraft libraries root' -Directory
}

if ([IO.Path]::GetPathRoot($clientRoot).TrimEnd('\') -eq $clientRoot.TrimEnd('\')) {
    throw 'ForgeClientRoot must not be a drive root.'
}
if ($VersionName -notmatch '^1\.20\.1-forge-[0-9A-Za-z.+_-]+$') {
    throw "Unexpected Forge production version name: $VersionName"
}
if ([IO.Path]::GetFileName($artifact) -notmatch '^packforge-forge-.+-mc1\.20\.1\.jar$') {
    throw "Artifact is not a Forge 1.20.1 production JAR: $artifact"
}

$childJsonPath = Resolve-RequiredPath -Path (Join-Path $clientRoot "versions\$VersionName\$VersionName.json") -Description 'Forge version metadata'
$child = Get-Content -LiteralPath $childJsonPath -Raw | ConvertFrom-Json
if ([string] $child.id -ne $VersionName -or [string] $child.inheritsFrom -ne '1.20.1') {
    throw "Forge metadata identity mismatch in $childJsonPath"
}

$parentName = [string] $child.inheritsFrom
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

$classpath = [Collections.Generic.List[string]]::new()
$seenLibraries = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($library in @($child.libraries) + @($parent.libraries)) {
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
$logsRoot = Join-Path $gameRoot 'logs'
New-Item -ItemType Directory -Path $modsRoot, $logsRoot -Force | Out-Null
$stagedArtifact = Join-Path $modsRoot ([IO.Path]::GetFileName($artifact))
Copy-Item -LiteralPath $artifact -Destination $stagedArtifact
if ((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $stagedArtifact -Algorithm SHA256).Hash) {
    throw 'Staged production artifact hash mismatch.'
}

$replacements = @{
    '${auth_player_name}' = 'PackForgeSmoke'
    '${version_name}' = $VersionName
    '${game_directory}' = $gameRoot
    '${assets_root}' = $assets
    '${assets_index_name}' = [string] $parent.assetIndex.id
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
foreach ($argument in (Expand-Arguments -Arguments (@($parent.arguments.jvm) + @($child.arguments.jvm)))) {
    $expandedArgument = Expand-Token -Value $argument
    if ($expandedArgument.StartsWith('-DignoreList=', [StringComparison]::Ordinal) -and
        $expandedArgument.IndexOf("$parentName.jar", [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        $expandedArgument += ",$parentName.jar"
    }
    $javaArguments.Add($expandedArgument)
}
$javaArguments.Add([string] $child.mainClass)
foreach ($argument in (Expand-Arguments -Arguments (@($parent.arguments.game) + @($child.arguments.game)))) {
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

$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$started = $false
$passed = $false
$stdoutTask = $null
$stderrTask = $null
$window = [IntPtr]::Zero
$cleanExit = $false
$controlledTermination = $false
$reloadMarker = 'minecraft:textures/atlas/mob_effects.png-atlas'
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
        $hasCapabilities = $logText -match 'PackForge capabilities:.*target=mc1_20_1'
        $hasReload = $logText.IndexOf($reloadMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0
        if ($hasArtifact -and $hasCapabilities -and $hasReload -and ($ReloadCount -eq 0 -or $window -ne [IntPtr]::Zero)) {
            $ready = $true
            break
        }
        if ($process.HasExited) { throw "Production Forge exited before readiness with code $($process.ExitCode)." }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Production Forge did not reach its exact-artifact capability and final-atlas markers before timeout.' }

    for ($reload = 1; $reload -le $ReloadCount; $reload++) {
        [string] $before = Get-LogText -Path $latestLog
        $beforeCount = Get-MarkerCount -Text $before -Marker $reloadMarker
        if (-not [PackForgeProductionSmokeNative]::SendReload($window)) { throw "Could not send F3+T for reload $reload." }
        $reloadDeadline = [datetime]::UtcNow.AddSeconds(180)
        if ($reloadDeadline -gt $deadline) { $reloadDeadline = $deadline }
        $reloaded = $false
        while ([datetime]::UtcNow -lt $reloadDeadline) {
            [string] $logText = Get-LogText -Path $latestLog
            Assert-NoFatalLog -Text $logText -Context "production reload $reload"
            if ((Get-MarkerCount -Text $logText -Marker $reloadMarker) -gt $beforeCount) {
                $reloaded = $true
                break
            }
            if ($process.HasExited) { throw "Production Forge exited during reload $reload with code $($process.ExitCode)." }
            Start-Sleep -Seconds 2
        }
        if (-not $reloaded) { throw "Production reload $reload did not complete before timeout." }
    }

    if ($ReloadCount -eq 0) {
        Stop-Process -Id $process.Id -Force
        [void] $process.WaitForExit(30000)
        $controlledTermination = $true
    } else {
        if (-not [PackForgeProductionSmokeNative]::Close($window)) { throw 'Could not request a clean Minecraft window close.' }
        if (-not $process.WaitForExit(90000)) { throw 'Production Forge did not exit after its window was closed.' }
        if ($process.ExitCode -ne 0) { throw "Production Forge clean-close exit code was $($process.ExitCode)." }
        $cleanExit = $true
    }
    Assert-NoFatalLog -Text (Get-LogText -Path $latestLog) -Context 'production shutdown'
    $passed = $true
} finally {
    if ($started) {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        if ($null -ne $stdoutTask) { Set-Content -LiteralPath $stdoutPath -Value $stdoutTask.GetAwaiter().GetResult() -Encoding utf8 }
        if ($null -ne $stderrTask) { Set-Content -LiteralPath $stderrPath -Value $stderrTask.GetAwaiter().GetResult() -Encoding utf8 }
        $process.Dispose()
    }
}

if (-not $passed) { throw 'Production Forge smoke failed.' }
$hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
Write-Output "PASS Forge production smoke: version=$VersionName artifact=$([IO.Path]::GetFileName($artifact)) sha256=$hash reloads=$ReloadCount cleanExit=$($cleanExit.ToString().ToLowerInvariant()) controlledTermination=$($controlledTermination.ToString().ToLowerInvariant()) run=$gameRoot"
