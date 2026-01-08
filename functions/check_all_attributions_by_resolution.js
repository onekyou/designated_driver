const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkAllAttributions() {
  try {
    console.log('=== 모든 사무실의 Attribution 확인 (해상도: 1080x2265) ===\n');

    const regionsSnapshot = await db.collection('regions').get();

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      console.log(`\n📍 지역: ${regionId}`);

      const officesSnapshot = await db
        .collection(`regions/${regionId}/offices`)
        .get();

      for (const officeDoc of officesSnapshot.docs) {
        const officeId = officeDoc.id;
        const officeData = officeDoc.data();
        const officeName = officeData.name || 'N/A';

        const attributionsSnapshot = await db
          .collection(`regions/${regionId}/offices/${officeId}/attributions`)
          .where('screenResolution', '==', '1080x2265')
          .orderBy('createdAt', 'desc')
          .get();

        if (!attributionsSnapshot.empty) {
          console.log(`\n  🏢 사무실: ${officeName} (${officeId})`);
          console.log(`  📊 Attribution 개수: ${attributionsSnapshot.size}개\n`);

          attributionsSnapshot.forEach((doc, index) => {
            const data = doc.data();
            const createdAt = data.createdAt?.toDate();
            const now = new Date();
            const hoursDiff = (now - createdAt) / (1000 * 60 * 60);

            console.log(`    [${index + 1}] 문서 ID: ${doc.id}`);
            console.log(`        토큰: ${data.token}`);
            console.log(`        해상도: ${data.screenResolution}`);
            console.log(`        사용 여부: ${data.claimed ? '✅ 사용됨' : '❌ 미사용'}`);
            console.log(`        생성 시간: ${createdAt}`);
            console.log(`        경과 시간: ${hoursDiff.toFixed(1)}시간 전`);
            if (data.driverId) {
              console.log(`        기사 ID: ${data.driverId}`);
              console.log(`        기사 이름: ${data.driverName || 'N/A'}`);
            }
            console.log('');
          });
        }
      }
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

checkAllAttributions();
