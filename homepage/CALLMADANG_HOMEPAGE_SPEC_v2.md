# 콜마당 홈페이지 설계서 v2
> Claude Code 구현용 상세 스펙 | 시연영상 메인 + QR 추적 시스템 포함

---

## 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 서비스명 | 콜마당 (CallMadang) |
| 도메인 | calllink.io.kr |
| Firebase 프로젝트 | calldetector-5d61e |
| 배포 사이트 | callmadang-web (기존 calldetector-5d61e.web.app 과 분리) |
| 배포 방식 | `firebase deploy --only hosting:homepage` |
| 산출물 | `public/index.html` 단일 파일 |

---

## ⚠️ Firebase 호스팅 사이트 분리 구조

```
Firebase 프로젝트: calldetector-5d61e
├── 사이트 1: calldetector-5d61e.web.app  ← 절대 건드리지 말 것 (구글 플레이 인증용)
└── 사이트 2: callmadang-web              ← 여기에 index.html 배포
                    ↕
              calllink.io.kr 커스텀 도메인 연결
```

**새 사이트 생성 명령어 (최초 1회):**
```bash
firebase hosting:sites:create callmadang-web
firebase target:apply hosting homepage callmadang-web
```

**배포 명령어:**
```bash
firebase deploy --only hosting:homepage
```

**`firebase.json` 설정:**
```json
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
  --navy-mid:      #243352;
  --navy-deep:     #0f1a2e;
  --border:        rgba(255,171,0,0.2);
  --radius:        18px;
  --radius-sm:     10px;
  --shadow:        0 8px 32px rgba(44,62,80,0.10);
  --shadow-gold:   0 8px 32px rgba(255,171,0,0.20);
}
```

---

## 타이포그래피

```html
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@300;400;500;700;900&family=Black+Han+Sans&display=swap" rel="stylesheet">
```

- **헤딩**: `Black Han Sans` (임팩트 있는 한글 디스플레이)
- **본문/UI**: `Noto Sans KR`

---

## 🎯 핵심 전략: QR 추적 시스템

### 개념
명함마다 고유 QR을 부여하여 **누가 뿌린 명함인지 + 얼마나 효과있는지** 추적

```
명함 QR (사무실별 고유) → calllink.io.kr?ref=yangpyeong-01
                                    ↓
                        URL 파라미터 자동 감지
                                    ↓
                        sessionStorage에 ref 저장
                                    ↓
                        가입 폼 제출 시 ref 자동 포함
                                    ↓
                        Firebase Firestore에 저장:
                        { 소개사무실: "yangpyeong-01",
                          신규문의: { 이름, 연락처, 지역 },
                          접속시각: timestamp }
```

### QR 추적 JavaScript 코드 (반드시 포함)

```javascript
// ── QR 파라미터 추적 ──────────────────────────
const QR_FORM_ENDPOINT = 'YOUR_APPS_SCRIPT_URL'; // TODO: 실제 URL 삽입

function getRefParam() {
  const params = new URLSearchParams(window.location.search);
  return params.get('ref') || 'direct';
}

// 페이지 로드 시 ref 저장
const refSource = getRefParam();
sessionStorage.setItem('callmadang_ref', refSource);

// ref가 있으면 상단에 배너 표시 (선택적 UX)
if (refSource !== 'direct') {
  const banner = document.getElementById('ref-banner');
  if (banner) {
    banner.textContent = `${refSource} 사무실의 소개로 방문하셨네요 👋`;
    banner.style.display = 'block';
  }
}

// 폼 제출 시 ref 자동 포함
async function submitForm(formData) {
  formData.ref = sessionStorage.getItem('callmadang_ref') || 'direct';
  formData.timestamp = new Date().toISOString();
  formData.userAgent = navigator.userAgent;

  try {
    const res = await fetch(QR_FORM_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(formData)
    });
    if (res.ok) showSuccess();
    else showError();
  } catch (e) {
    showError();
  }
}
```

