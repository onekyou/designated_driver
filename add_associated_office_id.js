const admin = require('firebase-admin');

// Firebase Admin SDK 초기화 (서비스 계정 키 필요)
const serviceAccount = require('./functions/serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function addAssociatedOfficeId() {
  try {
    console.log('🔍 픽업 기사 문서 검색 중...');
    
    // regions/yangpyong/offices/office_4/pickup_drivers 컬렉션에서 모든 문서 가져오기
    const pickupDriversRef = db.collection('regions').doc('yangpyong')
                              .collection('offices').doc('office_4')
                              .collection('pickup_drivers');
    
    const snapshot = await pickupDriversRef.get();
    
    if (snapshot.empty) {
      console.log('❌ 픽업 기사 문서를 찾을 수 없습니다.');
      return;
    }

    console.log(`📋 ${snapshot.size}개의 픽업 기사 문서를 찾았습니다.`);

    const batch = db.batch();
    let updateCount = 0;

    snapshot.forEach(doc => {
      const data = doc.data();
      
      // associatedOfficeId 필드가 없는 경우에만 추가
      if (!data.associatedOfficeId) {
        console.log(`📝 업데이트: ${doc.id}`);
        batch.update(doc.ref, { associatedOfficeId: 'office_4' });
        updateCount++;
      } else {
        console.log(`⏭️ 건너뜀: ${doc.id} (이미 associatedOfficeId 존재)`);
      }
    });

    if (updateCount > 0) {
      await batch.commit();
      console.log(`✅ ${updateCount}개 문서에 associatedOfficeId: 'office_4' 필드를 추가했습니다.`);
    } else {
      console.log('ℹ️ 업데이트할 문서가 없습니다.');
    }

  } catch (error) {
    console.error('❌ 오류 발생:', error);
  }
}

// 스크립트 실행
addAssociatedOfficeId().then(() => {
  console.log('🏁 작업 완료');
  process.exit(0);
});