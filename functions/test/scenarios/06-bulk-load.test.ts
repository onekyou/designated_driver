/**
 * 시나리오 6: 대량 부하 테스트
 * 10개 사무실에 각 5건씩 총 50건 콜 생성 → 배차 → 완료 → 정산 정합성 확인
 * (에뮬레이터 부하를 고려하여 500건 대신 50건으로 축소)
 *
 * 검증:
 * - 대량 콜 처리 시 데이터 유실 없음
 * - 정산 세션 callCount/totalFare 정합성
 * - 기사 상태 전부 WAITING 복구
 */

import { initTest, printResult } from "../setup/emulator-config";
import { db } from "../setup/emulator-config";
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
import { waitForCondition } from "../helpers/wait-for-trigger";
import { assertDriverStatus } from "../helpers/assertions";

const CALLS_PER_OFFICE = 5;
const TOTAL_OFFICES = 10;
const FARE = 20000;

export async function runScenario06(): Promise<boolean> {
  await initTest("시나리오 6: 대량 부하 (50건)");
  const startTime = Date.now();
  let allPassed = true;

  try {
    // ---- Test 1: 대량 콜 생성 + 완료 ----
    console.log(`  [1/3] ${TOTAL_OFFICES}개 사무실 × ${CALLS_PER_OFFICE}건 콜 생성 + 완료...`);
    const t1 = Date.now();
    let completedCount = 0;

    for (let oi = 0; oi < TOTAL_OFFICES; oi++) {
      const office = OFFICES[oi];
      const drivers = getDriversForOffice(oi);

      for (let ci = 0; ci < CALLS_PER_OFFICE; ci++) {
        const callId = `bulk_${String(oi + 1).padStart(2, "0")}_${String(ci + 1).padStart(3, "0")}`;
        const driver = drivers[ci % drivers.length];

        await createCall(office, callId, {
          phoneNumber: `010-6${String(oi).padStart(2, "0")}0-${String(ci).padStart(4, "0")}`,
          customerName: `대량고객${oi}-${ci}`,
          fare: FARE,
        });

        await assignCall(office, callId, driver);
        await acceptCall(office, callId, driver);
        await startDriving(office, callId, {
          departure: `출발${oi}-${ci}`,
          destination: `도착${oi}-${ci}`,
          fare: FARE,
        });
        await completeCall(office, callId);
        await finalizeTrip(office, callId, driver, {
          fare: FARE,
          paymentMethod: "현금",
          cashReceived: FARE,
        });

        completedCount++;
      }
    }

    const totalExpected = TOTAL_OFFICES * CALLS_PER_OFFICE;
    if (completedCount === totalExpected) {
      printResult(`콜 ${completedCount}건 완료`, true, Date.now() - t1);
    } else {
      printResult(`콜 완료`, false, Date.now() - t1,
        `완료: ${completedCount}/${totalExpected}`);
      allPassed = false;
    }

    // ---- Test 2: 정산 세션 정합성 확인 ----
    console.log("  [2/3] 정산 세션 정합성 검증 (CF 대기 후)...");
    const t2 = Date.now();
    const workDate = getTodayWorkDate();

    // CF 트리거 대기 (50건 콜 완료 → 50개 CF 발동)
    await waitForCondition(
      async () => {
        // 마지막 사무실의 정산 세션에 마지막 콜이 있는지 확인
        const lastOffice = OFFICES[TOTAL_OFFICES - 1];
        const doc = await db.doc(`${officePath(lastOffice)}/settlementSessions/${workDate}`).get();
        if (!doc.exists) return false;
        const data = doc.data();
        return (data?.totals?.callCount ?? 0) >= CALLS_PER_OFFICE;
      },
      { timeout: 120000, interval: 2000, description: "전체 정산 세션 완료 대기" }
    );

    let totalCalls = 0;
    let totalFare = 0;
    let mismatches: string[] = [];

    for (let oi = 0; oi < TOTAL_OFFICES; oi++) {
      const office = OFFICES[oi];
      const session = await getDoc(`${officePath(office)}/settlementSessions/${workDate}`);

      if (!session) {
        mismatches.push(`사무실${String(oi + 1).padStart(2, "0")}: 정산 세션 없음`);
        continue;
      }

      const callCount = session.totals?.callCount ?? 0;
      const fare = session.totals?.totalFare ?? 0;

      totalCalls += callCount;
      totalFare += fare;

      if (callCount !== CALLS_PER_OFFICE) {
        mismatches.push(`사무실${String(oi + 1).padStart(2, "0")}: callCount=${callCount} (expected ${CALLS_PER_OFFICE})`);
      }
      if (fare !== FARE * CALLS_PER_OFFICE) {
        mismatches.push(`사무실${String(oi + 1).padStart(2, "0")}: totalFare=${fare} (expected ${FARE * CALLS_PER_OFFICE})`);
      }
    }

    if (mismatches.length === 0) {
      printResult(`정산 정합성 (${totalCalls}건, ${totalFare}원)`, true, Date.now() - t2);
    } else {
      printResult("정산 정합성", false, Date.now() - t2,
        mismatches.join("; "));
      allPassed = false;
    }

    // ---- Test 3: 기사 상태 전부 WAITING 복구 ----
    console.log("  [3/3] 기사 상태 전부 WAITING 복구 확인...");
    const t3 = Date.now();
    let nonWaiting: string[] = [];

    for (let oi = 0; oi < TOTAL_OFFICES; oi++) {
      const office = OFFICES[oi];
      const drivers = getDriversForOffice(oi);

      for (const driver of drivers) {
        const doc = await getDoc(`${officePath(office)}/designated_drivers/${driver.driverId}`);
        if (doc?.status !== "WAITING") {
          nonWaiting.push(`${driver.driverId}: ${doc?.status}`);
        }
      }
    }

    if (nonWaiting.length === 0) {
      printResult("기사 50명 전부 WAITING", true, Date.now() - t3);
    } else {
      printResult("기사 상태 복구", false, Date.now() - t3,
        `WAITING 아닌 기사: ${nonWaiting.join(", ")}`);
      allPassed = false;
    }

  } catch (error: any) {
    printResult("시나리오 6", false, Date.now() - startTime, error.message);
    allPassed = false;
  }

  const totalTime = Date.now() - startTime;
  console.log(`\n  시나리오 6 결과: ${allPassed ? "PASS ✓" : "FAIL ✗"} (${(totalTime / 1000).toFixed(1)}s)\n`);
  return allPassed;
}

if (require.main === module) {
  runScenario06().then((passed) => {
    process.exit(passed ? 0 : 1);
  });
}
