# LXMusic 播放器

一个基于 Jetpack Compose 的安卓音乐播放器，支持在线播放（酷狗音乐源）、本地播放、USB 独占输出、歌词（含逐字卡拉OK）、自定义主题等。

## 功能特性

- 🎵 在线播放：每日推荐、排行榜、搜索、歌单（酷狗音乐源）
- 📱 本地播放：扫描本地音乐、收藏管理
- 🎤 歌词：逐字卡拉OK 歌词、翻译、滚动歌词
- 🎨 界面：Material You 动态主题、液态玻璃效果、Mesh 渐变背景
- 🔌 USB 音频：USB DAC 独占输出（UAC1/UAC2）、采样率/位深切换
- 📡 应用内更新：多镜像检查 GitHub Releases 新版本，一键升级

## 项目结构

```
app/src/main/java/com/example/lxmusic/
├── KuGouApi.kt              # 酷狗 API 客户端（Retrofit）
├── SongDataSource.kt        # 播放 URL 解析（音质回退链）
├── VipConfigManager.kt      # 激活码/VIP 配置管理
├── UpdateChecker.kt         # 应用更新检查与安装
├── PlayerService.kt         # Media3 播放服务
├── PlayerViewModel.kt       # 播放状态管理
└── ui/
    ├── pages/               # 各页面（首页/发现/我的/设置/播放器/歌词）
    ├── lyrics/              # 歌词组件（逐字卡拉OK、翻译）
    ├── components/          # 通用组件（底栏、卡片等）
    └── theme/               # 主题系统
server_vip_api.template.js   # 激活码服务端模板（自建后端用）
.github/workflows/release.yml # 自动构建发布 APK
```

## 构建

需要 Android SDK 34+、JDK 17。

```bash
./gradlew assembleDebug   # 调试包
./gradlew assembleRelease # 发布包
```

### 本地配置（可选）

复制 `keystore.properties.example` 为 `keystore.properties`，填写你的服务器地址和签名信息：

```properties
# 酷狗 API 服务器地址（自建后端，见 server_vip_api.template.js）
LX_SERVER_URL=http://your-server:3000/
# 设置页"管理员验证"密码
LX_ADMIN_PASSWORD=
# 应用更新 version.json 地址（可选，留空则用 GitHub 镜像）
LX_UPDATE_VERSION_URL=
# Release 签名（可选，不配置则 APK 未签名）
STORE_FILE=release.jks
STORE_PASSWORD=
KEY_ALIAS=
KEY_PASSWORD=
```

> **注意**：`keystore.properties` 已被 .gitignore 排除，不会提交到仓库。

## 自建后端

本项目的在线音乐功能需要一个酷狗 API 后端。推荐部署开源项目 [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi)，并将 `server_vip_api.template.js` 挂载到同一服务上提供激活码功能：

1. 复制 `server_vip_api.template.js` 为 `server_vip_api.js` 并部署
2. 通过环境变量配置 VIP 账号与管理密钥：
   ```bash
   VIP_TOKEN=你的酷狗VIP账号token VIP_USERID=你的酷狗VIP账号userid ADMIN_KEY=你的管理密钥 node server.js
   ```
3. 修改 `ACTIVATE_CODES` 生成激活码发给朋友
4. 客户端设置页 → VIP 服务 → 输入激活码激活

## 自动发布

推送 `v*` 标签（如 `v3.7.56`）到 GitHub 会自动触发 [release.yml](.github/workflows/release.yml)：

- 构建 Release APK
- 生成 `publish/version.json`（客户端更新检查用）
- 上传 APK 与 version.json 到 GitHub Releases

需要配置仓库 Secrets：`LX_SERVER_URL`、`LX_ADMIN_PASSWORD`、`STORE_FILE`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`、`KEYSTORE_BASE64`（keystore 的 base64）。

客户端在设置 → 关于 → 检查更新，会依次尝试多个镜像（GitHub raw / jsdelivr / 国内加速）获取 version.json。

## 免责声明

本项目仅用于学习和个人使用。音乐内容的版权归原平台所有，请勿将本项目用于商业用途。使用 VIP 共享功能可能违反第三方平台服务条款，后果自负。

## License

本项目代码仅供学习交流使用。第三方开源组件遵循其各自的开源协议。
