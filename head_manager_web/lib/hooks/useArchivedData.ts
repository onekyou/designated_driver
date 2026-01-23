import { httpsCallable } from 'firebase/functions';
import { functions } from '../firebase';

/**
 * 아카이브된 콜 데이터 통계 조회
 */
export async function getArchivedStats(params: {
  startDate: string;
  endDate: string;
  provinceId?: string;
  cityId?: string;
  officeId?: string;
}) {
  const getArchivedStatsFunc = httpsCallable(functions, 'getArchivedStats');
  const result = await getArchivedStatsFunc(params);
  return result.data as {
    success: boolean;
    period: { startDate: string; endDate: string };
    stats: {
      totalCalls: number;
      totalFare: number;
      totalDriverFee: number;
      totalCommission: number;
      officeStats: Record<string, {
        officeName: string;
        totalCalls: number;
        totalFare: number;
        totalDriverFee: number;
        totalCommission: number;
      }>;
      dailyStats: Record<string, {
        totalCalls: number;
        totalFare: number;
        totalDriverFee: number;
        totalCommission: number;
      }>;
    };
  };
}

/**
 * 아카이브된 콜 검색
 */
export async function searchArchivedCalls(params: {
  phoneNumber?: string;
  driverName?: string;
  startDate: string;
  endDate: string;
  provinceId?: string;
  cityId?: string;
  officeId?: string;
}) {
  const searchArchivedCallsFunc = httpsCallable(functions, 'searchArchivedCalls');
  const result = await searchArchivedCallsFunc(params);
  return result.data as {
    success: boolean;
    results: Array<any>;
    totalCount: number;
  };
}

/**
 * 사무실별 리포트 생성
 */
export async function getOfficeReport(params: {
  provinceId: string;
  cityId: string;
  officeId: string;
  year: number;
  month: number;
}) {
  const getOfficeReportFunc = httpsCallable(functions, 'getOfficeReport');
  const result = await getOfficeReportFunc(params);
  return result.data as {
    success: boolean;
    report: {
      office: {
        provinceId: string;
        cityId: string;
        officeId: string;
        officeName: string;
        address: string;
        phoneNumber: string;
      };
      period: {
        year: number;
        month: number;
        startDate: string;
        endDate: string;
      };
      summary: {
        totalCalls: number;
        totalFare: number;
        totalDriverFee: number;
        totalCommission: number;
        totalTips: number;
      };
      dailyData: Array<{
        date: string;
        calls: number;
        fare: number;
        driverFee: number;
        commission: number;
      }>;
      driverStats: Record<string, {
        totalCalls: number;
        totalFare: number;
        totalDriverFee: number;
      }>;
      hourlyDistribution: number[];
    };
  };
}
