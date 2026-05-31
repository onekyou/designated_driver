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
exports.migrateExistingOfficesWallet = void 0;
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
const https_1 = require("firebase-functions/v2/https");
const logger = __importStar(require("firebase-functions/logger"));
const REGION = "asia-northeast3";
const SIGNUP_BONUS = 30000;
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
 * migrateExistingOfficesWallet (admin only callable, 1회)
 * 기존 사무실에 SIGNUP_BONUS 30,000 적립 (없으면 wallet 신규 생성, 있으면 += 30,000)
 * 멱등성: signup_bonus_${officeId} 트랜잭션 doc ID로 중복 방지
 *
 * input: { dryRun?: boolean }
 * output: { migrated, skipped, errors, details }
 */
exports.migrateExistingOfficesWallet = (0, https_1.onCall)({ region: REGION, timeoutSeconds: 540 }, async (request) => {
    var _a, _b;
    if (!(await isHeadManager((_a = request.auth) === null || _a === void 0 ? void 0 : _a.uid))) {
        throw new https_1.HttpsError("permission-denied", "총관리자만 마이그레이션 실행 가능합니다.");
    }
    const dryRun = !!((_b = request.data) === null || _b === void 0 ? void 0 : _b.dryRun);
    const officesSnap = await admin.firestore().collectionGroup("offices").get();
    logger.info(`[migrate] 사무실 ${officesSnap.size}개 발견 (dryRun: ${dryRun})`);
    let migrated = 0;
    let skipped = 0;
    const errors = [];
    const details = [];
    for (const officeDoc of officesSnap.docs) {
        const officeRef = officeDoc.ref;
        const officeId = officeDoc.id;
        const pointsRef = officeRef.collection("points").doc("points");
        const txId = `signup_bonus_${officeId}`;
        const txRef = officeRef.collection("point_transactions").doc(txId);
        try {
            const result = await admin.firestore().runTransaction(async (tx) => {
                var _a;
                const existingTx = await tx.get(txRef);
                if (existingTx.exists) {
                    return { action: "skipped (already migrated)", before: 0, after: 0 };
                }
                const pointsSnap = await tx.get(pointsRef);
                const before = ((_a = pointsSnap.data()) === null || _a === void 0 ? void 0 : _a.balance) || 0;
                const after = before + SIGNUP_BONUS;
                if (!dryRun) {
                    tx.set(pointsRef, {
                        balance: after,
                        updatedAt: firestore_1.FieldValue.serverTimestamp(),
                    }, { merge: true });
                    tx.set(txRef, {
                        type: "SIGNUP_BONUS",
                        amount: SIGNUP_BONUS,
                        balanceAfter: after,
                        description: "마이그레이션 가입 보너스",
                        status: "COMPLETED",
                        timestamp: firestore_1.FieldValue.serverTimestamp(),
                        createdBy: request.auth.uid,
                    });
                }
                return { action: dryRun ? "would migrate" : "migrated", before, after };
            });
            details.push(Object.assign({ officeId }, result));
            if (result.action.startsWith("skipped")) {
                skipped++;
            }
            else {
                migrated++;
            }
        }
        catch (e) {
            errors.push({ officeId, error: e.message });
            logger.error(`[migrate] 사무실 ${officeId} 마이그레이션 실패`, e);
        }
    }
    logger.info(`[migrate] 완료 - migrated: ${migrated}, skipped: ${skipped}, errors: ${errors.length}`);
    return { migrated, skipped, errors, details };
});
//# sourceMappingURL=migrateExistingOfficesWallet.js.map