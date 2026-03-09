/**
 * 일일 콜 생성기
 * 사무실별 콜 수 + 시나리오 비율에 따라 콜을 생성하고 실행
 */

import { OfficeConfig, getDailyCallCount } from "../config/offices";
import { DayConfig } from "../config/weekly-schedule";
import {
  SeededRandom,
  CallScenario,
  getScenarioRatios,
  PAYMENT_RATIOS,
  PaymentMethod,
} from "../config/scenario-ratios";
import {
  executeNormalCall,
  executeRejectReassignCall,
  executeCancelCall,
} from "./metered-helpers";
import { Driver } from "../../setup/constants";

// ==========================================
// 콜 생성 계획
// ==========================================

export interface PlannedCall {
  callId: string;
  officeConfig: OfficeConfig;
  scenario: CallScenario;
  paymentMethod: PaymentMethod;
  fare: number;
  driverIndex: number;      // 메인 기사 인덱스
  altDriverIndex?: number;  // 재배차용 기사 인덱스
  phoneNumber: string;
  customerName: string;
  isAppCustomer: boolean;
  pointsUsed: number;
}

/**
 * 하루의 콜 계획을 생성 (결정론적)
 */
export function planDayCalls(
  dayConfig: DayConfig,
  officeConfigs: OfficeConfig[]
): PlannedCall[] {
  const seed = dayConfig.dayNumber * 10000 + dayConfig.week * 100;
  const rng = new SeededRandom(seed);
  const ratios = getScenarioRatios(dayConfig.week, dayConfig.dayInWeek);

  const allCalls: PlannedCall[] = [];
  let globalCallSeq = 0;

  for (const oc of officeConfigs) {
    const callCount = getDailyCallCount(oc, dayConfig.multiplier);
    const officeNum = String(oc.officeIndex + 1).padStart(2, "0");

    // 기사 라운드로빈 인덱스
    let driverRR = 0;

    for (let ci = 0; ci < callCount; ci++) {
      globalCallSeq++;
      const callSeq = String(ci + 1).padStart(3, "0");
      const callId = `d${dayConfig.dayNumber}_o${officeNum}_${callSeq}`;

      // 시나리오 결정
      let scenario = rng.pickByRatio(ratios) as CallScenario;

      // 공유콜은 대형 사무실에서만 생성 (소형은 수임만)
      if ((scenario === "SHARED_CALL" || scenario === "SHARED_CONTENTION") && oc.size === "SMALL") {
        scenario = "NORMAL";
      }

      // 결제수단 결정
      const paymentMethod = rng.pickByRatio(PAYMENT_RATIOS) as PaymentMethod;

      // 요금 결정
      const fare = rng.nextFare();

      // 기사 배정 (라운드로빈)
      const driverIndex = driverRR % oc.drivers.length;
      driverRR++;

      // 재배차용 다른 기사
      const altDriverIndex = (driverIndex + 1) % oc.drivers.length;

      // 앱콜 여부 (약 30% 앱콜)
      const isAppCustomer = rng.next() < 0.3;

      // 포인트 사용 (포인트/현금+포인트 결제 시)
      let pointsUsed = 0;
      if (paymentMethod === "포인트") {
        pointsUsed = fare;
      } else if (paymentMethod === "현금+포인트") {
        pointsUsed = rng.nextInt(1000, Math.floor(fare * 0.5));
        pointsUsed = Math.floor(pointsUsed / 1000) * 1000; // 1000원 단위
      }

      // 전화번호 생성
      const phoneNumber = `010-${officeNum}${String(rng.nextInt(0, 99)).padStart(2, "0")}-${String(globalCallSeq).padStart(4, "0")}`;

      allCalls.push({
        callId,
        officeConfig: oc,
        scenario,
        paymentMethod,
        fare,
        driverIndex,
        altDriverIndex,
        phoneNumber,
        customerName: `고객_${callId}`,
        isAppCustomer,
        pointsUsed,
      });
    }
  }

  return allCalls;
}

// ==========================================
// 결제 파라미터 계산
// ==========================================

function getPaymentParams(method: PaymentMethod, fare: number, pointsUsed: number) {
  switch (method) {
    case "현금":
      return { cashReceived: fare, creditAmount: 0 };
    case "카드":
      return { cashReceived: 0, creditAmount: 0 };
    case "이체":
      return { cashReceived: 0, creditAmount: 0 };
    case "외상":
      return { cashReceived: 0, creditAmount: fare };
    case "포인트":
      return { cashReceived: 0, creditAmount: 0 };
    case "현금+포인트":
      return { cashReceived: fare - pointsUsed, creditAmount: 0 };
    default:
      return { cashReceived: fare, creditAmount: 0 };
  }
}

// ==========================================
// 콜 실행
// ==========================================

/**
 * 계획된 콜 1건을 실행
 */
