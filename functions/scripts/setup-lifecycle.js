/**
 * GCS 버킷 lifecycle 정책 설정 (미래 codebase 분리 후 자동 청소 안전망)
 *
 * 사용법:
 *   node functions/scripts/setup-lifecycle.js          # dry-run (default)
 *   node functions/scripts/setup-lifecycle.js --execute   # 실 적용
 *
 * 동작:
 *   - asia-northeast3 sources + uploads 두 버킷에 30일 자동 삭제 lifecycle PATCH
 *   - 정책 정의: functions/scripts/lifecycle-cf-sources.json
 *   - dry-run 시: 현재 정책 + 적용 예정 정책만 보여줌
 *   - 즉시 효과 X (현재 모든 zip이 30일 이내). monorepo 분리 후 의미 발생
 */

const path = require("path");
const os = require("os");
const fs = require("fs");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BUCKETS = [
  "gcf-v2-sources-60275310305-asia-northeast3",
  "gcf-v2-uploads-60275310305.asia-northeast3.cloudfunctions.appspot.com",
];

const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

const LIFECYCLE_FILE = path.join(__dirname, "lifecycle-cf-sources.json");

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

async function getBucketLifecycle(token, bucket) {
  const url = `https://storage.googleapis.com/storage/v1/b/${bucket}?fields=name,lifecycle`;
  const r = await httpRequest(url, { headers: { Authorization: `Bearer ${token}` } });
  if (r.body.error) throw new Error(`get ${bucket}: ${r.body.error.message}`);
  return r.body;
}

async function setBucketLifecycle(token, bucket, lifecycle) {
  const url = `https://storage.googleapis.com/storage/v1/b/${bucket}`;
  const r = await httpRequest(url, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ lifecycle })
  });
  if (r.status >= 400) {
    throw new Error(`PATCH ${bucket}: HTTP ${r.status} ${JSON.stringify(r.body)}`);
  }
  return r.body;
}

(async () => {
  const args = process.argv.slice(2);
  const execute = args.includes("--execute");

  const desired = JSON.parse(fs.readFileSync(LIFECYCLE_FILE, "utf-8")).lifecycle;
  console.log(`Mode: ${execute ? "EXECUTE (PATCH)" : "DRY-RUN (조회만)"}`);
  console.log(`Project: ${PROJECT_ID}`);
  console.log(`Lifecycle 정책 (적용 예정):`);
  console.log(JSON.stringify(desired, null, 2));
  console.log("");

  const token = await getAccessToken();

  for (const bucket of BUCKETS) {
    console.log(`========== ${bucket} ==========`);

    const current = await getBucketLifecycle(token, bucket);
    console.log(`  현재 lifecycle:`);
    if (current.lifecycle) {
      console.log(JSON.stringify(current.lifecycle, null, 2).split("\n").map(l => "    " + l).join("\n"));
    } else {
      console.log(`    (없음)`);
    }

    if (!execute) {
      console.log(`  → DRY-RUN: PATCH 안 함`);
      continue;
    }

    await setBucketLifecycle(token, bucket, desired);
    console.log(`  ✓ lifecycle 적용 완료`);

    const verify = await getBucketLifecycle(token, bucket);
    console.log(`  적용 후:`);
    console.log(JSON.stringify(verify.lifecycle, null, 2).split("\n").map(l => "    " + l).join("\n"));
  }

  console.log("");
  if (!execute) {
    console.log("DRY-RUN 완료 — 실제 적용하려면 --execute 추가");
  } else {
    console.log("✓ 모든 버킷 lifecycle 설정 완료");
  }
})().catch(e => { console.error("FATAL:", e); process.exit(1); });
