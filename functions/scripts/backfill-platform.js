/**
 * 1회성 백필 스크립트 — 기존 기사 문서에 platform: "android" + fcmTokenPlatform: "android" 추가
 *
 * 실행 시점: Kotlin 기사앱이 platform 필드를 저장하는 코드로 업데이트 배포된 직후
 * (Phase 6 ② 작업 완료 후)
 *
 * 주의: 이 스크립트는 현재 실행하지 않음. Phase 6 ② 단계에서 실행.
 *
 * 실행 방법:
 *   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
 *   node functions/scripts/backfill-platform.js
 */

const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.applicationDefault() });
const db = admin.firestore();

async function backfill() {
  const snap = await db.collectionGroup("designated_drivers").get();
  let batch = db.batch();
  let count = 0;

  for (const doc of snap.docs) {
    const data = doc.data();
    if (!data.platform) {
      batch.update(doc.ref, { platform: "android", fcmTokenPlatform: "android" });
      count++;
      if (count % 400 === 0) {
        // Firestore 배치 한도 500 (여유 두고 400)
        await batch.commit();
        batch = db.batch();
        console.log(`[Backfill] Committed ${count} updates so far...`);
      }
    }
  }

  if (count > 0 && count % 400 !== 0) {
    await batch.commit();
  }
  console.log(`[Backfill] Complete: ${count} drivers updated`);
}

backfill()
  .then(() => process.exit(0))
  .catch(err => {
    console.error("[Backfill] Failed:", err);
    process.exit(1);
  });
