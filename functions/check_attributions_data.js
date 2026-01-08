const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

/**
 * Firestore attributions 컬렉션의 기사 정보 확인
 */
async function checkAttributionsData() {
  try {
    console.log('=== Firestore attributions 컬렉션 확인 ===\n');

    const regionId = 'Hongchon';
    const officeId = 'qwfdeSOL8Vz4lXEEP4TD';

    const attributionsSnapshot = await db
      .collection('regions').doc(regionId)
      .collection('offices').doc(officeId)
      .collection('attributions')
      .orderBy('createdAt', 'desc')
      .limit(20)
      .get();

    console.log(`Region: ${regionId}`);
    console.log(`Office: ${officeId}`);
    console.log(`Attribution 문서 수 (최근 20개): ${attributionsSnapshot.size}\n`);

    if (attributionsSnapshot.empty) {
      console.log('⚠️ attribution 데이터가 없습니다.');
    } else {
      let hasDriverInfoCount = 0;
      let noDriverInfoCount = 0;

      attributionsSnapshot.forEach(doc => {
        const data = doc.data();
        const attributionId = doc.id;
        const driverId = data.driverId;
        const driverName = data.driverName;
        const screenResolution = data.screenResolution;
        const claimed = data.claimed;
        const createdAt = data.createdAt ? data.createdAt.toDate().toLocaleString() : 'null';

        console.log(`Attribution ID: ${attributionId}`);
        console.log(`생성일: ${createdAt}`);
        console.log(`화면 해상도: ${screenResolution || 'null'}`);
        console.log(`Claimed: ${claimed}`);

        if (driverId && driverName) {
          hasDriverInfoCount++;
          console.log(`✅ 기사 정보 있음:`);
          console.log(`   Driver ID: ${driverId}`);
          console.log(`   Driver Name: ${driverName}`);
        } else {
          noDriverInfoCount++;
          console.log(`❌ 기사 정보 없음`);
          console.log(`   Driver ID: ${driverId || 'null'}`);
          console.log(`   Driver Name: ${driverName || 'null'}`);
        }
        console.log('---\n');
      });

      console.log('\n=== 요약 ===');
      console.log(`총 attribution 수: ${attributionsSnapshot.size}`);
      console.log(`기사 정보 있음: ${hasDriverInfoCount}개`);
      console.log(`기사 정보 없음: ${noDriverInfoCount}개`);
    }

  } catch (error) {
    console.error('오류 발생:', error);
  }

  process.exit(0);
}

checkAttributionsData();
