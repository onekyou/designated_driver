/**
 * Cloud Functions 빌드 소스 zip 일회성 청소 (Storage 한도 즉시 회수)
 *
 * Spark plan 5GB 한도의 92%(4.59GB) 사용 → 다음 deploy 시 한도 초과 위험.
 * 30일 이상된 빌드 zip 즉시 삭제로 GB 회수.
 *
 * 사용법:
 *   node functions/scripts/cleanup-old-cf-sources.js                # dry-run (default)
 *   node functions/scripts/cleanup-old-cf-sources.js --execute      # 실 삭제
 *   node functions/scripts/cleanup-old-cf-sources.js --execute --age 60   # 60일 기준
 *
 * 설계:
 *   - diagnose-storage.js 의 firebase-tools refresh token + GCS REST API 패턴 재사용
 *   - asia-northeast3 sources + uploads 두 버킷 대상
 *   - dry-run 기본 (실수 방지). --execute 명시 시만 실 삭제
 */

const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BUCKETS = [
  "gcf-v2-sources-60275310305-asia-northeast3",
  "gcf-v2-uploads-60275310305.asia-northeast3.cloudfunctions.appspot.com",
];

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
        try {
          resolve({ status, body: JSON.parse(data) });
        } catch {
          resolve({ status, body: data });
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
  const r = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  if (!r.body.access_token) throw new Error("Token error: " + JSON.stringify(r.body));
  return r.body.access_token;
}

async function listAllObjects(token, bucket) {
  let pageToken = "";
  const items = [];
  do {
    const url = `https://storage.googleapis.com/storage/v1/b/${bucket}/o?maxResults=1000${pageToken ? `&pageToken=${pageToken}` : ""}`;
    const r = await httpRequest(url, { headers: { Authorization: `Bearer ${token}` } });
    if (r.body.error) throw new Error(`list ${bucket}: ${r.body.error.message}`);
    for (const item of (r.body.items || [])) {
      items.push({
        name: item.name,
        size: Number(item.size || 0),
        timeCreated: new Date(item.timeCreated).getTime(),
      });
    }
    pageToken = r.body.nextPageToken || "";
  } while (pageToken);
  return items;
}

async function deleteObject(token, bucket, name) {
  const encName = encodeURIComponent(name);
  const url = `https://storage.googleapis.com/storage/v1/b/${bucket}/o/${encName}`;
  const r = await httpRequest(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` }
  });
  // 204 No Content = success
  if (r.status >= 400) {
    throw new Error(`delete ${bucket}/${name}: HTTP ${r.status} ${JSON.stringify(r.body)}`);
  }
}

function fmt(bytes) {
  if (bytes > 1024 * 1024 * 1024) return (bytes / 1024 / 1024 / 1024).toFixed(2) + " GB";
  if (bytes > 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + " MB";
  if (bytes > 1024) return (bytes / 1024).toFixed(2) + " KB";
  return bytes + " B";
}

(async () => {
  const args = process.argv.slice(2);
  const execute = args.includes("--execute");
  const ageIdx = args.indexOf("--age");
  const ageDays = ageIdx >= 0 ? Number(args[ageIdx + 1]) : 30;

  if (!Number.isFinite(ageDays) || ageDays < 1) {
    console.error(`Invalid --age value: ${args[ageIdx + 1]} (must be positive integer)`);
    process.exit(1);
  }

  console.log(`Mode: ${execute ? "EXECUTE (실 삭제)" : "DRY-RUN (변경 없음)"}`);
  console.log(`Age threshold: ${ageDays}일 이상된 zip 대상`);
  console.log(`Project: ${PROJECT_ID}`);
  console.log("");

  const token = await getAccessToken();
  const cutoffMs = Date.now() - ageDays * 24 * 60 * 60 * 1000;

  let totalReclaimed = 0;
  let totalDeleted = 0;

  for (const bucket of BUCKETS) {
    console.log(`========== ${bucket} ==========`);
    const items = await listAllObjects(token, bucket);
    const candidates = items.filter(i => i.timeCreated < cutoffMs);
    const reclaimSize = candidates.reduce((sum, i) => sum + i.size, 0);

    console.log(`  Total objects: ${items.length}`);
    console.log(`  Cutoff date: ${new Date(cutoffMs).toISOString()}`);
    console.log(`  Candidates (${ageDays}일+): ${candidates.length} objs, ${fmt(reclaimSize)}`);

    if (candidates.length === 0) {
      console.log(`  → 대상 없음`);
      continue;
    }

    if (!execute) {
      console.log(`  Sample (oldest 5):`);
      const oldest = [...candidates].sort((a, b) => a.timeCreated - b.timeCreated).slice(0, 5);
      for (const c of oldest) {
        console.log(`    [${new Date(c.timeCreated).toISOString().slice(0,10)}] ${fmt(c.size).padStart(10)}  ${c.name.substring(0, 80)}`);
      }
      continue;
    }

    let deleted = 0;
    let reclaimed = 0;
    let failed = 0;
    for (const item of candidates) {
      try {
        await deleteObject(token, bucket, item.name);
        deleted++;
        reclaimed += item.size;
        if (deleted % 10 === 0) {
          process.stdout.write(`\r  Deleted: ${deleted}/${candidates.length} (${fmt(reclaimed)})`);
        }
      } catch (e) {
        failed++;
        console.error(`\n  [skip] ${item.name}: ${e.message}`);
      }
    }
    console.log(`\n  ✓ Deleted: ${deleted} objs (${fmt(reclaimed)}). Failed: ${failed}`);
    totalDeleted += deleted;
    totalReclaimed += reclaimed;
  }

  console.log("");
  console.log("==================================================");
  if (execute) {
    console.log(`Total: ${totalDeleted} objs deleted, ${fmt(totalReclaimed)} reclaimed`);
  } else {
    console.log("DRY-RUN 완료 — 실제 삭제하려면 --execute 추가");
  }
})().catch(e => { console.error("FATAL:", e); process.exit(1); });
