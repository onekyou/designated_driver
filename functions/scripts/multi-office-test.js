/**
 * 10개 사무실 공유콜 + 크로스 정산 시뮬레이션
 *
 * 검증 대상:
 *   - 공유콜 발신→수임→완료→정산 (CF 트리거 체인)
 *   - 다방향 공유콜 교차 (10개 사무실 순환)
 *   - 로컬+공유콜 혼합 정산
 *   - 공유콜 취소 (기사/발신/관리자)
 *   - 마감 거절/재제출 (공유콜 포함)
 *   - 공유콜 경합
 *
 * Usage:
 *   node scripts/multi-office-test.js setup
 *   node scripts/multi-office-test.js phase1~7 | all
 *   node scripts/multi-office-test.js --cleanup | --status
 */

const path = require("path");
const os = require("os");
const https = require("https");

// ─── Config ───
const PROJECT_ID = "calldetector-5d61e";
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

const PROVINCE = "gyeonggi";
const CITY = "yangpyeong";
const REAL_OFFICE_ID = "nEkf0X9g3LZtRX94Mrzu";
const ADMIN_ID = "admin-test-script";
const TEST_PREFIX = "mo_"; // multi-office prefix

// CF는 completedAt 기준으로 workDate 결정 → 오늘 날짜 사용
// KST 기준 06:00 이전이면 전날로 처리되므로 KST 기준 계산
function getTodayWorkDate() {
  const now = new Date();
  // KST = UTC + 9h. 06:00 KST = 21:00 UTC (전날)
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  const kstHour = kst.getUTCHours();
  // 06:00 이전이면 전날
  if (kstHour < 6) {
    kst.setUTCDate(kst.getUTCDate() - 1);
  }
  return kst.toISOString().split("T")[0];
}
const TEST_DATE = getTodayWorkDate();

// ─── 10개 사무실 정의 ───
const OFFICES = [];
for (let i = 0; i < 10; i++) {
  const officeId = i === 0 ? REAL_OFFICE_ID : `test_office_${i}`;
  const officePath = `provinces/${PROVINCE}/cities/${CITY}/offices/${officeId}`;
  OFFICES.push({
    index: i,
    id: officeId,
    path: officePath,
    name: i === 0 ? "본사무실" : `테스트사무실${i}`,
    drivers: {
      A: {
        id: i === 0 ? "6RQEWvmDkkfTAHXjvbxPtYfa7mY2" : `test_driver_${i}_A`,
        name: i === 0 ? "양세훈" : `테스트기사${i}A`,
      },
      B: {
        id: i === 0 ? "vIbRH7Ci17UqCUR6ty84eDdl2me2" : `test_driver_${i}_B`,
        name: i === 0 ? "고양이" : `테스트기사${i}B`,
      },
    },
  });
}

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

async function createDocument(collectionPath, docId, data) {
  const token = await getAccessToken();
  const fields = {};
  for (const [k, v] of Object.entries(data)) fields[k] = toFirestoreValue(v);
  const url = docId
    ? `${BASE_URL}/${collectionPath}?documentId=${docId}`
    : `${BASE_URL}/${collectionPath}`;
  const result = await httpRequest(url, {
    method: "POST",
    headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields })
  });
  if (result.error) throw new Error(`Create failed: ${result.error.message}`);
  return parseDoc(result);
}

async function updateDocument(docPath, data) {
  const token = await getAccessToken();
  const fields = buildNestedFields(data);
  const updateMask = Object.keys(data).map(k => `updateMask.fieldPaths=${k}`).join("&");
  const url = `${BASE_URL}/${docPath}?${updateMask}`;
  const result = await httpRequest(url, {
    method: "PATCH",
    headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields })
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
    const result = await httpRequest(url, { headers: { "Authorization": `Bearer ${token}` } });
    if (result.error) throw new Error(`List failed: ${result.error.message}`);
    allDocs.push(...(result.documents || []).map(parseDoc));
    pageToken = result.nextPageToken || "";
  } while (pageToken);
  return allDocs;
}

async function deleteDocument(docPath) {
  const token = await getAccessToken();
  return await httpRequest(`${BASE_URL}/${docPath}`, {
    method: "DELETE", headers: { "Authorization": `Bearer ${token}` }
  });
}

// ─── Test Helpers ───
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
let passCount = 0;
let failCount = 0;

function assert(condition, message) {
  if (condition) { passCount++; console.log(`  ✓ ${message}`); }
  else { failCount++; console.log(`  ✗ FAIL: ${message}`); }
}

function assertEqual(actual, expected, message) {
  if (actual === expected) { passCount++; console.log(`  ✓ ${message}`); }
  else { failCount++; console.log(`  ✗ FAIL: ${message} (expected=${expected}, actual=${actual})`); }
}

let callCounter = 0;
function nextCallId(phase) {
  callCounter++;
  return `${TEST_PREFIX}p${phase}_${String(callCounter).padStart(3, "0")}`;
}

