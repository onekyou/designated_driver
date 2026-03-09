/**
 * 시나리오 7: 정산 마감
 * 콜 완료 후 정산 세션 자동 생성 → 마감 처리 → 마감 후 콜 추가 불가 확인
 *
 * 검증:
 * - 정산 세션 자동 생성 + totals 계산
 * - depositRatio 적용 (60%)
 * - 마감(isFinalized=true) 후 세션 상태 확인
 */

import { initTest, printResult } from "../setup/emulator-config";
import { db, FieldValue, Timestamp } from "../setup/emulator-config";
import { OFFICES, getDriversForOffice, getTodayWorkDate, officePath } from "../setup/constants";
import {
  createCall,
  assignCall,
  acceptCall,
  startDriving,
  completeCall,
  finalizeTrip,
  getDoc,
} from "../helpers/firestore-helpers";
import { waitForSettlementCall, waitForCondition } from "../helpers/wait-for-trigger";
import {
  assertSettlementSession,
} from "../helpers/assertions";

const office = OFFICES[5]; // 사무실 06
const drivers = getDriversForOffice(5);

export async function runScenario07(): Promise<boolean> {
  await initTest("시나리오 7: 정산 마감");
  const startTime = Date.now();
  let allPassed = true;

  try {
    const workDate = getTodayWorkDate();

    // ---- Test 1: 콜 3건 완료 → 정산 세션 자동 생성 ----
    console.log("  [1/4] 콜 3건 완료 → 정산 세션 생성...");
    const t1 = Date.now();

    const fares = [20000, 30000, 15000];

    for (let i = 0; i < 3; i++) {
      const callId = `stl_s07_${String(i + 1).padStart(3, "0")}`;
      const driver = drivers[i];

      await createCall(office, callId, {
        phoneNumber: `010-7000-${String(i + 1).padStart(4, "0")}`,
        customerName: `정산고객${i + 1}`,
        fare: fares[i],
      });
      await assignCall(office, callId, driver);
      await acceptCall(office, callId, driver);
      await startDriving(office, callId, { fare: fares[i] });
      await completeCall(office, callId);
      await finalizeTrip(office, callId, driver, {
        fare: fares[i],
        paymentMethod: i === 0 ? "현금" : i === 1 ? "카드" : "현금",
        cashReceived: i === 1 ? 0 : fares[i],
        creditAmount: i === 1 ? fares[i] : 0,
      });
    }

    // CF 트리거 대기 (3건 정산)
    const lastCallAdded = await waitForCondition(
      async () => {
        const doc = await db.doc(`${officePath(office)}/settlementSessions/${workDate}`).get();
        return (doc.data()?.totals?.callCount ?? 0) >= 3;
      },
      { timeout: 60000, description: "정산 3건 완료 대기" }
    );

    if (lastCallAdded) {
      printResult("콜 3건 정산 세션 생성", true, Date.now() - t1);
    } else {
      printResult("콜 3건 정산 세션 생성", false, Date.now() - t1, "타임아웃");
      allPassed = false;
    }

    // ---- Test 2: 정산 totals 검증 (depositRatio 60%) ----
    console.log("  [2/4] 정산 totals 검증 (depositRatio 60%)...");
    const t2 = Date.now();

    const session = await getDoc(`${officePath(office)}/settlementSessions/${workDate}`);
    const totalFare = fares.reduce((a, b) => a + b, 0); // 65000
    const totalDeposit = Math.floor(totalFare * 60 / 100); // 39000
    const totalDriverShare = totalFare - totalDeposit; // 26000

    const checks = [
      { name: "callCount", actual: session?.totals?.callCount, expected: 3 },
      { name: "totalFare", actual: session?.totals?.totalFare, expected: totalFare },
      { name: "totalDeposit", actual: session?.totals?.totalDeposit, expected: totalDeposit },
      { name: "totalDriverShare", actual: session?.totals?.totalDriverShare, expected: totalDriverShare },
      { name: "totalCash", actual: session?.totals?.totalCash, expected: 20000 + 15000 }, // 현금 2건
      { name: "totalCard", actual: session?.totals?.totalCard, expected: 30000 }, // 카드 1건
      { name: "isFinalized", actual: session?.metadata?.isFinalized, expected: false },
    ];

    const failures = checks.filter(c => c.actual !== c.expected);

    if (failures.length === 0) {
      printResult("정산 totals (depositRatio 60%)", true, Date.now() - t2,
        `총요금:${totalFare}, 입금:${totalDeposit}, 기사몫:${totalDriverShare}`);
    } else {
      printResult("정산 totals", false, Date.now() - t2,
        failures.map(f => `${f.name}: ${f.actual} (expected ${f.expected})`).join(", "));
      allPassed = false;
    }

    // ---- Test 3: 수동 마감 처리 ----
    console.log("  [3/4] 수동 마감 처리...");
    const t3 = Date.now();

    await db.doc(`${officePath(office)}/settlementSessions/${workDate}`).update({
      "metadata.isFinalized": true,
      "metadata.lastUpdatedBy": "test_manual_finalize",
      "metadata.lastUpdatedAt": Timestamp.now(),
    });

    const finalizedSession = await getDoc(`${officePath(office)}/settlementSessions/${workDate}`);

    if (finalizedSession?.metadata?.isFinalized === true) {
      printResult("마감 처리", true, Date.now() - t3);
    } else {
      printResult("마감 처리", false, Date.now() - t3, "isFinalized가 true가 아님");
      allPassed = false;
    }

    // ---- Test 4: 마감 후 콜 추가 시도 → CF가 무시해야 함 ----
    console.log("  [4/4] 마감 후 콜 추가 → 무시 확인...");
    const t4 = Date.now();

    const callId4 = "stl_s07_004_after_finalize";
    const driver4 = drivers[3];

    await createCall(office, callId4, {
      phoneNumber: "010-7000-0099",
      customerName: "마감후고객",
      fare: 25000,
    });
    await assignCall(office, callId4, driver4);
    await acceptCall(office, callId4, driver4);
    await startDriving(office, callId4, { fare: 25000 });
    await completeCall(office, callId4);
    await finalizeTrip(office, callId4, driver4, {
      fare: 25000,
      paymentMethod: "현금",
      cashReceived: 25000,
    });

    // CF 처리 대기
    await waitForCondition(async () => true, { timeout: 5000, description: "마감 후 CF 대기" });

    // 정산 세션 callCount가 여전히 3인지 확인
    const afterSession = await getDoc(`${officePath(office)}/settlementSessions/${workDate}`);
    const afterCallCount = afterSession?.totals?.callCount ?? 0;

    if (afterCallCount === 3) {
      printResult("마감 후 콜 추가 차단", true, Date.now() - t4);
    } else {
      printResult("마감 후 콜 추가 차단", false, Date.now() - t4,
        `callCount: ${afterCallCount} (expected 3, 마감 후 추가 차단 실패)`);
      allPassed = false;
    }

  } catch (error: any) {
    printResult("시나리오 7", false, Date.now() - startTime, error.message);
    allPassed = false;
  }

  const totalTime = Date.now() - startTime;
  console.log(`\n  시나리오 7 결과: ${allPassed ? "PASS ✓" : "FAIL ✗"} (${(totalTime / 1000).toFixed(1)}s)\n`);
  return allPassed;
}

if (require.main === module) {
  runScenario07().then((passed) => {
    process.exit(passed ? 0 : 1);
  });
}
