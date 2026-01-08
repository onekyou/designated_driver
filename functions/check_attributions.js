const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkAttributions() {
  console.log('===== Attribution 데이터 확인 =====\n');

  // 모든 지역 확인
  const regionsSnapshot = await db.collection('regions').get();

  for (const regionDoc of regionsSnapshot.docs) {
    const regionId = regionDoc.id;
    console.log(`지역: ${regionId}`);

    // 각 지역의 사무실 확인
    const officesSnapshot = await db
      .collection('regions')
      .doc(regionId)
      .collection('offices')
      .get();

    for (const officeDoc of officesSnapshot.docs) {
      const officeId = officeDoc.id;
      const officeName = officeDoc.data().name || officeId;

      // attributions 컬렉션 확인
      const attributionsSnapshot = await db
        .collection('regions')
        .doc(regionId)
        .collection('offices')
        .doc(officeId)
        .collection('attributions')
        .orderBy('createdAt', 'desc')
        .limit(5)
        .get();

      if (!attributionsSnapshot.empty) {
        console.log(`\n  사무실: ${officeName} (${officeId})`);
        console.log(`  Attribution 데이터: ${attributionsSnapshot.size}개`);

        attributionsSnapshot.forEach((doc, idx) => {
          const data = doc.data();
          console.log(`\n    [${idx + 1}] ID: ${doc.id}`);
          console.log(`        생성일: ${data.createdAt?.toDate() || 'N/A'}`);
          console.log(`        소스: ${data.source || 'N/A'}`);
          console.log(`        visitorId: ${data.visitorId || 'N/A'}`);
          console.log(`        userAgent: ${(data.userAgent || '').substring(0, 50)}...`);
          console.log(`        screenResolution: ${data.screenResolution || 'N/A'}`);
          console.log(`        timezone: ${data.timezone || 'N/A'}`);
          console.log(`        platform: ${data.platform || 'N/A'}`);
        });
      }
    }
  }

  process.exit();
}

checkAttributions().catch(console.error);