function baseTime() {
  return new Date(`${TEST_DATE}T09:00:00Z`);
}

// ─── 정산 계산 ───
function calculateDriverSettlement(calls, depositRatio, originalCarryOver = 0) {
  const totalFare = calls.reduce((s, c) => s + c.fare, 0);
  const totalCredit = calls.reduce((s, c) => s + (c.creditAmount || 0), 0);
  const totalCash = calls.reduce((s, c) => s + (c.cashReceived || 0), 0);
  const officeDeposit = Math.floor(totalFare * depositRatio / 100);
  const driverShare = totalFare - officeDeposit;
  const finalDeposit = officeDeposit - totalCredit;
  const realDeposit = totalCash - driverShare;
  const calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit;
  return { totalFare, totalCredit, totalCash, officeDeposit, driverShare, finalDeposit, realDeposit, calculatedCarryOver, tripCount: calls.length };
}

// ═══════════════════════════════════════════════════
// SETUP: 사무실 + 기사 생성
// ═══════════════════════════════════════════════════
async function setup() {
  console.log("\n" + "=".repeat(60));
  console.log("SETUP: 테스트 사무실 9개 + 기사 18명 생성");
  console.log("=".repeat(60));

  // 사무실 생성 (office_1 ~ office_9)
  for (let i = 1; i <= 9; i++) {
    const office = OFFICES[i];
    console.log(`\n  사무실 ${i}: ${office.id}...`);

    try {
      await createDocument(
        `provinces/${PROVINCE}/cities/${CITY}/offices`,
        office.id,
        {
          name: office.name,
          depositRatio: 60,
          createdAt: new Date(),
          isTestData: true,
          provinceId: PROVINCE,
          cityId: CITY,
        }
      );
      console.log(`    ✓ 생성 완료`);
    } catch (e) {
      if (e.message.includes("ALREADY_EXISTS")) {
        console.log(`    - 이미 존재`);
      } else {
        throw e;
      }
    }

    // 기사 2명 생성
    for (const key of ["A", "B"]) {
      const driver = office.drivers[key];
      try {
        await createDocument(
          `${office.path}/designated_drivers`,
          driver.id,
          {
            id: driver.id,
            authUid: driver.id,
            name: driver.name,
            phoneNumber: `0100000${String(i).padStart(2, "0")}${key === "A" ? "01" : "02"}`,
            email: `${driver.id}@test.com`,
            driverType: "DESIGNATED",
            status: "ONLINE",
            approvalStatus: "APPROVED",
            provinceId: PROVINCE,
            cityId: CITY,
            officeId: office.id,
            associatedOfficeId: office.id,
            isActive: true,
            isTestData: true,
            createdAt: new Date(),
            updatedAt: new Date(),
            rating: 0,
            totalTrips: 0,
          }
        );
        console.log(`    ✓ ${driver.name} 생성`);
      } catch (e) {
        if (e.message.includes("ALREADY_EXISTS")) {
          console.log(`    - ${driver.name} 이미 존재`);
        } else {
          throw e;
        }
      }
    }
  }

  // office_0 기사 상태 초기화
  console.log("\n  office_0 기사 정산 초기화...");
  for (const key of ["A", "B"]) {
    const driver = OFFICES[0].drivers[key];
    await updateDocument(`${OFFICES[0].path}/designated_drivers/${driver.id}`, {
      dailySettlement: {
        date: "", status: "WORKING", finalDeposit: 0, realDeposit: 0,
        settlementDiff: 0, totalFare: 0, totalCredit: 0, tripCount: 0,
        submittedAt: null, confirmedAt: null, confirmedBy: null,
        calculatedCarryOver: 0, originalCarryOver: 0,
        originalTripCount: 0, originalTotalFare: 0, originalRealDeposit: 0,
      },
      "carryOver.balance": 0,
      "carryOver.status": "SETTLED",
      "carryOver.lastUpdatedAt": new Date(),
    });
  }

  console.log("\n  SETUP 완료: 10개 사무실, 20명 기사");
}

// ═══════════════════════════════════════════════════
// 공유콜 헬퍼
// ═══════════════════════════════════════════════════

/**
 * 공유콜 생성 (OPEN)
 */
async function createSharedCall(phase, sourceOffice, fare) {
  const callId = nextCallId(phase);
  const ts = new Date(baseTime().getTime() + callCounter * 60000);

  await createDocument("shared_calls", callId, {
    status: "OPEN",
    phoneNumber: `010${String(callCounter).padStart(8, "0")}`,
    customerName: "",
    customerAddress: "",
    sourceProvinceId: PROVINCE,
    sourceCityId: CITY,
    sourceOfficeId: sourceOffice.id,
    targetProvinceId: PROVINCE,
    targetCityId: CITY,
    deviceName: "test-script",
    callType: "수신",
    timestamp: ts,
    timestampClient: ts.getTime(),
    expireAt: new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000),
    sharedTimestamp: ts,
    fare: fare,
    fare_set: fare,
    departure: "공유출발",
    destination: "공유도착",
    createdBy: "test-script",
  });

  return { callId, fare, ts };
}

