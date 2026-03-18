# 콜마당 홈페이지 작업 로그

## 현재 상태
- **파일**: `homepage/public/index.html` (단일 파일)
- **상태**: 배포 완료, 정상 운영중
- **URL**: https://callmadang-web.web.app
- **도메인**: `calllink.io.kr` → DNS 전파 대기중 (가비아 TXT 레코드 설정 완료, A 레코드 미설정)

---

## 완료 항목

| 항목 | 상태 | 내용 |
|------|------|------|
| FORM_ENDPOINT | ✅ 완료 | Apps Script 새 배포 URL 적용 |
| CONTACT_PHONE | ✅ 완료 | 010-8145-2011 |
| 폼 → Google Sheets | ✅ 완료 | 한글 정상, `text/plain;charset=utf-8` |
| Firebase 배포 | ✅ 완료 | callmadang-web 사이트 생성 + 배포 |
| Hero 스탯 미사용 코드 정리 | ✅ 완료 | CSS/JS에서 countUp, statsObserver 삭제 |

## 남은 항목

| 항목 | 상태 | 내용 |
|------|------|------|
| calllink.io.kr 도메인 연결 | ⏳ 진행중 | TXT 레코드 설정 완료, DNS 전파 후 A 레코드 설정 필요 |
| YouTube VIDEO_ID | ☐ 미완료 | 영상 촬영 후 교체 |
| SIGNUP 성공 이모지 | ☐ 사소함 | `🎉` → Lucide SVG 미교체 (기능 영향 없음) |

---

## 기술 스택

| 항목 | 선택 | 비고 |
|------|------|------|
| 구조 | 단일 HTML (CSS/JS 인라인) | 외부 의존성 최소화 |
| 폰트 | Google Fonts - Noto Sans KR (300,400,500,700,900) | Black Han Sans 제거 (가독성 문제) |
| 아이콘 | Lucide SVG 인라인 | CDN 미사용, 각 아이콘 직접 삽입 |
| 애니메이션 | IntersectionObserver + `.animate` 클래스 | fadeInUp, stagger delay |
| 폼 제출 | Google Apps Script (`mode: 'no-cors'`, `text/plain;charset=utf-8`) | try/catch 방식 |
| QR 추적 | URL 파라미터 `?ref=xxx` → sessionStorage | 폼 제출 시 함께 전송 |
| 호스팅 | Firebase Hosting (callmadang-web 사이트) | 멀티사이트 구성 |
| 도메인 | calllink.io.kr (연결 진행중) | |

---

## 계정 정보

| 서비스 | 계정 | 용도 |
|--------|------|------|
| Firebase | onekyou71@gmail.com | 호스팅, 프로젝트 (calldetector-5d61e) |
| Google Apps Script + Sheets | calllinkdrive@gmail.com | 신청 폼 데이터 수집 |

---

## Firebase 호스팅 구성

- **프로젝트**: `calldetector-5d61e`
- **멀티사이트**: `firebase.json`에서 배열 형태로 관리
  - `calldetector` → `public/` (Google Play 인증용, 변경 금지)
  - `callmadang-web` → `homepage/public/` (콜마당 홈페이지)
- **배포 명령어**: `firebase deploy --only hosting:callmadang-web`

### Apps Script

- **스프레드시트**: "콜마당 파일럿 신청" (calllinkdrive@gmail.com)
- **배포 URL**: `https://script.google.com/macros/s/AKfycbx5CpDFBSmkavN6sMQUqTj_ZXoaX4JC7m1TJkAF01s_jd1KveUyKlr4_DxtEweRSa1J/exec`
- **주의**: 코드 수정 시 반드시 "배포 관리 → 새 버전"으로 업데이트 필요

---

## 도메인 연결 (진행중)

### 완료된 단계
1. ✅ Firebase Console에서 callmadang-web에 커스텀 도메인 `calllink.io.kr` 추가
2. ✅ 가비아에서 기존 `hosting-site=calldetector-5d61e` TXT 레코드 삭제
3. ✅ 가비아에서 새 TXT 레코드 추가: `@` → `hosting-site=callmadang-web` (TTL 600)

