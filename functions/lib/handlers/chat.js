"use strict";
/**
 * 콜마당 사무실 단톡방 채팅 — Cloud Functions
 *
 * 트리거 / 함수:
 *  1. onChatMessageCreated — 새 메시지 → FCM 발송
 *  2. addChatMember / removeChatMember — 멤버 동기화 헬퍼 (index.ts에서 호출)
 *  3. scheduledChatMessageCleanup — 90일 PII 자동 삭제 (매일 04:00 KST)
 *  4. backfillChatMembers — 운영 1회 호출 (Callable, 매니저 admin 전용)
 *
 * 관련 문서:
 *  - 플랜: memory/designated_drive/chat_feature_plan_2026-04-28.md
 *  - 공유 스펙: docs/chat-shared-spec.md
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
exports.backfillChatMembers = exports.scheduledChatMessageCleanup = exports.onChatSyncAdminRemoval = exports.onChatSyncPickupDriver = exports.onChatSyncDesignatedDriver = exports.onChatMessageCreated = void 0;
exports.addChatMember = addChatMember;
exports.removeChatMember = removeChatMember;
const firestore_1 = require("firebase-functions/v2/firestore");
const scheduler_1 = require("firebase-functions/v2/scheduler");
const https_1 = require("firebase-functions/v2/https");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
const logger = __importStar(require("firebase-functions/logger"));
const fcmPayload_1 = require("../utils/fcmPayload");
const REGION = "asia-northeast3";
/**
 * 채팅방 멤버 등록 — 가입 승인 시 호출
 */
async function addChatMember(provinceId, cityId, officeId, userId, role) {
    if (!provinceId || !cityId || !officeId || !userId) {
        logger.warn("[addChatMember] 필수 매개변수 누락", { provinceId, cityId, officeId, userId });
        return;
    }
    try {
        const memberRef = admin.firestore()
            .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}/chatRoom/main/members/${userId}`);
        await memberRef.set({
            userId,
            role,
            joinedAt: firestore_2.FieldValue.serverTimestamp(),
        }, { merge: true });
        logger.info(`[addChatMember] ${role} 멤버 등록: ${userId} @ ${officeId}`);
    }
    catch (e) {
        logger.error("[addChatMember] 실패", e);
    }
}
/**
 * 채팅방 멤버 제거 — 회원 거부/탈퇴 시 호출. 멱등성 보장 (이미 없으면 무시).
 */
async function removeChatMember(provinceId, cityId, officeId, userId) {
    if (!provinceId || !cityId || !officeId || !userId) {
        logger.warn("[removeChatMember] 필수 매개변수 누락");
        return;
    }
    try {
        const memberRef = admin.firestore()
            .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}/chatRoom/main/members/${userId}`);
        await memberRef.delete().catch(() => { });
        logger.info(`[removeChatMember] 멤버 제거: ${userId} @ ${officeId}`);
    }
    catch (e) {
        logger.error("[removeChatMember] 실패", e);
    }
}
/**
 * 채팅방 메시지 생성 트리거.
 *  사무실 모든 멤버 토큰 수집 → 발신자 본인 제외 → FCM multicast 발송.
 *  채팅 알림 채널은 클라이언트 앱이 type=NEW_CHAT_MESSAGE 수신 후 자체 채널로 표시.
 */
