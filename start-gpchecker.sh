#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

if ! command -v node >/dev/null 2>&1; then
  echo "未检测到 Node.js。请先安装 Node.js 后重试。"
  exit 1
fi

if [ ! -d "node_modules/@mradex77/google-play-scraper" ]; then
  echo "首次启动，正在安装已锁定的解析依赖…"
  npm ci --no-audit --no-fund
fi

exec node .check.js "$@"
