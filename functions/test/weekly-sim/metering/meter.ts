/**
 * Firebase 비용 미터링 모듈
 * 모든 Firestore 연산을 카운팅하여 비용 예측 리포트 생성
 */

// ==========================================
// 카운터
// ==========================================

export interface MeterData {
  // Firestore 연산
  reads: number;
  writes: number;
  deletes: number;

  // Cloud Functions
  cfInvocations: number;
  cfDurationMs: number;    // CF 실행 총 시간 (ms)

  // FCM
  fcmSent: number;

  // 문서 통계
  documentsCreated: number;
  documentsUpdated: number;
  batchWrites: number;     // batch.commit() 횟수

  // 콜 통계
  callsProcessed: number;
  sharedCallsCreated: number;
  sharedCallsClaimed: number;
  settlementSessions: number;

  // 타임스탬프
  startTime: number;
  endTime: number;
}

// 일별 미터
export interface DailyMeter extends MeterData {
  dayNumber: number;
  workDate: string;
  description: string;
}

// 주별 미터
export interface WeeklyMeter {
  week: number;
  days: DailyMeter[];
  totals: MeterData;
}

// ==========================================
// 글로벌 미터 인스턴스
// ==========================================

let currentMeter: MeterData = createEmptyMeter();
const dailyMeters: DailyMeter[] = [];

function createEmptyMeter(): MeterData {
  return {
    reads: 0,
    writes: 0,
    deletes: 0,
    cfInvocations: 0,
    cfDurationMs: 0,
    fcmSent: 0,
    documentsCreated: 0,
    documentsUpdated: 0,
    batchWrites: 0,
    callsProcessed: 0,
    sharedCallsCreated: 0,
    sharedCallsClaimed: 0,
    settlementSessions: 0,
    startTime: 0,
    endTime: 0,
  };
}

// ==========================================
// 미터 조작 API
// ==========================================

export function startDayMeter(): void {
  currentMeter = createEmptyMeter();
  currentMeter.startTime = Date.now();
}

export function endDayMeter(dayNumber: number, workDate: string, description: string): DailyMeter {
  currentMeter.endTime = Date.now();
  const daily: DailyMeter = {
    ...currentMeter,
    dayNumber,
    workDate,
    description,
  };
  dailyMeters.push(daily);
  return daily;
}

// 개별 카운터 증가
export function meterRead(count: number = 1): void {
  currentMeter.reads += count;
}

export function meterWrite(count: number = 1): void {
  currentMeter.writes += count;
}

export function meterDelete(count: number = 1): void {
  currentMeter.deletes += count;
}

export function meterCfInvocation(durationMs: number = 500): void {
  currentMeter.cfInvocations += 1;
  currentMeter.cfDurationMs += durationMs;
}

export function meterFcm(count: number = 1): void {
  currentMeter.fcmSent += count;
}

export function meterBatchWrite(writeCount: number): void {
  currentMeter.batchWrites += 1;
  currentMeter.writes += writeCount;
}

export function meterCallProcessed(): void {
  currentMeter.callsProcessed += 1;
}

export function meterSharedCallCreated(): void {
  currentMeter.sharedCallsCreated += 1;
}

export function meterSharedCallClaimed(): void {
  currentMeter.sharedCallsClaimed += 1;
}

export function meterSettlementSession(): void {
  currentMeter.settlementSessions += 1;
}

// ==========================================
// CF 트리거 추정 (콜 상태별 발동되는 CF 수)
// ==========================================

/**
 * 콜 1건의 라이프사이클에서 발생하는 CF 트리거를 추정하여 카운팅
 * 실제 에뮬레이터에서 CF가 실행되지만, 비용 측정을 위해 별도 카운팅
 */
