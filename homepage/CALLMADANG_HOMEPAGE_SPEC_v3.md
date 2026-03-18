# 콜마당 홈페이지 설계서 v3 (파일럿 버전)
> Claude Code 구현용 | 양평 파일럿 집중 / 투자자 섹션 제외

---

## 이 홈페이지의 목적 (단 하나)

```
명함 QR을 찍은 양평 대리기사 사무실 사장님이
"이거 한번 써볼까?" 하고 신청 버튼을 누르게 만드는 것
```

**타겟**: 양평 지역 대리운전 사무실 사장님 (40~60대)
**액션**: 폼 제출 (이름 + 연락처 + 사무실명)
**나중에**: 양평 파일럿 성공 후 투자자 섹션 포함한 정식 홈페이지로 교체

---

## 프로젝트 정보

| 항목 | 내용 |
|------|------|
| 운영사 | 콜링크 |
| 사업자번호 | 460-08-03296 |
| 소재지 | 경기도 양평군 |
| 도메인 | calllink.io.kr |
| Firebase 프로젝트 | calldetector-5d61e |
| 배포 타겟 사이트 | callmadang-web (기존 사이트와 **반드시 분리**) |
| 산출물 | `public/index.html` 단일 파일 (CSS/JS 인라인) |

---

## ⚠️ Firebase 호스팅 분리 구조 (중요)

```
Firebase 프로젝트: calldetector-5d61e
├── 사이트 1: calldetector-5d61e.web.app  ← 절대 건드리지 말 것 (구글 플레이 인증용)
└── 사이트 2: callmadang-web              ← index.html 여기에만 배포
                    ↕
              calllink.io.kr 연결
```

```bash
# 최초 1회
firebase hosting:sites:create callmadang-web
firebase target:apply hosting homepage callmadang-web

# 배포
firebase deploy --only hosting:homepage
```

```json
// firebase.json
{
  "hosting": [
    {
      "target": "homepage",
      "public": "public",
      "ignore": ["firebase.json", "**/.*"],
      "rewrites": [{ "source": "**", "destination": "/index.html" }]
    }
  ]
}
```

---

## 색상 팔레트

```css
:root {
  --primary:       #FFAB00;
  --primary-dark:  #FF8F00;
  --primary-light: #FFCF66;
  --bg-light:      #FFFBF0;
  --text-dark:     #2C3E50;
  --text-muted:    #5D6D7E;
  --white:         #ffffff;
  --navy:          #1a2640;
  --navy-deep:     #0f1a2e;
  --border:        rgba(255,171,0,0.2);
  --radius:        18px;
  --shadow-gold:   0 8px 32px rgba(255,171,0,0.20);
}
```

## 타이포그래피

```html
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@300;400;500;700;900&family=Black+Han+Sans&display=swap" rel="stylesheet">
```
- 헤딩: `Black Han Sans`
- 본문: `Noto Sans KR`

---

## 페이지 구조 (심플하게)

```
1. NAV      — 고정 상단
2. HERO     — 시연영상 메인 + CTA ★
3. PAIN     — 사장님 공감 (사업계획서 강화 버전)
4. FEATURES — 핵심 차별점 3가지 (사업계획서 반영)
5. SIGNUP   — 신청 폼 ★ (QR 추적 포함)
6. FOOTER   — 사업자 정보
```

> 투자자 섹션, HOW IT WORKS, PRICING 섹션 **제외** (파일럿 단계 불필요)

---

## 섹션별 상세 스펙

### 1. NAV
- 배경: `--navy` + `backdrop-filter: blur(16px)`
- 좌: `콜마당` (골드) + `대리운전 관리 플랫폼`
- 우: `무료 신청하기` 버튼 하나 (→ `#signup`)
- 모바일: 버튼 우측 상단 고정 유지
- 스크롤 50px↑: `box-shadow` 추가

### 2. HERO ★ (시연영상 메인)

**배경**: `--navy` + 우하단 골드 glow

**레이아웃 (PC)**: 좌 55% 텍스트/CTA | 우 45% 영상  
**레이아웃 (모바일)**: 텍스트 → 영상 → CTA 수직

**헤드라인** (`Black Han Sans`, 흰색):
```
전화 한 통으로
모든 배차가 끝납니다
```

**서브**:
```
전화 받기만 하면 콜이 자동 기록됩니다.
배차, 정산, 기사 관리를 하나의 앱으로.
```

