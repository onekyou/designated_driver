const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

/**
 * 기사 데이터의 referralQrUrl 확인
 */
async function checkDriverQrUrls() {
  try {
    console.log('=== 기사 QR URL 확인 ===\n');

    const regionId = 'Hongchon';
    const officeId = 'qwfdeSOL8Vz4lXEEP4TD';

    const driversSnapshot = await db
      .collection('regions').doc(regionId)
      .collection('offices').doc(officeId)
      .collection('designated_drivers')
      .get();

    console.log(`Region: ${regionId}`);
    console.log(`Office: ${officeId}`);
    console.log(`기사 수: ${driversSnapshot.size}\n`);

    if (driversSnapshot.empty) {
      console.log('⚠️ 기사 데이터가 없습니다.');
    } else {
      driversSnapshot.forEach(doc => {
        const data = doc.data();
        const driverId = doc.id;
        const driverName = data.name || '이름없음';
        const referralQrUrl = data.referralQrUrl;

        console.log(`기사 ID: ${driverId}`);
        console.log(`이름: ${driverName}`);
        console.log(`QR URL: ${referralQrUrl || '없음'}`);

        // URL 파라미터 분석
        if (referralQrUrl) {
          const url = new URL(referralQrUrl);
          const params = {};
          url.searchParams.forEach((value, key) => {
            params[key] = value;
          });
          console.log(`URL 파라미터:`, params);

          // 기사 정보 파라미터 확인
          const hasDriverId = url.searchParams.has('d');
          const hasDriverName = url.searchParams.has('dn');

          if (!hasDriverId || !hasDriverName) {
            console.log(`❌ 문제 발견: URL에 기사 정보가 없습니다!`);
            console.log(`   - d (driverId): ${hasDriverId ? '있음' : '없음'}`);
            console.log(`   - dn (driverName): ${hasDriverName ? '있음' : '없음'}`);
          } else {
            console.log(`✅ 기사 정보 포함됨`);
            console.log(`   - d (driverId): ${url.searchParams.get('d')}`);
            console.log(`   - dn (driverName): ${url.searchParams.get('dn')}`);
          }
        } else {
          console.log(`❌ referralQrUrl이 없습니다!`);
        }

        console.log('---\n');
      });
    }

  } catch (error) {
    console.error('오류 발생:', error);
  }

  process.exit(0);
}

checkDriverQrUrls();
