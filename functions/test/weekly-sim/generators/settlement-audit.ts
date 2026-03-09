/**
 * 정산 사후 대조 (Post-hoc Settlement Audit)
 * CF 트리거 대기 없이 Firestore의 COMPLETED 콜 데이터를 직접 읽어 정산 수학을 검증
 *
 * 필터링 전략:
 * - 일반 콜: d{day}_o{office}_{seq} → completedByOffice로 검증
 * - 공유콜 복사본: shared_sc_d{day}_{seq} → 별도 카운트 (run-day에서 sharedResult로 이미 검증)
 * - 이전 Day CF 잔여 쓰기: callId 프리픽스로 자동 제외
 */

import { db } from "../../setup/emulator-config";
import { officePath } from "../../setup/constants";
import { OfficeConfig, OFFICE_CONFIGS } from "../config/offices";
import { meterRead } from "../metering/meter";

export interface AuditResult {
  officeName: string;
  officeId: string;
  completedCalls: number;
  expectedCalls: number;
  sharedCopyCalls: number;
  totalFare: number;
  totalDeposit: number;
  totalDriverShare: number;
  depositRatioUsed: number;
  mathCorrect: boolean;
  callCountMatch: boolean;
  allPass: boolean;
  details?: string;
}

/**
 * 사무실 1개의 COMPLETED 콜을 직접 읽어 정산 수학 대조
 */
async function auditOffice(
  oc: OfficeConfig,
  expectedCallCount: number,
  dayNumber: number
): Promise<AuditResult> {
  const officeNum = String(oc.officeIndex + 1).padStart(2, "0");
  const regularPrefix = `d${dayNumber}_o${officeNum}_`;

  const callsRef = db.collection(`${officePath(oc.office)}/calls`);
  const completedSnap = await callsRef.where("status", "==", "COMPLETED").get();
  meterRead(completedSnap.size || 1);

  // 현재 Day의 일반 콜만 필터 (공유콜 복사본 제외)
  const regularDocs = completedSnap.docs.filter((doc) => doc.id.startsWith(regularPrefix));
  // 공유콜 복사본 카운트 (참고용)
  const sharedDocs = completedSnap.docs.filter((doc) => doc.id.startsWith("shared_"));

  const completedCalls = regularDocs.length;
  let totalFare = 0;

  for (const doc of regularDocs) {
    const data = doc.data();
    const fare = data.fare_final || data.fare_set || data.fare || 0;
    totalFare += fare;
  }

  // 공유콜 복사본 요금도 합산 (전체 정산에 포함)
  let sharedFare = 0;
  for (const doc of sharedDocs) {
    const data = doc.data();
    sharedFare += data.fare_final || data.fare_set || data.fare || 0;
  }

  const allFare = totalFare + sharedFare;
  const depositRatio = oc.office.depositRatio;
  const totalDeposit = Math.floor(allFare * depositRatio / 100);
  const totalDriverShare = allFare - totalDeposit;

  // 검증: deposit + driverShare = totalFare
  const mathCorrect = (totalDeposit + totalDriverShare) === allFare;
  // 일반 콜 수만 비교 (공유콜은 별도 검증)
  const callCountMatch = completedCalls === expectedCallCount;

  const issues: string[] = [];
  if (!mathCorrect) {
    issues.push(`수학 오류: deposit(${totalDeposit}) + driver(${totalDriverShare}) != fare(${allFare})`);
  }
  if (!callCountMatch) {
    issues.push(`콜 수: expected ${expectedCallCount}, got ${completedCalls}`);
  }

  return {
    officeName: oc.office.name,
    officeId: oc.office.officeId,
    completedCalls,
    expectedCalls: expectedCallCount,
    sharedCopyCalls: sharedDocs.length,
    totalFare: allFare,
    totalDeposit,
    totalDriverShare,
    depositRatioUsed: depositRatio,
    mathCorrect,
    callCountMatch,
    allPass: mathCorrect && callCountMatch,
    details: issues.length > 0 ? issues.join("; ") : undefined,
  };
}

/**
 * 전 사무실 정산 사후 대조
 * @param expectedCounts 사무실별 예상 COMPLETED 일반 콜 수 (취소 제외)
 * @param dayNumber 현재 Day 번호 (callId 필터링용)
 */
export async function auditAllSettlements(
  expectedCounts: Map<number, number>,
  dayNumber: number
): Promise<{
  results: AuditResult[];
  allPass: boolean;
  totalCalls: number;
  totalFare: number;
  totalSharedCopies: number;
}> {
  const promises = OFFICE_CONFIGS.map((oc) => {
    const expected = expectedCounts.get(oc.officeIndex) ?? 0;
    return auditOffice(oc, expected, dayNumber);
  });

  const results = await Promise.all(promises);

  const totalCalls = results.reduce((a, r) => a + r.completedCalls, 0);
  const totalFare = results.reduce((a, r) => a + r.totalFare, 0);
  const totalSharedCopies = results.reduce((a, r) => a + r.sharedCopyCalls, 0);
  const allPass = results.every((r) => r.allPass);

  return { results, allPass, totalCalls, totalFare, totalSharedCopies };
}
