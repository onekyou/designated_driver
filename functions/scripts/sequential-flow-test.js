/**
 * 순차적 흐름 테스트: 콜 생성 → 배차 → 운행 → 완료 → 정산 마감 → 확인 → 이체 → 수령
 *
 * test-day1-7.js 인프라 기반 + 정산 마감 흐름 추가
 *
 * Usage:
 *   node scripts/sequential-flow-test.js [day]     Day 1~7 실행
 *   node scripts/sequential-flow-test.js --cleanup  테스트 데이터 삭제
 *   node scripts/sequential-flow-test.js --status   현재 상태 확인
 */

const path = require("path");
const os = require("os");
const https = require("https");

// ─── Config ───
const PROJECT_ID = "calldetector-5d61e";
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

const OFFICE_PATH = "provinces/gyeonggi/cities/yangpyeong/offices/nEkf0X9g3LZtRX94Mrzu";

// 기사 정보 (실제 기사 2명)
const DRIVERS = {
  A: { id: "6RQEWvmDkkfTAHXjvbxPtYfa7mY2", name: "양세훈" },
  B: { id: "vIbRH7Ci17UqCUR6ty84eDdl2me2", name: "고양이" },
};
const DRIVER_KEYS = ["A", "B"];

// 관리자 ID (정산 확인자)
const ADMIN_ID = "admin-test-script";

// ─── HTTP / Auth ───
let cachedToken = null;
let tokenExpiry = 0;

function httpRequest(url, options = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      let data = "";
      res.on("data", (chunk) => data += chunk);
      res.on("end", () => {
        try { resolve(JSON.parse(data)); }
        catch { resolve(data); }
      });
    });
    req.on("error", reject);
    if (options.body) req.write(options.body);
    req.end();
  });
}

function getRefreshToken() {
  const configPath = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  return require(configPath).tokens.refresh_token;
}

async function getAccessToken() {
  if (cachedToken && Date.now() < tokenExpiry) return cachedToken;
  const body = `client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&refresh_token=${getRefreshToken()}&grant_type=refresh_token`;
  const result = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  if (!result.access_token) throw new Error("Token failed: " + JSON.stringify(result));
  cachedToken = result.access_token;
  tokenExpiry = Date.now() + 50 * 60 * 1000;
  return cachedToken;
}

// ─── Firestore REST Helpers ───

function toFirestoreValue(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === "string") return { stringValue: val };
  if (typeof val === "number") {
    if (Number.isInteger(val)) return { integerValue: String(val) };
    return { doubleValue: val };
  }
  if (typeof val === "boolean") return { booleanValue: val };
  if (val instanceof Date) return { timestampValue: val.toISOString() };
  if (Array.isArray(val)) return { arrayValue: { values: val.map(toFirestoreValue) } };
  if (typeof val === "object") {
    const fields = {};
    for (const [k, v] of Object.entries(val)) {
      fields[k] = toFirestoreValue(v);
    }
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

function fromFirestoreValue(val) {
  if (val.stringValue !== undefined) return val.stringValue;
  if (val.integerValue !== undefined) return Number(val.integerValue);
  if (val.doubleValue !== undefined) return val.doubleValue;
  if (val.booleanValue !== undefined) return val.booleanValue;
  if (val.timestampValue !== undefined) return val.timestampValue;
  if (val.nullValue !== undefined) return null;
  if (val.mapValue) {
    const obj = {};
    for (const [k, v] of Object.entries(val.mapValue.fields || {})) {
      obj[k] = fromFirestoreValue(v);
    }
    return obj;
  }
  if (val.arrayValue) return (val.arrayValue.values || []).map(fromFirestoreValue);
  return val;
}

function parseDoc(doc) {
  const id = doc.name.split("/").pop();
  const fields = {};
  for (const [k, v] of Object.entries(doc.fields || {})) {
    fields[k] = fromFirestoreValue(v);
  }
  return { id, ...fields };
}

async function createDocument(collectionPath, docId, data) {
  const token = await getAccessToken();
  const fields = {};
  for (const [k, v] of Object.entries(data)) {
    fields[k] = toFirestoreValue(v);
  }
  const url = docId
    ? `${BASE_URL}/${collectionPath}?documentId=${docId}`
    : `${BASE_URL}/${collectionPath}`;
  const body = JSON.stringify({ fields });
  const result = await httpRequest(url, {
    method: "POST",
    headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
    body
  });
  if (result.error) throw new Error(`Create failed: ${result.error.message}`);
  return parseDoc(result);
}

// dot-notation 키를 중첩 Firestore fields 구조로 변환
function buildNestedFields(data) {
  const result = {};
  for (const [key, value] of Object.entries(data)) {
    const parts = key.split(".");
    if (parts.length === 1) {
      result[key] = toFirestoreValue(value);
    } else {
      // "a.b.c" → { a: { mapValue: { fields: { b: { mapValue: { fields: { c: value } } } } } } }
      let current = result;
      for (let i = 0; i < parts.length - 1; i++) {
        if (!current[parts[i]]) {
          current[parts[i]] = { mapValue: { fields: {} } };
        }
        current = current[parts[i]].mapValue.fields;
      }
      current[parts[parts.length - 1]] = toFirestoreValue(value);
    }
  }
  return result;
}

async function updateDocument(docPath, data) {
  const token = await getAccessToken();
  const fields = buildNestedFields(data);
  const updateMask = Object.keys(data).map(k => `updateMask.fieldPaths=${k}`).join("&");
  const url = `${BASE_URL}/${docPath}?${updateMask}`;
  const body = JSON.stringify({ fields });
  const result = await httpRequest(url, {
    method: "PATCH",
    headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
    body
  });
  if (result.error) throw new Error(`Update failed (${docPath}): ${result.error.message}`);
  return parseDoc(result);
}

async function getDocument(docPath) {
  const token = await getAccessToken();
  const result = await httpRequest(`${BASE_URL}/${docPath}`, {
    headers: { "Authorization": `Bearer ${token}` }
  });
  if (result.error) return null;
  return parseDoc(result);
}

async function listDocuments(collectionPath, pageSize = 300) {
  const token = await getAccessToken();
  const allDocs = [];
  let pageToken = "";
  do {
    const url = `${BASE_URL}/${collectionPath}?pageSize=${pageSize}${pageToken ? `&pageToken=${pageToken}` : ""}`;
    const result = await httpRequest(url, {
      headers: { "Authorization": `Bearer ${token}` }
    });
    if (result.error) throw new Error(`List failed: ${result.error.message}`);
    const docs = (result.documents || []).map(parseDoc);
    allDocs.push(...docs);
    pageToken = result.nextPageToken || "";
  } while (pageToken);
  return allDocs;
}

async function deleteDocument(docPath) {
  const token = await getAccessToken();
  return await httpRequest(`${BASE_URL}/${docPath}`, {
    method: "DELETE",
    headers: { "Authorization": `Bearer ${token}` }
  });
}

// ─── Test Helpers ───
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

let passCount = 0;
let failCount = 0;

function assert(condition, message) {
  if (condition) {
    passCount++;
    console.log(`  ✓ ${message}`);
  } else {
    failCount++;
    console.log(`  ✗ FAIL: ${message}`);
  }
}

function assertEqual(actual, expected, message) {
  if (actual === expected) {
    passCount++;
    console.log(`  ✓ ${message}`);
  } else {
    failCount++;
    console.log(`  ✗ FAIL: ${message} (expected=${expected}, actual=${actual})`);
  }
}

const TEST_PREFIX = "seq_";
let callCounter = 0;

function nextCallId(dayNum) {
  callCounter++;
  return `${TEST_PREFIX}d${dayNum}_${String(callCounter).padStart(3, "0")}`;
}

// 2026-04-01 ~ 04-07 사용 (기존 test-day1-7.js의 03-20~26과 충돌 방지)
function dayBaseTime(dayNum) {
  const base = new Date("2026-04-01T09:00:00Z"); // 18:00 KST
  base.setDate(base.getDate() + (dayNum - 1));
  return base;
}

function dayDateString(dayNum) {
  const d = dayBaseTime(dayNum);
  return d.toISOString().split("T")[0]; // "2026-04-01" etc
}

// ─── 콜 생성 + 상태 전이 ───

async function createAndCompleteCall(dayNum, opts) {
  const {
    driverKey, fare, paymentMethod, cashReceived = 0,
    creditAmount = 0, pointsUsed = 0,
    customerPhone = "", customerName = "",
    departure = "테스트출발", destination = "테스트도착",
    fromCallManager = true, finalStatus = "COMPLETED",
  } = opts;

  const driver = DRIVERS[driverKey];
  const callId = nextCallId(dayNum);
  const baseTime = dayBaseTime(dayNum);
  const offset = callCounter * 60000;
  const timestamp = new Date(baseTime.getTime() + offset);
  const expireAt = new Date(timestamp.getTime() + 30 * 24 * 60 * 60 * 1000);

  // Step 1: WAITING (손님앱/콜디텍터 → 콜 생성)
  await createDocument(`${OFFICE_PATH}/calls`, callId, {
    status: "WAITING",
    phoneNumber: customerPhone, customerName, customerAddress: "",
    fare, fare_set: fare, pointsUsed,
    timestamp, timestampClient: timestamp.getTime(),
    fromCallManager, cityId: "yangpyeong", provinceId: "gyeonggi",
    officeId: "nEkf0X9g3LZtRX94Mrzu",
    departure_set: departure, destination_set: destination,
    waypoints_set: "", createdBy: "test-script", expireAt,
  });

  if (finalStatus === "WAITING") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // Step 2: ASSIGNED (콜매니저 → 배차)
  const assignedTime = new Date(timestamp.getTime() + 2000);
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "ASSIGNED",
    assignedDriverId: driver.id, assignedDriverName: driver.name,
    assignedDriverPhone: "01000000000",
    assignedTimestamp: assignedTime, updatedAt: assignedTime,
  });

  if (finalStatus === "ASSIGNED") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // Step 3: ACCEPTED (기사앱 → 수락)
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "ACCEPTED", updatedAt: new Date(timestamp.getTime() + 5000),
  });

  if (finalStatus === "ACCEPTED") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // Step 4: IN_PROGRESS (기사앱 → 운행 시작)
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "IN_PROGRESS", updatedAt: new Date(timestamp.getTime() + 30000),
  });

  if (finalStatus === "IN_PROGRESS") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // Step 5: AWAITING_SETTLEMENT (기사앱 → 운행 완료)
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "AWAITING_SETTLEMENT",
    updatedAt: new Date(timestamp.getTime() + 120000),
    finalFare: fare, fareFinal: fare,
    tripSummaryFinal: `출발: ${departure}, 도착: ${destination}, 요금: ${fare}원`,
    trip_summary: `출발: ${departure}, 도착: ${destination}, 요금: ${fare}원`,
  });

  if (finalStatus === "AWAITING_SETTLEMENT") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // Step 6: COMPLETED (기사앱 → 정산 입력, CF 트리거)
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "COMPLETED",
    completedAt: new Date(timestamp.getTime() + 180000),
    updatedAt: new Date(timestamp.getTime() + 180000),
    paymentMethod, cashReceived, creditAmount,
  });

  return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };
}

