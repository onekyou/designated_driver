const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkOfficesData() {
  console.log('🔍 Firestore offices 데이터 구조 확인 중...\n');

  try {
    // collectionGroup으로 모든 offices 조회
    const officesSnapshot = await db.collectionGroup('offices').limit(3).get();

    if (officesSnapshot.empty) {
      console.log('❌ offices 컬렉션에 데이터가 없습니다.');
      return;
    }

    console.log(`✅ ${officesSnapshot.size}개의 사무실 발견\n`);

    officesSnapshot.forEach((doc, index) => {
      console.log(`\n=== 사무실 #${index + 1} ===`);
      console.log('Document Path:', doc.ref.path);
      console.log('Document ID:', doc.id);
      console.log('Data:', JSON.stringify(doc.data(), null, 2));
      console.log('Fields:', Object.keys(doc.data()));
    });

  } catch (error) {
    console.error('❌ 오류 발생:', error);
  }

  process.exit(0);
}

checkOfficesData();
