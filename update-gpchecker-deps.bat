@echo off
setlocal
cd /d "%~dp0"

where npm >nul 2>&1
if errorlevel 1 (
  echo npm is not installed or is not available in PATH.
  echo Install Node.js 22.12 or newer and then run this file again.
  pause
  exit /b 1
)

call npm install @mradex77/google-play-scraper@latest --save-exact --no-audit --no-fund
if errorlevel 1 (
  echo Dependency update failed.
  pause
  exit /b 1
)

echo Parser dependency updated. Restart GP Checker to apply it.
pause