exports.onChatMessageCreated = (0, firestore_1.onDocumentCreated)({
    region: REGION,
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/chatRoom/main/messages/{messageId}",
}, async (event) => {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l;
    const { provinceId, cityId, officeId, messageId } = event.params;
    if (!event.data) {
        logger.warn(`[onChatMessageCreated:${messageId}] event.data 없음`);
        return;
    }
    const msg = event.data.data();
    if (!msg.senderId || (!msg.text && !msg.imageUrl)) {
        logger.warn(`[onChatMessageCreated:${messageId}] senderId 누락 또는 text/imageUrl 둘 다 누락`);
        return;
    }
    const senderId = msg.senderId;
    const senderName = (_a = msg.senderName) !== null && _a !== void 0 ? _a : "알 수 없음";
    const senderRole = (_b = msg.senderRole) !== null && _b !== void 0 ? _b : "MANAGER";
    const text = (_c = msg.text) !== null && _c !== void 0 ? _c : "";
    const imageUrl = (_d = msg.imageUrl) !== null && _d !== void 0 ? _d : "";
    const imagePath = (_e = msg.imagePath) !== null && _e !== void 0 ? _e : "";
    const imageWidth = msg.imageWidth;
    const imageHeight = msg.imageHeight;
    const createdAtMs = (_h = (_g = (_f = msg.createdAt) === null || _f === void 0 ? void 0 : _f.toMillis) === null || _g === void 0 ? void 0 : _g.call(_f)) !== null && _h !== void 0 ? _h : Date.now();
    const clientCreatedAt = (_j = msg.clientCreatedAt) !== null && _j !== void 0 ? _j : createdAtMs;
    logger.info(`[onChatMessageCreated:${messageId}] from ${senderRole}/${senderName}/${senderId} @ office=${officeId}`);
    try {
        const officeRef = admin.firestore()
            .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}`);
        // 1. 매니저 토큰 — managerTokens/{adminUid}.fcmToken (docId == authUid 가정)
        const managerSnapshot = await officeRef.collection("managerTokens").get();
        const managerTokens = managerSnapshot.docs
            .filter(d => d.id !== senderId)
            .map(d => d.data().fcmToken)
            .filter((t) => typeof t === "string" && t.length > 0);
        // 2. 대리기사 토큰 — designated_drivers, fcmToken 보유 + authUid != sender
        const designatedSnapshot = await officeRef.collection("designated_drivers").get();
        const designatedTokens = designatedSnapshot.docs
            .filter(d => {
            const data = d.data();
            if (data.authUid === senderId || d.id === senderId)
                return false;
            return typeof data.fcmToken === "string" && data.fcmToken.length > 0;
        })
            .map(d => d.data().fcmToken);
        // 3. 픽업기사 토큰 — pickup_drivers (V1 미배포 시 빈 배열)
        const pickupSnapshot = await officeRef.collection("pickup_drivers").get();
        const pickupTokens = pickupSnapshot.docs
            .filter(d => {
            const data = d.data();
            if (data.authUid === senderId || d.id === senderId)
                return false;
            return typeof data.fcmToken === "string" && data.fcmToken.length > 0;
        })
            .map(d => d.data().fcmToken);
        const allTokens = [...managerTokens, ...designatedTokens, ...pickupTokens];
        const uniqueTokens = Array.from(new Set(allTokens));
        logger.info(`[onChatMessageCreated:${messageId}] tokens=${uniqueTokens.length} (mgr=${managerTokens.length}, des=${designatedTokens.length}, pck=${pickupTokens.length})`);
        if (uniqueTokens.length === 0) {
            logger.info(`[onChatMessageCreated:${messageId}] 발송 대상 토큰 0건. 스킵`);
            return;
        }
        // 알림 미리보기: 이미지면 [사진], 텍스트는 본문 100자 cap
        const titleText = senderName;
        const bodyText = imageUrl
            ? "[사진]"
            : (text.length > 100 ? text.substring(0, 100) + "…" : text);
        const multicast = (0, fcmPayload_1.buildMulticastFcmPayload)({
            data: {
                type: "NEW_CHAT_MESSAGE",
                messageId,
                senderId,
                senderName,
                senderRole,
                text,
                imageUrl,
                imagePath,
                imageWidth: (_k = imageWidth === null || imageWidth === void 0 ? void 0 : imageWidth.toString()) !== null && _k !== void 0 ? _k : "",
                imageHeight: (_l = imageHeight === null || imageHeight === void 0 ? void 0 : imageHeight.toString()) !== null && _l !== void 0 ? _l : "",
                createdAt: String(createdAtMs),
                clientCreatedAt: String(clientCreatedAt),
                provinceId,
                cityId,
                officeId,
            },
            title: titleText,
            body: bodyText,
            level: "active",
            ttlSeconds: 86400, // 24시간
        }, uniqueTokens);
        const response = await admin.messaging().sendEachForMulticast(multicast);
        logger.info(`[onChatMessageCreated:${messageId}] sent: success=${response.successCount}, failure=${response.failureCount}`);
        // 등록 해제 토큰 정리
        if (response.failureCount > 0) {
            const deadTokens = [];
            response.responses.forEach((r, idx) => {
                if (!r.success && r.error) {
                    const code = r.error.code;
                    if (code === "messaging/registration-token-not-registered") {
                        deadTokens.push(uniqueTokens[idx]);
                    }
                }
            });
            if (deadTokens.length > 0) {
                await cleanupDeadTokens(officeRef, deadTokens);
            }
        }
    }
    catch (e) {
        logger.error(`[onChatMessageCreated:${messageId}] 처리 실패`, e);
    }
});
/**
 * 등록 해제된 토큰을 office의 토큰 컬렉션 3곳에서 제거
 */
async function cleanupDeadTokens(officeRef, deadTokens) {
    const collections = ["managerTokens", "designated_drivers", "pickup_drivers"];
    for (const colName of collections) {
        try {
            const snapshot = await officeRef.collection(colName).get();
            const batch = admin.firestore().batch();
            let count = 0;
            snapshot.forEach(doc => {
                const token = doc.data().fcmToken;
                if (typeof token === "string" && deadTokens.includes(token)) {
                    batch.update(doc.ref, { fcmToken: firestore_2.FieldValue.delete() });
                    count++;
                }
            });
            if (count > 0) {
                await batch.commit();
                logger.info(`[cleanupDeadTokens] ${colName}: ${count}건 fcmToken 제거`);
            }
        }
        catch (e) {
            logger.error(`[cleanupDeadTokens:${colName}] 실패`, e);
        }
    }
}
// ============================================================
//  트리거: 기사 가입/탈퇴 시 chat member 자동 동기화
// ============================================================
/**
 * 기사 문서 변화에 따른 chat member 동기화 헬퍼.
 *  - 생성 (create): authUid 기반 chat member 추가
 *  - 삭제 (delete): authUid 기반 chat member 제거
 *  - 업데이트: 무시 (멤버십 변화 없음)
 */
async function syncChatMemberOnDriverChange(event, role) {
    var _a, _b;
    const { provinceId, cityId, officeId } = event.params;
    if (!event.data)
        return;
    const before = ((_a = event.data.before) === null || _a === void 0 ? void 0 : _a.exists) ? event.data.before.data() : null;
    const after = ((_b = event.data.after) === null || _b === void 0 ? void 0 : _b.exists) ? event.data.after.data() : null;
    // 생성: before 없음 + after 있음
    if (!before && after) {
        const authUid = after.authUid || undefined;
        if (!authUid) {
            logger.warn(`[syncChatMemberOnDriverChange:${role}] authUid 없음 — chat member 등록 스킵`);
            return;
        }
        await addChatMember(provinceId, cityId, officeId, authUid, role);
        return;
    }
    // 삭제: before 있음 + after 없음
    if (before && !after) {
        const authUid = before.authUid || undefined;
        if (!authUid)
            return;
        await removeChatMember(provinceId, cityId, officeId, authUid);
        return;
    }
    // 업데이트는 멤버십 변화 없음. authUid 변경 시점은 가입 직후뿐이라 안전 (사후 변경 X)
}
/**
 * 대리기사 가입/탈퇴 시 chat member 동기화.
 *  PendingDriversViewModel에서 designated_drivers 문서가 생성되면 트리거 발화.
 */
exports.onChatSyncDesignatedDriver = (0, firestore_1.onDocumentWritten)({
    region: REGION,
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}",
}, async (event) => {
    await syncChatMemberOnDriverChange(event, "DESIGNATED_DRIVER");
});
/**
 * 픽업기사 가입/탈퇴 시 chat member 동기화.
 *  픽업기사 승인 시 pickup_drivers 문서가 생성되면 트리거 발화.
 */
exports.onChatSyncPickupDriver = (0, firestore_1.onDocumentWritten)({
    region: REGION,
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/pickup_drivers/{driverId}",
}, async (event) => {
    await syncChatMemberOnDriverChange(event, "PICKUP_DRIVER");
});
/**
 * 매니저 admins 문서 삭제 시 chat member 제거.
 *  생성은 registerOwner / approveOfficeApplication 흐름에서 명시적 호출.
 *  본 트리거는 삭제 케이스만 처리 (admins 컬렉션 트리거에서 associatedOfficeId 추출 후 멤버 제거).
 */
exports.onChatSyncAdminRemoval = (0, firestore_1.onDocumentWritten)({
    region: REGION,
    document: "admins/{adminId}",
}, async (event) => {
    var _a, _b;
    if (!event.data)
        return;
    const before = ((_a = event.data.before) === null || _a === void 0 ? void 0 : _a.exists) ? event.data.before.data() : null;
    const after = ((_b = event.data.after) === null || _b === void 0 ? void 0 : _b.exists) ? event.data.after.data() : null;
    // 삭제 케이스만 처리
    if (before && !after) {
        const adminId = event.params.adminId;
        const provinceId = before.associatedProvinceId;
        const cityId = before.associatedCityId;
        const officeId = before.associatedOfficeId;
        if (!provinceId || !cityId || !officeId) {
            logger.warn(`[onChatSyncAdminRemoval:${adminId}] 사무실 정보 없음 — chat member 제거 스킵`);
            return;
        }
        await removeChatMember(provinceId, cityId, officeId, adminId);
    }
});
// ============================================================
//  스케줄: 7일 PII cleanup (Local-first 철학 — Room이 단일 진실, Firestore는 신규 사용자/FCM 누락 복구용)
// ============================================================
/**
 * 채팅 메시지 7일 자동 삭제 — 매일 04:00 KST 실행.
 *  collectionGroup("messages") + path 필터로 chatRoom 메시지만 삭제.
 *  500건 batch로 일괄 처리.
 *  imagePath 있는 메시지는 Firestore delete 후 Storage 파일도 함께 삭제 (best-effort).
 *  실패해도 UI 영향 X — orphan storage만 남음 (Phase 2에서 weekly orphan-scan 검토).
 */
exports.scheduledChatMessageCleanup = (0, scheduler_1.onSchedule)({
    schedule: "every day 04:00",
    timeZone: "Asia/Seoul",
    region: REGION,
}, async () => {
    const cutoffMs = Date.now() - 7 * 24 * 60 * 60 * 1000;
    const cutoff = firestore_2.Timestamp.fromMillis(cutoffMs);
    logger.info(`[scheduledChatMessageCleanup] 시작. cutoff=${new Date(cutoffMs).toISOString()}`);
    let totalDeleted = 0;
    let totalStorageDeleted = 0;
    let totalStorageFailed = 0;
    const BATCH_SIZE = 500;
    try {
        let cursor = null;
        let safetyCounter = 0; // 무한 루프 방지
        while (safetyCounter < 1000) { // 최대 50만건 / 회 (현실적 충분)
            safetyCounter++;
            let q = admin.firestore()
                .collectionGroup("messages")
                .where("createdAt", "<", cutoff)
                .orderBy("createdAt", "asc")
                .limit(BATCH_SIZE);
            if (cursor)
                q = q.startAfter(cursor);
            const snapshot = await q.get();
            if (snapshot.empty)
                break;
            // chatRoom 메시지만 필터 (다른 messages 컬렉션 보호)
            const chatMessages = snapshot.docs.filter(d => d.ref.path.includes("/chatRoom/main/messages/"));
            if (chatMessages.length > 0) {
                // 1. imagePath 추출 (Storage delete 위해)
                const imagePaths = chatMessages
                    .map(d => d.data().imagePath)
                    .filter((p) => typeof p === "string" && p.length > 0);
                // 2. Firestore delete 먼저 (이게 진짜 cleanup, Storage 실패해도 UI 영향 X)
                const batch = admin.firestore().batch();
                chatMessages.forEach(d => batch.delete(d.ref));
                await batch.commit();
                totalDeleted += chatMessages.length;
                // 3. Storage delete (best-effort, 실패는 logging만)
                if (imagePaths.length > 0) {
                    const bucket = admin.storage().bucket();
                    const results = await Promise.allSettled(imagePaths.map(path => bucket.file(path).delete()));
                    results.forEach((r, idx) => {
                        if (r.status === "fulfilled") {
                            totalStorageDeleted++;
                        }
                        else {
                            totalStorageFailed++;
                            logger.warn(`[scheduledChatMessageCleanup] storage delete 실패: ${imagePaths[idx]}`, r.reason);
                        }
                    });
                }
            }
            cursor = snapshot.docs[snapshot.docs.length - 1];
            if (snapshot.size < BATCH_SIZE)
                break;
        }
        logger.info(`[scheduledChatMessageCleanup] 완료. firestore=${totalDeleted}건, storage=${totalStorageDeleted}건 (실패 ${totalStorageFailed})`);
    }
    catch (e) {
        logger.error(`[scheduledChatMessageCleanup] 실패. firestore 진행 ${totalDeleted}건`, e);
    }
});
/**
 * 기존 사무실 멤버를 chat members에 일괄 등록.
 *  매니저 admin이 firebase functions:shell 또는 Cloud Console에서 1회 호출.
 *  매개변수: { provinceId, cityId, officeId }
 *  검증: 호출자가 admins/{uid} 문서 보유 + 해당 사무실 매니저인지.
 *  멱등성: 이미 멤버이면 set merge로 안전하게 재등록.
 */
exports.backfillChatMembers = (0, https_1.onCall)({
    region: REGION,
}, async (request) => {
    var _a, _b;
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "인증이 필요합니다.");
    }
    const callerUid = request.auth.uid;
    const adminDoc = await admin.firestore().doc(`admins/${callerUid}`).get();
    if (!adminDoc.exists) {
        throw new https_1.HttpsError("permission-denied", "관리자만 호출 가능합니다.");
    }
    const data = ((_a = request.data) !== null && _a !== void 0 ? _a : {});
    const { provinceId, cityId, officeId } = data;
    if (!provinceId || !cityId || !officeId) {
        throw new https_1.HttpsError("invalid-argument", "provinceId, cityId, officeId 필수");
    }
    const adminData = (_b = adminDoc.data()) !== null && _b !== void 0 ? _b : {};
    if (adminData.associatedProvinceId !== provinceId ||
        adminData.associatedCityId !== cityId ||
        adminData.associatedOfficeId !== officeId) {
        throw new https_1.HttpsError("permission-denied", "해당 사무실 관리자가 아닙니다.");
    }
    logger.info(`[backfillChatMembers] 시작 by ${callerUid} @ ${officeId}`);
    const officeRef = admin.firestore()
        .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}`);
    const registered = { managers: 0, designated: 0, pickup: 0 };
    // 1. 매니저 — admins 컬렉션에서 office 일치
    const adminsSnapshot = await admin.firestore().collection("admins")
        .where("associatedProvinceId", "==", provinceId)
        .where("associatedCityId", "==", cityId)
        .where("associatedOfficeId", "==", officeId)
        .get();
    for (const doc of adminsSnapshot.docs) {
        await addChatMember(provinceId, cityId, officeId, doc.id, "MANAGER");
        registered.managers++;
    }
    // 2. 대리기사 — designated_drivers, authUid 필드 보유
    const designatedSnapshot = await officeRef.collection("designated_drivers").get();
    for (const doc of designatedSnapshot.docs) {
        const dData = doc.data();
        const authUid = dData.authUid;
        if (authUid) {
            await addChatMember(provinceId, cityId, officeId, authUid, "DESIGNATED_DRIVER");
            registered.designated++;
        }
    }
    // 3. 픽업기사 — pickup_drivers
    const pickupSnapshot = await officeRef.collection("pickup_drivers").get();
    for (const doc of pickupSnapshot.docs) {
        const dData = doc.data();
        const authUid = dData.authUid;
        if (authUid) {
            await addChatMember(provinceId, cityId, officeId, authUid, "PICKUP_DRIVER");
            registered.pickup++;
        }
    }
    logger.info(`[backfillChatMembers] 완료`, registered);
    return { success: true, registered };
});
//# sourceMappingURL=chat.js.map