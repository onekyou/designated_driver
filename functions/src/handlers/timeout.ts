/**
 * 콜 timeout / presence 이벤트 기반 처리
 *
 * 기존 `checkAssignedTimeout` 매분 폴링 함수를 이벤트 기반으로 전환.
 *
 * ┌───────────────────────────────────────────────────────────────────┐
 * │ ⚠️ 콜마당 간소화 결정 (2026-05-05)                                │
 * │                                                                   │
 * │ 본 파일에 4개 함수가 정의되어 있으나, **분기 1만 활성**:          │
 * │   ✅ checkSingleCallAssignedTimeout  — index.ts에서 export        │
 * │   💤 checkSingleCallInProgressOffline — index.ts export 주석 (휴면)│
 * │   💤 onDriverPresenceOffline          — index.ts export 주석 (휴면)│
 * │   💤 enqueueInProgressOfflineTask     — 분기 3 helper, dead code  │
 * │                                                                   │
 * │ 휴면 사유:                                                        │
 * │   - 콜·기사 데이터는 driver_app 자동 fetch + 매니저 listener 로    │
 * │     자동 복구되어 시스템 무결성 보장됨                             │
 * │   - 분기 2/3은 매니저 알림용 — 양평 1곳 사람 운영으로 대체 가능   │
 * │   - 사무실 확장 시 재활성화 검토 (index.ts export 주석 풀기)      │
 * │                                                                   │
 * │ 분기 1 동작:                                                      │
 * │   - ASSIGNED timeout: 배차 시점 Cloud Tasks deferred (1분 후 발화)│
 * │     → 단일 콜 검사 → status 검증 후 WAITING 복귀                  │
 * └───────────────────────────────────────────────────────────────────┘
 *
 * 효과: 매분 풀스캔 폴링(reads 25만/일) → 이벤트 기반(reads ~150~300/일).
 */

import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onValueWritten } from "firebase-functions/v2/database";
import { getFunctions } from "firebase-admin/functions";
import * as admin from "firebase-admin";
import { Timestamp, FieldValue } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { recordAcceptanceEvent } from "../analytics/acceptanceEvents";
import { buildFcmPayload, buildMulticastFcmPayload } from "../utils/fcmPayload";

const REGION = "asia-northeast3";

// =========================================================================
// payloads
// =========================================================================

interface AssignedTimeoutPayload {
  provinceId: string;
  cityId: string;
  officeId: string;
  callId: string;
  expectedDriverId: string;
  expectedAssignedTimestampMs: number;
}

interface InProgressOfflinePayload {
  provinceId: string;
  cityId: string;
  officeId: string;
  callId: string;
  driverId: string;
}

// =========================================================================
// helpers
// =========================================================================

/**
 * 기사 presence 상태 조회 (RTDB).
 * `presence/drivers/{driverId}` 의 status 값 ("online" | "background" | "offline").
 * 누락 시 "offline" 으로 간주 (안전한 default).
 */
export async function getDriverPresenceStatus(driverId: string): Promise<string> {
  try {
    const presenceSnapshot = await admin.database().ref(`presence/drivers/${driverId}`).get();
    return presenceSnapshot.val()?.status || "offline";
  } catch {
    return "unknown";
  }
}

/**
 * 관리자에게 presence 기반 알림 FCM 전송 (managerTokens 컬렉션 multicast).
 */
async function sendPresenceAlert(
  officeRef: FirebaseFirestore.DocumentReference,
  callId: string,
  driverName: string,
  title: string,
  message: string,
): Promise<void> {
  try {
    const managerTokensSnap = await officeRef.collection("managerTokens").get();
    if (managerTokensSnap.empty) return;

    const tokens = managerTokensSnap.docs
      .map(doc => doc.data().fcmToken)
      .filter((token): token is string => !!token);
    if (tokens.length === 0) return;

    await admin.messaging().sendEachForMulticast(buildMulticastFcmPayload({
      data: {
        type: "NOTIFICATION_FAILURE",
        callId,
        driverName,
        title,
        message,
      },
      title,
      body: message,
      level: "active",
    }, tokens));
    logger.info(`[PresenceAlert] ${message}`);
  } catch (error) {
    logger.error("[PresenceAlert] 알림 전송 오류", error);
  }
}

