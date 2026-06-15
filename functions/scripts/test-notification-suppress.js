/**
 * test-notification-suppress.js — ①번 알림 표기 억제 로직 런타임 검증용 임시 스크립트.
 *
 * 2026-06-12 알림정리(4f625eed)의 무음 분기를 실 FCM으로 검증.
 * Firestore를 건드리지 않고(기사 상태·콜 무변경), 콜매니저 매니저 토큰에
 * 각 type별 data-only FCM을 직접 전송 → MyFirebaseMessagingService 분기 관찰.
 *
 *   node scripts/test-notification-suppress.js
 *
 * 대상 office = gyeonggi/yangpyeong/RUbeBEvGGYP5wMhJHhMF (1004 테스트, S21+ 로그인)
 *
 * 기대(logcat CallManager_FCM):
 *   NEW_CALL                       → [handleNewCall] 알림 생략(데이터만)         (무음)
 *   DRIVER_STATUS_UPDATE WAITING   → [DRIVER_STATUS] 알림 생략(데이터만)         (무음)
 *   DRIVER_STATUS_UPDATE IN_PROGRESS → [DRIVER_STATUS] 알림 생략(데이터만)       (무음)
 *   DRIVER_STATUS_UPDATE ONLINE    → showNotification 호출 시작                  (기본음)
 *   DRIVER_STATUS_UPDATE OFFLINE   → showNotification 호출 시작                  (기본음)
 *   CALL_STATUS_UPDATE  CANCELED   → [CALL_STATUS_UPDATE] 취소 알림 표시 + show… (기본음)
 */

const admin = require('firebase-admin');
if (!admin.apps.length) admin.initializeApp({ projectId: 'calldetector-5d61e' });
const db = admin.firestore();

const OFFICE = {
  provinceId: 'gyeonggi',
  cityId: 'yangpyeong',
  officeId: 'RUbeBEvGGYP5wMhJHhMF',
};
const TOKENS_PATH =
  `provinces/${OFFICE.provinceId}/cities/${OFFICE.cityId}/offices/${OFFICE.officeId}/managerTokens`;

// 검증 케이스 (전송 순서대로). label은 logcat 대조용.
const CASES = [
  { label: 'NEW_CALL (무음 기대)', data: {
      type: 'NEW_CALL', callId: 'test_suppress_call_1',
      customerName: '[테스트]억제검증', customerPhone: '01000000000',
      pickupLocation: '테스트', status: 'WAITING',
      provinceId: OFFICE.provinceId, cityId: OFFICE.cityId, officeId: OFFICE.officeId,
  }},
  { label: 'DRIVER_STATUS WAITING (무음 기대)', data: {
      type: 'DRIVER_STATUS_UPDATE', driverId: 'test_suppress_drv',
      driverName: '[테스트]기사', newStatus: 'WAITING', statusMessage: '대기중',
  }},
  { label: 'DRIVER_STATUS IN_PROGRESS (무음 기대)', data: {
      type: 'DRIVER_STATUS_UPDATE', driverId: 'test_suppress_drv',
      driverName: '[테스트]기사', newStatus: 'IN_PROGRESS', statusMessage: '운행중',
  }},
  { label: 'DRIVER_STATUS ONLINE (기본음 기대)', data: {
      type: 'DRIVER_STATUS_UPDATE', driverId: 'test_suppress_drv',
      driverName: '[테스트]기사', newStatus: 'ONLINE', statusMessage: '출근',
  }},
  { label: 'DRIVER_STATUS OFFLINE (기본음 기대)', data: {
      type: 'DRIVER_STATUS_UPDATE', driverId: 'test_suppress_drv',
      driverName: '[테스트]기사', newStatus: 'OFFLINE', statusMessage: '퇴근',
  }},
  { label: 'CALL_STATUS CANCELED (기본음 기대)', data: {
      type: 'CALL_STATUS_UPDATE', callId: 'test_suppress_call_1',
      status: 'CANCELED', message: '[테스트] 관리자 취소',
  }},
];

(async () => {
  const snap = await db.collection(TOKENS_PATH).get();
  const tokens = snap.docs.map((d) => d.data()?.fcmToken).filter(Boolean);
  if (!tokens.length) { console.error('매니저 토큰 없음:', TOKENS_PATH); process.exit(2); }
  console.log(`매니저 토큰 ${tokens.length}개 대상 전송 시작\n`);

  for (const c of CASES) {
    console.log(`▶ ${c.label}`);
    for (const token of tokens) {
      try {
        const id = await admin.messaging().send({
          token,
          data: c.data,
          android: { priority: 'high' },
        });
        console.log(`   sent ${id}`);
      } catch (e) {
        console.error(`   send 실패: ${e.message}`);
      }
    }
    await new Promise((r) => setTimeout(r, 2500)); // logcat 구분용 간격
  }
  console.log('\n✅ 전 케이스 전송 완료. S21+ logcat(CallManager_FCM) 확인.');
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
