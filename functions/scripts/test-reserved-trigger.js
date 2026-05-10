/**
 * test-reserved-trigger.js — RESERVED 트리거 E2E 검증.
 *
 * 양평 사무실 designated_drivers 중 fcmToken 보유 기사 1명 query → RESERVED 콜 1건 → 30초 대기 → cleanup.
 */

const admin = require('firebase-admin');

const PROJECT_ID = 'calldetector-5d61e';
const PROVINCE_ID = 'gyeonggi';
const CITY_ID = 'yangpyeong';
// 양평 활성 사무실 (5/4 P0 wallet 마이그레이션 기록). ONLINE 기사 양세훈 보유.
const OFFICE_ID = 'nEkf0X9g3LZtRX94Mrzu';

async function main() {
  if (!admin.apps.length) {
    admin.initializeApp({ projectId: PROJECT_ID });
  }
  const db = admin.firestore();
  const FieldValue = admin.firestore.FieldValue;

  const officeRef = db
    .collection('provinces').doc(PROVINCE_ID)
    .collection('cities').doc(CITY_ID)
    .collection('offices').doc(OFFICE_ID);

  // 1. 양평 기사 전체 + fcmToken 보유 분류
  const driversSnap = await officeRef.collection('designated_drivers').get();
  console.log(`[test-reserved] 양평 designated_drivers ${driversSnap.size}개`);

  const detail = [];
  driversSnap.docs.forEach((d) => {
    const data = d.data() || {};
    detail.push({
      docId: d.id,
      name: data.name ?? '(이름 없음)',
      status: data.status ?? '(상태 없음)',
      authUid: data.authUid ?? null,
      hasFcm: !!data.fcmToken,
      fcmPrefix: data.fcmToken ? data.fcmToken.substring(0, 20) + '...' : null,
    });
  });
  console.log('  기사 상세:');
  detail.forEach((d) => {
    console.log(`    - ${d.docId.substring(0, 8)}... name=${d.name} status=${d.status} authUid=${d.authUid ? d.authUid.substring(0, 8) + '...' : '(없음)'} hasFcm=${d.hasFcm} ${d.hasFcm ? '(' + d.fcmPrefix + ')' : ''}`);
  });

  const target = detail.find((d) => d.hasFcm && d.authUid);
  if (!target) {
    console.error('\n❌ fcmToken + authUid 보유 기사 없음 — RESERVED FCM 송신 검증 불가');
    console.error('   양평 사무실 활성 기사 0명 (운영 데이터 issue, RESERVED 코드와 무관)');
    process.exit(2);
  }
  console.log(`\n[test-reserved] 대상 기사: ${target.name} (authUid=${target.authUid.substring(0, 12)}..., status=${target.status}, fcmPrefix=${target.fcmPrefix})`);

  // 2. 테스트 RESERVED 콜 add
  const testCallData = {
    phoneNumber: '01000000000',
    customerName: '[테스트 RESERVED — 즉시 cleanup]',
    customerAddress: '테스트 주소',
    status: 'RESERVED',
    assignedDriverId: target.authUid,
    assignedDriverName: target.name,
    assignedDriverPhone: '',
    reservedAt: FieldValue.serverTimestamp(),
    timestamp: FieldValue.serverTimestamp(),
    timestampClient: Date.now(),
    callType: '테스트',
    deviceName: 'admin-script',
    testFlag: true,
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + 60 * 1000),
  };

  const callRef = await officeRef.collection('calls').add(testCallData);
  const callId = callRef.id;
  console.log(`[test-reserved] 테스트 RESERVED 콜 생성: ${callId}`);
  console.log(`  → oncallreserved 트리거가 ${target.name} 에게 FCM call_reserved 송신 예상`);

  // 3. 30초 대기
  console.log('[test-reserved] 30초 대기 중...');
  await new Promise((r) => setTimeout(r, 30000));

  // 4. cleanup
  await callRef.delete();
  console.log(`[test-reserved] cleanup 완료: ${callId} 삭제`);

  process.exit(0);
}

main().catch((e) => {
  console.error('❌ 치명적 오류:', e);
  process.exit(3);
});
