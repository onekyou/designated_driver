const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function createTestAttribution() {
  try {
    console.log('=== 테스트 Attribution 생성 ===\n');

    // 현재 테스트 기기의 실제 해상도
    const screenResolution = '1080x2400';
    const regionId = 'Hongchon';
    const officeId = '1xpcATEs3EvyZwJ00k8G'; // 지지 사무실

    // UUID 토큰 생성
    const { randomUUID } = require('crypto');
    const token = randomUUID();

    // Attribution 데이터
    const attributionData = {
      screenResolution: screenResolution,
      token: token,
      driverId: null,
      driverName: null,
      createdAt: admin.firestore.Timestamp.now(),
      claimed: false
    };

    // 기존 같은 해상도 attribution 삭제
    console.log(`1. 기존 ${screenResolution} attribution 삭제 중...`);
    const existingQuery = await db
      .collection(`regions/${regionId}/offices/${officeId}/attributions`)
      .where('screenResolution', '==', screenResolution)
      .get();

    const batch = db.batch();
    existingQuery.forEach(doc => {
      batch.delete(doc.ref);
    });
    await batch.commit();
    console.log(`   ✅ ${existingQuery.size}개 삭제 완료\n`);

    // 새 attribution 생성
    console.log('2. 새 attribution 생성 중...');
    await db
      .collection(`regions/${regionId}/offices/${officeId}/attributions`)
      .add(attributionData);

    console.log('   ✅ 생성 완료!\n');
    console.log('생성된 Attribution:');
    console.log(`   해상도: ${screenResolution}`);
    console.log(`   토큰: ${token}`);
    console.log(`   사무실: 지지 (${officeId})`);
    console.log(`   지역: ${regionId}`);

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

createTestAttribution();
