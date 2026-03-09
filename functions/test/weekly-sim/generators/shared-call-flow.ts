/**
 * 공유콜 생성/수임 플로우
 * 대형 사무실 적체 시 → 공유콜 생성 → 여유 있는 사무실이 수임
 */

import { OfficeConfig, getOfficesBySize } from "../config/offices";
import { DayConfig } from "../config/weekly-schedule";
import { SeededRandom } from "../config/scenario-ratios";
import {
  createSharedCall,
  claimSharedCall,
  executeNormalCall,
} from "./metered-helpers";
import { meterCallLifecycleCF, meterCallProcessed } from "../metering/meter";

export interface SharedCallResult {
  created: number;
  claimed: number;
  contentions: number;
  errors: string[];
}

/**
 * 일일 공유콜 플로우 실행
 * 1. 대형 사무실에서 공유콜 생성
 * 2. 중형/소형 사무실이 수임
 * 3. 수임 후 정상 플로우 실행
 */
export async function executeSharedCallFlow(
  dayConfig: DayConfig
): Promise<SharedCallResult> {
  const target = dayConfig.sharedCallTarget;
  if (target === 0) {
    return { created: 0, claimed: 0, contentions: 0, errors: [] };
  }

  const seed = dayConfig.dayNumber * 99999;
  const rng = new SeededRandom(seed);

  const largeOffices = getOfficesBySize("LARGE");
  const mediumOffices = getOfficesBySize("MEDIUM");
  const smallOffices = getOfficesBySize("SMALL");

  // 수임 후보 (중형 + 소형, 콜이 적은 사무실)
  const claimCandidates = [...mediumOffices, ...smallOffices];

  let created = 0;
  let claimed = 0;
  let contentions = 0;
  const errors: string[] = [];

  // 경합 대상 (공유콜 중 일부)
  const contentionCount = Math.floor(target * 0.2); // 약 20%가 경합
  const normalSharedCount = target - contentionCount;

  // ---- 일반 공유콜 (생성 → 수임 → 완료) ----
  for (let i = 0; i < normalSharedCount; i++) {
    const scId = `sc_d${dayConfig.dayNumber}_${String(i + 1).padStart(3, "0")}`;
    const sourceOffice = largeOffices[i % largeOffices.length];
    const claimOffice = claimCandidates[i % claimCandidates.length];
    const driver = claimOffice.drivers[rng.nextInt(0, claimOffice.drivers.length - 1)];
    const fare = rng.nextFare();

    try {
      // 1. 공유콜 생성 (대형 사무실 마감 후 자동생성 시뮬레이션)
      await createSharedCall(scId, {
        phoneNumber: `010-${String(rng.nextInt(1000, 9999))}-${String(rng.nextInt(1000, 9999))}`,
        sourceOffice: sourceOffice.office,
        fare,
        customerName: `공유고객_${scId}`,
      });
      created++;

      // 2. 수임 (여유 사무실)
      await claimSharedCall(scId, claimOffice.office, {
        fare,
        departure: `출발_${scId}`,
        destination: `도착_${scId}`,
        driverId: driver.driverId,
        driverAuthUid: driver.authUid,
      });
      claimed++;

      // 3. CF가 콜을 수임 사무실에 복사한 후, 정상 완료 플로우
      // (에뮬레이터에서 CF onSharedCallClaimed가 처리)
      // CF 대기 없이 직접 콜 생성 (CF가 하는 일을 시뮬레이션)
      const copiedCallId = `shared_${scId}`;
      await executeNormalCall(claimOffice.office, copiedCallId, driver, {
        phoneNumber: `010-${String(rng.nextInt(1000, 9999))}-${String(rng.nextInt(1000, 9999))}`,
        customerName: `공유고객_${scId}`,
        fare,
        paymentMethod: "현금",
        cashReceived: fare,
      });

      meterCallLifecycleCF("SHARED_CALL");

    } catch (error: any) {
      errors.push(`${scId}: ${error.message}`);
    }
  }

  // ---- 경합 공유콜 (2 사무실 동시 수임 시도) ----
  for (let i = 0; i < contentionCount; i++) {
    const scId = `sc_ct_d${dayConfig.dayNumber}_${String(i + 1).padStart(3, "0")}`;
    const sourceOffice = largeOffices[i % largeOffices.length];

    // 경합 후보 2개 사무실
    const contender1 = claimCandidates[i % claimCandidates.length];
    const contender2 = claimCandidates[(i + 1) % claimCandidates.length];
    const driver1 = contender1.drivers[rng.nextInt(0, contender1.drivers.length - 1)];
    const driver2 = contender2.drivers[rng.nextInt(0, contender2.drivers.length - 1)];
    const fare = rng.nextFare();

    try {
      // 1. 공유콜 생성
      await createSharedCall(scId, {
        phoneNumber: `010-${String(rng.nextInt(1000, 9999))}-${String(rng.nextInt(1000, 9999))}`,
        sourceOffice: sourceOffice.office,
        fare,
        customerName: `경합고객_${scId}`,
      });
      created++;

      // 2. 첫 번째 사무실 수임 (성공)
      await claimSharedCall(scId, contender1.office, {
        fare,
        departure: `출발_${scId}`,
        destination: `도착_${scId}`,
        driverId: driver1.driverId,
        driverAuthUid: driver1.authUid,
      });
      claimed++;
      contentions++;

      // 두 번째 사무실 수임 시도 (실패 - 이미 CLAIMED)
      // 실제로는 트랜잭션이 실패하지만, 미터링을 위해 시도 카운팅
      meterCallLifecycleCF("SHARED_CONTENTION");

      // 3. 승자 사무실에서 콜 완료
      const copiedCallId = `shared_${scId}`;
      await executeNormalCall(contender1.office, copiedCallId, driver1, {
        phoneNumber: `010-${String(rng.nextInt(1000, 9999))}-${String(rng.nextInt(1000, 9999))}`,
        customerName: `경합고객_${scId}`,
        fare,
        paymentMethod: "현금",
        cashReceived: fare,
      });

    } catch (error: any) {
      errors.push(`${scId}: ${error.message}`);
    }
  }

  return { created, claimed, contentions, errors };
}
