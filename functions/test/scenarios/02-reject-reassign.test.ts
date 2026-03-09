/**
 * 시나리오 2: 거절 → 재배차
 * 콜 생성 → 기사1 배차 → 기사1 거절 → 기사2 재배차 → 수락 → 완료
 *
 * 검증:
 * - 거절 시 기사1 상태 WAITING 복구
 * - 콜 상태 WAITING 복구 + rejectedByDriver 기록
 * - 재배차 후 정상 플로우 완료
 * - 정산 세션에 기사2로 기록
 */

import { initTest, printResult } from "../setup/emulator-config";
import { OFFICES, getDriversForOffice, getTodayWorkDate } from "../setup/constants";
import {
  createCall,
  assignCall,
  acceptCall,
  rejectCall,
  startDriving,
  completeCall,
  finalizeTrip,
  getDoc,
} from "../helpers/firestore-helpers";
import { waitForSettlementCall, waitForCondition } from "../helpers/wait-for-trigger";
import {
  assertCallStatus,
  assertCallField,
  assertDriverStatus,
  assertSettlementSession,
} from "../helpers/assertions";

const office = OFFICES[0];
const drivers = getDriversForOffice(0);
const driver1 = drivers[0]; // 기사 01-01 (거절할 기사)
const driver2 = drivers[1]; // 기사 01-02 (재배차될 기사)

export async function runScenario02(): Promise<boolean> {
  await initTest("시나리오 2: 거절 → 재배차");
  const startTime = Date.now();
  let allPassed = true;

  try {
    // ---- Test 1: 콜 생성 ----
    const callId = "test_call_s02_001";
    console.log("  [1/7] 콜 생성 (WAITING)...");
    await createCall(office, callId, {
      phoneNumber: "010-1234-0002",
      customerName: "테스트고객2",
      fare: 25000,
    });
    await assertCallStatus(office, callId, "WAITING");
    printResult("콜 생성", true, Date.now() - startTime);

    // ---- Test 2: 기사1 배차 ----
    console.log("  [2/7] 기사1 배차 (ASSIGNED)...");
    const t2 = Date.now();
    await assignCall(office, callId, driver1);
    await assertCallStatus(office, callId, "ASSIGNED");
    await assertDriverStatus(office, driver1.driverId, "ASSIGNED");
    printResult("기사1 배차", true, Date.now() - t2);

    // ---- Test 3: 기사1 거절 → 콜 WAITING 복구 ----
    console.log("  [3/7] 기사1 거절 → WAITING 복구...");
    const t3 = Date.now();
    await rejectCall(office, callId, driver1);
    await assertCallStatus(office, callId, "WAITING");
    await assertDriverStatus(office, driver1.driverId, "WAITING");
    await assertCallField(office, callId, "rejectedByDriver", driver1.driverId);
    printResult("기사1 거절 + 상태 복구", true, Date.now() - t3);

    // CF 트리거 대기 (onCallStatusChanged: ASSIGNED→WAITING)
    await waitForCondition(async () => true, { timeout: 2000, description: "거절 트리거 대기" });

    // ---- Test 4: 기사2 재배차 ----
    console.log("  [4/7] 기사2 재배차 (ASSIGNED)...");
    const t4 = Date.now();
    await assignCall(office, callId, driver2);
    await assertCallStatus(office, callId, "ASSIGNED");
    await assertDriverStatus(office, driver2.driverId, "ASSIGNED");
    printResult("기사2 재배차", true, Date.now() - t4);

    // ---- Test 5: 기사2 수락 ----
    console.log("  [5/7] 기사2 수락 (ACCEPTED)...");
    const t5 = Date.now();
    await acceptCall(office, callId, driver2);
    await assertCallStatus(office, callId, "ACCEPTED");
    await assertDriverStatus(office, driver2.driverId, "PREPARING");
    printResult("기사2 수락", true, Date.now() - t5);

    // ---- Test 6: 운행 완료 ----
    console.log("  [6/7] 운행 완료 (COMPLETED)...");
    const t6 = Date.now();
    await startDriving(office, callId, {
      departure: "출발지2",
      destination: "도착지2",
      fare: 25000,
    });
    await assertCallStatus(office, callId, "IN_PROGRESS");

    await completeCall(office, callId);
    await assertCallStatus(office, callId, "AWAITING_SETTLEMENT");

    await finalizeTrip(office, callId, driver2, {
      fare: 25000,
      paymentMethod: "카드",
      creditAmount: 25000,
    });
    await assertCallStatus(office, callId, "COMPLETED");
    await assertDriverStatus(office, driver2.driverId, "WAITING");
    printResult("운행 완료", true, Date.now() - t6);

    // ---- Test 7: 정산 세션에 기사2로 기록 확인 ----
    console.log("  [7/7] 정산 세션 검증 (기사2 기록)...");
    const t7 = Date.now();
    const workDate = getTodayWorkDate();

    const settlementCreated = await waitForSettlementCall(office, callId, workDate);

    if (settlementCreated) {
      // 정산에 기사2의 authUid로 기록되었는지 확인
      const settlementDoc = await getDoc(
        `provinces/${office.provinceId}/cities/${office.cityId}/offices/${office.officeId}/settlementSessions/${workDate}`
      );
      const callEntry = settlementDoc?.calls?.find((c: any) => c.callId === callId);
      const driverMatch = callEntry?.driverId === driver2.authUid;

      if (driverMatch) {
        printResult("정산 세션 (기사2 기록)", true, Date.now() - t7);
      } else {
        printResult("정산 세션 (기사2 기록)", false, Date.now() - t7,
          `driverId: expected=${driver2.authUid}, actual=${callEntry?.driverId}`);
        allPassed = false;
      }
    } else {
      printResult("정산 세션 자동 생성", false, Date.now() - t7, "타임아웃 - CF 트리거 미발동");
      allPassed = false;
    }

  } catch (error: any) {
    printResult("시나리오 2", false, Date.now() - startTime, error.message);
    allPassed = false;
  }

  const totalTime = Date.now() - startTime;
  console.log(`\n  시나리오 2 결과: ${allPassed ? "PASS ✓" : "FAIL ✗"} (${(totalTime / 1000).toFixed(1)}s)\n`);
  return allPassed;
}

// 직접 실행 시
if (require.main === module) {
  runScenario02().then((passed) => {
    process.exit(passed ? 0 : 1);
  });
}
