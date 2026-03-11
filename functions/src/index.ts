/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onDocumentWritten, onDocumentUpdated, onDocumentCreated} from "firebase-functions/v2/firestore";
import {onRequest, onCall} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";
import { Timestamp, FieldValue } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { processSharedCallPoints, processCustomerPointsOnComplete, refundCustomerPointsOnCancel } from "./handlers/points";
import { addCallToSettlementSession, autoFinalizeSettlementSessions, checkSettlementDiscrepancies, notifySettlementDiscrepancy, notifyDriversSettlementFinalized, notifyDriverSettlementResultHandler, getTodayWorkDate, getYesterdayWorkDate } from "./handlers/settlement";

// Firebase Admin SDK 초기화
admin.initializeApp({
  databaseURL: "https://calldetector-5d61e-default-rtdb.firebaseio.com",
});

// 데이터 구조를 명확히 하기 위한 인터페이스 정의
interface CallData {
    assignedDriverId?: string;
    callType?: string;
    sourceSharedCallId?: string;
    status?: string;
    isAppCustomer?: boolean;
    phoneNumber?: string;
    customerName?: string;
    customerPhone?: string;
    departure?: string;
    departure_set?: string;
    destination?: string;
    destination_set?: string;
    waypoints_set?: string;
    fare?: number;
    fare_set?: number;
    assignedDriverName?: string;
    assignedDriverPhone?: string;
    sourceOfficeId?: string;
    // 여기에 필요한 다른 필드들을 추가할 수 있습니다.
}

const DRIVER_COLLECTION_NAME = "designated_drivers";

// ========================================
// ACK 시스템 - 알림 상태 관리
// ========================================

interface NotificationStatus {
    id: string;
    type: string;  // call_assigned, STATUS_CHANGE, etc.
    targetId: string;  // 수신자 ID (driverId, managerId, customerId)
    targetType: "driver" | "manager" | "customer";
    callId?: string;
    officeId: string;
    provinceId: string;
    cityId: string;
    status: "pending" | "delivered" | "failed";
    sentAt: Timestamp;
    deliveredAt?: Timestamp;
    retryCount: number;
    lastRetryAt?: Timestamp;
    fcmToken: string;
    payload: any;
}

/**
 * 알림 상태 저장 (FCM 전송 시 호출)
 */
async function saveNotificationStatus(
    notificationId: string,
    type: string,
    targetId: string,
    targetType: "driver" | "manager" | "customer",
    officeId: string,
    provinceId: string,
    cityId: string,
    fcmToken: string,
    payload: any,
    callId?: string
): Promise<void> {
    try {
        const notificationData: NotificationStatus = {
            id: notificationId,
            type,
            targetId,
            targetType,
            callId,
            officeId,
            provinceId,
            cityId,
            status: "pending",
            sentAt: Timestamp.now(),
            retryCount: 0,
            fcmToken,
            payload
        };

        await admin.firestore()
            .collection("notifications")
            .doc(notificationId)
            .set(notificationData);

        logger.info(`[ACK] 알림 상태 저장: ${notificationId}, type=${type}, target=${targetId}`);
    } catch (error) {
        logger.error(`[ACK] 알림 상태 저장 실패: ${notificationId}`, error);
    }
}

/**
 * 알림 도착 ACK 처리 (앱에서 호출)
 */
export const acknowledgeNotification = onCall(
    { region: "asia-northeast3" },
    async (request) => {
        const { notificationId } = request.data;

        if (!notificationId) {
            throw new Error("notificationId is required");
        }

        try {
            const notificationRef = admin.firestore()
                .collection("notifications")
                .doc(notificationId);

            const doc = await notificationRef.get();
            if (!doc.exists) {
                logger.warn(`[ACK] 알림 없음: ${notificationId}`);
                return { success: false, error: "Notification not found" };
            }

            await notificationRef.update({
                status: "delivered",
                deliveredAt: Timestamp.now()
            });

            logger.info(`[ACK] 알림 도착 확인: ${notificationId}`);
            return { success: true };
        } catch (error) {
            logger.error(`[ACK] 알림 ACK 처리 실패: ${notificationId}`, error);
            return { success: false, error: String(error) };
        }
    }
);

/**
 * 미전달 알림 재전송 스케줄러 (10초마다 실행)
 */
export const retryPendingNotifications = onSchedule(
    {
        region: "asia-northeast3",
        schedule: "every 1 minutes",  // 최소 1분 간격 (Cloud Scheduler 제한)
        timeZone: "Asia/Seoul"
    },
    async () => {
        const tenSecondsAgo = Timestamp.fromMillis(
            Date.now() - 10000  // 10초 전
        );

        try {
            // pending 상태이고 10초 이상 지난 알림 조회
            const pendingNotifications = await admin.firestore()
                .collection("notifications")
                .where("status", "==", "pending")
                .where("sentAt", "<", tenSecondsAgo)
                .where("retryCount", "<", 2)  // 최대 2회 재시도
                .limit(50)
                .get();

            if (pendingNotifications.empty) {
                logger.info("[ACK] 재전송 대상 알림 없음");
                return;
            }

            logger.info(`[ACK] 재전송 대상: ${pendingNotifications.size}건`);

            const batch = admin.firestore().batch();
            const fcmPromises: Promise<any>[] = [];

            for (const doc of pendingNotifications.docs) {
                const notification = doc.data() as NotificationStatus;

                // FCM 재전송
                const fcmPromise = admin.messaging().send({
                    ...notification.payload,
                    token: notification.fcmToken
                }).then(() => {
                    logger.info(`[ACK] 재전송 성공: ${notification.id}`);
                }).catch((error) => {
                    logger.error(`[ACK] 재전송 실패: ${notification.id}`, error);
                });

                fcmPromises.push(fcmPromise);

                // 재시도 횟수 증가
                batch.update(doc.ref, {
                    retryCount: notification.retryCount + 1,
                    lastRetryAt: Timestamp.now()
                });
            }

            await Promise.all([batch.commit(), ...fcmPromises]);

        } catch (error) {
            logger.error("[ACK] 재전송 스케줄러 오류", error);
        }
    }
);

/**
 * 3회 실패 알림 처리 (콜매니저에 경고)
 */
export const handleFailedNotifications = onSchedule(
    {
        region: "asia-northeast3",
        schedule: "every 1 minutes",
        timeZone: "Asia/Seoul"
    },
    async () => {
        try {
            // 2회 재시도 후에도 pending인 알림 조회
            const failedNotifications = await admin.firestore()
                .collection("notifications")
                .where("status", "==", "pending")
                .where("retryCount", ">=", 2)
                .limit(50)
                .get();

            if (failedNotifications.empty) {
                return;
            }

            logger.info(`[ACK] 실패 알림 처리: ${failedNotifications.size}건`);

            for (const doc of failedNotifications.docs) {
                const notification = doc.data() as NotificationStatus;

                // Realtime DB에서 대상 Presence 확인
                let presenceStatus = "unknown";
                try {
                    const presencePath = `presence/${notification.targetType}s/${notification.targetId}`;
                    const presenceSnapshot = await admin.database().ref(presencePath).get();
                    presenceStatus = presenceSnapshot.val()?.status || "offline";
                } catch (e) {
                    logger.warn(`[ACK] Presence 조회 실패: ${notification.targetId}`);
                }

                // 콜매니저에게 경고 알림 전송
                if (notification.targetType === "driver" && notification.callId) {
                    await sendNotificationFailureAlert(
                        notification.provinceId,
                        notification.cityId,
                        notification.officeId,
                        notification.callId,
                        notification.targetId,
                        presenceStatus
                    );
                }

                // 상태를 failed로 업데이트
                await doc.ref.update({
                    status: "failed",
                    failureReason: `Presence: ${presenceStatus}`
                });
            }

        } catch (error) {
            logger.error("[ACK] 실패 알림 처리 오류", error);
        }
    }
);

/**
 * 콜매니저에게 알림 전달 실패 경고 전송
 */
async function sendNotificationFailureAlert(
    provinceId: string,
    cityId: string,
    officeId: string,
    callId: string,
    driverId: string,
    presenceStatus: string
): Promise<void> {
    try {
        // 기사 정보 조회
        const driverDoc = await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection(DRIVER_COLLECTION_NAME).doc(driverId)
            .get();

        const driverName = driverDoc.data()?.name || "기사";

        // 콜매니저 토큰 조회
        const managerTokensSnapshot = await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("managerTokens")
            .get();

        if (managerTokensSnapshot.empty) {
            logger.warn(`[ACK] 콜매니저 토큰 없음: ${officeId}`);
            return;
        }

        const tokens = managerTokensSnapshot.docs
            .map(doc => doc.data().fcmToken)
            .filter(token => token);

        if (tokens.length === 0) return;

        const statusMessage = presenceStatus === "offline"
            ? "앱 꺼짐 또는 네트워크 연결 끊김"
            : presenceStatus === "background"
            ? "앱이 백그라운드 상태"
            : "알림 전달 실패";

        const payload = {
            data: {
                type: "NOTIFICATION_FAILURE",
                callId: callId,
                driverId: driverId,
                driverName: driverName,
                presenceStatus: presenceStatus,
                message: `${driverName} 기사에게 알림 전달 실패: ${statusMessage}`,
                title: "⚠️ 알림 전달 실패"
            },
            android: {
                priority: "high" as const
            },
            tokens: tokens
        };

        await admin.messaging().sendEachForMulticast(payload);
        logger.info(`[ACK] 콜매니저에 실패 알림 전송: callId=${callId}, driver=${driverName}`);

    } catch (error) {
        logger.error("[ACK] 실패 알림 전송 오류", error);
    }
}

// ========================================
// 기존 함수들
// ========================================

export const oncallassigned = onDocumentWritten(
    {
        region: "asia-northeast3",
        document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
    },
    async (event: any) => {
        const {provinceId, cityId, officeId, callId} = event.params;

        // 1. 이벤트 데이터와 변경 후 데이터 존재 여부 확인 (가장 안전한 방법)
        if (!event.data || !event.data.after) {
            logger.info(`[${callId}] 이벤트 데이터가 없어 함수를 종료합니다.`);
            return;
        }

        const afterData = event.data.after.data() as CallData;

        // 문서가 삭제된 경우
        if (!event.data.after.exists) {
            logger.info(`[${callId}] 문서가 삭제되어 함수를 종료합니다.`);
            return;
        }

        const beforeData = event.data.before?.data() as CallData | undefined;

        // 2. assignedDriverId가 유효하게 할당/변경되었는지 확인
        // 공유콜의 경우 새 문서 생성 시에는 알림을 보내지 않음 (중복 알림 방지)
        const isSharedCall = afterData.callType === "SHARED";
        const isNewDocument = !beforeData;
        const isDriverChanged = beforeData && afterData.assignedDriverId !== beforeData.assignedDriverId;
        
        logger.info(`[${callId}] 알림 조건 확인: callType=${afterData.callType}, isSharedCall=${isSharedCall}, isNewDocument=${isNewDocument}, isDriverChanged=${isDriverChanged}, assignedDriverId=${afterData.assignedDriverId}, sourceSharedCallId=${afterData.sourceSharedCallId}`);
        
        // 공유콜이면서 새 문서인 경우 상세 로그
        if (isSharedCall && isNewDocument) {
            logger.info(`[${callId}] 공유콜 새 문서 생성 감지. 기사 배정 시 알림 전송됩니다.`);
        }
        
        const isDriverAssigned = afterData.assignedDriverId && (
            isNewDocument || // 새 문서 생성 시 (공유콜 포함)
            isDriverChanged // 기존 문서의 기사 변경 시
        );

        if (!isDriverAssigned || !afterData.assignedDriverId) {
            logger.info(`[${callId}] 기사 배정 변경사항이 없어 알림을 보내지 않습니다. assignedDriverId: ${afterData.assignedDriverId}, beforeAssignedDriverId: ${beforeData?.assignedDriverId}, isSharedCall: ${isSharedCall}, isNewDocument: ${isNewDocument}, isDriverAssigned: ${isDriverAssigned}`);
            return;
        }

        const driverId = afterData.assignedDriverId;
        logger.info(`[${callId}] 기사 [${driverId}]에게 알림 전송 시작. isNewDocument=${isNewDocument}, isDriverChanged=${isDriverChanged}`);

        try {
            // 3. 기사 문서에서 FCM 토큰 및 정보 가져오기
            const driverRef = admin.firestore()
                .collection("provinces").doc(provinceId)
                .collection("cities").doc(cityId)
                .collection("offices").doc(officeId)
                .collection(DRIVER_COLLECTION_NAME).doc(driverId);

            const driverDoc = await driverRef.get();
            if (!driverDoc.exists) {
                logger.error(`[${callId}] 기사 문서 [${driverId}]를 찾을 수 없습니다.`);
                return;
            }

            const driverData = driverDoc.data();
            const driverFcmToken = driverData?.fcmToken;
            const driverName = driverData?.name || "기사";
            const driverPhone = driverData?.phoneNumber || "";
            const vehicleNumber = driverData?.vehicleNumber || "";

            // 4. 기사에게 알림 전송 (Presence 확인 후)
            if (driverFcmToken) {
                // Presence 먼저 확인 (오프라인이면 즉시 콜매니저에 알림)
                let presenceStatus = "unknown";
                try {
                    const presencePath = `presence/drivers/${driverId}`;
                    const presenceSnapshot = await admin.database().ref(presencePath).get();
                    presenceStatus = presenceSnapshot.val()?.status || "offline";
                    logger.info(`[${callId}] 기사 [${driverId}] Presence 상태: ${presenceStatus}`);
                } catch (e) {
                    logger.warn(`[${callId}] Presence 조회 실패, FCM 전송 계속 진행`);
                }

                // 오프라인이면 즉시 콜매니저에 경고 (FCM 재전송 안 함)
                if (presenceStatus === "offline") {
                    logger.warn(`[${callId}] 기사 [${driverId}] 오프라인 상태 - 콜매니저에 즉시 알림`);
                    await sendNotificationFailureAlert(
                        provinceId,
                        cityId,
                        officeId,
                        callId,
                        driverId,
                        presenceStatus
                    );
                    // FCM도 보내봄 (혹시 모르니)
                }

                const notificationId = `${callId}_${driverId}_${Date.now()}`;
                const driverPayload = {
                    data: {
                        callId: callId,
                        notificationId: notificationId,  // ACK용 ID 추가
                        type: "call_assigned",
                        title: "🚨 새로운 콜 배정",
                        body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!"
                    },
                    android: {
                        priority: "high" as const,
                        ttl: 30000, // 30초 TTL
                    },
                    token: driverFcmToken,
                };

                // 알림 상태 저장 (ACK 추적용) - 오프라인이 아닐 때만
                if (presenceStatus !== "offline") {
                    await saveNotificationStatus(
                        notificationId,
                        "call_assigned",
                        driverId,
                        "driver",
                        officeId,
                        provinceId,
                        cityId,
                        driverFcmToken,
                        driverPayload,
                        callId
                    );
                }

                await admin.messaging().send(driverPayload);
                logger.info(`[${callId}] 기사 [${driverId}]에게 성공적으로 알림을 보냈습니다. notificationId=${notificationId}`);
            } else {
                logger.warn(`[${callId}] 기사 [${driverId}]의 FCM 토큰이 없습니다.`);
            }

            // 5. 고객에게도 기사 배정 알림 전송 (앱 고객만)
            const isAppCustomer = afterData.isAppCustomer || false;
            const customerPhone = afterData.phoneNumber;

            if (isAppCustomer && customerPhone) {
                logger.info(`[${callId}] 고객에게 기사 배정 알림 전송 시작 - phoneNumber: ${customerPhone}`);

                try {
                    // 고객 FCM 토큰 조회
                    const customerDoc = await admin.firestore()
                        .collection("provinces").doc(provinceId)
                        .collection("cities").doc(cityId)
                        .collection("offices").doc(officeId)
                        .collection("customerInfo")
                        .doc(customerPhone)
                        .get();

                    const customerFcmToken = customerDoc.data()?.fcmToken;
                    logger.info(`[${callId}] 고객 FCM 토큰: ${customerFcmToken}`);
                    if (!customerFcmToken) {
                        logger.warn(`[${callId}] 고객 FCM 토큰 없음: ${customerPhone}`);
                        return;
                    }

                    // 고객에게 기사 정보 포함한 알림 전송
                    const customerPayload = {
                        data: {
                            type: "DRIVER_ASSIGNED",
                            callId: callId,
                            driverName: driverName,
                            driverPhone: driverPhone,
                            vehicleNumber: vehicleNumber,
                            driverId: driverId
                        },
                        android: {
                            priority: "high" as const,
                            ttl: 60000
                        },
                        token: customerFcmToken
                    };

                    await admin.messaging().send(customerPayload);
                    logger.info(`[${callId}] 고객에게 기사 배정 알림 전송 완료: ${customerPhone}`);

                } catch (customerError) {
                    logger.error(`[${callId}] 고객 알림 전송 오류:`, customerError);
                }
            } else {
                logger.info(`[${callId}] 앱 고객이 아니거나 전화번호 없음 - 고객 알림 스킵. isAppCustomer: ${isAppCustomer}, phoneNumber: ${customerPhone}`);
            }

            // 6. 콜매니저에게도 배차 상태 변경 알림 전송
            try {
                const managerTokensSnapshot = await admin.firestore()
                    .collection("provinces").doc(provinceId)
                    .collection("cities").doc(cityId)
                    .collection("offices").doc(officeId)
                    .collection("managerTokens")
                    .get();

                if (!managerTokensSnapshot.empty) {
                    const managerTokens: string[] = [];
                    managerTokensSnapshot.forEach((doc) => {
                        const token = doc.data().fcmToken;
                        if (token) managerTokens.push(token);
                    });

                    if (managerTokens.length > 0) {
                        const managerPayload = {
                            data: {
                                type: "CALL_STATUS_UPDATE",
                                callId: callId,
                                status: "ASSIGNED",
                                assignedDriverId: driverId,
                                assignedDriverName: driverName,
                                customerName: afterData.customerName || "고객",
                                customerPhone: afterData.phoneNumber || "",
                                departure: afterData.departure || "",
                                destination: afterData.destination || "",
                                fare: (afterData.fare ?? 0).toString(),
                                provinceId: provinceId,
                                cityId: cityId,
                                officeId: officeId
                            },
                            tokens: managerTokens
                        };

                        const response = await admin.messaging().sendEachForMulticast(managerPayload);
                        logger.info(`[${callId}] 콜매니저 FCM 전송 완료 - 성공: ${response.successCount}, 실패: ${response.failureCount}`);
                    }
                }
            } catch (managerError) {
                logger.error(`[${callId}] 콜매니저 알림 전송 오류:`, managerError);
            }

        } catch (error) {
            logger.error(`[${callId}] 알림 전송 중 오류 발생:`, error);
        }
    }
);

// =============================
// 공유 콜이 OPEN -> CLAIMED 으로 변경될 때 트리거
// 1) 대상 사무실 calls 컬렉션에 복사
// 2) 포인트 +/- (10% 기본, 추후 환경변수화)
// 3) FCM 알림 양쪽 매니저에게 전송 (토큰 로직은 미구현 -> TODO)
// =============================
interface SharedCallData {
  status: string;
  departure?: string;
  destination?: string;
  fare?: number;
  sourceProvinceId: string;
  sourceCityId: string;
  sourceOfficeId: string;
  targetProvinceId: string;
  targetCityId: string;
  claimedOfficeId?: string;
  createdBy: string;
  claimedAt?: FirebaseFirestore.FieldValue | string;
  claimedDriverId?: string;
  phoneNumber?: string; // 새로 추가: 공유 콜 상세정보 전달용
  completedAt?: FirebaseFirestore.FieldValue | string; // 완료 시각
  destCallId?: string; // 복사된 콜의 ID
  cancelledByDriver?: boolean; // 기사가 취소했는지 여부
  originalCallId?: string; // 원본 콜 ID
  cancelReason?: string; // 취소 사유
  cancelledAt?: FirebaseFirestore.FieldValue | string; // 취소 시각
}