### 남은 단계
4. ⏳ DNS TXT 레코드 전파 대기 (최대 24시간)
5. ☐ Firebase Console에서 인증 확인 후 A 레코드 안내 받기
6. ☐ 가비아에서 A 레코드 설정
7. ☐ SSL 인증서 자동 발급 대기

---

## 섹션 구성 (순서대로)

1. **NAV** - 고정 상단바. "콜마당" + "지역대리운전 관리 플랫폼" + 무료 신청 CTA
2. **HERO** - 메인 비주얼. "통화가 끝나면 바로 배차가 시작됩니다", 6→1클릭, 다이렉트 연결 강조
3. **PAIN** - 페인포인트 3장. 운전 중 배차 위험 / 수기 정산 / 데이터 소실
4. **FEATURES** - 차별점 3장. Call-to-Digital CRM / 데이터 주권(노드 귀속) / 자동 재연결
5. **COMPARE** - 기존 플랫폼 vs 콜마당 비교 (좌우 카드, VS 중앙)
6. **APPS** - 4개 앱 소개 (콜 디텍터 / 콜 매니저 / 기사 앱 / 콜마당 손님앱)
7. **SIGNUP** - 파일럿 신청 폼 (사무실 이름, 대표자, 연락처, 지역, 일일 콜 수, 문의사항)
8. **FOOTER** - 사업자 정보, 이메일

---

## 핵심 메시징

### 정체성 (반드시 유지)
- **노드 귀속형** 아키텍처: 중앙 플랫폼 없이 사무실과 손님 다이렉트 연결
- **데이터 주권**: 해지해도 고객 데이터는 사장님 소유
- **Call-to-Digital**: 전화만 받으면 자동으로 CRM이 됨 (학습 비용 제로)

### 주의사항
- "인터넷 없이 작동" ❌ → "연결이 끊겨도 자동 재연결" ✅
- GPS 미사용 (기사앱에서 GPS 관련 언급 금지)
- "콜 키퍼" ❌ → "콜 디텍터" ✅ (정식 앱 이름)
- 콜마당 손님앱 차별점: 앱 가입 후 전화로 호출해도 앱호출과 동일 혜택 적용

### 대상 타겟
- 양평 지역 대리운전 사무실 사장님 (40~60대)
- IT에 익숙하지 않은 사용자 → 쉽고 직관적인 표현 사용

---

## 디자인 결정 사항

### 폰트
- 모든 제목: `Noto Sans KR weight 900`, `letter-spacing: -0.02em`
- Black Han Sans는 "뭉쳐보여 가독성 떨어진다"는 피드백으로 제거

### 아이콘
- 이모지 → Lucide SVG 전면 교체 (전문적이고 일관된 디자인)
- SVG는 parent CSS로 스타일링 (`stroke: var(--primary-dark)`)
- hero-badge: sparkles 아이콘 (별표)

### 색상 체계
- Primary: `#FFAB00` (골드)
- Navy: `#1a2640` (배경)
- COMPARE 섹션: navy 배경에 old(회색 반투명) vs new(골드 반투명) 카드

### 반응형 브레이크포인트
- `639px`: 모바일 (1열 그리드)
- `768px`: COMPARE 섹션 세로 전환
- `1023px`: 타블렛 (HERO 세로, 2열 그리드)

---

## QR 코드 활용
- 전단지에 `https://calllink.io.kr/?ref=yangpyeong-01` 형태로 QR 인쇄
- ref 값으로 유입 경로 추적 가능
- 방문 시 ref 배너가 폼 위에 표시됨

---

## 파일 구조
```
homepage/
├── public/
│   └── index.html                  ← 메인 랜딩 페이지
├── CALLMADANG_HOMEPAGE_SPEC_v2.md  ← 초기 스펙 (9섹션, 참고용)
├── CALLMADANG_HOMEPAGE_SPEC_v3.md  ← 최종 스펙 (6섹션, 파일럿 중심)
├── APPS_SCRIPT_SETUP.md            ← Apps Script 설정 가이드
└── HOMEPAGE_WORK_LOG.md            ← 이 파일
```
