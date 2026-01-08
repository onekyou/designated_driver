const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

/**
 * 고객 데이터의 attribution 점수와 추천 기사 정보 확인
 */
async function checkCustomerWithScore() {
  try {
    console.log('=== 고객 데이터의 Attribution 점수 및 추천 정보 확인 ===\n');

    const regionId = 'Hongchon';
    const officeId = 'qwfdeSOL8Vz4lXEEP4TD';

    const customersSnapshot = await db
      .collection('regions').doc(regionId)
      .collection('offices').doc(officeId)
      .collection('customers')
      .get();

    console.log(`Region: ${regionId}`);
    console.log(`Office: ${officeId}`);
    console.log(`고객 수: ${customersSnapshot.size}\n`);

    customersSnapshot.forEach(doc => {
      const data = doc.data();
      const customerId = doc.id;
      const customerName = data.name || '이름없음';
      const phoneNumber = data.phoneNumber || '';
      const referralDriverId = data.referralDriverId;
      const referralDriverName = data.referralDriverName;
      const attributionSource = data.attributionSource;
      const attributionScore = data.attributionScore;
      const registeredAt = data.registeredAt ? data.registeredAt.toDate().toLocaleString() : 'null';

      console.log(`Customer ID: ${customerId}`);
      console.log(`  이름: ${customerName}`);
      console.log(`  전화번호: ${phoneNumber}`);
      console.log(`  유입 경로: ${attributionSource || 'null'}`);
      console.log(`  Attribution 점수: ${attributionScore !== undefined ? attributionScore : 'null'}`);
      console.log(`  추천 기사 ID: ${referralDriverId || 'null'}`);
      console.log(`  추천 기사 이름: ${referralDriverName || 'null'}`);
      console.log(`  가입일: ${registeredAt}`);
      console.log('---\n');
    });

  } catch (error) {
    console.error('오류 발생:', error);
  }

  process.exit(0);
}

checkCustomerWithScore();
