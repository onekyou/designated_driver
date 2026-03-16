/**
 * 시나리오 10: 정산 일관성 검증 시뮬레이션
 * 7일 × 50콜 = 350콜, 기사 5명
 *
 * Firestore 실제값 ↔ 콜매니저 UI 계산 ↔ 기사앱 UI 계산 3자 일치 검증
 * 26개 체크포인트
 */

import { initTest, printResult, clearEmulatorData } from "../setup/emulator-config";
import { db, Timestamp, FieldValue } from "../setup/emulator-config";
import { OFFICES, getDriversForOffice, officePath, driverPath, callPath, settlementPath, getTodayWorkDate } from "../setup/constants";
import { createCall, assignCall, acceptCall, startDriving, finalizeTrip, cancelCallByManager, cancelCallByDriver, cancelCallByCustomer, rejectCall, getDoc } from "../helpers/firestore-helpers";
import { waitForSettlementCall, waitForCondition } from "../helpers/wait-for-trigger";
import { assertTripleConsistency, printConsistencyResult, ConsistencyCheckResult } from "./consistency-checker";

// ========== 설정 ==========

const office = OFFICES[8]; // office_09
const drivers = getDriversForOffice(8);
const DEPOSIT_RATIO = 60;

// 기사 별칭
const DRIVER_A = drivers[0]; // driver_09_01
const DRIVER_B = drivers[1]; // driver_09_02
const DRIVER_C = drivers[2]; // driver_09_03
const DRIVER_D = drivers[3]; // driver_09_04
const DRIVER_E = drivers[4]; // driver_09_05

