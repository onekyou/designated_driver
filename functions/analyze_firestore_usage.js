const admin = require('firebase-admin');

// Firebase Admin SDK 초기화
const serviceAccount = require('./service-account-key.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

/**
 * Firestore 전체 컬렉션 사용량 분석
 * - 각 컬렉션의 문서 개수
 * - 오래된 데이터 식별
 * - 불필요한 중복 읽기 패턴 분석
 */
async function analyzeFirestoreUsage() {
  console.log('📊 Firestore 사용량 분석 시작...\n');

  const results = {
    topLevel: {},
    nested: {},
    oldData: [],
    recommendations: []
  };

  try {
    // 1. Top-level 컬렉션 분석
    console.log('=== 1. Top-Level 컬렉션 ===\n');

    // admins
    const adminsSnapshot = await db.collection('admins').get();
    results.topLevel.admins = adminsSnapshot.size;
    console.log(`✅ admins: ${adminsSnapshot.size}개`);

    // pending_drivers
    const pendingDriversSnapshot = await db.collection('pending_drivers').get();
    results.topLevel.pending_drivers = pendingDriversSnapshot.size;
    console.log(`✅ pending_drivers: ${pendingDriversSnapshot.size}개`);

    // attributionTokens
    const tokensSnapshot = await db.collection('attributionTokens').get();
    results.topLevel.attributionTokens = tokensSnapshot.size;
    console.log(`✅ attributionTokens: ${tokensSnapshot.size}개`);

    // attributionTokens 중 오래된 것 확인
    let oldTokens = 0;
    const thirtyDaysAgo = Date.now() - (30 * 24 * 60 * 60 * 1000);
    tokensSnapshot.forEach(doc => {
      const data = doc.data();
      const createdAt = data.createdAt?.toMillis() || 0;
      if (createdAt < thirtyDaysAgo) {
        oldTokens++;
      }
    });
    if (oldTokens > 0) {
      results.oldData.push(`attributionTokens: ${oldTokens}개의 30일 이상 된 토큰`);
      console.log(`   ⚠️ 30일 이상 된 토큰: ${oldTokens}개`);
    }

    // shared_calls
    const sharedCallsSnapshot = await db.collection('shared_calls').get();
    results.topLevel.shared_calls = sharedCallsSnapshot.size;
    console.log(`✅ shared_calls: ${sharedCallsSnapshot.size}개`);

    // 2. Regions 분석
    console.log('\n=== 2. Regions/Offices 분석 ===\n');

    const regionsSnapshot = await db.collection('regions').get();
    console.log(`✅ Regions: ${regionsSnapshot.size}개\n`);

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      console.log(`📍 Region: ${regionId}`);

      const officesSnapshot = await db.collection('regions').doc(regionId)
        .collection('offices').get();

      console.log(`   사무실 수: ${officesSnapshot.size}개`);

      for (const officeDoc of officesSnapshot.docs) {
        const officeId = officeDoc.id;
        const officeData = officeDoc.data();
        console.log(`\n   🏢 Office: ${officeData.officeName || officeId}`);

        // 각 서브컬렉션 분석
        const collections = [
          'calls',
          'customers',
          'designated_drivers',
          'pickup_drivers',
          'attributions',
          'customerInfo',
          'managerTokens',
          'points',
          'point_transactions',
          'pointTransactions',
          'customerPoints',
          'settings',
          'dailySettlements'
        ];

        for (const collectionName of collections) {
          try {
            const snapshot = await db.collection('regions').doc(regionId)
              .collection('offices').doc(officeId)
              .collection(collectionName).get();

            if (snapshot.size > 0) {
              console.log(`      ✅ ${collectionName}: ${snapshot.size}개`);

              const key = `${regionId}/${officeId}/${collectionName}`;
              if (!results.nested[collectionName]) {
                results.nested[collectionName] = 0;
              }
              results.nested[collectionName] += snapshot.size;

              // 특정 컬렉션 상세 분석
              if (collectionName === 'calls') {
                await analyzeCallsCollection(snapshot, regionId, officeId, results);
              } else if (collectionName === 'attributions') {
                await analyzeAttributionsCollection(snapshot, regionId, officeId, results);
              } else if (collectionName === 'point_transactions' || collectionName === 'pointTransactions') {
                await analyzePointTransactions(snapshot, regionId, officeId, collectionName, results);
              } else if (collectionName === 'customerInfo') {
                await analyzeCustomerInfo(snapshot, regionId, officeId, results);
              }
            }
          } catch (error) {
            // 컬렉션이 없으면 무시
          }
        }
      }
    }

    // 3. 결과 요약
    console.log('\n\n=== 📊 분석 결과 요약 ===\n');

    console.log('📂 Top-Level 컬렉션:');
    Object.entries(results.topLevel).forEach(([name, count]) => {
      console.log(`   ${name}: ${count}개`);
    });

    console.log('\n📂 Nested 컬렉션 (전체 합계):');
    Object.entries(results.nested).forEach(([name, count]) => {
      console.log(`   ${name}: ${count}개`);
    });

    if (results.oldData.length > 0) {
      console.log('\n⚠️ 오래된 데이터:');
      results.oldData.forEach(msg => console.log(`   ${msg}`));
    }

    if (results.recommendations.length > 0) {
      console.log('\n💡 최적화 권장사항:');
      results.recommendations.forEach(msg => console.log(`   ${msg}`));
    }

  } catch (error) {
    console.error('❌ 오류 발생:', error);
  }
}

