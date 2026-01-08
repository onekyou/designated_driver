Add-Type -AssemblyName System.Drawing

$sourceImage = [System.Drawing.Image]::FromFile('C:\app_dev\designated_driver\logo\call_manager_original.png')
$width = $sourceImage.Width
$height = $sourceImage.Height

Write-Host "Original size: $width x $height"

# 크기를 50%로 줄이기
$newWidth = [int]($width * 0.5)
$newHeight = [int]($height * 0.5)

Write-Host "New size: $newWidth x $newHeight"

$bitmap = New-Object System.Drawing.Bitmap($newWidth, $newHeight)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.DrawImage($sourceImage, 0, 0, $newWidth, $newHeight)

# JPEG로 저장하여 용량 줄이기
$encoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object {$_.MimeType -eq 'image/jpeg'}
$encoderParams = New-Object System.Drawing.Imaging.EncoderParameters(1)
$encoderParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, 80L)

$bitmap.Save('C:\app_dev\designated_driver\logo\call_manager_250kb.jpg', $encoder, $encoderParams)

$sourceImage.Dispose()
$bitmap.Dispose()
$graphics.Dispose()

$fileInfo = Get-Item 'C:\app_dev\designated_driver\logo\call_manager_250kb.jpg'
$sizeKB = [math]::Round($fileInfo.Length/1KB, 1)
Write-Host "New file size: $($fileInfo.Length) bytes ($sizeKB KB)"

# 250KB 초과시 품질 더 낮춤
if ($fileInfo.Length -gt 256000) {
    Write-Host "File exceeds 250KB. Reducing quality..."

    $sourceImage2 = [System.Drawing.Image]::FromFile('C:\app_dev\designated_driver\logo\call_manager_original.png')

    $newWidth2 = [int]($width * 0.4)
    $newHeight2 = [int]($height * 0.4)

    $bitmap2 = New-Object System.Drawing.Bitmap($newWidth2, $newHeight2)
    $graphics2 = [System.Drawing.Graphics]::FromImage($bitmap2)
    $graphics2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics2.DrawImage($sourceImage2, 0, 0, $newWidth2, $newHeight2)

    $encoderParams2 = New-Object System.Drawing.Imaging.EncoderParameters(1)
    $encoderParams2.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, 60L)

    $bitmap2.Save('C:\app_dev\designated_driver\logo\call_manager_250kb.jpg', $encoder, $encoderParams2)

    $sourceImage2.Dispose()
    $bitmap2.Dispose()
    $graphics2.Dispose()

    $fileInfo2 = Get-Item 'C:\app_dev\designated_driver\logo\call_manager_250kb.jpg'
    $sizeKB2 = [math]::Round($fileInfo2.Length/1KB, 1)
    Write-Host "Final file size: $($fileInfo2.Length) bytes ($sizeKB2 KB)"
}