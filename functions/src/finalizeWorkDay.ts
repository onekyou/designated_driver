import * as functions from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

export const finalizeWorkDay = functions.onCall({region: "asia-northeast3"}, async (req) => {
  const { regionId, officeId } = req.data || {};
  if (!regionId || !officeId) throw new functions.HttpsError("invalid-argument", "regionId and officeId required");
  if (!req.auth) throw new functions.HttpsError("unauthenticated", "Must be signed in");

  const db = admin.firestore();
  const settlementsCol = db.collection("regions").doc(regionId)
      .collection("offices").doc(officeId)
      .collection("settlements");

  const snap = await settlementsCol.where("isFinalized", "==", false).get();

  const batch = db.batch();
  let totalTrips = 0;
  let totalFare = 0;

  if (!snap.empty) {
    snap.forEach(doc => {
      batch.update(doc.ref, { isFinalized: true });
      totalTrips++;
      totalFare += doc.get("fare") || 0;
    });

    // 빈 배치가 아닐 때만 커밋 (0건이면 커밋 생략)
    await batch.commit();
  }

  const today = new Date().toISOString().substring(0,10);
  await db.collection("regions").doc(regionId)
    .collection("offices").doc(officeId)
    .collection("dailySettlements").doc(today)
    .collection("sessions").add({
      endAt: admin.firestore.FieldValue.serverTimestamp(),
      totalTrips,
      totalFare
    });
  return { totalTrips };
}); 