/**
 * HEAD_MANAGER admin 문서 직접 생성
 */

const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function createHeadManager() {
  const uid = 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2';
  const email = 'onekyou71@gmail.com';

  console.log(`\n🔧 HEAD_MANAGER admin 문서 생성 중...\n`);

  try {
    // admin 문서 생성
    const adminData = {
      email: email,
      name: '총관리자',
      phoneNumber: '010-0000-0000', // 필요시 수정
      role: 'HEAD_MANAGER',
      associatedRegionId: null, // HEAD_MANAGER는 모든 지역 관리
      associatedOfficeId: null, // HEAD_MANAGER는 모든 사무실 관리
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    };

    await db.collection('admins').doc(uid).set(adminData);

    console.log(`✅ admin 문서 생성 완료!\n`);
    console.log(`📋 생성된 정보:`);
    console.log(`   UID: ${uid}`);
    console.log(`   Email: ${email}`);
    console.log(`   이름: ${adminData.name}`);
    console.log(`   역할: ${adminData.role}`);
    console.log(`   전화번호: ${adminData.phoneNumber}`);
    console.log(`   지역: ${adminData.associatedRegionId || '전체'}`);
    console.log(`   사무실: ${adminData.associatedOfficeId || '전체'}\n`);

    console.log(`🎉 완료! 이제 다음 기능을 사용할 수 있습니다:`);
    console.log(`   ✅ 환전 신청 조회/승인/거부/완료`);
    console.log(`   ✅ 디바이스 모니터링 알림 조회`);
    console.log(`   ✅ Lock-in 관리`);
    console.log(`   ✅ 모든 지역/사무실 데이터 조회\n`);

    console.log(`💡 브라우저에서 페이지를 새로고침(F5)하면 바로 사용 가능합니다!\n`);

    process.exit(0);

  } catch (error) {
    console.error(`\n❌ 오류 발생:`, error.message);
    process.exit(1);
  }
}

createHeadManager();
