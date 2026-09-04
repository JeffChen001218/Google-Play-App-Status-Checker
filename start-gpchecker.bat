@echo off
setlocal
cd /d "%~dp0"

where node >nul 2>&1
if errorlevel 1 (
  echo Node.js is not installed or is not available in PATH.
  echo Install Node.js and then run this file again.
  pause
  exit /b 1
)

if not exist "node_modules\@mradex77\google-play-scraper" goto install_dependencies
if not exist "node_modules\qrcode" goto install_dependencies
goto start_server

:install_dependencies
echo Installing the locked parser dependency for the first run...
call npm ci --no-audit --no-fund
if errorlevel 1 (
  echo Dependency installation failed.
  pause
  exit /b 1
)

:start_server
node ".check.js" %*
