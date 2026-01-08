const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function listOffices() {
  try {
    console.log('=== Hongchon 지역 사무실 ===');
    const hongchonSnapshot = await db.collection('regions').doc('Hongchon').collection('offices').get();

    if (hongchonSnapshot.empty) {
      console.log('Hongchon 지역에 등록된 사무실이 없습니다.');
    } else {
      for (const doc of hongchonSnapshot.docs) {
        const data = doc.data();
        console.log(`\nID: ${doc.id}`);
        console.log(`전체 데이터:`, JSON.stringify(data, null, 2));
        console.log('---');

        // 기사 수 확인
        const driversSnapshot = await db.collection('regions').doc('Hongchon').collection('offices').doc(doc.id).collection('designated_drivers').get();
        console.log(`기사 수: ${driversSnapshot.size}`);

        // 고객 수 확인
        const customersSnapshot = await db.collection('regions').doc('Hongchon').collection('offices').doc(doc.id).collection('customers').get();
        console.log(`고객 수: ${customersSnapshot.size}`);
      }
    }
  } catch (error) {
    console.error('Error:', error);
  }
}

listOffices().then(() => process.exit(0));