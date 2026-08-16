[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'RuntimeSmokeScenarioEvidence.ps1')

$samples = @(
    [pscustomobject]@{ Name = 'cancel-in-flight'; Text = "[INFO] PackForge runtime smoke scenario PASS: name=cancel-in-flight attempts=2 failures=0 cancellations=1 recovery=true contextCleared=true futureSettled=true overlayCleared=true`r`n" },
    [pscustomobject]@{ Name = 'forced-resource-failure'; Text = "PackForge runtime smoke scenario PASS: name=forced-resource-failure attempts=2 failures=1 cancellations=0 recovery=true contextCleared=true futureSettled=true overlayCleared=true" },
    [pscustomobject]@{ Name = 'retry-success'; Text = "PackForge runtime smoke scenario PASS: name=retry-success attempts=2 failures=1 cancellations=0 recovery=true contextCleared=true futureSettled=true overlayCleared=true" },
    [pscustomobject]@{ Name = 'retry-exhaustion'; Text = "PackForge runtime smoke scenario PASS: name=retry-exhaustion attempts=3 failures=2 cancellations=0 recovery=true contextCleared=true futureSettled=true overlayCleared=true" }
)

foreach ($sample in $samples) {
    $record = Resolve-PackForgeRuntimeSmokeScenarioEvidence -Text $sample.Text -ExpectedScenario $sample.Name -Context $sample.Name
    if ($record.Name -cne $sample.Name) { throw "Scenario parser changed the expected name for $($sample.Name)." }
}

$invalid = @(
    [pscustomobject]@{ Name = 'missing marker'; Text = ''; Scenario = 'cancel-in-flight'; Pattern = 'exactly one' },
    [pscustomobject]@{ Name = 'malformed marker'; Text = 'PackForge runtime smoke scenario PASS: name=cancel-in-flight attempts=nope failures=0 cancellations=1 recovery=true contextCleared=true futureSettled=true overlayCleared=true'; Scenario = 'cancel-in-flight'; Pattern = 'exactly one|malformed' },
    [pscustomobject]@{ Name = 'wrong scenario'; Text = $samples[0].Text; Scenario = 'retry-success'; Pattern = 'recorded scenario' },
    [pscustomobject]@{ Name = 'missing cleanup'; Text = 'PackForge runtime smoke scenario PASS: name=cancel-in-flight attempts=2 failures=0 cancellations=1 recovery=true contextCleared=false futureSettled=true overlayCleared=true'; Scenario = 'cancel-in-flight'; Pattern = 'cleanup' },
    [pscustomobject]@{ Name = 'failure marker'; Text = 'PackForge runtime smoke scenario FAIL: name=cancel-in-flight reason=cleanup'; Scenario = 'cancel-in-flight'; Pattern = 'FAIL marker' },
    [pscustomobject]@{ Name = 'duplicate pass'; Text = "$($samples[0].Text)`n$($samples[0].Text)"; Scenario = 'cancel-in-flight'; Pattern = 'exactly one' }
)
foreach ($case in $invalid) {
    try {
        Resolve-PackForgeRuntimeSmokeScenarioEvidence -Text $case.Text -ExpectedScenario $case.Scenario -Context $case.Name | Out-Null
        throw "$($case.Name) unexpectedly passed."
    } catch {
        if ($_.Exception.Message -notmatch $case.Pattern) { throw "$($case.Name) failed with an unexpected error: $($_.Exception.Message)" }
    }
}

Write-Output 'Runtime smoke scenario evidence self-test PASS: cancellation, forced failure, retry success, retry exhaustion, cleanup, malformed, duplicate, and FAIL-marker gates verified.'
