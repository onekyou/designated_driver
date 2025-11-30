export interface BankInfo {
  bankName: string;
  accountNumber: string;
  accountHolder: string;
}

export interface WithdrawalRequest {
  id: string;
  regionId: string;
  officeId: string;
  officeName: string;
  requestedBy: string; // admin uid

  // 금액 정보
  amount: number;
  currentBalance: number;
  fee: number;
  actualAmount: number; // 실제 송금 금액 (수수료 제외)

  // 계좌 정보
  bankInfo: BankInfo;

  // 상태
  status: 'pending' | 'approved' | 'completed' | 'rejected';

  // 처리 정보
  reviewedBy: string | null; // 승인자 uid
  reviewedAt: Date | null;

  transferredBy: string | null; // 송금 처리자 uid
  transferredAt: Date | null;
  transferNote: string | null; // 송금 메모 (거래번호 등)

  rejectedReason: string | null;

  requestedAt: Date;
}

export interface WithdrawalStats {
  pending: number;
  approved: number;
  completed: number;
  rejected: number;
  totalAmount: number; // 이번 달 총 환전 금액
}
