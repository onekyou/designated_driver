const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkHongchonAll() {
  try {
    console.log('=== 홍천군 전체 상황 확인 ===\n');

    const officesSnapshot = await db
      .collection('regions/Hongchon/offices')
      .get();

    console.log(`📍 홍천군 사무실 총 ${officesSnapshot.size}개:\n`);

    for (const officeDoc of officesSnapshot.docs) {
      const officeId = officeDoc.id;
      const data = officeDoc.data();
      const officeName = data.name || 'N/A';

      console.log(`🏢 사무실: ${officeName} (${officeId})`);
      console.log(`   전화: ${data.phone || data.phoneNumber || 'N/A'}`);

      // 이 사무실의 모든 attribution 확인 (해상도별)
      const attributionsSnapshot = await db
        .collection(`regions/Hongchon/offices/${officeId}/attributions`)
        .get();

      if (attributionsSnapshot.empty) {
        console.log(`   Attribution: 0개\n`);
      } else {
        console.log(`   Attribution: ${attributionsSnapshot.size}개`);

        // 해상도별로 그룹화
        const resolutionMap = {};
        attributionsSnapshot.forEach(doc => {
          const resolution = doc.data().screenResolution;
          if (!resolutionMap[resolution]) {
            resolutionMap[resolution] = [];
          }
          resolutionMap[resolution].push({
            id: doc.id,
            token: doc.data().token,
            claimed: doc.data().claimed,
            createdAt: doc.data().createdAt?.toDate()
          });
        });

        Object.entries(resolutionMap).forEach(([resolution, items]) => {
          console.log(`   - ${resolution}: ${items.length}개`);
          items.forEach((item, idx) => {
            const timeAgo = item.createdAt ?
              Math.round((Date.now() - item.createdAt) / 60000) + '분 전' :
              'N/A';
            console.log(`     [${idx + 1}] ${timeAgo}, 사용: ${item.claimed ? 'O' : 'X'}, 토큰: ${item.token.substring(0, 8)}...`);
          });
        });
        console.log('');
      }
    }

    // 지지 사무실 찾기
    console.log('\n🔍 "지지" 사무실 검색:');
    const jijiOffice = officesSnapshot.docs.find(doc =>
      doc.data().name === '지지'
    );

    if (jijiOffice) {
      console.log(`   ✅ 발견: ID = ${jijiOffice.id}`);
    } else {
      console.log('   ❌ "지지" 사무실을 찾을 수 없습니다');
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

checkHongchonAll();