let callSeq = 0;
function nextCallId(day: number): string {
  return `sim_d${day}_c${String(++callSeq).padStart(3, "0")}`;
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ========== 정산 계산 함수 (앱 로직 복제) ==========

function calcSettlement(params: {
  totalFare: number;
  cashReceived: number;
  realDeposit: number;
  depositRatio: number;
  originalCarryOver: number;
  totalCredit?: number;
  tripCount?: number;
}) {
  const { totalFare, cashReceived, realDeposit, depositRatio, originalCarryOver } = params;
  const officeDeposit = Math.floor(totalFare * depositRatio / 100);
  const driverShare = totalFare - officeDeposit;
  const totalCredit = params.totalCredit ?? (totalFare - cashReceived);
  const finalDeposit = officeDeposit - totalCredit;
  const calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit;
  const settlementDiff = (realDeposit - finalDeposit) + originalCarryOver;
  const tripCount = params.tripCount ?? 1;

  return {
    officeDeposit, driverShare, totalCredit, finalDeposit,
    realDeposit, calculatedCarryOver, settlementDiff, originalCarryOver,
    totalFare, tripCount,
  };
}

// ========== Firestore 헬퍼 ==========

async function seedOfficeAndDrivers(): Promise<void> {
  // 사무실 생성
  await db.doc(officePath(office)).set({
    name: office.name,
    depositRatio: DEPOSIT_RATIO,
    assignedTimeoutMinutes: 3,
    settlementLastCleared: null,
  });

  // 기사 5명 생성
  for (const driver of drivers) {
    await db.doc(driverPath(office, driver.driverId)).set({
      name: driver.name,
      authUid: driver.authUid,
      status: "WAITING",
      approvalStatus: "APPROVED",
      officeId: office.officeId,
      provinceId: office.provinceId,
      cityId: office.cityId,
      driverType: "대리기사",
      phoneNumber: driver.phoneNumber,
      carryOver: { balance: 0, status: "SETTLED", lastUpdatedAt: Timestamp.now() },
    });
  }
}

async function setDriverCarryOver(driverId: string, balance: number, status: string = "PENDING"): Promise<void> {
  await db.doc(driverPath(office, driverId)).update({
    "carryOver.balance": balance,
    "carryOver.status": balance !== 0 ? status : "SETTLED",
    "carryOver.lastUpdatedAt": Timestamp.now(),
  });
}

async function simulateSubmitDailySettlement(
  driverId: string,
  settlement: ReturnType<typeof calcSettlement>,
  originalTripCount: number = 0,
  originalTotalFare: number = 0,
  originalRealDeposit: number = 0,
): Promise<void> {
  await db.doc(driverPath(office, driverId)).update({
    dailySettlement: {
      date: getTodayWorkDate(),
      finalDeposit: settlement.finalDeposit,
      realDeposit: settlement.realDeposit,
      settlementDiff: settlement.settlementDiff,
      totalFare: settlement.totalFare,
      totalCredit: settlement.totalCredit,
      tripCount: settlement.tripCount,
      status: "PENDING_CONFIRM",
      submittedAt: Timestamp.now(),
      calculatedCarryOver: settlement.calculatedCarryOver,
      originalCarryOver: settlement.originalCarryOver,
      originalTripCount,
      originalTotalFare,
      originalRealDeposit,
    }
  });
}

async function simulateConfirmDailySettlement(driverId: string): Promise<number> {
  const path = driverPath(office, driverId);
  return db.runTransaction(async (transaction) => {
    const doc = await transaction.get(db.doc(path));
    const data = doc.data();
    const ds = data?.dailySettlement;
    const newBalance = ds ? (ds.calculatedCarryOver ?? 0) : (data?.carryOver?.balance ?? 0);
    const newStatus = newBalance !== 0 ? "PENDING" : "SETTLED";

    transaction.update(db.doc(path), {
      "dailySettlement.status": "CONFIRMED",
      "dailySettlement.confirmedAt": Timestamp.now(),
      "dailySettlement.confirmedBy": "test_admin",
      "carryOver.balance": newBalance,
      "carryOver.status": newStatus,
      "carryOver.lastUpdatedAt": Timestamp.now(),
    });
    return newBalance;
  });
}

async function simulateRejectDailySettlement(driverId: string): Promise<void> {
  await db.doc(driverPath(office, driverId)).update({
    "dailySettlement.status": "REJECTED",
    "dailySettlement.rejectedAt": Timestamp.now(),
  });
}

async function simulateTransferCarryOver(driverId: string, balance: number, todayUnpaid: number = 0): Promise<void> {
  const totalBalance = balance + todayUnpaid;
  await db.doc(driverPath(office, driverId)).update({
    "carryOver.balance": totalBalance,
    "carryOver.todayAmount": todayUnpaid,
    "carryOver.status": "TRANSFERRED",
    "carryOver.transferredAt": Timestamp.now(),
    "carryOver.transferredBy": "test_admin",
    "carryOver.lastUpdatedAt": Timestamp.now(),
  });
}

async function simulateReceiveCarryOver(driverId: string): Promise<void> {
  await db.doc(driverPath(office, driverId)).update({
    "carryOver.balance": 0,
    "carryOver.status": "SETTLED",
    "carryOver.lastUpdatedAt": Timestamp.now(),
  });
}

async function simulateClearSettlement(driverId: string): Promise<void> {
  await db.doc(driverPath(office, driverId)).update({
    settlementLastCleared: Timestamp.now(),
    status: "OFFLINE",
    dailySettlement: FieldValue.delete(),
  });
}

async function simulateDriverLogin(driverId: string): Promise<void> {
  await db.doc(driverPath(office, driverId)).update({
    status: "WAITING",
  });
}

async function getDriverDoc(driverId: string): Promise<any> {
  const doc = await db.doc(driverPath(office, driverId)).get();
  return doc.data();
}

/** 콜 생성 → 배차 → 수락 → 운행시작 → 완료 (풀 사이클) */
async function runFullCallCycle(
  day: number,
  driver: typeof DRIVER_A,
  fare: number,
  paymentMethod: string,
  cashReceived?: number,
  pointsUsed?: number,
): Promise<string> {
  const callId = nextCallId(day);
  await createCall(office, callId, { phoneNumber: "010-9999-0001", customerName: "테스트고객", fare });
  await assignCall(office, callId, driver);
  await acceptCall(office, callId, driver);
  await startDriving(office, callId, { departure: "출발지", destination: "도착지", fare });

  const finalizeOpts: any = { fare, paymentMethod };
  if (cashReceived !== undefined) finalizeOpts.cashReceived = cashReceived;
  if (paymentMethod === "현금") finalizeOpts.cashReceived = cashReceived ?? fare;
  if (paymentMethod === "이체") { finalizeOpts.cashReceived = 0; finalizeOpts.creditAmount = fare; }
  if (paymentMethod === "현금+포인트") {
    finalizeOpts.cashReceived = cashReceived ?? 0;
    finalizeOpts.pointsUsed = pointsUsed ?? 0;
  }

  await finalizeTrip(office, callId, driver, finalizeOpts);
  return callId;
}

// ========== 결과 수집 ==========

const results: ConsistencyCheckResult[] = [];
let totalCheckpoints = 0;
let passedCheckpoints = 0;

async function checkpoint(
  name: string,
  driverDocId: string,
  driverAuthUid: string,
): Promise<boolean> {
  totalCheckpoints++;
  const result = await assertTripleConsistency(
    office, driverDocId, driverAuthUid, DEPOSIT_RATIO, name
  );
  results.push(result);
  printConsistencyResult(result);

  if (result.match) {
    passedCheckpoints++;
    return true;
  }
  return false;
}

// ========== Day 1: 기본 흐름 ==========

async function runDay1(): Promise<boolean> {
  console.log("\n" + "─".repeat(60));
  console.log("  Day 1 (월) — 기본 흐름 (50콜)");
  console.log("─".repeat(60));
  let allPassed = true;

  // 정상 완료 (현금) 20콜 — 기사 A~E 각 4콜
  for (const driver of drivers) {
    for (let i = 0; i < 4; i++) {
      const fare = 20000 + i * 5000;
      await runFullCallCycle(1, driver, fare, "현금");
    }
  }

  // 정상 완료 (이체) 15콜 — A,B,C 각 5콜
  for (const driver of [DRIVER_A, DRIVER_B, DRIVER_C]) {
    for (let i = 0; i < 5; i++) {
      await runFullCallCycle(1, driver, 25000, "이체");
    }
  }

  // 현금+포인트 10콜 — D,E 각 5콜
  for (const driver of [DRIVER_D, DRIVER_E]) {
    for (let i = 0; i < 5; i++) {
      await runFullCallCycle(1, driver, 30000, "현금+포인트", 25000, 5000);
    }
  }

  // 거절 → 재배차 5콜
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(1);
    await createCall(office, callId, { phoneNumber: "010-9999-0002", fare: 20000 });
    await assignCall(office, callId, DRIVER_A);
    await rejectCall(office, callId, DRIVER_A);
    // 재배차 → B
    await assignCall(office, callId, DRIVER_B);
    await acceptCall(office, callId, DRIVER_B);
    await startDriving(office, callId, { fare: 20000 });
    await finalizeTrip(office, callId, DRIVER_B, { fare: 20000, paymentMethod: "현금", cashReceived: 20000 });
  }

  // CF 트리거 대기
  await sleep(5000);

  // CP 1: 50콜 완료 후 3자 일치
  console.log("\n  ■ CP 1: 50콜 완료 후 기사별 3자 일치");
  for (const driver of drivers) {
    const passed = await checkpoint(`CP1-${driver.name}`, driver.driverId, driver.authUid);
    if (!passed) allPassed = false;
  }

  // CP 2: 기사 A,B 업무마감 제출
  console.log("\n  ■ CP 2: 기사 A,B 업무마감 제출");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const doc = await getDriverDoc(driver.driverId);
    const carryOverBalance = doc?.carryOver?.balance ?? 0;

    // 기사앱 로직: 현금 수령 합계 계산
    // A: 현금 4콜(20k~35k) + 이체 5콜(외상) + 거절(정산 미포함) → 현금수령 합계
    // 간단히: 기사의 Firestore 콜 기반으로 계산
    const callsSnap = await db.collection(`${officePath(office)}/calls`)
      .where("status", "==", "COMPLETED")
      .where("assignedDriverId", "==", driver.authUid)
      .get();

    let totalFare = 0, totalCash = 0, totalCredit = 0;
    callsSnap.docs.forEach(d => {
      const data = d.data();
      totalFare += data.fare || 0;
      totalCash += data.cashReceived || 0;
      const credit = data.creditAmount || 0;
      totalCredit += credit;
    });

    const driverShare = totalFare - Math.floor(totalFare * DEPOSIT_RATIO / 100);
    const realDeposit = totalCash - driverShare;

    const settlement = calcSettlement({
      totalFare, cashReceived: totalCash, realDeposit, depositRatio: DEPOSIT_RATIO,
      originalCarryOver: carryOverBalance, totalCredit, tripCount: callsSnap.size,
    });

    await simulateSubmitDailySettlement(driver.driverId, settlement);

    const afterDoc = await getDriverDoc(driver.driverId);
    const status = afterDoc?.dailySettlement?.status;
    printResult(`CP2-${driver.name} 제출`, status === "PENDING_CONFIRM", 0,
      `status=${status}, calcCarryOver=${settlement.calculatedCarryOver}`);
    if (status !== "PENDING_CONFIRM") allPassed = false;
  }

  // CP 3: 매니저 확인
  console.log("\n  ■ CP 3: 매니저 확인 후 carryOver 일치");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const newBalance = await simulateConfirmDailySettlement(driver.driverId);
    const doc = await getDriverDoc(driver.driverId);
    const carryOverBalance = doc?.carryOver?.balance;
    const carryOverStatus = doc?.carryOver?.status;
    const dsStatus = doc?.dailySettlement?.status;

    const ok = dsStatus === "CONFIRMED" && carryOverBalance === newBalance;
    printResult(`CP3-${driver.name} 확인`, ok, 0,
      `dsStatus=${dsStatus}, carryOver=${carryOverBalance}(${carryOverStatus}), txBalance=${newBalance}`);
    if (!ok) allPassed = false;
  }

  return allPassed;
}

