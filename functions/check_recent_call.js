const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkCall() {
  // 양평 사무실의 최근 콜 조회
  const callsSnapshot = await db
    .collection('regions').doc('yangpyong')
    .collection('offices').doc('9IDUVAZJbzshLePkauQx')
    .collection('calls')
    .orderBy('timestamp', 'desc')
    .limit(3)
    .get();

  if (callsSnapshot.empty) {
    console.log('콜이 없습니다.');
    return;
  }

  callsSnapshot.forEach(doc => {
    const data = doc.data();
    console.log('\n=== 콜 정보 ===');
    console.log('콜 ID:', doc.id);
    console.log('상태:', data.status);
    console.log('고객명:', data.customerName);
    console.log('출발지 (departure):', data.departure || '없음');
    console.log('목적지 (destination):', data.destination || '없음');
    console.log('기사:', data.assignedDriverName);
    console.log('전화번호:', data.phoneNumber);
    console.log('timestamp:', data.timestamp?.toDate());
  });
}

checkCall().then(() => process.exit(0)).catch(err => {
  console.error('Error:', err);
  process.exit(1);
});
