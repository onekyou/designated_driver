const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

(async () => {
  try {
    // 최근 승인된 기사 확인
    const regionsSnapshot = await db.collection('regions').get();

    for (const regionDoc of regionsSnapshot.docs) {
      const officesSnapshot = await db.collection('regions').doc(regionDoc.id)
        .collection('offices').get();

      for (const officeDoc of officesSnapshot.docs) {
        // designated_drivers 확인
        const driversSnapshot = await db.collection('regions').doc(regionDoc.id)
          .collection('offices').doc(officeDoc.id)
          .collection('designated_drivers')
          .orderBy('approvedAt', 'desc')
          .limit(3)
          .get();

        if (!driversSnapshot.empty) {
          console.log('\n=== ' + regionDoc.id + ' / ' + officeDoc.id + ' ===');
          driversSnapshot.forEach(doc => {
            const data = doc.data();
            console.log('기사명:', data.name);
            console.log('기사ID:', doc.id);
            console.log('QR URL:', data.referralQrUrl || '❌ 없음');
            console.log('지역ID:', data.regionId);
            console.log('사무실ID:', data.officeId);
            console.log('승인일:', data.approvedAt?.toDate());
            console.log('---');
          });
        }
      }
    }

    process.exit(0);
  } catch (error) {
    console.error('에러:', error);
    process.exit(1);
  }
})();
