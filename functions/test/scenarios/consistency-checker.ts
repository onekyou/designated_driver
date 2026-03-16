/**
 * 3자 일치 검증 유틸리티
 * Firestore 실제값 ↔ 콜매니저 UI 계산 ↔ 기사앱 UI 계산
 *
 * 콜매니저: SettlementViewModel.kt의 filteredTrips + driverLastClearedMap 로직 재현
 * 기사앱: DriverViewModel.kt의 loadTodaySettlement + settlementLastCleared 로직 재현
 */

import { db, Timestamp } from "../setup/emulator-config";
import { Office, officePath, driverPath, settlementPath, getTodayWorkDate } from "../setup/constants";

// ========== 타입 정의 ==========

export interface ConsistencyCheckResult {
  checkpoint: string;
  driverId: string;
  firestore: {
    sessionTotalFare: number;
    sessionCallCount: number;
    driverCallCount: number;      // 이 기사의 콜 수 (세션 내)
    driverTotalFare: number;      // 이 기사의 총 운행료 (세션 내)
    carryOverBalance: number;
    carryOverStatus: string;
    dailySettlementStatus: string | null;
    calculatedCarryOver: number;
  };
  managerCalc: {
    filteredTripCount: number;
    totalFare: number;
    deposit: number;
    totalCredit: number;
    realDeposit: number;
    displayedCarryOver: number;
  };
  driverCalc: {
    tripCount: number;
    totalFare: number;
    officeDeposit: number;
    driverShare: number;
    totalCredit: number;
    displayedCarryOver: number;
  };
  match: boolean;
  mismatches: string[];
}

export interface CallData {
  callId: string;
  driverId: string;      // assignedDriverId (authUid)
  driverDocId: string;   // designated_drivers 문서 ID
  fare: number;
  paymentMethod: string;
  cashReceived: number;
  creditAmount: number;
  pointsUsed: number;
  completedAt: number;   // millis
  status: string;
}

// ========== Firestore에서 콜 데이터 읽기 ==========

/**
 * 사무실의 COMPLETED 콜 목록을 Firestore에서 읽기
 */
export async function getCompletedCalls(office: Office): Promise<CallData[]> {
  const callsRef = db.collection(`${officePath(office)}/calls`);
  const snap = await callsRef.where("status", "==", "COMPLETED").get();

  return snap.docs.map(doc => {
    const d = doc.data();
    const completedAt = d.completedAt instanceof Timestamp
      ? d.completedAt.toMillis()
      : (typeof d.completedAt === "number" ? d.completedAt : 0);

    return {
      callId: doc.id,
      driverId: d.assignedDriverId || "",
      driverDocId: "",  // 나중에 매핑
      fare: d.fare || d.fare_final || d.fare_set || 0,
      paymentMethod: d.paymentMethod || "현금",
      cashReceived: d.cashReceived || 0,
      creditAmount: d.creditAmount || 0,
      pointsUsed: d.pointsUsed || 0,
      completedAt,
      status: d.status,
    };
  });
}

/**
 * 기사 문서에서 settlementLastCleared 읽기
 */
export async function getDriverSettlementInfo(office: Office, driverDocId: string): Promise<{
  settlementLastCleared: number;
  carryOverBalance: number;
  carryOverStatus: string;
  dailySettlementStatus: string | null;
  calculatedCarryOver: number;
}> {
  const doc = await db.doc(driverPath(office, driverDocId)).get();
  const data = doc.data() || {};

  const slc = data.settlementLastCleared;
  const settlementLastCleared = slc instanceof Timestamp
    ? slc.toMillis()
    : (typeof slc === "number" ? slc : 0);

  return {
    settlementLastCleared,
    carryOverBalance: data.carryOver?.balance ?? 0,
    carryOverStatus: data.carryOver?.status ?? "SETTLED",
    dailySettlementStatus: data.dailySettlement?.status ?? null,
    calculatedCarryOver: data.dailySettlement?.calculatedCarryOver ?? 0,
  };
}

