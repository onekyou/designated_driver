export interface LockInScore {
  // 락인 점수 (0-100)
  dataLockIn: number;        // 데이터 락인 점수
  financialLockIn: number;   // 경제적 락인 점수
  networkLockIn: number;     // 네트워크 락인 점수
  totalScore: number;        // 전체 락인 점수 (가중 평균)

  // 이탈 위험도
  churnRisk: 'low' | 'medium' | 'high';

  // 유료 전환 확률 (0-100%)
  conversionProbability: number;

  // 세부 지표
  metrics: {
    // 데이터 락인 관련
    totalCustomers: number;
    activeCustomers: number;
    activeCustomerRatio: number;
    totalCalls: number;
    avgCallsPerCustomer: number;

    // 경제적 락인 관련
    pointsBalance: number;
    vipCustomers: number;
    goldCustomers: number;
    premiumCustomerRatio: number;

    // 네트워크 락인 관련
    sharedCallConnections: number;
    sharedCallCount: number;
  };

  // 추천 액션
  recommendedAction: 'convert' | 'nurture' | 'support';
  actionMessage: string;

  // 계산 시간
  calculatedAt: Date;
}

export interface OfficeWithLockIn {
  officeId: string;
  officeName: string;
  region: string;
  lockInScore: LockInScore;
  subscriptionStatus: 'trial' | 'active' | 'inactive';
  trialDaysElapsed: number;
}
