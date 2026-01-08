const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkManagerTokens() {
  try {
    console.log('=== managerTokens 컬렉션 확인 ===\n');

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

    if (tokensSnapshot.empty) {
      console.log('⚠️ managerTokens 컬렉션이 비어있습니다!');
      console.log('콜매니저 앱에서 FCM 토큰이 저장되지 않았습니다.\n');

      // admins 컬렉션도 확인
      console.log('=== admins 컬렉션 확인 ===\n');
      const adminsSnapshot = await db.collection('admins').get();
      console.log(`admins 문서 수: ${adminsSnapshot.size}\n`);

      adminsSnapshot.forEach(doc => {
        const data = doc.data();
        console.log(`Admin ID: ${doc.id}`);
        console.log(`FCM Token: ${data.fcmToken ? data.fcmToken.substring(0, 50) + '...' : 'null'}`);
        console.log(`Associated Region: ${data.associatedRegionId || 'null'}`);
        console.log(`Associated Office: ${data.associatedOfficeId || 'null'}`);
        console.log(`Last Updated: ${data.lastUpdated ? new Date(data.lastUpdated).toLocaleString() : 'null'}`);
        console.log('---\n');
      });
    } else {
      tokensSnapshot.forEach(doc => {
        const data = doc.data();
        console.log(`Manager ID: ${doc.id}`);
        console.log(`FCM Token: ${data.fcmToken ? data.fcmToken.substring(0, 50) + '...' : 'null'}`);
        console.log(`Updated At: ${data.updatedAt ? data.updatedAt.toDate().toLocaleString() : 'null'}`);
        console.log('---\n');
      });
    }

  } catch (error) {
    console.error('오류 발생:', error);
  }

  process.exit(0);
}

checkManagerTokens();
