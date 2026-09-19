[CmdletBinding()]
param(
    [string] $OutputRoot = (Join-Path $PSScriptRoot '../../.gradle/performance-rework/legacy-fabric-runtime'),
    [string] $LocalMinecraftRoot = 'C:\Users\nathaphon\AppData\Roaming\.minecraft',
    [string] $JavaPath = 'C:\Program Files\Java\jdk-21\bin\java.exe',
    [ValidateSet('1.20.1', '1.21.1', '1.21.4', '1.21.8', '1.21.11', '26.3')]
    [string] $MinecraftVersion = '1.20.1'
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
if ($MinecraftVersion -eq '26.3' -and -not $PSBoundParameters.ContainsKey('JavaPath')) {
    $JavaPath = 'C:\Program Files\Java\jdk-25\bin\java.exe'
}
if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) { throw "Java executable is missing: $JavaPath" }

function Get-Url([string] $Url, [string] $Path) {
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Path
    } catch {
        $urlArg = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Url))
        $pathArg = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Path))
        $python = 'import base64,sys,urllib.request;u=base64.b64decode(sys.argv[1]).decode();p=base64.b64decode(sys.argv[2]).decode();r=urllib.request.urlopen(u,timeout=120);f=open(p,"wb");f.write(r.read());f.close()'
        & python -c $python $urlArg $pathArg
        if ($LASTEXITCODE -ne 0) { throw "Could not download $Url with PowerShell or Python." }
    }
}

function Get-RemoteText([string] $Url) {
    try {
        return [string](Invoke-WebRequest -Uri $Url).Content
    } catch {
        $urlArg = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Url))
        $python = 'import base64,sys,urllib.request;u=base64.b64decode(sys.argv[1]).decode();print(urllib.request.urlopen(u,timeout=120).read().decode())'
        $output = & python -c $python $urlArg
        if ($LASTEXITCODE -ne 0) { throw "Could not fetch $Url with PowerShell or Python." }
        return ($output -join "`n")
    }
}

function Get-RemoteJson([string] $Url) {
    return (Get-RemoteText $Url | ConvertFrom-Json)
}

function Get-Verified([string] $Url, [string] $Path, [string] $Sha1) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        if ((Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash.ToLowerInvariant() -eq $Sha1.ToLowerInvariant()) { return }
        Remove-Item -LiteralPath $Path -Force
    }
    Get-Url $Url $Path
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash.ToLowerInvariant()
    if ($actual -ne $Sha1.ToLowerInvariant()) { throw "SHA-1 mismatch for ${Url}: expected $Sha1 actual $actual" }
}

$root = [IO.Path]::GetFullPath($OutputRoot)
$loaderVersion = if ($MinecraftVersion -eq '26.3') { '0.19.5' } else { '0.19.2' }
$versionName = "$MinecraftVersion-fabric-$loaderVersion"
$versionDir = Join-Path $root "versions/$versionName"
$librariesRoot = Join-Path $root 'libraries'
$assetsRoot = Join-Path $root 'assets'
$manifest = Get-RemoteJson 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$version = @($manifest.versions | Where-Object id -eq $MinecraftVersion)[0]
$mojangPath = Join-Path $versionDir 'mojang.json'
Get-Verified $version.url $mojangPath $version.sha1
$mojang = Get-Content $mojangPath -Raw | ConvertFrom-Json
if ([string]$mojang.id -ne $MinecraftVersion) { throw "Mojang metadata identity mismatch: expected $MinecraftVersion" }

