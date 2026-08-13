[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runnerPath = Join-Path $PSScriptRoot 'Run-Exact-ProductionMatrix.ps1'
$sourceCatalogPath = Join-Path $repositoryRoot 'gradle\compatibility-profiles.json'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("packforge-profile-runner-test-" + [guid]::NewGuid().ToString('N'))
$pwshPath = Join-Path $PSHOME 'pwsh.exe'

function Write-Catalog {
    param($Catalog, [string] $Path)

    [IO.File]::WriteAllText($Path, ($Catalog | ConvertTo-Json -Depth 100), [Text.UTF8Encoding]::new($false))
}

function Copy-Catalog {
    param($Catalog)

    return ($Catalog | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function Invoke-Runner {
    param([string[]] $Arguments)

    $output = @(& $pwshPath -NoProfile -File $runnerPath @Arguments 2>&1 | ForEach-Object { [string] $_ })
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Assert-Success {
    param($Invocation, [string] $Name)

    if ($Invocation.ExitCode -ne 0) {
        throw "$Name failed with exit code $($Invocation.ExitCode): $($Invocation.Output -join [Environment]::NewLine)"
    }
}

function Assert-Failure {
    param($Invocation, [string] $Name, [string] $Pattern)

    if ($Invocation.ExitCode -eq 0) { throw "$Name unexpectedly succeeded." }
    $combined = (($Invocation.Output -join ' ') -replace '\x1B\[[0-9;]*[A-Za-z]', '') -replace '\s+', ' '
    if ($combined -notmatch $Pattern) {
        throw "$Name failed without expected pattern '$Pattern': $($Invocation.Output -join [Environment]::NewLine)"
    }
}

function Assert-NoSmokeLaunch {
    param($Invocation, [string] $ResultsPath, [string] $Name)

    $combined = $Invocation.Output -join [Environment]::NewLine
    if ($combined -match '(?m)^(?:START|READY)\s') { throw "$Name entered smoke preparation or launch flow." }
    if (Test-Path -LiteralPath (Join-Path $ResultsPath 'evidence-inputs')) { throw "$Name materialized smoke evidence inputs." }
    if (@(Get-ChildItem -LiteralPath $ResultsPath -Filter '*.log' -File -ErrorAction SilentlyContinue).Count -gt 0) {
        throw "$Name created a smoke log."
    }
}

New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
try {
    $catalog = Get-Content -LiteralPath $sourceCatalogPath -Raw | ConvertFrom-Json
    $pendingCatalogPath = Join-Path $testRoot 'pending.json'
    Write-Catalog $catalog $pendingCatalogPath

	$unknown = Invoke-Runner @('-PlanOnly', '-ProfileId', 'not-a-profile', '-CompatibilityCatalogPath', $pendingCatalogPath)
	Assert-Failure $unknown 'unknown profile rejection' 'Unknown compatibility profile ID'
	$catalogWithoutProfile = Invoke-Runner @('-PlanOnly', '-CompatibilityCatalogPath', $pendingCatalogPath)
	Assert-Failure $catalogWithoutProfile 'catalog without profile rejection' 'CompatibilityCatalogPath requires -ProfileId'
	$emptyProfile = Invoke-Runner @('-PlanOnly', '-ProfileId', '')
	Assert-Failure $emptyProfile 'empty profile rejection' 'ProfileId must be a non-empty compatibility profile ID'

    $conflict = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $pendingCatalogPath, '-AdditionalModPaths', 'never-resolve.jar')
    Assert-Failure $conflict 'legacy argument conflict' 'cannot be combined with legacy compatibility argument'

    $duplicateCatalog = Copy-Catalog $catalog
    $duplicateCatalog.profiles = @($duplicateCatalog.profiles) + @((Copy-Catalog $duplicateCatalog.profiles[0]))
    $duplicateCatalogPath = Join-Path $testRoot 'duplicate.json'
    Write-Catalog $duplicateCatalog $duplicateCatalogPath
    $duplicate = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $duplicateCatalogPath)
    Assert-Failure $duplicate 'duplicate profile rejection' '(?:duplicate|duplicated)'

    $wrongCell = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $pendingCatalogPath, '-OnlyCell', '1.21.1/forge')
    Assert-Failure $wrongCell 'profile cell mismatch' "OnlyCell must be absent or exactly '1.21.1/fabric'"
    $exactCell = Invoke-Runner @('-PlanOnly', '-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $pendingCatalogPath, '-OnlyCell', '1.21.1/fabric')
    Assert-Success $exactCell 'exact profile cell selection'
    if (($exactCell.Output -join [Environment]::NewLine) -notmatch 'availability=PENDING_METADATA') {
        throw 'Exact profile cell selection did not print its declared state.'
    }

    $pendingResults = Join-Path $testRoot 'pending-results'
    $pending = Invoke-Runner @('-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $pendingCatalogPath, '-ResultsRoot', $pendingResults)
    Assert-Success $pending 'pending profile recording'
    Assert-NoSmokeLaunch $pending $pendingResults 'pending profile recording'
    $pendingRecord = Get-Content -LiteralPath (Join-Path $pendingResults 'results.jsonl') -Raw | ConvertFrom-Json
    $pendingSummary = Get-Content -LiteralPath (Join-Path $pendingResults 'summary.json') -Raw | ConvertFrom-Json
    if ($pendingRecord.executed -ne $false -or $pendingRecord.profileId -ne 'fabric-quick-pack' -or $pendingRecord.availability -ne 'PENDING_METADATA' -or $pendingRecord.result -ne 'UNTESTED') {
        throw 'Pending results.jsonl record does not preserve the non-executed profile state.'
    }
    if ($pendingSummary.executed -ne $false -or $pendingSummary.catalogSha256 -notmatch '^[A-F0-9]{64}$') {
        throw 'Pending summary.json does not preserve execution state and catalog identity.'
    }

    $unavailableCatalog = Copy-Catalog $catalog
    $unavailableProfile = @($unavailableCatalog.profiles | Where-Object id -eq 'fabric-quick-pack')[0]
    $unavailableProfile.availability = 'UNAVAILABLE'
    $unavailableProfile.result = 'UNAVAILABLE'
    $unavailableProfile.reason = 'No compatible published build exists for this exact loader cell.'
    $unavailableCatalogPath = Join-Path $testRoot 'unavailable.json'
    Write-Catalog $unavailableCatalog $unavailableCatalogPath
    $unavailableResults = Join-Path $testRoot 'unavailable-results'
    $unavailable = Invoke-Runner @('-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $unavailableCatalogPath, '-ResultsRoot', $unavailableResults)
    Assert-Success $unavailable 'unavailable profile recording'
    Assert-NoSmokeLaunch $unavailable $unavailableResults 'unavailable profile recording'
    $unavailableRecord = Get-Content -LiteralPath (Join-Path $unavailableResults 'results.jsonl') -Raw | ConvertFrom-Json
    if ($unavailableRecord.executed -ne $false -or $unavailableRecord.availability -ne 'UNAVAILABLE' -or $unavailableRecord.result -ne 'UNAVAILABLE') {
        throw 'Unavailable results.jsonl record does not preserve the non-executed profile state.'
    }

    $availableCatalog = Copy-Catalog $catalog
    $availableProfile = @($availableCatalog.profiles | Where-Object id -eq 'fabric-quick-pack')[0]
    $availableProfile.availability = 'AVAILABLE'
    $availableProfile.reason = 'Pinned metadata is ready for future executable materialization.'
    $availableMod = $availableProfile.externalMods[0]
    $availableMod | Add-Member -NotePropertyName coordinate -NotePropertyValue 'modrinth:quick-pack' -Force
    $availableMod | Add-Member -NotePropertyName sourceUrl -NotePropertyValue 'https://cdn.modrinth.com/data/quick-pack/versions/1.2.3/quick-pack.jar' -Force
    $availableMod | Add-Member -NotePropertyName version -NotePropertyValue '1.2.3' -Force
    $availableMod | Add-Member -NotePropertyName sha256 -NotePropertyValue '9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08' -Force
    $availableCatalogPath = Join-Path $testRoot 'available.json'
    Write-Catalog $availableCatalog $availableCatalogPath
    $availableResults = Join-Path $testRoot 'available-results'
    $available = Invoke-Runner @('-ProfileId', 'fabric-quick-pack', '-CompatibilityCatalogPath', $availableCatalogPath, '-ResultsRoot', $availableResults)
    Assert-Failure $available 'available profile fail-closed behavior' 'schema-2'
    if (Test-Path -LiteralPath $availableResults) { throw 'Available fail-closed selection created a results or smoke directory.' }

	Write-Output 'Exact production matrix profile selection self-test PASS: empty/unknown, catalog guard, duplicate, conflict, exact-cell, pending, unavailable, and available paths verified without smoke launch.'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
