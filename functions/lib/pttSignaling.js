"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.onDesignatedDriverStatusChange = exports.onPickupDriverStatusChange = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const logger = __importStar(require("firebase-functions/logger"));
/**
 * PTT 자동 채널 참여를 위한 Firestore 트리거 함수
 *
 * 픽업 기사가 온라인 상태로 변경될 때, 같은 사무실의
 * - 콜매니저 사용자들 (admins 컬렉션)
 * - 다른 픽업앱 사용자들 (pickup_drivers 컬렉션)
 * 모두에게 FCM을 통해 PTT 채널 참여를 알림
 */
exports.onPickupDriverStatusChange = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/pickup_drivers/{driverId}"
}, async (event) => {
    var _a, _b, _c, _d;
    const beforeData = (_b = (_a = event.data) === null || _a === void 0 ? void 0 : _a.before) === null || _b === void 0 ? void 0 : _b.data();
    const afterData = (_d = (_c = event.data) === null || _c === void 0 ? void 0 : _c.after) === null || _d === void 0 ? void 0 : _d.data();
    const { regionId, officeId, driverId } = event.params;
    // status가 OFFLINE에서 ONLINE으로 바뀐 경우에만 실행
    const wasOffline = (beforeData === null || beforeData === void 0 ? void 0 : beforeData.status) === "OFFLINE";
    const isNowOnline = (afterData === null || afterData === void 0 ? void 0 : afterData.status) === "ONLINE";
    if (!wasOffline || !isNowOnline) {
        logger.info(`Pickup driver ${driverId} status did not change from offline to online. Skipping.`);
        return null;
    }
    logger.info(`🚗 Pickup driver ${driverId} came online in ${regionId}/${officeId}. Notifying PTT users.`);
    // PTT 채널명 생성
    const channelName = `${regionId}_${officeId}_ptt`;
    const onlineDriverName = afterData.name || "픽업 기사";
    try {
        const db = admin.firestore();
        const fcmTokens = [];
        // 1. 같은 사무실의 콜매니저 사용자들 (admins) 조회
        const adminsSnapshot = await db.collection("admins")
            .where("associatedRegionId", "==", regionId)
            .where("associatedOfficeId", "==", officeId)
            .get();
        adminsSnapshot.forEach(doc => {
            const adminData = doc.data();
            if (adminData.fcmToken) {
                fcmTokens.push(adminData.fcmToken);
                logger.info(`Added admin FCM token: ${doc.id}`);
            }
        });
        // 2. 같은 사무실의 다른 픽업 기사들 (pickup_drivers) 조회
        const pickupDriversSnapshot = await db
            .collection("regions").doc(regionId)
            .collection("offices").doc(officeId)
            .collection("pickup_drivers")
            .where("status", "==", "ONLINE")
            .get();
        pickupDriversSnapshot.forEach(doc => {
            // 자기 자신에게는 보내지 않음
            if (doc.id !== driverId) {
                const driverData = doc.data();
                if (driverData.fcmToken) {
                    fcmTokens.push(driverData.fcmToken);
                    logger.info(`Added pickup driver FCM token: ${doc.id}`);
                }
            }
        });
        if (fcmTokens.length === 0) {
            logger.warn("No FCM tokens found for PTT notification.");
            return null;
        }
        // FCM 데이터 메시지 페이로드 구성
        const payload = {
            data: {
                type: "PTT_AUTO_JOIN",
                channel: channelName,
                regionId: regionId,
                officeId: officeId,
                newDriverName: onlineDriverName,
                message: `${onlineDriverName}님이 PTT 채널에 참여했습니다`
            },
            android: {
                priority: "high",
            },
            tokens: fcmTokens
        };
        logger.info(`Sending PTT auto-join FCM to ${fcmTokens.length} users for channel ${channelName}`);
        // FCM 메시지 발송
        const response = await admin.messaging().sendEachForMulticast(payload);
        logger.info(`PTT FCM sent successfully. Success: ${response.successCount}, Failed: ${response.failureCount}`);
        // 실패한 토큰들 로그
        response.responses.forEach((resp, idx) => {
            var _a;
            if (!resp.success) {
                logger.warn(`FCM token ${idx} failed: ${(_a = resp.error) === null || _a === void 0 ? void 0 : _a.message}`);
            }
        });
    }
    catch (error) {
        logger.error("Error in onPickupDriverStatusChange:", error);
    }
    return null;
});
/**
 * 일반 대리기사가 온라인 상태로 변경될 때도 동일하게 처리
 */
