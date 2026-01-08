const admin = require('firebase-admin');

// Firebase Admin 초기화
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkQRSettings() {
  try {
    console.log('=== QR 코드 설정 확인 ===\n');

    // 모든 지역 확인
    const regionsSnapshot = await db.collection('regions').get();

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      const regionName = regionDoc.data().name;

      console.log(`지역: ${regionName} (${regionId})`);

      // 해당 지역의 모든 사무실 확인
      const officesSnapshot = await db
        .collection('regions')
        .doc(regionId)
        .collection('offices')
        .get();

      for (const officeDoc of officesSnapshot.docs) {
        const officeId = officeDoc.id;
        const officeData = officeDoc.data();

        console.log(`\n  사무실: ${officeData.name || officeId}`);
        console.log(`  ID: ${officeId}`);
        console.log(`  전화: ${officeData.phone || 'N/A'}`);
        console.log(`  은행: ${officeData.bankName || 'N/A'}`);
        console.log(`  계좌: ${officeData.accountNumber || 'N/A'}`);
        console.log(`  예금주: ${officeData.accountHolder || 'N/A'}`);

        // Attribution 설정 확인
        const attributionDoc = await db
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('settings')
          .doc('attribution')
          .get();

        if (attributionDoc.exists) {
          const attrData = attributionDoc.data();
          console.log(`\n  [QR 설정]`);
          console.log(`  QR 코드 URL: ${attrData.qrCode || 'N/A'}`);
          console.log(`  랜딩 페이지 URL: ${attrData.landingPageUrl || 'N/A'}`);
          console.log(`  임계값: ${attrData.attributionThreshold || 'N/A'}`);
        } else {
          console.log(`  [QR 설정] 없음`);
        }

        console.log('  ---');
      }
    }

  } catch (error) {
    console.error('에러 발생:', error);
  } finally {
    process.exit();
  }
}

checkQRSettings();
