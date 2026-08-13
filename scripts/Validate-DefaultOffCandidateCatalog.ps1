[CmdletBinding()]
param(
    [string] $CatalogPath = (Join-Path $PSScriptRoot '..\gradle\default-off-candidates.json'),
    [string] $ConfigSourcePath = (Join-Path $PSScriptRoot '..\common\src\main\java\com\teenkung\packforge\config\PackForgeConfig.java'),
    [switch] $SelfTest
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Frozen independently from catalog so catalog cannot redefine Phase J coverage.
$CanonicalCandidateIds = @(
    'zip-read-pool',
    'font-bitmap-provider-cache',
    'atlas-mipmap-parallelization',
    'sprite-decode-batching',
    'model-adaptive-batching',
    'model-duplicate-parse-cache',
    'startup-executor-tuning',
    'startup-async-data-parsing',
    'startup-async-class-scan',
    'startup-async-font-atlas',
    'atlas-retry'
)
$CanonicalGateNames = @('semanticParity', 'stability', 'lifecycle', 'performance', 'compatibility', 'configuration')
$CanonicalCandidateMappings = @{
    'zip-read-pool' = @{ ConfigKey = 'loaderZipPoolEnabled'; DefaultGuard = '' }
    'font-bitmap-provider-cache' = @{ ConfigKey = 'fontBitmapProviderCacheEnabled'; DefaultGuard = '' }
    'atlas-mipmap-parallelization' = @{ ConfigKey = 'atlasMipParallelEnabled'; DefaultGuard = '' }
    'sprite-decode-batching' = @{ ConfigKey = 'atlasDecodeBatchingEnabled'; DefaultGuard = '' }
    'model-adaptive-batching' = @{ ConfigKey = 'modelAdaptiveBatchingEnabled'; DefaultGuard = '' }
    'model-duplicate-parse-cache' = @{ ConfigKey = 'modelDuplicateParseCacheEnabled'; DefaultGuard = '' }
    'startup-executor-tuning' = @{ ConfigKey = 'startupExecutorTuningEnabled'; DefaultGuard = 'startupOptimizerEnabled=false' }
    'startup-async-data-parsing' = @{ ConfigKey = 'startupAsyncDataParsingEnabled'; DefaultGuard = 'startupOptimizerEnabled=false' }
    'startup-async-class-scan' = @{ ConfigKey = 'startupAsyncClassScanEnabled'; DefaultGuard = 'startupOptimizerEnabled=false' }
    'startup-async-font-atlas' = @{ ConfigKey = 'startupAsyncFontAtlasEnabled'; DefaultGuard = 'startupOptimizerEnabled=false' }
    'atlas-retry' = @{ ConfigKey = 'atlasRetryEnabled'; DefaultGuard = '' }
}
$AllowedGateStates = @('PASS', 'FAIL', 'NOT_RUN', 'NOT_APPLICABLE')
$AllowedDispositions = @('PROMOTED_DEFAULT_ON', 'SAFE_KEEP_DEFAULT_OFF', 'UNAVAILABLE', 'FAILED_WITH_REASON')
$AllowedImplementationStates = @('IMPLEMENTED_UNVERIFIED', 'PARTIAL', 'NOT_STARTED', 'UNAVAILABLE')

function Fail([string] $Message) { throw "Default-off candidate catalog: $Message" }

function Value($Object, [string] $Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    if ($property.Value -is [Collections.IEnumerable] -and $property.Value -isnot [string]) {
        Write-Output -NoEnumerate $property.Value
        return
    }
    return $property.Value
}

function Test-JsonArray($Value) {
    return $null -ne $Value -and $Value -is [Collections.IEnumerable] -and $Value -isnot [string]
}

function Assert-JsonArray($Value, [string] $Context) {
    if (-not (Test-JsonArray $Value)) { Fail "$Context must be an array." }
    $items = @($Value)
    Write-Output -NoEnumerate $items
}

function Test-JsonObject($Value) {
    return $null -ne $Value -and $Value -isnot [string] -and $Value -isnot [Collections.IEnumerable]
}

function Assert-JsonObject($Value, [string] $Context) {
    if (-not (Test-JsonObject $Value)) { Fail "$Context must be an object." }
    return $Value
}

function Require-Text($Object, [string] $Name, [string] $Context) {
    $value = Value $Object $Name
    if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) { Fail "$Context requires non-empty string '$Name'." }
    return $value.Trim()
}

