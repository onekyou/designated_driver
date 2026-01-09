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
exports.onDriverStatusChange = exports.getOfficeReport = exports.searchArchivedCalls = exports.getArchivedStats = exports.archiveOldCalls = exports.scheduledDataCleanup = exports.onCallDetectorCrash = exports.onCustomerCountChange = exports.onDriverCountChange = exports.onNewCustomerRegistered = exports.onCallCancelledByDriver = exports.claimToken = exports.matchByToken = exports.saveManualAttribution = exports.matchAttribution = exports.testFcmMessage = exports.migratePickupDrivers = exports.onDesignatedDriverStatusChange = exports.onPickupDriverStatusChange = exports.refreshAgoraToken = exports.generateAgoraToken = exports.finalizeWorkDay = exports.onSharedCallCompleted = exports.onSharedCallStatusSync = exports.onDriverSignupRequest = exports.onCallStatusChanged = exports.notifyCustomerOnComplete = exports.onSharedCallCancelledByDriver = exports.onSharedCallClaimed = exports.notifyCustomerOnOfficeClosed = exports.onSharedCallCreated = exports.sendNewCallNotification = exports.oncallassigned = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const https_1 = require("firebase-functions/v2/https");
const scheduler_1 = require("firebase-functions/v2/scheduler");
const admin = __importStar(require("firebase-admin"));
const logger = __importStar(require("firebase-functions/logger"));
const points_1 = require("./handlers/points");
// Firebase Admin SDK 초기화
admin.initializeApp();
const DRIVER_COLLECTION_NAME = "designated_drivers";
exports.oncallassigned = (0, firestore_1.onDocumentWritten)({
    region: "asia-northeast3",
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
}, async (event) => {
    var _a, _b, _c;
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
        // 4. 기사에게 알림 전송
        if (driverFcmToken) {
            const driverPayload = {
                data: {
                    callId: callId,
                    type: "call_assigned",
                    title: "🚨 새로운 콜 배정",
                    body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!"
                },
                android: {
                    priority: "high",
                    ttl: 30000, // 30초 TTL
                },
                token: driverFcmToken,
            };
            await admin.messaging().send(driverPayload);
            logger.info(`[${callId}] 기사 [${driverId}]에게 성공적으로 알림을 보냈습니다.`);
        }
        else {
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
                const customerFcmToken = (_b = customerDoc.data()) === null || _b === void 0 ? void 0 : _b.fcmToken;
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
                        priority: "high",
                        ttl: 60000
                    },
                    token: customerFcmToken
                };
                await admin.messaging().send(customerPayload);
                logger.info(`[${callId}] 고객에게 기사 배정 알림 전송 완료: ${customerPhone}`);
            }
            catch (customerError) {
                logger.error(`[${callId}] 고객 알림 전송 오류:`, customerError);
            }
        }
        else {
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
                            fare: ((_c = afterData.fare) !== null && _c !== void 0 ? _c : 0).toString(),
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
    const { provinceId, cityId, officeId, callId } = event.params;
    if (!event.data) {
        logger.warn(`[new-call:${callId}] 이벤트 데이터가 없습니다.`);
        return;
    }
    const callData = event.data.data();
    // fromCallDetector가 true인 경우 알림을 보내지 않음 (중복 방지)
    if (callData.fromCallDetector === true) {
        logger.info(`[new-call:${callId}] Call Detector에서 생성한 콜 - FCM 알림 스킵`);
        return;
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
        const tokens = [];
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
        const message = {
            data: {
                type: "NEW_CALL",
                callId: callId,
                customerName: callData.customerName || callData.phoneNumber || "신규 고객",
                customerPhone: callData.phoneNumber || "",
                pickupLocation: callData.customerAddress || callData.departure || "위치 미확인",
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
                            batch.update(doc.ref, { fcmToken: admin.firestore.FieldValue.delete() });
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
        // Data-only FCM 메시지 - 앱에서 커스텀 알림 생성
        const message = {
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
                            batch.update(doc.ref, { fcmToken: admin.firestore.FieldValue.delete() });
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
        const message = {
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
                    .update({ fcmToken: admin.firestore.FieldValue.delete() });
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
                    updatedAt: admin.firestore.FieldValue.serverTimestamp()
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
                const message = {
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
                const callDoc = Object.assign(Object.assign(Object.assign({}, afterData), { status: assignedDriverId ? "ASSIGNED" : "WAITING", departure_set: (_a = afterData.departure) !== null && _a !== void 0 ? _a : null, destination_set: (_b = afterData.destination) !== null && _b !== void 0 ? _b : null, fare_set: (_c = afterData.fare) !== null && _c !== void 0 ? _c : null, callType: "SHARED", sourceSharedCallId: callId, createdAt: admin.firestore.FieldValue.serverTimestamp() }), (assignedDriverId && {
                    assignedDriverId: assignedDriverId,
                    assignedDriverName: assignedDriverName,
                    assignedDriverPhone: assignedDriverPhone,
                    assignedTimestamp: admin.firestore.FieldValue.serverTimestamp(),
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
                        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
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
                    await driverSnap.ref.update({ status: "배차중" });
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
                    const msg = {
                        // notification 필드 완전 제거 - Android가 자동 알림 생성하지 않도록
                        data: {
                            sharedCallId: callId,
                            type: "SHARED_CALL_CLAIMED",
                            // 알림 제목과 내용을 data로 전송
                            alertTitle: "공유 콜 수락됨",
                            alertMessage: `${(_a = afterData.departure) !== null && _a !== void 0 ? _a : "출발"} → ${(_b = afterData.destination) !== null && _b !== void 0 ? _b : "도착"} / 요금 ${(_c = afterData.fare) !== null && _c !== void 0 ? _c : 0}원`,
                        },
                        android: {
                            priority: "high",
                            // notification 필드 완전 제거
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
                    // HOLD 상태인 경우에만 삭제 (이미 진행 중인 콜은 건드리지 않음)
                    if ((copiedCallData === null || copiedCallData === void 0 ? void 0 : copiedCallData.status) === "HOLD") {
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
                logger.error(`[call-cancelled:${callId}] originalCallId가 없습니다. shared_calls 데이터를 확인하세요.`);
                return;
            }
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
                        updatedAt: admin.firestore.FieldValue.serverTimestamp()
                    };
                    tx.update(originalCallRef, updateData);
                    logger.info(`[call-cancelled:${callId}] 원본 콜을 HOLD 상태로 복구 완료. Path: ${originalCallRef.path}`);
                }
                else {
                    logger.warn(`[call-cancelled:${callId}] 원본 콜 문서가 존재하지 않습니다. Path: ${originalCallRef.path}`);
                }
                logger.info(`[call-cancelled:${callId}] shared_calls 초기화 완료`);
            });
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
                const message = {
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
        }
        catch (error) {
            logger.error(`[call-cancelled:${callId}] 공유콜 취소 처리 오류:`, error);
        }
    }
});
// =============================
// 운행 완료 시 고객에게 FCM 알림 전송
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
        if (!isAppCustomer) {
            logger.info(`[notifyCustomerOnComplete:${callId}] 앱 고객이 아님 - 알림 스킵`);
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
                logger.warn(`[notifyCustomerOnComplete:${callId}] FCM 토큰 없음: ${phoneNumber}`);
                return;
            }
            const fare = afterData.fare_set || afterData.finalFare || afterData.fare || 0;
            const pointsUsed = afterData.pointsUsed || 0;
            // FCM 알림 전송
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
            logger.info(`[notifyCustomerOnComplete:${callId}] 고객에게 완료 알림 전송 완료: ${phoneNumber}`);
        }
        catch (error) {
            logger.error(`[notifyCustomerOnComplete:${callId}] 알림 전송 오류:`, error);
        }
    }
});
// 콜 상태 변경 시 알림 (운행시작, 정산완료 등)
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
            const managerTokens = [];
            managerTokensSnapshot.forEach((doc) => {
                const token = doc.data().fcmToken;
                if (token)
                    managerTokens.push(token);
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
                        departure: afterData.departure_set || afterData.departure || "",
                        destination: afterData.destination_set || afterData.destination || "",
                        fare: ((_d = (_c = afterData.fare_set) !== null && _c !== void 0 ? _c : afterData.fare) !== null && _d !== void 0 ? _d : 0).toString(),
                        provinceId: provinceId,
                        cityId: cityId,
                        officeId: officeId
                    },
                    android: {
                        priority: "high",
                        ttl: 60000
                    },
                    tokens: managerTokens
                };
                const response = await admin.messaging().sendEachForMulticast(managerPayload);
                logger.info(`[onCallStatusChanged:${callId}] 콜매니저 FCM 전송 - ${afterData.status} - 성공: ${response.successCount}`);
            }
        }
    }
    catch (managerError) {
        logger.error(`[onCallStatusChanged:${callId}] 콜매니저 FCM 오류:`, managerError);
    }
    // 운행 시작 (IN_PROGRESS) 또는 정산 완료 (COMPLETED) 상태 체크
    if (afterData.status === "ACCEPTED" || afterData.status === "IN_PROGRESS" || afterData.status === "COMPLETED") {
        // 콜매니저 FCM 토큰 조회 (managerTokens 사용)
        const managerTokensSnapshot = await admin
            .firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId)
            .collection("managerTokens")
            .get();
        const tokens = [];
        managerTokensSnapshot.forEach((doc) => {
            const token = doc.data().fcmToken;
            if (token)
                tokens.push(token);
        });
        logger.info(`[onCallStatusChanged:${callId}] STATUS_CHANGE tokens count: ${tokens.length}`);
        if (tokens.length === 0) {
            logger.warn(`[onCallStatusChanged:${callId}] No manager tokens found.`);
            return;
        }
        // notificationData 변수 제거 - 더 이상 사용하지 않음
        if (afterData.status === "IN_PROGRESS") {
            // 운행 시작 로직
            logger.info(`[onCallStatusChanged:${callId}] IN_PROGRESS block entered - sending STATUS_CHANGE`);
            const driverName = afterData.assignedDriverName || "기사";
            // 공유콜인 경우: 원사무실(sourceOfficeId)에만 (공유기사) 표시, 수락사무실에는 실제 기사 이름만 표시
            const isSourceOffice = afterData.callType === "SHARED" && afterData.sourceOfficeId === officeId;
            const driverDisplayName = isSourceOffice ? `${driverName} (공유기사)` : driverName;
            logger.info(`[onCallStatusChanged:${callId}] 기사 이름 표시 로직 - callType: ${afterData.callType}, sourceOfficeId: ${afterData.sourceOfficeId}, currentOfficeId: ${officeId}, isSourceOffice: ${isSourceOffice}, driverDisplayName: ${driverDisplayName}`);
            // 디버깅: 운행 정보 로그
            logger.info(`[onCallStatusChanged:${callId}] 운행 정보 - departure_set: ${afterData.departure_set}, destination_set: ${afterData.destination_set}, fare_set: ${afterData.fare_set}, fare: ${afterData.fare}`);
            // FCM 메시지 전송 (notification 필드 추가로 백그라운드에서도 확실히 알림 표시)
            const payload = {
                notification: {
                    title: "🚗 운행 시작",
                    body: `${afterData.customerName || "고객"} - ${driverDisplayName}`,
                },
                data: {
                    type: "STATUS_CHANGE",
                    callId: callId,
                    statusText: "운행 시작",
                    customerName: afterData.customerName || "고객",
                    customerPhone: afterData.customerPhone || "-",
                    driverName: driverDisplayName,
                    departure: afterData.departure_set || afterData.departure || "",
                    destination: afterData.destination_set || afterData.destination || "",
                    fare: ((_f = (_e = afterData.fare_set) !== null && _e !== void 0 ? _e : afterData.fare) !== null && _f !== void 0 ? _f : 0).toString()
                },
                android: {
                    priority: "high",
                    ttl: 60000,
                    notification: {
                        sound: "default",
                        clickAction: "com.designated.callmanager.HOME",
                        channelId: "status_change_fcm_channel"
                    }
                }
            };
            // 모든 관리자에게 전송
            for (const token of tokens) {
                try {
                    await admin.messaging().send(Object.assign(Object.assign({}, payload), { token }));
                    logger.info(`[onCallStatusChanged:${callId}] 운행시작 FCM 알림 전송 성공 - token: ${token.substring(0, 10)}...`);
                }
                catch (error) {
                    logger.error(`[onCallStatusChanged:${callId}] 운행시작 FCM 알림 전송 실패:`, error);
                }
            }
        }
        else if (afterData.status === "COMPLETED") {
            // 운행 완료 로직
            const basedriverName = afterData.assignedDriverName || "기사";
            const isSourceOffice = afterData.callType === "SHARED" && afterData.sourceOfficeId === officeId;
            const driverName = isSourceOffice ? `${basedriverName} (공유기사)` : basedriverName;
            logger.info(`[onCallStatusChanged:${callId}] 운행완료 기사 이름 표시 로직 - callType: ${afterData.callType}, sourceOfficeId: ${afterData.sourceOfficeId}, currentOfficeId: ${officeId}, isSourceOffice: ${isSourceOffice}, driverName: ${driverName}`);
            // 디버깅: 운행 정보 로그
            logger.info(`[onCallStatusChanged:${callId}] 운행 정보 - departure_set: ${afterData.departure_set}, destination_set: ${afterData.destination_set}, fare_set: ${afterData.fare_set}, fare: ${afterData.fare}`);
            // FCM 메시지 전송 (notification 필드 추가로 백그라운드에서도 확실히 알림 표시)
            const payload = {
                notification: {
                    title: "✅ 운행 완료",
                    body: `${afterData.customerName || "고객"} - ${driverName}`,
                },
                data: {
                    type: "STATUS_CHANGE",
                    callId: callId,
                    statusText: "운행 완료",
                    customerName: afterData.customerName || "고객",
                    customerPhone: afterData.customerPhone || "-",
                    driverName: driverName,
                    departure: afterData.departure_set || afterData.departure || "",
                    destination: afterData.destination_set || afterData.destination || "",
                    fare: ((_h = (_g = afterData.fare_set) !== null && _g !== void 0 ? _g : afterData.fare) !== null && _h !== void 0 ? _h : 0).toString()
                },
                android: {
                    priority: "high",
                    ttl: 60000,
                    notification: {
                        sound: "default",
                        clickAction: "com.designated.callmanager.HOME",
                        channelId: "status_change_fcm_channel"
                    }
                }
            };
            // 모든 관리자에게 전송
            for (const token of tokens) {
                try {
                    await admin.messaging().send(Object.assign(Object.assign({}, payload), { token }));
                    logger.info(`[onCallStatusChanged:${callId}] 운행완료 FCM 알림 전송 성공 - token: ${token.substring(0, 10)}...`);
                }
                catch (error) {
                    logger.error(`[onCallStatusChanged:${callId}] 운행완료 FCM 알림 전송 실패:`, error);
                }
            }
        }
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
                priority: "high",
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
                    updatedAt: admin.firestore.FieldValue.serverTimestamp()
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
                    updatedAt: admin.firestore.FieldValue.serverTimestamp()
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
                completedAt: admin.firestore.FieldValue.serverTimestamp(),
                destCallId: callId
            });
            // 2. 포인트 처리 (별도 함수 호출)
            await (0, points_1.processSharedCallPoints)(sharedCallData, provinceId, cityId, officeId, fare, sourceSharedCallId);
            logger.info(`[call-completed:${callId}] 공유콜 완료 처리 및 포인트 분배 완료. SharedCallId: ${sourceSharedCallId}`);
        }
        catch (error) {
            logger.error(`[call-completed:${callId}] 공유콜 완료 처리 오류:`, error);
        }
    }
});
var finalizeWorkDay_1 = require("./finalizeWorkDay");
Object.defineProperty(exports, "finalizeWorkDay", { enumerable: true, get: function () { return finalizeWorkDay_1.finalizeWorkDay; } });
// Agora PTT 토큰 관련 함수 추가
var agoraToken_1 = require("./agoraToken");
Object.defineProperty(exports, "generateAgoraToken", { enumerable: true, get: function () { return agoraToken_1.generateAgoraToken; } });
Object.defineProperty(exports, "refreshAgoraToken", { enumerable: true, get: function () { return agoraToken_1.refreshAgoraToken; } });
// PTT 자동 채널 참여 함수들
var pttSignaling_1 = require("./pttSignaling");
Object.defineProperty(exports, "onPickupDriverStatusChange", { enumerable: true, get: function () { return pttSignaling_1.onPickupDriverStatusChange; } });
Object.defineProperty(exports, "onDesignatedDriverStatusChange", { enumerable: true, get: function () { return pttSignaling_1.onDesignatedDriverStatusChange; } });
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
                linkedAt: admin.firestore.FieldValue.serverTimestamp(),
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
            linkedAt: admin.firestore.FieldValue.serverTimestamp(),
            timestamp: admin.firestore.FieldValue.serverTimestamp()
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
                        const now = admin.firestore.Timestamp.now();
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
            claimedAt: admin.firestore.FieldValue.serverTimestamp(),
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
    // 기사가 배정/수락된 상태에서 -> HOLD 또는 CANCELLED_BY_DRIVER로 변경된 경우
    const wasCancelled = ((beforeData.status === "ASSIGNED" || beforeData.status === "ACCEPTED") && afterData.status === "HOLD") ||
        ((beforeData.status === "ASSIGNED" || beforeData.status === "ACCEPTED") && afterData.status === "CANCELLED_BY_DRIVER");
    if (!wasCancelled) {
        return;
    }
    logger.info(`[${callId}] 기사가 운행 취소 - 손님에게 알림 전송 시작`);
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
        const customerPayload = {
            data: {
                type: "CALL_CANCELLED",
                callId: callId,
                cancelReason: afterData.cancelReason || "운행취소"
            },
            android: {
                priority: "high",
                ttl: 60000
            },
            token: customerFcmToken
        };
        await admin.messaging().send(customerPayload);
        logger.info(`[${callId}] 고객에게 취소 알림 전송 완료: ${customerPhone}`);
    }
    catch (error) {
        logger.error(`[${callId}] 고객 취소 알림 전송 오류:`, error);
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
                requestedAt: admin.firestore.Timestamp.now(),
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
            statsUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
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
            statsUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
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
        // 멀티캐스트 메시지 전송
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
            }
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
// 자동 데이터 정리: 매일 오전 11시 실행
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
                        .where("timestamp", "<", admin.firestore.Timestamp.fromMillis(oneHourAgo))
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
                        .where("timestamp", "<", admin.firestore.Timestamp.fromMillis(oneHourAgo))
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
                    // CANCELLED_BY_DRIVER 상태 조회
                    const cancelledByDriverCalls = await db.collection("provinces").doc(provinceId)
                        .collection("cities").doc(cityId)
                        .collection("offices").doc(officeId)
                        .collection("calls")
                        .where("status", "==", "CANCELLED_BY_DRIVER")
                        .get();
                    const allCancelled = [...cancelledCalls.docs, ...cancelledByDriverCalls.docs];
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
            .where("timestamp", "<", admin.firestore.Timestamp.fromMillis(oneHourAgo))
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
                priority: "high",
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
    }
    catch (error) {
        logger.error(`[기사상태] ${driverId}: FCM 전송 오류`, error);
    }
});
const _forceDeploy = Date.now() + 1000006; // 배포 강제용 더미 변수
void _forceDeploy; // 사용해서 컴파일 경고 해소
//# sourceMappingURL=index.js.map