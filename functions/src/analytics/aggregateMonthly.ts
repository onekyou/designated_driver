import { onSchedule } from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";
import { Timestamp, FieldValue } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";

type PlatformOutcomeBucket = {
  accepted: number;
  rejected: number;
  timeout: number;
  totalLatencyMs: number;
};

type OfficeOutcomeBucket = {
  accepted: number;
  rejected: number;
  timeout: number;
};

/**
 * 매월 1일 00:00 KST에 지난달 acceptanceEvents를 집계하여 monthlyStats/{yearMonth} 문서에 저장.
 * R1_LOCKSCREEN 발동 기준 자동 평가 (Android/iOS 각 100건 이상 + 5%p 이상 차이 시 logger.warn).
 */
export const aggregateMonthlyStats = onSchedule(
  {
    schedule: "0 0 1 * *",
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
  },
  async () => {
    const db = admin.firestore();
    const now = new Date();
    const lastMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const thisMonthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const yearMonth = `${lastMonthStart.getFullYear()}-${String(lastMonthStart.getMonth() + 1).padStart(2, "0")}`;

    const eventsSnap = await db.collection("acceptanceEvents")
      .where("createdAt", ">=", Timestamp.fromDate(lastMonthStart))
      .where("createdAt", "<", Timestamp.fromDate(thisMonthStart))
      .get();

    const byPlatform: { android: PlatformOutcomeBucket; ios: PlatformOutcomeBucket } = {
      android: { accepted: 0, rejected: 0, timeout: 0, totalLatencyMs: 0 },
      ios: { accepted: 0, rejected: 0, timeout: 0, totalLatencyMs: 0 },
    };
    const byOffice: Record<string, OfficeOutcomeBucket> = {};

    eventsSnap.forEach(doc => {
      const data = doc.data();
      const p = (data.platform === "ios" ? "ios" : "android") as "android" | "ios";
      const o = data.outcome as "accepted" | "rejected" | "timeout";
      byPlatform[p][o]++;
      if (o === "accepted") {
        byPlatform[p].totalLatencyMs += data.latencyMs ?? 0;
      }

      const office = data.officeId as string;
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
      computedAt: FieldValue.serverTimestamp(),
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
      } else {
        logger.info(`[MonthlyStats ${yearMonth}] 수락률 차이 ${diffPp.toFixed(1)}pp (R1 미발동)`);
      }
    } else {
      logger.info(`[MonthlyStats ${yearMonth}] 표본 부족 (Android ${androidTotal} / iOS ${iosTotal}) - R1 평가 스킵`);
    }
  }
);
