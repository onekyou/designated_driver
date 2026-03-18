/**
 * 정산 계산 로직 단위 테스트
 * 기사앱 submitDailySettlement + 매니저앱 confirmDailySettlement 계산 검증
 *
 * 실행: cd functions && node test/settlement-calc-test.js
 */

// ========== 계산 함수 (앱 로직 그대로 복제) ==========

/**
 * 기사앱: 오늘 정산 계산 (calculateTodaySettlement)
 * @param {number} totalFare - 총 운행료
 * @param {number} cashReceived - 현금 수령액
 * @param {number} depositRatio - 수수료 비율 (%)
 * @param {number} pointsUsed - 포인트 사용액 (기본 0)
 */
function calculateTodaySettlement(totalFare, cashReceived, depositRatio, pointsUsed = 0) {
  const officeDeposit = Math.floor(totalFare * depositRatio / 100);
  const driverShare = totalFare - officeDeposit;
  const realDeposit = cashReceived - driverShare;
  const totalCredit = totalFare - cashReceived - pointsUsed;
  return { totalFare, officeDeposit, driverShare, cashReceived, realDeposit, totalCredit, pointsUsed };
}

/**
 * 기사앱: 업무마감 제출 (submitDailySettlement)
 * @param {object} todaySettlement - calculateTodaySettlement 결과
 * @param {number} realDeposit - 기사가 입력한 실납입액
 * @param {number} originalCarryOver - 기존 이월금
 */
function submitDailySettlement(todaySettlement, realDeposit, originalCarryOver) {
  const { totalFare, officeDeposit, totalCredit } = todaySettlement;
  const finalDeposit = officeDeposit - totalCredit;
  const calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit;
  const settlementDiff = (realDeposit - finalDeposit) + originalCarryOver;

  return {
    totalFare,
    finalDeposit,
    realDeposit,
    totalCredit,
    calculatedCarryOver,
    settlementDiff,
    originalCarryOver,
    status: 'PENDING_CONFIRM'
  };
}

/**
 * 매니저앱: 정산 확인 (confirmDailySettlement)
 * @param {object} dailySettlement - submitDailySettlement 결과
 */
function confirmDailySettlement(dailySettlement) {
  const newBalance = dailySettlement.calculatedCarryOver;
  const newStatus = newBalance !== 0 ? 'PENDING' : 'SETTLED';
  return {
    carryOverBalance: newBalance,
    carryOverStatus: newStatus,
    dailySettlementStatus: 'CONFIRMED'
  };
}

// ========== 검증 함수 ==========

let passCount = 0;
let failCount = 0;

function assert(condition, message) {
  if (!condition) {
    console.error(`  ❌ FAIL: ${message}`);
    failCount++;
    return false;
  }
  passCount++;
  return true;
}

