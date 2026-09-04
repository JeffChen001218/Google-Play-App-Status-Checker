#!/usr/bin/env node

/**
 * GPChecker 的本地同源服务：
 * - 仅监听 127.0.0.1，不对局域网公开；
 * - 通过维护中的 Node Google Play 解析器读取公开资料；
 * - 同时托管 GPChecker.html，浏览器不直接请求或解析 Google Play HTML。
 */
const http = require("http");
const fs = require("fs/promises");
const path = require("path");

const host = "127.0.0.1";
const port = Number(process.argv[2]) || 18765;
const htmlPath = path.join(__dirname, "GPChecker.html");
const packageNamePattern = /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/;
const maxNotificationBodyBytes = 64 * 1024;
const appInfoCacheTtlMs = 5 * 60 * 1_000;
const appInfoCache = new Map();
const appInfoRequests = new Map();
const mockReferrerTargetOrigin = "https://mock-ref-01a06744.jeffchen001218.chatgpt.site";

let scraperClient = null;
let scraperLoadError = "";
let qrCode = null;
let qrCodeLoadError = "";
try {
  // 该库是经过类型校验与每日契约测试维护的 Google Play 公开信息解析器。
  const scraper = require("@mradex77/google-play-scraper").default;
  scraperClient = scraper.createClient({ lang: "en", country: "us", throttle: 1 });
} catch (error) {
  scraperLoadError = error instanceof Error ? error.message : String(error);
}
try {
  qrCode = require("qrcode");
} catch (error) {
  qrCodeLoadError = error instanceof Error ? error.message : String(error);
}

function send(response, status, body, headers = {}) {
  response.writeHead(status, {
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    ...headers,
  });
  response.end(body);
}

function validatePackageName(rawPackageName) {
  const packageName = String(rawPackageName || "").trim();
  if (!packageNamePattern.test(packageName)) throw new Error("无效的应用包名。");
  return packageName;
}

function validateCountry(rawCountry) {
  const country = String(rawCountry || "us").trim().toLowerCase();
  if (!/^[a-z]{2}$/.test(country)) throw new Error("国家/地区代码必须为两个英文字母。");
  return country;
}

function textValue(value, maxLength = 400) {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function httpsUrl(value) {
  try {
    const url = new URL(String(value || ""));
    return url.protocol === "https:" ? url.toString() : "";
  } catch {
    return "";
  }
}

function normalizedDetailValue(value, depth = 0) {
  if (depth > 3 || value === null || value === undefined) return null;
  if (typeof value === "string") return value.trim().slice(0, 12_000) || null;
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value === "boolean") return value;
  if (Array.isArray(value)) {
    const values = value.slice(0, 50).map(item => normalizedDetailValue(item, depth + 1)).filter(item => item !== null);
    return values.length ? values : null;
  }
  if (typeof value === "object") {
    const entries = Object.entries(value).slice(0, 50)
      .map(([key, item]) => [key, normalizedDetailValue(item, depth + 1)])
      .filter(([, item]) => item !== null);
    return entries.length ? Object.fromEntries(entries) : null;
  }
  return null;
}

function normalizedAppDetails(app) {
  const hiddenFields = new Set([
    "title", "icon", "version", "updated", "appId",
    "descriptionHTML", "minInstalls", "maxInstalls", "price", "currency", "priceText", "free", "histogram",
    "developerInternalID", "developerId", "genreId", "categories", "contentRating", "preregister", "isAvailableInPlayPass", "androidVersionText",
  ]);
  const details = {};
  for (const [key, value] of Object.entries(app || {})) {
    if (hiddenFields.has(key)) continue;
    const normalized = normalizedDetailValue(value);
    if (normalized !== null) details[key] = normalized;
  }
  return details;
}

function normalizedAppInfo(app) {
  const updated = Number(app?.updated);
  return {
    source: "google-play-scraper",
    title: textValue(app?.title, 160),
    icon: httpsUrl(app?.icon),
    version: textValue(app?.version, 80),
    updated: Number.isSafeInteger(updated) && updated > 0 ? updated : null,
    summary: textValue(app?.summary, 1_000),
    developer: textValue(app?.developer, 160),
    genre: textValue(app?.genre, 120),
    installs: textValue(app?.installs, 80),
    score: Number.isFinite(app?.score) ? app.score : null,
    details: normalizedAppDetails(app),
  };
}