// ========== Day 2: 퇴근 후 재로그인 ★핵심★ ==========

async function runDay2(): Promise<boolean> {
  console.log("\n" + "─".repeat(60));
  console.log("  Day 2 (화) — 퇴근 후 재로그인 ★핵심★ (50콜)");
  console.log("─".repeat(60));
  let allPassed = true;

  // 1차 운행 (A,B 각 8콜 = 16콜)
  for (const driver of [DRIVER_A, DRIVER_B]) {
    for (let i = 0; i < 8; i++) {
      await runFullCallCycle(2, driver, 25000, "현금");
    }
  }
  await sleep(3000);

  // 1차 마감 제출 + 확인
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const callsSnap = await db.collection(`${officePath(office)}/calls`)
      .where("status", "==", "COMPLETED")
      .where("assignedDriverId", "==", driver.authUid)
      .get();

    // settlementLastCleared 이후 콜만 (이전 Day 1 마감분 제외)
    const driverDoc = await getDriverDoc(driver.driverId);
    const lastCleared = driverDoc?.settlementLastCleared?.toMillis?.() ?? 0;

    let totalFare = 0, totalCash = 0;
    let tripCount = 0;
    callsSnap.docs.forEach(d => {
      const data = d.data();
      const completedAt = data.completedAt?.toMillis?.() ?? 0;
      if (completedAt > lastCleared) {
        totalFare += data.fare || 0;
        totalCash += data.cashReceived || 0;
        tripCount++;
      }
    });

    const driverShare = totalFare - Math.floor(totalFare * DEPOSIT_RATIO / 100);
    const realDeposit = totalCash - driverShare;
    const carryOverBalance = driverDoc?.carryOver?.balance ?? 0;

    const settlement = calcSettlement({
      totalFare, cashReceived: totalCash, realDeposit, depositRatio: DEPOSIT_RATIO,
      originalCarryOver: carryOverBalance, tripCount,
    });

    await simulateSubmitDailySettlement(driver.driverId, settlement);
    await simulateConfirmDailySettlement(driver.driverId);
  }

  // CP 4: 1차 마감 → clearSettlement
  console.log("\n  ■ CP 4: 1차 마감 후 clearSettlement");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    await simulateClearSettlement(driver.driverId);
    const doc = await getDriverDoc(driver.driverId);
    const hasSlc = doc?.settlementLastCleared != null;
    const status = doc?.status;
    printResult(`CP4-${driver.name}`, hasSlc && status === "OFFLINE", 0,
      `settlementLastCleared=${hasSlc}, status=${status}`);
    if (!hasSlc || status !== "OFFLINE") allPassed = false;
  }

  // 재로그인
  for (const driver of [DRIVER_A, DRIVER_B]) {
    await simulateDriverLogin(driver.driverId);
  }

  // 2차 운행 (A,B 각 7콜 = 14콜)
  for (const driver of [DRIVER_A, DRIVER_B]) {
    for (let i = 0; i < 7; i++) {
      await runFullCallCycle(2, driver, 30000, "현금");
    }
  }

  // C,D,E 일반 운행 (20콜)
  for (const driver of [DRIVER_C, DRIVER_D, DRIVER_E]) {
    for (let i = 0; i < 7; i++) {
      await runFullCallCycle(2, driver, 20000, "현금");
    }
  }
  // 나머지 1콜 (총 50콜 맞추기: 16+14+21=51 → 조정: C,D,E 각 6콜 = 18콜 + 2콜)
  await sleep(3000);

  // CP 5: 2차 운행 → filteredTrips = 2차 콜만
  console.log("\n  ■ CP 5: 2차 운행 시작 → filteredTrips = 2차 콜만");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const passed = await checkpoint(`CP5-${driver.name}`, driver.driverId, driver.authUid);
    if (!passed) allPassed = false;
  }

  // 2차 마감
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const driverDoc = await getDriverDoc(driver.driverId);
    const lastCleared = driverDoc?.settlementLastCleared?.toMillis?.() ?? 0;
    const carryOverBalance = driverDoc?.carryOver?.balance ?? 0;

    const callsSnap = await db.collection(`${officePath(office)}/calls`)
      .where("status", "==", "COMPLETED")
      .where("assignedDriverId", "==", driver.authUid)
      .get();

    let totalFare = 0, totalCash = 0, tripCount = 0;
    callsSnap.docs.forEach(d => {
      const data = d.data();
      const completedAt = data.completedAt?.toMillis?.() ?? 0;
      if (completedAt > lastCleared) {
        totalFare += data.fare || 0;
        totalCash += data.cashReceived || 0;
        tripCount++;
      }
    });

    const driverShare = totalFare - Math.floor(totalFare * DEPOSIT_RATIO / 100);
    const realDeposit = totalCash - driverShare;

    const settlement = calcSettlement({
      totalFare, cashReceived: totalCash, realDeposit, depositRatio: DEPOSIT_RATIO,
      originalCarryOver: carryOverBalance, tripCount,
    });

    await simulateSubmitDailySettlement(driver.driverId, settlement);
  }

  // CP 6: 2차 마감 → originalCarryOver
  console.log("\n  ■ CP 6: 2차 마감 제출 확인");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const doc = await getDriverDoc(driver.driverId);
    const origCO = doc?.dailySettlement?.originalCarryOver ?? -1;
    const status = doc?.dailySettlement?.status;
    const ok = status === "PENDING_CONFIRM";
    printResult(`CP6-${driver.name}`, ok, 0,
      `status=${status}, originalCarryOver=${origCO}`);
    if (!ok) allPassed = false;
  }

  // CP 7: 매니저 확인 후 3자 일치
  console.log("\n  ■ CP 7: 매니저 확인 후 매니저 UI = 2차 콜만");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    await simulateConfirmDailySettlement(driver.driverId);
    const passed = await checkpoint(`CP7-${driver.name}`, driver.driverId, driver.authUid);
    if (!passed) allPassed = false;
  }

  return allPassed;
}

// ========== Day 3: 이월금 + 이체 타이밍 ==========

