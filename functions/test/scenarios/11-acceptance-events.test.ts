/**
 * 시나리오 11: acceptanceEvents 집계 검증 (Phase 6 ① + ② Day 4 Phase A)
 *
 * 검증:
 * - 기사 문서 platform="ios" 세팅 시 acceptanceEvents.platform === "ios"
 * - platform 필드 부재 시 "android" fallback (Phase 6 ① 회귀 없음)
 * - 거절 분기 (outcome === "rejected", rejectReason === "driver_rejected")
 */

import { initTest, printResult, db } from "../setup/emulator-config";
import { OFFICES, getDriversForOffice, driverPath } from "../setup/constants";
import {
  createCall,
  assignCall,
  acceptCall,
  rejectCall,
} from "../helpers/firestore-helpers";
import { waitForCondition } from "../helpers/wait-for-trigger";

const office = OFFICES[0];
const drivers = getDriversForOffice(0);

async function findAcceptanceEvent(callId: string): Promise<any | null> {
  const snap = await db.collection("acceptanceEvents").where("callId", "==", callId).get();
  return snap.empty ? null : snap.docs[0].data();
}

export async function runScenario11(): Promise<boolean> {
  await initTest("시나리오 11: acceptanceEvents 집계 검증");
  const startTime = Date.now();
  let allPassed = true;

  try {
    // ---- Test 1: platform="ios" → acceptanceEvents.platform === "ios" ----
    {
      const driver = drivers[0];
      const callId = "test_call_s11_001";
      console.log('  [1/3] platform="ios" → acceptanceEvents.platform === "ios"...');
      const t1 = Date.now();

      await db.doc(driverPath(office, driver.driverId)).update({ platform: "ios" });
      await createCall(office, callId, {
        phoneNumber: "010-1234-1101",
        customerName: "iOS_tester",
        fare: 20000,
      });
      await assignCall(office, callId, driver);
      await acceptCall(office, callId, driver);

      const found = await waitForCondition(
        async () => (await findAcceptanceEvent(callId)) !== null,
        { timeout: 8000, description: "acceptanceEvents(ios) 생성 대기" }
      );
      const event = await findAcceptanceEvent(callId);
      const ok =
        found &&
        event?.platform === "ios" &&
        event?.outcome === "accepted" &&
        typeof event?.latencyMs === "number" &&
        event?.latencyMs >= 0;
      printResult(
        "ios platform 집계",
        ok,
        Date.now() - t1,
        ok ? undefined : `event=${JSON.stringify(event)}`
      );
      if (!ok) allPassed = false;
    }

    // ---- Test 2: platform 필드 부재 → android fallback ----
    {
      const driver = drivers[1];
      const callId = "test_call_s11_002";
      console.log('  [2/3] platform 부재 → fallback "android"...');
      const t2 = Date.now();

      await createCall(office, callId, {
        phoneNumber: "010-1234-1102",
        customerName: "android_fallback",
        fare: 20000,
      });
      await assignCall(office, callId, driver);
      await acceptCall(office, callId, driver);

      const found = await waitForCondition(
        async () => (await findAcceptanceEvent(callId)) !== null,
        { timeout: 8000, description: "acceptanceEvents(android) 생성 대기" }
      );
      const event = await findAcceptanceEvent(callId);
      const ok = found && event?.platform === "android" && event?.outcome === "accepted";
      printResult(
        "android fallback 집계",
        ok,
        Date.now() - t2,
        ok ? undefined : `event=${JSON.stringify(event)}`
      );
      if (!ok) allPassed = false;
    }

    // ---- Test 3: 거절 분기 (outcome=rejected, rejectReason=driver_rejected) ----
    {
      const driver = drivers[2];
      const callId = "test_call_s11_003";
      console.log("  [3/3] 거절 분기 → outcome=rejected...");
      const t3 = Date.now();

      await createCall(office, callId, {
        phoneNumber: "010-1234-1103",
        customerName: "reject_tester",
        fare: 20000,
      });
      await assignCall(office, callId, driver);
      await rejectCall(office, callId, driver);

      const found = await waitForCondition(
        async () => (await findAcceptanceEvent(callId)) !== null,
        { timeout: 8000, description: "acceptanceEvents(rejected) 생성 대기" }
      );
      const event = await findAcceptanceEvent(callId);
      const ok =
        found && event?.outcome === "rejected" && event?.rejectReason === "driver_rejected";
      printResult(
        "rejected 분기 집계",
        ok,
        Date.now() - t3,
        ok ? undefined : `event=${JSON.stringify(event)}`
      );
      if (!ok) allPassed = false;
    }
  } catch (error: any) {
    printResult("시나리오 11", false, Date.now() - startTime, error.message);
    allPassed = false;
  }

  const totalTime = Date.now() - startTime;
  console.log(
    `\n  시나리오 11 결과: ${allPassed ? "PASS ✓" : "FAIL ✗"} (${(totalTime / 1000).toFixed(1)}s)\n`
  );
  return allPassed;
}

if (require.main === module) {
  (async () => {
    const { clearEmulatorData } = await import("../setup/emulator-config");
    const { seedAll } = await import("../setup/seed-data");
    await clearEmulatorData();
    await seedAll();
    await new Promise((r) => setTimeout(r, 3000));
    const passed = await runScenario11();
    process.exit(passed ? 0 : 1);
  })();
}
