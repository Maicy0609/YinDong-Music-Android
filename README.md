# 音动音乐（YinDong Music）— 完整技术文档

一个基于 Jetpack Compose 的 Android 音乐播放器，支持多平台音乐搜索和播放。

[](https://github.com/88541/YinDong-Music/stargazers)
[](https://github.com/88541/YinDong-Music/network)
[](https://github.com/88541/YinDong-Music/blob/main/LICENSE)

> **版本**：v3.0.4（versionCode 33）  
> **协议**：MIT License  
> **作者**：知难辞-音动音乐  
> **最后更新**：2026-07-24

---

## 目录

- [一、项目概述](#一项目概述)
- [二、功能特性](#二功能特性)
- [三、技术栈与依赖](#三技术栈与依赖)
- [四、项目架构](#四项目架构)
- [五、模块详解](#五模块详解)
  - [5.1 入口层](#51-入口层)
  - [5.2 Service 层](#52-service-层)
  - [5.3 ViewModel 层](#53-viewmodel-层)
  - [5.4 数据层 — 插件系统（data/lx/）](#54-数据层--插件系统datalx)
  - [5.5 数据层 — 原生 SDK（data/lxsdk/）](#55-数据层--原生-sdkdatalxsdk)
  - [5.6 数据层 — 服务端 API（data/api/）](#56-数据层--服务端-apidataapi)
  - [5.7 数据层 — MusicFree 插件系统](#57-数据层--musicfree-插件系统)
  - [5.8 数据层 — 本地存储与配置](#58-数据层--本地存储与配置)
  - [5.9 安全层（security/）](#59-安全层security)
  - [5.10 音频增强（audio/）](#510-音频增强audio)
  - [5.11 UI 层（ui/）](#511-ui-层ui)
- [六、播放链路与取链优先级](#六播放链路与取链优先级)
- [七、安全防护体系](#七安全防护体系)
- [八、后端服务](#八后端服务)
- [九、歌词 API 模块](#九歌词-api-模块)
- [十、构建与配置](#十构建与配置)
- [十一、安装与使用](#十一安装与使用)
- [十二、API 配置指南](#十二api-配置指南)
- [十三、免责声明](#十三免责声明)

---

## 一、项目概述

音动音乐是一款基于 **Jetpack Compose** 构建的 Android 音乐播放器，支持多平台音乐搜索、在线播放、歌词显示、音频可视化等丰富功能。项目采用 MVVM 架构，集成三套并行音源路径（JS 插件、原生 SDK、服务端 API），并内置完整的安全防护体系。

| 属性  | 值   |
| --- | --- |
| 包名  | `com.yindong.music` |
| minSdk | 26（Android 8.0） |
| targetSdk / compileSdk | 36  |
| Kotlin | 1.9.22 |
| AGP | 8.2.2 |
| JVM Target | 17  |
| Gradle 配置缓存 | 已启用 |

### 周边辅助项目

| 目录  | 说明  |
| --- | --- |
| `houduan/` | Node.js 后端（校验服务 + 静态 txt 托管） |
| `kotlin/com/lyrics/api/` | 独立歌词 API 模块（KG/WY/TX Provider） |
| `com/whl/quickjs/wrapper/` | QuickJS 引擎 wrapper 源码 |
| `server/` | Java/Maven 服务端项目（pom.xml） |

---

## 二、功能特性

### 核心播放

- **多平台音乐搜索** — 支持WY、TX音乐、KW音乐、KG音乐、MG音乐五大平台
- **在线播放** — 基于 ExoPlayer (Media3) 的在线流式播放
- **多音质选择** — 标准(128kbps)、极高(320kbps)、无损(FLAC)、Hi-Res、超清母带
- **歌词显示** — 同步歌词、逐字歌词、翻译歌词
- **播放模式** — 顺序播放、随机播放、单曲循环、列表循环
- **音频可视化** — 128 条频谱条环绕封面，随音乐实时律动

### 搜索与发现

- **实时搜索建议** — 输入时自动提示相关搜索词
- **热搜榜单** — 显示热门搜索关键词
- **链接解析** — 支持DY/DY音乐分享链接自动解析播放
- **歌单链接导入** — 支持WY/TX/KW/KG歌单链接解析导入
- **搜索历史** — 保存搜索记录，方便快速搜索
- **分类发现** — 抖音热歌、伤感情歌、怀旧金曲等分类
- **榜单** — 各平台热歌榜、飙升榜等

### 歌单管理

- **创建歌单** — 自定义创建个人歌单
- **收藏歌曲** — 收藏喜欢的歌曲
- **添加到歌单** — 将歌曲添加到指定歌单
- **播放全部** — 一键播放歌单所有歌曲
- **数据导入导出** — 支持收藏/歌单/历史数据 JSON 导入导出

### 下载功能

- **多音质下载** — 支持多种音质下载
- **下载管理** — 查看和管理已下载的歌曲
- **元数据写入** — 下载时自动写入歌词、封面到音频文件

### 高级功能

- **LX Plugin 支持** — 兼容洛雪音乐插件格式（.js）
- **MusicFree 插件支持** — 兼容 MusicFree 插件格式
- **原生 SDK 音源** — 内置 5 平台原生搜索/取链（不依赖 QuickJS）
- **自定义 API** — 支持自定义服务端 API 地址和密钥
- **听歌识曲** — 通过麦克风录音 + 音频指纹识别歌曲
- **悬浮歌词** — 系统级悬浮窗歌词
- **车载蓝牙歌词** — 蓝牙连接时通知栏显示歌词
- **睡眠定时** — 定时停止播放
- **歌词偏移** — 歌词时间微调
- **USB 独占音频** — USB 音频设备独占模式

### UI 特性

- **暗色/亮色主题** — 精美的深色与亮色界面
- **毛玻璃效果** — Glassmorphism 设计语言
- **频谱可视化** — 128 条紫色频谱条环绕封面
- **封面旋转** — 播放时封面随节拍跳动
- **沉浸式体验** — 全屏歌词、沉浸式播放界面
- **底部导航** — 首页、歌单、我的三大模块
- **屏幕方向** — 跟随系统/竖屏/横屏/反向横屏

---

## 三、技术栈与依赖

### 核心依赖

| 依赖  | 版本  | 用途  |
| --- | --- | --- |
| Kotlin | 1.9.22 | 开发语言 |
| Jetpack Compose BOM | 2024.02.00 | 声明式 UI 框架 |
| Material 3 | (BOM 管理) | 设计规范 |
| Navigation Compose | 2.7.7 | 页面导航 |
| Lifecycle | 2.7.0 | 生命周期管理 |
| Media3 ExoPlayer | 1.3.0 | 音频播放引擎 |
| Media3 Session | 1.3.0 | 通知栏/锁屏媒体控制 |
| OkHttp | 4.12.0 | 网络请求 |
| QuickJS Wrapper | 3.2.3 | JavaScript 引擎（插件执行） |
| Coil Compose | 2.6.0 | 图片加载 |
| Lottie Compose | 6.4.0 | 动画效果 |
| DataStore Preferences | 1.0.0 | 数据持久化 |
| Security Crypto | 1.0.0 | 加密存储（EncryptedSharedPreferences） |
| Room | 2.6.1 | 本地数据库（本地音乐库） |
| jaudiotagger | 2.2.5 | 音频元数据编辑 |
| Palette KTX | 1.0.0 | 颜色提取（封面主题） |

### 构建工具

- **KSP** — `1.9.22-1.0.17`（Room 编译器）
- **ProGuard/R8** — Release 混淆 + 资源压缩
- **ABI 拆分** — 生成 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 独立 APK

---

## 四、项目架构

### 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                     Android 主应用 (app/)                     │
│                                                               │
│  ┌─────────┐  ┌───────────┐  ┌──────────┐  ┌────────────┐  │
│  │ 入口层   │  │ Service层 │  │ ViewModel│  │  UI 层     │  │
│  │App/Act  │→│ Playback  │  │MusicVM   │→ │ Compose    │  │
│  └────┬────┘  └─────┬─────┘  └────┬─────┘  └─────┬──────┘  │
│       │             │              │              │          │
│       │     ┌───────┴──────────────┴──────────────┘         │
│       │     │                                                  │
│       │     ▼                                                  │
│       │  ┌──────────────────────────────────────────────┐    │
│       │  │              数据层 (data/)                   │    │
│       │  │  ┌─────────┐ ┌─────────┐ ┌───────────────┐  │    │
│       │  │  │ LX 插件  │ │ LxSdk   │ │ MusicApiService│  │    │
│       │  │  │ (QuickJS)│ │ (原生)  │ │ (服务端转发)  │  │    │
│       │  │  └─────────┘ └─────────┘ └───────────────┘  │    │
│       │  │  ┌─────────────────────────────────────────┐ │    │
│       │  │  │  LocalStorage / RemoteConfig / Room DB  │ │    │
│       │  │  └─────────────────────────────────────────┘ │    │
│       │  └──────────────────────────────────────────────┘    │
│       │                                                       │
│  ┌────┴──────────────┐  ┌──────────────────┐                │
│  │  安全层(security/) │  │  音频增强(audio/) │                │
│  └───────────────────┘  └──────────────────┘                │
└─────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────┐              ┌──────────────────┐
│  houduan/       │              │ kotlin/com/lyrics/│
│  Node.js 后端    │              │ 歌词 API 模块     │
└─────────────────┘              └──────────────────┘
```

### 关键调用链路

| 链路  | 流程  |
| --- | --- |
| **启动链** | `CloudMusicApplication` → `MainActivity` → `LicenseVerifier`(远程校验) → `MusicViewModel` → `AppNavigation` |
| **播放链** | `MusicViewModel`(ExoPlayer+MediaSession) ↔ `MusicPlaybackService`(前台通知) |
| **歌词链** | `MusicViewModel` → `FloatingLyricsService`(系统悬浮窗) |
| **插件链(JS)** | `MusicViewModel` → `LxPluginManager` → `LuoxuePluginManager` → `QuickJSWrapper` → 执行 lx `.js` 插件 |
| **原生SDK链** | `MusicViewModel` → `LxSdkSearchManager`/`LxSdkMusicUrl` → `LxSdk`(加密/httpFetch) |
| **服务端API链** | `MusicViewModel` → `MusicApiService` → `houduan/`(Node.js 后端) |
| **安全链** | `LicenseVerifier`(启动) + `SecurityGuard`(反调试/Root/Hook) + `MusicPlaybackGate`(播放拦截) |

---

## 五、模块详解

### 5.1 入口层

#### CloudMusicApplication

全局 Application，负责最早的初始化：

```
1. LocalStorage.init()         — 本地存储初始化（含加密存储）
2. LocalStorage.loadOrCreateDeviceId() — 设备标识生成
3. CrashLogManager.init()      — 崩溃日志管理器
4. CrashLogManager.installCrashHandler() — 全局异常捕获
5. SecurityGuard.init()        — 安全加固（签名校验）
6. CriticalUiProtector.init()  — 关键 UI 保护
7. 安全快速检测（调试器/Hook，仅 Release）
8. StatsReporter.reportAppOpen() — 统计上报
```

同时实现 `ImageLoaderFactory`，配置全局 Coil 图片加载器：

- 内存缓存：应用可用内存的 25%
- 磁盘缓存：100MB
- 关闭 crossfade（避免滑动时额外绘制帧）
- `respectCacheHeaders = false`（音乐封面图不变，强制缓存）

#### MainActivity

唯一 Activity 入口，职责包括：

- **权限申请** — 通知、存储、录音等运行时权限
- **License 远程校验** — 启动时联网验证，失败白屏拦截
- **Compose UI 容器** — 承载整个 Compose 导航
- **外部 .js 插件导入** — 通过 `intent-filter` 支持从文件管理器/浏览器打开插件
- **屏幕方向控制** — 跟随系统/竖屏/横屏
- **远程配置加载** — 启动时拉取服务器配置
- **更新检查** — 版本更新对话框

### 5.2 Service 层

#### MusicPlaybackService

继承 Media3 的 `MediaSessionService`，提供：

- 前台通知栏媒体控件（上一首/播放暂停/下一首/关闭）
- `MediaSession` 管理（锁屏/灵动岛/车载蓝牙 AVRCP 兼容）
- `MediaSessionCompat` 兼容层（蓝牙耳机按键、车载歌词显示）
- 封面位图更新（通过 `MusicPlaybackServiceHelper` 桥接）
- 通知正文点击返回应用（`setContentIntent` + `singleTask`）

**通知修复说明**：Android 国产 ROM（vivo/OPPO/小米/华为/鸿蒙）不继承 MediaSession 的 `setSessionActivity()` 点击行为，必须显式设置 `setContentIntent()`，使用 `FLAG_IMMUTABLE`（Android 12+ 合规）。

#### MusicPlaybackServiceHelper

单例桥接器，负责 ViewModel 与 Service 之间的通信，特别是封面位图更新。

#### FloatingLyricsService

系统悬浮窗歌词服务：

- 使用 `WindowManager` 绘制系统级悬浮歌词窗
- 被动接收 ViewModel 写入的歌词数据（通过静态字段/回调）
- 支持自定义颜色、字体大小
- 前台服务通知（`specialUse` 类型），通知正文可点击返回应用

### 5.3 ViewModel 层

#### MusicViewModel（主）

应用中枢，承载全部核心业务逻辑（5400+ 行），包括：

- **播放控制** — ExoPlayer 初始化、播放/暂停/上一首/下一首、播放模式
- **搜索** — 多平台并发搜索、搜索建议、热搜
- **歌词** — 歌词获取、解析、同步
- **插件管理** — LX 插件/MusicFree 插件的加载/搜索/取链
- **下载** — 多音质下载
- **音效** — 均衡器、虚拟器
- **听歌识曲** — 麦克风录音 + 指纹识别
- **歌单** — 创建/收藏/播放歌单
- **音量控制** — 音量调节、静音

#### MusicViewModelxl（未启用）

旧版变体，结构同构但功能裁减。全仓 grep 确认无引用，属未启用的保留代码。

#### LocalMusicViewModel

本地音乐 ViewModel，管理设备本地音乐扫描与播放。

### 5.4 数据层 — 插件系统（data/lx/）

这是兼容洛雪音乐（lx-music）插件格式的 JavaScript 插件系统。

#### LxPluginManager

对外门面，包装 `PluginEntry`，所有方法在 `Dispatchers.IO` 执行：

- `search()` — 调用插件搜索
- `musicUrl()` — 调用插件获取播放链接
- `lyric()` — 调用插件获取歌词
- `getAlbumInfo()` / `getArtistWorks()` / `getMusicSheetInfo()` — 获取专辑/歌手/歌单详情
- `loadPluginFromScript()` — 从脚本加载插件

#### LuoxuePluginManager

核心实现：

- 创建 QuickJS 沙箱环境
- 注入原生桥接函数（`httpFetch`、`crypto` 等）
- 加载 `prelude.js`（运行时引导脚本）
- 解析插件元信息（名称、版本、作者、可用源列表）
- 支持多插件管理

#### QuickJSWrapper

QuickJS 引擎线程安全封装：

- 单线程 `ExecutorService` 隔离执行（避免 JS 引擎线程安全问题）
- 30 秒超时保护
- 支持加载脚本、调用函数、获取返回值

#### BuiltinPluginManager

管理 assets 内置 JS 插件（`builtin_qq.js`、`cjxz123_*.js` 等）：

- 从 assets 读取插件脚本
- 支持插件激活（需输入激活码）
- 插件哈希校验

#### PluginModels

插件数据模型：

- `PluginEntry` — 插件实体（id、uri、info、sources、format）
- `PluginInfo` — 插件元信息（name、version、author、description、homepage）
- `PluginFormat` — 插件格式（LX / MusicFree）
- `LxMusicUrlResult` — 取链结果
- `LyricResult` — 歌词结果

### 5.5 数据层 — 原生 SDK（data/lxsdk/）

完全用 Kotlin 移植 lx-music-mobile 的算法，不依赖 QuickJS 引擎。

#### LxSdk

加密工具集与网络层：

- **加密算法** — MD5、SHA1、AES、RSA、WEAPI 加密、ZZC 签名
- **网络层** — `httpFetch` 封装（OkHttp + 自定义 Headers/Cookies）

#### LxSdkSearchManager

统一搜索入口，分派 5 个平台：

| 源 ID | 平台  |
| --- | --- |
| `wy` | WY音乐 |
| `tx` | TX音乐 |
| `kw` | KW音乐 |
| `kg` | KG音乐 |
| `mg` | MG音乐 |

#### LxSdkMusicUrl

原生 lxmusic.js 取链实现：

- 各平台播放链接获取算法
- 音质降级（请求高音质失败时自动降级）
- Cookies/Header 注入

#### 其他 LxSdk 模块

| 模块  | 功能  |
| --- | --- |
| `LxSdkSearch` | 各平台搜索实现 |
| `LxSdkHotSearch` | 热搜词获取 |
| `LxSdkLeaderboard` | 排行榜数据 |
| `LxSdkSongList` | 歌单详情 |
| `LxSdkTipSearch` | 搜索建议 |

### 5.6 数据层 — 服务端 API（data/api/）

#### MusicApiService

服务端模式音乐网络服务：

- 所有 API 逻辑部署到服务器，客户端仅做 HTTP 转发
- `challenge-token` 鉴权机制
- 内存缓存（避免重复请求）
- 并发搜索（`Semaphore` 控制并发数）
- 自定义 DNS + 连接池

#### MusicApiConfig

API 配置管理（服务端地址、各平台 API Key 等）。

#### LinkParser

外部歌单链接解析器：

- 支持WY音乐
- 支持TX音乐
- 支持KW音乐
- 支持KG音乐
- 从分享口令文本中提取 URL

### 5.7 数据层 — MusicFree 插件系统

#### MusicFreePluginManager

兼容 MusicFree 插件格式的管理器：

- 使用 `Function()` 构造沙箱
- 注入 `require`/`module`/`exports`/`console`/`env`/`URL`/`process`
- 沙箱内可用包：`axios`（原生 HTTP 桥接）、`cheerio`（基础桩）、`crypto-js`（基础桩）、`dayjs`/`qs`/`he`/`big-integer`（基础桩）
- 独立于 LX 插件系统，互不影响

### 5.8 数据层 — 本地存储与配置

#### LocalStorage

本地持久化存储（SharedPreferences + EncryptedSharedPreferences）：

| 数据类型 | 存储方式 | 说明  |
| --- | --- | --- |
| 播放历史 | 明文 SP | 最多 200 条 |
| 搜索历史 | 明文 SP | 最多 20 条 |
| 收藏歌曲 | 明文 SP | JSON 序列化 |
| 用户歌单 | 明文 SP | JSON 序列化 |
| 音质设置 | 明文 SP | exhigh（默认） |
| 播放模式 | 明文 SP | LOOP（默认） |
| QQ Cookie | 加密 SP | 敏感数据 |
| API Key（各平台） | 加密 SP | 敏感数据 |
| 用户 Token | 加密 SP | 登录凭证 |
| 设备 ID | 双写  | 明文 + 加密（防 Keystore 失效） |
| 插件列表 | 明文 SP | JSON 序列化 |
| 悬浮歌词设置 | 明文 SP | 颜色/大小/开关 |
| 车载蓝牙歌词 | 明文 SP | 字号/背景透明度/开关 |

**加密存储策略**：敏感数据优先写入 `EncryptedSharedPreferences`，同时写入明文 SP 作为备份。Keystore 失效时自动回退到明文，避免数据丢失。

#### RemoteConfig

远程配置管理器 — 服务器完全控制 APP 行为：

- 总开关（appEnabled）
- 公告（announcement / announcementType / showAnnouncement）
- 版本控制（minVersion / latestVersion / forceUpdate / updateApkSha256）
- 音乐平台开关（各平台启用/禁用）
- 官方联系方式控制

#### UpdateManager

应用更新管理器：

- 版本比对
- SHA-256 校验（防止 APK 篡改）
- `DownloadManager` 下载
- 自动安装（FileProvider）

#### 其他数据模块

| 模块  | 功能  |
| --- | --- |
| `CrashLogManager` | 崩溃日志记录 + 全局异常捕获 |
| `PlaylistSyncManager` | 歌单同步 |
| `SimpleUpdateChecker` | 简易更新检查 |
| `SimpleAnnouncementChecker` | 简易公告检查 |
| `StatsReporter` | 使用统计上报 |
| `UsbExclusiveManager` | USB 独占音频设备管理 |
| `BluetoothHeadsetManager` | 蓝牙耳机管理 |
| `AppDatabase` / `LocalMusicDao` / `LocalSong` | Room 数据库（本地音乐库） |
| `MusicScanner` / `MetadataParser` | 本地音乐扫描与元数据解析 |

### 5.9 安全层（security/）

#### SecurityGuard

安全加固核心模块，提供 7 大检测：

| 检测项 | 方法  | 说明  |
| --- | --- | --- |
| 反调试 | `checkDebugger()` | Debugger / TracerPid / Debug Flag |
| 反Root | `checkRoot()` | su / Magisk / 可写系统分区 |
| 反模拟器 | `checkEmulator()` | 指纹/硬件/传感器/电话特征 |
| 签名校验 | `checkSignature()` | SHA-256 签名摘要比对 |
| 完整性 | `checkIntegrity()` | Installer 来源 / debuggable flag |
| 反Hook | `checkHook()` | Xposed / Frida / LSPosed / 内存特征 |
| 反抓包 | `checkProxy()` / `checkVpn()` | 代理/VPN 检测 |

#### LicenseVerifier

软件启动远程联网校验器：

1. 检测网络连通性（断网直接拦截）
2. 解密 4 个校验 URL（XOR + Base64 混淆）
3. 并发请求 4 个校验地址（3000ms 超时，全部返回 `yes` 才通过）
4. 失败重试 1 次（重试失败永久白屏拦截）

**安全声明**：客户端校验无法做到绝对不可绕过，本方案最大化提升逆向门槛。

#### MusicPlaybackGate

播放链路安全闸门，在 4 个关键节点拦截：

- `PARSE_LINK` — 解析链接时
- `FETCH_PLAY_URL` — 获取播放链接时
- `START_PLAYBACK` — 开始播放时
- `DOWNLOAD` — 下载时

**策略**：

- 高风险（调试器/Hook/签名篡改）→ 阻止所有操作
- 网络风险（VPN/代理）→ 仅阻止解析和取链接，不阻止播放和下载
- Debug 构建 → 不拦截，保持开发体验

#### CriticalUiProtector / StringEncryptor / StringObfuscator

- `CriticalUiProtector` — 保护关键 UI（QQ群号等），常量指纹 + 渲染时序校验
- `StringEncryptor` — 字符串加密
- `StringObfuscator` — 字符串混淆

### 5.10 音频增强（audio/）

#### AmplitudeAudioProcessor + AmplitudeRenderersFactory

实时振幅提取器：

- 从 ExoPlayer 音频流中提取音量数据
- 回调驱动封面跳动/频谱可视化效果
- 实现 `AudioProcessor` 接口，不改变音频数据

#### AudioFingerprintGenerator + AudioRecorder

听歌识曲功能：

- `AudioRecorder` — 麦克风录音
- `AudioFingerprintGenerator` — 通过 WebView + WASM 生成音频指纹
- 指纹上传服务器匹配识别

### 5.11 UI 层（ui/）

#### 导航（navigation/）

| 屏幕  | 路由  | 说明  |
| --- | --- | --- |
| Discover | `discover` | 发现首页 |
| Playlist | `playlist` | 歌单管理 |
| Mine | `mine` | 我的（设置） |
| Search | `search` | 搜索  |
| Player | `player` | 播放器 |
| HotChart | `hot_chart` | 热歌榜 |
| PlaylistDetail | `playlist_detail/{id}` | 歌单详情 |
| ImportPlaylist | `import_playlist` | 导入歌单 |
| PlaylistSquare | `playlist_square` | 歌单广场 |
| ExternalPlaylistDetail | `external_playlist_detail` | 外部歌单详情 |
| Downloads | `downloads` | 下载管理 |
| AiAudioEffect | `ai_audio_effect` | AI 音效 |
| SongRecognition | `song_recognition` | 听歌识曲 |
| LocalMusic | (local_music) | 本地音乐 |

#### 组件（components/）

| 组件  | 说明  |
| --- | --- |
| `MiniPlayer` | 底部迷你播放器 |
| `BottomNavBar` | 底部导航栏 |
| `PlaylistCard` | 歌单卡片 |
| `QueueBottomSheet` | 播放队列底部弹窗 |
| `SongItem` | 歌曲列表项 |
| `AddToPlaylistSheet` | 添加到歌单弹窗 |
| `SleepTimerDialog` | 睡眠定时对话框 |
| `UpdateDialog` | 更新对话框 |
| `BannerPager` | 轮播图 |
| `LiquidGlass` / `Glassmorphism` | 毛玻璃效果组件 |
| `BluetoothWelcomeAnimation` | 蓝牙连接欢迎动画 |
| `HeadsetBanner` | 耳机连接横幅 |
| `PinyinInputPanel` / `PinyinDict` | 拼音搜索面板 |

#### 主题（theme/）

| 文件  | 说明  |
| --- | --- |
| `AppTheme.kt` | Material 3 主题构建（暗色/亮色） |
| `ThemeManager.kt` | 主题管理（动态切换） |
| `Color.kt` / `ColorSchemeConfig.kt` | 颜色定义 |
| `DynamicThemeBuilder.kt` | 动态主题（根据封面提取颜色） |
| `Glassmorphism.kt` / `GlassButton.kt` | 毛玻璃效果 |
| `Type.kt` | 字体定义 |

---

## 六、播放链路与取链优先级

### 播放链路

```
MusicViewModel (ExoPlayer + MediaSession)
    ↕ MediaSessionHolder
MusicPlaybackService (前台通知栏)
    → MediaSessionCompat (蓝牙耳机/车载 AVRCP)
```

### resolveMusicUrl 取链优先级

```
0. 直接 URL（搜索时已返回播放链接）
    ↓ (失败/为空)
1. 插件模式：通过 lxPluginId 路由到原始插件
    ↓ (失败)
2. 插件兜底：尝试已加载插件
    ├─ 优先：搜索该歌曲的原始插件（lxPluginId）
    └─ 其次：内置插件（LuoxuePluginManager）
```

### 三套音源路径并行

| 路径  | 引擎  | 说明  |
| --- | --- | --- |
| JS 插件路径 | QuickJS | 执行 lx 插件 `.js` 脚本 |
| 原生 SDK 路径 | Kotlin | 移植 lxmusic 算法，不依赖 QuickJS |
| 服务端 API 路径 | HTTP | 转发到 `houduan/` Node.js 后端 |

---

## 七、安全防护体系

安全是横切关注点，`SecurityGuard` 被 Application / Activity / Service / ViewModel / API 多处调用。

### 防护层次

```
┌──────────────────────────────────────────────┐
│  第 1 层：启动校验 (LicenseVerifier)            │
│  4 URL 并发校验，全部返回 "yes" 才通过            │
│  断网直接白屏拦截                                 │
├──────────────────────────────────────────────┤
│  第 2 层：环境检测 (SecurityGuard)              │
│  反调试 / 反Root / 反模拟器 / 反Hook / 反抓包      │
├──────────────────────────────────────────────┤
│  第 3 层：签名校验 (SecurityGuard)              │
│  SHA-256 签名摘要比对                            │
├──────────────────────────────────────────────┤
│  第 4 层：播放闸门 (MusicPlaybackGate)          │
│  解析 / 取链 / 播放 / 下载 四节点拦截             │
├──────────────────────────────────────────────┤
│  第 5 层：UI 保护 (CriticalUiProtector)         │
│  关键 UI 常量指纹 + 渲染时序校验                  │
├──────────────────────────────────────────────┤
│  第 6 层：字符串保护 (StringEncryptor/Obfuscator) │
│  敏感字符串加密 + 混淆                            │
└──────────────────────────────────────────────┘
```

### BuildConfig 安全字段

| 字段  | 说明  |
| --- | --- |
| `SECURITY_KILL_ON_RISK` | 检测到高危风险时是否杀进程 |
| `EXPECTED_SIGNATURE` | 预期签名 SHA-256（Release 校验） |
| `API_APP_ID` | API 应用 ID |
| `API_AUTH_KEY` | API 鉴权密钥（构建时注入） |

---

## 八、后端服务

### houduan/ — Node.js 校验后端

#### 功能

1. 静态托管 4 个 txt 校验文件（`wyy.txt` / `kw.txt` / `kg.txt` / `qq.txt`），返回纯文本 `yes`
2. CORS 跨域配置
3. 安全响应头（`nosniff` / `DENY` / `no-store`）
4. 设备指纹审计日志
5. 支持 HTTPS（生产环境必须）

#### 启动方式

```bash
cd houduan
npm install

# 开发环境（HTTP）
npm start

# 生产环境（HTTPS）
HTTPS=true SSL_KEY=/path/to/key.pem SSL_CERT=/path/to/cert.pem \
ALLOWED_ORIGINS=https://app.yourdomain.com \
npm start
```

#### 关闭授权

将对应 txt 文件内容改为 `no` 即可让所有客户端白屏拦截。

### server/ — Java/Maven 服务端

`server/pom.xml` — Java 服务端项目，用于扩展后端能力。

---

## 九、歌词 API 模块

独立歌词 API 模块（`kotlin/com/lyrics/api/`），提供多平台歌词搜索与获取：

### 架构

```
LyricsApi（入口）
├── KugouProvider    — KG歌词
├── NeteaseProvider  — WY歌词
└── QQMusicProvider  — TX音乐歌词
```

### 核心功能

- `search(keyword, platforms, limit)` — 并发搜索多平台歌词
- `getLyrics(platform, songId)` — 获取指定歌曲的歌词（LRC + 翻译）
- `LyricsParser` — LRC 歌词解析（支持逐字歌词）

### 支持平台

| 代码  | 平台  |
| --- | --- |
| `kg` | KG音乐 |
| `ne` | WY音乐 |
| `tx` | TX音乐 |

---

## 十、构建与配置

### 环境要求

- **Android Studio** — Hedgehog 或更高
- **JDK** — 17
- **Android SDK** — API 36（compileSdk）
- **Gradle** — 配置缓存已启用

### 构建配置

#### 必需参数

| 参数  | 说明  | 获取方式 |
| --- | --- | --- |
| `API_AUTH_KEY` | API 鉴权密钥（构建 APK 必填） | `-PAPI_AUTH_KEY=<KEY>` 或环境变量或 `local.properties` |

#### 可选参数

| 参数  | 说明  | 默认值 |
| --- | --- | --- |
| `API_APP_ID` | API 应用 ID | `cloud_music` |
| `EXPECTED_SIGNATURE_HASH` | 预期签名 SHA-256 | 空（跳过签名校验） |
| `ENFORCE_RELEASE_SIGNATURE_HASH` | 是否强制要求签名哈希 | `false` |

### 构建命令

```bash
# Debug 构建
./gradlew assembleDebug -PAPI_AUTH_KEY=your_key

# Release 构建（含混淆）
./gradlew assembleRelease -PAPI_AUTH_KEY=your_key -PEXPECTED_SIGNATURE_HASH=<sha256_hex>

# 按 ABI 拆分生成独立 APK（4 个包）
# arm64-v8a / armeabi-v7a / x86 / x86_64
# 不生成通用包（减少体积）
```

### local.properties 示例

```properties
API_AUTH_KEY=your_api_auth_key
API_APP_ID=cloud_music
# EXPECTED_SIGNATURE_HASH=a1b2c3d4e5f6...（64位小写hex）
# ENFORCE_RELEASE_SIGNATURE_HASH=true
```

### Gradle 优化

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8 -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.incremental=true
```

### Maven 仓库

项目使用阿里云镜像加速依赖下载：

- `https://maven.aliyun.com/repository/google`
- `https://maven.aliyun.com/repository/central`
- `https://maven.aliyun.com/repository/public`
- `https://maven.aliyun.com/repository/gradle-plugin`
- `https://jitpack.io`

---

## 十一、安装与使用

### 方式一：下载 APK

从 [Releases](https://github.com/88541/YinDong-Music/releases) 页面下载最新版本的 APK 文件安装。

### 方式二：自行编译

1. **克隆仓库**
  
  ```bash
  git clone https://github.com/88541/YinDong-Music.git
  ```
  
2. **配置密钥**
  在 `local.properties` 中添加：
  
  ```properties
  API_AUTH_KEY=your_api_auth_key
  ```
  
3. **打开项目**
  使用 Android Studio 打开项目目录
  
4. **编译运行**
  点击 Run 按钮编译并安装到设备，或使用命令行：
  
  ```bash
  ./gradlew assembleDebug -PAPI_AUTH_KEY=your_key
  ```
  

### 权限说明

| 权限  | 用途  |
| --- | --- |
| `INTERNET` | 网络请求 |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 网络状态检测 |
| `WAKE_LOCK` | 播放时保持 CPU 唤醒 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 前台播放服务 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 悬浮歌词服务 |
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` | 读取本地音乐 |
| `MANAGE_EXTERNAL_STORAGE` | 下载到公共目录 |
| `POST_NOTIFICATIONS` | 通知栏媒体控件 |
| `SYSTEM_ALERT_WINDOW` | 悬浮歌词 |
| `RECORD_AUDIO` | 听歌识曲 |
| `BLUETOOTH_CONNECT` | 蓝牙耳机控制 |
| `REQUEST_INSTALL_PACKAGES` | APK 更新安装 |
| `READ_PHONE_STATE` | 来电暂停播放 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 电池优化白名单 |

---

## 十二、API 配置指南

### 配置入口

1. 打开 App → 点击右下角 **"我的"**
2. 点击右上角 **设置图标**
3. 找到 **"音乐API地址"** → 点击进入

### 配置项

| 配置项 | 说明  |
| --- | --- |
| API 模式 | 本地 API / 官方 API |
| API 地址 | 你的音源 API 服务地址 |
| TX音乐 API Key | TX音乐平台密钥 |
| WY API Key | WY平台密钥 |
| KW音乐 API Key | KW平台密钥 |
| MG音乐 API Key | MG平台密钥 |
| KG音乐 API Key | KG平台密钥 |
| TX Cookie | TX音乐 Cookie（获取播放链接） |

### API Key 格式要求

- 长度：16-128 位
- 字符：只能包含字母(a-z A-Z)和数字(0-9)

### TX音乐 Cookie 获取

**手机获取**：

1. 手机浏览器打开TX音乐网页版并登录
2. 在地址栏输入 `javascript:alert(document.cookie)`
3. 复制弹出的 Cookie 内容

**电脑获取**：

1. 电脑浏览器打开TX音乐网页版并登录
2. 按 F12 打开开发者工具 → Network 标签
3. 刷新页面，点击任意请求
4. 在 Headers → Request Headers 中找到 Cookie 行，复制整串内容

> Cookie 有效期约 1-2 周，过期需重新获取。

### 音质选择

| 音质  | 码率  | 说明  |
| --- | --- | --- |
| 128K | 128kbps | 省流量 |
| 320K | 320kbps | 推荐，均衡 |
| FLAC | 无损  | 最高音质，文件大 |

### 插件配置

- 支持 LX Music 插件格式（`.js` 文件）
- 支持 MusicFree 插件格式
- 可在设置中导入插件扩展音乐源
- 支持从文件管理器/浏览器直接打开 `.js` 文件导入

---

## 十三、免责声明

本项目仅供学习交流使用，请勿用于商业用途。音乐版权归原平台所有。

使用者需自行准备音源 API 服务，软件仅提供配置入口，不提供任何 API 服务。

本项目采用 [MIT](LICENSE) 协议开源。

---

## 联系方式

- **官方 QQ 群**：673778042（音动音乐×众和夜雨科）
- **GitHub Issues**：[提交问题](https://github.com/88541/YinDong-Music/issues)

---

## 致谢

感谢以下开源项目：

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [ExoPlayer / Media3](https://github.com/google/ExoPlayer)
- [OkHttp](https://github.com/square/okhttp)
- [Coil](https://github.com/coil-kt/coil)
- [QuickJS](https://github.com/nicolo-ribaudo/tc39-proposal-quickjs)
- [Lottie](https://github.com/airbnb/lottie-android)
- [Room](https://developer.android.com/training/data-storage/room)
- [jaudiotagger](https://bitbucket.org/ijabz/jaudiotagger/src/master/)

---

<p align="center">
  Made with ❤️ by 音动音乐团队
</p>
## 📄 开源协议

本项目采用 [MIT](LICENSE) 协议开源。

## 💬 联系我们

- **官方 QQ 群**: [673778042](https://github.com/88541/YinDong-Music#) - 音动音乐×众和夜雨科
- **GitHub Issues**: [提交问题](https://github.com/88541/YinDong-Music-Android/issues)

## 🙏 致谢

感谢以下开源项目：

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [ExoPlayer](https://github.com/google/ExoPlayer)
- [OkHttp](https://github.com/square/okhttp)
- [Coil](https://github.com/coil-kt/coil)

## ⚠️ 免责声明

本项目仅供学习交流使用，请勿用于商业用途。音乐版权归原平台所有。

---

<p align="center">
  Made with ❤️ by 音动音乐团队
</p>