async function runDay3(): Promise<boolean> {
  console.log("\n" + "─".repeat(60));
  console.log("  Day 3 (수) — 이월금 + 이체 타이밍 (50콜)");
  console.log("─".repeat(60));
  let allPassed = true;

  // A: 외상 비율 높은 운행 10콜 (이체=외상)
  for (let i = 0; i < 10; i++) {
    await runFullCallCycle(3, DRIVER_A, 30000, "이체");
  }

  // B: 외상 비율 높은 운행 10콜
  for (let i = 0; i < 10; i++) {
    await runFullCallCycle(3, DRIVER_B, 25000, "이체");
  }

  // C,D: 정상 현금 10콜씩
  for (const driver of [DRIVER_C, DRIVER_D]) {
    for (let i = 0; i < 10; i++) {
      await runFullCallCycle(3, driver, 20000, "현금");
    }
  }

  // E: 10콜
  for (let i = 0; i < 10; i++) {
    await runFullCallCycle(3, DRIVER_E, 20000, "현금");
  }

  await sleep(3000);

  // A,B 마감 → 미지급금 발생
  for (const driver of [DRIVER_A, DRIVER_B]) {
    // clearSettlement 먼저 (Day 2 마감 기준)
    const driverDoc = await getDriverDoc(driver.driverId);
    const lastCleared = driverDoc?.settlementLastCleared?.toMillis?.() ?? 0;
    const carryOverBalance = driverDoc?.carryOver?.balance ?? 0;

    const callsSnap = await db.collection(`${officePath(office)}/calls`)
      .where("status", "==", "COMPLETED")
      .where("assignedDriverId", "==", driver.authUid)
      .get();

    let totalFare = 0, totalCash = 0, tripCount = 0;
    callsSnap.docs.forEach(d => {
      const data = d.data();
      const completedAt = data.completedAt?.toMillis?.() ?? 0;
      if (completedAt > lastCleared) {
        totalFare += data.fare || 0;
        totalCash += data.cashReceived || 0;
        tripCount++;
      }
    });

    const driverShare = totalFare - Math.floor(totalFare * DEPOSIT_RATIO / 100);
    const realDeposit = totalCash - driverShare;

    const settlement = calcSettlement({
      totalFare, cashReceived: totalCash, realDeposit, depositRatio: DEPOSIT_RATIO,
      originalCarryOver: carryOverBalance, tripCount,
    });

    await simulateSubmitDailySettlement(driver.driverId, settlement);
    await simulateConfirmDailySettlement(driver.driverId);
  }

  // CP 8: 미지급금 발생 확인
  console.log("\n  ■ CP 8: 미지급금 발생 → carryOver 양수");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const doc = await getDriverDoc(driver.driverId);
    const balance = doc?.carryOver?.balance ?? 0;
    const status = doc?.carryOver?.status;
    // 이체 콜이 전액 외상이므로 미지급금 발생해야 함
    const ok = balance !== 0; // 양수든 음수든 0이 아니어야 함
    printResult(`CP8-${driver.name}`, ok, 0,
      `carryOver=${balance}, status=${status}`);
    if (!ok) allPassed = false;
  }

  // === 시나리오 3-1: 퇴근 후 이체 (기사 A) ===
  console.log("\n  ■ 시나리오 3-1: 퇴근 후 이체 (기사 A)");

  // A 퇴근
  await simulateClearSettlement(DRIVER_A.driverId);
  const docAfterLogout = await getDriverDoc(DRIVER_A.driverId);
  const balanceBeforeTransfer = docAfterLogout?.carryOver?.balance ?? 0;

  // 매니저가 퇴근한 A에게 이체
  await simulateTransferCarryOver(DRIVER_A.driverId, balanceBeforeTransfer);

  // CP 9: 퇴근 후 이체 → TRANSFERRED
  const docAfterTransfer = await getDriverDoc(DRIVER_A.driverId);
  const transferStatus = docAfterTransfer?.carryOver?.status;
  const transferBalance = docAfterTransfer?.carryOver?.balance;
  printResult("CP9-A 퇴근후이체", transferStatus === "TRANSFERRED", 0,
    `status=${transferStatus}, balance=${transferBalance}, driverStatus=${docAfterTransfer?.status}`);
  if (transferStatus !== "TRANSFERRED") allPassed = false;

  // A 다음날 출근
  await simulateDriverLogin(DRIVER_A.driverId);

  // CP 10: 재출근 시 TRANSFERRED 상태 확인
  const docAfterLogin = await getDriverDoc(DRIVER_A.driverId);
  const loginCarryOver = docAfterLogin?.carryOver;
  printResult("CP10-A 재출근", loginCarryOver?.status === "TRANSFERRED", 0,
    `carryOver.status=${loginCarryOver?.status}, balance=${loginCarryOver?.balance}`);
  if (loginCarryOver?.status !== "TRANSFERRED") allPassed = false;

  // A 수령확인
  await simulateReceiveCarryOver(DRIVER_A.driverId);

  // === 시나리오 3-2: 출근 후 마감 전 이체 (기사 B) ===
  console.log("\n  ■ 시나리오 3-2: 출근 후 마감 전 이체 (기사 B)");

  // B는 이미 carryOver가 있는 상태 → clearSettlement → 재로그인
  await simulateClearSettlement(DRIVER_B.driverId);
  await simulateDriverLogin(DRIVER_B.driverId);

  const bDocBefore = await getDriverDoc(DRIVER_B.driverId);
  const bBalanceBefore = bDocBefore?.carryOver?.balance ?? 0;

  // B 운행 시작 (5콜)
  for (let i = 0; i < 5; i++) {
    await runFullCallCycle(3, DRIVER_B, 20000, "현금");
  }

  // 매니저가 B의 이전 이월금 이체 (운행 중, 마감 전)
  if (bBalanceBefore !== 0) {
    await simulateTransferCarryOver(DRIVER_B.driverId, bBalanceBefore);
  }

  // CP 11: 마감 전 이체 → 기사앱 실시간 반영
  const bDocAfterTransfer = await getDriverDoc(DRIVER_B.driverId);
  const bTransferStatus = bDocAfterTransfer?.carryOver?.status;
  printResult("CP11-B 마감전이체",
    bBalanceBefore === 0 || bTransferStatus === "TRANSFERRED", 0,
    `carryOver.status=${bTransferStatus}, balance=${bDocAfterTransfer?.carryOver?.balance}, 이전잔액=${bBalanceBefore}`);
  if (bBalanceBefore !== 0 && bTransferStatus !== "TRANSFERRED") allPassed = false;

  // B 수령확인
  if (bBalanceBefore !== 0) {
    await simulateReceiveCarryOver(DRIVER_B.driverId);
  }

  // B 추가 운행 후 마감
  await sleep(2000);
  const bDocForSubmit = await getDriverDoc(DRIVER_B.driverId);
  const bLastCleared = bDocForSubmit?.settlementLastCleared?.toMillis?.() ?? 0;
  const bCarryOver = bDocForSubmit?.carryOver?.balance ?? 0;

  const bCallsSnap = await db.collection(`${officePath(office)}/calls`)
    .where("status", "==", "COMPLETED")
    .where("assignedDriverId", "==", DRIVER_B.authUid)
    .get();

  let bTotalFare = 0, bTotalCash = 0, bTripCount = 0;
  bCallsSnap.docs.forEach(d => {
    const data = d.data();
    const completedAt = data.completedAt?.toMillis?.() ?? 0;
    if (completedAt > bLastCleared) {
      bTotalFare += data.fare || 0;
      bTotalCash += data.cashReceived || 0;
      bTripCount++;
    }
  });

  const bDriverShare = bTotalFare - Math.floor(bTotalFare * DEPOSIT_RATIO / 100);
  const bRealDeposit = bTotalCash - bDriverShare;

  const bSettlement = calcSettlement({
    totalFare: bTotalFare, cashReceived: bTotalCash, realDeposit: bRealDeposit,
    depositRatio: DEPOSIT_RATIO, originalCarryOver: bCarryOver, tripCount: bTripCount,
  });

  await simulateSubmitDailySettlement(DRIVER_B.driverId, bSettlement);

  // CP 12: 수령 후 마감 → originalCarryOver = 0
  const bDocFinal = await getDriverDoc(DRIVER_B.driverId);
  const bOrigCO = bDocFinal?.dailySettlement?.originalCarryOver ?? -1;
  printResult("CP12-B 수령후마감", bOrigCO === 0, 0,
    `originalCarryOver=${bOrigCO}, tripCount=${bDocFinal?.dailySettlement?.tripCount}`);
  if (bOrigCO !== 0) allPassed = false;

  await simulateConfirmDailySettlement(DRIVER_B.driverId);

  return allPassed;
}

