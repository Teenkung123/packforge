[CmdletBinding()]
param(
	[string]$RegistryPath = (Join-Path $PSScriptRoot '..\gradle\minecraft-targets.json'),
	[switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot

function Require-Contract {
	param(
		[Parameter(Mandatory = $true)][bool]$Condition,
		[Parameter(Mandatory = $true)][string]$Message
	)
	if (-not $Condition) {
		throw $Message
	}
}

function Read-RepositoryText {
	param([Parameter(Mandatory = $true)][string]$RelativePath)
	$path = Join-Path $repositoryRoot $RelativePath
	Require-Contract (Test-Path -LiteralPath $path -PathType Leaf) "Missing configuration-screen contract file: $RelativePath"
	return Get-Content -LiteralPath $path -Raw
}

$sharedTargets = @(
	'mc1_20_5', 'mc1_20_6', 'mc1_21', 'mc1_21_1', 'mc1_21_2', 'mc1_21_3', 'mc1_21_4',
	'mc1_21_5', 'mc1_21_6', 'mc1_21_7', 'mc1_21_8', 'mc1_21_9', 'mc1_21_10'
)
$baseTargets = @($sharedTargets + 'mc1_21_11')
$sharedPackSelectionTargets = @(
	'mc1_21', 'mc1_21_1', 'mc1_21_2', 'mc1_21_3', 'mc1_21_4', 'mc1_21_5',
	'mc1_21_6', 'mc1_21_7', 'mc1_21_8', 'mc1_21_9', 'mc1_21_10'
)

function Assert-SharedConfigRoutes {
	param([Parameter(Mandatory = $true)]$Registry)

	$sharedWrapperPath = 'versions/shared/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java'
	$sharedBasePath = 'versions/shared/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreenBase.java'
	$sharedPackSelectionPath = 'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/config/PackSelectionScreenMixin.java'
	$sharedWrapper = @($Registry.sharedJavaSources | Where-Object id -ceq 'config-screen-1.20.5-through-1.21.10')
	$sharedBase = @($Registry.sharedJavaSources | Where-Object id -ceq 'config-screen-base-1.20.5-through-1.21.11')
	$sharedPackSelection = @($Registry.sharedJavaSources | Where-Object id -ceq 'pack-selection-1.21-through-1.21.10')

	Require-Contract ($sharedWrapper.Count -eq 1) 'Registry must declare exactly one shared PackForgeConfigScreen wrapper route.'
	Require-Contract ($sharedBase.Count -eq 1) 'Registry must declare exactly one shared PackForgeConfigScreenBase route.'
	Require-Contract ($sharedPackSelection.Count -eq 1) 'Registry must declare exactly one shared resource-pack config entrypoint route.'
	Require-Contract ($sharedWrapper[0].path -ceq $sharedWrapperPath) 'Shared PackForgeConfigScreen wrapper path is stale.'
	Require-Contract ($sharedBase[0].path -ceq $sharedBasePath) 'Shared PackForgeConfigScreenBase path is stale.'
	Require-Contract ($sharedPackSelection[0].path -ceq $sharedPackSelectionPath) 'Shared resource-pack config entrypoint path is stale.'
	Require-Contract (($sharedWrapper[0].targets -join ',') -ceq ($sharedTargets -join ',')) 'Shared PackForgeConfigScreen wrapper targets are stale.'
	Require-Contract (($sharedBase[0].targets -join ',') -ceq ($baseTargets -join ',')) 'Shared PackForgeConfigScreenBase targets are stale.'
	Require-Contract (($sharedPackSelection[0].targets -join ',') -ceq ($sharedPackSelectionTargets -join ',')) 'Shared resource-pack config entrypoint targets are stale.'
	Require-Contract (
		$sharedWrapper[0].sourceSet -ceq 'client' -and
		$sharedBase[0].sourceSet -ceq 'client' -and
		$sharedPackSelection[0].sourceSet -ceq 'client'
	) 'Configuration renderer and resource-pack entrypoint routes must remain client-only.'
}

Require-Contract (Test-Path -LiteralPath $RegistryPath -PathType Leaf) "Missing target registry: $RegistryPath"
$registry = Get-Content -LiteralPath $RegistryPath -Raw | ConvertFrom-Json
Assert-SharedConfigRoutes $registry

$rendererFiles = @(
	'versions/mc1_20_1/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java',
	'versions/shared/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreenBase.java',
	'versions/shared/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java',
	'versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java',
	'versions/mc26/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java'
)
$discoveredRendererFiles = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'versions') -Recurse -File -Filter 'PackForgeConfigScreen*.java' |
	ForEach-Object { [IO.Path]::GetRelativePath($repositoryRoot, $_.FullName).Replace('\', '/') } |
	Sort-Object)
Require-Contract (($discoveredRendererFiles -join ',') -eq (($rendererFiles | Sort-Object) -join ',')) 'Unexpected configuration renderer implementation or adapter file set.'

$rendererBodies = @(
	$rendererFiles[0],
	$rendererFiles[1],
	$rendererFiles[4]
)
$modelTokens = @(
	'PackForgeConfigScreenModel.availableOptions()',
	'PackForgeConfigScreenModel.availableCategories(',
	'PackForgeConfigScreenModel.effectiveState(',
	'draft.apply()'
)
foreach ($bodyPath in $rendererBodies) {
	$body = Read-RepositoryText $bodyPath
	foreach ($token in $modelTokens) {
		Require-Contract $body.Contains($token) "$bodyPath does not consume the shared configuration model token '$token'."
	}
}

$sharedWrapperText = Read-RepositoryText $rendererFiles[2]
$specialWrapperText = Read-RepositoryText $rendererFiles[3]
Require-Contract $sharedWrapperText.Contains('extends PackForgeConfigScreenBase') 'Shared screen wrapper must extend PackForgeConfigScreenBase.'
Require-Contract $sharedWrapperText.Contains('init(Minecraft.getInstance(), this.width, this.height);') 'Shared screen wrapper must retain the pre-1.21.11 rebuild signature.'
Require-Contract $specialWrapperText.Contains('extends PackForgeConfigScreenBase') '1.21.11 screen wrapper must extend PackForgeConfigScreenBase.'
Require-Contract $specialWrapperText.Contains('init();') '1.21.11 screen wrapper must retain the no-argument rebuild signature.'

$entryPoints = [ordered]@{
	'platform/fabric/src/client/java/com/teenkung/packforge/client/config/PackForgeModMenuApi.java' = 'PackForgeConfigScreen::new'
	'platform/forge/src/main/java/com/teenkung/packforge/forge/PackForgeForge.java' = 'new PackForgeConfigScreen(parent)'
	'platform/neoforge/src/main/java/com/teenkung/packforge/neoforge/PackForgeNeoForge.java' = 'new PackForgeConfigScreen(parent)'
	'versions/shared/common/src/main/java/com/teenkung/packforge/neoforge/PackForgeNeoForge.java' = 'new PackForgeConfigScreen(parent)'
	'versions/mc1_20_1/common/src/client/java/com/teenkung/packforge/client/mixin/config/PackSelectionScreenMixin.java' = 'new PackForgeConfigScreen('
	'versions/mc1_20_5_6/common/src/client/java/com/teenkung/packforge/client/mixin/config/PackSelectionScreenMixin.java' = 'new PackForgeConfigScreen('
	'versions/shared/common/src/client/java/com/teenkung/packforge/client/mixin/config/PackSelectionScreenMixin.java' = 'new PackForgeConfigScreen('
	'versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/mixin/config/PackSelectionScreenMixin.java' = 'new PackForgeConfigScreen('
	'versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/config/PackSelectionScreenMixin.java' = 'new PackForgeConfigScreen('
}
foreach ($entry in $entryPoints.GetEnumerator()) {
	$text = Read-RepositoryText $entry.Key
	Require-Contract $text.Contains($entry.Value) "$($entry.Key) no longer opens PackForgeConfigScreen."
}

if ($SelfTest) {
	$temporaryRegistryPath = Join-Path ([IO.Path]::GetTempPath()) ("packforge-config-screen-registry-" + [guid]::NewGuid().ToString('N') + '.json')
	try {
		$mutatedRegistry = $registry | ConvertTo-Json -Depth 100 | ConvertFrom-Json
		$mutatedRoute = @($mutatedRegistry.sharedJavaSources | Where-Object id -ceq 'pack-selection-1.21-through-1.21.10')[0]
		$mutatedRoute.targets = @($mutatedRoute.targets | Where-Object { $_ -ne 'mc1_21_10' })
		[IO.File]::WriteAllText(
			$temporaryRegistryPath,
			($mutatedRegistry | ConvertTo-Json -Depth 100),
			[Text.UTF8Encoding]::new($false)
		)
		$temporaryRegistry = Get-Content -LiteralPath $temporaryRegistryPath -Raw | ConvertFrom-Json
		try {
			Assert-SharedConfigRoutes $temporaryRegistry
			throw 'Configuration-screen self-test accepted a shared resource-pack entrypoint route with a missing target.'
		} catch {
			if ($_.Exception.Message -cne 'Shared resource-pack config entrypoint targets are stale.') {
				throw
			}
		}
	} finally {
		if (Test-Path -LiteralPath $temporaryRegistryPath -PathType Leaf) {
			Remove-Item -LiteralPath $temporaryRegistryPath -Force
		}
	}
	Write-Output 'PackForge configuration-screen contract self-test valid: stale shared resource-pack entrypoint target rejected.'
}

Write-Output "PackForge configuration-screen contract valid: bodies=$($rendererBodies.Count), wrappers=2, entryPoints=$($entryPoints.Count), sharedTargets=$($baseTargets.Count), sharedEntryPointTargets=$($sharedPackSelectionTargets.Count)"