// ========== 콜매니저 UI 계산 로직 재현 ==========
// SettlementViewModel.kt: filteredTrips = trips.filter { completedAt > driverLastClearedMap[driverId] }

export function replicateManagerCalc(
  allCalls: CallData[],
  driverDocId: string,
  driverAuthUid: string,
  depositRatio: number,
  driverLastCleared: number
): ConsistencyCheckResult["managerCalc"] {
  // filteredTrips: 기사별 마감 시점 이후 콜만
  const filteredTrips = allCalls.filter(c =>
    c.driverId === driverAuthUid && c.completedAt > driverLastCleared
  );

  const totalFare = filteredTrips.reduce((sum, c) => sum + c.fare, 0);
  const deposit = Math.floor(totalFare * depositRatio / 100);

  // totalCredit 계산 (DriverSummaryScreen.kt 로직)
  const totalCredit = filteredTrips.reduce((sum, c) => {
    if (c.paymentMethod === "현금") return sum;
    if (c.paymentMethod === "현금+포인트") {
      return sum + (c.cashReceived > 0 ? c.fare - c.cashReceived : c.fare);
    }
    return sum + c.fare; // 이체, 외상 = 전액 외상
  }, 0);

  const realDeposit = deposit - totalCredit;

  // displayedCarryOver: carryOverBalance는 별도로 Firestore에서 읽으므로 여기서는 계산하지 않음
  return {
    filteredTripCount: filteredTrips.length,
    totalFare,
    deposit,
    totalCredit,
    realDeposit,
    displayedCarryOver: 0, // 체크포인트별로 별도 설정
  };
}

// ========== 기사앱 UI 계산 로직 재현 ==========
// DriverViewModel.kt: loadTodaySettlement에서 completedAt > settlementLastCleared 필터

export function replicateDriverCalc(
  allCalls: CallData[],
  driverAuthUid: string,
  depositRatio: number,
  settlementLastCleared: number
): ConsistencyCheckResult["driverCalc"] {
  // 기사앱: completedAt > lastClearedMillis
  const trips = allCalls.filter(c =>
    c.driverId === driverAuthUid && c.completedAt > settlementLastCleared
  );

  const totalFare = trips.reduce((sum, c) => sum + c.fare, 0);
  const officeDeposit = Math.floor(totalFare * depositRatio / 100);
  const driverShare = totalFare - officeDeposit;

  // totalCredit = totalFare - totalCashReceived (포인트도 외상에 포함)
  const totalCashReceived = trips.reduce((sum, c) => {
    if (c.paymentMethod === "현금") return sum + c.cashReceived;
    if (c.paymentMethod === "현금+포인트") return sum + c.cashReceived;
    return sum; // 이체/외상은 현금 0
  }, 0);
  const totalPointsUsed = trips.reduce((sum, c) => sum + c.pointsUsed, 0);
  const totalCredit = totalFare - totalCashReceived;

  return {
    tripCount: trips.length,
    totalFare,
    officeDeposit,
    driverShare,
    totalCredit,
    displayedCarryOver: 0, // 체크포인트별로 별도 설정
  };
}

// ========== 3자 일치 검증 ==========

