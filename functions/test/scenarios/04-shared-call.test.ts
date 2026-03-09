/**
 * 시나리오 4: 공유콜
 * 사무실1이 공유콜 생성 → 사무실2가 수임(CLAIMED) → 사무실2에 콜 복사 → 운행 완료
 *
 * 검증:
 * - CF 체인: onSharedCallCreated → onSharedCallClaimed(콜 복사) → onSharedCallCompleted(포인트)
 * - 대상 사무실에 콜 복사 (callType=SHARED, sourceSharedCallId 설정)
 * - 운행 완료 시 shared_calls 상태 COMPLETED
 * - 포인트 ±10%: 원본 사무실 +10%, 수임 사무실 -10%
 */

import { initTest, printResult } from "../setup/emulator-config";
import { db } from "../setup/emulator-config";
import { OFFICES, getDriversForOffice, getTodayWorkDate, officePath } from "../setup/constants";
import {
  createSharedCall,
  claimSharedCall,
  acceptCall,
  startDriving,
  completeCall,
  finalizeTrip,
  getDoc,
  getCollection,
} from "../helpers/firestore-helpers";
import {
  waitForCondition,
  waitForSharedCallStatus,
  waitForCallInOffice,
} from "../helpers/wait-for-trigger";
import {
  assertCallStatus,
  assertDriverStatus,
  assertSharedCallStatus,
  assertSharedCallField,
} from "../helpers/assertions";

const sourceOffice = OFFICES[0]; // 원본 사무실 (콜 발신)
const claimOffice = OFFICES[1];  // 수임 사무실
const claimDrivers = getDriversForOffice(1);
const claimDriver = claimDrivers[0]; // 수임 기사

