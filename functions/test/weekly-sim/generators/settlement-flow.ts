/**
 * 정산/마감 플로우
 * 일일 마감 시 정산 세션 검증
 */

import { db, FieldValue, Timestamp } from "../../setup/emulator-config";
import { OfficeConfig, OFFICE_CONFIGS } from "../config/offices";
import { officePath } from "../../setup/constants";
import { meterRead, meterWrite, meterSettlementSession } from "../metering/meter";
import { waitForCondition } from "../../helpers/wait-for-trigger";

export interface SettlementResult {
  officeName: string;
  callCount: number;
  totalFare: number;
  totalDeposit: number;
  totalDriverShare: number;
  totalCash: number;
  totalCard: number;
  isFinalized: boolean;
  matched: boolean;
  mismatchDetails?: string;
}

/**
 * 정산 세션 대기 + 검증 (CF가 자동 생성)
 */
export async function verifySettlementSession(
  officeConfig: OfficeConfig,
  workDate: string,
  expectedCallCount: number,
  timeout: number = 60000
): Promise<SettlementResult> {
  const path = `${officePath(officeConfig.office)}/settlementSessions/${workDate}`;

  // CF가 정산 세션을 업데이트할 때까지 대기
  const ready = await waitForCondition(
    async () => {
      const doc = await db.doc(path).get();
      meterRead(1);
      if (!doc.exists) return false;
      const data = doc.data();
      return (data?.totals?.callCount ?? 0) >= expectedCallCount;
    },
    { timeout, interval: 2000, description: `${officeConfig.office.officeId} 정산 대기 (${expectedCallCount}건)` }
  );

  // 정산 세션 읽기
  const doc = await db.doc(path).get();
  meterRead(1);
  const data = doc.data();

  if (!ready || !data) {
    return {
      officeName: officeConfig.office.name,
      callCount: data?.totals?.callCount ?? 0,
      totalFare: 0,
      totalDeposit: 0,
      totalDriverShare: 0,
      totalCash: 0,
      totalCard: 0,
      isFinalized: false,
      matched: false,
      mismatchDetails: `타임아웃 (expected ${expectedCallCount}, got ${data?.totals?.callCount ?? 0})`,
    };
  }

  const totals = data.totals || {};
  const depositRatio = officeConfig.office.depositRatio;
  const expectedDeposit = Math.floor(totals.totalFare * depositRatio / 100);
  const expectedDriverShare = totals.totalFare - expectedDeposit;

  // 정합성 검증
  const checks: string[] = [];

  if (totals.totalDeposit !== undefined && totals.totalDeposit !== expectedDeposit) {
    checks.push(`deposit: ${totals.totalDeposit} (expected ${expectedDeposit})`);
  }
  if (totals.totalDriverShare !== undefined && totals.totalDriverShare !== expectedDriverShare) {
    checks.push(`driverShare: ${totals.totalDriverShare} (expected ${expectedDriverShare})`);
  }

  meterSettlementSession();

  return {
    officeName: officeConfig.office.name,
    callCount: totals.callCount ?? 0,
    totalFare: totals.totalFare ?? 0,
    totalDeposit: totals.totalDeposit ?? 0,
    totalDriverShare: totals.totalDriverShare ?? 0,
    totalCash: totals.totalCash ?? 0,
    totalCard: totals.totalCard ?? 0,
    isFinalized: data.metadata?.isFinalized ?? false,
    matched: checks.length === 0,
    mismatchDetails: checks.length > 0 ? checks.join("; ") : undefined,
  };
}

/**
 * 사무실 마감 처리 (isFinalized = true)
 */
export async function finalizeOffice(
  officeConfig: OfficeConfig,
  workDate: string
): Promise<void> {
  const path = `${officePath(officeConfig.office)}/settlementSessions/${workDate}`;

  await db.doc(path).update({
    "metadata.isFinalized": true,
    "metadata.lastUpdatedBy": "sim_finalize",
    "metadata.lastUpdatedAt": Timestamp.now(),
  });
  meterWrite(1);
}

/**
 * 전 사무실 일일 정산 검증
 */
export async function verifyAllSettlements(
  workDate: string,
  expectedCounts: Map<number, number>,
  timeout: number = 300000
): Promise<{
  results: SettlementResult[];
  allMatched: boolean;
  totalCalls: number;
  totalFare: number;
}> {
  // 10사무실 병렬 검증 (순차 → 병렬로 변경)
  const promises: Promise<{ oc: OfficeConfig; result: SettlementResult }>[] = [];

  for (const oc of OFFICE_CONFIGS) {
    const expected = expectedCounts.get(oc.officeIndex) ?? 0;
    if (expected === 0) continue;

    promises.push(
      verifySettlementSession(oc, workDate, expected, timeout)
        .then((result) => ({ oc, result }))
    );
  }

  const settled = await Promise.all(promises);

  const results: SettlementResult[] = [];
  let totalCalls = 0;
  let totalFare = 0;

  for (const { result } of settled) {
    results.push(result);
    totalCalls += result.callCount;
    totalFare += result.totalFare;
  }

  const allMatched = results.every((r) => r.matched);

  return { results, allMatched, totalCalls, totalFare };
}