/**
 * 공유콜 수임 (CLAIMED) — CF가 수임 사무실에 콜 복사
 */
async function claimSharedCall(callId, claimOffice, driverKey) {
  const driver = claimOffice.drivers[driverKey];

  await updateDocument(`shared_calls/${callId}`, {
    status: "CLAIMED",
    claimedOfficeId: claimOffice.id,
    claimedAt: new Date(),
    claimedDriverId: driver.id,
    claimedDriverAuthUid: driver.id,
    targetProvinceId: PROVINCE,
    targetCityId: CITY,
  });
}

/**
 * CF가 복사한 콜 대기 → 상태 전이 → COMPLETED
 */
async function waitAndCompleteSharedCall(callId, claimOffice, driverKey, fare) {
  const driver = claimOffice.drivers[driverKey];
  const callPath = `${claimOffice.path}/calls/${callId}`;

  // CF 복사 대기 (최대 10초)
  let copiedCall = null;
  for (let attempt = 0; attempt < 5; attempt++) {
    await sleep(2000);
    copiedCall = await getDocument(callPath);
    if (copiedCall) break;
  }

  if (!copiedCall) {
    failCount++;
    console.log(`  ✗ FAIL: CF가 ${callPath}에 콜 복사 안 됨 (10초 대기)`);
    return null;
  }
  passCount++;
  console.log(`  ✓ CF가 콜 복사 완료 (status=${copiedCall.status})`);

  // 상태 전이
  const ts = new Date();
  if (copiedCall.status === "WAITING") {
    await updateDocument(callPath, {
      status: "ASSIGNED",
      assignedDriverId: driver.id,
      assignedDriverName: driver.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: ts,
      updatedAt: ts,
    });
  }
  await updateDocument(callPath, { status: "ACCEPTED", updatedAt: new Date() });
  await updateDocument(callPath, { status: "IN_PROGRESS", updatedAt: new Date() });
  await updateDocument(callPath, {
    status: "AWAITING_SETTLEMENT", updatedAt: new Date(),
    finalFare: fare, fareFinal: fare,
    tripSummaryFinal: `공유콜 ${fare}원`,
    trip_summary: `공유콜 ${fare}원`,
  });
  await updateDocument(callPath, {
    status: "COMPLETED", completedAt: new Date(), updatedAt: new Date(),
    paymentMethod: "현금", cashReceived: fare, creditAmount: 0,
  });

  return { callId, driverKey, fare, paymentMethod: "현금", cashReceived: fare, creditAmount: 0, pointsUsed: 0 };
}

/**
 * 로컬 콜 생성→완료
 */
async function createLocalCall(phase, office, driverKey, fare) {
  const driver = office.drivers[driverKey];
  const callId = nextCallId(phase);
  const ts = new Date(baseTime().getTime() + callCounter * 60000);
  const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

  await createDocument(`${office.path}/calls`, callId, {
    status: "WAITING", phoneNumber: "", customerName: "", customerAddress: "",
    fare, fare_set: fare, pointsUsed: 0,
    timestamp: ts, timestampClient: ts.getTime(),
    fromCallManager: true, cityId: CITY, provinceId: PROVINCE, officeId: office.id,
    departure_set: "로컬출발", destination_set: "로컬도착",
    waypoints_set: "", createdBy: "test-script", expireAt,
  });

  const assignTs = new Date(ts.getTime() + 2000);
  await updateDocument(`${office.path}/calls/${callId}`, {
    status: "ASSIGNED", assignedDriverId: driver.id, assignedDriverName: driver.name,
    assignedDriverPhone: "01000000000", assignedTimestamp: assignTs, updatedAt: assignTs,
  });
  await updateDocument(`${office.path}/calls/${callId}`, { status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 5000) });
  await updateDocument(`${office.path}/calls/${callId}`, { status: "IN_PROGRESS", updatedAt: new Date(ts.getTime() + 30000) });
  await updateDocument(`${office.path}/calls/${callId}`, {
    status: "AWAITING_SETTLEMENT", updatedAt: new Date(ts.getTime() + 120000),
    finalFare: fare, fareFinal: fare,
    tripSummaryFinal: `로컬 ${fare}원`, trip_summary: `로컬 ${fare}원`,
  });
  await updateDocument(`${office.path}/calls/${callId}`, {
    status: "COMPLETED", completedAt: new Date(ts.getTime() + 180000),
    updatedAt: new Date(ts.getTime() + 180000),
    paymentMethod: "현금", cashReceived: fare, creditAmount: 0,
  });

  return { callId, driverKey, fare, paymentMethod: "현금", cashReceived: fare, creditAmount: 0, pointsUsed: 0 };
}

