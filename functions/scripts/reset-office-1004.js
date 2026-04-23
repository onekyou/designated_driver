/**
 * 1004 사무실 정산 제로 베이스 리셋
 *
 * 사용법:
 *   node scripts/reset-office-1004.js            # dry-run (삭제/수정 예정 항목만 출력)
 *   node scripts/reset-office-1004.js --execute  # 실제 실행
 *
 * 대상: provinces/gyeonggi/cities/yangpyeong/offices/RUbeBEvGGYP5wMhJHhMF  (사장님: 1004@naver.com)
 * 삭제: calls, settlementSessions, dailySettlements 서브컬렉션
 * 리셋: designated_drivers 각 문서의 carryOver(0), dailySettlement(삭제), currentCallId(null)
 *        status 는 WAITING 유지 (재로그인 회피)
 * 유지: admins, settings, managerTokens, customers, customerInfo, points 등
 */

const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

const OFFICE_PATH = "provinces/gyeonggi/cities/yangpyeong/offices/RUbeBEvGGYP5wMhJHhMF";
const DELETE_SUBCOLLECTIONS = ["calls", "settlementSessions", "dailySettlements"];

const EXECUTE = process.argv.includes("--execute");

function getRefreshToken() {
  const configPath = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  return require(configPath).tokens.refresh_token;
}

function httpRequest(url, options = {}, body = null) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      let data = "";
      res.on("data", (c) => data += c);
      res.on("end", () => {
        if (res.statusCode >= 400) return reject(new Error(`HTTP ${res.statusCode}: ${data}`));
        try { resolve(data ? JSON.parse(data) : {}); } catch (e) { resolve(data); }
      });
    });
    req.on("error", reject);
    if (body) req.write(typeof body === "string" ? body : JSON.stringify(body));
    req.end();
  });
}

async function getAccessToken() {
  const refreshToken = getRefreshToken();
  const body = `client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&refresh_token=${refreshToken}&grant_type=refresh_token`;
  const res = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", "Content-Length": Buffer.byteLength(body) },
  }, body);
  return res.access_token;
}

async function listDocs(token, collectionPath) {
  const docs = [];
  let pageToken = null;
  do {
    const url = `${BASE_URL}/${collectionPath}?pageSize=300${pageToken ? `&pageToken=${pageToken}` : ""}`;
    const res = await httpRequest(url, { method: "GET", headers: { Authorization: `Bearer ${token}` } });
    if (res.documents) docs.push(...res.documents);
    pageToken = res.nextPageToken;
  } while (pageToken);
  return docs;
}

async function deleteDoc(token, fullName) {
  const url = `https://firestore.googleapis.com/v1/${fullName}`;
  await httpRequest(url, { method: "DELETE", headers: { Authorization: `Bearer ${token}` } });
}

async function patchDriver(token, docPath) {
  // carryOver(0 리셋) + dailySettlement(삭제) + currentCallId(null). status 는 건드리지 않음.
  const updateMask = [
    "carryOver",
    "dailySettlement",
    "currentCallId",
  ].map((f) => `updateMask.fieldPaths=${encodeURIComponent(f)}`).join("&");
  const url = `${BASE_URL}/${docPath}?${updateMask}`;
  const body = {
    fields: {
      carryOver: {
        mapValue: {
          fields: {
            balance: { integerValue: "0" },
            todayAmount: { integerValue: "0" },
            status: { stringValue: "SETTLED" },
            transferredBy: { nullValue: null },
            transferredAt: { nullValue: null },
            lastUpdatedAt: { timestampValue: new Date().toISOString() },
          },
        },
      },
      currentCallId: { nullValue: null },
      // dailySettlement 은 body 에서 생략 → updateMask 에 의해 필드 삭제됨
    },
  };
  await httpRequest(url, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  }, body);
}

(async () => {
  console.log(`\n=== Reset Office 1004 (${EXECUTE ? "EXECUTE" : "DRY-RUN"}) ===`);
  console.log(`Target: ${OFFICE_PATH}\n`);

  const token = await getAccessToken();

  const toDelete = {};
  for (const sub of DELETE_SUBCOLLECTIONS) {
    const docs = await listDocs(token, `${OFFICE_PATH}/${sub}`);
    toDelete[sub] = docs;
    console.log(`[DELETE] ${sub}: ${docs.length}건`);
    if (docs.length > 0 && docs.length <= 15) {
      for (const d of docs) {
        const id = d.name.split("/").pop();
        console.log(`           - ${id}`);
      }
    }
  }

  const drivers = await listDocs(token, `${OFFICE_PATH}/designated_drivers`);
  console.log(`\n[RESET ] designated_drivers: ${drivers.length}명`);
  console.log("           각 문서에 대해: carryOver → 0/SETTLED, dailySettlement → 삭제, currentCallId → null");
  console.log("           (status/approvalStatus/name/email/phone 등은 변경 안 함)");
  for (const d of drivers) {
    const id = d.name.split("/").pop();
    const name = d.fields?.name?.stringValue || "?";
    console.log(`           - ${id} (${name})`);
  }

  if (!EXECUTE) {
    console.log("\n※ dry-run 모드. 실제 적용하려면 --execute 플래그 추가.");
    return;
  }

  console.log("\n--- 실행 시작 ---");
  for (const sub of DELETE_SUBCOLLECTIONS) {
    for (const doc of toDelete[sub]) {
      await deleteDoc(token, doc.name);
    }
    console.log(`  ${sub}: ${toDelete[sub].length}건 삭제 완료`);
  }
  for (const d of drivers) {
    const relPath = d.name.split("/documents/")[1];
    await patchDriver(token, relPath);
  }
  console.log(`  designated_drivers: ${drivers.length}명 리셋 완료`);
  console.log("\n✅ 리셋 완료.");
})().catch((e) => { console.error("ERROR:", e.message); process.exit(1); });
