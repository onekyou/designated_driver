const admin = require('firebase-admin');

// Firebase Admin SDK 초기화
const serviceAccount = require('./service-account-key.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkSharedCalls() {
  console.log('🔍 shared_calls 컬렉션 상세 확인...\n');

  try {
    const snapshot = await db.collection('shared_calls').get();

    console.log(`총 shared_calls: ${snapshot.size}개\n`);

    const statusCount = {};
    let oldCount = 0;
    const oneHourAgo = Date.now() - (60 * 60 * 1000);

    const oldCalls = [];

    snapshot.forEach(doc => {
      const data = doc.data();
      const status = data.status || 'UNKNOWN';
      const createdAt = data.createdAt?.toMillis() || 0;

      statusCount[status] = (statusCount[status] || 0) + 1;

      if (createdAt < oneHourAgo) {
        oldCount++;
        oldCalls.push({
          id: doc.id,
          status,
          createdAt: new Date(createdAt).toLocaleString('ko-KR'),
          sourceOffice: data.sourceOfficeName || 'N/A',
          targetOffice: data.claimedOfficeName || 'N/A'
        });
      }
    });

    console.log('상태별 분포:');
    Object.entries(statusCount).forEach(([status, count]) => {
      console.log(`  ${status}: ${count}개`);
    });

    console.log(`\n⚠️ 1시간 이상 된 공유콜: ${oldCount}개`);

    if (oldCalls.length > 0) {
      console.log('\n상세 내역:');
      oldCalls.forEach((call, index) => {
        console.log(`  ${index + 1}. ${call.id}`);
        console.log(`     상태: ${call.status}`);
        console.log(`     생성일: ${call.createdAt}`);
        console.log(`     출발 사무실: ${call.sourceOffice}`);
        console.log(`     수락 사무실: ${call.targetOffice}`);
      });
    }

    console.log(`\n💡 삭제 대상: ${oldCount}개 (전체의 ${((oldCount / snapshot.size) * 100).toFixed(1)}%)`);

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

checkSharedCalls();
