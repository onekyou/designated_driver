const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkAttribution() {
  try {
    console.log('=== 테스트 Attribution 확인 ===\n');

    const snapshot = await db
      .collection('regions/Hongchon/offices/qwfdeSOL8Vz4lXEEP4TD/attributions')
      .where('screenResolution', '==', '1080x2265')
      .get();

    if (snapshot.empty) {
      console.log('❌ Attribution을 찾을 수 없습니다.');
    } else {
      console.log(`✅ ${snapshot.size}개의 attribution 발견:\n`);
      snapshot.forEach((doc) => {
        const data = doc.data();
        console.log(`문서 ID: ${doc.id}`);
        console.log(`  해상도: ${data.screenResolution}`);
        console.log(`  토큰: ${data.token}`);
        console.log(`  기사 ID: ${data.driverId || 'N/A'}`);
        console.log(`  기사 이름: ${data.driverName || 'N/A'}`);
        console.log(`  사용 여부: ${data.claimed ? '사용됨' : '미사용'}`);
        console.log(`  생성 시간: ${data.createdAt?.toDate()}`);
        console.log('');
      });
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

checkAttribution();