**뱃지** (골드 알약형):
```
🚀 양평 지역 사무실 무료 파일럿 신청중
```

**CTA 버튼** 2개:
- Primary (골드): `지금 무료 신청하기` → `#signup`
- Secondary (아웃라인 흰색): `영상으로 보기` → 영상 섹션 스크롤

**시연영상 영역**:
```html
<!-- 영상 준비 후: YouTube ID 교체 -->
<div class="video-wrapper">
  <iframe
    src="https://www.youtube.com/embed/YOUR_VIDEO_ID?rel=0&modestbranding=1"
    title="콜마당 시연 영상"
    frameborder="0"
    allowfullscreen>
  </iframe>
</div>

<!-- 영상 미준비 시 placeholder -->
<div class="video-placeholder">
  <div class="play-btn">▶</div>
  <p>시연 영상 준비중</p>
  <span>실제 배차 과정을 영상으로 확인하세요</span>
</div>
```

```css
.video-wrapper {
  position: relative;
  padding-bottom: 56.25%;
  border-radius: var(--radius);
  overflow: hidden;
  border: 2px solid rgba(255,171,0,0.35);
  box-shadow: 0 24px 60px rgba(0,0,0,0.45);
}
.video-wrapper iframe {
  position: absolute;
  top:0; left:0; width:100%; height:100%;
}
.video-placeholder {
  /* 같은 크기, 골드 테두리, 중앙 재생버튼 아이콘 */
  aspect-ratio: 16/9;
  background: rgba(255,171,0,0.06);
  border: 2px dashed rgba(255,171,0,0.4);
  border-radius: var(--radius);
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 12px; color: var(--primary-light);
}
.play-btn {
  width: 64px; height: 64px;
  background: var(--primary);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 1.5rem; color: var(--navy);
}
```

**스탯 카운터** (카운트업 애니메이션, 화이트/골드):
- `1` 실운영 사무실
- `0원` 파일럿 참여 비용
- `6번→1번` 배차 클릭 수

### 3. PAIN (공감 — 사업계획서 강화 버전)
- 배경: `--bg-light`
- 헤딩: `이런 불편함, 지금도 겪고 계신가요?`
- 3열 카드 (아이콘 + 굵은 제목 + 설명):

```
🚗  운전 중 배차, 하루에도 수십 번
    전화 받고 → 문자 찾고 → 다시 전화하고...
    6번 조작이 사고 위험을 부릅니다

💸  기사님 실수령은 30%뿐
    수수료 25% + 6:4 분배로
    정작 기사님 손에 들어오는 돈이 너무 적습니다

🔒  해지하면 데이터가 사라집니다
    플랫폼을 떠나면 단골 고객 정보도 같이 사라집니다.
    내 고객인데 내 것이 아닌 구조
```

- 카드: 흰 배경, `--border` 테두리, 아이콘 골드 원형 배경
- 하단 전환 문구: `콜마당은 다릅니다 →` (골드, `#features` 앵커)

### 4. FEATURES (차별점 — 사업계획서 핵심 3가지)
- 배경: `--white`
- 헤딩: `콜마당이 다른 이유 3가지`
- 3열 카드, hover → `translateY(-6px)` + `shadow-gold`

```
📲  전화 받는 순간 CRM이 됩니다
    Call-to-Digital
    따로 입력할 필요 없습니다. 전화를 받기만 하면
    발신번호가 자동 기록되고 콜 이력이 쌓입니다.
    학습 비용 제로, 기존 방식 그대로.

🔐  해지해도 데이터는 사장님 것입니다
    데이터 주권 (노드 귀속)
    콜마당을 그만 써도 쌓아온 고객 데이터는
    그대로 사장님 소유입니다.
    플랫폼에 종속되지 않는 유일한 시스템.

📡  인터넷 없어도 작동합니다
    Offline-First
    지하주차장, 산간지역, 통신 불안정 환경에서도
    끊김 없이 동작합니다.
    지방 대리운전 현장을 위해 설계됐습니다.
```

### 5. SIGNUP ★ `id="signup"`
- 배경: `--navy`
- 헤딩 (흰색): `양평 지역 사무실 무료 신청`
- 서브 (골드): `담당자가 직접 방문하여 설치해드립니다`
- 안내 뱃지: `✅ 1년 무료 · ✅ 설치 무료 · ✅ 해지 자유`

