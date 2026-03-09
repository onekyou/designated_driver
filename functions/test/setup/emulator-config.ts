/**
 * Firebase Emulator 연결 설정
 * 테스트 스크립트에서 import하여 사용
 */

// 에뮬레이터 환경변수 설정 (admin.initializeApp 전에 반드시 실행)
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";

import * as admin from "firebase-admin";

// 이미 초기화된 경우 재사용
if (!admin.apps.length) {
  admin.initializeApp({ projectId: "calldetector-5d61e" });
}

export const db = admin.firestore();
export const FieldValue = admin.firestore.FieldValue;
export const Timestamp = admin.firestore.Timestamp;

/**
 * 에뮬레이터 Firestore 데이터 전체 삭제
 */
export async function clearEmulatorData(): Promise<void> {
  const http = await import("http");

  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: "127.0.0.1",
        port: 8080,
        path: "/emulator/v1/projects/calldetector-5d61e/databases/(default)/documents",
        method: "DELETE",
      },
      (res) => {
        if (res.statusCode === 200) {
          resolve();
        } else {
          reject(new Error(`Failed to clear emulator data: ${res.statusCode}`));
        }
      }
    );
    req.on("error", reject);
    req.end();
  });
}

/**
 * 테스트 시작 시 호출: 데이터 초기화
 */
export async function initTest(testName: string): Promise<void> {
  console.log(`\n${"=".repeat(60)}`);
  console.log(`  ${testName}`);
  console.log(`${"=".repeat(60)}`);
}

/**
 * 테스트 결과 출력
 */
export function printResult(
  testName: string,
  passed: boolean,
  duration: number,
  details?: string
): void {
  const status = passed ? "PASS ✓" : "FAIL ✗";
  console.log(`  ${testName}: ${status} (${(duration / 1000).toFixed(1)}s)`);
  if (details) {
    console.log(`    → ${details}`);
  }
}
