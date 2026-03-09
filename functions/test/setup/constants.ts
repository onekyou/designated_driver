/**
 * 테스트용 상수 정의
 * 10개 사무실 + 사무실당 기사 5명 + 고객 데이터
 */

export interface Office {
  provinceId: string;
  cityId: string;
  officeId: string;
  name: string;
  depositRatio: number;
}

export interface Driver {
  driverId: string;
  authUid: string;
  name: string;
  phoneNumber: string;
  officeIndex: number;
}

// 10개 사무실 (같은 province/city 내 다중 사무실)
export const OFFICES: Office[] = Array.from({ length: 10 }, (_, i) => ({
  provinceId: "test_province",
  cityId: "test_city",
  officeId: `office_${String(i + 1).padStart(2, "0")}`,
  name: `테스트사무실_${String(i + 1).padStart(2, "0")}`,
  depositRatio: 60,
}));

// 사무실당 기사 5명 (총 50명)
export const DRIVERS: Driver[] = [];
for (let oi = 0; oi < 10; oi++) {
  for (let di = 0; di < 5; di++) {
    const officeIdx = String(oi + 1).padStart(2, "0");
    const driverIdx = String(di + 1).padStart(2, "0");
    DRIVERS.push({
      driverId: `driver_${officeIdx}_${driverIdx}`,
      authUid: `auth_driver_${officeIdx}_${driverIdx}`,
      name: `기사${officeIdx}-${driverIdx}`,
      phoneNumber: `010-${officeIdx}00-${driverIdx}000`,
      officeIndex: oi,
    });
  }
}

// 사무실별 기사 조회 헬퍼
export function getDriversForOffice(officeIndex: number): Driver[] {
  return DRIVERS.filter((d) => d.officeIndex === officeIndex);
}

// 테스트용 고객 전화번호
export function customerPhone(seq: number): string {
  return `010-9999-${String(seq).padStart(4, "0")}`;
}

// 전화번호 정규화 (하이픈 제거)
export function normalizePhone(phone: string): string {
  return phone.replace(/-/g, "");
}

// Firestore 경로 빌더
export function officePath(office: Office): string {
  return `provinces/${office.provinceId}/cities/${office.cityId}/offices/${office.officeId}`;
}

export function callPath(office: Office, callId: string): string {
  return `${officePath(office)}/calls/${callId}`;
}

export function driverPath(office: Office, driverId: string): string {
  return `${officePath(office)}/designated_drivers/${driverId}`;
}

export function settlementPath(office: Office, workDate: string): string {
  return `${officePath(office)}/settlementSessions/${workDate}`;
}

export function customerPointsPath(office: Office, phone: string): string {
  return `${officePath(office)}/customerPoints/${normalizePhone(phone)}`;
}

export function pointBalancePath(office: Office): string {
  return `${officePath(office)}/points/points`;
}

// 오늘 근무일 (KST 기준, 새벽 6시 이전은 전날)
export function getTodayWorkDate(): string {
  const now = new Date();
  const kstOffset = 9 * 60 * 60 * 1000;
  const kstTime = new Date(now.getTime() + kstOffset);

  if (kstTime.getUTCHours() < 6) {
    kstTime.setUTCDate(kstTime.getUTCDate() - 1);
  }

  const year = kstTime.getUTCFullYear();
  const month = String(kstTime.getUTCMonth() + 1).padStart(2, "0");
  const day = String(kstTime.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
