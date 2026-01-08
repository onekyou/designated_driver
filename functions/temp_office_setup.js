const admin = require('firebase-admin');

// Firebase Admin 초기화
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: 'calldetector-5d61e'
});

const db = admin.firestore();

async function createTestOfficeData() {
  try {
    console.log('임시 테스트 사무실 데이터 생성 시작...');

    // 테스트 사무실 데이터
    const testOffice = {
      officeId: 'TEST_OFFICE',
      name: '테스트 대리운전',
      phoneNumber: '010-1234-5678',
      address: '서울시 강남구 테스트로 123',
      businessLicense: 'TEST-123-45-67890',
      managerName: '김테스트',
      region: 'seoul',
      status: 'active',
      settings: {
        allowSharedCalls: true,
        pointRate: {
          bronze: 0.03,
          silver: 0.05,
          gold: 0.07,
          vip: 0.09
        },
        gradeThresholds: {
          silver: 50000,
          gold: 100000,
          vip: 200000
        }
      },
      createdAt: admin.firestore.Timestamp.now(),
      updatedAt: admin.firestore.Timestamp.now()
    };

    // 테스트 지역 데이터 먼저 생성
    const testRegion = {
      regionId: 'seoul',
      name: '서울특별시',
      isActive: true,
      offices: ['TEST_OFFICE'],
      createdAt: admin.firestore.Timestamp.now()
    };

    // regions 컬렉션에 테스트 지역 추가
    await db.collection('regions').doc('seoul').set(testRegion);
    console.log('✅ 테스트 지역 데이터 추가 완료');

    // regions/seoul/offices/TEST_OFFICE 구조로 사무실 추가
    await db.collection('regions').doc('seoul')
      .collection('offices').doc('TEST_OFFICE').set(testOffice);
    console.log('✅ 테스트 사무실 데이터 추가 완료 (올바른 경로)');

    // 사무실 설정 추가 (콜매니저 계획서에 맞춤)
    const officeSettings = {
      officeId: 'TEST_OFFICE',
      qrCode: '',
      inviteCode: 'TEST_INVITE_2024',
      landingPageUrl: 'https://nondecorated-annabel-oversentimentally.ngrok-free.dev/TEST_OFFICE',
      pointPolicy: {
        bronzeRate: 3,
        silverRate: 5,
        goldRate: 7,
        vipRate: 9,
        bronzeThreshold: 0,
        silverThreshold: 6,
        goldThreshold: 21,
        vipThreshold: 51,
        minimumUsagePoints: 1000,
        pointsExpireMonths: 12
      },
      customerAppEnabled: true,
      autoPointsEnabled: true,
      attributionThreshold: 70,
      kpiThreshold: 0.9
    };

    await db.collection('regions').doc('seoul')
      .collection('offices').doc('TEST_OFFICE')
      .collection('settings').doc('default').set(officeSettings);
    console.log('✅ 테스트 사무실 설정 추가 완료 (콜매니저 계획서 구조)');

    console.log('\n🎉 모든 테스트 데이터 생성 완료!');
    console.log('테스트용 사무실 ID: TEST_OFFICE');
    console.log('테스트용 지역 ID: seoul');

  } catch (error) {
    console.error('❌ 테스트 데이터 생성 실패:', error);
  } finally {
    process.exit(0);
  }
}

createTestOfficeData();