/**
 * admins 컬렉션 전체 조회 및 특정 UID 확인
 */

const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkAdminUser() {
  const targetUID = 'vK9X9OJ9dZWgUvlvwT48KTG3ljS2';
  const targetEmail = 'onekyou71@gmail.com';

  console.log(`\n🔍 사용자 정보 확인 중...\n`);
  console.log(`   대상 UID: ${targetUID}`);
  console.log(`   대상 Email: ${targetEmail}\n`);

  try {
    // 1. admins 컬렉션 전체 조회
    console.log(`📋 admins 컬렉션 전체 조회:\n`);
    const adminsSnapshot = await db.collection('admins').get();

    if (adminsSnapshot.empty) {
      console.log(`❌ admins 컬렉션이 비어있습니다!\n`);
    } else {
      console.log(`✅ admins 컬렉션에 ${adminsSnapshot.size}개 문서가 있습니다.\n`);

      adminsSnapshot.forEach((doc) => {
        const data = doc.data();
        console.log(`문서 ID: ${doc.id}`);
        console.log(`  - 이메일: ${data.email || '없음'}`);
        console.log(`  - 이름: ${data.name || '없음'}`);
        console.log(`  - 역할: ${data.role || '없음'}`);
        console.log(`  - 지역: ${data.associatedRegionId || '없음'}`);
        console.log(`  - 사무실: ${data.associatedOfficeId || '없음'}`);
        console.log('');
      });
    }

    // 2. 특정 UID로 문서 조회
    console.log(`\n🔍 특정 UID로 직접 조회:\n`);
    const specificDoc = await db.collection('admins').doc(targetUID).get();

    if (specificDoc.exists) {
      console.log(`✅ 문서 발견!`);
      console.log(JSON.stringify(specificDoc.data(), null, 2));
    } else {
      console.log(`❌ 해당 UID의 문서가 없습니다.`);
    }

    // 3. Firebase Auth 사용자 정보 확인
    console.log(`\n🔍 Firebase Auth 사용자 정보:\n`);
    const userRecord = await admin.auth().getUser(targetUID);
    console.log(`✅ Auth 사용자 발견:`);
    console.log(`  - UID: ${userRecord.uid}`);
    console.log(`  - Email: ${userRecord.email}`);
    console.log(`  - Email 인증: ${userRecord.emailVerified}`);
    console.log(`  - 생성일: ${userRecord.metadata.creationTime}`);
    console.log(`  - 마지막 로그인: ${userRecord.metadata.lastSignInTime}`);

    process.exit(0);

  } catch (error) {
    console.error(`\n❌ 오류 발생:`, error.message);
    process.exit(1);
  }
}

checkAdminUser();