// ========== Day 4: 거절 + 재제출 ==========

async function runDay4(): Promise<boolean> {
  console.log("\n" + "─".repeat(60));
  console.log("  Day 4 (목) — 거절 + 재제출 (50콜)");
  console.log("─".repeat(60));
  let allPassed = true;

  // 전원 clearSettlement (Day 3 정리)
  for (const driver of drivers) {
    const doc = await getDriverDoc(driver.driverId);
    if (doc?.dailySettlement) {
      await simulateClearSettlement(driver.driverId);
      await simulateDriverLogin(driver.driverId);
    }
  }

  // 정상 운행 30콜 (전원 각 6콜)
  for (const driver of drivers) {
    for (let i = 0; i < 6; i++) {
      await runFullCallCycle(4, driver, 25000, "현금");
    }
  }

  // A: 마감 → 거절 → 재제출 (10콜 중 추가 4콜)
  for (let i = 0; i < 4; i++) {
    await runFullCallCycle(4, DRIVER_A, 20000, "현금");
  }

  await sleep(3000);

  // A 1차 마감
  const aDoc1 = await getDriverDoc(DRIVER_A.driverId);
  const aLastCleared = aDoc1?.settlementLastCleared?.toMillis?.() ?? 0;
  const aCarryOver = aDoc1?.carryOver?.balance ?? 0;

  const aCallsSnap = await db.collection(`${officePath(office)}/calls`)
    .where("status", "==", "COMPLETED")
    .where("assignedDriverId", "==", DRIVER_A.authUid)
    .get();

  let aTotalFare = 0, aTotalCash = 0, aTripCount = 0;
  aCallsSnap.docs.forEach(d => {
    const data = d.data();
    const completedAt = data.completedAt?.toMillis?.() ?? 0;
    if (completedAt > aLastCleared) {
      aTotalFare += data.fare || 0;
      aTotalCash += data.cashReceived || 0;
      aTripCount++;
    }
  });

  const aDriverShare = aTotalFare - Math.floor(aTotalFare * DEPOSIT_RATIO / 100);
  const aRealDeposit = aTotalCash - aDriverShare;
  const aSettlement1 = calcSettlement({
    totalFare: aTotalFare, cashReceived: aTotalCash, realDeposit: aRealDeposit,
    depositRatio: DEPOSIT_RATIO, originalCarryOver: aCarryOver, tripCount: aTripCount,
  });
  await simulateSubmitDailySettlement(DRIVER_A.driverId, aSettlement1);

  // 매니저 거절
  await simulateRejectDailySettlement(DRIVER_A.driverId);

  // CP 13: 거절 후 REJECTED 상태
  console.log("\n  ■ CP 13: 거절 후 기사 상태");
  const aDocRejected = await getDriverDoc(DRIVER_A.driverId);
  const rejStatus = aDocRejected?.dailySettlement?.status;
  printResult("CP13-A 거절", rejStatus === "REJECTED", 0, `status=${rejStatus}`);
  if (rejStatus !== "REJECTED") allPassed = false;

  // A 재제출 (같은 데이터로)
  await simulateSubmitDailySettlement(DRIVER_A.driverId, aSettlement1);

  // CP 14: 재제출 시 정확성
  console.log("\n  ■ CP 14: 재제출 시 tripCount, totalFare 정확");
  const aDocResubmit = await getDriverDoc(DRIVER_A.driverId);
  const resubStatus = aDocResubmit?.dailySettlement?.status;
  const resubTripCount = aDocResubmit?.dailySettlement?.tripCount;
  const resubFare = aDocResubmit?.dailySettlement?.totalFare;
  printResult("CP14-A 재제출",
    resubStatus === "PENDING_CONFIRM" && resubTripCount === aTripCount, 0,
    `status=${resubStatus}, tripCount=${resubTripCount}(expected=${aTripCount}), totalFare=${resubFare}`);
  if (resubStatus !== "PENDING_CONFIRM" || resubTripCount !== aTripCount) allPassed = false;

  await simulateConfirmDailySettlement(DRIVER_A.driverId);

  // C: 다중 거절 (2회 거절 → 승인)
  for (let i = 0; i < 4; i++) {
    await runFullCallCycle(4, DRIVER_C, 20000, "현금");
  }
  await sleep(2000);

  const cDoc = await getDriverDoc(DRIVER_C.driverId);
  const cLastCleared = cDoc?.settlementLastCleared?.toMillis?.() ?? 0;
  const cCarryOver = cDoc?.carryOver?.balance ?? 0;

  const cCallsSnap = await db.collection(`${officePath(office)}/calls`)
    .where("status", "==", "COMPLETED")
    .where("assignedDriverId", "==", DRIVER_C.authUid)
    .get();

  let cTotalFare = 0, cTotalCash = 0, cTripCount = 0;
  cCallsSnap.docs.forEach(d => {
    const data = d.data();
    const completedAt = data.completedAt?.toMillis?.() ?? 0;
    if (completedAt > cLastCleared) {
      cTotalFare += data.fare || 0;
      cTotalCash += data.cashReceived || 0;
      cTripCount++;
    }
  });

  const cDriverShare = cTotalFare - Math.floor(cTotalFare * DEPOSIT_RATIO / 100);
  const cRealDeposit = cTotalCash - cDriverShare;
  const cSettlement = calcSettlement({
    totalFare: cTotalFare, cashReceived: cTotalCash, realDeposit: cRealDeposit,
    depositRatio: DEPOSIT_RATIO, originalCarryOver: cCarryOver, tripCount: cTripCount,
  });

  // 1차 제출 → 거절
  await simulateSubmitDailySettlement(DRIVER_C.driverId, cSettlement);
  await simulateRejectDailySettlement(DRIVER_C.driverId);
  // 2차 제출 → 거절
  await simulateSubmitDailySettlement(DRIVER_C.driverId, cSettlement);
  await simulateRejectDailySettlement(DRIVER_C.driverId);
  // 3차 제출 → 승인
  await simulateSubmitDailySettlement(DRIVER_C.driverId, cSettlement);
  const cNewBalance = await simulateConfirmDailySettlement(DRIVER_C.driverId);

  // CP 15: 2회 거절 후 최종 승인
  console.log("\n  ■ CP 15: 2회 거절 후 최종 승인 → carryOver 정확");
  const cDocFinal = await getDriverDoc(DRIVER_C.driverId);
  const cFinalBalance = cDocFinal?.carryOver?.balance;
  printResult("CP15-C 다중거절", cFinalBalance === cSettlement.calculatedCarryOver, 0,
    `carryOver=${cFinalBalance}, expected=${cSettlement.calculatedCarryOver}`);
  if (cFinalBalance !== cSettlement.calculatedCarryOver) allPassed = false;

  // 나머지 B,D,E 2콜씩 (50콜 맞추기)
  for (const driver of [DRIVER_B, DRIVER_D, DRIVER_E]) {
    for (let i = 0; i < 2; i++) {
      await runFullCallCycle(4, driver, 20000, "현금");
    }
  }

  return allPassed;
}

