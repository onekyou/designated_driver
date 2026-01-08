/**
 * Firebase regions 컬렉션에 province 필드 추가
 * 전국 서비스 확장을 위한 도/특별시/광역시 정보 추가
 */

const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// 기존 지역에 대한 province 매핑
const regionProvinceMap = {
  'Hongchon': {
    province: '강원특별자치도',
    provinceCode: 'gangwon'
  },
  'yangpyong': {
    province: '경기도',
    provinceCode: 'gyeonggi'
  }
};

// 전국 도/특별시/광역시 목록 (서울 제외)
const allProvinces = {
  // 특별시/광역시
  'busan': '부산광역시',
  'daegu': '대구광역시',
  'incheon': '인천광역시',
  'gwangju': '광주광역시',
  'daejeon': '대전광역시',
  'ulsan': '울산광역시',
  'sejong': '세종특별자치시',

  // 도
  'gyeonggi': '경기도',
  'gangwon': '강원특별자치도',
  'chungbuk': '충청북도',
  'chungnam': '충청남도',
  'jeonbuk': '전북특별자치도',
  'jeonnam': '전라남도',
  'gyeongbuk': '경상북도',
  'gyeongnam': '경상남도',
  'jeju': '제주특별자치도'
};

async function addProvinceToRegions() {
  console.log('=== Firebase regions에 province 필드 추가 시작 ===\n');

  try {
    // 1. 기존 regions 가져오기
    const regionsSnapshot = await db.collection('regions').get();
    console.log(`총 ${regionsSnapshot.size}개 지역 발견\n`);

    // 2. 각 region에 province 정보 추가
    for (const doc of regionsSnapshot.docs) {
      const regionId = doc.id;
      const regionData = doc.data();

      console.log(`처리 중: ${regionId} (${regionData.name})`);

      if (regionProvinceMap[regionId]) {
        const { province, provinceCode } = regionProvinceMap[regionId];

        await db.collection('regions').doc(regionId).update({
          province: province,
          provinceCode: provinceCode,
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        console.log(`  ✅ 업데이트 완료: ${province} (${provinceCode})`);
      } else {
        console.log(`  ⚠️  매핑 정보 없음 - 수동으로 추가 필요`);
      }
      console.log('');
    }

    // 3. 업데이트된 결과 확인
    console.log('\n=== 업데이트 결과 확인 ===\n');
    const updatedSnapshot = await db.collection('regions').get();

    for (const doc of updatedSnapshot.docs) {
      const data = doc.data();
      console.log(`${doc.id}:`);
      console.log(`  - name: ${data.name}`);
      console.log(`  - province: ${data.province || '(없음)'}`);
      console.log(`  - provinceCode: ${data.provinceCode || '(없음)'}`);
      console.log('');
    }

    // 4. 전국 도/특별시/광역시 목록 출력
    console.log('\n=== 전국 서비스 확장 시 사용 가능한 도/특별시/광역시 ===\n');
    Object.entries(allProvinces).forEach(([code, name]) => {
      console.log(`${code}: ${name}`);
    });

    console.log('\n✅ 작업 완료!');

  } catch (error) {
    console.error('❌ 오류 발생:', error);
    process.exit(1);
  }

  process.exit(0);
}

addProvinceToRegions();
