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
exports.notifyRestaurantOnNoResponse = exports.createSharedCallFromRestaurant = exports.redeemRestaurantInviteCode = exports.generateRestaurantInviteCode = void 0;
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
const https_1 = require("firebase-functions/v2/https");
const scheduler_1 = require("firebase-functions/v2/scheduler");
const logger = __importStar(require("firebase-functions/logger"));
const REGION = "asia-northeast3";
const INVITE_CODE_LENGTH = 6;
const INVITE_CODE_TTL_DAYS = 7;
const NO_RESPONSE_TIMEOUT_MIN = 5;
function generateRandomCode(length) {
    const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 1, I, 0, O 제외
    let result = "";
    for (let i = 0; i < length; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
}
async function isHeadManager(uid) {
    var _a;
    if (!uid)
        return false;
    const adminDoc = await admin.firestore().doc(`admins/${uid}`).get();
    if (!adminDoc.exists)
        return false;
    const role = (_a = adminDoc.data()) === null || _a === void 0 ? void 0 : _a.role;
    return role === "HEAD_MANAGER" || role === "SUPER_ADMIN";
}
/**
 * generateRestaurantInviteCode (admin only, P0)
 * 사용자(원규씨, HEAD_MANAGER)가 식당 가입 코드 발급
 * P1+ 귀속 모드 활성화 시 사무실 매니저도 발급 가능하도록 확장
 */
exports.generateRestaurantInviteCode = (0, https_1.onCall)({ region: REGION }, async (request) => {
    var _a;
    if (!(await isHeadManager((_a = request.auth) === null || _a === void 0 ? void 0 : _a.uid))) {
        throw new https_1.HttpsError("permission-denied", "관리자만 가입 코드를 발급할 수 있습니다.");
    }
    const { provinceId, cityId, officeId, notes } = request.data || {};
    if (!provinceId || !cityId || !officeId) {
        throw new https_1.HttpsError("invalid-argument", "provinceId, cityId, officeId가 필요합니다.");
    }
    const codesRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("restaurantInviteCodes");
    // 중복 회피: 최대 5회 시도
    let code = "";
    for (let attempt = 0; attempt < 5; attempt++) {
        code = generateRandomCode(INVITE_CODE_LENGTH);
        const existing = await codesRef.doc(code).get();
        if (!existing.exists)
            break;
        if (attempt === 4) {
            throw new https_1.HttpsError("internal", "코드 발급 실패 (중복).");
        }
    }
    const expiresAt = firestore_1.Timestamp.fromMillis(Date.now() + INVITE_CODE_TTL_DAYS * 24 * 60 * 60 * 1000);
    await codesRef.doc(code).set({
        used: false,
        usedByRestaurantId: null,
        createdBy: "PLATFORM", // P0: 사용자 admin만
        notes: notes || "",
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        expiresAt,
    });
    logger.info(`[restaurant] generateInviteCode - office: ${officeId}, code: ${code}`);
    return { code, expiresAt: expiresAt.toMillis() };
});
/**
 * redeemRestaurantInviteCode (식당앱 익명 인증)
 * 식당이 가입 코드로 가입 → restaurants doc 생성 + 코드 used=true
 */
exports.redeemRestaurantInviteCode = (0, https_1.onCall)({ region: REGION }, async (request) => {
    var _a;
    const uid = (_a = request.auth) === null || _a === void 0 ? void 0 : _a.uid;
    if (!uid) {
        throw new https_1.HttpsError("unauthenticated", "익명 인증이 필요합니다.");
    }
    const { code, provinceId, cityId, officeId, name, phone, address, taxiPhoneNumber, fcmToken } = request.data || {};
    if (!code || !provinceId || !cityId || !officeId) {
        throw new https_1.HttpsError("invalid-argument", "code, provinceId, cityId, officeId가 필요합니다.");
    }
    if (!name || !phone) {
        throw new https_1.HttpsError("invalid-argument", "식당명과 전화번호가 필요합니다.");
    }
    const officeRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId);
    const codeRef = officeRef.collection("restaurantInviteCodes").doc(code);
    const result = await admin.firestore().runTransaction(async (tx) => {
        var _a, _b, _c;
        const codeSnap = await tx.get(codeRef);
        if (!codeSnap.exists) {
            throw new https_1.HttpsError("not-found", "유효하지 않은 코드입니다.");
        }
        const codeData = codeSnap.data();
        if (codeData.used) {
            throw new https_1.HttpsError("already-exists", "이미 사용된 코드입니다.");
        }
        const expiresMs = (_c = (_b = (_a = codeData.expiresAt) === null || _a === void 0 ? void 0 : _a.toMillis) === null || _b === void 0 ? void 0 : _b.call(_a)) !== null && _c !== void 0 ? _c : 0;
        if (expiresMs && expiresMs < Date.now()) {
            throw new https_1.HttpsError("deadline-exceeded", "만료된 코드입니다.");
        }
        const restaurantRef = officeRef.collection("restaurants").doc();
        tx.set(restaurantRef, {
            name,
            phone,
            address: address || "",
            taxiPhoneNumber: taxiPhoneNumber || "",
            points: 0,
            ownerUid: uid,
            fcmToken: fcmToken || "",
            registeredOfficeId: officeId,
            acquiredBy: codeData.createdBy === "PLATFORM" ? "PLATFORM" : codeData.createdBy, // P0=PLATFORM, P1+=officeId
            createdAt: firestore_1.FieldValue.serverTimestamp(),
        });
        tx.update(codeRef, {
            used: true,
            usedByRestaurantId: restaurantRef.id,
            usedAt: firestore_1.FieldValue.serverTimestamp(),
        });
        return { restaurantId: restaurantRef.id };
    });
    logger.info(`[restaurant] redeem - code: ${code}, restaurantId: ${result.restaurantId}, ownerUid: ${uid}`);
    return {
        restaurantId: result.restaurantId,
        provinceId,
        cityId,
        officeId,
    };
});
/**
 * createSharedCallFromRestaurant (식당앱 익명 인증)
 * 식당앱이 호출 → shared_calls 생성 (sourceRestaurantId, paymentMethod 포함)
 * P0 = PLATFORM acquired (모든 사무실 broadcast)
 */