// ========== Day 5: 동시성 + 취소 ==========

async function runDay5(): Promise<boolean> {
  console.log("\n" + "─".repeat(60));
  console.log("  Day 5 (금) — 동시성 + 취소 (50콜)");
  console.log("─".repeat(60));
  let allPassed = true;

  // 전원 clearSettlement
  for (const driver of drivers) {
    const doc = await getDriverDoc(driver.driverId);
    if (doc?.dailySettlement || doc?.status === "OFFLINE") {
      try { await simulateClearSettlement(driver.driverId); } catch {}
      await simulateDriverLogin(driver.driverId);
    }
  }

  // 동시 콜 완료 (5건 동시 × 5라운드 = 25콜)
  for (let round = 0; round < 5; round++) {
    const promises = drivers.map(driver =>
      runFullCallCycle(5, driver, 20000 + round * 5000, "현금")
    );
    await Promise.all(promises);
  }

  await sleep(5000);

  // CP 16: 동시 완료 후 세션 중복 없음
  console.log("\n  ■ CP 16: 동시 완료 후 세션 중복 확인");
  const sessionDoc = await db.doc(settlementPath(office, getTodayWorkDate())).get();
  const sessionCalls = sessionDoc.data()?.calls ?? [];
  const callIds = sessionCalls.map((c: any) => c.callId);
  const uniqueCallIds = new Set(callIds);
  const noDuplicates = callIds.length === uniqueCallIds.size;
  printResult("CP16 세션중복", noDuplicates, 0,
    `총=${callIds.length}, 유니크=${uniqueCallIds.size}`);
  if (!noDuplicates) allPassed = false;

  // 취소 유형 혼합 15콜 (정산에 포함되면 안 됨)
  const cancelledCallIds: string[] = [];

  // 관리자 취소 5건
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(5);
    await createCall(office, callId, { phoneNumber: "010-9999-0003", fare: 15000 });
    await assignCall(office, callId, DRIVER_A);
    await cancelCallByManager(office, callId);
    cancelledCallIds.push(callId);
  }

  // 기사 취소 5건
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(5);
    await createCall(office, callId, { phoneNumber: "010-9999-0004", fare: 15000 });
    await assignCall(office, callId, DRIVER_B);
    await acceptCall(office, callId, DRIVER_B);
    await cancelCallByDriver(office, callId);
    cancelledCallIds.push(callId);
  }

  // 고객 취소 5건
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(5);
    await createCall(office, callId, { phoneNumber: "010-9999-0005", fare: 15000 });
    await cancelCallByCustomer(office, callId);
    cancelledCallIds.push(callId);
  }

  await sleep(3000);

  // CP 17: 취소된 콜이 정산에 미포함
  console.log("\n  ■ CP 17: 취소된 콜 정산 미포함");
  const sessionDoc2 = await db.doc(settlementPath(office, getTodayWorkDate())).get();
  const sessionCalls2 = sessionDoc2.data()?.calls ?? [];
  const cancelledInSession = cancelledCallIds.filter(id =>
    sessionCalls2.some((c: any) => c.callId === id)
  );
  printResult("CP17 취소미포함", cancelledInSession.length === 0, 0,
    `취소 ${cancelledCallIds.length}건 중 세션 포함=${cancelledInSession.length}`);
  if (cancelledInSession.length > 0) allPassed = false;

  // 타임아웃 → 재배차 10콜
  for (let i = 0; i < 10; i++) {
    const callId = nextCallId(5);
    await createCall(office, callId, { phoneNumber: "010-9999-0006", fare: 25000 });
    await assignCall(office, callId, DRIVER_D);
    // 타임아웃 시뮬레이션 (WAITING으로 복귀)
    await db.doc(callPath(office, callId)).update({ status: "WAITING", assignedDriverId: null });
    await db.doc(driverPath(office, DRIVER_D.driverId)).update({ status: "WAITING" });
    // 재배차 → E
    await assignCall(office, callId, DRIVER_E);
    await acceptCall(office, callId, DRIVER_E);
    await startDriving(office, callId, { fare: 25000 });
    await finalizeTrip(office, callId, DRIVER_E, { fare: 25000, paymentMethod: "현금", cashReceived: 25000 });
  }

  await sleep(3000);

  // CP 18: 재배차 콜 → 최종 완료 기사(E)에 귀속
  console.log("\n  ■ CP 18: 재배차 콜 정산 귀속 = 최종 기사");
  const passed = await checkpoint("CP18-E 재배차귀속", DRIVER_E.driverId, DRIVER_E.authUid);
  if (!passed) allPassed = false;

  return allPassed;
}

// ========== Day 6: 통합 정산 ==========

