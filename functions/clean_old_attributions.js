const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

async function cleanOldAttributions() {
  console.log('=== 412x915 해상도의 오래된 attribution 삭제 ===\n');

  const regionsSnapshot = await db.collection('regions').get();
  let totalDeleted = 0;

  for (const regionDoc of regionsSnapshot.docs) {
    const regionId = regionDoc.id;
    console.log(`\nRegion: ${regionId}`);

    const officesSnapshot = await db.collection('regions').doc(regionId).collection('offices').get();

    for (const officeDoc of officesSnapshot.docs) {
      const officeId = officeDoc.id;
      const officeData = officeDoc.data();
      console.log(`  Office: ${officeId} (${officeData.name || 'N/A'})`);

      const attributionsSnapshot = await db.collection('regions').doc(regionId)
        .collection('offices').doc(officeId)
        .collection('attributions')
        .where('screenResolution', '==', '412x915')
        .get();

      if (!attributionsSnapshot.empty) {
        console.log(`    Deleting ${attributionsSnapshot.size} attribution(s)...`);

        for (const doc of attributionsSnapshot.docs) {
          await doc.ref.delete();
          totalDeleted++;
        }
      }
    }
  }

  console.log(`\n총 ${totalDeleted}개의 attribution 삭제 완료`);
}

cleanOldAttributions().then(() => process.exit(0)).catch(e => { console.error(e); process.exit(1); });
