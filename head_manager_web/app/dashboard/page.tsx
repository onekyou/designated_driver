'use client';

import { useEffect, useState } from 'react';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { Building2, TrendingUp, DollarSign, Users } from 'lucide-react';
import { db } from '@/lib/firebase';
import { collectionGroup, getDocs, query, where } from 'firebase/firestore';

export default function DashboardPage() {
  const [stats, setStats] = useState({
    totalOffices: 0,
    activeOffices: 0,
    avgLockInScore: 0,
    pendingWithdrawals: 0
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchStats() {
      try {
        // collectionGroup으로 모든 region의 offices 조회
        const officesQuery = query(collectionGroup(db, 'offices'));
        const officesSnapshot = await getDocs(officesQuery);

        const totalOffices = officesSnapshot.size;

        // 활성 사무실 계산 (status가 'active'인 사무실)
        const activeOffices = officesSnapshot.docs.filter(
          doc => doc.data().status === 'active'
        ).length;

        setStats({
          totalOffices,
          activeOffices,
          avgLockInScore: 0, // Phase 3에서 구현
          pendingWithdrawals: 0 // Phase 4에서 구현
        });
      } catch (error) {
        console.error('데이터 조회 오류:', error);
      } finally {
        setLoading(false);
      }
    }

    fetchStats();
  }, []);

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-screen">
          <div className="text-lg">데이터 로딩 중...</div>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">대시보드</h1>
          <p className="mt-2 text-gray-600">
            총관리자 시스템에 오신 것을 환영합니다
          </p>
        </div>

        {/* 통계 카드 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">전체 사무실</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{stats.totalOffices}</p>
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
                <p className="mt-2 text-3xl font-bold text-gray-900">{stats.activeOffices}</p>
              </div>
              <div className="p-3 bg-green-100 rounded-full">
                <Users className="h-6 w-6 text-green-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">평균 락인 점수</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{stats.avgLockInScore}</p>
                <p className="text-xs text-gray-500 mt-1">Phase 3에서 구현 예정</p>
              </div>
              <div className="p-3 bg-yellow-100 rounded-full">
                <TrendingUp className="h-6 w-6 text-yellow-600" />
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">환전 대기</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{stats.pendingWithdrawals}</p>
                <p className="text-xs text-gray-500 mt-1">Phase 4에서 구현 예정</p>
              </div>
              <div className="p-3 bg-red-100 rounded-full">
                <DollarSign className="h-6 w-6 text-red-600" />
              </div>
            </div>
          </div>
        </div>

        {/* Phase 2 완료 상태 */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">✅ Phase 2 완료</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <h3 className="font-medium text-gray-700">완료된 작업</h3>
              <ul className="space-y-1 text-sm text-gray-600">
                <li>✅ Firebase 인증 연동</li>
                <li>✅ Firestore 데이터 조회</li>
                <li>✅ 권한 체계 (HEAD_MANAGER)</li>
                <li>✅ Sidebar 레이아웃</li>
                <li>✅ collectionGroup 쿼리</li>
              </ul>
            </div>
            <div className="space-y-2">
              <h3 className="font-medium text-gray-700">조회된 데이터</h3>
              <ul className="space-y-1 text-sm text-gray-600">
                <li>• 전체 사무실: <strong>{stats.totalOffices}개</strong></li>
                <li>• 활성 사무실: <strong>{stats.activeOffices}개</strong></li>
                <li>• Firestore 연결: <strong className="text-green-600">정상</strong></li>
              </ul>
            </div>
          </div>
        </div>

        {/* 주요 기능 안내 */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">주요 기능</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div>
              <div className="flex items-center space-x-3 mb-2">
                <Building2 className="h-5 w-5 text-indigo-600" />
                <h3 className="font-semibold">사무실 관리</h3>
              </div>
              <p className="text-sm text-gray-600">
                전국 사무실 등록, 모니터링, 구독 관리
              </p>
            </div>
            <div>
              <div className="flex items-center space-x-3 mb-2">
                <TrendingUp className="h-5 w-5 text-indigo-600" />
                <h3 className="font-semibold">락인 모니터링</h3>
              </div>
              <p className="text-sm text-gray-600">
                사무실별 락인 점수 계산 및 이탈 위험 분석
              </p>
            </div>
            <div>
              <div className="flex items-center space-x-3 mb-2">
                <DollarSign className="h-5 w-5 text-indigo-600" />
                <h3 className="font-semibold">환전 관리</h3>
              </div>
              <p className="text-sm text-gray-600">
                공유콜 포인트 환전 요청 승인 및 처리
              </p>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
