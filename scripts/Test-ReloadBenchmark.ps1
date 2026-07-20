[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $BaselineWarmCsv,

    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $OptimizedWarmCsv,

    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $BaselineColdCsv,

    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $OptimizedColdCsv,

    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $BaselineHashFile,

    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $OptimizedHashFile,

    [double] $MinimumWarmImprovementPercent = 15.0,
    [double] $MaximumColdRegressionPercent = 5.0
)

$ErrorActionPreference = 'Stop'

function Get-Median {
    param([double[]] $Values)

    if ($Values.Count -eq 0) {
        throw 'Cannot calculate a median from an empty sample.'
    }
    $ordered = @($Values | Sort-Object)
    $middle = [Math]::Floor($ordered.Count / 2)
    if (($ordered.Count % 2) -eq 1) {
        return [double] $ordered[$middle]
    }
    return ([double] $ordered[$middle - 1] + [double] $ordered[$middle]) / 2.0
}

function Read-ElapsedSamples {
    param(
        [string] $Path,
        [int] $Required,
        [switch] $SkipPriming
    )

    $rows = @(Import-Csv -LiteralPath $Path)
    if ($SkipPriming) {
        if ($rows.Count -lt ($Required + 1)) {
            throw "'$Path' needs one priming reload plus $Required measured reloads."
        }
        $rows = @($rows | Select-Object -Skip 1 -First $Required)
    } else {
        if ($rows.Count -lt $Required) {
            throw "'$Path' needs at least $Required cold-process samples."
        }
        $rows = @($rows | Select-Object -First $Required)
    }

    return [double[]] @($rows | ForEach-Object {
        if ($null -eq $_.elapsed_ms -or $_.elapsed_ms -notmatch '^\d+(\.\d+)?$') {
            throw "'$Path' contains an invalid elapsed_ms value."
        }
        [double] $_.elapsed_ms
    })
}

$baselineWarm = Get-Median (Read-ElapsedSamples -Path $BaselineWarmCsv -Required 5 -SkipPriming)
$optimizedWarm = Get-Median (Read-ElapsedSamples -Path $OptimizedWarmCsv -Required 5 -SkipPriming)
$baselineCold = Get-Median (Read-ElapsedSamples -Path $BaselineColdCsv -Required 3)
$optimizedCold = Get-Median (Read-ElapsedSamples -Path $OptimizedColdCsv -Required 3)

$warmImprovement = if ($baselineWarm -eq 0.0) { 0.0 } else { 100.0 * ($baselineWarm - $optimizedWarm) / $baselineWarm }
$coldRegression = if ($baselineCold -eq 0.0) { 0.0 } else { 100.0 * ($optimizedCold - $baselineCold) / $baselineCold }
$baselineHash = (Get-Content -LiteralPath $BaselineHashFile -Raw).Trim()
$optimizedHash = (Get-Content -LiteralPath $OptimizedHashFile -Raw).Trim()

$result = [ordered] @{
    baselineWarmMedianMs = $baselineWarm
    optimizedWarmMedianMs = $optimizedWarm
    warmImprovementPercent = [Math]::Round($warmImprovement, 2)
    baselineColdMedianMs = $baselineCold
    optimizedColdMedianMs = $optimizedCold
    coldRegressionPercent = [Math]::Round($coldRegression, 2)
    resolvedResourceHash = $optimizedHash
}
$result | ConvertTo-Json

if ($baselineHash.Length -eq 0 -or $baselineHash -ne $optimizedHash) {
    throw 'Resolved-resource hashes differ between baseline and optimized runs.'
}
if ($warmImprovement -lt $MinimumWarmImprovementPercent) {
    throw "Warm reload improvement was $([Math]::Round($warmImprovement, 2))%; required at least $MinimumWarmImprovementPercent%."
}
if ($coldRegression -gt $MaximumColdRegressionPercent) {
    throw "Cold loading regressed by $([Math]::Round($coldRegression, 2))%; maximum is $MaximumColdRegressionPercent%."
}
