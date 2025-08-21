@echo off
echo ========================================
echo   PHASE 1: Security Token Monitor
echo ========================================
echo.
echo Monitoring PTT Phase 1 Security Logs...
echo Press Ctrl+C to stop
echo.

REM Phase 1 보안 로그만 필터링
adb logcat -c
adb logcat PTT_PHASE1_SECURITY:* SecureTokenManager:* *:S

REM 상세 로그를 파일로 저장하려면:
REM adb logcat PTT_PHASE1_SECURITY:* SecureTokenManager:* *:S > phase1_logs.txt