/**
 * 테스트용 emergency alerts 생성 스크립트
 */

const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function createTestAlerts() {
  console.log('\n🧪 테스트용 emergency alerts 생성 중...\n');

  const alerts = [
    // 1. CRITICAL - 앱 크래시 (미확인)
    {
      deviceId: 'DEVICE_001',
      type: 'EMERGENCY_CRASH_ALERT',
      priority: 'CRITICAL',
      regionId: 'Hongchon',
      officeId: 'qwfdeSOL8Vz4lXEEP4TD',
      officeName: '홍천 사무실',
      message: '콜 디텍터 앱이 강제종료되었습니다. 즉시 확인이 필요합니다.',
      crashTime: Date.now() - 1000 * 60 * 5, // 5분 전
      timestamp: new Date(Date.now() - 1000 * 60 * 5),
      requiresImmediateAction: true,
      acknowledged: false,
      resolved: false
    },

    // 2. CRITICAL - 앱 크래시 (확인됨, 미해결)
    {
      deviceId: 'DEVICE_002',
      type: 'EMERGENCY_CRASH_ALERT',
      priority: 'CRITICAL',
      regionId: 'Hongchon',
      officeId: 'qwfdeSOL8Vz4lXEEP4TD',
      officeName: '홍천 사무실',
      message: '콜 디텍터 앱이 비정상 종료되었습니다.',
      crashTime: Date.now() - 1000 * 60 * 15, // 15분 전
      timestamp: new Date(Date.now() - 1000 * 60 * 15),
      requiresImmediateAction: true,
      acknowledged: true,
      acknowledgedBy: 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2',
      acknowledgedAt: new Date(Date.now() - 1000 * 60 * 10),
      resolved: false
    },

    // 3. HIGH - 디바이스 오프라인
    {
      deviceId: 'DEVICE_003',
      type: 'DEVICE_OFFLINE',
      priority: 'HIGH',
      regionId: 'Hongchon',
      officeId: 'qwfdeSOL8Vz4lXEEP4TD',
      officeName: '홍천 사무실',
      message: '디바이스가 10분 이상 오프라인 상태입니다.',
      timestamp: new Date(Date.now() - 1000 * 60 * 30), // 30분 전
      requiresImmediateAction: false,
      acknowledged: false,
      resolved: false
    },

    // 4. MEDIUM - 권한 거부
    {
      deviceId: 'DEVICE_004',
      type: 'PERMISSION_DENIED',
      priority: 'MEDIUM',
      regionId: 'Hongchon',
      officeId: 'qwfdeSOL8Vz4lXEEP4TD',
      officeName: '홍천 사무실',
      message: '전화 상태 읽기 권한이 거부되었습니다.',
      timestamp: new Date(Date.now() - 1000 * 60 * 60), // 1시간 전
      requiresImmediateAction: false,
      acknowledged: true,
      acknowledgedBy: 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2',
      acknowledgedAt: new Date(Date.now() - 1000 * 60 * 45),
      resolved: false
    },

    // 5. LOW - 기타 (확인됨, 미해결)
    {
      deviceId: 'DEVICE_005',
      type: 'OTHER',
      priority: 'LOW',
      regionId: 'Hongchon',
      officeId: 'qwfdeSOL8Vz4lXEEP4TD',
      officeName: '홍천 사무실',
      message: '배터리 최적화 설정 확인이 필요합니다.',
      timestamp: new Date(Date.now() - 1000 * 60 * 120), // 2시간 전
      requiresImmediateAction: false,
      acknowledged: true,
      acknowledgedBy: 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2',
      acknowledgedAt: new Date(Date.now() - 1000 * 60 * 100),
      resolved: false
    },

    // 6. CRITICAL - 앱 크래시 (해결됨)
    {
      deviceId: 'DEVICE_006',
      type: 'EMERGENCY_CRASH_ALERT',
      priority: 'CRITICAL',
      regionId: 'Hongchon',
      officeId: 'qwfdeSOL8Vz4lXEEP4TD',
      officeName: '홍천 사무실',
      message: '콜 디텍터 앱 크래시 발생 (해결 완료)',
      crashTime: Date.now() - 1000 * 60 * 180, // 3시간 전
      timestamp: new Date(Date.now() - 1000 * 60 * 180),
      requiresImmediateAction: true,
      acknowledged: true,
      acknowledgedBy: 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2',
      acknowledgedAt: new Date(Date.now() - 1000 * 60 * 170),
      resolved: true,
      resolvedBy: 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2',
      resolvedAt: new Date(Date.now() - 1000 * 60 * 150),
      resolveNote: '앱 재시작 후 정상 작동 확인'
    },

    // 7. HIGH - 디바이스 오프라인 (해결됨)
    {
      deviceId: 'DEVICE_007',
      type: 'DEVICE_OFFLINE',
      priority: 'HIGH',
      regionId: 'Hongchon',
      officeId: 'qwfdeSOL8Vz4lXEEP4TD',
      officeName: '홍천 사무실',
      message: '디바이스 네트워크 연결 문제',
      timestamp: new Date(Date.now() - 1000 * 60 * 240), // 4시간 전
      requiresImmediateAction: false,
      acknowledged: true,
      acknowledgedBy: 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2',
      acknowledgedAt: new Date(Date.now() - 1000 * 60 * 230),
      resolved: true,
      resolvedBy: 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2',
      resolvedAt: new Date(Date.now() - 1000 * 60 * 200),
      resolveNote: '네트워크 재연결 완료'
    }
  ];

  try {
    const batch = db.batch();

    alerts.forEach((alert) => {
      const docRef = db.collection('emergency_alerts').doc();
      batch.set(docRef, alert);
      console.log(`✅ Alert 생성: ${alert.type} - ${alert.priority} - ${alert.message}`);
    });

    await batch.commit();

    console.log(`\n🎉 총 ${alerts.length}개의 테스트 알림이 생성되었습니다!\n`);
    console.log(`📊 통계:`);
    console.log(`   - CRITICAL: ${alerts.filter(a => a.priority === 'CRITICAL').length}개`);
    console.log(`   - HIGH: ${alerts.filter(a => a.priority === 'HIGH').length}개`);
    console.log(`   - MEDIUM: ${alerts.filter(a => a.priority === 'MEDIUM').length}개`);
    console.log(`   - LOW: ${alerts.filter(a => a.priority === 'LOW').length}개`);
    console.log(`\n   - 미확인: ${alerts.filter(a => !a.acknowledged).length}개`);
    console.log(`   - 확인됨: ${alerts.filter(a => a.acknowledged && !a.resolved).length}개`);
    console.log(`   - 해결됨: ${alerts.filter(a => a.resolved).length}개\n`);

    console.log(`💡 브라우저에서 디바이스 모니터링 페이지를 새로고침하여 확인하세요!\n`);

    process.exit(0);

  } catch (error) {
    console.error('\n❌ 오류 발생:', error.message);
    console.error(error);
    process.exit(1);
  }
}

createTestAlerts();