// ─── 정산 마감 흐름 (per-driver) ───

function driverDocPath(driverKey) {
  return `${OFFICE_PATH}/designated_drivers/${DRIVERS[driverKey].id}`;
}

/**
 * 기사별 정산 계산
 * @param {Array} calls - 해당 기사의 완료된 콜 목록
 * @param {number} depositRatio - 사무실 수수료율 (기본 60)
 * @param {number} originalCarryOver - 기존 이월금
 * @returns {object} 정산 데이터
 */
function calculateDriverSettlement(calls, depositRatio, originalCarryOver = 0) {
  const totalFare = calls.reduce((sum, c) => sum + c.fare, 0);
  const totalCredit = calls.reduce((sum, c) => sum + c.creditAmount, 0);
  const totalCash = calls.reduce((sum, c) => sum + c.cashReceived, 0);
  const totalPoints = calls.reduce((sum, c) => sum + c.pointsUsed, 0);

  const officeDeposit = Math.floor(totalFare * depositRatio / 100);
  const driverShare = totalFare - officeDeposit;
  const finalDeposit = officeDeposit - totalCredit;
  const realDeposit = totalCash - driverShare;
  const calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit;
  const settlementDiff = (realDeposit - finalDeposit) + originalCarryOver;

  return {
    totalFare, totalCredit, totalCash, totalPoints,
    officeDeposit, driverShare, finalDeposit, realDeposit,
    calculatedCarryOver, settlementDiff,
    tripCount: calls.length,
  };
}

/**
 * Step 7: 기사 마감 제출 (PENDING_CONFIRM)
 */
async function submitDailySettlement(driverKey, dayNum, calls, depositRatio, originalCarryOver = 0) {
  const calc = calculateDriverSettlement(calls, depositRatio, originalCarryOver);
  const now = new Date();

  await updateDocument(driverDocPath(driverKey), {
    dailySettlement: {
      date: dayDateString(dayNum),
      status: "PENDING_CONFIRM",
      finalDeposit: calc.finalDeposit,
      realDeposit: calc.realDeposit,
      settlementDiff: calc.settlementDiff,
      totalFare: calc.totalFare,
      totalCredit: calc.totalCredit,
      tripCount: calc.tripCount,
      submittedAt: now,
      confirmedAt: null,
      confirmedBy: null,
      calculatedCarryOver: calc.calculatedCarryOver,
      originalCarryOver: originalCarryOver,
      originalTripCount: 0,
      originalTotalFare: 0,
      originalRealDeposit: 0,
    }
  });

  return calc;
}

/**
 * Step 8: 매니저 확인 (CONFIRMED)
 */
async function confirmDailySettlement(driverKey, calculatedCarryOver) {
  const now = new Date();
  const newCarryOverStatus = calculatedCarryOver === 0 ? "SETTLED" : "PENDING";

  await updateDocument(driverDocPath(driverKey), {
    "dailySettlement.status": "CONFIRMED",
    "dailySettlement.confirmedAt": now,
    "dailySettlement.confirmedBy": ADMIN_ID,
    "carryOver.balance": calculatedCarryOver,
    "carryOver.status": newCarryOverStatus,
    "carryOver.lastUpdatedAt": now,
  });
}

