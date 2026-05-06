/**
 * Deploy 직후 in-flight ASSIGNED 콜에 timeout task 일괄 enqueue.
 *
 * 배경: 기존 checkAssignedTimeout 폴링 함수가 사라지고 oncallassigned 트리거가 task enqueue.
 * 이미 ASSIGNED 상태인 기존 콜은 재배차되지 않으면 트리거가 안 fire → 좀비 가능.
 * Deploy 직후 1회 실행해서 기존 ASSIGNED 콜에 task 1개씩 보충.
 *
 * 사용법:
 *   node functions/scripts/backfill-assigned-timeout-tasks.js              # dry-run
 *   node functions/scripts/backfill-assigned-timeout-tasks.js --execute    # 실 enqueue
 *
 * 동작:
 *   1. collectionGroup("calls").where(status==ASSIGNED) 1 query
 *   2. 매칭 콜마다 office의 assignedTimeoutMinutes 조회
 *   3. assignedTimestamp 기준 남은 시간 계산:
 *      - 이미 timeout 초과한 콜 → delay 1초로 즉시 발화
 *      - 아직 안 지난 콜 → 남은 시간 만큼 delay
 *   4. Cloud Tasks REST API 로 enqueue (task ID = `${callId}_${assignedTsMs}`)
 *   5. 같은 task ID 가 이미 존재하면 Cloud Tasks 가 ALREADY_EXISTS 반환 → 정상 (중복 방지)
 */

const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const PROJECT_NUMBER = "60275310305";
const REGION = "asia-northeast3";
const FUNCTION_NAME = "checkSingleCallAssignedTimeout";
const QUEUE_PATH = `projects/${PROJECT_ID}/locations/${REGION}/queues/${FUNCTION_NAME}`;
const TARGET_URL = `https://${REGION}-${PROJECT_ID}.cloudfunctions.net/${FUNCTION_NAME}`;
const SERVICE_ACCOUNT = `${PROJECT_NUMBER}-compute@developer.gserviceaccount.com`;

const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

function getRefreshToken() {
  const configPath = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  return require(configPath).tokens.refresh_token;
}

function httpRequest(url, options = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      let data = "";
      res.on("data", (c) => data += c);
      res.on("end", () => {
        const status = res.statusCode;
        try { resolve({ status, body: JSON.parse(data) }); } catch { resolve({ status, body: data }); }
      });
    });
    req.on("error", reject);
    if (options.body) req.write(options.body);
    req.end();
  });
}

async function getAccessToken() {
  const refreshToken = getRefreshToken();
  const body = `client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&refresh_token=${refreshToken}&grant_type=refresh_token`;
  const r = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  if (!r.body.access_token) throw new Error("Token error: " + JSON.stringify(r.body));
  return r.body.access_token;
}

// Firestore REST API: collectionGroup query for status==ASSIGNED
async function listAssignedCalls(token) {
  const url = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents:runQuery`;
  const body = JSON.stringify({
    structuredQuery: {
      from: [{ collectionId: "calls", allDescendants: true }],
      where: {
        fieldFilter: {
          field: { fieldPath: "status" },
          op: "EQUAL",
          value: { stringValue: "ASSIGNED" }
        }
      }
    }
  });
  const r = await httpRequest(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body
  });
  if (r.body.error) throw new Error(`runQuery: ${r.body.error.message}`);
  // result is array; entries without `document` are query overhead — skip
  const docs = (Array.isArray(r.body) ? r.body : []).filter(e => e.document);
  return docs.map(e => {
    const fullName = e.document.name; // projects/.../documents/provinces/{p}/cities/{c}/offices/{o}/calls/{callId}
    const segs = fullName.split("/documents/")[1].split("/");
    // segs: ["provinces", p, "cities", c, "offices", o, "calls", callId]
    return {
      provinceId: segs[1],
      cityId: segs[3],
      officeId: segs[5],
      callId: segs[7],
      fields: e.document.fields || {},
      docPath: fullName.split("/documents/")[1],
    };
  });
}

async function getOfficeTimeoutMinutes(token, provinceId, cityId, officeId) {
  const url = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/provinces/${provinceId}/cities/${cityId}/offices/${officeId}`;
  const r = await httpRequest(url, { headers: { Authorization: `Bearer ${token}` } });
  if (r.body.error) return 1;
  const v = r.body.fields?.assignedTimeoutMinutes;
  if (v?.integerValue) return Number(v.integerValue);
  if (v?.doubleValue) return Number(v.doubleValue);
  return 1;
}

