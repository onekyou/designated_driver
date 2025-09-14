#!/bin/bash

# Firebase 프로젝트 정보
PROJECT_ID="calldetector-5d61e"
FCM_TOKEN="eA4imumxSBiEKbbh6KkjDF:APA91bF3ATavoruXmXL-FVK6_noFvKLvVlZsV4jgR3xxclv4OKJA52t96x9jZBKcfX_Oizg8j06iChliQKa0yfXr7oJKI_jwo_wXTTYGZD4vQ-GOsa_X9qQ"

# Service Account 키로 액세스 토큰 획득 (Firebase CLI 사용)
echo "🔑 액세스 토큰 획득 중..."
ACCESS_TOKEN=$(gcloud auth application-default print-access-token 2>/dev/null)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "❌ 액세스 토큰 획득 실패. gcloud auth login을 먼저 실행하세요."
  exit 1
fi

echo "✅ 액세스 토큰 획득 성공"

# 순수 data-only FCM 메시지 JSON
FCM_PAYLOAD='{
  "message": {
    "token": "'$FCM_TOKEN'",
    "android": {
      "priority": "high"
    },
    "data": {
      "type": "NEW_SHARED_CALL", 
      "alertTitle": "🚨 직접 HTTP 테스트 콜! 🚨",
      "alertMessage": "HTTP출발지 → HTTP도착지\n요금: 99999원\n📞 01099999999",
      "departure": "HTTP출발지",
      "destination": "HTTP도착지",
      "phoneNumber": "01099999999",
      "fare": "99999",
      "sharedCallId": "direct_http_test_'$(date +%s)'"
    }
  }
}'

echo "🚨 전송할 FCM 페이로드 (순수 data-only):"
echo "$FCM_PAYLOAD" | jq .

echo "📤 FCM HTTP v1 API 호출 중..."

# FCM HTTP v1 API 직접 호출
curl -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json; UTF-8" \
  "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send" \
  -d "$FCM_PAYLOAD" \
  -v

echo -e "\n✅ HTTP 호출 완료"