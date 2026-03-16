/**
 * 피크타임 동시 부하 테스트
 *
 * 10개 사무실 × 10건 = 100건 동시 COMPLETED → CF 트랜잭션 경합 검증
 * 검증: 정산 세션에 누락 없이 전부 반영되는지 + 소요 시간
 *
 * Usage:
 *   node scripts/peak-load-test.js run       # 테스트 실행
 *   node scripts/peak-load-test.js --cleanup  # 정리
 *   node scripts/peak-load-test.js --status   # 상태 확인
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
const TEST_PREFIX = "pk_";

function getTodayWorkDate() {
  const now = new Date();
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  if (kst.getUTCHours() < 6) kst.setUTCDate(kst.getUTCDate() - 1);
  return kst.toISOString().split("T")[0];
}
const TEST_DATE = getTodayWorkDate();

// 10개 사무실 (multi-office-test.js에서 생성한 것 재사용)
const OFFICES = [];
for (let i = 0; i < 10; i++) {
  const officeId = i === 0 ? REAL_OFFICE_ID : `test_office_${i}`;
  OFFICES.push({
    index: i,
    id: officeId,
    path: `provinces/${PROVINCE}/cities/${CITY}/offices/${officeId}`,
    name: i === 0 ? "본사무실" : `테스트사무실${i}`,
    drivers: {
      A: { id: i === 0 ? "6RQEWvmDkkfTAHXjvbxPtYfa7mY2" : `test_driver_${i}_A`, name: i === 0 ? "양세훈" : `기사${i}A` },
      B: { id: i === 0 ? "vIbRH7Ci17UqCUR6ty84eDdl2me2" : `test_driver_${i}_B`, name: i === 0 ? "고양이" : `기사${i}B` },
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
      res.on("end", () => { try { resolve(JSON.parse(data)); } catch { resolve(data); } });
    });
    req.on("error", reject);
    if (options.body) req.write(options.body);
    req.end();
  });
}

function getRefreshToken() {
  return require(path.join(os.homedir(), ".config", "configstore", "firebase-tools.json")).tokens.refresh_token;
}

async function getAccessToken() {
  if (cachedToken && Date.now() < tokenExpiry) return cachedToken;
  const body = `client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&refresh_token=${getRefreshToken()}&grant_type=refresh_token`;
  const result = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body
  });
  if (!result.access_token) throw new Error("Token failed: " + JSON.stringify(result));
  cachedToken = result.access_token;
  tokenExpiry = Date.now() + 50 * 60 * 1000;
  return cachedToken;
}

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
  const url = docId ? `${BASE_URL}/${collectionPath}?documentId=${docId}` : `${BASE_URL}/${collectionPath}`;
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
  const fields = {};
  for (const [k, v] of Object.entries(data)) fields[k] = toFirestoreValue(v);
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
  const result = await httpRequest(`${BASE_URL}/${docPath}`, { headers: { "Authorization": `Bearer ${token}` } });
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
  return await httpRequest(`${BASE_URL}/${docPath}`, { method: "DELETE", headers: { "Authorization": `Bearer ${token}` } });
}

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

// ═══════════════════════════════════════════════════
// Phase 1: 콜 생성 (AWAITING_SETTLEMENT 까지 — 동시 COMPLETED 직전)
// ═══════════════════════════════════════════════════

let callCounter = 0;

async function prepareCall(office, driverKey, fare) {
  callCounter++;
  const callId = `${TEST_PREFIX}${String(callCounter).padStart(3, "0")}`;
  const driver = office.drivers[driverKey];
  const ts = new Date();
  const expireAt = new Date(ts.getTime() + 30 * 24 * 60 * 60 * 1000);

  // WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT
  await createDocument(`${office.path}/calls`, callId, {
    status: "WAITING", phoneNumber: "", customerName: "", customerAddress: "",
    fare, fare_set: fare, pointsUsed: 0,
    timestamp: ts, timestampClient: ts.getTime(),
    fromCallManager: true, cityId: CITY, provinceId: PROVINCE, officeId: office.id,
    departure_set: "피크출발", destination_set: "피크도착",
    waypoints_set: "", createdBy: "test-script", expireAt,
  });
  await updateDocument(`${office.path}/calls/${callId}`, {
    status: "ASSIGNED", assignedDriverId: driver.id, assignedDriverName: driver.name,
    assignedDriverPhone: "01000000000", assignedTimestamp: ts, updatedAt: ts,
  });
  await updateDocument(`${office.path}/calls/${callId}`, { status: "ACCEPTED", updatedAt: new Date() });
  await updateDocument(`${office.path}/calls/${callId}`, { status: "IN_PROGRESS", updatedAt: new Date() });
  await updateDocument(`${office.path}/calls/${callId}`, {
    status: "AWAITING_SETTLEMENT", updatedAt: new Date(),
    finalFare: fare, fareFinal: fare,
    tripSummaryFinal: `피크 ${fare}원`, trip_summary: `피크 ${fare}원`,
  });

  return { callId, office, fare };
}

// 단일 콜 COMPLETED 전환
async function completeCall(callPath, fare) {
  const now = new Date();
  await updateDocument(callPath, {
    status: "COMPLETED", completedAt: now, updatedAt: now,
    paymentMethod: "현금", cashReceived: fare, creditAmount: 0,
  });
}

// ═══════════════════════════════════════════════════
// Main Test
// ═══════════════════════════════════════════════════

async function run() {
  const CALLS_PER_OFFICE = 10;
  const TOTAL_CALLS = OFFICES.length * CALLS_PER_OFFICE;

  console.log("\n" + "=".repeat(60));
  console.log(`피크타임 부하 테스트: ${OFFICES.length}개 사무실 × ${CALLS_PER_OFFICE}건 = ${TOTAL_CALLS}건`);
  console.log(`정산 세션 날짜: ${TEST_DATE}`);
  console.log("=".repeat(60));

  // ── Step 1: 기존 정산 세션 삭제 (깨끗한 상태에서 시작) ──
  console.log("\n[Step 1] 기존 정산 세션 삭제...");
  for (const office of OFFICES) {
    await deleteDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
  }

  // ── Step 2: 100건 콜 준비 (AWAITING_SETTLEMENT까지) ──
  console.log(`\n[Step 2] ${TOTAL_CALLS}건 콜 준비 (AWAITING_SETTLEMENT까지)...`);
  const preparedCalls = [];

  for (const office of OFFICES) {
    process.stdout.write(`  ${office.name}: `);
    for (let j = 0; j < CALLS_PER_OFFICE; j++) {
      const driverKey = j % 2 === 0 ? "A" : "B";
      const fare = 20000 + (j * 2000);
      const prepared = await prepareCall(office, driverKey, fare);
      preparedCalls.push(prepared);
      process.stdout.write(".");
    }
    console.log(` ${CALLS_PER_OFFICE}건 준비 완료`);
  }

  console.log(`\n  총 ${preparedCalls.length}건 AWAITING_SETTLEMENT 상태`);

  // ── Step 3: 동시 COMPLETED (핵심 — CF 트랜잭션 경합 유발) ──
  console.log(`\n[Step 3] ${TOTAL_CALLS}건 동시 COMPLETED 전환...`);
  const startTime = Date.now();

  // 사무실별로 그룹핑하여 동시 발사
  const batchPromises = preparedCalls.map(({ callId, office, fare }) =>
    completeCall(`${office.path}/calls/${callId}`, fare)
  );

  const results = await Promise.allSettled(batchPromises);
  const completedTime = Date.now();

  const succeeded = results.filter(r => r.status === "fulfilled").length;
  const failed = results.filter(r => r.status === "rejected").length;

  console.log(`  REST API 완료: ${succeeded} 성공, ${failed} 실패 (${completedTime - startTime}ms)`);

  if (failed > 0) {
    const errors = results.filter(r => r.status === "rejected").map(r => r.reason.message);
    const uniqueErrors = [...new Set(errors)];
    console.log(`  실패 원인: ${uniqueErrors.join(", ")}`);
  }

  // ── Step 4: CF 트리거 대기 + 폴링 ──
  console.log(`\n[Step 4] CF 트랜잭션 처리 대기 (폴링)...`);

  const expectedPerOffice = CALLS_PER_OFFICE;
  const maxWait = 120000; // 최대 2분
  const pollInterval = 5000;
  let elapsed = 0;
  let allComplete = false;

  while (elapsed < maxWait) {
    await sleep(pollInterval);
    elapsed += pollInterval;

    let totalSettled = 0;
    let officeResults = [];

    for (const office of OFFICES) {
      const session = await getDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
      const count = session?.calls?.length || 0;
      totalSettled += count;
      officeResults.push({ name: office.name, count, expected: expectedPerOffice });
    }

    const pct = Math.round(totalSettled / TOTAL_CALLS * 100);
    console.log(`  ${elapsed / 1000}초: ${totalSettled}/${TOTAL_CALLS}건 반영 (${pct}%)`);

    if (totalSettled >= TOTAL_CALLS) {
      allComplete = true;
      break;
    }
  }

  const settlementTime = Date.now();
  const totalElapsed = settlementTime - startTime;

  // ── Step 5: 최종 검증 ──
  console.log(`\n[Step 5] 최종 검증`);
  console.log("─".repeat(50));

  let grandTotal = 0;
  let grandFare = 0;

  for (const office of OFFICES) {
    const session = await getDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
    const count = session?.calls?.length || 0;
    const fare = session?.totals?.totalFare || 0;
    grandTotal += count;
    grandFare += fare;

    assertEqual(count, expectedPerOffice, `  ${office.name}: ${count}/${expectedPerOffice}건`);

    if (session) {
      // 중복 확인
      const callIds = session.calls.map(c => c.callId);
      const unique = new Set(callIds);
      assertEqual(unique.size, count, `  ${office.name}: 중복 없음`);

      // 정산 공식
      const ratio = session.metadata?.depositRatio || 60;
      const expectedDeposit = Math.floor(fare * ratio / 100);
      assertEqual(session.totals?.totalDeposit, expectedDeposit, `  ${office.name}: deposit 공식 일치`);
    }
  }

  // ── 결과 요약 ──
  console.log("\n" + "═".repeat(50));
  console.log("피크타임 부하 테스트 결과");
  console.log("═".repeat(50));
  console.log(`  동시 COMPLETED:     ${TOTAL_CALLS}건`);
  console.log(`  REST API 소요:      ${completedTime - startTime}ms`);
  console.log(`  정산 반영 완료:     ${allComplete ? "YES" : "NO (타임아웃)"}`);
  console.log(`  정산 반영 소요:     ${totalElapsed}ms (${Math.round(totalElapsed / 1000)}초)`);
  console.log(`  총 정산 콜:         ${grandTotal}/${TOTAL_CALLS}건`);
  console.log(`  총 매출:            ₩${grandFare.toLocaleString()}`);
  console.log(`  누락:               ${TOTAL_CALLS - grandTotal}건`);
  console.log(`  데이터 무결성:      ${grandTotal === TOTAL_CALLS ? "PASS ✓" : "FAIL ✗"}`);
  console.log("═".repeat(50));

  if (!allComplete) {
    console.log("\n  ⚠ 2분 내 미완료 — CF 로그 확인 필요:");
    console.log("    firebase functions:log --only onCallCompletedUpdateSettlement");
  }
}

// ─── Cleanup ───
async function cleanup() {
  console.log("\n정리 중...");

  for (const office of OFFICES) {
    const calls = await listDocuments(`${office.path}/calls`);
    const testCalls = calls.filter(c => c.id.startsWith(TEST_PREFIX));
    if (testCalls.length > 0) {
      console.log(`  ${office.name}: ${testCalls.length}건 삭제...`);
      for (const call of testCalls) await deleteDocument(`${office.path}/calls/${call.id}`);
    }
    await deleteDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
  }

  console.log("  정리 완료");
}

// ─── Status ───
async function statusCheck() {
  console.log(`\n현재 상태 (${TEST_DATE}):\n`);
  let total = 0;
  for (const office of OFFICES) {
    const session = await getDocument(`${office.path}/settlementSessions/${TEST_DATE}`);
    const count = session?.calls?.length || 0;
    const fare = session?.totals?.totalFare || 0;
    total += count;
    console.log(`  ${office.name}: ${count}건, ₩${fare.toLocaleString()}`);
  }
  console.log(`\n  총: ${total}건`);
}

// ─── Main ───
(async () => {
  const arg = process.argv[2];

  console.log("╔════════════════════════════════════════════════════════╗");
  console.log("║  피크타임 동시 부하 테스트 (CF 트랜잭션 경합 검증)    ║");
  console.log("╚════════════════════════════════════════════════════════╝");

  try {
    if (arg === "--cleanup") { await cleanup(); return; }
    if (arg === "--status") { await statusCheck(); return; }
    if (arg === "run") {
      await run();
      console.log("\n" + "=".repeat(60));
      console.log(`결과: ${passCount} PASS / ${failCount} FAIL`);
      console.log("=".repeat(60));
      if (failCount > 0) process.exit(1);
      return;
    }
    console.log("Usage: node scripts/peak-load-test.js [run|--cleanup|--status]");
  } catch (e) {
    console.error("Error:", e.message);
    console.error(e.stack);
    process.exit(1);
  }
})();