/**
 * Step 9: 매니저 이체 (TRANSFERRED) — carryOver가 0이 아닌 경우만
 */
async function transferCarryOver(driverKey, balance) {
  const now = new Date();

  await updateDocument(driverDocPath(driverKey), {
    carryOver: {
      balance: balance,
      todayAmount: balance,
      status: "TRANSFERRED",
      transferredAt: now,
      transferredBy: ADMIN_ID,
      lastUpdatedAt: now,
    }
  });
}

/**
 * Step 10: 기사 수령 확인 (SETTLED, balance=0)
 */
async function confirmReceiveCarryOver(driverKey) {
  const now = new Date();

  await updateDocument(driverDocPath(driverKey), {
    "carryOver.balance": 0,
    "carryOver.status": "SETTLED",
    "carryOver.transferredAt": null,
    "carryOver.transferredBy": null,
    "carryOver.lastUpdatedAt": now,
  });
}

// ─── 기사 상태 초기화 (테스트 전) ───
async function resetDriverSettlement(driverKey) {
  await updateDocument(driverDocPath(driverKey), {
    dailySettlement: {
      date: "",
      status: "WORKING",
      finalDeposit: 0,
      realDeposit: 0,
      settlementDiff: 0,
      totalFare: 0,
      totalCredit: 0,
      tripCount: 0,
      submittedAt: null,
      confirmedAt: null,
      confirmedBy: null,
      calculatedCarryOver: 0,
      originalCarryOver: 0,
      originalTripCount: 0,
      originalTotalFare: 0,
      originalRealDeposit: 0,
    },
    "carryOver.balance": 0,
    "carryOver.status": "SETTLED",
    "carryOver.lastUpdatedAt": new Date(),
  });
}

// ═══════════════════════════════════════════════════════════
// DAY 1: 정상 플로우 (50콜) + 전체 정산 마감
// ═══════════════════════════════════════════════════════════
async function day1() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 1: 정상 플로우 50콜 + 정산 마감 전체 흐름");
  console.log("=".repeat(60));

  // 기사 상태 초기화
  console.log("\n[준비] 기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  const allCalls = []; // { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed }

  // ── Phase 1: 콜 생성 → COMPLETED ──

  // 1. 현금 15건
  console.log("\n[1/6] 현금 결제 15건...");
  for (let i = 0; i < 15; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 1000);
    const result = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
      customerPhone: `0101111${String(i).padStart(4, "0")}`,
    });
    allCalls.push(result);
  }

  // 2. 앱콜 현금 10건
  console.log("[2/6] 앱콜 현금 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 15000 + (i * 2000);
    const result = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "현금",
      cashReceived: fare, fromCallManager: false,
    });
    allCalls.push(result);
  }

  // 3. 이체 8건
  console.log("[3/6] 이체 결제 8건...");
  for (let i = 0; i < 8; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 25000 + (i * 3000);
    const result = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "이체", cashReceived: 0,
    });
    allCalls.push(result);
  }

  // 4. 현금+포인트 7건
  console.log("[4/6] 현금+포인트 혼합 7건...");
  for (let i = 0; i < 7; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 30000;
    const points = 5000;
    const cash = fare - points;
    const result = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "현금+포인트",
      cashReceived: cash, pointsUsed: points,
    });
    allCalls.push(result);
  }

  // 5. 전액 포인트 5건
  console.log("[5/6] 전액 포인트 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 10000;
    const result = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "포인트",
      cashReceived: 0, pointsUsed: fare,
    });
    allCalls.push(result);
  }

  // 6. 외상 5건
  console.log("[6/6] 외상 결제 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000;
    const result = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "외상",
      cashReceived: 0, creditAmount: fare,
    });
    allCalls.push(result);
  }

  console.log(`\n총 ${allCalls.length}건 COMPLETED. CF 트리거 대기 (15초)...`);
  await sleep(15000);

  // ── Phase 2: CF 트리거 검증 (정산 세션) ──
  console.log("\n--- Phase 2: 정산 세션 검증 ---");

  const workDate = dayDateString(1);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);

  if (!session) {
    console.log("  !! 정산 세션 없음 — Phase 2~4 건너뜀");
    return allCalls;
  }

  const sessionCalls = session.calls || [];
  assertEqual(sessionCalls.length, 50, `CP2: 정산 세션 콜 수 = 50 (actual: ${sessionCalls.length})`);

  const depositRatio = session.metadata?.depositRatio || 60;
  assertEqual(depositRatio, 60, `CP3: depositRatio = 60`);

  // 전체 요금 검증
  const expectedTotalFare = allCalls.reduce((s, c) => s + c.fare, 0);
  assertEqual(session.totals?.totalFare, expectedTotalFare, `CP4: totalFare = ${expectedTotalFare}`);

  const expectedDeposit = Math.floor(expectedTotalFare * depositRatio / 100);
  assertEqual(session.totals?.totalDeposit, expectedDeposit, `CP5: totalDeposit = ${expectedDeposit}`);

  const expectedDriverShare = expectedTotalFare - expectedDeposit;
  assertEqual(session.totals?.totalDriverShare, expectedDriverShare, `CP6: totalDriverShare = ${expectedDriverShare}`);

  // ── Phase 3: 기사별 마감 제출 + 검증 ──
  console.log("\n--- Phase 3: 기사 마감 제출 (PENDING_CONFIRM) ---");

  const driverCalcs = {};

  for (const key of DRIVER_KEYS) {
    const driverCalls = allCalls.filter(c => c.driverKey === key);
    console.log(`\n  [${DRIVERS[key].name}] ${driverCalls.length}건 마감 제출...`);

    const calc = await submitDailySettlement(key, 1, driverCalls, depositRatio, 0);
    driverCalcs[key] = calc;

    // 검증: driver 문서 읽기
    const driverDoc = await getDocument(driverDocPath(key));
    const ds = driverDoc?.dailySettlement;

    assertEqual(ds?.status, "PENDING_CONFIRM", `  CP: ${DRIVERS[key].name} status = PENDING_CONFIRM`);
    assertEqual(ds?.tripCount, driverCalls.length, `  CP: ${DRIVERS[key].name} tripCount = ${driverCalls.length}`);
    assertEqual(ds?.totalFare, calc.totalFare, `  CP: ${DRIVERS[key].name} totalFare = ${calc.totalFare}`);
    assertEqual(ds?.finalDeposit, calc.finalDeposit, `  CP: ${DRIVERS[key].name} finalDeposit = ${calc.finalDeposit}`);
    assertEqual(ds?.realDeposit, calc.realDeposit, `  CP: ${DRIVERS[key].name} realDeposit = ${calc.realDeposit}`);
    assertEqual(ds?.calculatedCarryOver, calc.calculatedCarryOver, `  CP: ${DRIVERS[key].name} carryOver = ${calc.calculatedCarryOver}`);

    console.log(`    총요금=${calc.totalFare}, 사무실몫=${calc.officeDeposit}, 기사몫=${calc.driverShare}`);
    console.log(`    현금수령=${calc.totalCash}, 실납입=${calc.realDeposit}, 이월=${calc.calculatedCarryOver}`);
  }

  // ── Phase 4: 매니저 확인 → 이체 → 수령 ──
  console.log("\n--- Phase 4: 매니저 확인 (CONFIRMED) ---");

  for (const key of DRIVER_KEYS) {
    const calc = driverCalcs[key];
    console.log(`\n  [${DRIVERS[key].name}] 매니저 확인...`);

    await confirmDailySettlement(key, calc.calculatedCarryOver);

    const driverDoc = await getDocument(driverDocPath(key));
    const ds = driverDoc?.dailySettlement;
    assertEqual(ds?.status, "CONFIRMED", `  CP: ${DRIVERS[key].name} status = CONFIRMED`);
    assert(ds?.confirmedAt !== null, `  CP: ${DRIVERS[key].name} confirmedAt 설정됨`);

    const co = driverDoc?.carryOver;
    assertEqual(co?.balance, calc.calculatedCarryOver, `  CP: ${DRIVERS[key].name} carryOver.balance = ${calc.calculatedCarryOver}`);

    if (calc.calculatedCarryOver === 0) {
      assertEqual(co?.status, "SETTLED", `  CP: ${DRIVERS[key].name} carryOver.status = SETTLED (이월 없음)`);
    } else {
      assertEqual(co?.status, "PENDING", `  CP: ${DRIVERS[key].name} carryOver.status = PENDING (이월 ${calc.calculatedCarryOver})`);
    }
  }

  // 이체 + 수령 (이월금이 있는 기사만)
  console.log("\n--- Phase 4b: 이체 (TRANSFERRED) → 수령 (SETTLED) ---");

  for (const key of DRIVER_KEYS) {
    const calc = driverCalcs[key];
    if (calc.calculatedCarryOver === 0) {
      console.log(`  [${DRIVERS[key].name}] 이월금 없음 — 건너뜀`);
      continue;
    }

    console.log(`\n  [${DRIVERS[key].name}] 매니저 이체 (₩${calc.calculatedCarryOver})...`);
    await transferCarryOver(key, calc.calculatedCarryOver);

    let driverDoc = await getDocument(driverDocPath(key));
    let co = driverDoc?.carryOver;
    assertEqual(co?.status, "TRANSFERRED", `  CP: ${DRIVERS[key].name} carryOver.status = TRANSFERRED`);
    assertEqual(co?.balance, calc.calculatedCarryOver, `  CP: ${DRIVERS[key].name} 이체 금액 = ${calc.calculatedCarryOver}`);

    console.log(`  [${DRIVERS[key].name}] 기사 수령 확인...`);
    await confirmReceiveCarryOver(key);

    driverDoc = await getDocument(driverDocPath(key));
    co = driverDoc?.carryOver;
    assertEqual(co?.status, "SETTLED", `  CP: ${DRIVERS[key].name} carryOver.status = SETTLED`);
    assertEqual(co?.balance, 0, `  CP: ${DRIVERS[key].name} carryOver.balance = 0`);
  }

  // ── 최종 요약 ──
  console.log("\n--- Day 1 최종 요약 ---");
  console.log(`  콜: ${allCalls.length}건`);
  console.log(`  총 매출: ₩${expectedTotalFare.toLocaleString()}`);
  for (const key of DRIVER_KEYS) {
    const c = driverCalcs[key];
    const driverCalls = allCalls.filter(cc => cc.driverKey === key);
    console.log(`  ${DRIVERS[key].name}: ${driverCalls.length}건, 매출 ₩${c.totalFare.toLocaleString()}, 이월 ₩${c.calculatedCarryOver.toLocaleString()} → 0`);
  }

  return allCalls;
}

