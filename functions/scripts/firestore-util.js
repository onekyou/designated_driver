/**
 * Firestore REST API 유틸리티
 *
 * 사용법:
 *   node scripts/firestore-util.js list <collectionPath>
 *   node scripts/firestore-util.js get <documentPath>
 *   node scripts/firestore-util.js audit <documentPath>   (서브컬렉션 조사)
 *
 * 예시:
 *   node scripts/firestore-util.js list provinces
 *   node scripts/firestore-util.js list provinces/gyeonggi/cities
 *   node scripts/firestore-util.js get provinces/gyeonggi
 *   node scripts/firestore-util.js audit provinces/gyeonggi/cities/yangpyeong/offices/nEkf0X9g3LZtRX94Mrzu
 */

const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

// Firebase CLI client credentials (공개값 - firebase-tools 소스코드에 있음)
const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

function getRefreshToken() {
  const configPath = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  const config = require(configPath);
  return config.tokens.refresh_token;
}

function httpRequest(url, options = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      let data = "";
      res.on("data", (chunk) => data += chunk);
      res.on("end", () => {
        try {
          resolve(JSON.parse(data));
        } catch {
          resolve(data);
        }
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
  const result = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  if (!result.access_token) {
    throw new Error("Failed to get access token: " + JSON.stringify(result));
  }
  return result.access_token;
}

function parseValue(val) {
  if (val.stringValue !== undefined) return val.stringValue;
  if (val.integerValue !== undefined) return Number(val.integerValue);
  if (val.doubleValue !== undefined) return val.doubleValue;
  if (val.booleanValue !== undefined) return val.booleanValue;
  if (val.timestampValue !== undefined) return val.timestampValue;
  if (val.nullValue !== undefined) return null;
  if (val.mapValue) {
    const obj = {};
    for (const [k, v] of Object.entries(val.mapValue.fields || {})) {
      obj[k] = parseValue(v);
    }
    return obj;
  }
  if (val.arrayValue) {
    return (val.arrayValue.values || []).map(parseValue);
  }
  return val;
}

function parseDocument(doc) {
  const id = doc.name.split("/").pop();
  const fields = {};
  for (const [k, v] of Object.entries(doc.fields || {})) {
    fields[k] = parseValue(v);
  }
  return { id, ...fields };
}

async function listDocuments(collectionPath) {
  const token = await getAccessToken();
  const url = `${BASE_URL}/${collectionPath}?pageSize=100`;
  const result = await httpRequest(url, {
    headers: { Authorization: `Bearer ${token}` }
  });

  if (result.error) {
    console.error("Error:", result.error.message);
    return;
  }

  const docs = result.documents || [];
  console.log(`\n=== ${collectionPath}: ${docs.length} docs ===\n`);
  docs.forEach((doc) => {
    const parsed = parseDocument(doc);
    const { id, ...rest } = parsed;
    // 주요 필드만 한 줄로 표시
    const summary = Object.entries(rest)
      .filter(([k]) => ["name", "status", "officeId", "targetOfficeId", "phoneNumber", "driverType", "approvalStatus", "email"].includes(k))
      .map(([k, v]) => `${k}=${v}`)
      .join(", ");
    console.log(`  ${id}: ${summary || JSON.stringify(rest).substring(0, 120)}`);
  });

  if (docs.length === 0) {
    console.log("  (empty)");
  }
}

async function getDocument(docPath) {
  const token = await getAccessToken();
  const url = `${BASE_URL}/${docPath}`;
  const result = await httpRequest(url, {
    headers: { Authorization: `Bearer ${token}` }
  });

  if (result.error) {
    console.error("Error:", result.error.message);
    return;
  }

  const parsed = parseDocument(result);
  console.log(`\n=== ${docPath} ===\n`);
  console.log(JSON.stringify(parsed, null, 2));
}

async function auditSubcollections(docPath) {
  const token = await getAccessToken();

  // 알려진 서브컬렉션 목록
  const knownCollections = [
    "designated_drivers", "pickup_drivers", "calls", "customers",
    "customerInfo", "settings", "dailySettlements", "settlementSessions",
    "points", "point_transactions", "pointTransactions", "customerPoints",
    "attributions", "bannerAds", "managerTokens", "withdrawalRequests"
  ];

  console.log(`\n=== Audit: ${docPath} ===\n`);

  for (const col of knownCollections) {
    const url = `${BASE_URL}/${docPath}/${col}?pageSize=1`;
    const result = await httpRequest(url, {
      headers: { Authorization: `Bearer ${token}` }
    });

    const count = result.documents ? result.documents.length : 0;
    const hasMore = result.nextPageToken ? "+" : "";
    if (count > 0) {
      console.log(`  [O] ${col}: ${count}${hasMore} docs`);
    } else {
      console.log(`  [ ] ${col}: empty`);
    }
  }
}

// CLI
const [,, command, ...args] = process.argv;
const docPath = args.join(" ");

if (!command || !docPath) {
  console.log("Usage:");
  console.log("  node scripts/firestore-util.js list <collectionPath>");
  console.log("  node scripts/firestore-util.js get <documentPath>");
  console.log("  node scripts/firestore-util.js audit <documentPath>");
  process.exit(1);
}

(async () => {
  try {
    switch (command) {
      case "list":
        await listDocuments(docPath);
        break;
      case "get":
        await getDocument(docPath);
        break;
      case "audit":
        await auditSubcollections(docPath);
        break;
      default:
        console.error(`Unknown command: ${command}`);
        process.exit(1);
    }
  } catch (e) {
    console.error("Error:", e.message);
    process.exit(1);
  }
})();
