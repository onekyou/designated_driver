"use strict";
/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.onCallDetectorCrash = exports.onCustomerCountChange = exports.onDriverCountChange = exports.onNewCustomerRegistered = exports.onCallCancelledByDriver = exports.claimToken = exports.matchByToken = exports.saveManualAttribution = exports.matchAttribution = exports.testFcmMessage = exports.migratePickupDrivers = exports.onSharedCallCompleted = exports.onSharedCallStatusSync = exports.onDriverSignupRequest = exports.onCallStatusChanged = exports.oncallreserved = exports.notifyCustomerOnComplete = exports.notifyCustomerOnPhoneCall = exports.onSharedCallCancelledByDriver = exports.onSharedCallClaimed = exports.notifyCustomerOnOfficeClosed = exports.onSharedCallCreated = exports.sendNewCallNotification = exports.oncallassigned = exports.handleFailedNotifications = exports.retryPendingNotifications = exports.acknowledgeNotification = exports.recoverCustomerAccount = exports.checkPhoneNumberDuplicate = exports.migrateExistingOfficesWallet = exports.transcribePtt = exports.parseReservation = exports.sendPttPreWake = exports.sendPttWake = exports.generateAgoraToken = exports.processWithdrawal = exports.processDeposit = exports.submitWithdrawalRequest = exports.notifyRestaurantOnNoResponse = exports.createSharedCallFromRestaurant = exports.redeemRestaurantInviteCode = exports.generateRestaurantInviteCode = exports.onChatSyncAdminRemoval = exports.onChatSyncPickupDriver = exports.onChatSyncDesignatedDriver = exports.backfillChatMembers = exports.scheduledChatMessageCleanup = exports.onChatMessageCreated = exports.aggregateMonthlyStats = exports.checkSingleCallAssignedTimeout = void 0;
exports.homepageGate = exports.getApkDownloadUrl = exports.rejectOfficeApplication = exports.registerOwner = exports.redeemDownloadToken = exports.approveOfficeApplication = exports.submitOfficeApplication = exports.onDriverSettlementSubmitted = exports.sendDriverNotification = exports.finalizeSettlementAndNotifyDrivers = exports.notifyDriverCancellation = exports.notifyDriverAssignment = exports.manualCheckSettlementDiscrepancy = exports.autoFinalizeSettlements = exports.onCallCompletedUpdateSettlement = exports.onDriverStatusChange = exports.getOfficeReport = exports.searchArchivedCalls = exports.getArchivedStats = exports.archiveOldCalls = exports.scheduledDataCleanup = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const https_1 = require("firebase-functions/v2/https");
const scheduler_1 = require("firebase-functions/v2/scheduler");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
const logger = __importStar(require("firebase-functions/logger"));
const points_1 = require("./handlers/points");
const settlement_1 = require("./handlers/settlement");
const fcmPayload_1 = require("./utils/fcmPayload");
const acceptanceEvents_1 = require("./analytics/acceptanceEvents");
const timeout_1 = require("./handlers/timeout");
// === timeout.ts export — 분기 1만 활성, 2/3은 휴면 (콜마당 간소화 2026-05-05) ===
//   분기 2/3 휴면 사유: 콜·기사 데이터는 driver_app 자동 fetch + 매니저 listener 로
//   자동 복구되어 시스템 무결성 보장됨. 매니저 알림은 양평 1곳 사람 운영으로 대체.
//   재활성화: 아래 두 라인 주석 풀고 firebase deploy.
var timeout_2 = require("./handlers/timeout");
Object.defineProperty(exports, "checkSingleCallAssignedTimeout", { enumerable: true, get: function () { return timeout_2.checkSingleCallAssignedTimeout; } });
var aggregateMonthly_1 = require("./analytics/aggregateMonthly");
Object.defineProperty(exports, "aggregateMonthlyStats", { enumerable: true, get: function () { return aggregateMonthly_1.aggregateMonthlyStats; } });
var chat_1 = require("./handlers/chat");
Object.defineProperty(exports, "onChatMessageCreated", { enumerable: true, get: function () { return chat_1.onChatMessageCreated; } });
Object.defineProperty(exports, "scheduledChatMessageCleanup", { enumerable: true, get: function () { return chat_1.scheduledChatMessageCleanup; } });
Object.defineProperty(exports, "backfillChatMembers", { enumerable: true, get: function () { return chat_1.backfillChatMembers; } });
Object.defineProperty(exports, "onChatSyncDesignatedDriver", { enumerable: true, get: function () { return chat_1.onChatSyncDesignatedDriver; } });
Object.defineProperty(exports, "onChatSyncPickupDriver", { enumerable: true, get: function () { return chat_1.onChatSyncPickupDriver; } });
Object.defineProperty(exports, "onChatSyncAdminRemoval", { enumerable: true, get: function () { return chat_1.onChatSyncAdminRemoval; } });
var restaurant_1 = require("./handlers/restaurant");
Object.defineProperty(exports, "generateRestaurantInviteCode", { enumerable: true, get: function () { return restaurant_1.generateRestaurantInviteCode; } });
Object.defineProperty(exports, "redeemRestaurantInviteCode", { enumerable: true, get: function () { return restaurant_1.redeemRestaurantInviteCode; } });
Object.defineProperty(exports, "createSharedCallFromRestaurant", { enumerable: true, get: function () { return restaurant_1.createSharedCallFromRestaurant; } });
Object.defineProperty(exports, "notifyRestaurantOnNoResponse", { enumerable: true, get: function () { return restaurant_1.notifyRestaurantOnNoResponse; } });
var wallet_1 = require("./handlers/wallet");
Object.defineProperty(exports, "submitWithdrawalRequest", { enumerable: true, get: function () { return wallet_1.submitWithdrawalRequest; } });
Object.defineProperty(exports, "processDeposit", { enumerable: true, get: function () { return wallet_1.processDeposit; } });
Object.defineProperty(exports, "processWithdrawal", { enumerable: true, get: function () { return wallet_1.processWithdrawal; } });
var ptt_1 = require("./handlers/ptt");
Object.defineProperty(exports, "generateAgoraToken", { enumerable: true, get: function () { return ptt_1.generateAgoraToken; } });
Object.defineProperty(exports, "sendPttWake", { enumerable: true, get: function () { return ptt_1.sendPttWake; } });
Object.defineProperty(exports, "sendPttPreWake", { enumerable: true, get: function () { return ptt_1.sendPttPreWake; } });
var reservation_1 = require("./handlers/reservation");
Object.defineProperty(exports, "parseReservation", { enumerable: true, get: function () { return reservation_1.parseReservation; } });
var pttStt_1 = require("./handlers/pttStt");
Object.defineProperty(exports, "transcribePtt", { enumerable: true, get: function () { return pttStt_1.transcribePtt; } });
var migrateExistingOfficesWallet_1 = require("./scripts/migrateExistingOfficesWallet");
Object.defineProperty(exports, "migrateExistingOfficesWallet", { enumerable: true, get: function () { return migrateExistingOfficesWallet_1.migrateExistingOfficesWallet; } });
const chat_2 = require("./handlers/chat");
const express_1 = __importDefault(require("express"));
const express_basic_auth_1 = __importDefault(require("express-basic-auth"));
const nodePath = __importStar(require("node:path"));
const params_1 = require("firebase-functions/params");
const HOMEPAGE_USER = (0, params_1.defineSecret)("HOMEPAGE_USER");
const HOMEPAGE_PASS = (0, params_1.defineSecret)("HOMEPAGE_PASS");
// Firebase Admin SDK 초기화
admin.initializeApp({
    databaseURL: "https://calldetector-5d61e-default-rtdb.firebaseio.com",
    storageBucket: "calldetector-5d61e.firebasestorage.app",
});
const DRIVER_COLLECTION_NAME = "designated_drivers";
/**
 * 알림 상태 저장 (FCM 전송 시 호출)
 */
async function saveNotificationStatus(notificationId, type, targetId, targetType, officeId, provinceId, cityId, fcmToken, payload, callId) {
    try {
        const notificationData = {
            id: notificationId,
            type,
            targetId,
            targetType,
            callId,
            officeId,
            provinceId,
            cityId,
            status: "pending",
            sentAt: firestore_2.Timestamp.now(),
            retryCount: 0,
            fcmToken,
            payload
        };
        await admin.firestore()
            .collection("notifications")
            .doc(notificationId)
            .set(notificationData);
        logger.info(`[ACK] 알림 상태 저장: ${notificationId}, type=${type}, target=${targetId}`);
    }
    catch (error) {
        logger.error(`[ACK] 알림 상태 저장 실패: ${notificationId}`, error);
    }
}
/**
 * 전화번호 중복 체크 (손님앱 프로필 설정 시 호출)
 * - 해당 사무실의 customers 컬렉션에서 동일 전화번호 존재 여부 확인
 * - 재설치 시 기존 계정 복구를 위한 SMS 인증 트리거용
 */
exports.checkPhoneNumberDuplicate = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    const { phoneNumber, provinceId, cityId, officeId, currentUid } = request.data;
    if (!phoneNumber || !provinceId || !cityId || !officeId) {
        logger.error("[checkPhoneNumberDuplicate] 필수 파라미터 누락", request.data);
        return { success: false, error: "Missing required parameters" };
    }
    try {
        const customersRef = admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("customers");
        const snapshot = await customersRef
            .where("phoneNumber", "==", phoneNumber)
            .limit(1)
            .get();
        if (!snapshot.empty) {
            const existingUid = snapshot.docs[0].data().id;
            if (existingUid !== currentUid) {
                logger.info(`[checkPhoneNumberDuplicate] 중복 감지: ${phoneNumber} (기존 UID: ${existingUid}, 현재 UID: ${currentUid})`);
                return { success: true, isDuplicate: true };
            }
        }
        return { success: true, isDuplicate: false };
    }
    catch (error) {
        logger.error("[checkPhoneNumberDuplicate] 오류:", error);
        return { success: false, error: String(error) };
    }
});
/**
 * 계정 복구 (재설치 후 SMS 인증 성공 시 호출)
 * - 기존 customers 문서를 새 UID로 이관
 * - 기존 문서 삭제 + customerInfo FCM 토큰 갱신
 */
exports.recoverCustomerAccount = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    const { phoneNumber, newUid, provinceId, cityId, officeId, fcmToken } = request.data;
    if (!phoneNumber || !newUid || !provinceId || !cityId || !officeId) {
        logger.error("[recoverCustomerAccount] 필수 파라미터 누락", request.data);
        return { success: false, error: "Missing required parameters" };
    }
    try {
        const customersRef = admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("customers");
        // 기존 계정 찾기
        const snapshot = await customersRef
            .where("phoneNumber", "==", phoneNumber)
            .limit(1)
            .get();
        if (snapshot.empty) {
            logger.warn(`[recoverCustomerAccount] 기존 계정 없음: ${phoneNumber}`);
            return { success: false, error: "No existing account found" };
        }
        const oldDoc = snapshot.docs[0];
        const oldData = oldDoc.data();
        const oldUid = oldData.id;
        if (oldUid === newUid) {
            logger.info(`[recoverCustomerAccount] 동일 UID, 복구 불필요: ${newUid}`);
            return { success: true, recovered: false };
        }
        // 기존 데이터를 새 UID 문서로 복사
        const updatedData = Object.assign(Object.assign({}, oldData), { id: newUid, authProvider: "phone", lastActiveAt: firestore_2.Timestamp.now() });
        await customersRef.doc(newUid).set(updatedData);
        await oldDoc.ref.delete();
        logger.info(`[recoverCustomerAccount] 계정 복구 완료: ${oldUid} → ${newUid}`);
        // customerInfo FCM 토큰 갱신
        if (fcmToken) {
            await admin.firestore()
                .collection("provinces").doc(provinceId)
                .collection("cities").doc(cityId)
                .collection("offices").doc(officeId)
                .collection("customerInfo").doc(phoneNumber)
                .set({
                fcmToken: fcmToken,
                phoneNumber: phoneNumber,
                updatedAt: firestore_2.Timestamp.now(),
            }, { merge: true });
            logger.info(`[recoverCustomerAccount] FCM 토큰 갱신 완료: ${phoneNumber}`);
        }
        return { success: true, recovered: true };
    }
    catch (error) {
        logger.error("[recoverCustomerAccount] 오류:", error);
        return { success: false, error: String(error) };
    }
});
/**
 * 알림 도착 ACK 처리 (앱에서 호출)
 */
exports.acknowledgeNotification = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
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
            deliveredAt: firestore_2.Timestamp.now()
        });
        logger.info(`[ACK] 알림 도착 확인: ${notificationId}`);
        return { success: true };
    }
    catch (error) {
        logger.error(`[ACK] 알림 ACK 처리 실패: ${notificationId}`, error);
        return { success: false, error: String(error) };
    }
});
/**
 * 미전달 알림 재전송 스케줄러 (10초마다 실행)
 */
exports.retryPendingNotifications = (0, scheduler_1.onSchedule)({
    region: "asia-northeast3",
    schedule: "every 1 minutes", // 최소 1분 간격 (Cloud Scheduler 제한)
    timeZone: "Asia/Seoul"
}, async () => {
    const tenSecondsAgo = firestore_2.Timestamp.fromMillis(Date.now() - 10000 // 10초 전
    );
    try {
        // pending 상태이고 10초 이상 지난 알림 조회
        const pendingNotifications = await admin.firestore()
            .collection("notifications")
            .where("status", "==", "pending")
            .where("sentAt", "<", tenSecondsAgo)
            .where("retryCount", "<", 2) // 최대 2회 재시도
            .limit(50)
            .get();
        if (pendingNotifications.empty) {
            logger.info("[ACK] 재전송 대상 알림 없음");
            return;
        }
        logger.info(`[ACK] 재전송 대상: ${pendingNotifications.size}건`);
        const batch = admin.firestore().batch();
        const fcmPromises = [];
        for (const doc of pendingNotifications.docs) {
            const notification = doc.data();
            // FCM 재전송
            const fcmPromise = admin.messaging().send(Object.assign(Object.assign({}, notification.payload), { token: notification.fcmToken })).then(() => {
                logger.info(`[ACK] 재전송 성공: ${notification.id}`);
            }).catch((error) => {
                logger.error(`[ACK] 재전송 실패: ${notification.id}`, error);
            });
            fcmPromises.push(fcmPromise);
            // 재시도 횟수 증가
            batch.update(doc.ref, {
                retryCount: notification.retryCount + 1,
                lastRetryAt: firestore_2.Timestamp.now()
            });
        }
        await Promise.all([batch.commit(), ...fcmPromises]);
    }
    catch (error) {
        logger.error("[ACK] 재전송 스케줄러 오류", error);
    }
});
/**
 * 3회 실패 알림 처리 (콜매니저에 경고)
 */
exports.handleFailedNotifications = (0, scheduler_1.onSchedule)({
    region: "asia-northeast3",
    schedule: "every 1 minutes",
    timeZone: "Asia/Seoul"
}, async () => {
    var _a;
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
            const notification = doc.data();
            // Realtime DB에서 대상 Presence 확인
            let presenceStatus = "unknown";
            try {
                const presencePath = `presence/${notification.targetType}s/${notification.targetId}`;
                const presenceSnapshot = await admin.database().ref(presencePath).get();
                presenceStatus = ((_a = presenceSnapshot.val()) === null || _a === void 0 ? void 0 : _a.status) || "offline";
            }
            catch (e) {
                logger.warn(`[ACK] Presence 조회 실패: ${notification.targetId}`);
            }
            // 콜매니저에게 경고 알림 전송
            if (notification.targetType === "driver" && notification.callId) {
                await sendNotificationFailureAlert(notification.provinceId, notification.cityId, notification.officeId, notification.callId, notification.targetId, presenceStatus);
            }
            // 상태를 failed로 업데이트
            await doc.ref.update({
                status: "failed",
                failureReason: `Presence: ${presenceStatus}`
            });
        }
    }
    catch (error) {
        logger.error("[ACK] 실패 알림 처리 오류", error);
    }
});
/**
 * 콜매니저에게 알림 전달 실패 경고 전송
 */
