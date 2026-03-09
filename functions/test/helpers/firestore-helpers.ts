/**
 * Firestore 문서 CRUD 헬퍼
 * 각 앱의 동작을 시뮬레이션하는 함수들
 */

import { db, FieldValue, Timestamp } from "../setup/emulator-config";
import { Office, Driver, officePath, callPath, driverPath } from "../setup/constants";

// ==========================================
// Call Detector 역할: 콜 생성
// ==========================================

export interface CreateCallOptions {
  phoneNumber: string;
  customerName?: string;
  fare?: number;
  isAppCustomer?: boolean;
  fromCallDetector?: boolean;
  pointsUsed?: number;
}

export async function createCall(
  office: Office,
  callId: string,
  options: CreateCallOptions
): Promise<void> {
  const data: any = {
    phoneNumber: options.phoneNumber,
    customerName: options.customerName || "",
    customerAddress: "",
    status: "WAITING",
    timestamp: FieldValue.serverTimestamp(),
    detectedTimestamp: FieldValue.serverTimestamp(),
    provinceId: office.provinceId,
    cityId: office.cityId,
    officeId: office.officeId,
    deviceName: "테스트전화기",
    callType: "수신",
    timestampClient: Date.now(),
    fromCallDetector: options.fromCallDetector ?? true,
    isAppCustomer: options.isAppCustomer ?? false,
    customerId: options.isAppCustomer ? options.phoneNumber : "",
    createdFrom: options.isAppCustomer ? "app" : "phone",
  };

  if (options.pointsUsed) {
    data.pointsUsed = options.pointsUsed;
  }

  await db.doc(callPath(office, callId)).set(data);
}

// ==========================================
// Call Manager 역할: 배차, 취소
// ==========================================

export async function assignCall(
  office: Office,
  callId: string,
  driver: Driver
): Promise<void> {
  const batch = db.batch();

  // 콜 업데이트
  batch.update(db.doc(callPath(office, callId)), {
    assignedDriverId: driver.authUid,
    assignedDriverName: driver.name,
    assignedDriverPhone: driver.phoneNumber,
    status: "ASSIGNED",
    assignedTimestamp: Timestamp.now(),
    updatedAt: Timestamp.now(),
  });

  // 기사 상태 업데이트
  batch.update(db.doc(driverPath(office, driver.driverId)), {
    status: "ASSIGNED",
  });

  await batch.commit();
}

export async function cancelCallByManager(
  office: Office,
  callId: string
): Promise<void> {
  const callDoc = await db.doc(callPath(office, callId)).get();
  const callData = callDoc.data();

  const batch = db.batch();

  // 콜 상태 변경
  batch.update(db.doc(callPath(office, callId)), {
    status: "CANCELED",
    updatedAt: FieldValue.serverTimestamp(),
  });

  // 기사 상태 복구
  if (callData?.assignedDriverId) {
    const driversRef = db.collection(`${officePath(office)}/designated_drivers`);
    const driverQuery = await driversRef
      .where("authUid", "==", callData.assignedDriverId)
      .get();

    if (!driverQuery.empty) {
      batch.update(driverQuery.docs[0].ref, { status: "WAITING" });
    }
  }

  await batch.commit();
}

// ==========================================
// Driver App 역할: 수락, 거절, 운행, 완료, 정산
// ==========================================

export async function acceptCall(
  office: Office,
  callId: string,
  driver: Driver
): Promise<void> {
  const batch = db.batch();

  batch.update(db.doc(callPath(office, callId)), {
    status: "ACCEPTED",
    updatedAt: FieldValue.serverTimestamp(),
  });

  batch.update(db.doc(driverPath(office, driver.driverId)), {
    status: "PREPARING",
  });

  await batch.commit();
}

export async function rejectCall(
  office: Office,
  callId: string,
  driver: Driver
): Promise<void> {
  const batch = db.batch();

  batch.update(db.doc(callPath(office, callId)), {
    status: "WAITING",
    assignedDriverId: null,
    assignedDriverName: null,
    assignedDriverPhone: null,
    rejectedByDriver: driver.driverId,
    updatedAt: FieldValue.serverTimestamp(),
  });

  batch.update(db.doc(driverPath(office, driver.driverId)), {
    status: "WAITING",
  });

  await batch.commit();
}

