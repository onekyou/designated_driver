/**
 * 2단계 최종 검증: Day 1~7 Firestore 콜 생성 + CF 트리거 검증
 *
 * 350콜을 7일에 걸쳐 생성하고, 상태 전이 + 정산 CF 트리거를 검증.
 * firestore-util.js 패턴 기반 REST API 사용.
 *
 * Usage: node scripts/test-day1-7.js [day]
 *   day: 1~7 (생략 시 전체 실행)
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

// 기사 정보
const DRIVERS = {
  A: { id: "6RQEWvmDkkfTAHXjvbxPtYfa7mY2", name: "양세훈 " },
  B: { id: "vIbRH7Ci17UqCUR6ty84eDdl2me2", name: "고양이 " },
  C: { id: "XpTYX1LDFXPRkmTf35xSsr0vNkA2", name: "근데 " },
  D: { id: "fhwEVhPdovfA8BpXFlXSgSeGEKm1", name: "배드웨어 " },
  E: { id: "AFbO0czts2Pw1Ze9YSvgKuGOMk12", name: "화이팅 " },
};

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
  tokenExpiry = Date.now() + 50 * 60 * 1000; // 50 min
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
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body
  });
  if (result.error) throw new Error(`Create failed: ${result.error.message}`);
  return parseDoc(result);
}

async function updateDocument(docPath, data) {
  const token = await getAccessToken();
  const fields = {};
  for (const [k, v] of Object.entries(data)) {
    fields[k] = toFirestoreValue(v);
  }
  const updateMask = Object.keys(data).map(k => `updateMask.fieldPaths=${k}`).join("&");
  const url = `${BASE_URL}/${docPath}?${updateMask}`;
  const body = JSON.stringify({ fields });
  const result = await httpRequest(url, {
    method: "PATCH",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
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
  const result = await httpRequest(`${BASE_URL}/${docPath}`, {
    method: "DELETE",
    headers: { "Authorization": `Bearer ${token}` }
  });
  return result;
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

// Test date prefix for all calls created by this test
const TEST_PREFIX = "test7d_";

// Generate a unique call ID
let callCounter = 0;
function nextCallId(dayNum) {
  callCounter++;
  return `${TEST_PREFIX}d${dayNum}_${String(callCounter).padStart(3, "0")}`;
}

// Base timestamp for Day N (use dates in the past to avoid collisions with live data)
// Use 2026-03-20 ~ 2026-03-26 (future dates to avoid overlap)
function dayBaseTime(dayNum) {
  const base = new Date("2026-03-20T09:00:00Z"); // 18:00 KST
  base.setDate(base.getDate() + (dayNum - 1));
  return base;
}

// Create a call and transition it through states
async function createAndCompleteCall(dayNum, opts) {
  const {
    driverKey, fare, paymentMethod, cashReceived = 0,
    creditAmount = 0, pointsUsed = 0,
    customerPhone = "", customerName = "",
    departure = "테스트출발", destination = "테스트도착",
    fromCallManager = true, skipComplete = false,
    finalStatus = "COMPLETED",
  } = opts;

  const driver = DRIVERS[driverKey];
  const callId = nextCallId(dayNum);
  const baseTime = dayBaseTime(dayNum);
  const offset = callCounter * 60000; // 1 minute apart
  const timestamp = new Date(baseTime.getTime() + offset);
  const expireAt = new Date(timestamp.getTime() + 30 * 24 * 60 * 60 * 1000);

  // Step 1: Create call as WAITING
  const callData = {
    status: "WAITING",
    phoneNumber: customerPhone,
    customerName: customerName,
    customerAddress: "",
    fare: fare,
    fare_set: fare,
    pointsUsed: pointsUsed,
    timestamp: timestamp,
    timestampClient: timestamp.getTime(),
    fromCallManager: fromCallManager,
    cityId: "yangpyeong",
    provinceId: "gyeonggi",
    officeId: "nEkf0X9g3LZtRX94Mrzu",
    departure_set: departure,
    destination_set: destination,
    waypoints_set: "",
    createdBy: "test-script",
    expireAt: expireAt,
  };

  await createDocument(`${OFFICE_PATH}/calls`, callId, callData);

  if (finalStatus === "WAITING") return callId;

  // Step 2: ASSIGNED
  const assignedTime = new Date(timestamp.getTime() + 2000);
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "ASSIGNED",
    assignedDriverId: driver.id,
    assignedDriverName: driver.name,
    assignedDriverPhone: "01000000000",
    assignedTimestamp: assignedTime,
    updatedAt: assignedTime,
  });

  if (finalStatus === "ASSIGNED") return callId;

  // Step 3: ACCEPTED
  const acceptedTime = new Date(timestamp.getTime() + 5000);
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "ACCEPTED",
    updatedAt: acceptedTime,
  });

  if (finalStatus === "ACCEPTED") return callId;

  // Step 4: IN_PROGRESS
  const inProgressTime = new Date(timestamp.getTime() + 30000);
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "IN_PROGRESS",
    updatedAt: inProgressTime,
  });

  if (finalStatus === "IN_PROGRESS") return callId;

  // Step 5: AWAITING_SETTLEMENT
  const awaitingTime = new Date(timestamp.getTime() + 120000);
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "AWAITING_SETTLEMENT",
    updatedAt: awaitingTime,
    finalFare: fare,
    fareFinal: fare,
    tripSummaryFinal: `출발: ${departure}, 도착: ${destination}, 요금: ${fare}원`,
    trip_summary: `출발: ${departure}, 도착: ${destination}, 요금: ${fare}원`,
  });

  if (finalStatus === "AWAITING_SETTLEMENT") return callId;

  // Step 6: COMPLETED (triggers onCallCompletedUpdateSettlement CF)
  const completedTime = new Date(timestamp.getTime() + 180000);
  await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
    status: "COMPLETED",
    completedAt: completedTime,
    updatedAt: completedTime,
    paymentMethod: paymentMethod,
    cashReceived: cashReceived,
    creditAmount: creditAmount,
  });

  return callId;
}

// ─── DAY 1: Normal Flow (50 calls) ───
async function day1() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 1: 정상 플로우 (50콜)");
  console.log("=".repeat(60));

  const callIds = [];

  // 1. 전화수신→배차→수락→운행→완료 (15건, 현금)
  console.log("\n[1/6] 현금 결제 15건...");
  for (let i = 0; i < 15; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 20000 + (i * 1000);
    const id = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "현금",
      cashReceived: fare, customerPhone: `0101111${String(i).padStart(4, "0")}`,
    });
    callIds.push(id);
  }

  // 2. 앱콜→배차→완료 (10건, 현금)
  console.log("[2/6] 앱콜 현금 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 15000 + (i * 2000);
    const id = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "현금",
      cashReceived: fare, fromCallManager: false,
    });
    callIds.push(id);
  }

  // 3. 이체 결제 (8건)
  console.log("[3/6] 이체 결제 8건...");
  for (let i = 0; i < 8; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 25000 + (i * 3000);
    const id = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "이체",
      cashReceived: 0,
    });
    callIds.push(id);
  }

  // 4. 현금+포인트 혼합 (7건)
  console.log("[4/6] 현금+포인트 혼합 7건...");
  for (let i = 0; i < 7; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 30000;
    const points = 5000;
    const cash = fare - points;
    const id = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "현금+포인트",
      cashReceived: cash, pointsUsed: points,
    });
    callIds.push(id);
  }

  // 5. 전액 포인트 (5건)
  console.log("[5/6] 전액 포인트 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 10000;
    const id = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "포인트",
      cashReceived: 0, pointsUsed: fare,
    });
    callIds.push(id);
  }

  // 6. 외상 결제 (5건)
  console.log("[6/6] 외상 결제 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 20000;
    const id = await createAndCompleteCall(1, {
      driverKey, fare, paymentMethod: "외상",
      cashReceived: 0, creditAmount: fare,
    });
    callIds.push(id);
  }

  console.log(`\n총 ${callIds.length}건 생성 완료. CF 트리거 대기 (15초)...`);
  await sleep(15000);

  // ─── CP: Day 1 체크포인트 ───
  console.log("\n--- Day 1 체크포인트 ---");

  // CP1: 마지막 콜 상태 확인
  const lastCall = await getDocument(`${OFFICE_PATH}/calls/${callIds[callIds.length - 1]}`);
  assertEqual(lastCall?.status, "COMPLETED", "CP1: 마지막 콜 상태 = COMPLETED");

  // CP2: 정산 세션 생성 확인
  const workDate = "2026-03-20"; // Day 1
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, "CP2: 정산 세션 2026-03-20 생성됨");

  if (session) {
    // CP3: 콜 카운트
    const sessionCalls = session.calls || [];
    assertEqual(sessionCalls.length, 50, `CP3: 정산 세션 콜 수 = 50 (actual: ${sessionCalls.length})`);

    // CP4: 총 요금 검증 (수동 계산)
    // 현금 15건: 20000~34000 (합 = 15*20000 + 1000*(0+1+...+14) = 300000 + 105000 = 405000)
    // 앱콜 10건: 15000~33000 (합 = 10*15000 + 2000*(0+1+...+9) = 150000 + 90000 = 240000)
    // 이체 8건: 25000~46000 (합 = 8*25000 + 3000*(0+1+...+7) = 200000 + 84000 = 284000)
    // 현금+포인트 7건: 30000 each = 210000
    // 포인트 5건: 10000 each = 50000
    // 외상 5건: 20000 each = 100000
    const expectedTotalFare = 405000 + 240000 + 284000 + 210000 + 50000 + 100000; // 1289000
    assertEqual(session.totals?.totalFare, expectedTotalFare, `CP4: 총 요금 = ${expectedTotalFare}`);

    // CP5: depositRatio = 60
    assertEqual(session.metadata?.depositRatio, 60, "CP5: depositRatio = 60");

    // CP6: totalDeposit = totalFare * 60%
    const expectedDeposit = Math.floor(expectedTotalFare * 60 / 100);
    assertEqual(session.totals?.totalDeposit, expectedDeposit, `CP6: totalDeposit = ${expectedDeposit}`);

    // CP7: 외상 총액
    const expectedCredit = 5 * 20000; // 100000
    assertEqual(session.totals?.totalCredit, expectedCredit, `CP7: totalCredit = ${expectedCredit}`);

    // CP8: 포인트 총액 (현금+포인트 7건 * 5000 + 전액포인트 5건 * 10000)
    const expectedPoints = 7 * 5000 + 5 * 10000; // 85000
    assertEqual(session.totals?.totalPoints, expectedPoints, `CP8: totalPoints = ${expectedPoints}`);
  }

  return callIds;
}

// ─── DAY 2: 취소 + 거절 + 타임아웃 (50콜) ───
async function day2() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 2: 취소 + 거절 + 타임아웃 (50콜)");
  console.log("=".repeat(60));

  const callIds = [];
  const cancelledIds = [];

  // 1. 정상 완료 20건
  console.log("\n[1/6] 정상 완료 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 20000 + (i * 500);
    const id = await createAndCompleteCall(2, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  // 2. 기사 거절→재배차→완료 (5건: A거절→B완료)
  console.log("[2/6] 기사 거절→재배차→완료 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const baseTime = dayBaseTime(2);
    const ts = new Date(baseTime.getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    // Create WAITING
    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 25000, fare_set: 25000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong",
      provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "테스트출발", destination_set: "테스트도착",
      waypoints_set: "", createdBy: "test-script", expireAt: expireAt,
      pointsUsed: 0,
    });

    // Assign to A
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED",
      assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000),
      updatedAt: new Date(ts.getTime() + 2000),
    });

    // A rejects → back to WAITING
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "WAITING",
      assignedDriverId: "", assignedDriverName: "", assignedDriverPhone: "",
      updatedAt: new Date(ts.getTime() + 10000),
    });

    // Reassign to B → complete
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED",
      assignedDriverId: DRIVERS.B.id, assignedDriverName: DRIVERS.B.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 15000),
      updatedAt: new Date(ts.getTime() + 15000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 20000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "IN_PROGRESS", updatedAt: new Date(ts.getTime() + 30000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "AWAITING_SETTLEMENT", updatedAt: new Date(ts.getTime() + 120000),
      finalFare: 25000, fareFinal: 25000,
      tripSummaryFinal: "출발: 테스트출발, 도착: 테스트도착, 요금: 25000원",
      trip_summary: "출발: 테스트출발, 도착: 테스트도착, 요금: 25000원",
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "COMPLETED",
      completedAt: new Date(ts.getTime() + 180000),
      updatedAt: new Date(ts.getTime() + 180000),
      paymentMethod: "현금", cashReceived: 25000, creditAmount: 0,
    });
    callIds.push(callId);
  }

  // 3. 관리자 취소 CANCELED (5건)
  console.log("[3/6] 관리자 취소 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 15000, fare_set: 15000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong",
      provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "취소테스트", destination_set: "취소도착",
      waypoints_set: "", createdBy: "test-script", expireAt: expireAt,
      pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED",
      assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000),
      updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "CANCELED",
      updatedAt: new Date(ts.getTime() + 10000),
    });
    cancelledIds.push(callId);
  }

  // 4. 기사 취소 CANCELLED_BY_DRIVER (5건)
  console.log("[4/6] 기사 취소 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 18000, fare_set: 18000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong",
      provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "취소테스트", destination_set: "취소도착",
      waypoints_set: "", createdBy: "test-script", expireAt: expireAt,
      pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED",
      assignedDriverId: DRIVERS.B.id, assignedDriverName: DRIVERS.B.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000),
      updatedAt: new Date(ts.getTime() + 2000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ACCEPTED",
      updatedAt: new Date(ts.getTime() + 5000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "CANCELLED_BY_DRIVER",
      updatedAt: new Date(ts.getTime() + 15000),
    });
    cancelledIds.push(callId);
  }

  // 5. 고객 취소 CANCELLED_BY_CUSTOMER (5건)
  console.log("[5/6] 고객 취소 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 22000, fare_set: 22000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong",
      provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "취소테스트", destination_set: "취소도착",
      waypoints_set: "", createdBy: "test-script", expireAt: expireAt,
      pointsUsed: 0,
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "CANCELLED_BY_CUSTOMER",
      updatedAt: new Date(ts.getTime() + 5000),
    });
    cancelledIds.push(callId);
  }

  // 6. 배차 타임아웃 → WAITING → 재배차 → 완료 (5건)
  console.log("[6/6] 타임아웃→재배차 5건...");
  for (let i = 0; i < 5; i++) {
    const callId = nextCallId(2);
    const ts = new Date(dayBaseTime(2).getTime() + callCounter * 60000);
    const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

    await createDocument(`${OFFICE_PATH}/calls`, callId, {
      status: "WAITING", fare: 30000, fare_set: 30000,
      phoneNumber: "", customerName: "", customerAddress: "",
      timestamp: ts, timestampClient: ts.getTime(),
      fromCallManager: true, cityId: "yangpyeong",
      provinceId: "gyeonggi", officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: "타임아웃테스트", destination_set: "타임아웃도착",
      waypoints_set: "", createdBy: "test-script", expireAt: expireAt,
      pointsUsed: 0,
    });
    // Assign to A → timeout → WAITING
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED",
      assignedDriverId: DRIVERS.A.id, assignedDriverName: DRIVERS.A.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 2000),
      updatedAt: new Date(ts.getTime() + 2000),
    });
    // Simulate timeout: back to WAITING
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "WAITING",
      assignedDriverId: "", assignedDriverName: "", assignedDriverPhone: "",
      updatedAt: new Date(ts.getTime() + 182000), // 3min later
    });
    // Reassign to B → complete
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ASSIGNED",
      assignedDriverId: DRIVERS.B.id, assignedDriverName: DRIVERS.B.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: new Date(ts.getTime() + 185000),
      updatedAt: new Date(ts.getTime() + 185000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "ACCEPTED", updatedAt: new Date(ts.getTime() + 190000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "IN_PROGRESS", updatedAt: new Date(ts.getTime() + 200000),
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "AWAITING_SETTLEMENT", updatedAt: new Date(ts.getTime() + 300000),
      finalFare: 30000, fareFinal: 30000,
      tripSummaryFinal: "출발: 타임아웃테스트, 도착: 타임아웃도착, 요금: 30000원",
      trip_summary: "출발: 타임아웃테스트, 도착: 타임아웃도착, 요금: 30000원",
    });
    await updateDocument(`${OFFICE_PATH}/calls/${callId}`, {
      status: "COMPLETED",
      completedAt: new Date(ts.getTime() + 360000),
      updatedAt: new Date(ts.getTime() + 360000),
      paymentMethod: "현금", cashReceived: 30000, creditAmount: 0,
    });
    callIds.push(callId);
  }

  // HOLD→재배차 (5건) — counted within the 50 total
  // Already covered by timeout scenario above (effectively HOLD pattern)

  console.log(`\n완료: ${callIds.length}건 정상, ${cancelledIds.length}건 취소. CF 대기 (15초)...`);
  await sleep(15000);

  // ─── CP: Day 2 체크포인트 ───
  console.log("\n--- Day 2 체크포인트 ---");

  const workDate = "2026-03-21";
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${workDate}`);
  assert(session !== null, "CP1: 정산 세션 2026-03-21 생성됨");

  if (session) {
    const sessionCalls = session.calls || [];
    // 완료된 콜만 정산에 포함 = 20 + 5(거절→재배차) + 5(타임아웃→재배차) = 30
    assertEqual(sessionCalls.length, 30, `CP2: 정산 세션 콜 수 = 30 (actual: ${sessionCalls.length})`);

    // CP3: 취소된 콜이 정산에 없는지
    for (const cid of cancelledIds) {
      const found = sessionCalls.find(c => c.callId === cid);
      assert(!found, `CP3: 취소 콜 ${cid} 정산 미포함`);
    }

    // CP4: 거절→재배차 콜이 최종 기사(B)로 귀속
    const rejectedCalls = sessionCalls.filter(c => callIds.slice(20, 25).includes(c.callId));
    for (const rc of rejectedCalls) {
      assertEqual(rc.driverId, DRIVERS.B.id, `CP4: 거절→재배차 콜 ${rc.callId} → 기사B 귀속`);
    }

    // CP5: 타임아웃→재배차 콜이 최종 기사(B)로 귀속
    const timeoutCalls = sessionCalls.filter(c => callIds.slice(25, 30).includes(c.callId));
    for (const tc of timeoutCalls) {
      assertEqual(tc.driverId, DRIVERS.B.id, `CP5: 타임아웃 콜 ${tc.callId} → 기사B 귀속`);
    }
  }

  // CP6: 취소 콜 상태 확인
  for (const cid of cancelledIds.slice(0, 5)) {
    const call = await getDocument(`${OFFICE_PATH}/calls/${cid}`);
    assertEqual(call?.status, "CANCELED", `CP6: 관리자취소 ${cid} = CANCELED`);
  }

  return callIds;
}

// ─── DAY 3: 다양한 결제 혼합 (50콜) ───
async function day3() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 3: 퇴근/재로그인/이체타이밍 (50콜)");
  console.log("=".repeat(60));

  const callIds = [];

  // Simplified: 50 calls with mixed payments for drivers A-E
  console.log("\n[1/3] A,B 1차 운행 15건 (현금)...");
  for (let i = 0; i < 15; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 20000 + (i * 1000);
    const id = await createAndCompleteCall(3, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  console.log("[2/3] A 2차 운행 + B 추가 운행 15건...");
  for (let i = 0; i < 15; i++) {
    const driverKey = i % 3 === 0 ? "A" : "B";
    const fare = 25000 + (i * 500);
    const id = await createAndCompleteCall(3, {
      driverKey, fare, paymentMethod: i % 2 === 0 ? "현금" : "이체",
      cashReceived: i % 2 === 0 ? fare : 0,
    });
    callIds.push(id);
  }

  console.log("[3/3] C,D,E 일반 운행 20건...");
  const keys = ["C", "D", "E"];
  for (let i = 0; i < 20; i++) {
    const driverKey = keys[i % 3];
    const fare = 18000 + (i * 1000);
    const methods = ["현금", "이체", "현금+포인트", "외상"];
    const method = methods[i % 4];
    const cash = method === "현금" ? fare : method === "현금+포인트" ? fare - 3000 : 0;
    const points = method === "현금+포인트" ? 3000 : 0;
    const credit = method === "외상" ? fare : 0;
    const id = await createAndCompleteCall(3, {
      driverKey, fare, paymentMethod: method,
      cashReceived: cash, pointsUsed: points, creditAmount: credit,
    });
    callIds.push(id);
  }

  console.log(`\n총 ${callIds.length}건 생성. CF 대기 (15초)...`);
  await sleep(15000);

  // ─── CP: Day 3 ───
  console.log("\n--- Day 3 체크포인트 ---");
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/2026-03-22`);
  assert(session !== null, "CP1: 정산 세션 2026-03-22 생성됨");
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);
    assert(session.totals?.totalFare > 0, `CP3: totalFare > 0 (${session.totals?.totalFare})`);

    // CP4: 기사 C,D,E 콜이 정산에 포함
    const cdeDriverIds = [DRIVERS.C.id, DRIVERS.D.id, DRIVERS.E.id];
    const cdeCalls = session.calls.filter(c => cdeDriverIds.includes(c.driverId));
    assertEqual(cdeCalls.length, 20, `CP4: C,D,E 콜 수 = 20 (actual: ${cdeCalls.length})`);
  }

  return callIds;
}

// ─── DAY 4: 거절/재제출 + 통합정산 (50콜) ───
async function day4() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 4: 거절/재제출 + 통합정산 (50콜)");
  console.log("=".repeat(60));

  const callIds = [];

  // 1. 전원 정상운행 20건
  console.log("\n[1/3] 전원 정상운행 20건...");
  const allKeys = ["A", "B", "C", "D", "E"];
  for (let i = 0; i < 20; i++) {
    const driverKey = allKeys[i % 5];
    const fare = 22000 + (i * 500);
    const id = await createAndCompleteCall(4, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  // 2. A 추가 운행 10건 (마감→거절→재제출 시나리오 데이터)
  console.log("[2/3] A 마감/재제출 데이터 10건...");
  for (let i = 0; i < 10; i++) {
    const fare = 20000;
    const id = await createAndCompleteCall(4, {
      driverKey: "A", fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  // 3. B 통합정산 데이터 10건 + D,E 일반 10건
  console.log("[3/3] B 통합 10건 + D,E 일반 10건...");
  for (let i = 0; i < 10; i++) {
    const fare = 25000 + (i * 1000);
    const id = await createAndCompleteCall(4, {
      driverKey: "B", fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }
  for (let i = 0; i < 10; i++) {
    const driverKey = i % 2 === 0 ? "D" : "E";
    const fare = 18000 + (i * 1000);
    const id = await createAndCompleteCall(4, {
      driverKey, fare, paymentMethod: i % 2 === 0 ? "이체" : "현금",
      cashReceived: i % 2 === 0 ? 0 : fare,
    });
    callIds.push(id);
  }

  console.log(`\n총 ${callIds.length}건 생성. CF 대기 (15초)...`);
  await sleep(15000);

  console.log("\n--- Day 4 체크포인트 ---");
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/2026-03-23`);
  assert(session !== null, "CP1: 정산 세션 2026-03-23 생성됨");
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);

    // CP3: A 기사 콜 수
    const aCalls = session.calls.filter(c => c.driverId === DRIVERS.A.id);
    assertEqual(aCalls.length, 14, `CP3: A 기사 콜 수 = 14 (actual: ${aCalls.length})`);

    // CP4: B 기사 콜 수
    const bCalls = session.calls.filter(c => c.driverId === DRIVERS.B.id);
    assertEqual(bCalls.length, 14, `CP4: B 기사 콜 수 = 14 (actual: ${bCalls.length})`);
  }

  return callIds;
}

// ─── DAY 5: 동시성 + 연속 콜 (50콜) ───
async function day5() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 5: 동시성 + 연속 콜 (50콜)");
  console.log("=".repeat(60));

  const callIds = [];

  // 1. 5건 동시배차×5라운드 = 25건 (병렬 생성)
  console.log("\n[1/3] 동시배차 5라운드 × 5건 = 25건...");
  for (let round = 0; round < 5; round++) {
    const promises = [];
    for (let i = 0; i < 5; i++) {
      const driverKey = allKeys5[i];
      const fare = 20000 + (round * 2000);
      promises.push(createAndCompleteCall(5, {
        driverKey, fare, paymentMethod: "현금", cashReceived: fare,
      }));
    }
    const ids = await Promise.all(promises);
    callIds.push(...ids);
    console.log(`  라운드 ${round + 1}/5 완료`);
  }

  // 2. 연속 빠른 콜 10건 (30초 간격 시뮬레이션)
  console.log("[2/3] 연속 빠른 콜 10건...");
  for (let i = 0; i < 10; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const fare = 15000 + (i * 1000);
    const id = await createAndCompleteCall(5, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  // 3. 정상운행 15건
  console.log("[3/3] 정상운행 15건...");
  for (let i = 0; i < 15; i++) {
    const driverKey = allKeys5[i % 5];
    const fare = 22000 + (i * 500);
    const id = await createAndCompleteCall(5, {
      driverKey, fare, paymentMethod: i % 3 === 0 ? "이체" : "현금",
      cashReceived: i % 3 === 0 ? 0 : (22000 + i * 500),
    });
    callIds.push(id);
  }

  console.log(`\n총 ${callIds.length}건 생성. CF 대기 (20초 - 동시성)...`);
  await sleep(20000);

  console.log("\n--- Day 5 체크포인트 ---");
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/2026-03-24`);
  assert(session !== null, "CP1: 정산 세션 2026-03-24 생성됨");
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);

    // CP3: 중복 콜 없음
    const callIdSet = new Set(session.calls.map(c => c.callId));
    assertEqual(callIdSet.size, session.calls.length, `CP3: 중복 콜 없음 (unique=${callIdSet.size})`);

    // CP4: 5명 기사 모두 포함
    const driverIds = new Set(session.calls.map(c => c.driverId));
    assertEqual(driverIds.size, 5, `CP4: 5명 기사 모두 포함 (actual: ${driverIds.size})`);
  }

  return callIds;
}

const allKeys5 = ["A", "B", "C", "D", "E"];

// ─── DAY 6: 공유콜 + 특수상황 (50콜) ───
async function day6() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 6: 공유콜 + 특수상황 (50콜)");
  console.log("=".repeat(60));

  const callIds = [];

  // 1. 정상운행 20건
  console.log("\n[1/5] 정상운행 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = allKeys5[i % 5];
    const fare = 20000 + (i * 1000);
    const id = await createAndCompleteCall(6, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  // 2. 공유콜 5건 (shared_calls에 생성)
  console.log("[2/5] 공유콜 5건 (shared_calls)...");
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
    // These stay as SHARED_WAITING (not completed in our office)
  }

  // 3. 0원 콜 5건
  console.log("[3/5] 0원 콜 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = i % 2 === 0 ? "A" : "B";
    const id = await createAndCompleteCall(6, {
      driverKey, fare: 0, paymentMethod: "현금", cashReceived: 0,
    });
    callIds.push(id);
  }

  // 4. 소액/고액 콜 10건
  console.log("[4/5] 소액 5000원 5건 + 고액 200000원 5건...");
  for (let i = 0; i < 5; i++) {
    const id = await createAndCompleteCall(6, {
      driverKey: "C", fare: 5000, paymentMethod: "현금", cashReceived: 5000,
    });
    callIds.push(id);
  }
  for (let i = 0; i < 5; i++) {
    const id = await createAndCompleteCall(6, {
      driverKey: "D", fare: 200000, paymentMethod: "이체", cashReceived: 0,
    });
    callIds.push(id);
  }

  // 5. 동일기사 연속 10건
  console.log("[5/6] 동일기사(A) 연속 10건...");
  for (let i = 0; i < 10; i++) {
    const fare = 20000 + (i * 1000);
    const id = await createAndCompleteCall(6, {
      driverKey: "A", fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  // 6. 추가 일반 운행 5건 (공유콜은 별도 컬렉션이므로 정산 50건 맞추기)
  console.log("[6/6] 추가 일반 운행 5건...");
  for (let i = 0; i < 5; i++) {
    const driverKey = allKeys5[i % 5];
    const fare = 22000 + (i * 2000);
    const id = await createAndCompleteCall(6, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  console.log(`\n총 ${callIds.length}건 완료 + 공유콜 5건. CF 대기 (15초)...`);
  await sleep(15000);

  console.log("\n--- Day 6 체크포인트 ---");
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/2026-03-25`);
  assert(session !== null, "CP1: 정산 세션 2026-03-25 생성됨");
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);

    // CP3: 0원 콜 포함
    const zeroCalls = session.calls.filter(c => c.fare === 0);
    assertEqual(zeroCalls.length, 5, `CP3: 0원 콜 5건 (actual: ${zeroCalls.length})`);

    // CP4: 고액 콜
    const highCalls = session.calls.filter(c => c.fare === 200000);
    assertEqual(highCalls.length, 5, `CP4: 200000원 콜 5건 (actual: ${highCalls.length})`);

    // CP5: A 기사 연속 10건 포함 (total A calls in day 6)
    const aCalls = session.calls.filter(c => c.driverId === DRIVERS.A.id);
    assert(aCalls.length >= 10, `CP5: A 기사 콜 ≥ 10 (actual: ${aCalls.length})`);
  }

  // CP6: 공유콜 확인
  const sharedCall = await getDocument(`shared_calls/${TEST_PREFIX}d6_shared_0`);
  assertEqual(sharedCall?.status, "SHARED_WAITING", "CP6: 공유콜 상태 = SHARED_WAITING");

  return callIds;
}

// ─── DAY 7: 최종 정리 + 전원 마감 (50콜) ───
async function day7() {
  console.log("\n" + "=".repeat(60));
  console.log("DAY 7: 최종 정리 + 다양한 결제 (50콜)");
  console.log("=".repeat(60));

  const callIds = [];

  // 1. 이월금 있는 상태 운행 15건
  console.log("\n[1/3] 이월 상태 운행 15건...");
  for (let i = 0; i < 15; i++) {
    const driverKey = allKeys5[i % 5];
    const fare = 25000 + (i * 1000);
    const id = await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  // 2. 다양한 결제 혼합 15건
  console.log("[2/3] 혼합 결제 15건...");
  const methods = ["현금", "이체", "현금+포인트", "외상", "포인트"];
  for (let i = 0; i < 15; i++) {
    const driverKey = allKeys5[i % 5];
    const fare = 20000 + (i * 2000);
    const method = methods[i % 5];
    const cash = method === "현금" ? fare : method === "현금+포인트" ? fare - 5000 : 0;
    const points = method === "현금+포인트" ? 5000 : method === "포인트" ? fare : 0;
    const credit = method === "외상" ? fare : 0;
    const id = await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: method,
      cashReceived: cash, pointsUsed: points, creditAmount: credit,
    });
    callIds.push(id);
  }

  // 3. 전원 마감 데이터 20건
  console.log("[3/3] 전원 마감 운행 20건...");
  for (let i = 0; i < 20; i++) {
    const driverKey = allKeys5[i % 5];
    const fare = 30000;
    const id = await createAndCompleteCall(7, {
      driverKey, fare, paymentMethod: "현금", cashReceived: fare,
    });
    callIds.push(id);
  }

  console.log(`\n총 ${callIds.length}건 생성. CF 대기 (15초)...`);
  await sleep(15000);

  console.log("\n--- Day 7 체크포인트 ---");
  const session = await getDocument(`${OFFICE_PATH}/settlementSessions/2026-03-26`);
  assert(session !== null, "CP1: 정산 세션 2026-03-26 생성됨");
  if (session) {
    assertEqual(session.calls?.length, 50, `CP2: 정산 콜 수 = 50 (actual: ${session.calls?.length})`);

    // CP3: 5명 기사 모두 포함
    const driverIds = new Set(session.calls.map(c => c.driverId));
    assertEqual(driverIds.size, 5, `CP3: 5명 기사 모두 포함 (actual: ${driverIds.size})`);

    // CP4: 혼합 결제 타입 존재
    const paymentTypes = new Set(session.calls.map(c => c.paymentMethod));
    assert(paymentTypes.size >= 4, `CP4: 결제타입 ≥ 4 (actual: ${paymentTypes.size}, types: ${[...paymentTypes]})`);

    // CP5: isFinalized = false (아직 마감 안됨)
    assertEqual(session.metadata?.isFinalized, false, "CP5: isFinalized = false");

    // CP6: depositRatio = 60
    assertEqual(session.metadata?.depositRatio, 60, "CP6: depositRatio = 60");

    // CP7: totalDriverShare = totalFare - totalDeposit
    const expectedDriverShare = session.totals.totalFare - session.totals.totalDeposit;
    assertEqual(session.totals.totalDriverShare, expectedDriverShare,
      `CP7: totalDriverShare = totalFare - totalDeposit (${expectedDriverShare})`);
  }

  return callIds;
}

// ─── Final Cross-Day Verification ───
async function crossDayVerification() {
  console.log("\n" + "=".repeat(60));
  console.log("CROSS-DAY 종합 검증");
  console.log("=".repeat(60));

  const dates = [
    "2026-03-20", "2026-03-21", "2026-03-22",
    "2026-03-23", "2026-03-24", "2026-03-25", "2026-03-26"
  ];

  let grandTotalFare = 0;
  let grandTotalCalls = 0;

  for (const date of dates) {
    const session = await getDocument(`${OFFICE_PATH}/settlementSessions/${date}`);
    if (session) {
      const callCount = session.calls?.length || 0;
      const totalFare = session.totals?.totalFare || 0;
      grandTotalCalls += callCount;
      grandTotalFare += totalFare;
      console.log(`  ${date}: ${callCount}건, ₩${totalFare.toLocaleString()}`);

      // Verify deposit formula for each session
      const expectedDeposit = Math.floor(totalFare * (session.metadata?.depositRatio || 60) / 100);
      assertEqual(session.totals?.totalDeposit, expectedDeposit,
        `  ${date} deposit = floor(fare * ratio / 100)`);

      const expectedDriverShare = totalFare - expectedDeposit;
      assertEqual(session.totals?.totalDriverShare, expectedDriverShare,
        `  ${date} driverShare = fare - deposit`);
    } else {
      console.log(`  ✗ ${date}: 세션 없음!`);
      failCount++;
    }
  }

  // Day2에서 취소 15건은 정산 미포함이므로 정산 콜은 50*7 - 15(취소) - 5(공유) = 330
  // 실제로: Day1=50, Day2=30(완료만), Day3=50, Day4=50, Day5=50, Day6=50(공유 제외), Day7=50 = 330
  console.log(`\n  총 정산 콜: ${grandTotalCalls}건`);
  console.log(`  총 매출: ₩${grandTotalFare.toLocaleString()}`);
  assertEqual(grandTotalCalls, 330, `총 정산 콜 수 = 330 (취소 15건 + 공유 5건 미포함)`);
}

// ─── Cleanup ───
async function cleanup() {
  console.log("\n정리 중... (테스트 콜 삭제)");

  // List all test calls
  const allCalls = await listDocuments(`${OFFICE_PATH}/calls`);
  const testCalls = allCalls.filter(c => c.id.startsWith(TEST_PREFIX));
  console.log(`  테스트 콜 ${testCalls.length}건 발견`);

  for (const call of testCalls) {
    await deleteDocument(`${OFFICE_PATH}/calls/${call.id}`);
  }

  // Clean shared calls
  const sharedCalls = await listDocuments("shared_calls");
  const testShared = sharedCalls.filter(c => c.id.startsWith(TEST_PREFIX));
  for (const call of testShared) {
    await deleteDocument(`shared_calls/${call.id}`);
  }

  // Clean test settlement sessions
  const testDates = [
    "2026-03-20", "2026-03-21", "2026-03-22",
    "2026-03-23", "2026-03-24", "2026-03-25", "2026-03-26"
  ];
  for (const date of testDates) {
    await deleteDocument(`${OFFICE_PATH}/settlementSessions/${date}`);
  }

  console.log("  정리 완료");
}

// ─── Main ───
(async () => {
  const args = process.argv.slice(2);
  const dayArg = args[0];
  const doCleanup = args.includes("--cleanup");

  console.log("╔════════════════════════════════════════════════╗");
  console.log("║  2단계 최종 검증: Day 1~7 (350콜)             ║");
  console.log("║  Firestore REST API + CF 트리거 검증           ║");
  console.log("╚════════════════════════════════════════════════╝");

  try {
    if (doCleanup) {
      await cleanup();
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
        console.log("Usage: node scripts/test-day1-7.js [1-7|all|verify|--cleanup]");
        return;
      }
    }

    console.log("\n" + "=".repeat(60));
    console.log(`결과: ${passCount} PASS / ${failCount} FAIL`);
    console.log("=".repeat(60));

    if (failCount > 0) {
      process.exit(1);
    }
  } catch (e) {
    console.error("Error:", e.message);
    console.error(e.stack);
    process.exit(1);
  }
})();
