const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function setupGggOffice() {
  try {
    console.log('=== ggg 사무실 정보 설정 ===\n');

    const officeData = {
      name: 'ggg',
      phone: '01036702011',
      phoneNumber: '01036702011',
      bankName: 'NH농협은행',
      accountNumber: '3333149014688',
      accountHolder: 'ggg',
      createdAt: admin.firestore.Timestamp.now(),
      updatedAt: admin.firestore.Timestamp.now()
    };

    await db
      .collection('regions')
      .document('Hongchon')
      .collection('offices')
      .document('qwfdeSOL8Vz4lXEEP4TD')
      .set(officeData, { merge: true });

    console.log('✅ ggg 사무실 정보 설정 완료:');
    console.log('   이름:', officeData.name);
    console.log('   전화:', officeData.phone);
    console.log('   은행:', officeData.bankName);
    console.log('   계좌:', officeData.accountNumber);
    console.log('   예금주:', officeData.accountHolder);

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

setupGggOffice();
