---
name: feedback-stash-dependency-split
description: stash로 변경을 분리할 때 src 코드와 의존성(package.json)을 갈라놓으면 빌드/배포가 깨진다. 오진단 전에 빌드 에러 원문부터 볼 것.
metadata:
  type: feedback
---

# stash 의존성 분리로 deploy 부팅 크래시 (2026-05-31 실제 발생)

**사건**: 정산 검증을 위해 정산-무관 미커밋 변경을 `git stash push -- functions ...`로 분리. 이때 `functions/package.json`의 `express`/`express-basic-auth` 의존성 추가분이 stash로 함께 빠졌다. 그런데 **커밋된** `functions/src/index.ts:52-53`은 이미 그 모듈을 import(homepageGate 인증). → src와 의존성 불일치 → GCP functions deploy 시 컨테이너 8080 listen 실패 → 41개 함수 전부 healthcheck fail.

**클코 오진단**: 처음에 "정산 commit 2(`3b3d808b`)가 package.json을 손상시켰다"고 단정하고 plan까지 작성. 틀렸다. `git checkout ccaf6d5d`는 invalid ref로 실패했는데도 그걸 못 보고 진행. 진짜 원인은 **로컬 tsc 빌드 에러 원문**(`TS2307: Cannot find module 'express-basic-auth'`)을 봤을 때야 드러났다.

**Why**: stash는 파일 단위로 변경을 가르는데, *코드와 그 코드가 의존하는 manifest(package.json)가 서로 다른 트랙*이면 갈라진다. 한쪽만 워킹트리에 남으면 빌드가 깨진 채로 deploy까지 간다. 게다가 build 산출물이 tracked인 repo라 lib/도 함께 흔들린다.

**How to apply**:
1. **`stash push -- <경로>`로 가르기 전에**, 그 경로의 변경이 *다른 경로의 커밋된 코드와 의존 관계*인지 확인. 특히 `package.json`/`package-lock.json`/`build.gradle` 같은 manifest는 함부로 분리하지 말 것 — src가 require하는 의존성이 같이 빠진다.
2. **deploy/build 실패 시 오진단 금지.** GCP "container healthcheck failed"는 증상일 뿐. **로컬에서 같은 빌드/부팅을 재현**(`npm run build` → `firebase emulators:exec --only functions`)해 *에러 원문*을 먼저 확보. 추측으로 plan 쓰지 말 것.
3. **`git checkout <ref>`가 실패(invalid ref)하면 멈추고 확인.** 실패했는데 "복원됐다" 가정하고 진행하면 측정이 오락가락한다. 명령 exit/출력 먼저 읽을 것.
4. **production deploy 전 로컬 부팅 게이트**: GCP에서 실패할 조건(컨테이너 부팅)을 로컬 에뮬레이터로 먼저 통과시켜라. 통과 못 하면 deploy 안 함.
5. 분리한 stash는 *어떤 파일이 들어갔는지* `git stash show --stat`로 기록에 남길 것. 나중에 무엇을 빼냈는지 추적 가능해야 함.

관련: [[feedback-no-false-attribution]] (추측을 사실처럼 다루지 말 것 — 본 건도 오진단이 같은 뿌리)
