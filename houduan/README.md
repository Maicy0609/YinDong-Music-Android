# 软件启动远程联网校验系统

软件初始化/启动第一优先级执行网络校验，校验失败永久白屏拦截，适配 Electron 桌面端 / 网页端通用。

> ⚠️ 安全声明：**没有任何客户端校验是 100% 不可绕过的**。客户端代码最终都能被逆向修改。本方案最大化提升破解门槛（多层防护 + 严格 HTTPS + 调试检测 + DOM 锁死），真正的授权控制最终仍需由后端服务器决策（服务端返回 yes/no）。

## 目录结构

```
yyy/
├── frontend-verify/              # 前端校验代码
│   ├── license-verify.js        # ① 初始化校验主逻辑（第一优先级）
│   ├── verify-utils.js           # ② 工具函数（网络检测/并发请求/白屏/调试检测/DOM保护）
│   └── verify.css                # ③ 白屏全局遮罩 CSS
└── houduan/                      # 后端校验服务
    ├── server.js                 # Node.js 校验服务器
    ├── package.json
    ├── public/
    │   ├── wyy.txt               # 网易云校验文件（内容：yes）
    │   ├── kw.txt                # 酷我校验文件
    │   ├── kg.txt                # 酷狗校验文件
    │   └── qq.txt                # QQ音乐校验文件
    └── README.md                 # 本文件
```

---

## ① 完整初始化校验主逻辑

文件：`frontend-verify/license-verify.js`

执行顺序（软件初始化第一优先级）：
1. 注入遮罩 CSS + 隐藏 body（防止主界面闪现）
2. 清空本地缓存（localStorage / sessionStorage / IndexedDB）
3. 启动调试器检测（命中即白屏）
4. 检测网络连通性（断网即白屏 "offline"）
5. 解密 4 个校验 URL
6. 并发请求 4 个校验地址（3000ms 超时，全局重试 1 次）
7. 判定：全部返回 "yes" → 渲染主界面；任一失败 → 白屏 "server"

**集成方式**：在 HTML 入口最顶部引入
```html
<script src="verify-utils.js"></script>
<script src="license-verify.js"></script>
```

---

## ② 独立工具函数

文件：`frontend-verify/verify-utils.js`

| 函数 | 说明 |
|---|---|
| `getVerifyUrls()` | XOR+Base64 解密 4 个校验 URL，代码内不暴露明文 |
| `checkNetworkConnectivity()` | 双重检测：navigator.onLine + 实际 HEAD 请求 |
| `generateDeviceFingerprint()` | canvas+屏幕+时区+UUID 生成设备指纹 |
| `fetchVerifyUrls(urls, 3000)` | 并发请求 4 个地址，全部返回 "yes" 才通过 |
| `startDebuggerDetection(cb)` | 时间差检测 debugger + devtools 尺寸 + console 重写 |
| `showWhiteScreen('offline'|'server'|'device')` | 白屏遮罩 + DOM 锁死 |
| `clearLocalCache()` | 清空本地缓存，禁止缓存校验结果 |

---

## ③ 白屏全局遮罩 CSS

文件：`frontend-verify/verify.css`（已通过 JS 内联注入并保护）

- `z-index: 2147483647`（最高层，覆盖全部 DOM）
- 屏蔽右键菜单、F12、Ctrl+Shift+I、Ctrl+R、F5
- MutationObserver 监听遮罩被删除，立即重建
- 三种报错文案：断网提示 / 服务器校验异常 / 设备拦截限制
- 唯一交互：关闭程序按钮

---

## ④ 配套后端 txt 服务器配置说明

### 4.1 返回格式
4 个 txt 文件内容为纯文本 `yes`（末尾带换行）。校验通过时返回：
```
yes
```
关闭授权时，将对应 txt 改为 `no` 即可让所有客户端白屏拦截。

### 4.2 跨域配置
- CORS 白名单通过环境变量 `ALLOWED_ORIGINS` 配置
- 允许自定义请求头：`X-Device-Fingerprint`、`X-Verify-Token`
- 生产环境**必须**配置为你自己的域名，禁止 `*`
```bash
# 示例：只允许你的前端域名
ALLOWED_ORIGINS=https://app.yourdomain.com,https://www.yourdomain.com node server.js
```

### 4.3 防篡改配置
- `Cache-Control: no-store`（禁用缓存）
- `X-Content-Type-Options: nosniff`（防 MIME 嗅探）
- `X-Frame-Options: DENY`（防点击劫持）
- `Content-Type: text/plain; charset=utf-8`（纯文本）
- 路由正则严格匹配 `/yy/(wyy|kw|kg|qq)\.txt`，防目录遍历
- HTTPS 强制 TLS 1.2+，拒绝过旧协议

