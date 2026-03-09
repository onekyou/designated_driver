/**
 * 4주 시뮬레이션 사무실/기사 설정
 * 대형(3) × 중형(3) × 소형(4) = 10사무실
 */

import { Office, OFFICES, Driver, getDriversForOffice } from "../../setup/constants";

export type OfficeSize = "LARGE" | "MEDIUM" | "SMALL";

export interface OfficeConfig {
  office: Office;
  size: OfficeSize;
  baseDailyCalls: number;
  drivers: Driver[];
  officeIndex: number;
}

// 사무실 그룹 정의
const OFFICE_SIZES: { indices: number[]; size: OfficeSize; baseCalls: number }[] = [
  { indices: [0, 1, 2], size: "LARGE", baseCalls: 100 },
  { indices: [3, 4, 5], size: "MEDIUM", baseCalls: 50 },
  { indices: [6, 7, 8, 9], size: "SMALL", baseCalls: 20 },
];

// 전체 사무실 설정 생성
export const OFFICE_CONFIGS: OfficeConfig[] = [];

for (const group of OFFICE_SIZES) {
  for (const idx of group.indices) {
    OFFICE_CONFIGS.push({
      office: OFFICES[idx],
      size: group.size,
      baseDailyCalls: group.baseCalls,
      drivers: getDriversForOffice(idx),
      officeIndex: idx,
    });
  }
}

// 그룹별 조회 헬퍼
export function getOfficesBySize(size: OfficeSize): OfficeConfig[] {
  return OFFICE_CONFIGS.filter((c) => c.size === size);
}

export function getOfficeConfig(officeIndex: number): OfficeConfig {
  return OFFICE_CONFIGS.find((c) => c.officeIndex === officeIndex)!;
}

// 일일 콜수 계산 (요일 배율 적용)
export function getDailyCallCount(config: OfficeConfig, dayMultiplier: number): number {
  return Math.round(config.baseDailyCalls * dayMultiplier);
}
