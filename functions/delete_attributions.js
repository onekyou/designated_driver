const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function deleteAttributions() {
  try {
    console.log('=== Attribution 삭제 시작 ===\n');

    // Hongchon 사무실의 모든 attribution 조회
    const snapshot = await db
      .collection('regions/Hongchon/offices/qwfdeSOL8Vz4lXEEP4TD/attributions')
      .get();

    if (snapshot.empty) {
      console.log('삭제할 Attribution이 없습니다.');
      process.exit(0);
      return;
    }

    console.log(`총 ${snapshot.size}개의 attribution 발견\n`);

    // 각 문서 삭제
    const batch = db.batch();
    let count = 0;

    snapshot.forEach((doc) => {
      const data = doc.data();
      console.log(`[${count + 1}] 삭제 예정:`);
      console.log(`    ID: ${doc.id}`);
      console.log(`    screenResolution: ${data.screenResolution}`);
      console.log(`    token: ${data.token || 'NONE'}`);
      console.log(`    createdAt: ${data.createdAt?.toDate()}`);
      console.log('');

      batch.delete(doc.ref);
      count++;
    });

    // Batch commit
    await batch.commit();
    console.log(`\n✅ 총 ${count}개의 attribution을 삭제했습니다.`);

  } catch (error) {
    console.error('오류:', error);
  }

  process.exit(0);
}

deleteAttributions();
