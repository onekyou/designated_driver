/**
 * Firestore 구조 변경: regions → provinces/cities 마이그레이션
 *
 * 기존 구조:
 *   regions/{regionId}/offices/{officeId}/...
 *
 * 새 구조:
 *   provinces/{provinceId}/cities/{cityId}/offices/{officeId}/...
 *
 * 실행 방법:
 *   node functions/migrate_regions_to_provinces.js
 *
 * 작성일: 2025-12-31
 */

const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// 기존 region ID → province ID, city ID 매핑
const regionMapping = {
  'Hongchon': {
    provinceId: 'gangwon',
    provinceName: '강원특별자치도',
    cityId: 'hongchon',
    cityName: '홍천군'
  },
  'yangpyong': {
    provinceId: 'gyeonggi',
    provinceName: '경기도',
    cityId: 'yangpyong',
    cityName: '양평군'
  }
};

// 전국 도/특별시/광역시 목록 (서울 제외)
const allProvinces = {
  // 도
  'gangwon': { name: '강원특별자치도', type: 'province' },
  'gyeonggi': { name: '경기도', type: 'province' },
  'chungbuk': { name: '충청북도', type: 'province' },
  'chungnam': { name: '충청남도', type: 'province' },
  'jeonbuk': { name: '전북특별자치도', type: 'province' },
  'jeonnam': { name: '전라남도', type: 'province' },
  'gyeongbuk': { name: '경상북도', type: 'province' },
  'gyeongnam': { name: '경상남도', type: 'province' },
  'jeju': { name: '제주특별자치도', type: 'province' },

  // 특별시/광역시
  'busan': { name: '부산광역시', type: 'metropolitan' },
  'daegu': { name: '대구광역시', type: 'metropolitan' },
  'incheon': { name: '인천광역시', type: 'metropolitan' },
  'gwangju': { name: '광주광역시', type: 'metropolitan' },
  'daejeon': { name: '대전광역시', type: 'metropolitan' },
  'ulsan': { name: '울산광역시', type: 'metropolitan' },
  'sejong': { name: '세종특별자치시', type: 'special' }
};

/**
 * 1단계: provinces 컬렉션 생성
 */
