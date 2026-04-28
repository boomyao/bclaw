# bclaw Host Agent

这个目录是当前 beta 的 host agent。产品主路径是 remote desktop：手机通过 HTTP 调用 host agent 管理本机 Sunshine，随后直连 Sunshine 的 Moonlight-compatible stream。

## 工作方式

- 手机配对到 `bclaw2://host:8766?tok=...`。
- Android remote desktop surface 用 `Authorization: Bearer ...` 调用 `GET /v1/sunshine/catalog`、`POST /v1/sunshine/session/start` 等接口。
- host agent 负责 Sunshine lifecycle、display selection、GameStream pairing/session negotiation、Wake-on-LAN metadata 和 macOS input helper。

## 文件

- `server.js`: 当前 beta 的 host-agent 服务。
- `package.json`: 依赖与启动脚本。
- `start.sh`: 启动入口。

## 公开一键安装

面向开源用户的推荐入口是根目录 `install.sh`。它可以直接通过 GitHub raw URL 执行，会把仓库安装/更新到 `~/.bclaw/bclaw`，然后自动选择 macOS 或 Linux host-agent 安装器:

```bash
curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash
```

安装完成后下载 Android APK:

```text
https://github.com/boomyao/bclaw/releases/latest/download/bclaw-android-debug.apk
```

然后生成手机配对 URL / QR:

```bash
~/.bclaw/bclaw/scripts/bclaw-handoff --qr
```

## Beta 一键安装（macOS）

推荐给 beta 用户的入口是在仓库根目录执行:

```bash
scripts/install-macos-host-agent
```

旧入口 `scripts/install-macos-bridge` 仍然保留，会转发到新的安装脚本。

它会完成:

- 安装 Homebrew（如果机器上没有，使用 Homebrew 官方安装脚本）。
- 安装 Node.js 18+ 与 host agent 的 npm 依赖。
- 安装 Sunshine（默认 `brew tap LizardByte/homebrew && brew install sunshine`）。
- 生成 `~/.bclaw/host-agent.token`（如果还不存在；旧 `~/.bclaw/ws.token` 会自动迁移）。
- 注册并启动用户级 launchd 服务 `dev.bclaw.host-agent`，之后登录时自动启动。
- 启动一次 Sunshine，以便 macOS 弹出首次屏幕录制 / 麦克风权限授权。

安装完成后，用下面的命令生成手机配对 URL / QR:

```bash
scripts/bclaw-handoff --qr
```

可选环境变量:

```bash
PORT=9000 scripts/install-macos-host-agent
BCLAW_SUNSHINE_FORMULA=sunshine-beta scripts/install-macos-host-agent
BCLAW_SUNSHINE_USER=... BCLAW_SUNSHINE_PASSWORD=... scripts/install-macos-host-agent
BCLAW_INSTALL_HOMEBREW=0 scripts/install-macos-host-agent
BCLAW_START_SUNSHINE=0 scripts/install-macos-host-agent
```

日志位置:

- `~/Library/Logs/bclaw/host-agent.out.log`
- `~/Library/Logs/bclaw/host-agent.err.log`

macOS 的隐私权限（Screen Recording / Microphone / Accessibility）必须由用户在系统弹窗或 System Settings 里授权，脚本不能可靠绕过。

## Beta 一键安装（Ubuntu）

推荐给 Ubuntu beta 用户的入口是在仓库根目录执行:

```bash
scripts/install-linux-host-agent
```

也可以使用同义入口:

```bash
scripts/install-ubuntu-host-agent
```

它会完成:

- 安装 apt 基础依赖（`curl`、`openssl`、`build-essential` 等）。
- 安装 Node.js 18+；如果 apt 自带版本过旧，默认使用 NodeSource 安装 Node.js 20。
- 安装 Sunshine（默认从 LizardByte GitHub latest release 下载 Ubuntu 22.04/24.04 `.deb`）。
- 生成 `~/.bclaw/host-agent.token`（如果还不存在；旧 `~/.bclaw/ws.token` 会自动迁移）。
- 注册并启动用户级 systemd 服务 `dev.bclaw.host-agent`。
- 启动一次 Sunshine。

可选环境变量:

```bash
PORT=9000 scripts/install-linux-host-agent
BCLAW_SUNSHINE_DEB_URL=https://example/sunshine.deb scripts/install-linux-host-agent
BCLAW_SUNSHINE_USER=... BCLAW_SUNSHINE_PASSWORD=... scripts/install-linux-host-agent
BCLAW_INSTALL_SUNSHINE=0 scripts/install-linux-host-agent
BCLAW_START_SUNSHINE=0 scripts/install-linux-host-agent
BCLAW_INSTALL_NODESOURCE=0 scripts/install-linux-host-agent
BCLAW_ENABLE_LINGER=1 scripts/install-linux-host-agent
```

日志位置:

- `~/.local/state/bclaw/host-agent.out.log`
- `~/.local/state/bclaw/host-agent.err.log`

## 手动安装

在 `host-agent/` 目录安装依赖:

```bash
npm install
```

运行时依赖:

- Node.js 18+。
- Sunshine：remote desktop 强依赖。
- `openssl`：生成 Sunshine native client 证书。
- Xcode Command Line Tools：macOS 首次使用 pinch helper 时需要 `xcrun clang` 编译 `macos_input_helper.c`。
- `build-essential`：Linux 安装脚本会确保存在，后续 native helper 扩展可复用。