function runScenario(name, config) {
  console.log(`\n━━━ ${name} ━━━`);
  console.log(`  입력: 운행료=${config.totalFare}, 현금수령=${config.cashReceived}, 실납입=${config.realDeposit}, 비율=${config.depositRatio}%, 기존이월=${config.originalCarryOver}, 포인트=${config.pointsUsed || 0}`);

  const today = calculateTodaySettlement(config.totalFare, config.cashReceived, config.depositRatio, config.pointsUsed || 0);
  console.log(`  계산: 수수료=${today.officeDeposit}, 기사몫=${today.driverShare}, 외상=${today.totalCredit}`);

  // 기본 검증: 수수료 + 기사몫 = 운행료
  assert(today.officeDeposit + today.driverShare === config.totalFare,
    `수수료(${today.officeDeposit}) + 기사몫(${today.driverShare}) = ${today.officeDeposit + today.driverShare} ≠ 운행료(${config.totalFare})`);

  const settlement = submitDailySettlement(today, config.realDeposit, config.originalCarryOver);
  console.log(`  마감: finalDeposit=${settlement.finalDeposit}, realDeposit=${settlement.realDeposit}, diff=${settlement.settlementDiff}, calcCarryOver=${settlement.calculatedCarryOver}`);

  const confirmed = confirmDailySettlement(settlement);
  console.log(`  확인: carryOver.balance=${confirmed.carryOverBalance}, status=${confirmed.carryOverStatus}`);

  // carryOver = calculatedCarryOver
  assert(confirmed.carryOverBalance === settlement.calculatedCarryOver,
    `carryOver(${confirmed.carryOverBalance}) ≠ calculatedCarryOver(${settlement.calculatedCarryOver})`);

  // 예상값 검증
  if (config.expectedCarryOver !== undefined) {
    assert(confirmed.carryOverBalance === config.expectedCarryOver,
      `carryOver(${confirmed.carryOverBalance}) ≠ 예상(${config.expectedCarryOver})`);
  }
  if (config.expectedDiff !== undefined) {
    assert(settlement.settlementDiff === config.expectedDiff,
      `diff(${settlement.settlementDiff}) ≠ 예상(${config.expectedDiff})`);
  }

  // settlementDiff 정합성: diff = realDeposit - finalDeposit + originalCarryOver
  const expectedDiffCalc = (settlement.realDeposit - settlement.finalDeposit) + config.originalCarryOver;
  assert(settlement.settlementDiff === expectedDiffCalc,
    `diff(${settlement.settlementDiff}) ≠ 계산값(${expectedDiffCalc})`);

  // calculatedCarryOver 정합성: carryOver = originalCarryOver - finalDeposit + realDeposit
  const expectedCarryCalc = config.originalCarryOver - settlement.finalDeposit + settlement.realDeposit;
  assert(settlement.calculatedCarryOver === expectedCarryCalc,
    `calcCarryOver(${settlement.calculatedCarryOver}) ≠ 계산값(${expectedCarryCalc})`);

  // settlementDiff == calculatedCarryOver 항상 성립하는지
  assert(settlement.settlementDiff === settlement.calculatedCarryOver,
    `diff(${settlement.settlementDiff}) ≠ calcCarryOver(${settlement.calculatedCarryOver}) — 이 둘은 항상 같아야 함`);

  return confirmed;
}

function runMultiDayScenario(name, days) {
  console.log(`\n══════ ${name} ══════`);
  let currentCarryOver = days[0].originalCarryOver || 0;

  for (let i = 0; i < days.length; i++) {
    const day = days[i];
    day.originalCarryOver = currentCarryOver;
    console.log(`\n  --- Day ${i + 1} (이월: ${currentCarryOver}) ---`);

    const today = calculateTodaySettlement(day.totalFare, day.cashReceived, day.depositRatio, day.pointsUsed || 0);
    const settlement = submitDailySettlement(today, day.realDeposit, currentCarryOver);
    const confirmed = confirmDailySettlement(settlement);

    console.log(`  운행료=${day.totalFare}, 실납입=${day.realDeposit}, diff=${settlement.settlementDiff}, carryOver=${confirmed.carryOverBalance}`);

    // carryOver 정합성
    assert(confirmed.carryOverBalance === settlement.calculatedCarryOver,
      `Day${i + 1}: carryOver(${confirmed.carryOverBalance}) ≠ calcCarryOver(${settlement.calculatedCarryOver})`);

    currentCarryOver = confirmed.carryOverBalance;
  }

  if (days[days.length - 1].expectedFinalCarryOver !== undefined) {
    assert(currentCarryOver === days[days.length - 1].expectedFinalCarryOver,
      `최종 carryOver(${currentCarryOver}) ≠ 예상(${days[days.length - 1].expectedFinalCarryOver})`);
  }

  console.log(`  ▸ 최종 carryOver: ${currentCarryOver}`);
  return currentCarryOver;
}

// ========== 단일 운행 시나리오 ==========

console.log('╔══════════════════════════════════════════╗');
console.log('║   정산 계산 로직 단위 테스트              ║');
console.log('╚══════════════════════════════════════════╝');

console.log('\n■ 단일 운행 시나리오');

// #1: 현금 정확히 납입 (30,000원 운행, 현금결제, 수수료 18,000원 납입)
runScenario('#1 현금 정확히 납입', {
  totalFare: 30000, cashReceived: 30000, realDeposit: 18000,
  depositRatio: 60, originalCarryOver: 0,
  expectedCarryOver: 0, expectedDiff: 0
});

// #2: 현금 초과 납입
runScenario('#2 현금 초과 납입', {
  totalFare: 30000, cashReceived: 30000, realDeposit: 20000,
  depositRatio: 60, originalCarryOver: 0,
  expectedCarryOver: 2000, expectedDiff: 2000
});

