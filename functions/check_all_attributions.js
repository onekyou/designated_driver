const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

async function checkAttributions() {
  console.log('=== 모든 지역의 모든 사무실 attribution 확인 ===\n');

  const regionsSnapshot = await db.collection('regions').get();

  for (const regionDoc of regionsSnapshot.docs) {
    const regionId = regionDoc.id;
    console.log(`\nRegion: ${regionId}`);

    const officesSnapshot = await db.collection('regions').doc(regionId).collection('offices').get();

    for (const officeDoc of officesSnapshot.docs) {
      const officeId = officeDoc.id;
      const officeData = officeDoc.data();
      console.log(`  Office: ${officeId} (name: ${officeData.name || 'N/A'})`);

      const attributionsSnapshot = await db.collection('regions').doc(regionId)
        .collection('offices').doc(officeId)
        .collection('attributions').get();

      if (attributionsSnapshot.empty) {
        console.log(`    No attributions`);
      } else {
        console.log(`    ${attributionsSnapshot.size} attribution(s):`);
        attributionsSnapshot.forEach(doc => {
          const data = doc.data();
          console.log(`      - ${doc.id}:`);
          console.log(`        screenResolution: ${data.screenResolution}`);
          console.log(`        source: ${data.source}`);
          console.log(`        timezone: ${data.timezone}`);
          console.log(`        language: ${data.language}`);
          console.log(`        expiresAt: ${data.expiresAt ? new Date(data.expiresAt.toMillis()).toISOString() : 'N/A'}`);
          console.log(`        createdAt: ${data.createdAt ? new Date(data.createdAt.toMillis()).toISOString() : 'N/A'}`);
        });
      }
    }
  }
}

checkAttributions().then(() => process.exit(0)).catch(e => { console.error(e); process.exit(1); });
