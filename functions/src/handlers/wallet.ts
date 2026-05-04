import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

const REGION = "asia-northeast3";

interface AdminContext {
  uid: string;
  provinceId: string;
  cityId: string;
  officeId: string;
}

async function getOfficeAdminContext(uid: string | undefined): Promise<AdminContext> {
  if (!uid) {
    throw new HttpsError("unauthenticated", "인증이 필요합니다.");
  }
  const adminDoc = await admin.firestore().doc(`admins/${uid}`).get();
  if (!adminDoc.exists) {
    throw new HttpsError("permission-denied", "관리자만 접근 가능합니다.");
  }
  const data = adminDoc.data()!;
  if (!data.associatedProvinceId || !data.associatedCityId || !data.associatedOfficeId) {
    throw new HttpsError("permission-denied", "사무실에 소속된 관리자만 접근 가능합니다.");
  }
  return {
    uid,
    provinceId: data.associatedProvinceId,
    cityId: data.associatedCityId,
    officeId: data.associatedOfficeId,
  };
}

async function isHeadManager(uid: string | undefined): Promise<boolean> {
  if (!uid) return false;
  const adminDoc = await admin.firestore().doc(`admins/${uid}`).get();
  if (!adminDoc.exists) return false;
  const role = adminDoc.data()?.role;
  return role === "HEAD_MANAGER" || role === "SUPER_ADMIN";
}

/**
 * submitWithdrawalRequest (사무실 매니저 callable)
 * 매니저가 wallet 출금 신청 → withdrawalRequests doc 생성 (PENDING)
 * 위치: provinces/{p}/cities/{c}/withdrawalRequests/{requestId} (기존 rules 활용, 결정 #17 옵션 A)
 */
export const submitWithdrawalRequest = onCall(
  { region: REGION },
  async (request) => {
    const ctx = await getOfficeAdminContext(request.auth?.uid);
    const { amount, bankName, accountNumber, accountHolder } = request.data || {};

    if (!amount || typeof amount !== "number" || amount <= 0) {
      throw new HttpsError("invalid-argument", "출금 금액이 올바르지 않습니다.");
    }
    if (!bankName || !accountNumber || !accountHolder) {
      throw new HttpsError("invalid-argument", "은행명·계좌번호·예금주가 필요합니다.");
    }

    // 잔액 검증
    const pointsRef = admin.firestore()
      .collection("provinces").doc(ctx.provinceId)
      .collection("cities").doc(ctx.cityId)
      .collection("offices").doc(ctx.officeId)
      .collection("points").doc("points");
    const pointsSnap = await pointsRef.get();
    const currentBalance = pointsSnap.data()?.balance || 0;
    if (currentBalance < amount) {
      throw new HttpsError("failed-precondition", `잔액 부족 (현재: ${currentBalance}, 요청: ${amount})`);
    }

    const reqRef = admin.firestore()
      .collection("provinces").doc(ctx.provinceId)
      .collection("cities").doc(ctx.cityId)
      .collection("withdrawalRequests").doc();

    await reqRef.set({
      officeId: ctx.officeId,
      amount,
      bankName,
      accountNumber,
      accountHolder,
      status: "PENDING",
      requestedBy: ctx.uid,
      requestedAt: FieldValue.serverTimestamp(),
      processedAt: null,
    });

    logger.info(`[wallet] submitWithdrawal - office: ${ctx.officeId}, amount: ${amount}, requestId: ${reqRef.id}`);
    return { requestId: reqRef.id };
  }
);

/**
 * processDeposit (admin = 사용자 HEAD_MANAGER callable)
 * 사용자가 입금 확인 후 사무실 wallet 잔액 +amount + DEPOSIT 트랜잭션
 */
