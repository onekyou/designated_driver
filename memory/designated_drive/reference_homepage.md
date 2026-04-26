---
name: 홈페이지 (calllink.io.kr) 위치
description: calllink.io.kr 홈페이지 파일 위치와 Firebase Hosting 배포 정보
type: reference
---

- **홈페이지 URL**: https://calllink.io.kr/
- **HTML 파일**: `homepage/public/index.html` (주의: `public/index.html`은 구버전 별도 사이트)
- **Firebase Hosting target**: `callmadang-web` (public 디렉토리: `homepage/public`)
- **배포 명령**: `firebase deploy --only hosting:callmadang-web`
- **.firebaserc**: `callmadang-web` → `callmadang-web` 사이트
- **구버전 사이트**: `public/index.html` → target `calldetector` (calldetector-5d61e.web.app)
