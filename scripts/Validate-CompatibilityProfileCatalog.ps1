[CmdletBinding()]
param(
    [string] $CatalogPath = (Join-Path $PSScriptRoot '..\gradle\compatibility-profiles.json'),
    [string] $RegistryPath = (Join-Path $PSScriptRoot '..\gradle\minecraft-targets.json'),
    [string] $PackForgeConfigPath = (Join-Path $PSScriptRoot '..\common\src\main\java\com\Teenkung\packforge\config\PackForgeConfig.java'),
    [switch] $SelfTest
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$compatibilityConfigHelperPath = Join-Path $PSScriptRoot 'CompatibilityProfileConfig.ps1'
if (-not (Test-Path -LiteralPath $compatibilityConfigHelperPath -PathType Leaf)) {
    throw "Compatibility profile config helper is missing: $compatibilityConfigHelperPath"
}
. $compatibilityConfigHelperPath

# This is intentionally duplicated from the catalog. The catalog must not be able to
# redefine its own coverage contract.
$CanonicalRecipeIds = @(
    'fabric-quick-pack', 'fabric-sodium', 'fabric-iris', 'fabric-immediatelyfast', 'fabric-modernfix', 'fabric-ferritecore', 'fabric-continuity-indium', 'fabric-cit-resewn', 'fabric-etf-emf', 'fabric-resource-pack-unbounded', 'fabric-axiom', 'fabric-vulkanmod',
    'forge-quick-pack', 'forge-embeddium', 'forge-oculus', 'forge-immediatelyfast', 'forge-modernfix', 'forge-ferritecore', 'forge-etf-emf', 'forge-resource-pack-unbounded', 'forge-axiom',
    'neoforge-quick-pack', 'neoforge-embeddium', 'neoforge-oculus', 'neoforge-immediatelyfast', 'neoforge-modernfix', 'neoforge-ferritecore', 'neoforge-etf-emf', 'neoforge-resource-pack-unbounded', 'neoforge-axiom',
    'fabric-quick-pack-sodium-iris', 'fabric-quick-pack-immediatelyfast', 'fabric-sodium-iris-immediatelyfast', 'forge-embeddium-oculus-immediatelyfast', 'fabric-modernfix-ferritecore', 'fabric-quick-pack-default-on'
)

function Fail([string] $Message) { throw "Compatibility profile catalog: $Message" }

function Value($Object, [string] $Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    # Preserve single-element JSON arrays; normal PowerShell output would unwrap them.
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
    return @($Value)
}

function Test-JsonObject($Value) {
    return $null -ne $Value -and $Value -isnot [string] -and $Value -isnot [Collections.IEnumerable]
}

function Assert-JsonObject($Value, [string] $Context) {
    if (-not (Test-JsonObject $Value)) { Fail "$Context must be an object." }
    return $Value
}

function Assert-FeatureOverrides($Overrides, [string] $Context) {
    try {
        ConvertTo-NormalizedFeatureOverrides -Overrides $Overrides -Context $Context | Out-Null
    } catch {
        Fail $_.Exception.Message
    }
}

function Get-PackForgeConfigSourceDefaults([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "missing PackForgeConfig source '$Path'." }
    $source = Get-Content -LiteralPath $Path
    $versionMatch = @($source | Select-String -Pattern '^\s*private static final int CURRENT_VERSION = ([0-9]+);\s*$')
    if ($versionMatch.Count -ne 1) { Fail 'PackForgeConfig source must declare one numeric CURRENT_VERSION.' }
    $currentVersion = [int] $versionMatch[0].Matches[0].Groups[1].Value
    $defaults = [ordered]@{}
    foreach ($line in $source) {
        if ($line -notmatch '^\s*public\s+(boolean|int|List<String>)\s+([A-Za-z0-9_]+)\s*=\s*(.+);\s*$') { continue }
        $type = $Matches[1]
        $name = $Matches[2]
        $expression = $Matches[3].Trim()
        if ($defaults.Contains($name)) { Fail "PackForgeConfig source duplicates serialized field '$name'." }
        $value = switch ($type) {
            'boolean' {
                if ($expression -cnotin @('true', 'false')) { Fail "PackForgeConfig boolean '$name' has unsupported initializer '$expression'." }
                $expression -ceq 'true'
            }
            'int' {
                if ($expression -ceq 'CURRENT_VERSION') { $currentVersion }
                elseif ($expression -match '^-?[0-9]+$') { [int] $expression }
                else { Fail "PackForgeConfig integer '$name' has unsupported initializer '$expression'." }
            }
            'List<String>' {
                if ($expression -notmatch '^new ArrayList<>\(List\.of\((.*)\)\)$') {
                    Fail "PackForgeConfig list '$name' has unsupported initializer '$expression'."
                }
                $listBody = $Matches[1]
                try {
                    @(("[$listBody]" | ConvertFrom-Json))
                } catch {
                    Fail "PackForgeConfig list '$name' has invalid string literals."
                }
            }
        }
        $defaults[$name] = $value
    }
    return $defaults
}

function Assert-PackForgeConfigBaseline([string] $Path) {
    $sourceDefaults = Get-PackForgeConfigSourceDefaults $Path
    $baseline = New-CompatibilityBaseConfig
    $sourceKeys = @($sourceDefaults.Keys)
    $baselineKeys = @($baseline.Keys)
    if ($sourceKeys.Count -ne 50 -or $baselineKeys.Count -ne 50 -or
        @(Compare-Object -ReferenceObject $sourceKeys -DifferenceObject $baselineKeys).Count -ne 0) {
        Fail "compatibility smoke baseline must match all 50 serialized PackForgeConfig.Cfg fields exactly; source=$($sourceKeys.Count) baseline=$($baselineKeys.Count)."
    }
    if ($sourceDefaults.loaderTimingsEnabled -ne $false -or $sourceDefaults.startupStatusOverlayEnabled -ne $true) {
        Fail 'documented smoke deltas require Java defaults loaderTimingsEnabled=false and startupStatusOverlayEnabled=true.'
    }
    $expected = [ordered]@{}
    foreach ($key in $sourceKeys) { $expected[$key] = $sourceDefaults[$key] }
    $expected.loaderTimingsEnabled = $true
    $expected.startupStatusOverlayEnabled = $false
    foreach ($key in $sourceKeys) {
        $actualJson = $baseline[$key] | ConvertTo-Json -Compress -Depth 5
        $expectedJson = $expected[$key] | ConvertTo-Json -Compress -Depth 5
        if ($actualJson -cne $expectedJson) {
            Fail "compatibility smoke baseline field '$key' differs from PackForgeConfig.Cfg default outside the two documented smoke deltas; expected=$expectedJson actual=$actualJson."
        }
    }
    $baselineJson = $baseline | ConvertTo-Json
    $roundTripJson = ($baselineJson | ConvertFrom-Json) | ConvertTo-Json
    if ((Get-Sha256Text $baselineJson) -cne (Get-Sha256Text $roundTripJson)) {
        Fail 'compatibility smoke baseline is not JSON serialization/hash stable.'
    }
}

function Require-Text($Object, [string] $Name, [string] $Context) {
    $value = Value $Object $Name
    if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) { Fail "$Context requires non-empty string '$Name'." }
    return $value.Trim()
}

