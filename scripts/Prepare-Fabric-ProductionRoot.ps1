[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9]+\.[0-9]+(?:\.[0-9]+)?$')]
    [string] $MinecraftVersion,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9]+\.[0-9]+(?:\.[0-9]+)?$')]
    [string] $LoaderVersion,

    [Parameter(Mandatory = $true)]
    [string] $ClientRoot
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

function Get-PropertyValue {
    param($Object, [string] $Name)

    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Get-LibraryPath {
    param($Library)

    $artifact = $null
    $downloads = Get-PropertyValue $Library 'downloads'
    if ($null -ne $downloads) { $artifact = Get-PropertyValue $downloads 'artifact' }
    $artifactPath = Get-PropertyValue $artifact 'path'
    if ($null -ne $artifact -and -not [string]::IsNullOrWhiteSpace([string] $artifactPath)) {
        return ([string] $artifactPath).Replace('/', [IO.Path]::DirectorySeparatorChar)
    }

    $coordinate = [string] (Get-PropertyValue $Library 'name')
    $extension = 'jar'
    $atIndex = $coordinate.LastIndexOf([char] 64)
    if ($atIndex -ge 0) {
        $extension = $coordinate.Substring($atIndex + 1)
        $coordinate = $coordinate.Substring(0, $atIndex)
    }

    $parts = $coordinate.Split(':')
    if ($parts.Count -lt 3) { throw "Unsupported library coordinate: $coordinate" }
    $classifier = if ($parts.Count -gt 3) { "-$($parts[3])" } else { '' }
    $fileName = "$($parts[1])-$($parts[2])$classifier.$extension"
    return (Join-Path ($parts[0].Replace('.', [IO.Path]::DirectorySeparatorChar)) (Join-Path $parts[1] (Join-Path $parts[2] $fileName)))
}

function Test-WindowsLibrary {
    param($Library)

    $name = [string] (Get-PropertyValue $Library 'name')
    if ($name -match ':natives-(?<platform>[^:]+)$') {
        return $Matches.platform -ieq 'windows'
    }

    $rules = Get-PropertyValue $Library 'rules'
    if ($null -eq $rules) { return $true }
    $matched = $false
    $allowed = $false
    foreach ($rule in @($rules)) {
        $matches = $true
        $ruleOs = Get-PropertyValue $rule 'os'
        $ruleOsName = Get-PropertyValue $ruleOs 'name'
        if ($null -ne $ruleOs -and $null -ne $ruleOsName -and [string] $ruleOsName -ine 'windows') {
            $matches = $false
        }
        if (-not $matches) { continue }
        $matched = $true
        $action = [string] (Get-PropertyValue $rule 'action')
        if ($action -ieq 'allow') { $allowed = $true }
        elseif ($action -ieq 'disallow') { $allowed = $false }
        else { throw "Unsupported library rule action: $action" }
    }
    return $matched -and $allowed
}

function Get-LibraryUrl {
    param($Library, [string] $RelativePath)

    $downloads = Get-PropertyValue $Library 'downloads'
    $artifact = Get-PropertyValue $downloads 'artifact'
    $artifactUrl = Get-PropertyValue $artifact 'url'
    if ($null -ne $artifact -and -not [string]::IsNullOrWhiteSpace([string] $artifactUrl)) {
        return [string] $artifactUrl
    }

    $baseUrl = [string] (Get-PropertyValue $Library 'url')
    if ([string]::IsNullOrWhiteSpace($baseUrl)) { $baseUrl = 'https://libraries.minecraft.net/' }
    return ($baseUrl.TrimEnd('/') + '/' + $RelativePath.Replace('\', '/'))
}

function Save-RemoteFile {
    param([string] $Url, [string] $Path)

    if (Test-Path -LiteralPath $Path -PathType Leaf) { return }
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Invoke-WebRequest -Uri $Url -OutFile $Path -UseBasicParsing
}

function ConvertTo-Array {
    param($Value)
    if ($null -eq $Value) { return @() }
    return @($Value)
}

$root = [IO.Path]::GetFullPath($ClientRoot)
$versionName = "$MinecraftVersion-fabric-$LoaderVersion"
$versionDirectory = Join-Path $root "versions\$versionName"
$librariesRoot = Join-Path $root 'libraries'
$assetsRoot = Join-Path $root 'assets'
$nativesRoot = Join-Path $root 'natives'
New-Item -ItemType Directory -Path $versionDirectory, $librariesRoot, (Join-Path $assetsRoot 'indexes'), (Join-Path $assetsRoot 'objects'), $nativesRoot, (Join-Path $root 'log_configs') -Force | Out-Null

$manifest = Invoke-RestMethod -Uri 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$versionEntry = @($manifest.versions | Where-Object { [string] $_.id -eq $MinecraftVersion })
if ($versionEntry.Count -ne 1) { throw "Mojang version metadata is missing $MinecraftVersion." }
$vanilla = Invoke-RestMethod -Uri ([string] $versionEntry[0].url)
$profileUrl = "https://meta.fabricmc.net/v2/versions/loader/$MinecraftVersion/$LoaderVersion/profile/json"
$profile = Invoke-RestMethod -Uri $profileUrl

$clientPath = Join-Path $versionDirectory "$versionName.jar"
Save-RemoteFile -Url ([string] $vanilla.downloads.client.url) -Path $clientPath

$assetIndexId = [string] $vanilla.assetIndex.id
$assetIndexPath = Join-Path $assetsRoot "indexes\$assetIndexId.json"
Save-RemoteFile -Url ([string] $vanilla.assetIndex.url) -Path $assetIndexPath
$assetIndex = Get-Content -LiteralPath $assetIndexPath -Raw | ConvertFrom-Json
foreach ($asset in $assetIndex.objects.PSObject.Properties) {
    $hash = [string] $asset.Value.hash
    if ($hash.Length -lt 2) { throw "Asset '$($asset.Name)' has an invalid hash '$hash'." }
    $hashPrefix = $hash.Substring(0, 2)
    $objectPath = Join-Path $assetsRoot "objects\$hashPrefix\$hash"
    Save-RemoteFile -Url "https://resources.download.minecraft.net/$hashPrefix/$hash" -Path $objectPath
}

$clientLogging = $null
if ($null -ne $vanilla.logging) { $clientLogging = $vanilla.logging.client }
if ($null -ne $clientLogging -and $null -ne $clientLogging.file) {
    Save-RemoteFile -Url ([string] $clientLogging.file.url) -Path (Join-Path $root "log_configs\$([string] $clientLogging.file.id)")
}

$mergedLibraries = [Collections.Generic.List[object]]::new()
foreach ($library in (ConvertTo-Array $vanilla.libraries) + (ConvertTo-Array $profile.libraries)) {
    if (-not (Test-WindowsLibrary -Library $library)) { continue }
    $relativePath = Get-LibraryPath -Library $library
    $libraryPath = Join-Path $librariesRoot $relativePath
    Save-RemoteFile -Url (Get-LibraryUrl -Library $library -RelativePath $relativePath) -Path $libraryPath
    if ([string] $library.name -match ':natives-windows$') {
        Expand-Archive -LiteralPath $libraryPath -DestinationPath $nativesRoot -Force
    }
    [void] $mergedLibraries.Add($library)
}

$clientDownload = [ordered]@{
    sha1 = $vanilla.downloads.client.sha1
    size = $vanilla.downloads.client.size
    url = $vanilla.downloads.client.url
    path = "$versionName.jar"
}
$mergedArguments = [ordered]@{
    jvm = @(ConvertTo-Array $vanilla.arguments.jvm) + @(ConvertTo-Array $profile.arguments.jvm)
    game = @(ConvertTo-Array $vanilla.arguments.game) + @(ConvertTo-Array $profile.arguments.game)
}
$metadata = [ordered]@{
    id = $versionName
    type = $vanilla.type
    mainClass = $profile.mainClass
    assets = $vanilla.assets
    assetIndex = $vanilla.assetIndex
    downloads = [ordered]@{ client = $clientDownload }
    logging = $vanilla.logging
    arguments = $mergedArguments
    libraries = @($mergedLibraries)
}
$metadataPath = Join-Path $versionDirectory "$versionName.json"
$metadata | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $metadataPath -Encoding utf8
$assetCount = @($assetIndex.objects.PSObject.Properties).Count
Write-Output "READY Fabric production root: version=$versionName root=$root libraries=$($mergedLibraries.Count) assets=$assetCount"
