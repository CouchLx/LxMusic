# 音乐源插件（兼容落雪音乐 JS 自定义源）技术方案

日期：2026-10-23
状态：设计稿（待评审后实施）

## 1. 目标与边界

### 目标
- App 内置播放器、搜索、歌词、歌单、播放队列等能力不变；
- **不内置任何在线音乐源、不内置任何服务器地址、不内置官方音源接口**；
- 支持用户**自行导入 JS 音乐源插件**，获得在线搜索 / 取流 / 歌词 / 封面能力；
- 尽可能兼容 [lx-music-desktop](https://github.com/lyswhut/lx-music-desktop) 的「自定义源」脚本格式；
- 插件可启用/停用/删除，可同时装多个源，搜索页可切换音源。

### 非目标（本方案不做）
- 不内置、不捆绑、不推荐任何具体音乐源脚本；
- 不做官方网易云/QQ/酷狗接口的直连实现；
- 不提供服务器端聚合（插件在用户设备本地运行）。

### 合规说明（技术事实，非法律意见）
本方案交付的是**插件运行时**（类似浏览器/播放器内核），不包含任何音乐内容或音源脚本。
用户导入的插件由用户自行获取并承担相应责任。仓库与 APK 中不应提交任何第三方音源脚本。

## 2. 总体架构

```
┌────────────────────────────── App ──────────────────────────────┐
│ UI：搜索页 / 设置页(插件管理) / 播放器 / 歌词页                    │
│            │                    │                                │
│   MusicSourceRegistry   ──►  MusicSourceProvider (统一接口)       │
│            │                    │                                │
│   ┌────────┴─────────┬──────────┴───────────┐                    │
│   │ KuGouSource      │ NeteaseSource        │ PluginSource        │
│   │ (现有逻辑)        │ (现有逻辑)            │ (JS 运行时)          │
│   └──────────────────┴──────────────────────┘                    │
│                              │                                   │
│              SongDataSource / LyricRepository / SearchPage        │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                     QuickJS 沙箱（每插件一个 runtime）
                               │ lx.request（OkHttp 桥）
                               ▼
                        用户导入的 JS 音源脚本
```

## 3. 运行时选型（已验证）

选用 **`io.github.taoweiji.quickjs:quickjs-android:1.4.6`**（Maven Central / 阿里云镜像可拉取）。

验证结论（本地已下载 AAR 检查）：
- AAR 约 2.1MB，含 arm64-v8a / armeabi-v7a / x86 / x86_64；
- 提供 `CommonJSModule`（`module.exports` / `require` 基础能力）——落雪源正是 CommonJS 格式；
- 提供 `JSObject` / `JSFunction` / `JavaCallback` 双向调用（JS 可调用 Java 方法）；
- 自带 `ConsolePlugin`、`SetTimeoutPlugin`、`WorkerPlugin`、事件队列，适合异步请求回调；
- 相比 J2V8（AAR 31MB，单 ABI so 22~29MB）体积小一个数量级。

JS 语言能力：QuickJS 支持 ES2020（async/await、可选链、模板字符串等），可覆盖绝大多数落雪源。

## 4. 插件格式与宿主 API

### 4.1 落雪源格式（兼容目标）
落雪自定义源脚本主体形如：

```js
module.exports = {
  name: '示例源',
  version: '1.0.0',
  author: '',
  description: '',
  api: {
    search(keyword, page, limit) { /* 返回 Promise 或回调 */ },
    getMusicUrl(songInfo, quality) { /* Promise<string> */ },
    getLyric(songInfo) { /* Promise<{lyric, tlyric?}> */ },
    getPic(songInfo) { /* Promise<string> */ },
  },
}
```

脚本运行在宿主编排的沙箱里，宿主提供全局 `lx` 对象（落雪文档中的 `lx.request`、`lx.utils` 等）。

### 4.2 宿主提供的全局对象（最小兼容集）
| 名称 | 说明 | 实现 |
|---|---|---|
| `lx.request(url, options, callback)` | HTTP 请求，回调 `(err, resp)`；`resp.body/statusCode/headers` | OkHttp（异步，回调投递回 JS 线程） |
| `lx.utils.crypto` | `md5/sha1/sha256/hmac/aesEncrypt/rsaEncrypt/randomBytes` 常用子集 | JVM `MessageDigest` / `Mac` / `Cipher` |
| `lx.utils.buffer` | `from/alloc/concat/toString('hex'\|'base64'\|'utf8')` | 自实现 UTF-8/Base64 工具 |
| `lx.EVENT_NAMES` | 常量集合 | 固定常量 |
| `lx.env` / `lx.version` | 版本信息 | 固定值 |
| `require('crypto' / 'buffer' / 'url' / 'querystring' / 'zlib')` | Node 内置模块子集 | 对应 Java 实现 |
| `console` / `setTimeout` / `setInterval` | 调试与异步 | 引擎自带插件 |
| `Promise` / `async` / `await` | 引擎原生 | QuickJS |

**注意**：`lx.request` 必须支持回调与超时；插件里常见的 `callback(null, { statusCode, body })` 语义要严格对齐。

### 4.3 不支持的能力（明确降级）
- `require('fs')`、`process`、`child_process`、原生模块：一律不提供（安全边界）；
- 依赖 Node 特定行为（Buffer 某些边缘 API、zlib 流式压缩）的源可能失败；
- 需要额外 npm 依赖的源：除非源本身已打包依赖，否则无法运行。

插件加载失败时，UI 显示具体错误（脚本语法错误 / 初始化超时 / 调用的 API 不支持）。

## 5. 数据模型（关键设计）

### 5.1 歌曲标识
新增 `lxsrc://` 前缀，格式：

```
lxsrc://<sourceId>/<songKey>
```

- `sourceId`：插件安装时生成的稳定 ID（如 `pl_<sha1(文件名+安装时间)>`）；
- `songKey`：插件返回歌曲对象的指纹（建议 `sha1(JSON)` 或插件自带 id）；
- 歌曲的可展示字段（title/artist/album/cover/duration）直接存在 `SongInfo` 里；
- 插件原始歌曲对象（`getMusicUrl` 需要的完整字段）持久化到新表 `plugin_song`（path → rawJson）。

这样 `filePath` 短、稳定，收藏/歌单/历史都能正常引用；若 rawJson 丢失（如换机恢复歌单），取流时可用 title+artist 调插件 `search` 反查，失败则提示「源数据缺失，请重新搜索」。

### 5.2 音源注册
`MusicSourceRegistry` 暴露：
- `searchSourceList()`：内置源（酷狗/网易云，可配置隐藏）+ 已启用插件源；
- `providerFor(sourceId)`；
- `providerForPath(filePath)`。

## 6. 项目落点（按现有代码）

### 6.1 新增文件
```
data/source/MusicSourceProvider.kt      // 统一接口
data/source/KuGouSourceProvider.kt      // 现有 KuGouApi 适配
data/source/NeteaseSourceProvider.kt    // 现有 NeteaseApi 适配
data/source/PluginSourceProvider.kt     // 插件源实现
data/plugin/PluginStore.kt              // 插件安装目录、元数据、启用列表
data/plugin/PluginInstaller.kt          // SAF 导入、校验、复制
data/plugin/QuickJsRuntime.kt           // runtime 生命周期与线程模型
data/plugin/LxHostApi.kt                // lx 对象、request、crypto 桥
data/plugin/LxCompatShims.kt            // require/Buffer/console 等
data/plugin/PluginSongStore.kt          // rawJson 持久化
db 迁移：plugin_song 表（Room 版本 +1）
ui/pages/PluginSettingsPage.kt          // 插件管理页
```

### 6.2 改造点
| 现状 | 改造 |
|---|---|
| `SongDataSource` 里 `song://`（酷狗）与 `netease://` 分支 | 抽出 `provider.open()`；新增 `lxsrc://` 分支 → `PluginSourceProvider.getMusicUrl()` → DefaultHttpDataSource 播放 |
| `LyricRepository` 多处 `isNeteasePath` 分支 | 先查 `providerForPath(path)?.getLyric()`，失败再走现有酷狗/网易云兜底 |
| `SearchPage` 的 `searchSource`（kugou/netease） | 动态列表：内置 + 已启用插件；插件源走 `provider.search()` |
| `PlayerViewModel` / `SongContextMenuActions` 的 `isNeteasePath` 判断 | 改为「是否本地文件」判断；插件歌曲视为在线歌曲（收藏、来源显示、分享文案等） |
| 设置页 | 新增「音乐源插件」入口：导入、启用、停用、删除、查看错误日志 |

## 7. 实施阶段与验收

### M1 音源抽象（不改变行为）
- 引入 `MusicSourceProvider`，把酷狗/网易云现有调用收口；
- 验收：现有搜索、播放、歌词、收藏、歌单回归通过（用户视角零变化）。

### M2 插件管理
- SAF 导入 `.js`、元数据读取（沙箱里执行并取 `name/version`）、启用列表、删除；
- 验收：可导入/启停/删除；重启后列表保持。

### M3 运行时最小闭环
- QuickJS + CommonJS 加载 + `lx.request` + console/setTimeout；
- 用一个本地测试桩源（不入库）验证 `search` / `getMusicUrl` 返回；
- 验收：能在日志/调试页看到插件返回的 URL。

### M4 链路接入
- 搜索页音源选择、`lxsrc://` 播放、歌词、封面；
- 验收：用 1~2 个真实插件完成「搜索 → 播放 → 歌词」全链路。

### M5 兼容与稳定
- 补齐 crypto/buffer 常用 API、错误提示、超时与崩溃自恢复（每个插件独立 runtime，异常重建）；
- 验收：目标插件清单逐个通过，失败项记录原因。

## 8. 风险与对策

| 风险 | 说明 | 对策 |
|---|---|---|
| 兼容率不确定 | 落雪源写法差异大，部分依赖 Node 特性 | 以「常用 API 子集」为准，建立插件兼容测试清单；失败时明确报错 |
| 安全 | 插件是可执行 JS | 沙箱无文件/系统访问；只暴露 `lx.request`；导入前提示；单插件超时与内存上限 |
| 稳定性 | 插件死循环/异常会拖垮 App | 独立线程 + 独立 runtime；调用超时（如 15s）；异常后关闭并重建；禁止主线程执行 |
| APK 体积 | 引擎 2.1MB（多 ABI） | 可接受；如后续支持 App Bundle 可按 ABI 下发 |
| 合规 | 用户导入的源可能涉及版权 | App 不内置源、不提供源下载；README 与导入页明确用户自担责任 |
| 维护 | 上游 API 变化 | 插件与 App 解耦，源的问题由源作者修复；App 只维护运行时兼容层 |

## 9. 待确认
1. 是否接受「插件运行时 + 用户自行导入」的定位（不内置任何源、不内置任何源地址）？
2. 首批要重点兼容的落雪源清单（提供 1~2 个脚本用于本地测试，**不要提交到仓库**）。
3. 插件管理入口放在「设置 → 音乐源插件」是否合适（与「激活与在线服务」同级）。
