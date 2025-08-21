@echo off
echo ========================================
echo   PHASE 3: Token Refresh Monitor
echo ========================================
echo.
echo Monitoring PTT Phase 3 Token Refresh Logs...
echo Press Ctrl+C to stop
echo.

REM Phase 3 토큰 갱신 로그만 필터링
adb logcat -c
adb logcat PTT_PHASE3_TOKEN_REFRESH:* PTT_PHASE3_TEST:* PTT_PHASE3_INIT:* TokenRefreshWorker:* *:S

REM 상세 로그를 파일로 저장하려면:
REM adb logcat PTT_PHASE3_TOKEN_REFRESH:* PTT_PHASE3_TEST:* PTT_PHASE3_INIT:* TokenRefreshWorker:* *:S > phase3_logs.txt