function Assert-RuntimeLoaderVersion($Profile, [string] $Loader, [string] $Context) {
    $value = Value $Profile 'runtimeLoaderVersion'
    if ($null -eq $value) { return $null }
    if ($Loader -ne 'fabric') { Fail "$Context runtimeLoaderVersion is only supported for Fabric profiles." }
    if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace([string] $value)) {
        Fail "$Context runtimeLoaderVersion must be a non-empty Fabric Loader version."
    }
    $version = ([string] $value).Trim()
    if ($version -notmatch '^[0-9]+\.[0-9]+(?:\.[0-9]+)?$') {
        Fail "$Context runtimeLoaderVersion '$version' is not a valid Fabric Loader version."
    }
    return $version
}

function Assert-UniqueStrings($Values, [string] $Context) {
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($value in @(Assert-JsonArray $Values $Context)) {
        if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) { Fail "$Context contains an empty or non-string value." }
        if (-not $seen.Add($value)) { Fail "$Context contains duplicate '$value'." }
    }
    Write-Output -NoEnumerate $seen
}

function Assert-ExactStringSet($Actual, $Expected, [string] $Context) {
    $actualSet = Assert-UniqueStrings $Actual $Context
    $expectedSet = Assert-UniqueStrings $Expected "frozen $Context"
    if ($actualSet.Count -ne $expectedSet.Count) { Fail "$Context must contain exactly $($expectedSet.Count) frozen recipe IDs." }
    foreach ($value in $expectedSet) {
        if (-not $actualSet.Contains($value)) { Fail "$Context is missing frozen recipe '$value'." }
    }
    Write-Output -NoEnumerate $actualSet
}