/**
 * Cloud Tasks task ID는 `[A-Za-z0-9_-]` 만 허용 → 그 외 문자는 _ 로 치환.
 */
function sanitizeTaskId(raw: string): string {
  return raw.replace(/[^A-Za-z0-9_-]/g, "_");
}

/**
 * ASSIGNED timeout task enqueue.
 * task ID = `${callId}_${assignedTsMs}` — 재배차 시 새 timestamp = 새 task (자연 분리)
 */
export async function enqueueAssignedTimeoutTask(
  payload: AssignedTimeoutPayload,
  delaySec: number,
): Promise<void> {
  const queue = getFunctions().taskQueue<AssignedTimeoutPayload>(
    `locations/${REGION}/functions/checkSingleCallAssignedTimeout`
  );
  const taskId = sanitizeTaskId(`${payload.callId}_${payload.expectedAssignedTimestampMs}`);
  await queue.enqueue(payload, {
    id: taskId,
    scheduleDelaySeconds: delaySec,
    dispatchDeadlineSeconds: 60,
  });
}

async function enqueueInProgressOfflineTask(
  payload: InProgressOfflinePayload,
  delaySec: number,
): Promise<void> {
  const queue = getFunctions().taskQueue<InProgressOfflinePayload>(
    `locations/${REGION}/functions/checkSingleCallInProgressOffline`
  );
  const taskId = sanitizeTaskId(`${payload.callId}_${payload.driverId}_${Date.now()}`);
  await queue.enqueue(payload, {
    id: taskId,
    scheduleDelaySeconds: delaySec,
    dispatchDeadlineSeconds: 60,
  });
}

// =========================================================================
// 단일 콜 timeout 복구 (폴링 대체)
// =========================================================================

/**
 * Cloud Tasks 발화: 단일 ASSIGNED 콜 timeout 처리.
 * 1 read (call doc) + 검증 → presence + WAITING 복귀 + FCM + acceptanceEvents.
 * obsolete task (수락 완료, 재배차, 취소 등) 은 silent no-op.
 */
