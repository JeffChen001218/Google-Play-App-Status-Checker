#!/usr/bin/env node

/**
 * GPChecker 的本地同源服务：
 * - 仅监听 127.0.0.1，不对局域网公开；
 * - 仅转发 play.google.com/store/apps/details；
 * - 同时托管 GPChecker.html，浏览器无需跨域访问 Google Play。
 */
const http = require("http");
const fs = require("fs/promises");
const path = require("path");

const host = "127.0.0.1";
const port = Number(process.argv[2]) || 18765;
const htmlPath = path.join(__dirname, "GPChecker.html");
const packageNamePattern = /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/;
const maxNotificationBodyBytes = 64 * 1024;

function send(response, status, body, headers = {}) {
  response.writeHead(status, {
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    ...headers,
  });
  response.end(body);
}

function validatePlayUrl(rawUrl) {
  const url = new URL(rawUrl);
  if (url.protocol !== "https:" || url.hostname !== "play.google.com" || url.pathname !== "/store/apps/details") {
    throw new Error("只允许访问 Google Play 应用详情页。");
  }
  const packageName = url.searchParams.get("id") || "";
  if (!packageNamePattern.test(packageName)) throw new Error("无效的应用包名。");
  // 更新时间解析依赖英文页面的固定日期格式（例如 Aug 29, 2026）。
  url.searchParams.set("hl", "en_US");
  return url;
}

function validateFeishuWebhookUrl(rawUrl) {
  const url = new URL(rawUrl);
  if (url.protocol !== "https:" || url.hostname !== "open.feishu.cn" || !/^\/open-apis\/bot\/v2\/hook\/[A-Za-z0-9-]+$/.test(url.pathname)) {
    throw new Error("当前仅支持有效的飞书自定义机器人 Webhook URL。");
  }
  return url;
}

function readJsonBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    request.on("data", chunk => {
      size += chunk.length;
      if (size > maxNotificationBodyBytes) {
        reject(new Error("通知请求过大。"));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("error", reject);
    request.on("end", () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(new Error("通知请求必须是 JSON。"));
      }
    });
  });
}

async function forwardFeishuNotification(targetUrl, payload) {
  if (!payload || payload.msg_type !== "text" || typeof payload.content?.text !== "string" || !payload.content.text.trim()) {
    throw new Error("仅允许发送非空的飞书文本消息。");
  }
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 15_000);
  try {
    const upstream = await fetch(targetUrl, {
      method: "POST",
      headers: { "content-type": "application/json; charset=utf-8", accept: "application/json" },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
    const rawBody = await upstream.text();
    let data;
    try { data = rawBody ? JSON.parse(rawBody) : {}; } catch { data = { rawBody }; }
    const success = upstream.ok && data?.code === 0;
    return { success, upstreamStatus: upstream.status, data, error: success ? "" : (data?.msg || `飞书返回 HTTP ${upstream.status}`) };
  } finally {
    clearTimeout(timeoutId);
  }
}

const server = http.createServer(async (request, response) => {
  const requestUrl = new URL(request.url, `http://${host}:${port}`);
  if (request.method === "POST" && requestUrl.pathname === "/api/notify") {
    try {
      const body = await readJsonBody(request);
      const targetUrl = validateFeishuWebhookUrl(String(body?.url || ""));
      const result = await forwardFeishuNotification(targetUrl, body?.payload);
      send(response, result.success ? 200 : 502, JSON.stringify(result), { "content-type": "application/json; charset=utf-8" });
    } catch (error) {
      send(response, 502, JSON.stringify({ success: false, error: error instanceof Error ? error.message : String(error) }), { "content-type": "application/json; charset=utf-8" });
    }
    return;
  }

  if (request.method !== "GET") {
    send(response, 405, "Method Not Allowed", { allow: "GET" });
    return;
  }

  if (requestUrl.pathname === "/") {
    try {
      const html = await fs.readFile(htmlPath);
      send(response, 200, html, { "content-type": "text/html; charset=utf-8" });
    } catch (error) {
      send(response, 500, `无法读取 GPChecker.html：${error.message}`);
    }
    return;
  }

  if (requestUrl.pathname !== "/api/play") {
    send(response, 404, "Not Found");
    return;
  }

  try {
    const targetUrl = validatePlayUrl(requestUrl.searchParams.get("url") || "");
    const upstream = await fetch(targetUrl, {
      headers: {
        accept: "text/html,application/xhtml+xml",
        "accept-language": "en-US,en;q=0.9",
        "user-agent": "GPChecker Local Proxy/1.0",
      },
    });
    const body = await upstream.text();
    send(response, upstream.status, body, {
      "content-type": upstream.headers.get("content-type") || "text/plain; charset=utf-8",
    });
  } catch (error) {
    send(response, 502, error instanceof Error ? error.message : String(error));
  }
});

server.listen(port, host, () => {
  console.log(`GP Checker 已启动：http://${host}:${port}`);
  console.log("按 Ctrl+C 停止服务。");
});
