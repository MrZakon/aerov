#pragma once
namespace aero::aerocore {
inline constexpr const char* InstallScript = R"AEROCORE_PS(
param([Parameter(Mandatory=$true)][string]$Root,
      [Parameter(Mandatory=$true)][string]$MinecraftDir)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Root = [IO.Path]::GetFullPath($Root)
$MinecraftDir = [IO.Path]::GetFullPath($MinecraftDir)
New-Item -ItemType Directory -Force $Root | Out-Null
Remove-Item -LiteralPath (Join-Path $Root 'aerocore-ready.txt') -Force -ErrorAction SilentlyContinue
function Fetch([string]$Url, [string]$Path, [string]$Hash, [string]$Algorithm = 'SHA256') {
    if ((Test-Path -LiteralPath $Path) -and ((Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash -ieq $Hash)) { return }
    if (!$Hash) { throw "Missing checksum: $Url" }
    New-Item -ItemType Directory -Force (Split-Path $Path) | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile ($Path + '.partial')
    if ((Get-FileHash -LiteralPath ($Path + '.partial') -Algorithm $Algorithm).Hash -ine $Hash) { throw "Checksum mismatch: $Url" }
    Move-Item -LiteralPath ($Path + '.partial') -Destination $Path -Force
}
Write-Output 'AeroCore: downloading verified engine v0.2.91'
$archive = Join-Path $Root 'engine.zip'
Fetch 'https://github.com/horizon-rust/horizon/releases/download/v0.2.91/horizon-windows-x64.zip' $archive '4d324d555fdb28ba75df4ffebf0be22c366e1beb2b91406ad8e8aa228bf4ee39'
$unpack = Join-Path $Root 'package'
Expand-Archive -LiteralPath $archive -DestinationPath $unpack -Force
$engine = @(Get-ChildItem -LiteralPath $unpack -Filter horizon-client.exe -Recurse -File)
if ($engine.Count -ne 1) { throw 'AeroCore: invalid engine archive' }
$package = $engine[0].Directory.FullName
Copy-Item -Path (Join-Path $package '*') -Destination $Root -Recurse -Force
$lock = Get-Content -LiteralPath (Join-Path $Root 'research-lock.json') -Raw | ConvertFrom-Json
if ($lock.minecraft.version -ne '26.3') { throw 'AeroCore requires Minecraft 26.3' }
$metadataPath = Join-Path $MinecraftDir 'versions\26.3\26.3.json'
Write-Output 'AeroCore: downloading original Minecraft 26.3 and assets'
$manifest = Invoke-RestMethod -Uri 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$version = @($manifest.versions | Where-Object id -eq '26.3')[0]
if (!$version) { throw 'Minecraft 26.3 is unavailable in the Mojang manifest' }
Fetch $version.url $metadataPath $version.sha1 'SHA1'
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if ($metadata.downloads.client.sha1 -ne $lock.minecraft.client_sha1) { throw 'Minecraft client does not match AeroCore' }
Fetch $metadata.downloads.client.url (Join-Path $MinecraftDir 'versions\26.3\26.3.jar') $lock.minecraft.client_sha1 'SHA1'
foreach ($library in $metadata.libraries) {
    $artifact = $library.downloads.artifact
    if ($artifact) { Fetch $artifact.url (Join-Path $MinecraftDir ('libraries\' + $artifact.path)) $artifact.sha1 'SHA1' }
}
$indexPath = Join-Path $MinecraftDir ('assets\indexes\' + $metadata.assetIndex.id + '.json')
Fetch $metadata.assetIndex.url $indexPath $metadata.assetIndex.sha1 'SHA1'
$index = Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json
foreach ($entry in $index.objects.PSObject.Properties) {
    $hash = $entry.Value.hash
    $objectPath = $hash.Substring(0,2) + '/' + $hash
    Fetch ('https://resources.download.minecraft.net/' + $objectPath) (Join-Path $MinecraftDir ('assets\objects\' + $objectPath)) $hash 'SHA1'
}
Fetch $metadata.downloads.server.url (Join-Path $Root 'server.jar') $lock.minecraft.server_sha1 'SHA1'

Write-Output 'AeroCore: downloading Java 25 for resource preparation'
$runtime = Join-Path $Root 'java'
$java = @(Get-ChildItem -LiteralPath $runtime -Filter java.exe -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1)
if (!$java) {
    $jdks = Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $jdk = @($jdks)[0].binary.package
    $javaZip = Join-Path $Root 'java.zip'
    Fetch $jdk.link $javaZip $jdk.checksum
    Expand-Archive -LiteralPath $javaZip -DestinationPath $runtime -Force
    $java = @(Get-ChildItem -LiteralPath $runtime -Filter java.exe -Recurse -File | Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1)
}
if (!$java) { throw 'AeroCore: Java 25 not found' }
$env:JAVA_HOME = $java[0].Directory.Parent.FullName
$env:PATH = $java[0].Directory.FullName + ';' + $env:PATH
$prepare = Join-Path $Root 'horizon-prepare.exe'
Write-Output 'AeroCore: preparing Minecraft resources'
& $prepare --prepare --root $Root --minecraft-dir $MinecraftDir --server-jar (Join-Path $Root 'server.jar')
if ($LASTEXITCODE -ne 0) { throw 'AeroCore: resource preparation failed' }
& $prepare sounds --root $Root --minecraft-dir $MinecraftDir
if ($LASTEXITCODE -ne 0) { throw 'AeroCore: sound preparation failed' }
$resources = Join-Path $Root '.local\horizon-client\26.3'
if (!(Test-Path -LiteralPath (Join-Path $resources 'horizon-resources.json'))) { throw 'AeroCore: resources missing' }
# Only publish readiness after every installation step has succeeded.
Set-Content -LiteralPath (Join-Path $Root 'aerocore-ready.txt') -Value 'v0.2.91 / Minecraft 26.3' -Encoding UTF8
Write-Output 'AeroCore: ready'

)AEROCORE_PS";
}