/**
 * calls 컬렉션 상세 분석
 */
async function analyzeCallsCollection(snapshot, regionId, officeId, results) {
  const statusCount = {};
  let oldWaiting = 0;
  let oldCompleted = 0;

  const oneHourAgo = Date.now() - (60 * 60 * 1000);
  const sevenDaysAgo = Date.now() - (7 * 24 * 60 * 60 * 1000);

  snapshot.forEach(doc => {
    const data = doc.data();
    const status = data.status || 'UNKNOWN';

    statusCount[status] = (statusCount[status] || 0) + 1;

    // WAITING 콜 중 1시간 이상 된 것
    if (status === 'WAITING') {
      const createdAt = data.createdAt?.toMillis() || 0;
      if (createdAt < oneHourAgo) {
        oldWaiting++;
      }
    }

    // COMPLETED 콜 중 7일 이상 된 것
    if (status === 'COMPLETED') {
      const completedAt = data.completedAt?.toMillis() || data.updatedAt?.toMillis() || 0;
      if (completedAt < sevenDaysAgo) {
        oldCompleted++;
      }
    }
  });

  console.log(`         상태별: ${JSON.stringify(statusCount)}`);

  if (oldWaiting > 0) {
    results.oldData.push(`${regionId}/${officeId}/calls: ${oldWaiting}개의 1시간 이상 된 WAITING 콜`);
    console.log(`         ⚠️ 1시간 이상 된 WAITING: ${oldWaiting}개`);
  }

  if (oldCompleted > 0) {
    results.oldData.push(`${regionId}/${officeId}/calls: ${oldCompleted}개의 7일 이상 된 COMPLETED 콜`);
    console.log(`         ⚠️ 7일 이상 된 COMPLETED: ${oldCompleted}개`);
  }
}

/**
 * attributions 컬렉션 상세 분석
 */
async function analyzeAttributionsCollection(snapshot, regionId, officeId, results) {
  let expiredCount = 0;
  const now = Date.now();

  snapshot.forEach(doc => {
    const data = doc.data();
    const expiresAt = data.expiresAt?.toMillis() || 0;

    if (expiresAt > 0 && expiresAt < now) {
      expiredCount++;
    }
  });

  if (expiredCount > 0) {
    results.oldData.push(`${regionId}/${officeId}/attributions: ${expiredCount}개의 만료된 attribution`);
    console.log(`         ⚠️ 만료된 attribution: ${expiredCount}개`);
    results.recommendations.push(`attributions 컬렉션: 만료된 데이터 ${expiredCount}개 삭제 권장`);
  }
}

/**
 * point_transactions 컬렉션 상세 분석
 */
async function analyzePointTransactions(snapshot, regionId, officeId, collectionName, results) {
  let oldCount = 0;
  const ninetyDaysAgo = Date.now() - (90 * 24 * 60 * 60 * 1000);

  snapshot.forEach(doc => {
    const data = doc.data();
    const createdAt = data.createdAt?.toMillis() || data.timestamp?.toMillis() || 0;

    if (createdAt < ninetyDaysAgo) {
      oldCount++;
    }
  });

  if (oldCount > 0) {
    results.oldData.push(`${regionId}/${officeId}/${collectionName}: ${oldCount}개의 90일 이상 된 거래 내역`);
    console.log(`         ⚠️ 90일 이상 된 거래: ${oldCount}개`);
    results.recommendations.push(`${collectionName}: 90일 이상 된 거래 내역 아카이브 권장`);
  }
}

/**
 * customerInfo 컬렉션 상세 분석
 */
async function analyzeCustomerInfo(snapshot, regionId, officeId, results) {
  let inactiveCount = 0;
  const ninetyDaysAgo = Date.now() - (90 * 24 * 60 * 60 * 1000);

  snapshot.forEach(doc => {
    const data = doc.data();
    const lastUpdated = data.lastUpdated || data.updatedAt?.toMillis() || 0;

    // 90일 이상 업데이트 안 된 FCM 토큰
    if (lastUpdated < ninetyDaysAgo) {
      inactiveCount++;
    }
  });

  if (inactiveCount > 0) {
    results.oldData.push(`${regionId}/${officeId}/customerInfo: ${inactiveCount}개의 90일 이상 비활성 FCM 토큰`);
    console.log(`         ⚠️ 90일 이상 비활성 FCM 토큰: ${inactiveCount}개`);
    results.recommendations.push(`customerInfo: 비활성 FCM 토큰 ${inactiveCount}개 삭제 권장`);
  }
}

// 실행
analyzeFirestoreUsage()
  .then(() => {
    console.log('\n✅ 분석 완료');
    process.exit(0);
  })
  .catch(error => {
    console.error('❌ 오류:', error);
    process.exit(1);
  });