**⚠️ ref 소개 배너** (QR 통해 접속 시만 표시):
```html
<div id="ref-banner" style="display:none;
  background:rgba(255,171,0,0.12); border:1px solid var(--border);
  border-radius:8px; padding:10px 16px; color:var(--primary-light);
  font-size:0.9rem; margin-bottom:20px; text-align:center;">
</div>
```

**폼 필드** (간결하게):
```
사무실 이름     (text, required, placeholder="예: 양평 대리운전")
대표자 이름     (text, required)
연락처          (tel, required, placeholder="010-0000-0000")
운영 지역       (text, value="양평", placeholder="예: 양평 용문읍")
현재 콜 수/일   (select: 50건 미만 / 50~100건 / 100건 이상)
문의사항        (textarea, optional, rows=3)
```

**제출 버튼**: `신청하기` (골드, 100% 너비, padding 16px 20px)

**성공 메시지** (폼 숨기고 표시):
```html
<div id="signup-success" style="display:none; text-align:center; padding:40px;">
  <div style="font-size:3rem">🎉</div>
  <h3 style="color:var(--primary); margin:16px 0 8px;">신청 완료!</h3>
  <p style="color:var(--white)">담당자가 24시간 내에 연락드립니다.</p>
</div>
```

**에러 메시지**:
```html
<div id="signup-error" style="display:none; color:#ff6b6b; 
  text-align:center; margin-top:12px; font-size:0.9rem;">
  전송에 실패했습니다. 아래 번호로 직접 연락 주세요.<br>
  <strong style="color:var(--primary)">📞 010-XXXX-XXXX</strong>
</div>
```

### 6. FOOTER
- 배경: `--navy-deep`
- 내용:
```
콜마당 · 콜링크
사업자등록번호: 460-08-03296
경기도 양평군

© 2025 콜링크. All rights reserved.
```
- 우측: 이메일 연락처 (placeholder `contact@calllink.io.kr`)

---

## 🎯 QR 추적 시스템

### 개념
```
명함 QR (사무실별 고유 URL)
→ calllink.io.kr?ref=yangpyeong-01
→ ref 파라미터 자동 감지 & 저장
→ 가입 폼 제출 시 자동 태깅
→ Google Sheets: 소개처 + 신규문의 동시 기록
```

### JS 코드 (페이지 최하단 `<script>` 에 포함)

```javascript
// ═══════════════════════════════════════
// 설정값 (나중에 실제 값으로 교체)
// ═══════════════════════════════════════
const FORM_ENDPOINT = 'YOUR_APPS_SCRIPT_URL'; // TODO
const CONTACT_PHONE = '010-XXXX-XXXX';        // TODO

// ═══════════════════════════════════════
// QR ref 추적
// ═══════════════════════════════════════
const ref = new URLSearchParams(window.location.search).get('ref') || 'direct';
sessionStorage.setItem('cm_ref', ref);

if (ref !== 'direct') {
  const banner = document.getElementById('ref-banner');
  if (banner) {
    banner.textContent = `소개로 방문하셨군요 👋 감사합니다!`;
    banner.style.display = 'block';
  }
}

// ═══════════════════════════════════════
// 폼 제출
// ═══════════════════════════════════════
document.getElementById('signup-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const btn = e.target.querySelector('button[type=submit]');
  btn.textContent = '전송중...';
  btn.disabled = true;

  const data = {
    officeName:  e.target.officeName.value.trim(),
    ownerName:   e.target.ownerName.value.trim(),
    phone:       e.target.phone.value.trim(),
    region:      e.target.region.value.trim(),
    dailyCalls:  e.target.dailyCalls.value,
    message:     e.target.message.value.trim(),
    ref:         sessionStorage.getItem('cm_ref') || 'direct',
    timestamp:   new Date().toLocaleString('ko-KR'),
  };

  try {
    await fetch(FORM_ENDPOINT, {
      method: 'POST',
      mode: 'no-cors',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    document.getElementById('signup-form').style.display = 'none';
    document.getElementById('signup-success').style.display = 'block';
  } catch {
    document.getElementById('signup-error').style.display = 'block';
    document.getElementById('signup-error').querySelector('strong').textContent =
      `📞 ${CONTACT_PHONE}`;
    btn.textContent = '신청하기';
    btn.disabled = false;
  }
});

// ═══════════════════════════════════════
// 스크롤 애니메이션
// ═══════════════════════════════════════
const observer = new IntersectionObserver((entries) => {
  entries.forEach(e => {
    if (e.isIntersecting) e.target.classList.add('visible');
  });
}, { threshold: 0.12 });
document.querySelectorAll('.animate').forEach(el => observer.observe(el));

// ═══════════════════════════════════════
// 네비 스크롤 shadow
// ═══════════════════════════════════════
window.addEventListener('scroll', () => {
  document.querySelector('nav').classList.toggle('scrolled', window.scrollY > 50);
});

// ═══════════════════════════════════════
// 숫자 카운트업 (Hero 스탯)
// ═══════════════════════════════════════
function countUp(el, target, suffix = '', duration = 1500) {
  let start = null;
  const step = ts => {
    if (!start) start = ts;
    const p = Math.min((ts - start) / duration, 1);
    const ease = 1 - Math.pow(1 - p, 3);
    el.textContent = Math.floor(ease * target) + suffix;
    if (p < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}
// Hero가 visible 될 때 트리거 (IntersectionObserver 활용)
```

