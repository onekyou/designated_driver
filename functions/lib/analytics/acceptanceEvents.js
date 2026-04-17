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
exports.recordAcceptanceEvent = recordAcceptanceEvent;
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
const db = () => admin.firestore();
/**
 * acceptanceEvents 컬렉션에 이벤트 기록.
 * onCallStatusChanged (ASSIGNED→ACCEPTED/REJECTED), checkAssignedTimeout (3분 타임아웃)에서 호출.
 *
 * driver 문서의 platform 필드가 없으면 "android"로 fallback (backfill-platform.js 실행 전 호환).
 */
async function recordAcceptanceEvent(input) {
    var _a;
    const driverRef = db()
        .collection("provinces").doc(input.provinceId)
        .collection("cities").doc(input.cityId)
        .collection("offices").doc(input.officeId)
        .collection("designated_drivers").doc(input.assignedDriverId);
    let platform = "android";
    try {
        const driverSnap = await driverRef.get();
        const driverData = driverSnap.data();
        const raw = driverData === null || driverData === void 0 ? void 0 : driverData.platform;
        if (raw === "ios")
            platform = "ios";
    }
    catch (_b) {
        // fallback
    }
    const outcomeAt = firestore_1.Timestamp.now();
    const latencyMs = input.outcome === "accepted"
        ? outcomeAt.toMillis() - input.assignedAt.toMillis()
        : 0;
    await db().collection("acceptanceEvents").add({
        callId: input.callId,
        assignedDriverId: input.assignedDriverId,
        platform,
        officeId: input.officeId,
        provinceId: input.provinceId,
        cityId: input.cityId,
        assignedAt: input.assignedAt,
        outcome: input.outcome,
        outcomeAt,
        latencyMs,
        rejectReason: (_a = input.rejectReason) !== null && _a !== void 0 ? _a : null,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    });
}
//# sourceMappingURL=acceptanceEvents.js.map