function Require-Bool($Object, [string] $Name, [string] $Context) {
    $value = Value $Object $Name
    if ($value -isnot [bool]) { Fail "$Context requires boolean '$Name'." }
    return [bool] $value
}

function Assert-UniqueTextArray($Value, [string] $Context, [bool] $AllowEmpty = $false) {
    $items = Assert-JsonArray $Value $Context
    if (-not $AllowEmpty -and $items.Count -eq 0) { Fail "$Context must not be empty." }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($item in $items) {
        if ($item -isnot [string] -or [string]::IsNullOrWhiteSpace($item)) { Fail "$Context contains empty or non-string text." }
        if (-not $seen.Add($item)) { Fail "$Context contains duplicate '$item'." }
    }
    Write-Output -NoEnumerate $seen
}

function Assert-ExactSet($Actual, $Expected, [string] $Context) {
    $actualSet = Assert-UniqueTextArray $Actual $Context
    $expectedSet = Assert-UniqueTextArray $Expected "frozen $Context"
    if ($actualSet.Count -ne $expectedSet.Count) { Fail "$Context must contain exactly $($expectedSet.Count) frozen values." }
    foreach ($item in $expectedSet) {
        if (-not $actualSet.Contains($item)) { Fail "$Context is missing frozen value '$item'." }
    }
    Write-Output -NoEnumerate $actualSet
}

function Resolve-RepositoryFile([string] $RelativePath, [string] $Context) {
    if ([string]::IsNullOrWhiteSpace($RelativePath) -or $RelativePath -match '^(?:[A-Za-z]:[\\/]|\\\\|/|file:|https?://)' -or $RelativePath -match '\.\.(?:[\\/]|$)') {
        Fail "$Context must be a safe repository-relative path."
    }
    $candidate = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RelativePath))
    $rootPrefix = $RepositoryRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        Fail "$Context must resolve to an existing repository file."
    }
    return (Resolve-Path -LiteralPath $candidate).Path
}

function Read-BooleanDefault([string] $Source, [string] $ConfigKey, [string] $Context) {
    if ($ConfigKey -notmatch '^[A-Za-z][A-Za-z0-9]*$') { Fail "$Context has invalid configKey '$ConfigKey'." }
    $pattern = '(?m)public\s+boolean\s+' + [regex]::Escape($ConfigKey) + '\s*=\s*(true|false)\s*;'
    $matches = [regex]::Matches($Source, $pattern)
    if ($matches.Count -ne 1) { Fail "$Context configKey '$ConfigKey' must have exactly one boolean default assignment." }
    return $matches[0].Groups[1].Value -eq 'true'
}

