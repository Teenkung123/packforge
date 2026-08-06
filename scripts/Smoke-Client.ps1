[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('fabric', 'forge', 'neoforge')]
    [string] $Platform,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string] $Target,

    [ValidateRange(1, 86400)]
    [int] $TimeoutSeconds = 900,

    [ValidateRange(0, 100)]
    [int] $ReloadCount = 2,

    [string] $ForgeVersionOverride,

    [string] $NeoForgeVersionOverride,

    [string] $ArtifactPathOverride,

    [switch] $AllowControlledTermination
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

if (-not ('PackForgeSmokeNative' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public sealed class PackForgeWindowInfo
{
    public PackForgeWindowInfo(IntPtr handle, int processId, string title)
    {
        Handle = handle;
        ProcessId = processId;
        Title = title;
    }

    public IntPtr Handle { get; private set; }
    public int ProcessId { get; private set; }
    public string Title { get; private set; }
}

public static class PackForgeSmokeNative
{
    private const int SwRestore = 9;
    private const uint KeyEventKeyUp = 0x0002;
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

    public static List<PackForgeWindowInfo> GetVisibleMinecraftWindows()
    {
        var windows = new List<PackForgeWindowInfo>();
        EnumWindowsProc callback = delegate(IntPtr hWnd, IntPtr lParam)
        {
            if (!IsWindowVisible(hWnd)) return true;
            int length = GetWindowTextLength(hWnd);
            if (length <= 0) return true;

            var title = new StringBuilder(length + 1);
            GetWindowText(hWnd, title, title.Capacity);
            if (title.ToString().IndexOf("Minecraft", StringComparison.OrdinalIgnoreCase) < 0) return true;

            uint processId;
            GetWindowThreadProcessId(hWnd, out processId);
            if (processId != 0) windows.Add(new PackForgeWindowInfo(hWnd, (int)processId, title.ToString()));
            return true;
        };
        EnumWindows(callback, IntPtr.Zero);
        return windows;
    }

    public static bool IsWindowVisibleAndValid(IntPtr hWnd)
    {
        return IsWindow(hWnd) && IsWindowVisible(hWnd);
    }

    public static bool ActivateWindow(IntPtr hWnd)
    {
        if (!IsWindow(hWnd)) return false;
        ShowWindow(hWnd, SwRestore);
        BringWindowToTop(hWnd);
        return SetForegroundWindow(hWnd);
    }

    public static IntPtr ForegroundWindow()
    {
        return GetForegroundWindow();
    }

    public static bool CloseWindow(IntPtr hWnd)
    {
        return IsWindow(hWnd) && PostMessage(hWnd, WmClose, IntPtr.Zero, IntPtr.Zero);
    }

    public static void KeyDown(byte virtualKey)
    {
        keybd_event(virtualKey, 0, 0, UIntPtr.Zero);
    }

    public static void KeyUp(byte virtualKey)
    {
        keybd_event(virtualKey, 0, KeyEventKeyUp, UIntPtr.Zero);
    }
}
'@
}

function ConvertTo-JsonBoolean {
    param([bool] $Value)

    if ($Value) { return 'true' }
    return 'false'
}

function Get-EnvironmentBoolean {
    param(
        [string] $Name,
        [bool] $Default
    )

    $raw = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($raw)) { return $Default }

    switch ($raw.Trim().ToLowerInvariant()) {
        'true' { return $true }
        'false' { return $false }
        default { throw "$Name must be true or false." }
    }
}

function Read-GradleProperties {
    param([string] $Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $values[$key] = $value
    }
    return $values
}

function Get-FullPath {
    param([string] $Path)

    return [System.IO.Path]::GetFullPath($Path)
}

function Write-Utf8NoBom {
    param(
        [string] $Path,
        [string] $Contents
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Contents, $encoding)
}

function Get-CimProcessSnapshot {
    try {
        return @(Get-CimInstance -ClassName Win32_Process -ErrorAction Stop)
    } catch {
        return @()
    }
}

function Get-ProcessTreeRecords {
    param([int] $RootProcessId)

    $allProcesses = @(Get-CimProcessSnapshot)
    if ($allProcesses.Count -eq 0) { return @() }

    $pending = New-Object 'System.Collections.Generic.Queue[int]'
    $seen = @{}
    [void] $pending.Enqueue($RootProcessId)
    $seen[$RootProcessId] = $true

    while ($pending.Count -gt 0) {
        $parentId = $pending.Dequeue()
        foreach ($process in $allProcesses) {
            $processId = [int] $process.ProcessId
            if ([int] $process.ParentProcessId -eq $parentId -and -not $seen.ContainsKey($processId)) {
                $seen[$processId] = $true
                [void] $pending.Enqueue($processId)
            }
        }
    }

    return @($allProcesses | Where-Object { $seen.ContainsKey([int] $_.ProcessId) })
}

