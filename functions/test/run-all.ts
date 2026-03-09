/**
 * 전체 시나리오 순차 실행
 * 각 시나리오 전에 데이터 초기화 + 시드 주입
 */

import { clearEmulatorData, db, FieldValue } from "./setup/emulator-config";
import { OFFICES } from "./setup/constants";

import { runScenario01 } from "./scenarios/01-normal-flow.test";
import { runScenario02 } from "./scenarios/02-reject-reassign.test";
import { runScenario03 } from "./scenarios/03-cancel-types.test";
import { runScenario04 } from "./scenarios/04-shared-call.test";
import { runScenario05 } from "./scenarios/05-shared-contention.test";
import { runScenario06 } from "./scenarios/06-bulk-load.test";
import { runScenario07 } from "./scenarios/07-settlement.test";
import { runScenario08 } from "./scenarios/08-customer-points.test";

/**
 * 시드 데이터 주입 (10사무실 × 5기사 + 관리자 + 포인트)
 */
async function seedData(): Promise<void> {
  const batch = db.batch();

  batch.set(db.doc("provinces/test_province"), { name: "테스트도" });
  batch.set(db.doc("provinces/test_province/cities/test_city"), { name: "테스트시" });

  for (let oi = 0; oi < 10; oi++) {
    const x = String(oi + 1).padStart(2, "0");
    const p = `provinces/test_province/cities/test_city/offices/office_${x}`;

    batch.set(db.doc(p), {
      name: `사무실${x}`, depositRatio: 60, assignedTimeoutMinutes: 3,
      provinceId: "test_province", cityId: "test_city", officeId: `office_${x}`, status: "OPEN",
    });

    for (let di = 0; di < 5; di++) {
      const y = String(di + 1).padStart(2, "0");
      const id = `driver_${x}_${y}`;
      batch.set(db.doc(`${p}/designated_drivers/${id}`), {
        name: `기사${x}-${y}`, phoneNumber: `010-${x}00-${y}000`,
        fcmToken: `t_${id}`, status: "WAITING", authUid: `auth_${id}`,
        isLoggedIn: true, createdAt: FieldValue.serverTimestamp(),
      });
    }

    batch.set(db.doc(`${p}/points/points`), { balance: 10000 });
    batch.set(db.doc(`${p}/managerTokens/admin_office_${x}`), { token: `mt_${x}` });
    batch.set(db.doc(`admins/admin_office_${x}`), {
      associatedProvinceId: "test_province", associatedCityId: "test_city",
      associatedOfficeId: `office_${x}`, role: "ADMIN", fcmToken: `at_${x}`,
    });
  }

  await batch.commit();
}

/**
 * CF 트리거 안정화 대기
 */
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 데이터 초기화 + 시드 + CF 안정화 대기
 */
async function resetAndSeed(): Promise<void> {
  await clearEmulatorData();
  await seedData();
  await sleep(8000); // 시드 CF 트리거 안정화 대기
}

// ==========================================
// 메인 실행
// ==========================================

interface ScenarioResult {
  name: string;
  passed: boolean;
}

(async () => {
  const startTime = Date.now();
  console.log("\n" + "=".repeat(60));
  console.log("  Firebase Emulator 통합 테스트 - 전체 실행");
  console.log("=".repeat(60));

  const results: ScenarioResult[] = [];

  // 시나리오 1~3: 같은 데이터셋으로 실행 가능 (사무실 01, 02 사용)
  console.log("\n--- 데이터 초기화 + 시드 (1~3) ---");
  await resetAndSeed();

  results.push({ name: "시나리오 1: 정상 플로우", passed: await runScenario01() });
  results.push({ name: "시나리오 2: 거절→재배차", passed: await runScenario02() });
  results.push({ name: "시나리오 3: 취소 3종", passed: await runScenario03() });

  // 시나리오 4~5: 공유콜 (사무실 01~05 사용, 포인트 초기값 필요)
  console.log("\n--- 데이터 초기화 + 시드 (4~5) ---");
  await resetAndSeed();

  results.push({ name: "시나리오 4: 공유콜", passed: await runScenario04() });
  results.push({ name: "시나리오 5: 공유콜 경합", passed: await runScenario05() });

  // 시나리오 6: 대량 (전 사무실 사용, 독립 데이터 필요)
  console.log("\n--- 데이터 초기화 + 시드 (6) ---");
  await resetAndSeed();

  results.push({ name: "시나리오 6: 대량 50건", passed: await runScenario06() });

  // 시나리오 7~8: 정산/포인트 (사무실 06, 07 사용)
  console.log("\n--- 데이터 초기화 + 시드 (7~8) ---");
  await resetAndSeed();

  results.push({ name: "시나리오 7: 정산 마감", passed: await runScenario07() });
  results.push({ name: "시나리오 8: 고객 포인트", passed: await runScenario08() });

  // ==========================================
  // 최종 결과
  // ==========================================
  const totalTime = Date.now() - startTime;
  const passCount = results.filter((r) => r.passed).length;
  const failCount = results.filter((r) => !r.passed).length;

  console.log("\n" + "=".repeat(60));
  console.log("  최종 결과");
  console.log("=".repeat(60));

  for (const r of results) {
    console.log(`  ${r.passed ? "PASS" : "FAIL"} ${r.name}`);
  }

  console.log(`\n  총 ${results.length}건: PASS ${passCount} / FAIL ${failCount}`);
  console.log(`  소요 시간: ${(totalTime / 1000).toFixed(1)}s`);
  console.log("=".repeat(60) + "\n");

  process.exit(failCount > 0 ? 1 : 0);
})();
