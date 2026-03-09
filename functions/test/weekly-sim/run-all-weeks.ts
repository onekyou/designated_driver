/**
 * 4주 전체 시뮬레이션 실행
 * 주 단위로 데이터 초기화 + 시드 → 7일 실행 → 비용 리포트
 *
 * 사용법:
 *   npx ts-node test/weekly-sim/run-all-weeks.ts
 *   npx ts-node test/weekly-sim/run-all-weeks.ts --from 2  (2주차부터)
 */

import { clearEmulatorData, db } from "../setup/emulator-config";
import { FieldValue } from "../setup/emulator-config";
import { getWeekSchedule } from "./config/weekly-schedule";
import { OFFICE_CONFIGS } from "./config/offices";
import { runDay, DayResult } from "./run-day";
import {
  getGrandTotals,
  getAllDailyMeters,
  printCostReport,
  resetAllMeters,
  meterWrite,
  MeterData,
} from "./metering/meter";

// ==========================================
// 시드 데이터
// ==========================================

async function seedData(): Promise<void> {
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
  meterWrite(92);

  console.log("  시드 CF 안정화 대기 (10초)...");
  await sleep(10000);
}

// ==========================================
// 메인 실행
// ==========================================

interface WeekSummary {
  week: number;
  totalCalls: number;
  successCalls: number;
  failedCalls: number;
  sharedCalls: number;
  settlementAllPass: boolean;
  durationSec: number;
}

async function runAllWeeks(fromWeek: number = 1): Promise<void> {
  const globalStart = Date.now();

  console.log(`\n${"█".repeat(60)}`);
  console.log(`  4주 대규모 시뮬레이션`);
  console.log(`  10사무실 × 50기사 × 28일 × ~530콜/일 ≈ 15,000콜`);
  console.log(`  시작 주차: Week ${fromWeek}`);
  console.log(`${"█".repeat(60)}`);

  const weekSummaries: WeekSummary[] = [];
  const allDayResults: DayResult[] = [];

  for (let week = fromWeek; week <= 4; week++) {
    const schedule = getWeekSchedule(week);

    console.log(`\n${"#".repeat(60)}`);
    console.log(`  Week ${week}/4 시작`);
    console.log(`${"#".repeat(60)}`);

    const weekDayResults: DayResult[] = [];

    for (const dayConfig of schedule) {
      // 매일 데이터 초기화 + 시드 (CF workDate가 항상 오늘이므로 일별 독립 실행)
      console.log(`\n--- Day ${dayConfig.dayNumber} 데이터 초기화 + 시드 ---`);
      await clearEmulatorData();
      await seedData();

      const result = await runDay(dayConfig);
      weekDayResults.push(result);
      allDayResults.push(result);
    }

    // 주간 요약
    const ws: WeekSummary = {
      week,
      totalCalls: weekDayResults.reduce((a, d) => a + d.totalCalls, 0),
      successCalls: weekDayResults.reduce((a, d) => a + d.successCalls, 0),
      failedCalls: weekDayResults.reduce((a, d) => a + d.failedCalls, 0),
      sharedCalls: weekDayResults.reduce((a, d) => a + d.sharedCalls.created, 0),
      settlementAllPass: weekDayResults.every((d) => d.settlement.allMatched),
      durationSec: weekDayResults.reduce((a, d) => a + d.durationSec, 0),
    };
    weekSummaries.push(ws);

    console.log(`\n  Week ${week} 완료: ${ws.successCalls}/${ws.totalCalls} 콜, ${ws.durationSec.toFixed(0)}s`);
  }

  // ==========================================
  // 최종 종합 결과
  // ==========================================

  const totalDuration = (Date.now() - globalStart) / 1000;

  console.log(`\n${"█".repeat(60)}`);
  console.log(`  4주 시뮬레이션 최종 결과`);
  console.log(`${"█".repeat(60)}`);

  console.log(`\n  주별 요약:`);
  console.log(`  ${"─".repeat(56)}`);
  console.log(`  주   콜(성공/전체)     공유콜   정산     소요시간`);
  console.log(`  ${"─".repeat(56)}`);

  for (const ws of weekSummaries) {
    console.log(
      `  W${ws.week}  ${String(ws.successCalls).padStart(5)}/${String(ws.totalCalls).padStart(5)}  ` +
      `   ${String(ws.sharedCalls).padStart(4)}건  ` +
      `${ws.settlementAllPass ? "PASS" : "FAIL"}  ` +
      `  ${(ws.durationSec / 60).toFixed(1)}분`
    );
  }

  const grandTotal = weekSummaries.reduce((a, w) => ({
    calls: a.calls + w.totalCalls,
    success: a.success + w.successCalls,
    failed: a.failed + w.failedCalls,
    shared: a.shared + w.sharedCalls,
  }), { calls: 0, success: 0, failed: 0, shared: 0 });

  console.log(`  ${"─".repeat(56)}`);
  console.log(
    `  합계 ${String(grandTotal.success).padStart(5)}/${String(grandTotal.calls).padStart(5)}  ` +
    `   ${String(grandTotal.shared).padStart(4)}건  ` +
    `${weekSummaries.every((w) => w.settlementAllPass) ? "PASS" : "FAIL"}  ` +
    `  ${(totalDuration / 60).toFixed(1)}분`
  );

  // ==========================================
  // 비용 리포트
  // ==========================================

  const grandMeter = getGrandTotals();
  printCostReport(grandMeter, `4주 전체 (${grandTotal.calls}콜, 28일)`);

  // 일별 상세 미터
  console.log(`\n  === 일별 Firestore 연산 상세 ===`);
  console.log(`  Day  설명                    읽기      쓰기      CF호출    콜수`);
  console.log(`  ${"─".repeat(70)}`);

  for (const dm of getAllDailyMeters()) {
    console.log(
      `  ${String(dm.dayNumber).padStart(3)}  ${dm.description.padEnd(22)} ` +
      `${String(dm.reads).padStart(8)} ${String(dm.writes).padStart(8)} ` +
      `${String(dm.cfInvocations).padStart(8)} ${String(dm.callsProcessed).padStart(6)}`
    );
  }

  console.log(`\n  총 소요 시간: ${(totalDuration / 60).toFixed(1)}분 (${(totalDuration / 3600).toFixed(2)}시간)`);
  console.log(`${"█".repeat(60)}\n`);

  process.exit(grandTotal.failed > 0 ? 1 : 0);
}

// ==========================================
// 유틸
// ==========================================

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ==========================================
// CLI
// ==========================================

const args = process.argv.slice(2);
let fromWeek = 1;

const fromIdx = args.indexOf("--from");
if (fromIdx !== -1 && args[fromIdx + 1]) {
  fromWeek = parseInt(args[fromIdx + 1], 10);
}

if (fromWeek < 1 || fromWeek > 4) {
  console.error("사용법: npx ts-node test/weekly-sim/run-all-weeks.ts [--from 1-4]");
  process.exit(1);
}

runAllWeeks(fromWeek);
