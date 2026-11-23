param(
    [string]$Version='both',
    [string]$ModVersion='',
    [string]$TargetRoot='',
    [string]$Tool='E:\Razrabotka\AeroProject\obf\target\fabric-obfuscator.jar',
    [string]$Java='E:\Games\Prism Launcher\java\jdk-25\bin\java.exe'
)
$ErrorActionPreference='Stop'
function Get-AeroSha256([string]$Path){
    $stream=[IO.File]::OpenRead($Path)
    $algorithm=[Security.Cryptography.SHA256]::Create()
    try{return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','')}
    finally{$algorithm.Dispose();$stream.Dispose()}
}
$projectRoot=Split-Path $PSScriptRoot -Parent
if(!(Test-Path -LiteralPath $Tool)){throw 'Fabric obfuscator JAR not found'}
if(!(Test-Path -LiteralPath $Java)){throw 'JDK 25 not found'}
$jobs=@(
    @{Version='1.21.11';Root=$projectRoot},
    @{Version='26.1.2';Root=(Join-Path $projectRoot 'ports/26.1.2')}
)
if($TargetRoot){
    if($Version -eq 'both'){throw 'Specify one Minecraft version with TargetRoot'}
    $jobs=@(@{Version=$Version;Root=(Resolve-Path -LiteralPath $TargetRoot).Path})
}elseif(!(Test-Path -LiteralPath (Join-Path $projectRoot 'ports/26.1.2/build.gradle'))){
    $property=Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') | Where-Object {$_ -match '^minecraft_version='} | Select-Object -First 1
    if(!$property){throw 'Cannot determine Minecraft version; specify Version and TargetRoot'}
    $localVersion=$property.Substring($property.IndexOf('=')+1).Trim()
    $jobs=@(@{Version=$localVersion;Root=$projectRoot})
}
if($Version -ne 'both'){$jobs=@($jobs | Where-Object {$_.Version -eq $Version});if($jobs.Count -eq 0){throw 'Unsupported version'}}
foreach($job in $jobs){
    $clientVersion=$ModVersion
    if([string]::IsNullOrWhiteSpace($clientVersion)){
        $property=Select-String -LiteralPath (Join-Path $job.Root 'gradle.properties') -Pattern '^\s*mod_version\s*=\s*(\S+)\s*$' | Select-Object -First 1
        if(!$property){throw ('Cannot determine mod_version for '+$job.Root)}
        $clientVersion=$property.Matches[0].Groups[1].Value
    }
    $name='aerovisuals-free-'+$job.Version+'-'+$clientVersion
    $sourceJar=Join-Path $job.Root ('build/libs/'+$name+'.jar')
    $protectedDir=Join-Path $job.Root 'build/protected'
    $mappingDir=Join-Path $job.Root 'build/protected-mappings'
    $releaseDir=Join-Path $job.Root 'build/release'
    $rawDir=Join-Path $job.Root 'build/unprotected'
    New-Item -ItemType Directory -Force -Path $protectedDir,$mappingDir,$releaseDir,$rawDir | Out-Null
    $protectedJar=Join-Path $protectedDir ($name+'.jar')
    $mapping=Join-Path $mappingDir ($name+'.mappings.txt')
    $log=Join-Path $job.Root 'build/hwid-obfuscation.log'
    $receiptPath=Join-Path $protectedDir 'build-receipt.json'
    $releaseJar=Join-Path $releaseDir ($name+'-obf.jar')
    $rawJar=Join-Path $rawDir ($name+'-unprotected.jar')
    $temporaryJar=Join-Path $protectedDir ($name+'.pending.jar')
    if(!(Test-Path -LiteralPath $sourceJar)){throw ('Assemble first: '+$sourceJar)}
    # Never process an already protected canonical JAR a second time.
    # Gradle's remapJar/jar produces fresh input; direct repeated script calls use the saved raw input.
    $canonicalHash=Get-AeroSha256 $sourceJar
    $previous=$null
    if(Test-Path -LiteralPath $receiptPath){try{$previous=Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json}catch{}}
    if($previous -and $canonicalHash -eq $previous.protectedSHA256){
        if(!(Test-Path -LiteralPath $rawJar) -or (Get-AeroSha256 $rawJar) -ne $previous.sourceSHA256){throw 'Protected input found without its original archive; run assemble first'}
    }else{
        Copy-Item -LiteralPath $sourceJar -Destination $rawJar -Force
    }
    # Use the supplied obf tool's Fabric compatibility preset. Keep native/reflection bridges stable.
    $flags=@('--no-flow','--no-constants','--no-anti-debug','--no-anti-decomp','--no-tamper','--no-junk','--no-junk-annotations','--seed','20261030','--whitelist','net/aero/visuals/guard/HardwareId*,net/aero/visuals/media/WindowsMediaTracker*,client/util/VoicechatBridge*')
    & $Java -jar $Tool $rawJar $temporaryJar --mappings $mapping @flags *> $log
    if($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $temporaryJar)){throw ('Obfuscation failed; see '+$log)}
    $protectedHash=Get-AeroSha256 $temporaryJar
    if($protectedHash -eq (Get-AeroSha256 $rawJar)){throw 'Obfuscator output equals the unprotected input'}
    Copy-Item -LiteralPath $temporaryJar -Destination $protectedJar -Force
    Copy-Item -LiteralPath $temporaryJar -Destination $releaseJar -Force
    Copy-Item -LiteralPath $temporaryJar -Destination $sourceJar -Force
    $optimizedJar=Join-Path $job.Root ('build/optimized/'+$name+'-optimized.jar')
    if(Test-Path -LiteralPath $optimizedJar){Copy-Item -LiteralPath $temporaryJar -Destination $optimizedJar -Force}
    Remove-Item -LiteralPath $temporaryJar
    $receipt=@{version=$job.Version;clientVersion=$clientVersion;sourceSHA256=(Get-AeroSha256 $rawJar);protectedSHA256=$protectedHash;toolSHA256=(Get-AeroSha256 $Tool);flags=$flags;mapping=$mapping;raw=$rawJar;release=$releaseJar;canonical=$sourceJar;builtAt=[DateTimeOffset]::UtcNow.ToString('o');testsRun=$false;gameLaunched=$false}
    $receipt | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 $receiptPath
    @('AeroVisuals protected release.','Distribute the *-obf.jar from this folder.','build/libs contains the same protected bytes.','Original input: build/unprotected (do not distribute).','Mappings: build/protected-mappings (do not distribute).') | Set-Content -Encoding UTF8 (Join-Path $releaseDir 'README.txt')
    Write-Output ('Obfuscated '+$job.Version+': '+$releaseJar)
    Get-Content -LiteralPath $log -Tail 9
}
