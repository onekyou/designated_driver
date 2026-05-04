import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

const REGION = "asia-northeast3";
const SIGNUP_BONUS = 30000;

async function isHeadManager(uid: string | undefined): Promise<boolean> {
  if (!uid) return false;
  const adminDoc = await admin.firestore().doc(`admins/${uid}`).get();
  if (!adminDoc.exists) return false;
  const role = adminDoc.data()?.role;
  return role === "HEAD_MANAGER" || role === "SUPER_ADMIN";
}

/**
 * migrateExistingOfficesWallet (admin only callable, 1회)
 * 기존 사무실에 SIGNUP_BONUS 30,000 적립 (없으면 wallet 신규 생성, 있으면 += 30,000)
 * 멱등성: signup_bonus_${officeId} 트랜잭션 doc ID로 중복 방지
 *
 * input: { dryRun?: boolean }
 * output: { migrated, skipped, errors, details }
 */
export const migrateExistingOfficesWallet = onCall(
  { region: REGION, timeoutSeconds: 540 },
  async (request) => {
    if (!(await isHeadManager(request.auth?.uid))) {
      throw new HttpsError("permission-denied", "총관리자만 마이그레이션 실행 가능합니다.");
    }
    const dryRun = !!request.data?.dryRun;

    const officesSnap = await admin.firestore().collectionGroup("offices").get();
    logger.info(`[migrate] 사무실 ${officesSnap.size}개 발견 (dryRun: ${dryRun})`);

    let migrated = 0;
    let skipped = 0;
    const errors: { officeId: string; error: string }[] = [];
    const details: { officeId: string; before: number; after: number; action: string }[] = [];

    for (const officeDoc of officesSnap.docs) {
      const officeRef = officeDoc.ref;
      const officeId = officeDoc.id;
      const pointsRef = officeRef.collection("points").doc("points");
      const txId = `signup_bonus_${officeId}`;
      const txRef = officeRef.collection("point_transactions").doc(txId);

      try {
        const result = await admin.firestore().runTransaction(async (tx) => {
          const existingTx = await tx.get(txRef);
          if (existingTx.exists) {
            return { action: "skipped (already migrated)", before: 0, after: 0 };
          }

          const pointsSnap = await tx.get(pointsRef);
          const before = pointsSnap.data()?.balance || 0;
          const after = before + SIGNUP_BONUS;

          if (!dryRun) {
            tx.set(pointsRef, {
              balance: after,
              updatedAt: FieldValue.serverTimestamp(),
            }, { merge: true });

            tx.set(txRef, {
              type: "SIGNUP_BONUS",
              amount: SIGNUP_BONUS,
              balanceAfter: after,
              description: "마이그레이션 가입 보너스",
              status: "COMPLETED",
              timestamp: FieldValue.serverTimestamp(),
              createdBy: request.auth!.uid,
            });
          }

          return { action: dryRun ? "would migrate" : "migrated", before, after };
        });

        details.push({ officeId, ...result });
        if (result.action.startsWith("skipped")) {
          skipped++;
        } else {
          migrated++;
        }
      } catch (e: any) {
        errors.push({ officeId, error: e.message });
        logger.error(`[migrate] 사무실 ${officeId} 마이그레이션 실패`, e);
      }
    }

    logger.info(`[migrate] 완료 - migrated: ${migrated}, skipped: ${skipped}, errors: ${errors.length}`);
    return { migrated, skipped, errors, details };
  }
);
