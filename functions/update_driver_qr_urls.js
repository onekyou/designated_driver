const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

(async () => {
  try {
    console.log('모든 기사의 QR URL을 새로운 형식으로 업데이트합니다...\n');

    const regionsSnapshot = await db.collection('regions').get();
    let totalUpdated = 0;

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      const officesSnapshot = await db.collection('regions').doc(regionId)
        .collection('offices').get();

      for (const officeDoc of officesSnapshot.docs) {
        const officeId = officeDoc.id;

        // designated_drivers 확인
        const driversSnapshot = await db.collection('regions').doc(regionId)
          .collection('offices').doc(officeId)
          .collection('designated_drivers')
          .get();

        for (const driverDoc of driversSnapshot.docs) {
          const driverId = driverDoc.id;
          const driverData = driverDoc.data();
          const driverName = driverData.name || '';

          // 새로운 QR URL 생성 (메인 페이지로)
          const encodedName = encodeURIComponent(driverName);
          const newQrUrl = `https://calldetector-5d61e.web.app/?r=${regionId}&o=${officeId}&d=${driverId}&dn=${encodedName}`;

          // 기존 URL과 비교
          const oldQrUrl = driverData.referralQrUrl || '없음';
          const needsUpdate = !oldQrUrl.startsWith('https://calldetector-5d61e.web.app/?r=');

          if (needsUpdate) {
            console.log(`업데이트: ${driverName} (${driverId})`);
            console.log(`  이전: ${oldQrUrl}`);
            console.log(`  이후: ${newQrUrl}`);

            await driverDoc.ref.update({
              referralQrUrl: newQrUrl
            });

            totalUpdated++;
            console.log('  ✓ 업데이트 완료\n');
          }
        }

        // pickup_drivers도 확인
        const pickupDriversSnapshot = await db.collection('regions').doc(regionId)
          .collection('offices').doc(officeId)
          .collection('pickup_drivers')
          .get();

        for (const driverDoc of pickupDriversSnapshot.docs) {
          const driverId = driverDoc.id;
          const driverData = driverDoc.data();
          const driverName = driverData.name || '';

          const encodedName = encodeURIComponent(driverName);
          const newQrUrl = `https://calldetector-5d61e.web.app/?r=${regionId}&o=${officeId}&d=${driverId}&dn=${encodedName}`;

          const oldQrUrl = driverData.referralQrUrl || '없음';
          const needsUpdate = !oldQrUrl.startsWith('https://calldetector-5d61e.web.app/?r=');

          if (needsUpdate) {
            console.log(`업데이트: ${driverName} (${driverId}) [픽업기사]`);
            console.log(`  이전: ${oldQrUrl}`);
            console.log(`  이후: ${newQrUrl}`);

            await driverDoc.ref.update({
              referralQrUrl: newQrUrl
            });

            totalUpdated++;
            console.log('  ✓ 업데이트 완료\n');
          }
        }
      }
    }

    console.log(`\n총 ${totalUpdated}명의 기사 QR URL 업데이트 완료`);
    process.exit(0);
  } catch (error) {
    console.error('에러:', error);
    process.exit(1);
  }
})();
