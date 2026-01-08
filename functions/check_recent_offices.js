const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function checkRecentOffices() {
  try {
    console.log('=== 최근 생성된 사무실 확인 ===\n');

    // 모든 지역의 사무실을 가져와서 생성일 기준으로 정렬
    const regionsSnapshot = await db.collection('regions').get();
    const allOffices = [];

    for (const regionDoc of regionsSnapshot.docs) {
      const regionId = regionDoc.id;
      const regionName = regionDoc.data().name;

      const officesSnapshot = await db
        .collection('regions')
        .doc(regionId)
        .collection('offices')
        .get();

      for (const officeDoc of officesSnapshot.docs) {
        const data = officeDoc.data();
        allOffices.push({
          regionId,
          regionName,
          officeId: officeDoc.id,
          data,
          ref: officeDoc.ref
        });
      }
    }

    // 생성일 기준으로 정렬 (최근 것이 먼저)
    allOffices.sort((a, b) => {
      const timeA = a.data.createdAt?.toMillis() || 0;
      const timeB = b.data.createdAt?.toMillis() || 0;
      return timeB - timeA;
    });

    // 최근 5개만 출력
    for (let i = 0; i < Math.min(5, allOffices.length); i++) {
      const office = allOffices[i];

      console.log(`${i + 1}. 사무실: ${office.data.name || office.officeId}`);
      console.log(`   ID: ${office.officeId}`);
      console.log(`   지역: ${office.regionName} (${office.regionId})`);
      console.log(`   생성일: ${office.data.createdAt?.toDate() || 'N/A'}`);
      console.log(`   전화: ${office.data.phone || 'N/A'}`);
      console.log(`   은행: ${office.data.bankName || 'N/A'}`);
      console.log(`   계좌: ${office.data.accountNumber || 'N/A'}`);

      // QR 설정 확인
      const attrDoc = await office.ref.collection('settings').doc('attribution').get();
      if (attrDoc.exists) {
        const attrData = attrDoc.data();
        console.log(`   QR URL: ${attrData.qrCode || 'N/A'}`);

        // URL 형식 분석
        const qrUrl = attrData.qrCode || '';
        if (qrUrl.includes('?r=')) {
          console.log(`   형식: ✅ 새 방식 (Query Parameter)`);
        } else if (qrUrl.includes('/')) {
          console.log(`   형식: ❌ 옛날 방식 (Path Parameter)`);
        }
      } else {
        console.log(`   QR URL: 없음`);
      }
      console.log('');
    }

  } catch (error) {
    console.error('에러:', error);
  } finally {
    process.exit();
  }
}

checkRecentOffices();