async function sendNotificationFailureAlert(provinceId, cityId, officeId, callId, driverId, presenceStatus) {
    var _a;
    try {
        // 기사 정보 조회
        const driverDoc = await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection(DRIVER_COLLECTION_NAME).doc(driverId)
            .get();
        const driverName = ((_a = driverDoc.data()) === null || _a === void 0 ? void 0 : _a.name) || "기사";
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
        if (tokens.length === 0)
            return;
        const statusMessage = presenceStatus === "offline"
            ? "앱 꺼짐 또는 네트워크 연결 끊김"
            : presenceStatus === "background"
                ? "앱이 백그라운드 상태"
                : "알림 전달 실패";
        const payload = (0, fcmPayload_1.buildMulticastFcmPayload)({
            data: {
                type: "NOTIFICATION_FAILURE",
                callId: callId,
                driverId: driverId,
                driverName: driverName,
                presenceStatus: presenceStatus,
                message: `${driverName} 기사에게 알림 전달 실패: ${statusMessage}`,
                title: "⚠️ 알림 전달 실패"
            },
            title: "⚠️ 알림 전달 실패",
            body: `${driverName} 기사에게 알림 전달 실패: ${statusMessage}`,
            level: "active",
        }, tokens);
        await admin.messaging().sendEachForMulticast(payload);
        logger.info(`[ACK] 콜매니저에 실패 알림 전송: callId=${callId}, driver=${driverName}`);
    }
    catch (error) {
        logger.error("[ACK] 실패 알림 전송 오류", error);
    }
}
// ========================================
// 기존 함수들
// ========================================
exports.oncallassigned = (0, firestore_1.onDocumentWritten)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    var _a, _b, _c, _d, _e, _f, _g;
    const { provinceId, cityId, officeId, callId } = event.params;
    // 1. 이벤트 데이터와 변경 후 데이터 존재 여부 확인 (가장 안전한 방법)
    if (!event.data || !event.data.after) {
        logger.info(`[${callId}] 이벤트 데이터가 없어 함수를 종료합니다.`);
        return;
    }
    const afterData = event.data.after.data();
    // 문서가 삭제된 경우
    if (!event.data.after.exists) {
        logger.info(`[${callId}] 문서가 삭제되어 함수를 종료합니다.`);
        return;
    }
    // RESERVED(예약) 콜은 oncallreserved 트리거가 단독 전담 — 일반 배차 흐름(FCM/timeout/presence) 모두 스킵
    if (afterData.status === "RESERVED") {
        logger.info(`[${callId}] RESERVED 상태 — oncallreserved 전담, 본 트리거 종료`);
        return;
    }
    // 관리자 직접운행 콜은 배차 알림 불필요 (assignedDriverId="MANAGER"로 오탐 방지)
    if (afterData.handledByManager === true) {
        logger.info(`[${callId}] 직접운행 콜 - 배차 알림 스킵`);
        return;
    }
    // 기사 자가배차 콜: 기사 본인은 이미 운행준비 진입, 고객은 이미 통화 완료 →
    // 기사 FCM + 고객 FCM 만 스킵, 콜매니저 FCM 은 그대로 발송
    const isSelfAssigned = afterData.selfAssigned === true;
    if (isSelfAssigned) {
        logger.info(`[${callId}] 자가배차 콜 감지 - 기사/고객 FCM 스킵, 콜매니저 FCM 만 발송`);
    }
    const beforeData = (_a = event.data.before) === null || _a === void 0 ? void 0 : _a.data();
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
    const isDriverAssigned = afterData.assignedDriverId && (isNewDocument || // 새 문서 생성 시 (공유콜 포함)
        isDriverChanged // 기존 문서의 기사 변경 시
    );
    if (!isDriverAssigned || !afterData.assignedDriverId) {
        logger.info(`[${callId}] 기사 배정 변경사항이 없어 알림을 보내지 않습니다. assignedDriverId: ${afterData.assignedDriverId}, beforeAssignedDriverId: ${beforeData === null || beforeData === void 0 ? void 0 : beforeData.assignedDriverId}, isSharedCall: ${isSharedCall}, isNewDocument: ${isNewDocument}, isDriverAssigned: ${isDriverAssigned}`);
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
        const driverFcmToken = driverData === null || driverData === void 0 ? void 0 : driverData.fcmToken;
        const driverName = (driverData === null || driverData === void 0 ? void 0 : driverData.name) || "기사";
        const driverPhone = (driverData === null || driverData === void 0 ? void 0 : driverData.phoneNumber) || "";
        const vehicleNumber = (driverData === null || driverData === void 0 ? void 0 : driverData.vehicleNumber) || "";
        // 3-1. ASSIGNED timeout task enqueue (이벤트 기반, 폴링 대체)
        // - selfAssigned/handledByManager 는 timeout 보호 불요 (자가배차/직접운행)
        // - RESERVED 도 timeout 미enqueue (운행 종료까지 무한 대기) — 첫 줄 가드로 이미 빠지지만 향후 리팩터링 방어용 1줄
        // - enqueue 실패는 logger.warn 만 → FCM 흐름 보호. in-flight 콜은 onDriverPresenceOffline 트리거가 안전망.
        if (!isSelfAssigned && !afterData.handledByManager && afterData.status !== "RESERVED") {
            try {
                const officeSnap = await admin.firestore()
                    .collection("provinces").doc(provinceId)
                    .collection("cities").doc(cityId)
                    .collection("offices").doc(officeId)
                    .get();
                const timeoutMin = Number((_c = (_b = officeSnap.data()) === null || _b === void 0 ? void 0 : _b.assignedTimeoutMinutes) !== null && _c !== void 0 ? _c : 1);
                const assignedTsMs = (_e = (_d = afterData.assignedTimestamp) === null || _d === void 0 ? void 0 : _d.toMillis()) !== null && _e !== void 0 ? _e : Date.now();
                await (0, timeout_1.enqueueAssignedTimeoutTask)({
                    provinceId,
                    cityId,
                    officeId,
                    callId,
                    expectedDriverId: driverId,
                    expectedAssignedTimestampMs: assignedTsMs,
                }, timeoutMin * 60);
                logger.info(`[${callId}] timeout task enqueue 완료 (delay=${timeoutMin}분)`);
            }
            catch (taskErr) {
                logger.warn(`[${callId}] timeout task enqueue 실패 — onDriverPresenceOffline 트리거가 보호망`, taskErr);
            }
        }
        // 4. 기사에게 FCM 알림 전송
        // Note: 배차 직후 presence 즉시 체크 제거 — 도즈모드/화면꺼짐 시 오탐 발생
        // (FCM high priority가 기기를 깨우기 전에 offline으로 판단하여 불필요한 경고 전송)
        // 실제 오프라인 보호는 Cloud Tasks deferred + onDriverPresenceOffline 트리거가 담당
        if (driverFcmToken && !isSelfAssigned) {
            const notificationId = `${callId}_${driverId}_${Date.now()}`;
            const driverPayload = (0, fcmPayload_1.buildFcmPayload)({
                data: {
                    callId: callId,
                    notificationId: notificationId, // ACK용 ID 추가
                    type: "call_assigned",
                    title: "새로운 콜 배정",
                    body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!"
                },
                title: "새로운 콜 배정",
                body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!",
                level: "time-sensitive",
                ttlSeconds: 30,
            }, driverFcmToken);
            // 알림 상태 저장 (ACK 추적용)
            await saveNotificationStatus(notificationId, "call_assigned", driverId, "driver", officeId, provinceId, cityId, driverFcmToken, driverPayload, callId);
            await admin.messaging().send(driverPayload);
            logger.info(`[${callId}] 기사 [${driverId}]에게 성공적으로 알림을 보냈습니다. notificationId=${notificationId}`);
        }
        else if (!isSelfAssigned) {
            logger.warn(`[${callId}] 기사 [${driverId}]의 FCM 토큰이 없습니다.`);
        }
        // 5. 고객에게도 기사 배정 알림 전송 (앱 고객만, 자가배차 제외)
        const isAppCustomer = afterData.isAppCustomer || false;
        const customerPhone = afterData.phoneNumber;
        if (isAppCustomer && customerPhone && !isSelfAssigned) {
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
                const customerFcmToken = (_f = customerDoc.data()) === null || _f === void 0 ? void 0 : _f.fcmToken;
                logger.info(`[${callId}] 고객 FCM 토큰: ${customerFcmToken}`);
                if (!customerFcmToken) {
                    logger.warn(`[${callId}] 고객 FCM 토큰 없음: ${customerPhone}`);
                    return;
                }
                // 고객에게 기사 정보 포함한 알림 전송
                const customerPayload = (0, fcmPayload_1.buildFcmPayload)({
                    data: {
                        type: "DRIVER_ASSIGNED",
                        callId: callId,
                        driverName: driverName,
                        driverPhone: driverPhone,
                        vehicleNumber: vehicleNumber,
                        driverId: driverId
                    },
                    title: "기사 배정 완료",
                    body: `${driverName} 기사가 배정되었습니다. 곧 출발합니다.`,
                    level: "time-sensitive",
                    ttlSeconds: 60,
                }, customerFcmToken);
                await admin.messaging().send(customerPayload);
                logger.info(`[${callId}] 고객에게 기사 배정 알림 전송 완료: ${customerPhone}`);
            }
            catch (customerError) {
                logger.error(`[${callId}] 고객 알림 전송 오류:`, customerError);
            }
        }
        else if (!isSelfAssigned) {
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
                const managerTokens = [];
                managerTokensSnapshot.forEach((doc) => {
                    const token = doc.data().fcmToken;
                    if (token)
                        managerTokens.push(token);
                });
                if (managerTokens.length > 0) {
                    const managerPayload = (0, fcmPayload_1.buildMulticastFcmPayload)({
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
                            fare: ((_g = afterData.fare) !== null && _g !== void 0 ? _g : 0).toString(),
                            provinceId: provinceId,
                            cityId: cityId,
                            officeId: officeId
                        },
                        title: "콜 상태 변경",
                        body: `${driverName} 기사에게 배차되었습니다.`,
                        level: "active",
                    }, managerTokens);
                    const response = await admin.messaging().sendEachForMulticast(managerPayload);
                    logger.info(`[${callId}] 콜매니저 FCM 전송 완료 - 성공: ${response.successCount}, 실패: ${response.failureCount}`);
                }
            }
        }
        catch (managerError) {
            logger.error(`[${callId}] 콜매니저 알림 전송 오류:`, managerError);
        }
    }
    catch (error) {
        logger.error(`[${callId}] 알림 전송 중 오류 발생:`, error);
    }
});
// =============================
// 새로운 공유 콜이 생성될 때 트리거
// 대상 지역의 모든 사무실 관리자에게 FCM 알림 전송
// =============================
/**
 * 새로운 콜 생성 시 콜매니저에 FCM 알림 전송
 * - 고객앱에서 콜 생성 시 자동으로 트리거
 * - Call Detector의 전화 호출과 동일한 팝업 표시
 */
