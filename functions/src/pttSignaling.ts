import {onDocumentUpdated} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

/**
 * PTT 자동 채널 참여를 위한 Firestore 트리거 함수
 * 
 * 픽업 기사가 온라인 상태로 변경될 때, 같은 사무실의
 * - 콜매니저 사용자들 (admins 컬렉션)  
 * - 다른 픽업앱 사용자들 (pickup_drivers 컬렉션)
 * 모두에게 FCM을 통해 PTT 채널 참여를 알림
 */
export const onPickupDriverStatusChange = onDocumentUpdated(
    {
        region: "asia-northeast3",
        document: "regions/{regionId}/offices/{officeId}/pickup_drivers/{driverId}"
    },
    async (event: any) => {
        const beforeData = event.data?.before?.data();
        const afterData = event.data?.after?.data();
        
        const { regionId, officeId, driverId } = event.params;

        // status가 OFFLINE에서 ONLINE으로 바뀐 경우에만 실행
        const wasOffline = beforeData?.status === "OFFLINE";
        const isNowOnline = afterData?.status === "ONLINE";

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
            const fcmTokens: string[] = [];

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
            const payload: admin.messaging.MulticastMessage = {
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
                if (!resp.success) {
                    logger.warn(`FCM token ${idx} failed: ${resp.error?.message}`);
                }
            });

        } catch (error) {
            logger.error("Error in onPickupDriverStatusChange:", error);
        }

        return null;
    }
);

/**
 * 일반 대리기사가 온라인 상태로 변경될 때도 동일하게 처리
 */
export const onDesignatedDriverStatusChange = onDocumentUpdated(
    {
        region: "asia-northeast3",
        document: "regions/{regionId}/offices/{officeId}/designated_drivers/{driverId}"
    },
    async (event: any) => {
        const beforeData = event.data?.before?.data();
        const afterData = event.data?.after?.data();
        
        const { regionId, officeId, driverId } = event.params;

        // status가 OFFLINE에서 ONLINE으로 바뀐 경우에만 실행
        const wasOffline = beforeData?.status === "OFFLINE";
        const isNowOnline = afterData?.status === "ONLINE";

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
            const fcmTokens: string[] = [];

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
            const payload: admin.messaging.MulticastMessage = {
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

        } catch (error) {
            logger.error("Error in onDesignatedDriverStatusChange:", error);
        }

        return null;
    }
);