exports.createSharedCallFromRestaurant = (0, https_1.onCall)({ region: REGION }, async (request) => {
    var _a;
    const uid = (_a = request.auth) === null || _a === void 0 ? void 0 : _a.uid;
    if (!uid) {
        throw new https_1.HttpsError("unauthenticated", "익명 인증이 필요합니다.");
    }
    const { restaurantId, provinceId, cityId, officeId, departure, destination, paymentMethod } = request.data || {};
    if (!restaurantId || !provinceId || !cityId || !officeId) {
        throw new https_1.HttpsError("invalid-argument", "restaurantId, provinceId, cityId, officeId가 필요합니다.");
    }
    if (paymentMethod && !["CASH", "RESTAURANT_POINT"].includes(paymentMethod)) {
        throw new https_1.HttpsError("invalid-argument", "paymentMethod는 CASH 또는 RESTAURANT_POINT여야 합니다.");
    }
    const restaurantRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("restaurants").doc(restaurantId);
    const restaurantSnap = await restaurantRef.get();
    if (!restaurantSnap.exists) {
        throw new https_1.HttpsError("not-found", "식당을 찾을 수 없습니다.");
    }
    const restaurantData = restaurantSnap.data();
    if (restaurantData.ownerUid !== uid) {
        throw new https_1.HttpsError("permission-denied", "본인 식당만 호출할 수 있습니다.");
    }
    const expiresAt = firestore_1.Timestamp.fromMillis(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30일
    const expiresAt5min = firestore_1.Timestamp.fromMillis(Date.now() + NO_RESPONSE_TIMEOUT_MIN * 60 * 1000);
    const sharedCallRef = admin.firestore().collection("shared_calls").doc();
    await sharedCallRef.set({
        phoneNumber: restaurantData.phone,
        contactName: restaurantData.name,
        contactAddress: restaurantData.address || "",
        sourceProvinceId: provinceId,
        sourceCityId: cityId,
        sourceOfficeId: officeId, // 행정 소속
        sourceRestaurantId: restaurantId, // 식당앱 발생 표시 (PLATFORM 분기 키)
        targetProvinceId: provinceId,
        targetCityId: cityId,
        status: "OPEN",
        callType: "RESTAURANT", // 신규 type (식당앱 발생)
        paymentMethod: paymentMethod || "CASH",
        departure: departure || restaurantData.address || "",
        destination: destination || "",
        timestamp: firestore_1.FieldValue.serverTimestamp(),
        timestampClient: Date.now(),
        expireAt: expiresAt,
        expiresAt5min, // 5분 미응답 알림용
        notifiedNoResponse: false,
        fromRestaurantApp: true,
    });
    logger.info(`[restaurant] createSharedCall - restaurantId: ${restaurantId}, paymentMethod: ${paymentMethod}, sharedCallId: ${sharedCallRef.id}`);
    return { sharedCallId: sharedCallRef.id };
});
/**
 * notifyRestaurantOnNoResponse (scheduled, 매분)
 * shared_calls 중 status=OPEN AND expiresAt5min < now AND notifiedNoResponse=false
 * → 식당 fcmToken으로 "사무실 미응답" 알림 발송 + notifiedNoResponse=true
 */
exports.notifyRestaurantOnNoResponse = (0, scheduler_1.onSchedule)({
    region: REGION,
    schedule: "every 1 minutes",
    timeZone: "Asia/Seoul",
}, async () => {
    const now = firestore_1.Timestamp.now();
    const snap = await admin.firestore()
        .collection("shared_calls")
        .where("status", "==", "OPEN")
        .where("notifiedNoResponse", "==", false)
        .where("expiresAt5min", "<", now)
        .limit(50)
        .get();
    if (snap.empty)
        return;
    const promises = [];
    for (const doc of snap.docs) {
        const data = doc.data();
        const sourceRestaurantId = data.sourceRestaurantId;
        if (!sourceRestaurantId) {
            // 식당앱 콜 아니면 skip (notifiedNoResponse만 true 처리)
            promises.push(doc.ref.update({ notifiedNoResponse: true }).then(() => undefined));
            continue;
        }
        const restRef = admin.firestore()
            .collection("provinces").doc(data.sourceProvinceId)
            .collection("cities").doc(data.sourceCityId)
            .collection("offices").doc(data.sourceOfficeId)
            .collection("restaurants").doc(sourceRestaurantId);
        promises.push((async () => {
            var _a;
            try {
                const restSnap = await restRef.get();
                const fcmToken = (_a = restSnap.data()) === null || _a === void 0 ? void 0 : _a.fcmToken;
                if (fcmToken) {
                    await admin.messaging().send({
                        token: fcmToken,
                        notification: {
                            title: "사무실 미응답",
                            body: "5분간 콜 응답이 없습니다. 다른 호출을 시도하세요.",
                        },
                        data: {
                            type: "RESTAURANT_NO_RESPONSE",
                            sharedCallId: doc.id,
                        },
                    });
                    logger.info(`[restaurant] noResponse 알림 - restaurantId: ${sourceRestaurantId}, sharedCallId: ${doc.id}`);
                }
                await doc.ref.update({ notifiedNoResponse: true });
            }
            catch (e) {
                logger.error(`[restaurant] noResponse 알림 실패 - sharedCallId: ${doc.id}`, e);
            }
        })());
    }
    await Promise.all(promises);
    logger.info(`[restaurant] noResponse 알림 처리 완료 - 건수: ${snap.size}`);
});
//# sourceMappingURL=restaurant.js.map