## 启动

默认监听 `0.0.0.0:8766`:

```bash
cd host-agent
./start.sh
```

也可以用环境变量覆盖端口:

```bash
cd host-agent
PORT=9000 ./start.sh
```

## Host status

- `GET /v1/host/status`: host summary + remote status + enabled feature flags。
- `GET /v1/host/capabilities`: host capability flags。

旧入口 `GET /status`、`GET /host/status`、`GET /host/capabilities` 仍然兼容。

## Auth

Host-agent API endpoints require the capability token from `~/.bclaw/host-agent.token` or `BCLAW_TOKEN_FILE`.

Supported forms:

```bash
curl -H "Authorization: Bearer $(cat ~/.bclaw/host-agent.token)" http://127.0.0.1:8766/v1/host/status
curl -H "X-Bclaw-Token: $(cat ~/.bclaw/host-agent.token)" http://127.0.0.1:8766/v1/host/status
curl "http://127.0.0.1:8766/v1/host/status?tok=$(cat ~/.bclaw/host-agent.token)"
```

Do not expose the host-agent port directly to the public internet.

## Remote desktop

remote desktop 走 Sunshine。host agent 只负责控制设备端 Sunshine，不代理视频流；移动端会直接连接 Sunshine 的 Moonlight-compatible stream，并逐步替换成 bclaw 自研 native client。

Host 侧准备:

1. 安装并配置 Sunshine。
2. 按需设置 `BCLAW_SUNSHINE_PREPARE_CMD` / `BCLAW_SUNSHINE_CLOSE_CMD`，用于进入/退出 remote 前后的自定义显示器、唤醒、分辨率逻辑。
3. 保持 host agent 运行；Android remote desktop 会通过 host agent 检查和启动本机 Sunshine。

host agent 暴露:

- `GET /v1/remote/status`: Sunshine-only status，兼容旧客户端入口名。
- `GET /v1/sunshine/status`: Sunshine 安装、进程和 API 可达性。
- `GET /v1/sunshine/start`: 启动 `/Applications/Sunshine.app`、Homebrew、系统包或 Snap 路径里的 `sunshine`。
- `GET /v1/sunshine/apps`: 代理 Sunshine `GET /api/apps`。
- `GET /v1/sunshine/catalog`: 规范化 Sunshine apps/display/wake 状态，供 Android UI 直接展示。
- `GET /v1/sunshine/displays`: 从 Sunshine 日志和配置中读取可串流显示器。
- `POST /v1/sunshine/display/select`: 写入 Sunshine `output_name`，用于选择单块显示器串流。
- `POST /v1/sunshine/session/start`: 确认 pairing，按请求的 `displayId` 配置 Sunshine，向 GameStream HTTPS `/launch` 发起会话，并返回 bclaw native client 后续 RTSP/UDP 所需的 launch metadata。
- `GET /v1/sunshine/client/pair`: 生成 bclaw client cert/key，并用 Sunshine admin API 自动提交 PIN 完成 GameStream pairing。
- `POST /v1/sunshine/pair`: 代理 Sunshine `POST /api/pin`。
- `GET /v1/sunshine/prepare`: 执行 `BCLAW_SUNSHINE_PREPARE_CMD`，未配置时返回 skipped。
- `GET /v1/sunshine/close`: 请求 Sunshine close 当前 app，再执行 `BCLAW_SUNSHINE_CLOSE_CMD`，未配置时返回 skipped。
- `POST /v1/input/macos/pinch`: macOS trackpad pinch helper。
- `POST /v1/input/ai/text`: 用 `gpt-5.4-mini` + `reasoningEffort=none` 将手机输入的粗略文本推断为要发到远端桌面的精确文本。

旧 `/remote/...` endpoints 仍然保留为兼容入口。

可选环境变量:

```bash
BCLAW_SUNSHINE_API_URL=https://127.0.0.1:47990 ./start.sh
BCLAW_SUNSHINE_AUTH='Basic ...' ./start.sh
BCLAW_SUNSHINE_USER=... BCLAW_SUNSHINE_PASSWORD=... ./start.sh
BCLAW_SUNSHINE_CONFIG=~/.config/sunshine/sunshine.conf ./start.sh
BCLAW_SUNSHINE_LOG=~/.config/sunshine/sunshine.log ./start.sh
BCLAW_SUNSHINE_PREPARE_CMD='~/.config/sunshine/bclaw-prepare.sh' ./start.sh
BCLAW_SUNSHINE_CLOSE_CMD='~/.config/sunshine/bclaw-close.sh' ./start.sh
```

VNC/noVNC 已移除；连不上 Sunshine 时客户端应进入连接失败链路，而不是回退到 VNC。

Android remote desktop 当前会在 `STREAM` 后按选中的 display 直连 Sunshine RTSP 端口执行 `OPTIONS` / `DESCRIBE` / `SETUP audio` / `SETUP video` / `SETUP control` / `ANNOUNCE` / `PLAY`，并把 native `SurfaceView` 挂到画面区域。Wake 按钮会使用 host agent 上一次返回的 LAN broadcast 和 MAC 发送 Wake-on-LAN magic packet；跨网段或 Tailnet 场景仍需要 LAN 内 relay。

## 日志

启动、连接、退出事件会输出到标准日志；launchd 安装时写入 `~/Library/Logs/bclaw/host-agent.*.log`，systemd 用户服务安装时写入 `~/.local/state/bclaw/host-agent.*.log`。