function Assert-EvidenceResults($Candidate, [string] $Context) {
    $results = Assert-JsonArray (Value $Candidate 'evidenceResults') "$Context evidenceResults"
    foreach ($result in $results) {
        Assert-JsonObject $result "$Context evidence result" | Out-Null
        $path = Require-Text $result 'path' "$Context evidence result"
        $resolved = Resolve-RepositoryFile $path "$Context evidence result path"
        $sha = Require-Text $result 'sha256' "$Context evidence result"
        if ($sha -notmatch '^[A-F0-9]{64}$' -or $sha -match '^0{64}$') { Fail "$Context evidence result requires non-zero uppercase SHA-256." }
        if ((Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash -ne $sha) { Fail "$Context evidence result hash does not match '$path'." }
    }
    return $results.Count
}

function Invoke-CatalogValidation($Catalog, [string] $ConfigSource) {
    Assert-JsonObject $Catalog 'catalog' | Out-Null
    if ([int] (Value $Catalog 'schemaVersion') -ne 1) { Fail "unsupported schemaVersion '$($Catalog.schemaVersion)'." }
    Assert-ExactSet (Value $Catalog 'requiredCandidateIds') $CanonicalCandidateIds 'requiredCandidateIds' | Out-Null
    Assert-ExactSet (Value $Catalog 'gateNames') $CanonicalGateNames 'gateNames' | Out-Null

    $candidates = Assert-JsonArray (Value $Catalog 'candidates') 'candidates'
    $candidateIds = @($candidates | ForEach-Object { Require-Text $_ 'id' 'candidate' })
    Assert-ExactSet $candidateIds $CanonicalCandidateIds 'candidate ids' | Out-Null

    $promotedCount = 0
    foreach ($candidate in $candidates) {
        Assert-JsonObject $candidate 'candidate' | Out-Null
        $id = Require-Text $candidate 'id' 'candidate'
        $context = "candidate '$id'"
        Require-Text $candidate 'name' $context | Out-Null
        $configKey = Require-Text $candidate 'configKey' $context
        $configuredDefault = Require-Bool $candidate 'configuredDefault' $context
        $effectiveDefault = Require-Bool $candidate 'effectiveDefault' $context
        $guard = Value $candidate 'defaultGuard'
        if ($guard -isnot [string]) { Fail "$context requires string 'defaultGuard'." }

        $mapping = $CanonicalCandidateMappings[$id]
        if ($null -eq $mapping) { Fail "$context has no frozen config mapping." }
        if ($configKey -cne $mapping.ConfigKey) { Fail "$context configKey must be frozen value '$($mapping.ConfigKey)'." }
        if ($guard -cne $mapping.DefaultGuard) { Fail "$context defaultGuard must be frozen value '$($mapping.DefaultGuard)'." }

        $sourceDefault = Read-BooleanDefault $ConfigSource $configKey $context
        if ($configuredDefault -ne $sourceDefault) { Fail "$context configuredDefault does not match PackForgeConfig.$configKey." }
        $computedEffectiveDefault = $configuredDefault
        if (-not [string]::IsNullOrWhiteSpace($guard)) {
            if ($guard -notmatch '^([A-Za-z][A-Za-z0-9]*)=(true|false)$') { Fail "$context has invalid defaultGuard '$guard'." }
            $guardDefault = Read-BooleanDefault $ConfigSource $Matches[1] $context
            $expectedGuardDefault = $Matches[2] -eq 'true'
            if ($guardDefault -ne $expectedGuardDefault) { Fail "$context defaultGuard '$guard' does not match PackForgeConfig." }
            $computedEffectiveDefault = $configuredDefault -and $guardDefault
        }
        if ($effectiveDefault -ne $computedEffectiveDefault) { Fail "$context effectiveDefault does not follow configuredDefault/defaultGuard." }

        $implementationState = Require-Text $candidate 'implementationState' $context
        if ($implementationState -notin $AllowedImplementationStates) { Fail "$context has unsupported implementationState '$implementationState'." }
        $disposition = Require-Text $candidate 'disposition' $context
        if ($disposition -notin $AllowedDispositions) { Fail "$context has unsupported disposition '$disposition'." }
        Require-Text $candidate 'reason' $context | Out-Null

        $sourcePaths = Assert-UniqueTextArray (Value $candidate 'sourcePaths') "$context sourcePaths"
        foreach ($path in $sourcePaths) { Resolve-RepositoryFile $path "$context source path '$path'" | Out-Null }
        $missingEvidence = Assert-UniqueTextArray (Value $candidate 'missingEvidence') "$context missingEvidence" ($disposition -eq 'PROMOTED_DEFAULT_ON')

        $gates = Assert-JsonObject (Value $candidate 'gates') "$context gates"
        Assert-ExactSet @($gates.PSObject.Properties.Name) $CanonicalGateNames "$context gate names" | Out-Null
        $gateStates = @{}
        foreach ($gateName in $CanonicalGateNames) {
            $state = Require-Text $gates $gateName "$context gates"
            if ($state -notin $AllowedGateStates) { Fail "$context gate '$gateName' has unsupported state '$state'." }
            $gateStates[$gateName] = $state
        }
        $evidenceCount = Assert-EvidenceResults $candidate $context
        if ($gateStates.Values -contains 'PASS' -and $evidenceCount -eq 0) { Fail "$context cannot mark a gate PASS without immutable evidenceResults." }

        switch ($disposition) {
            'PROMOTED_DEFAULT_ON' {
                $promotedCount++
                if (-not $configuredDefault -or -not $effectiveDefault) { Fail "$context promotion requires configured and effective defaults on." }
                foreach ($gateName in $CanonicalGateNames) {
                    if ($gateStates[$gateName] -ne 'PASS') { Fail "$context promotion requires gate '$gateName' PASS." }
                }
                if ($evidenceCount -eq 0) { Fail "$context promotion requires immutable evidenceResults." }
            }
            'SAFE_KEEP_DEFAULT_OFF' {
                if ($effectiveDefault) { Fail "$context keep-off disposition cannot be effective by default." }
                if ($implementationState -notin @('IMPLEMENTED_UNVERIFIED', 'PARTIAL')) { Fail "$context keep-off disposition requires implemented or partial code." }
                if ($missingEvidence.Count -eq 0) { Fail "$context keep-off disposition requires missingEvidence." }
            }
            'FAILED_WITH_REASON' {
                if ($effectiveDefault) { Fail "$context failed disposition cannot be effective by default." }
                if ($implementationState -notin @('PARTIAL', 'NOT_STARTED')) { Fail "$context failed disposition requires PARTIAL or NOT_STARTED implementationState." }
                if ($missingEvidence.Count -eq 0) { Fail "$context failed disposition requires missingEvidence." }
            }
            'UNAVAILABLE' {
                if ($effectiveDefault) { Fail "$context unavailable disposition cannot be effective by default." }
                if ($implementationState -ne 'UNAVAILABLE') { Fail "$context unavailable disposition requires UNAVAILABLE implementationState." }
            }
        }
    }

    return [pscustomobject]@{ CandidateCount = $candidates.Count; PromotedCount = $promotedCount }
}

function Copy-JsonObject($Object) {
    return ($Object | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function Assert-MutationRejected([string] $Name, [scriptblock] $Mutation, $Catalog, [string] $ConfigSource) {
    $candidate = Copy-JsonObject $Catalog
    & $Mutation $candidate
    try {
        Invoke-CatalogValidation $candidate $ConfigSource | Out-Null
    } catch {
        return
    }
    Fail "self-test '$Name' was accepted."
}

function Invoke-SelfTests($Catalog, [string] $ConfigSource) {
    Invoke-CatalogValidation $Catalog $ConfigSource | Out-Null
    Assert-MutationRejected 'missing-candidate' { param($c) $c.candidates = @($c.candidates | Select-Object -Skip 1) } $Catalog $ConfigSource
    Assert-MutationRejected 'duplicate-candidate' { param($c) $c.candidates[1].id = $c.candidates[0].id } $Catalog $ConfigSource
    Assert-MutationRejected 'configured-default-drift' { param($c) $c.candidates[0].configuredDefault = $true } $Catalog $ConfigSource
    Assert-MutationRejected 'effective-default-drift' { param($c) $c.candidates[0].effectiveDefault = $true } $Catalog $ConfigSource
    Assert-MutationRejected 'config-key-swap' { param($c) $c.candidates[0].configKey = $c.candidates[1].configKey } $Catalog $ConfigSource
    Assert-MutationRejected 'invalid-default-guard' { param($c) $c.candidates[6].defaultGuard = 'startupOptimizerEnabled=true' } $Catalog $ConfigSource
    Assert-MutationRejected 'missing-canonical-guard' { param($c) $c.candidates[7].defaultGuard = '' } $Catalog $ConfigSource
    Assert-MutationRejected 'missing-source-path' { param($c) $c.candidates[0].sourcePaths[0] = 'missing/PhaseJ.java' } $Catalog $ConfigSource
    Assert-MutationRejected 'unknown-gate-state' { param($c) $c.candidates[0].gates.performance = 'SOURCE_ONLY' } $Catalog $ConfigSource
    Assert-MutationRejected 'missing-gate' { param($c) $c.candidates[0].gates.PSObject.Properties.Remove('lifecycle') } $Catalog $ConfigSource
    Assert-MutationRejected 'pass-without-evidence' { param($c) $c.candidates[0].gates.semanticParity = 'PASS' } $Catalog $ConfigSource
    Assert-MutationRejected 'promotion-without-gates' { param($c) $c.candidates[0].disposition = 'PROMOTED_DEFAULT_ON' } $Catalog $ConfigSource
    Assert-MutationRejected 'failed-without-reason' { param($c) $c.candidates[2].reason = '' } $Catalog $ConfigSource
    Assert-MutationRejected 'keep-off-without-missing-evidence' { param($c) $c.candidates[0].missingEvidence = @() } $Catalog $ConfigSource
    Write-Output 'Default-off candidate catalog self-test PASS: baseline accepted; 14 mutations rejected.'
}

foreach ($path in @($CatalogPath, $ConfigSourcePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "missing required file '$path'." }
}
try { $catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json } catch { Fail "catalog is not valid JSON: $($_.Exception.Message)" }
$configSource = Get-Content -LiteralPath $ConfigSourcePath -Raw
$summary = Invoke-CatalogValidation $catalog $configSource
if ($SelfTest) { Invoke-SelfTests $catalog $configSource }
Write-Output "Default-off candidate catalog PASS: $($summary.CandidateCount) frozen candidates; $($summary.PromotedCount) promoted defaults."
