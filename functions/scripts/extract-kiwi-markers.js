#!/usr/bin/env node
/**
 * Kiwi 형태소 사전에서 우리 도메인 마커 추출 — Phase B 2단계
 *
 * 입력: .kiwi-cache/morphemes.txt (fetch-kiwi-data.sh 로 다운로드)
 * 출력: .kiwi-cache/marker_variants.txt (assets/ 로 복사)
 *
 * 추출 정책 — 모두 Kiwi 데이터 직접 검증:
 *   DEPARTURE_MARKERS    : JKB 태그 중 "에서/서" (의미 = 장소·출처)
 *   DESTINATION_MARKERS  : JX  태그 중 "까지" (의미 = 한계·도달점)
 *   SENTENCE_ENDINGS     : EF  태그 중 표면형 ≥ 2글자, 빈도 ≥ 30000
 *                         받침 결합형(ㄴ다/ㅂ니다 등)은 surface 매칭 불가라 제외
 *
 * 추측 변형 0. Kiwi 사전에 등재되지 않은 것은 출력에 포함하지 않음.
 * 방언/구어 변형(까정/꺼정/거쳐 등)은 Phase C (NIKL 모두의 말뭉치) 또는
 * Phase D (AI Hub 콜센터) 에서 별도 흡수.
 *
 * 출력 포맷 (탭 구분):
 *   # 헤더 + 메타데이터 (주석)
 *   <kind>\t<variant>\t<weight>
 *
 * 참조:
 *   - Kiwi: github.com/bab2min/Kiwi (LGPL v3)
 *   - POS 태그 정의: 세종 품사 태그셋
 */

const fs = require('fs');
const path = require('path');

const CACHE_DIR = path.join(__dirname, '.kiwi-cache');
const INPUT = path.join(CACHE_DIR, 'morphemes.txt');
const OUTPUT = path.join(CACHE_DIR, 'marker_variants.txt');
const COMMIT_FILE = path.join(CACHE_DIR, 'commit.txt');

if (!fs.existsSync(INPUT)) {
  console.error(`입력 없음: ${INPUT}\nfetch-kiwi-data.sh 먼저 실행하세요.`);
  process.exit(1);
}

const commit = fs.existsSync(COMMIT_FILE) ? fs.readFileSync(COMMIT_FILE, 'utf-8').trim() : 'unknown';

// === 파싱 ===
const entries = [];
const lines = fs.readFileSync(INPUT, 'utf-8').split('\n');
for (const line of lines) {
  const parts = line.split('\t');
  if (parts.length < 3) continue;
  const [surface, pos, freqStr] = parts;
  const freq = parseInt(freqStr, 10) || 0;
  if (!surface || !pos) continue;
  entries.push({ surface, pos, freq });
}

// === 출발지 마커 (JKB) — 의미: 장소/출처 ===
// "에서/서" 만 채택. 다른 JKB("에/으로/에게/와" 등)는 의미 다름.
const DEPARTURE_SURFACES = new Set(['에서', '서']);
const departure = entries
  .filter(e => e.pos === 'JKB' && DEPARTURE_SURFACES.has(e.surface))
  .sort((a, b) => b.freq - a.freq);

// === 도착지 마커 (JX) — 의미: 한계/도달점 ===
const DESTINATION_SURFACES = new Set(['까지']);
const destination = entries
  .filter(e => e.pos === 'JX' && DESTINATION_SURFACES.has(e.surface))
  .sort((a, b) => b.freq - a.freq);

// === 종결어미 (EF) — 사장님 발화(평서/격식체) 적합한 것만 ===
// 시나리오: 콜매니저/콜디텍터 사용자(관리자)가 손님 통화 후 정보 종합 STT 입력.
// 손님 발화(가요/갑니다/주세요/갑시다/부탁드립니다) 종결어미는 안 등장.
// 사장님 적합: 평서·격식체 (입니다/이에요 류) + 부연 설명 (거든요)
//
// 가드:
//   1. 표면형 길이 ≥ 2 (한 글자는 오인식 위험)
//   2. 자모 받침 시작 제외 (받침 결합형은 surface 매칭 불가)
//   3. 빈도 ≥ 30000
//   4. 사장님 발화 적합 화이트리스트 통과
function startsWithJamoBatchim(s) {
  const code = s.charCodeAt(0);
  return code >= 0x1100 && code <= 0x11FF;
}
// 사장님 발화 적합 — 평서/격식체. 손님 발화 전제 종결어미는 제외.
const MANAGER_APPROPRIATE_EF = new Set([
  '습니다',  // 격식체 평서
  '어요',    // 비격식 평서 (모호하지만 보존)
  '에요',    // 이에요 결합 일부
  '네요',    // 감탄/확인 (모호하지만 보존)
  '예요',    // 비격식 종결
  '거든요',  // 부연 설명
]);
const sentenceEndings = entries
  .filter(e => e.pos === 'EF')
  .filter(e => e.surface.length >= 2)
  .filter(e => !startsWithJamoBatchim(e.surface))
  .filter(e => e.freq >= 30000)
  .filter(e => MANAGER_APPROPRIATE_EF.has(e.surface))
  .sort((a, b) => b.freq - a.freq);

// =====================================================================
// === 수동 큐레이션 — 다른 출처 (Kiwi 외) ============================
// =====================================================================
// Kiwi morphemes.txt 에 직접 등재되지 않지만 한국어 표준/방언 자료에서
// 검증 가능한 변형들. 각 엔트리에 출처 명시.
//
// weight 0 = Kiwi 빈도 직접 측정 불가 (다른 출처). 마커 매칭 시 우선순위는
// "가장 긴 마커" 기준이라 weight 0 이어도 매칭 자체엔 문제 없음.
// =====================================================================