function validateMockReferrerUrl(rawUrl) {
  const url = new URL(rawUrl);
  if (url.origin !== mockReferrerTargetOrigin || url.pathname !== "/") {
    throw new Error("二维码仅支持指定的模拟 referrer 页面。");
  }
  validatePackageName(url.searchParams.get("packageName"));
  if (!url.searchParams.has("referrer")) throw new Error("模拟 referrer 链接缺少 referrer 参数。");
  return url;
}

function withTimeout(promise, timeoutMs, message) {
  let timeoutId;
  const timeout = new Promise((_, reject) => {
    timeoutId = setTimeout(() => reject(new Error(message)), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timeoutId));
}

async function getAppInfo(packageName, country) {
  if (!scraperClient) throw new Error(`应用信息解析器不可用：${scraperLoadError || "依赖未安装"}`);
  const cacheKey = `${country}:${packageName}`;
  const now = Date.now();
  const cached = appInfoCache.get(cacheKey);
  if (cached?.expiresAt > now) return cached.value;
  if (appInfoRequests.has(cacheKey)) return appInfoRequests.get(cacheKey);
  const request = withTimeout(scraperClient.app({ appId: packageName, country, lang: "en" }), 15_000, "应用信息解析超时。")
    .then(normalizedAppInfo)
    .then(value => {
      appInfoCache.set(cacheKey, { value, expiresAt: Date.now() + appInfoCacheTtlMs });
      return value;
    })
    .finally(() => appInfoRequests.delete(cacheKey));
  appInfoRequests.set(cacheKey, request);
  return request;
}

async function getAppInspection(packageName, country) {
  if (!scraperClient) throw new Error(`应用信息解析器不可用：${scraperLoadError || "依赖未安装"}`);
  const availability = await withTimeout(
    scraperClient.availability({ appId: packageName, countries: [country] }),
    15_000,
    "在线状态检测超时。",
  );
  const status = availability?.countries?.[country]?.status;
  if (status === "unavailable") return { online: false, version: null, updated: null, appInfo: null };
  if (status !== "available") throw new Error("无法确认应用在所选国家/地区的上架状态。");

  const appInfo = await getAppInfo(packageName, country);
  return {
    online: true,
    version: appInfo.version,
    updated: appInfo.updated,
    appInfo,
  };
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

  if (requestUrl.pathname === "/api/app-inspect") {
    try {
      const packageName = validatePackageName(requestUrl.searchParams.get("id"));
      const country = validateCountry(requestUrl.searchParams.get("country"));
      const data = await getAppInspection(packageName, country);
      send(response, 200, JSON.stringify({ success: true, data }), { "content-type": "application/json; charset=utf-8" });
    } catch (error) {
      send(response, scraperClient ? 502 : 503, JSON.stringify({ success: false, error: error instanceof Error ? error.message : String(error) }), { "content-type": "application/json; charset=utf-8" });
    }
    return;
  }

  if (requestUrl.pathname === "/api/qr") {
    try {
      if (!qrCode) throw new Error(`二维码生成器不可用：${qrCodeLoadError || "依赖未安装"}`);
      const targetUrl = validateMockReferrerUrl(requestUrl.searchParams.get("url") || "");
      const svg = await qrCode.toString(targetUrl.toString(), {
        type: "svg",
        // URL 不变时，降低纠错级别可减少模块数量；SVG 再由前端放大，提升扫码容错。
        errorCorrectionLevel: "L",
        margin: 2,
      });
      send(response, 200, svg, {
        "content-type": "image/svg+xml; charset=utf-8",
        "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'",
      });
    } catch (error) {
      send(response, qrCode ? 400 : 503, error instanceof Error ? error.message : String(error));
    }
    return;
  }

  send(response, 404, "Not Found");
});

server.listen(port, host, () => {
  console.log(`GP Checker 已启动：http://${host}:${port}`);
  console.log("按 Ctrl+C 停止服务。");
});