// ═══════════════════════════════════════════════════════════
// DAY 2: 취소 + 거절 + 타임아웃 (50콜) + 정산 마감
// ═══════════════════════════════════════════════════════════
async function day2() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 2: 취소/거절/타임아웃 50콜 + 정산 마감");
  console.log("=".repeat(60));

  // 기사 상태 초기화
  console.log("\n[준비] 기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  const completedCalls = [];
  const cancelledIds = [];

  // 1. 정상 완료 20건
  console.log("\n[1/7] 정상 완료 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 500);
    const result = await createAndCompleteCall(2, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    completedCalls.push(result);
  }

  // 2. 거절→재배차→완료 5건 (A거절→B완료)
  console.log("[2/7] 기사 거절→재배차→완료 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 25000, fare_set: 25000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong", provinceId: "gyeonggi",
      officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "테스트출발", destination_set: "테스트도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });

    // A에게 배차 → 거절 → WAITING
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "WAITING", assignedDriverId: "", assignedDriverName: "", assignedDriverPhone: "",
      updatedAt: new Date(ts.getTime() + 10000),
    });

    // B에게 재배차 → 완료
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.B.id, assignedDriverName: DRIVERS.B.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 15000), updatedAt: new Date(ts.getTime() + 15000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 20000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "IN_PROGRESS", updatedAt: new Date(ts.getTime() + 30000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "AWAITING_SETTLEMENT", updatedAt: new Date(ts.getTime() + 120000),
      finalFare: 25000, fareFinal: 25000,
      tripSummaryFinal: "출발: 테스트출발, 도착: 테스트도착, 요금: 25000원",
      trip_summary: "출발: 테스트출발, 도착: 테스트도착, 요금: 25000원",
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "COMPLETED", completedAt: new Date(ts.getTime() + 180000),
      updatedAt: new Date(ts.getTime() + 180000),
      paymentMethod: "현금", cashReceived: 25000, creditAmount: 0,
    });
    completedCalls.push({ callId, driverKey: "B", fare: 25000, paymentMethod: "현금", cashReceived: 25000, creditAmount: 0, pointsUsed: 0 });
  }

  // 3. 관리자 취소 CANCELED 5건
  console.log("[3/7] 관리자 취소 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 15000, fare_set: 15000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong", provinceId: "gyeonggi",
      officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "취소테스트", destination_set: "취소도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "CANCELED", updatedAt: new Date(ts.getTime() + 10000),
    });
    cancelledIds.push(callId);
  }

  // 4. 기사 취소 5건
  console.log("[4/7] 기사 취소 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 18000, fare_set: 18000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong", provinceId: "gyeonggi",
      officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "취소테스트", destination_set: "취소도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.B.id, assignedDriverName: DRIVERS.B.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 5000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "CANCELLED_BY_DRIVER", updatedAt: new Date(ts.getTime() + 15000) });
    cancelledIds.push(callId);
  }

  // 5. 고객 취소 5건
  console.log("[5/7] 고객 취소 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 22000, fare_set: 22000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong", provinceId: "gyeonggi",
      officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "취소테스트", destination_set: "취소도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "CANCELLED_BY_CUSTOMER", updatedAt: new Date(ts.getTime() + 5000),
    });
    cancelledIds.push(callId);
  }

  // 6. 타임아웃→재배차→완료 5건
  console.log("[6/7] 타임아웃→재배차 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 30000, fare_set: 30000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong", provinceId: "gyeonggi",
      officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "타임아웃", destination_set: "타임아웃도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "WAITING", assignedDriverId: "", assignedDriverName: "", assignedDriverPhone: "",
      updatedAt: new Date(ts.getTime() + 182000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.B.id, assignedDriverName: DRIVERS.B.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 185000), updatedAt: new Date(ts.getTime() + 185000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 190000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "IN_PROGRESS", updatedAt: new Date(ts.getTime() + 200000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "AWAITING_SETTLEMENT", updatedAt: new Date(ts.getTime() + 300000),
      finalFare: 30000, fareFinal: 30000,
      tripSummaryFinal: "출발: 타임아웃, 도착: 타임아웃도착, 요금: 30000원",
      trip_summary: "출발: 타임아웃, 도착: 타임아웃도착, 요금: 30000원",
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "COMPLETED", completedAt: new Date(ts.getTime() + 360000),
      updatedAt: new Date(ts.getTime() + 360000),
      paymentMethod: "현금", cashReceived: 30000, creditAmount: 0,
    });
    completedCalls.push({ callId, driverKey: "B", fare: 30000, paymentMethod: "현금", cashReceived: 30000, creditAmount: 0, pointsUsed: 0 });
  }

  // 7. HOLD→재배차 5건
  console.log("[7/7] HOLD→재배차 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 28000, fare_set: 28000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong", provinceId: "gyeonggi",
      officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "HOLD테스트", destination_set: "HOLD도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });
    // 배차 → 기사취소 → HOLD → 재배차 → 완료
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "HOLD", updatedAt: new Date(ts.getTime() + 10000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 20000), updatedAt: new Date(ts.getTime() + 20000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 25000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "IN_PROGRESS", updatedAt: new Date(ts.getTime() + 35000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "AWAITING_SETTLEMENT", updatedAt: new Date(ts.getTime() + 120000),
      finalFare: 28000, fareFinal: 28000,
      tripSummaryFinal: "출발: HOLD테스트, 도착: HOLD도착, 요금: 28000원",
      trip_summary: "출발: HOLD테스트, 도착: HOLD도착, 요금: 28000원",
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "COMPLETED", completedAt: new Date(ts.getTime() + 180000),
      updatedAt: new Date(ts.getTime() + 180000),
      paymentMethod: "현금", cashReceived: 28000, creditAmount: 0,
    });
    completedCalls.push({ callId, driverKey: "A", fare: 28000, paymentMethod: "현금", cashReceived: 28000, creditAmount: 0, pointsUsed: 0 });
  }

  console.log(`\n완료: ${completedCalls.length}건 정상, ${cancelledIds.length}건 취소. CF 대기 (15초)...`);
  await sleep(15000);

  // ── Phase 2: 정산 세션 검증 ──
  console.log("\n--- Phase 2: 정산 세션 검증 ---");
  const workDate = dayDateString(2);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);

  if (!session) {
    console.log("  !! 정산 세션 없음 — 건너뜀");
    return completedCalls;
  }

  const sessionCalls = session.calls || [];
  // 완료된 콜만: 20 + 5(거절→재배차) + 5(타임아웃→재배차) + 5(HOLD→재배차) = 35
  assertEqual(sessionCalls.length, 35, `CP2: 정산 콜 수 = 35 (actual: ${sessionCalls.length})`);

  // 취소 콜 정산 미포함 확인
  for (const cid of cancelledIds) {
    const found = sessionCalls.find(c => c.callId === cid);
    assert(!found, `CP3: 취소 콜 ${cid} 정산 미포함`);
  }

  // ── Phase 3: 기사별 마감 ──
  console.log("\n--- Phase 3: 기사 마감 + 확인 + 이체 + 수령 ---");
  const depositRatio = session.metadata?.depositRatio || 60;

  for (const key of DRIVER_KEYS) {
    const driverCalls = completedCalls.filter(c => c.driverKey === key);
    console.log(`\n  [${DRIVERS[key].name}] ${driverCalls.length}건 마감...`);

    const calc = await submitDailySettlement(key, 2, driverCalls, depositRatio, 0);

    const doc1 = await getDocument(driverDocPath(key));
    assertEqual(doc1?.dailySettlement?.status, "PENDING_CONFIRM", `  CP: ${DRIVERS[key].name} PENDING_CONFIRM`);

    await confirmDailySettlement(key, calc.calculatedCarryOver);
    const doc2 = await getDocument(driverDocPath(key));
    assertEqual(doc2?.dailySettlement?.status, "CONFIRMED", `  CP: ${DRIVERS[key].name} CONFIRMED`);

    if (calc.calculatedCarryOver !== 0) {
      await transferCarryOver(key, calc.calculatedCarryOver);
      await confirmReceiveCarryOver(key);
      const doc3 = await getDocument(driverDocPath(key));
      assertEqual(doc3?.carryOver?.balance, 0, `  CP: ${DRIVERS[key].name} balance = 0`);
      assertEqual(doc3?.carryOver?.status, "SETTLED", `  CP: ${DRIVERS[key].name} SETTLED`);
    }

    console.log(`    매출=${calc.totalFare}, 실납입=${calc.realDeposit}, 이월=${calc.calculatedCarryOver}`);
  }

  return completedCalls;
}

