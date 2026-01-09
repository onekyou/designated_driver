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
exports.processSharedCallPoints = processSharedCallPoints;
exports.initializePoints = initializePoints;
exports.getPointBalance = getPointBalance;
const admin = __importStar(require("firebase-admin"));
const functions = __importStar(require("firebase-functions"));
const logger = functions.logger;
/**
 * 공유콜 완료 시 포인트 분배 처리
 * @param sharedCallData 공유콜 데이터
 * @param provinceId 대상 사무실 도/광역시 ID
 * @param cityId 대상 사무실 시/군/구 ID
 * @param officeId 대상 사무실 ID
 * @param fare 요금
 * @param sourceSharedCallId 원본 공유콜 ID
 * @returns 처리 결과
 */
async function processSharedCallPoints(sharedCallData, provinceId, cityId, officeId, fare, sourceSharedCallId) {
    const pointRatio = 0.1; // 10% 수수료
    const pointAmount = Math.round(fare * pointRatio);
    logger.info(`[points] 포인트 처리 시작. 요금: ${fare}, 포인트: ${pointAmount}, sharedCallId: ${sourceSharedCallId}`);
    await admin.firestore().runTransaction(async (tx) => {
        var _a, _b;
        // ====== 중복 처리 방지 체크 ======
        // 이미 이 공유콜에 대한 포인트 거래가 존재하는지 확인
        const sourceOfficeRef = admin.firestore()
            .collection("provinces").doc(sharedCallData.sourceProvinceId)
            .collection("cities").doc(sharedCallData.sourceCityId)
            .collection("offices").doc(sharedCallData.sourceOfficeId);
        const targetOfficeRef = admin.firestore()
            .collection("provinces").doc(provinceId)
            .collection("cities").doc(cityId)
            .collection("offices").doc(officeId);
        // 이미 처리된 거래가 있는지 확인
        const [sourceExistingTx, targetExistingTx] = await Promise.all([
            tx.get(sourceOfficeRef.collection("point_transactions")
                .where("relatedSharedCallId", "==", sourceSharedCallId)
                .where("type", "==", "SHARED_CALL_RECEIVE")
                .limit(1)),
            tx.get(targetOfficeRef.collection("point_transactions")
                .where("relatedSharedCallId", "==", sourceSharedCallId)
                .where("type", "==", "SHARED_CALL_SEND")
                .limit(1))
        ]);
        if (!sourceExistingTx.empty || !targetExistingTx.empty) {
            logger.warn(`[points] 이미 처리된 공유콜 포인트입니다. SharedCallId: ${sourceSharedCallId}`);
            return; // 이미 처리됨
        }
        logger.info(`[points] 새로운 포인트 처리를 시작합니다. SharedCallId: ${sourceSharedCallId}`);
        // 1) 포인트 잔액 조회 및 업데이트
        const sourcePointsRef = sourceOfficeRef.collection("points").doc("points");
        const targetPointsRef = targetOfficeRef.collection("points").doc("points");
        // 포인트 잔액 읽기
        const [sourceSnap, targetSnap] = await Promise.all([
            tx.get(sourcePointsRef),
            tx.get(targetPointsRef)
        ]);
        const sourceBalance = (((_a = sourceSnap.data()) === null || _a === void 0 ? void 0 : _a.balance) || 0) + pointAmount;
        const targetBalance = (((_b = targetSnap.data()) === null || _b === void 0 ? void 0 : _b.balance) || 0) - pointAmount;
        logger.info(`[points] 잔액 계산 - Source: ${sourceBalance} (+${pointAmount}), Target: ${targetBalance} (-${pointAmount})`);
        // 포인트 문서 업데이트
        tx.set(sourcePointsRef, {
            balance: sourceBalance,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
        tx.set(targetPointsRef, {
            balance: targetBalance,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
        // 2) 포인트 거래 내역 저장 (사무실별 서브컬렉션)
        const timestamp = admin.firestore.FieldValue.serverTimestamp();
        // 원본 사무실 거래 내역 (포인트 받음)
        const sourceTransactionRef = sourceOfficeRef.collection("point_transactions").doc();
        tx.set(sourceTransactionRef, {
            type: "SHARED_CALL_RECEIVE",
            amount: pointAmount,
            description: `공유콜 수수료 수익 (${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"})`,
            timestamp: timestamp,
            createdBy: "system",
            relatedSharedCallId: sourceSharedCallId
        });
        // 대상 사무실 거래 내역 (포인트 차감)
        const targetTransactionRef = targetOfficeRef.collection("point_transactions").doc();
        tx.set(targetTransactionRef, {
            type: "SHARED_CALL_SEND",
            amount: -pointAmount,
            description: `공유콜 수수료 지출 (${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"})`,
            timestamp: timestamp,
            createdBy: "system",
            relatedSharedCallId: sourceSharedCallId
        });
        logger.info(`[points] 포인트 처리 완료. Source: +${pointAmount}, Target: -${pointAmount}`);
    });
}
/**
 * 포인트 초기화 (테스트용)
 */
async function initializePoints(provinceId, cityId, officeId, initialBalance = 0) {
    const pointsRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("points").doc("points");
    await pointsRef.set({
        balance: initialBalance,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    logger.info(`[points] 포인트 초기화 완료. Province: ${provinceId}, City: ${cityId}, Office: ${officeId}, Balance: ${initialBalance}`);
}
/**
 * 포인트 잔액 조회
 */
async function getPointBalance(provinceId, cityId, officeId) {
    var _a;
    const pointsRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("points").doc("points");
    const snapshot = await pointsRef.get();
    return ((_a = snapshot.data()) === null || _a === void 0 ? void 0 : _a.balance) || 0;
}
//# sourceMappingURL=points.js.map