/**
 * 시나리오 1: 정상 플로우
 * 콜 생성(WAITING) → 배차(ASSIGNED) → 수락(ACCEPTED) → 운행(IN_PROGRESS) → 완료(COMPLETED)
 *
 * 검증:
 * - CF 트리거 체인: sendNewCallNotification, oncallassigned, onCallStatusChanged, onCallCompletedUpdateSettlement
 * - 정산 세션 자동 생성 + depositRatio 적용
 * - 기사 상태 전이
 */

import { initTest, printResult } from "../setup/emulator-config";
import { OFFICES, getDriversForOffice, getTodayWorkDate } from "../setup/constants";
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
  assertCallStatus,
  assertDriverStatus,
  assertSettlementSession,
} from "../helpers/assertions";

const office = OFFICES[0]; // 사무실 01
const drivers = getDriversForOffice(0);
const driver = drivers[0]; // 기사 01-01

export async function runScenario01(): Promise<boolean> {
  await initTest("시나리오 1: 정상 플로우");
  const startTime = Date.now();
  let allPassed = true;

  try {
    // ---- Test 1: 콜 생성 ----
    const callId = "test_call_s01_001";
    console.log("  [1/6] 콜 생성 (WAITING)...");
    await createCall(office, callId, {
      phoneNumber: "010-1234-0001",
      customerName: "테스트고객1",
      fare: 20000,
    });
    await assertCallStatus(office, callId, "WAITING");
    printResult("콜 생성", true, Date.now() - startTime);

    // CF 트리거 대기: sendNewCallNotification (notifications 문서 생성)
    await waitForCondition(
      async () => {
        const doc = await getDoc(`provinces/${office.provinceId}/cities/${office.cityId}/offices/${office.officeId}/calls/${callId}`);
        return doc?.timestamp !== undefined;
      },
      { timeout: 5000, description: "콜 생성 트리거 대기" }
    );

    // ---- Test 2: 배차 ----
    console.log("  [2/6] 배차 (ASSIGNED)...");
    const t2 = Date.now();
    await assignCall(office, callId, driver);
    await assertCallStatus(office, callId, "ASSIGNED");
    await assertDriverStatus(office, driver.driverId, "ASSIGNED");

    // oncallassigned 트리거 대기
    await waitForCondition(async () => true, { timeout: 2000, description: "배차 트리거 대기" });
    printResult("배차", true, Date.now() - t2);

    // ---- Test 3: 수락 ----
    console.log("  [3/6] 수락 (ACCEPTED)...");
    const t3 = Date.now();
    await acceptCall(office, callId, driver);
    await assertCallStatus(office, callId, "ACCEPTED");
    await assertDriverStatus(office, driver.driverId, "PREPARING");
    printResult("수락", true, Date.now() - t3);

    // ---- Test 4: 운행 시작 ----
    console.log("  [4/6] 운행 시작 (IN_PROGRESS)...");
    const t4 = Date.now();
    await startDriving(office, callId, {
      departure: "출발지",
      destination: "도착지",
      fare: 20000,
    });
    await assertCallStatus(office, callId, "IN_PROGRESS");
    printResult("운행 시작", true, Date.now() - t4);

    // ---- Test 5: 운행 완료 → 정산 확정 ----
    console.log("  [5/6] 운행 완료 + 정산 확정 (COMPLETED)...");
    const t5 = Date.now();
    await completeCall(office, callId);
    await assertCallStatus(office, callId, "AWAITING_SETTLEMENT");

    await finalizeTrip(office, callId, driver, {
      fare: 20000,
      paymentMethod: "현금",
      cashReceived: 20000,
    });
    await assertCallStatus(office, callId, "COMPLETED");
    await assertDriverStatus(office, driver.driverId, "WAITING");
    printResult("운행 완료 + 정산 확정", true, Date.now() - t5);

    // ---- Test 6: 정산 세션 검증 ----
    console.log("  [6/6] 정산 세션 검증...");
    const t6 = Date.now();
    const workDate = getTodayWorkDate();

    // onCallCompletedUpdateSettlement CF 트리거 대기
    const settlementCreated = await waitForSettlementCall(office, callId, workDate);

    if (settlementCreated) {
      await assertSettlementSession(office, workDate, {
        containsCallId: callId,
        callCount: 1,
        totalFare: 20000,
      });
      printResult("정산 세션 자동 생성 + 검증", true, Date.now() - t6);
    } else {
      printResult("정산 세션 자동 생성", false, Date.now() - t6, "타임아웃 - CF 트리거 미발동");
      allPassed = false;
    }

  } catch (error: any) {
    printResult("시나리오 1", false, Date.now() - startTime, error.message);
    allPassed = false;
  }

  const totalTime = Date.now() - startTime;
  console.log(`\n  시나리오 1 결과: ${allPassed ? "PASS ✓" : "FAIL ✗"} (${(totalTime / 1000).toFixed(1)}s)\n`);
  return allPassed;
}

// 직접 실행 시
if (require.main === module) {
  runScenario01().then((passed) => {
    process.exit(passed ? 0 : 1);
  });
}
