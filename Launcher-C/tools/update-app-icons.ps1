$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$launcherRoot = Split-Path $PSScriptRoot -Parent
$installerRoot = Join-Path (Split-Path $launcherRoot -Parent) 'installer'
$source = [Drawing.Bitmap]::new((Join-Path $launcherRoot 'resources/aero-logo.png'))
function Render-Png([int]$size) {
    $bitmap = [Drawing.Bitmap]::new($size,$size,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($source,0,0,$size,$size)
    $stream = [IO.MemoryStream]::new()
    $bitmap.Save($stream,[Drawing.Imaging.ImageFormat]::Png)
    $bytes = $stream.ToArray()
    $graphics.Dispose(); $bitmap.Dispose(); $stream.Dispose()
    return ,$bytes
}
$sizes = @(16,24,32,48,64,128,256)
$images = @($sizes | ForEach-Object { ,(Render-Png $_) })
$ico = [IO.MemoryStream]::new(); $writer = [IO.BinaryWriter]::new($ico)
$writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i=0; $i -lt $sizes.Count; $i++) {
    $dimension = if ($sizes[$i] -eq 256) { 0 } else { $sizes[$i] }
    $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
    $writer.Write([byte]0); $writer.Write([byte]0)
    $writer.Write([uint16]1); $writer.Write([uint16]32)
    $writer.Write([uint32]$images[$i].Length); $writer.Write([uint32]$offset)
    $offset += $images[$i].Length
}
foreach ($bytes in $images) { $writer.Write([byte[]]$bytes) }
$icoBytes = $ico.ToArray(); $writer.Dispose(); $ico.Dispose()
foreach ($path in @('resources/app.ico','assets/icon.ico','bin/assets/icon.ico','release/assets/icon.ico','linux/assets/icon.ico','mac/assets/icon.ico')) {
    [IO.File]::WriteAllBytes((Join-Path $launcherRoot $path),$icoBytes)
}
[IO.File]::WriteAllBytes((Join-Path $installerRoot 'resources/app.ico'),$icoBytes)
foreach ($folder in @('assets','bin/assets','release/assets','linux/assets','mac/assets')) {
    foreach ($name in @('icon.png','aero-logo.png','discord/aero_logo.png','discord/aero-app-icon-1024.png')) {
        [IO.File]::WriteAllBytes((Join-Path $launcherRoot "$folder/$name"),(Render-Png 1024))
    }
    [IO.File]::WriteAllBytes((Join-Path $launcherRoot "$folder/icon-256.png"),(Render-Png 256))
}
$source.Dispose()
Write-Output 'Transparent PNG assets and seven-size Windows icons updated.'
