# PowerShell 스크립트: FCM HTTP v1 API 직접 테스트

# Firebase 프로젝트 정보
$PROJECT_ID = "calldetector-5d61e"
$FCM_TOKEN = "eA4imumxSBiEKbbh6KkjDF:APA91bF3ATavoruXmXL-FVK6_noFvKLvVlZsV4jgR3xxclv4OKJA52t96x9jZBKcfX_Oizg8j06iChliQKa0yfXr7oJKI_jwo_wXTTYGZD4vQ-GOsa_X9qQ"

Write-Host "🔑 액세스 토큰 획득 중..." -ForegroundColor Yellow

# Service Account 키로 액세스 토큰 획득 (Firebase CLI 사용)
try {
    $ACCESS_TOKEN = & gcloud auth application-default print-access-token 2>$null
    if ([string]::IsNullOrEmpty($ACCESS_TOKEN)) {
        throw "토큰이 비어있음"
    }
    Write-Host "✅ 액세스 토큰 획득 성공" -ForegroundColor Green
} catch {
    Write-Host "❌ 액세스 토큰 획득 실패. gcloud auth login을 먼저 실행하세요." -ForegroundColor Red
    exit 1
}

# 순수 data-only FCM 메시지 
$timestamp = [DateTimeOffset]::Now.ToUnixTimeSeconds()
$FCM_PAYLOAD = @{
    message = @{
        token = $FCM_TOKEN
        android = @{
            priority = "high"
        }
        data = @{
            type = "NEW_SHARED_CALL"
            alertTitle = "🚨 직접 HTTP 테스트 콜! 🚨"
            alertMessage = "HTTP출발지 → HTTP도착지`n요금: 99999원`n📞 01099999999"
            departure = "HTTP출발지" 
            destination = "HTTP도착지"
            phoneNumber = "01099999999"
            fare = "99999"
            sharedCallId = "direct_http_test_$timestamp"
        }
    }
} | ConvertTo-Json -Depth 10

Write-Host "🚨 전송할 FCM 페이로드 (순수 data-only):" -ForegroundColor Yellow
Write-Host $FCM_PAYLOAD -ForegroundColor Cyan

Write-Host "📤 FCM HTTP v1 API 호출 중..." -ForegroundColor Yellow

# FCM HTTP v1 API 직접 호출
$headers = @{
    "Authorization" = "Bearer $ACCESS_TOKEN"
    "Content-Type" = "application/json; UTF-8"
}

$uri = "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send"

try {
    $response = Invoke-RestMethod -Uri $uri -Method POST -Headers $headers -Body $FCM_PAYLOAD
    Write-Host "✅ FCM 전송 성공!" -ForegroundColor Green
    Write-Host "응답:" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 5
} catch {
    Write-Host "❌ FCM 전송 실패:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseText = $reader.ReadToEnd()
        Write-Host "에러 응답: $responseText" -ForegroundColor Red
    }
}

Write-Host "`n✅ HTTP 호출 완료" -ForegroundColor Green