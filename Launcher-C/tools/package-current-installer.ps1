$ErrorActionPreference = 'Stop'
$launcherRoot = Split-Path $PSScriptRoot -Parent
$installerRoot = Join-Path (Split-Path $launcherRoot -Parent) 'installer'
$payload = Join-Path $installerRoot 'payload-final'
Copy-Item -LiteralPath (Join-Path $launcherRoot 'bin/Aero Launcher.exe') -Destination (Join-Path $launcherRoot 'release/Aero Launcher.exe') -Force
Copy-Item -LiteralPath (Join-Path $launcherRoot 'release/Aero Launcher.exe') -Destination (Join-Path $payload 'Aero Launcher.exe') -Force
Copy-Item -LiteralPath (Join-Path $installerRoot 'bin/uninstall.exe') -Destination (Join-Path $payload 'uninstall.exe') -Force
Copy-Item -Path (Join-Path $launcherRoot 'bin/assets/*') -Destination (Join-Path $payload 'assets') -Recurse -Force
$files = @(Get-ChildItem -LiteralPath $payload -Recurse -File)
$total = [uint64](($files | Measure-Object Length -Sum).Sum)
$output = Join-Path $installerRoot 'release/Aero Installer.exe'
$base = [IO.File]::ReadAllBytes((Join-Path $installerRoot 'bin/Aero Installer.exe'))
$baseLength = $base.Length
if ($baseLength -ge 16 -and [BitConverter]::ToUInt64($base,$baseLength-8) -eq [uint64]0x444E454B504E554C) {
    $baseLength = [int][BitConverter]::ToUInt64($base,$baseLength-16)
}
$stream = [IO.File]::Create($output); $writer = [IO.BinaryWriter]::new($stream)
try {
    $writer.Write($base,0,$baseLength)
    $writer.Write([uint64]0x31305741524B504C); $writer.Write($total); $writer.Write($total)
    $writer.Write([uint32]$files.Count); $writer.Write([uint32]1)
    foreach ($file in $files) {
        $relative = [IO.Path]::GetRelativePath($payload,$file.FullName).Replace('\','/')
        $name = [Text.Encoding]::UTF8.GetBytes($relative)
        $writer.Write([uint32]$name.Length); $writer.Write([uint64]$file.Length); $writer.Write($name)
        $writer.Write([IO.File]::ReadAllBytes($file.FullName))
    }
    $writer.Write([uint64]$baseLength); $writer.Write([uint64]0x444E454B504E554C)
} finally { $writer.Dispose(); $stream.Dispose() }
$reader = [IO.BinaryReader]::new([IO.File]::OpenRead($output))
try {
    $reader.BaseStream.Position=$baseLength
    if ($reader.ReadUInt64() -ne [uint64]0x31305741524B504C) { throw 'Invalid package' }
    $null=$reader.ReadUInt64(); $null=$reader.ReadUInt64(); $count=$reader.ReadUInt32(); $null=$reader.ReadUInt32()
    for ($i=0; $i -lt $count; $i++) {
        $nameLength=$reader.ReadUInt32(); $length=$reader.ReadUInt64()
        $name=[Text.Encoding]::UTF8.GetString($reader.ReadBytes($nameLength))
        $actual=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($reader.ReadBytes([int]$length)))
        $expected=(Get-FileHash -LiteralPath (Join-Path $payload $name) -Algorithm SHA256).Hash
        if ($actual -ne $expected) { throw "Package mismatch: $name" }
    }
    Write-Output "Verified all $count packaged files."
} finally { $reader.Dispose() }
