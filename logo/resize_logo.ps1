Add-Type -AssemblyName System.Drawing

$sourceImage = [System.Drawing.Image]::FromFile('C:\app_dev\designated_driver\logo\콜매니저.png')
$width = $sourceImage.Width
$height = $sourceImage.Height

Write-Host "원본 크기: $width x $height"

# 크기를 60%로 줄이기
$newWidth = [int]($width * 0.6)
$newHeight = [int]($height * 0.6)

Write-Host "새 크기: $newWidth x $newHeight"

$bitmap = New-Object System.Drawing.Bitmap($newWidth, $newHeight)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.DrawImage($sourceImage, 0, 0, $newWidth, $newHeight)

# PNG로 저장 (높은 압축률)
$bitmap.Save('C:\app_dev\designated_driver\logo\콜매니저_250kb.png', [System.Drawing.Imaging.ImageFormat]::Png)

$sourceImage.Dispose()
$bitmap.Dispose()
$graphics.Dispose()

$fileInfo = Get-Item 'C:\app_dev\designated_driver\logo\콜매니저_250kb.png'
$sizeKB = [math]::Round($fileInfo.Length/1KB, 1)
Write-Host "새 파일 크기: $($fileInfo.Length) bytes ($sizeKB KB)"

# 250KB 초과시 JPEG로 재저장
if ($fileInfo.Length -gt 256000) {
    Write-Host "파일이 250KB를 초과합니다. JPEG로 변환 중..."

    $sourceImage2 = [System.Drawing.Image]::FromFile('C:\app_dev\designated_driver\logo\콜매니저_250kb.png')

    # JPEG 품질 설정
    $encoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object {$_.MimeType -eq 'image/jpeg'}
    $encoderParams = New-Object System.Drawing.Imaging.EncoderParameters(1)
    $encoderParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, 80L)

    $sourceImage2.Save('C:\app_dev\designated_driver\logo\콜매니저_250kb.jpg', $encoder, $encoderParams)
    $sourceImage2.Dispose()

    $fileInfoJpg = Get-Item 'C:\app_dev\designated_driver\logo\콜매니저_250kb.jpg'
    $sizeKBJpg = [math]::Round($fileInfoJpg.Length/1KB, 1)
    Write-Host "JPEG 파일 크기: $($fileInfoJpg.Length) bytes ($sizeKBJpg KB)"

    # PNG 파일 삭제
    Remove-Item 'C:\app_dev\designated_driver\logo\콜매니저_250kb.png'
}