/**
 * 주간 시뮬레이션 실행
 * 데이터 초기화 → 시드 → 7일 실행 → 주간 검증 → 비용 리포트
 *
 * 사용법:
 *   npx ts-node test/weekly-sim/run-week.ts --week 1
 */

import { clearEmulatorData, db } from "../setup/emulator-config";
import { FieldValue } from "../setup/emulator-config";
import { OFFICES } from "../setup/constants";
import { getWeekSchedule, DayConfig } from "./config/weekly-schedule";
import { OFFICE_CONFIGS } from "./config/offices";
import { runDay, DayResult } from "./run-day";
import {
  getGrandTotals,
  printCostReport,
  resetAllMeters,
  meterWrite,
} from "./metering/meter";

// ==========================================
// 시드 데이터 주입
// ==========================================

async function seedData(): Promise<void> {
  console.log("  시드 데이터 주입 중...");
  const batch = db.batch();

  batch.set(db.doc("provinces/test_province"), { name: "테스트도" });
  batch.set(db.doc("provinces/test_province/cities/test_city"), { name: "테스트시" });

  for (let oi = 0; oi < 10; oi++) {
    const x = String(oi + 1).padStart(2, "0");
    const p = `provinces/test_province/cities/test_city/offices/office_${x}`;

    batch.set(db.doc(p), {
      name: `사무실${x}`,
      depositRatio: 60,
      assignedTimeoutMinutes: 3,
      provinceId: "test_province",
      cityId: "test_city",
      officeId: `office_${x}`,
      status: "OPEN",
    });

    for (let di = 0; di < 5; di++) {
      const y = String(di + 1).padStart(2, "0");
      const id = `driver_${x}_${y}`;
      batch.set(db.doc(`${p}/designated_drivers/${id}`), {
        name: `기사${x}-${y}`,
        phoneNumber: `010-${x}00-${y}000`,
        fcmToken: `t_${id}`,
        status: "WAITING",
        authUid: `auth_${id}`,
        isLoggedIn: true,
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    batch.set(db.doc(`${p}/points/points`), { balance: 100000 });
    batch.set(db.doc(`${p}/managerTokens/admin_office_${x}`), { token: `mt_${x}` });
    batch.set(db.doc(`admins/admin_office_${x}`), {
      associatedProvinceId: "test_province",
      associatedCityId: "test_city",
      associatedOfficeId: `office_${x}`,
      role: "ADMIN",
      fcmToken: `at_${x}`,
    });
  }

  await batch.commit();
  meterWrite(92); // 시드 문서 수

  // CF 트리거 안정화 대기
  console.log("  시드 CF 안정화 대기 (10초)...");
  await sleep(10000);
}

// ==========================================
// 주간 실행
// ==========================================

async function runWeek(weekNumber: number): Promise<void> {
  const schedule = getWeekSchedule(weekNumber);

  if (schedule.length === 0) {
    console.error(`Week ${weekNumber} 스케줄이 없습니다.`);
    process.exit(1);
  }

  console.log(`\n${"#".repeat(60)}`);
  console.log(`  Week ${weekNumber} 시뮬레이션 시작`);
  console.log(`  ${schedule.length}일, 예상 콜: ~${estimateWeekCalls(schedule)}건`);
  console.log(`${"#".repeat(60)}`);

  resetAllMeters();

  // 매일 데이터 초기화 + 시드 후 실행 (CF workDate가 항상 오늘이므로 일별 독립 실행)
  const dayResults: DayResult[] = [];

  for (const dayConfig of schedule) {
    console.log(`\n--- Day ${dayConfig.dayNumber} 데이터 초기화 + 시드 ---`);
    await clearEmulatorData();
    await seedData();

    const result = await runDay(dayConfig);
    dayResults.push(result);
  }

  // ==========================================
  // 주간 결과 요약
  // ==========================================

  const totalCalls = dayResults.reduce((a, d) => a + d.totalCalls, 0);
  const totalSuccess = dayResults.reduce((a, d) => a + d.successCalls, 0);
  const totalFailed = dayResults.reduce((a, d) => a + d.failedCalls, 0);
  const totalShared = dayResults.reduce((a, d) => a + d.sharedCalls.created, 0);
  const totalDuration = dayResults.reduce((a, d) => a + d.durationSec, 0);
  const settlementMatch = dayResults.every((d) => d.settlement.allMatched);

  console.log(`\n${"#".repeat(60)}`);
  console.log(`  Week ${weekNumber} 결과 요약`);
  console.log(`${"#".repeat(60)}`);
  console.log(`\n  일별 결과:`);

  for (const d of dayResults) {
    const status = d.failedCalls === 0 && d.settlement.allMatched ? "PASS" : "FAIL";
    console.log(
      `  Day ${String(d.dayNumber).padStart(2)}: ${status} ` +
      `| 콜 ${d.successCalls}/${d.totalCalls} ` +
      `| 공유 ${d.sharedCalls.created} ` +
      `| 정산 ${d.settlement.allMatched ? "OK" : "FAIL"} ` +
      `| ${d.durationSec.toFixed(1)}s`
    );
  }

  console.log(`\n  총 콜: ${totalSuccess}/${totalCalls} 성공 (실패: ${totalFailed})`);
  console.log(`  공유콜: ${totalShared}건`);
  console.log(`  정산 정합성: ${settlementMatch ? "ALL PASS" : "FAIL"}`);
  console.log(`  소요 시간: ${totalDuration.toFixed(1)}s (${(totalDuration / 60).toFixed(1)}분)`);

  // 에러 요약
  const allErrors = dayResults.flatMap((d) => d.errors);
  if (allErrors.length > 0) {
    console.log(`\n  ⚠ 에러 ${allErrors.length}건:`);
    for (const e of allErrors.slice(0, 10)) {
      console.log(`    - ${e}`);
    }
  }

  // ==========================================
  // 비용 리포트
  // ==========================================

  const grandTotals = getGrandTotals();
  printCostReport(grandTotals, `Week ${weekNumber} (${totalCalls}콜, ${schedule.length}일)`);

  // 종료 코드
  process.exit(totalFailed > 0 ? 1 : 0);
}

// ==========================================
// 유틸
// ==========================================

function estimateWeekCalls(schedule: DayConfig[]): number {
  let total = 0;
  for (const day of schedule) {
    for (const oc of OFFICE_CONFIGS) {
      total += Math.round(oc.baseDailyCalls * day.multiplier);
    }
    total += day.sharedCallTarget;
  }
  return total;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ==========================================
// CLI 실행
// ==========================================

const args = process.argv.slice(2);
let weekNum = 1;

const weekIdx = args.indexOf("--week");
if (weekIdx !== -1 && args[weekIdx + 1]) {
  weekNum = parseInt(args[weekIdx + 1], 10);
}

if (weekNum < 1 || weekNum > 4) {
  console.error("사용법: npx ts-node test/weekly-sim/run-week.ts --week [1-4]");
  process.exit(1);
}

runWeek(weekNum);
