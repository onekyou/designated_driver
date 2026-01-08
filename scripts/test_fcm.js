const admin = require('firebase-admin');

// Firebase Admin SDK 초기화 (Firebase CLI 인증 사용)
admin.initializeApp({
  projectId: "calldetector-5d61e",
  databaseURL: "https://calldetector-5d61e-default-rtdb.firebaseio.com"
});

// 테스트용 순수 data-only FCM 메시지
async function sendPureDataOnlyMessage() {
  const message = {
    data: {
      type: "NEW_SHARED_CALL",
      alertTitle: "🚨 테스트 공유콜입니다! 🚨",
      alertMessage: "test_departure → test_destination\n요금: 10000원\n📞 01012345678",
      departure: "test_departure",
      destination: "test_destination",
      phoneNumber: "01012345678",
      fare: "10000",
      sharedCallId: "test_shared_call_" + Date.now()
    },
    android: {
      priority: "high",
      // notification 필드 완전 제거 - 순수 data-only
    },
    token: "eA4imumxSBiEKbbh6KkjDF:APA91bF3ATavoruXmXL-FVK6_noFvKLvVlZsV4jgR3xxclv4OKJA52t96x9jZBKcfX_Oizg8j06iChliQKa0yfXr7oJKI_jwo_wXTTYGZD4vQ-GOsa_X9qQ",
  };

  console.log('🚨 전송할 FCM 메시지 (순수 data-only):');
  console.log(JSON.stringify(message, null, 2));

  try {
    const response = await admin.messaging().send(message);
    console.log('✅ FCM 메시지 전송 성공:', response);
  } catch (error) {
    console.error('❌ FCM 메시지 전송 실패:', error);
  }
}

sendPureDataOnlyMessage();