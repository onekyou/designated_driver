'use client';

import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { TrendingUp, TrendingDown, AlertTriangle, CheckCircle, Users, DollarSign, Share2 } from 'lucide-react';
import { useLockIn } from '@/lib/hooks/useLockIn';

export default function LockInPage() {
  const { offices, loading, stats, readyForConversion, atRisk } = useLockIn();

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-screen">
          <div className="text-center">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
            <p className="mt-4 text-gray-600">락인 데이터 분석 중...</p>
          </div>
        </div>
      </DashboardLayout>
    );
  }

  const getScoreColor = (score: number) => {
    if (score >= 70) return 'text-green-600';
    if (score >= 40) return 'text-yellow-600';
    return 'text-red-600';
  };

  const getScoreBgColor = (score: number) => {
    if (score >= 70) return 'bg-green-100';
    if (score >= 40) return 'bg-yellow-100';
    return 'bg-red-100';
  };

  const getRiskBadge = (risk: 'low' | 'medium' | 'high') => {
    const styles = {
      low: 'bg-green-100 text-green-800',
      medium: 'bg-yellow-100 text-yellow-800',
      high: 'bg-red-100 text-red-800',
    };
    const labels = {
      low: '안전',
      medium: '주의',
      high: '위험',
    };
    return (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${styles[risk]}`}>
        {labels[risk]}
      </span>
    );
  };

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold text-gray-900">락인 모니터링</h1>
          <p className="mt-2 text-gray-600">
            사무실별 락인 점수를 확인하고 유료 전환을 관리합니다
          </p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">평균 락인 점수</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{stats.avgLockInScore}</p>
              </div>
              <div className="p-3 bg-indigo-100 rounded-full">
                <TrendingUp className="h-6 w-6 text-indigo-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">전환 준비</p>
                <p className="mt-2 text-3xl font-bold text-green-600">{stats.readyForConversion}</p>
                <p className="text-xs text-gray-500 mt-1">락인 70점 이상</p>
              </div>
              <div className="p-3 bg-green-100 rounded-full">
                <CheckCircle className="h-6 w-6 text-green-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">이탈 위험</p>
                <p className="mt-2 text-3xl font-bold text-red-600">{stats.atRisk}</p>
                <p className="text-xs text-gray-500 mt-1">락인 40점 미만</p>
              </div>
              <div className="p-3 bg-red-100 rounded-full">
                <AlertTriangle className="h-6 w-6 text-red-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">전체 사무실</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{stats.total}</p>
              </div>
              <div className="p-3 bg-gray-100 rounded-full">
                <Users className="h-6 w-6 text-gray-600" />
              </div>
            </div>
          </div>
        </div>

        {/* 전환 준비 사무실 */}
        {readyForConversion.length > 0 && (
          <div className="bg-white rounded-lg shadow">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <CheckCircle className="h-6 w-6 text-green-600" />
                  <h2 className="text-xl font-semibold text-gray-900">유료 전환 준비 완료</h2>
                </div>
                <span className="px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm font-medium">
                  {readyForConversion.length}개
                </span>
              </div>
            </div>
            <div className="p-6">
              <div className="space-y-4">
                {readyForConversion.slice(0, 5).map((office) => (
                  <div
                    key={`${office.region}-${office.officeId}`}
                    className="flex items-center justify-between p-4 bg-green-50 rounded-lg"
                  >
                    <div className="flex-1">
                      <h3 className="font-medium text-gray-900">{office.officeName}</h3>
                      <p className="text-sm text-gray-600">
                        {office.region} · 무료체험 {office.trialDaysElapsed}일째
                      </p>
                    </div>
                    <div className="flex items-center space-x-6">
                      <div className="text-right">
                        <p className="text-2xl font-bold text-green-600">
                          {office.lockInScore.totalScore}
                        </p>
                        <p className="text-xs text-gray-600">락인 점수</p>
                      </div>
                      <div className="text-right">
                        <p className="text-lg font-bold text-gray-900">
                          {office.lockInScore.conversionProbability}%
                        </p>
                        <p className="text-xs text-gray-600">전환 확률</p>
                      </div>
                      <button className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors text-sm font-medium">
                        전환 제안
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* 이탈 위험 사무실 */}
        {atRisk.length > 0 && (
          <div className="bg-white rounded-lg shadow">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <AlertTriangle className="h-6 w-6 text-red-600" />
                  <h2 className="text-xl font-semibold text-gray-900">이탈 위험 사무실</h2>
                </div>
                <span className="px-3 py-1 bg-red-100 text-red-800 rounded-full text-sm font-medium">
                  {atRisk.length}개
                </span>
              </div>
            </div>
            <div className="p-6">
              <div className="space-y-4">
                {atRisk.slice(0, 5).map((office) => (
                  <div
                    key={`${office.region}-${office.officeId}`}
                    className="flex items-center justify-between p-4 bg-red-50 rounded-lg"
                  >
                    <div className="flex-1">
                      <h3 className="font-medium text-gray-900">{office.officeName}</h3>
                      <p className="text-sm text-gray-600">
                        {office.region} · 무료체험 {office.trialDaysElapsed}일째
                      </p>
                    </div>
                    <div className="flex items-center space-x-6">
                      <div className="text-right">
                        <p className="text-2xl font-bold text-red-600">
                          {office.lockInScore.totalScore}
                        </p>
                        <p className="text-xs text-gray-600">락인 점수</p>
                      </div>
                      <div className="text-right">
                        <p className="text-sm text-gray-900">
                          {office.lockInScore.actionMessage}
                        </p>
                      </div>
                      <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm font-medium">
                        지원하기
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* 전체 사무실 목록 */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="p-6 border-b border-gray-200">
            <h2 className="text-xl font-semibold text-gray-900">전체 사무실 락인 현황</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    사무실
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    락인 점수
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    데이터
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    경제적
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    네트워크
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    이탈 위험
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    전환 확률
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    추천 액션
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {offices.map((office) => (
                  <tr key={`${office.region}-${office.officeId}`} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div>
                        <div className="text-sm font-medium text-gray-900">{office.officeName}</div>
                        <div className="text-sm text-gray-500">{office.region}</div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        <div className={`text-2xl font-bold ${getScoreColor(office.lockInScore.totalScore)}`}>
                          {office.lockInScore.totalScore}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        <Users className="h-4 w-4 mr-1 text-gray-400" />
                        <span className="text-sm text-gray-900">{office.lockInScore.dataLockIn}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        <DollarSign className="h-4 w-4 mr-1 text-gray-400" />
                        <span className="text-sm text-gray-900">{office.lockInScore.financialLockIn}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        <Share2 className="h-4 w-4 mr-1 text-gray-400" />
                        <span className="text-sm text-gray-900">{office.lockInScore.networkLockIn}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {getRiskBadge(office.lockInScore.churnRisk)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-sm font-medium text-gray-900">
                        {office.lockInScore.conversionProbability}%
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-xs text-gray-600">
                        {office.lockInScore.actionMessage}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
