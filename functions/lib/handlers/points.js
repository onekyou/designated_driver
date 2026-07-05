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
exports.processCustomerPointsOnComplete = processCustomerPointsOnComplete;
exports.refundCustomerPointsOnCancel = refundCustomerPointsOnCancel;
exports.getCustomerPointBalance = getCustomerPointBalance;
exports.processRestaurantCallPayout = processRestaurantCallPayout;
exports.checkOfficeWalletForClaim = checkOfficeWalletForClaim;
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
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
    var _a;
    // 공유콜 수수료율 = 중앙 config(system_config/commission.sharedCallRatio). 콘솔 운영(rules write:false).
    // 프로모션 시 콜마당이 중앙 조정. 사무실별 아님(B가 A에 내는 값이라 플랫폼 소관). 없거나 범위밖이면 0.1.
    let pointRatio = 0.1; // 기본 10% 수수료
    try {
        const cfgSnap = await admin.firestore().collection("system_config").doc("commission").get();
        const r = (_a = cfgSnap.data()) === null || _a === void 0 ? void 0 : _a.sharedCallRatio;
        if (typeof r === "number" && r > 0 && r <= 1) {
            pointRatio = r;
        }
    }
    catch (e) {
        logger.warn(`[points] commission config 읽기 실패 — 기본 0.1 사용`, e);
    }
    const pointAmount = Math.round(fare * pointRatio);
    logger.info(`[points] 포인트 처리 시작. 요금: ${fare}, 요율: ${pointRatio}, 포인트: ${pointAmount}, sharedCallId: ${sourceSharedCallId}`);
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
        // 멱등성 키 기반 중복 처리 방지: 문서 ID로 존재 여부 확인
        const sourceTransactionRef = sourceOfficeRef.collection("point_transactions")
            .doc(`shared_receive_${sourceSharedCallId}`);
        const targetTransactionRef = targetOfficeRef.collection("point_transactions")
            .doc(`shared_send_${sourceSharedCallId}`);
        const [sourceExistingTx, targetExistingTx] = await Promise.all([
            tx.get(sourceTransactionRef),
            tx.get(targetTransactionRef)
        ]);
        if (sourceExistingTx.exists || targetExistingTx.exists) {
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
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
        tx.set(targetPointsRef, {
            balance: targetBalance,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
        // 2) 포인트 거래 내역 저장 (사무실별 서브컬렉션, 멱등성 키 문서 ID 사용)
        const timestamp = firestore_1.FieldValue.serverTimestamp();
        // 원본 사무실 거래 내역 (포인트 받음)
        tx.set(sourceTransactionRef, {
            type: "SHARED_CALL_RECEIVE",
            amount: pointAmount,
            description: `공유콜 수수료 수익 (${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"})`,
            timestamp: timestamp,
            createdBy: "system",
            relatedSharedCallId: sourceSharedCallId
        });
        // 대상 사무실 거래 내역 (포인트 차감)
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
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
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
// ============================================================
// 고객 포인트 적립 관련 함수
// ============================================================
/**
 * 고객 등급별 포인트 적립률
 */
const GRADE_POINT_RATES = {
    "BRONZE": 0.03, // 3%
    "SILVER": 0.05, // 5%
    "GOLD": 0.07, // 7%
    "VIP": 0.09, // 9%
};
/**
 * 등급 승급 기준 (총 이용 횟수)
 */
const GRADE_THRESHOLDS = {
    "BRONZE": 0,
    "SILVER": 10,
    "GOLD": 30,
    "VIP": 50,
};
/**
 * 총 이용 횟수에 따른 등급 결정
 */
function calculateGrade(totalCalls) {
    if (totalCalls >= GRADE_THRESHOLDS.VIP)
        return "VIP";
    if (totalCalls >= GRADE_THRESHOLDS.GOLD)
        return "GOLD";
    if (totalCalls >= GRADE_THRESHOLDS.SILVER)
        return "SILVER";
    return "BRONZE";
}
/**
 * 등급에 따른 포인트 적립률 반환
 */
function getPointRate(grade) {
    return GRADE_POINT_RATES[grade.toUpperCase()] || GRADE_POINT_RATES.BRONZE;
}
/**
 * 운행 완료 시 고객 포인트 적립 처리
 *
 * @param provinceId 사무실 도/광역시 ID
 * @param cityId 사무실 시/군/구 ID
 * @param officeId 사무실 ID
 * @param callId 콜 ID
 * @param phoneNumber 고객 전화번호
 * @param fare 운행 요금
 * @param customerName 고객 이름 (옵션)
 * @returns 포인트 적립 결과
 */
async function processCustomerPointsOnComplete(provinceId, cityId, officeId, callId, phoneNumber, fare, customerName) {
    logger.info(`[customerPoints] 고객 포인트 적립 시작. Phone: ${phoneNumber}, Fare: ${fare}, CallId: ${callId}`);
    // 요금이 0 이하면 적립하지 않음
    if (!fare || fare <= 0) {
        logger.warn(`[customerPoints] 요금이 0 이하입니다. 포인트 적립 스킵. Fare: ${fare}`);
        return {
            success: false,
            pointsEarned: 0,
            newBalance: 0,
            grade: "BRONZE",
            gradeUpgraded: false,
            error: "요금이 0 이하입니다."
        };
    }
    // 전화번호 정규화 (하이픈 제거)
    const normalizedPhone = phoneNumber.replace(/-/g, "");
    try {
        const result = await admin.firestore().runTransaction(async (tx) => {
            const officeRef = admin.firestore()
                .collection("provinces").doc(provinceId)
                .collection("cities").doc(cityId)
                .collection("offices").doc(officeId);
            // 고객 포인트 문서 참조
            const customerPointsRef = officeRef.collection("customerPoints").doc(normalizedPhone);
            // 기존 고객 포인트 데이터 조회
            const customerPointsSnap = await tx.get(customerPointsRef);
            const existingData = customerPointsSnap.data();
            // 중복 처리 방지: 멱등성 키 기반 문서 ID로 존재 여부 확인
            const earnTransactionRef = officeRef
                .collection("customerPointTransactions")
                .doc(`earn_${normalizedPhone}_${callId}`);
            const existingTxSnap = await tx.get(earnTransactionRef);
            if (existingTxSnap.exists) {
                logger.warn(`[customerPoints] 이미 적립된 콜입니다. CallId: ${callId}, Phone: ${normalizedPhone}`);
                return {
                    success: false,
                    pointsEarned: 0,
                    newBalance: (existingData === null || existingData === void 0 ? void 0 : existingData.currentPoints) || 0,
                    grade: (existingData === null || existingData === void 0 ? void 0 : existingData.grade) || "BRONZE",
                    gradeUpgraded: false,
                    error: "이미 적립된 콜입니다."
                };
            }
            // 현재 데이터 가져오기
            const currentPoints = (existingData === null || existingData === void 0 ? void 0 : existingData.currentPoints) || 0;
            const totalEarned = (existingData === null || existingData === void 0 ? void 0 : existingData.totalEarned) || 0;
            const totalUsed = (existingData === null || existingData === void 0 ? void 0 : existingData.totalUsed) || 0;
            const totalCalls = (existingData === null || existingData === void 0 ? void 0 : existingData.totalCalls) || 0;
            const previousGrade = (existingData === null || existingData === void 0 ? void 0 : existingData.grade) || "BRONZE";
            // 새로운 총 이용 횟수 (이번 콜 포함)
            const newTotalCalls = totalCalls + 1;
            // 새 등급 계산
            const newGrade = calculateGrade(newTotalCalls);
            const gradeUpgraded = newGrade !== previousGrade;
            // 포인트 적립률 (새 등급 기준)
            const pointRate = getPointRate(newGrade);
            const pointsEarned = Math.floor(fare * pointRate);
            // 새 잔액 계산
            const newBalance = currentPoints + pointsEarned;
            const newTotalEarned = totalEarned + pointsEarned;
            logger.info(`[customerPoints] 계산 완료. 등급: ${previousGrade} → ${newGrade}, 적립률: ${pointRate * 100}%, 적립: ${pointsEarned}P, 잔액: ${newBalance}P`);
            // 1) 고객 포인트 문서 업데이트
            const timestamp = firestore_1.FieldValue.serverTimestamp();
            tx.set(customerPointsRef, {
                phoneNumber: normalizedPhone,
                customerName: customerName || (existingData === null || existingData === void 0 ? void 0 : existingData.customerName) || "",
                currentPoints: newBalance,
                totalEarned: newTotalEarned,
                totalUsed: totalUsed,
                totalCalls: newTotalCalls,
                grade: newGrade,
                lastUpdated: timestamp,
                createdAt: (existingData === null || existingData === void 0 ? void 0 : existingData.createdAt) || timestamp,
            }, { merge: true });
            // 2) 포인트 거래 내역 저장 (멱등성 키 문서 ID 사용)
            tx.set(earnTransactionRef, {
                phoneNumber: normalizedPhone,
                customerName: customerName || "",
                type: "EARN",
                amount: pointsEarned,
                balance: newBalance,
                description: `운행 완료 포인트 적립 (${newGrade} ${pointRate * 100}%)`,
                callId: callId,
                fare: fare,
                grade: newGrade,
                timestamp: timestamp,
                createdBy: "system"
            });
            // 3) 등급 업그레이드 시 별도 기록
            if (gradeUpgraded) {
                const gradeUpgradeRef = officeRef.collection("customerPointTransactions").doc();
                tx.set(gradeUpgradeRef, {
                    phoneNumber: normalizedPhone,
                    customerName: customerName || "",
                    type: "GRADE_UPGRADE",
                    amount: 0,
                    balance: newBalance,
                    description: `등급 승급: ${previousGrade} → ${newGrade}`,
                    callId: callId,
                    previousGrade: previousGrade,
                    newGrade: newGrade,
                    totalCalls: newTotalCalls,
                    timestamp: timestamp,
                    createdBy: "system"
                });
                logger.info(`[customerPoints] 등급 승급! ${previousGrade} → ${newGrade}, 총 이용: ${newTotalCalls}회`);
            }
            return {
                success: true,
                pointsEarned: pointsEarned,
                newBalance: newBalance,
                grade: newGrade,
                gradeUpgraded: gradeUpgraded,
                previousGrade: gradeUpgraded ? previousGrade : undefined,
            };
        });
        logger.info(`[customerPoints] 포인트 적립 완료. Phone: ${normalizedPhone}, Earned: ${result.pointsEarned}P, Balance: ${result.newBalance}P`);
        return result;
    }
    catch (error) {
        logger.error(`[customerPoints] 포인트 적립 실패. Phone: ${phoneNumber}, Error: ${error.message}`);
        return {
            success: false,
            pointsEarned: 0,
            newBalance: 0,
            grade: "BRONZE",
            gradeUpgraded: false,
            error: error.message
        };
    }
}
async function refundCustomerPointsOnCancel(provinceId, cityId, officeId, callId, phoneNumber, pointsUsed) {
    if (!pointsUsed || pointsUsed <= 0) {
        return { success: false, refunded: 0, newBalance: 0, error: "환불할 포인트 없음" };
    }
    const normalizedPhone = phoneNumber.replace(/-/g, "");
    try {
        const result = await admin.firestore().runTransaction(async (tx) => {
            const officeRef = admin.firestore()
                .collection("provinces").doc(provinceId)
                .collection("cities").doc(cityId)
                .collection("offices").doc(officeId);
            // 멱등성 키: 이중 환불 방지
            const refundTransactionRef = officeRef
                .collection("customerPointTransactions")
                .doc(`refund_${normalizedPhone}_${callId}`);
            const existingRefund = await tx.get(refundTransactionRef);
            if (existingRefund.exists) {
                logger.warn(`[customerPoints] 이미 환불된 콜: ${callId}, Phone: ${normalizedPhone}`);
                return { success: false, refunded: 0, newBalance: 0, error: "이미 환불 처리됨" };
            }
            // 고객 포인트 문서 조회
            const customerPointsRef = officeRef.collection("customerPoints").doc(normalizedPhone);
            const customerPointsSnap = await tx.get(customerPointsRef);
            const existingData = customerPointsSnap.data();
            const currentPoints = (existingData === null || existingData === void 0 ? void 0 : existingData.currentPoints) || 0;
            const totalUsed = (existingData === null || existingData === void 0 ? void 0 : existingData.totalUsed) || 0;
            const newBalance = currentPoints + pointsUsed;
            const newTotalUsed = Math.max(0, totalUsed - pointsUsed);
            const timestamp = firestore_1.FieldValue.serverTimestamp();
            // 포인트 잔액 복구
            tx.set(customerPointsRef, {
                currentPoints: newBalance,
                totalUsed: newTotalUsed,
                lastUpdated: timestamp,
            }, { merge: true });
            // 환불 거래 내역 저장
            tx.set(refundTransactionRef, {
                phoneNumber: normalizedPhone,
                type: "REFUND",
                amount: pointsUsed,
                balance: newBalance,
                description: `콜 취소 포인트 환불`,
                callId: callId,
                timestamp: timestamp,
                createdBy: "system"
            });
            return { success: true, refunded: pointsUsed, newBalance: newBalance };
        });
        if (result.success) {
            logger.info(`[customerPoints] 포인트 환불 완료. Phone: ${normalizedPhone}, Refunded: ${pointsUsed}P, Balance: ${result.newBalance}P`);
        }
        return result;
    }
    catch (error) {
        logger.error(`[customerPoints] 포인트 환불 실패. Phone: ${phoneNumber}, Error: ${error.message}`);
        return { success: false, refunded: 0, newBalance: 0, error: error.message };
    }
}
/**
 * 고객 포인트 잔액 조회
 */
async function getCustomerPointBalance(provinceId, cityId, officeId, phoneNumber) {
    const normalizedPhone = phoneNumber.replace(/-/g, "");
    const customerPointsRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("customerPoints").doc(normalizedPhone);
    const snapshot = await customerPointsRef.get();
    const data = snapshot.data();
    return {
        balance: (data === null || data === void 0 ? void 0 : data.currentPoints) || 0,
        grade: (data === null || data === void 0 ? void 0 : data.grade) || "BRONZE",
        totalCalls: (data === null || data === void 0 ? void 0 : data.totalCalls) || 0,
    };
}
// ============================================================
// 식당앱 콜 정산 (P0 — 식당 ↔ 사무실)
// ============================================================
// 사용자 결정 #4: P0는 PLATFORM 모드 (모든 사무실 동일 broadcast)
// 사용자 결정 #5/12: 결제 방식 무관 식당 +1,000 적립
// 사용자 결정 #10: 운행 완료 시 차감 + 마이너스 허용
// 식당앱 콜은 source 사무실 분배 SKIP — 기존 processSharedCallPoints와 분리
const RESTAURANT_CALL_BONUS = 1000;
const OFFICE_CHARGE_RATIO = 0.1;
/**
 * 식당앱 콜 운행 완료 시 정산 처리
 * - CASH: 사무실 wallet -fare×10% + 식당 +1,000
 * - RESTAURANT_POINT: 식당 -fare + 사무실 wallet +fare + 식당 +1,000
 * 멱등성 키: restaurant_call_${sourceSharedCallId}
 */
async function processRestaurantCallPayout(sharedCallData, targetProvinceId, targetCityId, targetOfficeId, fare, sourceSharedCallId) {
    const paymentMethod = sharedCallData.paymentMethod || "CASH";
    const restaurantId = sharedCallData.sourceRestaurantId;
    if (!restaurantId) {
        logger.warn(`[restaurant_payout] sourceRestaurantId 없음 - skip. sharedCallId: ${sourceSharedCallId}`);
        return;
    }
    logger.info(`[restaurant_payout] 시작 - method: ${paymentMethod}, fare: ${fare}, restaurantId: ${restaurantId}, sharedCallId: ${sourceSharedCallId}`);
    await admin.firestore().runTransaction(async (tx) => {
        var _a, _b;
        const targetOfficeRef = admin.firestore()
            .collection("provinces").doc(targetProvinceId)
            .collection("cities").doc(targetCityId)
            .collection("offices").doc(targetOfficeId);
        const sourceOfficeRef = admin.firestore()
            .collection("provinces").doc(sharedCallData.sourceProvinceId)
            .collection("cities").doc(sharedCallData.sourceCityId)
            .collection("offices").doc(sharedCallData.sourceOfficeId);
        const restaurantRef = sourceOfficeRef.collection("restaurants").doc(restaurantId);
        const officeTxRef = targetOfficeRef.collection("point_transactions").doc(`restaurant_call_${sourceSharedCallId}`);
        const restTxRef = restaurantRef.collection("transactions").doc(`call_${sourceSharedCallId}`);
        // 멱등성 체크
        const [existingOfficeTx, existingRestTx] = await Promise.all([
            tx.get(officeTxRef),
            tx.get(restTxRef),
        ]);
        if (existingOfficeTx.exists || existingRestTx.exists) {
            logger.warn(`[restaurant_payout] 이미 처리된 콜 - sharedCallId: ${sourceSharedCallId}`);
            return;
        }
        const officePointsRef = targetOfficeRef.collection("points").doc("points");
        const [officePointsSnap, restSnap] = await Promise.all([
            tx.get(officePointsRef),
            tx.get(restaurantRef),
        ]);
        const officeCurrent = ((_a = officePointsSnap.data()) === null || _a === void 0 ? void 0 : _a.balance) || 0;
        const restCurrent = ((_b = restSnap.data()) === null || _b === void 0 ? void 0 : _b.points) || 0;
        let officeDelta = 0;
        let restDelta = RESTAURANT_CALL_BONUS;
        let officeTxType = "";
        let officeTxDesc = "";
        let restTxType = "";
        let restTxDesc = "";
        if (paymentMethod === "RESTAURANT_POINT") {
            // 식당이 fare 결제 → 사무실 wallet +fare, 식당 -fare + 적립 +1,000 (마이너스 허용)
            const restPaymentAmount = -fare;
            officeDelta = fare;
            restDelta = restPaymentAmount + RESTAURANT_CALL_BONUS;
            officeTxType = "RESTAURANT_PAYMENT_RECEIVED";
            officeTxDesc = `식당 포인트 결제 입금 (${sharedCallData.contactName || "식당"}, +${fare})`;
            restTxType = "PAYMENT_AND_EARN";
            restTxDesc = `식당 포인트 결제 (-${fare}) + 콜 적립 (+${RESTAURANT_CALL_BONUS})`;
        }
        else {
            // CASH: 사무실 -fare×10%, 식당 +1,000
            const charge = Math.round(fare * OFFICE_CHARGE_RATIO);
            officeDelta = -charge;
            restDelta = RESTAURANT_CALL_BONUS;
            officeTxType = "RESTAURANT_CALL_CHARGE";
            officeTxDesc = `식당앱 콜 수수료 (${sharedCallData.contactName || "식당"}, -${charge})`;
            restTxType = "EARN_FROM_CALL";
            restTxDesc = `콜 적립 (+${RESTAURANT_CALL_BONUS})`;
        }
        const officeNext = officeCurrent + officeDelta;
        const restNext = restCurrent + restDelta;
        tx.set(officePointsRef, {
            balance: officeNext,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
        tx.set(officeTxRef, {
            type: officeTxType,
            amount: officeDelta,
            balanceAfter: officeNext,
            description: officeTxDesc,
            callId: sourceSharedCallId,
            paymentMethod,
            timestamp: firestore_1.FieldValue.serverTimestamp(),
            createdBy: "system",
        });
        tx.update(restaurantRef, { points: restNext });
        tx.set(restTxRef, {
            type: restTxType,
            amount: restDelta,
            pointsAfter: restNext,
            description: restTxDesc,
            callId: sourceSharedCallId,
            paymentMethod,
            timestamp: firestore_1.FieldValue.serverTimestamp(),
            createdBy: "system",
        });
        logger.info(`[restaurant_payout] 완료 - office: ${officeDelta} (${officeCurrent}→${officeNext}), restaurant: ${restDelta} (${restCurrent}→${restNext})`);
    });
}
/**
 * 사무실 wallet 잔액 검증 (claim 가능 여부)
 * 결정 #11: 잔액 < 5,000이면 claim 거절
 */
async function checkOfficeWalletForClaim(provinceId, cityId, officeId, threshold = 5000) {
    var _a;
    const pointsRef = admin.firestore()
        .collection("provinces").doc(provinceId)
        .collection("cities").doc(cityId)
        .collection("offices").doc(officeId)
        .collection("points").doc("points");
    const snap = await pointsRef.get();
    const balance = ((_a = snap.data()) === null || _a === void 0 ? void 0 : _a.balance) || 0;
    return { allowed: balance >= threshold, balance };
}
//# sourceMappingURL=points.js.map