exports.sendNewCallNotification = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    var _a, _b;
    const { provinceId, cityId, officeId, callId } = event.params;
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
        const now = firestore_2.Timestamp.now();
        const tenSecondsAgo = new firestore_2.Timestamp(now.seconds - 10, now.nanoseconds);
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
            }
            catch (deleteError) {
                logger.error(`[new-call:${callId}] 중복 콜 삭제 실패:`, deleteError);
            }
            return;
        }
    }
    // 블랙박스 9-B: 신규 콜(WAITING) 들어옴을 단톡방에 무음 기록 (중복콜 삭제 가드 통과 후, best-effort).
    // 직접운행 콜은 제외 (onCallStatusChanged:2144 동일 정책).
    if (callData.handledByManager !== true) {
        const who = callData.customerName || callData.phoneNumber || "";
        await (0, chat_2.postSystemMessage)(provinceId, cityId, officeId, who ? `콜 들어옴 — ${who}` : "콜 들어옴");
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
        const adminTokens = [];
        adminsSnapshot.forEach((doc) => {
            const adminData = doc.data();
            if (adminData.fcmToken) {
                adminTokens.push(adminData.fcmToken);
            }
        });
        // 픽업기사는 NEW_CALL(WAITING) 미수신 (2026-05-11) — onCallStatusChanged의 ASSIGNED 전이 시점부터 알림 수신
        const tokens = [...adminTokens];
        if (tokens.length === 0) {
            logger.warn(`[new-call:${callId}] FCM 토큰을 가진 관리자/픽업기사가 없습니다.`);
            return;
        }
        logger.info(`[new-call:${callId}] tokens: admin=${adminTokens.length} (pickup은 NEW_CALL 미수신)`);
        // timestamp 필드 추출 (Firestore Timestamp → ms long → String)
        const tsMs = (callData.timestamp && typeof callData.timestamp.toMillis === "function")
            ? callData.timestamp.toMillis()
            : (callData.createdAt && typeof callData.createdAt.toMillis === "function")
                ? callData.createdAt.toMillis()
                : Date.now();
        // FCM 메시지 구성
        const message = (0, fcmPayload_1.buildMulticastFcmPayload)({
            data: {
                type: "NEW_CALL",
                callId: callId,
                status: callData.status || "WAITING",
                customerName: callData.customerName || callData.phoneNumber || "신규 고객",
                customerPhone: callData.phoneNumber || "",
                customerAddress: callData.customerAddress || "",
                pickupLocation: callData.customerAddress || callData.departure || "위치 미확인",
                departure: callData.departure_set || callData.departure || "",
                destination: callData.destination_set || callData.destination || "",
                waypoints: callData.waypoints_set || "",
                fare: String((_b = (_a = callData.fare_set) !== null && _a !== void 0 ? _a : callData.fare) !== null && _b !== void 0 ? _b : ""),
                assignedDriverName: callData.assignedDriverName || "",
                timestamp: String(tsMs),
                provinceId: provinceId,
                cityId: cityId,
                officeId: officeId,
                fromCallDetector: String(callData.fromCallDetector === true),
                fromCallManager: String(callData.fromCallManager === true),
            },
            title: "🔔 새 콜 접수",
            body: `${callData.customerName || callData.phoneNumber || "신규 고객"} - ${callData.customerAddress || callData.departure || "위치 미확인"}`,
            level: "time-sensitive",
        }, tokens);
        const response = await admin.messaging().sendEachForMulticast(message);
        logger.info(`[new-call:${callId}] FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);
        // 실패한 토큰 정리 (admin + pickup 둘 다)
        const batch = admin.firestore().batch();
        let invalidTokensFound = 0;
        response.responses.forEach((resp, idx) => {
            var _a;
            if (!resp.success) {
                const error = resp.error;
                logger.warn(`[new-call:${callId}] 토큰 ${idx} 전송 실패: ${error === null || error === void 0 ? void 0 : error.message}`);
                if ((error === null || error === void 0 ? void 0 : error.code) === "messaging/registration-token-not-registered" ||
                    (error === null || error === void 0 ? void 0 : error.code) === "messaging/invalid-registration-token" ||
                    ((_a = error === null || error === void 0 ? void 0 : error.message) === null || _a === void 0 ? void 0 : _a.includes("Requested entity was not found"))) {
                    const invalidToken = tokens[idx];
                    logger.info(`[new-call:${callId}] 무효한 FCM 토큰 발견, 자동 정리 예정`);
                    adminsSnapshot.docs.forEach((doc) => {
                        const adminData = doc.data();
                        if (adminData.fcmToken === invalidToken) {
                            batch.update(doc.ref, { fcmToken: firestore_2.FieldValue.delete() });
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
            }
            catch (batchError) {
                logger.error(`[new-call:${callId}] 무효한 토큰 정리 중 오류:`, batchError);
            }
        }
    }
    catch (error) {
        logger.error(`[new-call:${callId}] 알림 전송 중 오류 발생:`, error);
    }
});
exports.onSharedCallCreated = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
}, async (event) => {
    const callId = event.params.callId;
    if (!event.data) {
        logger.warn(`[shared-created:${callId}] 이벤트 데이터가 없습니다.`);
        return;
    }
    const sharedCallData = event.data.data();
    if (!sharedCallData || sharedCallData.status !== "OPEN") {
        logger.info(`[shared-created:${callId}] OPEN 상태가 아니므로 알림을 보내지 않습니다. Status: ${sharedCallData === null || sharedCallData === void 0 ? void 0 : sharedCallData.status}`);
        return;
    }
    logger.info(`[shared-created:${callId}] 새로운 공유콜 생성됨. 대상 지역 관리자들에게 알림 전송 시작.`);
    logger.info(`[shared-created:${callId}] 공유콜 데이터: sourceProvinceId=${sharedCallData.sourceProvinceId}, sourceCityId=${sharedCallData.sourceCityId}, sourceOfficeId=${sharedCallData.sourceOfficeId}, targetProvinceId=${sharedCallData.targetProvinceId}, targetCityId=${sharedCallData.targetCityId}`);
    // 블랙박스 9-B: 출처 사무실 단톡방에 공유콜 등록 무음 기록
    await (0, chat_2.postSystemMessage)(sharedCallData.sourceProvinceId, sharedCallData.sourceCityId, sharedCallData.sourceOfficeId, "공유콜 등록");
    try {
        // 대상 지역의 모든 관리자 FCM 토큰 조회 (원본 사무실 제외)
        const adminQuery = await admin
            .firestore()
            .collection("admins")
            .where("associatedProvinceId", "==", sharedCallData.targetProvinceId)
            .where("associatedCityId", "==", sharedCallData.targetCityId)
            .get();
        const tokens = [];
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
                }
                else {
                }
            }
            else {
                logger.warn(`[shared-created:${callId}] ⚠️ FCM 토큰 없음: ${adminData.associatedOfficeId}`);
            }
        });
        logger.info(`[shared-created:${callId}] 알림 대상: ${tokens.length}명의 관리자`);
        if (tokens.length === 0) {
            logger.warn(`[shared-created:${callId}] 알림을 보낼 관리자 토큰이 없습니다.`);
            return;
        }
        // 자동공유(detector)는 출발/도착이 없음(정보가 아직 세상에 없음) → "손님 전화 필요"로 표시.
        // 수동공유(shareCall)만 출발/도착/요금이 채워짐 → 기존 route/요금 표시.
        const sc_hasTrip = !!(sharedCallData.departure || sharedCallData.destination);
        const sc_phone = sharedCallData.phoneNumber || "전화번호";
        const sc_body = sc_hasTrip
            ? `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"}`
            : `📞 손님에게 전화 필요`;
        const sc_customMessage = sc_hasTrip
            ? `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"}\n요금: ${sharedCallData.fare || 0}원\n📞 ${sc_phone}`
            : `📞 손님에게 전화 필요\n${sc_phone}`;
        // Data-only FCM 메시지 - 앱에서 커스텀 알림 생성
        const message = (0, fcmPayload_1.buildMulticastFcmPayload)({
            data: {
                type: "NEW_SHARED_CALL",
                sharedCallId: callId,
                departure: sharedCallData.departure || "",
                destination: sharedCallData.destination || "",
                fare: (sharedCallData.fare || 0).toString(),
                callType: sharedCallData.callType || "",
                phoneNumber: sharedCallData.phoneNumber || "",
                // 앱에서 알림 생성용 데이터
                title: "🔄 새로운 공유콜!",
                body: sc_body,
                customMessage: sc_customMessage,
            },
            title: "🔄 새로운 공유콜!",
            body: sc_body,
            level: "time-sensitive",
        }, tokens);
        // 🚨 실제 전송되는 페이로드 확인
        const response = await admin.messaging().sendEachForMulticast(message);
        logger.info(`[shared-created:${callId}] FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);
        // 실패한 토큰들 로그 및 자동 정리
        const batch = admin.firestore().batch();
        let invalidTokensFound = 0;
        response.responses.forEach((resp, idx) => {
            var _a;
            if (!resp.success) {
                const error = resp.error;
                logger.warn(`[shared-created:${callId}] 토큰 ${idx} 전송 실패: ${error === null || error === void 0 ? void 0 : error.message}`);
                // 무효한 토큰인 경우 (만료, 등록 취소 등)
                if ((error === null || error === void 0 ? void 0 : error.code) === 'messaging/registration-token-not-registered' ||
                    (error === null || error === void 0 ? void 0 : error.code) === 'messaging/invalid-registration-token' ||
                    ((_a = error === null || error === void 0 ? void 0 : error.message) === null || _a === void 0 ? void 0 : _a.includes('Requested entity was not found'))) {
                    const invalidToken = tokens[idx];
                    logger.info(`[shared-created:${callId}] 무효한 FCM 토큰 발견, 자동 정리 예정: ${invalidToken === null || invalidToken === void 0 ? void 0 : invalidToken.substring(0, 20)}...`);
                    // 해당 토큰을 가진 관리자 문서에서 fcmToken 필드 제거
                    adminQuery.docs.forEach((doc) => {
                        const adminData = doc.data();
                        if (adminData.fcmToken === invalidToken) {
                            batch.update(doc.ref, { fcmToken: firestore_2.FieldValue.delete() });
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
            }
            catch (batchError) {
                logger.error(`[shared-created:${callId}] 무효한 토큰 정리 중 오류:`, batchError);
            }
        }
    }
    catch (error) {
        logger.error(`[shared-created:${callId}] 알림 전송 중 오류 발생:`, error);
    }
});
/**
 * 고객에게 사무실 마감 알림 전송
 * shared_calls 문서 생성 시 고객 앱에 FCM 알림 전송
 */
exports.notifyCustomerOnOfficeClosed = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
}, async (event) => {
    var _a;
    const callId = event.params.callId;
    if (!event.data) {
        logger.warn(`[customer-closed:${callId}] 이벤트 데이터가 없습니다.`);
        return;
    }
    const sharedCallData = event.data.data();
    if (!sharedCallData || sharedCallData.status !== "OPEN") {
        logger.info(`[customer-closed:${callId}] OPEN 상태가 아니므로 고객 알림을 보내지 않습니다. Status: ${sharedCallData === null || sharedCallData === void 0 ? void 0 : sharedCallData.status}`);
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
        const fcmToken = (_a = customerDoc.data()) === null || _a === void 0 ? void 0 : _a.fcmToken;
        if (!fcmToken) {
            logger.info(`[customer-closed:${callId}] 고객 FCM 토큰 없음 (앱 미설치 또는 미가입): ${phoneNumber}`);
            return;
        }
        // FCM 알림 전송
        const message = (0, fcmPayload_1.buildFcmPayload)({
            data: {
                type: "OFFICE_CLOSED",
                sharedCallId: callId,
                phoneNumber: phoneNumber,
                title: "🏢 사무실 마감 안내",
                body: "사무실이 마감되어 잠시후 운행가능한 다른 사무실과 연결해 드리겠습니다.",
            },
            title: "🏢 사무실 마감 안내",
            body: "사무실이 마감되어 잠시후 운행가능한 다른 사무실과 연결해 드리겠습니다.",
            level: "active",
            ttlSeconds: 300,
        }, fcmToken);
        await admin.messaging().send(message);
        logger.info(`[customer-closed:${callId}] 고객에게 사무실 마감 알림 전송 완료: ${phoneNumber}`);
    }
    catch (error) {
        // FCM 토큰이 무효한 경우 자동 정리
        if ((error === null || error === void 0 ? void 0 : error.code) === 'messaging/registration-token-not-registered' ||
            (error === null || error === void 0 ? void 0 : error.code) === 'messaging/invalid-registration-token') {
            logger.warn(`[customer-closed:${callId}] 무효한 FCM 토큰 발견, 자동 정리: ${phoneNumber}`);
            try {
                await admin.firestore()
                    .collection("provinces").doc(sourceProvinceId)
                    .collection("cities").doc(sourceCityId)
                    .collection("offices").doc(sourceOfficeId)
                    .collection("customerInfo")
                    .doc(phoneNumber)
                    .update({ fcmToken: firestore_2.FieldValue.delete() });
                logger.info(`[customer-closed:${callId}] 무효한 FCM 토큰 제거 완료: ${phoneNumber}`);
            }
            catch (deleteError) {
                logger.error(`[customer-closed:${callId}] 토큰 제거 중 오류:`, deleteError);
            }
        }
        else {
            logger.error(`[customer-closed:${callId}] 알림 전송 중 오류:`, error);
        }
    }
});
exports.onSharedCallClaimed = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
}, async (event) => {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j;
    const callId = event.params.callId;
    if (!event.data) {
        logger.warn(`[shared:${callId}] 이벤트 데이터가 없습니다.`);
        return;
    }
    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();
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
                    updatedAt: firestore_2.FieldValue.serverTimestamp()
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
            const tokens = [];
            adminQuery.docs.forEach((doc) => {
                const adminData = doc.data();
                if (adminData.fcmToken) {
                    tokens.push(adminData.fcmToken);
                }
            });
            if (tokens.length > 0) {
                const message = (0, fcmPayload_1.buildMulticastFcmPayload)({
                    data: {
                        type: "SHARED_CALL_CANCELLED",
                        callId: callId,
                        cancelReason: afterData.cancelReason || "",
                        // 알림 제목과 내용을 완전히 다른 키로 전송
                        alertTitle: "공유콜이 취소되었습니다",
                        alertMessage: `${afterData.cancelReason || "사유 없음"} - 콜이 대기 상태로 복구되었습니다.`,
                    },
                    title: "공유콜이 취소되었습니다",
                    body: `${afterData.cancelReason || "사유 없음"} - 콜이 대기 상태로 복구되었습니다.`,
                    level: "active",
                }, tokens);
                const response = await admin.messaging().sendEachForMulticast(message);
                logger.info(`[shared:${callId}] 원본 사무실에 취소 알림 전송 완료. 성공: ${response.successCount}`);
            }
        }
        catch (error) {
            logger.error(`[shared:${callId}] 원본 콜 복구 중 오류:`, error);
        }
        return;
    }
    // OPEN -> CLAIMED 인지 확인 (콜 복사만, 포인트 처리 없음)
    if (beforeData.status === "OPEN" && afterData.status === "CLAIMED") {
        logger.info(`[shared:${callId}] 공유 콜이 CLAIMED 되었습니다. 대상사무실로 복사 시작.`);
        logger.info(`[shared:${callId}] afterData.claimedDriverId=${afterData.claimedDriverId}`);
        logger.info(`[shared:${callId}] assignedDriverId=${afterData.claimedDriverId}`);
        // 선불 포인트 게이트: claim 시점 수임 사무실 wallet 잔액 ≥ 5,000 검증 (부족 시 revert + 매니저 충전 안내)
        // 2026-07-05 공유콜 지역 OS 전환[확정]: 식당콜 한정 → 전 공유콜(사무실간 포함)로 확장.
        //   근거 = 사무실간 zero-sum 콜도 B가 A에 −10% 지급 → 선불 잔액 없으면 미회수 부채. targetProvince/City는
        //   수임 함수가 수임 사무실 자기값으로 덮어써(claimShared*) checkOfficeWalletForClaim이 수임 사무실 지갑을 정확히 조회.
        if (afterData.claimedOfficeId) {
            const { allowed, balance } = await (0, points_1.checkOfficeWalletForClaim)(afterData.targetProvinceId, afterData.targetCityId, afterData.claimedOfficeId);
            if (!allowed) {
                logger.warn(`[shared:${callId}] 잔액 부족으로 claim revert - office: ${afterData.claimedOfficeId}, balance: ${balance}`);
                try {
                    await admin.firestore().collection("shared_calls").doc(callId).update({
                        status: "OPEN",
                        claimedOfficeId: null,
                        claimedDriverId: null,
                        claimRejectedReason: "INSUFFICIENT_BALANCE",
                        claimRejectedAt: firestore_2.FieldValue.serverTimestamp(),
                    });
                    const adminQuery = await admin.firestore()
                        .collection("admins")
                        .where("associatedProvinceId", "==", afterData.targetProvinceId)
                        .where("associatedCityId", "==", afterData.targetCityId)
                        .where("associatedOfficeId", "==", afterData.claimedOfficeId)
                        .get();
                    const tokens = [];
                    adminQuery.docs.forEach((doc) => {
                        const fcm = doc.data().fcmToken;
                        if (fcm)
                            tokens.push(fcm);
                    });
                    if (tokens.length > 0) {
                        await admin.messaging().sendEachForMulticast({
                            tokens,
                            notification: {
                                title: "포인트 충전이 필요합니다",
                                body: `현재 잔액 ${balance}P. 콜을 잡으려면 5,000P 이상 필요합니다.`,
                            },
                            data: {
                                type: "WALLET_INSUFFICIENT",
                                callId,
                                balance: String(balance),
                                required: "5000",
                            },
                        });
                    }
                }
                catch (revertErr) {
                    logger.error(`[shared:${callId}] revert 실패`, revertErr);
                }
                return;
            }
        }
        // 블랙박스 9-B: 공유콜 수임(타 사무실) 무음 기록 — wallet revert(잔액부족 식당앱콜, 1397 return) 통과 후라야 실제 수임만 기록
        await (0, chat_2.postSystemMessage)(afterData.sourceProvinceId, afterData.sourceCityId, afterData.sourceOfficeId, "공유콜 수임 — 타 사무실");
        // 트랜잭션 외부에서 변수 선언
        let assignedDriverId = null;
        let assignedDriverName = null;
        let assignedDriverPhone = null;
        let driverSnap = null;
        try {
            await admin.firestore().runTransaction(async (tx) => {
                // ========== 모든 읽기 작업을 먼저 수행 ==========
                var _a, _b, _c;
                // 1. 기사 정보 읽기 (배정된 기사가 있을 경우)
                driverSnap = afterData.claimedDriverId ? await tx.get(admin.firestore()
                    .collection("provinces").doc(afterData.targetProvinceId)
                    .collection("cities").doc(afterData.targetCityId)
                    .collection("offices").doc(afterData.claimedOfficeId)
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
                    .doc(afterData.claimedOfficeId)
                    .collection("calls")
                    .doc(callId);
                // 공유콜 생성 - 기사 배정이 있으면 바로 ASSIGNED 상태로 생성
                // memoText 포함 — spread 복사로 자동 전달. 수임 사무실 기사도 원본 메모("시장에서 용문 25000") 확인 가능
                const callDoc = Object.assign(Object.assign(Object.assign({}, afterData), { status: assignedDriverId ? "ASSIGNED" : "WAITING", departure_set: (_a = afterData.departure) !== null && _a !== void 0 ? _a : null, destination_set: (_b = afterData.destination) !== null && _b !== void 0 ? _b : null, fare_set: (_c = afterData.fare) !== null && _c !== void 0 ? _c : null, callType: "SHARED", sourceSharedCallId: callId, createdAt: firestore_2.FieldValue.serverTimestamp() }), (assignedDriverId && {
                    assignedDriverId: assignedDriverId,
                    assignedDriverName: assignedDriverName,
                    assignedDriverPhone: assignedDriverPhone,
                    assignedTimestamp: firestore_2.FieldValue.serverTimestamp(),
                }));
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
                    const sourceCallUpdates = {
                        status: "CLAIMED", // 수락됨 상태
                        claimedOfficeId: afterData.claimedOfficeId,
                        assignedDriverName: `수락됨 (${afterData.claimedOfficeId})`,
                        updatedAt: firestore_2.FieldValue.serverTimestamp(),
                    };
                    tx.update(sourceCallRef, sourceCallUpdates);
                    logger.info(`[shared:${callId}] 원본 콜을 수락됨 상태로 업데이트 완료`);
                }
                else {
                    logger.warn(`[shared:${callId}] 원본 콜 문서가 존재하지 않습니다. 건너뜁니다.`);
                }
                //   c) 공유콜 문서 processed 플래그 수정 → 트랜잭션 외부로 이동하여
                //      "읽기 후 쓰기" 제약을 피함 (트랜잭션 내부에 포함하면
                //      사전에 해당 문서를 읽지 않았기 때문에 Firestore가
                //      암묵적 read 를 삽입하며 오류가 발생한다)
            });
            logger.info(`[shared:${callId}] 콜 복사 및 포인트 처리 완료. 대상 사무실에 WAITING 상태로 생성됨.`);
            // ---- 기사 상태 업데이트 (기사 배정이 있는 경우만) ----
            if (assignedDriverId && (driverSnap === null || driverSnap === void 0 ? void 0 : driverSnap.exists)) {
                try {
                    logger.info(`[shared:${callId}] 기사 상태 업데이트: ${assignedDriverId}`);
                    await driverSnap.ref.update({ status: "ASSIGNED" });
                    logger.info(`[shared:${callId}] 기사 상태 업데이트 완료: ${assignedDriverId}`);
                }
                catch (assignErr) {
                    logger.error(`[shared:${callId}] 기사 상태 업데이트 실패`, assignErr);
                }
            }
            // ---- 공유콜 문서 processed 플래그 업데이트 (트랜잭션 외부) ----
            try {
                await event.data.after.ref.update({ processed: true });
                logger.debug(`[shared:${callId}] shared_calls 문서 processed 플래그 업데이트 완료.`);
            }
            catch (updateErr) {
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
                const tokens = [];
                srcSnap.forEach((doc) => {
                    const t = doc.data().fcmToken;
                    if (t)
                        tokens.push(t);
                });
                tgtSnap.forEach((doc) => {
                    const t = doc.data().fcmToken;
                    if (t)
                        tokens.push(t);
                });
                if (tokens.length > 0) {
                    const msg = (0, fcmPayload_1.buildMulticastFcmPayload)({
                        data: {
                            sharedCallId: callId,
                            type: "SHARED_CALL_CLAIMED",
                            // 알림 제목과 내용을 data로 전송
                            alertTitle: "공유 콜 수락됨",
                            alertMessage: `${(_a = afterData.departure) !== null && _a !== void 0 ? _a : "출발"} → ${(_b = afterData.destination) !== null && _b !== void 0 ? _b : "도착"} / 요금 ${(_c = afterData.fare) !== null && _c !== void 0 ? _c : 0}원`,
                        },
                        title: "공유 콜 수락됨",
                        body: `${(_d = afterData.departure) !== null && _d !== void 0 ? _d : "출발"} → ${(_e = afterData.destination) !== null && _e !== void 0 ? _e : "도착"} / 요금 ${(_f = afterData.fare) !== null && _f !== void 0 ? _f : 0}원`,
                        level: "active",
                    }, tokens);
                    const resp = await admin.messaging().sendEachForMulticast(msg);
                    logger.info(`[shared:${callId}] FCM sendEachForMulticast done. Success: ${resp.successCount}, Failure: ${resp.failureCount}`);
                }
                else {
                    logger.info(`[shared:${callId}] 알림을 보낼 토큰이 없습니다.`);
                }
            }
            catch (fcmErr) {
                logger.error(`[shared:${callId}] FCM 전송 오류`, fcmErr);
            }
            // PR 1 plan §3.3 — 식당앱 발생 콜이면 식당 fcmToken으로 잡힘 알림 (사무실명·전화번호 + 통화 버튼)
            if (afterData.sourceRestaurantId && afterData.claimedOfficeId) {
                try {
                    const restRef = admin.firestore()
                        .collection("provinces").doc(afterData.sourceProvinceId)
                        .collection("cities").doc(afterData.sourceCityId)
                        .collection("offices").doc(afterData.sourceOfficeId)
                        .collection("restaurants").doc(afterData.sourceRestaurantId);
                    const officeRef = admin.firestore()
                        .collection("provinces").doc(afterData.targetProvinceId)
                        .collection("cities").doc(afterData.targetCityId)
                        .collection("offices").doc(afterData.claimedOfficeId);
                    const [restSnap, officeSnap] = await Promise.all([restRef.get(), officeRef.get()]);
                    const restFcmToken = (_g = restSnap.data()) === null || _g === void 0 ? void 0 : _g.fcmToken;
                    const officeName = ((_h = officeSnap.data()) === null || _h === void 0 ? void 0 : _h.name) || "사무실";
                    const officePhone = ((_j = officeSnap.data()) === null || _j === void 0 ? void 0 : _j.phone) || "";
                    if (restFcmToken) {
                        await admin.messaging().send({
                            token: restFcmToken,
                            notification: {
                                title: "콜이 잡혔습니다",
                                body: `${officeName}에서 운행을 시작합니다.`,
                            },
                            data: {
                                type: "RESTAURANT_CALL_CLAIMED",
                                sharedCallId: callId,
                                officeName,
                                officePhone,
                            },
                        });
                        logger.info(`[shared:${callId}] 식당 알림 발송 - restaurantId: ${afterData.sourceRestaurantId}, officeName: ${officeName}`);
                    }
                }
                catch (restErr) {
                    logger.error(`[shared:${callId}] 식당 알림 실패`, restErr);
                }
            }
        }
        catch (err) {
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
                    logger.info(`[shared:${callId}] 복사된 콜 상태: ${copiedCallData === null || copiedCallData === void 0 ? void 0 : copiedCallData.status}`);
                    // HOLD 또는 CANCELLED_BY_DRIVER 상태인 경우 삭제 (이미 진행 중인 콜은 건드리지 않음)
                    if ((copiedCallData === null || copiedCallData === void 0 ? void 0 : copiedCallData.status) === "HOLD" || (copiedCallData === null || copiedCallData === void 0 ? void 0 : copiedCallData.status) === "CANCELLED_BY_DRIVER") {
                        await copiedCallRef.delete();
                        logger.info(`[shared:${callId}] HOLD 상태의 복사된 콜을 삭제했습니다.`);
                    }
                }
            }
            logger.info(`[shared:${callId}] 공유콜 취소 처리 완료.`);
        }
        catch (err) {
            logger.error(`[shared:${callId}] 공유콜 취소 처리 오류`, err);
        }
    }
});
// 공유콜이 기사에 의해 취소될 때 처리하는 함수
exports.onSharedCallCancelledByDriver = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
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
            const sharedCallData = sharedCallSnap.data();
            const originalCallId = sharedCallData.originalCallId;
            logger.info(`[call-cancelled:${callId}] shared_calls 정보: sourceProvinceId=${sharedCallData.sourceProvinceId}, sourceCityId=${sharedCallData.sourceCityId}, sourceOfficeId=${sharedCallData.sourceOfficeId}, originalCallId=${originalCallId}`);
            if (!originalCallId) {
                // Detector가 직접 생성한 공유콜 - originalCallId가 없으므로 원본 복구 스킵
                logger.info(`[call-cancelled:${callId}] Detector 생성 공유콜 - 원본 없으므로 복구 스킵. shared_calls 문서만 삭제합니다.`);
                await sharedCallRef.delete();
                logger.info(`[call-cancelled:${callId}] shared_calls 문서 삭제 완료 (Detector 생성 공유콜)`);
            }
            else {
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
                        logger.info(`[call-cancelled:${callId}] 원본 콜 현재 상태: ${originalCallData === null || originalCallData === void 0 ? void 0 : originalCallData.status}`);
                        // memoText 는 명시적으로 삭제하지 않음 — 원본 사무실 매니저가 입력한 메모는 복구 후에도 유지
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
                            updatedAt: firestore_2.FieldValue.serverTimestamp()
                        };
                        tx.update(originalCallRef, updateData);
                        logger.info(`[call-cancelled:${callId}] 원본 콜을 HOLD 상태로 복구 완료. Path: ${originalCallRef.path}`);
                    }
                    else {
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
            const tokens = [];
            adminQuery.docs.forEach((doc) => {
                const adminData = doc.data();
                if (adminData.fcmToken) {
                    tokens.push(adminData.fcmToken);
                }
            });
            if (tokens.length > 0) {
                const message = (0, fcmPayload_1.buildMulticastFcmPayload)({
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
                    title: "🚫 공유콜이 취소되었습니다!",
                    body: `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"}\n취소사유: ${afterData.cancelReason || "사유 없음"}`,
                    level: "active",
                }, tokens);
                const response = await admin.messaging().sendEachForMulticast(message);
                logger.info(`[call-cancelled:${callId}] 원본 사무실에 FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);
            }
            // 수락사무실에서 취소된 콜 문서 삭제
            await event.data.after.ref.delete();
            logger.info(`[call-cancelled:${callId}] 수락사무실에서 취소된 공유콜 삭제 완료`);
        }
        catch (error) {
            logger.error(`[call-cancelled:${callId}] 공유콜 취소 처리 오류:`, error);
        }
    }
});
// =============================
// 전화 콜 접수 시 앱 고객에게 FCM 알림 전송
// =============================
exports.notifyCustomerOnPhoneCall = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    var _a;
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
    // 자가배차 콜은 이미 기사가 배정된 상태 → "배정 기다려주세요" 알림이 거짓 정보가 됨, 스킵
    if (callData.selfAssigned === true) {
        logger.info(`[notifyCustomerOnPhoneCall:${callId}] 자가배차 콜 - 고객 알림 스킵`);
        return;
    }
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
        const fcmToken = (_a = customerDoc.data()) === null || _a === void 0 ? void 0 : _a.fcmToken;
        if (!fcmToken) {
            logger.warn(`[notifyCustomerOnPhoneCall:${callId}] FCM 토큰 없음: ${phoneNumber}`);
            return;
        }
        // FCM 알림 전송
        await admin.messaging().send((0, fcmPayload_1.buildFcmPayload)({
            data: {
                type: "CALL_RECEIVED",
                callId: callId,
                message: "콜이 접수되었습니다. 기사 배정을 기다려주세요."
            },
            title: "콜 접수 완료",
            body: "콜이 접수되었습니다. 기사 배정을 기다려주세요.",
            level: "active",
            ttlSeconds: 60,
        }, fcmToken));
        logger.info(`[notifyCustomerOnPhoneCall:${callId}] 고객에게 접수 완료 알림 전송 완료: ${phoneNumber}`);
    }
    catch (error) {
        logger.error(`[notifyCustomerOnPhoneCall:${callId}] 알림 전송 오류:`, error);
    }
});
// =============================
// 운행 완료 시 고객에게 FCM 알림 전송 + 포인트 적립
// =============================
exports.notifyCustomerOnComplete = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    var _a;
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
            const fcmToken = (_a = customerDoc.data()) === null || _a === void 0 ? void 0 : _a.fcmToken;
            // 1) 운행 완료 FCM 알림 전송
            if (fcmToken) {
                await admin.messaging().send((0, fcmPayload_1.buildFcmPayload)({
                    data: {
                        type: "RIDE_COMPLETED",
                        callId: callId,
                        fare: fare.toString(),
                        pointsUsed: pointsUsed.toString()
                    },
                    title: "운행 완료",
                    body: `운행이 완료되었습니다. 요금: ${fare.toLocaleString()}원`,
                    level: "active",
                    ttlSeconds: 60,
                }, fcmToken));
                logger.info(`[notifyCustomerOnComplete:${callId}] 운행 완료 알림 전송 완료: ${phoneNumber}`);
            }
            else {
                logger.warn(`[notifyCustomerOnComplete:${callId}] FCM 토큰 없음: ${phoneNumber}`);
            }
            // 2) 고객 포인트 적립 처리
            logger.info(`[notifyCustomerOnComplete:${callId}] 포인트 적립 시작. Fare: ${fare}`);
            const pointsResult = await (0, points_1.processCustomerPointsOnComplete)(provinceId, cityId, officeId, callId, phoneNumber, fare, customerName);
            if (pointsResult.success && pointsResult.pointsEarned > 0 && fcmToken) {
                // 3) 포인트 적립 완료 FCM 알림 전송
                let pointsMessage = `${pointsResult.pointsEarned}P 적립! (잔액: ${pointsResult.newBalance}P)`;
                // 등급 업그레이드 시 추가 메시지
                if (pointsResult.gradeUpgraded) {
                    pointsMessage = `${pointsResult.pointsEarned}P 적립! 축하합니다! ${pointsResult.previousGrade} → ${pointsResult.grade} 등급 승급! (잔액: ${pointsResult.newBalance}P)`;
                }
                // 예외: 기존 최상위 notification 필드 유지 (Android 고객앱 호환). apns 블록만 추가
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
                    apns: {
                        headers: {
                            "apns-push-type": "alert",
                            "apns-priority": "10",
                            "apns-expiration": String(Math.floor(Date.now() / 1000) + 60),
                        },
                        payload: {
                            aps: {
                                alert: { title: "포인트 적립 완료", body: pointsMessage },
                                sound: "default",
                                "content-available": 1,
                                "mutable-content": 1,
                                "interruption-level": "active",
                            },
                        },
                    },
                    token: fcmToken
                });
                logger.info(`[notifyCustomerOnComplete:${callId}] 포인트 적립 알림 전송 완료. Earned: ${pointsResult.pointsEarned}P, Balance: ${pointsResult.newBalance}P, Grade: ${pointsResult.grade}`);
            }
            else if (!pointsResult.success) {
                logger.warn(`[notifyCustomerOnComplete:${callId}] 포인트 적립 실패: ${pointsResult.error}`);
            }
        }
        catch (error) {
            logger.error(`[notifyCustomerOnComplete:${callId}] 처리 오류:`, error);
        }
    }
});
// 콜 상태 변경 시 알림 (운행시작, 정산완료 등)
// 신규콜 예약 (RESERVED) 트리거
// - 다른 status → RESERVED 진입 시 1회 발화
// - 운행중 기사에게 가벼운 FCM 1회 (type=call_reserved) — FullScreenIntent 안 씀, timeout enqueue 안 함
// - oncallassigned / onCallStatusChanged 는 RESERVED 가드로 빠져 본 트리거가 RESERVED 진입을 단독 전담
// - RESERVED → ACCEPTED/WAITING 이탈은 일반 트리거(oncallassigned 등)가 신규 status 로 처리
exports.oncallreserved = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}",
}, async (event) => {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const { provinceId, cityId, officeId, callId } = event.params;
    if (!((_a = event.data) === null || _a === void 0 ? void 0 : _a.before) || !((_b = event.data) === null || _b === void 0 ? void 0 : _b.after)) {
        logger.warn(`[oncallreserved:${callId}] before/after 데이터 없음 — 종료`);
        return;
    }
    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();
    // RESERVED 진입 시점만 처리 (그 외 전이 무시)
    if ((beforeData === null || beforeData === void 0 ? void 0 : beforeData.status) === "RESERVED" || afterData.status !== "RESERVED") {
        return;
    }
    const driverId = afterData.assignedDriverId;
    if (!driverId) {
        logger.warn(`[oncallreserved:${callId}] assignedDriverId 누락 — 종료`);
        return;
    }
    logger.info(`[oncallreserved:${callId}] RESERVED 진입 감지 (before=${beforeData === null || beforeData === void 0 ? void 0 : beforeData.status} → after=RESERVED), driver=${driverId}`);
    // 블랙박스 9-B: 예약 콜 등록 무음 기록 (RESERVED 진입 단일 지점 — 중복 없음)
    await (0, chat_2.postSystemMessage)(provinceId, cityId, officeId, `예약 콜 등록 — ${afterData.assignedDriverName || "기사"}`);
    try {
        const driverRef = admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection(DRIVER_COLLECTION_NAME).doc(driverId);
        const driverDoc = await driverRef.get();
        if (!driverDoc.exists) {
            logger.error(`[oncallreserved:${callId}] 기사 문서 [${driverId}] 없음`);
            return;
        }
        const driverFcmToken = (_c = driverDoc.data()) === null || _c === void 0 ? void 0 : _c.fcmToken;
        if (!driverFcmToken) {
            logger.warn(`[oncallreserved:${callId}] 기사 [${driverId}] FCM 토큰 없음 — 알림 스킵`);
            return;
        }
        const customerName = (_d = afterData.customerName) !== null && _d !== void 0 ? _d : "";
        const customerAddress = (_e = afterData.customerAddress) !== null && _e !== void 0 ? _e : "";
        const destination = (_f = afterData.destination) !== null && _f !== void 0 ? _f : "";
        const fareValue = (_h = (_g = afterData.fare) !== null && _g !== void 0 ? _g : afterData.fare_set) !== null && _h !== void 0 ? _h : 0;
        const message = (0, fcmPayload_1.buildFcmPayload)({
            data: {
                type: "call_reserved",
                callId: String(callId),
                customerName: String(customerName),
                customerAddress: String(customerAddress),
                destination: String(destination),
                fare: String(fareValue),
                timestamp: String(Date.now()),
            },
            title: "예약 콜",
            body: "운행 종료 후 처리할 콜이 예약되었습니다",
            level: "active",
            ttlSeconds: 3600,
        }, driverFcmToken);
        const response = await admin.messaging().send(message);
        logger.info(`[oncallreserved:${callId}] FCM 송신 완료 → driver=${driverId}, response=${response}`);
    }
    catch (err) {
        logger.error(`[oncallreserved:${callId}] FCM 송신 오류:`, err);
    }
});
exports.onCallStatusChanged = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}",
}, async (event) => {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const { provinceId, cityId, officeId, callId } = event.params;
    if (!event.data) {
        logger.warn(`[onCallStatusChanged:${callId}] No event data.`);
        return;
    }
    const beforeData = (_a = event.data.before) === null || _a === void 0 ? void 0 : _a.data();
    const afterData = (_b = event.data.after) === null || _b === void 0 ? void 0 : _b.data();
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
    // 관리자 직접운행 콜은 콜매니저 본인이 처리하므로 FCM 알림 불필요
    if (afterData.handledByManager === true) {
        logger.info(`[onCallStatusChanged:${callId}] 직접운행 콜 - 알림 스킵`);
        return;
    }
    // RESERVED 진입(after === RESERVED) 만 가드 — oncallreserved 가 단독 전담, 중복 FCM 방지.
    // RESERVED 이탈(before === RESERVED, after === ACCEPTED/WAITING/CANCELED) 은 통과해
    // 매니저/픽업기사에게 정상 status_update FCM 송신 (기사 [수락]/[거절], 매니저 [예약 취소] 인지).
    if (afterData.status === "RESERVED") {
        logger.info(`[onCallStatusChanged:${callId}] RESERVED 진입 (${beforeData.status} → RESERVED) — oncallreserved 전담`);
        return;
    }
    logger.info(`[onCallStatusChanged:${callId}] Status changed: ${beforeData.status} → ${afterData.status}`);
    // 블랙박스 9-B: 콜 상태 전이를 단톡방에 무음 시스템 메시지로 자동 기록 (best-effort, 본 흐름 막지 않음)
    try {
        const driverName = afterData.assignedDriverName || "기사";
        const dep = afterData.departure_set || afterData.departure || "";
        const dest = afterData.destination_set || afterData.destination || "";
        const fareNum = Number((_d = (_c = afterData.fare_set) !== null && _c !== void 0 ? _c : afterData.fare) !== null && _d !== void 0 ? _d : 0);
        const route = (dep && dest)
            ? ` (${dep} → ${dest}${fareNum > 0 ? `, ${fareNum.toLocaleString()}원` : ""})`
            : "";
        let sysText = "";
        switch (afterData.status) {
            case "ASSIGNED":
                sysText = `${driverName} 배차됨`;
                break;
            case "ACCEPTED":
                sysText = `${driverName} 수락`;
                break;
            case "IN_PROGRESS":
                sysText = `운행 시작${route}`;
                break;
            // AWAITING_SETTLEMENT = 실제 운행완료(기사 [운행완료]), COMPLETED = 정산완료(정산 세션 추가). 둘은 다른 사건 → 텍스트 분리(double "운행 완료" 제거).
            case "AWAITING_SETTLEMENT":
                sysText = `운행 완료${route}`;
                break;
            case "COMPLETED":
                sysText = "정산 완료";
                break;
            case "CANCELED":
                sysText = "콜 취소 (관리자)";
                break;
            case "CANCELLED_BY_DRIVER":
                sysText = `${driverName} 운행 취소`;
                break;
            case "CANCELLED_BY_CUSTOMER":
                sysText = "고객 취소";
                break;
            case "HOLD":
                sysText = "콜 보류";
                break;
            default: sysText = "";
        }
        if (sysText) {
            await (0, chat_2.postSystemMessage)(provinceId, cityId, officeId, sysText);
        }
    }
    catch (sysErr) {
        logger.error(`[onCallStatusChanged:${callId}] 블랙박스 시스템 메시지 게시 실패`, sysErr);
    }
    // ✅ 콜매니저 + 픽업기사에 상태 변경 알림 전송
    try {
        const managerTokensSnapshot = await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("managerTokens")
            .get();
        const pickupSnapshot = await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("pickup_drivers")
            .get();
        const managerTokens = [];
        managerTokensSnapshot.forEach((doc) => {
            const token = doc.data().fcmToken;
            if (token)
                managerTokens.push(token);
        });
        const pickupTokens = [];
        pickupSnapshot.forEach((doc) => {
            const token = doc.data().fcmToken;
            if (token)
                pickupTokens.push(token);
        });
        const tokens = [...managerTokens, ...pickupTokens];
        if (tokens.length > 0) {
            // timestamp 필드 추출 (Firestore Timestamp → ms long → String)
            const tsMs = (afterData.timestamp && typeof afterData.timestamp.toMillis === "function")
                ? afterData.timestamp.toMillis()
                : (afterData.createdAt && typeof afterData.createdAt.toMillis === "function")
                    ? afterData.createdAt.toMillis()
                    : Date.now();
            const payload = (0, fcmPayload_1.buildMulticastFcmPayload)({
                data: {
                    type: "CALL_STATUS_UPDATE",
                    callId: callId,
                    status: afterData.status,
                    customerName: afterData.customerName || "고객",
                    customerPhone: afterData.phoneNumber || "",
                    customerAddress: afterData.customerAddress || "",
                    assignedDriverName: afterData.assignedDriverName || "",
                    assignedDriverPhone: afterData.assignedDriverPhone || "",
                    departure: afterData.departure_set || afterData.departure || "",
                    destination: afterData.destination_set || afterData.destination || "",
                    waypoints: afterData.waypoints_set || "",
                    fare: ((_f = (_e = afterData.fare_set) !== null && _e !== void 0 ? _e : afterData.fare) !== null && _f !== void 0 ? _f : 0).toString(),
                    timestamp: String(tsMs),
                    provinceId: provinceId,
                    cityId: cityId,
                    officeId: officeId
                },
                title: "콜 상태 변경",
                body: `${afterData.customerName || "고객"} - ${afterData.status}`,
                level: "active",
                ttlSeconds: 60,
            }, tokens);
            logger.info(`[onCallStatusChanged:${callId}] tokens: manager=${managerTokens.length}, pickup=${pickupTokens.length}`);
            const response = await admin.messaging().sendEachForMulticast(payload);
            logger.info(`[onCallStatusChanged:${callId}] FCM 전송 - ${afterData.status} - 성공: ${response.successCount}, 실패: ${response.failureCount}`);
            // 무효 토큰 정리 (manager + pickup 둘 다)
            const batch = admin.firestore().batch();
            let invalidTokensFound = 0;
            response.responses.forEach((resp, idx) => {
                var _a;
                if (!resp.success) {
                    const error = resp.error;
                    if ((error === null || error === void 0 ? void 0 : error.code) === "messaging/registration-token-not-registered" ||
                        (error === null || error === void 0 ? void 0 : error.code) === "messaging/invalid-registration-token" ||
                        ((_a = error === null || error === void 0 ? void 0 : error.message) === null || _a === void 0 ? void 0 : _a.includes("Requested entity was not found"))) {
                        const invalidToken = tokens[idx];
                        managerTokensSnapshot.docs.forEach((doc) => {
                            if (doc.data().fcmToken === invalidToken) {
                                batch.update(doc.ref, { fcmToken: firestore_2.FieldValue.delete() });
                                invalidTokensFound++;
                            }
                        });
                        pickupSnapshot.docs.forEach((doc) => {
                            if (doc.data().fcmToken === invalidToken) {
                                batch.update(doc.ref, { fcmToken: firestore_2.FieldValue.delete() });
                                invalidTokensFound++;
                            }
                        });
                    }
                }
            });
            if (invalidTokensFound > 0) {
                try {
                    await batch.commit();
                    logger.info(`[onCallStatusChanged:${callId}] ${invalidTokensFound}개 무효 토큰 정리 완료`);
                }
                catch (batchError) {
                    logger.error(`[onCallStatusChanged:${callId}] 무효 토큰 정리 오류:`, batchError);
                }
            }
        }
    }
    catch (managerError) {
        logger.error(`[onCallStatusChanged:${callId}] FCM 전송 오류:`, managerError);
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
            const fcmToken = (_g = customerDoc.data()) === null || _g === void 0 ? void 0 : _g.fcmToken;
            if (fcmToken) {
                const statusMessages = {
                    "ACCEPTED": "기사가 콜을 수락했습니다. 곧 도착합니다.",
                    "IN_PROGRESS": "운행이 시작되었습니다.",
                };
                // 예외: 기존 최상위 notification 필드 유지 (Android 고객앱 호환). apns 블록만 추가
                const statusBody = statusMessages[afterData.status] || `상태가 ${afterData.status}(으)로 변경되었습니다.`;
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
                        body: statusBody,
                    },
                    android: {
                        priority: "high",
                        ttl: 60000,
                    },
                    apns: {
                        headers: {
                            "apns-push-type": "alert",
                            "apns-priority": "10",
                            "apns-expiration": String(Math.floor(Date.now() / 1000) + 60),
                        },
                        payload: {
                            aps: {
                                alert: { title: "콜 상태 알림", body: statusBody },
                                sound: "default",
                                "content-available": 1,
                                "mutable-content": 1,
                                "interruption-level": "active",
                            },
                        },
                    },
                });
                logger.info(`[onCallStatusChanged:${callId}] 고객 FCM 전송 완료: ${afterData.status}`);
            }
        }
        catch (customerError) {
            logger.error(`[onCallStatusChanged:${callId}] 고객 FCM 오류:`, customerError);
        }
    }
    // acceptanceEvents 기록 (Phase 6 ① B) — ASSIGNED→ACCEPTED / ASSIGNED→WAITING+rejectedByDriver
    try {
        const beforeStatus = beforeData.status;
        const afterStatus = afterData.status;
        const assignedAt = (_h = beforeData.assignedTimestamp) !== null && _h !== void 0 ? _h : firestore_2.Timestamp.now();
        if (beforeStatus === "ASSIGNED" && afterStatus === "ACCEPTED" && afterData.assignedDriverId) {
            await (0, acceptanceEvents_1.recordAcceptanceEvent)({
                callId,
                assignedDriverId: afterData.assignedDriverId,
                provinceId,
                cityId,
                officeId,
                outcome: "accepted",
                assignedAt,
            });
        }
        else if (beforeStatus === "ASSIGNED" && afterStatus === "WAITING" && afterData.rejectedByDriver) {
            await (0, acceptanceEvents_1.recordAcceptanceEvent)({
                callId,
                assignedDriverId: afterData.rejectedByDriver,
                provinceId,
                cityId,
                officeId,
                outcome: "rejected",
                assignedAt,
                rejectReason: "driver_rejected",
            });
        }
    }
    catch (analyticsError) {
        logger.warn(`[onCallStatusChanged:${callId}] acceptanceEvents 기록 실패`, analyticsError);
    }
});
// pending_drivers 컬렉션에 새 문서 생성 시 FCM 알림 전송
exports.onDriverSignupRequest = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "pending_drivers/{driverId}"
}, async (event) => {
    var _a;
    const driverId = event.params.driverId;
    const driverData = (_a = event.data) === null || _a === void 0 ? void 0 : _a.data();
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
        const tokens = [];
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
        // 예외: 기존 최상위 notification + android.notification 유지 (알림 채널 호환). apns 블록만 추가
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
                priority: "high",
                ttl: 60000,
                notification: {
                    sound: "default",
                    clickAction: "com.designated.callmanager.HOME",
                    channelId: "driver_approval_channel"
                }
            },
            apns: {
                headers: {
                    "apns-push-type": "alert",
                    "apns-priority": "10",
                    "apns-expiration": String(Math.floor(Date.now() / 1000) + 60),
                },
                payload: {
                    aps: {
                        alert: { title: "🚗 새 기사 가입 신청", body: `${name}님이 가입 승인을 기다리고 있습니다.` },
                        sound: "default",
                        "content-available": 1,
                        "mutable-content": 1,
                        "interruption-level": "active",
                    },
                },
            },
        };
        // 모든 관리자에게 전송
        for (const token of tokens) {
            try {
                await admin.messaging().send(Object.assign(Object.assign({}, payload), { token }));
                logger.info(`[onDriverSignupRequest:${driverId}] FCM sent to admin token: ${token.substring(0, 10)}...`);
            }
            catch (error) {
                logger.error(`[onDriverSignupRequest:${driverId}] Failed to send FCM:`, error);
            }
        }
    }
    catch (error) {
        logger.error(`[onDriverSignupRequest:${driverId}] Error processing pending driver:`, error);
    }
});
// 새 콜 알림 함수 제거됨
// 이유: Call Detector에서 로컬 데이터로 즉시 팝업 생성하므로 FCM 알림 불필요
// 기존 함수는 중복 알림 및 앱 재빌드 시 이전 콜 재팝업 문제 야기
// =============================
// 공유콜 상태 동기화 - 수락사무실의 콜 상태를 원사무실에 반영
// =============================
exports.onSharedCallStatusSync = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
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
        const sharedCallData = sharedCallSnap.data();
        // 원사무실 콜 업데이트 (originalCallId 사용)
        const originalCallId = sharedCallData.originalCallId;
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
                    updatedAt: firestore_2.FieldValue.serverTimestamp()
                };
                await originalCallRef.update(updateData);
                logger.info(`[shared-sync:${callId}] 원사무실 콜 상태 업데이트 완료: ${originalCallId} → ${afterData.status}`);
            }
            else {
                logger.warn(`[shared-sync:${callId}] 원사무실 콜을 찾을 수 없습니다: ${originalCallId}`);
            }
        }
        else {
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
                    updatedAt: firestore_2.FieldValue.serverTimestamp()
                });
                logger.info(`[shared-sync:${callId}] 원사무실 콜 상태 업데이트 완료 (fallback): ${afterData.sourceSharedCallId} → ${afterData.status}`);
            }
        }
    }
    catch (error) {
        logger.error(`[shared-sync:${callId}] 상태 동기화 오류:`, error);
    }
});
// =============================
// 공유 콜에서 복사된 일반 콜이 COMPLETED 될 때 트리거
// 1) 원본 shared_calls 문서를 COMPLETED로 업데이트
// 2) 포인트 가감 처리 (10% 수수료)
// =============================
exports.onSharedCallCompleted = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
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
            const sharedCallData = sharedCallSnap.data();
            // shared_calls 문서를 COMPLETED로 업데이트
            await sharedCallRef.update({
                status: "COMPLETED",
                completedAt: firestore_2.FieldValue.serverTimestamp(),
                destCallId: callId
            });
            // 2. 포인트 처리 — 식당앱 콜이면 분기 (PR 1, plan §3.3)
            // sourceRestaurantId 존재 시 = 식당앱 발생 콜 → processRestaurantCallPayout
            // 없으면 기존 사무실↔사무실 zero-sum 분배 (processSharedCallPoints)
            if (sharedCallData.sourceRestaurantId) {
                await (0, points_1.processRestaurantCallPayout)(sharedCallData, provinceId, cityId, officeId, fare, sourceSharedCallId);
            }
            else {
                const commissionResult = await (0, points_1.processSharedCallPoints)(sharedCallData, provinceId, cityId, officeId, fare, sourceSharedCallId);
                // A(콜 올린 사무실) '파악': 완료·수수료 통지 (FCM + 블랙박스). best-effort — 통지 실패가 회수를 깨지 않음.
                if (commissionResult.processed && commissionResult.commission > 0) {
                    try {
                        const srcProv = sharedCallData.sourceProvinceId;
                        const srcCity = sharedCallData.sourceCityId;
                        const srcOffice = sharedCallData.sourceOfficeId;
                        const commission = commissionResult.commission;
                        const newBalance = commissionResult.sourceBalance;
                        const dep = sharedCallData.departure || "";
                        const dest = sharedCallData.destination || "";
                        // FCM → A 관리자 (admins where associatedOffice == source office)
                        const adminQ = await admin.firestore().collection("admins")
                            .where("associatedProvinceId", "==", srcProv)
                            .where("associatedCityId", "==", srcCity)
                            .where("associatedOfficeId", "==", srcOffice)
                            .get();
                        const commissionTokens = [];
                        adminQ.docs.forEach((d) => { const f = d.data().fcmToken; if (f)
                            commissionTokens.push(f); });
                        if (commissionTokens.length > 0) {
                            await admin.messaging().sendEachForMulticast({
                                tokens: commissionTokens,
                                notification: {
                                    title: "공유콜 완료 · 수수료 적립",
                                    body: `수수료 +${commission}P (잔액 ${newBalance}P)`,
                                },
                                data: {
                                    type: "SHARED_CALL_COMMISSION",
                                    departure: dep,
                                    destination: dest,
                                    fare: String(fare),
                                    commission: String(commission),
                                    newBalance: String(newBalance),
                                },
                            });
                        }
                        // 블랙박스 9-B: A 채팅에 무음 시스템 메시지 (push→pull, 팬아웃 비용 0)
                        await (0, chat_2.postSystemMessage)(srcProv, srcCity, srcOffice, `공유콜 완료 · 수수료 +${commission}P (잔액 ${newBalance}P)`);
                    }
                    catch (notifyErr) {
                        logger.error(`[call-completed:${callId}] A 완료·수수료 통지 실패(best-effort)`, notifyErr);
                    }
                }
            }
            logger.info(`[call-completed:${callId}] 공유콜 완료 처리 및 포인트 분배 완료. SharedCallId: ${sourceSharedCallId}`);
        }
        catch (error) {
            logger.error(`[call-completed:${callId}] 공유콜 완료 처리 오류:`, error);
        }
    }
});
// [STL-10] finalizeWorkDay 구 정산 함수 제거됨 (새 정산 시스템: settlementSessions 사용)
// 픽업 기사 데이터 마이그레이션 함수 (한 번만 실행)
exports.migratePickupDrivers = (0, https_1.onCall)({
    region: "asia-northeast3",
}, async (request) => {
    logger.info("픽업 기사 데이터 마이그레이션 시작");
    try {
        const results = {
            found: 0,
            migrated: 0,
            errors: 0,
            details: []
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
            }
            catch (error) {
                results.errors++;
                logger.error(`픽업 기사 마이그레이션 오류: ${doc.id}`, error);
                results.details.push(`❌ ${doc.id} 마이그레이션 실패: ${error}`);
            }
        }
        logger.info(`픽업 기사 마이그레이션 완료: ${results.migrated}/${results.found} 성공, ${results.errors} 오류`);
        return Object.assign({ success: true, message: `픽업 기사 마이그레이션 완료` }, results);
    }
    catch (error) {
        logger.error("픽업 기사 마이그레이션 전체 오류:", error);
        return {
            success: false,
            message: `마이그레이션 실패: ${error}`,
            error: error
        };
    }
});
// =============================
// 🚨 FCM 테스트 함수 (HTTP 트리거)
// =============================
exports.testFcmMessage = (0, https_1.onRequest)({ region: "asia-northeast3" }, async (req, res) => {
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
            priority: "high",
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
    }
    catch (error) {
        logger.error("❌ [testFcmMessage] FCM 메시지 전송 실패:", error);
        res.status(500).json({
            success: false,
            error: error
        });
    }
});
// 어트리뷰션 점수 계산 함수 (웹과 앱 두 형식 모두 지원)
function calculateAttributionScore(attribution, currentFingerprint) {
    var _a;
    let score = 0;
    const scoreDetails = [];
    logger.info(`[점수계산] 시작 - source: ${attribution.source}`);
    logger.info(`[점수계산] attribution 데이터:`, {
        screenResolution: attribution.screenResolution,
        timezone: attribution.timezone,
        language: attribution.language,
        platform: attribution.platform,
        userAgent: (_a = attribution.userAgent) === null || _a === void 0 ? void 0 : _a.substring(0, 100),
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
        }
        else {
            logger.info(`[점수계산] 화면해상도 불일치: ${attribution.screenResolution} ≠ ${currentFingerprint.screenResolution}`);
        }
        // 타임존 매칭 (30점)
        if (attribution.timezone === currentFingerprint.timezone) {
            score += 30;
            scoreDetails.push("타임존(30)");
            logger.info(`[점수계산] 타임존 매칭: ${attribution.timezone} = ${currentFingerprint.timezone} (+30점)`);
        }
        else {
            logger.info(`[점수계산] 타임존 불일치: ${attribution.timezone} ≠ ${currentFingerprint.timezone}`);
        }
        // 언어 매칭 (20점)
        if (attribution.language === currentFingerprint.language) {
            score += 20;
            scoreDetails.push("언어(20)");
            logger.info(`[점수계산] 언어 매칭: ${attribution.language} = ${currentFingerprint.language} (+20점)`);
        }
        else {
            logger.info(`[점수계산] 언어 불일치: ${attribution.language} ≠ ${currentFingerprint.language}`);
        }
        // 플랫폼 매칭 - 웹은 Win32, 앱은 Android이므로 교차 플랫폼 보너스
        if (attribution.platform && attribution.platform.includes("Win") &&
            currentFingerprint.deviceModel) {
            score += 20;
            scoreDetails.push("교차플랫폼(20)");
            logger.info(`[점수계산] 교차 플랫폼 보너스: Win → Android (+20점)`);
        }
        else {
            logger.info(`[점수계산] 교차 플랫폼 조건 불충족: platform=${attribution.platform}, deviceModel=${currentFingerprint.deviceModel}`);
        }
        // userAgent에서 추출 가능한 정보 매칭
        if (attribution.userAgent && currentFingerprint.osVersion) {
            if (attribution.userAgent.includes("Android") ||
                attribution.userAgent.includes("Mobile")) {
                score += 10;
                scoreDetails.push("UserAgent(10)");
                logger.info(`[점수계산] UserAgent 모바일 매칭 (+10점)`);
            }
            else {
                logger.info(`[점수계산] UserAgent 모바일 불일치: ${attribution.userAgent.substring(0, 50)}`);
            }
        }
        else {
            logger.info(`[점수계산] UserAgent 조건 불충족: userAgent=${!!attribution.userAgent}, osVersion=${!!currentFingerprint.osVersion}`);
        }
    }
    else {
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
exports.matchAttribution = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    logger.warn(`[matchAttribution] 함수 호출됨 - 전체 데이터:`, JSON.stringify(request.data));
    const { fingerprint, phoneNumber } = request.data;
    logger.warn(`[matchAttribution] 시작 - phoneNumber: ${phoneNumber}`);
    try {
        // 모든 지역의 모든 사무실에서 attribution 데이터 찾기
        const db = admin.firestore();
        const provincesSnapshot = await db.collection("provinces").get();
        let bestMatch = null;
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
                                (score === bestScore && attribution.createdAt && (bestMatch === null || bestMatch === void 0 ? void 0 : bestMatch.createdAt) &&
                                    attribution.createdAt.toMillis() > bestMatch.createdAt.toMillis());
                            if (isNewBetter) {
                                bestScore = score;
                                bestMatch = Object.assign(Object.assign({ id: doc.id }, attribution), { provinceId: provinceId, cityId: cityId, officeId: officeId });
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
                linkedAt: firestore_2.FieldValue.serverTimestamp(),
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
    }
    catch (error) {
        logger.error(`[matchAttribution] 오류 발생:`, error);
        return {
            success: false,
            requiresManualEntry: true,
            score: 0,
            confidence: "ERROR",
            error: error
        };
    }
});
// 수동 사무실 선택 저장 함수
exports.saveManualAttribution = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    const { phoneNumber, officeId, reason } = request.data;
    logger.info(`[saveManualAttribution] 수동 선택 저장 - phoneNumber: ${phoneNumber}, officeId: ${officeId}`);
    try {
        await admin.firestore().collection("attributions").add({
            phoneNumber,
            officeId,
            source: "manual",
            reason,
            linkedAt: firestore_2.FieldValue.serverTimestamp(),
            timestamp: firestore_2.FieldValue.serverTimestamp()
        });
        return { success: true };
    }
    catch (error) {
        logger.error(`[saveManualAttribution] 오류:`, error);
        return { success: false, error };
    }
});
// 토큰 기반 Attribution 매칭 함수
exports.matchByToken = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
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
                        const now = firestore_2.Timestamp.now();
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
    }
    catch (error) {
        logger.error(`[matchByToken] 오류 발생:`, error);
        return {
            success: false,
            message: "토큰 처리 중 오류가 발생했습니다",
            error: error
        };
    }
});
// 토큰 상태 업데이트 함수 (앱에서 호출)
exports.claimToken = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    const { token, phoneNumber } = request.data;
    logger.info(`[claimToken] 토큰 사용 처리 - token: ${token}, phoneNumber: ${phoneNumber}`);
    try {
        const db = admin.firestore();
        await db.collection("attributionTokens").doc(token).update({
            status: "claimed",
            claimedAt: firestore_2.FieldValue.serverTimestamp(),
            claimedBy: phoneNumber || "unknown"
        });
        logger.info(`[claimToken] 토큰 사용 처리 완료`);
        return { success: true };
    }
    catch (error) {
        logger.error(`[claimToken] 오류 발생:`, error);
        return { success: false, error };
    }
});
// =============================
// 기사가 운행 취소 시 손님앱에 알림 전송
// ASSIGNED -> HOLD 또는 CANCELLED_BY_DRIVER 감지
// =============================
exports.onCallCancelledByDriver = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    var _a;
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
    const cancelledFromAssigned = (beforeData.status === "ASSIGNED" || beforeData.status === "ACCEPTED" || beforeData.status === "IN_PROGRESS") &&
        (afterData.status === "HOLD" || afterData.status === "CANCELLED_BY_DRIVER" || afterData.status === "CANCELED" || afterData.status === "CANCELLED_BY_CUSTOMER");
    const cancelledByCustomer = (beforeData.status === "WAITING" || beforeData.status === "REQUESTED" || beforeData.status === "ASSIGNED" || beforeData.status === "ACCEPTED") &&
        (afterData.status === "CANCELLED_BY_CUSTOMER");
    if (!cancelledFromAssigned && !cancelledByCustomer) {
        return;
    }
    // 포인트 환불 처리 (종료 상태에서만, HOLD는 재배차 가능하므로 제외)
    const isTerminalCancel = afterData.status === "CANCELED" || afterData.status === "CANCELLED" || afterData.status === "CANCELLED_BY_DRIVER" || afterData.status === "CANCELLED_BY_CUSTOMER";
    const pointsUsed = afterData.pointsUsed || 0;
    if (isTerminalCancel && pointsUsed > 0 && afterData.phoneNumber) {
        try {
            const refundResult = await (0, points_1.refundCustomerPointsOnCancel)(provinceId, cityId, officeId, callId, afterData.phoneNumber, pointsUsed);
            if (refundResult.success) {
                logger.info(`[${callId}] 포인트 ${pointsUsed}P 환불 완료`);
            }
            else {
                logger.warn(`[${callId}] 포인트 환불 스킵: ${refundResult.error}`);
            }
        }
        catch (error) {
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
                    const driverPayload = (0, fcmPayload_1.buildFcmPayload)({
                        data: {
                            type: "call_cancelled",
                            callId: callId,
                            title: "콜 취소",
                            body: "고객이 콜을 취소했습니다",
                            cancelReason: "고객이 콜을 취소했습니다"
                        },
                        title: "콜 취소",
                        body: "고객이 콜을 취소했습니다",
                        level: "time-sensitive",
                        ttlSeconds: 60,
                    }, driverFcmToken);
                    await admin.messaging().send(driverPayload);
                    logger.info(`[${callId}] 기사에게 고객 취소 알림 전송 완료`);
                }
            }
        }
        catch (driverError) {
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
        }
        catch (sharedError) {
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
        const customerFcmToken = (_a = customerDoc.data()) === null || _a === void 0 ? void 0 : _a.fcmToken;
        if (!customerFcmToken) {
            logger.warn(`[${callId}] 고객 FCM 토큰 없음: ${customerPhone}`);
            return;
        }
        // 고객에게 취소 알림 전송
        const customerPayload = (0, fcmPayload_1.buildFcmPayload)({
            data: {
                type: "CALL_CANCELLED",
                callId: callId,
                cancelReason: afterData.cancelReason || "운행취소"
            },
            title: "콜 취소",
            body: `콜이 취소되었습니다: ${afterData.cancelReason || "운행취소"}`,
            level: "time-sensitive",
            ttlSeconds: 60,
        }, customerFcmToken);
        await admin.messaging().send(customerPayload);
        logger.info(`[${callId}] 고객에게 취소 알림 전송 완료: ${customerPhone}`);
    }
    catch (error) {
        logger.error(`[${callId}] 고객 취소 알림 전송 오류:`, error);
    }
    // 매니저에게 취소 알림 전송
    try {
        const managerTokensSnapshot = await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("managerTokens")
            .get();
        if (!managerTokensSnapshot.empty) {
            const tokens = [];
            managerTokensSnapshot.forEach((doc) => {
                const token = doc.data().fcmToken;
                if (token)
                    tokens.push(token);
            });
            if (tokens.length > 0) {
                const cancelledBy = afterData.status === "CANCELLED_BY_CUSTOMER"
                    ? "고객"
                    : afterData.status === "CANCELLED_BY_DRIVER"
                        ? "기사"
                        : "관리자";
                await admin.messaging().sendEachForMulticast((0, fcmPayload_1.buildMulticastFcmPayload)({
                    data: {
                        type: "CALL_STATUS_UPDATE",
                        callId: callId,
                        status: afterData.status,
                        message: `${cancelledBy} 취소: ${afterData.cancelReason || "운행취소"}`
                    },
                    title: "콜 취소",
                    body: `${cancelledBy} 취소: ${afterData.cancelReason || "운행취소"}`,
                    level: "active",
                    ttlSeconds: 60,
                }, tokens));
                logger.info(`[${callId}] 매니저에게 취소 알림 전송 완료`);
            }
        }
    }
    catch (managerError) {
        logger.error(`[${callId}] 매니저 취소 알림 전송 오류:`, managerError);
    }
});
// =============================
// 신규 회원 가입 시 콜매니저에 알림 전송
// =============================
exports.onNewCustomerRegistered = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/customers/{customerId}"
}, async (event) => {
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
        // 예외: 기존 최상위 notification 유지 (Android 콜매니저 호환). apns 블록 추가
        const notifBody = `${customerData.name || '신규 회원'}님이 가입했습니다${referralInfo}`;
        const message = {
            notification: {
                title: "🎉 새 회원 가입",
                body: notifBody
            },
            data: {
                type: "new_customer",
                customerId: customerId,
                customerName: customerData.name || "",
                customerPhone: customerData.phoneNumber || "",
                referralDriverId: customerData.referralDriverId || "",
                referralDriverName: customerData.referralDriverName || ""
            },
            apns: {
                headers: {
                    "apns-push-type": "alert",
                    "apns-priority": "10",
                },
                payload: {
                    aps: {
                        alert: { title: "🎉 새 회원 가입", body: notifBody },
                        sound: "default",
                        "content-available": 1,
                        "mutable-content": 1,
                        "interruption-level": "active",
                    },
                },
            },
        };
        // 각 토큰으로 알림 전송
        const sendResults = await Promise.allSettled(tokenDocs.map(({ token }) => admin.messaging().send(Object.assign(Object.assign({}, message), { token }))));
        let successCount = 0;
        const invalidTokenDocIds = [];
        // 실패한 알림 처리 및 만료된 토큰 수집
        sendResults.forEach((result, index) => {
            var _a, _b;
            if (result.status === 'fulfilled') {
                successCount++;
            }
            else {
                const error = result.reason;
                logger.error(`[onNewCustomerRegistered] 토큰 ${index + 1} 알림 전송 실패:`, error);
                // 만료되거나 무효한 토큰 감지
                if (((_a = error === null || error === void 0 ? void 0 : error.errorInfo) === null || _a === void 0 ? void 0 : _a.code) === 'messaging/registration-token-not-registered' ||
                    ((_b = error === null || error === void 0 ? void 0 : error.errorInfo) === null || _b === void 0 ? void 0 : _b.code) === 'messaging/invalid-registration-token') {
                    invalidTokenDocIds.push(tokenDocs[index].docId);
                    logger.warn(`[onNewCustomerRegistered] 만료된 토큰 발견 - 삭제 예정: ${tokenDocs[index].docId}`);
                }
            }
        });
        // 만료된 토큰 자동 삭제 및 갱신 요청
        if (invalidTokenDocIds.length > 0) {
            const deletePromises = invalidTokenDocIds.map(docId => admin.firestore()
                .collection("provinces").doc(provinceId)
                .collection("cities").doc(cityId)
                .collection("offices").doc(officeId)
                .collection("managerTokens")
                .doc(docId)
                .delete());
            await Promise.allSettled(deletePromises);
            logger.info(`[onNewCustomerRegistered] 만료된 토큰 ${invalidTokenDocIds.length}개 삭제 완료`);
            // 각 만료된 토큰에 대해 갱신 요청 생성
            const refreshRequests = invalidTokenDocIds.map(managerId => admin.firestore()
                .collection("provinces").doc(provinceId)
                .collection("cities").doc(cityId)
                .collection("offices").doc(officeId)
                .collection("tokenRefreshRequests")
                .doc(managerId)
                .set({
                managerId: managerId,
                requestedAt: firestore_2.Timestamp.now(),
                reason: "token_expired",
                processed: false
            }));
            await Promise.allSettled(refreshRequests);
            logger.info(`[onNewCustomerRegistered] 토큰 갱신 요청 ${invalidTokenDocIds.length}개 생성 완료`);
        }
        logger.info(`[onNewCustomerRegistered] 알림 전송 완료 - 성공: ${successCount}/${tokenDocs.length}`);
    }
    catch (error) {
        logger.error(`[onNewCustomerRegistered] 오류 발생:`, error);
    }
});
// =============================
// 사무실 통계 자동 업데이트
// =============================
// 기사 추가/삭제 시 driverCount 업데이트
exports.onDriverCountChange = (0, firestore_1.onDocumentWritten)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}"
}, async (event) => {
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
            statsUpdatedAt: firestore_2.FieldValue.serverTimestamp()
        });
        logger.info(`[${officeId}] 기사 수 업데이트 완료: ${driverCount}명`);
    }
    catch (error) {
        logger.error(`[${officeId}] 기사 수 업데이트 실패:`, error);
    }
});
// 고객 추가/삭제 시 customerCount 업데이트
exports.onCustomerCountChange = (0, firestore_1.onDocumentWritten)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/customers/{customerId}"
}, async (event) => {
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
            statsUpdatedAt: firestore_2.FieldValue.serverTimestamp()
        });
        logger.info(`[${officeId}] 고객 수 업데이트 완료: ${customerCount}명`);
    }
    catch (error) {
        logger.error(`[${officeId}] 고객 수 업데이트 실패:`, error);
    }
});
// =============================
// 콜 디텍터 크래시 시 사무실 관리자에게 FCM 알림 전송
// emergency_alerts 컬렉션에 EMERGENCY_CRASH_ALERT 생성 시 트리거
// =============================
exports.onCallDetectorCrash = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "emergency_alerts/{alertId}"
}, async (event) => {
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
        const fcmTokens = [];
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
        // 예외: 기존 최상위 notification + emergency_alerts 채널 유지 (긴급 알림). apns 블록 추가
        const sendResults = await Promise.allSettled(fcmTokens.map(token => admin.messaging().send({
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
            },
            apns: {
                headers: {
                    "apns-push-type": "alert",
                    "apns-priority": "10",
                    "apns-expiration": String(Math.floor(Date.now() / 1000) + 60),
                },
                payload: {
                    aps: {
                        alert: notification,
                        sound: "default",
                        "content-available": 1,
                        "mutable-content": 1,
                        "interruption-level": "time-sensitive",
                    },
                },
            },
        })));
        // 결과 로깅
        const successCount = sendResults.filter(r => r.status === 'fulfilled').length;
        const failedCount = sendResults.filter(r => r.status === 'rejected').length;
        sendResults.forEach((result, index) => {
            if (result.status === 'rejected') {
                logger.error(`[${alertId}] 토큰 ${index + 1} 알림 전송 실패:`, result.reason);
            }
        });
        logger.info(`[${alertId}] 알림 전송 완료 - 성공: ${successCount}/${fcmTokens.length}, 실패: ${failedCount}`);
    }
    catch (error) {
        logger.error(`[${alertId}] 오류 발생:`, error);
    }
});
// =============================
// 스케줄 기반 데이터 정리 (매일 11시)
// - WAITING 콜 (1시간 이상)
// - shared_calls (1시간 이상)
// - 만료된 attributions (24시간 이상)
// =============================
exports.scheduledDataCleanup = (0, scheduler_1.onSchedule)({
    schedule: "0 11 * * *", // 매일 오전 11시 (한국 시간)
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "512MiB",
    timeoutSeconds: 540
}, async (event) => {
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
                        .where("timestamp", "<", firestore_2.Timestamp.fromMillis(oneHourAgo))
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
                        .where("timestamp", "<", firestore_2.Timestamp.fromMillis(oneHourAgo))
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
            .where("timestamp", "<", firestore_2.Timestamp.fromMillis(oneHourAgo))
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
        }
        else {
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
    }
    catch (error) {
        logger.error("❌ 자동 데이터 정리 중 오류 발생:", error);
        throw error;
    }
});
// =============================
// COMPLETED 콜 아카이브: 매일 오전 11시 실행
// - 7일 이상 된 COMPLETED 콜 중 50개 초과분만 아카이브
// - Cloud Storage에 월별 JSON Lines 파일로 저장
// =============================
exports.archiveOldCalls = (0, scheduler_1.onSchedule)({
    schedule: "0 11 * * *", // 매일 오전 11시 (한국 시간)
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "1GiB",
    timeoutSeconds: 540
}, async (event) => {
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
                        var _a, _b;
                        const completedAt = ((_a = doc.data().completedAt) === null || _a === void 0 ? void 0 : _a.toMillis()) || ((_b = doc.data().updatedAt) === null || _b === void 0 ? void 0 : _b.toMillis()) || 0;
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
                        var _a, _b, _c;
                        const data = doc.data();
                        return JSON.stringify(Object.assign(Object.assign({ id: doc.id }, data), { completedAt: ((_a = data.completedAt) === null || _a === void 0 ? void 0 : _a.toMillis()) || null, createdAt: ((_b = data.createdAt) === null || _b === void 0 ? void 0 : _b.toMillis()) || null, updatedAt: ((_c = data.updatedAt) === null || _c === void 0 ? void 0 : _c.toMillis()) || null, archivedAt: now }));
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
    }
    catch (error) {
        logger.error("❌ COMPLETED 콜 아카이브 중 오류 발생:", error);
        throw error;
    }
});
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
exports.getArchivedStats = (0, https_1.onCall)({
    region: "asia-northeast3",
    cors: ["http://localhost:3000", "https://calldetector-5d61e.web.app", "https://calldetector-5d61e.firebaseapp.com"]
}, async (request) => {
    var _a;
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
        const dates = [];
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
            officeStats: {},
            dailyStats: {}
        };
        // 조회할 사무실 목록 결정
        let officesToQuery = [];
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
                    officeName: ((_a = officeDoc.data()) === null || _a === void 0 ? void 0 : _a.name) || officeId
                });
            }
        }
        else {
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
                }
                catch (error) {
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
    }
    catch (error) {
        logger.error("❌ 아카이브 통계 조회 오류:", error);
        throw error;
    }
});
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
exports.searchArchivedCalls = (0, https_1.onCall)({
    region: "asia-northeast3",
    cors: ["http://localhost:3000", "https://calldetector-5d61e.web.app", "https://calldetector-5d61e.firebaseapp.com"]
}, async (request) => {
    var _a, _b;
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
        const dates = [];
        for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
            dates.push(d.toISOString().split('T')[0]);
        }
        // 조회할 사무실 목록 결정
        let officesToQuery = [];
        if (provinceId && cityId && officeId) {
            const officeDoc = await db.collection('provinces').doc(provinceId)
                .collection('cities').doc(cityId)
                .collection('offices').doc(officeId).get();
            if (officeDoc.exists) {
                officesToQuery.push({
                    provinceId,
                    cityId,
                    officeId,
                    officeName: ((_a = officeDoc.data()) === null || _a === void 0 ? void 0 : _a.name) || officeId
                });
            }
        }
        else {
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
        const results = [];
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
                        if (driverName && !((_b = call.assignedDriverName) === null || _b === void 0 ? void 0 : _b.includes(driverName))) {
                            match = false;
                        }
                        if (match) {
                            results.push(Object.assign(Object.assign({}, call), { officeName: office.officeName, provinceId: office.provinceId, cityId: office.cityId, officeId: office.officeId }));
                        }
                    }
                }
                catch (error) {
                    if (error.code !== 404) {
                        logger.warn(`파일 읽기 실패: ${filePath}`, error);
                    }
                }
            }
        }
        logger.info(`✅ 검색 완료: ${results.length}개 결과`);
        // 완료 시간 기준 내림차순 정렬
        results.sort((a, b) => {
            var _a, _b;
            const timeA = ((_a = a.completedAt) === null || _a === void 0 ? void 0 : _a._seconds) || 0;
            const timeB = ((_b = b.completedAt) === null || _b === void 0 ? void 0 : _b._seconds) || 0;
            return timeB - timeA;
        });
        return {
            success: true,
            results: results.slice(0, 100), // 최대 100개 반환
            totalCount: results.length
        };
    }
    catch (error) {
        logger.error("❌ 아카이브 검색 오류:", error);
        throw error;
    }
});
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
exports.getOfficeReport = (0, https_1.onCall)({
    region: "asia-northeast3",
    cors: ["http://localhost:3000", "https://calldetector-5d61e.web.app", "https://calldetector-5d61e.firebaseapp.com"]
}, async (request) => {
    var _a;
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
        const dates = [];
        for (let d = new Date(startDate); d <= endDate; d.setDate(d.getDate() + 1)) {
            dates.push(d.toISOString().split('T')[0]);
        }
        // 리포트 데이터 구조
        const report = {
            office: {
                provinceId,
                cityId,
                officeId,
                officeName: (officeData === null || officeData === void 0 ? void 0 : officeData.name) || officeId,
                address: (officeData === null || officeData === void 0 ? void 0 : officeData.address) || 'N/A',
                phoneNumber: (officeData === null || officeData === void 0 ? void 0 : officeData.phoneNumber) || 'N/A'
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
            dailyData: [],
            driverStats: {},
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
                    if ((_a = call.completedAt) === null || _a === void 0 ? void 0 : _a._seconds) {
                        const hour = new Date(call.completedAt._seconds * 1000).getHours();
                        report.hourlyDistribution[hour]++;
                    }
                }
            }
            catch (error) {
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
    }
    catch (error) {
        logger.error("❌ 사무실 리포트 생성 오류:", error);
        throw error;
    }
});
// ====== 기사 상태 변경 알림 ======
/**
 * 기사 상태 변경 시 콜매니저로 FCM 알림 전송
 * - 로그인/로그아웃
 * - 배차 수락/거절
 * - 운행 시작 (ONLINE -> DRIVING)
 * - 운행 완료 (DRIVING -> WAITING)
 */
exports.onDriverStatusChange = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}"
}, async (event) => {
    var _a;
    const { provinceId, cityId, officeId, driverId } = event.params;
    if (!event.data || !event.data.after || !event.data.after.exists) {
        logger.info(`[기사상태] ${driverId}: 데이터 없음`);
        return;
    }
    const beforeData = (_a = event.data.before) === null || _a === void 0 ? void 0 : _a.data();
    const afterData = event.data.after.data();
    // 상태 변경 확인
    const statusChanged = (beforeData === null || beforeData === void 0 ? void 0 : beforeData.status) !== (afterData === null || afterData === void 0 ? void 0 : afterData.status);
    if (!statusChanged) {
        // 상태 변경이 없으면 알림 보내지 않음
        return;
    }
    const oldStatus = (beforeData === null || beforeData === void 0 ? void 0 : beforeData.status) || "UNKNOWN";
    const newStatus = (afterData === null || afterData === void 0 ? void 0 : afterData.status) || "UNKNOWN";
    const driverName = (afterData === null || afterData === void 0 ? void 0 : afterData.name) || "기사";
    const authUid = (afterData === null || afterData === void 0 ? void 0 : afterData.authUid) || driverId; // authUid 추가 (없으면 driverId 사용)
    logger.info(`[기사상태] ${driverId} (${driverName}): ${oldStatus} -> ${newStatus}`);
    // 블랙박스 9-B: 기사 출퇴근만 무음 기록 (ASSIGNED/DRIVING 등 잦은 전이는 콜 트리거가 이미 기록 → 제외).
    {
        let dutyText = "";
        if (newStatus === "OFFLINE")
            dutyText = `${driverName} 퇴근`;
        else if ((oldStatus === "OFFLINE" || oldStatus === "UNKNOWN") && (newStatus === "ONLINE" || newStatus === "WAITING"))
            dutyText = `${driverName} 출근`;
        if (dutyText)
            await (0, chat_2.postSystemMessage)(provinceId, cityId, officeId, dutyText);
    }
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
        const managerTokens = [];
        managerTokensSnapshot.forEach((doc) => {
            const token = doc.data().fcmToken;
            if (token)
                managerTokens.push(token);
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
            case "PENDING_CONFIRM":
                statusMessage = "정산대기";
                break;
            default:
                statusMessage = newStatus;
        }
        // FCM 메시지 생성
        const message = (0, fcmPayload_1.buildMulticastFcmPayload)({
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
                lastLoginTime: (afterData === null || afterData === void 0 ? void 0 : afterData.lastLoginTime) ? afterData.lastLoginTime.toMillis().toString() : "",
                timestamp: Date.now().toString()
            },
            title: "기사 상태 변경",
            body: `${driverName}: ${statusMessage}`,
            level: "active",
            ttlSeconds: 60,
        }, managerTokens);
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
    }
    catch (error) {
        logger.error(`[기사상태] ${driverId}: FCM 전송 오류`, error);
    }
});
// =============================
// 정산 세션 자동 업데이트: 콜 완료 시 정산 세션에 추가
// =============================
exports.onCallCompletedUpdateSettlement = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
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
        // 관리자 직접운행 콜은 정산 세션에 추가하지 않음 (별도 집계)
        if (afterData.handledByManager === true) {
            logger.info(`[Settlement:${callId}] 직접운행 콜 - 정산 세션 스킵`);
            return;
        }
        logger.info(`[Settlement:${callId}] 운행 완료 감지 - 정산 세션 업데이트 시작`);
        try {
            await (0, settlement_1.addCallToSettlementSession)(provinceId, cityId, officeId, afterData, callId);
            logger.info(`[Settlement:${callId}] 정산 세션 업데이트 완료`);
        }
        catch (error) {
            logger.error(`[Settlement:${callId}] 정산 세션 업데이트 실패:`, error);
        }
    }
});
// =============================
// 일일 정산 자동 마감: 매일 오전 10시 10분 실행
// - 전날 정산 세션을 자동으로 마감 처리
// =============================
exports.autoFinalizeSettlements = (0, scheduler_1.onSchedule)({
    schedule: "10 10 * * *", // 매일 오전 10시 10분 (한국 시간)
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "512MiB",
    timeoutSeconds: 300
}, async () => {
    logger.info("[Settlement] Starting scheduled auto-finalization");
    try {
        const result = await (0, settlement_1.autoFinalizeSettlementSessions)();
        logger.info(`[Settlement] Auto-finalization completed. Processed: ${result.processed}, Errors: ${result.errors.length}`);
        if (result.errors.length > 0) {
            logger.warn("[Settlement] Auto-finalization errors:", result.errors);
        }
    }
    catch (error) {
        logger.error("[Settlement] Auto-finalization failed:", error);
    }
});
// =============================
// 수동 정산 불일치 검사 (HTTP Callable)
// =============================
exports.manualCheckSettlementDiscrepancy = (0, https_1.onCall)({
    region: "asia-northeast3"
}, async (req) => {
    const { provinceId, cityId, officeId, sessionDate } = req.data || {};
    if (!provinceId || !cityId || !officeId || !sessionDate) {
        throw new Error("provinceId, cityId, officeId, sessionDate are required");
    }
    if (!req.auth) {
        throw new Error("Must be authenticated");
    }
    logger.info(`[Settlement] Manual discrepancy check: ${provinceId}/${cityId}/${officeId}/${sessionDate}`);
    const result = await (0, settlement_1.checkSettlementDiscrepancies)(provinceId, cityId, officeId, sessionDate);
    return result;
});
const _forceDeploy = Date.now() + 1000007; // 배포 강제용 더미 변수
void _forceDeploy; // 사용해서 컴파일 경고 해소
// =============================
// 기사 배차 알림 (Callable Function)
// 콜매니저에서 배차 시 직접 호출
// =============================
exports.notifyDriverAssignment = (0, https_1.onCall)({
    region: "asia-northeast3",
}, async (request) => {
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
        const fcmToken = driverData === null || driverData === void 0 ? void 0 : driverData.fcmToken;
        const driverName = (driverData === null || driverData === void 0 ? void 0 : driverData.name) || "기사";
        logger.info(`[notifyDriverAssignment] 기사 정보 - name: ${driverName}, token: ${fcmToken ? "exists" : "NONE"}`);
        if (!fcmToken) {
            logger.warn(`[notifyDriverAssignment] FCM 토큰 없음 - ${driverName}`);
            return { success: false, error: "No FCM token" };
        }
        // FCM 전송
        const bodyText = customerName ? `${customerName}님 콜이 배정되었습니다.` : "새로운 콜이 배정되었습니다.";
        const payload = (0, fcmPayload_1.buildFcmPayload)({
            data: {
                callId: callId,
                type: "call_assigned",
                title: "새로운 콜 배정",
                body: bodyText,
                departure: departure || "",
            },
            title: "새로운 콜 배정",
            body: bodyText,
            level: "time-sensitive",
        }, fcmToken);
        await admin.messaging().send(payload);
        logger.info(`[notifyDriverAssignment] FCM 전송 성공 - ${driverName}`);
        return { success: true, driverName: driverName };
    }
    catch (error) {
        logger.error("[notifyDriverAssignment] 오류:", error);
        return { success: false, error: String(error) };
    }
});
// =============================
// 기사 배차 취소 알림 (Callable Function)
// 콜매니저에서 콜 취소 시 직접 호출
// =============================
exports.notifyDriverCancellation = (0, https_1.onCall)({
    region: "asia-northeast3",
}, async (request) => {
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
        const fcmToken = driverData === null || driverData === void 0 ? void 0 : driverData.fcmToken;
        const driverName = (driverData === null || driverData === void 0 ? void 0 : driverData.name) || "기사";
        logger.info(`[notifyDriverCancellation] 기사 정보 - name: ${driverName}, token: ${fcmToken ? "exists" : "NONE"}`);
        if (!fcmToken) {
            logger.warn(`[notifyDriverCancellation] FCM 토큰 없음 - ${driverName}`);
            return { success: false, error: "No FCM token" };
        }
        const payload = (0, fcmPayload_1.buildFcmPayload)({
            data: {
                callId: callId,
                type: "call_cancelled",
                title: "배차 취소",
                body: "배정된 콜이 취소되었습니다.",
            },
            title: "배차 취소",
            body: "배정된 콜이 취소되었습니다.",
            level: "time-sensitive",
        }, fcmToken);
        await admin.messaging().send(payload);
        logger.info(`[notifyDriverCancellation] FCM 전송 성공 - ${driverName}`);
        return { success: true, driverName: driverName };
    }
    catch (error) {
        logger.error("[notifyDriverCancellation] 오류:", error);
        return { success: false, error: String(error) };
    }
});
/**
 * 업무 마감 및 로그인 상태 기사에게 알림 전송
 * Call Manager에서 업무 마감 시 호출
 */
exports.finalizeSettlementAndNotifyDrivers = (0, https_1.onCall)({
    region: "asia-northeast3",
}, async (request) => {
    var _a, _b;
    const { provinceId, cityId, officeId, sessionDate } = request.data;
    logger.info(`[finalizeSettlement] 호출됨 - ${provinceId}/${cityId}/${officeId}, date: ${sessionDate}`);
    if (!provinceId || !cityId || !officeId) {
        logger.error("[finalizeSettlement] 필수 파라미터 누락");
        return { success: false, error: "Missing required parameters" };
    }
    // 세션 날짜가 없으면 오늘 근무일 사용
    const targetDate = sessionDate || (0, settlement_1.getTodayWorkDate)();
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
        const wasAlreadyFinalized = ((_a = session === null || session === void 0 ? void 0 : session.metadata) === null || _a === void 0 ? void 0 : _a.isFinalized) === true;
        if (wasAlreadyFinalized) {
            logger.info(`[finalizeSettlement] 재마감 요청 - ${targetDate}`);
        }
        // 세션 마감 처리 (재마감도 허용)
        const finalizeTimestamp = firestore_2.Timestamp.now();
        await sessionRef.update({
            "metadata.isFinalized": true,
            "metadata.version": (((_b = session === null || session === void 0 ? void 0 : session.metadata) === null || _b === void 0 ? void 0 : _b.version) || 0) + 1,
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
        const totals = (session === null || session === void 0 ? void 0 : session.totals) || {
            callCount: 0,
            totalFare: 0,
            totalDeposit: 0,
            totalDriverShare: 0,
            totalCash: 0,
            totalCard: 0,
            totalCredit: 0,
            totalPoints: 0
        };
        const notifyResult = await (0, settlement_1.notifyDriversSettlementFinalized)(provinceId, cityId, officeId, targetDate, totals);
        logger.info(`[finalizeSettlement] 알림 전송 완료 - sent: ${notifyResult.sent}, skipped: ${notifyResult.skipped}`);
        return {
            success: true,
            sessionDate: targetDate,
            sent: notifyResult.sent,
            skipped: notifyResult.skipped,
            totals: totals,
            wasRefinalized: wasAlreadyFinalized
        };
    }
    catch (error) {
        logger.error("[finalizeSettlement] 오류:", error);
        return { success: false, error: String(error) };
    }
});
/**
 * 기사에게 알림 전송 (범용)
 * Call Manager에서 CarryOver 이체 등 알림 시 호출
 */
exports.sendDriverNotification = (0, https_1.onCall)({
    region: "asia-northeast3",
}, async (request) => {
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
        const fcmToken = driverData === null || driverData === void 0 ? void 0 : driverData.fcmToken;
        const driverName = (driverData === null || driverData === void 0 ? void 0 : driverData.name) || "기사";
        logger.info(`[sendDriverNotification] 기사 정보 - name: ${driverName}, token: ${fcmToken ? "exists" : "NONE"}`);
        if (!fcmToken) {
            logger.warn(`[sendDriverNotification] FCM 토큰 없음 - ${driverName}`);
            return { success: false, error: "No FCM token" };
        }
        // FCM 전송
        const payload = (0, fcmPayload_1.buildFcmPayload)({
            data: {
                type: type,
                title: title,
                body: body,
            },
            title: title,
            body: body,
            level: "active",
        }, fcmToken);
        await admin.messaging().send(payload);
        logger.info(`[sendDriverNotification] FCM 전송 성공 - ${driverName}, type: ${type}`);
        return { success: true, driverName: driverName };
    }
    catch (error) {
        logger.error("[sendDriverNotification] 오류:", error);
        return { success: false, error: String(error) };
    }
});
/**
 * 기사가 업무마감(dailySettlement) 제출 시 매니저에게 FCM 알림
 * designated_drivers/{uid} 문서의 dailySettlement.status가 PENDING_CONFIRM으로 변경되면 트리거
 */
exports.onDriverSettlementSubmitted = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}",
}, async (event) => {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const beforeData = (_b = (_a = event.data) === null || _a === void 0 ? void 0 : _a.before) === null || _b === void 0 ? void 0 : _b.data();
    const afterData = (_d = (_c = event.data) === null || _c === void 0 ? void 0 : _c.after) === null || _d === void 0 ? void 0 : _d.data();
    if (!beforeData || !afterData)
        return;
    const beforeStatus = (_e = beforeData.dailySettlement) === null || _e === void 0 ? void 0 : _e.status;
    const afterStatus = (_f = afterData.dailySettlement) === null || _f === void 0 ? void 0 : _f.status;
    // dailySettlement.status가 PENDING_CONFIRM으로 변경된 경우만 처리
    if (afterStatus !== "PENDING_CONFIRM" || beforeStatus === afterStatus)
        return;
    const { provinceId, cityId, officeId, driverId } = event.params;
    const driverName = afterData.name || "기사";
    const tripCount = ((_g = afterData.dailySettlement) === null || _g === void 0 ? void 0 : _g.tripCount) || 0;
    const realDeposit = ((_h = afterData.dailySettlement) === null || _h === void 0 ? void 0 : _h.realDeposit) || 0;
    logger.info(`[onDriverSettlementSubmitted] ${driverName}(${driverId}) 업무마감 제출 - ${tripCount}건, 실납입: ${realDeposit}원`);
    // 블랙박스 9-B: 업무마감 제출 무음 기록 (매니저 토큰 유무와 무관하게 항상 기록 → try 앞)
    await (0, chat_2.postSystemMessage)(provinceId, cityId, officeId, `${driverName} 업무마감 제출 — ${tripCount}건`);
    try {
        // 매니저 토큰 조회
        const managerTokensSnap = await admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("managerTokens")
            .get();
        if (managerTokensSnap.empty) {
            logger.warn(`[onDriverSettlementSubmitted] 매니저 토큰 없음 - office: ${officeId}`);
            return;
        }
        const tokens = [];
        managerTokensSnap.forEach(doc => {
            const token = doc.data().fcmToken;
            if (token)
                tokens.push(token);
        });
        if (tokens.length === 0) {
            logger.warn(`[onDriverSettlementSubmitted] 유효한 매니저 토큰 없음`);
            return;
        }
        // FCM 전송 (data-only: 백그라운드에서도 onMessageReceived 호출 보장)
        const notifTitle = "📋 업무마감 제출";
        const notifBody = `${driverName}님이 업무마감을 제출했습니다. (${tripCount}건, 실납입: ${realDeposit.toLocaleString()}원)`;
        const payload = {
            data: {
                type: "SETTLEMENT_SUBMITTED",
                driverId: driverId,
                driverName: driverName,
                tripCount: String(tripCount),
                realDeposit: String(realDeposit),
                title: notifTitle,
                body: notifBody,
            },
            android: {
                priority: "high",
            },
            apns: {
                headers: {
                    "apns-push-type": "alert",
                    "apns-priority": "10",
                },
                payload: {
                    aps: {
                        alert: { title: notifTitle, body: notifBody },
                        sound: "default",
                        "content-available": 1,
                        "mutable-content": 1,
                        "interruption-level": "active",
                    },
                },
            },
        };
        for (const token of tokens) {
            try {
                await admin.messaging().send(Object.assign(Object.assign({}, payload), { token }));
                logger.info(`[onDriverSettlementSubmitted] FCM 전송 성공 - token: ${token.substring(0, 10)}...`);
            }
            catch (error) {
                logger.error(`[onDriverSettlementSubmitted] FCM 전송 실패:`, error);
            }
        }
    }
    catch (error) {
        logger.error(`[onDriverSettlementSubmitted] 오류:`, error);
    }
});
// ========================================
// 사무실 신청 관리 시스템
// ========================================
/**
 * submitOfficeApplication - 홈페이지 신청 폼에서 호출하는 HTTP 엔드포인트
 * office_applications 컬렉션에 신청 데이터를 저장합니다.
 */
exports.submitOfficeApplication = (0, https_1.onRequest)({ region: "asia-northeast3", cors: true }, async (req, res) => {
    var _a, _b, _c;
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const data = req.body;
        // 필수 필드 검증
        if (!data.officeName || !data.ownerName || !data.phone) {
            res.status(400).json({ error: "필수 항목이 누락되었습니다 (사무실명, 대표자, 연락처)" });
            return;
        }
        // 전화번호 정규화 (하이픈 제거)
        const normalizedPhone = data.phone.replace(/-/g, "").trim();
        // 중복 신청 확인 (같은 전화번호로 pending 상태인 신청이 있는지)
        const existingQuery = await admin.firestore()
            .collection("office_applications")
            .where("phone", "==", normalizedPhone)
            .where("status", "==", "pending")
            .get();
        if (!existingQuery.empty) {
            res.status(409).json({ error: "이미 접수된 신청이 있습니다. 승인 대기 중입니다." });
            return;
        }
        const applicationDoc = {
            officeName: data.officeName.trim(),
            ownerName: data.ownerName.trim(),
            phone: normalizedPhone,
            gmail: ((_a = data.gmail) === null || _a === void 0 ? void 0 : _a.trim()) || "",
            region: ((_b = data.region) === null || _b === void 0 ? void 0 : _b.trim()) || "",
            dailyCalls: data.dailyCalls || "",
            message: ((_c = data.message) === null || _c === void 0 ? void 0 : _c.trim()) || "",
            ref: data.ref || "direct",
            status: "pending",
            createdAt: firestore_2.FieldValue.serverTimestamp(),
        };
        const docRef = await admin.firestore()
            .collection("office_applications")
            .add(applicationDoc);
        logger.info(`[submitOfficeApplication] 신청 저장 완료 - docId: ${docRef.id}, office: ${data.officeName}`);
        res.status(200).json({ success: true, applicationId: docRef.id });
    }
    catch (error) {
        logger.error("[submitOfficeApplication] 오류:", error);
        res.status(500).json({ error: "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요." });
    }
});
/**
 * approveOfficeApplication - 총관리자가 신청을 승인 (H2 설계)
 *
 * 변경 이력:
 *  - 2026-04-19: Gmail + emailVerified:true 로 Auth 계정 즉시 생성 (Google OAuth 전제)
 *  - 2026-04-20 (H2): Auth 계정 생성 제거. downloadInvites/{token} 발급만 수행.
 *    사장님이 call_manager 앱에서 직접 가입(registerOwner) 시 Auth 계정이 만들어짐.
 *
 * 처리 순서:
 *  1) offices/{officeId} 미리 생성 (ownerAuthUid=null)
 *  2) downloadInvites/{token} 생성 (active, usesRemaining=1, expires=+7d)
 *  3) office_applications 상태 업데이트 (approved, inviteToken 저장)
 *  4) 응답: inviteUrl, inviteToken, officeId 반환
 */
exports.approveOfficeApplication = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    var _a;
    // 인증 확인
    if (!request.auth) {
        throw new Error("인증이 필요합니다.");
    }
    // HEAD_MANAGER 권한 확인
    const callerDoc = await admin.firestore().doc(`admins/${request.auth.uid}`).get();
    if (!callerDoc.exists || !["HEAD_MANAGER", "SUPER_ADMIN"].includes((_a = callerDoc.data()) === null || _a === void 0 ? void 0 : _a.role)) {
        throw new Error("총관리자 권한이 필요합니다.");
    }
    const { applicationId, provinceId, cityId, officeName } = request.data;
    if (!applicationId || !provinceId || !cityId) {
        throw new Error("필수 항목이 누락되었습니다 (applicationId, provinceId, cityId)");
    }
    const appRef = admin.firestore().doc(`office_applications/${applicationId}`);
    const appDoc = await appRef.get();
    if (!appDoc.exists) {
        throw new Error("신청서를 찾을 수 없습니다.");
    }
    const appData = appDoc.data();
    if (appData.status !== "pending") {
        throw new Error(`이미 처리된 신청입니다 (상태: ${appData.status})`);
    }
    // 1) 사무실 문서 미리 생성 (ownerAuthUid=null, 사장님 가입 시 채워짐)
    const officeRef = admin.firestore()
        .collection(`provinces/${provinceId}/cities/${cityId}/offices`)
        .doc();
    await officeRef.set({
        name: officeName || appData.officeName,
        ownerName: appData.ownerName,
        phone: appData.phone,
        region: appData.region,
        status: "active",
        ownerAuthUid: null, // H2: 사장님 앱 가입 시 registerOwner 가 채움
        createdAt: firestore_2.FieldValue.serverTimestamp(),
        createdBy: request.auth.uid, // H2: 총관리자 UID (이전엔 ownerUid 였음)
    });
    logger.info(`[approveOfficeApplication] 사무실 생성 - officeId: ${officeRef.id}`);
    // 2) invite 토큰 생성 (URL-safe 43자, base64url)
    const crypto = require("crypto");
    const token = crypto.randomBytes(32).toString("base64url");
    const expiresAt = firestore_2.Timestamp.fromMillis(Date.now() + 7 * 24 * 60 * 60 * 1000);
    await admin.firestore().doc(`downloadInvites/${token}`).set({
        status: "active",
        applicationId,
        officeId: officeRef.id,
        provinceId,
        cityId,
        officeName: officeName || appData.officeName,
        ownerName: appData.ownerName,
        phone: appData.phone,
        gmail: appData.gmail || "",
        usesRemaining: 1,
        expiresAt,
        createdAt: firestore_2.FieldValue.serverTimestamp(),
        createdBy: request.auth.uid,
    });
    logger.info(`[approveOfficeApplication] invite 토큰 발급 - token: ${token.substring(0, 8)}..., expires: ${expiresAt.toDate().toISOString()}`);
    // 3) 신청 상태 업데이트
    await appRef.update({
        status: "approved",
        reviewedAt: firestore_2.FieldValue.serverTimestamp(),
        reviewedBy: request.auth.uid,
        inviteToken: token,
        ownerAuthUid: null, // H2: 가입 시점에 채워짐
        officeRef: `provinces/${provinceId}/cities/${cityId}/offices/${officeRef.id}`,
    });
    logger.info(`[approveOfficeApplication] 승인 완료 - applicationId: ${applicationId}`);
    const inviteUrl = `https://head-manager-web.web.app/owner/download?t=${token}`;
    return {
        success: true,
        inviteToken: token,
        inviteUrl,
        officeId: officeRef.id,
        officePath: `provinces/${provinceId}/cities/${cityId}/offices/${officeRef.id}`,
    };
});
/**
 * redeemDownloadToken - 초대 토큰으로 APK 다운로드 URL 을 받는다 (H2, 인증 불필요)
 *
 * 호출 시점: /owner/download?t=xxx 페이지 진입 시 (로그인 없음)
 * 토큰 상태 변경 안 함 — 다운로드 재시도 허용. 실제 invalidate 는 registerOwner 에서.
 *
 * 응답 에러 코드:
 *   - token_invalid : 존재하지 않는 토큰
 *   - token_expired : 7일 만료
 *   - token_used    : 이미 사용됨 (가입 완료)
 */
