'use client';

import { useEffect, useState } from 'react';
import { db } from '../firebase';
import { collectionGroup, getDocs, collection, query, where } from 'firebase/firestore';
import { calculateLockInScore } from '../services/lockInCalculator';
import { OfficeWithLockIn } from '../types/lockin';

export function useLockIn() {
  const [offices, setOffices] = useState<OfficeWithLockIn[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchOfficesWithLockIn();
  }, []);

  const fetchOfficesWithLockIn = async () => {
    try {
      setLoading(true);
      setError(null);

      // collectionGroup으로 모든 provinces/cities의 offices 조회
      const officesQuery = collectionGroup(db, 'offices');
      const officesSnapshot = await getDocs(officesQuery);

      const officesList: OfficeWithLockIn[] = [];

      for (const officeDoc of officesSnapshot.docs) {
        const officeData = officeDoc.data();
        const pathParts = officeDoc.ref.path.split('/');
        // provinces/{provinceId}/cities/{cityId}/offices/{officeId}
        const provinceId = pathParts[pathParts.indexOf('provinces') + 1] || '';
        const cityId = pathParts[pathParts.indexOf('cities') + 1] || '';

        // 사무실 통계 데이터 수집
        const stats = {
          totalCustomers: officeData.customerCount || 0,
          activeCustomers: officeData.activeCustomerCount || 0,
          totalCalls: officeData.totalCalls || 0,
          pointsBalance: officeData.pointsBalance || 0,
          vipCustomers: officeData.vipCustomerCount || 0,
          goldCustomers: officeData.goldCustomerCount || 0,
          sharedCallConnections: officeData.sharedCallConnections || 0,
          sharedCallCount: officeData.sharedCallCount || 0,
        };

        // 락인 점수 계산
        const lockInScore = calculateLockInScore(stats);

        // trial 시작일로부터 경과 일수 계산
        const createdAt = officeData.createdAt?.toDate();
        const trialDaysElapsed = createdAt
          ? Math.floor((Date.now() - createdAt.getTime()) / (1000 * 60 * 60 * 24))
          : 0;

        officesList.push({
          officeId: officeDoc.id,
          officeName: officeData.name || '이름 없음',
          region: `${provinceId}/${cityId}`,
          lockInScore,
          subscriptionStatus: officeData.subscriptionStatus || 'trial',
          trialDaysElapsed,
        });
      }

      // 락인 점수순 정렬 (높은 순)
      officesList.sort((a, b) => b.lockInScore.totalScore - a.lockInScore.totalScore);

      setOffices(officesList);
    } catch (err) {
      console.error('락인 데이터 조회 실패:', err);
      setError('락인 데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 전환 준비 사무실 (락인 70점 이상)
  const readyForConversion = offices.filter(
    (office) =>
      office.lockInScore.totalScore >= 70 &&
      office.subscriptionStatus === 'trial'
  );

  // 이탈 위험 사무실 (락인 40점 미만)
  const atRisk = offices.filter(
    (office) => office.lockInScore.totalScore < 40
  );

  // 평균 락인 점수
  const avgLockInScore =
    offices.length > 0
      ? Math.round(
          offices.reduce((sum, o) => sum + o.lockInScore.totalScore, 0) /
            offices.length
        )
      : 0;

  // 통계
  const stats = {
    total: offices.length,
    readyForConversion: readyForConversion.length,
    atRisk: atRisk.length,
    avgLockInScore,
  };

  return {
    offices,
    loading,
    error,
    stats,
    readyForConversion,
    atRisk,
    refresh: fetchOfficesWithLockIn,
  };
}
