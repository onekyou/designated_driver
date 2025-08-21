@echo off
echo ========================================
echo   PHASE 2: PTT Debouncing Monitor
echo ========================================
echo.
echo Monitoring PTT Phase 2 Debouncing Logs...
echo Press Ctrl+C to stop
echo.

REM Phase 2 디바운싱 로그만 필터링
adb logcat -c
adb logcat PTT_PHASE2_DEBOUNCE:* PTT_PHASE2_TEST:* PTTDebouncer:* *:S

REM 상세 로그를 파일로 저장하려면:
REM adb logcat PTT_PHASE2_DEBOUNCE:* PTT_PHASE2_TEST:* PTTDebouncer:* *:S > phase2_logs.txt