function Test-PlaceholderText([string] $Text) {
    return [string]::IsNullOrWhiteSpace($Text) -or $Text -match '(?i)(?:placeholder|pending|todo|tbd|unknown|example|change[-_ ]?me|replace[-_ ]?me|<[^>]+>)'
}

function Assert-NonPlaceholderText($Object, [string] $Name, [string] $Context) {
    $text = Require-Text $Object $Name $Context
    if (Test-PlaceholderText $text) { Fail "$Context has placeholder '$Name'." }
    return $text
}

function Test-NonZeroSha256([string] $Value) {
    if ($Value -notmatch '^[A-F0-9]{64}$' -or $Value -match '^0{64}$') { return $false }
    foreach ($length in 1..32) {
        if (64 % $length -ne 0) { continue }
        $block = $Value.Substring(0, $length)
        if (($block * (64 / $length)) -eq $Value) { return $false }
    }
    return $true
}

function Assert-NonPlaceholderHttpsUrl($Object, [string] $Name, [string] $Context) {
    $url = Assert-NonPlaceholderText $Object $Name $Context
    try { $uri = [Uri] $url } catch { Fail "$Context has invalid URL '$Name'." }
    if (-not $uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($uri.Host)) { Fail "$Context requires HTTPS '$Name'." }
	$urlHost = $uri.DnsSafeHost.ToLowerInvariant()
	if ($urlHost -in @('localhost', 'localhost.localdomain', 'local') -or $urlHost -match '(?:^|\.)?(?:example|invalid|test|localhost|local)(?:\.|$)') {
		Fail "$Context has reserved or local host '$urlHost' in '$Name'."
	}
	$address = $null
	if ([Net.IPAddress]::TryParse($urlHost, [ref] $address)) {
        $bytes = $address.GetAddressBytes()
        $isLocalAddress = $address.Equals([Net.IPAddress]::IPv6Loopback) -or
            ($bytes.Length -eq 4 -and ($bytes[0] -eq 10 -or $bytes[0] -eq 127 -or ($bytes[0] -eq 169 -and $bytes[1] -eq 254) -or ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or ($bytes[0] -eq 192 -and $bytes[1] -eq 168))) -or
            ($bytes.Length -eq 16 -and (($bytes[0] -eq 0xFE -and ($bytes[1] -band 0xC0) -eq 0x80) -or ($bytes[0] -band 0xFE) -eq 0xFC))
		if ($isLocalAddress) { Fail "$Context has local IP host '$urlHost' in '$Name'." }
    }
    return $url
}

function Assert-SafeMetadata($Value, [string] $Context) {
    if ($Value -is [string]) {
        $text = [string] $Value
        if ($text -match '^(?:[A-Za-z]:[\\/]|\\\\|/|file:)') { Fail "$Context contains a local path or file URI." }
        if ($text -notmatch '^https?://' -and $text -match '\.(?:jar|zip)(?:$|[?#])') { Fail "$Context names a local binary." }
        if ($text -match '(?i)(?:^|[\\/])(?:build|mods|libraries)(?:[\\/]|$)') { Fail "$Context contains a local artifact path." }
        return
    }
    if ($null -eq $Value) { return }
    if ($Value -is [Collections.IEnumerable]) {
        foreach ($item in $Value) { Assert-SafeMetadata $item $Context }
        return
    }
    foreach ($property in $Value.PSObject.Properties) { Assert-SafeMetadata $property.Value "$Context.$($property.Name)" }
}

function Test-AnyPinField($Mod) {
    return $null -ne (Value $Mod 'coordinate') -or $null -ne (Value $Mod 'sourceUrl') -or $null -ne (Value $Mod 'version') -or $null -ne (Value $Mod 'sha256')
}

