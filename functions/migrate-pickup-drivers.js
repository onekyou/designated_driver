const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json'); // 서비스 계정 키 파일

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function migratePickupDrivers() {
  console.log('픽업 기사 데이터 마이그레이션 시작...');
  
  try {
    const results = {
      found: 0,
      migrated: 0,
      errors: 0,
      details: []
    };

    // designated_drivers에서 driverType이 PICKUP인 기사들 찾기
    const pickupDriversInDesignated = await db
      .collectionGroup('designated_drivers')
      .where('driverType', '==', 'PICKUP')
      .get();

    results.found = pickupDriversInDesignated.size;
    console.log(`발견된 픽업 기사: ${results.found}명`);

    for (const doc of pickupDriversInDesignated.docs) {
      try {
        const driverData = doc.data();
        const driverId = doc.id;
        
        // 경로에서 regionId와 officeId 추출
        const pathSegments = doc.ref.path.split('/');
        const regionId = pathSegments[1]; // regions/{regionId}
        const officeId = pathSegments[3]; // offices/{officeId}
        
        console.log(`마이그레이션 중: ${driverData.name} (${regionId}/${officeId})`);

        // pickup_drivers 컬렉션에 새 문서 생성
        const pickupDriverRef = db
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('pickup_drivers')
          .doc(driverId);

        await pickupDriverRef.set(driverData);
        
        // 원본 designated_drivers 문서 삭제
        await doc.ref.delete();
        
        results.migrated++;
        results.details.push(`✅ ${driverData.name} (${regionId}/${officeId}) 마이그레이션 완료`);
        
      } catch (error) {
        results.errors++;
        console.error(`픽업 기사 마이그레이션 오류: ${doc.id}`, error);
        results.details.push(`❌ ${doc.id} 마이그레이션 실패: ${error.message}`);
      }
    }

    console.log(`\n마이그레이션 완료:`);
    console.log(`- 발견: ${results.found}명`);
    console.log(`- 성공: ${results.migrated}명`);
    console.log(`- 실패: ${results.errors}명`);
    
    results.details.forEach(detail => console.log(detail));
    
    return results;

  } catch (error) {
    console.error('마이그레이션 전체 오류:', error);
  }
}

migratePickupDrivers().then(() => {
  console.log('\n마이그레이션 완료. 프로세스 종료.');
  process.exit(0);
}).catch(error => {
  console.error('마이그레이션 실패:', error);
  process.exit(1);
});