$libraries = [Collections.Generic.List[object]]::new()
foreach ($library in @($mojang.libraries)) { [void] $libraries.Add($library) }
$fabricProfileUrl = "https://meta.fabricmc.net/v2/versions/loader/$MinecraftVersion/$loaderVersion"
$fabricResponse = Get-RemoteJson $fabricProfileUrl
$fabricProfile = if ($fabricResponse -is [array]) { $fabricResponse[0] } else { $fabricResponse }
$fabricMeta = $fabricProfile.launcherMeta
$fabricCoordinates = [Collections.Generic.List[string]]::new()
[void]$fabricCoordinates.Add("net.fabricmc:fabric-loader:$loaderVersion")
if ($MinecraftVersion -ne '26.3') {
    [void]$fabricCoordinates.Add("net.fabricmc:intermediary:$MinecraftVersion")
}
foreach ($coordinate in $fabricCoordinates) {
    $parts = $coordinate -split ':'
    $relative = (($parts[0] -replace '\.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2] + '.jar')
    $base = if ($parts[1] -eq 'fabric-loader') { 'https://maven.fabricmc.net/' } else { 'https://maven.fabricmc.net/' }
    $sha1 = (Get-RemoteText ($base + $relative + '.sha1')).Trim()
    $libraries.Add([pscustomobject]@{ name = $coordinate; url = $base; downloads = [pscustomobject]@{ artifact = [pscustomobject]@{ path = $relative; url = ($base + $relative); sha1 = $sha1 } } })
}
$fabricLibraries = if ($MinecraftVersion -eq '26.3') {
    @($fabricMeta.libraries.common)
} else {
    @($fabricMeta.libraries.common) + @($fabricMeta.libraries.development)
}
foreach ($library in $fabricLibraries) {
    $libraries.Add([pscustomobject]@{ name = $library.name; url = $library.url; downloads = [pscustomobject]@{ artifact = [pscustomobject]@{ path = ((($library.name -split ':')[0] -replace '\.', '/') + '/' + ($library.name -split ':')[1] + '/' + ($library.name -split ':')[2] + '/' + (($library.name -split ':')[1]) + '-' + ($library.name -split ':')[2] + '.jar'); url = (([string]$library.url).TrimEnd('/') + '/' + ((($library.name -split ':')[0] -replace '\.', '/') + '/' + ($library.name -split ':')[1] + '/' + ($library.name -split ':')[2] + '/' + (($library.name -split ':')[1]) + '-' + ($library.name -split ':')[2] + '.jar')); sha1 = $library.sha1 } } })
}

# Fabric's launcher metadata intentionally upgrades a few Maven artifacts (for
# example ASM). Keep the later Fabric entry for the same GA/classifier while
# retaining distinct native classifiers.
$deduplicated = [Collections.Generic.List[object]]::new()
$positions = @{}
foreach ($library in $libraries) {
    $parts = ([string]$library.name).Split(':')
    $key = if ($parts.Count -gt 3) { "$($parts[0]):$($parts[1]):$($parts[3])" } else { "$($parts[0]):$($parts[1]):" }
    if ($positions.ContainsKey($key)) { $deduplicated[$positions[$key]] = $library }
    else { $positions[$key] = $deduplicated.Count; [void]$deduplicated.Add($library) }
}
$libraries = $deduplicated

$classpathLibraries = [Collections.Generic.List[object]]::new()
foreach ($library in $libraries) {
    $artifact = $library.downloads.artifact
    if ($null -eq $artifact) { continue }
    $relative = [string]$artifact.path
    $destination = Join-Path $librariesRoot ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    $source = Join-Path $LocalMinecraftRoot ('libraries/' + $relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    if ((Test-Path -LiteralPath $source -PathType Leaf) -and $artifact.sha1 -and (Get-FileHash -LiteralPath $source -Algorithm SHA1).Hash.ToLowerInvariant() -eq ([string]$artifact.sha1).ToLowerInvariant()) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
    } elseif ($artifact.sha1) {
        Get-Verified ([string]$artifact.url) $destination ([string]$artifact.sha1)
    } else { continue }
    [void]$classpathLibraries.Add($library)
}

$clientPath = Join-Path $versionDir "$versionName.jar"
Get-Verified $mojang.downloads.client.url $clientPath $mojang.downloads.client.sha1
$assetIndexId = [string]$mojang.assetIndex.id
$assetIndexPath = Join-Path $assetsRoot "indexes/$assetIndexId.json"
if (-not (Test-Path -LiteralPath $assetIndexPath -PathType Leaf)) {
    $localIndex = Join-Path $LocalMinecraftRoot "assets/indexes/$assetIndexId.json"
    if (Test-Path -LiteralPath $localIndex -PathType Leaf) {
        if ((Get-FileHash -LiteralPath $localIndex -Algorithm SHA1).Hash.ToLowerInvariant() -eq ([string]$mojang.assetIndex.sha1).ToLowerInvariant()) {
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $assetIndexPath) | Out-Null
            Copy-Item $localIndex $assetIndexPath
        }
    }
    if (-not (Test-Path -LiteralPath $assetIndexPath -PathType Leaf)) { Get-Verified $mojang.assetIndex.url $assetIndexPath $mojang.assetIndex.sha1 }
}
if ((Get-FileHash -LiteralPath $assetIndexPath -Algorithm SHA1).Hash.ToLowerInvariant() -ne ([string]$mojang.assetIndex.sha1).ToLowerInvariant()) {
    Get-Verified $mojang.assetIndex.url $assetIndexPath $mojang.assetIndex.sha1
}
$assetIndex = Get-Content $assetIndexPath -Raw | ConvertFrom-Json
foreach ($asset in $assetIndex.objects.PSObject.Properties) {
    $hash = [string]$asset.Value.hash
    $objectPath = Join-Path $assetsRoot "objects/$($hash.Substring(0, 2))/$hash"
    $localObject = Join-Path $LocalMinecraftRoot "assets/objects/$($hash.Substring(0, 2))/$hash"
    if ((Test-Path -LiteralPath $localObject -PathType Leaf) -and
        ((Get-FileHash -LiteralPath $localObject -Algorithm SHA1).Hash.ToLowerInvariant() -eq $hash.ToLowerInvariant())) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $objectPath) | Out-Null
        Copy-Item -LiteralPath $localObject -Destination $objectPath -Force
    } else {
        Get-Verified "https://resources.download.minecraft.net/$($hash.Substring(0, 2))/$hash" $objectPath $hash
    }
}