// ─── 정산 마감 헬퍼 ───
function driverDocPath(office, driverKey) {
  return `${office.path}/designated_drivers/${office.drivers[driverKey].id}`;
}

async function submitSettlement(office, driverKey, calls, depositRatio, originalCarryOver = 0) {
  const calc = calculateDriverSettlement(calls, depositRatio, originalCarryOver);
  await updateDocument(driverDocPath(office, driverKey), {
    dailySettlement: {
      date: TEST_DATE, status: "PENDING_CONFIRM",
      finalDeposit: calc.finalDeposit, realDeposit: calc.realDeposit,
      settlementDiff: calc.realDeposit - calc.finalDeposit + originalCarryOver,
      totalFare: calc.totalFare, totalCredit: calc.totalCredit,
      tripCount: calc.tripCount, submittedAt: new Date(),
      confirmedAt: null, confirmedBy: null,
      calculatedCarryOver: calc.calculatedCarryOver, originalCarryOver,
      originalTripCount: 0, originalTotalFare: 0, originalRealDeposit: 0,
    }
  });
  return calc;
}

async function confirmSettlement(office, driverKey, calculatedCarryOver) {
  await updateDocument(driverDocPath(office, driverKey), {
    "dailySettlement.status": "CONFIRMED",
    "dailySettlement.confirmedAt": new Date(),
    "dailySettlement.confirmedBy": ADMIN_ID,
    "carryOver.balance": calculatedCarryOver,
    "carryOver.status": calculatedCarryOver === 0 ? "SETTLED" : "PENDING",
    "carryOver.lastUpdatedAt": new Date(),
  });
}

async function transferAndReceive(office, driverKey, balance) {
  if (balance === 0) return;
  await updateDocument(driverDocPath(office, driverKey), {
    carryOver: {
      balance, todayAmount: balance, status: "TRANSFERRED",
      transferredAt: new Date(), transferredBy: ADMIN_ID, lastUpdatedAt: new Date(),
    }
  });
  await updateDocument(driverDocPath(office, driverKey), {
    "carryOver.balance": 0, "carryOver.status": "SETTLED",
    "carryOver.transferredAt": null, "carryOver.transferredBy": null,
    "carryOver.lastUpdatedAt": new Date(),
  });
}

async function fullSettlementFlow(office, driverKey, calls, depositRatio) {
  const calc = await submitSettlement(office, driverKey, calls, depositRatio);
  await confirmSettlement(office, driverKey, calc.calculatedCarryOver);
  await transferAndReceive(office, driverKey, calc.calculatedCarryOver);
  return calc;
}

async function resetDriverSettlement(office, driverKey) {
  await updateDocument(driverDocPath(office, driverKey), {
    dailySettlement: {
      date: "", status: "WORKING", finalDeposit: 0, realDeposit: 0,
      settlementDiff: 0, totalFare: 0, totalCredit: 0, tripCount: 0,
      submittedAt: null, confirmedAt: null, confirmedBy: null,
      calculatedCarryOver: 0, originalCarryOver: 0,
      originalTripCount: 0, originalTotalFare: 0, originalRealDeposit: 0,
    },
    "carryOver.balance": 0,
    "carryOver.status": "SETTLED",
    "carryOver.lastUpdatedAt": new Date(),
  });
}

// ═══════════════════════════════════════════════════
// PHASE 1: 기본 공유콜 흐름 (10건)
// ═══════════════════════════════════════════════════
async function phase1() {
  console.log("\n" + "=".repeat(60));
  console.log("PHASE 1: 기본 공유콜 흐름 (office_0 → office_1, 10건)");
  console.log("=".repeat(60));

  const src = OFFICES[0];
  const dst = OFFICES[1];
  const completedCalls = [];

  for (let i = 0; i < 10; i++) {
    const fare = 20000 + (i * 2000);
    const driverKey = i % 2 === 0 ? "A" : "B";
    console.log(`\n  [${i + 1}/10] 공유콜 ₩${fare} (${src.name} → ${dst.name}, ${dst.drivers[driverKey].name})...`);

    // 1. 공유콜 생성 (OPEN)
    const { callId } = await createSharedCall(1, src, fare);

    // 2. 수임 (CLAIMED) → CF 트리거
    await claimSharedCall(callId, dst, driverKey);

    // 3. CF 복사 대기 → 상태 전이 → COMPLETED
    const result = await waitAndCompleteSharedCall(callId, dst, driverKey, fare);
    if (result) completedCalls.push(result);
  }

  console.log(`\n  CF 정산 트리거 대기 (15초)...`);
  await sleep(15000);

  // 검증: dst(office_1) 정산에 포함
  console.log("\n--- Phase 1 검증 ---");
  const dstSession = await getDocument(`${dst.path}/settlementSessions/${TEST_DATE}`);
  assert(dstSession !== null, `CP1: office_1 정산 세션 생성됨`);
  if (dstSession) {
    assertEqual(dstSession.calls?.length, 10, `CP2: office_1 정산 콜 수 = 10 (actual: ${dstSession.calls?.length})`);
  }

  // 검증: src(office_0) 정산에 미포함
  const srcSession = await getDocument(`${src.path}/settlementSessions/${TEST_DATE}`);
  const srcCalls = srcSession?.calls?.length || 0;
  assertEqual(srcCalls, 0, `CP3: office_0 정산 콜 수 = 0 (actual: ${srcCalls})`);

  return completedCalls;
}