### Google Sheets 연동 구조 (Apps Script)
```
시트 컬럼:
A: timestamp | B: 사무실명 | C: 대표자 | D: 연락처 | E: 지역
F: 일 콜수   | G: 문의내용 | H: ref(소개출처) | I: 접속기기
```

---

## 페이지 구조

```
1. NAV          — 고정 상단
2. HERO         — 시연영상 메인 ★ (가장 중요)
3. PAIN         — 공감 섹션
4. FEATURES     — 핵심 기능 3가지
5. HOW IT WORKS — 도입 3단계
6. PRICING      — 현재 무료
7. SIGNUP       — 사무실 가입/문의 폼 ★ (QR 추적 포함)
8. INVESTOR     — 투자자/파트너
9. FOOTER
```

---

## 섹션별 상세 스펙

### 1. NAV
- 배경: `--navy` + `backdrop-filter: blur(16px)`
- 좌: `콜마당` 로고 (골드) + `대리운전 관리 플랫폼` 서브텍스트
- 우 링크: `사무실 가입`, `투자자 문의`, `앱 다운로드`
- 모바일: 햄버거 메뉴 (슬라이드 다운)
- 스크롤 50px 이상: `box-shadow` 추가

### 2. HERO ★ (시연영상 메인)
- 배경: `--navy` + 우하단 골드 glow 원형 그라디언트
- **레이아웃 (PC)**: 좌측 60% 텍스트/CTA | 우측 40% 영상
- **레이아웃 (모바일)**: 텍스트 → 영상 → CTA 순서 (수직)

**헤드라인:**
```
전화 한 통,
스마트하게 배차하세요
```

**서브 문구:**
```
콜마당은 대리기사 사무실을 위한
올인원 콜 관리 플랫폼입니다
```

**뱃지:** `🚀 현재 양평 10개 사무실 무료 파일럿 운영중`

**CTA 버튼 2개:**
- Primary (골드): `무료로 시작하기` → `#signup`
- Secondary (아웃라인 화이트): `서비스 알아보기` → `#features`

**시연영상 영역:**
```html
<!-- 영상이 준비된 경우: YouTube embed -->
<div class="video-wrapper">
  <iframe 
    src="https://www.youtube.com/embed/YOUR_VIDEO_ID?autoplay=0&rel=0&modestbranding=1"
    title="콜마당 시연 영상"
    frameborder="0"
    allowfullscreen>
  </iframe>
</div>

<!-- 영상 미준비 시 placeholder (나중에 교체) -->
<div class="video-placeholder">
  <div class="play-icon">▶</div>
  <p>시연 영상 준비중</p>
</div>
```

```css
.video-wrapper {
  position: relative;
  width: 100%;
  padding-bottom: 56.25%; /* 16:9 비율 */
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(0,0,0,0.4);
  border: 2px solid rgba(255,171,0,0.3);
}
.video-wrapper iframe {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
}
```

**스탯 카운터 (카운트업 애니메이션):**
- `10+` 파일럿 사무실
- `0원` 초기 비용
- `1년` 무료 제공

### 3. PAIN (공감 섹션)
- 배경: `--bg-light`
- 헤딩: `이런 불편함, 익숙하지 않으신가요?`
- 3열 카드:
  - 📋 `엑셀·메모장으로 콜 관리... 매번 실수`
  - 💸 `월말 정산이 항상 골치`
  - 📞 `운전 중 전화 조작으로 아찔한 순간`

### 4. FEATURES (핵심 기능)
- 배경: `--white`
- 헤딩: `콜마당 하나로 모든 것을 해결하세요`
- 3열 카드 (hover → translateY(-6px) + shadow-gold):

| 아이콘 | 제목 | 설명 |
|--------|------|------|
| 📱 | 스마트 콜 배차 | 전화 수신 즉시 기사 목록이 뜨고 한 번에 배차 완료 |
| 📊 | 자동 정산 관리 | 일·주·월별 자동 계산. 엑셀 내보내기 지원 |
| 👥 | 기사님 관리 | 출퇴근·실적·등급 관리. 기사 전용 앱 연동 |