async function createProvinces() {
  console.log('\n=== 1단계: provinces 컬렉션 생성 ===\n');

  const batch = db.batch();
  let count = 0;

  for (const [provinceId, provinceData] of Object.entries(allProvinces)) {
    const provinceRef = db.collection('provinces').doc(provinceId);

    batch.set(provinceRef, {
      name: provinceData.name,
      type: provinceData.type,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`✅ ${provinceId}: ${provinceData.name} (${provinceData.type})`);
    count++;
  }

  await batch.commit();
  console.log(`\n총 ${count}개 도/특별시/광역시 생성 완료\n`);
}

/**
 * 2단계: 기존 regions → provinces/cities로 데이터 복사
 */
async function migrateRegionsData() {
  console.log('\n=== 2단계: regions → provinces/cities 데이터 마이그레이션 ===\n');

  // 기존 regions 조회
  const regionsSnapshot = await db.collection('regions').get();
  console.log(`기존 regions: ${regionsSnapshot.size}개 발견\n`);

  for (const regionDoc of regionsSnapshot.docs) {
    const regionId = regionDoc.id;
    const regionData = regionDoc.data();

    console.log(`\n--- 처리 중: ${regionId} (${regionData.name}) ---`);

    // 매핑 정보 확인
    const mapping = regionMapping[regionId];
    if (!mapping) {
      console.log(`⚠️  매핑 정보 없음 - 건너뛰기`);
      continue;
    }

    const { provinceId, provinceName, cityId, cityName } = mapping;
    console.log(`→ ${provinceName} / ${cityName}`);

    // cities 문서 생성
    const cityRef = db.collection('provinces').doc(provinceId)
      .collection('cities').doc(cityId);

    await cityRef.set({
      name: cityName,
      provinceId: provinceId,
      provinceName: provinceName,
      // 기존 region 메타데이터 복사
      oldRegionId: regionId,
      createdAt: regionData.createdAt || admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`  ✅ cities 문서 생성: provinces/${provinceId}/cities/${cityId}`);

    // offices 서브컬렉션 복사
    await migrateOffices(regionId, provinceId, cityId);

    // designated_drivers 서브컬렉션 복사 (각 office 내부)
    // calls, customers, points 등도 포함됨
  }

  console.log('\n✅ 2단계 완료!\n');
}

/**
 * offices 서브컬렉션 복사
 */
async function migrateOffices(regionId, provinceId, cityId) {
  const officesSnapshot = await db.collection('regions').doc(regionId)
    .collection('offices').get();

  if (officesSnapshot.empty) {
    console.log(`  ℹ️  사무실 없음`);
    return;
  }

  console.log(`  사무실: ${officesSnapshot.size}개`);

  for (const officeDoc of officesSnapshot.docs) {
    const officeId = officeDoc.id;
    const officeData = officeDoc.data();

    // 새 경로에 office 복사
    const newOfficeRef = db.collection('provinces').doc(provinceId)
      .collection('cities').doc(cityId)
      .collection('offices').doc(officeId);

    await newOfficeRef.set({
      ...officeData,
      // 새 필드 추가
      provinceId: provinceId,
      cityId: cityId,
      oldRegionId: regionId,
      migratedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`    ✅ ${officeData.name} (${officeId})`);

    // 각 office의 서브컬렉션들 복사
    await migrateOfficeSubcollections(regionId, officeId, provinceId, cityId, officeId);
  }
}

/**
 * office의 서브컬렉션들 복사 (designated_drivers, customers, calls, etc.)
 */
async function migrateOfficeSubcollections(oldRegionId, oldOfficeId, provinceId, cityId, officeId) {
  const subcollections = [
    'designated_drivers',
    'pickup_drivers',
    'customers',
    'calls',
    'points',
    'point_transactions',
    'settlements',
    'archived_calls',
    'shared_calls'
  ];

  for (const collectionName of subcollections) {
    const snapshot = await db.collection('regions').doc(oldRegionId)
      .collection('offices').doc(oldOfficeId)
      .collection(collectionName).get();

    if (snapshot.empty) continue;

    console.log(`      - ${collectionName}: ${snapshot.size}개 문서`);

    // 배치 처리로 복사
    const batch = db.batch();
    let count = 0;

    for (const doc of snapshot.docs) {
      const newRef = db.collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection(collectionName).doc(doc.id);

      batch.set(newRef, {
        ...doc.data(),
        migratedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      count++;

      // Firestore 배치는 최대 500개
      if (count % 500 === 0) {
        await batch.commit();
        console.log(`        (${count}개 처리 중...)`);
      }
    }

    if (count % 500 !== 0) {
      await batch.commit();
    }
    console.log(`        ✅ ${count}개 복사 완료`);
  }
}

/**
 * 3단계: admins 컬렉션 업데이트
 */
async function updateAdmins() {
  console.log('\n=== 3단계: admins 컬렉션 업데이트 ===\n');

  const adminsSnapshot = await db.collection('admins').get();
  console.log(`총 ${adminsSnapshot.size}명의 관리자 발견\n`);

  for (const adminDoc of adminsSnapshot.docs) {
    const adminData = adminDoc.data();
    const oldRegionId = adminData.associatedRegionId;

    if (!oldRegionId) {
      console.log(`⚠️  ${adminDoc.id}: regionId 없음 - 건너뛰기`);
      continue;
    }

    const mapping = regionMapping[oldRegionId];
    if (!mapping) {
      console.log(`⚠️  ${adminDoc.id}: 매핑 정보 없음 (${oldRegionId}) - 건너뛰기`);
      continue;
    }

    await db.collection('admins').doc(adminDoc.id).update({
      associatedProvinceId: mapping.provinceId,
      associatedCityId: mapping.cityId,
      // 기존 필드는 유지 (하위 호환성)
      oldAssociatedRegionId: oldRegionId,
      migratedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`✅ ${adminData.email}: ${mapping.provinceName} / ${mapping.cityName}`);
  }

  console.log('\n✅ 3단계 완료!\n');
}

/**
 * 4단계: pending_drivers 업데이트
 */
async function updatePendingDrivers() {
  console.log('\n=== 4단계: pending_drivers 업데이트 ===\n');

  const pendingSnapshot = await db.collection('pending_drivers').get();
  console.log(`총 ${pendingSnapshot.size}명의 승인 대기 기사 발견\n`);

  for (const driverDoc of pendingSnapshot.docs) {
    const driverData = driverDoc.data();
    const oldRegionId = driverData.targetRegionId;

    if (!oldRegionId) {
      console.log(`⚠️  ${driverDoc.id}: targetRegionId 없음 - 건너뛰기`);
      continue;
    }

    const mapping = regionMapping[oldRegionId];
    if (!mapping) {
      console.log(`⚠️  ${driverDoc.id}: 매핑 정보 없음 (${oldRegionId}) - 건너뛰기`);
      continue;
    }

    await db.collection('pending_drivers').doc(driverDoc.id).update({
      targetProvinceId: mapping.provinceId,
      targetCityId: mapping.cityId,
      // 기존 필드는 유지
      oldTargetRegionId: oldRegionId,
      migratedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`✅ ${driverData.name}: ${mapping.provinceName} / ${mapping.cityName}`);
  }

  console.log('\n✅ 4단계 완료!\n');
}

/**
 * 5단계: 데이터 검증
 */
async function verifyMigration() {
  console.log('\n=== 5단계: 데이터 검증 ===\n');

  // provinces 확인
  const provincesSnapshot = await db.collection('provinces').get();
  console.log(`✅ provinces: ${provincesSnapshot.size}개`);

  // cities 확인
  for (const [regionId, mapping] of Object.entries(regionMapping)) {
    const cityDoc = await db.collection('provinces').doc(mapping.provinceId)
      .collection('cities').doc(mapping.cityId).get();

    if (cityDoc.exists) {
      const officesSnapshot = await db.collection('provinces').doc(mapping.provinceId)
        .collection('cities').doc(mapping.cityId)
        .collection('offices').get();

      console.log(`✅ ${mapping.provinceName}/${mapping.cityName}: ${officesSnapshot.size}개 사무실`);
    } else {
      console.log(`❌ ${mapping.provinceName}/${mapping.cityName}: 생성 실패!`);
    }
  }

  // admins 확인
  const adminsWithNew = await db.collection('admins')
    .where('associatedProvinceId', '!=', null).get();
  console.log(`✅ 업데이트된 관리자: ${adminsWithNew.size}명`);

  // pending_drivers 확인
  const driversWithNew = await db.collection('pending_drivers')
    .where('targetProvinceId', '!=', null).get();
  console.log(`✅ 업데이트된 승인 대기 기사: ${driversWithNew.size}명`);

  console.log('\n✅ 5단계 완료!\n');
}

/**
 * 메인 실행 함수
 */
async function main() {
  console.log('╔═══════════════════════════════════════════════════════════╗');
  console.log('║   Firestore 구조 변경: regions → provinces/cities       ║');
  console.log('╚═══════════════════════════════════════════════════════════╝');
  console.log('\n⚠️  주의: 이 작업은 되돌릴 수 없습니다!');
  console.log('⚠️  실행 전 Firestore 백업을 확인하세요.\n');

  try {
    // 1단계: provinces 생성
    await createProvinces();

    // 2단계: regions 데이터 복사
    await migrateRegionsData();

    // 3단계: admins 업데이트
    await updateAdmins();

    // 4단계: pending_drivers 업데이트
    await updatePendingDrivers();

    // 5단계: 검증
    await verifyMigration();

    console.log('\n╔═══════════════════════════════════════════════════════════╗');
    console.log('║              ✅ 마이그레이션 완료!                         ║');
    console.log('╚═══════════════════════════════════════════════════════════╝\n');

    console.log('다음 단계:');
    console.log('1. Firebase Console에서 데이터 확인');
    console.log('2. 앱 코드 경로 수정 (손님앱, 콜매니저, 기사앱)');
    console.log('3. Cloud Functions 경로 수정');
    console.log('4. Firestore Rules 수정');
    console.log('5. 전체 테스트 후 기존 regions 컬렉션 삭제\n');

  } catch (error) {
    console.error('\n❌ 오류 발생:', error);
    console.error('\n마이그레이션 실패. 로그를 확인하고 문제를 해결하세요.');
    process.exit(1);
  }

  process.exit(0);
}

// 실행
main();
