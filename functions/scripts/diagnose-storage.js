/**
 * Firebase Storage 사용량 진단
 * Usage: node functions/scripts/diagnose-storage.js
 */
const admin = require("firebase-admin");
const path = require("path");
const os = require("os");

// firebase-tools refresh token 으로 application-default 사용 시도가 어려우므로
// gcloud application-default credentials 또는 GOOGLE_APPLICATION_CREDENTIALS 필요.
// 대안: REST API로 Storage objects:list 호출 (firebase-tools refresh token 사용)
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BUCKETS_AUTO = true; // 자동 발견

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

async function listBucket(token, bucket, prefix = "") {
  let pageToken = "";
  let totalSize = 0;
  let totalCount = 0;
  const prefixSizes = {};

  do {
    const url = `https://storage.googleapis.com/storage/v1/b/${bucket}/o?maxResults=1000${pageToken ? `&pageToken=${pageToken}` : ""}${prefix ? `&prefix=${prefix}` : ""}`;
    const r = await httpRequest(url, { headers: { Authorization: `Bearer ${token}` } });
    if (r.error) {
      return { error: r.error.message };
    }
    const items = r.items || [];
    for (const item of items) {
      const size = Number(item.size || 0);
      totalSize += size;
      totalCount++;
      const topPrefix = item.name.split("/")[0];
      prefixSizes[topPrefix] = (prefixSizes[topPrefix] || { count: 0, size: 0 });
      prefixSizes[topPrefix].count++;
      prefixSizes[topPrefix].size += size;
    }
    pageToken = r.nextPageToken || "";
  } while (pageToken);

  return { totalSize, totalCount, prefixSizes };
}

function fmt(bytes) {
  if (bytes > 1024 * 1024 * 1024) return (bytes / 1024 / 1024 / 1024).toFixed(2) + " GB";
  if (bytes > 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + " MB";
  if (bytes > 1024) return (bytes / 1024).toFixed(2) + " KB";
  return bytes + " B";
}

async function listProjectBuckets(token) {
  const url = `https://storage.googleapis.com/storage/v1/b?project=${PROJECT_ID}`;
  const r = await httpRequest(url, { headers: { Authorization: `Bearer ${token}` } });
  if (r.error) return { error: r.error.message };
  return (r.items || []).map(b => b.name);
}

(async () => {
  const token = await getAccessToken();

  console.log("==== Buckets ====");
  const buckets = await listProjectBuckets(token);
  if (buckets.error) {
    console.log("ERROR listing buckets:", buckets.error);
    return;
  }
  console.log(buckets);

  for (const bucket of buckets) {
    console.log(`\n========== ${bucket} ==========`);
    const r = await listBucket(token, bucket);
    if (r.error) {
      console.log("  ERROR:", r.error);
      continue;
    }
    console.log(`  Total: ${r.totalCount} objects, ${fmt(r.totalSize)}`);
    console.log(`\n  Top-level prefixes (size desc):`);
    const sorted = Object.entries(r.prefixSizes).sort((a,b) => b[1].size - a[1].size);
    for (const [p, info] of sorted) {
      console.log(`    ${p.padEnd(40)}  ${String(info.count).padStart(6)} objs  ${fmt(info.size).padStart(10)}`);
    }
  }
})().catch(e => { console.error(e); process.exit(1); });
