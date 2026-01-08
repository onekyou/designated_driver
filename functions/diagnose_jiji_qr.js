const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function diagnoseJijiQR() {
  try {
    console.log('=== 지지 사무실 QR 진단 ===\n');

    // 1. 지지 사무실 확인
    console.log('1️⃣ 지지 사무실 정보:');
    const jijiDoc = await db
      .collection('regions/Hongchon/offices')
      .doc('1xpcATEs3EvyZwJ00k8G')
      .get();

    if (!jijiDoc.exists) {
      console.log('   ❌ 지지 사무실을 찾을 수 없습니다!');
      return;
    }

    const jijiData = jijiDoc.data();
    console.log('   ✅ 사무실 존재');
    console.log('   이름:', jijiData.name);
    console.log('   전화:', jijiData.phone || jijiData.phoneNumber || 'N/A');
    console.log('   은행:', jijiData.bankName || 'N/A');
    console.log('   계좌:', jijiData.accountNumber || 'N/A');
    console.log('   예금주:', jijiData.accountHolder || 'N/A');

    // 2. 지지 사무실의 기사 목록 확인
    console.log('\n2️⃣ 지지 사무실 기사 목록:');
    const driversSnapshot = await db
      .collection('regions/Hongchon/offices/1xpcATEs3EvyZwJ00k8G/drivers')
      .get();

    if (driversSnapshot.empty) {
      console.log('   ⚠️  등록된 기사 없음');
      console.log('   → QR 생성 시 기사 정보를 포함하려면 기사가 필요합니다');
    } else {
      console.log(`   ✅ ${driversSnapshot.size}명의 기사 등록됨:`);
      driversSnapshot.forEach(doc => {
        const driverData = doc.data();
        console.log(`   - ${driverData.name} (${doc.id})`);
      });
    }

    // 3. 예상되는 QR URL 포맷 출력
    console.log('\n3️⃣ 지지 사무실 QR URL 포맷:');
    console.log('\n   [기본 QR - 사무실만]');
    console.log('   https://calldetector-5d61e.web.app/download?r=Hongchon&o=1xpcATEs3EvyZwJ00k8G');

    if (!driversSnapshot.empty) {
      const firstDriver = driversSnapshot.docs[0];
      const driverData = firstDriver.data();
      console.log('\n   [기사 추천 QR - 예시]');
      console.log(`   https://calldetector-5d61e.web.app/download?r=Hongchon&o=1xpcATEs3EvyZwJ00k8G&d=${firstDriver.id}&dn=${encodeURIComponent(driverData.name)}`);
    }

    // 4. 현재 attribution 상태
    console.log('\n4️⃣ 현재 Attribution 상태:');
    const attributionsSnapshot = await db
      .collection('regions/Hongchon/offices/1xpcATEs3EvyZwJ00k8G/attributions')
      .get();

    if (attributionsSnapshot.empty) {
      console.log('   ❌ Attribution 0개');
      console.log('   → 랜딩페이지에서 attribution이 생성되지 않고 있습니다!');
      console.log('   → 이것이 매칭 실패의 원인입니다.');
    } else {
      console.log(`   ✅ Attribution ${attributionsSnapshot.size}개 존재:`);
      attributionsSnapshot.forEach(doc => {
        const data = doc.data();
        const minutesAgo = Math.round((Date.now() - data.createdAt.toDate()) / 60000);
        console.log(`\n   문서 ID: ${doc.id}`);
        console.log(`   해상도: ${data.screenResolution}`);
        console.log(`   토큰: ${data.token}`);
        console.log(`   생성: ${minutesAgo}분 전`);
        console.log(`   사용됨: ${data.claimed ? 'Yes' : 'No'}`);
        if (data.driverId) {
          console.log(`   추천기사: ${data.driverName} (${data.driverId})`);
        }
      });
    }

    // 5. 모든 지역의 모든 attribution 확인 (혹시 잘못된 곳에 생성되었는지)
    console.log('\n5️⃣ 전체 시스템 Attribution 확인 (최근 1시간):');
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
    const regionsSnapshot = await db.collection('regions').get();

    let foundAny = false;
    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      const officesSnapshot = await db.collection(`regions/${regionId}/offices`).get();

      for (const officeDoc of officesSnapshot.docs) {
        const officeId = officeDoc.id;
        const officeData = officeDoc.data();
        const attributionsRef = db.collection(`regions/${regionId}/offices/${officeId}/attributions`);
        const recentAttributions = await attributionsRef
          .where('createdAt', '>', oneHourAgo)
          .get();

        if (!recentAttributions.empty) {
          foundAny = true;
          console.log(`\n   📍 ${regionId} > ${officeData.name || officeId}:`);
          console.log(`      최근 1시간 내 ${recentAttributions.size}개 생성됨`);
          recentAttributions.forEach(doc => {
            const data = doc.data();
            const minutesAgo = Math.round((Date.now() - data.createdAt.toDate()) / 60000);
            console.log(`      - ${data.screenResolution}, ${minutesAgo}분 전, 토큰: ${data.token.substring(0, 8)}...`);
          });
        }
      }
    }

    if (!foundAny) {
      console.log('   ❌ 최근 1시간 내 생성된 attribution이 전체 시스템에 없습니다!');
      console.log('   → 랜딩페이지의 attribution 생성 로직이 작동하지 않습니다.');
    }

    // 6. 문제 해결 방안 제시
    console.log('\n📋 진단 결과 및 해결 방안:');
    console.log('─────────────────────────────────────');

    if (attributionsSnapshot.empty && !foundAny) {
      console.log('❌ 문제: 랜딩페이지가 attribution을 생성하지 못하고 있음');
      console.log('\n가능한 원인:');
      console.log('1. 랜딩페이지 JavaScript 실행 오류');
      console.log('2. Firebase Web SDK 초기화 실패');
      console.log('3. Firestore 권한 문제');
      console.log('4. QR URL 파라미터 형식 오류');
      console.log('\n해결 방법:');
      console.log('1. 브라우저 개발자 도구 콘솔에서 오류 확인');
      console.log('2. 네트워크 탭에서 Firestore 요청 확인');
      console.log('3. 랜딩페이지 console.log 출력 확인');
      console.log('4. QR URL이 올바른 형식인지 확인');
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

diagnoseJijiQR();