export const checkSingleCallAssignedTimeout = onTaskDispatched<AssignedTimeoutPayload>(
  {
    region: REGION,
    retryConfig: { maxAttempts: 1 },
    rateLimits: { maxConcurrentDispatches: 50 },
  },
  async (req) => {
    const { provinceId, cityId, officeId, callId, expectedDriverId, expectedAssignedTimestampMs } = req.data;
    const officeRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId);
    const callRef = officeRef.collection("calls").doc(callId);
    const callSnap = await callRef.get();

    if (!callSnap.exists) {
      logger.info(`[AssignedTimeoutTask:${callId}] 콜 doc 없음 — silent no-op`);
      return;
    }
    const callData = callSnap.data()!;

    if (callData.status !== "ASSIGNED") {
      logger.info(`[AssignedTimeoutTask:${callId}] status=${callData.status} (ASSIGNED 아님) — silent no-op`);
      return;
    }
    if (callData.assignedDriverId !== expectedDriverId) {
      logger.info(`[AssignedTimeoutTask:${callId}] driver 변경 (expected=${expectedDriverId}, current=${callData.assignedDriverId}) — silent no-op`);
      return;
    }
    const currentTsMs = (callData.assignedTimestamp as Timestamp | undefined)?.toMillis();
    if (currentTsMs !== expectedAssignedTimestampMs) {
      logger.info(`[AssignedTimeoutTask:${callId}] timestamp 변경 (expected=${expectedAssignedTimestampMs}, current=${currentTsMs}) — silent no-op`);
      return;
    }

    const presenceStatus = await getDriverPresenceStatus(expectedDriverId);
    const isOffline = presenceStatus === "offline";

    // 기사 정보 조회 (이름 + FCM)
    const driversQuery = await officeRef
      .collection("designated_drivers")
      .where("authUid", "==", expectedDriverId)
      .limit(1)
      .get();
    const driverDocSnap = driversQuery.empty ? null : driversQuery.docs[0];
    const driverName = driverDocSnap?.data().name || "기사";
    const driverFcmToken = driverDocSnap?.data().fcmToken;

    // WAITING 복귀
    await callRef.update({
      status: "WAITING",
      assignedDriverId: FieldValue.delete(),
      assignedDriverName: FieldValue.delete(),
      assignedDriverPhone: FieldValue.delete(),
      assignedTimestamp: FieldValue.delete(),
      timeoutRecoveredAt: FieldValue.serverTimestamp(),
    });

    // 기사 doc status 복귀 (race 가드: ASSIGNED 일 때만)
    if (driverDocSnap && driverDocSnap.data().status === "ASSIGNED") {
      await driverDocSnap.ref.update({ status: "WAITING" });
    }

    // acceptanceEvents 기록
    try {
      await recordAcceptanceEvent({
        callId,
        assignedDriverId: expectedDriverId,
        provinceId,
        cityId,
        officeId,
        outcome: "timeout",
        assignedAt: callData.assignedTimestamp as Timestamp ?? Timestamp.now(),
        rejectReason: isOffline ? "assigned_timeout_offline" : "assigned_timeout_3min",
      });
    } catch (err) {
      logger.warn(`[AssignedTimeoutTask:${callId}] acceptanceEvents 기록 실패`, err);
    }

    // 기사 FCM (배차 해제 알림)
    if (driverFcmToken) {
      try {
        await admin.messaging().send(buildFcmPayload({
          data: {
            type: "call_cancelled",
            callId,
            cancelReason: "응답 시간 초과로 배차가 해제되었습니다",
          },
          title: "콜 배차 해제",
          body: "응답 시간 초과로 배차가 해제되었습니다",
          level: "active",
          ttlSeconds: 60,
        }, driverFcmToken));
      } catch (err) {
        logger.warn(`[AssignedTimeoutTask:${callId}] 기사 FCM 실패`, err);
      }
    }

    // 고객 FCM (앱 고객만)
    if (callData.isAppCustomer && callData.phoneNumber) {
      try {
        const customerDoc = await officeRef
          .collection("customerInfo")
          .doc(callData.phoneNumber)
          .get();
        const customerFcmToken = customerDoc.data()?.fcmToken;
        if (customerFcmToken) {
          await admin.messaging().send(buildFcmPayload({
            data: {
              type: "CALL_STATUS_UPDATE",
              callId,
              status: "WAITING",
              message: "기사 재배정 중입니다",
            },
            title: "콜 상태 변경",
            body: "기사 재배정 중입니다",
            level: "active",
            ttlSeconds: 60,
          }, customerFcmToken));
        }
      } catch (err) {
        logger.warn(`[AssignedTimeoutTask:${callId}] 고객 FCM 실패`, err);
      }
    }

    // 관리자 알림
    const alertTitle = isOffline ? "⚠️ 기사 오프라인" : "⚠️ 기사 미응답";
    const alertMsg = isOffline
      ? `${driverName} 기사 오프라인 — 자동 재배차 대기 중`
      : `${driverName} 기사 미응답 — 확인 필요`;
    await sendPresenceAlert(officeRef, callId, driverName, alertTitle, alertMsg);

    logger.info(`[AssignedTimeoutTask:${callId}] 타임아웃 복구 완료 (${isOffline ? "offline" : "online/bg"}) 기사: ${driverName}`);
  }
);

// =========================================================================
// IN_PROGRESS 운행 중 5분 연속 offline 감시
// =========================================================================

