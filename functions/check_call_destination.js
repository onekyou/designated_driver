const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkCall() {
  // 최근 IN_PROGRESS 상태인 콜 조회
  const callsSnapshot = await db.collectionGroup('calls')
    .where('status', '==', 'IN_PROGRESS')
    .orderBy('timestamp', 'desc')
    .limit(1)
    .get();

  if (callsSnapshot.empty) {
    console.log('IN_PROGRESS 콜이 없습니다.');
    return;
  }

  callsSnapshot.forEach(doc => {
    const data = doc.data();
    console.log('\n=== 최근 IN_PROGRESS 콜 ===');
    console.log('콜 ID:', doc.id);
    console.log('상태:', data.status);
    console.log('고객명:', data.customerName);
    console.log('출발지 (departure):', data.departure || '없음');
    console.log('목적지 (destination):', data.destination || '없음');
    console.log('기사:', data.assignedDriverName);
    console.log('전화번호:', data.phoneNumber);
  });
}

checkCall().then(() => process.exit(0)).catch(err => {
  console.error('Error:', err);
  process.exit(1);
});
