const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function debugAttributionFlow() {
  try {
    console.log('=== Attribution 플로우 디버깅 ===\n');

    // 1. ggg 사무실 확인
    console.log('1️⃣ ggg 사무실 정보 확인:');
    const officeDoc = await db
      .collection('regions/Hongchon/offices')
      .doc('qwfdeSOL8Vz4lXEEP4TD')
      .get();

    if (officeDoc.exists) {
      const data = officeDoc.data();
      console.log('   ✅ 사무실 존재');
      console.log('   이름:', data.name);
      console.log('   전화:', data.phone || data.phoneNumber || 'N/A');
      console.log('   은행:', data.bankName || 'N/A');
      console.log('   계좌:', data.accountNumber || 'N/A');
    } else {
      console.log('   ❌ 사무실을 찾을 수 없음!');
      return;
    }

    // 2. 최근 attribution 확인 (모든 해상도)
    console.log('\n2️⃣ 최근 생성된 attribution (최근 1시간):');
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);

    const attributionsSnapshot = await db
      .collection('regions/Hongchon/offices/qwfdeSOL8Vz4lXEEP4TD/attributions')
      .where('createdAt', '>', oneHourAgo)
      .orderBy('createdAt', 'desc')
      .limit(10)
      .get();

    if (attributionsSnapshot.empty) {
      console.log('   ❌ 최근 1시간 내 attribution 없음');
    } else {
      console.log(`   ✅ ${attributionsSnapshot.size}개 발견:\n`);
      attributionsSnapshot.forEach((doc, index) => {
        const data = doc.data();
        const minutesAgo = Math.round((Date.now() - data.createdAt.toDate()) / 60000);
        console.log(`   [${index + 1}] ${minutesAgo}분 전 생성`);
        console.log(`       문서 ID: ${doc.id}`);
        console.log(`       해상도: ${data.screenResolution}`);
        console.log(`       토큰: ${data.token}`);
        console.log(`       기사: ${data.driverName || 'N/A'} (${data.driverId || 'N/A'})`);
        console.log(`       사용됨: ${data.claimed ? 'Yes' : 'No'}`);
        console.log('');
      });
    }

    // 3. 모든 해상도의 attribution 개수 확인
    console.log('3️⃣ 사무실의 모든 attribution:');
    const allAttributions = await db
      .collection('regions/Hongchon/offices/qwfdeSOL8Vz4lXEEP4TD/attributions')
      .get();

    console.log(`   총 ${allAttributions.size}개 존재`);

    // 해상도별로 그룹화
    const resolutionMap = {};
    allAttributions.forEach(doc => {
      const resolution = doc.data().screenResolution;
      resolutionMap[resolution] = (resolutionMap[resolution] || 0) + 1;
    });

    console.log('\n   해상도별 개수:');
    Object.entries(resolutionMap).forEach(([resolution, count]) => {
      console.log(`   - ${resolution}: ${count}개`);
    });

    // 4. 최근 고객 가입 확인
    console.log('\n4️⃣ 최근 고객 가입 (최근 1시간):');
    const customersSnapshot = await db
      .collection('regions/Hongchon/offices/qwfdeSOL8Vz4lXEEP4TD/customers')
      .where('registeredAt', '>', oneHourAgo)
      .orderBy('registeredAt', 'desc')
      .limit(5)
      .get();

    if (customersSnapshot.empty) {
      console.log('   ❌ 최근 1시간 내 가입한 고객 없음');
    } else {
      console.log(`   ✅ ${customersSnapshot.size}명 발견:\n`);
      customersSnapshot.forEach((doc, index) => {
        const data = doc.data();
        const minutesAgo = Math.round((Date.now() - data.registeredAt.toDate()) / 60000);
        console.log(`   [${index + 1}] ${minutesAgo}분 전 가입`);
        console.log(`       고객 ID: ${doc.id}`);
        console.log(`       이름: ${data.name}`);
        console.log(`       전화: ${data.phoneNumber}`);
        console.log(`       토큰: ${data.attributionToken || 'N/A'}`);
        console.log(`       추천기사: ${data.referralDriverName || 'N/A'}`);
        console.log('');
      });
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

debugAttributionFlow();
