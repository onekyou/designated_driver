const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function updateOfficeStats() {
  try {
    console.log('=== 모든 사무실에 통계 필드 추가 ===\n');

    // 모든 region 조회
    const regionsSnapshot = await db.collection('regions').get();

    let totalOffices = 0;
    let updatedOffices = 0;

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      console.log(`\n📍 ${regionId} 지역 처리 중...`);

      // 해당 region의 모든 offices 조회
      const officesSnapshot = await db
        .collection('regions')
        .doc(regionId)
        .collection('offices')
        .get();

      console.log(`   사무실 수: ${officesSnapshot.size}`);

      for (const officeDoc of officesSnapshot.docs) {
        totalOffices++;
        const officeId = officeDoc.id;
        const officeName = officeDoc.data().name || '이름없음';

        // 기사 수 조회
        const driversSnapshot = await db
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('designated_drivers')
          .get();
        const driverCount = driversSnapshot.size;

        // 고객 수 조회
        const customersSnapshot = await db
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('customers')
          .get();
        const customerCount = customersSnapshot.size;

        // 사무실 문서 업데이트
        await db
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .update({
            driverCount: driverCount,
            customerCount: customerCount,
            statsUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
          });

        updatedOffices++;
        console.log(`   ✅ ${officeName}: 기사 ${driverCount}명, 고객 ${customerCount}명`);
      }
    }

    console.log('\n=== 완료 ===');
    console.log(`총 사무실: ${totalOffices}개`);
    console.log(`업데이트 완료: ${updatedOffices}개`);

  } catch (error) {
    console.error('오류 발생:', error);
  } finally {
    process.exit(0);
  }
}

updateOfficeStats();
