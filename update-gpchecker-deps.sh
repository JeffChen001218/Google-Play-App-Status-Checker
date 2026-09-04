#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

if ! command -v npm >/dev/null 2>&1; then
  echo "未检测到 npm。请先安装 Node.js 22.12 或更高版本后重试。"
  exit 1
fi

npm install @mradex77/google-play-scraper@latest --save-exact --no-audit --no-fund
echo "解析依赖已更新。请重启 GP Checker 服务后生效。"
