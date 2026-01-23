/**
 * Settlement Handler Functions
 * 정산 세션 관리 및 동기화를 위한 Cloud Functions
 */

import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

// 정산 세션 인터페이스
interface SettlementSession {
  metadata: SettlementMetadata;
  totals: SettlementTotals;
  calls: CallSettlement[];
}

interface SettlementMetadata {
  version: number;
  lastUpdatedAt: admin.firestore.Timestamp | null;
  lastUpdatedBy: string;
  depositRatio: number;
  createdAt: admin.firestore.Timestamp | null;
  isFinalized: boolean;
}

interface SettlementTotals {
  totalFare: number;
  totalDeposit: number;
  totalDriverShare: number;
  totalCash: number;
  totalCard: number;
  totalCredit: number;
  totalPoints: number;
  callCount: number;
}

interface CallSettlement {
  callId: string;
  driverId: string;
  driverName: string;
  customerName: string;
  customerPhone: string;
  departure: string;
  destination: string;
  fare: number;
  paymentMethod: string;
  cashReceived: number;
  creditAmount: number;
  pointsUsed: number;
  completedAt: admin.firestore.Timestamp | null;
  confirmedByOffice: boolean;
  syncedAt: admin.firestore.Timestamp | null;
}

/**
 * 근무일 계산 (새벽 6시 이전은 전날로 처리)
 */
function calculateWorkDate(timestamp: Date): string {
  const date = new Date(timestamp);
  if (date.getHours() < 6) {
    date.setDate(date.getDate() - 1);
  }
  return date.toISOString().substring(0, 10); // YYYY-MM-DD
}

/**
 * 오늘 근무일 계산 (exported for use in index.ts)
 */
export function getTodayWorkDate(): string {
  const now = new Date();
  // 한국 시간으로 변환
  const koreaTime = new Date(now.getTime() + (9 * 60 * 60 * 1000));
  return calculateWorkDate(koreaTime);
}

/**
 * 콜 완료 시 정산 세션에 자동 추가
 * onDocumentUpdated 트리거에서 호출
 */
export async function addCallToSettlementSession(
  provinceId: string,
  cityId: string,
  officeId: string,
  callData: any,
  callId: string
): Promise<void> {
  const db = admin.firestore();

  // 완료 시간 기반으로 근무일 계산
  const completedAt = callData.completedAt?.toDate() || new Date();
  const workDate = calculateWorkDate(completedAt);

  const sessionRef = db.collection("provinces").doc(provinceId)
    .collection("cities").doc(cityId)
    .collection("offices").doc(officeId)
    .collection("settlementSessions").doc(workDate);

  try {
    await db.runTransaction(async (transaction) => {
      const sessionDoc = await transaction.get(sessionRef);

      // 요금 정보
      const fare = callData.fareFinal || callData.fare_set || 0;
      const cashReceived = callData.cashReceived || 0;
      const creditAmount = callData.creditAmount || 0;
      const pointsUsed = callData.pointsUsed || 0;

      // 새 콜 정산 정보
      const newCall: CallSettlement = {
        callId: callId,
        driverId: callData.assignedDriverId || "",
        driverName: callData.assignedDriverName || "N/A",
        customerName: callData.customerName || "N/A",
        customerPhone: callData.phoneNumber || "",
        departure: callData.departure_set || callData.departure || "N/A",
        destination: callData.destination_set || callData.destination || "N/A",
        fare: fare,
        paymentMethod: callData.paymentMethod || "N/A",
        cashReceived: cashReceived,
        creditAmount: creditAmount,
        pointsUsed: pointsUsed,
        completedAt: callData.completedAt || admin.firestore.Timestamp.now(),
        confirmedByOffice: false,
        syncedAt: admin.firestore.Timestamp.now()
      };

      if (sessionDoc.exists) {
        const session = sessionDoc.data() as SettlementSession;

        // 중복 체크
        const existingCallIndex = session.calls?.findIndex(c => c.callId === callId) ?? -1;
        if (existingCallIndex >= 0) {
          logger.info(`[Settlement] Call ${callId} already exists in session ${workDate}`);
          return;
        }

        // 기존 세션에 콜 추가
        const updatedCalls = [...(session.calls || []), newCall];
        const depositRatio = session.metadata?.depositRatio || 60;

        // 집계 재계산
        const updatedTotals = recalculateTotals(updatedCalls, depositRatio);

        transaction.update(sessionRef, {
          "calls": updatedCalls,
          "totals": updatedTotals,
          "metadata.version": (session.metadata?.version || 0) + 1,
          "metadata.lastUpdatedAt": admin.firestore.Timestamp.now(),
          "metadata.lastUpdatedBy": "cloud_function"
        });

        logger.info(`[Settlement] Added call ${callId} to existing session ${workDate}`);
      } else {
        // 새 세션 생성
        const depositRatio = 60; // 기본값
        const newTotals = recalculateTotals([newCall], depositRatio);

        const newSession: SettlementSession = {
          metadata: {
            version: 1,
            lastUpdatedAt: admin.firestore.Timestamp.now(),
            lastUpdatedBy: "cloud_function",
            depositRatio: depositRatio,
            createdAt: admin.firestore.Timestamp.now(),
            isFinalized: false
          },
          totals: newTotals,
          calls: [newCall]
        };

        transaction.set(sessionRef, newSession);
        logger.info(`[Settlement] Created new session ${workDate} with call ${callId}`);
      }
    });
  } catch (error) {
    logger.error(`[Settlement] Failed to add call ${callId} to session:`, error);
    throw error;
  }
}

