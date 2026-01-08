const admin = require('firebase-admin');

// Firebase Admin 초기화
const serviceAccount = require('./functions/service-account-key.json');

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

    // offices 컬렉션에 테스트 사무실 추가
    await db.collection('offices').doc('TEST_OFFICE').set(testOffice);
    console.log('✅ 테스트 사무실 데이터 추가 완료');

    // 테스트 지역 데이터
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

    // 테스트 사무실의 office_configs 서브컬렉션 추가
    const officeConfig = {
      fingerprint: {
        scoreWeights: {
          deviceId: 30,
          userAgent: 20,
          screenResolution: 15,
          timezone: 10,
          language: 10,
          platform: 10,
          cookieEnabled: 5
        },
        thresholds: {
          highConfidence: 80,
          mediumConfidence: 60,
          lowConfidence: 40
        }
      },
      attribution: {
        timeWindow: 3600000, // 1시간
        maxRetries: 3,
        autoAssignThreshold: 70
      },
      points: {
        earnRate: 0.01, // 요금의 1%
        bonusEvents: {
          firstRide: 1000,
          weeklyBonus: 500,
          monthlyBonus: 2000
        }
      }
    };

    await db.collection('offices').doc('TEST_OFFICE')
      .collection('office_configs').doc('default').set(officeConfig);
    console.log('✅ 테스트 사무실 설정 추가 완료');

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