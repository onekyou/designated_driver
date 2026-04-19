/**
 * Firebase Storage + Firestore apk_releases 문서를 한 번에 생성한다.
 * Firebase CLI refresh token 기반 OAuth2로 Storage/Firestore REST API 호출
 * (사용자 토큰이므로 프로젝트 Owner/Editor 권한 시 rules bypass).
 *
 * 사용법:
 *   node scripts/upload-apk-release.js <appName> <version> <filePath> [releaseNotes]
 *
 * 예시:
 *   node scripts/upload-apk-release.js call_manager 1.0.0 \
 *     ../call_manager/app/build/outputs/apk/debug/app-debug.apk "최초 배포"
 */

const fs = require("fs");
const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BUCKET = "calldetector-5d61e.firebasestorage.app";
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

// Firebase CLI 공개 OAuth client (firebase-tools 소스 공개값)
const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

function getRefreshToken() {
  const configPath = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  return require(configPath).tokens.refresh_token;
}

function httpRequest(url, options, body) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => {
        const buf = Buffer.concat(chunks);
        try {
          resolve({ status: res.statusCode, body: JSON.parse(buf.toString()) });
        } catch {
          resolve({ status: res.statusCode, body: buf.toString() });
        }
      });
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

async function getAccessToken() {
  const refreshToken = getRefreshToken();
  const body = `client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}` +
               `&refresh_token=${refreshToken}&grant_type=refresh_token`;
  const result = await httpRequest(
    "https://oauth2.googleapis.com/token",
    { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" } },
    body
  );
  if (!result.body.access_token) {
    throw new Error("OAuth2 token fetch failed: " + JSON.stringify(result.body));
  }
  return result.body.access_token;
}

async function uploadToStorage(accessToken, storagePath, filePath) {
  const fileBuffer = fs.readFileSync(filePath);
  const url = `https://storage.googleapis.com/upload/storage/v1/b/${BUCKET}/o` +
              `?uploadType=media&name=${encodeURIComponent(storagePath)}`;
  return new Promise((resolve, reject) => {
    const req = https.request(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/vnd.android.package-archive",
        "Content-Length": fileBuffer.length,
      },
    }, (res) => {
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => {
        const raw = Buffer.concat(chunks).toString();
        if (res.statusCode >= 400) return reject(new Error(`Storage upload ${res.statusCode}: ${raw}`));
        resolve(JSON.parse(raw));
      });
    });
    req.on("error", reject);
    req.write(fileBuffer);
    req.end();
  });
}

async function firestorePatch(accessToken, docPath, fields, updateMask) {
  const maskParam = updateMask
    ? "?" + updateMask.map(f => `updateMask.fieldPaths=${encodeURIComponent(f)}`).join("&")
    : "";
  const url = `${FIRESTORE_BASE}/${docPath}${maskParam}`;
  const body = JSON.stringify({ fields });
  const result = await httpRequest(url, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
      "Content-Length": Buffer.byteLength(body),
    },
  }, body);
  if (result.status >= 400) throw new Error(`Firestore PATCH ${result.status}: ${JSON.stringify(result.body)}`);
  return result.body;
}

async function firestoreRunQuery(accessToken, structuredQuery) {
  const url = `${FIRESTORE_BASE}:runQuery`;
  const body = JSON.stringify({ structuredQuery });
  const result = await httpRequest(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
      "Content-Length": Buffer.byteLength(body),
    },
  }, body);
  if (result.status >= 400) throw new Error(`Firestore query ${result.status}: ${JSON.stringify(result.body)}`);
  return Array.isArray(result.body) ? result.body : [];
}

async function main() {
  const [, , appName, version, filePath, ...rest] = process.argv;
  const releaseNotes = rest.join(" ");

  if (!appName || !version || !filePath) {
    console.error("Usage: node scripts/upload-apk-release.js <appName> <version> <filePath> [releaseNotes]");
    process.exit(1);
  }
  const absFile = path.resolve(filePath);
  if (!fs.existsSync(absFile)) {
    console.error("File not found:", absFile);
    process.exit(1);
  }

  const fileSize = fs.statSync(absFile).size;
  const storagePath = `apks/${appName}/${appName}_v${version}.apk`;
  const releaseId = `${appName}_v${version}`;
  const sizeMB = (fileSize / 1024 / 1024).toFixed(1);

  console.log(`📦 ${appName} v${version} (${sizeMB}MB) → gs://${BUCKET}/${storagePath}`);
  const token = await getAccessToken();

  console.log("  [1/3] Uploading to Storage ...");
  await uploadToStorage(token, storagePath, absFile);
  console.log("  ✓ Storage uploaded");

  console.log("  [2/3] Flipping existing isLatest=true → false ...");
  const existing = await firestoreRunQuery(token, {
    from: [{ collectionId: "apk_releases" }],
    where: {
      compositeFilter: {
        op: "AND",
        filters: [
          { fieldFilter: { field: { fieldPath: "appName" }, op: "EQUAL", value: { stringValue: appName } } },
          { fieldFilter: { field: { fieldPath: "isLatest" }, op: "EQUAL", value: { booleanValue: true } } },
        ],
      },
    },
  });
  for (const r of existing) {
    if (!r.document) continue;
    const id = r.document.name.split("/").pop();
    if (id === releaseId) continue;
    await firestorePatch(token, `apk_releases/${id}`, { isLatest: { booleanValue: false } }, ["isLatest"]);
    console.log(`    - ${id}.isLatest = false`);
  }

  console.log("  [3/3] Writing apk_releases/" + releaseId + " ...");
  await firestorePatch(token, `apk_releases/${releaseId}`, {
    appName:      { stringValue: appName },
    version:      { stringValue: version },
    storagePath:  { stringValue: storagePath },
    fileSize:     { integerValue: String(fileSize) },
    releaseNotes: { stringValue: releaseNotes },
    isLatest:     { booleanValue: true },
    uploadedAt:   { timestampValue: new Date().toISOString() },
  });
  console.log(`  ✓ Firestore doc written\n✅ Done: ${appName} v${version}\n`);
}

main().catch((e) => { console.error("❌ Error:", e.message); process.exit(1); });
