/**
 * diagnose-reserved.js — 매니저가 방금 만든 RESERVED 콜 + 기사 fcmToken 상태 즉석 확인.
 */
const admin = require('firebase-admin');
if (!admin.apps.length) admin.initializeApp({ projectId: 'calldetector-5d61e' });
const db = admin.firestore();

const OFFICES = [
  { id: 'RUbeBEvGGYP5wMhJHhMF', name: '1004' },
  { id: 'nEkf0X9g3LZtRX94Mrzu', name: '양평 활성' },
  { id: 'OyLNNY8GbFExPHHLkuMK', name: '총알대리' },
];

(async () => {
  for (const o of OFFICES) {
    const officeRef = db.collection('provinces').doc('gyeonggi')
      .collection('cities').doc('yangpyeong')
      .collection('offices').doc(o.id);

    // 1. RESERVED 콜
    const reservedSnap = await officeRef.collection('calls')
      .where('status', '==', 'RESERVED').get();

    console.log(`\n=== ${o.name} (${o.id.substring(0,12)}...) ===`);
    console.log(`RESERVED 콜: ${reservedSnap.size}건`);
    reservedSnap.docs.forEach((d) => {
      const data = d.data() || {};
      console.log(`  - ${d.id.substring(0,8)}... assignedDriverId=${data.assignedDriverId ? data.assignedDriverId.substring(0,12) + '...' : '(없음)'} customer=${data.customerName ?? ''} reservedAt=${data.reservedAt ? new Date(data.reservedAt.toMillis()).toISOString() : '(없음)'} testFlag=${data.testFlag ?? false}`);
    });

    // 2. 기사 fcmToken
    const driversSnap = await officeRef.collection('designated_drivers').get();
    console.log(`기사 fcmToken 보유 현황:`);
    driversSnap.docs.forEach((d) => {
      const data = d.data() || {};
      if (data.name || data.authUid) {
        console.log(`  - ${data.name ?? '(이름없음)'} status=${data.status ?? '?'} authUid=${data.authUid ? data.authUid.substring(0,12) + '...' : '(없음)'} fcm=${data.fcmToken ? 'O (' + data.fcmToken.substring(0,15) + '...)' : 'X'}`);
      }
    });
  }

  process.exit(0);
})().catch((e) => { console.error(e); process.exit(3); });