// ═══════════════════════════════════════════════════════════
// DAY 3: 퇴근 + 재출근 + 이체 타이밍 (50콜)
// ═══════════════════════════════════════════════════════════
async function day3() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 3: 퇴근/재출근/이체타이밍 50콜 + 정산 마감");
  console.log("=".repeat(60));

  console.log("\n[준비] 기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  const allCalls = [];

  // 1차 운행 15건 (현금)
  console.log("\n[1/3] A,B 1차 운행 15건 (현금)...");
  for (let i = 0; i < 15; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 1000);
    const result = await createAndCompleteCall(3, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 2차 운행 15건 (현금/이체 혼합)
  console.log("[2/3] A,B 2차 운행 15건 (혼합)...");
  for (let i = 0; i < 15; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 25000 + (i * 500);
    const method = i % 2 === 0 ? "현금" : "이체";
    const result = await createAndCompleteCall(3, {
      driverKey, fare, paymentMethod: method,
      cashReceived: method === "현금" ? fare : 0,
    });
    allCalls.push(result);
  }

  // 추가 운행 20건 (다양한 결제)
  console.log("[3/3] A,B 추가 운행 20건 (다양한 결제)...");
  const methods = ["현금", "이체", "현금+포인트", "외상"];
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 18000 + (i * 1000);
    const method = methods[i % 4];
    const cash = method === "현금" ? fare : method === "현금+포인트" ? fare - 3000 : 0;
    const points = method === "현금+포인트" ? 3000 : 0;
    const credit = method === "외상" ? fare : 0;
    const result = await createAndCompleteCall(3, {
      driverKey, fare, paymentMethod: method,
      cashReceived: cash, pointsUsed: points, creditAmount: credit,
    });
    allCalls.push(result);
  }

  console.log(`\n총 ${allCalls.length}건. CF 대기 (15초)...`);
  await sleep(15000);

  // Phase 2: 정산 검증
  console.log("\n--- Phase 2: 정산 세션 검증 ---");
  const workDate = dayDateString(3);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);
  }

  // Phase 3+4: 마감 전체 흐름
  console.log("\n--- Phase 3+4: 전체 마감 흐름 ---");
  const depositRatio = session?.metadata?.depositRatio || 60;

  for (const key of DRIVER_KEYS) {
    const driverCalls = allCalls.filter(c => c.driverKey === key);
    console.log(`\n  [${DRIVERS[key].name}] ${driverCalls.length}건 마감...`);

    const calc = await submitDailySettlement(key, 3, driverCalls, depositRatio, 0);
    assertEqual((await getDocument(driverDocPath(key)))?.dailySettlement?.status, "PENDING_CONFIRM", `  CP: PENDING_CONFIRM`);

    await confirmDailySettlement(key, calc.calculatedCarryOver);
    assertEqual((await getDocument(driverDocPath(key)))?.dailySettlement?.status, "CONFIRMED", `  CP: CONFIRMED`);

    if (calc.calculatedCarryOver !== 0) {
      await transferCarryOver(key, calc.calculatedCarryOver);
      await confirmReceiveCarryOver(key);
    }
    assertEqual((await getDocument(driverDocPath(key)))?.carryOver?.balance, 0, `  CP: balance = 0`);

    console.log(`    매출=${calc.totalFare}, 외상=${calc.totalCredit}, 이월=${calc.calculatedCarryOver}`);
  }

  return allCalls;
}

