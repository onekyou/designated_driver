/**
 * 손님앱 수정 검증용 7일 350콜 회귀 시뮬레이션
 *
 * sequential-flow-test.js 인프라 기반.
 * 이번 수정(Install Referrer 파싱 + 지역명 동적 조회)에 초점:
 *  - 손님앱 콜(createdFrom: "customer_app") 비율 높임
 *  - 다양한 취소 시나리오 (고객/기사/관리자)
 *  - 정산 마감 전체 흐름
 *  - provinces 문서 name 필드 존재 확인
 *
 * Usage:
 *   node scripts/customer-regression-test.js [day]       Day 1~7 실행 (기본: 전체)
 *   node scripts/customer-regression-test.js --cleanup    테스트 데이터 삭제
 *   node scripts/customer-regression-test.js --status     현재 상태 확인
 *   node scripts/customer-regression-test.js --provinces  provinces name 필드 확인만
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

const DRIVERS = {
  A: { id: "6RQEWvmDkkfTAHXjvbxPtYfa7mY2", name: "양세훈" },
  B: { id: "vIbRH7Ci17UqCUR6ty84eDdl2me2", name: "고양이" },
};
const DRIVER_KEYS = ["A", "B"];
const ADMIN_ID = "admin-test-script";

// ─── HTTP / Auth (sequential-flow-test.js와 동일) ───
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
    for (const [k, v] of Object.entries(val)) fields[k] = toFirestoreValue(v);
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
    for (const [k, v] of Object.entries(val.mapValue.fields || {})) obj[k] = fromFirestoreValue(v);
    return obj;
  }
  if (val.arrayValue) return (val.arrayValue.values || []).map(fromFirestoreValue);
  return val;
}

function parseDoc(doc) {
  const id = doc.name.split("/").pop();
  const fields = {};
  for (const [k, v] of Object.entries(doc.fields || {})) fields[k] = fromFirestoreValue(v);
  return { id, ...fields };
}

async function createDocument(collectionPath, docId, data) {
  const token = await getAccessToken();
  const fields = {};
  for (const [k, v] of Object.entries(data)) fields[k] = toFirestoreValue(v);
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

function buildNestedFields(data) {
  const result = {};
  for (const [key, value] of Object.entries(data)) {
    const parts = key.split(".");
    if (parts.length === 1) {
      result[key] = toFirestoreValue(value);
    } else {
      let current = result;
      for (let i = 0; i < parts.length - 1; i++) {
        if (!current[parts[i]]) current[parts[i]] = { mapValue: { fields: {} } };
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
  if (condition) { passCount++; console.log(`  \u2713 ${message}`); }
  else { failCount++; console.log(`  \u2717 FAIL: ${message}`); }
}

function assertEqual(actual, expected, message) {
  if (actual === expected) { passCount++; console.log(`  \u2713 ${message}`); }
  else { failCount++; console.log(`  \u2717 FAIL: ${message} (expected=${expected}, actual=${actual})`); }
}

const TEST_PREFIX = "creg_"; // customer-regression
let callCounter = 0;

function nextCallId(dayNum) {
  callCounter++;
  return `${TEST_PREFIX}d${dayNum}_${String(callCounter).padStart(3, "0")}`;
}

// 2026-04-15 ~ 04-21 (기존 테스트와 충돌 방지)
function dayBaseTime(dayNum) {
  const base = new Date("2026-04-15T09:00:00Z");
  base.setDate(base.getDate() + (dayNum - 1));
  return base;
}

function dayDateString(dayNum) {
  return dayBaseTime(dayNum).toISOString().split("T")[0];
}

// ─── 콜 생성 + 상태 전이 ───
async function createAndCompleteCall(dayNum, opts) {
  const {
    driverKey, fare, paymentMethod, cashReceived = 0,
    creditAmount = 0, pointsUsed = 0,
    customerPhone = "", customerName = "",
    departure = "테스트출발", destination = "테스트도착",
    fromCallManager = true, finalStatus = "COMPLETED",
    isAppCustomer = false, createdFrom = "call_detector",
  } = opts;

  const driver = DRIVERS[driverKey];
  const callId = nextCallId(dayNum);
  const baseTime = dayBaseTime(dayNum);
  const offset = callCounter * 60000;
  const timestamp = new Date(baseTime.getTime() + offset);
  const expireAt = new Date(timestamp.getTime() + 30 * 24 * 60 * 60 * 1000);

  // WAITING
  await createDocument(`${OFFICE_PATH}/calls`, callId, {
    status: "WAITING",
    phoneNumber: customerPhone, customerName, customerAddress: departure,
    fare, fare_set: fare, pointsUsed,
    timestamp, timestampClient: timestamp.getTime(),
    fromCallManager, cityId: "yangpyeong", provinceId: "gyeonggi",
    officeId: "nEkf0X9g3LZtRX94Mrzu",
    departure_set: departure, destination_set: destination, departure, destination,
    waypoints_set: "", createdBy: createdFrom, expireAt,
    createdFrom, isAppCustomer,
    customerId: isAppCustomer ? customerPhone : null,
    customerGrade: isAppCustomer ? "BRONZE" : null,
  });

  if (finalStatus === "WAITING") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // ASSIGNED
  const assignedTime = new Date(timestamp.getTime() + 2000);
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "ASSIGNED",
    assignedDriverId: driver.id, assignedDriverName: driver.name,
    assignedDriverPhone: "01000000000",
    assignedTimestamp: assignedTime, updatedAt: assignedTime,
  });

  if (finalStatus === "ASSIGNED") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // ACCEPTED
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "ACCEPTED", updatedAt: new Date(timestamp.getTime() + 5000),
  });

  if (finalStatus === "ACCEPTED") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // IN_PROGRESS
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "IN_PROGRESS", updatedAt: new Date(timestamp.getTime() + 30000),
  });

  if (finalStatus === "IN_PROGRESS") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // AWAITING_SETTLEMENT
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "AWAITING_SETTLEMENT",
    updatedAt: new Date(timestamp.getTime() + 120000),
    finalFare: fare, fareFinal: fare,
    tripSummaryFinal: `출발: ${departure}, 도착: ${destination}, 요금: ${fare}원`,
    trip_summary: `출발: ${departure}, 도착: ${destination}, 요금: ${fare}원`,
  });

  if (finalStatus === "AWAITING_SETTLEMENT") return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };

  // COMPLETED
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "COMPLETED",
    completedAt: new Date(timestamp.getTime() + 180000),
    updatedAt: new Date(timestamp.getTime() + 180000),
    paymentMethod, cashReceived, creditAmount,
  });

  return { callId, driverKey, fare, paymentMethod, cashReceived, creditAmount, pointsUsed };
}

// ─── 정산 헬퍼 ───
function driverDocPath(driverKey) {
  return `${OFFICE_PATH}/designated_drivers/${DRIVERS[driverKey].id}`;
}

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
  return { totalFare, totalCredit, totalCash, totalPoints, officeDeposit, driverShare, finalDeposit, realDeposit, calculatedCarryOver, tripCount: calls.length };
}

async function submitDailySettlement(driverKey, dayNum, calls, depositRatio, originalCarryOver = 0) {
  const calc = calculateDriverSettlement(calls, depositRatio, originalCarryOver);
  await updateDocument(driverDocPath(driverKey), {
    dailySettlement: {
      date: dayDateString(dayNum), status: "PENDING_CONFIRM",
      finalDeposit: calc.finalDeposit, realDeposit: calc.realDeposit,
      settlementDiff: (calc.realDeposit - calc.finalDeposit) + originalCarryOver,
      totalFare: calc.totalFare, totalCredit: calc.totalCredit,
      tripCount: calc.tripCount, submittedAt: new Date(),
      confirmedAt: null, confirmedBy: null,
      calculatedCarryOver: calc.calculatedCarryOver,
      originalCarryOver, originalTripCount: 0, originalTotalFare: 0, originalRealDeposit: 0,
    }
  });
  return calc;
}

async function confirmDailySettlement(driverKey, calculatedCarryOver) {
  const now = new Date();
  await updateDocument(driverDocPath(driverKey), {
    "dailySettlement.status": "CONFIRMED",
    "dailySettlement.confirmedAt": now,
    "dailySettlement.confirmedBy": ADMIN_ID,
    "carryOver.balance": calculatedCarryOver,
    "carryOver.status": calculatedCarryOver === 0 ? "SETTLED" : "PENDING",
    "carryOver.lastUpdatedAt": now,
  });
}

async function transferCarryOver(driverKey, balance) {
  const now = new Date();
  await updateDocument(driverDocPath(driverKey), {
    carryOver: { balance, todayAmount: balance, status: "TRANSFERRED", transferredAt: now, transferredBy: ADMIN_ID, lastUpdatedAt: now }
  });
}

async function confirmReceiveCarryOver(driverKey) {
  const now = new Date();
  await updateDocument(driverDocPath(driverKey), {
    "carryOver.balance": 0, "carryOver.status": "SETTLED",
    "carryOver.transferredAt": null, "carryOver.transferredBy": null, "carryOver.lastUpdatedAt": now,
  });
}

async function resetDriverSettlement(driverKey) {
  await updateDocument(driverDocPath(driverKey), {
    dailySettlement: {
      date: "", status: "WORKING", finalDeposit: 0, realDeposit: 0, settlementDiff: 0,
      totalFare: 0, totalCredit: 0, tripCount: 0, submittedAt: null, confirmedAt: null, confirmedBy: null,
      calculatedCarryOver: 0, originalCarryOver: 0, originalTripCount: 0, originalTotalFare: 0, originalRealDeposit: 0,
    },
    "carryOver.balance": 0, "carryOver.status": "SETTLED", "carryOver.lastUpdatedAt": new Date(),
  });
}

// ─── 정산 마감 전체 사이클 (기사별) ───
async function settleDay(dayNum, allCalls, depositRatio = 60) {
  console.log("\n--- 정산 마감 ---");
  for (const key of DRIVER_KEYS) {
    const driverCalls = allCalls.filter(c => c.driverKey === key);
    if (driverCalls.length === 0) continue;
    console.log(`  [${DRIVERS[key].name}] ${driverCalls.length}건 마감...`);
    const calc = await submitDailySettlement(key, dayNum, driverCalls, depositRatio, 0);
    const doc1 = await getDocument(driverDocPath(key));
    assertEqual(doc1?.dailySettlement?.status, "PENDING_CONFIRM", `  ${DRIVERS[key].name} PENDING_CONFIRM`);
    await confirmDailySettlement(key, calc.calculatedCarryOver);
    if (calc.calculatedCarryOver !== 0) {
      await transferCarryOver(key, calc.calculatedCarryOver);
      await confirmReceiveCarryOver(key);
    }
    const doc2 = await getDocument(driverDocPath(key));
    assertEqual(doc2?.carryOver?.balance, 0, `  ${DRIVERS[key].name} carryOver = 0`);
    console.log(`    매출=${calc.totalFare}, 실납입=${calc.realDeposit}, 이월=${calc.calculatedCarryOver} -> 0`);
  }
}

// ═══════════════════════════════════════════════════
// provinces name 필드 확인
// ═══════════════════════════════════════════════════
async function checkProvinces() {
  console.log("\n" + "=".repeat(60));
  console.log("provinces 문서 name 필드 확인");
  console.log("=".repeat(60));

  const provinces = await listDocuments("provinces");
  console.log(`\n  총 ${provinces.length}개 province 문서 발견`);

  let missingName = 0;
  for (const prov of provinces) {
    const hasName = prov.name !== undefined && prov.name !== null && prov.name !== "";
    if (hasName) {
      console.log(`  \u2713 ${prov.id}: name="${prov.name}"`);
      passCount++;
    } else {
      console.log(`  \u2717 ${prov.id}: name 필드 없음!`);
      failCount++;
      missingName++;
    }
  }

  assert(missingName === 0, `모든 province에 name 필드 존재 (missing: ${missingName})`);
  return provinces;
}

// ═══════════════════════════════════════════════════
// DAY 1: 정상 50콜 (현금/이체/포인트/외상 혼합)
// ═══════════════════════════════════════════════════
async function day1() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 1: 정상 50콜 (혼합 결제) + 정산");
  console.log("=".repeat(60));

  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);
  const allCalls = [];

  // 현금 20건
  console.log("\n[1/5] 현금 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 15000 + (i * 1000);
    allCalls.push(await createAndCompleteCall(1, { driverKey, fare, paymentMethod: "현금", cashReceived: fare }));
  }

  // 이체 10건
  console.log("[2/5] 이체 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 2000);
    allCalls.push(await createAndCompleteCall(1, { driverKey, fare, paymentMethod: "이체", cashReceived: 0 }));
  }

  // 포인트 혼합 10건
  console.log("[3/5] 현금+포인트 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 25000;
    const points = 3000;
    allCalls.push(await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "현금+포인트",
      cashReceived: fare - points, pointsUsed: points,
    }));
  }

  // 외상 5건
  console.log("[4/5] 외상 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000;
    allCalls.push(await createAndCompleteCall(1, { driverKey, fare, paymentMethod: "외상", cashReceived: 0, creditAmount: fare }));
  }

  // 전액 포인트 5건
  console.log("[5/5] 전액 포인트 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 10000;
    allCalls.push(await createAndCompleteCall(1, { driverKey, fare, paymentMethod: "포인트", cashReceived: 0, pointsUsed: fare }));
  }

  console.log(`\n총 ${allCalls.length}건 COMPLETED. CF 대기 (15초)...`);
  await sleep(15000);

  // 정산 세션 검증
  const workDate = dayDateString(1);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    const sessionCalls = session.calls || [];
    assertEqual(sessionCalls.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${sessionCalls.length})`);
  }

  await settleDay(1, allCalls);
  return allCalls;
}

// ═══════════════════════════════════════════════════
// DAY 2: 취소 혼합 50콜
// ═══════════════════════════════════════════════════
async function day2() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 2: 취소 혼합 50콜 (관리자/기사/고객)");
  console.log("=".repeat(60));

  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);
  const completedCalls = [];
  const cancelledIds = [];

  // 정상 완료 30건
  console.log("\n[1/4] 정상 완료 30건...");
  for (let i = 0; i < 30; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 18000 + (i * 500);
    completedCalls.push(await createAndCompleteCall(2, { driverKey, fare, paymentMethod: "현금", cashReceived: fare }));
  }

  // 관리자 취소 7건
  console.log("[2/4] 관리자 취소(CANCELED) 7건...");
  for (let i = 0; i < 7; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);
    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 15000, fare_set: 15000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(), fromCallManager: true,
      cityId: "yangpyeong", provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "취소테스트", destination_set: "취소도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "CANCELED", updatedAt: new Date(ts.getTime() + 10000) });
    cancelledIds.push(callId);
  }

  // 기사 취소 7건
  console.log("[3/4] 기사 취소(CANCELLED_BY_DRIVER) 7건...");
  for (let i = 0; i < 7; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);
    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 18000, fare_set: 18000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(), fromCallManager: true,
      cityId: "yangpyeong", provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
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

  // 고객 취소 6건 (WAITING에서 3건 + ASSIGNED에서 3건)
  console.log("[4/4] 고객 취소(CANCELLED_BY_CUSTOMER) 6건...");
  for (let i = 0; i < 6; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);
    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 22000, fare_set: 22000,
      phoneNumber: `0109999${String(i).padStart(4, "0")}`, customerName: `테스트고객${i}`,
      customerAddress: "", timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: false, createdFrom: "customer_app", isAppCustomer: true,
      cityId: "yangpyeong", provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "고객취소출발", destination_set: "고객취소도착",
      waypoints_set: "", createdBy: "customer_app", expireAt, pointsUsed: 0,
    });
    if (i >= 3) {
      // ASSIGNED 후 고객 취소
      await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
        status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
        assignedDriverPhone: "01000000000",
        assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
      });
    }
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "CANCELLED_BY_CUSTOMER", updatedAt: new Date(ts.getTime() + 5000) });
    cancelledIds.push(callId);
  }

  console.log(`\n완료: ${completedCalls.length}건 정상, ${cancelledIds.length}건 취소. CF 대기 (15초)...`);
  await sleep(15000);

  // 취소 콜이 정산에 미포함 확인
  const workDate = dayDateString(2);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    const sessionCalls = session.calls || [];
    assertEqual(sessionCalls.length, 30, `CP2: 정산 콜 수 = 30 (취소 20건 미포함, actual: ${sessionCalls.length})`);
  }

  await settleDay(2, completedCalls);
  return completedCalls;
}

// ═══════════════════════════════════════════════════
// DAY 3: 앱콜 50건 (createdFrom: "customer_app")
// ═══════════════════════════════════════════════════
async function day3() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 3: 손님앱 콜 50건 (createdFrom: customer_app)");
  console.log("=".repeat(60));

  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);
  const allCalls = [];

  console.log("\n[1/1] 손님앱 콜 50건 (현금/이체/포인트 혼합)...");
  for (let i = 0; i < 50; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 15000 + (i * 500);
    const methods = ["현금", "현금", "현금", "이체", "현금+포인트"];
    const method = methods[i % methods.length];
    const points = method === "현금+포인트" ? 3000 : 0;
    const cash = method === "이체" ? 0 : fare - points;

    allCalls.push(await createAndCompleteCall(3, {
      driverKey, fare, paymentMethod: method,
      cashReceived: cash, pointsUsed: points,
      customerPhone: `0108888${String(i).padStart(4, "0")}`,
      customerName: `앱고객${i}`,
      departure: `앱출발${i}`, destination: `앱도착${i}`,
      fromCallManager: false,
      createdFrom: "customer_app", isAppCustomer: true,
    }));
  }

  console.log(`\n총 ${allCalls.length}건 COMPLETED. CF 대기 (15초)...`);
  await sleep(15000);

  // createdFrom 필드 검증
  const sampleCallId = allCalls[0].callId;
  const sampleDoc = await getDocument(`${OFFICE_PATH}/calls/${sampleCallId}`);
  assertEqual(sampleDoc?.createdFrom, "customer_app", `CP1: createdFrom = customer_app`);
  assertEqual(sampleDoc?.isAppCustomer, true, `CP2: isAppCustomer = true`);
  assert(sampleDoc?.customerName !== "", `CP3: customerName 존재`);

  // 정산 세션 검증
  const workDate = dayDateString(3);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP4: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual((session.calls || []).length, 50, `CP5: 정산 콜 수 = 50`);
  }

  await settleDay(3, allCalls);
  return allCalls;
}

// ═══════════════════════════════════════════════════
// DAY 4: 거절 + 재배차 + 타임아웃 50건
// ═══════════════════════════════════════════════════
async function day4() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 4: 거절/재배차/타임아웃 50건");
  console.log("=".repeat(60));

  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);
  const completedCalls = [];

  // 정상 30건
  console.log("\n[1/3] 정상 완료 30건...");
  for (let i = 0; i < 30; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 500);
    completedCalls.push(await createAndCompleteCall(4, { driverKey, fare, paymentMethod: "현금", cashReceived: fare }));
  }

  // 거절->재배차->완료 10건
  console.log("[2/3] 거절->재배차->완료 10건...");
  for (let i = 0; i < 10; i++) {
    const callId = nextCallId(4);
    const ts = new Date(dayBaseTime(4).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);
    const fare = 25000;
    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare, fare_set: fare,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(), fromCallManager: true,
      cityId: "yangpyeong", provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "거절테스트", destination_set: "거절도착",
      waypoints_set: "", createdBy: "test-script", expireAt, pointsUsed: 0,
    });
    // A 배차 -> 거절 -> WAITING
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000), updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "WAITING", assignedDriverId: "", assignedDriverName: "", assignedDriverPhone: "",
      updatedAt: new Date(ts.getTime() + 10000),
    });
    // B 재배차 -> 완료
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED", assignedDriverId: DRIVERS.B.id, assignedDriverName: DRIVERS.B.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 15000), updatedAt: new Date(ts.getTime() + 15000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 20000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, { status: "IN_PROGRESS", updatedAt: new Date(ts.getTime() + 30000) });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "AWAITING_SETTLEMENT", updatedAt: new Date(ts.getTime() + 120000),
      finalFare: fare, fareFinal: fare,
      tripSummaryFinal: `출발: 거절테스트, 도착: 거절도착, 요금: ${fare}원`,
      trip_summary: `출발: 거절테스트, 도착: 거절도착, 요금: ${fare}원`,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "COMPLETED", completedAt: new Date(ts.getTime() + 180000),
      updatedAt: new Date(ts.getTime() + 180000),
      paymentMethod: "현금", cashReceived: fare, creditAmount: 0,
    });
    completedCalls.push({ callId, driverKey: "B", fare, paymentMethod: "현금", cashReceived: fare, creditAmount: 0, pointsUsed: 0 });
  }

  // 타임아웃->재배차->완료 10건
  console.log("[3/3] 타임아웃->재배차 10건...");
  for (let i = 0; i < 10; i++) {
    const callId = nextCallId(4);
    const ts = new Date(dayBaseTime(4).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);
    const fare = 30000;
    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare, fare_set: fare,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(), fromCallManager: true,
      cityId: "yangpyeong", provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
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
      finalFare: fare, fareFinal: fare,
      tripSummaryFinal: `출발: 타임아웃, 도착: 타임아웃도착, 요금: ${fare}원`,
      trip_summary: `출발: 타임아웃, 도착: 타임아웃도착, 요금: ${fare}원`,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "COMPLETED", completedAt: new Date(ts.getTime() + 360000),
      updatedAt: new Date(ts.getTime() + 360000),
      paymentMethod: "현금", cashReceived: fare, creditAmount: 0,
    });
    completedCalls.push({ callId, driverKey: "B", fare, paymentMethod: "현금", cashReceived: fare, creditAmount: 0, pointsUsed: 0 });
  }

  console.log(`\n총 ${completedCalls.length}건 COMPLETED. CF 대기 (15초)...`);
  await sleep(15000);

  const workDate = dayDateString(4);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual((session.calls || []).length, 50, `CP2: 정산 콜 수 = 50`);
  }

  await settleDay(4, completedCalls);
  return completedCalls;
}

// ═══════════════════════════════════════════════════
// DAY 5: 이월금 + 정산 50건
// ═══════════════════════════════════════════════════
async function day5() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 5: 이월금 누적 + 정산 50건");
  console.log("=".repeat(60));

  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);
  const allCalls = [];

  // 이체 위주 (이월금 발생하도록)
  console.log("\n[1/2] 이체 위주 25건 (이월금 발생)...");
  for (let i = 0; i < 25; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 1000);
    allCalls.push(await createAndCompleteCall(5, { driverKey, fare, paymentMethod: "이체", cashReceived: 0 }));
  }

  // 현금 25건 (이월금 상쇄)
  console.log("[2/2] 현금 25건...");
  for (let i = 0; i < 25; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 18000 + (i * 500);
    allCalls.push(await createAndCompleteCall(5, { driverKey, fare, paymentMethod: "현금", cashReceived: fare }));
  }

  console.log(`\n총 ${allCalls.length}건 COMPLETED. CF 대기 (15초)...`);
  await sleep(15000);

  const workDate = dayDateString(5);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);

  await settleDay(5, allCalls);
  return allCalls;
}

// ═══════════════════════════════════════════════════
// DAY 6: 앱콜+전화콜 혼합 50건
// ═══════════════════════════════════════════════════
async function day6() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 6: 앱콜+전화콜 혼합 50건");
  console.log("=".repeat(60));

  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);
  const allCalls = [];

  // 앱콜 25건
  console.log("\n[1/2] 앱콜 25건...");
  for (let i = 0; i < 25; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 15000 + (i * 1000);
    allCalls.push(await createAndCompleteCall(6, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
      customerPhone: `0107777${String(i).padStart(4, "0")}`,
      customerName: `앱손님${i}`,
      fromCallManager: false, createdFrom: "customer_app", isAppCustomer: true,
    }));
  }

  // 전화콜 25건 (call_detector)
  console.log("[2/2] 전화콜 25건...");
  for (let i = 0; i < 25; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 500);
    allCalls.push(await createAndCompleteCall(6, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
      createdFrom: "call_detector",
    }));
  }

  console.log(`\n총 ${allCalls.length}건 COMPLETED. CF 대기 (15초)...`);
  await sleep(15000);

  // 앱콜/전화콜 구분 확인
  const appCallDoc = await getDocument(`${OFFICE_PATH}/calls/${allCalls[0].callId}`);
  assertEqual(appCallDoc?.createdFrom, "customer_app", `CP1: 앱콜 createdFrom = customer_app`);
  const phoneCallDoc = await getDocument(`${OFFICE_PATH}/calls/${allCalls[25].callId}`);
  assertEqual(phoneCallDoc?.createdFrom, "call_detector", `CP2: 전화콜 createdFrom = call_detector`);

  await settleDay(6, allCalls);
  return allCalls;
}

// ═══════════════════════════════════════════════════
// DAY 7: 스트레스 혼합 50건
// ═══════════════════════════════════════════════════
async function day7() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 7: 스트레스 혼합 50건 (전 시나리오 조합)");
  console.log("=".repeat(60));

  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);
  const completedCalls = [];

  // 앱콜 현금 10건
  console.log("\n[1/5] 앱콜 현금 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000 + (i * 1000);
    completedCalls.push(await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
      customerPhone: `0106666${String(i).padStart(4, "0")}`,
      fromCallManager: false, createdFrom: "customer_app", isAppCustomer: true,
    }));
  }

  // 전화콜 이체 10건
  console.log("[2/5] 전화콜 이체 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 25000 + (i * 2000);
    completedCalls.push(await createAndCompleteCall(7, { driverKey, fare, paymentMethod: "이체", cashReceived: 0 }));
  }

  // 앱콜 포인트 혼합 10건
  console.log("[3/5] 앱콜 포인트 혼합 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 30000;
    const points = 5000;
    completedCalls.push(await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: "현금+포인트",
      cashReceived: fare - points, pointsUsed: points,
      fromCallManager: false, createdFrom: "customer_app", isAppCustomer: true,
    }));
  }

  // 외상 10건
  console.log("[4/5] 외상 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000;
    completedCalls.push(await createAndCompleteCall(7, { driverKey, fare, paymentMethod: "외상", cashReceived: 0, creditAmount: fare }));
  }

  // 고객 취소 후 재호출->완료 10건
  console.log("[5/5] 고객취소->재호출->완료 10건...");
  for (let i = 0; i < 10; i++) {
    // 먼저 취소
    const cancelId = nextCallId(7);
    const ts = new Date(dayBaseTime(7).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);
    await createDocument(`${OFFICE_PATH}/calls`, cancelId, {
      status: "WAITING", fare: 20000, fare_set: 20000,
      phoneNumber: `0105555${String(i).padStart(4, "0")}`, customerName: `재호출고객${i}`,
      customerAddress: "", timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: false, createdFrom: "customer_app", isAppCustomer: true,
      cityId: "yangpyeong", provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "재호출출발", destination_set: "재호출도착",
      waypoints_set: "", createdBy: "customer_app", expireAt, pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${cancelId}`, { status: "CANCELLED_BY_CUSTOMER", updatedAt: new Date(ts.getTime() + 5000) });

    // 재호출 -> 완료
    const driverKey = DRIVER_KEYS[i % 2];
    const fare = 20000;
    completedCalls.push(await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
      customerPhone: `0105555${String(i).padStart(4, "0")}`,
      fromCallManager: false, createdFrom: "customer_app", isAppCustomer: true,
    }));
  }

  console.log(`\n총 ${completedCalls.length}건 COMPLETED. CF 대기 (15초)...`);
  await sleep(15000);

  const workDate = dayDateString(7);
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, `CP1: 정산 세션 ${workDate} 생성됨`);
  if (session) {
    assertEqual((session.calls || []).length, 50, `CP2: 정산 콜 수 = 50 (취소 미포함, actual: ${(session.calls || []).length})`);
  }

  await settleDay(7, completedCalls);
  return completedCalls;
}

// ─── Cleanup ───
async function cleanup() {
  console.log("\n테스트 데이터 삭제 중...");

  // creg_ 접두어 콜 삭제
  const calls = await listDocuments(`${OFFICE_PATH}/calls`);
  const testCalls = calls.filter(c => c.id.startsWith(TEST_PREFIX));
  console.log(`  콜 ${testCalls.length}건 삭제 중...`);
  for (const call of testCalls) {
    await deleteDocument(`${OFFICE_PATH}/calls/${call.id}`);
  }

  // 정산 세션 삭제
  for (let d = 1; d <= 7; d++) {
    const dateStr = dayDateString(d);
    await deleteDocument(`${OFFICE_PATH}/settlementSessions/${dateStr}`);
  }

  // 기사 상태 초기화
  for (const key of DRIVER_KEYS) await resetDriverSettlement(key);

  console.log("  삭제 완료!");
}

// ─── Status ───
async function status() {
  console.log("\n현재 테스트 상태:");
  const calls = await listDocuments(`${OFFICE_PATH}/calls`);
  const testCalls = calls.filter(c => c.id.startsWith(TEST_PREFIX));
  console.log(`  테스트 콜: ${testCalls.length}건`);

  for (let d = 1; d <= 7; d++) {
    const dateStr = dayDateString(d);
    const dayCalls = testCalls.filter(c => c.id.startsWith(`${TEST_PREFIX}d${d}_`));
    const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${dateStr}`);
    console.log(`  Day ${d} (${dateStr}): 콜 ${dayCalls.length}건, 정산 세션 ${session ? "있음" : "없음"}`);
  }
}

// ─── Main ───
async function main() {
  const arg = process.argv[2];

  if (arg === "--cleanup") return await cleanup();
  if (arg === "--status") return await status();
  if (arg === "--provinces") {
    await checkProvinces();
    console.log(`\n${"=".repeat(40)}\nRESULT: ${passCount} PASS / ${failCount} FAIL\n${"=".repeat(40)}`);
    return;
  }

  console.log("=========================================");
  console.log(" 손님앱 수정 검증 7일 350콜 시뮬레이션");
  console.log("=========================================");

  // provinces name 필드 확인 (공통)
  await checkProvinces();

  const dayNum = arg ? parseInt(arg) : 0;
  const days = [day1, day2, day3, day4, day5, day6, day7];

  if (dayNum >= 1 && dayNum <= 7) {
    await days[dayNum - 1]();
  } else {
    for (let i = 0; i < days.length; i++) {
      await days[i]();
    }
  }

  console.log(`\n${"=".repeat(50)}`);
  console.log(` FINAL RESULT: ${passCount} PASS / ${failCount} FAIL`);
  console.log(`${"=".repeat(50)}`);

  if (failCount > 0) process.exit(1);
}

main().catch(e => { console.error("Fatal:", e); process.exit(1); });