// ═══════════════════════════════════════════════════
// PHASE 2: 다방향 공유콜 교차 (30건, 순환)
// ═══════════════════════════════════════════════════
async function phase2() {
  console.log("\n" + "=".repeat(60));
  console.log("PHASE 2: 다방향 공유콜 교차 (10개 사무실 순환, 30건)");
  console.log("=".repeat(60));

  const officeSharedCalls = {}; // officeIndex → [ completedCalls ]
  for (let i = 0; i < 10; i++) officeSharedCalls[i] = [];

  for (let srcIdx = 0; srcIdx < 10; srcIdx++) {
    const dstIdx = (srcIdx + 1) % 10;
    const src = OFFICES[srcIdx];
    const dst = OFFICES[dstIdx];

    console.log(`\n  ${src.name}(${srcIdx}) → ${dst.name}(${dstIdx}): 3건`);

    for (let j = 0; j < 3; j++) {
      const fare = 25000 + (srcIdx * 1000) + (j * 500);
      const driverKey = j % 2 === 0 ? "A" : "B";
      const { callId } = await createSharedCall(2, src, fare);
      await claimSharedCall(callId, dst, driverKey);
      const result = await waitAndCompleteSharedCall(callId, dst, driverKey, fare);
      if (result) officeSharedCalls[dstIdx].push(result);
    }
  }

  console.log(`\n  CF 정산 트리거 대기 (20초)...`);
  await sleep(20000);

  // 검증: 각 사무실 정산에 수임 콜만 포함
  console.log("\n--- Phase 2 검증 ---");
  for (let i = 0; i < 10; i++) {
    const session = await getDocument(`${OFFICES[i].path}/settlementSessions/${TEST_DATE}`);
    const expected = officeSharedCalls[i].length + (i === 1 ? 10 : 0); // office_1은 Phase1 10건 포함
    const actual = session?.calls?.length || 0;
    assertEqual(actual, expected, `  office_${i} 정산 콜 = ${expected} (actual: ${actual})`);
  }

  return officeSharedCalls;
}

// ═══════════════════════════════════════════════════
// PHASE 3: 로컬 콜 + 공유콜 혼합 정산
// ═══════════════════════════════════════════════════
async function phase3() {
  console.log("\n" + "=".repeat(60));
  console.log("PHASE 3: 로컬 콜 5건/사무실 → 혼합 정산 검증");
  console.log("=".repeat(60));

  for (let i = 0; i < 10; i++) {
    const office = OFFICES[i];
    console.log(`\n  ${office.name}: 로컬 5건 생성...`);

    for (let j = 0; j < 5; j++) {
      const fare = 20000 + (j * 3000);
      const driverKey = j % 2 === 0 ? "A" : "B";
      await createLocalCall(3, office, driverKey, fare);
    }
  }

  console.log(`\n  CF 정산 트리거 대기 (20초)...`);
  await sleep(20000);

  // 검증: 각 사무실 정산 = 기존 수임콜 + 로컬 5건
  console.log("\n--- Phase 3 검증 ---");
  for (let i = 0; i < 10; i++) {
    const session = await getDocument(`${OFFICES[i].path}/settlementSessions/${TEST_DATE}`);
    if (!session) {
      failCount++;
      console.log(`  ✗ FAIL: office_${i} 정산 세션 없음`);
      continue;
    }

    // 정산 공식 검증
    const ratio = session.metadata?.depositRatio || 60;
    const totalFare = session.totals?.totalFare || 0;
    const expectedDeposit = Math.floor(totalFare * ratio / 100);
    assertEqual(session.totals?.totalDeposit, expectedDeposit, `  office_${i} deposit 공식 일치`);
    assertEqual(session.totals?.totalDriverShare, totalFare - expectedDeposit, `  office_${i} driverShare 공식 일치`);
  }
}