export async function startDriving(
  office: Office,
  callId: string,
  options?: { departure?: string; destination?: string; fare?: number }
): Promise<void> {
  const updateData: any = {
    status: "IN_PROGRESS",
    updatedAt: FieldValue.serverTimestamp(),
  };

  if (options?.departure) updateData.departure_set = options.departure;
  if (options?.destination) updateData.destination_set = options.destination;
  if (options?.fare) updateData.fare_set = options.fare;

  await db.doc(callPath(office, callId)).update(updateData);
}

export async function completeCall(
  office: Office,
  callId: string
): Promise<void> {
  await db.doc(callPath(office, callId)).update({
    status: "AWAITING_SETTLEMENT",
    updatedAt: FieldValue.serverTimestamp(),
  });
}

export interface FinalizeOptions {
  fare: number;
  paymentMethod: string;
  cashReceived?: number;
  creditAmount?: number;
  pointsUsed?: number;
}

export async function finalizeTrip(
  office: Office,
  callId: string,
  driver: Driver,
  options: FinalizeOptions
): Promise<void> {
  const batch = db.batch();

  const callUpdate: any = {
    status: "COMPLETED",
    paymentMethod: options.paymentMethod,
    fare: options.fare,
    fare_final: options.fare,
    completedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    pointsUsed: options.pointsUsed || 0,
    finalFare: options.fare - (options.pointsUsed || 0),
  };

  if (options.cashReceived !== undefined) {
    callUpdate.cashReceived = options.cashReceived;
  }
  if (options.creditAmount !== undefined) {
    callUpdate.creditAmount = options.creditAmount;
  }

  batch.update(db.doc(callPath(office, callId)), callUpdate);

  batch.update(db.doc(driverPath(office, driver.driverId)), {
    status: "WAITING",
  });

  await batch.commit();
}

// ==========================================
// Customer App 역할: 고객 취소
// ==========================================

export async function cancelCallByCustomer(
  office: Office,
  callId: string
): Promise<void> {
  await db.doc(callPath(office, callId)).update({
    status: "CANCELLED_BY_CUSTOMER",
    updatedAt: FieldValue.serverTimestamp(),
  });
}

// ==========================================
// Driver App: 기사 취소
// ==========================================

export async function cancelCallByDriver(
  office: Office,
  callId: string
): Promise<void> {
  await db.doc(callPath(office, callId)).update({
    status: "CANCELLED_BY_DRIVER",
    updatedAt: FieldValue.serverTimestamp(),
  });
}

// ==========================================
// 공유콜
// ==========================================

export interface SharedCallOptions {
  phoneNumber: string;
  sourceOffice: Office;
  fare?: number;
  customerName?: string;
}

export async function createSharedCall(
  callId: string,
  options: SharedCallOptions
): Promise<void> {
  await db.doc(`shared_calls/${callId}`).set({
    phoneNumber: options.phoneNumber,
    sourceProvinceId: options.sourceOffice.provinceId,
    sourceCityId: options.sourceOffice.cityId,
    sourceOfficeId: options.sourceOffice.officeId,
    targetProvinceId: options.sourceOffice.provinceId,
    targetCityId: options.sourceOffice.cityId,
    deviceName: "테스트전화기",
    status: "OPEN",
    timestamp: FieldValue.serverTimestamp(),
    callType: "AFTER_HOURS",
    timestampClient: Date.now(),
    fromCallDetector: true,
    customerName: options.customerName || "",
    fare: options.fare || 20000,
  });
}

export async function claimSharedCall(
  callId: string,
  claimingOffice: Office,
  options: { fare: number; departure: string; destination: string; driverId?: string; driverAuthUid?: string }
): Promise<void> {
  await db.doc(`shared_calls/${callId}`).update({
    status: "CLAIMED",
    claimedOfficeId: claimingOffice.officeId,
    claimedAt: Timestamp.now(),
    departure: options.departure,
    destination: options.destination,
    fare: options.fare,
    targetProvinceId: claimingOffice.provinceId,
    targetCityId: claimingOffice.cityId,
    claimedDriverId: options.driverId || null,
    claimedDriverAuthUid: options.driverAuthUid || null,
  });
}

// ==========================================
// 문서 조회
// ==========================================

export async function getDoc(path: string): Promise<any | null> {
  const snap = await db.doc(path).get();
  return snap.exists ? snap.data() : null;
}

export async function getCollection(path: string): Promise<any[]> {
  const snap = await db.collection(path).get();
  return snap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}
