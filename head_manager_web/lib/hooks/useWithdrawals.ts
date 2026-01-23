'use client';

import { useEffect, useState } from 'react';
import { db } from '../firebase';
import { collectionGroup, getDocs, doc, updateDoc, serverTimestamp } from 'firebase/firestore';
import { WithdrawalRequest, WithdrawalStats } from '../types/withdrawal';
import { useAuth } from './useAuth';

export function useWithdrawals() {
  const { user } = useAuth();
  const [requests, setRequests] = useState<WithdrawalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchWithdrawals();
  }, []);

  const fetchWithdrawals = async () => {
    try {
      setLoading(true);
      setError(null);

      // collectionGroup으로 모든 provinces/cities의 withdrawalRequests 조회
      const withdrawalsQuery = collectionGroup(db, 'withdrawalRequests');
      const snapshot = await getDocs(withdrawalsQuery);

      const withdrawalsList: WithdrawalRequest[] = [];

      snapshot.forEach((docSnap) => {
        const data = docSnap.data();
        const pathParts = docSnap.ref.path.split('/');
        // provinces/{provinceId}/cities/{cityId}/offices/{officeId}/withdrawalRequests/{requestId}
        const provinceId = pathParts[pathParts.indexOf('provinces') + 1] || '';
        const cityId = pathParts[pathParts.indexOf('cities') + 1] || '';

        withdrawalsList.push({
          id: docSnap.id,
          provinceId,
          cityId,
          officeId: data.officeId,
          officeName: data.officeName,
          requestedBy: data.requestedBy,
          amount: data.amount,
          currentBalance: data.currentBalance,
          fee: data.fee || 0,
          actualAmount: data.actualAmount || data.amount,
          bankInfo: data.bankInfo,
          status: data.status,
          reviewedBy: data.reviewedBy || null,
          reviewedAt: data.reviewedAt?.toDate() || null,
          transferredBy: data.transferredBy || null,
          transferredAt: data.transferredAt?.toDate() || null,
          transferNote: data.transferNote || null,
          rejectedReason: data.rejectedReason || null,
          requestedAt: data.requestedAt?.toDate() || new Date(),
        });
      });

      // 신청일 기준 최신순 정렬
      withdrawalsList.sort((a, b) => b.requestedAt.getTime() - a.requestedAt.getTime());

      setRequests(withdrawalsList);
    } catch (err) {
      console.error('환전 데이터 조회 실패:', err);
      setError('환전 데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 환전 승인
  const approveWithdrawal = async (requestId: string, provinceId: string, cityId: string, officeId: string) => {
    if (!user) return;

    try {
      const requestRef = doc(db, `provinces/${provinceId}/cities/${cityId}/offices/${officeId}/withdrawalRequests/${requestId}`);
      await updateDoc(requestRef, {
        status: 'approved',
        reviewedBy: user.uid,
        reviewedAt: serverTimestamp(),
      });

      // 목록 새로고침
      await fetchWithdrawals();
    } catch (err) {
      console.error('환전 승인 실패:', err);
      throw err;
    }
  };

  // 환전 거부
  const rejectWithdrawal = async (requestId: string, provinceId: string, cityId: string, officeId: string, reason: string) => {
    if (!user) return;

    try {
      const requestRef = doc(db, `provinces/${provinceId}/cities/${cityId}/offices/${officeId}/withdrawalRequests/${requestId}`);
      await updateDoc(requestRef, {
        status: 'rejected',
        reviewedBy: user.uid,
        reviewedAt: serverTimestamp(),
        rejectedReason: reason,
      });

      // 목록 새로고침
      await fetchWithdrawals();
    } catch (err) {
      console.error('환전 거부 실패:', err);
      throw err;
    }
  };

  // 송금 완료 처리
  const completeTransfer = async (requestId: string, provinceId: string, cityId: string, officeId: string, transferNote?: string) => {
    if (!user) return;

    try {
      const requestRef = doc(db, `provinces/${provinceId}/cities/${cityId}/offices/${officeId}/withdrawalRequests/${requestId}`);
      await updateDoc(requestRef, {
        status: 'completed',
        transferredBy: user.uid,
        transferredAt: serverTimestamp(),
        transferNote: transferNote || null,
      });

      // 목록 새로고침
      await fetchWithdrawals();
    } catch (err) {
      console.error('송금 완료 처리 실패:', err);
      throw err;
    }
  };

  // 상태별 필터링
  const pending = requests.filter((r) => r.status === 'pending');
  const approved = requests.filter((r) => r.status === 'approved');
  const completed = requests.filter((r) => r.status === 'completed');
  const rejected = requests.filter((r) => r.status === 'rejected');

  // 이번 달 총 환전 금액 (completed)
  const now = new Date();
  const thisMonth = completed.filter((r) => {
    if (!r.transferredAt) return false;
    return (
      r.transferredAt.getMonth() === now.getMonth() &&
      r.transferredAt.getFullYear() === now.getFullYear()
    );
  });
  const totalAmount = thisMonth.reduce((sum, r) => sum + r.amount, 0);

  const stats: WithdrawalStats = {
    pending: pending.length,
    approved: approved.length,
    completed: completed.length,
    rejected: rejected.length,
    totalAmount,
  };

  return {
    requests,
    loading,
    error,
    stats,
    pending,
    approved,
    completed,
    rejected,
    approveWithdrawal,
    rejectWithdrawal,
    completeTransfer,
    refresh: fetchWithdrawals,
  };
}
