/**
 * chatRoom/main/members 백필 (1회용 운영 도구)
 *
 * 배포된 backfillChatMembers callable과 동일 로직을 REST API로 수행.
 * (callable은 클라이언트 ID 토큰이 필요해 CLI에서 직접 호출 불가)
 *
 * 사용법:
 *   node scripts/backfill-chat-members.js <provinceId> <cityId> <officeId>            # dry-run
 *   node scripts/backfill-chat-members.js <provinceId> <cityId> <officeId> --execute  # 실제 실행
 *
 * 예 (1004 사무실):
 *   node scripts/backfill-chat-members.js gyeonggi yangpyeong RUbeBEvGGYP5wMhJHhMF --execute
 *
 * 등록 대상:
 *  1. admins where associatedProvinceId/CityId/OfficeId 일치 → role=MANAGER
 *  2. designated_drivers의 authUid 필드 보유 문서 → role=DESIGNATED_DRIVER
 *  3. pickup_drivers의 authUid 필드 보유 문서 → role=PICKUP_DRIVER
 *
 * 멱등성: PATCH (createDocument 아님) — 이미 존재하면 set merge 효과로 안전하게 재등록.
 */

const path = require("path");
const os = require("os");
const https = require("https");

const PROJECT_ID = "calldetector-5d61e";
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

const args = process.argv.slice(2);
const EXECUTE = args.includes("--execute");
const positional = args.filter((a) => !a.startsWith("--"));
const [provinceId, cityId, officeId] = positional;

if (!provinceId || !cityId || !officeId) {
  console.error("Usage: node scripts/backfill-chat-members.js <provinceId> <cityId> <officeId> [--execute]");
  process.exit(1);
}

function getRefreshToken() {
  const configPath = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  return require(configPath).tokens.refresh_token;
}

function httpRequest(url, options = {}, body = null) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => {
        const text = Buffer.concat(chunks).toString();
        try {
          resolve({ status: res.statusCode, body: JSON.parse(text) });
        } catch {
          resolve({ status: res.statusCode, body: text });
        }
      });
    });
    req.on("error", reject);
    if (body) req.write(typeof body === "string" ? body : JSON.stringify(body));
    req.end();
  });
}

async function getAccessToken() {
  const body = `client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&refresh_token=${getRefreshToken()}&grant_type=refresh_token`;
  const res = await httpRequest("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  }, body);
  if (!res.body.access_token) throw new Error("토큰 획득 실패: " + JSON.stringify(res.body));
  return res.body.access_token;
}

async function listDocs(token, collectionPath) {
  const url = `${BASE_URL}/${collectionPath}?pageSize=300`;
  const res = await httpRequest(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status !== 200) {
    throw new Error(`list 실패 (${collectionPath}): ${res.status} ${JSON.stringify(res.body)}`);
  }
  return res.body.documents || [];
}

async function runQuery(token, structuredQuery) {
  const url = `${BASE_URL}:runQuery`;
  const res = await httpRequest(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  }, { structuredQuery });
  if (res.status !== 200) {
    throw new Error(`runQuery 실패: ${res.status} ${JSON.stringify(res.body)}`);
  }
  return (res.body || []).filter((r) => r.document).map((r) => r.document);
}

async function setMember(token, userId, role) {
  const docPath = `provinces/${provinceId}/cities/${cityId}/offices/${officeId}/chatRoom/main/members/${userId}`;
  const url = `${BASE_URL}/${docPath}?updateMask.fieldPaths=userId&updateMask.fieldPaths=role&updateMask.fieldPaths=joinedAt`;
  const body = {
    fields: {
      userId: { stringValue: userId },
      role: { stringValue: role },
      joinedAt: { timestampValue: new Date().toISOString() },
    },
  };
  const res = await httpRequest(url, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  }, body);
  if (res.status !== 200) {
    throw new Error(`setMember 실패 (${userId}): ${res.status} ${JSON.stringify(res.body)}`);
  }
}

(async () => {
  console.log(`\n=== Backfill chatRoom Members (${EXECUTE ? "EXECUTE" : "DRY-RUN"}) ===`);
  console.log(`Target: provinces/${provinceId}/cities/${cityId}/offices/${officeId}\n`);

  const token = await getAccessToken();
  const officePath = `provinces/${provinceId}/cities/${cityId}/offices/${officeId}`;

  // 1. 매니저 (admins where associated{X}Id 일치) — runQuery로 정확하게
  const managerDocs = await runQuery(token, {
    from: [{ collectionId: "admins" }],
    where: {
      compositeFilter: {
        op: "AND",
        filters: [
          { fieldFilter: { field: { fieldPath: "associatedProvinceId" }, op: "EQUAL", value: { stringValue: provinceId } } },
          { fieldFilter: { field: { fieldPath: "associatedCityId" }, op: "EQUAL", value: { stringValue: cityId } } },
          { fieldFilter: { field: { fieldPath: "associatedOfficeId" }, op: "EQUAL", value: { stringValue: officeId } } },
        ],
      },
    },
  });
  const managers = managerDocs.map((d) => d.name.split("/").pop());

  // 2. 대리기사 (designated_drivers의 authUid)
  const designatedDocs = await listDocs(token, `${officePath}/designated_drivers`);
  const designated = designatedDocs
    .map((d) => d.fields?.authUid?.stringValue)
    .filter(Boolean);

  // 3. 픽업기사 (pickup_drivers의 authUid)
  const pickupDocs = await listDocs(token, `${officePath}/pickup_drivers`);
  const pickup = pickupDocs
    .map((d) => d.fields?.authUid?.stringValue)
    .filter(Boolean);

  console.log(`[MANAGER]          ${managers.length}명: ${managers.join(", ") || "(없음)"}`);
  console.log(`[DESIGNATED_DRIVER] ${designated.length}명: ${designated.join(", ") || "(없음)"}`);
  console.log(`[PICKUP_DRIVER]    ${pickup.length}명: ${pickup.join(", ") || "(없음)"}`);

  const total = managers.length + designated.length + pickup.length;
  console.log(`\n총 등록 예정: ${total}명`);

  if (!EXECUTE) {
    console.log("\n※ dry-run 모드. 실제 적용하려면 --execute 플래그 추가.");
    return;
  }

  console.log("\n--- 실행 시작 ---");
  for (const uid of managers) {
    await setMember(token, uid, "MANAGER");
    console.log(`  [OK] MANAGER ${uid}`);
  }
  for (const uid of designated) {
    await setMember(token, uid, "DESIGNATED_DRIVER");
    console.log(`  [OK] DESIGNATED_DRIVER ${uid}`);
  }
  for (const uid of pickup) {
    await setMember(token, uid, "PICKUP_DRIVER");
    console.log(`  [OK] PICKUP_DRIVER ${uid}`);
  }
  console.log(`\n=== 완료: ${total}명 등록 ===`);
})().catch((err) => {
  console.error("\n[ERROR]", err.message);
  process.exit(1);
});