exports.redeemDownloadToken = (0, https_1.onRequest)({ region: "asia-northeast3", cors: true }, async (req, res) => {
    var _a, _b, _c, _d;
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed" });
        return;
    }
    try {
        const { token } = req.body || {};
        if (!token || typeof token !== "string") {
            res.status(400).json({ error: "token 파라미터가 필요합니다.", code: "token_invalid" });
            return;
        }
        // 1) invite 문서 조회
        const inviteRef = admin.firestore().doc(`downloadInvites/${token}`);
        const inviteSnap = await inviteRef.get();
        if (!inviteSnap.exists) {
            res.status(404).json({ error: "유효하지 않은 링크입니다.", code: "token_invalid" });
            return;
        }
        const invite = inviteSnap.data();
        // 2) 상태/만료/사용횟수 검증
        if (invite.status === "used" || ((_a = invite.usesRemaining) !== null && _a !== void 0 ? _a : 0) <= 0) {
            res.status(400).json({ error: "이미 사용된 링크입니다.", code: "token_used" });
            return;
        }
        if (invite.status !== "active") {
            res.status(400).json({ error: "유효하지 않은 링크입니다.", code: "token_invalid" });
            return;
        }
        const expiresMs = (_d = (_c = (_b = invite.expiresAt) === null || _b === void 0 ? void 0 : _b.toMillis) === null || _c === void 0 ? void 0 : _c.call(_b)) !== null && _d !== void 0 ? _d : 0;
        if (expiresMs && expiresMs < Date.now()) {
            res.status(400).json({ error: "만료된 링크입니다 (7일).", code: "token_expired" });
            return;
        }
        // 3) apk_releases 조회 (isLatest=true) - call_manager, call_detector 각 1건
        const releasesSnap = await admin.firestore()
            .collection("apk_releases")
            .where("isLatest", "==", true)
            .get();
        const byApp = {};
        releasesSnap.forEach((d) => {
            const r = d.data();
            if (r.appName)
                byApp[r.appName] = r;
        });
        const callManagerRel = byApp["call_manager"];
        const callDetectorRel = byApp["call_detector"];
        if (!callManagerRel || !callDetectorRel) {
            logger.error("[redeemDownloadToken] apk_releases 부족", { hasCallManager: !!callManagerRel, hasCallDetector: !!callDetectorRel });
            res.status(500).json({ error: "APK 릴리즈 정보가 준비되지 않았습니다. 관리자에게 문의하세요." });
            return;
        }
        // 4) Storage signed URL 생성 (1시간)
        //    responseDisposition: 모바일 브라우저가 inline 렌더하지 않고 다운로드하도록 강제
        const bucket = admin.storage().bucket();
        const oneHour = Date.now() + 60 * 60 * 1000;
        const cmFileName = `call_manager_${callManagerRel.version}.apk`;
        const cdFileName = `call_detector_${callDetectorRel.version}.apk`;
        const [cmUrl] = await bucket.file(callManagerRel.storagePath).getSignedUrl({
            action: "read",
            expires: oneHour,
            responseDisposition: `attachment; filename="${cmFileName}"`,
        });
        const [cdUrl] = await bucket.file(callDetectorRel.storagePath).getSignedUrl({
            action: "read",
            expires: oneHour,
            responseDisposition: `attachment; filename="${cdFileName}"`,
        });
        logger.info(`[redeemDownloadToken] 서명 URL 발급 - token: ${token.substring(0, 8)}..., office: ${invite.officeName}`);
        res.status(200).json({
            success: true,
            token, // 앱 가입 시 재입력 편의용
            officeName: invite.officeName,
            ownerName: invite.ownerName,
            callManager: {
                url: cmUrl,
                version: callManagerRel.version,
                fileSize: callManagerRel.fileSize,
                releaseNotes: callManagerRel.releaseNotes || "",
            },
            callDetector: {
                url: cdUrl,
                version: callDetectorRel.version,
                fileSize: callDetectorRel.fileSize,
                releaseNotes: callDetectorRel.releaseNotes || "",
            },
        });
    }
    catch (error) {
        logger.error("[redeemDownloadToken] 오류:", error);
        res.status(500).json({ error: "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요." });
    }
});
/**
 * registerOwner - 사장님이 앱에서 이메일+비번+초대토큰으로 가입 (H2, 인증 불필요)
 *
 * 호출 시점: call_manager SignUpScreen "가입" 버튼
 *
 * 처리 순서:
 *   1) 토큰 검증 (active / 만료 / 사용횟수)
 *   2) admin.auth().createUser({email, password})
 *   3) Firestore 트랜잭션:
 *      - admins/{uid} 생성
 *      - offices/{officeId}.ownerAuthUid = uid
 *      - office_applications/{applicationId}.ownerAuthUid = uid
 *      - downloadInvites/{token} → status=used, usesRemaining=0
 *   4) 실패 시 admin.auth().deleteUser(uid) 롤백
 */