### 5. HOW IT WORKS
- 배경: `--navy`
- 헤딩: `도입이 어렵지 않습니다`
- 3단계 (골드 번호 + 연결선):
  1. **신청** — 아래 폼 입력 (5분)
  2. **설치** — 담당자 앱 설치 안내 (당일)
  3. **운영** — 즉시 사용 시작, 1년 무료

### 6. PRICING
- 배경: `--bg-light`
- 헤딩: `지금은 완전 무료입니다`
- 중앙 카드: `₩0 / 월` + 포함 기능 체크리스트 (골드 ✅)
  - 콜 배차 앱 (사무실용)
  - 기사용 앱
  - 고객용 앱
  - 정산 관리
  - 전담 온보딩 지원
- 하단 안내: `파일럿 기간 종료 후 유료 전환 시 사전 안내 드립니다`

### 7. SIGNUP ★ (가입/문의 폼) `id="signup"`
- 배경: `--white`
- 헤딩: `사무실 무료 등록 신청`
- 서브: `담당자가 24시간 내 연락드립니다`

**⚠️ ref 소개 배너 (QR 통해 접속 시만 표시):**
```html
<div id="ref-banner" style="display:none; background:var(--bg-light); 
     border:1px solid var(--border); border-radius:8px; padding:10px 16px;
     color:var(--text-muted); font-size:0.9rem; margin-bottom:20px;">
</div>
```

**폼 필드:**
```html
<input type="hidden" id="ref-input" name="ref" value="direct">
<!-- JS로 sessionStorage에서 자동 주입 -->

사무실 이름        (text, required)
대표자 이름        (text, required)
연락처            (tel, required)
운영 지역          (text, placeholder="예: 경기 양평")
현재 콜 건수/일    (select: ~50건 / 51~100건 / 101건 이상)
문의사항           (textarea, optional)
```

**폼 처리 로직:**
```javascript
document.getElementById('signup-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  
  const formData = {
    officeName:  e.target.officeName.value,
    ownerName:   e.target.ownerName.value,
    phone:       e.target.phone.value,
    region:      e.target.region.value,
    dailyCalls:  e.target.dailyCalls.value,
    message:     e.target.message.value,
    ref:         sessionStorage.getItem('callmadang_ref') || 'direct',
    timestamp:   new Date().toISOString(),
  };

  await submitForm(formData); // 위에서 정의한 함수
});

function showSuccess() {
  document.getElementById('signup-form').style.display = 'none';
  document.getElementById('signup-success').style.display = 'block';
  // 성공 메시지: "신청 완료! 24시간 내 연락드리겠습니다 🎉"
}

function showError() {
  document.getElementById('signup-error').style.display = 'block';
  // 에러 메시지: "전송 실패. 아래 번호로 직접 연락 주세요."
}
```

**제출 버튼:** `신청하기` (골드 배경, 100% 너비, padding 16px)

### 8. INVESTOR `id="investor"`
- 배경: `--navy`
- 좌: 텍스트 | 우: 핵심 지표 카드 (골드 테두리)
- 헤딩: `함께 성장할 파트너를 찾습니다`
- 본문:
  ```
  콜마당은 대한민국 농촌·지방 대리운전 시장의
  디지털 전환을 이끄는 B2B SaaS 플랫폼입니다.
  현재 초기 파일럿을 성공적으로 운영 중이며,
  Series A 투자 및 전략적 파트너십을 모색하고 있습니다.
  ```
- 우측 지표 카드:
  - 타겟: 전국 대리운전 사무실 ~5,701개소
  - 현재: MVP 파일럿 (양평 10개소)
  - 모델: SaaS 구독 + 거래수수료
  - 확장: 대리운전 → 식당 → 부동산 → 동남아
- CTA: `투자자 덱 요청하기` → `mailto:` 링크

### 9. FOOTER
- 배경: `--navy-deep`
- 좌: 로고 + 슬로건 + 사업자 정보 (placeholder)
- 중: 내부 링크 목록
- 우: 연락처
- 카피라이트: `© 2025 콜마당 / 콜링크. All rights reserved.`

---

## 애니메이션 & 인터랙션