// ═══════════════════════════════════════════════════════════
// DAY 4: 거절/재제출 + 통합정산 (50콜)
// ═══════════════════════════════════════════════════════════
async function day4() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 4: 거절/재제출 + 통합정산 50콜");
  console.log("=".repeat(60));

  console.log("\n[준비] 기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  const allCalls = [];

  // 1. 정상운행 20건
  console.log("\n[1/3] 정상운행 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 22000 + (i * 500);
    const result = await createAndCompleteCall(4, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 2. A 추가 운행 15건 (마감→거절→재제출 시나리오)
  console.log("[2/3] A 추가 15건...");
  for (let i = 0; i < 15; i++) {
    const fare = 20000;
    const result = await createAndCompleteCall(4, {
      driverKey: "A", fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 3. B 추가 15건
  console.log("[3/3] B 추가 15건...");
  for (let i = 0; i < 15; i++) {
    const fare = 25000 + (i * 1000);
    const result = await createAndCompleteCall(4, {
      driverKey: "B", fare, paymentMethod: i % 2 === 0 ? "이체" : "현금",
      cashReceived: i % 2 === 0 ? 0 : (25000 + i * 1000),
    });
    allCalls.push(result);
  }

  console.log(`\n총 ${allCalls.length}건. CF 대기 (15초)...`);
  await sleep(15000);

  // 정산 검증
  const workDate = dayDateString(4);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);
  }

  // Phase 3: A 마감 → 거절 → 재제출 시나리오
  console.log("\n--- Phase 3: A 마감 → 거절 → 재제출 ---");
  const depositRatio = session?.metadata?.depositRatio || 60;

  const aCalls = allCalls.filter(c => c.driverKey === "A");
  const aCalc = await submitDailySettlement("A", 4, aCalls, depositRatio, 0);
  assertEqual((await getDocument(driverDocPath("A")))?.dailySettlement?.status, "PENDING_CONFIRM", `  CP: A PENDING_CONFIRM`);

  // 매니저 거절
  console.log("  [A] 매니저 거절...");
  await updateDocument(driverDocPath("A"), {
    "dailySettlement.status": "REJECTED",
  });
  assertEqual((await getDocument(driverDocPath("A")))?.dailySettlement?.status, "REJECTED", `  CP: A REJECTED`);

  // 기사 재제출
  console.log("  [A] 기사 재제출...");
  const aCalc2 = await submitDailySettlement("A", 4, aCalls, depositRatio, 0);
  assertEqual((await getDocument(driverDocPath("A")))?.dailySettlement?.status, "PENDING_CONFIRM", `  CP: A 재제출 PENDING_CONFIRM`);

  // 매니저 재확인
  await confirmDailySettlement("A", aCalc2.calculatedCarryOver);
  assertEqual((await getDocument(driverDocPath("A")))?.dailySettlement?.status, "CONFIRMED", `  CP: A 최종 CONFIRMED`);

  if (aCalc2.calculatedCarryOver !== 0) {
    await transferCarryOver("A", aCalc2.calculatedCarryOver);
    await confirmReceiveCarryOver("A");
  }
  assertEqual((await getDocument(driverDocPath("A")))?.carryOver?.balance, 0, `  CP: A balance = 0`);
  console.log(`    A: 매출=${aCalc2.totalFare}, 이월=${aCalc2.calculatedCarryOver}`);

  // B 정상 마감
  console.log("\n  [B] 정상 마감...");
  const bCalls = allCalls.filter(c => c.driverKey === "B");
  const bCalc = await submitDailySettlement("B", 4, bCalls, depositRatio, 0);
  await confirmDailySettlement("B", bCalc.calculatedCarryOver);
  if (bCalc.calculatedCarryOver !== 0) {
    await transferCarryOver("B", bCalc.calculatedCarryOver);
    await confirmReceiveCarryOver("B");
  }
  assertEqual((await getDocument(driverDocPath("B")))?.carryOver?.balance, 0, `  CP: B balance = 0`);
  console.log(`    B: 매출=${bCalc.totalFare}, 이월=${bCalc.calculatedCarryOver}`);

  return allCalls;
}

// ═══════════════════════════════════════════════════════════
// DAY 5: 동시성 + 연속 빠른 콜 (50콜)
// ═══════════════════════════════════════════════════════════
async function day5() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 5: 동시성 + 연속 빠른 콜 50콜");
  console.log("=".repeat(60));

  console.log("\n[준비] 기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  const allCalls = [];

  // 1. 동시배차 5라운드 × 2건 = 10건 (병렬)
  console.log("\n[1/3] 동시배차 5라운드 × 2건 = 10건...");
  for (let round = 0; round < 5; round++) {
    const promises = DRIVER_KEYS.map(driverKey => {
      const fare = 20000 + (round * 2000);
      return createAndCompleteCall(5, {
        driverKey, fare, paymentMethod: "현금", cashReceived: fare,
      });
    });
    const ids = await Promise.all(promises);
    allCalls.push(...ids);
    console.log(`  라운드 ${round + 1}/5 완료`);
  }

  // 2. 연속 빠른 콜 20건
  console.log("[2/3] 연속 빠른 콜 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 15000 + (i * 1000);
    const result = await createAndCompleteCall(5, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 3. 정상운행 20건
  console.log("[3/3] 정상운행 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 22000 + (i * 500);
    const result = await createAndCompleteCall(5, {
      driverKey, fare, paymentMethod: i % 3 === 0 ? "이체" : "현금",
      cashReceived: i % 3 === 0 ? 0 : (22000 + i * 500),
    });
    allCalls.push(result);
  }

  console.log(`\n총 ${allCalls.length}건. CF 대기 (20초 - 동시성)...`);
  await sleep(20000);

  const workDate = dayDateString(5);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);
    const callIdSet = new Set(session.calls.map(c => c.callId));
    assertEqual(callIdSet.size, session.calls.length, `CP3: 중복 콜 없음`);
  }

  // 마감
  console.log("\n--- 전체 마감 ---");
  const depositRatio = session?.metadata?.depositRatio || 60;

  for (const key of DRIVER_KEYS) {
    const driverCalls = allCalls.filter(c => c.driverKey === key);
    const calc = await submitDailySettlement(key, 5, driverCalls, depositRatio, 0);
    await confirmDailySettlement(key, calc.calculatedCarryOver);
    if (calc.calculatedCarryOver !== 0) {
      await transferCarryOver(key, calc.calculatedCarryOver);
      await confirmReceiveCarryOver(key);
    }
    assertEqual((await getDocument(driverDocPath(key)))?.carryOver?.balance, 0, `  CP: ${DRIVERS[key].name} balance = 0`);
    console.log(`  ${DRIVERS[key].name}: ${driverCalls.length}건, 매출=${calc.totalFare}`);
  }

  return allCalls;
}

// ═══════════════════════════════════════════════════════════
// DAY 6: 공유콜 + 0원/소액/고액 + 연속 콜 (50콜)
// ═══════════════════════════════════════════════════════════
async function day6() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 6: 공유콜 + 특수상황 50콜");
  console.log("=".repeat(60));

  console.log("\n[준비] 기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  const allCalls = [];

  // 1. 정상운행 20건
  console.log("\n[1/6] 정상운행 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 1000);
    const result = await createAndCompleteCall(6, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 2. 공유콜 5건 (shared_calls — 정산 미포함)
  console.log("[2/6] 공유콜 5건 (shared_calls)...");
  for (let i = 0; i < 5; i++) {
    const callId = `${TEST_PREFIX}d6_shared_${i}`;
    const ts = new Date(dayBaseTime(6).getTime() + (callCounter + i) * 60000);
    await createDocument("shared_calls", callId, {
      status: "SHARED_WAITING",
      fare: 25000, fare_set: 25000,
      phoneNumber: `0109999${String(i).padStart(4, "0")}`,
      customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      sourceOfficeId: "nEkf0X9g3LZtRX94Mrzu",
      sourceOfficeName: "테스트사무실",
      provinceId: "gyeonggi", cityId: "yangpyeong",
      departure_set: "공유출발", destination_set: "공유도착",
      waypoints_set: "", createdBy: "test-script",
    });
  }

  // 3. 0원 콜 5건
  console.log("[3/6] 0원 콜 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const result = await createAndCompleteCall(6, {
      driverKey, fare: 0, paymentMethod: "현금", cashReceived: 0,
    });
    allCalls.push(result);
  }

  // 4. 소액 5건 + 고액 5건
  console.log("[4/6] 소액 5000원 5건 + 고액 200000원 5건...");
  for (let i = 0; i < 5; i++) {
    const result = await createAndCompleteCall(6, {
      driverKey: "A", fare: 5000, paymentMethod: "현금", cashReceived: 5000,
    });
    allCalls.push(result);
  }
  for (let i = 0; i < 5; i++) {
    const result = await createAndCompleteCall(6, {
      driverKey: "B", fare: 200000, paymentMethod: "이체", cashReceived: 0,
    });
    allCalls.push(result);
  }

  // 5. 동일기사 연속 10건
  console.log("[5/6] 동일기사(A) 연속 10건...");
  for (let i = 0; i < 10; i++) {
    const fare = 20000 + (i * 1000);
    const result = await createAndCompleteCall(6, {
      driverKey: "A", fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 6. 추가 운행 5건 (50건 맞추기)
  console.log("[6/6] 추가 운행 5건 (50건 맞추기)...");
  for (let i = 0; i < 5; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 22000 + (i * 2000);
    const result = await createAndCompleteCall(6, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 총: 20+5+10+10+5 = 50 완료 + 공유 5 (미포함)
  console.log(`\n총 ${allCalls.length}건 완료 + 공유콜 5건. CF 대기 (15초)...`);
  await sleep(15000);

  const workDate = dayDateString(6);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);

    const zeroCalls = session.calls.filter(c => c.fare === 0);
    assertEqual(zeroCalls.length, 5, `CP3: 0원 콜 5건 (actual: ${zeroCalls.length})`);

    const highCalls = session.calls.filter(c => c.fare === 200000);
    assertEqual(highCalls.length, 5, `CP4: 200000원 콜 5건 (actual: ${highCalls.length})`);
  }

  // 공유콜 상태 확인
  const sharedCall = await getDocument(`shared_calls/${TEST_PREFIX}d6_shared_0`);
  assertEqual(sharedCall?.status, "SHARED_WAITING", "CP5: 공유콜 상태 = SHARED_WAITING");

  // 마감
  console.log("\n--- 전체 마감 ---");
  const depositRatio = session?.metadata?.depositRatio || 60;

  for (const key of DRIVER_KEYS) {
    const driverCalls = allCalls.filter(c => c.driverKey === key);
    const calc = await submitDailySettlement(key, 6, driverCalls, depositRatio, 0);
    await confirmDailySettlement(key, calc.calculatedCarryOver);
    if (calc.calculatedCarryOver !== 0) {
      await transferCarryOver(key, calc.calculatedCarryOver);
      await confirmReceiveCarryOver(key);
    }
    assertEqual((await getDocument(driverDocPath(key)))?.carryOver?.balance, 0, `  CP: ${DRIVERS[key].name} balance = 0`);
    console.log(`  ${DRIVERS[key].name}: ${driverCalls.length}건, 매출=${calc.totalFare}`);
  }

  return allCalls;
}

// ═══════════════════════════════════════════════════════════
// DAY 7: 최종 정리 + 혼합결제 + 전원 마감 (50콜)
// ═══════════════════════════════════════════════════════════
async function day7() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 7: 최종 혼합 + 전원 마감 50콜");
  console.log("=".repeat(60));

  console.log("\n[준비] 기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  const allCalls = [];

  // 1. 이월금 상태 운행 15건
  console.log("\n[1/3] 이월 상태 운행 15건...");
  for (let i = 0; i < 15; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 25000 + (i * 1000);
    const result = await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  // 2. 혼합 결제 15건
  console.log("[2/3] 혼합 결제 15건...");
  const payMethods = ["현금", "이체", "현금+포인트", "외상", "포인트"];
  for (let i = 0; i < 15; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 2000);
    const method = payMethods[i % 5];
    const cash = method === "현금" ? fare : method === "현금+포인트" ? fare - 5000 : 0;
    const points = method === "현금+포인트" ? 5000 : method === "포인트" ? fare : 0;
    const credit = method === "외상" ? fare : 0;
    const result = await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: method,
      cashReceived: cash, pointsUsed: points, creditAmount: credit,
    });
    allCalls.push(result);
  }

  // 3. 전원 마감 운행 20건
  console.log("[3/3] 전원 마감 운행 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 30000;
    const result = await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    allCalls.push(result);
  }

  console.log(`\n총 ${allCalls.length}건. CF 대기 (15초)...`);
  await sleep(15000);

  const workDate = dayDateString(7);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);

    const paymentTypes = new Set(session.calls.map(c => c.paymentMethod));
    assert(paymentTypes.size >= 4, `CP3: 결제타입 ≥ 4 (actual: ${paymentTypes.size})`);

    assertEqual(session.totals?.totalDriverShare, session.totals?.totalFare - session.totals?.totalDeposit,
      `CP4: driverShare = fare - deposit`);
  }

  // 마감
  console.log("\n--- 전원 마감 ---");
  const depositRatio = session?.metadata?.depositRatio || 60;

  for (const key of DRIVER_KEYS) {
    const driverCalls = allCalls.filter(c => c.driverKey === key);
    const calc = await submitDailySettlement(key, 7, driverCalls, depositRatio, 0);
    await confirmDailySettlement(key, calc.calculatedCarryOver);
    if (calc.calculatedCarryOver !== 0) {
      await transferCarryOver(key, calc.calculatedCarryOver);
      await confirmReceiveCarryOver(key);
    }
    assertEqual((await getDocument(driverDocPath(key)))?.carryOver?.balance, 0, `  CP: ${DRIVERS[key].name} balance = 0`);
    console.log(`  ${DRIVERS[key].name}: ${driverCalls.length}건, 매출=${calc.totalFare}, 이월=${calc.calculatedCarryOver}`);
  }

  return allCalls;
}

// ═══════════════════════════════════════════════════════════
// 종합 검증
// ═══════════════════════════════════════════════════════════
async function crossDayVerification() {
  console.log("\n" + "=".repeat(60));
  console.log("CROSS-DAY 종합 검증");
  console.log("=".repeat(60));

  let grandTotalFare = 0;
  let grandTotalCalls = 0;

  for (let d = 1; d <= 7; d++) {
    const date = dayDateString(d);
    const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${date}`);
    if (session) {
      const callCount = session.calls?.length || 0;
      const totalFare = session.totals?.totalFare || 0;
      grandTotalCalls += callCount;
      grandTotalFare += totalFare;
      console.log(`  ${date}: ${callCount}건, ₩${totalFare.toLocaleString()}`);

      // 정산 공식 검증
      const ratio = session.metadata?.depositRatio || 60;
      const expectedDeposit = Math.floor(totalFare * ratio / 100);
      assertEqual(session.totals?.totalDeposit, expectedDeposit, `  ${date} deposit 검증`);
      assertEqual(session.totals?.totalDriverShare, totalFare - expectedDeposit, `  ${date} driverShare 검증`);
    } else {
      failCount++;
      console.log(`  ✗ ${date}: 세션 없음!`);
    }
  }

  // Day2: 15건 취소 → 35건만 정산. 나머지 Day는 50건
  // 총: 50 + 35 + 50 + 50 + 50 + 50 + 50 = 335
  console.log(`\n  총 정산 콜: ${grandTotalCalls}건`);
  console.log(`  총 매출: ₩${grandTotalFare.toLocaleString()}`);
  assertEqual(grandTotalCalls, 335, `총 정산 콜 수 = 335`);

  // 기사 최종 상태 검증
  console.log("\n--- 기사 최종 상태 ---");
  for (const key of DRIVER_KEYS) {
    const doc = await getDocument(driverDocPath(key));
    assertEqual(doc?.carryOver?.balance, 0, `  ${DRIVERS[key].name} 최종 balance = 0`);
    assertEqual(doc?.carryOver?.status, "SETTLED", `  ${DRIVERS[key].name} 최종 status = SETTLED`);
  }
}

// ─── Cleanup ───
async function cleanup() {
  console.log("\n정리 중...");

  const allCalls = await listDocuments(`${OFFICE_PATH}/calls`);
  const testCalls = allCalls.filter(c => c.id.startsWith(TEST_PREFIX));
  console.log(`  테스트 콜 ${testCalls.length}건 삭제...`);
  for (const call of testCalls) {
    await deleteDocument(`${OFFICE_PATH}/calls/${call.id}`);
  }

  const sharedCalls = await listDocuments("shared_calls");
  const testShared = sharedCalls.filter(c => c.id.startsWith(TEST_PREFIX));
  console.log(`  공유콜 ${testShared.length}건 삭제...`);
  for (const call of testShared) {
    await deleteDocument(`shared_calls/${call.id}`);
  }

  console.log("  정산 세션 삭제...");
  for (let d = 1; d <= 7; d++) {
    await deleteDocument(`${OFFICE_PATH}/settlementSessions/${dayDateString(d)}`);
  }

  // 기사 정산 상태 초기화
  console.log("  기사 정산 상태 초기화...");
  for (const key of DRIVER_KEYS) {
    await resetDriverSettlement(key);
  }

  console.log("  정리 완료");
}

// ─── Status Check ───
async function statusCheck() {
  console.log("\n현재 상태 확인...\n");

  for (let d = 1; d <= 7; d++) {
    const date = dayDateString(d);
    const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${date}`);
    if (session) {
      console.log(`  Day ${d} (${date}): ${session.calls?.length || 0}건, ₩${(session.totals?.totalFare || 0).toLocaleString()}`);
    } else {
      console.log(`  Day ${d} (${date}): 없음`);
    }
  }

  console.log("");
  for (const key of DRIVER_KEYS) {
    const doc = await getDocument(driverDocPath(key));
    const ds = doc?.dailySettlement;
    const co = doc?.carryOver;
    console.log(`  ${DRIVERS[key].name}: settlement=${ds?.status || "없음"}, carryOver=${co?.status || "없음"} (₩${co?.balance || 0})`);
  }

  const allCalls = await listDocuments(`${OFFICE_PATH}/calls`);
  const testCalls = allCalls.filter(c => c.id.startsWith(TEST_PREFIX));
  console.log(`\n  테스트 콜: ${testCalls.length}건`);
}

// ─── Main ───
(async () => {
  const args = process.argv.slice(2);
  const dayArg = args[0];

  console.log("╔════════════════════════════════════════════════════╗");
  console.log("║  순차적 흐름 테스트: 콜→배차→운행→정산→마감→수령   ║");
  console.log("║  Firestore REST API + CF 트리거 + 정산 흐름 검증   ║");
  console.log("╚════════════════════════════════════════════════════╝");

  try {
    if (dayArg === "--cleanup") {
      await cleanup();
      return;
    }

    if (dayArg === "--status") {
      await statusCheck();
      return;
    }

    if (!dayArg || dayArg === "all") {
      await day1();
      await day2();
      await day3();
      await day4();
      await day5();
      await day6();
      await day7();
      await crossDayVerification();
    } else {
      const dayNum = parseInt(dayArg);
      const days = { 1: day1, 2: day2, 3: day3, 4: day4, 5: day5, 6: day6, 7: day7 };
      if (days[dayNum]) {
        await days[dayNum]();
      } else if (dayArg === "verify") {
        await crossDayVerification();
      } else {
        console.log("Usage: node scripts/sequential-flow-test.js [1-7|all|verify|--cleanup|--status]");
        return;
      }
    }

    console.log("\n" + "=".repeat(60));
    console.log(`결과: ${passCount} PASS / ${failCount} FAIL`);
    console.log("=".repeat(60));

    if (failCount > 0) process.exit(1);
  } catch (e) {
    console.error("Error:", e.message);
    console.error(e.stack);
    process.exit(1);
  }
})();
