const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkCall() {
  const callId = 'I3Op5hAfNe7fVxWUDYKI';
  
  const callDoc = await db
    .collection('regions').doc('yangpyong')
    .collection('offices').doc('9IDUVAZJbzshLePkauQx')
    .collection('calls').doc(callId)
    .get();

  if (!callDoc.exists) {
    console.log('콜이 없습니다.');
    return;
  }

  const data = callDoc.data();
  console.log('\n=== 콜 정보 ===');
  console.log('콜 ID:', callDoc.id);
  console.log('상태:', data.status);
  console.log('고객명:', data.customerName);
  console.log('기사:', data.assignedDriverName);
  console.log('\n--- 운행 정보 (_set 필드) ---');
  console.log('departure_set:', data.departure_set || '없음');
  console.log('destination_set:', data.destination_set || '없음');
  console.log('fare_set:', data.fare_set || '없음');
  console.log('waypoints_set:', data.waypoints_set || '없음');
  console.log('\n--- 운행 정보 (일반 필드) ---');
  console.log('departure:', data.departure || '없음');
  console.log('destination:', data.destination || '없음');
  console.log('fare:', data.fare || '없음');
  console.log('trip_summary:', data.trip_summary || '없음');
  
  console.log('\n--- 전체 데이터 ---');
  console.log(JSON.stringify(data, null, 2));
}

checkCall().then(() => process.exit(0)).catch(err => {
  console.error('Error:', err);
  process.exit(1);
});