### 스크롤 진입 애니메이션
```css
.animate {
  opacity: 0;
  transform: translateY(32px);
  transition: opacity 0.6s ease, transform 0.6s ease;
}
.animate.visible { opacity: 1; transform: translateY(0); }
/* stagger: nth-child마다 transition-delay 0.1s씩 증가 */
```

```javascript
const observer = new IntersectionObserver((entries) => {
  entries.forEach(e => {
    if (e.isIntersecting) e.target.classList.add('visible');
  });
}, { threshold: 0.15 });
document.querySelectorAll('.animate').forEach(el => observer.observe(el));
```

### 숫자 카운트업 (Hero 스탯)
```javascript
function countUp(el, target, duration = 1500) {
  let start = 0;
  const step = timestamp => {
    if (!start) start = timestamp;
    const progress = Math.min((timestamp - start) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3); // easeOutCubic
    el.textContent = Math.floor(eased * target);
    if (progress < 1) requestAnimationFrame(step);
    else el.textContent = target + (el.dataset.suffix || '');
  };
  requestAnimationFrame(step);
}
// Hero 진입 시 트리거
```

### 네비 스크롤
```javascript
window.addEventListener('scroll', () => {
  document.querySelector('nav').classList.toggle('scrolled', window.scrollY > 50);
});
```

### 모바일 햄버거
```javascript
document.getElementById('hamburger').addEventListener('click', () => {
  document.getElementById('nav-menu').classList.toggle('open');
});
```

---

## 반응형 브레이크포인트

```css
/* Mobile First */
/* 기본(~639px): 1열, 영상 전체 너비 */
@media (min-width: 640px)  { /* 2열 일부 */ }
@media (min-width: 1024px) { /* 3열 카드, Hero 좌우 분할 */ }
```

- **Hero**: 모바일에서 텍스트 → 영상 → CTA 수직 스택
- **카드 그리드**: 모바일 1열 → 태블릿 2열 → PC 3열
- **NAV**: 768px 이하 햄버거 메뉴

---

## TODO (나중에 채울 값들)

| 변수 | 위치 | 설명 |
|------|------|------|
| `YOUR_VIDEO_ID` | Hero iframe src | YouTube 시연영상 ID |
| `YOUR_APPS_SCRIPT_URL` | JS 상단 상수 | Google Apps Script 폼 수신 URL |
| `mailto:your@email.com` | Investor CTA | 실제 이메일 |
| 사업자 정보 | Footer | 대표자, 사업자번호, 주소 |
| 앱 다운로드 링크 | NAV + Hero | Play Store URL |

---

## QR 명함 운영 가이드 (Claude Code 구현 범위 외, 참고용)

```
명함 제작 시 사무실별 QR URL:
- 양평 1호: https://calllink.io.kr?ref=yangpyeong-01
- 양평 2호: https://calllink.io.kr?ref=yangpyeong-02
- 여주 1호: https://calllink.io.kr?ref=yeoju-01
- (이름 없이 나눠줄 때): https://calllink.io.kr?ref=field-01

→ QR 생성: qr-code-generator.com 등 무료 사이트에서 URL 입력 → PNG 다운로드
→ Google Sheets에서 ref별 신청 건수 집계 → 어느 사무실 소개가 가장 효과적인지 파악
→ 나중에 리퍼럴 보상 체계로 확장 가능
```

---

## Claude Code 실행 명령

```bash
# 터미널에서:
claude

# 프롬프트:
CALLMADANG_HOMEPAGE_SPEC_v2.md 를 읽고
public/index.html 을 완성해줘.

조건:
- 단일 HTML 파일 (CSS/JS 인라인)
- 외부: Google Fonts CDN만 사용
- 이미지 없이 CSS + 이모지로 시각 요소 표현
- YouTube iframe은 YOUR_VIDEO_ID placeholder로 남겨둘 것
- QR ref 추적 JS 반드시 포함
- 폼 endpoint는 YOUR_APPS_SCRIPT_URL로 남겨둘 것
- 반응형 (모바일 우선)
- 스크롤 애니메이션 포함
```