// #3: 현금 미납
runScenario('#3 현금 미납', {
  totalFare: 30000, cashReceived: 30000, realDeposit: 15000,
  depositRatio: 60, originalCarryOver: 0,
  expectedCarryOver: -3000, expectedDiff: -3000
});

// #4: 이월 있고 초과 납입
runScenario('#4 이월+초과납입', {
  totalFare: 15000, cashReceived: 15000, realDeposit: 10000,
  depositRatio: 60, originalCarryOver: 2000,
  expectedCarryOver: 3000, expectedDiff: 3000
});

// #5: 이월 있고 정확히 납입
runScenario('#5 이월+정확히납입', {
  totalFare: 30000, cashReceived: 30000, realDeposit: 18000,
  depositRatio: 60, originalCarryOver: 2000,
  expectedCarryOver: 2000, expectedDiff: 2000
});

// #6: 미수 있고 납입
runScenario('#6 미수+납입', {
  totalFare: 30000, cashReceived: 30000, realDeposit: 20000,
  depositRatio: 60, originalCarryOver: -3000,
  expectedCarryOver: -1000, expectedDiff: -1000
});

// #7: 미수 있고 초과 납입
runScenario('#7 미수+초과납입', {
  totalFare: 30000, cashReceived: 30000, realDeposit: 25000,
  depositRatio: 60, originalCarryOver: -3000,
  expectedCarryOver: 4000, expectedDiff: 4000
});

// #8: 외상 운행 (이체 결제, 현금수령 0)
// totalCredit=30000, finalDeposit=18000-30000=-12000, carryOver=0-(-12000)+0=12000
// 사무실이 기사에게 기사몫(12000)을 줘야 하므로 carryOver 양수가 맞음
runScenario('#8 외상(이체) 운행', {
  totalFare: 30000, cashReceived: 0, realDeposit: 0,
  depositRatio: 60, originalCarryOver: 0,
  expectedCarryOver: 12000, expectedDiff: 12000
});

// #9: 현금+포인트 (현금20000+포인트10000=30000, 기사몫12000, 실납입8000)
// totalCredit=30000-20000-10000=0, finalDeposit=18000, carryOver=0-18000+8000=-10000
// 기사가 수수료를 다 못 채웠으므로 미수 발생
runScenario('#9 현금+포인트', {
  totalFare: 30000, cashReceived: 20000, realDeposit: 8000,
  depositRatio: 60, originalCarryOver: 0, pointsUsed: 10000,
  expectedCarryOver: -10000, expectedDiff: -10000
});

// ========== 연속 시나리오 ==========

console.log('\n\n■ 연속 시나리오');

// #10: 2일 연속 초과
runMultiDayScenario('#10 2일 연속 초과', [
  { totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, originalCarryOver: 0 },
  { totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, expectedFinalCarryOver: 4000 }
]);

// #11: 초과 → 미납
runMultiDayScenario('#11 초과→미납', [
  { totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, originalCarryOver: 0 },
  { totalFare: 30000, cashReceived: 30000, realDeposit: 15000, depositRatio: 60, expectedFinalCarryOver: -1000 }
]);

// #12: 미납 → 초과
runMultiDayScenario('#12 미납→초과', [
  { totalFare: 30000, cashReceived: 30000, realDeposit: 15000, depositRatio: 60, originalCarryOver: 0 },
  { totalFare: 30000, cashReceived: 30000, realDeposit: 25000, depositRatio: 60, expectedFinalCarryOver: 4000 }
]);

// #13: 3일 연속 누적
runMultiDayScenario('#13 3일 연속 누적 (초과→초과→미납)', [
  { totalFare: 30000, cashReceived: 30000, realDeposit: 20000, depositRatio: 60, originalCarryOver: 0 },
  { totalFare: 25000, cashReceived: 25000, realDeposit: 18000, depositRatio: 60 },
  { totalFare: 30000, cashReceived: 30000, realDeposit: 15000, depositRatio: 60, expectedFinalCarryOver: 2000 }
]);

// ========== 결과 ==========

console.log('\n\n╔══════════════════════════════════════════╗');
console.log(`║  결과: ${passCount} PASS / ${failCount} FAIL              `);
console.log('╚══════════════════════════════════════════╝');

if (failCount > 0) {
  process.exit(1);
} else {
  console.log('\n✅ 모든 정산 계산 테스트 통과!');
}
