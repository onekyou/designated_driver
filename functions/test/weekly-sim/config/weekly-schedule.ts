/**
 * 4주 28일 스케줄 정의
 * 요일별 배율 + 주차별 시나리오 특성
 */

// 요일 배율 (일=0, 월=1, ..., 토=6)
export const DAY_MULTIPLIERS: Record<number, number> = {
  0: 0.60,  // 일요일
  1: 0.70,  // 월요일
  2: 0.80,  // 화요일
  3: 0.90,  // 수요일
  4: 1.10,  // 목요일
  5: 1.20,  // 금요일
  6: 1.15,  // 토요일
};

// 요일 이름 (한국어)
export const DAY_NAMES: Record<number, string> = {
  0: "일", 1: "월", 2: "화", 3: "수", 4: "목", 5: "금", 6: "토",
};

// 일별 설정
export interface DayConfig {
  week: number;           // 1~4
  dayInWeek: number;      // 0~6 (일~토)
  dayNumber: number;      // 1~28 (전체 일수)
  multiplier: number;     // 요일 배율
  description: string;    // 설명
  sharedCallTarget: number; // 공유콜 목표 수
  specialFlags: string[]; // 특수 시나리오 플래그
}

// 28일 전체 스케줄 생성
export function generateSchedule(): DayConfig[] {
  const schedule: DayConfig[] = [];

  const weekDescriptions: { description: string; sharedCalls: number[]; flags: string[][] }[] = [
    // Week 1: 워밍업
    {
      description: "워밍업",
      sharedCalls: [0, 0, 3, 5, 10, 15, 12],
      flags: [
        ["CASH_ONLY"],                // 일: 현금 위주
        ["MIXED_PAYMENT"],            // 월: 결제 혼합
        ["REJECT_FOCUS"],             // 화: 거절 집중
        ["CANCEL_MIX"],               // 수: 취소 혼합
        ["PEAK_START"],               // 목: 피크 시작
        ["PEAK_POINTS"],              // 금: 포인트/외상
        ["PEAK_AUTO_SHARE"],          // 토: 마감 자동공유
      ],
    },
    // Week 2: 복잡도 증가
    {
      description: "복잡도 증가",
      sharedCalls: [2, 3, 5, 8, 18, 20, 15],
      flags: [
        ["CARRYOVER_SETTLE"],         // 일: 이월 정리
        ["APP_CALL_HEAVY"],           // 월: 앱콜 40%
        ["TIMEOUT_FOCUS"],            // 화: 타임아웃
        ["POINTS_FOCUS"],             // 수: 포인트 집중
        ["CONTENTION_HEAVY"],         // 목: 공유콜 경합
        ["CHAOS"],                    // 금: 카오스
        ["SETTLEMENT_FOCUS"],         // 토: 정산 집중
      ],
    },
    // Week 3: 스트레스
    {
      description: "스트레스",
      sharedCalls: [3, 8, 10, 12, 25, 22, 20],
      flags: [
        ["CARRYOVER_SETTLE"],         // 일: 이월 정리
        ["DRIVER_ABSENT"],            // 월: 기사 결근
        ["HIGH_CANCEL_RATE"],         // 화: 취소율 15%
        ["CREDIT_FOCUS"],             // 수: 외상 집중
        ["MAX_PEAK"],                 // 목: 최대 피크
        ["CONTENTION_CANCEL"],        // 금: 경합+수임후취소
        ["SEQUENTIAL_CLOSE"],         // 토: 순차 마감
      ],
    },
    // Week 4: 종합 + 마감
    {
      description: "종합 마감",
      sharedCalls: [2, 5, 8, 15, 25, 18, 10],
      flags: [
        ["CARRYOVER_SETTLE"],         // 일: 이월 정리
        ["EDGE_CASES"],               // 월: 0원콜/최대요금
        ["CUSTOMER_POINTS_FOCUS"],    // 화: 고객 포인트 승급
        ["ALL_MIX"],                  // 수: 전 시나리오 혼합
        ["FINAL_PEAK"],               // 목: 최종 피크
        ["FINAL_SETTLEMENT"],         // 금: 최종 정산
        ["FINAL_CLOSE"],              // 토: 최종 마감+정합성
      ],
    },
  ];

  let dayNumber = 1;

  for (let week = 0; week < 4; week++) {
    const wd = weekDescriptions[week];
    for (let dow = 0; dow < 7; dow++) {
      schedule.push({
        week: week + 1,
        dayInWeek: dow,
        dayNumber,
        multiplier: DAY_MULTIPLIERS[dow],
        description: `W${week + 1} ${DAY_NAMES[dow]} - ${wd.description}`,
        sharedCallTarget: wd.sharedCalls[dow],
        specialFlags: wd.flags[dow],
      });
      dayNumber++;
    }
  }

  return schedule;
}

// 특정 주의 스케줄만 가져오기
export function getWeekSchedule(week: number): DayConfig[] {
  return generateSchedule().filter((d) => d.week === week);
}

// 워크데이트 생성 (시뮬레이션용, 2026-03-10 시작)
export function getSimWorkDate(dayNumber: number): string {
  const baseDate = new Date(2026, 2, 10); // 2026-03-10 (화)
  baseDate.setDate(baseDate.getDate() + dayNumber - 1);
  const y = baseDate.getFullYear();
  const m = String(baseDate.getMonth() + 1).padStart(2, "0");
  const d = String(baseDate.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}
