/**
 * Day A Phase 1: 8콜 생성 + 배차
 * 기사A (CA-001~004), 기사B (CA-005~008)
 */
const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";
const OFFICE_PATH = "provinces/gyeonggi/cities/yangpyeong/offices/nEkf0X9g3LZtRX94Mrzu";

const DRIVERS = {
  A: { id: "6RQEWvmDkkfTAHXjvbxPtYfa7mY2", name: "양세훈" },
  B: { id: "vIbRH7Ci17UqCUR6ty84eDdl2me2", name: "고양이" },
};

// ─── HTTP / Auth ───
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
  const configPath = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  return require(configPath).tokens.refresh_token;
}

let cachedToken = null;
async function getAccessToken() {
  if (cachedToken) return cachedToken;
  const body = `client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&refresh_token=${getRefreshToken()}&grant_type=refresh_token`;
  const result = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body
  });
  if (!result.access_token) throw new Error("Token failed: " + JSON.stringify(result));
  cachedToken = result.access_token;
  return cachedToken;
}

// ─── Firestore Helpers ───
function toFirestoreValue(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === "string") return { stringValue: val };
  if (typeof val === "number") return Number.isInteger(val) ? { integerValue: String(val) } : { doubleValue: val };
  if (typeof val === "boolean") return { booleanValue: val };
  if (val instanceof Date) return { timestampValue: val.toISOString() };
  if (Array.isArray(val)) return { arrayValue: { values: val.map(toFirestoreValue) } };
  if (typeof val === "object") {
    const f = {};
    for (const [k, v] of Object.entries(val)) f[k] = toFirestoreValue(v);
    return { mapValue: { fields: f } };
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
    const o = {};
    for (const [k, v] of Object.entries(val.mapValue.fields || {})) o[k] = fromFirestoreValue(v);
    return o;
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
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body
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
  const body = JSON.stringify({ fields });
  const result = await httpRequest(url, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body
  });
  if (result.error) throw new Error(`Update failed (${docPath}): ${result.error.message}`);
  return parseDoc(result);
}

async function getDocument(docPath) {
  const token = await getAccessToken();
  const result = await httpRequest(`${BASE_URL}/${docPath}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (result.error) return null;
  return parseDoc(result);
}

// ─── Phase 1: 8콜 정의 ───
const CALLS = [
  { callId: "CA-001", driver: "A", phone: "010-1001-0001", type: "phone", fare: 20000, payment: "현금" },
  { callId: "CA-002", driver: "A", phone: "010-1001-0002", type: "app",   fare: 25000, payment: "이체" },
  { callId: "CA-003", driver: "A", phone: "010-1001-0003", type: "phone", fare: 18000, payment: "현금" },
  { callId: "CA-004", driver: "A", phone: "010-1001-0004", type: "phone", fare: 15000, payment: "포인트" },
  { callId: "CA-005", driver: "B", phone: "010-1001-0005", type: "phone", fare: 30000, payment: "현금" },
  { callId: "CA-006", driver: "B", phone: "010-1001-0006", type: "app",   fare: 22000, payment: "현금" },
  { callId: "CA-007", driver: "B", phone: "010-1001-0007", type: "phone", fare: 18000, payment: "현금" },
  { callId: "CA-008", driver: "B", phone: "010-1001-0008", type: "phone", fare: 20000, payment: "이체" },
];

async function run() {
  const now = new Date();
  const results = [];

  for (let i = 0; i < CALLS.length; i++) {
    const c = CALLS[i];
    const driver = DRIVERS[c.driver];
    const timestamp = new Date(now.getTime() + i * 60000);
    const expireAt = new Date(timestamp.getTime() + 30 * 24 * 60 * 60 * 1000);
    const fromCallDetector = c.type === "phone";

    // Step 1: 콜 생성 (WAITING)
    console.log(`[${c.callId}] 콜 생성 (WAITING) - ${c.type}, 요금: ${c.fare}, 결제: ${c.payment}`);
    await createDocument(`${OFFICE_PATH}/calls`, c.callId, {
      status: "WAITING",
      phoneNumber: c.phone,
      customerName: `손님${c.callId}`,
      customerAddress: "",
      fare: c.fare,
      fare_set: c.fare,
      pointsUsed: 0,
      timestamp: timestamp,
      timestampClient: timestamp.getTime(),
      fromCallDetector: fromCallDetector,
      fromCallManager: !fromCallDetector,
      isAppCustomer: c.type === "app",
      createdFrom: c.type === "phone" ? "phone" : "app",
      cityId: "yangpyeong",
      provinceId: "gyeonggi",
      officeId: "nEkf0X9g3LZtRX94Mrzu",
      departure_set: `양평 출발지 ${c.callId}`,
      destination_set: `양평 도착지 ${c.callId}`,
      waypoints_set: "",
      callType: c.type === "phone" ? "수신" : "앱",
      createdBy: "sim-mgr-main",
      expireAt: expireAt,
      deviceName: "sim-detector",
    });

    // Step 2: 배차 (ASSIGNED)
    const assignedTime = new Date(timestamp.getTime() + 2000);
    console.log(`[${c.callId}] 배차 → ${driver.name} (기사${c.driver})`);
    await updateDocument(`${OFFICE_PATH}/calls/${c.callId}`, {
      status: "ASSIGNED",
      assignedDriverId: driver.id,
      assignedDriverName: driver.name,
      assignedDriverPhone: "01000000000",
      assignedTimestamp: assignedTime,
      updatedAt: assignedTime,
    });

    // 기사 상태 → ASSIGNED
    await updateDocument(`${OFFICE_PATH}/designated_drivers/${driver.id}`, {
      status: "ASSIGNED",
    });

    // 확인
    const doc = await getDocument(`${OFFICE_PATH}/calls/${c.callId}`);
    results.push({
      callId: c.callId,
      status: doc.status,
      driver: doc.assignedDriverName,
      fare: doc.fare,
      payment: c.payment,
    });
    console.log(`  -> 완료: status=${doc.status}, driver=${doc.assignedDriverName}\n`);
  }

  console.log("===== Phase 1 결과 요약 =====");
  console.log("| 콜ID | 상태 | 배차 기사 | 요금 | 결제 |");
  console.log("|------|------|----------|------|------|");
  results.forEach(r => {
    console.log(`| ${r.callId} | ${r.status} | ${r.driver} | ${r.fare} | ${r.payment} |`);
  });
  console.log("\nPhase 1 완료: 8콜 생성 + 배차 ASSIGNED");
}

run().catch(e => console.error("ERROR:", e.message));
