/**
 * 시나리오 9: 정산 이월/미수 통합 테스트
 * carryOver가 있는 상태에서 submitDailySettlement → confirmDailySettlement 흐름 검증
 *
 * 검증:
 * - 이월금(양수) 있을 때 정산 계산 정확성
 * - 미수금(음수) 있을 때 정산 계산 정확성
 * - 연속 2일 이월 누적
 * - confirmDailySettlement 후 carryOver.balance 정확성
 */

import { initTest, printResult } from "../setup/emulator-config";
import { db, Timestamp } from "../setup/emulator-config";
import { OFFICES, getDriversForOffice, officePath, driverPath } from "../setup/constants";

const office = OFFICES[8]; // 사무실 09 (다른 시나리오와 충돌 방지)
const drivers = getDriversForOffice(8);

// ========== 정산 계산 함수 (앱 로직 복제) ==========

function calcSettlement(params: {
  totalFare: number;
  cashReceived: number;
  realDeposit: number;
  depositRatio: number;
  originalCarryOver: number;
  totalCredit?: number;
}) {
  const { totalFare, cashReceived, realDeposit, depositRatio, originalCarryOver } = params;
  const officeDeposit = Math.floor(totalFare * depositRatio / 100);
  const driverShare = totalFare - officeDeposit;
  const totalCredit = params.totalCredit ?? (totalFare - cashReceived);
  const finalDeposit = officeDeposit - totalCredit;
  const calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit;
  const settlementDiff = (realDeposit - finalDeposit) + originalCarryOver;

  return {
    officeDeposit, driverShare, totalCredit, finalDeposit,
    realDeposit, calculatedCarryOver, settlementDiff, originalCarryOver
  };
}

// ========== Firestore 헬퍼 ==========

/** 기사 문서에 carryOver 설정 */
async function setDriverCarryOver(driverId: string, balance: number, status: string = "PENDING") {
  const path = driverPath(office, driverId);
  await db.doc(path).set({
    name: driverId,
    status: "ONLINE",
    approvalStatus: "APPROVED",
    officeId: office.officeId,
    provinceId: office.provinceId,
    cityId: office.cityId,
    driverType: "대리기사",
    phoneNumber: "010-0000-0000",
    carryOver: {
      balance: balance,
      status: balance !== 0 ? status : "SETTLED",
      lastUpdatedAt: Timestamp.now()
    }
  }, { merge: true });
}

/** 기사앱 업무마감 시뮬레이션 (dailySettlement 필드 쓰기) */
async function simulateSubmitDailySettlement(
  driverId: string,
  settlement: ReturnType<typeof calcSettlement>
) {
  const path = driverPath(office, driverId);
  await db.doc(path).update({
    dailySettlement: {
      date: new Date().toISOString().split("T")[0],
      finalDeposit: settlement.finalDeposit,
      realDeposit: settlement.realDeposit,
      settlementDiff: settlement.settlementDiff,
      totalFare: 0, // 시뮬레이션이므로 간략화
      totalCredit: settlement.totalCredit,
      tripCount: 1,
      status: "PENDING_CONFIRM",
      submittedAt: Timestamp.now(),
      calculatedCarryOver: settlement.calculatedCarryOver,
      originalCarryOver: settlement.originalCarryOver,
      originalTripCount: 0,
      originalTotalFare: 0,
      originalRealDeposit: 0,
    }
  });
}

/** 매니저앱 정산확인 시뮬레이션 (confirmDailySettlement 트랜잭션 로직) */
async function simulateConfirmDailySettlement(driverId: string): Promise<number> {
  const path = driverPath(office, driverId);

  return db.runTransaction(async (transaction) => {
    const doc = await transaction.get(db.doc(path));
    const data = doc.data();

    const dailySettlementMap = data?.dailySettlement;
    const newBalance = dailySettlementMap
      ? (dailySettlementMap.calculatedCarryOver ?? 0)
      : (data?.carryOver?.balance ?? 0);

    const newCarryOverStatus = newBalance !== 0 ? "PENDING" : "SETTLED";

    transaction.update(db.doc(path), {
      "dailySettlement.status": "CONFIRMED",
      "dailySettlement.confirmedAt": Timestamp.now(),
      "dailySettlement.confirmedBy": "test_admin",
      "carryOver.balance": newBalance,
      "carryOver.status": newCarryOverStatus,
      "carryOver.lastUpdatedAt": Timestamp.now(),
    });

    return newBalance;
  });
}

/** Firestore에서 기사 문서 읽기 */
async function getDriverDoc(driverId: string) {
  const doc = await db.doc(driverPath(office, driverId)).get();
  return doc.data();
}

// ========== 테스트 케이스 ==========

interface TestCase {
  name: string;
  driverId: string;
  initialCarryOver: number;
  totalFare: number;
  cashReceived: number;
  realDeposit: number;
  depositRatio: number;
  expectedCarryOver: number;
  expectedDiff: number;
}