export async function executeCall(call: PlannedCall): Promise<{ success: boolean; error?: string }> {
  const { officeConfig, scenario, callId, driverIndex, altDriverIndex } = call;
  const office = officeConfig.office;
  const driver = officeConfig.drivers[driverIndex];
  const altDriver = altDriverIndex !== undefined
    ? officeConfig.drivers[altDriverIndex]
    : officeConfig.drivers[(driverIndex + 1) % officeConfig.drivers.length];

  const payment = getPaymentParams(call.paymentMethod, call.fare, call.pointsUsed);

  try {
    switch (scenario) {
      case "NORMAL":
        await executeNormalCall(office, callId, driver, {
          phoneNumber: call.phoneNumber,
          customerName: call.customerName,
          fare: call.fare,
          paymentMethod: call.paymentMethod,
          cashReceived: payment.cashReceived,
          creditAmount: payment.creditAmount,
          pointsUsed: call.pointsUsed,
          isAppCustomer: call.isAppCustomer,
        });
        break;

      case "REJECT_REASSIGN":
        await executeRejectReassignCall(office, callId, driver, altDriver, {
          phoneNumber: call.phoneNumber,
          fare: call.fare,
          paymentMethod: call.paymentMethod,
          cashReceived: payment.cashReceived,
        });
        break;

      case "CANCEL_MANAGER":
        await executeCancelCall(office, callId, driver, "MANAGER", {
          phoneNumber: call.phoneNumber,
          fare: call.fare,
        });
        break;

      case "CANCEL_DRIVER":
        await executeCancelCall(office, callId, driver, "DRIVER", {
          phoneNumber: call.phoneNumber,
          fare: call.fare,
        });
        break;

      case "CANCEL_CUSTOMER":
        await executeCancelCall(office, callId, driver, "CUSTOMER", {
          phoneNumber: call.phoneNumber,
          fare: call.fare,
          pointsUsed: call.pointsUsed,
        });
        break;

      case "SHARED_CALL":
      case "SHARED_CONTENTION":
        // 공유콜은 shared-call-flow.ts에서 별도 처리
        // 여기서는 일반 콜로 처리 (공유콜 생성기가 나중에 덮어씀)
        await executeNormalCall(office, callId, driver, {
          phoneNumber: call.phoneNumber,
          customerName: call.customerName,
          fare: call.fare,
          paymentMethod: call.paymentMethod,
          cashReceived: payment.cashReceived,
          creditAmount: payment.creditAmount,
          isAppCustomer: call.isAppCustomer,
        });
        break;

      case "ASSIGNED_TIMEOUT":
        // 타임아웃: 배차만 하고 수락 안 함 → 재배차 후 정상 완료
        // (에뮬레이터에서 checkAssignedTimeout CF는 3분 후 발동하므로, 여기서는 수동 재현)
        await executeRejectReassignCall(office, callId, driver, altDriver, {
          phoneNumber: call.phoneNumber,
          fare: call.fare,
          paymentMethod: call.paymentMethod,
          cashReceived: payment.cashReceived,
        });
        break;

      default:
        await executeNormalCall(office, callId, driver, {
          phoneNumber: call.phoneNumber,
          fare: call.fare,
          paymentMethod: call.paymentMethod,
          cashReceived: payment.cashReceived,
          isAppCustomer: call.isAppCustomer,
        });
    }

    return { success: true };
  } catch (error: any) {
    return { success: false, error: error.message };
  }
}

/**
 * 하루의 전체 콜 실행
 */
export async function executeDayCalls(
  calls: PlannedCall[],
  options?: { batchSize?: number; logInterval?: number }
): Promise<{ total: number; success: number; failed: number; errors: string[] }> {
  const batchSize = options?.batchSize ?? 10;
  const logInterval = options?.logInterval ?? 50;

  let success = 0;
  let failed = 0;
  const errors: string[] = [];

  for (let i = 0; i < calls.length; i += batchSize) {
    const batch = calls.slice(i, i + batchSize);

    // 배치 내 콜을 순차 실행 (CF 트리거 처리 시간 확보)
    for (const call of batch) {
      const result = await executeCall(call);
      if (result.success) {
        success++;
      } else {
        failed++;
        errors.push(`${call.callId}: ${result.error}`);
      }

      // CF 트리거 분산: 콜당 100ms 대기 (각 콜이 6+ CF를 트리거하므로 에뮬레이터 부하 분산)
      await new Promise((resolve) => setTimeout(resolve, 100));
    }

    // 진행 로그
    const processed = Math.min(i + batchSize, calls.length);
    if (processed % logInterval === 0 || processed === calls.length) {
      console.log(`    진행: ${processed}/${calls.length} (성공: ${success}, 실패: ${failed})`);
    }
  }

  return { total: calls.length, success, failed, errors };
}
