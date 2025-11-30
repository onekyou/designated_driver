'use client';

import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { useEffect, useState } from 'react';
import { collection, query, getDocs, collectionGroup } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Office } from '@/lib/types/office';
import { Search, Plus, Building2, Users, Phone } from 'lucide-react';
import Link from 'next/link';

export default function OfficesPage() {
  const [offices, setOffices] = useState<Office[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    fetchOffices();
  }, []);

  const fetchOffices = async () => {
    try {
      setLoading(true);

      // collectionGroup을 사용하여 모든 region의 offices를 조회
      const officesQuery = collectionGroup(db, 'offices');
      const snapshot = await getDocs(officesQuery);

      const officesList: Office[] = [];

      // 각 사무실 문서에서 직접 통계 필드 읽기 (빠름!)
      snapshot.forEach((doc) => {
        const data = doc.data();

        // document 경로에서 regionId 추출
        const pathParts = doc.ref.path.split('/');
        const regionId = pathParts[pathParts.indexOf('regions') + 1];

        officesList.push({
          id: doc.id,
          regionId: regionId,
          name: data.name || '이름 없음',
          region: data.region || regionId,
          phoneNumber: data.phone || data.phoneNumber, // 실제 필드명은 'phone'
          address: data.address,
          qrCode: data.qrCode,
          inviteCode: data.inviteCode,
          landingPageUrl: data.landingPageUrl,
          attributionThreshold: data.attributionThreshold,
          driverCount: data.driverCount || 0, // 문서에서 직접 읽기
          customerCount: data.customerCount || 0, // 문서에서 직접 읽기
          subscriptionTier: data.subscriptionTier || 'small',
          subscriptionStatus: data.subscriptionStatus || 'trial',
          createdAt: data.createdAt,
          updatedAt: data.updatedAt,
        });
      });

      setOffices(officesList);
    } catch (error) {
      console.error('사무실 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const filteredOffices = offices.filter(
    (office) =>
      office.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      office.region.toLowerCase().includes(searchTerm.toLowerCase()) ||
      office.phoneNumber?.includes(searchTerm)
  );

  const getStatusBadge = (status?: string) => {
    const styles = {
      trial: 'bg-yellow-100 text-yellow-800',
      active: 'bg-green-100 text-green-800',
      inactive: 'bg-gray-100 text-gray-800',
    };
    const labels = {
      trial: '무료체험',
      active: '활성',
      inactive: '비활성',
    };
    return (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${styles[status as keyof typeof styles] || styles.inactive}`}>
        {labels[status as keyof typeof labels] || '알 수 없음'}
      </span>
    );
  };

  const getTierBadge = (tier?: string) => {
    const styles = {
      small: 'bg-blue-100 text-blue-800',
      medium: 'bg-purple-100 text-purple-800',
      large: 'bg-orange-100 text-orange-800',
    };
    const labels = {
      small: '소형 (3명)',
      medium: '중형 (5명)',
      large: '대형 (5명+)',
    };
    return tier ? (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${styles[tier as keyof typeof styles]}`}>
        {labels[tier as keyof typeof labels]}
      </span>
    ) : null;
  };

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">사무실 관리</h1>
            <p className="mt-2 text-gray-600">
              전국 사무실을 관리하고 모니터링합니다
            </p>
          </div>
          <Link
            href="/offices/new"
            className="flex items-center space-x-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
          >
            <Plus className="h-5 w-5" />
            <span>사무실 등록</span>
          </Link>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">전체 사무실</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{offices.length}</p>
              </div>
              <div className="p-3 bg-indigo-100 rounded-full">
                <Building2 className="h-6 w-6 text-indigo-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">활성 사무실</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">
                  {offices.filter(o => o.subscriptionStatus === 'active').length}
                </p>
              </div>
              <div className="p-3 bg-green-100 rounded-full">
                <Building2 className="h-6 w-6 text-green-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">무료체험</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">
                  {offices.filter(o => o.subscriptionStatus === 'trial').length}
                </p>
              </div>
              <div className="p-3 bg-yellow-100 rounded-full">
                <Building2 className="h-6 w-6 text-yellow-600" />
              </div>
            </div>
          </div>
        </div>

        {/* Search */}
        <div className="bg-white p-4 rounded-lg shadow">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-gray-400" />
            <input
              type="text"
              placeholder="사무실 이름, 지역, 전화번호로 검색..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>
        </div>

        {/* Office List */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          {loading ? (
            <div className="p-12 text-center">
              <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
              <p className="mt-4 text-gray-600">로딩 중...</p>
            </div>
          ) : filteredOffices.length === 0 ? (
            <div className="p-12 text-center">
              <Building2 className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-4 text-lg font-medium text-gray-900">사무실이 없습니다</h3>
              <p className="mt-2 text-gray-600">새로운 사무실을 등록해주세요</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      사무실 정보
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      지역
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      연락처
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      기사/고객
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      구독
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      상태
                    </th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                      작업
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {filteredOffices.map((office) => (
                    <tr key={`${office.regionId}-${office.id}`} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center">
                          <div className="flex-shrink-0 h-10 w-10 bg-indigo-100 rounded-full flex items-center justify-center">
                            <Building2 className="h-5 w-5 text-indigo-600" />
                          </div>
                          <div className="ml-4">
                            <div className="text-sm font-medium text-gray-900">{office.name}</div>
                            <div className="text-sm text-gray-500">{office.address || '-'}</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">{office.region}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center text-sm text-gray-900">
                          <Phone className="h-4 w-4 mr-1 text-gray-400" />
                          {office.phoneNumber || '-'}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center text-sm text-gray-900">
                          <Users className="h-4 w-4 mr-1 text-gray-400" />
                          {office.driverCount || 0} / {office.customerCount || 0}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {getTierBadge(office.subscriptionTier)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {getStatusBadge(office.subscriptionStatus)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                        <Link
                          href={`/offices/${office.regionId}/${office.id}`}
                          className="text-indigo-600 hover:text-indigo-900"
                        >
                          상세보기
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
}