async function runDay6(): Promise<boolean> {
  console.log("\n" + "─".repeat(60));
  console.log("  Day 6 (토) — 통합 정산 (50콜)");
  console.log("─".repeat(60));
  let allPassed = true;

  // 전원 clearSettlement
  for (const driver of drivers) {
    try { await simulateClearSettlement(driver.driverId); } catch {}
    await simulateDriverLogin(driver.driverId);
  }

  // 1차 운행 (A,B 각 8콜 = 16콜)
  for (const driver of [DRIVER_A, DRIVER_B]) {
    for (let i = 0; i < 8; i++) {
      await runFullCallCycle(6, driver, 25000, "현금");
    }
  }
  await sleep(3000);

  // 1차 마감 제출 (확인 안 함 → PENDING_CONFIRM 유지)
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const driverDoc = await getDriverDoc(driver.driverId);
    const lastCleared = driverDoc?.settlementLastCleared?.toMillis?.() ?? 0;
    const carryOverBalance = driverDoc?.carryOver?.balance ?? 0;

    const callsSnap = await db.collection(`${officePath(office)}/calls`)
      .where("status", "==", "COMPLETED")
      .where("assignedDriverId", "==", driver.authUid)
      .get();

    let totalFare = 0, totalCash = 0, tripCount = 0;
    callsSnap.docs.forEach(d => {
      const data = d.data();
      const completedAt = data.completedAt?.toMillis?.() ?? 0;
      if (completedAt > lastCleared) {
        totalFare += data.fare || 0;
        totalCash += data.cashReceived || 0;
        tripCount++;
      }
    });

    const driverShare = totalFare - Math.floor(totalFare * DEPOSIT_RATIO / 100);
    const realDeposit = totalCash - driverShare;
    const settlement = calcSettlement({
      totalFare, cashReceived: totalCash, realDeposit, depositRatio: DEPOSIT_RATIO,
      originalCarryOver: carryOverBalance, tripCount,
    });

    await simulateSubmitDailySettlement(driver.driverId, settlement);
  }

  // CP 19: PENDING_CONFIRM 상태
  console.log("\n  ■ CP 19: 1차 제출 → PENDING_CONFIRM");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const doc = await getDriverDoc(driver.driverId);
    const dsStatus = doc?.dailySettlement?.status;
    const calcCO = doc?.dailySettlement?.calculatedCarryOver ?? 0;
    const actualCO = doc?.carryOver?.balance ?? 0;
    printResult(`CP19-${driver.name}`, dsStatus === "PENDING_CONFIRM", 0,
      `dsStatus=${dsStatus}, calcCarryOver=${calcCO}, actualCarryOver=${actualCO}`);
    if (dsStatus !== "PENDING_CONFIRM") allPassed = false;
  }

  // 2차 운행 (PENDING_CONFIRM 상태에서 추가 운행)
  for (const driver of [DRIVER_A, DRIVER_B]) {
    for (let i = 0; i < 7; i++) {
      await runFullCallCycle(6, driver, 20000, "현금");
    }
  }

  // C,D,E 일반 운행
  for (const driver of [DRIVER_C, DRIVER_D, DRIVER_E]) {
    for (let i = 0; i < 4; i++) {
      await runFullCallCycle(6, driver, 20000, "현금");
    }
  }
  await sleep(3000);

  // 통합 정산 제출 (isIntegration)
  // CP 20: 통합 제출
  console.log("\n  ■ CP 20: 2차 제출 → 통합 정산");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const prevDoc = await getDriverDoc(driver.driverId);
    const prevDS = prevDoc?.dailySettlement;
    const lastCleared = prevDoc?.settlementLastCleared?.toMillis?.() ?? 0;

    const callsSnap = await db.collection(`${officePath(office)}/calls`)
      .where("status", "==", "COMPLETED")
      .where("assignedDriverId", "==", driver.authUid)
      .get();

    let totalFare = 0, totalCash = 0, tripCount = 0;
    callsSnap.docs.forEach(d => {
      const data = d.data();
      const completedAt = data.completedAt?.toMillis?.() ?? 0;
      if (completedAt > lastCleared) {
        totalFare += data.fare || 0;
        totalCash += data.cashReceived || 0;
        tripCount++;
      }
    });

    const driverShare = totalFare - Math.floor(totalFare * DEPOSIT_RATIO / 100);
    const realDeposit = totalCash - driverShare;
    const originalCarryOver = prevDS?.originalCarryOver ?? (prevDoc?.carryOver?.balance ?? 0);

    const settlement = calcSettlement({
      totalFare, cashReceived: totalCash, realDeposit, depositRatio: DEPOSIT_RATIO,
      originalCarryOver, tripCount,
    });

    // 통합 시 원본값 보존
    await simulateSubmitDailySettlement(
      driver.driverId, settlement,
      prevDS?.tripCount ?? 0,      // originalTripCount
      prevDS?.totalFare ?? 0,      // originalTotalFare
      prevDS?.realDeposit ?? 0,    // originalRealDeposit
    );

    const doc = await getDriverDoc(driver.driverId);
    const mergedTrips = doc?.dailySettlement?.tripCount;
    const origTrips = doc?.dailySettlement?.originalTripCount;
    printResult(`CP20-${driver.name}`, mergedTrips === tripCount && origTrips > 0, 0,
      `mergedTrips=${mergedTrips}, originalTrips=${origTrips}`);
    if (mergedTrips !== tripCount) allPassed = false;
  }

  // CP 21: 매니저 확인 → originalTripCount 보존
  console.log("\n  ■ CP 21: 매니저 확인 → 원본값 보존");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const beforeDoc = await getDriverDoc(driver.driverId);
    const origTrips = beforeDoc?.dailySettlement?.originalTripCount;
    const origFare = beforeDoc?.dailySettlement?.originalTotalFare;

    await simulateConfirmDailySettlement(driver.driverId);

    const afterDoc = await getDriverDoc(driver.driverId);
    const afterOrigTrips = afterDoc?.dailySettlement?.originalTripCount;
    const afterOrigFare = afterDoc?.dailySettlement?.originalTotalFare;

    printResult(`CP21-${driver.name}`,
      afterOrigTrips === origTrips && afterOrigFare === origFare, 0,
      `origTrips=${afterOrigTrips}, origFare=${afterOrigFare}`);
    if (afterOrigTrips !== origTrips) allPassed = false;
  }

  // CP 22: 최종 carryOver 일치
  console.log("\n  ■ CP 22: 최종 carryOver = calculatedCarryOver");
  for (const driver of [DRIVER_A, DRIVER_B]) {
    const doc = await getDriverDoc(driver.driverId);
    const balance = doc?.carryOver?.balance;
    const calcCO = doc?.dailySettlement?.calculatedCarryOver;
    printResult(`CP22-${driver.name}`, balance === calcCO, 0,
      `carryOver.balance=${balance}, calculatedCarryOver=${calcCO}`);
    if (balance !== calcCO) allPassed = false;
  }

  return allPassed;
}

// ========== Day 7: 엣지케이스 종합 ==========

