'use client';

import { useState, useEffect } from 'react';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { Download, FileText } from 'lucide-react';
import { getOfficeReport } from '@/lib/hooks/useArchivedData';
import { db } from '@/lib/firebase';
import { collectionGroup, getDocs } from 'firebase/firestore';

export default function MonthlyReportPage() {
  const [loading, setLoading] = useState(false);
  const [offices, setOffices] = useState<any[]>([]);
  const [selectedOffice, setSelectedOffice] = useState({ regionId: '', officeId: '' });
  const [year, setYear] = useState(new Date().getFullYear());
  const [month, setMonth] = useState(new Date().getMonth() + 1);
  const [report, setReport] = useState<any>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    async function fetchOffices() {
      try {
        const officesSnapshot = await getDocs(collectionGroup(db, 'offices'));
        const officesList = officesSnapshot.docs.map(doc => {
          const data = doc.data();
          return {
            id: doc.id,
            regionId: doc.ref.parent.parent?.id || '',
            officeName: data.officeName || data.name || doc.id,
            address: data.address || '',
            phoneNumber: data.phoneNumber || '',
            ...data
          };
        }).sort((a, b) => {
          // 지역명 > 사무실명 순으로 정렬
          if (a.regionId !== b.regionId) {
            return a.regionId.localeCompare(b.regionId);
          }
          return a.officeName.localeCompare(b.officeName);
        });
        setOffices(officesList);
      } catch (err) {
        console.error('사무실 목록 조회 오류:', err);
      }
    }
    fetchOffices();
  }, []);

  const handleGenerateReport = async () => {
    if (!selectedOffice.regionId || !selectedOffice.officeId) {
      setError('사무실을 선택해주세요.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const result = await getOfficeReport({
        regionId: selectedOffice.regionId,
        officeId: selectedOffice.officeId,
        year,
        month
      });

      setReport(result);
    } catch (err: any) {
      setError(err.message || '리포트 생성 중 오류가 발생했습니다.');
      console.error('리포트 생성 오류:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadCSV = () => {
    if (!report) return;

    const csvRows = [];

    // 헤더
    csvRows.push('월간 사무실 리포트');
    csvRows.push(`사무실: ${report.report.office.officeName}`);
    csvRows.push(`기간: ${report.report.period.year}년 ${report.report.period.month}월`);
    csvRows.push('');
    csvRows.push('=== 요약 ===');
    csvRows.push(`총 콜 수,${report.report.summary.totalCalls}`);
    csvRows.push(`총 매출,${report.report.summary.totalFare}`);
    csvRows.push(`기사 수익,${report.report.summary.totalDriverFee}`);
    csvRows.push(`수수료,${report.report.summary.totalCommission}`);
    csvRows.push('');
    csvRows.push('=== 일별 데이터 ===');
    csvRows.push('날짜,콜 수,매출,기사 수익,수수료');

    report.report.dailyData.forEach((day: any) => {
      csvRows.push(`${day.date},${day.calls},${day.fare},${day.driverFee},${day.commission}`);
    });

    csvRows.push('');
    csvRows.push('=== 기사별 실적 ===');
    csvRows.push('기사명,콜 수,총 매출,기사 수익,평균 단가');

    Object.entries(report.report.driverStats).forEach(([driverName, stats]: [string, any]) => {
      const avgFare = stats.totalCalls > 0 ? (stats.totalFare / stats.totalCalls).toFixed(0) : 0;
      csvRows.push(`${driverName},${stats.totalCalls},${stats.totalFare},${stats.totalDriverFee},${avgFare}`);
    });

    const csvContent = csvRows.join('\n');
    const bom = '\uFEFF'; // UTF-8 BOM for Excel
    const blob = new Blob([bom + csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `report_${year}-${month.toString().padStart(2, '0')}_${report.report.office.officeName}.csv`;
    link.click();
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
          <h1 className="text-3xl font-bold text-gray-900">월간 사무실 리포트</h1>
          <p className="mt-2 text-gray-600">
            사무실별 월간 운행 실적 및 기사별 통계
          </p>
        </div>

        {/* 리포트 설정 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-lg font-semibold mb-4">리포트 생성</h2>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                연도
              </label>
              <select
                value={year}
                onChange={(e) => setYear(parseInt(e.target.value))}
                className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500"
              >
                {[2024, 2025, 2026].map(y => (
                  <option key={y} value={y}>{y}년</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                월
              </label>
              <select
                value={month}
                onChange={(e) => setMonth(parseInt(e.target.value))}
                className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500"
              >
                {Array.from({ length: 12 }, (_, i) => i + 1).map(m => (
                  <option key={m} value={m}>{m}월</option>
                ))}
              </select>
            </div>

            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                사무실
              </label>
              <select
                value={`${selectedOffice.regionId}/${selectedOffice.officeId}`}
                onChange={(e) => {
                  const [regionId, officeId] = e.target.value.split('/');
                  setSelectedOffice({ regionId, officeId });
                }}
                className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500"
              >
                <option value="/">사무실 선택</option>
                {offices.map(office => (
                  <option key={office.id} value={`${office.regionId}/${office.id}`}>
                    [{office.regionId}] {office.officeName}
                    {office.address && ` - ${office.address}`}
                    {office.phoneNumber && ` (${office.phoneNumber})`}
                  </option>
                ))}
              </select>
              {selectedOffice.regionId && selectedOffice.officeId && (
                <p className="mt-2 text-sm text-gray-500">
                  선택된 사무실: {offices.find(o => o.id === selectedOffice.officeId)?.officeName}
                </p>
              )}
            </div>
          </div>

          <div className="flex gap-2">
            <button
              onClick={handleGenerateReport}
              disabled={loading}
              className="px-6 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:bg-gray-400 flex items-center gap-2"
            >
              <FileText className="h-4 w-4" />
              {loading ? '생성 중...' : '리포트 생성'}
            </button>

            {report && (
              <button
                onClick={handleDownloadCSV}
                className="px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 flex items-center gap-2"
              >
                <Download className="h-4 w-4" />
                CSV 다운로드
              </button>
            )}
          </div>

          {error && (
            <p className="mt-4 text-sm text-red-600">{error}</p>
          )}
        </div>

        {/* 리포트 결과 */}
        {report && (
          <>
            {/* 사무실 정보 및 요약 */}
            <div className="bg-white rounded-lg shadow p-6">
              <h2 className="text-2xl font-bold mb-2">{report.report.office.officeName}</h2>
              <p className="text-gray-600 mb-4">
                {report.report.period.year}년 {report.report.period.month}월 리포트
              </p>

              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="bg-blue-50 p-4 rounded-lg">
                  <p className="text-sm font-medium text-blue-700">총 콜 수</p>
                  <p className="text-2xl font-bold text-blue-900">
                    {report.report.summary.totalCalls.toLocaleString()}
                  </p>
                </div>
                <div className="bg-green-50 p-4 rounded-lg">
                  <p className="text-sm font-medium text-green-700">총 매출</p>
                  <p className="text-2xl font-bold text-green-900">
                    {formatCurrency(report.report.summary.totalFare)}
                  </p>
                </div>
                <div className="bg-yellow-50 p-4 rounded-lg">
                  <p className="text-sm font-medium text-yellow-700">기사 수익</p>
                  <p className="text-2xl font-bold text-yellow-900">
                    {formatCurrency(report.report.summary.totalDriverFee)}
                  </p>
                </div>
                <div className="bg-purple-50 p-4 rounded-lg">
                  <p className="text-sm font-medium text-purple-700">수수료</p>
                  <p className="text-2xl font-bold text-purple-900">
                    {formatCurrency(report.report.summary.totalCommission)}
                  </p>
                </div>
              </div>
            </div>

            {/* 일별 데이터 */}
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
                    {report.report.dailyData.map((day: any) => (
                      <tr key={day.date}>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                          {day.date}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {day.calls}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(day.fare)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(day.driverFee)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(day.commission)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 기사별 실적 */}
            <div className="bg-white rounded-lg shadow">
              <div className="px-6 py-4 border-b border-gray-200">
                <h2 className="text-lg font-semibold">기사별 실적</h2>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        기사명
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        콜 수
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        총 매출
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        기사 수익
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        평균 단가
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {Object.entries(report.report.driverStats).map(([driverName, stats]: [string, any]) => (
                      <tr key={driverName}>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                          {driverName}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {stats.totalCalls}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(stats.totalFare)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(stats.totalDriverFee)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {formatCurrency(stats.totalFare / stats.totalCalls)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 시간대별 분포 */}
            <div className="bg-white rounded-lg shadow p-6">
              <h2 className="text-lg font-semibold mb-4">시간대별 콜 분포</h2>
              <div className="grid grid-cols-6 gap-2">
                {report.report.hourlyDistribution.map((count: number, hour: number) => (
                  <div key={hour} className="text-center">
                    <div
                      className="bg-indigo-100 rounded"
                      style={{
                        height: `${Math.max(20, (count / Math.max(...report.report.hourlyDistribution)) * 100)}px`
                      }}
                    >
                      <p className="text-xs font-bold text-indigo-900 pt-1">{count}</p>
                    </div>
                    <p className="text-xs text-gray-600 mt-1">{hour}시</p>
                  </div>
                ))}
              </div>
            </div>
          </>
        )}

        {/* 사용 안내 */}
        {!report && !loading && (
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
            <h3 className="text-lg font-semibold text-blue-900 mb-2">💡 활용 방법</h3>
            <ul className="space-y-1 text-sm text-blue-800">
              <li>• 사무실별 월간 실적을 확인할 수 있습니다</li>
              <li>• CSV 파일로 다운로드하여 Excel에서 추가 분석 가능</li>
              <li>• 기사별 성과 평가 및 인센티브 계산에 활용</li>
              <li>• 세무 신고 자료로 활용</li>
            </ul>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