---

## 애니메이션 CSS

```css
.animate {
  opacity: 0;
  transform: translateY(28px);
  transition: opacity 0.55s ease, transform 0.55s ease;
}
.animate.visible { opacity: 1; transform: translateY(0); }

/* stagger: 카드 nth-child */
.card:nth-child(1).animate { transition-delay: 0s; }
.card:nth-child(2).animate { transition-delay: 0.1s; }
.card:nth-child(3).animate { transition-delay: 0.2s; }
```

---

## 반응형

```css
/* Mobile first */
@media (min-width: 768px)  { /* 2열 */ }
@media (min-width: 1024px) { /* 3열, Hero 좌우 분할 */ }
```

- Hero 모바일: 영상이 텍스트 아래, 전체 너비
- 카드 그리드: 모바일 1열 → PC 3열
- 폼: 모바일 전체 너비

---

## TODO (나중에 채울 값)

| 상수명 | 위치 | 내용 |
|--------|------|------|
| `YOUR_VIDEO_ID` | Hero iframe src | YouTube 시연영상 ID |
| `YOUR_APPS_SCRIPT_URL` | JS 상단 FORM_ENDPOINT | Google Apps Script URL |
| `010-XXXX-XXXX` | JS + footer | 실제 연락처 |
| `contact@calllink.io.kr` | Footer | 실제 이메일 |

---

## QR 명함 운영 가이드

```
양평 배포용 QR URL 예시:
calllink.io.kr?ref=yangpyeong-01   ← 1번째 사무실에 나눠준 명함
calllink.io.kr?ref=yangpyeong-02
calllink.io.kr?ref=field-event     ← 행사장 배포용
calllink.io.kr                     ← ref 없음 = 직접 접속

→ Google Sheets ref 컬럼으로 어느 경로에서 신청이 많이 들어오는지 파악 가능
```

---

## Claude Code 실행 프롬프트

```
CALLMADANG_HOMEPAGE_SPEC_v3.md 를 읽고
public/index.html 을 완성해줘.

조건:
- 단일 HTML 파일 (CSS/JS 인라인, 외부 의존성 Google Fonts만)
- 이미지 없이 CSS + 이모지로 시각 요소 표현
- YouTube iframe은 YOUR_VIDEO_ID placeholder 유지
- video-placeholder div도 함께 구현 (JS로 영상 없으면 placeholder 표시)
- QR ref 추적 JS 반드시 포함
- FORM_ENDPOINT는 상수로 분리, YOUR_APPS_SCRIPT_URL placeholder 유지
- 반응형 (모바일 우선)
- 스크롤 fadeInUp 애니메이션
- 섹션 순서: NAV → HERO → PAIN → FEATURES → SIGNUP → FOOTER
  (투자자 섹션 없음)
```

---

## 향후 v4 (정식 버전) 에 추가할 것

- 투자자/파트너 섹션
- HOW IT WORKS (도입 3단계)
- PRICING 섹션 (정식 요금제 확정 후)
- 확장 로드맵 (식당, 배달)
- 실제 고객 후기 (파일럿 후)
- 앱 스토어 다운로드 버튼