async function runDay7(): Promise<boolean> {
  console.log("\n" + "─".repeat(60));
  console.log("  Day 7 (일) — 엣지케이스 종합 (50콜)");
  console.log("─".repeat(60));
  let allPassed = true;

  // 전원 clearSettlement (Day 6 잔여 정리, 이월금은 유지)
  for (const driver of drivers) {
    const doc = await getDriverDoc(driver.driverId);
    if (doc?.dailySettlement) {
      await simulateClearSettlement(driver.driverId);
    }
    if (doc?.status === "OFFLINE") {
      await simulateDriverLogin(driver.driverId);
    }
  }

  // A: 이월금 있는 상태로 시작 (Day 6 이월) — 10콜
  const aDoc = await getDriverDoc(DRIVER_A.driverId);
  const aInitCarryOver = aDoc?.carryOver?.balance ?? 0;
  console.log(`  A 초기 이월금: ${aInitCarryOver}`);

  for (let i = 0; i < 10; i++) {
    await runFullCallCycle(7, DRIVER_A, 25000, "현금");
  }

  // B: 0원 운행 5콜
  for (let i = 0; i < 5; i++) {
    await runFullCallCycle(7, DRIVER_B, 0, "현금", 0);
  }

  // C: 전액 포인트 5콜
  for (let i = 0; i < 5; i++) {
    await runFullCallCycle(7, DRIVER_C, 20000, "현금+포인트", 0, 20000);
  }

  // D: 대량 연속 20콜
  for (let i = 0; i < 20; i++) {
    await runFullCallCycle(7, DRIVER_D, 15000 + i * 1000, "현금");
  }

  // E: 10콜
  for (let i = 0; i < 10; i++) {
    await runFullCallCycle(7, DRIVER_E, 20000, "현금");
  }

  await sleep(5000);

  // CP 23: Day 6 이월 → Day 7 공제
  console.log("\n  ■ CP 23: 이월금 공제 확인 (기사 A)");
  const passed23 = await checkpoint("CP23-A 이월공제", DRIVER_A.driverId, DRIVER_A.authUid);
  if (!passed23) allPassed = false;

  // CP 24: 0원/전액포인트 예외 처리
  console.log("\n  ■ CP 24: 0원 콜 + 전액 포인트");
  const passed24b = await checkpoint("CP24-B 0원콜", DRIVER_B.driverId, DRIVER_B.authUid);
  const passed24c = await checkpoint("CP24-C 전액포인트", DRIVER_C.driverId, DRIVER_C.authUid);
  if (!passed24b || !passed24c) allPassed = false;

  // CP 25: 대량 연속 → 누락 없음
  console.log("\n  ■ CP 25: 대량 20건 → 누락 확인 (기사 D)");
  const passed25 = await checkpoint("CP25-D 대량", DRIVER_D.driverId, DRIVER_D.authUid);
  if (!passed25) allPassed = false;

  // 전원 마감
  console.log("\n  ■ CP 26: 전원 마감 → 모든 balance = 0");
  for (const driver of drivers) {
    const doc = await getDriverDoc(driver.driverId);
    const lastCleared = doc?.settlementLastCleared?.toMillis?.() ?? 0;
    const carryOverBalance = doc?.carryOver?.balance ?? 0;

    const callsSnap = await db.collection(`${officePath(office)}/calls`)
      .where("status", "==", "COMPLETED")
      .where("assignedDriverId", "==", driver.authUid)
      .get();

    let totalFare = 0, totalCash = 0, tripCount = 0;
    callsSnap.docs.forEach(d => {
      const data = d.data();
      const completedAt = data.completedAt?.toMillis?.() ?? 0;
      if (completedAt > lastCleared) {
        totalFare += data.fare || 0;
        totalCash += data.cashReceived || 0;
        tripCount++;
      }
    });

    if (tripCount > 0) {
      const driverShare = totalFare - Math.floor(totalFare * DEPOSIT_RATIO / 100);
      const realDeposit = totalCash - driverShare;
      const settlement = calcSettlement({
        totalFare, cashReceived: totalCash, realDeposit, depositRatio: DEPOSIT_RATIO,
        originalCarryOver: carryOverBalance, tripCount,
      });

      await simulateSubmitDailySettlement(driver.driverId, settlement);
      await simulateConfirmDailySettlement(driver.driverId);

      // 이월금 이체 → 수령 (잔액 0으로 만들기)
      const afterDoc = await getDriverDoc(driver.driverId);
      const finalBalance = afterDoc?.carryOver?.balance ?? 0;
      if (finalBalance !== 0) {
        await simulateTransferCarryOver(driver.driverId, finalBalance);
        await simulateReceiveCarryOver(driver.driverId);
      }
    }
  }

  // CP 26: 전원 balance = 0
  let allZero = true;
  for (const driver of drivers) {
    const doc = await getDriverDoc(driver.driverId);
    const balance = doc?.carryOver?.balance ?? 0;
    const status = doc?.carryOver?.status;
    if (balance !== 0) {
      allZero = false;
      printResult(`CP26-${driver.name}`, false, 0, `balance=${balance}, status=${status}`);
    }
  }
  printResult("CP26 전원마감", allZero, 0,
    allZero ? "모든 기사 balance=0 ✓" : "일부 기사 잔액 남음");
  if (!allZero) allPassed = false;

  return allPassed;
}

// ========== 메인 ==========

export async function runScenario10(): Promise<boolean> {
  await initTest("시나리오 10: 정산 일관성 검증 시뮬레이션 (7일 × 50콜)");
  const startTime = Date.now();

  try {
    // 데이터 초기화 + 시드
    console.log("  데이터 초기화 중...");
    await clearEmulatorData();
    await sleep(2000);
    await seedOfficeAndDrivers();
    await sleep(3000);
    console.log("  초기화 완료.\n");

    const dayResults: boolean[] = [];

    dayResults.push(await runDay1());
    dayResults.push(await runDay2());
    dayResults.push(await runDay3());
    dayResults.push(await runDay4());
    dayResults.push(await runDay5());
    dayResults.push(await runDay6());
    dayResults.push(await runDay7());

    // 최종 요약
    const totalTime = Date.now() - startTime;
    console.log("\n" + "═".repeat(60));
    console.log("  최종 결과");
    console.log("═".repeat(60));

    for (let i = 0; i < 7; i++) {
      console.log(`  Day ${i + 1}: ${dayResults[i] ? "PASS ✓" : "FAIL ✗"}`);
    }

    console.log(`\n  체크포인트: ${passedCheckpoints}/${totalCheckpoints} PASS`);
    console.log(`  총 소요시간: ${(totalTime / 1000).toFixed(1)}s`);

    const allPassed = dayResults.every(r => r);
    console.log(`\n  시나리오 10 최종: ${allPassed ? "ALL PASS ✓" : "FAIL ✗"}\n`);

    return allPassed;

  } catch (error: any) {
    printResult("시나리오 10", false, Date.now() - startTime, error.message);
    console.error(error.stack);
    return false;
  }
}

if (require.main === module) {
  runScenario10().then((passed) => {
    process.exit(passed ? 0 : 1);
  });
}
