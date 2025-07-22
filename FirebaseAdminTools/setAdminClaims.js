// node setAdminClaims.js <UID> <regionId> <officeId>
const admin = require('firebase-admin');
const uid     = process.argv[2];        // 예: 7kWRB5UHU4RPxIZfu1Z4adIy7723 (관리자 사용자의 UID)
const regionId    = process.argv[3] || 'yangpyong'; // 기본값: 'yangpyong' (귀하의 지역 ID로 변경 가능)
const officeId    = process.argv[4] || 'office_2';   // 기본값: 'office_2' (귀하의 사무실 ID로 변경 가능)

// UID가 입력되지 않았을 경우 사용법 안내 후 종료
if (!uid) {
  console.log('사용법: node setAdminClaims.js <UID> [regionId] [officeId]');
  process.exit(1);
}

// Firebase Admin SDK 초기화
// './' 뒤에 여러분의 실제 서비스 계정 JSON 파일 이름을 정확히 입력하세요.
// 스크린샷에 따르면 파일 이름은 'calldetector-5d61e-firebase-adminsdk-fbsvc-78ccaf497b.json' 입니다.
admin.initializeApp({
  credential: admin.credential.cert(require('./calldetector-5d61e-firebase-adminsdk-fbsvc-78ccaf497b.json'))
});

// 사용자 정의 클레임 설정
admin.auth().setCustomUserClaims(uid, {
  role: 'admin',    // 사용자에게 'admin' 역할을 부여
  regionId,         // 사용자가 속한 지역 ID
  officeId          // 사용자가 속한 사무실 ID
}).then(() => {
  // 클레임 설정 성공 시 메시지 출력
  console.log(`✅ 사용자 정의 클레임 설정 완료
  uid       : ${uid}
  role      : admin
  regionId  : ${regionId}
  officeId  : ${officeId}`);
  process.exit(0); // 스크립트 성공적으로 종료
}).catch(err => {
  // 클레임 설정 실패 시 오류 메시지 출력
  console.error('클레임 설정 오류:', err);
  process.exit(1); // 스크립트 오류와 함께 종료
});