export const checkSingleCallInProgressOffline = onTaskDispatched<InProgressOfflinePayload>(
  {
    region: REGION,
    retryConfig: { maxAttempts: 1 },
    rateLimits: { maxConcurrentDispatches: 50 },
  },
  async (req) => {
    const { provinceId, cityId, officeId, callId, driverId } = req.data;
    const officeRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId);
    const callRef = officeRef.collection("calls").doc(callId);
    const callSnap = await callRef.get();
    if (!callSnap.exists) return;
    const callData = callSnap.data()!;

    if (callData.status !== "IN_PROGRESS") {
      logger.info(`[InProgressOfflineTask:${callId}] status=${callData.status} (IN_PROGRESS 아님) — no-op`);
      return;
    }
    if (callData.assignedDriverId !== driverId) {
      logger.info(`[InProgressOfflineTask:${callId}] driver 변경 — no-op`);
      return;
    }
    if (callData.inProgressAlertSent === true) {
      logger.info(`[InProgressOfflineTask:${callId}] 이미 알림 발송됨 — no-op`);
      return;
    }

    const presenceStatus = await getDriverPresenceStatus(driverId);
    if (presenceStatus !== "offline") {
      logger.info(`[InProgressOfflineTask:${callId}] presence 복귀(${presenceStatus}) — no-op`);
      return;
    }

    const driverName = callData.assignedDriverName || "기사";
    await sendPresenceAlert(
      officeRef,
      callId,
      driverName,
      "🚨 운행 중 연결 끊김",
      `${driverName} 기사 운행 중 5분째 연결 끊김 — 확인 필요`,
    );
    await callRef.update({ inProgressAlertSent: true });
    logger.warn(`[InProgressOfflineTask:${callId}] IN_PROGRESS 5분 offline 알림 발송: ${driverName}`);
  }
);

// =========================================================================
// RTDB presence trigger — 기사 끊김 즉시 감지
// =========================================================================

/**
 * `presence/drivers/{driverId}/status` 변경 트리거.
 * online/background → offline 전환 시:
 *   - ASSIGNED 콜: 즉시 WAITING 복귀
 *   - ACCEPTED/PREPARING: 관리자 알림 (1회 dedup)
 *   - IN_PROGRESS: 5분 deferred task enqueue
 */
