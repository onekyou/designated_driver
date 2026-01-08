const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkOldCalls() {
  console.log('5일 이상 된 콜 상태 확인 중...\n');

  const fiveDaysAgo = new Date(Date.now() - (5 * 24 * 60 * 60 * 1000));

  const regionsSnapshot = await db.collection('regions').get();

  const statusCount = {};
  let totalOldCalls = 0;

  for (const regionDoc of regionsSnapshot.docs) {
    const regionId = regionDoc.id;
    const officesSnapshot = await db.collection('regions').doc(regionId)
      .collection('offices').get();

    for (const officeDoc of officesSnapshot.docs) {
      const officeId = officeDoc.id;

      const oldCalls = await db.collection('regions').doc(regionId)
        .collection('offices').doc(officeId)
        .collection('calls')
        .where('timestamp', '<', admin.firestore.Timestamp.fromDate(fiveDaysAgo))
        .get();

      if (oldCalls.size > 0) {
        console.log(`\n${regionId}/${officeId}: ${oldCalls.size}개의 오래된 콜`);

        oldCalls.docs.forEach(doc => {
          const data = doc.data();
          const status = data.status || 'UNKNOWN';

          statusCount[status] = (statusCount[status] || 0) + 1;
          totalOldCalls++;

          // 샘플로 최근 5개만 상세 출력
          if (totalOldCalls <= 10) {
            const timestamp = data.timestamp?.toDate();
            console.log(`  - ${doc.id}: ${status}, ${timestamp?.toLocaleString('ko-KR')}`);
          }
        });
      }
    }
  }

  console.log('\n\n=== 5일 이상 된 콜 통계 ===');
  console.log(`총 개수: ${totalOldCalls}개\n`);
  console.log('상태별 분포:');
  Object.entries(statusCount)
    .sort((a, b) => b[1] - a[1])
    .forEach(([status, count]) => {
      console.log(`  ${status}: ${count}개`);
    });
}

checkOldCalls()
  .then(() => {
    console.log('\n완료!');
    process.exit(0);
  })
  .catch(error => {
    console.error('오류:', error);
    process.exit(1);
  });
