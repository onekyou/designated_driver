/**
 * find-active-drivers.js — 모든 사무실에서 fcmToken 보유 + active 기사 query.
 */

const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp({ projectId: 'calldetector-5d61e' });
}

const db = admin.firestore();

(async () => {
  const snap = await db.collectionGroup('designated_drivers').get();
  console.log(`총 ${snap.size}개 designated_drivers 문서`);

  const active = [];
  snap.docs.forEach((d) => {
    const data = d.data() || {};
    if (data.fcmToken && data.authUid) {
      // path 분해: provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}
      const parts = d.ref.path.split('/');
      active.push({
        provinceId: parts[1],
        cityId: parts[3],
        officeId: parts[5],
        docId: d.id,
        name: data.name,
        status: data.status,
        authUid: data.authUid,
        fcmPrefix: data.fcmToken.substring(0, 25),
      });
    }
  });

  console.log(`\nfcmToken + authUid 보유 active 기사 ${active.length}명:`);
  active.forEach((a) => {
    console.log(`  ${a.provinceId}/${a.cityId}/${a.officeId.substring(0, 8)}... ${a.name} status=${a.status} authUid=${a.authUid.substring(0, 12)}... fcm=${a.fcmPrefix}...`);
  });

  process.exit(0);
})().catch((e) => { console.error(e); process.exit(3); });
