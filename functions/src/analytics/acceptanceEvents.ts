import * as admin from "firebase-admin";
import { Timestamp, FieldValue } from "firebase-admin/firestore";

const db = () => admin.firestore();

export interface RecordEventInput {
  callId: string;
  assignedDriverId: string;
  provinceId: string;
  cityId: string;
  officeId: string;
  outcome: "accepted" | "rejected" | "timeout";
  assignedAt: Timestamp;
  rejectReason?: string;
}

/**
 * acceptanceEvents 컬렉션에 이벤트 기록.
 * onCallStatusChanged (ASSIGNED→ACCEPTED/REJECTED), checkAssignedTimeout (3분 타임아웃)에서 호출.
 *
 * driver 문서의 platform 필드가 없으면 "android"로 fallback (backfill-platform.js 실행 전 호환).
 */
export async function recordAcceptanceEvent(input: RecordEventInput): Promise<void> {
  const driverRef = db()
    .collection("provinces").doc(input.provinceId)
    .collection("cities").doc(input.cityId)
    .collection("offices").doc(input.officeId)
    .collection("designated_drivers").doc(input.assignedDriverId);

  let platform: "android" | "ios" = "android";
  try {
    const driverSnap = await driverRef.get();
    const driverData = driverSnap.data();
    const raw = driverData?.platform;
    if (raw === "ios") platform = "ios";
  } catch {
    // fallback
  }

  const outcomeAt = Timestamp.now();
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
    rejectReason: input.rejectReason ?? null,
    createdAt: FieldValue.serverTimestamp(),
  });
}
