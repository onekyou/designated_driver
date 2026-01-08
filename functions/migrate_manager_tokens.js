const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

/**
 * admins 컬렉션의 FCM 토큰을 managerTokens 컬렉션으로 마이그레이션
 *
 * 문제: onNewCustomerRegistered 함수가 managerTokens 컬렉션에서 토큰을 조회하는데,
 *       콜매니저 앱이 admins 컬렉션에만 토큰을 저장하고 managerTokens에는 저장 안함
 *
 * 해결: admins 컬렉션의 토큰을 managerTokens로 복사
 */
async function migrateManagerTokens() {
  try {
    console.log('=== FCM 토큰 마이그레이션 시작 ===\n');

    // admins 컬렉션에서 모든 관리자 가져오기
    const adminsSnapshot = await db.collection('admins').get();

    console.log(`총 ${adminsSnapshot.size}개의 관리자 계정 발견\n`);

    let successCount = 0;
    let skipCount = 0;
    let errorCount = 0;

    for (const adminDoc of adminsSnapshot.docs) {
      const adminId = adminDoc.id;
      const adminData = adminDoc.data();

      const fcmToken = adminData.fcmToken;
      const regionId = adminData.associatedRegionId;
      const officeId = adminData.associatedOfficeId;

      console.log(`\n처리 중: Admin ID = ${adminId}`);
      console.log(`  - Region: ${regionId || 'null'}`);
      console.log(`  - Office: ${officeId || 'null'}`);
      console.log(`  - FCM Token: ${fcmToken ? fcmToken.substring(0, 30) + '...' : 'null'}`);

      // 필수 정보가 없으면 스킵
      if (!fcmToken || !regionId || !officeId) {
        console.log('  ⚠️ 스킵: FCM 토큰 또는 사무실 정보 없음');
        skipCount++;
        continue;
      }

      try {
        // managerTokens 컬렉션에 저장
        const managerTokenData = {
          fcmToken: fcmToken,
          updatedAt: admin.firestore.Timestamp.now(),
          migratedAt: admin.firestore.Timestamp.now(),
          migratedFrom: 'admins_collection'
        };

        await db.collection('regions').doc(regionId)
          .collection('offices').doc(officeId)
          .collection('managerTokens').doc(adminId)
          .set(managerTokenData, { merge: true });

        console.log('  ✅ 성공: managerTokens에 저장 완료');
        successCount++;

      } catch (error) {
        console.error('  ❌ 오류:', error.message);
        errorCount++;
      }
    }

    console.log('\n\n=== 마이그레이션 완료 ===');
    console.log(`성공: ${successCount}개`);
    console.log(`스킵: ${skipCount}개`);
    console.log(`오류: ${errorCount}개`);
    console.log(`총: ${adminsSnapshot.size}개`);

    // 마이그레이션 후 확인
    console.log('\n\n=== 마이그레이션 결과 확인 ===\n');

    const regionId = 'Hongchon';
    const officeId = 'qwfdeSOL8Vz4lXEEP4TD';

    const tokensSnapshot = await db
      .collection('regions').doc(regionId)
      .collection('offices').doc(officeId)
      .collection('managerTokens')
      .get();

    console.log(`Region: ${regionId}`);
    console.log(`Office: ${officeId}`);
    console.log(`managerTokens 문서 수: ${tokensSnapshot.size}\n`);

    tokensSnapshot.forEach(doc => {
      const data = doc.data();
      console.log(`Manager ID: ${doc.id}`);
      console.log(`FCM Token: ${data.fcmToken ? data.fcmToken.substring(0, 50) + '...' : 'null'}`);
      console.log(`Updated At: ${data.updatedAt ? data.updatedAt.toDate().toLocaleString() : 'null'}`);
      console.log('---\n');
    });

  } catch (error) {
    console.error('오류 발생:', error);
  }

  process.exit(0);
}

migrateManagerTokens();
