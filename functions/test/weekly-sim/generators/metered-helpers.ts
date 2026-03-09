/**
 * 미터링이 적용된 Firestore 헬퍼 래퍼
 * 기존 firestore-helpers.ts를 감싸서 모든 연산을 카운팅
 */

import { Office, Driver } from "../../setup/constants";
import * as helpers from "../../helpers/firestore-helpers";
import {
  meterWrite,
  meterRead,
  meterBatchWrite,
  meterCallProcessed,
  meterSharedCallCreated,
  meterSharedCallClaimed,
  meterCallLifecycleCF,
} from "../metering/meter";

// ==========================================
// 미터링 래퍼: 콜 생성
// ==========================================

export async function createCall(
  office: Office,
  callId: string,
  options: helpers.CreateCallOptions
): Promise<void> {
  await helpers.createCall(office, callId, options);
  meterWrite(1); // doc.set
}

// ==========================================
// 미터링 래퍼: 배차
// ==========================================

export async function assignCall(
  office: Office,
  callId: string,
  driver: Driver
): Promise<void> {
  await helpers.assignCall(office, callId, driver);
  meterBatchWrite(2); // 콜 + 기사
}

// ==========================================
// 미터링 래퍼: 수락
// ==========================================

export async function acceptCall(
  office: Office,
  callId: string,
  driver: Driver
): Promise<void> {
  await helpers.acceptCall(office, callId, driver);
  meterBatchWrite(2);
}

// ==========================================
// 미터링 래퍼: 거절
// ==========================================

export async function rejectCall(
  office: Office,
  callId: string,
  driver: Driver
): Promise<void> {
  await helpers.rejectCall(office, callId, driver);
  meterBatchWrite(2);
}

// ==========================================
// 미터링 래퍼: 운행 시작
// ==========================================

export async function startDriving(
  office: Office,
  callId: string,
  options?: { departure?: string; destination?: string; fare?: number }
): Promise<void> {
  await helpers.startDriving(office, callId, options);
  meterWrite(1);
}

// ==========================================
// 미터링 래퍼: 운행 완료
// ==========================================

export async function completeCall(
  office: Office,
  callId: string
): Promise<void> {
  await helpers.completeCall(office, callId);
  meterWrite(1);
}

// ==========================================
// 미터링 래퍼: 정산 확정
// ==========================================

export async function finalizeTrip(
  office: Office,
  callId: string,
  driver: Driver,
  options: helpers.FinalizeOptions
): Promise<void> {
  await helpers.finalizeTrip(office, callId, driver, options);
  meterBatchWrite(2); // 콜 + 기사
}

// ==========================================
// 미터링 래퍼: 취소
// ==========================================

export async function cancelCallByManager(
  office: Office,
  callId: string
): Promise<void> {
  await helpers.cancelCallByManager(office, callId);
  meterRead(1);       // 기사 조회
  meterBatchWrite(2); // 콜 + 기사
}

export async function cancelCallByDriver(
  office: Office,
  callId: string
): Promise<void> {
  await helpers.cancelCallByDriver(office, callId);
  meterWrite(1);
}

export async function cancelCallByCustomer(
  office: Office,
  callId: string
): Promise<void> {
  await helpers.cancelCallByCustomer(office, callId);
  meterWrite(1);
}

// ==========================================
// 미터링 래퍼: 공유콜
// ==========================================

export async function createSharedCall(
  callId: string,
  options: helpers.SharedCallOptions
): Promise<void> {
  await helpers.createSharedCall(callId, options);
  meterWrite(1);
  meterSharedCallCreated();
}

export async function claimSharedCall(
  callId: string,
  claimingOffice: Office,
  options: { fare: number; departure: string; destination: string; driverId?: string; driverAuthUid?: string }
): Promise<void> {
  await helpers.claimSharedCall(callId, claimingOffice, options);
  meterWrite(1);
  meterRead(1); // 트랜잭션 읽기
  meterSharedCallClaimed();
}

