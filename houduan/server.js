/**
 * server.js — 校验后端服务器
 *
 * 功能：
 *   1. 静态托管 4 个 txt 校验文件（wyy.txt / kw.txt / kg.txt / qq.txt），返回纯文本 "yes"
 *   2. 配置 CORS 跨域头，允许前端域名访问
 *   3. 配置安全响应头（防 MIME 嗅探、XSS、缓存禁用）
 *   4. 设备指纹审计日志（记录 X-Device-Fingerprint 请求头）
 *   5. 支持 HTTPS（需提供证书），生产环境必须使用 HTTPS
 *
 * 启动方式：
 *   开发环境：node server.js
 *   生产 HTTPS：HTTPS=true SSL_KEY=path SSL_CERT=path node server.js
 *
 * 访问地址：
 *   http(s)://你的域名/yy/wyy.txt  →  返回 "yes\n"（纯文本）
 *   http(s)://你的域名/yy/kw.txt   →  返回 "yes\n"
 *   http(s)://你的域名/yy/kg.txt   →  返回 "yes\n"
 *   http(s)://你的域名/yy/qq.txt   →  返回 "yes\n"
 *
 * 关闭授权（拦截软件）：
 *   将对应 txt 文件内容改为 "no" 即可让所有客户端白屏拦截。
 */

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

/* ============================================================
 * 配置项
 * ============================================================ */
const HTTP_PORT = process.env.PORT || 8080;
const HTTPS_PORT = process.env.HTTPS_PORT || 8443;
const USE_HTTPS = process.env.HTTPS === 'true';
const SSL_KEY = process.env.SSL_KEY || '';
const SSL_CERT = process.env.SSL_CERT || '';

// 允许的前端域名（CORS 白名单，生产环境必须配置为你自己的域名）
// 【防篡改关键】严格限制来源，防止第三方域名调用
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || '*').split(',');

/* ============================================================
 * 静态文件服务
 * ============================================================ */
const PUBLIC_DIR = path.join(__dirname, 'public');

/**
 * 处理 txt 校验文件请求
 * - 路径 /yy/wyy.txt → public/wyy.txt
 * - 强制 text/plain; charset=utf-8
 * - 禁用缓存（Cache-Control: no-store）
 * - 记录设备指纹
 */
function serveVerifyFile(req, res, filename) {
  const filePath = path.join(PUBLIC_DIR, filename);
  if (!fs.existsSync(filePath)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('no');
    return;
  }
  const content = fs.readFileSync(filePath, 'utf-8');
  res.writeHead(200, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0',
    'Pragma': 'no-cache',
    'Expires': '0',
    'Access-Control-Allow-Origin': getAllowedOrigin(req),
    'Access-Control-Allow-Headers': 'X-Device-Fingerprint, X-Verify-Token',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
  });
  res.end(content);

  // 审计日志：记录设备指纹（服务端可识别批量破解设备）
  const fp = req.headers['x-device-fingerprint'] || 'unknown';
  const token = req.headers['x-verify-token'] || 'unknown';
  const ip = req.socket.remoteAddress;
  console.log(`[${new Date().toISOString()}] ${filename} → "${content.trim()}" | ip=${ip} fp=${fp} token=${token}`);
}

/**
 * 获取允许的 CORS 来源
 */
function getAllowedOrigin(req) {
  const origin = req.headers.origin || '';
  if (ALLOWED_ORIGINS.includes('*')) return '*';
  if (ALLOWED_ORIGINS.includes(origin)) return origin;
  return ''; // 拒绝跨域
}

/* ============================================================
 * HTTP 请求路由
 * ============================================================ */
function handleRequest(req, res) {
  // 处理 CORS 预检请求
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': getAllowedOrigin(req),
      'Access-Control-Allow-Headers': 'X-Device-Fingerprint, X-Verify-Token',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Max-Age': '86400',
    });
    res.end();
    return;
  }

  const url = req.url || '';
  // 路由：/yy/{name}.txt
  const match = url.match(/^\/yy\/(wyy|kw|kg|qq)\.txt$/);
  if (match) {
    serveVerifyFile(req, res, `${match[1]}.txt`);
    return;
  }

  // 其他路径返回 404（防止目录遍历）
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('no');
}

/* ============================================================
 * 启动服务器
 * ============================================================ */
function startHttp() {
  const server = http.createServer(handleRequest);
  server.listen(HTTP_PORT, () => {
    console.log(`[校验服务] HTTP  running on http://0.0.0.0:${HTTP_PORT}`);
    console.log(`[校验服务] 访问路径: /yy/wyy.txt /yy/kw.txt /yy/kg.txt /yy/qq.txt`);
  });
}

function startHttps() {
  if (!SSL_KEY || !SSL_CERT) {
    console.error('[校验服务] HTTPS=true 但未提供 SSL_KEY / SSL_CERT 环境变量');
    process.exit(1);
  }
  const options = {
    key: fs.readFileSync(SSL_KEY),
    cert: fs.readFileSync(SSL_CERT),
    // 【防破解关键】严格 TLS，拒绝过旧协议
    secureProtocol: 'TLSv1_2_method',
    minVersion: 'TLSv1.2',
  };
  const server = https.createServer(options, handleRequest);
  server.listen(HTTPS_PORT, () => {
    console.log(`[校验服务] HTTPS running on https://0.0.0.0:${HTTPS_PORT}`);
  });
}

// 启动
if (USE_HTTPS) {
  startHttps();
} else {
  startHttp();
  console.log('[校验服务] ⚠️ 当前为 HTTP 模式，生产环境请使用 HTTPS=true');
}

// 健康检查日志
setInterval(() => {
  console.log(`[心跳] ${new Date().toISOString()} 服务运行中`);
}, 60000);
