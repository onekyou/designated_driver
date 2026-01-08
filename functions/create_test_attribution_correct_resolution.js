const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function createTestAttribution() {
  try {
    console.log('=== 테스트 Attribution 생성 (올바른 해상도) ===\n');

    const attributionData = {
      screenResolution: '1080x2265',  // Flutter가 실제로 읽는 해상도
      token: '5056ab56-25a2-4be1-bb6a-160939eabd36',
      driverId: 'testDriver123',
      driverName: '테스트기사',
      createdAt: admin.firestore.Timestamp.now(),
      claimed: false
    };

    const docRef = await db
      .collection('regions/Hongchon/offices/qwfdeSOL8Vz4lXEEP4TD/attributions')
      .add(attributionData);

    console.log('✅ Attribution 생성 완료');
    console.log('   문서 ID:', docRef.id);
    console.log('   해상도:', attributionData.screenResolution);
    console.log('   토큰:', attributionData.token);
    console.log('   기사 ID:', attributionData.driverId);
    console.log('   기사 이름:', attributionData.driverName);

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

createTestAttribution();