### 4.4 启动方式
```bash
# 开发环境（HTTP）
cd houduan
npm start

# 生产环境（HTTPS，需证书）
HTTPS=true SSL_KEY=/path/to/key.pem SSL_CERT=/path/to/cert.pem \
ALLOWED_ORIGINS=https://app.yourdomain.com \
npm start
```

### 4.5 服务器配置（Nginx 反代示例）
```nginx
location /yy/ {
  proxy_pass http://127.0.0.1:8080;
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  # 强制 HTTPS
  if ($scheme != https) { return 301 https://$host$request_uri; }
}
```

---

## ⑤ 代码防破解关键点逐条说明

| # | 关键点 | 实现位置 |
|---|---|---|
| 1 | **URL 混淆加密** | `verify-utils.js` XOR+Base64，代码内不暴露明文链接 |
| 2 | **断网直接拦截** | `checkNetworkConnectivity()` 双重检测 |
| 3 | **设备指纹** | `generateDeviceFingerprint()` 每次请求携带 |
| 4 | **严格 HTTPS 证书校验** | fetch 默认开启，`redirect: 'error'` 禁止重定向劫持 |
| 5 | **禁止缓存校验结果** | `clearLocalCache()` + `cache: 'no-store'` |
| 6 | **3000ms 超时 + 仅重试1次** | `fetchSingleVerify()` AbortController |
| 7 | **全部返回 yes 才通过** | `fetchVerifyUrls()` Promise.all + 逐个判定 |
| 8 | **调试器检测** | `startDebuggerDetection()` 时间差+尺寸+console |
| 9 | **DOM 锁死保护** | `showWhiteScreen()` MutationObserver + setInterval 重建 |
| 10 | **屏蔽快捷键/右键** | `keydown` + `contextmenu` 捕获阶段拦截 |
| 11 | **CORS 白名单** | 后端 `ALLOWED_ORIGINS` 严格限制来源 |
| 12 | **安全响应头** | `nosniff` / `DENY` / `no-store` |

---

## ⑥ 规避破解手段说明

### 6.1 抓包改返回（中间人）
- **防护**：严格 HTTPS 证书校验，fetch 默认拒绝自签证书与证书不匹配
- **加固**：`redirect: 'error'` 禁止重定向，防止代理劫持改写到假服务器
- **残留风险**：安装根证书的中间人仍可解密 HTTPS → 需服务端校验设备指纹+IP 频次

### 6.2 host 劫持
- **防护**：`checkNetworkConnectivity()` 第二层用公共端点 `gstatic.com/generate_204` 验证真实连通性
- **加固**：4 个校验 URL 使用不同路径，host 劫持需同时改 4 条记录
- **残留风险**：全量 host 劫持 → 需 DNS over HTTPS

### 6.3 清除缓存绕过
- **防护**：`clearLocalCache()` 启动即清空，且不缓存校验结果到本地
- **结论**：**已规避**。校验结果只在内存变量中，刷新/重启重新联网

### 6.4 控制台篡改页面
- **防护**：`startDebuggerDetection()` 检测 devtools 打开即白屏
- **加固**：`showWhiteScreen()` 用 MutationObserver 监听遮罩删除，立即重建 + setInterval 100ms 兜底
- **残留风险**：禁用 JS 后所有保护失效 → 需 Electron 主进程层做二次校验

### 6.5 断网绕过
- **防护**：断网直接白屏 "offline"，不渲染主界面
- **结论**：**已规避**。无网络无法进入软件

### 6.6 修改本地存储
- **防护**：校验结果不写入 localStorage/sessionStorage/IndexedDB
- **结论**：**已规避**

---

## 部署步骤

### 1. 生成 URL 密文（替换占位密文）
```bash
cd frontend-verify
node -e "const u=require('./verify-utils.js'); console.log(u.encodeUrl('https://你的域名/yy/wyy.txt'))"
# 将输出的密文替换 verify-utils.js 中 _ENCRYPTED_URLS 数组
```

### 2. 部署后端
```bash
cd houduan
npm install
npm start
```

### 3. 前端集成
将 `verify-utils.js` + `license-verify.js` 放入前端入口最顶部。

### 4. 生产环境配置 HTTPS
```bash
HTTPS=true \
SSL_KEY=/etc/letsencrypt/live/yourdomain.com/privkey.pem \
SSL_CERT=/etc/letsencrypt/live/yourdomain.com/fullchain.pem \
ALLOWED_ORIGINS=https://app.yourdomain.com \
npm start
```
