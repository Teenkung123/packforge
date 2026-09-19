[CmdletBinding()]
param(
    [string] $ScriptPath = (Join-Path $PSScriptRoot 'Smoke-Fabric-Production.ps1')
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
    throw "Smoke script is missing: $ScriptPath"
}

$source = Get-Content -LiteralPath $ScriptPath -Raw
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($ScriptPath, [ref] $tokens, [ref] $errors) | Out-Null
if ($errors.Count -gt 0) {
    throw "Smoke script does not parse: $($errors[0].Message)"
}

function Assert-Contract {
    param(
        [bool] $Condition,
        [string] $Message
    )

    if (-not $Condition) { throw "Smoke script contract failed: $Message" }
}

Assert-Contract ($source -match '\[switch\]\s+\$ObserveOnly') 'ObserveOnly switch is declared.'
Assert-Contract ($source -match '\[string\]\s+\$ConfigOverridePath') 'ConfigOverridePath is declared.'
Assert-Contract ($source -match '\[string\]\s+\$OptionsOverridePath') 'OptionsOverridePath is declared.'
Assert-Contract ($source -match '\[string\[\]\]\s+\$AdditionalModPaths') 'AdditionalModPaths is declared.'
Assert-Contract ($source -match 'if \(-not \$ObserveOnly -and -not \$UseRuntimeController -and -not \(') 'native Add-Type setup is excluded from observe-only mode.'
Assert-Contract ($source -match 'if \(\$UseRuntimeController -and -not \$ObserveOnly\)\s*\{\s*\[void\] \$javaArguments\.Add\("-Dpackforge\.runtimeSmokeReloadCount=') 'runtime-controller JVM property is excluded from observe-only mode.'
Assert-Contract ($source -notmatch 'Expand-Archive|System\.IO\.Compression|ZipFile') 'observe-only inputs do not extract protected ZIP contents.'

$observeBranchMatch = [regex]::Match($source, '(?s)if \(\$ObserveOnly\) \{\r?\n\s+\$observeReadyText.*?\} elseif \(\$UseRuntimeController\) \{')
Assert-Contract $observeBranchMatch.Success 'observe-only execution branch is present.'
$observeBranch = $observeBranchMatch.Value
Assert-Contract ($observeBranch -notmatch 'PackForgeFabricProductionSmokeNative|FindMinecraftWindow|IsVisibleAndValid|SendF3T|\.Close\(') 'observe-only branch has no native window or key calls.'
Assert-Contract ($observeBranch -match 'Get-LogMarkerCount') 'observe-only branch counts reload markers from logs.'
Assert-Contract ($observeBranch -match 'observeReloadsVerified') 'observe-only branch records reload verification.'
Assert-Contract ($observeBranch -match 'did not complete a clean exit') 'observe-only branch waits for clean exit.'

Assert-Contract ($source -match 'Copy-IsolatedInput') 'optional profile inputs use the isolated copy helper.'
Assert-Contract ($source -match 'resourcePacksRoot') 'resource packs are staged inside the isolated profile.'
Assert-Contract ($source -match 'additionalMods\s*=') 'additional mod provenance is recorded.'
Assert-Contract ($source -match 'optionsOverride\s*=') 'options override provenance is recorded.'
Assert-Contract ($source -match 'Join-Path \$runRoot ''options\.txt''') 'options override is staged at the isolated profile root.'
Assert-Contract ($source -match 'Copy-Item -LiteralPath \$optionsOverride -Destination \$optionsPath -Force') 'options override is copied without editing the source profile.'
$optionsCopyIndex = $source.IndexOf('Copy-Item -LiteralPath $optionsOverride -Destination $optionsPath -Force', [StringComparison]::Ordinal)
$launchIndex = $source.IndexOf('$started = $process.Start()', [StringComparison]::Ordinal)
Assert-Contract ($optionsCopyIndex -ge 0 -and $optionsCopyIndex -lt $launchIndex) 'options override is staged before the owned client starts.'
Assert-Contract ($source -notmatch 'Move-Item[^\r\n]*\$OptionsOverridePath|Remove-Item[^\r\n]*\$OptionsOverridePath') 'options override staging does not mutate the source profile.'
Assert-Contract ($source -match 'launcherArgumentsPath') 'launcher arguments are persisted for manual inspection.'
Assert-Contract ($source -match 'launcherArgs=\$launcherArgumentsPath') 'launcher arguments path is printed before launch and in the result.'
Assert-Contract ($source -match 'provenance=\$provenancePath') 'artifact provenance path is printed.'
Assert-Contract ($source -match 'manualReloadsVerified=\$ReloadCount') 'manual reload verification is visible in the pass output.'

Write-Output "PASS Smoke-Fabric-Production contract: $([IO.Path]::GetFullPath($ScriptPath))"