export function meterCallLifecycleCF(scenario: string): void {
  switch (scenario) {
    case "NORMAL":
      // oncallassigned(1) + onCallStatusChanged×3(ACCEPTED,IN_PROGRESS,AWAITING) + onCallCompleted(1) + settlement(1)
      meterCfInvocation(300);  // oncallassigned: FCM 발송
      meterCfInvocation(200);  // onCallStatusChanged: ACCEPTED
      meterCfInvocation(200);  // onCallStatusChanged: IN_PROGRESS
      meterCfInvocation(200);  // onCallStatusChanged: AWAITING_SETTLEMENT
      meterCfInvocation(500);  // onCallCompletedUpdateSettlement
      meterCfInvocation(300);  // notifyCustomerOnComplete (FCM)
      meterFcm(3);             // 기사+매니저+고객
      // CF 내부 읽기/쓰기
      meterRead(15);           // CF 내부 문서 조회
      meterWrite(5);           // CF 내부 문서 업데이트
      break;

    case "REJECT_REASSIGN":
      // 거절 + 재배차이므로 CF가 더 많이 발동
      meterCfInvocation(300);  // 1차 배차 oncallassigned
      meterCfInvocation(200);  // 거절 상태변경
      meterCfInvocation(300);  // 재배차 oncallassigned
      meterCfInvocation(200);  // ACCEPTED
      meterCfInvocation(200);  // IN_PROGRESS
      meterCfInvocation(200);  // AWAITING_SETTLEMENT
      meterCfInvocation(500);  // settlement
      meterCfInvocation(300);  // notifyCustomerOnComplete
      meterFcm(4);
      meterRead(20);
      meterWrite(7);
      break;

    case "CANCEL_MANAGER":
    case "CANCEL_DRIVER":
    case "CANCEL_CUSTOMER":
      // 배차 + 취소 (정산 없음)
      meterCfInvocation(300);  // oncallassigned
      meterCfInvocation(200);  // 취소 상태변경
      meterCfInvocation(300);  // notifyDriverCancellation (매니저/고객 취소 시)
      meterFcm(2);
      meterRead(10);
      meterWrite(3);
      break;

    case "SHARED_CALL":
      // 공유콜: 생성 + 수임 CF + 정상 플로우
      meterCfInvocation(400);  // onSharedCallClaimed (콜 복사)
      meterCfInvocation(500);  // onSharedCallCompleted (포인트 처리)
      meterCfInvocation(300);  // oncallassigned
      meterCfInvocation(200);  // status changes...
      meterCfInvocation(200);
      meterCfInvocation(500);  // settlement
      meterFcm(3);
      meterRead(25);
      meterWrite(8);
      break;

    case "SHARED_CONTENTION":
      // 경합: 2개 트랜잭션 (1성공 + 1실패)
      meterCfInvocation(400);  // onSharedCallClaimed
      meterCfInvocation(500);  // 이후 정상 플로우
      meterCfInvocation(300);
      meterCfInvocation(200);
      meterCfInvocation(200);
      meterCfInvocation(500);
      meterFcm(3);
      meterRead(30);           // 트랜잭션 재시도 포함
      meterWrite(8);
      break;

    case "ASSIGNED_TIMEOUT":
      // 배차 → 3분 무응답 → CF 복구 → 재배차 → 정상
      meterCfInvocation(300);  // oncallassigned
      meterCfInvocation(400);  // checkAssignedTimeout
      meterCfInvocation(300);  // 재배차 oncallassigned
      meterCfInvocation(200);
      meterCfInvocation(200);
      meterCfInvocation(200);
      meterCfInvocation(500);  // settlement
      meterFcm(4);
      meterRead(20);
      meterWrite(7);
      break;
  }
}

// ==========================================
// 비용 계산
// ==========================================

export interface CostBreakdown {
  firestoreReads: { count: number; cost: number };
  firestoreWrites: { count: number; cost: number };
  firestoreDeletes: { count: number; cost: number };
  cfInvocations: { count: number; cost: number };
  cfCompute: { gbSeconds: number; cost: number };
  fcm: { count: number; cost: number };
  storage: { estimatedMB: number; cost: number };
  totalMonthlyCost: number;
}

// Firebase 단가 (2026 기준, US region)
const PRICING = {
  firestoreRead: 0.06 / 100000,     // $0.06 per 100K
  firestoreWrite: 0.18 / 100000,    // $0.18 per 100K
  firestoreDelete: 0.02 / 100000,   // $0.02 per 100K
  cfInvocation: 0.40 / 1000000,     // $0.40 per 1M (2M free)
  cfCompute: 0.0000025,             // $0.0000025 per GB-second
  fcm: 0,                           // 무료
  storage: 0.18,                    // $0.18 per GB/month

  // 무료 할당 (월간)
  freeReads: 50000 * 30,            // 50K/일 × 30일
  freeWrites: 20000 * 30,           // 20K/일 × 30일
  freeDeletes: 20000 * 30,
  freeCfInvocations: 2000000,       // 2M/월
  freeCfCompute: 400000,            // 400K GB-seconds/월
  freeStorage: 1,                   // 1GB
};

export function calculateCost(meter: MeterData): CostBreakdown {
  // CF 컴퓨트 (256MB 기준)
  const cfGbSeconds = (meter.cfDurationMs / 1000) * 0.25; // 256MB = 0.25GB

  // 저장소 추정 (문서당 평균 2KB)
  const estimatedMB = ((meter.documentsCreated + meter.documentsUpdated) * 2) / 1024;

  return {
    firestoreReads: {
      count: meter.reads,
      cost: Math.max(0, meter.reads - PRICING.freeReads) * PRICING.firestoreRead,
    },
    firestoreWrites: {
      count: meter.writes,
      cost: Math.max(0, meter.writes - PRICING.freeWrites) * PRICING.firestoreWrite,
    },
    firestoreDeletes: {
      count: meter.deletes,
      cost: Math.max(0, meter.deletes - PRICING.freeDeletes) * PRICING.firestoreDelete,
    },
    cfInvocations: {
      count: meter.cfInvocations,
      cost: Math.max(0, meter.cfInvocations - PRICING.freeCfInvocations) * PRICING.cfInvocation,
    },
    cfCompute: {
      gbSeconds: cfGbSeconds,
      cost: Math.max(0, cfGbSeconds - PRICING.freeCfCompute) * PRICING.cfCompute,
    },
    fcm: {
      count: meter.fcmSent,
      cost: 0,
    },
    storage: {
      estimatedMB,
      cost: Math.max(0, estimatedMB / 1024 - PRICING.freeStorage) * PRICING.storage,
    },
    totalMonthlyCost: 0, // 아래에서 계산
  };
}

