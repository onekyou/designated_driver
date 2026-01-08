const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkAllCollections() {
  try {
    console.log('========================================');
    console.log('Firebase Firestore 전체 컬렉션 구조 확인');
    console.log('========================================\n');

    // 1. 최상위 컬렉션 확인
    console.log('📁 최상위 컬렉션:');
    const collections = await db.listCollections();
    collections.forEach(col => {
      console.log(`  - ${col.id}`);
    });
    console.log('');

    // 2. regions 확인
    console.log('📁 regions 컬렉션:');
    const regionsSnapshot = await db.collection('regions').get();

    for (const regionDoc of regionsSnapshot.docs) {
      console.log(`\n  지역: ${regionDoc.id}`);
      const regionData = regionDoc.data();
      console.log(`    이름: ${regionData.name || 'N/A'}`);

      // 각 지역의 offices 확인
      const officesSnapshot = await db.collection('regions').doc(regionDoc.id).collection('offices').get();
      console.log(`    사무실 수: ${officesSnapshot.size}개`);

      for (const officeDoc of officesSnapshot.docs) {
        console.log(`\n      사무실 ID: ${officeDoc.id}`);
        const officeData = officeDoc.data();
        console.log(`        이름: ${officeData.name || 'N/A'}`);
        console.log(`        전화: ${officeData.phoneNumber || 'N/A'}`);
        console.log(`        주소: ${officeData.address || 'N/A'}`);

        // 각 사무실의 서브컬렉션 확인
        const officeRef = db.collection('regions').doc(regionDoc.id).collection('offices').doc(officeDoc.id);
        const officeCollections = await officeRef.listCollections();

        if (officeCollections.length > 0) {
          console.log(`        서브컬렉션:`);
          for (const subCol of officeCollections) {
            const subColSnapshot = await subCol.limit(1).get();
            console.log(`          - ${subCol.id} (${subColSnapshot.size > 0 ? '데이터 있음' : '비어있음'})`);
          }
        }
      }
    }

    // 3. admins 확인
    console.log('\n\n📁 admins 컬렉션:');
    const adminsSnapshot = await db.collection('admins').get();
    console.log(`  총 관리자 수: ${adminsSnapshot.size}명`);
    adminsSnapshot.docs.forEach(doc => {
      const data = doc.data();
      console.log(`    - ${data.email || data.name || doc.id}`);
      console.log(`      지역: ${data.associatedRegionId || 'N/A'}`);
      console.log(`      사무실: ${data.associatedOfficeId || 'N/A'}`);
    });

    // 4. shared_calls 확인
    console.log('\n\n📁 shared_calls 컬렉션:');
    const sharedCallsSnapshot = await db.collection('shared_calls').limit(5).get();
    console.log(`  총 공유콜 수: ${sharedCallsSnapshot.size}개 (최근 5개)`);

    // 5. attributions 확인
    console.log('\n\n📁 attributions 컬렉션:');
    const attributionsSnapshot = await db.collection('attributions').limit(5).get();
    console.log(`  총 매칭 기록: ${attributionsSnapshot.size}개 (최근 5개)`);

    // 6. pre_attributions 확인
    console.log('\n\n📁 pre_attributions 컬렉션:');
    const preAttributionsSnapshot = await db.collection('pre_attributions').limit(5).get();
    console.log(`  총 사전 매칭: ${preAttributionsSnapshot.size}개 (최근 5개)`);

    // 7. notifications 확인
    console.log('\n\n📁 notifications 컬렉션:');
    const notificationsSnapshot = await db.collection('notifications').limit(5).get();
    console.log(`  총 알림: ${notificationsSnapshot.size}개 (최근 5개)`);

    console.log('\n========================================');
    console.log('확인 완료!');
    console.log('========================================');

  } catch (error) {
    console.error('Error:', error);
  }
}

checkAllCollections().then(() => process.exit(0));