const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkAttributions() {
  try {
    console.log('=== Attribution 문서 구조 확인 ===\n');

    // Hongchon 사무실의 최근 attribution 조회
    const snapshot = await db
      .collection('regions/Hongchon/offices/qwfdeSOL8Vz4lXEEP4TD/attributions')
      .orderBy('createdAt', 'desc')
      .limit(5)
      .get();

    if (snapshot.empty) {
      console.log('Attribution 문서가 없습니다.');
      return;
    }

    console.log(`총 ${snapshot.size}개의 최근 attribution 발견:\n`);

    snapshot.forEach((doc, index) => {
      const data = doc.data();
      console.log(`[${index + 1}] ID: ${doc.id}`);
      console.log(`    screenResolution: ${data.screenResolution}`);
      console.log(`    token: ${data.token || 'NONE'}`);
      console.log(`    driverId: ${data.driverId || 'null'}`);
      console.log(`    driverName: ${data.driverName || 'null'}`);
      console.log(`    claimed: ${data.claimed}`);
      console.log(`    createdAt: ${data.createdAt?.toDate()}`);
      console.log('');
    });

  } catch (error) {
    console.error('오류:', error);
  }

  process.exit(0);
}

checkAttributions();