exports.registerOwner = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    var _a, _b, _c, _d;
    // unauthenticated 허용 (가입 전이므로)
    const { token, email, password } = request.data || {};
    if (!token || typeof token !== "string") {
        throw new Error("초대 토큰이 필요합니다.");
    }
    if (!email || typeof email !== "string") {
        throw new Error("이메일이 필요합니다.");
    }
    if (!password || typeof password !== "string" || password.length < 6) {
        throw new Error("비밀번호는 6자 이상이어야 합니다.");
    }
    // 1) 초대 토큰 검증
    const inviteRef = admin.firestore().doc(`downloadInvites/${token}`);
    const inviteSnap = await inviteRef.get();
    if (!inviteSnap.exists) {
        throw new Error("유효하지 않은 초대 토큰입니다.");
    }
    const invite = inviteSnap.data();
    if (invite.status === "used" || ((_a = invite.usesRemaining) !== null && _a !== void 0 ? _a : 0) <= 0) {
        throw new Error("이미 사용된 초대 토큰입니다.");
    }
    if (invite.status !== "active") {
        throw new Error("유효하지 않은 초대 토큰입니다.");
    }
    const expiresMs = (_d = (_c = (_b = invite.expiresAt) === null || _b === void 0 ? void 0 : _b.toMillis) === null || _c === void 0 ? void 0 : _c.call(_b)) !== null && _d !== void 0 ? _d : 0;
    if (expiresMs && expiresMs < Date.now()) {
        throw new Error("만료된 초대 토큰입니다. 총관리자에게 재발급을 요청해주세요.");
    }
    const normalizedEmail = email.trim().toLowerCase();
    // 2) Auth 계정 생성
    let userRecord;
    try {
        userRecord = await admin.auth().createUser({
            email: normalizedEmail,
            password,
            emailVerified: false,
            displayName: invite.ownerName,
        });
        logger.info(`[registerOwner] Auth 계정 생성 - uid: ${userRecord.uid}, email: ${normalizedEmail}`);
    }
    catch (authError) {
        if (authError.code === "auth/email-already-exists") {
            throw new Error("이미 사용 중인 이메일입니다. 다른 이메일을 사용하거나 로그인해주세요.");
        }
        if (authError.code === "auth/invalid-email") {
            throw new Error("유효하지 않은 이메일 형식입니다.");
        }
        if (authError.code === "auth/weak-password") {
            throw new Error("비밀번호가 너무 약합니다 (6자 이상).");
        }
        logger.error("[registerOwner] Auth 계정 생성 오류:", authError);
        throw new Error("계정 생성 중 오류가 발생했습니다.");
    }
    const uid = userRecord.uid;
    const db = admin.firestore();
    const adminsRef = db.doc(`admins/${uid}`);
    const officeRef = db.doc(`provinces/${invite.provinceId}/cities/${invite.cityId}/offices/${invite.officeId}`);
    const appRef = invite.applicationId ? db.doc(`office_applications/${invite.applicationId}`) : null;
    // 3) Firestore 트랜잭션 — 실패 시 Auth 계정 롤백
    try {
        await db.runTransaction(async (tx) => {
            var _a, _b;
            // ===== READ 단계 =====
            const freshInvite = await tx.get(inviteRef);
            if (!freshInvite.exists) {
                throw new Error("초대 토큰이 사라졌습니다.");
            }
            const fi = freshInvite.data();
            if (fi.status !== "active" || ((_a = fi.usesRemaining) !== null && _a !== void 0 ? _a : 0) <= 0) {
                throw new Error("이미 사용된 초대 토큰입니다.");
            }
            // wallet 초기화 read (P0 plan §3.3, 결정 #19) — 가입 보너스 30,000 멱등성 체크
            const pointsRef = officeRef.collection("points").doc("points");
            const walletTxRef = officeRef.collection("point_transactions").doc(`signup_bonus_${invite.officeId}`);
            const existingWalletTx = await tx.get(walletTxRef);
            let walletBeforeBalance = 0;
            const needWalletInit = !existingWalletTx.exists;
            if (needWalletInit) {
                const pointsSnap = await tx.get(pointsRef);
                walletBeforeBalance = ((_b = pointsSnap.data()) === null || _b === void 0 ? void 0 : _b.balance) || 0;
            }
            // ===== WRITE 단계 =====
            tx.set(adminsRef, {
                email: normalizedEmail,
                name: invite.ownerName,
                phoneNumber: invite.phone,
                role: "OFFICE_OWNER",
                associatedProvinceId: invite.provinceId,
                associatedCityId: invite.cityId,
                associatedOfficeId: invite.officeId,
                applicationId: invite.applicationId || null,
                apkDownloadEnabled: true,
                createdAt: firestore_2.FieldValue.serverTimestamp(),
                updatedAt: firestore_2.FieldValue.serverTimestamp(),
            });
            tx.update(officeRef, { ownerAuthUid: uid });
            if (appRef) {
                tx.update(appRef, { ownerAuthUid: uid });
            }
            tx.update(inviteRef, {
                status: "used",
                usedAt: firestore_2.FieldValue.serverTimestamp(),
                usedBy: uid,
                usesRemaining: 0,
            });
            // wallet 가입 보너스 (멱등)
            if (needWalletInit) {
                const after = walletBeforeBalance + 30000;
                tx.set(pointsRef, {
                    balance: after,
                    updatedAt: firestore_2.FieldValue.serverTimestamp(),
                }, { merge: true });
                tx.set(walletTxRef, {
                    type: "SIGNUP_BONUS",
                    amount: 30000,
                    balanceAfter: after,
                    description: "사무실 가입 보너스",
                    status: "COMPLETED",
                    timestamp: firestore_2.FieldValue.serverTimestamp(),
                    createdBy: "system",
                });
            }
        });
    }
    catch (txError) {
        // 롤백: Auth 계정 삭제
        logger.error("[registerOwner] 트랜잭션 실패, Auth 계정 롤백:", txError);
        try {
            await admin.auth().deleteUser(uid);
            logger.info(`[registerOwner] Auth 계정 롤백 완료 - uid: ${uid}`);
        }
        catch (delErr) {
            logger.error(`[registerOwner] Auth 롤백 실패 - uid: ${uid}`, delErr);
        }
        throw new Error(txError.message || "가입 처리 중 오류가 발생했습니다.");
    }
    logger.info(`[registerOwner] 가입 완료 - uid: ${uid}, officeId: ${invite.officeId}`);
    // 채팅방 멤버 자동 등록 (실패해도 가입 자체는 성공 처리 — best-effort)
    try {
        await (0, chat_2.addChatMember)(invite.provinceId, invite.cityId, invite.officeId, uid, "MANAGER");
    }
    catch (chatErr) {
        logger.error(`[registerOwner] chat member 등록 실패 (무시, backfill로 보강 가능)`, chatErr);
    }
    return {
        success: true,
        uid,
        officeId: invite.officeId,
        officePath: `provinces/${invite.provinceId}/cities/${invite.cityId}/offices/${invite.officeId}`,
    };
});
/**
 * rejectOfficeApplication - 총관리자가 신청을 거부
 */