function sanitizeTaskId(raw) {
  return raw.replace(/[^A-Za-z0-9_-]/g, "_");
}

// Cloud Tasks v2 REST API: create task with HTTP target (OIDC auth)
async function createTask(token, taskId, payload, scheduleTimeRfc3339) {
  const url = `https://cloudtasks.googleapis.com/v2/${QUEUE_PATH}/tasks`;
  const taskName = `${QUEUE_PATH}/tasks/${taskId}`;
  const body = JSON.stringify({
    task: {
      name: taskName,
      scheduleTime: scheduleTimeRfc3339,
      httpRequest: {
        httpMethod: "POST",
        url: TARGET_URL,
        headers: { "Content-Type": "application/json" },
        body: Buffer.from(JSON.stringify({ data: payload })).toString("base64"),
        oidcToken: {
          serviceAccountEmail: SERVICE_ACCOUNT,
          audience: TARGET_URL,
        },
      },
      dispatchDeadline: "60s",
    },
  });
  const r = await httpRequest(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body
  });
  return r;
}

(async () => {
  const args = process.argv.slice(2);
  const execute = args.includes("--execute");

  console.log(`Mode: ${execute ? "EXECUTE" : "DRY-RUN"}`);
  console.log(`Project: ${PROJECT_ID}, Queue: ${QUEUE_PATH}`);
  console.log("");

  const token = await getAccessToken();
  const calls = await listAssignedCalls(token);
  console.log(`ASSIGNED 콜 ${calls.length}건 발견`);

  if (calls.length === 0) {
    console.log("→ 백필 대상 없음");
    return;
  }

  const now = Date.now();
  const officeTimeoutCache = new Map();

  let enqueued = 0;
  let alreadyExists = 0;
  let failed = 0;

  for (const call of calls) {
    const driverIdField = call.fields.assignedDriverId;
    const driverId = driverIdField?.stringValue;
    const tsField = call.fields.assignedTimestamp;
    const tsRfc = tsField?.timestampValue;
    if (!driverId || !tsRfc) {
      console.log(`  [skip] ${call.callId}: assignedDriverId/Timestamp 없음`);
      continue;
    }
    const tsMs = new Date(tsRfc).getTime();

    const officeKey = `${call.provinceId}/${call.cityId}/${call.officeId}`;
    let timeoutMin = officeTimeoutCache.get(officeKey);
    if (timeoutMin === undefined) {
      timeoutMin = await getOfficeTimeoutMinutes(token, call.provinceId, call.cityId, call.officeId);
      officeTimeoutCache.set(officeKey, timeoutMin);
    }

    const targetMs = tsMs + timeoutMin * 60 * 1000;
    const delayMs = Math.max(targetMs - now, 1000); // 최소 1초 (이미 만료된 콜은 즉시 발화)
    const scheduleRfc = new Date(now + delayMs).toISOString();
    const taskId = sanitizeTaskId(`${call.callId}_${tsMs}`);

    const payload = {
      provinceId: call.provinceId,
      cityId: call.cityId,
      officeId: call.officeId,
      callId: call.callId,
      expectedDriverId: driverId,
      expectedAssignedTimestampMs: tsMs,
    };

    if (!execute) {
      console.log(`  [dry] ${call.callId}: delay=${(delayMs/1000).toFixed(0)}s, taskId=${taskId}`);
      enqueued++;
      continue;
    }

    const r = await createTask(token, taskId, payload, scheduleRfc);
    if (r.status >= 200 && r.status < 300) {
      enqueued++;
      console.log(`  ✓ ${call.callId}: enqueued (delay=${(delayMs/1000).toFixed(0)}s)`);
    } else if (r.body?.error?.code === 409 || r.body?.error?.status === "ALREADY_EXISTS") {
      alreadyExists++;
      console.log(`  ◌ ${call.callId}: 이미 task 존재 (정상)`);
    } else {
      failed++;
      console.error(`  ✗ ${call.callId}: HTTP ${r.status} ${JSON.stringify(r.body?.error || r.body).substring(0, 200)}`);
    }
  }

  console.log("");
  console.log("==================================================");
  if (execute) {
    console.log(`Enqueued: ${enqueued}, AlreadyExists: ${alreadyExists}, Failed: ${failed}`);
  } else {
    console.log(`DRY-RUN: ${enqueued}건 enqueue 예정 — 실행하려면 --execute 추가`);
  }
})().catch(e => { console.error("FATAL:", e); process.exit(1); });
