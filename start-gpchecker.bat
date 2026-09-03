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

node ".check.js" %*
