[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'HeavyFixtureEvidence.ps1')

$fontText = @'
[PackForge] PackForge heavy fixture evidence: id=10 status=PASS fontProviderAttempts=32 fontProviderSuccesses=32 fixtureModelLoads=0 spriteDecodeCount=4 highResolutionSprites=0 mipmapStageCount=1 mipmapOwnedCount=0
[PackForge] PackForge heavy fixture evidence: id=11 status=PASS fontProviderAttempts=0 fontProviderSuccesses=0 fixtureModelLoads=0 spriteDecodeCount=4 highResolutionSprites=0 mipmapStageCount=1 mipmapOwnedCount=0
'@
$fontRecords = @(Get-PackForgeHeavyFixtureEvidenceRecords -Text $fontText)
if ($fontRecords.Count -ne 2) { throw "Expected two font evidence records, got $($fontRecords.Count)." }
[void] (Resolve-PackForgeHeavyFixtureEvidence -Text $fontText -FixtureId 'font-heavy-resource-pack' -MinimumCount 2)

$modelText = 'PackForge heavy fixture evidence: id=12 status=PASS fontProviderAttempts=0 fontProviderSuccesses=0 fixtureModelLoads=256 spriteDecodeCount=1 highResolutionSprites=0 mipmapStageCount=1 mipmapOwnedCount=0'
[void] (Resolve-PackForgeHeavyFixtureEvidence -Text $modelText -FixtureId 'model-heavy-resource-pack')

$mipmapText = 'PackForge heavy fixture evidence: id=13 status=PASS fontProviderAttempts=0 fontProviderSuccesses=0 fixtureModelLoads=0 spriteDecodeCount=3 highResolutionSprites=3 mipmapStageCount=1 mipmapOwnedCount=1'
[void] (Resolve-PackForgeHeavyFixtureEvidence -Text $mipmapText -FixtureId 'mipmap-heavy-resource-pack')

$failed = $false
try {
    [void] (Resolve-PackForgeHeavyFixtureEvidence -Text 'PackForge heavy fixture evidence: id=14 status=FAILURE fontProviderAttempts=1 fontProviderSuccesses=0 fixtureModelLoads=0 spriteDecodeCount=0 highResolutionSprites=0 mipmapStageCount=0 mipmapOwnedCount=0' -FixtureId 'font-heavy-resource-pack')
} catch {
    $failed = $true
}
if (-not $failed) { throw 'Failure evidence was accepted as a successful heavy-fixture result.' }

$malformed = 'PackForge heavy fixture evidence: id=15 status=PASS fontProviderAttempts=1 fontProviderSuccesses=1 fixtureModelLoads=0 spriteDecodeCount=0 highResolutionSprites=0 mipmapStageCount=0'
if (@(Get-PackForgeHeavyFixtureEvidenceRecords -Text $malformed).Count -ne 0) {
    throw 'Malformed heavy-fixture evidence was parsed.'
}

Write-Output 'PASS heavy fixture evidence self-test'
