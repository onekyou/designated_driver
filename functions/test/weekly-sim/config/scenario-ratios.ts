/**
 * 시나리오/결제수단 비율 설정
 * 주차별/요일별 비율 조정 가능
 */

// ==========================================
// 콜 시나리오 유형
// ==========================================

export type CallScenario =
  | "NORMAL"              // 정상 완료
  | "REJECT_REASSIGN"     // 기사 거절 → 재배차
  | "CANCEL_MANAGER"      // 관리자 취소
  | "CANCEL_DRIVER"       // 기사 취소
  | "CANCEL_CUSTOMER"     // 고객 취소
  | "SHARED_CALL"         // 공유콜 수임 완료
  | "SHARED_CONTENTION"   // 공유콜 경합
  | "ASSIGNED_TIMEOUT";   // 배차 타임아웃

export interface ScenarioRatios {
  NORMAL: number;
  REJECT_REASSIGN: number;
  CANCEL_MANAGER: number;
  CANCEL_DRIVER: number;
  CANCEL_CUSTOMER: number;
  SHARED_CALL: number;
  SHARED_CONTENTION: number;
  ASSIGNED_TIMEOUT: number;
}

// 기본 비율 (Week 1 기준)
export const BASE_SCENARIO_RATIOS: ScenarioRatios = {
  NORMAL: 0.75,
  REJECT_REASSIGN: 0.05,
  CANCEL_MANAGER: 0.05,
  CANCEL_DRIVER: 0.03,
  CANCEL_CUSTOMER: 0.03,
  SHARED_CALL: 0.05,
  SHARED_CONTENTION: 0.02,
  ASSIGNED_TIMEOUT: 0.02,
};

// 주차별 비율 조정
export const WEEKLY_RATIO_ADJUSTMENTS: Record<number, Partial<ScenarioRatios>> = {
  1: { NORMAL: 0.80, SHARED_CALL: 0.03, SHARED_CONTENTION: 0.01 },  // 워밍업: 공유콜 적음
  2: { NORMAL: 0.72, CANCEL_CUSTOMER: 0.05, SHARED_CALL: 0.06 },    // 복잡도 증가
  3: { NORMAL: 0.65, REJECT_REASSIGN: 0.08, SHARED_CALL: 0.08, SHARED_CONTENTION: 0.04 }, // 스트레스
  4: { NORMAL: 0.70, SHARED_CALL: 0.07, SHARED_CONTENTION: 0.03 },  // 종합
};

// 주차+요일에 따른 최종 비율 계산
export function getScenarioRatios(week: number, _dayOfWeek: number): ScenarioRatios {
  const base = { ...BASE_SCENARIO_RATIOS };
  const adj = WEEKLY_RATIO_ADJUSTMENTS[week];
  if (adj) {
    Object.assign(base, adj);
  }

  // 비율 정규화 (합이 1이 되도록)
  const total = Object.values(base).reduce((a, b) => a + b, 0);
  for (const key of Object.keys(base) as (keyof ScenarioRatios)[]) {
    base[key] = base[key] / total;
  }

  return base;
}

// ==========================================
// 결제수단
// ==========================================

export type PaymentMethod = "현금" | "카드" | "이체" | "외상" | "포인트" | "현금+포인트";

export interface PaymentRatios {
  "현금": number;
  "카드": number;
  "이체": number;
  "외상": number;
  "포인트": number;
  "현금+포인트": number;
}

export const PAYMENT_RATIOS: PaymentRatios = {
  "현금": 0.55,
  "카드": 0.20,
  "이체": 0.10,
  "외상": 0.05,
  "포인트": 0.05,
  "현금+포인트": 0.05,
};

// ==========================================
// 요금 범위
// ==========================================

export const FARE_RANGES = {
  min: 10000,
  max: 50000,
  step: 1000,
};

// 결정론적 랜덤 (시드 기반, 재현 가능)
export class SeededRandom {
  private seed: number;

  constructor(seed: number) {
    this.seed = seed;
  }

  next(): number {
    this.seed = (this.seed * 1664525 + 1013904223) & 0x7fffffff;
    return this.seed / 0x7fffffff;
  }

  // 범위 내 정수
  nextInt(min: number, max: number): number {
    return Math.floor(this.next() * (max - min + 1)) + min;
  }

  // 비율 기반 선택
  pickByRatio<T extends string>(ratios: Record<T, number>): T {
    const r = this.next();
    let cumulative = 0;
    for (const [key, ratio] of Object.entries(ratios) as [T, number][]) {
      cumulative += ratio;
      if (r < cumulative) return key;
    }
    // 마지막 키 반환 (부동소수점 오차 대비)
    return Object.keys(ratios).pop() as T;
  }

  // 요금 생성 (1000원 단위)
  nextFare(): number {
    const { min, max, step } = FARE_RANGES;
    const steps = (max - min) / step;
    return min + Math.floor(this.next() * (steps + 1)) * step;
  }
}
