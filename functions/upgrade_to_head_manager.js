/**
 * 특정 사용자를 HEAD_MANAGER로 업그레이드하는 스크립트
 * 사용법: node upgrade_to_head_manager.js
 */

const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

// Firebase Admin 초기화
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function upgradeToHeadManager() {
  const email = 'onekyou71@gmail.com';

  console.log(`\n🔍 ${email} 사용자를 찾는 중...\n`);

  try {
    // 1. 이메일로 사용자 찾기
    const userRecord = await admin.auth().getUserByEmail(email);
    const uid = userRecord.uid;

    console.log(`✅ 사용자 발견: ${email}`);
    console.log(`   UID: ${uid}\n`);

    // 2. admins 컬렉션에서 문서 확인
    const adminRef = db.collection('admins').doc(uid);
    const adminDoc = await adminRef.get();

    if (!adminDoc.exists) {
      console.log(`❌ admins 컬렉션에 문서가 없습니다.`);
      console.log(`   먼저 회원가입을 완료해주세요.\n`);
      process.exit(1);
    }

    const currentData = adminDoc.data();
    console.log(`📋 현재 사용자 정보:`);
    console.log(`   역할: ${currentData.role || '없음'}`);
    console.log(`   이름: ${currentData.name || '없음'}`);
    console.log(`   지역: ${currentData.associatedRegionId || '없음'}`);
    console.log(`   사무실: ${currentData.associatedOfficeId || '없음'}\n`);

    // 3. role을 HEAD_MANAGER로 업데이트
    await adminRef.update({
      role: 'HEAD_MANAGER',
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`✅ 역할 업데이트 완료!`);
    console.log(`   ${currentData.role || '일반 관리자'} → HEAD_MANAGER\n`);

    // 4. 업데이트 확인
    const updatedDoc = await adminRef.get();
    const updatedData = updatedDoc.data();

    console.log(`📋 업데이트된 사용자 정보:`);
    console.log(`   역할: ${updatedData.role}`);
    console.log(`   업데이트 시간: ${updatedData.updatedAt?.toDate()}\n`);

    console.log(`🎉 완료! 이제 다음 권한을 사용할 수 있습니다:`);
    console.log(`   - 환전 신청 승인/거부/완료 처리`);
    console.log(`   - 디바이스 모니터링 알림 조회`);
    console.log(`   - 모든 지역/사무실 데이터 조회\n`);

    process.exit(0);

  } catch (error) {
    console.error(`\n❌ 오류 발생:`, error.message);

    if (error.code === 'auth/user-not-found') {
      console.log(`\n💡 해당 이메일로 가입된 사용자가 없습니다.`);
      console.log(`   먼저 회원가입을 완료해주세요.\n`);
    }

    process.exit(1);
  }
}

// 스크립트 실행
upgradeToHeadManager();