$nativesRoot = Join-Path $root "natives/$MinecraftVersion"
New-Item -ItemType Directory -Force -Path $nativesRoot | Out-Null
foreach ($library in $classpathLibraries | Where-Object { ([string]$_.name) -match ':natives-windows$' }) {
    $native = $library.downloads.artifact
    if ($null -eq $native -or [string]::IsNullOrWhiteSpace([string]$native.sha1)) { continue }
    $nativePath = Join-Path $librariesRoot ([string]$native.path)
    $extract = Join-Path $root ("native-extract-" + [guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $nativePath -DestinationPath $extract -Force
    Get-ChildItem $extract -Recurse -File | Where-Object Extension -in @('.dll', '.so', '.dylib') | Copy-Item -Destination $nativesRoot -Force
    Remove-Item -LiteralPath $extract -Recurse -Force
}

$merged = [ordered]@{
    id = $versionName
    type = 'release'
    mainClass = 'net.fabricmc.loader.impl.launch.knot.KnotClient'
    downloads = [ordered]@{ client = [ordered]@{ path = "$versionName.jar"; sha1 = $mojang.downloads.client.sha1; url = $mojang.downloads.client.url } }
    assetIndex = $mojang.assetIndex
    assets = $mojang.assets
    libraries = $classpathLibraries
    arguments = $mojang.arguments
    fabricProfile = [ordered]@{ url = $fabricProfileUrl; loaderVersion = $loaderVersion; productionLibrariesOnly = ($MinecraftVersion -eq '26.3'); intermediaryIncluded = ($MinecraftVersion -ne '26.3') }
    packforge = [ordered]@{ minecraftVersion = $MinecraftVersion; nativesRoot = $nativesRoot }
}
New-Item -ItemType Directory -Force -Path $versionDir | Out-Null
$merged | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $versionDir "$versionName.json") -Encoding utf8
[ordered]@{ fabricClientRoot = $root; versionName = $versionName; minecraftVersion = $MinecraftVersion; assetsRoot = $assetsRoot; nativesRoot = $nativesRoot; javaPath = $JavaPath; metadata = (Join-Path $versionDir "$versionName.json"); client = $clientPath; libraryCount = $classpathLibraries.Count } | ConvertTo-Json
