import { LockInScore } from '../types/lockin';

interface OfficeData {
  // 고객 데이터
  totalCustomers: number;
  activeCustomers: number;
  totalCalls: number;

  // 포인트 데이터
  pointsBalance: number;
  vipCustomers: number;
  goldCustomers: number;

  // 공유콜 네트워크
  sharedCallConnections: number;
  sharedCallCount: number;
}

/**
 * 데이터 락인 점수 계산 (0-100)
 * 가중치: 50%
 */
function calculateDataLockIn(data: OfficeData): number {
  let score = 0;
  const { totalCustomers, activeCustomers, totalCalls } = data;

  // 총 고객 수 (최대 50점)
  if (totalCustomers >= 200) {
    score += 50;
  } else if (totalCustomers >= 100) {
    score += 40;
  } else if (totalCustomers >= 50) {
    score += 30;
  } else {
    score += (totalCustomers / 50) * 30;
  }

  // 활성 고객 비율 (최대 30점)
  if (totalCustomers > 0) {
    const activeRatio = activeCustomers / totalCustomers;
    score += activeRatio * 30;
  }

  // 평균 콜 수 (최대 20점)
  if (totalCustomers > 0) {
    const avgCallsPerCustomer = totalCalls / totalCustomers;
    if (avgCallsPerCustomer >= 5) {
      score += 20;
    } else {
      score += (avgCallsPerCustomer / 5) * 20;
    }
  }

  return Math.min(score, 100);
}

/**
 * 경제적 락인 점수 계산 (0-100)
 * 가중치: 35%
 */
function calculateFinancialLockIn(data: OfficeData): number {
  let score = 0;
  const { pointsBalance, vipCustomers, goldCustomers, totalCustomers } = data;

  // 포인트 잔액 (최대 60점)
  if (pointsBalance >= 5000000) {
    score += 60; // 500만원 이상
  } else if (pointsBalance >= 3000000) {
    score += 50; // 300만원 이상
  } else if (pointsBalance >= 1000000) {
    score += 40; // 100만원 이상
  } else {
    score += (pointsBalance / 1000000) * 40;
  }

  // VIP/GOLD 고객 비율 (최대 40점)
  if (totalCustomers > 0) {
    const premiumRatio = (vipCustomers + goldCustomers) / totalCustomers;
    score += premiumRatio * 40;
  }

  return Math.min(score, 100);
}

/**
 * 네트워크 락인 점수 계산 (0-100)
 * 가중치: 15%
 */
function calculateNetworkLockIn(data: OfficeData): number {
  let score = 0;
  const { sharedCallConnections, sharedCallCount } = data;

  // 공유콜 연결 수 (최대 50점)
  if (sharedCallConnections >= 10) {
    score += 50;
  } else if (sharedCallConnections >= 5) {
    score += 40;
  } else {
    score += (sharedCallConnections / 5) * 40;
  }

  // 공유콜 거래량 (최대 50점)
  if (sharedCallCount >= 50) {
    score += 50;
  } else {
    score += (sharedCallCount / 50) * 50;
  }

  return Math.min(score, 100);
}

/**
 * 전체 락인 점수 계산 (가중 평균)
 */
function calculateTotalScore(
  dataLockIn: number,
  financialLockIn: number,
  networkLockIn: number
): number {
  return dataLockIn * 0.5 + financialLockIn * 0.35 + networkLockIn * 0.15;
}

/**
 * 이탈 위험도 계산
 */
function calculateChurnRisk(totalScore: number): 'low' | 'medium' | 'high' {
  if (totalScore >= 70) return 'low';
  if (totalScore >= 40) return 'medium';
  return 'high';
}

/**
 * 유료 전환 확률 계산 (0-100%)
 */
function calculateConversionProbability(
  totalScore: number,
  data: OfficeData
): number {
  let probability = totalScore;

  // 보정 요소
  if (data.totalCustomers >= 50) probability += 5;
  if (data.pointsBalance >= 1000000) probability += 5;
  if (data.sharedCallConnections >= 3) probability += 5;

  return Math.min(probability, 100);
}

/**
 * 추천 액션 결정
 */
function getRecommendedAction(
  totalScore: number,
  conversionProbability: number
): { action: 'convert' | 'nurture' | 'support'; message: string } {
  if (totalScore >= 70 && conversionProbability >= 80) {
    return {
      action: 'convert',
      message: '유료 전환 제안 - 높은 전환 확률',
    };
  }

  if (totalScore >= 40) {
    return {
      action: 'nurture',
      message: '락인 강화 지원 - 계속 무료 제공',
    };
  }

  return {
    action: 'support',
    message: '이탈 위험 - 적극적 지원 필요',
  };
}

/**
 * 락인 점수 계산 (메인 함수)
 */
export function calculateLockInScore(data: OfficeData): LockInScore {
  // 각 락인 점수 계산
  const dataLockIn = calculateDataLockIn(data);
  const financialLockIn = calculateFinancialLockIn(data);
  const networkLockIn = calculateNetworkLockIn(data);

  // 전체 점수 계산
  const totalScore = calculateTotalScore(dataLockIn, financialLockIn, networkLockIn);

  // 이탈 위험도
  const churnRisk = calculateChurnRisk(totalScore);

  // 유료 전환 확률
  const conversionProbability = calculateConversionProbability(totalScore, data);

  // 추천 액션
  const { action, message } = getRecommendedAction(totalScore, conversionProbability);

  // 세부 지표
  const avgCallsPerCustomer =
    data.totalCustomers > 0 ? data.totalCalls / data.totalCustomers : 0;
  const activeCustomerRatio =
    data.totalCustomers > 0 ? data.activeCustomers / data.totalCustomers : 0;
  const premiumCustomerRatio =
    data.totalCustomers > 0
      ? (data.vipCustomers + data.goldCustomers) / data.totalCustomers
      : 0;

  return {
    dataLockIn: Math.round(dataLockIn),
    financialLockIn: Math.round(financialLockIn),
    networkLockIn: Math.round(networkLockIn),
    totalScore: Math.round(totalScore),
    churnRisk,
    conversionProbability: Math.round(conversionProbability),
    metrics: {
      totalCustomers: data.totalCustomers,
      activeCustomers: data.activeCustomers,
      activeCustomerRatio: Math.round(activeCustomerRatio * 100) / 100,
      totalCalls: data.totalCalls,
      avgCallsPerCustomer: Math.round(avgCallsPerCustomer * 10) / 10,
      pointsBalance: data.pointsBalance,
      vipCustomers: data.vipCustomers,
      goldCustomers: data.goldCustomers,
      premiumCustomerRatio: Math.round(premiumCustomerRatio * 100) / 100,
      sharedCallConnections: data.sharedCallConnections,
      sharedCallCount: data.sharedCallCount,
    },
    recommendedAction: action,
    actionMessage: message,
    calculatedAt: new Date(),
  };
}

/**
 * 테스트용 샘플 데이터
 */
export function getSampleOfficeData(): OfficeData {
  return {
    totalCustomers: 120,
    activeCustomers: 95,
    totalCalls: 580,
    pointsBalance: 2500000,
    vipCustomers: 12,
    goldCustomers: 28,
    sharedCallConnections: 7,
    sharedCallCount: 35,
  };
}