exports.rejectOfficeApplication = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    var _a, _b, _c;
    if (!request.auth) {
        throw new Error("인증이 필요합니다.");
    }
    const callerDoc = await admin.firestore().doc(`admins/${request.auth.uid}`).get();
    if (!callerDoc.exists || !["HEAD_MANAGER", "SUPER_ADMIN"].includes((_a = callerDoc.data()) === null || _a === void 0 ? void 0 : _a.role)) {
        throw new Error("총관리자 권한이 필요합니다.");
    }
    const { applicationId, reason } = request.data;
    if (!applicationId) {
        throw new Error("applicationId가 필요합니다.");
    }
    const appRef = admin.firestore().doc(`office_applications/${applicationId}`);
    const appDoc = await appRef.get();
    if (!appDoc.exists) {
        throw new Error("신청서를 찾을 수 없습니다.");
    }
    if (((_b = appDoc.data()) === null || _b === void 0 ? void 0 : _b.status) !== "pending") {
        throw new Error(`이미 처리된 신청입니다 (상태: ${(_c = appDoc.data()) === null || _c === void 0 ? void 0 : _c.status})`);
    }
    await appRef.update({
        status: "rejected",
        rejectedReason: reason || "",
        reviewedAt: firestore_2.FieldValue.serverTimestamp(),
        reviewedBy: request.auth.uid,
    });
    logger.info(`[rejectOfficeApplication] 거부 완료 - applicationId: ${applicationId}`);
    return { success: true };
});
/**
 * getApkDownloadUrl - 승인된 사장님이 APK 다운로드 URL을 요청
 */
