param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$workspace = Split-Path -Parent $PSScriptRoot
$artwork = Join-Path $workspace 'artwork/novelnest'
$res = Join-Path $workspace 'app/src/main/res'
[xml]$svg = Get-Content -LiteralPath (Join-Path $artwork 'mark.svg') -Raw
$paths = @($svg.DocumentElement.ChildNodes | Where-Object { $_.LocalName -eq 'path' })
$background = $svg.DocumentElement.GetAttribute('data-background')

function Write-Asset([string]$RelativePath, [string]$Content) {
    $target = Join-Path $res $RelativePath
    [void][System.IO.Directory]::CreateDirectory((Split-Path -Parent $target))
    [System.IO.File]::WriteAllText($target, $Content + "`n", [System.Text.UTF8Encoding]::new($false))
}

function Get-VectorPaths([bool]$Monochrome) {
    $result = foreach ($path in $paths) {
        $fill = if ($path.GetAttribute('fill') -eq 'none') { '@android:color/transparent' }
            elseif ($Monochrome) { '#FFFFFF' } else { $path.GetAttribute('fill') }
        $attributes = 'android:fillColor="' + $fill + '" android:pathData="' + $path.GetAttribute('d') + '"'
        if ($path.HasAttribute('stroke')) {
            $stroke = if ($Monochrome) { '#FFFFFF' } else { $path.GetAttribute('stroke') }
            $attributes += ' android:strokeColor="' + $stroke + '" android:strokeWidth="' + $path.GetAttribute('stroke-width') + '" android:strokeLineCap="round" android:strokeLineJoin="round"'
        }
        '    <path ' + $attributes + '/>'
    }
    $result -join "`n"
}

$vectorHeader = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
Write-Asset 'drawable/ic_novelnest_mark.xml' ($vectorHeader + "`n" + (Get-VectorPaths $false) + "`n</vector>")
Write-Asset 'drawable/ic_novelnest_monochrome.xml' ($vectorHeader + "`n" + (Get-VectorPaths $true) + "`n</vector>")
Write-Asset 'values/brand_colors.xml' ('<resources><color name="novelnest_launcher_background">' + $background + '</color></resources>')
foreach ($name in @('ic_launcher', 'ic_launcher_round')) {
    Write-Asset ('mipmap-anydpi-v26/' + $name + '.xml') '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/novelnest_launcher_background"/><foreground android:drawable="@drawable/ic_novelnest_mark"/></adaptive-icon>'
    Write-Asset ('mipmap-anydpi-v33/' + $name + '.xml') '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/novelnest_launcher_background"/><foreground android:drawable="@drawable/ic_novelnest_mark"/><monochrome android:drawable="@drawable/ic_novelnest_monochrome"/></adaptive-icon>'
    $legacy = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="72dp" android:height="72dp" android:viewportWidth="72" android:viewportHeight="72">' + "`n"
    $legacy += '<path android:fillColor="' + $background + '" android:pathData="M0,0 H72 V72 H0 Z"/>' + "`n"
    $legacy += '<group android:translateX="-18" android:translateY="-18">' + "`n" + (Get-VectorPaths $false) + "`n</group></vector>"
    Write-Asset ('mipmap-anydpi/' + $name + '.xml') $legacy
}
$iconSvg = $svg.OuterXml.Replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"').Replace('</title>', '</title><rect x="18" y="18" width="72" height="72" fill="' + $background + '"/>')
[System.IO.File]::WriteAllText((Join-Path $artwork 'icon.svg'), $iconSvg, [System.Text.UTF8Encoding]::new($false))

# Render the same SVG paths for the store asset and human preview. No external packages.
function New-MarkPath([string]$Data) {
    $tokens = [regex]::Matches($Data, '[MLCZ]|-?[0-9]+(?:\.[0-9]+)?')
    $shape = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $index = 0; $x = 0.0; $y = 0.0
    while ($index -lt $tokens.Count) {
        $command = $tokens[$index++].Value
        switch ($command) {
            'M' { $x = [float]::Parse($tokens[$index++].Value, [cultureinfo]::InvariantCulture); $y = [float]::Parse($tokens[$index++].Value, [cultureinfo]::InvariantCulture); $shape.StartFigure() }
            'L' { $nx = [float]::Parse($tokens[$index++].Value, [cultureinfo]::InvariantCulture); $ny = [float]::Parse($tokens[$index++].Value, [cultureinfo]::InvariantCulture); $shape.AddLine([float]$x,[float]$y,$nx,$ny); $x=$nx; $y=$ny }
            'C' {
                $v = @(); for ($n=0; $n -lt 6; $n++) { $v += [float]::Parse($tokens[$index++].Value, [cultureinfo]::InvariantCulture) }
                $shape.AddBezier([float]$x,[float]$y,$v[0],$v[1],$v[2],$v[3],$v[4],$v[5]); $x=$v[4]; $y=$v[5]
            }
            'Z' { $shape.CloseFigure() }
            default { throw "Unsupported mark command: $command" }
        }
    }
    return ,$shape
}
foreach ($export in @(@{Name='icon-512.png'; Size=512; Round=$false}, @{Name='icon-round-preview.png'; Size=512; Round=$true})) {
    $bitmap = [System.Drawing.Bitmap]::new($export.Size, $export.Size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.Clear([System.Drawing.Color]::Transparent)
        if ($export.Round) {
            $mask = [System.Drawing.Drawing2D.GraphicsPath]::new()
            $mask.AddEllipse(0, 0, $export.Size, $export.Size); $graphics.SetClip($mask); $mask.Dispose()
        }
        $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($background))
        $graphics.FillRectangle($brush, 0, 0, $export.Size, $export.Size); $brush.Dispose()
        $scale = $export.Size / 72.0
        $graphics.ScaleTransform($scale, $scale); $graphics.TranslateTransform(-18, -18)
        foreach ($path in $paths) {
            $shape = New-MarkPath $path.GetAttribute('d')
            try {
                if ($path.GetAttribute('fill') -ne 'none') {
                    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($path.GetAttribute('fill')))
                    $graphics.FillPath($brush, $shape); $brush.Dispose()
                }
                if ($path.HasAttribute('stroke')) {
                    $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml($path.GetAttribute('stroke')), [float]::Parse($path.GetAttribute('stroke-width'), [cultureinfo]::InvariantCulture))
                    $pen.StartCap = $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
                    $graphics.DrawPath($pen, $shape); $pen.Dispose()
                }
            } finally { $shape.Dispose() }
        }
        $bitmap.Save((Join-Path $artwork $export.Name), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}
Write-Output 'Exported Novel Nest vectors, adaptive/themed launcher icons, SVG, and 512px PNG assets.'