export const onDriverPresenceOffline = onValueWritten(
  {
    region: REGION,
    ref: "/presence/drivers/{driverId}/status",
  },
  async (event) => {
    const driverId = event.params.driverId;
    const before = event.data.before.val() as string | null;
    const after = event.data.after.val() as string | null;

    // offline 전환만 처리
    if (after !== "offline") return;
    if (before === "offline") return; // 변경 없음

    logger.info(`[PresenceOffline:${driverId}] ${before} → offline`);

    // driverId(authUid) → office 좌표 + 기사 doc
    const driversQuery = await admin.firestore()
      .collectionGroup("designated_drivers")
      .where("authUid", "==", driverId)
      .limit(1)
      .get();
    if (driversQuery.empty) {
      logger.info(`[PresenceOffline:${driverId}] designated_drivers doc 없음 — skip`);
      return;
    }
    const driverDocSnap = driversQuery.docs[0];
    const driverData = driverDocSnap.data();
    const driverName = driverData.name || "기사";

    // path: provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{authUid}
    const driverPath = driverDocSnap.ref.path.split("/");
    const provinceId = driverPath[1];
    const cityId = driverPath[3];
    const officeId = driverPath[5];
    const officeRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId);

    // 해당 기사가 맡은 active 콜 조회
    const activeCallsSnap = await officeRef
      .collection("calls")
      .where("assignedDriverId", "==", driverId)
      .where("status", "in", ["ASSIGNED", "ACCEPTED", "PREPARING", "IN_PROGRESS"])
      .get();

    if (activeCallsSnap.empty) {
      logger.info(`[PresenceOffline:${driverId}] active 콜 없음 — skip`);
      return;
    }

    for (const callDocSnap of activeCallsSnap.docs) {
      const callData = callDocSnap.data();
      const callId = callDocSnap.id;
      const status = callData.status as string;

      if (status === "ASSIGNED") {
        // 즉시 WAITING 복귀
        await callDocSnap.ref.update({
          status: "WAITING",
          assignedDriverId: FieldValue.delete(),
          assignedDriverName: FieldValue.delete(),
          assignedDriverPhone: FieldValue.delete(),
          assignedTimestamp: FieldValue.delete(),
          timeoutRecoveredAt: FieldValue.serverTimestamp(),
        });
        if (driverData.status === "ASSIGNED") {
          await driverDocSnap.ref.update({ status: "WAITING" });
        }
        try {
          await recordAcceptanceEvent({
            callId,
            assignedDriverId: driverId,
            provinceId,
            cityId,
            officeId,
            outcome: "timeout",
            assignedAt: callData.assignedTimestamp as Timestamp ?? Timestamp.now(),
            rejectReason: "assigned_timeout_offline",
          });
        } catch (err) {
          logger.warn(`[PresenceOffline:${callId}] acceptanceEvents 기록 실패`, err);
        }
        // 기사 FCM
        const driverFcmToken = driverData.fcmToken;
        if (driverFcmToken) {
          try {
            await admin.messaging().send(buildFcmPayload({
              data: { type: "call_cancelled", callId, cancelReason: "응답 시간 초과로 배차가 해제되었습니다" },
              title: "콜 배차 해제",
              body: "응답 시간 초과로 배차가 해제되었습니다",
              level: "active",
              ttlSeconds: 60,
            }, driverFcmToken));
          } catch (err) {
            logger.warn(`[PresenceOffline:${callId}] 기사 FCM 실패`, err);
          }
        }
        // 고객 FCM
        if (callData.isAppCustomer && callData.phoneNumber) {
          try {
            const customerDoc = await officeRef.collection("customerInfo").doc(callData.phoneNumber).get();
            const customerFcmToken = customerDoc.data()?.fcmToken;
            if (customerFcmToken) {
              await admin.messaging().send(buildFcmPayload({
                data: { type: "CALL_STATUS_UPDATE", callId, status: "WAITING", message: "기사 재배정 중입니다" },
                title: "콜 상태 변경",
                body: "기사 재배정 중입니다",
                level: "active",
                ttlSeconds: 60,
              }, customerFcmToken));
            }
          } catch (err) {
            logger.warn(`[PresenceOffline:${callId}] 고객 FCM 실패`, err);
          }
        }
        await sendPresenceAlert(
          officeRef,
          callId,
          driverName,
          "⚠️ 기사 오프라인",
          `${driverName} 기사 오프라인 — 자동 재배차 대기 중`,
        );
        logger.info(`[PresenceOffline:${callId}] ASSIGNED 즉시 복구: ${driverName}`);
      } else if (status === "ACCEPTED" || status === "PREPARING") {
        if (callData.acceptedAlertSent === true) continue;
        await sendPresenceAlert(
          officeRef,
          callId,
          driverName,
          "🚨 수락 후 연결 끊김",
          `${driverName} 기사 수락 후 연결 끊김 — 확인 필요`,
        );
        await callDocSnap.ref.update({ acceptedAlertSent: true });
        logger.warn(`[PresenceOffline:${callId}] ${status} 오프라인 알림: ${driverName}`);
      } else if (status === "IN_PROGRESS") {
        if (callData.inProgressAlertSent === true) continue;
        // 5분 deferred task — 발화 시점에 status/presence 재검증
        try {
          await enqueueInProgressOfflineTask({
            provinceId, cityId, officeId, callId, driverId,
          }, 5 * 60);
          logger.info(`[PresenceOffline:${callId}] IN_PROGRESS 5분 deferred task enqueue`);
        } catch (err) {
          logger.warn(`[PresenceOffline:${callId}] IN_PROGRESS task enqueue 실패`, err);
        }
      }
    }
  }
);