/**
 * 정산 집계 재계산
 */
function recalculateTotals(calls: CallSettlement[], depositRatio: number): SettlementTotals {
  let totalFare = 0;
  let totalCash = 0;
  let totalCard = 0;
  let totalCredit = 0;
  let totalPoints = 0;

  for (const call of calls) {
    totalFare += call.fare;
    totalCredit += call.creditAmount;
    totalPoints += call.pointsUsed;

    switch (call.paymentMethod) {
      case "현금":
        totalCash += call.fare;
        break;
      case "이체":
      case "카드":
        totalCard += call.fare;
        break;
      case "현금+포인트":
        totalCash += call.cashReceived;
        break;
    }
  }

  const totalDeposit = Math.floor(totalFare * depositRatio / 100);
  const totalDriverShare = totalFare - totalDeposit;

  return {
    totalFare,
    totalDeposit,
    totalDriverShare,
    totalCash,
    totalCard,
    totalCredit,
    totalPoints,
    callCount: calls.length
  };
}

/**
 * 일일 정산 자동 마감 (매일 새벽 6시 실행)
 */
export async function autoFinalizeSettlementSessions(): Promise<{ processed: number; errors: string[] }> {
  const db = admin.firestore();
  const errors: string[] = [];
  let processedCount = 0;

  // 어제 근무일 계산
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  const yesterdayDate = calculateWorkDate(yesterday);

  logger.info(`[Settlement] Starting auto-finalize for date: ${yesterdayDate}`);

  try {
    // 모든 사무실 조회
    const provincesSnap = await db.collection("provinces").get();

    for (const provinceDoc of provincesSnap.docs) {
      const citiesSnap = await provinceDoc.ref.collection("cities").get();

      for (const cityDoc of citiesSnap.docs) {
        const officesSnap = await cityDoc.ref.collection("offices").get();

        for (const officeDoc of officesSnap.docs) {
          try {
            const sessionRef = officeDoc.ref
              .collection("settlementSessions")
              .doc(yesterdayDate);

            const sessionDoc = await sessionRef.get();

            if (sessionDoc.exists) {
              const session = sessionDoc.data() as SettlementSession;

              // 이미 마감된 세션은 스킵
              if (session.metadata?.isFinalized) {
                continue;
              }

              // 마감 처리
              await sessionRef.update({
                "metadata.isFinalized": true,
                "metadata.version": (session.metadata?.version || 0) + 1,
                "metadata.lastUpdatedAt": admin.firestore.Timestamp.now(),
                "metadata.lastUpdatedBy": "auto_finalize"
              });

              processedCount++;
              logger.info(`[Settlement] Finalized session for ${provinceDoc.id}/${cityDoc.id}/${officeDoc.id}/${yesterdayDate}`);
            }
          } catch (officeError) {
            const errorMsg = `Failed to finalize ${provinceDoc.id}/${cityDoc.id}/${officeDoc.id}: ${officeError}`;
            errors.push(errorMsg);
            logger.error(`[Settlement] ${errorMsg}`);
          }
        }
      }
    }
  } catch (error) {
    logger.error("[Settlement] Auto-finalize failed:", error);
    throw error;
  }

  logger.info(`[Settlement] Auto-finalize completed. Processed: ${processedCount}, Errors: ${errors.length}`);
  return { processed: processedCount, errors };
}

