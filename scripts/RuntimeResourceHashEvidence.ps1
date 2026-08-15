$script:PackForgeResolvedResourceHashMarker = 'PackForge resolved-resource hash:'
$script:PackForgeResolvedResourceHashPattern = '(?m)PackForge resolved-resource hash:\s+id=(?<id>\d+)\s+entries=(?<entries>\d+)\s+sha256=(?<sha256>[0-9a-f]{64})(?=\s|$)'
$script:PackForgeResolvedResourcePassTokenPattern = '(?:^|\s)resolvedResourceSha256=(?<value>[^\s]+)'

function Get-PackForgeResolvedResourceHashRecords {
    param(
        [AllowEmptyString()]
        [string] $Text
    )

    if ([string]::IsNullOrEmpty($Text)) {
        return @()
    }

    $records = [Collections.Generic.List[object]]::new()
    foreach ($match in [regex]::Matches($Text, $script:PackForgeResolvedResourceHashPattern)) {
        $reloadId = 0L
        if (-not [long]::TryParse(
            $match.Groups['id'].Value,
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref] $reloadId
        )) {
            throw "Resolved-resource hash marker has an invalid reload ID: $($match.Value)"
        }

        $entries = 0
        if (-not [int]::TryParse(
            $match.Groups['entries'].Value,
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref] $entries
        )) {
            throw "Resolved-resource hash marker has an invalid entry count: $($match.Value)"
        }

        [void] $records.Add([pscustomobject]@{
            ReloadId = $reloadId
            Entries = $entries
            Sha256 = $match.Groups['sha256'].Value.ToUpperInvariant()
        })
    }
    return @($records)
}

function Resolve-PackForgeResolvedResourceSha256 {
    param(
        [AllowEmptyString()]
        [string] $Text,

        [ValidateRange(1, 10000)]
        [int] $MinimumCount = 1,

        [string] $Context = 'runtime smoke'
    )

    $markerCount = [regex]::Matches(
        $(if ($null -eq $Text) { '' } else { $Text }),
        [regex]::Escape($script:PackForgeResolvedResourceHashMarker)
    ).Count
    $records = @(Get-PackForgeResolvedResourceHashRecords -Text $Text)
    if ($markerCount -ne $records.Count) {
        throw "$Context contains malformed resolved-resource hash marker(s): markers=$markerCount parsed=$($records.Count)."
    }
    if ($records.Count -lt $MinimumCount) {
        throw "$Context has too few resolved-resource hash markers: actual=$($records.Count) required=$MinimumCount."
    }

    $nonPositiveEntries = @($records | Where-Object { [int] $_.Entries -le 0 })
    if ($nonPositiveEntries.Count -gt 0) {
        throw "$Context contains a resolved-resource hash with no fixture entries."
    }

    $duplicateReloadIds = @($records | Group-Object ReloadId | Where-Object Count -gt 1)
    if ($duplicateReloadIds.Count -gt 0) {
        throw "$Context contains duplicate resolved-resource hash reload IDs: $(@($duplicateReloadIds.Name) -join ', ')."
    }

    $uniqueHashes = @($records.Sha256 | Sort-Object -Unique)
    if ($uniqueHashes.Count -ne 1) {
        throw "$Context resolved-resource hash changed across reloads: $($uniqueHashes -join ', ')."
    }
    return [string] $uniqueHashes[0]
}

function Get-PackForgePassResolvedResourceSha256 {
    param(
        [AllowEmptyString()]
        [string] $PassLine,

        [switch] $Required
    )

    $matches = @([regex]::Matches(
        $(if ($null -eq $PassLine) { '' } else { $PassLine }),
        $script:PackForgeResolvedResourcePassTokenPattern
    ))
    if ($matches.Count -eq 0) {
        if ($Required.IsPresent) {
            throw 'Production PASS line omitted resolvedResourceSha256.'
        }
        return $null
    }
    if ($matches.Count -ne 1) {
        throw "Production PASS line contains $($matches.Count) resolvedResourceSha256 tokens."
    }
    $value = $matches[0].Groups['value'].Value
    if ($value -cnotmatch '^[A-F0-9]{64}$') {
        throw 'Production PASS line contains an invalid resolvedResourceSha256 token.'
    }
    return $value
}
