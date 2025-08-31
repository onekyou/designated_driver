@echo off
cd C:\app_dev\designated_driver\functions
echo Setting AGORA_APP_CERTIFICATE secret...
echo d4109290198749419a44bcb23a6a05c5| firebase functions:secrets:set AGORA_APP_CERTIFICATE
echo Secret set successfully!