async function runTestCase(tc: TestCase): Promise<boolean> {
  const t = Date.now();

  // 1. 기사 문서 생성 + carryOver 설정
  await setDriverCarryOver(tc.driverId, tc.initialCarryOver);

  // 2. 정산 계산
  const settlement = calcSettlement({
    totalFare: tc.totalFare,
    cashReceived: tc.cashReceived,
    realDeposit: tc.realDeposit,
    depositRatio: tc.depositRatio,
    originalCarryOver: tc.initialCarryOver,
  });

  // 3. 업무마감 제출 시뮬레이션
  await simulateSubmitDailySettlement(tc.driverId, settlement);

  // 4. 제출 후 Firestore 확인 (PENDING_CONFIRM 상태)
  const afterSubmit = await getDriverDoc(tc.driverId);
  const submitStatus = afterSubmit?.dailySettlement?.status;
  if (submitStatus !== "PENDING_CONFIRM") {
    printResult(tc.name, false, Date.now() - t, `제출 후 status=${submitStatus}, expected=PENDING_CONFIRM`);
    return false;
  }

  // 5. 정산확인 시뮬레이션
  const newBalance = await simulateConfirmDailySettlement(tc.driverId);

  // 6. 확인 후 Firestore 검증
  const afterConfirm = await getDriverDoc(tc.driverId);
  const carryOverBalance = afterConfirm?.carryOver?.balance;
  const carryOverStatus = afterConfirm?.carryOver?.status;
  const dailyStatus = afterConfirm?.dailySettlement?.status;

  const checks: string[] = [];

  if (carryOverBalance !== tc.expectedCarryOver) {
    checks.push(`carryOver.balance=${carryOverBalance}, expected=${tc.expectedCarryOver}`);
  }
  if (settlement.settlementDiff !== tc.expectedDiff) {
    checks.push(`settlementDiff=${settlement.settlementDiff}, expected=${tc.expectedDiff}`);
  }
  if (dailyStatus !== "CONFIRMED") {
    checks.push(`dailySettlement.status=${dailyStatus}, expected=CONFIRMED`);
  }
  if (newBalance !== tc.expectedCarryOver) {
    checks.push(`transaction newBalance=${newBalance}, expected=${tc.expectedCarryOver}`);
  }
  // carryOver.status 검증
  const expectedStatus = tc.expectedCarryOver !== 0 ? "PENDING" : "SETTLED";
  if (carryOverStatus !== expectedStatus) {
    checks.push(`carryOver.status=${carryOverStatus}, expected=${expectedStatus}`);
  }

  if (checks.length === 0) {
    printResult(tc.name, true, Date.now() - t,
      `carryOver=${carryOverBalance}, diff=${settlement.settlementDiff}`);
    return true;
  } else {
    printResult(tc.name, false, Date.now() - t, checks.join(" | "));
    return false;
  }
}

// ========== 메인 ==========