// ==========================================
// 미터링 래퍼: 문서 조회
// ==========================================

export async function getDoc(path: string): Promise<any | null> {
  const result = await helpers.getDoc(path);
  meterRead(1);
  return result;
}

export async function getCollection(path: string): Promise<any[]> {
  const result = await helpers.getCollection(path);
  meterRead(result.length || 1);
  return result;
}

// ==========================================
// 복합 플로우: 콜 정상 완료 (전체 라이프사이클)
// ==========================================

export async function executeNormalCall(
  office: Office,
  callId: string,
  driver: Driver,
  options: {
    phoneNumber: string;
    customerName?: string;
    fare: number;
    paymentMethod: string;
    cashReceived?: number;
    creditAmount?: number;
    pointsUsed?: number;
    isAppCustomer?: boolean;
  }
): Promise<void> {
  await createCall(office, callId, {
    phoneNumber: options.phoneNumber,
    customerName: options.customerName || "",
    fare: options.fare,
    isAppCustomer: options.isAppCustomer ?? false,
    pointsUsed: options.pointsUsed,
  });

  await assignCall(office, callId, driver);
  await acceptCall(office, callId, driver);
  await startDriving(office, callId, { fare: options.fare });
  await completeCall(office, callId);
  await finalizeTrip(office, callId, driver, {
    fare: options.fare,
    paymentMethod: options.paymentMethod,
    cashReceived: options.cashReceived ?? 0,
    creditAmount: options.creditAmount ?? 0,
    pointsUsed: options.pointsUsed ?? 0,
  });

  meterCallProcessed();
  meterCallLifecycleCF("NORMAL");
}

// ==========================================
// 복합 플로우: 거절 → 재배차 → 완료
// ==========================================

export async function executeRejectReassignCall(
  office: Office,
  callId: string,
  rejectDriver: Driver,
  assignDriver: Driver,
  options: {
    phoneNumber: string;
    fare: number;
    paymentMethod: string;
    cashReceived?: number;
  }
): Promise<void> {
  await createCall(office, callId, {
    phoneNumber: options.phoneNumber,
    fare: options.fare,
  });

  // 1차 배차 → 거절
  await assignCall(office, callId, rejectDriver);
  await rejectCall(office, callId, rejectDriver);

  // 재배차 → 완료
  await assignCall(office, callId, assignDriver);
  await acceptCall(office, callId, assignDriver);
  await startDriving(office, callId, { fare: options.fare });
  await completeCall(office, callId);
  await finalizeTrip(office, callId, assignDriver, {
    fare: options.fare,
    paymentMethod: options.paymentMethod,
    cashReceived: options.cashReceived ?? options.fare,
  });

  meterCallProcessed();
  meterCallLifecycleCF("REJECT_REASSIGN");
}

// ==========================================
// 복합 플로우: 취소 (관리자/기사/고객)
// ==========================================

export async function executeCancelCall(
  office: Office,
  callId: string,
  driver: Driver,
  cancelType: "MANAGER" | "DRIVER" | "CUSTOMER",
  options: {
    phoneNumber: string;
    fare: number;
    pointsUsed?: number;
  }
): Promise<void> {
  await createCall(office, callId, {
    phoneNumber: options.phoneNumber,
    fare: options.fare,
    isAppCustomer: cancelType === "CUSTOMER",
    pointsUsed: options.pointsUsed,
  });

  await assignCall(office, callId, driver);

  if (cancelType === "DRIVER") {
    await acceptCall(office, callId, driver);
    await cancelCallByDriver(office, callId);
  } else if (cancelType === "MANAGER") {
    await cancelCallByManager(office, callId);
  } else {
    await cancelCallByCustomer(office, callId);
  }

  meterCallProcessed();
  meterCallLifecycleCF(`CANCEL_${cancelType}`);
}