function Assert-FullPin($Mod, [string] $Context) {
    Assert-JsonObject $Mod $Context | Out-Null
    Assert-NonPlaceholderText $Mod 'id' $Context | Out-Null
    $coordinate = Assert-NonPlaceholderText $Mod 'coordinate' $Context
    if ($coordinate -notmatch '^(?:(?:modrinth|curseforge):[a-z0-9][a-z0-9._-]{1,127}|maven:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+|github:[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)$') {
        Fail "$Context has unsupported or weak coordinate '$coordinate'."
    }
    Assert-NonPlaceholderHttpsUrl $Mod 'sourceUrl' $Context | Out-Null
    $version = Assert-NonPlaceholderText $Mod 'version' $Context
    if ($version -notmatch '^(?=.*[0-9])[A-Za-z0-9][A-Za-z0-9._+~-]{0,127}$') { Fail "$Context has weak version '$version'." }
    $sha256 = Require-Text $Mod 'sha256' $Context
    if (-not (Test-NonZeroSha256 $sha256)) { Fail "$Context requires non-trivial uppercase SHA-256." }
}

function Assert-PinState($Mod, [string] $Availability, [string] $Context) {
    if ($Availability -eq 'AVAILABLE') {
        Assert-FullPin $Mod $Context
        return
    }
    if (Test-AnyPinField $Mod) { Assert-FullPin $Mod $Context }
}

function Assert-Evidence($Evidence, [string] $Context) {
    Assert-JsonObject $Evidence "$Context evidence" | Out-Null
    $hasArtifactRecord = $false
    foreach ($record in @(
        @{ Path = 'resultsPath'; Sha = 'resultsSha256' },
        @{ Path = 'failureLogPath'; Sha = 'failureLogSha256' }
    )) {
        $path = Value $Evidence $record.Path
        $sha = Value $Evidence $record.Sha
        if ($null -ne $path -or $null -ne $sha) {
            if ($path -isnot [string] -or [string]::IsNullOrWhiteSpace($path) -or $path -match '^(?:[A-Za-z]:[\\/]|\\\\|/|file:|https?://)' -or $path -match '\.\.(?:[\\/]|$)') {
                Fail "$Context evidence '$($record.Path)' must be a safe relative path."
            }
            if ($sha -isnot [string] -or -not (Test-NonZeroSha256 $sha)) { Fail "$Context evidence '$($record.Sha)' must be a non-trivial uppercase SHA-256." }
            $candidate = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $path))
            $rootPrefix = $RepositoryRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
            if (-not $candidate.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                Fail "$Context evidence '$($record.Path)' must resolve to an existing repository file."
            }
            $resolved = (Resolve-Path -LiteralPath $candidate).Path
            if (-not $resolved.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) { Fail "$Context evidence '$($record.Path)' escapes repository root." }
            $actualSha = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash
            if ($actualSha -ne $sha) { Fail "$Context evidence '$($record.Sha)' does not match '$($record.Path)'." }
            $hasArtifactRecord = $true
        }
    }
    $provenance = Value $Evidence 'provenance'
    if ($null -ne $provenance) {
        Assert-JsonObject $provenance "$Context evidence.provenance" | Out-Null
        $commit = Value $provenance 'commit'
        $artifactSha = Value $provenance 'artifactSha256'
        $reportUrl = Value $provenance 'reportUrl'
        if ($null -ne $commit -and ($commit -isnot [string] -or $commit -notmatch '^[a-fA-F0-9]{40}$')) { Fail "$Context evidence.provenance has invalid commit." }
        if ($null -ne $artifactSha -and ($artifactSha -isnot [string] -or -not (Test-NonZeroSha256 $artifactSha))) { Fail "$Context evidence.provenance has invalid artifactSha256." }
        if ($null -ne $reportUrl) { Assert-NonPlaceholderHttpsUrl $provenance 'reportUrl' "$Context evidence.provenance" | Out-Null }
    }
    if (-not $hasArtifactRecord) { Fail "$Context evidence requires an existing immutable result path plus matching SHA-256; provenance alone does not prove execution." }
}

function Assert-AvailabilityEvidence($Evidence, [string] $Context) {
    Assert-JsonObject $Evidence "$Context availabilityEvidence" | Out-Null
    $checkedOn = Assert-NonPlaceholderText $Evidence 'checkedOn' "$Context availabilityEvidence"
    $parsedDate = [datetime]::MinValue
    if (-not [datetime]::TryParseExact($checkedOn, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref] $parsedDate)) {
        Fail "$Context availabilityEvidence has invalid checkedOn date."
    }
    $sources = Assert-JsonArray (Value $Evidence 'sources') "$Context availabilityEvidence.sources"
    if ($sources.Count -eq 0) { Fail "$Context availabilityEvidence.sources must not be empty." }
    foreach ($source in $sources) {
        Assert-JsonObject $source "$Context availabilityEvidence source" | Out-Null
        Assert-NonPlaceholderHttpsUrl $source 'sourceUrl' "$Context availabilityEvidence source" | Out-Null
        Assert-NonPlaceholderText $source 'query' "$Context availabilityEvidence source" | Out-Null
        Assert-NonPlaceholderText $source 'result' "$Context availabilityEvidence source" | Out-Null
    }
}

