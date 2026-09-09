[CmdletBinding()]
param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string[]] $Tasks = @('build'),
    [ValidateRange(1, 20)] [int] $Iterations = 5,
    [ValidateRange(0, 5)] [int] $Warmups = 1,
    [string] $Name = 'build'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
if ($Name -notmatch '^[A-Za-z0-9_-]+$') { throw 'Name must be a simple report identifier.' }
$reports = Join-Path $root '.gradle/measurements'
New-Item -ItemType Directory -Force $reports | Out-Null
$samples = @()
Push-Location $root
try {
    for ($index = 0; $index -lt ($Warmups + $Iterations); $index++) {
        $log = Join-Path $reports "$Name-$index.log"
        $watch = [Diagnostics.Stopwatch]::StartNew()
        & (Join-Path $root 'gradlew.bat') @Tasks --console plain 2>&1 | Tee-Object -FilePath $log
        $result = $LASTEXITCODE
        $watch.Stop()
        if ($result -ne 0) { throw "Gradle failed with exit $result; see $log" }
        if ($index -ge $Warmups) { $samples += $watch.Elapsed.TotalSeconds }
        Write-Host ("Sample {0}: {1:N3}s (warmup={2})" -f $index, $watch.Elapsed.TotalSeconds, ($index -lt $Warmups))
    }
    $sorted = @($samples | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    $median = if ($sorted.Count % 2) { $sorted[$middle] } else { ($sorted[$middle - 1] + $sorted[$middle]) / 2 }
    [ordered]@{
        project = $root
        tasks = $Tasks
        measuredAtUtc = [DateTime]::UtcNow.ToString('o')
        samplesSeconds = $samples
        medianSeconds = $median
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reports "$Name.json") -Encoding utf8
} finally {
    Pop-Location
}
