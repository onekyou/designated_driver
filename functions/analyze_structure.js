const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function analyzeStructure() {
  console.log('========================================');
  console.log('Firebase 컬렉션 구조 최적화 분석');
  console.log('========================================\n');

  const issues = [];
  const recommendations = [];
  const goodPractices = [];

  // 1. 최상위 컬렉션 분석
  console.log('📋 1. 최상위 컬렉션 분석\n');

  const collections = await db.listCollections();
  const topLevelCollections = collections.map(c => c.id);

  console.log('현재 최상위 컬렉션:', topLevelCollections.join(', '));
  console.log('');

  // 2. 데이터 일관성 검사
  console.log('📋 2. 데이터 일관성 검사\n');

  // 2-1. admins와 offices 연결 확인
  const adminsSnapshot = await db.collection('admins').get();
  console.log(`✓ 총 관리자 수: ${adminsSnapshot.size}명`);

  let orphanedAdmins = 0;
  let validAdmins = 0;

  for (const adminDoc of adminsSnapshot.docs) {
    const adminData = adminDoc.data();
    const regionId = adminData.associatedRegionId;
    const officeId = adminData.associatedOfficeId;

    if (regionId && officeId) {
      const officeRef = db.collection('regions').doc(regionId).collection('offices').doc(officeId);
      const officeDoc = await officeRef.get();

      if (officeDoc.exists) {
        validAdmins++;
      } else {
        orphanedAdmins++;
        issues.push(`⚠️  관리자 ${adminData.email}의 사무실(${regionId}/${officeId})이 존재하지 않음`);
      }
    } else {
      orphanedAdmins++;
      issues.push(`⚠️  관리자 ${adminDoc.id}에 지역/사무실 정보 누락`);
    }
  }

  console.log(`  - 정상 연결: ${validAdmins}명`);
  console.log(`  - 연결 오류: ${orphanedAdmins}명`);
  console.log('');

  // 2-2. 각 사무실의 관리자 수 확인
  console.log('📋 3. 사무실별 관리자 배정 확인\n');

  const regionsSnapshot = await db.collection('regions').get();

  for (const regionDoc of regionsSnapshot.docs) {
    const officesSnapshot = await db.collection('regions').doc(regionDoc.id).collection('offices').get();

    console.log(`지역: ${regionDoc.data().name} (${regionDoc.id})`);

    for (const officeDoc of officesSnapshot.docs) {
      const officeData = officeDoc.data();

      // 해당 사무실의 관리자 찾기
      const officeAdmins = adminsSnapshot.docs.filter(a =>
        a.data().associatedOfficeId === officeDoc.id &&
        a.data().associatedRegionId === regionDoc.id
      );

      const adminCount = officeAdmins.length;
      const statusIcon = adminCount === 0 ? '❌' : adminCount === 1 ? '✅' : '⚠️';

      console.log(`  ${statusIcon} ${officeData.name || officeDoc.id}: ${adminCount}명의 관리자`);

      if (adminCount === 0) {
        issues.push(`❌ 사무실 "${officeData.name}"에 관리자가 없음`);
      } else if (adminCount > 1) {
        issues.push(`⚠️  사무실 "${officeData.name}"에 관리자가 ${adminCount}명 (중복 가능성)`);
      } else {
        goodPractices.push(`✅ 사무실 "${officeData.name}": 1명의 관리자 (정상)`);
      }

      // 사무실의 settings 확인
      const settingsRef = db.collection('regions').doc(regionDoc.id)
        .collection('offices').doc(officeDoc.id)
        .collection('settings').doc('attribution');

      const settingsDoc = await settingsRef.get();

      if (!settingsDoc.exists) {
        issues.push(`⚠️  사무실 "${officeData.name}"에 settings/attribution이 없음 (QR 미생성)`);
      } else {
        const settingsData = settingsDoc.data();
        if (settingsData.qrCode && settingsData.landingPageUrl) {
          goodPractices.push(`✅ 사무실 "${officeData.name}": QR 및 랜딩페이지 설정 완료`);
        } else {
          issues.push(`⚠️  사무실 "${officeData.name}": QR 또는 랜딩페이지 URL 누락`);
        }
      }
    }
    console.log('');
  }

  // 3. 컬렉션 구조 평가
  console.log('📋 4. 컬렉션 구조 평가\n');

  // 3-1. point_transactions 위치 확인
  const topLevelPtSnapshot = await db.collection('point_transactions').limit(1).get();
  if (!topLevelPtSnapshot.empty) {
    const sampleDoc = topLevelPtSnapshot.docs[0].data();
    console.log('✓ 최상위 point_transactions 확인됨');
    console.log('  샘플 데이터:', JSON.stringify(sampleDoc, null, 2));

    if (sampleDoc.sourceOfficeId || sampleDoc.targetOfficeId) {
      goodPractices.push('✅ 최상위 point_transactions: 사무실간 공유콜 포인트 (적절함)');
    }
  }
  console.log('');

  // 3-2. attributions vs pre_attributions
  const attributionsCount = (await db.collection('attributions').get()).size;
  const preAttributionsCount = (await db.collection('pre_attributions').get()).size;

  console.log(`✓ attributions: ${attributionsCount}건 (최종 매칭)`);
  console.log(`✓ pre_attributions: ${preAttributionsCount}건 (사전 방문)`);

  if (preAttributionsCount > attributionsCount * 2) {
    recommendations.push('💡 pre_attributions가 많음 → 앱 설치 전환율이 낮을 수 있음');
  } else {
    goodPractices.push('✅ 사전 방문 대비 매칭 전환율 양호');
  }
  console.log('');

  // 3-3. users 컬렉션 확인
  const usersSnapshot = await db.collection('users').limit(5).get();
  if (!usersSnapshot.empty) {
    console.log(`✓ users 컬렉션: ${usersSnapshot.size}건 샘플`);
    const sampleUser = usersSnapshot.docs[0].data();
    console.log('  샘플 데이터:', JSON.stringify(sampleUser, null, 2));

    recommendations.push('💡 users 컬렉션의 용도 확인 필요 (고객? 기사? 중복?)');
  }
  console.log('');

  // 3-4. device_status vs device_alerts
  const deviceStatusCount = (await db.collection('device_status').limit(1).get()).size;
  const deviceAlertsCount = (await db.collection('device_alerts').limit(1).get()).size;

  if (deviceStatusCount > 0 || deviceAlertsCount > 0) {
    console.log(`✓ device_status: ${deviceStatusCount > 0 ? '있음' : '없음'}`);
    console.log(`✓ device_alerts: ${deviceAlertsCount > 0 ? '있음' : '없음'}`);
    goodPractices.push('✅ 디바이스 모니터링 시스템 구축됨');
  }
  console.log('');

  // 4. 인덱싱 권장사항
  console.log('📋 5. 인덱싱 권장사항\n');

  recommendations.push('💡 admins 컬렉션: associatedOfficeId에 인덱스 추가 권장');
  recommendations.push('💡 attributions 컬렉션: phoneNumber, officeId에 복합 인덱스 권장');
  recommendations.push('💡 shared_calls 컬렉션: sourceOfficeId, status에 복합 인덱스 권장');
  console.log('');

  // 5. 보안 규칙 권장사항
  console.log('📋 6. 보안 규칙 권장사항\n');

  recommendations.push('💡 사무실별 데이터 격리: 관리자는 자기 사무실만 접근');
  recommendations.push('💡 settings/attribution: 생성 후 수정 불가 (무결성)');
  recommendations.push('💡 point_transactions: 생성만 가능, 수정/삭제 불가 (이력 보존)');
  console.log('');

  // 최종 요약
  console.log('\n========================================');
  console.log('📊 분석 결과 요약');
  console.log('========================================\n');

  console.log(`✅ 정상 항목: ${goodPractices.length}개`);
  goodPractices.forEach(p => console.log(p));
  console.log('');

  console.log(`⚠️  문제 항목: ${issues.length}개`);
  issues.forEach(i => console.log(i));
  console.log('');

  console.log(`💡 개선 권장: ${recommendations.length}개`);
  recommendations.forEach(r => console.log(r));
  console.log('');

  // 전체 평가
  const totalChecks = goodPractices.length + issues.length;
  const score = Math.round((goodPractices.length / totalChecks) * 100);

  console.log('========================================');
  console.log(`종합 점수: ${score}점 / 100점`);

  if (score >= 80) {
    console.log('평가: 🟢 양호 - 대부분의 구조가 적절합니다');
  } else if (score >= 60) {
    console.log('평가: 🟡 보통 - 일부 개선이 필요합니다');
  } else {
    console.log('평가: 🔴 미흡 - 구조 개선이 필요합니다');
  }
  console.log('========================================');
}

analyzeStructure().then(() => process.exit(0));