exports.getApkDownloadUrl = (0, https_1.onCall)({ region: "asia-northeast3" }, async (request) => {
    if (!request.auth) {
        throw new Error("인증이 필요합니다.");
    }
    // OFFICE_OWNER 또는 HEAD_MANAGER 권한 확인
    const callerDoc = await admin.firestore().doc(`admins/${request.auth.uid}`).get();
    if (!callerDoc.exists) {
        throw new Error("권한이 없습니다.");
    }
    const callerData = callerDoc.data();
    if (!["OFFICE_OWNER", "HEAD_MANAGER", "SUPER_ADMIN"].includes(callerData.role)) {
        throw new Error("APK 다운로드 권한이 없습니다.");
    }
    // OFFICE_OWNER인 경우 다운로드 권한 확인
    if (callerData.role === "OFFICE_OWNER" && !callerData.apkDownloadEnabled) {
        throw new Error("APK 다운로드가 비활성화되어 있습니다. 관리자에게 문의하세요.");
    }
    const { appName } = request.data;
    if (!appName || !["call_detector", "call_manager"].includes(appName)) {
        throw new Error("유효한 앱 이름이 필요합니다 (call_detector 또는 call_manager)");
    }
    // 최신 릴리즈 조회
    const releaseQuery = await admin.firestore()
        .collection("apk_releases")
        .where("appName", "==", appName)
        .where("isLatest", "==", true)
        .limit(1)
        .get();
    if (releaseQuery.empty) {
        throw new Error(`${appName}의 릴리즈를 찾을 수 없습니다.`);
    }
    const releaseData = releaseQuery.docs[0].data();
    const bucket = admin.storage().bucket();
    const file = bucket.file(releaseData.storagePath);
    // 1시간 유효한 signed URL 생성
    const [url] = await file.getSignedUrl({
        action: "read",
        expires: Date.now() + 60 * 60 * 1000,
    });
    logger.info(`[getApkDownloadUrl] URL 생성 - app: ${appName}, user: ${request.auth.uid}`);
    return {
        success: true,
        downloadUrl: url,
        version: releaseData.version,
        releaseNotes: releaseData.releaseNotes,
        fileSize: releaseData.fileSize,
    };
});
// ─────────────────────────────────────────────────────────────
// calllink.io.kr 임시 Basic Auth 게이트 (homepage/public 서빙)
// ─────────────────────────────────────────────────────────────
const homepageApp = (0, express_1.default)();
homepageApp.use((req, res, next) => {
    (0, express_basic_auth_1.default)({
        users: { [HOMEPAGE_USER.value()]: HOMEPAGE_PASS.value() },
        challenge: true,
        realm: "calllink",
    })(req, res, next);
});
homepageApp.use(express_1.default.static(nodePath.join(__dirname, "public"), {
    extensions: ["html"],
    setHeaders: (res) => res.setHeader("Cache-Control", "no-cache"),
}));
exports.homepageGate = (0, https_1.onRequest)({
    region: "asia-northeast3",
    memory: "256MiB",
    secrets: [HOMEPAGE_USER, HOMEPAGE_PASS],
}, homepageApp);
//# sourceMappingURL=index.js.map