Add-Type -AssemblyName System.Drawing
$srcPath = Join-Path $PSScriptRoot '../app/src/main/res/drawable/ic_novelnest_logo.png'
$outPath = Join-Path $PSScriptRoot '../app/src/main/res/drawable/ic_novelnest_splash_logo.png'

$src = [System.Drawing.Bitmap]::FromFile($srcPath)
$size = 512
$bmp = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.Clear([System.Drawing.Color]::Transparent)

$cx = $size / 2.0
$cy = $size / 2.0
$innerR = 158.0
$outerR = 212.0

# Draw radiant sunrays
$rayCount = 24
for ($i = 0; $i -lt $rayCount; $i++) {
    $angle = $i * (360.0 / $rayCount)
    $rad = [Math]::PI * $angle / 180.0
    $isMajor = ($i % 2 -eq 0)
    $rCur = if ($isMajor) { $outerR } else { $outerR * 0.88 }
    
    $x1 = $cx + $innerR * [Math]::Cos($rad)
    $y1 = $cy + $innerR * [Math]::Sin($rad)
    $x2 = $cx + $rCur * [Math]::Cos($rad)
    $y2 = $cy + $rCur * [Math]::Sin($rad)
    
    $alpha = if ($isMajor) { 200 } else { 130 }
    $penColor = [System.Drawing.Color]::FromArgb($alpha, 255, 213, 79)
    $penWidth = if ($isMajor) { 4.5 } else { 2.6 }
    $pen = [System.Drawing.Pen]::new($penColor, $penWidth)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawLine($pen, [float]$x1, [float]$y1, [float]$x2, [float]$y2)
    $pen.Dispose()
}

# Ambient warm glow behind circular medallion
$glowPath = [System.Drawing.Drawing2D.GraphicsPath]::new()
$glowPath.AddEllipse([float]($cx - $innerR - 10), [float]($cy - $innerR - 10), [float](($innerR + 10) * 2), [float](($innerR + 10) * 2))
$glowBrush = [System.Drawing.Drawing2D.PathGradientBrush]::new($glowPath)
$glowBrush.CenterColor = [System.Drawing.Color]::FromArgb(100, 226, 167, 101)
$glowBrush.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 226, 167, 101))
$g.FillPath($glowBrush, $glowPath)
$glowBrush.Dispose()
$glowPath.Dispose()

# Draw circular cropped inner logo
$clipPath = [System.Drawing.Drawing2D.GraphicsPath]::new()
$clipPath.AddEllipse([float]($cx - $innerR), [float]($cy - $innerR), [float]($innerR * 2), [float]($innerR * 2))
$g.SetClip($clipPath)
$srcRect = [System.Drawing.RectangleF]::new([float]($cx - $innerR - 12), [float]($cy - $innerR - 12), [float](($innerR + 12) * 2), [float](($innerR + 12) * 2))
$g.DrawImage($src, $srcRect)
$g.ResetClip()
$clipPath.Dispose()

# Amber-gold bezel rings
$penOuter = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 199, 140, 75), 7.0) # Warm bronze-gold outer
$penMid   = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 255, 213, 79), 4.0) # Shining amber-gold core
$penInner = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(220, 255, 249, 196), 1.8) # Lustrous highlight rim

$g.DrawEllipse($penOuter, [float]($cx - $innerR), [float]($cy - $innerR), [float]($innerR * 2), [float]($innerR * 2))
$g.DrawEllipse($penMid,   [float]($cx - $innerR), [float]($cy - $innerR), [float]($innerR * 2), [float]($innerR * 2))
$g.DrawEllipse($penInner, [float]($cx - $innerR + 1.2), [float]($cy - $innerR + 1.2), [float](($innerR - 1.2) * 2), [float](($innerR - 1.2) * 2))

$penOuter.Dispose(); $penMid.Dispose(); $penInner.Dispose()

$bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
$src.Dispose()
Write-Output "Successfully generated $outPath"
