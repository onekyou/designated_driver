/**
 * Settlement Handler Functions
 * 정산 세션 관리 및 동기화를 위한 Cloud Functions
 */

import * as admin from "firebase-admin";
import { Timestamp } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { buildMulticastFcmPayload } from "../utils/fcmPayload";

// 정산 세션 인터페이스
interface SettlementSession {
  metadata: SettlementMetadata;
  totals: SettlementTotals;
  calls: CallSettlement[];
}

interface SettlementMetadata {
  version: number;
  lastUpdatedAt: Timestamp | null;
  lastUpdatedBy: string;
  depositRatio: number;
  createdAt: Timestamp | null;
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
  completedAt: Timestamp | null;
  confirmedByOffice: boolean;
  syncedAt: Timestamp | null;
}

/**
 * 근무일 계산 (오전 10시 이전은 전날로 처리)
 * UTC 입력을 KST로 변환 후 판단
 */
function calculateWorkDate(timestamp: Date): string {
  const utc = new Date(timestamp);
  const koreaTime = new Date(utc.getTime() + (9 * 60 * 60 * 1000));
  if (koreaTime.getHours() < 10) {
    koreaTime.setDate(koreaTime.getDate() - 1);
  }
  return koreaTime.toISOString().substring(0, 10); // YYYY-MM-DD
}

/**
 * 오늘 근무일 계산 (exported for use in index.ts)
 */
export function getTodayWorkDate(): string {
  return calculateWorkDate(new Date());
}

/**
 * 어제 근무일 계산 (exported for use in index.ts)
 */
export function getYesterdayWorkDate(): string {
  const yesterday = new Date(Date.now() - (24 * 60 * 60 * 1000));
  return calculateWorkDate(yesterday);
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
        completedAt: callData.completedAt || Timestamp.now(),
        confirmedByOffice: false,
        syncedAt: Timestamp.now()
      };

      if (sessionDoc.exists) {
        const session = sessionDoc.data() as SettlementSession;

        // 마감된 세션에는 콜 추가 불가
        if (session.metadata?.isFinalized) {
          logger.warn(`[Settlement] Session ${workDate} is already finalized. Skipping call ${callId}`);
          return;
        }

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
          "metadata.lastUpdatedAt": Timestamp.now(),
          "metadata.lastUpdatedBy": "cloud_function"
        });

        logger.info(`[Settlement] Added call ${callId} to existing session ${workDate}`);
      } else {
        // 새 세션 생성 - 사무실 문서에서 depositRatio 읽기
        const officeRef = db.collection("provinces").doc(provinceId)
          .collection("cities").doc(cityId)
          .collection("offices").doc(officeId);
        const officeDoc = await transaction.get(officeRef);
        const depositRatio = officeDoc.exists ? (officeDoc.data()?.depositRatio || 60) : 60;
        const newTotals = recalculateTotals([newCall], depositRatio);

        const newSession: SettlementSession = {
          metadata: {
            version: 1,
            lastUpdatedAt: Timestamp.now(),
            lastUpdatedBy: "cloud_function",
            depositRatio: depositRatio,
            createdAt: Timestamp.now(),
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
  const yesterdayDate = getYesterdayWorkDate();

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
                "metadata.lastUpdatedAt": Timestamp.now(),
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
 * 로그인 상태의 기사들에게 업무 마감 알림 전송
 * Call Manager에서 마감 시 호출
 */
export async function notifyDriversSettlementFinalized(
  provinceId: string,
  cityId: string,
  officeId: string,
  sessionDate: string,
  totals: SettlementTotals
): Promise<{ sent: number; skipped: number }> {
  const db = admin.firestore();

  try {
    // 해당 사무실의 기사 목록 조회 (로그인 상태만)
    const driversSnap = await db.collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId)
      .collection("designated_drivers")
      .where("status", "not-in", ["OFFLINE", "offline"])
      .get();

    if (driversSnap.empty) {
      logger.info(`[Settlement] No online drivers found for ${provinceId}/${cityId}/${officeId}`);
      return { sent: 0, skipped: 0 };
    }

    const tokens: string[] = [];
    let skippedCount = 0;

    driversSnap.forEach(doc => {
      const data = doc.data();
      const token = data.fcmToken;
      const status = data.status;

      // OFFLINE이 아닌 기사만 알림 전송
      if (token && status !== "OFFLINE" && status !== "offline") {
        tokens.push(token);
        logger.info(`[Settlement] Will notify driver: ${doc.id}, status: ${status}`);
      } else {
        skippedCount++;
        logger.info(`[Settlement] Skipping driver: ${doc.id}, status: ${status}, hasToken: ${!!token}`);
      }
    });

    if (tokens.length === 0) {
      logger.info(`[Settlement] No valid tokens for online drivers`);
      return { sent: 0, skipped: skippedCount };
    }

    // FCM 알림 전송
    const finalizeBody = `오늘 업무가 마감되었습니다. 총 ${totals.callCount}건, ${totals.totalFare.toLocaleString()}원`;
    const response = await admin.messaging().sendEachForMulticast(buildMulticastFcmPayload({
      data: {
        type: "SETTLEMENT_FINALIZED",
        sessionDate: sessionDate,
        totalCount: String(totals.callCount),
        totalFare: String(totals.totalFare),
        totalDeposit: String(totals.totalDeposit),
        title: "업무 마감 안내",
        body: finalizeBody,
      },
      title: "업무 마감 안내",
      body: finalizeBody,
      level: "active",
    }, tokens));

    logger.info(`[Settlement] Finalization notification sent. Success: ${response.successCount}, Failure: ${response.failureCount}`);

    return { sent: response.successCount, skipped: skippedCount };
  } catch (error) {
    logger.error(`[Settlement] Failed to notify drivers:`, error);
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

    // 알림 전송 (예외: 기존 최상위 notification 유지, apns 블록 추가)
    const discBody = `${sessionDate} 정산에서 불일치가 발견되었습니다. 확인이 필요합니다.`;
    const response = await admin.messaging().sendEachForMulticast({
      tokens: tokens,
      notification: {
        title: "정산 불일치 감지",
        body: discBody
      },
      data: {
        type: "SETTLEMENT_DISCREPANCY",
        sessionDate: sessionDate,
        details: JSON.stringify(details)
      },
      android: {
        priority: "high" as const
      },
      apns: {
        headers: {
          "apns-push-type": "alert" as const,
          "apns-priority": "10" as const,
        },
        payload: {
          aps: {
            alert: { title: "정산 불일치 감지", body: discBody },
            sound: "default" as const,
            "content-available": 1,
            "mutable-content": 1,
            "interruption-level": "active" as const,
          },
        },
      },
    });

    logger.info(`[Settlement] Discrepancy notification sent. Success: ${response.successCount}, Failure: ${response.failureCount}`);
  } catch (error) {
    logger.error(`[Settlement] Failed to send discrepancy notification:`, error);
  }
}

