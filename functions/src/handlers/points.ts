import * as admin from "firebase-admin";
import * as functions from "firebase-functions";

const logger = functions.logger;

/**
 * 공유콜 완료 시 포인트 분배 처리
 * @param sharedCallData 공유콜 데이터
 * @param regionId 대상 사무실 지역 ID
 * @param officeId 대상 사무실 ID
 * @param fare 요금
 * @param sourceSharedCallId 원본 공유콜 ID
 * @returns 처리 결과
 */
export async function processSharedCallPoints(
  sharedCallData: any,
  regionId: string,
  officeId: string,
  fare: number,
  sourceSharedCallId: string
): Promise<void> {
  const pointRatio = 0.1; // 10% 수수료
  const pointAmount = Math.round(fare * pointRatio);

  logger.info(`[points] 포인트 처리 시작. 요금: ${fare}, 포인트: ${pointAmount}`);

  await admin.firestore().runTransaction(async (tx) => {
    // 1) 포인트 잔액 조회 및 업데이트
    const sourcePointsRef = admin.firestore()
      .collection("regions").doc(sharedCallData.sourceRegionId)
      .collection("offices").doc(sharedCallData.sourceOfficeId)
      .collection("points").doc("points");

    const targetPointsRef = admin.firestore()
      .collection("regions").doc(regionId)
      .collection("offices").doc(officeId)
      .collection("points").doc("points");

    // 포인트 잔액 읽기
    const [sourceSnap, targetSnap] = await Promise.all([
      tx.get(sourcePointsRef),
      tx.get(targetPointsRef)
    ]);

    const sourceBalance = (sourceSnap.data()?.balance || 0) + pointAmount;
    const targetBalance = (targetSnap.data()?.balance || 0) - pointAmount;

    logger.info(`[points] 잔액 계산 - Source: ${sourceBalance} (+${pointAmount}), Target: ${targetBalance} (-${pointAmount})`);

    // 포인트 문서 업데이트
    tx.set(sourcePointsRef, {
      balance: sourceBalance,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    tx.set(targetPointsRef, {
      balance: targetBalance,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    // 2) 포인트 거래 내역 저장 (사무실별 서브컬렉션)
    const timestamp = admin.firestore.FieldValue.serverTimestamp();

    // 원본 사무실 거래 내역 (포인트 받음)
    const sourceOfficeRef = admin.firestore()
      .collection("regions").doc(sharedCallData.sourceRegionId)
      .collection("offices").doc(sharedCallData.sourceOfficeId);
    const sourceTransactionRef = sourceOfficeRef.collection("point_transactions").doc();

    tx.set(sourceTransactionRef, {
      type: "SHARED_CALL_RECEIVE",
      amount: pointAmount,
      description: `공유콜 수수료 수익 (${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"})`,
      timestamp: timestamp,
      createdBy: "system",
      relatedSharedCallId: sourceSharedCallId
    });

    // 대상 사무실 거래 내역 (포인트 차감)
    const targetOfficeRef = admin.firestore()
      .collection("regions").doc(regionId)
      .collection("offices").doc(officeId);
    const targetTransactionRef = targetOfficeRef.collection("point_transactions").doc();

    tx.set(targetTransactionRef, {
      type: "SHARED_CALL_SEND",
      amount: -pointAmount,
      description: `공유콜 수수료 지출 (${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"})`,
      timestamp: timestamp,
      createdBy: "system",
      relatedSharedCallId: sourceSharedCallId
    });

    logger.info(`[points] 포인트 처리 완료. Source: +${pointAmount}, Target: -${pointAmount}`);
  });
}

/**
 * 포인트 초기화 (테스트용)
 */
export async function initializePoints(regionId: string, officeId: string, initialBalance: number = 0): Promise<void> {
  const pointsRef = admin.firestore()
    .collection("regions").doc(regionId)
    .collection("offices").doc(officeId)
    .collection("points").doc("points");

  await pointsRef.set({
    balance: initialBalance,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  logger.info(`[points] 포인트 초기화 완료. Region: ${regionId}, Office: ${officeId}, Balance: ${initialBalance}`);
}

/**
 * 포인트 잔액 조회
 */
export async function getPointBalance(regionId: string, officeId: string): Promise<number> {
  const pointsRef = admin.firestore()
    .collection("regions").doc(regionId)
    .collection("offices").doc(officeId)
    .collection("points").doc("points");

  const snapshot = await pointsRef.get();
  return snapshot.data()?.balance || 0;
}