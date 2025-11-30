'use client';

import { useState } from 'react';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { Search } from 'lucide-react';
import { searchArchivedCalls } from '@/lib/hooks/useArchivedData';

export default function ArchiveSearchPage() {
  const [loading, setLoading] = useState(false);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [driverName, setDriverName] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [results, setResults] = useState<any>(null);
  const [error, setError] = useState('');

  const handleSearch = async () => {
    if (!startDate || !endDate) {
      setError('시작 날짜와 종료 날짜를 모두 입력해주세요.');
      return;
    }

    if (!phoneNumber && !driverName) {
      setError('전화번호 또는 기사명 중 하나는 입력해주세요.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const result = await searchArchivedCalls({
        phoneNumber,
        driverName,
        startDate,
        endDate
      });

      setResults(result);
    } catch (err: any) {
      setError(err.message || '검색 중 오류가 발생했습니다.');
      console.error('아카이브 검색 오류:', err);
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

  const formatDateTime = (timestamp: any) => {
    if (!timestamp || !timestamp._seconds) return 'N/A';
    const date = new Date(timestamp._seconds * 1000);
    return date.toLocaleString('ko-KR');
  };

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">과거 운행 기록 검색</h1>
          <p className="mt-2 text-gray-600">
            7일 이상 지난 완료된 콜 검색 (아카이브에서 조회)
          </p>
        </div>

        {/* 검색 폼 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">검색 조건</h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                고객 전화번호
              </label>
              <input
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="010-1234-5678"
                className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                기사명
              </label>
              <input
                type="text"
                value={driverName}
                onChange={(e) => setDriverName(e.target.value)}
                placeholder="기사 이름 입력"
                className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>

            <div>
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

            <div>
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
          </div>

          <button
            onClick={handleSearch}
            disabled={loading}
            className="w-full md:w-auto px-6 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:bg-gray-400 flex items-center justify-center gap-2"
          >
            <Search className="h-4 w-4" />
            {loading ? '검색 중...' : '검색'}
          </button>

          {error && (
            <p className="mt-4 text-sm text-red-600">{error}</p>
          )}
        </div>

        {/* 검색 결과 */}
        {results && (
          <div className="bg-white rounded-lg shadow">
            <div className="px-6 py-4 border-b border-gray-200">
              <h2 className="text-lg font-semibold">
                검색 결과 ({results.totalCount.toLocaleString()}건)
              </h2>
              {results.totalCount > 100 && (
                <p className="text-sm text-gray-500 mt-1">
                  최대 100건만 표시됩니다
                </p>
              )}
            </div>

            {results.results.length === 0 ? (
              <div className="p-6 text-center text-gray-500">
                검색 결과가 없습니다.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        완료 시간
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        사무실
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        고객
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        기사
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        출발지
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        목적지
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        요금
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {results.results.map((call: any) => (
                      <tr key={call.id}>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {formatDateTime(call.completedAt)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {call.officeName}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          <div>
                            {call.customerName || 'N/A'}
                          </div>
                          <div className="text-xs text-gray-400">
                            {call.phoneNumber}
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {call.assignedDriverName || 'N/A'}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">
                          {call.pickupAddress || 'N/A'}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">
                          {call.dropoffAddress || 'N/A'}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                          {formatCurrency(call.fare || 0)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* 사용 안내 */}
        {!results && !loading && (
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
            <h3 className="text-lg font-semibold text-blue-900 mb-2">💡 활용 시나리오</h3>
            <ul className="space-y-2 text-sm text-blue-800">
              <li>
                <strong>고객 문의 대응:</strong> "3개월 전 이용했는데 영수증 필요해요"
                → 전화번호로 검색
              </li>
              <li>
                <strong>기사 급여 분쟁:</strong> "2개월 전 정산이 틀렸어요"
                → 기사명과 날짜로 검색
              </li>
              <li>
                <strong>감사 자료:</strong> 특정 기간 특정 기사의 모든 운행 기록 조회
              </li>
              <li className="text-xs text-blue-600 mt-3">
                * 최대 100건까지 표시됩니다. 더 많은 데이터가 필요한 경우 기간을 좁혀서 검색하세요.
              </li>
            </ul>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