function Register-OwnedProcessTree {
    param(
        [int] $RootProcessId,
        [hashtable] $OwnedProcesses
    )

    foreach ($process in @(Get-ProcessTreeRecords -RootProcessId $RootProcessId)) {
        $OwnedProcesses[[int] $process.ProcessId] = [pscustomobject] @{
            ProcessId = [int] $process.ProcessId
            Name = [string] $process.Name
            CreationDate = [string] $process.CreationDate
            CommandLine = [string] $process.CommandLine
        }
    }

    if (-not $OwnedProcesses.ContainsKey($RootProcessId)) {
        $root = Get-Process -Id $RootProcessId -ErrorAction SilentlyContinue
        if ($null -ne $root) {
            $OwnedProcesses[$RootProcessId] = [pscustomobject] @{
                ProcessId = $RootProcessId
                Name = [string] $root.ProcessName
                CreationDate = ''
                CommandLine = ''
            }
        }
    }
}

function Stop-OwnedProcessTree {
    param(
        [int] $RootProcessId,
        [hashtable] $OwnedProcesses
    )

    if ($RootProcessId -le 0) { return }

    $root = Get-Process -Id $RootProcessId -ErrorAction SilentlyContinue
    if ($null -ne $root) {
        try {
            $taskKillPath = Join-Path ([Environment]::GetEnvironmentVariable('SystemRoot')) 'System32\taskkill.exe'
            $taskKill = Start-Process -FilePath $taskKillPath `
                -ArgumentList @('/PID', [string] $RootProcessId, '/T', '/F') `
                -WindowStyle Hidden -PassThru -Wait
        } catch {
            Stop-Process -Id $RootProcessId -Force -ErrorAction SilentlyContinue
        }
    }

    $currentById = @{}
    foreach ($process in @(Get-CimProcessSnapshot)) {
        $currentById[[int] $process.ProcessId] = $process
    }

    foreach ($owned in @($OwnedProcesses.Values)) {
        if ([int] $owned.ProcessId -eq $RootProcessId -or -not $currentById.ContainsKey([int] $owned.ProcessId)) {
            continue
        }

        $current = $currentById[[int] $owned.ProcessId]
        if ($owned.CreationDate.Length -gt 0 -and [string] $current.CreationDate -ne $owned.CreationDate) {
            continue
        }

        $name = [string] $current.Name
        if ($name -match '^(java|javaw)\.exe$') {
            Stop-Process -Id ([int] $owned.ProcessId) -Force -ErrorAction SilentlyContinue
        }
    }
}