export const processDeposit = onCall(
  { region: REGION },
  async (request) => {
    if (!(await isHeadManager(request.auth?.uid))) {
      throw new HttpsError("permission-denied", "총관리자만 입금 처리할 수 있습니다.");
    }
    const { provinceId, cityId, officeId, amount, depositorName } = request.data || {};
    if (!provinceId || !cityId || !officeId) {
      throw new HttpsError("invalid-argument", "provinceId, cityId, officeId가 필요합니다.");
    }
    if (!amount || typeof amount !== "number" || amount <= 0) {
      throw new HttpsError("invalid-argument", "입금 금액이 올바르지 않습니다.");
    }

    const officeRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId);
    const pointsRef = officeRef.collection("points").doc("points");

    const newBalance = await admin.firestore().runTransaction(async (tx) => {
      const pointsSnap = await tx.get(pointsRef);
      const current = pointsSnap.data()?.balance || 0;
      const next = current + amount;

      tx.set(pointsRef, {
        balance: next,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      const txRef = officeRef.collection("point_transactions").doc();
      tx.set(txRef, {
        type: "DEPOSIT",
        amount,
        balanceAfter: next,
        description: `입금 처리 (${depositorName || "익명"})`,
        depositorName: depositorName || "",
        status: "COMPLETED",
        timestamp: FieldValue.serverTimestamp(),
        createdBy: request.auth!.uid,
      });

      return next;
    });

    logger.info(`[wallet] processDeposit - office: ${officeId}, amount: ${amount}, newBalance: ${newBalance}`);
    return { newBalance };
  }
);

/**
 * processWithdrawal (admin = 사용자 HEAD_MANAGER callable)
 * 출금 신청 PENDING → COMPLETED 처리 + wallet 잔액 차감 + WITHDRAWAL 트랜잭션
 * 사용자가 다음 날 사무실 계좌로 실 입금한 후 호출
 */
export const processWithdrawal = onCall(
  { region: REGION },
  async (request) => {
    if (!(await isHeadManager(request.auth?.uid))) {
      throw new HttpsError("permission-denied", "총관리자만 출금 처리할 수 있습니다.");
    }
    const { provinceId, cityId, requestId } = request.data || {};
    if (!provinceId || !cityId || !requestId) {
      throw new HttpsError("invalid-argument", "provinceId, cityId, requestId가 필요합니다.");
    }

    const reqRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("withdrawalRequests").doc(requestId);

    const newBalance = await admin.firestore().runTransaction(async (tx) => {
      const reqSnap = await tx.get(reqRef);
      if (!reqSnap.exists) {
        throw new HttpsError("not-found", "출금 요청을 찾을 수 없습니다.");
      }
      const reqData = reqSnap.data()!;
      if (reqData.status !== "PENDING") {
        throw new HttpsError("failed-precondition", `이미 처리된 요청입니다. (status: ${reqData.status})`);
      }

      const officeRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(reqData.officeId);
      const pointsRef = officeRef.collection("points").doc("points");
      const pointsSnap = await tx.get(pointsRef);
      const current = pointsSnap.data()?.balance || 0;
      if (current < reqData.amount) {
        throw new HttpsError("failed-precondition", `잔액 부족 (현재: ${current}, 출금: ${reqData.amount})`);
      }
      const next = current - reqData.amount;

      tx.set(pointsRef, {
        balance: next,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      const txRef = officeRef.collection("point_transactions").doc();
      tx.set(txRef, {
        type: "WITHDRAWAL",
        amount: -reqData.amount,
        balanceAfter: next,
        description: `출금 (${reqData.bankName} ${reqData.accountHolder})`,
        withdrawalRequestId: requestId,
        status: "COMPLETED",
        timestamp: FieldValue.serverTimestamp(),
        createdBy: request.auth!.uid,
      });

      tx.update(reqRef, {
        status: "COMPLETED",
        processedAt: FieldValue.serverTimestamp(),
        processedBy: request.auth!.uid,
      });

      return next;
    });

    logger.info(`[wallet] processWithdrawal - requestId: ${requestId}, newBalance: ${newBalance}`);
    return { newBalance };
  }
);
