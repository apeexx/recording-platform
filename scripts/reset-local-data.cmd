@echo off
setlocal
cd /d "%~dp0.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-local-data.ps1" -ConfirmDatabase "%~1"
exit /b %ERRORLEVEL%
