const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function cleanAttributions() {
  try {
    const targetResolution = '1080x2265';  // 테스트 기기 해상도
    console.log(`=== Attribution 정리 (해상도: ${targetResolution}) ===\n`);

    const regionsSnapshot = await db.collection('regions').get();
    let totalDeleted = 0;

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;

      const officesSnapshot = await db
        .collection(`regions/${regionId}/offices`)
        .get();

      for (const officeDoc of officesSnapshot.docs) {
        const officeId = officeDoc.id;
        const officeData = officeDoc.data();
        const officeName = officeData.name || 'N/A';

        const attributionsSnapshot = await db
          .collection(`regions/${regionId}/offices/${officeId}/attributions`)
          .where('screenResolution', '==', targetResolution)
          .get();

        if (!attributionsSnapshot.empty) {
          console.log(`🏢 ${officeName}: ${attributionsSnapshot.size}개 삭제 중...`);

          const batch = db.batch();
          attributionsSnapshot.forEach((doc) => {
            batch.delete(doc.ref);
            totalDeleted++;
          });
          await batch.commit();
        }
      }
    }

    console.log(`\n✅ 총 ${totalDeleted}개의 attribution 삭제 완료!`);
    console.log('\n💡 이제 앱 데이터를 초기화하고 테스트하세요:');
    console.log('   adb shell pm clear com.designated.customer');

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

cleanAttributions();