// ==========================================
// 리포트 생성
// ==========================================

export function getAllDailyMeters(): DailyMeter[] {
  return [...dailyMeters];
}

export function getWeeklyTotals(week: number): MeterData {
  const weekDays = dailyMeters.filter((d) => Math.ceil(d.dayNumber / 7) === week);
  return aggregateMeters(weekDays);
}

export function getGrandTotals(): MeterData {
  return aggregateMeters(dailyMeters);
}

function aggregateMeters(meters: MeterData[]): MeterData {
  const total = createEmptyMeter();
  for (const m of meters) {
    total.reads += m.reads;
    total.writes += m.writes;
    total.deletes += m.deletes;
    total.cfInvocations += m.cfInvocations;
    total.cfDurationMs += m.cfDurationMs;
    total.fcmSent += m.fcmSent;
    total.documentsCreated += m.documentsCreated;
    total.documentsUpdated += m.documentsUpdated;
    total.batchWrites += m.batchWrites;
    total.callsProcessed += m.callsProcessed;
    total.sharedCallsCreated += m.sharedCallsCreated;
    total.sharedCallsClaimed += m.sharedCallsClaimed;
    total.settlementSessions += m.settlementSessions;
  }
  total.startTime = meters[0]?.startTime || 0;
  total.endTime = meters[meters.length - 1]?.endTime || 0;
  return total;
}

export function printCostReport(meter: MeterData, label: string, officeCount: number = 10): void {
  const cost = calculateCost(meter);
  cost.totalMonthlyCost =
    cost.firestoreReads.cost +
    cost.firestoreWrites.cost +
    cost.firestoreDeletes.cost +
    cost.cfInvocations.cost +
    cost.cfCompute.cost +
    cost.storage.cost;

  const duration = (meter.endTime - meter.startTime) / 1000;

  console.log(`\n${"=".repeat(60)}`);
  console.log(`  Firebase 비용 예측 리포트: ${label}`);
  console.log(`${"=".repeat(60)}`);
  console.log(`  사무실: ${officeCount}개 / 콜: ${meter.callsProcessed.toLocaleString()}건`);
  console.log(`  실행 시간: ${duration.toFixed(1)}s`);
  console.log(`${"─".repeat(60)}`);
  console.log(`  항목                  수량              비용`);
  console.log(`${"─".repeat(60)}`);
  console.log(`  Firestore 읽기      ${padNum(meter.reads)}건    $${cost.firestoreReads.cost.toFixed(4)}`);
  console.log(`  Firestore 쓰기      ${padNum(meter.writes)}건    $${cost.firestoreWrites.cost.toFixed(4)}`);
  console.log(`  Firestore 삭제      ${padNum(meter.deletes)}건    $${cost.firestoreDeletes.cost.toFixed(4)}`);
  console.log(`  CF 호출             ${padNum(meter.cfInvocations)}건    $${cost.cfInvocations.cost.toFixed(4)}`);
  console.log(`  CF 컴퓨팅           ${cost.cfCompute.gbSeconds.toFixed(1)} GB-s   $${cost.cfCompute.cost.toFixed(4)}`);
  console.log(`  FCM 발송            ${padNum(meter.fcmSent)}건    $0.0000 (무료)`);
  console.log(`  저장소              ${cost.storage.estimatedMB.toFixed(1)} MB     $${cost.storage.cost.toFixed(4)}`);
  console.log(`${"─".repeat(60)}`);
  console.log(`  월 예상 비용 (무료 포함):  $${cost.totalMonthlyCost.toFixed(4)}`);

  // 무료 할당 제외한 순수 비용 (스케일업 시 참고)
  const rawCost = {
    reads: meter.reads * PRICING.firestoreRead,
    writes: meter.writes * PRICING.firestoreWrite,
    cf: meter.cfInvocations * PRICING.cfInvocation,
    total: 0,
  };
  rawCost.total = rawCost.reads + rawCost.writes + rawCost.cf;

  console.log(`  월 예상 비용 (무료 제외):  $${rawCost.total.toFixed(4)}`);
  console.log(`${"─".repeat(60)}`);

  // 확장 예측
  console.log(`\n  === 사무실 확장 시 월 비용 예측 (무료 할당 제외) ===`);
  const perOffice = rawCost.total / officeCount;
  for (const scale of [20, 50, 100, 200]) {
    const scaledCost = perOffice * scale;
    console.log(`  ${String(scale).padStart(3)}개 사무실:  $${scaledCost.toFixed(2)}/월`);
  }
  console.log(`${"=".repeat(60)}\n`);
}

function padNum(n: number): string {
  return n.toLocaleString().padStart(10);
}

// 미터 리셋 (주 단위 실행 시)
export function resetAllMeters(): void {
  dailyMeters.length = 0;
  currentMeter = createEmptyMeter();
}