// =============================
// 새로운 공유 콜이 생성될 때 트리거
// 대상 지역의 모든 사무실 관리자에게 FCM 알림 전송
// =============================
/**
 * 새로운 콜 생성 시 콜매니저에 FCM 알림 전송
 * - 고객앱에서 콜 생성 시 자동으로 트리거
 * - Call Detector의 전화 호출과 동일한 팝업 표시
 */
export const sendNewCallNotification = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const {provinceId, cityId, officeId, callId} = event.params;

    if (!event.data) {
      logger.warn(`[new-call:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const callData = event.data.data();

    // fromCallDetector가 true여도 FCM 알림 전송 (화면 꺼진 상태에서 콜매니저 알림 필요)
    // 콜디텍터의 DispatchActivity와 중복되지만, Doze 모드에서 FCM만 도착하므로 필수
    if (callData.fromCallDetector === true) {
      logger.info(`[new-call:${callId}] Call Detector에서 생성한 콜 - FCM 알림도 전송`);
    }

    // 공유콜은 별도 처리
    if (callData.callType === "SHARED") {
      logger.info(`[new-call:${callId}] 공유콜 - 별도 함수에서 처리`);
      return;
    }

    // WAITING 상태가 아니면 알림 전송하지 않음
    if (callData.status !== "WAITING") {
      logger.info(`[new-call:${callId}] 상태가 'WAITING'이 아님 (${callData.status}) - FCM 알림 스킵`);
      return;
    }

    // 중복 콜 감지: 같은 사무실에서 10초 내 같은 phoneNumber의 다른 WAITING 콜이 있는지 확인
    if (callData.phoneNumber) {
      const now = Timestamp.now();
      const tenSecondsAgo = new Timestamp(now.seconds - 10, now.nanoseconds);

      const duplicateQuery = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("calls")
        .where("phoneNumber", "==", callData.phoneNumber)
        .where("status", "==", "WAITING")
        .where("createdAt", ">=", tenSecondsAgo)
        .get();

      // 자기 자신을 제외한 다른 WAITING 콜이 있으면 중복
      const otherDuplicates = duplicateQuery.docs.filter(doc => doc.id !== callId);
      if (otherDuplicates.length > 0) {
        logger.warn(`[new-call:${callId}] 중복 콜 감지! 같은 번호(${callData.phoneNumber})의 WAITING 콜이 이미 존재합니다. 기존 콜 ID: ${otherDuplicates[0].id}. 이 콜을 삭제합니다.`);
        try {
          await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("calls").doc(callId)
            .delete();
          logger.info(`[new-call:${callId}] 중복 콜 삭제 완료`);
        } catch (deleteError) {
          logger.error(`[new-call:${callId}] 중복 콜 삭제 실패:`, deleteError);
        }
        return;
      }
    }

    logger.info(`[new-call:${callId}] 새 콜 알림 전송 시작`);
    logger.info(`[new-call:${callId}] 고객: ${callData.customerName || callData.phoneNumber}, 위치: ${callData.customerAddress}`);

    try {
      // 해당 사무실의 관리자 FCM 토큰 조회
      const adminsSnapshot = await admin.firestore()
        .collection("admins")
        .where("associatedProvinceId", "==", provinceId)
        .where("associatedCityId", "==", cityId)
        .where("associatedOfficeId", "==", officeId)
        .get();

      const tokens: string[] = [];
      adminsSnapshot.forEach((doc) => {
        const adminData = doc.data();
        if (adminData.fcmToken) {
          tokens.push(adminData.fcmToken);
        }
      });

      if (tokens.length === 0) {
        logger.warn(`[new-call:${callId}] FCM 토큰을 가진 관리자가 없습니다.`);
        return;
      }

      logger.info(`[new-call:${callId}] ${tokens.length}명의 관리자에게 알림 전송`);

      // FCM 메시지 구성
      const message: admin.messaging.MulticastMessage = {
        data: {
          type: "NEW_CALL",
          callId: callId,
          customerName: callData.customerName || callData.phoneNumber || "신규 고객",
          customerPhone: callData.phoneNumber || "",
          pickupLocation: callData.customerAddress || callData.departure || "위치 미확인",
          provinceId: provinceId,
          cityId: cityId,
          officeId: officeId,
          fromCallDetector: String(callData.fromCallDetector === true),
          fromCallManager: String(callData.fromCallManager === true),
        },
        android: {
          priority: "high",
        },
        tokens,
      };

      const response = await admin.messaging().sendEachForMulticast(message);
      logger.info(`[new-call:${callId}] FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);

      // 실패한 토큰 정리
      const batch = admin.firestore().batch();
      let invalidTokensFound = 0;

      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const error = resp.error;
          logger.warn(`[new-call:${callId}] 토큰 ${idx} 전송 실패: ${error?.message}`);

          if (error?.code === "messaging/registration-token-not-registered" ||
              error?.code === "messaging/invalid-registration-token" ||
              error?.message?.includes("Requested entity was not found")) {
            const invalidToken = tokens[idx];
            logger.info(`[new-call:${callId}] 무효한 FCM 토큰 발견, 자동 정리 예정`);

            adminsSnapshot.docs.forEach((doc) => {
              const adminData = doc.data();
              if (adminData.fcmToken === invalidToken) {
                batch.update(doc.ref, {fcmToken: FieldValue.delete()});
                invalidTokensFound++;
              }
            });
          }
        }
      });

      if (invalidTokensFound > 0) {
        try {
          await batch.commit();
          logger.info(`[new-call:${callId}] ${invalidTokensFound}개의 무효한 FCM 토큰 자동 정리 완료`);
        } catch (batchError) {
          logger.error(`[new-call:${callId}] 무효한 토큰 정리 중 오류:`, batchError);
        }
      }
    } catch (error) {
      logger.error(`[new-call:${callId}] 알림 전송 중 오류 발생:`, error);
    }
  }
);

