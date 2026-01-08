const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

async function cleanAllOldAttributions() {
  console.log('=== expiresAt이 없는 모든 오래된 attribution 삭제 ===\n');

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
        .get();

      let deletedCount = 0;
      for (const doc of attributionsSnapshot.docs) {
        const data = doc.data();
        // expiresAt이 없는 오래된 attribution 삭제
        if (!data.expiresAt) {
          await doc.ref.delete();
          deletedCount++;
          totalDeleted++;
        }
      }

      if (deletedCount > 0) {
        console.log(`    Deleted ${deletedCount} old attribution(s)`);
      }
    }
  }

  console.log(`\n총 ${totalDeleted}개의 오래된 attribution 삭제 완료`);
}

cleanAllOldAttributions().then(() => process.exit(0)).catch(e => { console.error(e); process.exit(1); });