export async function assertTripleConsistency(
  office: Office,
  driverDocId: string,
  driverAuthUid: string,
  depositRatio: number,
  checkpoint: string,
  workDate?: string
): Promise<ConsistencyCheckResult> {
  const allCalls = await getCompletedCalls(office);
  const driverInfo = await getDriverSettlementInfo(office, driverDocId);

  // 세션에서 이 기사의 콜 통계
  const date = workDate ?? getTodayWorkDate();
  const sessionDoc = await db.doc(settlementPath(office, date)).get();
  const sessionData = sessionDoc.exists ? sessionDoc.data() : null;

  const sessionCalls = sessionData?.calls ?? [];
  const driverSessionCalls = sessionCalls.filter((c: any) => c.driverId === driverAuthUid);

  // 콜매니저 계산 (driverLastClearedMap 기준 = settlementLastCleared)
  const managerCalc = replicateManagerCalc(
    allCalls, driverDocId, driverAuthUid, depositRatio, driverInfo.settlementLastCleared
  );

  // 기사앱 계산 (settlementLastCleared 기준)
  const driverCalc = replicateDriverCalc(
    allCalls, driverAuthUid, depositRatio, driverInfo.settlementLastCleared
  );

  const firestore = {
    sessionTotalFare: sessionData?.totals?.totalFare ?? 0,
    sessionCallCount: sessionData?.totals?.callCount ?? sessionCalls.length,
    driverCallCount: driverSessionCalls.length,
    driverTotalFare: driverSessionCalls.reduce((s: number, c: any) => s + (c.fare || 0), 0),
    carryOverBalance: driverInfo.carryOverBalance,
    carryOverStatus: driverInfo.carryOverStatus,
    dailySettlementStatus: driverInfo.dailySettlementStatus,
    calculatedCarryOver: driverInfo.calculatedCarryOver,
  };

  // 일치 검증
  const mismatches: string[] = [];

  // 1. 콜 수 일치 (매니저 filteredTrips vs 기사 trips)
  if (managerCalc.filteredTripCount !== driverCalc.tripCount) {
    mismatches.push(
      `콜수: manager=${managerCalc.filteredTripCount}, driver=${driverCalc.tripCount}`
    );
  }

  // 2. 총 운행료 일치
  if (managerCalc.totalFare !== driverCalc.totalFare) {
    mismatches.push(
      `총운행료: manager=${managerCalc.totalFare}, driver=${driverCalc.totalFare}`
    );
  }

  // 3. 수수료(deposit) vs officeDeposit 일치
  if (managerCalc.deposit !== driverCalc.officeDeposit) {
    mismatches.push(
      `수수료: manager.deposit=${managerCalc.deposit}, driver.officeDeposit=${driverCalc.officeDeposit}`
    );
  }

  // 4. 외상(totalCredit) 일치
  if (managerCalc.totalCredit !== driverCalc.totalCredit) {
    mismatches.push(
      `외상: manager=${managerCalc.totalCredit}, driver=${driverCalc.totalCredit}`
    );
  }

  // 5. Firestore 세션의 기사별 콜 수 vs 계산된 콜 수
  //    (세션은 전체 기간이므로, filteredTrips와 다를 수 있음 - lastCleared 이전 콜 포함)
  //    이 비교는 lastCleared가 0일 때만 의미 있음
  if (driverInfo.settlementLastCleared === 0) {
    if (firestore.driverCallCount !== managerCalc.filteredTripCount) {
      mismatches.push(
        `세션콜수: session=${firestore.driverCallCount}, manager=${managerCalc.filteredTripCount}`
      );
    }
  }

  return {
    checkpoint,
    driverId: driverDocId,
    firestore,
    managerCalc,
    driverCalc,
    match: mismatches.length === 0,
    mismatches,
  };
}

// ========== 결과 출력 ==========

export function printConsistencyResult(result: ConsistencyCheckResult): void {
  const status = result.match ? "PASS ✓" : "FAIL ✗";
  console.log(`\n  [${result.checkpoint}] ${result.driverId}: ${status}`);

  if (!result.match) {
    console.log(`    불일치 항목:`);
    result.mismatches.forEach(m => console.log(`      ✗ ${m}`));
  }

  console.log(`    Firestore: 세션콜=${result.firestore.driverCallCount}, 세션운행료=${result.firestore.driverTotalFare}, carryOver=${result.firestore.carryOverBalance}(${result.firestore.carryOverStatus})`);
  console.log(`    매니저:    콜=${result.managerCalc.filteredTripCount}, 운행료=${result.managerCalc.totalFare}, 수수료=${result.managerCalc.deposit}, 외상=${result.managerCalc.totalCredit}`);
  console.log(`    기사앱:    콜=${result.driverCalc.tripCount}, 운행료=${result.driverCalc.totalFare}, 수수료=${result.driverCalc.officeDeposit}, 외상=${result.driverCalc.totalCredit}`);
}
