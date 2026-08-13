Set-StrictMode -Version 2.0

$CompatibilityFeatureOverrideKeys = @(
    'loaderZipPoolEnabled',
    'fontBitmapProviderCacheEnabled',
    'atlasDecodeBatchingEnabled',
    'atlasRetryEnabled',
    'startupExecutorTuningEnabled'
)

function ConvertTo-NormalizedFeatureOverrides {
    param($Overrides, [string] $Context)

    if ($null -eq $Overrides -or
        ($Overrides -isnot [pscustomobject] -and $Overrides -isnot [Collections.IDictionary])) {
        throw "$Context must be a JSON object."
    }
    $properties = if ($Overrides -is [Collections.IDictionary]) {
        @($Overrides.Keys | ForEach-Object { [pscustomobject]@{ Name = [string] $_; Value = $Overrides[$_] } })
    } else {
        @($Overrides.PSObject.Properties)
    }
    $values = @{}
    foreach ($property in $properties) {
        if ([string] $property.Name -cnotin $CompatibilityFeatureOverrideKeys) {
            throw "$Context contains unsupported key '$($property.Name)'. Allowed keys: $($CompatibilityFeatureOverrideKeys -join ', ')."
        }
        if ($null -eq $property.Value -or $property.Value -isnot [bool]) {
            throw "$Context key '$($property.Name)' must be a JSON boolean."
        }
        $values[[string] $property.Name] = [bool] $property.Value
    }
    $normalized = [ordered]@{}
    foreach ($key in $CompatibilityFeatureOverrideKeys) {
        if ($values.ContainsKey($key)) { $normalized[$key] = $values[$key] }
    }
    return [pscustomobject] $normalized
}

function New-CompatibilityBaseConfig {
    # Full PackForgeConfig.Cfg v12 serialization. Only two smoke-observability
    # values intentionally differ from production defaults: loader timings are
    # enabled and the startup status overlay is disabled.
    return [ordered]@{
        configVersion = 12
        reloadOptimizerEnabled = $true
        largeAtlasFixerEnabled = $true
        loaderIndexEnabled = $true
        loaderZipPoolEnabled = $false
        loaderTimingsEnabled = $true
        reloadListenerTimingsEnabled = $false
        shaderApplyStallDiagnosticsEnabled = $true
        immediatelyFastFontAtlasCompatEnabled = $true
        loadingStatusOverlayEnabled = $true
        loadingScreenFadeOutDisabled = $false
        reloadSummaryToastEnabled = $false
        modelUvTransparencyClampEnabled = $true
        fontReloadDiagnosticsEnabled = $false
        fontPrepareProviderSelectionEnabled = $true
        fontBitmapProviderCacheEnabled = $false
        atlasPhaseTimingsEnabled = $false
        atlasMipParallelEnabled = $false
        atlasMipBatchSize = 128
        atlasDecodeBatchingEnabled = $false
        atlasDecodeBatchSize = 128
        modelParseBatchingEnabled = $true
        modelParseBatchSize = 64
        modelParseTimingEnabled = $false
        modelAdaptiveBatchingEnabled = $false
        modelDuplicateParseCacheEnabled = $false
        atlasCapEnabled = $true
        atlasCapPx = 256
        atlasExcludeIds = @('minecraft:gui')
        atlasRetryEnabled = $false
        atlasRetryMaxAttempts = 2
        forceDisablePartIIIWithIris = $true
        experimentalAtlasSplit = $false
        atlasSplitTargets = @('minecraft:items', 'minecraft:particles')
        atlasSplitMaxTiers = 1
        atlasSplitFallbackToDownscale = $true
        atlasSplitDisableWithIris = $true
        atlasSplitDisableWithSodium = $false
        atlasSplitModelCoherence = $true
        atlasSplitDiagnostics = $true
        startupOptimizerEnabled = $false
        startupTimingsEnabled = $true
        startupStatusOverlayEnabled = $false
        startupExecutorTuningEnabled = $true
        startupWorkerThreads = 0
        startupThreadPriority = 4
        startupSkipWithSmoothBoot = $true
        startupAsyncDataParsingEnabled = $false
        startupAsyncClassScanEnabled = $false
        startupAsyncFontAtlasEnabled = $false
    }
}

function Merge-CompatibilityProfileConfig {
    param($Overrides)

    $effective = New-CompatibilityBaseConfig
    foreach ($property in $Overrides.PSObject.Properties) {
        $effective[$property.Name] = [bool] $property.Value
    }
    if ($null -ne $Overrides.PSObject.Properties['startupExecutorTuningEnabled']) {
        $effective.startupOptimizerEnabled = [bool] $Overrides.startupExecutorTuningEnabled
    }
    return $effective
}

function Get-Sha256Text {
    param([string] $Text)

    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '')
    } finally {
        $sha256.Dispose()
    }
}

function Get-Schema2ConfigContract {
    param($Profile)

    if ($null -eq $Profile.config -or $null -eq $Profile.config.overrides -or $null -eq $Profile.featureOverrides) {
        throw 'Schema-2 compatibility profile omitted featureOverrides/config transport.'
    }
    $featureOverrides = ConvertTo-NormalizedFeatureOverrides -Overrides $Profile.featureOverrides -Context 'Schema-2 featureOverrides'
    $configOverrides = ConvertTo-NormalizedFeatureOverrides -Overrides $Profile.config.overrides -Context 'Schema-2 config.overrides'
    if (($featureOverrides | ConvertTo-Json -Compress) -cne ($configOverrides | ConvertTo-Json -Compress)) {
        throw 'Schema-2 compatibility profile config overrides differ from featureOverrides.'
    }
    $base = New-CompatibilityBaseConfig
    if ($null -eq $Profile.config.base -or
        ($Profile.config.base | ConvertTo-Json -Compress -Depth 10) -cne ($base | ConvertTo-Json -Compress -Depth 10)) {
        throw 'Schema-2 compatibility profile base config differs from the fixed smoke baseline.'
    }
    $effective = Merge-CompatibilityProfileConfig -Overrides $featureOverrides
    if ($null -eq $Profile.config.effective -or
        ($Profile.config.effective | ConvertTo-Json -Compress -Depth 10) -cne ($effective | ConvertTo-Json -Compress -Depth 10)) {
        throw 'Schema-2 compatibility profile effective config differs from the independently merged config.'
    }
    $json = $effective | ConvertTo-Json
    $sha256 = Get-Sha256Text -Text $json
    if ([string] $Profile.config.sha256 -cne $sha256) {
        throw "Schema-2 compatibility profile effective config SHA-256 mismatch: expected=$($Profile.config.sha256) actual=$sha256"
    }
    return [pscustomobject]@{ Overrides = $featureOverrides; Effective = $effective; Json = $json; Sha256 = $sha256 }
}