export const onSharedCallCreated = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
  },
  async (event: any) => {
    const callId = event.params.callId;
    
    if (!event.data) {
      logger.warn(`[shared-created:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const sharedCallData = event.data.data() as SharedCallData;
    
    if (!sharedCallData || sharedCallData.status !== "OPEN") {
      logger.info(`[shared-created:${callId}] OPEN 상태가 아니므로 알림을 보내지 않습니다. Status: ${sharedCallData?.status}`);
      return;
    }

    logger.info(`[shared-created:${callId}] 새로운 공유콜 생성됨. 대상 지역 관리자들에게 알림 전송 시작.`);
    logger.info(`[shared-created:${callId}] 공유콜 데이터: sourceProvinceId=${sharedCallData.sourceProvinceId}, sourceCityId=${sharedCallData.sourceCityId}, sourceOfficeId=${sharedCallData.sourceOfficeId}, targetProvinceId=${sharedCallData.targetProvinceId}, targetCityId=${sharedCallData.targetCityId}`);

    try {
      // 대상 지역의 모든 관리자 FCM 토큰 조회 (원본 사무실 제외)
      const adminQuery = await admin
        .firestore()
        .collection("admins")
        .where("associatedProvinceId", "==", sharedCallData.targetProvinceId)
        .where("associatedCityId", "==", sharedCallData.targetCityId)
        .get();

      const tokens: string[] = [];
      adminQuery.docs.forEach((doc) => {
        const adminData = doc.data();
        logger.info(`[shared-created:${callId}] 관리자 확인: provinceId=${adminData.associatedProvinceId}, cityId=${adminData.associatedCityId}, officeId=${adminData.associatedOfficeId}, sourceOfficeId=${sharedCallData.sourceOfficeId}`);
        
        // 원본 사무실은 제외 (sourceOfficeId와 동일한 사무실 제외)
        if (adminData.associatedOfficeId === sharedCallData.sourceOfficeId) {
          logger.info(`[shared-created:${callId}] ⛔ 원본 사무실 제외: ${adminData.associatedOfficeId} (sourceOfficeId: ${sharedCallData.sourceOfficeId})`);
          return; // 다음 관리자로 넘어감
        }
        
        
        if (adminData.fcmToken) {
          // 중복 토큰 방지
          if (!tokens.includes(adminData.fcmToken)) {
            tokens.push(adminData.fcmToken);
          } else {
          }
        } else {
          logger.warn(`[shared-created:${callId}] ⚠️ FCM 토큰 없음: ${adminData.associatedOfficeId}`);
        }
      });

      logger.info(`[shared-created:${callId}] 알림 대상: ${tokens.length}명의 관리자`);

      if (tokens.length === 0) {
        logger.warn(`[shared-created:${callId}] 알림을 보낼 관리자 토큰이 없습니다.`);
        return;
      }

      // Data-only FCM 메시지 - 앱에서 커스텀 알림 생성
      const message: admin.messaging.MulticastMessage = {
        data: {
          type: "NEW_SHARED_CALL",
          sharedCallId: callId,
          departure: sharedCallData.departure || "",
          destination: sharedCallData.destination || "",
          fare: (sharedCallData.fare || 0).toString(),
          callType: (sharedCallData as any).callType || "",
          phoneNumber: sharedCallData.phoneNumber || "",
          // 앱에서 알림 생성용 데이터
          title: "🔄 새로운 공유콜!",
          body: `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"}`,
          customMessage: `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"}\n요금: ${sharedCallData.fare || 0}원\n📞 ${sharedCallData.phoneNumber || "전화번호"}`,
        },
        android: {
          priority: "high", // 시스템을 깨우기 위해 필수
        },
        tokens,
      };

      // 🚨 실제 전송되는 페이로드 확인
      
      const response = await admin.messaging().sendEachForMulticast(message);
      logger.info(`[shared-created:${callId}] FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);

      // 실패한 토큰들 로그 및 자동 정리
      const batch = admin.firestore().batch();
      let invalidTokensFound = 0;
      
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const error = resp.error;
          logger.warn(`[shared-created:${callId}] 토큰 ${idx} 전송 실패: ${error?.message}`);
          
          // 무효한 토큰인 경우 (만료, 등록 취소 등)
          if (error?.code === 'messaging/registration-token-not-registered' || 
              error?.code === 'messaging/invalid-registration-token' ||
              error?.message?.includes('Requested entity was not found')) {
            
            const invalidToken = tokens[idx];
            logger.info(`[shared-created:${callId}] 무효한 FCM 토큰 발견, 자동 정리 예정: ${invalidToken?.substring(0, 20)}...`);
            
            // 해당 토큰을 가진 관리자 문서에서 fcmToken 필드 제거
            adminQuery.docs.forEach((doc) => {
              const adminData = doc.data();
              if (adminData.fcmToken === invalidToken) {
                batch.update(doc.ref, { fcmToken: FieldValue.delete() });
                invalidTokensFound++;
                logger.info(`[shared-created:${callId}] 관리자 ${adminData.associatedOfficeId}의 무효한 토큰 제거 예정`);
              }
            });
          }
        }
      });
      
      // 배치 업데이트 실행
      if (invalidTokensFound > 0) {
        try {
          await batch.commit();
          logger.info(`[shared-created:${callId}] ${invalidTokensFound}개의 무효한 FCM 토큰 자동 정리 완료`);
        } catch (batchError) {
          logger.error(`[shared-created:${callId}] 무효한 토큰 정리 중 오류:`, batchError);
        }
      }

    } catch (error) {
      logger.error(`[shared-created:${callId}] 알림 전송 중 오류 발생:`, error);
    }
  }
);

/**
 * 고객에게 사무실 마감 알림 전송
 * shared_calls 문서 생성 시 고객 앱에 FCM 알림 전송
 */
export const notifyCustomerOnOfficeClosed = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
  },
  async (event: any) => {
    const callId = event.params.callId;

    if (!event.data) {
      logger.warn(`[customer-closed:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const sharedCallData = event.data.data() as SharedCallData;

    if (!sharedCallData || sharedCallData.status !== "OPEN") {
      logger.info(`[customer-closed:${callId}] OPEN 상태가 아니므로 고객 알림을 보내지 않습니다. Status: ${sharedCallData?.status}`);
      return;
    }

    const phoneNumber = sharedCallData.phoneNumber;
    if (!phoneNumber) {
      logger.warn(`[customer-closed:${callId}] 전화번호가 없어 고객 알림을 보낼 수 없습니다.`);
      return;
    }

    const sourceProvinceId = sharedCallData.sourceProvinceId;
    const sourceCityId = sharedCallData.sourceCityId;
    const sourceOfficeId = sharedCallData.sourceOfficeId;

    logger.info(`[customer-closed:${callId}] 사무실 마감으로 인한 공유콜 생성. 고객에게 알림 전송 시작: ${phoneNumber}`);

    try {
      // 원본 사무실의 customerInfo에서 고객 FCM 토큰 조회
      const customerDoc = await admin.firestore()
        .collection("provinces").doc(sourceProvinceId)
        .collection("cities").doc(sourceCityId)
        .collection("offices").doc(sourceOfficeId)
        .collection("customerInfo")
        .doc(phoneNumber)
        .get();

      if (!customerDoc.exists) {
        logger.info(`[customer-closed:${callId}] customerInfo에 등록되지 않은 고객: ${phoneNumber}`);
        return;
      }

      const fcmToken = customerDoc.data()?.fcmToken;
      if (!fcmToken) {
        logger.info(`[customer-closed:${callId}] 고객 FCM 토큰 없음 (앱 미설치 또는 미가입): ${phoneNumber}`);
        return;
      }

      // FCM 알림 전송
      const message: admin.messaging.Message = {
        data: {
          type: "OFFICE_CLOSED",
          sharedCallId: callId,
          phoneNumber: phoneNumber,
          title: "🏢 사무실 마감 안내",
          body: "사무실이 마감되어 잠시후 운행가능한 다른 사무실과 연결해 드리겠습니다.",
        },
        android: {
          priority: "high",
          ttl: 300000, // 5분
        },
        token: fcmToken
      };

      await admin.messaging().send(message);
      logger.info(`[customer-closed:${callId}] 고객에게 사무실 마감 알림 전송 완료: ${phoneNumber}`);

    } catch (error: any) {
      // FCM 토큰이 무효한 경우 자동 정리
      if (error?.code === 'messaging/registration-token-not-registered' ||
          error?.code === 'messaging/invalid-registration-token') {
        logger.warn(`[customer-closed:${callId}] 무효한 FCM 토큰 발견, 자동 정리: ${phoneNumber}`);

        try {
          await admin.firestore()
            .collection("provinces").doc(sourceProvinceId)
            .collection("cities").doc(sourceCityId)
            .collection("offices").doc(sourceOfficeId)
            .collection("customerInfo")
            .doc(phoneNumber)
            .update({ fcmToken: FieldValue.delete() });

          logger.info(`[customer-closed:${callId}] 무효한 FCM 토큰 제거 완료: ${phoneNumber}`);
        } catch (deleteError) {
          logger.error(`[customer-closed:${callId}] 토큰 제거 중 오류:`, deleteError);
        }
      } else {
        logger.error(`[customer-closed:${callId}] 알림 전송 중 오류:`, error);
      }
    }
  }
);

export const onSharedCallClaimed = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
  },
  async (event: any) => {
    const callId = event.params.callId;

    if (!event.data) {
      logger.warn(`[shared:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const beforeData = event.data.before.data() as SharedCallData | undefined;
    const afterData = event.data.after.data() as SharedCallData;

    if (!beforeData || !afterData) {
      logger.warn(`[shared:${callId}] before/after 데이터 누락`);
      return;
    }

    // CLAIMED -> OPEN 인지 확인 (기사가 공유콜 취소)
    if (beforeData.status === "CLAIMED" && afterData.status === "OPEN" && afterData.cancelledByDriver) {
      logger.info(`[shared:${callId}] 공유 콜이 기사에 의해 취소되었습니다. 원본 사무실 콜 복구 시작.`);
      
      try {
        await admin.firestore().runTransaction(async (tx) => {
          // 원본 사무실의 콜을 WAITING 상태로 복구
          const originalCallRef = admin
            .firestore()
            .collection("provinces")
            .doc(afterData.sourceProvinceId)
            .collection("cities")
            .doc(afterData.sourceCityId)
            .collection("offices")
            .doc(afterData.sourceOfficeId)
            .collection("calls")
            .doc(callId);
          
          tx.update(originalCallRef, {
            status: "HOLD",
            callType: null,
            sourceSharedCallId: null,
            assignedDriverId: null,
            assignedDriverName: null,
            assignedDriverPhone: null,
            cancelReason: `공유콜 취소됨: ${afterData.cancelReason || "사유 없음"}`,
            departure_set: null,
            destination_set: null,
            fare_set: null,
            updatedAt: FieldValue.serverTimestamp()
          });
          
          logger.info(`[shared:${callId}] 원본 사무실 콜이 HOLD 상태로 복구되었습니다.`);
        });
        
        // 원본 사무실 관리자들에게 알림 전송
        const adminQuery = await admin
          .firestore()
          .collection("admins")
          .where("associatedProvinceId", "==", afterData.sourceProvinceId)
          .where("associatedCityId", "==", afterData.sourceCityId)
          .where("associatedOfficeId", "==", afterData.sourceOfficeId)
          .get();
        
        const tokens: string[] = [];
        adminQuery.docs.forEach((doc) => {
          const adminData = doc.data();
          if (adminData.fcmToken) {
            tokens.push(adminData.fcmToken);
          }
        });
        
        if (tokens.length > 0) {
          const message: admin.messaging.MulticastMessage = {
            // notification 필드 완전 제거 - Android가 자동 알림 생성하지 않도록
            data: {
              type: "SHARED_CALL_CANCELLED",
              callId: callId,
              cancelReason: afterData.cancelReason || "",
              // 알림 제목과 내용을 완전히 다른 키로 전송
              alertTitle: "공유콜이 취소되었습니다",
              alertMessage: `${afterData.cancelReason || "사유 없음"} - 콜이 대기 상태로 복구되었습니다.`,
            },
            android: {
              priority: "high",
              // notification 필드 완전 제거
            },
            tokens,
          };
          
          const response = await admin.messaging().sendEachForMulticast(message);
          logger.info(`[shared:${callId}] 원본 사무실에 취소 알림 전송 완료. 성공: ${response.successCount}`);
        }
      } catch (error) {
        logger.error(`[shared:${callId}] 원본 콜 복구 중 오류:`, error);
      }
      return;
    }
    
    // OPEN -> CLAIMED 인지 확인 (콜 복사만, 포인트 처리 없음)
    if (beforeData.status === "OPEN" && afterData.status === "CLAIMED") {
      logger.info(`[shared:${callId}] 공유 콜이 CLAIMED 되었습니다. 대상사무실로 복사 시작.`);
      logger.info(`[shared:${callId}] afterData.claimedDriverId=${afterData.claimedDriverId}`);

      logger.info(`[shared:${callId}] assignedDriverId=${afterData.claimedDriverId}`);

      // 트랜잭션 외부에서 변수 선언
      let assignedDriverId: string | null = null;
      let assignedDriverName: string | null = null;
      let assignedDriverPhone: string | null = null;
      let driverSnap: any = null;

      try {
        await admin.firestore().runTransaction(async (tx) => {
          // ========== 모든 읽기 작업을 먼저 수행 ==========
          
          // 1. 기사 정보 읽기 (배정된 기사가 있을 경우)
          driverSnap = afterData.claimedDriverId ? await tx.get(admin.firestore()
            .collection("provinces").doc(afterData.targetProvinceId)
            .collection("cities").doc(afterData.targetCityId)
            .collection("offices").doc(afterData.claimedOfficeId!)
            .collection("designated_drivers").doc(afterData.claimedDriverId)) : null;

          // 2. 원본 콜 문서 존재 여부 확인
          const sourceCallRef = admin
            .firestore()
            .collection("provinces")
            .doc(afterData.sourceProvinceId)
            .collection("cities")
            .doc(afterData.sourceCityId)
            .collection("offices")
            .doc(afterData.sourceOfficeId)
            .collection("calls")
            .doc(callId);
          const sourceCallSnap = await tx.get(sourceCallRef);

          // ========== 읽기 결과 처리 ==========
          
          const driverData = driverSnap ? driverSnap.data() : undefined;
          assignedDriverId = driverData ? driverData.authUid : null; // authUid 사용
          assignedDriverName = driverData ? driverData.name : null;
          assignedDriverPhone = driverData ? driverData.phoneNumber : null;

          logger.info(`[shared:${callId}] driverDocId=${afterData.claimedDriverId}, assignedDriverId(authUid)=${assignedDriverId}`);

          // ========== 모든 쓰기 작업 수행 ==========
          
          // 1. 대상 사무실에 콜 복사
          const destCallsRef = admin
            .firestore()
            .collection("provinces")
            .doc(afterData.targetProvinceId)
            .collection("cities")
            .doc(afterData.targetCityId)
            .collection("offices")
            .doc(afterData.claimedOfficeId!)
            .collection("calls")
            .doc(callId);

          // 공유콜 생성 - 기사 배정이 있으면 바로 ASSIGNED 상태로 생성
          const callDoc: any = {
            ...afterData,
            status: assignedDriverId ? "ASSIGNED" : "WAITING",
            departure_set: afterData.departure ?? null,
            destination_set: afterData.destination ?? null,
            fare_set: afterData.fare ?? null,
            callType: "SHARED",
            sourceSharedCallId: callId,
            createdAt: FieldValue.serverTimestamp(),
            // 기사 배정이 있다면 바로 포함
            ...(assignedDriverId && {
              assignedDriverId: assignedDriverId,
              assignedDriverName: assignedDriverName,
              assignedDriverPhone: assignedDriverPhone,
              assignedTimestamp: FieldValue.serverTimestamp(),
            })
          };
          tx.set(destCallsRef, callDoc);
          
          // assignedDriverId가 있다면 별도 업데이트로 처리 (중복 알림 방지)
          if (assignedDriverId) {
            logger.info(`[shared-claimed:${callId}] 기사 배정을 별도 업데이트로 처리: ${assignedDriverId}`);
            // 트랜잭션 외부에서 처리하도록 변경 필요
          }

          // 2. 드라이버 상태 업데이트는 트랜잭션 외부에서 처리

          // 3. 원본 콜 문서 업데이트 (존재하는 경우에만)
          if (sourceCallSnap.exists) {
            // 원본 콜은 일단 CLAIMED 상태로 업데이트 (기사 배정은 나중에)
            const sourceCallUpdates: any = {
              status: "CLAIMED", // 수락됨 상태
              claimedOfficeId: afterData.claimedOfficeId,
              assignedDriverName: `수락됨 (${afterData.claimedOfficeId})`,
              updatedAt: FieldValue.serverTimestamp(),
            };
            tx.update(sourceCallRef, sourceCallUpdates);
            logger.info(`[shared:${callId}] 원본 콜을 수락됨 상태로 업데이트 완료`);
          } else {
            logger.warn(`[shared:${callId}] 원본 콜 문서가 존재하지 않습니다. 건너뜁니다.`);
          }

          //   c) 공유콜 문서 processed 플래그 수정 → 트랜잭션 외부로 이동하여
          //      "읽기 후 쓰기" 제약을 피함 (트랜잭션 내부에 포함하면
          //      사전에 해당 문서를 읽지 않았기 때문에 Firestore가
          //      암묵적 read 를 삽입하며 오류가 발생한다)
        });

        logger.info(`[shared:${callId}] 콜 복사 및 포인트 처리 완료. 대상 사무실에 WAITING 상태로 생성됨.`);

        // ---- 기사 상태 업데이트 (기사 배정이 있는 경우만) ----
        if (assignedDriverId && driverSnap?.exists) {
          try {
            logger.info(`[shared:${callId}] 기사 상태 업데이트: ${assignedDriverId}`);
            await driverSnap.ref.update({ status: "ASSIGNED" });
            logger.info(`[shared:${callId}] 기사 상태 업데이트 완료: ${assignedDriverId}`);
          } catch (assignErr) {
            logger.error(`[shared:${callId}] 기사 상태 업데이트 실패`, assignErr);
          }
        }

        // ---- 공유콜 문서 processed 플래그 업데이트 (트랜잭션 외부) ----
        try {
          await event.data.after.ref.update({ processed: true });
          logger.debug(`[shared:${callId}] shared_calls 문서 processed 플래그 업데이트 완료.`);
        } catch (updateErr) {
          logger.error(`[shared:${callId}] processed 플래그 업데이트 실패`, updateErr);
        }

        // ---- FCM 알림 전송 ----
        try {
          const adminColl = admin.firestore().collection("admins");

          // 원본 사무실 관리자 토큰
          const srcSnap = await adminColl
            .where("associatedProvinceId", "==", afterData.sourceProvinceId)
            .where("associatedCityId", "==", afterData.sourceCityId)
            .where("associatedOfficeId", "==", afterData.sourceOfficeId)
            .get();

          // 수락 사무실 관리자 토큰
          const tgtSnap = await adminColl
            .where("associatedProvinceId", "==", afterData.targetProvinceId)
            .where("associatedCityId", "==", afterData.targetCityId)
            .where("associatedOfficeId", "==", afterData.claimedOfficeId)
            .get();

          const tokens: string[] = [];
          srcSnap.forEach((doc) => {
            const t = doc.data().fcmToken;
            if (t) tokens.push(t);
          });
          tgtSnap.forEach((doc) => {
            const t = doc.data().fcmToken;
            if (t) tokens.push(t);
          });

          if (tokens.length > 0) {
            const msg: admin.messaging.MulticastMessage = {
              // notification 필드 완전 제거 - Android가 자동 알림 생성하지 않도록
              data: {
                sharedCallId: callId,
                type: "SHARED_CALL_CLAIMED",
                // 알림 제목과 내용을 data로 전송
                alertTitle: "공유 콜 수락됨",
                alertMessage: `${afterData.departure ?? "출발"} → ${afterData.destination ?? "도착"} / 요금 ${afterData.fare ?? 0}원`,
              },
              android: {
                priority: "high",
                // notification 필드 완전 제거
              },
              tokens,
            };

            const resp = await admin.messaging().sendEachForMulticast(msg);
            logger.info(`[shared:${callId}] FCM sendEachForMulticast done. Success: ${resp.successCount}, Failure: ${resp.failureCount}`);
          } else {
            logger.info(`[shared:${callId}] 알림을 보낼 토큰이 없습니다.`);
          }
        } catch (fcmErr) {
          logger.error(`[shared:${callId}] FCM 전송 오류`, fcmErr);
        }

      } catch (err) {
        logger.error(`[shared:${callId}] 트랜잭션 오류`, err);
      }
    }
    
    // CLAIMED -> OPEN 인지 확인 (기사가 취소한 경우)
    else if (beforeData.status === "CLAIMED" && afterData.status === "OPEN") {
      logger.info(`[shared:${callId}] 공유 콜이 취소되어 OPEN으로 되돌려졌습니다.`);
      
      try {
        // 복사된 콜이 있다면 삭제 (선택사항 - HOLD 상태로 둘 수도 있음)
        if (beforeData.claimedOfficeId) {
          const copiedCallRef = admin
            .firestore()
            .collection("provinces")
            .doc(afterData.targetProvinceId)
            .collection("cities")
            .doc(afterData.targetCityId)
            .collection("offices")
            .doc(beforeData.claimedOfficeId)
            .collection("calls")
            .doc(callId);
            
          const copiedCallSnap = await copiedCallRef.get();
          if (copiedCallSnap.exists) {
            const copiedCallData = copiedCallSnap.data();
            logger.info(`[shared:${callId}] 복사된 콜 상태: ${copiedCallData?.status}`);
            
            // HOLD 상태인 경우에만 삭제 (이미 진행 중인 콜은 건드리지 않음)
            if (copiedCallData?.status === "HOLD") {
              await copiedCallRef.delete();
              logger.info(`[shared:${callId}] HOLD 상태의 복사된 콜을 삭제했습니다.`);
            }
          }
        }
        
        logger.info(`[shared:${callId}] 공유콜 취소 처리 완료.`);
      } catch (err) {
        logger.error(`[shared:${callId}] 공유콜 취소 처리 오류`, err);
      }
    }
  }
);

// 공유콜이 기사에 의해 취소될 때 처리하는 함수
export const onSharedCallCancelledByDriver = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { callId } = event.params;

    if (!event.data) {
      logger.warn(`[call-cancelled:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      logger.warn(`[call-cancelled:${callId}] before/after 데이터 누락`);
      return;
    }

    logger.info(`[call-cancelled:${callId}] 함수 시작. callType: ${afterData.callType}, status: ${afterData.status}, cancelledByDriver: ${afterData.cancelledByDriver}, sourceSharedCallId: ${afterData.sourceSharedCallId}`);

    // 공유콜이 기사에 의해 취소되었는지 확인
    if (afterData.callType === "SHARED" && 
        afterData.status === "CANCELLED_BY_DRIVER" && 
        afterData.cancelledByDriver === true &&
        afterData.sourceSharedCallId) {
      
      logger.info(`[call-cancelled:${callId}] 공유콜이 기사에 의해 취소되었습니다. 원본 사무실 복구 시작.`);
      
      try {
        const sourceSharedCallId = afterData.sourceSharedCallId;
        const sharedCallRef = admin.firestore().collection("shared_calls").doc(sourceSharedCallId);
        
        // shared_calls 정보 가져오기
        const sharedCallSnap = await sharedCallRef.get();
        if (!sharedCallSnap.exists) {
          logger.error(`[call-cancelled:${callId}] 원본 shared_calls 문서를 찾을 수 없습니다.`);
          return;
        }
        
        const sharedCallData = sharedCallSnap.data() as SharedCallData;
        const originalCallId = (sharedCallData as any).originalCallId;
        
        logger.info(`[call-cancelled:${callId}] shared_calls 정보: sourceProvinceId=${sharedCallData.sourceProvinceId}, sourceCityId=${sharedCallData.sourceCityId}, sourceOfficeId=${sharedCallData.sourceOfficeId}, originalCallId=${originalCallId}`);
        
        if (!originalCallId) {
          // Detector가 직접 생성한 공유콜 - originalCallId가 없으므로 원본 복구 스킵
          logger.info(`[call-cancelled:${callId}] Detector 생성 공유콜 - 원본 없으므로 복구 스킵. shared_calls 문서만 삭제합니다.`);
          await sharedCallRef.delete();
          logger.info(`[call-cancelled:${callId}] shared_calls 문서 삭제 완료 (Detector 생성 공유콜)`);
        } else {
          await admin.firestore().runTransaction(async (tx) => {
            // 원본 사무실의 콜 문서 레퍼런스 (originalCallId 사용!)
            const originalCallRef = admin.firestore()
              .collection("provinces").doc(sharedCallData.sourceProvinceId)
              .collection("cities").doc(sharedCallData.sourceCityId)
              .collection("offices").doc(sharedCallData.sourceOfficeId)
              .collection("calls").doc(originalCallId);

            // 원본 콜 문서 존재 여부 확인
            const originalCallSnap = await tx.get(originalCallRef);

            // shared_calls는 삭제 (원사무실에서 다시 공유 여부 결정)
            tx.delete(sharedCallRef);

            // 원본 콜을 HOLD 상태로 복구 (존재하는 경우에만)
            if (originalCallSnap.exists) {
              const originalCallData = originalCallSnap.data();
              logger.info(`[call-cancelled:${callId}] 원본 콜 현재 상태: ${originalCallData?.status}`);

              const updateData = {
                status: "HOLD", // 공유콜 취소 시 보류 상태로 변경
                callType: null,
                sourceSharedCallId: null,
                assignedDriverId: null,
                assignedDriverName: null,
                assignedDriverPhone: null,
                departure_set: null,
                destination_set: null,
                fare_set: null,
                cancelReason: `공유콜 취소됨: ${afterData.cancelReason || "사유 없음"}`,
                updatedAt: FieldValue.serverTimestamp()
              };

              tx.update(originalCallRef, updateData);
              logger.info(`[call-cancelled:${callId}] 원본 콜을 HOLD 상태로 복구 완료. Path: ${originalCallRef.path}`);
            } else {
              logger.warn(`[call-cancelled:${callId}] 원본 콜 문서가 존재하지 않습니다. Path: ${originalCallRef.path}`);
            }

            logger.info(`[call-cancelled:${callId}] shared_calls 초기화 완료`);
          });
        }
        
        // 원본 사무실 관리자들에게 FCM 알림 전송 (팝업 포함)
        const adminQuery = await admin
          .firestore()
          .collection("admins")
          .where("associatedProvinceId", "==", sharedCallData.sourceProvinceId)
          .where("associatedCityId", "==", sharedCallData.sourceCityId)
          .where("associatedOfficeId", "==", sharedCallData.sourceOfficeId)
          .get();

        const tokens: string[] = [];
        adminQuery.docs.forEach((doc) => {
          const adminData = doc.data();
          if (adminData.fcmToken) {
            tokens.push(adminData.fcmToken);
          }
        });

        if (tokens.length > 0) {
          const message: admin.messaging.MulticastMessage = {
            // notification 필드 완전 제거 - Android가 자동 알림 생성하지 않도록
            data: {
              type: "SHARED_CALL_CANCELLED_POPUP",
              sharedCallId: sourceSharedCallId,
              callId: sourceSharedCallId,
              departure: sharedCallData.departure || "",
              destination: sharedCallData.destination || "",
              fare: (sharedCallData.fare || 0).toString(),
              cancelReason: afterData.cancelReason || "사유 없음",
              // 알림 제목과 내용을 완전히 다른 키로 전송
              alertTitle: "🚫 공유콜이 취소되었습니다!",
              alertMessage: `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"}\n취소사유: ${afterData.cancelReason || "사유 없음"}\n콜이 대기상태로 복구되었습니다.`,
              phoneNumber: sharedCallData.phoneNumber || "",
              showPopup: "true" // 팝업 표시 플래그
            },
            android: {
              priority: "high",
              // notification 필드 완전 제거
            },
            tokens,
          };

          const response = await admin.messaging().sendEachForMulticast(message);
          logger.info(`[call-cancelled:${callId}] 원본 사무실에 FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);
        }
        
        // 수락사무실에서 취소된 콜 문서 삭제
        await event.data.after.ref.delete();
        logger.info(`[call-cancelled:${callId}] 수락사무실에서 취소된 공유콜 삭제 완료`);
        
      } catch (error) {
        logger.error(`[call-cancelled:${callId}] 공유콜 취소 처리 오류:`, error);
      }
    }
  }
);

// =============================
// 전화 콜 접수 시 앱 고객에게 FCM 알림 전송
// =============================
export const notifyCustomerOnPhoneCall = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId, callId } = event.params;

    if (!event.data) {
      logger.warn(`[notifyCustomerOnPhoneCall:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const callData = event.data.data();
    if (!callData) {
      logger.warn(`[notifyCustomerOnPhoneCall:${callId}] 콜 데이터가 없습니다.`);
      return;
    }

    const phoneNumber = callData.phoneNumber;
    const isAppCustomer = callData.isAppCustomer || false;

    logger.info(`[notifyCustomerOnPhoneCall:${callId}] 콜 생성 감지 - isAppCustomer: ${isAppCustomer}, phoneNumber: ${phoneNumber}`);

    // 앱 고객이 아니면 알림 스킵
    if (!isAppCustomer) {
      logger.info(`[notifyCustomerOnPhoneCall:${callId}] 앱 고객이 아님 - 알림 스킵`);
      return;
    }

    if (!phoneNumber) {
      logger.warn(`[notifyCustomerOnPhoneCall:${callId}] 전화번호 없음 - 알림 스킵`);
      return;
    }

    try {
      // 고객 FCM 토큰 조회
      const customerDoc = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("customerInfo")
        .doc(phoneNumber)
        .get();

      const fcmToken = customerDoc.data()?.fcmToken;
      if (!fcmToken) {
        logger.warn(`[notifyCustomerOnPhoneCall:${callId}] FCM 토큰 없음: ${phoneNumber}`);
        return;
      }

      // FCM 알림 전송
      await admin.messaging().send({
        data: {
          type: "CALL_RECEIVED",
          callId: callId,
          message: "콜이 접수되었습니다. 기사 배정을 기다려주세요."
        },
        android: {
          priority: "high",
          ttl: 60000
        },
        token: fcmToken
      });

      logger.info(`[notifyCustomerOnPhoneCall:${callId}] 고객에게 접수 완료 알림 전송 완료: ${phoneNumber}`);

    } catch (error) {
      logger.error(`[notifyCustomerOnPhoneCall:${callId}] 알림 전송 오류:`, error);
    }
  }
);

// =============================
// 운행 완료 시 고객에게 FCM 알림 전송 + 포인트 적립
// =============================
export const notifyCustomerOnComplete = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId, callId } = event.params;

    if (!event.data) {
      logger.warn(`[notifyCustomerOnComplete:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      logger.warn(`[notifyCustomerOnComplete:${callId}] before/after 데이터 누락`);
      return;
    }

    // 운행 완료 감지 (다른 상태 → COMPLETED)
    if (beforeData.status !== "COMPLETED" && afterData.status === "COMPLETED") {
      logger.info(`[notifyCustomerOnComplete:${callId}] 운행 완료 감지`);

      const phoneNumber = afterData.phoneNumber;
      const isAppCustomer = afterData.isAppCustomer || false;
      const customerName = afterData.customerName || "";

      if (!isAppCustomer) {
        logger.info(`[notifyCustomerOnComplete:${callId}] 앱 고객이 아님 - 알림 및 포인트 적립 스킵`);
        return;
      }

      if (!phoneNumber) {
        logger.warn(`[notifyCustomerOnComplete:${callId}] 전화번호 없음 - 스킵`);
        return;
      }

      const fare = afterData.fare_set || afterData.finalFare || afterData.fare || 0;
      const pointsUsed = afterData.pointsUsed || 0;

      try {
        // 고객 FCM 토큰 조회
        const customerDoc = await admin.firestore()
          .collection("provinces").doc(provinceId)
          .collection("cities").doc(cityId)
          .collection("offices").doc(officeId)
          .collection("customerInfo")
          .doc(phoneNumber)
          .get();

        const fcmToken = customerDoc.data()?.fcmToken;

        // 1) 운행 완료 FCM 알림 전송
        if (fcmToken) {
          await admin.messaging().send({
            data: {
              type: "RIDE_COMPLETED",
              callId: callId,
              fare: fare.toString(),
              pointsUsed: pointsUsed.toString()
            },
            android: {
              priority: "high",
              ttl: 60000
            },
            token: fcmToken
          });
          logger.info(`[notifyCustomerOnComplete:${callId}] 운행 완료 알림 전송 완료: ${phoneNumber}`);
        } else {
          logger.warn(`[notifyCustomerOnComplete:${callId}] FCM 토큰 없음: ${phoneNumber}`);
        }

        // 2) 고객 포인트 적립 처리
        logger.info(`[notifyCustomerOnComplete:${callId}] 포인트 적립 시작. Fare: ${fare}`);

        const pointsResult = await processCustomerPointsOnComplete(
          provinceId,
          cityId,
          officeId,
          callId,
          phoneNumber,
          fare,
          customerName
        );

        if (pointsResult.success && pointsResult.pointsEarned > 0 && fcmToken) {
          // 3) 포인트 적립 완료 FCM 알림 전송
          let pointsMessage = `${pointsResult.pointsEarned}P 적립! (잔액: ${pointsResult.newBalance}P)`;

          // 등급 업그레이드 시 추가 메시지
          if (pointsResult.gradeUpgraded) {
            pointsMessage = `${pointsResult.pointsEarned}P 적립! 축하합니다! ${pointsResult.previousGrade} → ${pointsResult.grade} 등급 승급! (잔액: ${pointsResult.newBalance}P)`;
          }

          await admin.messaging().send({
            data: {
              type: "POINTS_EARNED",
              callId: callId,
              pointsEarned: pointsResult.pointsEarned.toString(),
              newBalance: pointsResult.newBalance.toString(),
              grade: pointsResult.grade,
              gradeUpgraded: pointsResult.gradeUpgraded.toString()
            },
            notification: {
              title: "포인트 적립 완료",
              body: pointsMessage
            },
            android: {
              priority: "high",
              ttl: 60000
            },
            token: fcmToken
          });

          logger.info(`[notifyCustomerOnComplete:${callId}] 포인트 적립 알림 전송 완료. Earned: ${pointsResult.pointsEarned}P, Balance: ${pointsResult.newBalance}P, Grade: ${pointsResult.grade}`);
        } else if (!pointsResult.success) {
          logger.warn(`[notifyCustomerOnComplete:${callId}] 포인트 적립 실패: ${pointsResult.error}`);
        }

      } catch (error) {
        logger.error(`[notifyCustomerOnComplete:${callId}] 처리 오류:`, error);
      }
    }
  }
);

// 콜 상태 변경 시 알림 (운행시작, 정산완료 등)
export const onCallStatusChanged = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}",
  },
  async (event) => {
    const { provinceId, cityId, officeId, callId } = event.params;
    
    if (!event.data) {
      logger.warn(`[onCallStatusChanged:${callId}] No event data.`);
      return;
    }

    const beforeData = event.data.before?.data();
    const afterData = event.data.after?.data();

    if (!afterData) {
      logger.warn(`[onCallStatusChanged:${callId}] Missing after data.`);
      return;
    }

    // 새 문서 생성인 경우 (공유콜 포함)
    const isNewDocument = !beforeData;
    if (isNewDocument) {
      logger.info(`[onCallStatusChanged:${callId}] 새 문서 생성 감지. 알림을 보내지 않습니다. callType: ${afterData.callType}`);
      return;
    }

    // 상태가 변경되지 않았으면 무시
    if (beforeData.status === afterData.status) {
      logger.info(`[onCallStatusChanged:${callId}] 상태 변경 없음 - status: ${afterData.status}, departure_set: ${afterData.departure_set}, destination_set: ${afterData.destination_set}, fare_set: ${afterData.fare_set}`);
      return;
    }

    logger.info(`[onCallStatusChanged:${callId}] Status changed: ${beforeData.status} → ${afterData.status}`);
    // ✅ 콜매니저에 상태 변경 알림 전송
    try {
      const managerTokensSnapshot = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("managerTokens")
        .get();

      if (!managerTokensSnapshot.empty) {
        const managerTokens: string[] = [];
        managerTokensSnapshot.forEach((doc) => {
          const token = doc.data().fcmToken;
          if (token) managerTokens.push(token);
        });

        if (managerTokens.length > 0) {
          const managerPayload = {
            data: {
              type: "CALL_STATUS_UPDATE",
              callId: callId,
              status: afterData.status,
              customerName: afterData.customerName || "고객",
              customerPhone: afterData.phoneNumber || "",
              assignedDriverName: afterData.assignedDriverName || "",
              assignedDriverPhone: afterData.assignedDriverPhone || "",
              departure: afterData.departure_set || afterData.departure || "",
              destination: afterData.destination_set || afterData.destination || "",
              waypoints: afterData.waypoints_set || "",
              fare: (afterData.fare_set ?? afterData.fare ?? 0).toString(),
              provinceId: provinceId,
              cityId: cityId,
              officeId: officeId
            },
            android: {
              priority: "high" as const,
              ttl: 60000
            },
            tokens: managerTokens
          };

          const response = await admin.messaging().sendEachForMulticast(managerPayload);
          logger.info(`[onCallStatusChanged:${callId}] 콜매니저 FCM 전송 - ${afterData.status} - 성공: ${response.successCount}`);
        }
      }
    } catch (managerError) {
      logger.error(`[onCallStatusChanged:${callId}] 콜매니저 FCM 오류:`, managerError);
    }

    // CUST-03: 앱 회원 고객에게 상태 변경 FCM 전송 (ACCEPTED, IN_PROGRESS)
    const customerNotifyStatuses = ["ACCEPTED", "IN_PROGRESS"];
    if (afterData.isAppCustomer && afterData.phoneNumber && customerNotifyStatuses.includes(afterData.status)) {
      try {
        const customerDoc = await admin.firestore()
          .collection("provinces").doc(provinceId)
          .collection("cities").doc(cityId)
          .collection("offices").doc(officeId)
          .collection("customerInfo")
          .doc(afterData.phoneNumber)
          .get();

        const fcmToken = customerDoc.data()?.fcmToken;
        if (fcmToken) {
          const statusMessages: Record<string, string> = {
            "ACCEPTED": "기사가 콜을 수락했습니다. 곧 도착합니다.",
            "IN_PROGRESS": "운행이 시작되었습니다.",
          };

          await admin.messaging().send({
            token: fcmToken,
            data: {
              type: "call_status_update",
              callId: callId,
              status: afterData.status,
              driverName: afterData.assignedDriverName || "",
              driverPhone: afterData.assignedDriverPhone || "",
            },
            notification: {
              title: "콜 상태 알림",
              body: statusMessages[afterData.status] || `상태가 ${afterData.status}(으)로 변경되었습니다.`,
            },
            android: {
              priority: "high",
              ttl: 60000,
            },
          });
          logger.info(`[onCallStatusChanged:${callId}] 고객 FCM 전송 완료: ${afterData.status}`);
        }
      } catch (customerError) {
        logger.error(`[onCallStatusChanged:${callId}] 고객 FCM 오류:`, customerError);
      }
    }
  }
);

// pending_drivers 컬렉션에 새 문서 생성 시 FCM 알림 전송
export const onDriverSignupRequest = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "pending_drivers/{driverId}"
  },
  async (event: any) => {
    const driverId = event.params.driverId;
    const driverData = event.data?.data();

    if (!driverData) {
      logger.warn(`[onDriverSignupRequest:${driverId}] No driver data found.`);
      return;
    }

    const { targetProvinceId, targetCityId, targetOfficeId, name, phoneNumber } = driverData;

    logger.info(`[onDriverSignupRequest:${driverId}] New driver signup: ${name} for office ${targetOfficeId}`);

    try {
      // 해당 사무실의 관리자들 FCM 토큰 가져오기
      const adminsSnapshot = await admin.firestore()
        .collection("admins")
        .where("associatedProvinceId", "==", targetProvinceId)
        .where("associatedCityId", "==", targetCityId)
        .where("associatedOfficeId", "==", targetOfficeId)
        .get();

      const tokens: string[] = [];
      adminsSnapshot.forEach(doc => {
        const adminData = doc.data();
        if (adminData.fcmToken) {
          tokens.push(adminData.fcmToken);
        }
      });

      if (tokens.length === 0) {
        logger.warn(`[onDriverSignupRequest:${driverId}] No admin tokens found for office ${targetOfficeId}`);
        return;
      }

      // FCM 메시지 전송
      const payload = {
        notification: {
          title: "🚗 새 기사 가입 신청",
          body: `${name}님이 가입 승인을 기다리고 있습니다.`,
        },
        data: {
          type: "DRIVER_APPROVAL_REQUEST",
          driverId: driverId,
          driverName: name,
          driverPhone: phoneNumber || "",
        },
        android: {
          priority: "high" as const,
          ttl: 60000,
          notification: {
            sound: "default",
            clickAction: "com.designated.callmanager.HOME",
            channelId: "driver_approval_channel"
          }
        }
      };

      // 모든 관리자에게 전송
      for (const token of tokens) {
        try {
          await admin.messaging().send({ ...payload, token });
          logger.info(`[onDriverSignupRequest:${driverId}] FCM sent to admin token: ${token.substring(0, 10)}...`);
        } catch (error) {
          logger.error(`[onDriverSignupRequest:${driverId}] Failed to send FCM:`, error);
        }
      }

    } catch (error) {
      logger.error(`[onDriverSignupRequest:${driverId}] Error processing pending driver:`, error);
    }
  }
);

// 새 콜 알림 함수 제거됨
// 이유: Call Detector에서 로컬 데이터로 즉시 팝업 생성하므로 FCM 알림 불필요
// 기존 함수는 중복 알림 및 앱 재빌드 시 이전 콜 재팝업 문제 야기

// =============================
// 공유콜 상태 동기화 - 수락사무실의 콜 상태를 원사무실에 반영
// =============================
export const onSharedCallStatusSync = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { callId } = event.params;

    if (!event.data) {
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      return;
    }

    // 공유콜인지 확인
    if (afterData.callType !== "SHARED" || !afterData.sourceSharedCallId) {
      logger.info(`[shared-sync:${callId}] 공유콜이 아니므로 스킵. callType: ${afterData.callType}, sourceSharedCallId: ${afterData.sourceSharedCallId}`);
      return;
    }

    // 상태가 변경되었는지 확인
    if (beforeData.status === afterData.status) {
      logger.info(`[shared-sync:${callId}] 상태 변경 없음. status: ${afterData.status}`);
      return;
    }

    // CANCELLED_BY_DRIVER는 이미 별도 함수에서 처리
    if (afterData.status === "CANCELLED_BY_DRIVER") {
      return;
    }

    logger.info(`[shared-sync:${callId}] 공유콜 상태 동기화: ${beforeData.status} → ${afterData.status}`);

    try {
      // shared_calls 문서에서 원사무실 정보 가져오기
      const sharedCallRef = admin.firestore().collection("shared_calls").doc(afterData.sourceSharedCallId);
      const sharedCallSnap = await sharedCallRef.get();
      
      if (!sharedCallSnap.exists) {
        logger.warn(`[shared-sync:${callId}] shared_calls 문서를 찾을 수 없습니다.`);
        return;
      }

      const sharedCallData = sharedCallSnap.data() as SharedCallData;

      // 원사무실 콜 업데이트 (originalCallId 사용)
      const originalCallId = (sharedCallData as any).originalCallId;
      logger.info(`[shared-sync:${callId}] sharedCallData: ${JSON.stringify(sharedCallData)}`);
      
      if (originalCallId) {
        const originalCallRef = admin.firestore()
          .collection("provinces").doc(sharedCallData.sourceProvinceId)
          .collection("cities").doc(sharedCallData.sourceCityId)
          .collection("offices").doc(sharedCallData.sourceOfficeId)
          .collection("calls").doc(originalCallId);

        const originalCallSnap = await originalCallRef.get();
        if (originalCallSnap.exists) {
          const updateData = {
            status: afterData.status,
            updatedAt: FieldValue.serverTimestamp()
          };
          await originalCallRef.update(updateData);
          logger.info(`[shared-sync:${callId}] 원사무실 콜 상태 업데이트 완료: ${originalCallId} → ${afterData.status}`);
        } else {
          logger.warn(`[shared-sync:${callId}] 원사무실 콜을 찾을 수 없습니다: ${originalCallId}`);
        }
      } else {
        logger.warn(`[shared-sync:${callId}] originalCallId가 없습니다. 대신 callId로 시도합니다.`);
        
        // originalCallId가 없으면 shared_calls의 ID와 원본 콜 ID가 같을 수 있음
        const fallbackCallRef = admin.firestore()
          .collection("provinces").doc(sharedCallData.sourceProvinceId)
          .collection("cities").doc(sharedCallData.sourceCityId)
          .collection("offices").doc(sharedCallData.sourceOfficeId)
          .collection("calls").doc(afterData.sourceSharedCallId);
          
        const fallbackSnap = await fallbackCallRef.get();
        if (fallbackSnap.exists) {
          await fallbackCallRef.update({
            status: afterData.status,
            updatedAt: FieldValue.serverTimestamp()
          });
          logger.info(`[shared-sync:${callId}] 원사무실 콜 상태 업데이트 완료 (fallback): ${afterData.sourceSharedCallId} → ${afterData.status}`);
        }
      }
    } catch (error) {
      logger.error(`[shared-sync:${callId}] 상태 동기화 오류:`, error);
    }
  }
);

// =============================
// 공유 콜에서 복사된 일반 콜이 COMPLETED 될 때 트리거
// 1) 원본 shared_calls 문서를 COMPLETED로 업데이트
// 2) 포인트 가감 처리 (10% 수수료)
// =============================
export const onSharedCallCompleted = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId, callId } = event.params;

    if (!event.data) {
      logger.warn(`[call-completed:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      logger.warn(`[call-completed:${callId}] before/after 데이터 누락`);
      return;
    }

    // 공유콜에서 복사된 콜인지 확인
    if (afterData.callType !== "SHARED" || !afterData.sourceSharedCallId) {
      return; // 일반 콜이므로 처리하지 않음
    }

    // 완료 상태로 변경되었는지 확인
    if (beforeData.status !== "COMPLETED" && afterData.status === "COMPLETED") {
      logger.info(`[call-completed:${callId}] 공유콜에서 복사된 콜이 완료되었습니다. 원본 업데이트 시작.`);
      
      const sourceSharedCallId = afterData.sourceSharedCallId;
      const fare = afterData.fare_set || afterData.fare || 0;

      try {
        // 1. 먼저 shared_calls 업데이트
        const sharedCallRef = admin.firestore().collection("shared_calls").doc(sourceSharedCallId);
        const sharedCallSnap = await sharedCallRef.get();

        if (!sharedCallSnap.exists) {
          logger.error(`[call-completed:${callId}] 원본 shared_calls 문서를 찾을 수 없습니다: ${sourceSharedCallId}`);
          return;
        }

        const sharedCallData = sharedCallSnap.data() as SharedCallData;

        // shared_calls 문서를 COMPLETED로 업데이트
        await sharedCallRef.update({
          status: "COMPLETED",
          completedAt: FieldValue.serverTimestamp(),
          destCallId: callId
        });

        // 2. 포인트 처리 (별도 함수 호출)
        await processSharedCallPoints(
          sharedCallData,
          provinceId,
          cityId,
          officeId,
          fare,
          sourceSharedCallId
        );

        logger.info(`[call-completed:${callId}] 공유콜 완료 처리 및 포인트 분배 완료. SharedCallId: ${sourceSharedCallId}`);

      } catch (error) {
        logger.error(`[call-completed:${callId}] 공유콜 완료 처리 오류:`, error);
      }
    }
  }
);

// [STL-10] finalizeWorkDay 구 정산 함수 제거됨 (새 정산 시스템: settlementSessions 사용)

// 픽업 기사 데이터 마이그레이션 함수 (한 번만 실행)

export const migratePickupDrivers = onCall(
  {
    region: "asia-northeast3",
  },
  async (request) => {
    logger.info("픽업 기사 데이터 마이그레이션 시작");
    
    try {
      const results = {
        found: 0,
        migrated: 0,
        errors: 0,
        details: [] as string[]
      };

      // designated_drivers에서 driverType이 PICKUP인 기사들 찾기
      const pickupDriversInDesignated = await admin
        .firestore()
        .collectionGroup("designated_drivers")
        .where("driverType", "==", "PICKUP")
        .get();

      results.found = pickupDriversInDesignated.size;
      logger.info(`발견된 픽업 기사: ${results.found}명`);

      for (const doc of pickupDriversInDesignated.docs) {
        try {
          const driverData = doc.data();
          const driverId = doc.id;
          
          // 경로에서 provinceId, cityId, officeId 추출
          const pathSegments = doc.ref.path.split('/');
          const provinceId = pathSegments[1]; // provinces/{provinceId}
          const cityId = pathSegments[3]; // cities/{cityId}
          const officeId = pathSegments[5]; // offices/{officeId}

          logger.info(`마이그레이션 중: ${driverData.name} (${provinceId}/${cityId}/${officeId})`);

          // pickup_drivers 컬렉션에 새 문서 생성
          const pickupDriverRef = admin
            .firestore()
            .collection("provinces")
            .doc(provinceId)
            .collection("cities")
            .doc(cityId)
            .collection("offices")
            .doc(officeId)
            .collection("pickup_drivers")
            .doc(driverId);

          await pickupDriverRef.set(driverData);
          
          // 원본 designated_drivers 문서 삭제
          await doc.ref.delete();
          
          results.migrated++;
          results.details.push(`✅ ${driverData.name} (${provinceId}/${cityId}/${officeId}) 마이그레이션 완료`);
          
        } catch (error) {
          results.errors++;
          logger.error(`픽업 기사 마이그레이션 오류: ${doc.id}`, error);
          results.details.push(`❌ ${doc.id} 마이그레이션 실패: ${error}`);
        }
      }

      logger.info(`픽업 기사 마이그레이션 완료: ${results.migrated}/${results.found} 성공, ${results.errors} 오류`);
      
      return {
        success: true,
        message: `픽업 기사 마이그레이션 완료`,
        ...results
      };

    } catch (error) {
      logger.error("픽업 기사 마이그레이션 전체 오류:", error);
      return {
        success: false,
        message: `마이그레이션 실패: ${error}`,
        error: error
      };
    }
  }
);

// =============================
// 🚨 FCM 테스트 함수 (HTTP 트리거)
// =============================
export const testFcmMessage = onRequest(
  { region: "asia-northeast3" },
  async (req, res) => {
    
    const message = {
      notification: {
        title: "✅ 운행 완료 (테스트)",
        body: "테스트고객 - 테스트기사",
      },
      data: {
        type: "STATUS_CHANGE",
        callId: "test_call_" + Date.now(),
        statusText: "운행 완료",
        customerName: "테스트고객",
        customerPhone: "010-1234-5678",
        driverName: "테스트기사"
      },
      android: {
        priority: "high" as const,
        notification: {
          sound: "default",
          clickAction: "com.designated.callmanager.HOME",
          channelId: "status_change_fcm_channel"
        }
      },
      token: "fNqW53QeRTef5R9fHRoxJi:APA91bEMRlbcD26SX8iBi5EeU_bIrdtpLcGDHW9_7TQIHKeDBFJs_xlWet-QSrvUXPaHvWCZn8ZczvKr5e1HlTYtM3dewIbxGZfOnxYPgIMVgex-VELP4PI",
    };


    try {
      const response = await admin.messaging().send(message);
      
      res.json({
        success: true,
        messageId: response,
        payload: message
      });
    } catch (error) {
      logger.error("❌ [testFcmMessage] FCM 메시지 전송 실패:", error);
      
      res.status(500).json({
        success: false,
        error: error
      });
    }
  }
);

// =============================
// 고객 앱 어트리뷰션 매칭 함수
// =============================

interface DeviceFingerprint {
  androidId: string;
  deviceModel: string;
  osVersion: string;
  screenResolution: string;
  timezone: string;
  language: string;
  timestamp?: number;
}

interface AttributionData {
  fingerprint: DeviceFingerprint;
  phoneNumber: string;
  deviceInfo?: any;
}

// 어트리뷰션 점수 계산 함수 (웹과 앱 두 형식 모두 지원)
function calculateAttributionScore(
  attribution: any,
  currentFingerprint: DeviceFingerprint
): number {
  let score = 0;
  const scoreDetails: string[] = [];

  logger.info(`[점수계산] 시작 - source: ${attribution.source}`);
  logger.info(`[점수계산] attribution 데이터:`, {
    screenResolution: attribution.screenResolution,
    timezone: attribution.timezone,
    language: attribution.language,
    platform: attribution.platform,
    userAgent: attribution.userAgent?.substring(0, 100),
    androidId: attribution.androidId,
    deviceModel: attribution.deviceModel,
    osVersion: attribution.osVersion
  });
  logger.info(`[점수계산] currentFingerprint 데이터:`, {
    screenResolution: currentFingerprint.screenResolution,
    timezone: currentFingerprint.timezone,
    language: currentFingerprint.language,
    androidId: currentFingerprint.androidId,
    deviceModel: currentFingerprint.deviceModel,
    osVersion: currentFingerprint.osVersion
  });

  // 웹에서 수집한 데이터인지 확인 (source: 'landing')
  if (attribution.source === 'landing') {
    // 화면 해상도 매칭 (30점)
    if (attribution.screenResolution === currentFingerprint.screenResolution) {
      score += 30;
      scoreDetails.push("화면해상도(30)");
      logger.info(`[점수계산] 화면해상도 매칭: ${attribution.screenResolution} = ${currentFingerprint.screenResolution} (+30점)`);
    } else {
      logger.info(`[점수계산] 화면해상도 불일치: ${attribution.screenResolution} ≠ ${currentFingerprint.screenResolution}`);
    }

    // 타임존 매칭 (30점)
    if (attribution.timezone === currentFingerprint.timezone) {
      score += 30;
      scoreDetails.push("타임존(30)");
      logger.info(`[점수계산] 타임존 매칭: ${attribution.timezone} = ${currentFingerprint.timezone} (+30점)`);
    } else {
      logger.info(`[점수계산] 타임존 불일치: ${attribution.timezone} ≠ ${currentFingerprint.timezone}`);
    }

    // 언어 매칭 (20점)
    if (attribution.language === currentFingerprint.language) {
      score += 20;
      scoreDetails.push("언어(20)");
      logger.info(`[점수계산] 언어 매칭: ${attribution.language} = ${currentFingerprint.language} (+20점)`);
    } else {
      logger.info(`[점수계산] 언어 불일치: ${attribution.language} ≠ ${currentFingerprint.language}`);
    }

    // 플랫폼 매칭 - 웹은 Win32, 앱은 Android이므로 교차 플랫폼 보너스
    if (attribution.platform && attribution.platform.includes("Win") &&
        currentFingerprint.deviceModel) {
      score += 20;
      scoreDetails.push("교차플랫폼(20)");
      logger.info(`[점수계산] 교차 플랫폼 보너스: Win → Android (+20점)`);
    } else {
      logger.info(`[점수계산] 교차 플랫폼 조건 불충족: platform=${attribution.platform}, deviceModel=${currentFingerprint.deviceModel}`);
    }

    // userAgent에서 추출 가능한 정보 매칭
    if (attribution.userAgent && currentFingerprint.osVersion) {
      if (attribution.userAgent.includes("Android") ||
          attribution.userAgent.includes("Mobile")) {
        score += 10;
        scoreDetails.push("UserAgent(10)");
        logger.info(`[점수계산] UserAgent 모바일 매칭 (+10점)`);
      } else {
        logger.info(`[점수계산] UserAgent 모바일 불일치: ${attribution.userAgent.substring(0, 50)}`);
      }
    } else {
      logger.info(`[점수계산] UserAgent 조건 불충족: userAgent=${!!attribution.userAgent}, osVersion=${!!currentFingerprint.osVersion}`);
    }
  } else {
    // 앱에서 수집한 데이터 (기존 로직)
    logger.info(`[점수계산] 앱 데이터 매칭 시작`);

    // Android ID 매칭 (40점)
    if (attribution.androidId === currentFingerprint.androidId) {
      score += 40;
      scoreDetails.push("AndroidID(40)");
      logger.info(`[점수계산] AndroidID 매칭 (+40점)`);
    }

    // 디바이스 모델 매칭 (20점)
    if (attribution.deviceModel === currentFingerprint.deviceModel) {
      score += 20;
      scoreDetails.push("기기모델(20)");
      logger.info(`[점수계산] 기기모델 매칭 (+20점)`);
    }

    // OS 버전 매칭 (10점)
    if (attribution.osVersion === currentFingerprint.osVersion) {
      score += 10;
      scoreDetails.push("OS버전(10)");
      logger.info(`[점수계산] OS버전 매칭 (+10점)`);
    }

    // 화면 해상도 매칭 (15점)
    if (attribution.screenResolution === currentFingerprint.screenResolution) {
      score += 15;
      scoreDetails.push("화면해상도(15)");
      logger.info(`[점수계산] 화면해상도 매칭 (+15점)`);
    }

    // 타임존 매칭 (10점)
    if (attribution.timezone === currentFingerprint.timezone) {
      score += 10;
      scoreDetails.push("타임존(10)");
      logger.info(`[점수계산] 타임존 매칭 (+10점)`);
    }

    // 언어 매칭 (5점)
    if (attribution.language === currentFingerprint.language) {
      score += 5;
      scoreDetails.push("언어(5)");
      logger.info(`[점수계산] 언어 매칭 (+5점)`);
    }
  }

  logger.info(`[점수계산] 최종 점수: ${score}점, 세부: [${scoreDetails.join(", ")}]`);
  return score;
}

// 어트리뷰션 매칭 함수
export const matchAttribution = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    logger.warn(`[matchAttribution] 함수 호출됨 - 전체 데이터:`, JSON.stringify(request.data));

    const { fingerprint, phoneNumber } = request.data as AttributionData;

    logger.warn(`[matchAttribution] 시작 - phoneNumber: ${phoneNumber}`);

    try {
      // 모든 지역의 모든 사무실에서 attribution 데이터 찾기
      const db = admin.firestore();
      const provincesSnapshot = await db.collection("provinces").get();

      let bestMatch: any = null;
      let bestScore = 0;
      let totalAttributions = 0;

      logger.warn(`[matchAttribution] 검색할 도 수: ${provincesSnapshot.size}개`);

      // 모든 도 순회
      for (const provinceDoc of provincesSnapshot.docs) {
        const provinceId = provinceDoc.id;
        logger.info(`[matchAttribution] 도 확인: ${provinceId}`);

        // 해당 도의 모든 시 순회
        const citiesSnapshot = await db
          .collection("provinces").doc(provinceId)
          .collection("cities")
          .get();

        logger.info(`[matchAttribution] ${provinceId} 도의 시 수: ${citiesSnapshot.size}개`);

        for (const cityDoc of citiesSnapshot.docs) {
          const cityId = cityDoc.id;

          // 해당 시의 모든 사무실 순회
          const officesSnapshot = await db
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices")
            .get();

          logger.info(`[matchAttribution] ${provinceId}/${cityId}의 사무실 수: ${officesSnapshot.size}개`);

          for (const officeDoc of officesSnapshot.docs) {
            const officeId = officeDoc.id;

            // 각 사무실의 attributions 확인
            const attributionsSnapshot = await db
              .collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("attributions")
              .get();

            if (!attributionsSnapshot.empty) {
              logger.info(`[matchAttribution] ${provinceId}/${cityId}/${officeId} - Attribution 데이터: ${attributionsSnapshot.size}개`);
              totalAttributions += attributionsSnapshot.size;

            attributionsSnapshot.forEach((doc) => {
              const attribution = doc.data();

              // Option 2: 만료된 핑거프린트는 스킵
              if (attribution.expiresAt) {
                const expiresAtMillis = attribution.expiresAt.toMillis ? attribution.expiresAt.toMillis() : attribution.expiresAt;
                const now = Date.now();
                if (now > expiresAtMillis) {
                  logger.info(`[matchAttribution] 만료된 핑거프린트 스킵 - 문서 ${doc.id} (만료: ${new Date(expiresAtMillis).toISOString()})`);
                  return;
                }
              }

              const score = calculateAttributionScore(attribution, fingerprint);

              logger.info(`[matchAttribution] 문서 ${doc.id} (${provinceId}/${cityId}/${officeId}):`, {
                source: attribution.source,
                score: score,
                fingerprintData: {
                  screenResolution: fingerprint.screenResolution,
                  timezone: fingerprint.timezone,
                  language: fingerprint.language
                },
                attributionData: {
                  screenResolution: attribution.screenResolution,
                  timezone: attribution.timezone,
                  language: attribution.language
                }
              });

              // 점수가 더 높거나, 같은 점수일 때는 최신 것을 선택
              const isNewBetter = score > bestScore ||
                (score === bestScore && attribution.createdAt && bestMatch?.createdAt &&
                 attribution.createdAt.toMillis() > bestMatch.createdAt.toMillis());

              if (isNewBetter) {
                bestScore = score;
                bestMatch = {
                  id: doc.id,
                  ...attribution,
                  provinceId: provinceId,
                  cityId: cityId,
                  officeId: officeId
                };

                logger.info(`[matchAttribution] 새로운 bestMatch 발견! 점수: ${bestScore}, provinceId: ${provinceId}, cityId: ${cityId}, officeId: ${officeId}, createdAt: ${attribution.createdAt ? new Date(attribution.createdAt.toMillis()).toISOString() : 'N/A'}`);
              }
            });
            }
          }
        }
      }

      logger.warn(`[matchAttribution] 전체 처리한 attribution 문서 개수: ${totalAttributions}개`);

      if (totalAttributions === 0) {
        logger.info('[matchAttribution] 처리할 문서가 없어 함수를 조기 종료합니다.');
        return {
          success: false,
          requiresManualEntry: true,
          score: 0,
          confidence: "NO_DATA"
        };
      }

      logger.info(`[matchAttribution] 최고 점수: ${bestScore}점`);
      logger.info(`[matchAttribution] bestMatch 상태:`, bestMatch ? `존재 - officeId: ${bestMatch.officeId}` : "null");

      // 10점 이상이면 자동 매칭 (테스트용으로 임시 조정)
      if (bestScore >= 10 && bestMatch) {
        logger.info(`[matchAttribution] 자동 매칭 성공 - provinceId: ${bestMatch.provinceId}, cityId: ${bestMatch.cityId}, officeId: ${bestMatch.officeId}`);

        // attributions 컬렉션에 저장
        await admin.firestore().collection("attributions").add({
          phoneNumber,
          provinceId: bestMatch.provinceId,
          cityId: bestMatch.cityId,
          officeId: bestMatch.officeId,
          fingerprintId: bestMatch.id,
          attributionScore: bestScore,
          source: "automatic",
          linkedAt: FieldValue.serverTimestamp(),
          deviceFingerprint: fingerprint
        });

        return {
          success: true,
          provinceId: bestMatch.provinceId,
          cityId: bestMatch.cityId,
          officeId: bestMatch.officeId,
          score: bestScore,
          confidence: "HIGH",
          referralDriverId: bestMatch.driverId || null,
          referralDriverName: bestMatch.driverName || null
        };
      }
      // 50-69점이면 수동 확인 필요
      else if (bestScore >= 50 && bestMatch) {
        logger.info(`[matchAttribution] 수동 확인 필요 - provinceId: ${bestMatch.provinceId}, cityId: ${bestMatch.cityId}, officeId: ${bestMatch.officeId}, score: ${bestScore}`);

        return {
          success: false,
          requiresManualConfirmation: true,
          provinceId: bestMatch.provinceId,
          cityId: bestMatch.cityId,
          officeId: bestMatch.officeId,
          score: bestScore,
          confidence: "MEDIUM"
        };
      }
      // 50점 미만이면 수동 입력 필요
      else {
        logger.info(`[matchAttribution] 수동 입력 필요 - 최고 점수: ${bestScore}`);

        return {
          success: false,
          requiresManualEntry: true,
          score: bestScore,
          confidence: "LOW"
        };
      }

    } catch (error) {
      logger.error(`[matchAttribution] 오류 발생:`, error);

      return {
        success: false,
        requiresManualEntry: true,
        score: 0,
        confidence: "ERROR",
        error: error
      };
    }
  }
);

// 수동 사무실 선택 저장 함수
export const saveManualAttribution = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    const { phoneNumber, officeId, reason } = request.data;

    logger.info(`[saveManualAttribution] 수동 선택 저장 - phoneNumber: ${phoneNumber}, officeId: ${officeId}`);

    try {
      await admin.firestore().collection("attributions").add({
        phoneNumber,
        officeId,
        source: "manual",
        reason,
        linkedAt: FieldValue.serverTimestamp(),
        timestamp: FieldValue.serverTimestamp()
      });

      return { success: true };
    } catch (error) {
      logger.error(`[saveManualAttribution] 오류:`, error);
      return { success: false, error };
    }
  }
);

// 토큰 기반 Attribution 매칭 함수
export const matchByToken = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    const { token } = request.data;

    logger.info(`[matchByToken] 토큰 매칭 시작 - token: ${token}`);

    try {
      if (!token) {
        logger.warn(`[matchByToken] 토큰이 제공되지 않음`);
        return {
          success: false,
          message: "토큰이 제공되지 않았습니다"
        };
      }

      // provinces/.../cities/.../offices/.../attributions 컬렉션에서 토큰 검색
      const db = admin.firestore();
      const provincesSnapshot = await db.collection("provinces").get();

      for (const provinceDoc of provincesSnapshot.docs) {
        const provinceId = provinceDoc.id;
        const citiesSnapshot = await db.collection(`provinces/${provinceId}/cities`).get();

        for (const cityDoc of citiesSnapshot.docs) {
          const cityId = cityDoc.id;
          const officesSnapshot = await db.collection(`provinces/${provinceId}/cities/${cityId}/offices`).get();

          for (const officeDoc of officesSnapshot.docs) {
            const officeId = officeDoc.id;
            const officeData = officeDoc.data();

            // 해당 사무실의 attributions에서 토큰 검색
            const attributionsQuery = await db
              .collection(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}/attributions`)
              .where("token", "==", token)
              .limit(1)
              .get();

            if (!attributionsQuery.empty) {
              const attributionDoc = attributionsQuery.docs[0];
              const attributionData = attributionDoc.data();

              // 만료 확인 (생성 후 7일)
              const now = Timestamp.now();
              const createdAt = attributionData.createdAt;
              const expiryTime = createdAt.toMillis() + (7 * 24 * 60 * 60 * 1000);

              if (now.toMillis() > expiryTime) {
                logger.warn(`[matchByToken] 만료된 토큰: ${token}`);
                return {
                  success: false,
                  message: "만료된 QR 코드입니다 (7일 경과)"
                };
              }

              // 이미 사용된 토큰인지 확인 (재사용 허용)
              if (attributionData.claimed === true) {
                logger.info(`[matchByToken] 이미 사용된 토큰이지만 재사용 허용: ${token}`);
              }

              // 성공 응답
              logger.info(`[matchByToken] 매칭 성공 - provinceId: ${provinceId}, cityId: ${cityId}, officeId: ${officeId}, driverId: ${attributionData.driverId || 'null'}, driverName: ${attributionData.driverName || 'null'}`);

              return {
                success: true,
                provinceId: provinceId,
                cityId: cityId,
                officeId: officeId,
                officeName: officeData.name || "",
                officePhone: officeData.phoneNumber || "",
                bankName: officeData.bankName || "",
                accountNumber: officeData.accountNumber || "",
                accountHolder: officeData.accountHolder || "",
                referralDriverId: attributionData.driverId || null,
                referralDriverName: attributionData.driverName || null
              };
            }
          }
        }
      }

      // 토큰을 찾지 못한 경우
      logger.warn(`[matchByToken] 유효하지 않은 토큰: ${token}`);
      return {
        success: false,
        message: "유효하지 않은 QR 코드입니다"
      };

    } catch (error) {
      logger.error(`[matchByToken] 오류 발생:`, error);
      return {
        success: false,
        message: "토큰 처리 중 오류가 발생했습니다",
        error: error
      };
    }
  }
);

// 토큰 상태 업데이트 함수 (앱에서 호출)
export const claimToken = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    const { token, phoneNumber } = request.data;

    logger.info(`[claimToken] 토큰 사용 처리 - token: ${token}, phoneNumber: ${phoneNumber}`);

    try {
      const db = admin.firestore();
      await db.collection("attributionTokens").doc(token).update({
        status: "claimed",
        claimedAt: FieldValue.serverTimestamp(),
        claimedBy: phoneNumber || "unknown"
      });

      logger.info(`[claimToken] 토큰 사용 처리 완료`);
      return { success: true };

    } catch (error) {
      logger.error(`[claimToken] 오류 발생:`, error);
      return { success: false, error };
    }
  }
);

// =============================
// 기사가 운행 취소 시 손님앱에 알림 전송
// ASSIGNED -> HOLD 또는 CANCELLED_BY_DRIVER 감지
// =============================
export const onCallCancelledByDriver = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId, callId } = event.params;

    if (!event.data) {
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      return;
    }

    // 콜 취소 감지: 배정/수락/운행중 상태에서 취소 또는 고객 취소
    const cancelledFromAssigned =
      (beforeData.status === "ASSIGNED" || beforeData.status === "ACCEPTED" || beforeData.status === "IN_PROGRESS") &&
      (afterData.status === "HOLD" || afterData.status === "CANCELLED_BY_DRIVER" || afterData.status === "CANCELED" || afterData.status === "CANCELLED_BY_CUSTOMER");

    const cancelledByCustomer =
      (beforeData.status === "WAITING" || beforeData.status === "REQUESTED" || beforeData.status === "ASSIGNED" || beforeData.status === "ACCEPTED") &&
      (afterData.status === "CANCELLED_BY_CUSTOMER");

    if (!cancelledFromAssigned && !cancelledByCustomer) {
      return;
    }

    // 포인트 환불 처리 (종료 상태에서만, HOLD는 재배차 가능하므로 제외)
    const isTerminalCancel = afterData.status === "CANCELED" || afterData.status === "CANCELLED" || afterData.status === "CANCELLED_BY_DRIVER" || afterData.status === "CANCELLED_BY_CUSTOMER";
    const pointsUsed = afterData.pointsUsed || 0;
    if (isTerminalCancel && pointsUsed > 0 && afterData.phoneNumber) {
      try {
        const refundResult = await refundCustomerPointsOnCancel(
          provinceId, cityId, officeId, callId,
          afterData.phoneNumber, pointsUsed
        );
        if (refundResult.success) {
          logger.info(`[${callId}] 포인트 ${pointsUsed}P 환불 완료`);
        } else {
          logger.warn(`[${callId}] 포인트 환불 스킵: ${refundResult.error}`);
        }
      } catch (error) {
        logger.error(`[${callId}] 포인트 환불 실패:`, error);
      }
    }

    // 고객이 배정 상태에서 취소한 경우: 기사 상태 복구 + 기사에게 FCM 알림
    if (afterData.status === "CANCELLED_BY_CUSTOMER" && cancelledFromAssigned && afterData.assignedDriverId) {
      const driverAuthUid = afterData.assignedDriverId;
      try {
        // 기사 상태를 WAITING으로 복구
        const driversQuery = await admin.firestore()
          .collection("provinces").doc(provinceId)
          .collection("cities").doc(cityId)
          .collection("offices").doc(officeId)
          .collection(DRIVER_COLLECTION_NAME)
          .where("authUid", "==", driverAuthUid)
          .limit(1)
          .get();

        if (!driversQuery.empty) {
          const driverDoc = driversQuery.docs[0];
          const driverData = driverDoc.data();
          if (driverData.status === "ASSIGNED" || driverData.status === "ACCEPTED" || driverData.status === "ON_TRIP" || driverData.status === "PREPARING") {
            await driverDoc.ref.update({ status: "WAITING" });
            logger.info(`[${callId}] 기사 ${driverAuthUid} 상태 WAITING으로 복구 (이전: ${driverData.status})`);
          }

          // 기사에게 취소 FCM 알림
          const driverFcmToken = driverData.fcmToken;
          if (driverFcmToken) {
            const driverPayload = {
              data: {
                type: "call_cancelled",
                callId: callId,
                cancelReason: "고객이 콜을 취소했습니다"
              },
              android: {
                priority: "high" as const,
                ttl: 60000
              },
              token: driverFcmToken
            };
            await admin.messaging().send(driverPayload);
            logger.info(`[${callId}] 기사에게 고객 취소 알림 전송 완료`);
          }
        }
      } catch (driverError) {
        logger.error(`[${callId}] 기사 상태 복구/알림 실패:`, driverError);
      }
    }

    // 원본 콜 취소 시 관련 shared_calls 문서 정리 (OPEN 상태인 것만)
    if (isTerminalCancel && !afterData.sourceSharedCallId) {
      try {
        const sharedCallsQuery = await admin.firestore()
          .collection("shared_calls")
          .where("sourceOfficeId", "==", officeId)
          .where("status", "==", "OPEN")
          .get();

        for (const sharedDoc of sharedCallsQuery.docs) {
          const sharedData = sharedDoc.data();
          if (sharedData.originalCallId === callId) {
            await sharedDoc.ref.delete();
            logger.info(`[${callId}] 관련 shared_calls 문서 삭제: ${sharedDoc.id}`);
          }
        }
      } catch (sharedError) {
        logger.error(`[${callId}] shared_calls 정리 실패:`, sharedError);
      }
    }

    // HOLD(재배차 대기)는 취소가 아니므로 고객 알림 스킵
    if (afterData.status === "HOLD") {
      logger.info(`[${callId}] HOLD 상태 - 재배차 대기 중이므로 고객 취소 알림 스킵`);
      return;
    }

    logger.info(`[${callId}] 콜 취소 감지 - 손님에게 알림 전송 시작`);

    // 앱 고객만 알림
    const isAppCustomer = afterData.isAppCustomer || false;
    const customerPhone = afterData.phoneNumber;

    if (!isAppCustomer || !customerPhone) {
      logger.info(`[${callId}] 앱 고객 아님 - 알림 스킵`);
      return;
    }

    try {
      // 고객 FCM 토큰 조회
      const customerDoc = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("customerInfo")
        .doc(customerPhone)
        .get();

      const customerFcmToken = customerDoc.data()?.fcmToken;
      if (!customerFcmToken) {
        logger.warn(`[${callId}] 고객 FCM 토큰 없음: ${customerPhone}`);
        return;
      }

      // 고객에게 취소 알림 전송
      const customerPayload = {
        data: {
          type: "CALL_CANCELLED",
          callId: callId,
          cancelReason: afterData.cancelReason || "운행취소"
        },
        android: {
          priority: "high" as const,
          ttl: 60000
        },
        token: customerFcmToken
      };

      await admin.messaging().send(customerPayload);
      logger.info(`[${callId}] 고객에게 취소 알림 전송 완료: ${customerPhone}`);

    } catch (error) {
      logger.error(`[${callId}] 고객 취소 알림 전송 오류:`, error);
    }
  }
);

// =============================
// 신규 회원 가입 시 콜매니저에 알림 전송
// =============================
export const onNewCustomerRegistered = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/customers/{customerId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId, customerId } = event.params;

    if (!event.data) {
      logger.info(`[onNewCustomerRegistered] 이벤트 데이터 없음`);
      return;
    }

    const customerData = event.data.data();

    if (!customerData) {
      logger.info(`[onNewCustomerRegistered] 고객 데이터 없음`);
      return;
    }

    logger.info(`[onNewCustomerRegistered] 신규 회원 가입 감지 - customerId: ${customerId}, name: ${customerData.name}`);

    try {
      // 콜매니저 FCM 토큰 조회
      const managerTokensSnapshot = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("managerTokens")
        .get();

      const tokenDocs = managerTokensSnapshot.docs
        .map(doc => ({ docId: doc.id, token: doc.data().fcmToken }))
        .filter(item => item.token);

      if (tokenDocs.length === 0) {
        logger.warn(`[onNewCustomerRegistered] 콜매니저 FCM 토큰 없음 - provinceId: ${provinceId}, cityId: ${cityId}, officeId: ${officeId}`);
        return;
      }

      logger.info(`[onNewCustomerRegistered] 콜매니저 FCM 토큰 ${tokenDocs.length}개 발견`);

      // 알림 메시지 구성
      const referralInfo = customerData.referralDriverName
        ? ` (추천: ${customerData.referralDriverName})`
        : '';

      const message = {
        notification: {
          title: "🎉 새 회원 가입",
          body: `${customerData.name || '신규 회원'}님이 가입했습니다${referralInfo}`
        },
        data: {
          type: "new_customer",
          customerId: customerId,
          customerName: customerData.name || "",
          customerPhone: customerData.phoneNumber || "",
          referralDriverId: customerData.referralDriverId || "",
          referralDriverName: customerData.referralDriverName || ""
        }
      };

      // 각 토큰으로 알림 전송
      const sendResults = await Promise.allSettled(
        tokenDocs.map(({ token }) =>
          admin.messaging().send({ ...message, token })
        )
      );

      let successCount = 0;
      const invalidTokenDocIds: string[] = [];

      // 실패한 알림 처리 및 만료된 토큰 수집
      sendResults.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          successCount++;
        } else {
          const error: any = result.reason;
          logger.error(`[onNewCustomerRegistered] 토큰 ${index + 1} 알림 전송 실패:`, error);

          // 만료되거나 무효한 토큰 감지
          if (error?.errorInfo?.code === 'messaging/registration-token-not-registered' ||
              error?.errorInfo?.code === 'messaging/invalid-registration-token') {
            invalidTokenDocIds.push(tokenDocs[index].docId);
            logger.warn(`[onNewCustomerRegistered] 만료된 토큰 발견 - 삭제 예정: ${tokenDocs[index].docId}`);
          }
        }
      });

      // 만료된 토큰 자동 삭제 및 갱신 요청
      if (invalidTokenDocIds.length > 0) {
        const deletePromises = invalidTokenDocIds.map(docId =>
          admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("managerTokens")
            .doc(docId)
            .delete()
        );

        await Promise.allSettled(deletePromises);
        logger.info(`[onNewCustomerRegistered] 만료된 토큰 ${invalidTokenDocIds.length}개 삭제 완료`);

        // 각 만료된 토큰에 대해 갱신 요청 생성
        const refreshRequests = invalidTokenDocIds.map(managerId =>
          admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("tokenRefreshRequests")
            .doc(managerId)
            .set({
              managerId: managerId,
              requestedAt: Timestamp.now(),
              reason: "token_expired",
              processed: false
            })
        );

        await Promise.allSettled(refreshRequests);
        logger.info(`[onNewCustomerRegistered] 토큰 갱신 요청 ${invalidTokenDocIds.length}개 생성 완료`);
      }

      logger.info(`[onNewCustomerRegistered] 알림 전송 완료 - 성공: ${successCount}/${tokenDocs.length}`);

    } catch (error) {
      logger.error(`[onNewCustomerRegistered] 오류 발생:`, error);
    }
  }
);

// =============================
// 사무실 통계 자동 업데이트
// =============================

// 기사 추가/삭제 시 driverCount 업데이트
export const onDriverCountChange = onDocumentWritten(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId } = event.params;

    try {
      // 해당 사무실의 전체 기사 수 조회
      const driversSnapshot = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("designated_drivers")
        .get();

      const driverCount = driversSnapshot.size;

      // 사무실 문서 업데이트
      await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .update({
          driverCount: driverCount,
          statsUpdatedAt: FieldValue.serverTimestamp()
        });

      logger.info(`[${officeId}] 기사 수 업데이트 완료: ${driverCount}명`);

    } catch (error) {
      logger.error(`[${officeId}] 기사 수 업데이트 실패:`, error);
    }
  }
);

// 고객 추가/삭제 시 customerCount 업데이트
export const onCustomerCountChange = onDocumentWritten(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/customers/{customerId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId } = event.params;

    try {
      // 해당 사무실의 전체 고객 수 조회
      const customersSnapshot = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("customers")
        .get();

      const customerCount = customersSnapshot.size;

      // 사무실 문서 업데이트
      await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .update({
          customerCount: customerCount,
          statsUpdatedAt: FieldValue.serverTimestamp()
        });

      logger.info(`[${officeId}] 고객 수 업데이트 완료: ${customerCount}명`);

    } catch (error) {
      logger.error(`[${officeId}] 고객 수 업데이트 실패:`, error);
    }
  }
);

// =============================
// 콜 디텍터 크래시 시 사무실 관리자에게 FCM 알림 전송
// emergency_alerts 컬렉션에 EMERGENCY_CRASH_ALERT 생성 시 트리거
// =============================
export const onCallDetectorCrash = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "emergency_alerts/{alertId}"
  },
  async (event: any) => {
    const { alertId } = event.params;

    if (!event.data) {
      logger.info(`[${alertId}] 이벤트 데이터가 없어 함수를 종료합니다.`);
      return;
    }

    const alertData = event.data.data();

    // EMERGENCY_CRASH_ALERT가 아니면 무시
    if (alertData.type !== "EMERGENCY_CRASH_ALERT") {
      logger.info(`[${alertId}] EMERGENCY_CRASH_ALERT가 아니므로 알림을 보내지 않습니다. type: ${alertData.type}`);
      return;
    }

    const { provinceId, cityId, officeId, deviceId, message, crashTime } = alertData;

    if (!provinceId || !cityId || !officeId) {
      logger.error(`[${alertId}] provinceId, cityId 또는 officeId가 없습니다.`, alertData);
      return;
    }

    logger.info(`[${alertId}] 콜 디텍터 크래시 감지 - provinceId: ${provinceId}, cityId: ${cityId}, officeId: ${officeId}, deviceId: ${deviceId}`);

    try {
      // 해당 사무실의 관리자들 조회
      const db = admin.firestore();
      const adminsSnapshot = await db.collection("admins")
        .where("associatedProvinceId", "==", provinceId)
        .where("associatedCityId", "==", cityId)
        .where("associatedOfficeId", "==", officeId)
        .get();

      if (adminsSnapshot.empty) {
        logger.warn(`[${alertId}] 해당 사무실의 관리자를 찾을 수 없습니다. provinceId: ${provinceId}, cityId: ${cityId}, officeId: ${officeId}`);
        return;
      }

      // FCM 토큰 수집
      const fcmTokens: string[] = [];
      adminsSnapshot.forEach((doc) => {
        const adminData = doc.data();
        if (adminData.fcmToken) {
          fcmTokens.push(adminData.fcmToken);
        }
      });

      if (fcmTokens.length === 0) {
        logger.warn(`[${alertId}] 관리자의 FCM 토큰이 없습니다. provinceId: ${provinceId}, cityId: ${cityId}, officeId: ${officeId}`);
        return;
      }

      logger.info(`[${alertId}] ${fcmTokens.length}명의 관리자에게 알림 전송 시작`);

      // FCM 메시지 생성
      const notification = {
        title: "🚨 긴급: 콜 디텍터 앱 크래시!",
        body: message || `콜 디텍터 [${deviceId}]가 강제종료 되었습니다. 즉시 확인이 필요합니다.`
      };

      const data = {
        type: "CALL_DETECTOR_CRASH",
        alertId: alertId,
        deviceId: deviceId || "",
        provinceId: provinceId,
        cityId: cityId,
        officeId: officeId,
        crashTime: crashTime ? crashTime.toString() : "",
        priority: "CRITICAL"
      };

      // 멀티캐스트 메시지 전송
      const sendResults = await Promise.allSettled(
        fcmTokens.map(token =>
          admin.messaging().send({
            token,
            notification,
            data,
            android: {
              priority: "high",
              notification: {
                channelId: "emergency_alerts",
                priority: "max",
                sound: "default"
              }
            }
          })
        )
      );

      // 결과 로깅
      const successCount = sendResults.filter(r => r.status === 'fulfilled').length;
      const failedCount = sendResults.filter(r => r.status === 'rejected').length;

      sendResults.forEach((result, index) => {
        if (result.status === 'rejected') {
          logger.error(`[${alertId}] 토큰 ${index + 1} 알림 전송 실패:`, result.reason);
        }
      });

      logger.info(`[${alertId}] 알림 전송 완료 - 성공: ${successCount}/${fcmTokens.length}, 실패: ${failedCount}`);

    } catch (error) {
      logger.error(`[${alertId}] 오류 발생:`, error);
    }
  }
);

// =============================
// 자동 데이터 정리: 매일 오전 11시 실행
// =============================
// NEW-15: ASSIGNED 타임아웃 자동 복구
// 기사가 배차 후 일정 시간 내에 수락하지 않으면 WAITING으로 되돌림
// =============================
export const checkAssignedTimeout = onSchedule(
  {
    schedule: "every 1 minutes",
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
  },
  async () => {
    const db = admin.firestore();

    try {
      // 모든 사무실 조회
      const provincesSnapshot = await db.collection("provinces").get();
      let totalRecovered = 0;

      for (const provinceDoc of provincesSnapshot.docs) {
        const citiesSnapshot = await provinceDoc.ref.collection("cities").get();
        for (const cityDoc of citiesSnapshot.docs) {
          const officesSnapshot = await cityDoc.ref.collection("offices").get();
          for (const officeDoc of officesSnapshot.docs) {
            // 사무실별 타임아웃 설정 (기본 3분)
            const timeoutMinutes = officeDoc.data().assignedTimeoutMinutes ?? 3;
            const timeoutMs = timeoutMinutes * 60 * 1000;
            const cutoff = Timestamp.fromMillis(Date.now() - timeoutMs);

            // ASSIGNED 상태이고 assignedTimestamp가 타임아웃 초과인 콜 검색
            const assignedCalls = await officeDoc.ref
              .collection("calls")
              .where("status", "==", "ASSIGNED")
              .where("assignedTimestamp", "<", cutoff)
              .get();

            for (const callDoc of assignedCalls.docs) {
              const callData = callDoc.data();
              const assignedDriverId = callData.assignedDriverId;

              // 콜을 WAITING으로 복구
              await callDoc.ref.update({
                status: "WAITING",
                assignedDriverId: FieldValue.delete(),
                assignedDriverName: FieldValue.delete(),
                assignedDriverPhone: FieldValue.delete(),
                assignedTimestamp: FieldValue.delete(),
                timeoutRecoveredAt: FieldValue.serverTimestamp(),
              });

              // 기사 상태도 WAITING으로 복구 + FCM 알림
              if (assignedDriverId) {
                const driversQuery = await officeDoc.ref
                  .collection("designated_drivers")
                  .where("authUid", "==", assignedDriverId)
                  .limit(1)
                  .get();

                if (!driversQuery.empty) {
                  const driverDocSnap = driversQuery.docs[0];
                  const driverData = driverDocSnap.data();
                  // ASSIGNED 상태인 기사만 복구 (이미 다른 콜 수행 중이면 건너뜀)
                  if (driverData.status === "ASSIGNED") {
                    await driverDocSnap.ref.update({ status: "WAITING" });
                  }

                  // 기사에게 타임아웃 FCM 알림
                  const driverFcmToken = driverData.fcmToken;
                  if (driverFcmToken) {
                    try {
                      await admin.messaging().send({
                        data: {
                          type: "call_cancelled",
                          callId: callDoc.id,
                          cancelReason: "응답 시간 초과로 배차가 해제되었습니다"
                        },
                        android: { priority: "high" as const, ttl: 60000 },
                        token: driverFcmToken
                      });
                      logger.info(`[AssignedTimeout] 기사 FCM 전송 완료: ${assignedDriverId}`);
                    } catch (fcmError) {
                      logger.warn(`[AssignedTimeout] 기사 FCM 전송 실패: ${assignedDriverId}`, fcmError);
                    }
                  }
                }
              }

              // 고객에게 재배정 FCM 알림 (앱 고객인 경우)
              if (callData.isAppCustomer && callData.phoneNumber) {
                try {
                  const customerDoc = await officeDoc.ref
                    .collection("customerInfo")
                    .doc(callData.phoneNumber)
                    .get();
                  const customerFcmToken = customerDoc.data()?.fcmToken;
                  if (customerFcmToken) {
                    await admin.messaging().send({
                      data: {
                        type: "CALL_STATUS_UPDATE",
                        callId: callDoc.id,
                        status: "WAITING",
                        message: "기사 재배정 중입니다"
                      },
                      android: { priority: "high" as const, ttl: 60000 },
                      token: customerFcmToken
                    });
                    logger.info(`[AssignedTimeout] 고객 FCM 전송 완료: ${callData.phoneNumber}`);
                  }
                } catch (custError) {
                  logger.warn(`[AssignedTimeout] 고객 FCM 전송 실패`, custError);
                }
              }

              totalRecovered++;
              logger.info(
                `[AssignedTimeout] 콜 복구: ${callDoc.id}, ` +
                `기사: ${assignedDriverId}, 타임아웃: ${timeoutMinutes}분`
              );
            }
          }
        }
      }

      if (totalRecovered > 0) {
        logger.info(`[AssignedTimeout] 총 ${totalRecovered}건 타임아웃 복구 완료`);
      }
    } catch (error) {
      logger.error("[AssignedTimeout] 스케줄러 오류:", error);
    }
  }
);

// =============================
// 스케줄 기반 데이터 정리 (매일 11시)
// - WAITING 콜 (1시간 이상)
// - shared_calls (1시간 이상)
// - 만료된 attributions (24시간 이상)
// =============================
export const scheduledDataCleanup = onSchedule(
  {
    schedule: "0 11 * * *", // 매일 오전 11시 (한국 시간)
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "512MiB",
    timeoutSeconds: 540
  },
  async (event) => {
    logger.info("🗑️ 자동 데이터 정리 시작...");

    const now = Date.now();
    const oneHourAgo = now - (60 * 60 * 1000);

    const db = admin.firestore();
    let totalDeleted = 0;

    try {
      // ===== 1. WAITING 콜 삭제 (1시간 이상) =====
      logger.info("📞 WAITING 콜 정리 시작...");

      const provincesSnapshot = await db.collection("provinces").get();

      for (const provinceDoc of provincesSnapshot.docs) {
        const provinceId = provinceDoc.id;
        const citiesSnapshot = await db.collection("provinces").doc(provinceId)
          .collection("cities").get();

        for (const cityDoc of citiesSnapshot.docs) {
          const cityId = cityDoc.id;
          const officesSnapshot = await db.collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").get();

          for (const officeDoc of officesSnapshot.docs) {
            const officeId = officeDoc.id;

            // WAITING 상태 + 1시간 이상 된 콜 조회
            const oldWaitingCalls = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("calls")
              .where("status", "==", "WAITING")
              .where("timestamp", "<", Timestamp.fromMillis(oneHourAgo))
              .get();

            // Batch 삭제 (최대 500개씩)
            if (oldWaitingCalls.size > 0) {
              const batches = [];
              let batch = db.batch();
              let operationCount = 0;

              for (const doc of oldWaitingCalls.docs) {
                batch.delete(doc.ref);
                operationCount++;

                if (operationCount === 500) {
                  batches.push(batch.commit());
                  batch = db.batch();
                  operationCount = 0;
                }
              }

              if (operationCount > 0) {
                batches.push(batch.commit());
              }

              await Promise.all(batches);
              totalDeleted += oldWaitingCalls.size;
              logger.info(`✅ ${provinceId}/${cityId}/${officeId}: WAITING 콜 ${oldWaitingCalls.size}개 삭제`);
            }
          }
        }
      }

      logger.info(`✅ WAITING 콜 정리 완료: 총 ${totalDeleted}개 삭제`);

      // ===== 2. HOLD (보류) 콜 삭제 (1시간 이상) =====
      logger.info("⏸️ HOLD 콜 정리 시작...");

      let holdDeleted = 0;
      const provincesSnapshotHold = await db.collection("provinces").get();

      for (const provinceDoc of provincesSnapshotHold.docs) {
        const provinceId = provinceDoc.id;
        const citiesSnapshot = await db.collection("provinces").doc(provinceId)
          .collection("cities").get();

        for (const cityDoc of citiesSnapshot.docs) {
          const cityId = cityDoc.id;
          const officesSnapshot = await db.collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").get();

          for (const officeDoc of officesSnapshot.docs) {
            const officeId = officeDoc.id;

            // HOLD 상태 + 1시간 이상 된 콜 조회
            const oldHoldCalls = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("calls")
              .where("status", "==", "HOLD")
              .where("timestamp", "<", Timestamp.fromMillis(oneHourAgo))
              .get();

            if (oldHoldCalls.size > 0) {
              const batches = [];
              let batch = db.batch();
              let operationCount = 0;

              for (const doc of oldHoldCalls.docs) {
                batch.delete(doc.ref);
                operationCount++;

                if (operationCount === 500) {
                  batches.push(batch.commit());
                  batch = db.batch();
                  operationCount = 0;
                }
              }

              if (operationCount > 0) {
                batches.push(batch.commit());
              }

              await Promise.all(batches);
              holdDeleted += oldHoldCalls.size;
              logger.info(`✅ ${provinceId}/${cityId}/${officeId}: HOLD 콜 ${oldHoldCalls.size}개 삭제`);
            }
          }
        }
      }

      logger.info(`✅ HOLD 콜 정리 완료: 총 ${holdDeleted}개 삭제`);
      totalDeleted += holdDeleted;

      // ===== 3. CANCELLED 콜 즉시 삭제 (모든 취소 상태) =====
      logger.info("❌ CANCELLED 콜 정리 시작...");

      let cancelledDeleted = 0;
      const provincesSnapshotCancelled = await db.collection("provinces").get();

      for (const provinceDoc of provincesSnapshotCancelled.docs) {
        const provinceId = provinceDoc.id;
        const citiesSnapshot = await db.collection("provinces").doc(provinceId)
          .collection("cities").get();

        for (const cityDoc of citiesSnapshot.docs) {
          const cityId = cityDoc.id;
          const officesSnapshot = await db.collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").get();

          for (const officeDoc of officesSnapshot.docs) {
            const officeId = officeDoc.id;

            // CANCELLED 상태 조회 (시간 제한 없음)
            const cancelledCalls = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("calls")
              .where("status", "==", "CANCELLED")
              .get();

            // CANCELED 상태 조회 (관리자 취소)
            const canceledCalls = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("calls")
              .where("status", "==", "CANCELED")
              .get();

            // CANCELLED_BY_DRIVER 상태 조회
            const cancelledByDriverCalls = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("calls")
              .where("status", "==", "CANCELLED_BY_DRIVER")
              .get();

            // CANCELLED_BY_CUSTOMER 상태 조회
            const cancelledByCustomerCalls = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("calls")
              .where("status", "==", "CANCELLED_BY_CUSTOMER")
              .get();

            const allCancelled = [...cancelledCalls.docs, ...canceledCalls.docs, ...cancelledByDriverCalls.docs, ...cancelledByCustomerCalls.docs];

            if (allCancelled.length > 0) {
              const batches = [];
              let batch = db.batch();
              let operationCount = 0;

              for (const doc of allCancelled) {
                batch.delete(doc.ref);
                operationCount++;

                if (operationCount === 500) {
                  batches.push(batch.commit());
                  batch = db.batch();
                  operationCount = 0;
                }
              }

              if (operationCount > 0) {
                batches.push(batch.commit());
              }

              await Promise.all(batches);
              cancelledDeleted += allCancelled.length;
              logger.info(`✅ ${provinceId}/${cityId}/${officeId}: CANCELLED 콜 ${allCancelled.length}개 삭제`);
            }
          }
        }
      }

      logger.info(`✅ CANCELLED 콜 정리 완료: 총 ${cancelledDeleted}개 삭제`);
      totalDeleted += cancelledDeleted;

      // ===== 4. shared_calls 삭제 (1시간 이상) =====
      logger.info("🔄 shared_calls 정리 시작...");

      const oldSharedCalls = await db.collection("shared_calls")
        .where("timestamp", "<", Timestamp.fromMillis(oneHourAgo))
        .get();

      if (oldSharedCalls.size > 0) {
        const batches = [];
        let batch = db.batch();
        let operationCount = 0;

        for (const doc of oldSharedCalls.docs) {
          batch.delete(doc.ref);
          operationCount++;

          if (operationCount === 500) {
            batches.push(batch.commit());
            batch = db.batch();
            operationCount = 0;
          }
        }

        if (operationCount > 0) {
          batches.push(batch.commit());
        }

        await Promise.all(batches);
        logger.info(`✅ shared_calls 정리 완료: ${oldSharedCalls.size}개 삭제`);
        totalDeleted += oldSharedCalls.size;
      } else {
        logger.info("ℹ️ 삭제할 shared_calls 없음");
      }

      // ===== 3. 만료된 attributions 삭제 (24시간 이상) =====
      logger.info("🔍 만료된 attributions 정리 시작...");

      const provincesSnapshot2 = await db.collection("provinces").get();
      let expiredCount = 0;

      for (const provinceDoc of provincesSnapshot2.docs) {
        const provinceId = provinceDoc.id;
        const citiesSnapshot = await db.collection("provinces").doc(provinceId)
          .collection("cities").get();

        for (const cityDoc of citiesSnapshot.docs) {
          const cityId = cityDoc.id;
          const officesSnapshot = await db.collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").get();

          for (const officeDoc of officesSnapshot.docs) {
            const officeId = officeDoc.id;

            // 만료된 attributions 조회
            const expiredAttributions = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("attributions")
              .where("expiresAt", "<", new Date(now))
              .get();

            if (expiredAttributions.size > 0) {
              const batches = [];
              let batch = db.batch();
              let operationCount = 0;

              for (const doc of expiredAttributions.docs) {
                batch.delete(doc.ref);
                operationCount++;

                if (operationCount === 500) {
                  batches.push(batch.commit());
                  batch = db.batch();
                  operationCount = 0;
                }
              }

              if (operationCount > 0) {
                batches.push(batch.commit());
              }

              await Promise.all(batches);
              expiredCount += expiredAttributions.size;
              logger.info(`✅ ${provinceId}/${cityId}/${officeId}: 만료된 attributions ${expiredAttributions.size}개 삭제`);
            }
          }
        }
      }

      logger.info(`✅ 만료된 attributions 정리 완료: 총 ${expiredCount}개 삭제`);
      totalDeleted += expiredCount;

      logger.info(`✅ 자동 데이터 정리 완료 - 총 ${totalDeleted}개 삭제`);

    } catch (error) {
      logger.error("❌ 자동 데이터 정리 중 오류 발생:", error);
      throw error;
    }
  }
);

// =============================
// COMPLETED 콜 아카이브: 매일 오전 11시 실행
// - 7일 이상 된 COMPLETED 콜 중 50개 초과분만 아카이브
// - Cloud Storage에 월별 JSON Lines 파일로 저장
// =============================
export const archiveOldCalls = onSchedule(
  {
    schedule: "0 11 * * *", // 매일 오전 11시 (한국 시간)
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "1GiB",
    timeoutSeconds: 540
  },
  async (event) => {
    logger.info("📦 COMPLETED 콜 아카이브 시작...");

    const now = Date.now();
    const sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000);
    const KEEP_RECENT_COUNT = 50; // 최근 50개는 Firestore에 유지

    const db = admin.firestore();
    const bucket = admin.storage().bucket();

    let totalArchived = 0;
    let totalDeleted = 0;

    try {
      const provincesSnapshot = await db.collection("provinces").get();

      for (const provinceDoc of provincesSnapshot.docs) {
        const provinceId = provinceDoc.id;
        const citiesSnapshot = await db.collection("provinces").doc(provinceId)
          .collection("cities").get();

        for (const cityDoc of citiesSnapshot.docs) {
          const cityId = cityDoc.id;
          const officesSnapshot = await db.collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").get();

          for (const officeDoc of officesSnapshot.docs) {
            const officeId = officeDoc.id;
            const officeName = officeDoc.data().officeName || officeId;

            // 모든 COMPLETED 콜 조회 (최신순)
            const allCompletedCalls = await db.collection("provinces").doc(provinceId)
              .collection("cities").doc(cityId)
              .collection("offices").doc(officeId)
              .collection("calls")
              .where("status", "==", "COMPLETED")
              .orderBy("completedAt", "desc")
              .get();

            if (allCompletedCalls.size <= KEEP_RECENT_COUNT) {
              logger.info(`ℹ️ ${officeName}: COMPLETED 콜 ${allCompletedCalls.size}개 - 아카이브 불필요`);
              continue;
            }

            // 50개 초과 + 7일 이상 된 콜만 아카이브 대상
            const callsToArchive = allCompletedCalls.docs.slice(KEEP_RECENT_COUNT).filter(doc => {
              const completedAt = doc.data().completedAt?.toMillis() || doc.data().updatedAt?.toMillis() || 0;
              return completedAt < sevenDaysAgo;
            });

            if (callsToArchive.length === 0) {
              logger.info(`ℹ️ ${officeName}: 아카이브할 COMPLETED 콜 없음`);
              continue;
            }

            // Cloud Storage에 JSON Lines 형식으로 저장
            const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
            const archiveFileName = `archives/calls/${provinceId}/${cityId}/${officeId}/completed_${today}.jsonl`;

            const jsonLines = callsToArchive.map(doc => {
              const data = doc.data();
              return JSON.stringify({
                id: doc.id,
                ...data,
                completedAt: data.completedAt?.toMillis() || null,
                createdAt: data.createdAt?.toMillis() || null,
                updatedAt: data.updatedAt?.toMillis() || null,
                archivedAt: now
              });
            }).join('\n');

            // Cloud Storage에 저장
            const file = bucket.file(archiveFileName);
            await file.save(jsonLines, {
              metadata: {
                contentType: 'application/x-ndjson',
                metadata: {
                  provinceId,
                  cityId,
                  officeId,
                  officeName,
                  archivedAt: new Date().toISOString(),
                  callCount: callsToArchive.length.toString()
                }
              }
            });

            totalArchived += callsToArchive.length;
            logger.info(`📦 ${officeName}: ${callsToArchive.length}개 아카이브 완료 → ${archiveFileName}`);

            // Firestore에서 삭제
            const batches = [];
            let batch = db.batch();
            let operationCount = 0;

            for (const doc of callsToArchive) {
              batch.delete(doc.ref);
              operationCount++;

              if (operationCount === 500) {
                batches.push(batch.commit());
                batch = db.batch();
                operationCount = 0;
              }
            }

            if (operationCount > 0) {
              batches.push(batch.commit());
            }

            await Promise.all(batches);
            totalDeleted += callsToArchive.length;
            logger.info(`✅ ${officeName}: ${callsToArchive.length}개 Firestore에서 삭제 완료`);
          }
        }
      }

      logger.info(`✅ COMPLETED 콜 아카이브 완료 - 아카이브: ${totalArchived}개, 삭제: ${totalDeleted}개`);

    } catch (error) {
      logger.error("❌ COMPLETED 콜 아카이브 중 오류 발생:", error);
      throw error;
    }
  }
);

// =============================
// 아카이브 데이터 조회 API (총관리자앱용)
// =============================

/**
 * 아카이브된 콜 데이터 통계 조회
 *
 * @param data.startDate - 시작 날짜 (YYYY-MM-DD)
 * @param data.endDate - 종료 날짜 (YYYY-MM-DD)
 * @param data.provinceId - 도/광역시 ID (선택)
 * @param data.cityId - 시/군/구 ID (선택, provinceId 필요)
 * @param data.officeId - 사무실 ID (선택, provinceId, cityId 필요)
 *
 * @returns 기간별 통계 데이터
 */
export const getArchivedStats = onCall(
  {
    region: "asia-northeast3",
    cors: ["http://localhost:3000", "https://calldetector-5d61e.web.app", "https://calldetector-5d61e.firebaseapp.com"]
  },
  async (request) => {
    const { startDate, endDate, provinceId, cityId, officeId } = request.data;

    if (!startDate || !endDate) {
      throw new Error("startDate와 endDate는 필수입니다.");
    }

    logger.info(`📊 아카이브 통계 조회: ${startDate} ~ ${endDate}, province: ${provinceId || '전체'}, city: ${cityId || '전체'}, office: ${officeId || '전체'}`);

    try {
      const bucket = admin.storage().bucket();
      const db = admin.firestore();

      // 날짜 범위 생성
      const start = new Date(startDate);
      const end = new Date(endDate);
      const dates: string[] = [];

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        dates.push(d.toISOString().split('T')[0]);
      }

      logger.info(`📅 조회할 날짜: ${dates.length}일`);

      // 통계 집계
      const stats = {
        totalCalls: 0,
        totalFare: 0,
        totalDriverFee: 0,
        totalCommission: 0,
        officeStats: {} as Record<string, any>,
        dailyStats: {} as Record<string, any>
      };

      // 조회할 사무실 목록 결정
      let officesToQuery: Array<{provinceId: string, cityId: string, officeId: string, officeName: string}> = [];

      if (provinceId && cityId && officeId) {
        // 특정 사무실만
        const officeDoc = await db.collection('provinces').doc(provinceId)
          .collection('cities').doc(cityId)
          .collection('offices').doc(officeId).get();
        if (officeDoc.exists) {
          officesToQuery.push({
            provinceId,
            cityId,
            officeId,
            officeName: officeDoc.data()?.name || officeId
          });
        }
      } else {
        // 전체 사무실
        const provincesSnapshot = await db.collection('provinces').get();
        for (const provinceDoc of provincesSnapshot.docs) {
          const citiesSnapshot = await db.collection('provinces').doc(provinceDoc.id)
            .collection('cities').get();
          for (const cityDoc of citiesSnapshot.docs) {
            const officesSnapshot = await db.collection('provinces').doc(provinceDoc.id)
              .collection('cities').doc(cityDoc.id)
              .collection('offices').get();
            for (const officeDoc of officesSnapshot.docs) {
              officesToQuery.push({
                provinceId: provinceDoc.id,
                cityId: cityDoc.id,
                officeId: officeDoc.id,
                officeName: officeDoc.data().name || officeDoc.id
              });
            }
          }
        }
      }

      logger.info(`🏢 조회할 사무실: ${officesToQuery.length}개`);

      // 각 사무실별, 날짜별 아카이브 파일 읽기
      for (const office of officesToQuery) {
        const officeKey = `${office.provinceId}/${office.cityId}/${office.officeId}`;

        if (!stats.officeStats[officeKey]) {
          stats.officeStats[officeKey] = {
            officeName: office.officeName,
            totalCalls: 0,
            totalFare: 0,
            totalDriverFee: 0,
            totalCommission: 0
          };
        }

        for (const date of dates) {
          const filePath = `archives/calls/${office.provinceId}/${office.cityId}/${office.officeId}/completed_${date}.jsonl`;

          try {
            const file = bucket.file(filePath);
            const [exists] = await file.exists();

            if (!exists) {
              continue;
            }

            const [contents] = await file.download();
            const lines = contents.toString().split('\n').filter(line => line.trim());

            for (const line of lines) {
              const call = JSON.parse(line);

              stats.totalCalls++;
              stats.totalFare += call.fare || 0;
              stats.totalDriverFee += call.driverFee || 0;
              stats.totalCommission += call.commissionFee || 0;

              stats.officeStats[officeKey].totalCalls++;
              stats.officeStats[officeKey].totalFare += call.fare || 0;
              stats.officeStats[officeKey].totalDriverFee += call.driverFee || 0;
              stats.officeStats[officeKey].totalCommission += call.commissionFee || 0;

              if (!stats.dailyStats[date]) {
                stats.dailyStats[date] = {
                  totalCalls: 0,
                  totalFare: 0,
                  totalDriverFee: 0,
                  totalCommission: 0
                };
              }

              stats.dailyStats[date].totalCalls++;
              stats.dailyStats[date].totalFare += call.fare || 0;
              stats.dailyStats[date].totalDriverFee += call.driverFee || 0;
              stats.dailyStats[date].totalCommission += call.commissionFee || 0;
            }

          } catch (error: any) {
            if (error.code !== 404) {
              logger.warn(`파일 읽기 실패: ${filePath}`, error);
            }
          }
        }
      }

      logger.info(`✅ 통계 조회 완료: ${stats.totalCalls}개 콜`);

      return {
        success: true,
        period: { startDate, endDate },
        stats
      };

    } catch (error) {
      logger.error("❌ 아카이브 통계 조회 오류:", error);
      throw error;
    }
  }
);

/**
 * 아카이브된 콜 검색
 *
 * @param data.phoneNumber - 전화번호 (선택)
 * @param data.driverName - 기사명 (선택)
 * @param data.startDate - 시작 날짜 (필수)
 * @param data.endDate - 종료 날짜 (필수)
 * @param data.provinceId - 도/광역시 ID (선택)
 * @param data.cityId - 시/군/구 ID (선택)
 * @param data.officeId - 사무실 ID (선택)
 *
 * @returns 검색된 콜 목록
 */
export const searchArchivedCalls = onCall(
  {
    region: "asia-northeast3",
    cors: ["http://localhost:3000", "https://calldetector-5d61e.web.app", "https://calldetector-5d61e.firebaseapp.com"]
  },
  async (request) => {
    const { phoneNumber, driverName, startDate, endDate, provinceId, cityId, officeId } = request.data;

    if (!startDate || !endDate) {
      throw new Error("startDate와 endDate는 필수입니다.");
    }

    logger.info(`🔍 아카이브 검색: phone=${phoneNumber}, driver=${driverName}, ${startDate}~${endDate}`);

    try {
      const bucket = admin.storage().bucket();
      const db = admin.firestore();

      // 날짜 범위 생성
      const start = new Date(startDate);
      const end = new Date(endDate);
      const dates: string[] = [];

      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        dates.push(d.toISOString().split('T')[0]);
      }

      // 조회할 사무실 목록 결정
      let officesToQuery: Array<{provinceId: string, cityId: string, officeId: string, officeName: string}> = [];

      if (provinceId && cityId && officeId) {
        const officeDoc = await db.collection('provinces').doc(provinceId)
          .collection('cities').doc(cityId)
          .collection('offices').doc(officeId).get();
        if (officeDoc.exists) {
          officesToQuery.push({
            provinceId,
            cityId,
            officeId,
            officeName: officeDoc.data()?.name || officeId
          });
        }
      } else {
        const provincesSnapshot = await db.collection('provinces').get();
        for (const provinceDoc of provincesSnapshot.docs) {
          const citiesSnapshot = await db.collection('provinces').doc(provinceDoc.id)
            .collection('cities').get();
          for (const cityDoc of citiesSnapshot.docs) {
            const officesSnapshot = await db.collection('provinces').doc(provinceDoc.id)
              .collection('cities').doc(cityDoc.id)
              .collection('offices').get();
            for (const officeDoc of officesSnapshot.docs) {
              officesToQuery.push({
                provinceId: provinceDoc.id,
                cityId: cityDoc.id,
                officeId: officeDoc.id,
                officeName: officeDoc.data().name || officeDoc.id
              });
            }
          }
        }
      }

      const results: any[] = [];

      // 각 사무실, 날짜별 아카이브 검색
      for (const office of officesToQuery) {
        for (const date of dates) {
          const filePath = `archives/calls/${office.provinceId}/${office.cityId}/${office.officeId}/completed_${date}.jsonl`;

          try {
            const file = bucket.file(filePath);
            const [exists] = await file.exists();

            if (!exists) {
              continue;
            }

            const [contents] = await file.download();
            const lines = contents.toString().split('\n').filter(line => line.trim());

            for (const line of lines) {
              const call = JSON.parse(line);

              // 검색 조건 필터링
              let match = true;

              if (phoneNumber && call.phoneNumber !== phoneNumber) {
                match = false;
              }

              if (driverName && !call.assignedDriverName?.includes(driverName)) {
                match = false;
              }

              if (match) {
                results.push({
                  ...call,
                  officeName: office.officeName,
                  provinceId: office.provinceId,
                  cityId: office.cityId,
                  officeId: office.officeId
                });
              }
            }

          } catch (error: any) {
            if (error.code !== 404) {
              logger.warn(`파일 읽기 실패: ${filePath}`, error);
            }
          }
        }
      }

      logger.info(`✅ 검색 완료: ${results.length}개 결과`);

      // 완료 시간 기준 내림차순 정렬
      results.sort((a, b) => {
        const timeA = a.completedAt?._seconds || 0;
        const timeB = b.completedAt?._seconds || 0;
        return timeB - timeA;
      });

      return {
        success: true,
        results: results.slice(0, 100), // 최대 100개 반환
        totalCount: results.length
      };

    } catch (error) {
      logger.error("❌ 아카이브 검색 오류:", error);
      throw error;
    }
  }
);

/**
 * 사무실별 리포트 생성
 *
 * @param data.provinceId - 도/광역시 ID
 * @param data.cityId - 시/군/구 ID
 * @param data.officeId - 사무실 ID
 * @param data.year - 연도 (예: 2025)
 * @param data.month - 월 (1-12)
 *
 * @returns 월별 상세 리포트
 */
export const getOfficeReport = onCall(
  {
    region: "asia-northeast3",
    cors: ["http://localhost:3000", "https://calldetector-5d61e.web.app", "https://calldetector-5d61e.firebaseapp.com"]
  },
  async (request) => {
    const { provinceId, cityId, officeId, year, month } = request.data;

    if (!provinceId || !cityId || !officeId || !year || !month) {
      throw new Error("provinceId, cityId, officeId, year, month는 필수입니다.");
    }

    logger.info(`📋 사무실 리포트 생성: ${provinceId}/${cityId}/${officeId}, ${year}년 ${month}월`);

    try {
      const bucket = admin.storage().bucket();
      const db = admin.firestore();

      // 사무실 정보 조회
      const officeDoc = await db.collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId).get();

      if (!officeDoc.exists) {
        throw new Error("사무실을 찾을 수 없습니다.");
      }

      const officeData = officeDoc.data();

      // 해당 월의 날짜 범위 생성
      const startDate = new Date(year, month - 1, 1);
      const endDate = new Date(year, month, 0); // 마지막 날
      const dates: string[] = [];

      for (let d = new Date(startDate); d <= endDate; d.setDate(d.getDate() + 1)) {
        dates.push(d.toISOString().split('T')[0]);
      }

      // 리포트 데이터 구조
      const report = {
        office: {
          provinceId,
          cityId,
          officeId,
          officeName: officeData?.name || officeId,
          address: officeData?.address || 'N/A',
          phoneNumber: officeData?.phoneNumber || 'N/A'
        },
        period: {
          year,
          month,
          startDate: dates[0],
          endDate: dates[dates.length - 1]
        },
        summary: {
          totalCalls: 0,
          totalFare: 0,
          totalDriverFee: 0,
          totalCommission: 0,
          totalTips: 0
        },
        dailyData: [] as any[],
        driverStats: {} as Record<string, any>,
        hourlyDistribution: Array(24).fill(0)
      };

      // 각 날짜별 아카이브 읽기
      for (const date of dates) {
        const filePath = `archives/calls/${provinceId}/${cityId}/${officeId}/completed_${date}.jsonl`;

        let dailyCalls = 0;
        let dailyFare = 0;
        let dailyDriverFee = 0;
        let dailyCommission = 0;

        try {
          const file = bucket.file(filePath);
          const [exists] = await file.exists();

          if (!exists) {
            report.dailyData.push({
              date,
              calls: 0,
              fare: 0,
              driverFee: 0,
              commission: 0
            });
            continue;
          }

          const [contents] = await file.download();
          const lines = contents.toString().split('\n').filter(line => line.trim());

          for (const line of lines) {
            const call = JSON.parse(line);

            dailyCalls++;
            dailyFare += call.fare || 0;
            dailyDriverFee += call.driverFee || 0;
            dailyCommission += call.commissionFee || 0;

            report.summary.totalCalls++;
            report.summary.totalFare += call.fare || 0;
            report.summary.totalDriverFee += call.driverFee || 0;
            report.summary.totalCommission += call.commissionFee || 0;
            report.summary.totalTips += call.tipAmount || 0;

            // 기사별 통계
            const driverName = call.assignedDriverName || '미배정';
            if (!report.driverStats[driverName]) {
              report.driverStats[driverName] = {
                totalCalls: 0,
                totalFare: 0,
                totalDriverFee: 0
              };
            }
            report.driverStats[driverName].totalCalls++;
            report.driverStats[driverName].totalFare += call.fare || 0;
            report.driverStats[driverName].totalDriverFee += call.driverFee || 0;

            // 시간대별 분포
            if (call.completedAt?._seconds) {
              const hour = new Date(call.completedAt._seconds * 1000).getHours();
              report.hourlyDistribution[hour]++;
            }
          }

        } catch (error: any) {
          if (error.code !== 404) {
            logger.warn(`파일 읽기 실패: ${filePath}`, error);
          }
        }

        report.dailyData.push({
          date,
          calls: dailyCalls,
          fare: dailyFare,
          driverFee: dailyDriverFee,
          commission: dailyCommission
        });
      }

      logger.info(`✅ 리포트 생성 완료: ${report.summary.totalCalls}개 콜`);

      return {
        success: true,
        report
      };

    } catch (error) {
      logger.error("❌ 사무실 리포트 생성 오류:", error);
      throw error;
    }
  }
);

// ====== 기사 상태 변경 알림 ======
/**
 * 기사 상태 변경 시 콜매니저로 FCM 알림 전송
 * - 로그인/로그아웃
 * - 배차 수락/거절
 * - 운행 시작 (ONLINE -> DRIVING)
 * - 운행 완료 (DRIVING -> WAITING)
 */
export const onDriverStatusChange = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}"
  },
  async (event: any) => {
    const {provinceId, cityId, officeId, driverId} = event.params;

    if (!event.data || !event.data.after || !event.data.after.exists) {
      logger.info(`[기사상태] ${driverId}: 데이터 없음`);
      return;
    }

    const beforeData = event.data.before?.data();
    const afterData = event.data.after.data();

    // 상태 변경 확인
    const statusChanged = beforeData?.status !== afterData?.status;

    if (!statusChanged) {
      // 상태 변경이 없으면 알림 보내지 않음
      return;
    }

    const oldStatus = beforeData?.status || "UNKNOWN";
    const newStatus = afterData?.status || "UNKNOWN";
    const driverName = afterData?.name || "기사";
    const authUid = afterData?.authUid || driverId;  // authUid 추가 (없으면 driverId 사용)

    logger.info(`[기사상태] ${driverId} (${driverName}): ${oldStatus} -> ${newStatus}`);

    try {
      // 관리자 토큰 가져오기
      const managerTokensSnapshot = await admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("managerTokens")
        .get();

      if (managerTokensSnapshot.empty) {
        logger.warn(`[기사상태] ${driverId}: 관리자 토큰 없음`);
        return;
      }

      const managerTokens: string[] = [];
      managerTokensSnapshot.forEach((doc) => {
        const token = doc.data().fcmToken;
        if (token) managerTokens.push(token);
      });

      if (managerTokens.length === 0) {
        logger.warn(`[기사상태] ${driverId}: 유효한 관리자 토큰 없음`);
        return;
      }

      // 상태 메시지 생성
      let statusMessage = "";
      switch (newStatus) {
        case "ONLINE":
          statusMessage = "대기중";
          break;
        case "OFFLINE":
          statusMessage = "오프라인";
          break;
        case "WAITING":
          statusMessage = "대기중";
          break;
        case "DRIVING":
          statusMessage = "운행중";
          break;
        case "ASSIGNED":
          statusMessage = "배차됨";
          break;
        default:
          statusMessage = newStatus;
      }

      // FCM 메시지 생성
      const message = {
        data: {
          type: "DRIVER_STATUS_UPDATE",
          driverId: driverId,
          driverName: driverName,
          authUid: authUid,
          newStatus: newStatus,
          oldStatus: oldStatus,
          statusMessage: statusMessage,
          provinceId: provinceId,
          cityId: cityId,
          officeId: officeId,
          timestamp: Date.now().toString()
        },
        android: {
          priority: "high" as const,
          ttl: 60000
        },
        tokens: managerTokens
      };

      // FCM 전송
      const response = await admin.messaging().sendEachForMulticast(message);

      logger.info(`[기사상태] ${driverId}: FCM 전송 완료 - 성공: ${response.successCount}, 실패: ${response.failureCount}`);

      // 실패한 토큰 로그
      if (response.failureCount > 0) {
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            logger.error(`[기사상태] ${driverId}: 토큰 ${managerTokens[idx]} 전송 실패 - ${resp.error}`);
          }
        });
      }

    } catch (error) {
      logger.error(`[기사상태] ${driverId}: FCM 전송 오류`, error);
    }
  }
);

// =============================
// 정산 세션 자동 업데이트: 콜 완료 시 정산 세션에 추가
// =============================
export const onCallCompletedUpdateSettlement = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { provinceId, cityId, officeId, callId } = event.params;

    if (!event.data || !event.data.before || !event.data.after) {
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      return;
    }

    // 운행 완료 감지 (다른 상태 → COMPLETED)
    if (beforeData.status !== "COMPLETED" && afterData.status === "COMPLETED") {
      logger.info(`[Settlement:${callId}] 운행 완료 감지 - 정산 세션 업데이트 시작`);

      try {
        await addCallToSettlementSession(provinceId, cityId, officeId, afterData, callId);
        logger.info(`[Settlement:${callId}] 정산 세션 업데이트 완료`);
      } catch (error) {
        logger.error(`[Settlement:${callId}] 정산 세션 업데이트 실패:`, error);
      }
    }
  }
);

// =============================
// 일일 정산 자동 마감: 매일 새벽 6시 10분 실행
// - 전날 정산 세션을 자동으로 마감 처리
// =============================
export const autoFinalizeSettlements = onSchedule(
  {
    schedule: "10 6 * * *", // 매일 새벽 6시 10분 (한국 시간)
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "512MiB",
    timeoutSeconds: 300
  },
  async () => {
    logger.info("[Settlement] Starting scheduled auto-finalization");

    try {
      const result = await autoFinalizeSettlementSessions();
      logger.info(`[Settlement] Auto-finalization completed. Processed: ${result.processed}, Errors: ${result.errors.length}`);

      if (result.errors.length > 0) {
        logger.warn("[Settlement] Auto-finalization errors:", result.errors);
      }
    } catch (error) {
      logger.error("[Settlement] Auto-finalization failed:", error);
    }
  }
);

// =============================
// 정산 불일치 검사: 매일 오전 7시 실행
// - 전날 정산 데이터 불일치 확인 및 알림
// =============================
export const checkSettlementDiscrepanciesScheduled = onSchedule(
  {
    schedule: "0 7 * * *", // 매일 오전 7시 (한국 시간)
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "512MiB",
    timeoutSeconds: 540
  },
  async () => {
    const db = admin.firestore();
    logger.info("[Settlement] Starting scheduled discrepancy check");

    // 어제 근무일 계산
    const yesterdayDate = getYesterdayWorkDate();

    try {
      const provincesSnap = await db.collection("provinces").get();

      for (const provinceDoc of provincesSnap.docs) {
        const citiesSnap = await provinceDoc.ref.collection("cities").get();

        for (const cityDoc of citiesSnap.docs) {
          const officesSnap = await cityDoc.ref.collection("offices").get();

          for (const officeDoc of officesSnap.docs) {
            try {
              const result = await checkSettlementDiscrepancies(
                provinceDoc.id,
                cityDoc.id,
                officeDoc.id,
                yesterdayDate
              );

              if (result.hasDiscrepancy) {
                logger.warn(`[Settlement] Discrepancies found for ${provinceDoc.id}/${cityDoc.id}/${officeDoc.id}:`, result.details);

                // 불일치 발견 시 관리자에게 알림
                await notifySettlementDiscrepancy(
                  provinceDoc.id,
                  cityDoc.id,
                  officeDoc.id,
                  yesterdayDate,
                  result.details
                );
              }
            } catch (officeError) {
              logger.error(`[Settlement] Discrepancy check failed for ${provinceDoc.id}/${cityDoc.id}/${officeDoc.id}:`, officeError);
            }
          }
        }
      }

      logger.info("[Settlement] Discrepancy check completed");
    } catch (error) {
      logger.error("[Settlement] Discrepancy check failed:", error);
    }
  }
);

// =============================
// 수동 정산 불일치 검사 (HTTP Callable)
// =============================
export const manualCheckSettlementDiscrepancy = onCall(
  {
    region: "asia-northeast3"
  },
  async (req) => {
    const { provinceId, cityId, officeId, sessionDate } = req.data || {};

    if (!provinceId || !cityId || !officeId || !sessionDate) {
      throw new Error("provinceId, cityId, officeId, sessionDate are required");
    }

    if (!req.auth) {
      throw new Error("Must be authenticated");
    }

    logger.info(`[Settlement] Manual discrepancy check: ${provinceId}/${cityId}/${officeId}/${sessionDate}`);

    const result = await checkSettlementDiscrepancies(provinceId, cityId, officeId, sessionDate);

    return result;
  }
);

const _forceDeploy = Date.now() + 1000007; // 배포 강제용 더미 변수
void _forceDeploy;                 // 사용해서 컴파일 경고 해소

// =============================
// 기사 배차 알림 (Callable Function)
// 콜매니저에서 배차 시 직접 호출
// =============================
export const notifyDriverAssignment = onCall(
  {
    region: "asia-northeast3",
  },
  async (request) => {
    const { callId, driverAuthUid, provinceId, cityId, officeId, customerName, departure } = request.data;

    logger.info(`[notifyDriverAssignment] 호출됨 - callId: ${callId}, driverAuthUid: ${driverAuthUid}`);

    if (!callId || !driverAuthUid || !provinceId || !cityId || !officeId) {
      logger.error("[notifyDriverAssignment] 필수 파라미터 누락");
      return { success: false, error: "Missing required parameters" };
    }

    try {
      // 기사 문서 직접 조회 (문서 ID = authUid)
      const driverDocRef = admin.firestore()
        .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}/${DRIVER_COLLECTION_NAME}/${driverAuthUid}`);

      const driverDoc = await driverDocRef.get();

      if (!driverDoc.exists) {
        logger.error(`[notifyDriverAssignment] 기사 문서 없음 - docId: ${driverAuthUid}`);
        return { success: false, error: "Driver not found" };
      }

      const driverData = driverDoc.data();
      const fcmToken = driverData?.fcmToken;
      const driverName = driverData?.name || "기사";

      logger.info(`[notifyDriverAssignment] 기사 정보 - name: ${driverName}, token: ${fcmToken ? "exists" : "NONE"}`);

      if (!fcmToken) {
        logger.warn(`[notifyDriverAssignment] FCM 토큰 없음 - ${driverName}`);
        return { success: false, error: "No FCM token" };
      }

      // FCM 전송
      const payload = {
        data: {
          callId: callId,
          type: "call_assigned",
          title: "새로운 콜 배정",
          body: customerName ? `${customerName}님 콜이 배정되었습니다.` : "새로운 콜이 배정되었습니다.",
          departure: departure || "",
        },
        android: {
          priority: "high" as const,
        },
        token: fcmToken,
      };

      await admin.messaging().send(payload);
      logger.info(`[notifyDriverAssignment] FCM 전송 성공 - ${driverName}`);

      return { success: true, driverName: driverName };

    } catch (error) {
      logger.error("[notifyDriverAssignment] 오류:", error);
      return { success: false, error: String(error) };
    }
  }
);

// =============================
// 기사 배차 취소 알림 (Callable Function)
// 콜매니저에서 콜 취소 시 직접 호출
// =============================
export const notifyDriverCancellation = onCall(
  {
    region: "asia-northeast3",
  },
  async (request) => {
    const { callId, driverAuthUid, provinceId, cityId, officeId } = request.data;

    logger.info(`[notifyDriverCancellation] 호출됨 - callId: ${callId}, driverAuthUid: ${driverAuthUid}`);

    if (!callId || !driverAuthUid || !provinceId || !cityId || !officeId) {
      logger.error("[notifyDriverCancellation] 필수 파라미터 누락");
      return { success: false, error: "Missing required parameters" };
    }

    try {
      const driverDocRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("designated_drivers").doc(driverAuthUid);

      const driverDoc = await driverDocRef.get();

      if (!driverDoc.exists) {
        logger.error(`[notifyDriverCancellation] 기사 문서 없음 - docId: ${driverAuthUid}`);
        return { success: false, error: "Driver not found" };
      }

      const driverData = driverDoc.data();
      const fcmToken = driverData?.fcmToken;
      const driverName = driverData?.name || "기사";

      logger.info(`[notifyDriverCancellation] 기사 정보 - name: ${driverName}, token: ${fcmToken ? "exists" : "NONE"}`);

      if (!fcmToken) {
        logger.warn(`[notifyDriverCancellation] FCM 토큰 없음 - ${driverName}`);
        return { success: false, error: "No FCM token" };
      }

      const payload = {
        data: {
          callId: callId,
          type: "call_cancelled",
          title: "배차 취소",
          body: "배정된 콜이 취소되었습니다.",
        },
        android: {
          priority: "high" as const,
        },
        token: fcmToken,
      };

      await admin.messaging().send(payload);
      logger.info(`[notifyDriverCancellation] FCM 전송 성공 - ${driverName}`);

      return { success: true, driverName: driverName };

    } catch (error) {
      logger.error("[notifyDriverCancellation] 오류:", error);
      return { success: false, error: String(error) };
    }
  }
);

/**
 * 업무 마감 및 로그인 상태 기사에게 알림 전송
 * Call Manager에서 업무 마감 시 호출
 */
export const finalizeSettlementAndNotifyDrivers = onCall(
  {
    region: "asia-northeast3",
  },
  async (request) => {
    const { provinceId, cityId, officeId, sessionDate } = request.data;

    logger.info(`[finalizeSettlement] 호출됨 - ${provinceId}/${cityId}/${officeId}, date: ${sessionDate}`);

    if (!provinceId || !cityId || !officeId) {
      logger.error("[finalizeSettlement] 필수 파라미터 누락");
      return { success: false, error: "Missing required parameters" };
    }

    // 세션 날짜가 없으면 오늘 근무일 사용
    const targetDate = sessionDate || getTodayWorkDate();

    try {
      const db = admin.firestore();
      const sessionRef = db.collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("settlementSessions").doc(targetDate);

      const sessionDoc = await sessionRef.get();

      if (!sessionDoc.exists) {
        logger.warn(`[finalizeSettlement] 세션 없음 - ${targetDate}`);
        return { success: false, error: "Settlement session not found" };
      }

      const session = sessionDoc.data();
      const wasAlreadyFinalized = session?.metadata?.isFinalized === true;

      if (wasAlreadyFinalized) {
        logger.info(`[finalizeSettlement] 재마감 요청 - ${targetDate}`);
      }

      // 세션 마감 처리 (재마감도 허용)
      const finalizeTimestamp = Timestamp.now();
      await sessionRef.update({
        "metadata.isFinalized": true,
        "metadata.version": (session?.metadata?.version || 0) + 1,
        "metadata.lastUpdatedAt": finalizeTimestamp,
        "metadata.lastUpdatedBy": "call_manager_finalize"
      });

      // offices 문서에 마감 시점 저장 (기사앱에서 당일 데이터 필터링용)
      const officeRef = db.collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId);
      await officeRef.update({
        settlementLastCleared: finalizeTimestamp
      });

      logger.info(`[finalizeSettlement] 세션 마감 완료 - ${targetDate}, settlementLastCleared 설정됨`);

      // 로그인 상태 기사에게만 알림 전송
      const totals = session?.totals || {
        callCount: 0,
        totalFare: 0,
        totalDeposit: 0,
        totalDriverShare: 0,
        totalCash: 0,
        totalCard: 0,
        totalCredit: 0,
        totalPoints: 0
      };

      const notifyResult = await notifyDriversSettlementFinalized(
        provinceId,
        cityId,
        officeId,
        targetDate,
        totals
      );

      logger.info(`[finalizeSettlement] 알림 전송 완료 - sent: ${notifyResult.sent}, skipped: ${notifyResult.skipped}`);

      return {
        success: true,
        sessionDate: targetDate,
        sent: notifyResult.sent,
        skipped: notifyResult.skipped,
        totals: totals,
        wasRefinalized: wasAlreadyFinalized
      };

    } catch (error) {
      logger.error("[finalizeSettlement] 오류:", error);
      return { success: false, error: String(error) };
    }
  }
);