export async function runScenario09(): Promise<boolean> {
  await initTest("시나리오 9: 정산 이월/미수 통합 테스트");
  const startTime = Date.now();
  let allPassed = true;

  try {
    // --- 단일 운행 시나리오 ---
    console.log("  ■ 단일 운행 시나리오");

    const singleTests: TestCase[] = [
      {
        name: "#1 현금 정확히 납입",
        driverId: drivers[0].driverId,
        initialCarryOver: 0,
        totalFare: 30000, cashReceived: 30000, realDeposit: 18000,
        depositRatio: 60,
        expectedCarryOver: 0, expectedDiff: 0,
      },
      {
        name: "#2 현금 초과 납입 (환급금 발생)",
        driverId: drivers[1].driverId,
        initialCarryOver: 0,
        totalFare: 30000, cashReceived: 30000, realDeposit: 20000,
        depositRatio: 60,
        expectedCarryOver: 2000, expectedDiff: 2000,
      },
      {
        name: "#3 현금 미납 (미수 발생)",
        driverId: drivers[2].driverId,
        initialCarryOver: 0,
        totalFare: 30000, cashReceived: 30000, realDeposit: 15000,
        depositRatio: 60,
        expectedCarryOver: -3000, expectedDiff: -3000,
      },
      {
        name: "#4 이월 2,000 + 초과납입",
        driverId: drivers[3].driverId,
        initialCarryOver: 2000,
        totalFare: 15000, cashReceived: 15000, realDeposit: 10000,
        depositRatio: 60,
        expectedCarryOver: 3000, expectedDiff: 3000,
      },
      {
        name: "#5 이월 2,000 + 정확히 납입",
        driverId: drivers[4].driverId,
        initialCarryOver: 2000,
        totalFare: 30000, cashReceived: 30000, realDeposit: 18000,
        depositRatio: 60,
        expectedCarryOver: 2000, expectedDiff: 2000,
      },
    ];

    for (const tc of singleTests) {
      const passed = await runTestCase(tc);
      if (!passed) allPassed = false;
    }

    // 미수 시나리오 (기존 기사 재사용)
    console.log("\n  ■ 미수금 시나리오");

    // #6: 미수 -3,000 + 납입 20,000
    await setDriverCarryOver(drivers[0].driverId, -3000);
    const passed6 = await runTestCase({
      name: "#6 미수 -3,000 + 납입",
      driverId: drivers[0].driverId,
      initialCarryOver: -3000,
      totalFare: 30000, cashReceived: 30000, realDeposit: 20000,
      depositRatio: 60,
      expectedCarryOver: -1000, expectedDiff: -1000,
    });
    if (!passed6) allPassed = false;

    // #7: 미수 -3,000 + 초과납입 25,000
    const passed7 = await runTestCase({
      name: "#7 미수 -3,000 + 초과납입",
      driverId: drivers[1].driverId,
      initialCarryOver: -3000,
      totalFare: 30000, cashReceived: 30000, realDeposit: 25000,
      depositRatio: 60,
      expectedCarryOver: 4000, expectedDiff: 4000,
    });
    if (!passed7) allPassed = false;

    // --- 연속 시나리오 ---
    console.log("\n  ■ 연속 2일 시나리오");

    // #8: 2일 연속 초과 납입
    {
      const did = drivers[2].driverId;
      console.log("  [#8] 2일 연속 초과 납입");

      // Day 1: carryOver 0 → 실납입 20,000 → carryOver 2,000
      await setDriverCarryOver(did, 0);
      const s1 = calcSettlement({ totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, originalCarryOver: 0 });
      await simulateSubmitDailySettlement(did, s1);
      const bal1 = await simulateConfirmDailySettlement(did);

      if (bal1 !== 2000) {
        printResult("#8 Day1", false, 0, `carryOver=${bal1}, expected=2000`);
        allPassed = false;
      } else {
        printResult("#8 Day1", true, 0, `carryOver=${bal1}`);
      }

      // Day 2: carryOver 2,000 → 실납입 20,000 → carryOver 4,000
      const s2 = calcSettlement({ totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, originalCarryOver: bal1 });
      await simulateSubmitDailySettlement(did, s2);
      const bal2 = await simulateConfirmDailySettlement(did);

      if (bal2 !== 4000) {
        printResult("#8 Day2", false, 0, `carryOver=${bal2}, expected=4000`);
        allPassed = false;
      } else {
        printResult("#8 Day2", true, 0, `carryOver=${bal2}`);
      }
    }

    // #9: 초과 → 미납
    {
      const did = drivers[3].driverId;
      console.log("  [#9] 초과→미납");

      await setDriverCarryOver(did, 0);
      const s1 = calcSettlement({ totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, originalCarryOver: 0 });
      await simulateSubmitDailySettlement(did, s1);
      const bal1 = await simulateConfirmDailySettlement(did);
      printResult("#9 Day1 초과", bal1 === 2000, 0, `carryOver=${bal1}`);
      if (bal1 !== 2000) allPassed = false;

      const s2 = calcSettlement({ totalFare: 30000, cashReceived: 30000, realDeposit: 15000, depositRatio: 60, originalCarryOver: bal1 });
      await simulateSubmitDailySettlement(did, s2);
      const bal2 = await simulateConfirmDailySettlement(did);
      printResult("#9 Day2 미납", bal2 === -1000, 0, `carryOver=${bal2}`);
      if (bal2 !== -1000) allPassed = false;
    }

    // #10: 3일 연속 (초과→초과→미납)
    {
      const did = drivers[4].driverId;
      console.log("  [#10] 3일 연속 (초과→초과→미납)");

      await setDriverCarryOver(did, 0);

      // Day1: 0 + 30k/20k → 2000
      const s1 = calcSettlement({ totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, originalCarryOver: 0 });
      await simulateSubmitDailySettlement(did, s1);
      const b1 = await simulateConfirmDailySettlement(did);
      printResult("#10 Day1", b1 === 2000, 0, `carryOver=${b1}`);
      if (b1 !== 2000) allPassed = false;

      // Day2: 2000 + 25k/18k → 5000
      const s2 = calcSettlement({ totalFare: 25000, cashReceived: 25000, realDeposit: 18000, depositRatio: 60, originalCarryOver: b1 });
      await simulateSubmitDailySettlement(did, s2);
      const b2 = await simulateConfirmDailySettlement(did);
      printResult("#10 Day2", b2 === 5000, 0, `carryOver=${b2}`);
      if (b2 !== 5000) allPassed = false;

      // Day3: 5000 + 30k/15k → 2000
      const s3 = calcSettlement({ totalFare: 30000, cashReceived: 30000, realDeposit: 15000, depositRatio: 60, originalCarryOver: b2 });
      await simulateSubmitDailySettlement(did, s3);
      const b3 = await simulateConfirmDailySettlement(did);
      printResult("#10 Day3", b3 === 2000, 0, `carryOver=${b3}`);
      if (b3 !== 2000) allPassed = false;
    }

  } catch (error: any) {
    printResult("시나리오 9", false, Date.now() - startTime, error.message);
    allPassed = false;
  }

  const totalTime = Date.now() - startTime;
  console.log(`\n  시나리오 9 결과: ${allPassed ? "PASS ✓" : "FAIL ✗"} (${(totalTime / 1000).toFixed(1)}s)\n`);
  return allPassed;
}

if (require.main === module) {
  runScenario09().then((passed) => {
    process.exit(passed ? 0 : 1);
  });
}