// ═══════════════════════════════════════════════════
// PHASE 4: 전체 마감
// ═══════════════════════════════════════════════════
async function phase4() {
  console.log("\n" + "=".repeat(60));
  console.log("PHASE 4: 10개 사무실 × 2명 기사 = 20명 전체 마감");
  console.log("=".repeat(60));

  for (let i = 0; i < 10; i++) {
    const office = OFFICES[i];
    const session = await getDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
    if (!session) {
      console.log(`  office_${i}: 정산 세션 없음 — 건너뜀`);
      continue;
    }

    const depositRatio = session.metadata?.depositRatio || 60;
    const sessionCalls = session.calls || [];

    console.log(`\n  ${office.name}: ${sessionCalls.length}건 마감...`);

    for (const key of ["A", "B"]) {
      const driver = office.drivers[key];
      // 이 기사의 콜만 필터
      const driverCalls = sessionCalls
        .filter(c => c.driverId === driver.id)
        .map(c => ({ fare: c.fare || 0, cashReceived: c.cashReceived || 0, creditAmount: c.creditAmount || 0, pointsUsed: c.pointsUsed || 0 }));

      if (driverCalls.length === 0) {
        console.log(`    ${driver.name}: 콜 없음 — 건너뜀`);
        continue;
      }

      await resetDriverSettlement(office, key);
      const calc = await fullSettlementFlow(office, key, driverCalls, depositRatio);

      const doc = await getDocument(driverDocPath(office, key));
      assertEqual(doc?.carryOver?.balance, 0, `    ${driver.name} balance = 0`);
      console.log(`    ${driver.name}: ${driverCalls.length}건, 매출=${calc.totalFare}`);
    }
  }
}

// ═══════════════════════════════════════════════════
// PHASE 5: 공유콜 취소 시나리오
// ═══════════════════════════════════════════════════
async function phase5() {
  console.log("\n" + "=".repeat(60));
  console.log("PHASE 5: 공유콜 취소 시나리오 (15건)");
  console.log("=".repeat(60));

  // 5a: 수임 후 기사 취소 (5건)
  console.log("\n--- 5a: 수임 후 기사 취소 (5건) ---");
  const cancelledByDriver = [];
  for (let i = 0; i < 5; i++) {
    const { callId } = await createSharedCall(5, OFFICES[0], 20000);
    await claimSharedCall(callId, OFFICES[1], "A");

    // CF 복사 대기
    const callPath = `${OFFICES[1].path}/calls/${callId}`;
    let copiedCall = null;
    for (let attempt = 0; attempt < 5; attempt++) {
      await sleep(2000);
      copiedCall = await getDocument(callPath);
      if (copiedCall) break;
    }
    if (!copiedCall) {
      console.log(`  ✗ CF 복사 안 됨: ${callId}`);
      continue;
    }

    // ASSIGNED → ACCEPTED → 기사 취소
    if (copiedCall.status === "WAITING") {
      await updateDocument(callPath, {
        status: "ASSIGNED", assignedDriverId: OFFICES[1].drivers.A.id,
        assignedDriverName: OFFICES[1].drivers.A.name,
        assignedDriverPhone: "01000000000",
        assignedTimestamp: new Date(), updatedAt: new Date(),
      });
    }
    await updateDocument(callPath, { status: "ACCEPTED", updatedAt: new Date() });
    await updateDocument(callPath, { status: "CANCELLED_BY_DRIVER", updatedAt: new Date() });
    cancelledByDriver.push(callId);
  }

  await sleep(5000);

  // 검증: 취소된 콜이 office_1 정산에 추가되지 않았는지
  // (Phase 1~3에서 이미 정산 세션이 있으므로, 취소 콜이 추가되지 않았는지 확인)
  const session1 = await getDocument(`${OFFICES[1].path}/settlementSessions/${TEST_DATE}`);
  if (session1) {
    for (const cid of cancelledByDriver) {
      const found = session1.calls?.find(c => c.callId === cid);
      assert(!found, `  5a CP: 기사취소 콜 ${cid} 정산 미포함`);
    }
  }

  // 5b: 수임 전 발신 취소 (5건)
  console.log("\n--- 5b: 수임 전 발신 사무실 취소 (5건) ---");
  for (let i = 0; i < 5; i++) {
    const { callId } = await createSharedCall(5, OFFICES[2], 18000);
    // 바로 취소
    await updateDocument(`shared_calls/${callId}`, {
      status: "CANCELED", updatedAt: new Date(),
    });
    const sc = await getDocument(`shared_calls/${callId}`);
    assertEqual(sc?.status, "CANCELED", `  5b CP: ${callId} status = CANCELED`);
  }

  // 5c: 수임 후 관리자 취소 (5건)
  console.log("\n--- 5c: 수임 후 관리자 취소 (5건) ---");
  const cancelledByAdmin = [];
  for (let i = 0; i < 5; i++) {
    const { callId } = await createSharedCall(5, OFFICES[3], 22000);
    await claimSharedCall(callId, OFFICES[4], "B");

    const callPath = `${OFFICES[4].path}/calls/${callId}`;
    let copiedCall = null;
    for (let attempt = 0; attempt < 5; attempt++) {
      await sleep(2000);
      copiedCall = await getDocument(callPath);
      if (copiedCall) break;
    }
    if (!copiedCall) {
      console.log(`  ✗ CF 복사 안 됨: ${callId}`);
      continue;
    }

    // ASSIGNED 상태에서 관리자 취소
    await updateDocument(callPath, { status: "CANCELED", updatedAt: new Date() });
    cancelledByAdmin.push(callId);
  }

  await sleep(5000);

  const session4 = await getDocument(`${OFFICES[4].path}/settlementSessions/${TEST_DATE}`);
  if (session4) {
    for (const cid of cancelledByAdmin) {
      const found = session4.calls?.find(c => c.callId === cid);
      assert(!found, `  5c CP: 관리자취소 콜 ${cid} office_4 정산 미포함`);
    }
  }
}

