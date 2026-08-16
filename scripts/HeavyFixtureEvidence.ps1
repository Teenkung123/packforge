Set-StrictMode -Version 2.0

function Get-PackForgeHeavyFixtureEvidenceRecords {
    [CmdletBinding()]
    param(
        [AllowEmptyString()]
        [string] $Text
    )

    if ([string]::IsNullOrEmpty($Text)) {
        return @()
    }

    $pattern = '(?im)PackForge heavy fixture evidence:\s+id=(?<id>\d+)\s+status=(?<status>PASS|FAILURE)\s+fontProviderAttempts=(?<fontProviderAttempts>\d+)\s+fontProviderSuccesses=(?<fontProviderSuccesses>\d+)\s+fixtureModelLoads=(?<fixtureModelLoads>\d+)\s+spriteDecodeCount=(?<spriteDecodeCount>\d+)\s+highResolutionSprites=(?<highResolutionSprites>\d+)\s+mipmapStageCount=(?<mipmapStageCount>\d+)\s+mipmapOwnedCount=(?<mipmapOwnedCount>\d+)(?=\s|$)'
    $records = [Collections.Generic.List[object]]::new()
    foreach ($match in [regex]::Matches($Text, $pattern)) {
        [void] $records.Add([pscustomobject]@{
            Id = [long] $match.Groups['id'].Value
            Status = [string] $match.Groups['status'].Value
            FontProviderAttempts = [int] $match.Groups['fontProviderAttempts'].Value
            FontProviderSuccesses = [int] $match.Groups['fontProviderSuccesses'].Value
            FixtureModelLoads = [int] $match.Groups['fixtureModelLoads'].Value
            SpriteDecodeCount = [int] $match.Groups['spriteDecodeCount'].Value
            HighResolutionSprites = [int] $match.Groups['highResolutionSprites'].Value
            MipmapStageCount = [int] $match.Groups['mipmapStageCount'].Value
            MipmapOwnedCount = [int] $match.Groups['mipmapOwnedCount'].Value
        })
    }
    return @($records)
}

function Get-PackForgeHeavyFixtureRequirements {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string] $FixtureId
    )

    switch ($FixtureId) {
        'font-heavy-resource-pack' {
            return @{
                FontProviderAttempts = 1
                FontProviderSuccesses = 1
            }
        }
        'model-heavy-resource-pack' {
            return @{
                FixtureModelLoads = 1
            }
        }
        'mipmap-heavy-resource-pack' {
            return @{
                HighResolutionSprites = 1
                MipmapStageCount = 1
            }
        }
        default {
            return $null
        }
    }
}

function Test-PackForgeHeavyFixtureId {
    [CmdletBinding()]
    param(
        [AllowEmptyString()]
        [string] $FixtureId
    )

    return $null -ne (Get-PackForgeHeavyFixtureRequirements -FixtureId $FixtureId)
}

function Resolve-PackForgeHeavyFixtureEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string] $Text,

        [Parameter(Mandatory = $true)]
        [string] $FixtureId,

        [ValidateRange(1, 128)]
        [int] $MinimumCount = 1,

        [string] $Context = 'heavy fixture evidence'
    )

    $requirements = Get-PackForgeHeavyFixtureRequirements -FixtureId $FixtureId
    if ($null -eq $requirements) {
        throw "$Context does not recognize heavy fixture '$FixtureId'."
    }
    $records = @(Get-PackForgeHeavyFixtureEvidenceRecords -Text $Text)
    if ($records.Count -lt $MinimumCount) {
        throw "$Context emitted $($records.Count) evidence marker(s), expected at least $MinimumCount."
    }
    $successful = @($records | Where-Object { $_.Status -ceq 'PASS' })
    if ($successful.Count -lt $MinimumCount) {
        throw "$Context emitted fewer successful evidence markers than required: $($successful.Count)/$MinimumCount."
    }
    foreach ($requirement in $requirements.GetEnumerator()) {
        $field = [string] $requirement.Key
        $minimum = [int] $requirement.Value
        if (@($successful | Where-Object { [int] $_.$field -ge $minimum }).Count -eq 0) {
            throw "$Context did not prove stage '$field >= $minimum' for fixture '$FixtureId'."
        }
    }
    return $successful
}
