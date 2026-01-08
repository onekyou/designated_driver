const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkHongchon() {
  try {
    // Hongchon 지역 확인
    const hongchonOffices = await db
      .collection('regions')
      .doc('Hongchon')
      .collection('offices')
      .get();

    console.log('=== Hongchon 지역 사무실 ===');
    if (hongchonOffices.empty) {
      console.log('Hongchon 지역에 등록된 사무실이 없습니다.');
    } else {
      hongchonOffices.forEach(doc => {
        const data = doc.data();
        console.log(`ID: ${doc.id}`);
        console.log(`이름: ${data.name || '미지정'}`);
        console.log(`전화: ${data.phoneNumber || '미지정'}`);
        console.log(`주소: ${data.address || '미지정'}`);
        console.log('---');
      });
    }

    // 특정 사무실 ID 확인
    const specificOffice = await db
      .collection('regions')
      .doc('Hongchon')
      .collection('offices')
      .doc('qwfdeSOL8Vz4lXEEP4TD')
      .get();

    console.log('\n=== qwfdeSOL8Vz4lXEEP4TD 사무실 상세 ===');
    if (specificOffice.exists) {
      console.log(JSON.stringify(specificOffice.data(), null, 2));
    } else {
      console.log('해당 사무실이 존재하지 않습니다!');
    }

  } catch (error) {
    console.error('Error:', error);
  }

  process.exit(0);
}

checkHongchon();
