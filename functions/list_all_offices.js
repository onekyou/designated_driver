const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function listAllOffices() {
  try {
    console.log('=== 모든 사무실 목록 ===\n');

    const regionsSnapshot = await db.collection('regions').get();

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      console.log(`📍 지역: ${regionId}`);

      const officesSnapshot = await db
        .collection(`regions/${regionId}/offices`)
        .get();

      if (officesSnapshot.empty) {
        console.log('   (사무실 없음)\n');
        continue;
      }

      for (const officeDoc of officesSnapshot.docs) {
        const officeId = officeDoc.id;
        const data = officeDoc.data();

        console.log(`\n  🏢 사무실 ID: ${officeId}`);
        console.log(`     이름: ${data.name || 'N/A'}`);
        console.log(`     전화: ${data.phone || data.phoneNumber || 'N/A'}`);
        console.log(`     은행: ${data.bankName || 'N/A'}`);
        console.log(`     계좌: ${data.accountNumber || 'N/A'}`);
        console.log(`     예금주: ${data.accountHolder || 'N/A'}`);

        // 하위 컬렉션 개수 확인
        const driversCount = (await db.collection(`regions/${regionId}/offices/${officeId}/drivers`).get()).size;
        const customersCount = (await db.collection(`regions/${regionId}/offices/${officeId}/customers`).get()).size;
        const attributionsCount = (await db.collection(`regions/${regionId}/offices/${officeId}/attributions`).get()).size;

        console.log(`     기사: ${driversCount}명, 고객: ${customersCount}명, Attributions: ${attributionsCount}개`);
      }
      console.log('');
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

listAllOffices();
