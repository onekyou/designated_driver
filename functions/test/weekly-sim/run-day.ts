/**
 * 하루 시뮬레이션 실행
 * 1. 콜 생성 + 실행
 * 2. 공유콜 플로우
 * 3. 정산 대기 + 검증
 * 4. 미터링 기록
 */

import { DayConfig, DAY_NAMES } from "./config/weekly-schedule";
import { getTodayWorkDate } from "../setup/constants";
import { OFFICE_CONFIGS } from "./config/offices";
import { planDayCalls, executeDayCalls } from "./generators/call-generator";
import { executeSharedCallFlow } from "./generators/shared-call-flow";
import { auditAllSettlements } from "./generators/settlement-audit";
import {
  startDayMeter,
  endDayMeter,
  DailyMeter,
} from "./metering/meter";

export interface DayResult {
  dayNumber: number;
  workDate: string;
  description: string;
  totalCalls: number;
  successCalls: number;
  failedCalls: number;
  sharedCalls: { created: number; claimed: number; contentions: number };
  settlement: { totalCalls: number; totalFare: number; allMatched: boolean };
  meter: DailyMeter;
  errors: string[];
  durationSec: number;
}

export async function runDay(dayConfig: DayConfig): Promise<DayResult> {
  // CF의 getTodayWorkDate()와 동일한 workDate 사용 (실제 현재 시각 기준)
  const workDate = getTodayWorkDate();
  const dayLabel = `Day ${dayConfig.dayNumber} (W${dayConfig.week} ${DAY_NAMES[dayConfig.dayInWeek]})`;

  console.log(`\n${"=".repeat(60)}`);
  console.log(`  ${dayLabel}: ${dayConfig.description}`);
  console.log(`  근무일: ${workDate} / 배율: ${dayConfig.multiplier}×`);
  console.log(`${"=".repeat(60)}`);

  startDayMeter();
  const startTime = Date.now();
  const allErrors: string[] = [];

  // ---- 1. 콜 계획 생성 ----
  const calls = planDayCalls(dayConfig, OFFICE_CONFIGS);
  console.log(`  [1/3] 콜 계획: ${calls.length}건 (10사무실)`);

  // 사무실별 콜 수 요약
  const callsByOffice = new Map<number, number>();
  const completedByOffice = new Map<number, number>();

  for (const call of calls) {
    const idx = call.officeConfig.officeIndex;
    callsByOffice.set(idx, (callsByOffice.get(idx) ?? 0) + 1);
    // 취소가 아닌 콜만 정산 대상
    if (!call.scenario.startsWith("CANCEL")) {
      completedByOffice.set(idx, (completedByOffice.get(idx) ?? 0) + 1);
    }
  }

  const summary = OFFICE_CONFIGS.map((oc) =>
    `${oc.office.officeId}:${callsByOffice.get(oc.officeIndex) ?? 0}`
  ).join(", ");
  console.log(`    ${summary}`);

  // ---- 2. 콜 실행 ----
  console.log(`  [2/3] 콜 실행 중...`);
  const callResult = await executeDayCalls(calls, { logInterval: 100 });

  if (callResult.failed > 0) {
    console.log(`    ⚠ 실패 ${callResult.failed}건`);
    allErrors.push(...callResult.errors.slice(0, 5)); // 최대 5개만 기록
  }

  // ---- 2.5 공유콜 플로우 ----
  let sharedResult = { created: 0, claimed: 0, contentions: 0, errors: [] as string[] };
  if (dayConfig.sharedCallTarget > 0) {
    console.log(`    공유콜 ${dayConfig.sharedCallTarget}건 처리 중...`);
    sharedResult = await executeSharedCallFlow(dayConfig);
    console.log(`    공유콜: 생성 ${sharedResult.created}, 수임 ${sharedResult.claimed}, 경합 ${sharedResult.contentions}`);
    if (sharedResult.errors.length > 0) {
      allErrors.push(...sharedResult.errors.slice(0, 3));
    }
  }

  // ---- 3. 정산 사후 대조 (콜 데이터 직접 읽기 → 수학 검증) ----
  console.log(`  [3/3] 정산 대조 (COMPLETED 콜 직접 조회)...`);

  // callId 프리픽스로 현재 Day의 일반 콜만 필터 (이전 Day 잔여 + 공유콜 복사본 분리)
  const auditResult = await auditAllSettlements(completedByOffice, dayConfig.dayNumber);

  if (auditResult.allPass) {
    const sharedInfo = auditResult.totalSharedCopies > 0
      ? ` + 공유콜 ${auditResult.totalSharedCopies}건`
      : "";
    console.log(`    정산 PASS: ${auditResult.totalCalls}건, ${auditResult.totalFare.toLocaleString()}원${sharedInfo}`);
  } else {
    const failures = auditResult.results.filter((r) => !r.allPass);
    for (const f of failures) {
      console.log(`    ⚠ ${f.officeName}: ${f.details}`);
    }
  }

  // ---- 결과 기록 ----
  const durationSec = (Date.now() - startTime) / 1000;
  const meter = endDayMeter(dayConfig.dayNumber, workDate, dayConfig.description);

  console.log(`\n  Day ${dayConfig.dayNumber} 완료: ${durationSec.toFixed(1)}s`);
  console.log(`  콜: ${callResult.success}/${callResult.total} 성공`);
  console.log(`  미터: R:${meter.reads} W:${meter.writes} CF:${meter.cfInvocations}`);

  return {
    dayNumber: dayConfig.dayNumber,
    workDate,
    description: dayConfig.description,
    totalCalls: callResult.total,
    successCalls: callResult.success,
    failedCalls: callResult.failed,
    sharedCalls: {
      created: sharedResult.created,
      claimed: sharedResult.claimed,
      contentions: sharedResult.contentions,
    },
    settlement: {
      totalCalls: auditResult.totalCalls,
      totalFare: auditResult.totalFare,
      allMatched: auditResult.allPass,
    },
    meter,
    errors: allErrors,
    durationSec,
  };
}