export async function runScenario04(): Promise<boolean> {
  await initTest("시나리오 4: 공유콜");
  const startTime = Date.now();
  let allPassed = true;

  try {
    const sharedCallId = "test_shared_s04_001";

    // ---- Test 1: 공유콜 생성 (OPEN) ----
    console.log("  [1/6] 공유콜 생성 (OPEN)...");
    const t1 = Date.now();
    await createSharedCall(sharedCallId, {
      phoneNumber: "010-4000-0001",
      sourceOffice,
      fare: 30000,
      customerName: "공유콜고객1",
    });
    await assertSharedCallStatus(sharedCallId, "OPEN");

    // onSharedCallCreated CF 트리거 대기
    await waitForCondition(async () => true, { timeout: 3000, description: "공유콜 생성 트리거 대기" });
    printResult("공유콜 생성", true, Date.now() - t1);

    // ---- Test 2: 사무실2가 수임 (CLAIMED) ----
    console.log("  [2/6] 사무실2 수임 (CLAIMED)...");
    const t2 = Date.now();
    await claimSharedCall(sharedCallId, claimOffice, {
      fare: 30000,
      departure: "출발지S",
      destination: "도착지S",
      driverId: claimDriver.driverId,
      driverAuthUid: claimDriver.authUid,
    });
    await assertSharedCallStatus(sharedCallId, "CLAIMED");
    printResult("사무실2 수임", true, Date.now() - t2);

    // ---- Test 3: CF가 수임 사무실에 콜 복사했는지 확인 ----
    console.log("  [3/6] 수임 사무실 콜 복사 확인...");
    const t3 = Date.now();

    const callCopied = await waitForCallInOffice(claimOffice, (calls) => {
      return calls.some((c) => c.sourceSharedCallId === sharedCallId);
    });

    if (callCopied) {
      // 복사된 콜 확인
      const copiedCalls = await getCollection(`${officePath(claimOffice)}/calls`);
      const copiedCall = copiedCalls.find((c: any) => c.sourceSharedCallId === sharedCallId);

      const isSharedType = copiedCall?.callType === "SHARED";
      const hasDriver = copiedCall?.assignedDriverId === claimDriver.authUid;

      if (isSharedType && hasDriver) {
        printResult("콜 복사 (SHARED + 기사 배정)", true, Date.now() - t3);
      } else {
        printResult("콜 복사", false, Date.now() - t3,
          `callType=${copiedCall?.callType}, assignedDriverId=${copiedCall?.assignedDriverId}`);
        allPassed = false;
      }
    } else {
      printResult("콜 복사", false, Date.now() - t3, "타임아웃 - CF 콜 복사 미발동");
      allPassed = false;
    }

    // ---- Test 4: 수락 → 운행 → 완료 ----
    console.log("  [4/6] 수임 콜 수락 → 운행 → 완료...");
    const t4 = Date.now();

    // 복사된 콜 ID 조회
    const copiedCalls = await getCollection(`${officePath(claimOffice)}/calls`);
    const copiedCall = copiedCalls.find((c: any) => c.sourceSharedCallId === sharedCallId);

    if (!copiedCall) {
      printResult("수임 콜 진행", false, Date.now() - t4, "복사된 콜을 찾을 수 없음");
      allPassed = false;
    } else {
      const copiedCallId = copiedCall.id;

      await acceptCall(claimOffice, copiedCallId, claimDriver);
      await assertCallStatus(claimOffice, copiedCallId, "ACCEPTED");

      await startDriving(claimOffice, copiedCallId, {
        departure: "출발지S",
        destination: "도착지S",
        fare: 30000,
      });
      await assertCallStatus(claimOffice, copiedCallId, "IN_PROGRESS");

      await completeCall(claimOffice, copiedCallId);
      await assertCallStatus(claimOffice, copiedCallId, "AWAITING_SETTLEMENT");

      await finalizeTrip(claimOffice, copiedCallId, claimDriver, {
        fare: 30000,
        paymentMethod: "현금",
        cashReceived: 30000,
      });
      await assertCallStatus(claimOffice, copiedCallId, "COMPLETED");
      await assertDriverStatus(claimOffice, claimDriver.driverId, "WAITING");
      printResult("수임 콜 완료", true, Date.now() - t4);
    }

    // ---- Test 5: shared_calls 상태 COMPLETED 확인 ----
    console.log("  [5/6] shared_calls COMPLETED 확인...");
    const t5 = Date.now();

    const sharedCompleted = await waitForSharedCallStatus(sharedCallId, "COMPLETED", 60000);

    if (sharedCompleted) {
      printResult("shared_calls COMPLETED", true, Date.now() - t5);
    } else {
      printResult("shared_calls COMPLETED", false, Date.now() - t5,
        "타임아웃 - onSharedCallCompleted 미발동");
      allPassed = false;
    }

    // ---- Test 6: 포인트 ±10% 확인 ----
    console.log("  [6/6] 포인트 ±10% 확인...");
    const t6 = Date.now();

    const expectedBonus = Math.round(30000 * 0.1); // 3000
    const sourceExpected = 10000 + expectedBonus; // 13000
    const claimExpected = 10000 - expectedBonus;  // 7000

    // 포인트 처리 CF 완료까지 폴링 대기
    const pointsUpdated = await waitForCondition(
      async () => {
        const sp = await getDoc(`${officePath(sourceOffice)}/points/points`);
        return sp?.balance === sourceExpected;
      },
      { timeout: 60000, description: "포인트 ±10% 업데이트 대기" }
    );

    const sourcePoints = await getDoc(`${officePath(sourceOffice)}/points/points`);
    const claimPoints = await getDoc(`${officePath(claimOffice)}/points/points`);
    const sourceBalance = sourcePoints?.balance ?? 0;
    const claimBalance = claimPoints?.balance ?? 0;

    if (sourceBalance === sourceExpected && claimBalance === claimExpected) {
      printResult("포인트 ±10%", true, Date.now() - t6,
        `원본: ${sourceBalance} (+${expectedBonus}), 수임: ${claimBalance} (-${expectedBonus})`);
    } else {
      printResult("포인트 ±10%", false, Date.now() - t6,
        `원본: ${sourceBalance} (expected ${sourceExpected}), 수임: ${claimBalance} (expected ${claimExpected})`);
      allPassed = false;
    }

  } catch (error: any) {
    printResult("시나리오 4", false, Date.now() - startTime, error.message);
    allPassed = false;
  }

  const totalTime = Date.now() - startTime;
  console.log(`\n  시나리오 4 결과: ${allPassed ? "PASS ✓" : "FAIL ✗"} (${(totalTime / 1000).toFixed(1)}s)\n`);
  return allPassed;
}

if (require.main === module) {
  runScenario04().then((passed) => {
    process.exit(passed ? 0 : 1);
  });
}