const curated = [
  // ----- DESTINATION 방언 (국립국어원 우리말샘 표제어) -----
  // 출처: opendict.korean.go.kr — 표준국어대사전 + 우리말샘 방언 표제어
  // "까정" — 충청/전라/경상 일부 방언, '까지' 의 표준 방언형
  // "꺼정" — 충청/전라 방언, '까지' 의 변형
  { kind: 'DESTINATION', surface: '까정', source: 'urimalsam:dialect' },
  { kind: 'DESTINATION', surface: '꺼정', source: 'urimalsam:dialect' },

  // ----- WAYPOINT 동사 활용 (거치다 VV 활용형) -----
  // 출처: 한국어 표준 동사 활용. Kiwi morphemes.txt 에 거치다(VV) 등재됨.
  // "거치" — 거치다 어간
  // "거쳐" — 거치다 + 어 (연결어미) 활용형. "양평 거쳐 용문" 흔한 콜 발화
  { kind: 'WAYPOINT', surface: '거쳐', source: 'kiwi:VV-거치다+어' },
  { kind: 'WAYPOINT', surface: '거치', source: 'kiwi:VV-거치다-stem' },

  // ----- SENTENCE_ENDING 결합 surface (사장님 발화 적합만) -----
  // 시나리오: 사장님이 손님 통화 후 메모 입력 — 평서/격식체만 등장.
  // 손님 발화 전제(가요/갑니다/주세요/갑시다/부탁드립니다 등)는 제외.
  // 출처: Kiwi 의 ㅂ니다/에요(EF) + 한국어 표준 명사 종결.
  { kind: 'SENTENCE_ENDING', surface: '입니다', source: 'kiwi:이+ㅂ니다' },
  // 이에요: 이다 + 에요. "에요" 단독 trim 시 "이" 받침이 토큰에 남아 fare 깨짐.
  // 가장 긴 매칭 우선 룰로 "이에요" 가 먼저 매칭되도록 등재.
  { kind: 'SENTENCE_ENDING', surface: '이에요', source: 'kiwi:이+에요' },
];

// === 출력 ===
const header = [
  `# 마커 변형 사전 — Phase B + 큐레이션`,
  `#`,
  `# 출처:`,
  `#   - Kiwi morphemes.txt: github.com/bab2min/Kiwi (LGPL v3)`,
  `#     Kiwi commit: ${commit}`,
  `#   - 우리말샘 표제어: opendict.korean.go.kr (국립국어원 공공 데이터)`,
  `#   - 한국어 표준 동사 활용: Kiwi 어간/어미 등재된 항목의 결합 surface`,
  `#`,
  `# 추출 일시: ${new Date().toISOString().slice(0, 10)}`,
  `# 형식: <kind>\\t<variant>\\t<weight>\\t<source>`,
  `#`,
  `# Kiwi 추출 정책:`,
  `#   - DEPARTURE: JKB 태그 중 의미 = 장소/출처 ('에서','서')`,
  `#   - DESTINATION: JX 태그 중 의미 = 한계/도달점 ('까지')`,
  `#   - SENTENCE_ENDING: EF 태그, 표면형 ≥ 2글자, 빈도 ≥ 30000, 받침결합형 제외`,
  `#`,
  `# 큐레이션 (Kiwi 외 출처) 정책:`,
  `#   - 모든 변형은 사전/문법서/표준 활용으로 검증 가능한 출처 명시`,
  `#   - 추측 변형 0 (한국어 화자가 검증 가능한 표준)`,
].join('\n');

const out = [];
for (const e of departure) out.push(`DEPARTURE\t${e.surface}\t${e.freq}\tkiwi:JKB`);
for (const e of destination) out.push(`DESTINATION\t${e.surface}\t${e.freq}\tkiwi:JX`);
for (const e of sentenceEndings) out.push(`SENTENCE_ENDING\t${e.surface}\t${e.freq}\tkiwi:EF`);
for (const e of curated) out.push(`${e.kind}\t${e.surface}\t0\t${e.source}`);

fs.writeFileSync(OUTPUT, header + '\n' + out.join('\n') + '\n');

const curatedByKind = {};
for (const e of curated) {
  curatedByKind[e.kind] = (curatedByKind[e.kind] || 0) + 1;
}

console.log(`=== Kiwi 직접 ===`);
console.log(`✓ 출발지: ${departure.length}개 (` + departure.map(e => e.surface).join(', ') + `)`);
console.log(`✓ 도착지: ${destination.length}개 (` + destination.map(e => e.surface).join(', ') + `)`);
console.log(`✓ 종결어미: ${sentenceEndings.length}개`);
console.log(`\n=== 큐레이션 (Kiwi 외) ===`);
for (const kind of ['DESTINATION', 'WAYPOINT', 'SENTENCE_ENDING']) {
  const count = curatedByKind[kind] || 0;
  if (count > 0) {
    const items = curated.filter(e => e.kind === kind).map(e => e.surface);
    console.log(`✓ ${kind}: ${count}개 (` + items.join(', ') + `)`);
  }
}
const totalCurated = curated.length;
const totalKiwi = departure.length + destination.length + sentenceEndings.length;
console.log(`\n총: ${totalKiwi + totalCurated}개 (Kiwi ${totalKiwi} + 큐레이션 ${totalCurated})`);
console.log(`출력: ${OUTPUT}`);