function Assert-Reason($Profile, [string] $Context) {
    $reason = Require-Text $Profile 'reason' $Context
    if (Test-PlaceholderText $reason) { Fail "$Context reason is placeholder text." }
}

function Invoke-CatalogValidation($Catalog, $Registry) {
    Assert-JsonObject $Catalog 'catalog' | Out-Null
    Assert-JsonObject $Registry 'registry' | Out-Null
    if ([int] (Value $Catalog 'schemaVersion') -ne 1) { Fail "unsupported schemaVersion '$($Catalog.schemaVersion)'." }

    $catalogRequiredIds = Assert-ExactStringSet (Value $Catalog 'requiredRecipeIds') $CanonicalRecipeIds 'requiredRecipeIds'
    $profiles = Assert-JsonArray (Value $Catalog 'profiles') 'profiles'
    if ($profiles.Count -eq 0) { Fail 'profiles must not be empty.' }
    $profileIds = Assert-UniqueStrings @($profiles | ForEach-Object { Require-Text $_ 'id' 'profile' }) 'profile ids'
    Assert-ExactStringSet @($profileIds) $CanonicalRecipeIds 'profile ids' | Out-Null

    $targetsByKey = @{}
    foreach ($target in @(Assert-JsonArray (Value $Registry 'targets') 'registry.targets')) {
        $targetsByKey[[string] $target.key] = $target
    }
    $availabilityStates = @('AVAILABLE', 'PENDING_METADATA', 'UNAVAILABLE')
    $successfulResults = @('FULL_OPTIMIZED_PATH', 'HOOK_PRESERVING_COALESCED_PATH', 'SAFE_ORIGINAL_PATH', 'EXTERNALLY_OWNED_PATH')
    $outcomes = @($successfulResults + @('UNAVAILABLE', 'UNTESTED', 'FAILED'))

    foreach ($profile in $profiles) {
        Assert-JsonObject $profile 'profile' | Out-Null
        $id = Require-Text $profile 'id' 'profile'
        $release = Require-Text $profile 'minecraftVersion' "profile '$id'"
        $loader = Require-Text $profile 'loader' "profile '$id'"
        if ($loader -notin @('fabric', 'forge', 'neoforge')) { Fail "profile '$id' has unsupported loader '$loader'." }
        [void] (Assert-RuntimeLoaderVersion $profile $loader "profile '$id'")
        $artifact = Assert-JsonObject (Value $profile 'packForgeArtifact') "profile '$id' packForgeArtifact"
        $targetKey = Require-Text $artifact 'targetKey' "profile '$id' packForgeArtifact"
        $target = $targetsByKey[$targetKey]
        if ($null -eq $target) { Fail "profile '$id' references unknown target '$targetKey'." }
        $exactSmokeVersions = Assert-JsonArray (Value $target 'requiredExactSmokeVersions') "target '$targetKey' requiredExactSmokeVersions"
        $loaderAvailability = Assert-JsonArray (Value $target 'loaderAvailability') "target '$targetKey' loaderAvailability"
        if ($release -notin $exactSmokeVersions) { Fail "profile '$id' release '$release' is not an exact cell of '$targetKey'." }
        if ($loader -notin $loaderAvailability) { Fail "profile '$id' loader '$loader' is not available for '$targetKey'." }

        $availability = Require-Text $profile 'availability' "profile '$id'"
        $result = Require-Text $profile 'result' "profile '$id'"
        if ($availability -notin $availabilityStates) { Fail "profile '$id' has unsupported availability '$availability'." }
        if ($result -notin $outcomes) { Fail "profile '$id' has unsupported result '$result'." }
        if ($availability -eq 'PENDING_METADATA' -and $result -ne 'UNTESTED') { Fail "profile '$id' pending metadata must be UNTESTED." }
        if ($availability -eq 'UNAVAILABLE' -and $result -ne 'UNAVAILABLE') { Fail "profile '$id' unavailable metadata must be UNAVAILABLE." }
        if ($availability -eq 'AVAILABLE' -and $result -eq 'UNAVAILABLE') { Fail "profile '$id' available metadata cannot be UNAVAILABLE." }
        if ($availability -ne 'AVAILABLE' -or $result -eq 'FAILED') { Assert-Reason $profile "profile '$id'" }
        if ($availability -eq 'UNAVAILABLE') {
            Assert-AvailabilityEvidence (Value $profile 'availabilityEvidence') "profile '$id'"
        } elseif ($null -ne (Value $profile 'availabilityEvidence')) {
            Fail "profile '$id' only permits availabilityEvidence when availability is UNAVAILABLE."
        }

        $mods = Assert-JsonArray (Value $profile 'externalMods') "profile '$id' externalMods"
        if ($mods.Count -eq 0) { Fail "profile '$id' must declare at least one external mod." }
        [void] (Assert-UniqueStrings @($mods | ForEach-Object { Require-Text $_ 'id' "profile '$id' external mod" }) "profile '$id' external mod ids")
        foreach ($mod in $mods) { Assert-PinState $mod $availability "profile '$id' external mod '$($mod.id)'" }

        $dependencies = Assert-JsonArray (Value $profile 'dependencies') "profile '$id' dependencies"
        [void] (Assert-UniqueStrings @($dependencies | ForEach-Object { Require-Text $_ 'id' "profile '$id' dependency" }) "profile '$id' dependency ids")
        foreach ($dependency in $dependencies) { Assert-PinState $dependency $availability "profile '$id' dependency '$($dependency.id)'" }
        $runtimeModIds = @(
            $mods | ForEach-Object { [string] $_.id }
            $dependencies | ForEach-Object { [string] $_.id }
        )
        [void] (Assert-UniqueStrings $runtimeModIds "profile '$id' runtime mod ids")
        $featureOverrides = Value $profile 'featureOverrides'
        Assert-FeatureOverrides $featureOverrides "profile '$id' featureOverrides"

        $fixture = Require-Text $profile 'fixture' "profile '$id'"
        $path = Require-Text $profile 'expectedPath' "profile '$id'"
        if ($path -notin $successfulResults) { Fail "profile '$id' has unsupported expectedPath '$path'." }
        $expectedMarkers = Assert-UniqueStrings (Value $profile 'expectedLogMarkers') "profile '$id' expectedLogMarkers"
        $forbiddenMarkers = Assert-UniqueStrings (Value $profile 'forbiddenLogMarkers') "profile '$id' forbiddenLogMarkers"
        $reporterMarker = "PackForge compatibility profile: id=$id"
        if (-not $expectedMarkers.Contains($reporterMarker)) {
            Fail "profile '$id' must require its exact compatibility reporter marker."
        }
        foreach ($marker in @($expectedMarkers) + @($forbiddenMarkers)) {
            if ($marker -match '^profile:') { Fail "profile '$id' contains synthetic marker '$marker'." }
        }
        foreach ($marker in $expectedMarkers) {
            if ($forbiddenMarkers.Contains($marker)) { Fail "profile '$id' marker '$marker' is both expected and forbidden." }
        }
        if ($availability -eq 'AVAILABLE') {
            foreach ($runtimeModId in @($runtimeModIds | Sort-Object)) {
                $modMarker = "${runtimeModId}:true:"
                if (-not $expectedMarkers.Contains($modMarker)) {
                    Fail "profile '$id' available metadata must require loader-observed mod marker '$modMarker'."
                }
            }
        }
        if ($fixture -eq $path -or $expectedMarkers.Contains($fixture) -or $forbiddenMarkers.Contains($fixture)) { Fail "profile '$id' fixture overlaps its path or marker contract." }

        if ($result -in $successfulResults) {
            if ($availability -ne 'AVAILABLE') { Fail "profile '$id' successful result requires AVAILABLE metadata." }
            if ($result -ne $path) { Fail "profile '$id' successful result '$result' must equal expectedPath '$path'." }
            Assert-Evidence (Value $profile 'evidence') "profile '$id'"
        }
        if ($result -eq 'FAILED') { Assert-Evidence (Value $profile 'evidence') "profile '$id'" }
        if ($result -eq 'UNTESTED' -and $availability -eq 'UNAVAILABLE') { Fail "profile '$id' unavailable metadata cannot remain UNTESTED." }
        Assert-SafeMetadata $profile "profile '$id'"
    }

    return [pscustomobject]@{ ProfileCount = $profiles.Count; RequiredCount = $catalogRequiredIds.Count }
}

