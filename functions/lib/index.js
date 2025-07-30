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
Object.defineProperty(exports, "__esModule", { value: true });
exports.finalizeWorkDay = exports.onSharedCallCompleted = exports.sendNewCallNotification = exports.onSharedCallClaimed = exports.onSharedCallCreated = exports.oncallassigned = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const logger = __importStar(require("firebase-functions/logger"));
// Firebase Admin SDK 초기화
admin.initializeApp();
const DRIVER_COLLECTION_NAME = "designated_drivers";
exports.oncallassigned = (0, firestore_1.onDocumentWritten)({
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    var _a, _b;
    const { regionId, officeId, callId } = event.params;
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
    const beforeData = (_a = event.data.before) === null || _a === void 0 ? void 0 : _a.data();
    // 2. assignedDriverId가 유효하게 할당/변경되었는지 확인
    const isDriverAssigned = afterData.assignedDriverId &&
        afterData.assignedDriverId !== (beforeData === null || beforeData === void 0 ? void 0 : beforeData.assignedDriverId);
    if (!isDriverAssigned || !afterData.assignedDriverId) {
        logger.info(`[${callId}] 기사 배정 변경사항이 없어 알림을 보내지 않습니다.`);
        return;
    }
    const driverId = afterData.assignedDriverId;
    logger.info(`[${callId}] 기사 [${driverId}]에게 알림 전송 시작.`);
    try {
        // 3. 기사 문서에서 FCM 토큰 가져오기
        const driverRef = admin.firestore()
            .collection("regions").doc(regionId)
            .collection("offices").doc(officeId)
            .collection(DRIVER_COLLECTION_NAME).doc(driverId);
        const driverDoc = await driverRef.get();
        if (!driverDoc.exists) {
            logger.error(`[${callId}] 기사 문서 [${driverId}]를 찾을 수 없습니다.`);
            return;
        }
        const fcmToken = (_b = driverDoc.data()) === null || _b === void 0 ? void 0 : _b.fcmToken;
        if (!fcmToken) {
            logger.warn(`[${callId}] 기사 [${driverId}]의 FCM 토큰이 없습니다.`);
            return;
        }
        // 4. 알림 페이로드 구성 및 전송
        const payload = {
            data: {
                title: "새로운 콜 배정",
                body: "새로운 콜이 배정되었습니다. 앱을 확인해주세요.",
                callId: callId,
                type: "NEW_CALL_ASSIGNED"
            },
            token: fcmToken,
        };
        await admin.messaging().send(payload);
        logger.info(`[${callId}] 기사 [${driverId}]에게 성공적으로 알림을 보냈습니다.`);
    }
    catch (error) {
        logger.error(`[${callId}] 알림 전송 중 오류 발생:`, error);
    }
});
// =============================
// 새로운 공유 콜이 생성될 때 트리거
// 대상 지역의 모든 사무실 관리자에게 FCM 알림 전송
// =============================
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
    try {
        // 대상 지역의 모든 관리자 FCM 토큰 조회 (원본 사무실 제외)
        const adminQuery = await admin
            .firestore()
            .collection("admins")
            .where("associatedRegionId", "==", sharedCallData.targetRegionId)
            .get();
        const tokens = [];
        adminQuery.docs.forEach((doc) => {
            const adminData = doc.data();
            // 원본 사무실은 제외
            if (adminData.associatedOfficeId !== sharedCallData.sourceOfficeId && adminData.fcmToken) {
                tokens.push(adminData.fcmToken);
            }
        });
        logger.info(`[shared-created:${callId}] 알림 대상: ${tokens.length}명의 관리자`);
        if (tokens.length === 0) {
            logger.warn(`[shared-created:${callId}] 알림을 보낼 관리자 토큰이 없습니다.`);
            return;
        }
        // FCM 알림 전송
        const message = {
            notification: {
                title: "새로운 공유콜이 도착했습니다!",
                body: `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"} / ${sharedCallData.fare || 0}원`,
            },
            data: {
                type: "NEW_SHARED_CALL",
                sharedCallId: callId,
                departure: sharedCallData.departure || "",
                destination: sharedCallData.destination || "",
                fare: (sharedCallData.fare || 0).toString(),
            },
            android: {
                priority: "high",
                notification: {
                    sound: "default",
                    channelId: "shared_call_fcm_channel",
                    priority: "high",
                },
            },
            tokens,
        };
        const response = await admin.messaging().sendEachForMulticast(message);
        logger.info(`[shared-created:${callId}] FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);
        // 실패한 토큰들 로그
        response.responses.forEach((resp, idx) => {
            var _a;
            if (!resp.success) {
                logger.warn(`[shared-created:${callId}] 토큰 ${idx} 전송 실패: ${(_a = resp.error) === null || _a === void 0 ? void 0 : _a.message}`);
            }
        });
    }
    catch (error) {
        logger.error(`[shared-created:${callId}] 알림 전송 중 오류 발생:`, error);
    }
});
exports.onSharedCallClaimed = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
}, async (event) => {
    var _a, _b, _c;
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
    // OPEN -> CLAIMED 인지 확인 (콜 복사만, 포인트 처리 없음)
    if (beforeData.status === "OPEN" && afterData.status === "CLAIMED") {
        logger.info(`[shared:${callId}] 공유 콜이 CLAIMED 되었습니다. 대상사무실로 복사 시작.`);
        logger.info(`[shared:${callId}] afterData.claimedDriverId=${afterData.claimedDriverId}`);
        logger.info(`[shared:${callId}] assignedDriverId=${afterData.claimedDriverId}`);
        try {
            await admin.firestore().runTransaction(async (tx) => {
                var _a, _b, _c;
                // 대상 사무실 calls 컬렉션 레퍼런스
                const destCallsRef = admin
                    .firestore()
                    .collection("regions")
                    .doc(afterData.targetRegionId)
                    .collection("offices")
                    .doc(afterData.claimedOfficeId)
                    .collection("calls")
                    .doc(callId);
                // 기사 정보 읽기 (배정된 기사가 있을 경우)
                const driverSnap = afterData.claimedDriverId ? await tx.get(admin.firestore()
                    .collection("regions").doc(afterData.targetRegionId)
                    .collection("offices").doc(afterData.claimedOfficeId)
                    .collection("designated_drivers").doc(afterData.claimedDriverId)) : null;
                const driverData = driverSnap ? driverSnap.data() : undefined;
                const assignedDriverId = driverData ? driverData.authUid : null; // authUid 사용
                const assignedDriverName = driverData ? driverData.name : null;
                const assignedDriverPhone = driverData ? driverData.phoneNumber : null;
                logger.info(`[shared:${callId}] driverDocId=${afterData.claimedDriverId}, assignedDriverId(authUid)=${assignedDriverId}`);
                // 2) WRITE ----------------------------------------
                const callDoc = Object.assign(Object.assign({}, afterData), { status: assignedDriverId ? "ASSIGNED" : "WAITING", departure_set: (_a = afterData.departure) !== null && _a !== void 0 ? _a : null, destination_set: (_b = afterData.destination) !== null && _b !== void 0 ? _b : null, fare_set: (_c = afterData.fare) !== null && _c !== void 0 ? _c : null, callType: "SHARED", sourceSharedCallId: callId, createdAt: admin.firestore.FieldValue.serverTimestamp() });
                if (assignedDriverId) {
                    callDoc.assignedDriverId = assignedDriverId;
                    if (assignedDriverName)
                        callDoc.assignedDriverName = assignedDriverName;
                    if (assignedDriverPhone)
                        callDoc.assignedDriverPhone = assignedDriverPhone;
                }
                tx.set(destCallsRef, callDoc);
                // 드라이버 상태 ASSIGNED 로 업데이트 (옵션)
                if (assignedDriverId && (driverSnap === null || driverSnap === void 0 ? void 0 : driverSnap.exists)) {
                    tx.update(driverSnap.ref, { status: "배차중" });
                }
                //   c) 공유콜 문서 processed 플래그 수정 → 트랜잭션 외부로 이동하여
                //      "읽기 후 쓰기" 제약을 피함 (트랜잭션 내부에 포함하면
                //      사전에 해당 문서를 읽지 않았기 때문에 Firestore가
                //      암묵적 read 를 삽입하며 오류가 발생한다)
            });
            logger.info(`[shared:${callId}] 콜 복사 및 포인트 처리 완료.`);
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
                    .where("associatedRegionId", "==", afterData.sourceRegionId)
                    .where("associatedOfficeId", "==", afterData.sourceOfficeId)
                    .get();
                // 수락 사무실 관리자 토큰
                const tgtSnap = await adminColl
                    .where("associatedRegionId", "==", afterData.targetRegionId)
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
                    const msg = {
                        notification: {
                            title: "공유 콜 수락됨",
                            body: `${(_a = afterData.departure) !== null && _a !== void 0 ? _a : "출발"} → ${(_b = afterData.destination) !== null && _b !== void 0 ? _b : "도착"} / 요금 ${(_c = afterData.fare) !== null && _c !== void 0 ? _c : 0}원`,
                        },
                        data: {
                            sharedCallId: callId,
                            type: "SHARED_CALL_CLAIMED",
                        },
                        tokens,
                    };
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
        }
        catch (err) {
            logger.error(`[shared:${callId}] 트랜잭션 오류`, err);
        }
    }
});
// 신규 콜이 생성될 때 (status == WAITING && assignedDriverId == null)
exports.sendNewCallNotification = (0, firestore_1.onDocumentCreated)({
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}",
}, async (event) => {
    var _a;
    logger.info(`[sendNewCallNotification:${event.params.callId}] START - New call received.`);
    const data = (_a = event.data) === null || _a === void 0 ? void 0 : _a.data();
    if (!data) {
        logger.warn("[sendNewCallNotification] No data in document.");
        return;
    }
    if (data.status !== "WAITING") {
        logger.info("[sendNewCallNotification] Call is not in WAITING status. Skip.");
        return;
    }
    // 1) 관리자 FCM 토큰 조회
    const adminQuery = await admin
        .firestore()
        .collection("admins")
        .where("associatedRegionId", "==", event.params.regionId)
        .where("associatedOfficeId", "==", event.params.officeId)
        .get();
    const tokens = adminQuery.docs
        .map((d) => d.data().fcmToken)
        .filter((t) => !!t && t.length > 0);
    logger.info(`[getAdminTokens] SUCCESS: Found ${adminQuery.size} admins, ${tokens.length} valid tokens.`);
    if (tokens.length === 0) {
        logger.warn("[sendNewCallNotification] No valid admin FCM tokens, abort.");
        return;
    }
    // 2) 알림 + 데이터 메시지 전송
    await admin.messaging().sendEachForMulticast({
        notification: {
            title: "새로운 콜이 접수되었습니다.",
        },
        data: {
            type: "NEW_CALL_WAITING",
            callId: event.params.callId,
            customerPhone: data.phoneNumber || "",
        },
        android: {
            priority: "high",
            notification: {
                sound: "default",
                channelId: "new_call_fcm_channel_v2",
            },
        },
        tokens,
    });
    logger.info("[sendNewCallNotification] sendEachForMulticast with notification sent.");
});
// =============================
// 공유 콜에서 복사된 일반 콜이 COMPLETED 될 때 트리거
// 1) 원본 shared_calls 문서를 COMPLETED로 업데이트
// 2) 포인트 가감 처리 (10% 수수료)
// =============================
exports.onSharedCallCompleted = (0, firestore_1.onDocumentUpdated)({
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    const { regionId, officeId, callId } = event.params;
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
        const pointRatio = 0.1; // 10%
        const pointAmount = Math.round(fare * pointRatio);
        try {
            await admin.firestore().runTransaction(async (tx) => {
                var _a, _b;
                // 원본 shared_calls 문서 레퍼런스
                const sharedCallRef = admin.firestore().collection("shared_calls").doc(sourceSharedCallId);
                const sharedCallSnap = await tx.get(sharedCallRef);
                if (!sharedCallSnap.exists) {
                    logger.error(`[call-completed:${callId}] 원본 shared_calls 문서를 찾을 수 없습니다: ${sourceSharedCallId}`);
                    return;
                }
                const sharedCallData = sharedCallSnap.data();
                // 포인트 레퍼런스
                const sourcePointsRef = admin
                    .firestore()
                    .collection("regions")
                    .doc(sharedCallData.sourceRegionId)
                    .collection("offices")
                    .doc(sharedCallData.sourceOfficeId)
                    .collection("points")
                    .doc("points");
                const targetPointsRef = admin
                    .firestore()
                    .collection("regions")
                    .doc(regionId)
                    .collection("offices")
                    .doc(officeId)
                    .collection("points")
                    .doc("points");
                // 포인트 잔액 읽기
                const [sourceSnap, targetSnap] = await Promise.all([
                    tx.get(sourcePointsRef),
                    tx.get(targetPointsRef)
                ]);
                const sourceBalance = (((_a = sourceSnap.data()) === null || _a === void 0 ? void 0 : _a.balance) || 0) + pointAmount;
                const targetBalance = (((_b = targetSnap.data()) === null || _b === void 0 ? void 0 : _b.balance) || 0) - pointAmount;
                // 1) shared_calls 문서를 COMPLETED로 업데이트
                tx.update(sharedCallRef, {
                    status: "COMPLETED",
                    completedAt: admin.firestore.FieldValue.serverTimestamp(),
                    destCallId: callId
                });
                // 2) 포인트 문서 업데이트
                tx.set(sourcePointsRef, {
                    balance: sourceBalance,
                    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                }, { merge: true });
                tx.set(targetPointsRef, {
                    balance: targetBalance,
                    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                }, { merge: true });
                logger.info(`[call-completed:${callId}] 포인트 처리 완료. Source: +${pointAmount}, Target: -${pointAmount}`);
            });
            logger.info(`[call-completed:${callId}] 공유콜 완료 처리 성공. SharedCallId: ${sourceSharedCallId}`);
        }
        catch (error) {
            logger.error(`[call-completed:${callId}] 공유콜 완료 처리 오류:`, error);
        }
    }
});
var finalizeWorkDay_1 = require("./finalizeWorkDay");
Object.defineProperty(exports, "finalizeWorkDay", { enumerable: true, get: function () { return finalizeWorkDay_1.finalizeWorkDay; } });
const _forceDeploy = Date.now(); // 배포 강제용 더미 변수
void _forceDeploy; // 사용해서 컴파일 경고 해소
//# sourceMappingURL=index.js.map