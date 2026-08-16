$script:PackForgeRuntimeSmokeScenarioPassMarker = 'PackForge runtime smoke scenario PASS:'
$script:PackForgeRuntimeSmokeScenarioFailMarker = 'PackForge runtime smoke scenario FAIL:'
$script:PackForgeRuntimeSmokeScenarioPassPattern = '(?im)PackForge runtime smoke scenario PASS:\s+name=(?<name>[a-z0-9-]+)\s+attempts=(?<attempts>\d+)\s+failures=(?<failures>\d+)\s+cancellations=(?<cancellations>\d+)\s+recovery=(?<recovery>true|false)\s+contextCleared=(?<contextCleared>true|false)\s+futureSettled=(?<futureSettled>true|false)\s+overlayCleared=(?<overlayCleared>true|false)(?=\s|$)'

function Get-PackForgeRuntimeSmokeScenarioPassRecords {
    param(
        [AllowEmptyString()]
        [string] $Text
    )

    if ([string]::IsNullOrEmpty($Text)) {
        return @()
    }

    $records = [Collections.Generic.List[object]]::new()
    foreach ($match in [regex]::Matches($Text, $script:PackForgeRuntimeSmokeScenarioPassPattern)) {
        $attempts = 0
        $failures = 0
        $cancellations = 0
        foreach ($field in @('attempts', 'failures', 'cancellations')) {
            $value = 0
            if (-not [int]::TryParse(
                $match.Groups[$field].Value,
                [Globalization.NumberStyles]::None,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref] $value
            )) {
                throw "Runtime smoke scenario PASS marker has an invalid $field value: $($match.Value)"
            }
            Set-Variable -Name $field -Value $value -Scope Local
        }
        [void] $records.Add([pscustomobject]@{
            Name = $match.Groups['name'].Value.ToLowerInvariant()
            Attempts = $attempts
            Failures = $failures
            Cancellations = $cancellations
            Recovery = [bool]::Parse($match.Groups['recovery'].Value)
            ContextCleared = [bool]::Parse($match.Groups['contextCleared'].Value)
            FutureSettled = [bool]::Parse($match.Groups['futureSettled'].Value)
            OverlayCleared = [bool]::Parse($match.Groups['overlayCleared'].Value)
        })
    }
    return @($records)
}

function Resolve-PackForgeRuntimeSmokeScenarioEvidence {
    param(
        [AllowEmptyString()]
        [string] $Text,

        [Parameter(Mandatory = $true)]
        [ValidateSet('cancel-in-flight', 'forced-resource-failure', 'retry-success', 'retry-exhaustion')]
        [string] $ExpectedScenario,

        [string] $Context = 'runtime smoke scenario'
    )

    $value = if ($null -eq $Text) { '' } else { $Text }
    if ($value.IndexOf($script:PackForgeRuntimeSmokeScenarioFailMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw "$Context emitted a runtime smoke scenario FAIL marker."
    }
    $markerCount = [regex]::Matches(
        $value,
        [regex]::Escape($script:PackForgeRuntimeSmokeScenarioPassMarker),
        [Text.RegularExpressions.RegexOptions]::IgnoreCase
    ).Count
    $records = @(Get-PackForgeRuntimeSmokeScenarioPassRecords -Text $value)
    if ($markerCount -ne $records.Count) {
        throw "$Context contains malformed runtime smoke scenario PASS marker(s): markers=$markerCount parsed=$($records.Count)."
    }
    if ($records.Count -ne 1) {
        throw "$Context must contain exactly one runtime smoke scenario PASS marker: actual=$($records.Count)."
    }
    $record = $records[0]
    if ($record.Name -cne $ExpectedScenario) {
        throw "$Context recorded scenario '$($record.Name)' but expected '$ExpectedScenario'."
    }
    if (-not $record.Recovery -or -not $record.ContextCleared -or -not $record.FutureSettled -or -not $record.OverlayCleared) {
        throw "$Context did not prove recovery, context/future cleanup, and stale overlay cleanup."
    }
    switch ($ExpectedScenario) {
        'cancel-in-flight' {
            if ($record.Attempts -lt 2 -or $record.Cancellations -lt 1) {
                throw "$Context did not prove an in-flight cancellation and recovery."
            }
        }
        'forced-resource-failure' {
            if ($record.Attempts -lt 2 -or $record.Failures -lt 1) {
                throw "$Context did not prove a forced resource failure and recovery."
            }
        }
        'retry-success' {
            if ($record.Attempts -lt 2 -or $record.Failures -lt 1) {
                throw "$Context did not prove a failed first attempt and successful retry."
            }
        }
        'retry-exhaustion' {
            if ($record.Attempts -lt 3 -or $record.Failures -lt 2) {
                throw "$Context did not prove retry exhaustion before recovery."
            }
        }
    }
    return $record
}