function ConvertTo-NativeArgument {
    param([AllowEmptyString()][string] $Value)

    if ($null -eq $Value) { return '""' }
    if ($Value -notmatch '[\s"]') { return $Value }

    $escaped = $Value.Replace('"', '\"')
    if ($escaped.EndsWith('\')) { $escaped += '\' }
    return '"' + $escaped + '"'
}

function Join-NativeArguments {
    param([string[]] $Arguments)

    return (($Arguments | ForEach-Object { ConvertTo-NativeArgument -Value $_ }) -join ' ')
}

function Start-GradleProcess {
    param(
        [string] $GradleWrapper,
        [string[]] $Arguments,
        [string] $WorkingDirectory,
        [string] $StandardOutput,
        [string] $StandardError
    )

    $commandArguments = Join-NativeArguments -Arguments $Arguments
    $exitCodePath = "$StandardOutput.exitcode"
    Remove-Item -LiteralPath $exitCodePath -Force -ErrorAction SilentlyContinue
    $commandLine = '""{0}" {1} & set "_PACKFORGE_EXIT=!ERRORLEVEL!" & >"{2}" echo !_PACKFORGE_EXIT! & exit /b !_PACKFORGE_EXIT!"' -f `
        $GradleWrapper, $commandArguments, $exitCodePath
    $comSpec = [Environment]::GetEnvironmentVariable('ComSpec')
    if ([string]::IsNullOrWhiteSpace($comSpec)) {
        $comSpec = Join-Path ([Environment]::GetEnvironmentVariable('SystemRoot')) 'System32\cmd.exe'
    }

    $process = Start-Process -FilePath $comSpec `
        -ArgumentList @('/d', '/v:on', '/s', '/c', $commandLine) `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $StandardOutput `
        -RedirectStandardError $StandardError `
        -PassThru
    Add-Member -InputObject $process -NotePropertyName PackForgeExitCodePath -NotePropertyValue $exitCodePath
    return $process
}

function Get-GradleExitCode {
    param([System.Diagnostics.Process] $Process)

    $path = [string] $Process.PackForgeExitCodePath
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path -LiteralPath $path -PathType Leaf)) {
        $raw = (Get-Content -LiteralPath $path -Raw).Trim()
        if ($raw -match '^-?\d+$') { return [int] $raw }
    }
    return $null
}

function Get-FileTail {
    param([string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    try {
        return ((Get-Content -LiteralPath $Path -Tail 80 -ErrorAction Stop) -join [Environment]::NewLine)
    } catch {
        return ''
    }
}

function Invoke-GradleCommand {
    param(
        [string] $GradleWrapper,
        [string[]] $Arguments,
        [string] $WorkingDirectory,
        [string] $StandardOutput,
        [string] $StandardError,
        [datetime] $Deadline,
        [hashtable] $OwnedProcesses
    )

    $process = $null
    try {
        $process = Start-GradleProcess `
            -GradleWrapper $GradleWrapper `
            -Arguments $Arguments `
            -WorkingDirectory $WorkingDirectory `
            -StandardOutput $StandardOutput `
            -StandardError $StandardError

        while (-not $process.HasExited) {
            Register-OwnedProcessTree -RootProcessId $process.Id -OwnedProcesses $OwnedProcesses
            if ([datetime]::UtcNow -ge $Deadline) {
                throw "Gradle command timed out: $($Arguments -join ' ')"
            }
            Start-Sleep -Milliseconds 250
        }

        $process.WaitForExit()
        $process.Refresh()
        $exitCode = Get-GradleExitCode -Process $process
        if ($null -eq $exitCode) {
            throw "Gradle command exited without a readable exit code: $($Arguments -join ' ')"
        }
        if ([int] $exitCode -ne 0) {
            $stdoutTail = Get-FileTail -Path $StandardOutput
            $stderrTail = Get-FileTail -Path $StandardError
            throw "Gradle command failed with exit code $exitCode.`n$stdoutTail`n$stderrTail"
        }
    } catch {
        if ($null -ne $process) {
            Register-OwnedProcessTree -RootProcessId $process.Id -OwnedProcesses $OwnedProcesses
            Stop-OwnedProcessTree -RootProcessId $process.Id -OwnedProcesses $OwnedProcesses
        }
        throw
    }
}

function Get-JavaProcessIds {
    $ids = New-Object System.Collections.Generic.List[int]
    foreach ($name in @('java', 'javaw')) {
        foreach ($process in @(Get-Process -Name $name -ErrorAction SilentlyContinue)) {
            [void] $ids.Add([int] $process.Id)
        }
    }
    return @($ids.ToArray())
}

function Get-ProcessCommandLine {
    param(
        [int] $ProcessId,
        [object[]] $Snapshot
    )

    foreach ($process in @($Snapshot)) {
        if ([int] $process.ProcessId -eq $ProcessId) {
            return [string] $process.CommandLine
        }
    }
    return ''
}

function Find-MinecraftWindow {
    param(
        [int[]] $ExistingJavaProcessIds,
        [hashtable] $OwnedProcesses,
        [string] $RunRoot,
        [datetime] $ClientStartUtc
    )

    $normalizedRunRoot = (Get-FullPath -Path $RunRoot).Replace('/', '\')
    if ($normalizedRunRoot.EndsWith('\')) {
        $normalizedRunRoot = $normalizedRunRoot.Substring(0, $normalizedRunRoot.Length - 1)
    }

    $snapshot = @(Get-CimProcessSnapshot)
    foreach ($window in @([PackForgeSmokeNative]::GetVisibleMinecraftWindows())) {
        $processId = [int] $window.ProcessId
        if ($ExistingJavaProcessIds -contains $processId) { continue }

        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($null -eq $process -or [string] $process.ProcessName -notmatch '^(java|javaw)$') { continue }

        try {
            if ($process.StartTime.ToUniversalTime() -lt $ClientStartUtc) { continue }
        } catch {
        }

        $commandLine = (Get-ProcessCommandLine -ProcessId $processId -Snapshot $snapshot).Replace('/', '\')
        $pathMatch = $commandLine.IndexOf($normalizedRunRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0
        $ownedMatch = $OwnedProcesses.ContainsKey($processId)
        if (-not $pathMatch -and -not $ownedMatch) { continue }

        return $window
    }
    return $null
}

function Read-LogText {
    param([string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    return [string] (Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue)
}

function Get-RunText {
    param(
        [string] $RunRoot,
        [string[]] $LogPaths,
        [datetime] $SinceUtc
    )

    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($path in $LogPaths) {
        [string] $contents = Read-LogText -Path $path
        if (-not [string]::IsNullOrEmpty($contents)) { [void] $parts.Add($contents) }
    }

    $crashRoot = Join-Path $RunRoot 'crash-reports'
    if (Test-Path -LiteralPath $crashRoot -PathType Container) {
        foreach ($report in @(Get-ChildItem -LiteralPath $crashRoot -Filter '*.txt' -File -ErrorAction SilentlyContinue)) {
            if ($report.LastWriteTimeUtc -lt $SinceUtc) { continue }
            [void] $parts.Add("---- Minecraft Crash Report ----`n$($report.FullName)`n$(Read-LogText -Path $report.FullName)")
        }
    }
    return [string]::Join([Environment]::NewLine, $parts)
}

function Get-LogMarkerCount {
    param(
        [string] $Text,
        [string] $Marker
    )

    return [regex]::Matches($Text, [regex]::Escape($Marker), [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
}

function Assert-NoFatalLog {
    param(
        [string] $Text,
        [string] $FatalPattern,
        [string] $Context
    )

    $match = [regex]::Match($Text, $FatalPattern)
    if ($match.Success) {
        throw "Fatal Minecraft diagnostic during ${Context}: $($match.Value)"
    }
}

function Send-F3T {
    param([IntPtr] $WindowHandle)

    if (-not [PackForgeSmokeNative]::IsWindowVisibleAndValid($WindowHandle)) {
        throw 'Minecraft window is no longer visible before F3+T.'
    }

    $activated = $false
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        [void] [PackForgeSmokeNative]::ActivateWindow($WindowHandle)
        Start-Sleep -Milliseconds 150
        if ([PackForgeSmokeNative]::ForegroundWindow() -eq $WindowHandle) {
            $activated = $true
            break
        }
    }
    if (-not $activated) { throw 'Could not safely activate the Minecraft window for F3+T.' }

    [PackForgeSmokeNative]::KeyDown(0x72)
    try {
        Start-Sleep -Milliseconds 40
        [PackForgeSmokeNative]::KeyDown(0x54)
        try {
            Start-Sleep -Milliseconds 40
        } finally {
            [PackForgeSmokeNative]::KeyUp(0x54)
        }
    } finally {
        [PackForgeSmokeNative]::KeyUp(0x72)
    }
}

$repoRoot = Get-FullPath -Path (Join-Path $PSScriptRoot '..')
$registryPath = Join-Path $repoRoot 'gradle\minecraft-targets.json'
$propertiesPath = Join-Path $repoRoot 'gradle.properties'
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'

foreach ($requiredPath in @($registryPath, $propertiesPath, $gradleWrapper)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required PackForge file is missing: $requiredPath"
    }
}

try {
    $registry = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json
} catch {
    throw "Could not parse PackForge target registry: $registryPath. $($_.Exception.Message)"
}

$properties = Read-GradleProperties -Path $propertiesPath
if (-not $properties.ContainsKey('mod_version')) { throw "gradle.properties is missing mod_version." }

$targetMatches = @($registry.targets | Where-Object { [string] $_.key -eq $Target })
if ($targetMatches.Count -ne 1) {
    throw "Unknown or duplicate PackForge target '$Target'."
}
$targetConfig = $targetMatches[0]

$platformProperty = @($targetConfig.platforms.PSObject.Properties | Where-Object { $_.Name -eq $Platform })
if ($platformProperty.Count -ne 1) {
    throw "PackForge target '$Target' does not support platform '$Platform'."
}
$platformConfig = $platformProperty[0].Value

$modVersion = [string] $properties['mod_version']
$artifactVersion = $modVersion + [string] $platformConfig.versionSuffix
$artifactName = "packforge-$Platform-$artifactVersion-mc$([string] $targetConfig.artifactMinecraft).jar"

$forgeOverride = ''
$requestedForgeOverride = $ForgeVersionOverride
if ([string]::IsNullOrWhiteSpace($requestedForgeOverride)) {
    $requestedForgeOverride = [Environment]::GetEnvironmentVariable('PACKFORGE_FORGE_VERSION_OVERRIDE')
}
if (-not [string]::IsNullOrWhiteSpace($requestedForgeOverride)) {
    if ($Platform -ne 'forge') {
        throw 'ForgeVersionOverride is valid only for Forge smoke runs.'
    }

    $requestedForgeOverride = $requestedForgeOverride.Trim()
    if ($requestedForgeOverride -notmatch '^[0-9A-Za-z.+_-]+$') {
        throw 'ForgeVersionOverride contains unsupported characters.'
    }

    $minecraftVersion = [string] $targetConfig.minecraftVersion
    $supportedMinecraftVersions = @($minecraftVersion)
    $artifactMinecraft = [string] $targetConfig.artifactMinecraft
    if ($artifactMinecraft -match '^([0-9]+\.[0-9]+)-([0-9]+\.[0-9]+)$') {
        $supportedMinecraftVersions = @($Matches[1], $Matches[2])
    }
    $targetForgePrefix = "$minecraftVersion-"
    if ($requestedForgeOverride.Contains('-')) {
        $matchesSupportedVersion = $false
        foreach ($supportedMinecraftVersion in $supportedMinecraftVersions) {
            if ($requestedForgeOverride.StartsWith("$supportedMinecraftVersion-", [StringComparison]::OrdinalIgnoreCase)) {
                $matchesSupportedVersion = $true
                break
            }
        }
        if (-not $matchesSupportedVersion) {
            throw "Full ForgeVersionOverride must target one of: $($supportedMinecraftVersions -join ', ')."
        }
        $forgeOverride = $requestedForgeOverride
    } else {
        $forgeOverride = "$targetForgePrefix$requestedForgeOverride"
    }
    if ($forgeOverride -notmatch '^[0-9][0-9A-Za-z.]*-[0-9][0-9A-Za-z.+_-]*$') {
        throw "Invalid normalized Forge dependency version '$forgeOverride'."
    }
}

$neoForgeOverride = ''
$requestedNeoForgeOverride = $NeoForgeVersionOverride
if ([string]::IsNullOrWhiteSpace($requestedNeoForgeOverride)) {
    $requestedNeoForgeOverride = [Environment]::GetEnvironmentVariable('PACKFORGE_NEOFORGE_VERSION_OVERRIDE')
}
if (-not [string]::IsNullOrWhiteSpace($requestedNeoForgeOverride)) {
    if ($Platform -ne 'neoforge') {
        throw 'NeoForgeVersionOverride is valid only for NeoForge smoke runs.'
    }
    $requestedNeoForgeOverride = $requestedNeoForgeOverride.Trim()
    if ($requestedNeoForgeOverride -notmatch '^[0-9][0-9A-Za-z.+_-]*$') {
        throw 'NeoForgeVersionOverride contains unsupported characters.'
    }
    $neoForgeOverride = $requestedNeoForgeOverride
}

$effectiveReloadCount = $ReloadCount
if (-not $PSBoundParameters.ContainsKey('ReloadCount')) {
    $reloadEnvironmentValue = [Environment]::GetEnvironmentVariable('PACKFORGE_RELOAD_COUNT')
    if (-not [string]::IsNullOrWhiteSpace($reloadEnvironmentValue)) {
        if ($reloadEnvironmentValue -notmatch '^\d+$') { throw 'PACKFORGE_RELOAD_COUNT must be a non-negative integer.' }
        $effectiveReloadCount = [int] $reloadEnvironmentValue
    }
}

$optimizerEnabled = Get-EnvironmentBoolean -Name 'PACKFORGE_RELOAD_OPTIMIZER' -Default $true
$resourceHashEnabled = Get-EnvironmentBoolean -Name 'PACKFORGE_RUNTIME_RESOURCE_HASH' -Default $false
$artifactSmoke = Get-EnvironmentBoolean -Name 'PACKFORGE_ARTIFACT_SMOKE' -Default ($Platform -ne 'forge')
if ($Platform -eq 'forge' -and $artifactSmoke) {
    throw 'Forge production artifacts use SRG names and cannot be validated in ForgeGradle''s Mojmap runClient. Use scripts/Smoke-Forge-Production.ps1 for final-JAR validation, or set PACKFORGE_ARTIFACT_SMOKE=false for source-mode development smoke.'
}

$smokeProfile = [Environment]::GetEnvironmentVariable('PACKFORGE_SMOKE_PROFILE')
if ([string]::IsNullOrWhiteSpace($smokeProfile)) { $smokeProfile = 'default' }
$smokeProfile = $smokeProfile.Trim().ToLowerInvariant()
$loadingStatus = $true
$fadeDisabled = $false
$reloadToast = $true
$diagnostics = $false
switch ($smokeProfile) {
    'core' {
        $loadingStatus = $false
        $fadeDisabled = $false
        $reloadToast = $false
        $diagnostics = $false
    }
    'default' {
    }
    'diagnostics' {
        $diagnostics = $true
    }
    'ui' {
        $fadeDisabled = $true
    }
    default {
        throw 'PACKFORGE_SMOKE_PROFILE must be core, default, diagnostics, or ui.'
    }
}

$deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
$platformRoot = Get-FullPath -Path (Join-Path $repoRoot "platform\$Platform")
$runRoot = Get-FullPath -Path (Join-Path $platformRoot "run\$Target")
$logRoot = Join-Path $runRoot 'logs'
$configRoot = Join-Path $runRoot 'config'
$resourcePackRoot = Join-Path $runRoot 'resourcepacks'
$modsRoot = Join-Path $runRoot 'mods'
$logFile = Join-Path $logRoot 'latest.log'
$gradleStdout = Join-Path $logRoot 'packforge-smoke-gradle.stdout.log'
$gradleStderr = Join-Path $logRoot 'packforge-smoke-gradle.stderr.log'
$artifactPath = Join-Path (Join-Path $platformRoot "build\$Target\libs") $artifactName
if (-not [string]::IsNullOrWhiteSpace($ArtifactPathOverride)) {
    if (-not $artifactSmoke) {
        throw 'ArtifactPathOverride requires artifact smoke mode.'
    }
    $artifactPath = Get-FullPath -Path $ArtifactPathOverride
    if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        throw "ArtifactPathOverride is missing: $artifactPath"
    }
}
$fixturePath = Join-Path (Join-Path $platformRoot "build\$Target\benchmark") 'deterministic-large-pack.zip'
$fixtureDestination = Join-Path $resourcePackRoot 'deterministic-large-pack.zip'
$configFile = Join-Path $configRoot 'packforge.json'
$optionsFile = Join-Path $runRoot 'options.txt'

if (-not (Test-Path -LiteralPath $platformRoot -PathType Container)) {
    throw "PackForge platform directory is missing: $platformRoot"
}

$knownRunFiles = @(
    $logFile,
    $gradleStdout,
    $gradleStderr,
    (Join-Path $logRoot 'packforge-timings.csv'),
    (Join-Path $logRoot 'packforge-font-timings.csv'),
    (Join-Path $logRoot 'packforge-atlas-timings.csv'),
    (Join-Path $logRoot 'packforge-listener-timings.csv'),
    (Join-Path $logRoot 'packforge-startup-timings.csv'),
    (Join-Path $logRoot 'packforge-startup-summary.csv')
)

$clientRootProcessId = 0
$clientOwnedProcesses = @{}
$clientProcess = $null
$minecraftWindow = $null
$passed = $false
$cleanExit = $false
$controlledTermination = $false
$artifactHash = 'source-mode'

try {
    foreach ($directory in @($runRoot, $logRoot, $configRoot, $resourcePackRoot, $modsRoot)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    $preexistingRunWindow = Find-MinecraftWindow `
        -ExistingJavaProcessIds @() `
        -OwnedProcesses @{} `
        -RunRoot $runRoot `
        -ClientStartUtc ([datetime]::UtcNow.AddYears(-1))
    if ($null -ne $preexistingRunWindow) {
        throw "A Minecraft window already uses the isolated run directory '$runRoot'."
    }

    foreach ($knownRunFile in $knownRunFiles) {
        if (Test-Path -LiteralPath $knownRunFile -PathType Leaf) {
            Remove-Item -LiteralPath $knownRunFile -Force
        }
    }

    $benchmarkOwnedProcesses = @{}
    $benchmarkArguments = @('-p', $platformRoot, "-Ppackforge_target=$Target", 'benchmarkPackIndex', '--no-daemon')
    Invoke-GradleCommand `
        -GradleWrapper $gradleWrapper `
        -Arguments $benchmarkArguments `
        -WorkingDirectory $repoRoot `
        -StandardOutput $gradleStdout `
        -StandardError $gradleStderr `
        -Deadline $deadline `
        -OwnedProcesses $benchmarkOwnedProcesses

    if (-not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) {
        throw "Benchmark fixture was not produced at the exact expected path: $fixturePath"
    }
    Copy-Item -LiteralPath $fixturePath -Destination $fixtureDestination -Force

    foreach ($oldPackForgeJar in @(Get-ChildItem -LiteralPath $modsRoot -File -ErrorAction Stop | Where-Object {
        $_.Name -match '^packforge-(fabric|forge|neoforge)-.+\.jar$'
    })) {
        Remove-Item -LiteralPath $oldPackForgeJar.FullName -Force
    }

    if ($artifactSmoke) {
        if ([string]::IsNullOrWhiteSpace($ArtifactPathOverride)) {
            $artifactOwnedProcesses = @{}
            $artifactArguments = @('-p', $platformRoot, "-Ppackforge_target=$Target", 'build', '--no-daemon')
            Invoke-GradleCommand `
                -GradleWrapper $gradleWrapper `
                -Arguments $artifactArguments `
                -WorkingDirectory $repoRoot `
                -StandardOutput $gradleStdout `
                -StandardError $gradleStderr `
                -Deadline $deadline `
                -OwnedProcesses $artifactOwnedProcesses
        }

        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            throw "Expected packaged artifact was not produced at the exact expected path: $artifactPath"
        }

        $stagedArtifactPath = Join-Path $modsRoot $artifactName
        Copy-Item -LiteralPath $artifactPath -Destination $stagedArtifactPath -Force
        if (-not (Test-Path -LiteralPath $stagedArtifactPath -PathType Leaf)) {
            throw "Packaged artifact was not staged at the exact expected path: $stagedArtifactPath"
        }
        $artifactHash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash
        $stagedArtifactHash = (Get-FileHash -LiteralPath $stagedArtifactPath -Algorithm SHA256).Hash
        if ($artifactHash -ne $stagedArtifactHash) {
            throw "Staged artifact hash mismatch: source=$artifactHash staged=$stagedArtifactHash"
        }
    }

    $configText = @"
{
  "configVersion": 12,
  "reloadOptimizerEnabled": $(ConvertTo-JsonBoolean $optimizerEnabled),
  "loaderIndexEnabled": true,
  "loaderZipPoolEnabled": true,
  "loaderTimingsEnabled": true,
  "reloadListenerTimingsEnabled": $(ConvertTo-JsonBoolean $diagnostics),
  "shaderApplyStallDiagnosticsEnabled": $(ConvertTo-JsonBoolean $diagnostics),
  "immediatelyFastFontAtlasCompatEnabled": true,
  "loadingStatusOverlayEnabled": $(ConvertTo-JsonBoolean $loadingStatus),
  "loadingScreenFadeOutDisabled": $(ConvertTo-JsonBoolean $fadeDisabled),
  "reloadSummaryToastEnabled": $(ConvertTo-JsonBoolean $reloadToast),
  "fontReloadDiagnosticsEnabled": $(ConvertTo-JsonBoolean $diagnostics),
  "fontPrepareProviderSelectionEnabled": true,
  "fontBitmapProviderCacheEnabled": true,
  "modelParseBatchingEnabled": true,
  "modelParseTimingEnabled": $(ConvertTo-JsonBoolean $diagnostics),
  "modelAdaptiveBatchingEnabled": true,
  "modelDuplicateParseCacheEnabled": true,
  "atlasPhaseTimingsEnabled": $(ConvertTo-JsonBoolean $diagnostics),
  "atlasDecodeBatchingEnabled": true
}
"@
    Write-Utf8NoBom -Path $configFile -Contents $configText
    Write-Utf8NoBom -Path $optionsFile -Contents @'
resourcePacks:["vanilla","file/deterministic-large-pack.zip"]
incompatibleResourcePacks:["file/deterministic-large-pack.zip"]
'@

    $existingJavaProcessIds = @(Get-JavaProcessIds)
    $clientStartUtc = [datetime]::UtcNow
    $runArguments = @(
        '-p',
        $platformRoot,
        "-Ppackforge_target=$Target"
    )
    if ($artifactSmoke) { $runArguments += '-Ppackforge_artifact_smoke=true' }
    if ($forgeOverride.Length -gt 0) {
        $runArguments += "-Ppackforge_forge_version_override=$forgeOverride"
    }
    if ($neoForgeOverride.Length -gt 0) {
        $runArguments += "-Ppackforge_neoforge_version_override=$neoForgeOverride"
    }
    $runArguments += @('runClient', '--no-daemon')

    $clientProcess = Start-GradleProcess `
        -GradleWrapper $gradleWrapper `
        -Arguments $runArguments `
        -WorkingDirectory $repoRoot `
        -StandardOutput $gradleStdout `
        -StandardError $gradleStderr
    $clientRootProcessId = [int] $clientProcess.Id
    Register-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses

    $fatalPattern = '(?im)(Critical injection failure|Mixin apply (?:for mod packforge )?failed|MixinTransformerError|InvalidInjection(?:Exception|PointException)?|InjectionError|IllegalClassLoadError|NoClassDefFoundError|ExceptionInInitializerError|(?:^|\s)LinkageError:|Minecraft has crashed|A critical error occurred|---- Minecraft Crash Report ----|Shutdown failure!|PackForge[^\r\n]{0,240}(?:ERROR|Exception|FATAL)|(?:ERROR|FATAL)[^\r\n]{0,120}(?:\[packforge(?:/|\])|\(packforge\)))'
    $capabilityPattern = "PackForge capabilities:.*target=$([regex]::Escape($Target))"
    $reloadMarker = 'PackForge reload complete:'
    $artifactMarker = $artifactName
    $ready = $false

    while ([datetime]::UtcNow -lt $deadline) {
        Register-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses
        $logText = Read-LogText -Path $logFile
        Assert-NoFatalLog -Text $logText -FatalPattern $fatalPattern -Context 'client startup'

        $minecraftWindow = Find-MinecraftWindow `
            -ExistingJavaProcessIds $existingJavaProcessIds `
            -OwnedProcesses $clientOwnedProcesses `
            -RunRoot $runRoot `
            -ClientStartUtc $clientStartUtc
        $hasCapabilities = $logText -match $capabilityPattern
        $hasPackForgeReload = $logText.IndexOf($reloadMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0
        $hasVanillaReload = $logText.IndexOf('Reloading ResourceManager:', [StringComparison]::OrdinalIgnoreCase) -ge 0 -and `
            $logText.IndexOf('Sound engine started', [StringComparison]::OrdinalIgnoreCase) -ge 0 -and `
            $logText.IndexOf('textures/atlas/gui.png-atlas', [StringComparison]::OrdinalIgnoreCase) -ge 0
        $hasReload = $hasPackForgeReload -or $hasVanillaReload
        $hasArtifact = (-not $artifactSmoke) -or $logText.IndexOf($artifactMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0
        $hasResourceHash = (-not $resourceHashEnabled) -or $logText.IndexOf('PackForge resolved-resource hash:', [StringComparison]::OrdinalIgnoreCase) -ge 0
        if ($clientProcess.HasExited) {
            $clientProcess.WaitForExit()
            $clientProcess.Refresh()
            $stdoutTail = Get-FileTail -Path $gradleStdout
            $stderrTail = Get-FileTail -Path $gradleStderr
            $clientExitCode = Get-GradleExitCode -Process $clientProcess
            throw "runClient exited before readiness with code $clientExitCode.`n$stdoutTail`n$stderrTail"
        }
        if (($null -ne $minecraftWindow -or $AllowControlledTermination) `
            -and $hasCapabilities -and $hasReload -and $hasArtifact -and $hasResourceHash) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not $ready) {
        throw "Client readiness timed out: window=$($null -ne $minecraftWindow) allowControlled=$($AllowControlledTermination.IsPresent) capabilities=$hasCapabilities reload=$hasReload artifact=$hasArtifact resourceHash=$hasResourceHash."
    }
    if ($null -eq $minecraftWindow -and $effectiveReloadCount -gt 0) {
        throw 'A visible Minecraft window is required for F3+T reload validation.'
    }

    for ($reload = 1; $reload -le $effectiveReloadCount; $reload++) {
        $beforeText = Read-LogText -Path $logFile
        $previousReloadCount = Get-LogMarkerCount -Text $beforeText -Marker $reloadMarker
        $previousHashCount = Get-LogMarkerCount -Text $beforeText -Marker 'PackForge resolved-resource hash:'
        Send-F3T -WindowHandle $minecraftWindow.Handle

        $reloadDeadline = [datetime]::UtcNow.AddSeconds(180)
        if ($reloadDeadline -gt $deadline) { $reloadDeadline = $deadline }
        $reloadReady = $false
        while ([datetime]::UtcNow -lt $reloadDeadline) {
            Register-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses
            $reloadText = Read-LogText -Path $logFile
            Assert-NoFatalLog -Text $reloadText -FatalPattern $fatalPattern -Context "reload $reload"
            $currentReloadCount = Get-LogMarkerCount -Text $reloadText -Marker $reloadMarker
            $currentHashCount = Get-LogMarkerCount -Text $reloadText -Marker 'PackForge resolved-resource hash:'
            if ($currentReloadCount -gt $previousReloadCount -and `
                ((-not $resourceHashEnabled) -or $currentHashCount -gt $previousHashCount)) {
                $reloadReady = $true
                break
            }
            if ($clientProcess.HasExited) {
                $clientProcess.WaitForExit()
                $clientProcess.Refresh()
                $clientExitCode = Get-GradleExitCode -Process $clientProcess
                throw "runClient exited during reload $reload with code $clientExitCode."
            }
            Start-Sleep -Seconds 2
        }
        if (-not $reloadReady) { throw "Resource reload $reload did not emit a new completion marker before timeout." }
    }

    if ($null -eq $minecraftWindow) {
        Register-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses
        Stop-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses
        $controlledTermination = $true
    } else {
        if (-not [PackForgeSmokeNative]::CloseWindow($minecraftWindow.Handle)) {
            throw 'Could not request a clean close for the Minecraft window.'
        }

		$closeDeadline = [datetime]::UtcNow.AddSeconds(90)
		if ($closeDeadline -gt $deadline) { $closeDeadline = $deadline }
		while ([datetime]::UtcNow -lt $closeDeadline) {
			Register-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses
			if (-not [PackForgeSmokeNative]::IsWindowVisibleAndValid($minecraftWindow.Handle) -and $clientProcess.HasExited) {
				break
			}
			if ($clientProcess.HasExited -and [PackForgeSmokeNative]::IsWindowVisibleAndValid($minecraftWindow.Handle)) {
				throw 'Minecraft exited without closing its visible window cleanly.'
			}
			Start-Sleep -Seconds 2
		}

		if ([PackForgeSmokeNative]::IsWindowVisibleAndValid($minecraftWindow.Handle)) {
			throw 'Minecraft window did not close before the clean-exit timeout.'
		}
		if (-not $clientProcess.HasExited) {
			throw 'runClient did not exit after the Minecraft window was closed.'
		}
		$clientProcess.Refresh()
		$clientProcess.WaitForExit()
		$clientProcess.Refresh()
		$clientExitCode = Get-GradleExitCode -Process $clientProcess
		if ($null -eq $clientExitCode) {
			throw 'runClient exited after clean close without a readable exit code.'
		}
		if ([int] $clientExitCode -ne 0) {
			throw "runClient exited after clean close with code $clientExitCode."
		}
		$cleanExit = $true
        }

    $finalRunText = Get-RunText -RunRoot $runRoot -LogPaths @($logFile, $gradleStdout, $gradleStderr) -SinceUtc $clientStartUtc
    Assert-NoFatalLog -Text $finalRunText -FatalPattern $fatalPattern -Context 'client shutdown'
    $passed = $true
} finally {
    if (-not $passed -and $clientRootProcessId -gt 0) {
        Register-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses
        Stop-OwnedProcessTree -RootProcessId $clientRootProcessId -OwnedProcesses $clientOwnedProcesses
    }
}

$overrideLabel = 'lower-bound'
if ($forgeOverride.Length -gt 0) { $overrideLabel = $forgeOverride }
if ($neoForgeOverride.Length -gt 0) { $overrideLabel = $neoForgeOverride }
$smokeMode = 'source'
if ($artifactSmoke) { $smokeMode = 'artifact' }
Write-Output "PASS PackForge smoke: platform=$Platform target=$Target mode=$smokeMode artifact=$artifactName sha256=$artifactHash reloads=$effectiveReloadCount forgeVersion=$overrideLabel cleanExit=$($cleanExit.ToString().ToLowerInvariant()) controlledTermination=$($controlledTermination.ToString().ToLowerInvariant())"