/**
 * 정산 불일치 검사 및 알림
 */
export async function checkSettlementDiscrepancies(
  provinceId: string,
  cityId: string,
  officeId: string,
  sessionDate: string
): Promise<{ hasDiscrepancy: boolean; details: string[] }> {
  const db = admin.firestore();
  const details: string[] = [];

  try {
    // 공유 정산 세션 조회
    const sessionRef = db.collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId)
      .collection("settlementSessions").doc(sessionDate);

    const sessionDoc = await sessionRef.get();

    if (!sessionDoc.exists) {
      return { hasDiscrepancy: false, details: ["정산 세션이 없습니다."] };
    }

    const session = sessionDoc.data() as SettlementSession;

    // 원본 콜 데이터와 비교
    const callsRef = db.collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId)
      .collection("calls");

    // 해당 날짜의 완료된 콜 조회
    const completedCallsSnap = await callsRef
      .where("status", "==", "COMPLETED")
      .get();

    // 근무일 기준으로 필터링
    const dateFilteredCalls = completedCallsSnap.docs.filter(doc => {
      const completedAt = doc.data().completedAt?.toDate();
      if (!completedAt) return false;
      return calculateWorkDate(completedAt) === sessionDate;
    });

    // 콜 수 비교
    if (dateFilteredCalls.length !== session.calls.length) {
      details.push(`콜 수 불일치: 원본=${dateFilteredCalls.length}, 세션=${session.calls.length}`);
    }

    // 총 요금 비교
    const originalTotalFare = dateFilteredCalls.reduce((sum, doc) => {
      return sum + (doc.data().fareFinal || doc.data().fare_set || 0);
    }, 0);

    if (originalTotalFare !== session.totals.totalFare) {
      details.push(`총 요금 불일치: 원본=${originalTotalFare}, 세션=${session.totals.totalFare}`);
    }

    // 누락된 콜 확인
    const sessionCallIds = new Set(session.calls.map(c => c.callId));
    for (const callDoc of dateFilteredCalls) {
      if (!sessionCallIds.has(callDoc.id)) {
        details.push(`누락된 콜: ${callDoc.id}`);
      }
    }

    const hasDiscrepancy = details.length > 0;

    if (hasDiscrepancy) {
      logger.warn(`[Settlement] Discrepancies found for ${provinceId}/${cityId}/${officeId}/${sessionDate}:`, details);
    }

    return { hasDiscrepancy, details };
  } catch (error) {
    logger.error(`[Settlement] Discrepancy check failed:`, error);
    throw error;
  }
}

/**
 * 불일치 발견 시 관리자에게 알림 전송
 */
export async function notifySettlementDiscrepancy(
  provinceId: string,
  cityId: string,
  officeId: string,
  sessionDate: string,
  details: string[]
): Promise<void> {
  const db = admin.firestore();

  try {
    // 콜매니저 FCM 토큰 조회
    const managerTokensSnap = await db.collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId)
      .collection("managerTokens")
      .get();

    if (managerTokensSnap.empty) {
      logger.warn(`[Settlement] No manager tokens found for ${provinceId}/${cityId}/${officeId}`);
      return;
    }

    const tokens: string[] = [];
    managerTokensSnap.forEach(doc => {
      const token = doc.data().fcmToken;
      if (token) tokens.push(token);
    });

    if (tokens.length === 0) {
      return;
    }

    // 알림 전송
    const payload = {
      notification: {
        title: "정산 불일치 감지",
        body: `${sessionDate} 정산에서 불일치가 발견되었습니다. 확인이 필요합니다.`
      },
      data: {
        type: "SETTLEMENT_DISCREPANCY",
        sessionDate: sessionDate,
        details: JSON.stringify(details)
      },
      android: {
        priority: "high" as const
      }
    };

    const response = await admin.messaging().sendEachForMulticast({
      tokens: tokens,
      ...payload
    });

    logger.info(`[Settlement] Discrepancy notification sent. Success: ${response.successCount}, Failure: ${response.failureCount}`);
  } catch (error) {
    logger.error(`[Settlement] Failed to send discrepancy notification:`, error);
  }
}
