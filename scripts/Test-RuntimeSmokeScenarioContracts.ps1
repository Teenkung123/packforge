[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

function Assert-Contains {
    param([string] $Text, [string] $Pattern, [string] $Context)
    if ($Text -notmatch $Pattern) {
        throw "$Context is missing required contract '$Pattern'."
    }
}

$fabric = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'Smoke-Fabric-Production.ps1') -Raw
$forge = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'Smoke-Forge-Production.ps1') -Raw
$neoForge = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'Smoke-NeoForge-Production.ps1') -Raw
$scenarioController = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\common\src\client\java\com\teenkung\packforge\client\RuntimeSmokeScenarioController.java') -Raw
$scenarioCore = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\common\src\main\java\com\teenkung\packforge\loader\RuntimeSmokeScenario.java') -Raw
$minecraftSeam = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\common\src\client\java\com\teenkung\packforge\client\mixin\observe\MinecraftRuntimeSmokeMixin.java') -Raw
$minecraft26Seam = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\versions\mc26\common\src\client\java\com\teenkung\packforge\client\mixin\observe\MinecraftRuntimeSmoke26Mixin.java') -Raw

foreach ($loader in @([pscustomobject]@{ Name = 'Fabric'; Text = $fabric }, [pscustomobject]@{ Name = 'Forge'; Text = $forge })) {
    foreach ($scenario in @('cancel-in-flight', 'forced-resource-failure', 'retry-success', 'retry-exhaustion')) {
        Assert-Contains -Text $loader.Text -Pattern ([regex]::Escape($scenario)) -Context "$($loader.Name) wrapper"
    }
    Assert-Contains -Text $loader.Text -Pattern 'RuntimeSmokeScenarioEvidence\.ps1' -Context "$($loader.Name) wrapper"
    Assert-Contains -Text $loader.Text -Pattern 'packforge\.runtimeSmokeScenario' -Context "$($loader.Name) JVM transport"
    Assert-Contains -Text $loader.Text -Pattern 'Resolve-PackForgeRuntimeSmokeScenarioEvidence' -Context "$($loader.Name) evidence gate"
    Assert-Contains -Text $loader.Text -Pattern 'Non-repeat runtime smoke scenarios require' -Context "$($loader.Name) safety guard"
}
Assert-Contains -Text $neoForge -Pattern 'RuntimeSmokeScenario = \$RuntimeSmokeScenario' -Context 'NeoForge wrapper forwarding'
Assert-Contains -Text $scenarioController -Pattern 'runtime smoke scenario PASS' -Context 'Java scenario PASS marker'
Assert-Contains -Text $scenarioController -Pattern 'runtime smoke scenario FAIL' -Context 'Java scenario FAIL marker'
Assert-Contains -Text $scenarioController -Pattern 'overlayCleared' -Context 'Java stale overlay cleanup evidence'
Assert-Contains -Text $scenarioController -Pattern 'minecraft\.execute' -Context 'Java client-thread dispatch'
Assert-Contains -Text $scenarioCore -Pattern 'consumeFailure' -Context 'Java failure injection primitive'
Assert-Contains -Text $scenarioCore -Pattern 'injectFailureAfter' -Context 'Java future-boundary failure injection'
Assert-Contains -Text $scenarioController -Pattern 'RuntimeSmokeMinecraftCompat\.reloadForScenario' -Context 'Java cached-reload seam transport'
Assert-Contains -Text $minecraftSeam -Pattern 'pendingReload' -Context 'Java cached-reload seam'
Assert-Contains -Text $minecraftSeam -Pattern 'rollbackResourcePacks' -Context 'Java failure rollback guard'
Assert-Contains -Text $minecraft26Seam -Pattern 'MinecraftGuiCompat\.setOverlay' -Context '26.x GUI ownership bridge'

Write-Output 'Runtime smoke scenario contract self-test PASS: Fabric/Forge/NeoForge selectors, JVM transport, evidence gates, inert guard, client-thread dispatch, and Java PASS/FAIL/injection contracts verified.'