exports.onDesignatedDriverStatusChange = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/designated_drivers/{driverId}"
}, async (event) => {
    var _a, _b, _c, _d;
    const beforeData = (_b = (_a = event.data) === null || _a === void 0 ? void 0 : _a.before) === null || _b === void 0 ? void 0 : _b.data();
    const afterData = (_d = (_c = event.data) === null || _c === void 0 ? void 0 : _c.after) === null || _d === void 0 ? void 0 : _d.data();
    const { regionId, officeId, driverId } = event.params;
    // status가 OFFLINE에서 ONLINE으로 바뀐 경우에만 실행
    const wasOffline = (beforeData === null || beforeData === void 0 ? void 0 : beforeData.status) === "OFFLINE";
    const isNowOnline = (afterData === null || afterData === void 0 ? void 0 : afterData.status) === "ONLINE";
    if (!wasOffline || !isNowOnline) {
        logger.info(`Designated driver ${driverId} status did not change from offline to online. Skipping.`);
        return null;
    }
    logger.info(`🚕 Designated driver ${driverId} came online in ${regionId}/${officeId}. Notifying PTT users.`);
    // PTT 채널명 생성
    const channelName = `${regionId}_${officeId}_ptt`;
    const onlineDriverName = afterData.name || "대리기사";
    try {
        const db = admin.firestore();
        const fcmTokens = [];
        // 1. 같은 사무실의 콜매니저 사용자들 조회
        const adminsSnapshot = await db.collection("admins")
            .where("associatedRegionId", "==", regionId)
            .where("associatedOfficeId", "==", officeId)
            .get();
        adminsSnapshot.forEach(doc => {
            const adminData = doc.data();
            if (adminData.fcmToken) {
                fcmTokens.push(adminData.fcmToken);
            }
        });
        // 2. 같은 사무실의 픽업 기사들 조회
        const pickupDriversSnapshot = await db
            .collection("regions").doc(regionId)
            .collection("offices").doc(officeId)
            .collection("pickup_drivers")
            .where("status", "==", "ONLINE")
            .get();
        pickupDriversSnapshot.forEach(doc => {
            const driverData = doc.data();
            if (driverData.fcmToken) {
                fcmTokens.push(driverData.fcmToken);
            }
        });
        if (fcmTokens.length === 0) {
            logger.warn("No FCM tokens found for PTT notification.");
            return null;
        }
        // FCM 데이터 메시지 페이로드 구성
        const payload = {
            data: {
                type: "PTT_AUTO_JOIN",
                channel: channelName,
                regionId: regionId,
                officeId: officeId,
                newDriverName: onlineDriverName,
                message: `${onlineDriverName}님이 PTT 채널에 참여했습니다`
            },
            android: {
                priority: "high",
            },
            tokens: fcmTokens
        };
        logger.info(`Sending PTT auto-join FCM to ${fcmTokens.length} users for channel ${channelName}`);
        // FCM 메시지 발송
        const response = await admin.messaging().sendEachForMulticast(payload);
        logger.info(`PTT FCM sent successfully. Success: ${response.successCount}, Failed: ${response.failureCount}`);
    }
    catch (error) {
        logger.error("Error in onDesignatedDriverStatusChange:", error);
    }
    return null;
});
//# sourceMappingURL=pttSignaling.js.map