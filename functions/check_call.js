const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

async function check() {
  const calls = await db.collection('regions').doc('Hongchon')
    .collection('offices').doc('qwfdeSOL8Vz4lXEEP4TD')
    .collection('calls')
    .where('phoneNumber', '==', '01012344321')
    .where('status', '==', 'COMPLETED')
    .get();

  console.log(`완료된 콜: ${calls.docs.length}개\n`);

  const latestCall = calls.docs[calls.docs.length - 1];
  if (latestCall) {
    const data = latestCall.data();
    console.log(`가장 최근 콜 ID: ${latestCall.id}`);
    console.log(`  요금: ${data.fare_set || data.fare}원`);
    console.log(`  pointsUsed: ${data.pointsUsed || 0}P`);
    console.log(`  finalFare: ${data.finalFare || 'N/A'}원`);
    console.log(`  isAppCustomer: ${data.isAppCustomer}`);
  }
  process.exit(0);
}
check();
