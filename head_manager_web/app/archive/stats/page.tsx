'use client';

import { useState } from 'react';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { Phone, DollarSign, Users, Percent, Search } from 'lucide-react';
import { getArchivedStats } from '@/lib/hooks/useArchivedData';

export default function ArchiveStatsPage() {
  const [loading, setLoading] = useState(false);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [stats, setStats] = useState<any>(null);
  const [error, setError] = useState('');

  const handleSearch = async () => {
    if (!startDate || !endDate) {
      setError('시작 날짜와 종료 날짜를 모두 입력해주세요.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const result = await getArchivedStats({
        startDate,
        endDate
      });

      setStats(result);
    } catch (err: any) {
      setError(err.message || '데이터 조회 중 오류가 발생했습니다.');
      console.error('아카이브 통계 조회 오류:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('ko-KR', {
      style: 'currency',
      currency: 'KRW'
    }).format(amount);
  };

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">아카이브 데이터 조회</h1>
          <p className="mt-2 text-gray-600">
            7일 이상 지난 완료된 콜 데이터 통계
          </p>
        </div>

        {/* 검색 필터 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">조회 기간 선택</h2>
          <div className="flex gap-4 items-end">
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                시작 날짜
              </label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                종료 날짜
              </label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>
            <button
              onClick={handleSearch}
              disabled={loading}
              className="px-6 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:bg-gray-400 flex items-center gap-2"
            >
              <Search className="h-4 w-4" />
              {loading ? '조회 중...' : '조회'}
            </button>
          </div>
          {error && (
            <p className="mt-4 text-sm text-red-600">{error}</p>
          )}
        </div>

        {/* 통계 결과 */}
        {stats && (
          <>
            {/* 전체 통계 카드 */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-600">총 콜 수</p>
                    <p className="mt-2 text-3xl font-bold text-gray-900">
                      {stats.stats.totalCalls.toLocaleString()}
                    </p>
                  </div>
                  <div className="p-3 bg-blue-100 rounded-full">
                    <Phone className="h-6 w-6 text-blue-600" />
                  </div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-600">총 요금</p>
                    <p className="mt-2 text-2xl font-bold text-gray-900">
                      {formatCurrency(stats.stats.totalFare)}
                    </p>
                  </div>
                  <div className="p-3 bg-green-100 rounded-full">
                    <DollarSign className="h-6 w-6 text-green-600" />
                  </div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-600">총 기사 수익</p>
                    <p className="mt-2 text-2xl font-bold text-gray-900">
                      {formatCurrency(stats.stats.totalDriverFee)}
                    </p>
                  </div>
                  <div className="p-3 bg-yellow-100 rounded-full">
                    <Users className="h-6 w-6 text-yellow-600" />
                  </div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-lg shadow">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-600">총 수수료</p>
                    <p className="mt-2 text-2xl font-bold text-gray-900">
                      {formatCurrency(stats.stats.totalCommission)}
                    </p>
                  </div>
                  <div className="p-3 bg-purple-100 rounded-full">
                    <Percent className="h-6 w-6 text-purple-600" />
                  </div>
                </div>
              </div>
            </div>

            {/* 사무실별 통계 테이블 */}
            <div className="bg-white rounded-lg shadow">
              <div className="px-6 py-4 border-b border-gray-200">
                <h2 className="text-lg font-semibold">사무실별 집계</h2>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        지역
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        사무실
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        콜 수
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        총 요금
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        기사 수익
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        수수료
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {Object.entries(stats.stats.officeStats).map(([key, stat]: [string, any]) => {
                      // key format: provinceId/cityId/officeId
                      const parts = key.split('/');
                      const location = parts.length >= 3 ? `${parts[0]}/${parts[1]}` : parts[0];
                      const officeId = parts.length >= 3 ? parts[2] : parts[1];
                      return (
                        <tr key={key}>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                            {location}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                            {stat.officeName || officeId}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                            {stat.totalCalls.toLocaleString()}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                            {formatCurrency(stat.totalFare)}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                            {formatCurrency(stat.totalDriverFee)}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                            {formatCurrency(stat.totalCommission)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 일별 통계 */}
            <div className="bg-white rounded-lg shadow">
              <div className="px-6 py-4 border-b border-gray-200">
                <h2 className="text-lg font-semibold">일별 운행 현황</h2>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        날짜
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        콜 수
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        매출
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        기사 수익
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        수수료
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {Object.entries(stats.stats.dailyStats)
                      .sort(([a], [b]) => b.localeCompare(a))
                      .map(([date, data]: [string, any]) => (
                      <tr key={date}>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                          {date}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {data.totalCalls.toLocaleString()}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(data.totalFare)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(data.totalDriverFee)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(data.totalCommission)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}

        {/* 사용 안내 */}
        {!stats && !loading && (
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
            <h3 className="text-lg font-semibold text-blue-900 mb-2">💡 사용 안내</h3>
            <ul className="space-y-1 text-sm text-blue-800">
              <li>• 7일 이상 지난 완료된 콜 데이터를 조회할 수 있습니다</li>
              <li>• 조회 기간을 선택하고 "조회" 버튼을 클릭하세요</li>
              <li>• 사무실별, 일별 상세 통계를 확인할 수 있습니다</li>
              <li>• 세무 신고, 성과 분석 등에 활용하세요</li>
            </ul>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