function Copy-JsonObject($Object) {
    return ($Object | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function Assert-MutationRejected([string] $Name, [scriptblock] $Mutation, $Catalog, $Registry) {
    $candidate = Copy-JsonObject $Catalog
    & $Mutation $candidate
    try {
        Invoke-CatalogValidation $candidate $Registry | Out-Null
    } catch {
        return
    }
    Fail "self-test '$Name' was accepted."
}

function Invoke-SelfTests($Catalog, $Registry) {
	Invoke-CatalogValidation $Catalog $Registry | Out-Null
	$positive = Copy-JsonObject $Catalog
	$positiveProfile = @($positive.profiles | Where-Object id -eq 'fabric-sodium')[0]
	$positiveProfile.result = $positiveProfile.expectedPath
	$validatorRelativePath = [IO.Path]::GetRelativePath($RepositoryRoot, $PSCommandPath).Replace('\', '/')
	$validatorSha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash
	$positiveProfile | Add-Member -NotePropertyName evidence -NotePropertyValue ([pscustomobject]@{
		resultsPath = $validatorRelativePath
		resultsSha256 = $validatorSha256
	}) -Force
	Invoke-CatalogValidation $positive $Registry | Out-Null
    Assert-MutationRejected 'catalog-can-drop-required-id' { param($c) $c.requiredRecipeIds = @($c.requiredRecipeIds | Select-Object -Skip 1) } $Catalog $Registry
    Assert-MutationRejected 'catalog-can-add-noncanonical-profile' { param($c) $c.profiles[0].id = 'fabric-untracked-profile' } $Catalog $Registry
    Assert-MutationRejected 'dependencies-must-be-array' { param($c) ($c.profiles | Where-Object id -eq 'fabric-sodium').dependencies = [pscustomobject]@{} } $Catalog $Registry
    Assert-MutationRejected 'feature-overrides-must-be-object' { param($c) ($c.profiles | Where-Object id -eq 'fabric-sodium').featureOverrides = 'enabled' } $Catalog $Registry
    Assert-MutationRejected 'feature-overrides-reject-unknown-key' { param($c) ($c.profiles | Where-Object id -eq 'fabric-sodium').featureOverrides = [pscustomobject]@{ atlasMipParallelEnabled = $true } } $Catalog $Registry
    Assert-MutationRejected 'feature-overrides-reject-null' { param($c) ($c.profiles | Where-Object id -eq 'fabric-sodium').featureOverrides = [pscustomobject]@{ atlasRetryEnabled = $null } } $Catalog $Registry
    Assert-MutationRejected 'feature-overrides-reject-wrong-json-type' { param($c) ($c.profiles | Where-Object id -eq 'fabric-sodium').featureOverrides = [pscustomobject]@{ atlasRetryEnabled = 1 } } $Catalog $Registry
    Assert-MutationRejected 'runtime-loader-version-requires-fabric' { param($c) ($c.profiles | Where-Object id -eq 'forge-quick-pack') | Add-Member -NotePropertyName runtimeLoaderVersion -NotePropertyValue '0.17.3' -Force } $Catalog $Registry
    Assert-MutationRejected 'runtime-loader-version-must-be-numeric' { param($c) ($c.profiles | Where-Object id -eq 'fabric-quick-pack').runtimeLoaderVersion = 'latest' } $Catalog $Registry
    Assert-MutationRejected 'available-pins-must-be-complete' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.externalMods[0].PSObject.Properties.Remove('sha256') } $Catalog $Registry
    Assert-MutationRejected 'available-requires-reporter-marker' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.expectedLogMarkers = @($p.expectedLogMarkers | Where-Object { $_ -notlike 'PackForge compatibility profile: id=*' }) } $Catalog $Registry
    Assert-MutationRejected 'available-requires-loader-observed-mod' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.expectedLogMarkers = @($p.expectedLogMarkers | Where-Object { $_ -ne 'sodium:true:' }) } $Catalog $Registry
    Assert-MutationRejected 'synthetic-profile-marker-is-forbidden' { param($c) $c.profiles[1].forbiddenLogMarkers += ('profile' + ':fabric-sodium:failed') } $Catalog $Registry
    Assert-MutationRejected 'unavailable-requires-availability-evidence' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.availability = 'UNAVAILABLE'; $p.result = 'UNAVAILABLE'; $p | Add-Member -NotePropertyName reason -NotePropertyValue 'No compatible artifact was published.' -Force } $Catalog $Registry
    Assert-MutationRejected 'unavailable-evidence-requires-safe-source' { param($c) ($c.profiles | Where-Object id -eq 'fabric-resource-pack-unbounded').availabilityEvidence.sources[0].sourceUrl = 'https://example.invalid/release' } $Catalog $Registry
    Assert-MutationRejected 'pending-pins-cannot-be-partial' { param($c) ($c.profiles | Where-Object id -eq 'fabric-quick-pack').externalMods[0] | Add-Member -NotePropertyName coordinate -NotePropertyValue 'modrinth:pSISfJ4O' } $Catalog $Registry
    Assert-MutationRejected 'sha-cannot-be-zero' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.externalMods[0].sha256 = ('0' * 64) } $Catalog $Registry
    Assert-MutationRejected 'sha-cannot-repeat-pattern' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.externalMods[0].sha256 = ('ABCDEF01' * 8) } $Catalog $Registry
    Assert-MutationRejected 'pin-cannot-use-reserved-host' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.externalMods[0].sourceUrl = 'https://example.invalid/mod.jar' } $Catalog $Registry
    Assert-MutationRejected 'pin-cannot-use-weak-coordinate-or-version' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.externalMods[0].coordinate = 'latest'; $p.externalMods[0].version = 'latest' } $Catalog $Registry
    Assert-MutationRejected 'successful-result-requires-evidence' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.result = $p.expectedPath } $Catalog $Registry
    Assert-MutationRejected 'evidence-must-exist' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.result = $p.expectedPath; $p | Add-Member -NotePropertyName evidence -NotePropertyValue ([pscustomobject]@{ resultsPath = 'missing-profile-result.json'; resultsSha256 = '9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08' }) -Force } $Catalog $Registry
    Assert-MutationRejected 'evidence-hash-must-match-file' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.result = $p.expectedPath; $p | Add-Member -NotePropertyName evidence -NotePropertyValue ([pscustomobject]@{ resultsPath = 'scripts/Validate-CompatibilityProfileCatalog.ps1'; resultsSha256 = '9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08'; provenance = [pscustomobject]@{ commit = ('A' * 40) } }) -Force } $Catalog $Registry
    Assert-MutationRejected 'failed-result-requires-reason-and-evidence' { param($c) $p = $c.profiles | Where-Object id -eq 'fabric-sodium'; $p.result = 'FAILED'; $p.PSObject.Properties.Remove('reason') } $Catalog $Registry
	$mutatedConfigPath = Join-Path ([IO.Path]::GetTempPath()) ("packforge-config-default-mutation-" + [guid]::NewGuid().ToString('N') + '.java')
	try {
		$mutatedSource = (Get-Content -LiteralPath $PackForgeConfigPath -Raw).Replace(
			'public boolean largeAtlasFixerEnabled = true;',
			'public boolean largeAtlasFixerEnabled = false;')
		[IO.File]::WriteAllText($mutatedConfigPath, $mutatedSource, [Text.UTF8Encoding]::new($false))
		try {
			Assert-PackForgeConfigBaseline $mutatedConfigPath
			Fail "self-test 'PackForgeConfig boolean default drift' was accepted."
		} catch {
			if ($_.Exception.Message -notmatch "largeAtlasFixerEnabled.*differs") { throw }
		}
	} finally {
		if (Test-Path -LiteralPath $mutatedConfigPath -PathType Leaf) { Remove-Item -LiteralPath $mutatedConfigPath -Force }
	}
	Write-Output 'Compatibility profile catalog self-test PASS: positive AVAILABLE control accepted; 24 catalog mutations plus PackForgeConfig boolean-default drift rejected.'
}

foreach ($path in @($CatalogPath, $RegistryPath, $PackForgeConfigPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "missing required file '$path'." }
}
Assert-PackForgeConfigBaseline $PackForgeConfigPath
try { $catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json } catch { Fail "catalog is not valid JSON: $($_.Exception.Message)" }
try { $registry = Get-Content -LiteralPath $RegistryPath -Raw | ConvertFrom-Json } catch { Fail "registry is not valid JSON: $($_.Exception.Message)" }
$summary = Invoke-CatalogValidation $catalog $registry
if ($SelfTest) { Invoke-SelfTests $catalog $registry }
Write-Output "Compatibility profile catalog PASS: $($summary.ProfileCount) profiles; $($summary.RequiredCount) frozen recipes; all registry cells supported."