// ═══════════════════════════════════════════════════
// PHASE 6: 공유콜 포함 마감 거절/재제출
// ═══════════════════════════════════════════════════
async function phase6() {
  console.log("\n" + "=".repeat(60));
  console.log("PHASE 6: 공유콜 포함 정산 거절/재제출");
  console.log("=".repeat(60));

  // 6a: office_5에 로컬 3건 + 수임 공유콜 2건 → 거절 → 재제출
  console.log("\n--- 6a: office_5 거절→재제출 ---");
  const o5 = OFFICES[5];
  const o5Calls = [];

  // 로컬 3건
  for (let i = 0; i < 3; i++) {
    const result = await createLocalCall(6, o5, "A", 20000 + i * 5000);
    o5Calls.push(result);
  }

  // 수임 공유콜 2건
  for (let i = 0; i < 2; i++) {
    const { callId } = await createSharedCall(6, OFFICES[8], 30000);
    await claimSharedCall(callId, o5, "A");
    const result = await waitAndCompleteSharedCall(callId, o5, "A", 30000);
    if (result) o5Calls.push(result);
  }

  await sleep(15000);

  // 마감 → 거절 → 재제출 → 확인
  await resetDriverSettlement(o5, "A");
  const calc1 = await submitSettlement(o5, "A", o5Calls, 60);
  assertEqual((await getDocument(driverDocPath(o5, "A")))?.dailySettlement?.status, "PENDING_CONFIRM", `  6a CP: PENDING_CONFIRM`);

  // 거절
  await updateDocument(driverDocPath(o5, "A"), { "dailySettlement.status": "REJECTED" });
  assertEqual((await getDocument(driverDocPath(o5, "A")))?.dailySettlement?.status, "REJECTED", `  6a CP: REJECTED`);

  // 재제출 (같은 금액)
  const calc2 = await submitSettlement(o5, "A", o5Calls, 60);
  assertEqual(calc2.totalFare, calc1.totalFare, `  6a CP: 재제출 totalFare 동일 (${calc1.totalFare})`);
  assertEqual((await getDocument(driverDocPath(o5, "A")))?.dailySettlement?.status, "PENDING_CONFIRM", `  6a CP: 재제출 PENDING_CONFIRM`);

  // 확인
  await confirmSettlement(o5, "A", calc2.calculatedCarryOver);
  await transferAndReceive(o5, "A", calc2.calculatedCarryOver);
  assertEqual((await getDocument(driverDocPath(o5, "A")))?.carryOver?.balance, 0, `  6a CP: balance = 0`);

  // 6b: office_6 거절이 office_7에 무영향
  console.log("\n--- 6b: office_6 거절 → office_7 무영향 ---");
  const o6 = OFFICES[6];
  const o7 = OFFICES[7];

  // office_6, office_7 각각 로컬 3건 + 수임 2건
  for (const [office, srcOffice] of [[o6, OFFICES[9]], [o7, OFFICES[0]]]) {
    const calls = [];
    for (let i = 0; i < 3; i++) {
      calls.push(await createLocalCall(6, office, "A", 22000));
    }
    for (let i = 0; i < 2; i++) {
      const { callId } = await createSharedCall(6, srcOffice, 28000);
      await claimSharedCall(callId, office, "A");
      const result = await waitAndCompleteSharedCall(callId, office, "A", 28000);
      if (result) calls.push(result);
    }
    office._phase6Calls = calls;
  }

  await sleep(15000);

  // office_6: 마감 → 거절 (여기서 멈춤)
  await resetDriverSettlement(o6, "A");
  await submitSettlement(o6, "A", o6._phase6Calls, 60);
  await updateDocument(driverDocPath(o6, "A"), { "dailySettlement.status": "REJECTED" });
  const o6Doc = await getDocument(driverDocPath(o6, "A"));
  assertEqual(o6Doc?.dailySettlement?.status, "REJECTED", `  6b CP: office_6 REJECTED`);

  // office_7: 정상 마감 → 영향 없음
  await resetDriverSettlement(o7, "A");
  const o7Calc = await fullSettlementFlow(o7, "A", o7._phase6Calls, 60);
  const o7Doc = await getDocument(driverDocPath(o7, "A"));
  assertEqual(o7Doc?.carryOver?.balance, 0, `  6b CP: office_7 balance = 0 (office_6 거절 무영향)`);
  assertEqual(o7Doc?.dailySettlement?.status, "CONFIRMED", `  6b CP: office_7 CONFIRMED`);
}