/**
 * 기사에게 알림 전송 (범용)
 * Call Manager에서 CarryOver 이체 등 알림 시 호출
 */
export const sendDriverNotification = onCall(
  {
    region: "asia-northeast3",
  },
  async (request) => {
    const { driverId, provinceId, cityId, officeId, type, title, body } = request.data;

    logger.info(`[sendDriverNotification] 호출됨 - driverId: ${driverId}, type: ${type}`);

    if (!driverId || !provinceId || !cityId || !officeId || !type || !title || !body) {
      logger.error("[sendDriverNotification] 필수 파라미터 누락");
      return { success: false, error: "Missing required parameters" };
    }

    try {
      // 기사 문서 조회 (문서 ID = driverId = authUid)
      const driverDocRef = admin.firestore()
        .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}/${DRIVER_COLLECTION_NAME}/${driverId}`);

      const driverDoc = await driverDocRef.get();

      if (!driverDoc.exists) {
        logger.error(`[sendDriverNotification] 기사 문서 없음 - docId: ${driverId}`);
        return { success: false, error: "Driver not found" };
      }

      const driverData = driverDoc.data();
      const fcmToken = driverData?.fcmToken;
      const driverName = driverData?.name || "기사";

      logger.info(`[sendDriverNotification] 기사 정보 - name: ${driverName}, token: ${fcmToken ? "exists" : "NONE"}`);

      if (!fcmToken) {
        logger.warn(`[sendDriverNotification] FCM 토큰 없음 - ${driverName}`);
        return { success: false, error: "No FCM token" };
      }

      // FCM 전송
      const payload = {
        data: {
          type: type,
          title: title,
          body: body,
        },
        android: {
          priority: "high" as const,
        },
        token: fcmToken,
      };

      await admin.messaging().send(payload);
      logger.info(`[sendDriverNotification] FCM 전송 성공 - ${driverName}, type: ${type}`);

      return { success: true, driverName: driverName };

    } catch (error) {
      logger.error("[sendDriverNotification] 오류:", error);
      return { success: false, error: String(error) };
    }
  }
);

/**
 * 매니저가 정산 확인/거절 시 해당 기사에게 FCM 전송
 */
export const notifyDriverSettlementResult = onCall(
  {
    region: "asia-northeast3",
  },
  async (request) => {
    return notifyDriverSettlementResultHandler(request.data);
  }
);