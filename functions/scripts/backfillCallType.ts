import * as admin from "firebase-admin";

admin.initializeApp();

async function backfill() {
  const db = admin.firestore();
  const settlements = await db.collectionGroup("settlements").get();
  let processed = 0;
  let updated = 0;

  const batchLimit = 400; // Firestore batch limit 500, keep margin
  let batch = db.batch();

  for (const doc of settlements.docs) {
    processed++;
    const callType = doc.get("callType");
    if (callType === undefined || callType === null || callType === "") {
      batch.update(doc.ref, {
        callType: "NORMAL",
        lastUpdate: admin.firestore.FieldValue.serverTimestamp(),
      });
      updated++;
      // Commit periodically
      if (updated % batchLimit === 0) {
        await batch.commit();
        batch = db.batch();
        console.log(`Committed ${updated} updates so far...`);
      }
    }
  }

  // commit remaining
  await batch.commit();
  console.log(`Finished. Processed ${processed} settlement docs, updated ${updated}.`);
}

backfill().then(() => {
  console.log("✅ callType backfill completed");
  process.exit(0);
}).catch((err) => {
  console.error("❌ backfill failed", err);
  process.exit(1);
}); 