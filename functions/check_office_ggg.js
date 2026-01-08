const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkOffice() {
  try {
    console.log('=== Hongchon 사무실 정보 확인 ===\n');

    const officeDoc = await db
      .collection('regions/Hongchon/offices')
      .doc('qwfdeSOL8Vz4lXEEP4TD')
      .get();

    if (officeDoc.exists) {
      const data = officeDoc.data();
      console.log('✅ 사무실 정보:');
      console.log('   사무실 이름:', data.name || 'N/A');
      console.log('   전화번호:', data.phoneNumber || 'N/A');
      console.log('   은행:', data.bankName || 'N/A');
      console.log('   계좌번호:', data.accountNumber || 'N/A');
      console.log('   예금주:', data.accountHolder || 'N/A');
      console.log('');
      console.log('전체 데이터:', JSON.stringify(data, null, 2));
    } else {
      console.log('❌ 사무실을 찾을 수 없습니다.');
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

checkOffice();
