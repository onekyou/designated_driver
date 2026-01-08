const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

/**
 * 고객 데이터에 추천 기사 정보가 포함되어 있는지 확인
 */
async function checkCustomerReferrals() {
  try {
    console.log('=== 고객 데이터의 추천 기사 정보 확인 ===\n');

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

    if (customersSnapshot.empty) {
      console.log('⚠️ 고객 데이터가 없습니다.');
    } else {
      let hasReferralCount = 0;
      let noReferralCount = 0;

      customersSnapshot.forEach(doc => {
        const data = doc.data();
        const customerId = doc.id;
        const customerName = data.name || '이름없음';
        const phoneNumber = data.phoneNumber || '';
        const referralDriverId = data.referralDriverId;
        const referralDriverName = data.referralDriverName;
        const attributionSource = data.attributionSource;
        const registeredAt = data.registeredAt ? data.registeredAt.toDate().toLocaleString() : 'null';

        if (referralDriverName) {
          hasReferralCount++;
          console.log(`✅ 추천 정보 있음:`);
          console.log(`   Customer ID: ${customerId}`);
          console.log(`   이름: ${customerName}`);
          console.log(`   전화번호: ${phoneNumber}`);
          console.log(`   추천 기사 ID: ${referralDriverId || 'null'}`);
          console.log(`   추천 기사 이름: ${referralDriverName}`);
          console.log(`   유입 경로: ${attributionSource || 'null'}`);
          console.log(`   가입일: ${registeredAt}`);
          console.log('---\n');
        } else {
          noReferralCount++;
          console.log(`⚠️ 추천 정보 없음:`);
          console.log(`   Customer ID: ${customerId}`);
          console.log(`   이름: ${customerName}`);
          console.log(`   전화번호: ${phoneNumber}`);
          console.log(`   유입 경로: ${attributionSource || 'null'}`);
          console.log(`   가입일: ${registeredAt}`);
          console.log('---\n');
        }
      });

      console.log('\n=== 요약 ===');
      console.log(`총 고객 수: ${customersSnapshot.size}`);
      console.log(`추천 정보 있음: ${hasReferralCount}명`);
      console.log(`추천 정보 없음: ${noReferralCount}명`);
    }

  } catch (error) {
    console.error('오류 발생:', error);
  }

  process.exit(0);
}

checkCustomerReferrals();
