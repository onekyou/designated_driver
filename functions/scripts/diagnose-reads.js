/**
 * Firestore 일일 읽기 25만건 + Storage 한도 초과 진단
 *
 * count() aggregation API 사용 (쿼리당 1 read, free)
 * Usage: node functions/scripts/diagnose-reads.js
 */

const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const QUERY_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents:runAggregationQuery`;

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
        try { resolve(JSON.parse(data)); } catch { resolve(data); }
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
  if (!r.access_token) throw new Error("Token error: " + JSON.stringify(r));
  return r.access_token;
}

// count() aggregation — 결과 1 read
async function countCollection(token, parentPath, collectionId) {
  const fullParent = parentPath
    ? `projects/${PROJECT_ID}/databases/(default)/documents/${parentPath}`
    : `projects/${PROJECT_ID}/databases/(default)/documents`;
  const url = `https://firestore.googleapis.com/v1/${fullParent}:runAggregationQuery`;
  const body = JSON.stringify({
    structuredAggregationQuery: {
      structuredQuery: { from: [{ collectionId }] },
      aggregations: [{ alias: "c", count: {} }]
    }
  });
  const result = await httpRequest(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body
  });
  if (Array.isArray(result) && result[0]?.result?.aggregateFields?.c) {
    return Number(result[0].result.aggregateFields.c.integerValue);
  }
  if (result.error) return `ERR:${result.error.message?.substring(0,60)}`;
  return 0;
}

// collectionGroup count() aggregation
async function countCollectionGroup(token, collectionId) {
  const fullParent = `projects/${PROJECT_ID}/databases/(default)/documents`;
  const url = `https://firestore.googleapis.com/v1/${fullParent}:runAggregationQuery`;
  const body = JSON.stringify({
    structuredAggregationQuery: {
      structuredQuery: { from: [{ collectionId, allDescendants: true }] },
      aggregations: [{ alias: "c", count: {} }]
    }
  });
  const result = await httpRequest(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body
  });
  if (Array.isArray(result) && result[0]?.result?.aggregateFields?.c) {
    return Number(result[0].result.aggregateFields.c.integerValue);
  }
  if (result.error) return `ERR:${result.error.message?.substring(0,80)}`;
  return 0;
}

async function listOffices(token, provinceId, cityId) {
  const url = `${BASE_URL}/provinces/${provinceId}/cities/${cityId}/offices?pageSize=100`;
  const r = await httpRequest(url, { headers: { Authorization: `Bearer ${token}` } });
  return (r.documents || []).map(d => ({
    id: d.name.split("/").pop(),
    name: d.fields?.name?.stringValue || "?"
  }));
}

(async () => {
  const token = await getAccessToken();

  console.log("==================================================");
  console.log("== Collection Group counts (전 사무실 합산) ==");
  console.log("==================================================");

  const groupCols = [
    "calls", "designated_drivers", "pickup_drivers",
    "chat_messages", "chat_members",
    "point_transactions", "points",
    "settlementSessions", "dailySettlements",
    "customers", "customerInfo",
    "managerTokens", "withdrawalRequests",
    "tokenRefreshRequests"
  ];

  for (const col of groupCols) {
    const c = await countCollectionGroup(token, col);
    console.log(`  ${col.padEnd(28)}: ${c}`);
  }

  console.log("\n==================================================");
  console.log("== Top-level collections ==");
  console.log("==================================================");

  const rootCols = [
    "shared_calls", "pending_drivers", "admins", "owners",
    "restaurants", "system_config", "deviceCrashes"
  ];
  for (const col of rootCols) {
    const c = await countCollection(token, "", col);
    console.log(`  ${col.padEnd(28)}: ${c}`);
  }

  console.log("\n==================================================");
  console.log("== 양평 사무실별 주요 컬렉션 카운트 ==");
  console.log("==================================================");

  const offices = await listOffices(token, "gyeonggi", "yangpyeong");
  const officeCols = [
    "calls", "designated_drivers", "pickup_drivers",
    "chat_messages", "chat_members",
    "point_transactions", "settlementSessions", "dailySettlements",
    "customers", "customerInfo", "managerTokens", "tokenRefreshRequests"
  ];

  for (const office of offices) {
    console.log(`\n[${office.id}] ${office.name}`);
    const parent = `provinces/gyeonggi/cities/yangpyeong/offices/${office.id}`;
    for (const col of officeCols) {
      const c = await countCollection(token, parent, col);
      console.log(`    ${col.padEnd(26)}: ${c}`);
    }
  }
})().catch(e => { console.error(e); process.exit(1); });
