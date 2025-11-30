'use client';

import { useState } from 'react';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import {
  DollarSign,
  Clock,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Copy,
  Check
} from 'lucide-react';
import { useWithdrawals } from '@/lib/hooks/useWithdrawals';
import { WithdrawalRequest } from '@/lib/types/withdrawal';

export default function WithdrawalsPage() {
  const {
    requests,
    loading,
    stats,
    pending,
    approved,
    completed,
    rejected,
    approveWithdrawal,
    rejectWithdrawal,
    completeTransfer
  } = useWithdrawals();

  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [completingId, setCompletingId] = useState<string | null>(null);
  const [transferNote, setTransferNote] = useState('');
  const [copiedAccount, setCopiedAccount] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-screen">
          <div className="text-center">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
            <p className="mt-4 text-gray-600">환전 데이터 로딩 중...</p>
          </div>
        </div>
      </DashboardLayout>
    );
  }

  const handleApprove = async (requestId: string, regionId: string) => {
    if (!confirm('이 환전 요청을 승인하시겠습니까?')) return;

    try {
      setProcessing(true);
      await approveWithdrawal(requestId, regionId);
      alert('환전 요청이 승인되었습니다.');
    } catch (error) {
      alert('승인 처리 중 오류가 발생했습니다.');
      console.error(error);
    } finally {
      setProcessing(false);
    }
  };

  const handleReject = async (requestId: string, regionId: string) => {
    if (!rejectReason.trim()) {
      alert('거부 사유를 입력해주세요.');
      return;
    }

    try {
      setProcessing(true);
      await rejectWithdrawal(requestId, regionId, rejectReason);
      alert('환전 요청이 거부되었습니다.');
      setRejectingId(null);
      setRejectReason('');
    } catch (error) {
      alert('거부 처리 중 오류가 발생했습니다.');
      console.error(error);
    } finally {
      setProcessing(false);
    }
  };

  const handleComplete = async (requestId: string, regionId: string) => {
    if (!confirm('송금을 완료하셨습니까? 이 작업은 되돌릴 수 없습니다.')) return;

    try {
      setProcessing(true);
      await completeTransfer(requestId, regionId, transferNote.trim() || undefined);
      alert('송금 완료 처리되었습니다.');
      setCompletingId(null);
      setTransferNote('');
    } catch (error) {
      alert('완료 처리 중 오류가 발생했습니다.');
      console.error(error);
    } finally {
      setProcessing(false);
    }
  };

  const copyToClipboard = async (text: string, id: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedAccount(id);
      setTimeout(() => setCopiedAccount(null), 2000);
    } catch (error) {
      alert('복사에 실패했습니다.');
    }
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('ko-KR').format(amount);
  };

  const formatDate = (date: Date | null) => {
    if (!date) return '-';
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  };

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold text-gray-900">환전 관리</h1>
          <p className="mt-2 text-gray-600">
            공유콜 포인트 환전 요청을 처리합니다
          </p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">대기 중</p>
                <p className="mt-2 text-3xl font-bold text-yellow-600">{stats.pending}</p>
              </div>
              <div className="p-3 bg-yellow-100 rounded-full">
                <Clock className="h-6 w-6 text-yellow-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">승인 완료</p>
                <p className="mt-2 text-3xl font-bold text-blue-600">{stats.approved}</p>
                <p className="text-xs text-gray-500 mt-1">송금 대기 중</p>
              </div>
              <div className="p-3 bg-blue-100 rounded-full">
                <CheckCircle className="h-6 w-6 text-blue-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">완료</p>
                <p className="mt-2 text-3xl font-bold text-green-600">{stats.completed}</p>
              </div>
              <div className="p-3 bg-green-100 rounded-full">
                <DollarSign className="h-6 w-6 text-green-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">이번 달 총액</p>
                <p className="mt-2 text-2xl font-bold text-gray-900">
                  {formatCurrency(stats.totalAmount)}원
                </p>
              </div>
              <div className="p-3 bg-gray-100 rounded-full">
                <DollarSign className="h-6 w-6 text-gray-600" />
              </div>
            </div>
          </div>
        </div>

        {/* 대기 중인 환전 요청 */}
        {pending.length > 0 && (
          <div className="bg-white rounded-lg shadow">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <Clock className="h-6 w-6 text-yellow-600" />
                  <h2 className="text-xl font-semibold text-gray-900">대기 중인 환전 요청</h2>
                </div>
                <span className="px-3 py-1 bg-yellow-100 text-yellow-800 rounded-full text-sm font-medium">
                  {pending.length}건
                </span>
              </div>
            </div>
            <div className="p-6">
              <div className="space-y-4">
                {pending.map((request) => (
                  <div
                    key={request.id}
                    className="border border-yellow-200 bg-yellow-50 rounded-lg p-6"
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-3">
                          <h3 className="text-lg font-semibold text-gray-900">
                            {request.officeName}
                          </h3>
                          <span className="px-2 py-1 bg-yellow-200 text-yellow-800 rounded text-xs font-medium">
                            {request.regionId}
                          </span>
                        </div>

                        <div className="mt-4 grid grid-cols-2 gap-4">
                          <div>
                            <p className="text-sm text-gray-600">환전 신청 금액</p>
                            <p className="mt-1 text-xl font-bold text-gray-900">
                              {formatCurrency(request.amount)}원
                            </p>
                          </div>
                          <div>
                            <p className="text-sm text-gray-600">현재 잔액</p>
                            <p className="mt-1 text-xl font-bold text-gray-900">
                              {formatCurrency(request.currentBalance)}원
                            </p>
                          </div>
                          <div>
                            <p className="text-sm text-gray-600">수수료</p>
                            <p className="mt-1 text-lg font-medium text-gray-700">
                              {formatCurrency(request.fee)}원
                            </p>
                          </div>
                          <div>
                            <p className="text-sm text-gray-600">실제 송금액</p>
                            <p className="mt-1 text-lg font-bold text-green-600">
                              {formatCurrency(request.actualAmount)}원
                            </p>
                          </div>
                        </div>

                        <div className="mt-4 p-4 bg-white rounded border border-gray-200">
                          <p className="text-sm font-medium text-gray-700 mb-2">계좌 정보</p>
                          <div className="space-y-1">
                            <p className="text-sm text-gray-900">
                              <span className="font-medium">은행:</span> {request.bankInfo.bankName}
                            </p>
                            <p className="text-sm text-gray-900">
                              <span className="font-medium">계좌번호:</span> {request.bankInfo.accountNumber}
                            </p>
                            <p className="text-sm text-gray-900">
                              <span className="font-medium">예금주:</span> {request.bankInfo.accountHolder}
                            </p>
                          </div>
                        </div>

                        <p className="mt-2 text-xs text-gray-500">
                          신청일: {formatDate(request.requestedAt)}
                        </p>
                      </div>

                      <div className="ml-6 flex flex-col space-y-2">
                        {rejectingId === request.id ? (
                          <div className="bg-white p-4 rounded-lg border border-red-200 w-64">
                            <p className="text-sm font-medium text-gray-900 mb-2">거부 사유</p>
                            <textarea
                              value={rejectReason}
                              onChange={(e) => setRejectReason(e.target.value)}
                              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm resize-none"
                              rows={3}
                              placeholder="거부 사유를 입력하세요"
                            />
                            <div className="mt-2 flex space-x-2">
                              <button
                                onClick={() => handleReject(request.id, request.regionId)}
                                disabled={processing}
                                className="flex-1 px-3 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 text-sm font-medium disabled:opacity-50"
                              >
                                확인
                              </button>
                              <button
                                onClick={() => {
                                  setRejectingId(null);
                                  setRejectReason('');
                                }}
                                disabled={processing}
                                className="flex-1 px-3 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 text-sm font-medium disabled:opacity-50"
                              >
                                취소
                              </button>
                            </div>
                          </div>
                        ) : (
                          <>
                            <button
                              onClick={() => handleApprove(request.id, request.regionId)}
                              disabled={processing}
                              className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors text-sm font-medium disabled:opacity-50 whitespace-nowrap"
                            >
                              승인
                            </button>
                            <button
                              onClick={() => setRejectingId(request.id)}
                              disabled={processing}
                              className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors text-sm font-medium disabled:opacity-50 whitespace-nowrap"
                            >
                              거부
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* 승인 완료 (송금 대기) */}
        {approved.length > 0 && (
          <div className="bg-white rounded-lg shadow border-2 border-blue-200">
            <div className="p-6 border-b border-gray-200 bg-blue-50">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <AlertTriangle className="h-6 w-6 text-blue-600" />
                  <h2 className="text-xl font-semibold text-gray-900">송금 대기 중</h2>
                  <span className="text-sm text-blue-600 font-medium">
                    실제 송금을 완료해주세요
                  </span>
                </div>
                <span className="px-3 py-1 bg-blue-600 text-white rounded-full text-sm font-medium">
                  {approved.length}건
                </span>
              </div>
            </div>
            <div className="p-6">
              <div className="space-y-4">
                {approved.map((request) => (
                  <div
                    key={request.id}
                    className="border-2 border-blue-300 bg-blue-50 rounded-lg p-6"
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-3">
                          <h3 className="text-lg font-semibold text-gray-900">
                            {request.officeName}
                          </h3>
                          <span className="px-2 py-1 bg-blue-200 text-blue-800 rounded text-xs font-medium">
                            {request.regionId}
                          </span>
                        </div>

                        <div className="mt-4 p-4 bg-white rounded-lg border-2 border-blue-200">
                          <p className="text-base font-bold text-blue-600 mb-3">
                            💰 송금 금액: {formatCurrency(request.actualAmount)}원
                          </p>
                          <div className="space-y-2">
                            <div className="flex items-center justify-between">
                              <p className="text-sm text-gray-900">
                                <span className="font-medium">은행:</span> {request.bankInfo.bankName}
                              </p>
                            </div>
                            <div className="flex items-center justify-between">
                              <p className="text-sm text-gray-900">
                                <span className="font-medium">계좌번호:</span> {request.bankInfo.accountNumber}
                              </p>
                              <button
                                onClick={() => copyToClipboard(request.bankInfo.accountNumber, request.id)}
                                className="ml-2 p-1 hover:bg-gray-100 rounded transition-colors"
                              >
                                {copiedAccount === request.id ? (
                                  <Check className="h-4 w-4 text-green-600" />
                                ) : (
                                  <Copy className="h-4 w-4 text-gray-600" />
                                )}
                              </button>
                            </div>
                            <div className="flex items-center justify-between">
                              <p className="text-sm text-gray-900">
                                <span className="font-medium">예금주:</span> {request.bankInfo.accountHolder}
                              </p>
                            </div>
                          </div>
                        </div>

                        <div className="mt-3 text-xs text-gray-600 space-y-1">
                          <p>신청일: {formatDate(request.requestedAt)}</p>
                          <p>승인일: {formatDate(request.reviewedAt)}</p>
                        </div>
                      </div>

                      <div className="ml-6">
                        {completingId === request.id ? (
                          <div className="bg-white p-4 rounded-lg border border-blue-200 w-64">
                            <p className="text-sm font-medium text-gray-900 mb-2">송금 메모 (선택)</p>
                            <input
                              type="text"
                              value={transferNote}
                              onChange={(e) => setTransferNote(e.target.value)}
                              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                              placeholder="거래번호 등"
                            />
                            <div className="mt-2 flex space-x-2">
                              <button
                                onClick={() => handleComplete(request.id, request.regionId)}
                                disabled={processing}
                                className="flex-1 px-3 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 text-sm font-medium disabled:opacity-50"
                              >
                                완료
                              </button>
                              <button
                                onClick={() => {
                                  setCompletingId(null);
                                  setTransferNote('');
                                }}
                                disabled={processing}
                                className="flex-1 px-3 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 text-sm font-medium disabled:opacity-50"
                              >
                                취소
                              </button>
                            </div>
                          </div>
                        ) : (
                          <button
                            onClick={() => setCompletingId(request.id)}
                            disabled={processing}
                            className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium disabled:opacity-50 whitespace-nowrap"
                          >
                            송금 완료 처리
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* 완료된 환전 내역 */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="p-6 border-b border-gray-200">
            <h2 className="text-xl font-semibold text-gray-900">완료된 환전 내역</h2>
          </div>
          <div className="overflow-x-auto">
            {completed.length > 0 ? (
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      사무실
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      송금액
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      수수료
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      계좌 정보
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      송금 메모
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      완료일
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {completed.map((request) => (
                    <tr key={request.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div>
                          <div className="text-sm font-medium text-gray-900">
                            {request.officeName}
                          </div>
                          <div className="text-sm text-gray-500">{request.regionId}</div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm font-medium text-gray-900">
                          {formatCurrency(request.actualAmount)}원
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">
                          {formatCurrency(request.fee)}원
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="text-sm text-gray-900">
                          <div>{request.bankInfo.bankName}</div>
                          <div className="text-gray-600">{request.bankInfo.accountNumber}</div>
                          <div className="text-gray-600">{request.bankInfo.accountHolder}</div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-600">
                          {request.transferNote || '-'}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">
                          {formatDate(request.transferredAt)}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-12 text-center">
                <CheckCircle className="mx-auto h-12 w-12 text-gray-400" />
                <p className="mt-4 text-gray-600">완료된 환전 내역이 없습니다</p>
              </div>
            )}
          </div>
        </div>

        {/* 거부된 환전 내역 (선택적으로 표시) */}
        {rejected.length > 0 && (
          <details className="bg-white rounded-lg shadow">
            <summary className="p-6 cursor-pointer hover:bg-gray-50">
              <span className="text-lg font-semibold text-gray-900">
                거부된 환전 내역 ({rejected.length}건)
              </span>
            </summary>
            <div className="px-6 pb-6">
              <div className="space-y-3">
                {rejected.map((request) => (
                  <div
                    key={request.id}
                    className="border border-red-200 bg-red-50 rounded-lg p-4"
                  >
                    <div className="flex items-start justify-between">
                      <div>
                        <h4 className="font-medium text-gray-900">{request.officeName}</h4>
                        <p className="text-sm text-gray-600 mt-1">
                          신청 금액: {formatCurrency(request.amount)}원
                        </p>
                        <p className="text-sm text-red-600 mt-2">
                          <span className="font-medium">거부 사유:</span> {request.rejectedReason}
                        </p>
                        <p className="text-xs text-gray-500 mt-1">
                          거부일: {formatDate(request.reviewedAt)}
                        </p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </details>
        )}
      </div>
    </DashboardLayout>
  );
}
