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
exports.aggregateMonthlyStats = void 0;
const scheduler_1 = require("firebase-functions/v2/scheduler");
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
const logger = __importStar(require("firebase-functions/logger"));
/**
 * 매월 1일 00:00 KST에 지난달 acceptanceEvents를 집계하여 monthlyStats/{yearMonth} 문서에 저장.
 * R1_LOCKSCREEN 발동 기준 자동 평가 (Android/iOS 각 100건 이상 + 5%p 이상 차이 시 logger.warn).
 */
exports.aggregateMonthlyStats = (0, scheduler_1.onSchedule)({
    schedule: "0 0 1 * *",
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
}, async () => {
    const db = admin.firestore();
    const now = new Date();
    const lastMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const thisMonthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const yearMonth = `${lastMonthStart.getFullYear()}-${String(lastMonthStart.getMonth() + 1).padStart(2, "0")}`;
    const eventsSnap = await db.collection("acceptanceEvents")
        .where("createdAt", ">=", firestore_1.Timestamp.fromDate(lastMonthStart))
        .where("createdAt", "<", firestore_1.Timestamp.fromDate(thisMonthStart))
        .get();
    const byPlatform = {
        android: { accepted: 0, rejected: 0, timeout: 0, totalLatencyMs: 0 },
        ios: { accepted: 0, rejected: 0, timeout: 0, totalLatencyMs: 0 },
    };
    const byOffice = {};
    eventsSnap.forEach(doc => {
        var _a;
        const data = doc.data();
        const p = (data.platform === "ios" ? "ios" : "android");
        const o = data.outcome;
        byPlatform[p][o]++;
        if (o === "accepted") {
            byPlatform[p].totalLatencyMs += (_a = data.latencyMs) !== null && _a !== void 0 ? _a : 0;
        }
        const office = data.officeId;
        if (!byOffice[office]) {
            byOffice[office] = { accepted: 0, rejected: 0, timeout: 0 };
        }
        byOffice[office][o]++;
    });
    await db.doc(`monthlyStats/${yearMonth}`).set({
        yearMonth,
        total: eventsSnap.size,
        byPlatform,
        byOffice,
        computedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    // R1_LOCKSCREEN 발동 기준 자동 평가
    const androidTotal = byPlatform.android.accepted + byPlatform.android.rejected + byPlatform.android.timeout;
    const iosTotal = byPlatform.ios.accepted + byPlatform.ios.rejected + byPlatform.ios.timeout;
    if (androidTotal >= 100 && iosTotal >= 100) {
        const androidRate = byPlatform.android.accepted / androidTotal;
        const iosRate = byPlatform.ios.accepted / iosTotal;
        const diffPp = (androidRate - iosRate) * 100;
        if (diffPp >= 5) {
            logger.warn(`[R1_LOCKSCREEN_TRIGGER] iOS 수락률 ${iosRate.toFixed(3)} vs Android ${androidRate.toFixed(3)}, ${diffPp.toFixed(1)}pp 차이`);
        }
        else {
            logger.info(`[MonthlyStats ${yearMonth}] 수락률 차이 ${diffPp.toFixed(1)}pp (R1 미발동)`);
        }
    }
    else {
        logger.info(`[MonthlyStats ${yearMonth}] 표본 부족 (Android ${androidTotal} / iOS ${iosTotal}) - R1 평가 스킵`);
    }
});
//# sourceMappingURL=aggregateMonthly.js.map