// ═══════════════════════════════════════════════════
// PHASE 7: 공유콜 경합
// ═══════════════════════════════════════════════════
async function phase7() {
  console.log("\n" + "=".repeat(60));
  console.log("PHASE 7: 공유콜 경합 (3개 사무실 동시 수임 시도)");
  console.log("=".repeat(60));

  for (let i = 0; i < 5; i++) {
    const { callId } = await createSharedCall(7, OFFICES[0], 35000);
    console.log(`\n  [${i + 1}/5] ${callId}: office_1, office_2, office_3 동시 수임 시도...`);

    // 3개 사무실이 동시에 CLAIMED 시도
    const results = await Promise.allSettled([
      claimSharedCall(callId, OFFICES[1], "A").then(() => 1),
      claimSharedCall(callId, OFFICES[2], "A").then(() => 2),
      claimSharedCall(callId, OFFICES[3], "A").then(() => 3),
    ]);

    // 최종 상태 확인
    const sc = await getDocument(`shared_calls/${callId}`);
    assertEqual(sc?.status, "CLAIMED", `  CP: ${callId} status = CLAIMED`);
    console.log(`    수임 사무실: ${sc?.claimedOfficeId}`);
  }
}

// ═══════════════════════════════════════════════════
// Cleanup
// ═══════════════════════════════════════════════════
async function cleanup() {
  console.log("\n정리 중...");

  // 테스트 콜 삭제 (모든 사무실)
  for (const office of OFFICES) {
    const calls = await listDocuments(`${office.path}/calls`);
    const testCalls = calls.filter(c => c.id.startsWith(TEST_PREFIX));
    if (testCalls.length > 0) {
      console.log(`  ${office.name}: ${testCalls.length}건 콜 삭제...`);
      for (const call of testCalls) await deleteDocument(`${office.path}/calls/${call.id}`);
    }

    // 정산 세션 삭제
    await deleteDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
  }

  // shared_calls 삭제
  const sharedCalls = await listDocuments("shared_calls");
  const testShared = sharedCalls.filter(c => c.id.startsWith(TEST_PREFIX));
  if (testShared.length > 0) {
    console.log(`  공유콜 ${testShared.length}건 삭제...`);
    for (const call of testShared) await deleteDocument(`shared_calls/${call.id}`);
  }

  // 테스트 기사 삭제 (office_1~9)
  for (let i = 1; i <= 9; i++) {
    const office = OFFICES[i];
    for (const key of ["A", "B"]) {
      await deleteDocument(`${office.path}/designated_drivers/${office.drivers[key].id}`);
    }
  }

  // 테스트 사무실 삭제 (office_1~9)
  for (let i = 1; i <= 9; i++) {
    await deleteDocument(`provinces/${PROVINCE}/cities/${CITY}/offices/${OFFICES[i].id}`);
    console.log(`  ${OFFICES[i].name} 삭제`);
  }

  // office_0 기사 원복
  for (const key of ["A", "B"]) {
    await resetDriverSettlement(OFFICES[0], key);
  }

  console.log("  정리 완료");
}

// ─── Status ───
async function statusCheck() {
  console.log("\n현재 상태 확인...\n");

  for (const office of OFFICES) {
    const exists = await getDocument(office.path);
    const session = await getDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
    const callCount = session?.calls?.length || 0;
    const fare = session?.totals?.totalFare || 0;
    console.log(`  ${office.name} (${office.id}): ${exists ? "존재" : "없음"}, 정산=${callCount}건 ₩${fare.toLocaleString()}`);
  }

  const shared = await listDocuments("shared_calls");
  const testShared = shared.filter(c => c.id.startsWith(TEST_PREFIX));
  console.log(`\n  테스트 공유콜: ${testShared.length}건`);
}

// ─── Main ───
(async () => {
  const arg = process.argv[2];

  console.log("╔════════════════════════════════════════════════════════╗");
  console.log("║  10개 사무실 공유콜 + 크로스 정산 시뮬레이션           ║");
  console.log("╚════════════════════════════════════════════════════════╝");

  try {
    const phases = { setup, phase1, phase2, phase3, phase4, phase5, phase6, phase7 };

    if (arg === "--cleanup") { await cleanup(); return; }
    if (arg === "--status") { await statusCheck(); return; }

    if (arg === "all") {
      await setup();
      await phase1();
      await phase2();
      await phase3();
      await phase4();
      await phase5();
      await phase6();
      await phase7();
    } else if (phases[arg]) {
      await phases[arg]();
    } else {
      console.log("Usage: node scripts/multi-office-test.js [setup|phase1~7|all|--cleanup|--status]");
      return;
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
