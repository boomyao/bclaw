# Mac ACP Bridge Server

这个目录提供一个极简 Node.js bridge，把手机侧的 WebSocket 文本帧转成本机 ACP agent CLI 的 stdio 行协议。

## 工作方式

- 客户端连接 `ws://host:8766/<agent>`
- bridge 根据 `agents.json` 里的配置启动对应 agent 子进程
- WebSocket text frame 会被转发为一条 UTF-8 JSON-RPC 行并追加 `\n`
- agent stdout 每读到一行，就作为一个 WebSocket text frame 发回客户端
- 每个 WebSocket 连接独占一个 agent 子进程
- WebSocket 断开时会终止子进程，子进程退出时也会关闭 WebSocket

## 文件

- `server.js`: 主服务
- `agents.json`: agent 启动命令配置
- `package.json`: 依赖与启动脚本
- `start.sh`: 启动入口

## 安装

在 `bridge/` 目录手动安装依赖:

```bash
npm install
```

运行时依赖:

- `ws`: ACP WebSocket bridge。

## 启动

默认监听 `0.0.0.0:8766`:

```bash
cd bridge
./start.sh
```

也可以用环境变量覆盖端口:

```bash
cd bridge
PORT=9000 ./start.sh
```

## 连接示例

- Claude: `ws://host:8766/claude`
- Codex: `ws://host:8766/codex`
- Gemini: `ws://host:8766/gemini`

路径名必须和 `agents.json` 里的 agent key 一致。

## Remote desktop

remote sidecar 走 Sunshine。bridge 只负责控制设备端 Sunshine，不代理视频流；移动端会直接连接 Sunshine 的 Moonlight-compatible stream，并逐步替换成 bclaw 自研 native client。

Mac 侧准备:

1. 安装并配置 Sunshine。
2. 按需设置 `BCLAW_SUNSHINE_PREPARE_CMD` / `BCLAW_SUNSHINE_CLOSE_CMD`，用于进入/退出 remote 前后的自定义显示器、唤醒、分辨率逻辑。
3. 保持 bridge 运行；Android remote sidecar 会通过 bridge 检查和启动本机 Sunshine。

bridge 暴露:

- `GET /remote/status`: Sunshine-only status，兼容旧客户端入口名。
- `GET /remote/sunshine/status`: Sunshine 安装、进程和 API 可达性。
- `GET /remote/sunshine/start`: 启动 `/Applications/Sunshine.app` 或 `/opt/homebrew/bin/sunshine`。
- `GET /remote/sunshine/apps`: 代理 Sunshine `GET /api/apps`。
- `GET /remote/sunshine/catalog`: 规范化 Sunshine apps/display/wake 状态，供 Android UI 直接展示。
- `GET /remote/sunshine/displays`: 从 Sunshine 日志和配置中读取可串流显示器。
- `POST /remote/sunshine/display/select`: 写入 Sunshine `output_name`，用于选择单块显示器串流。
- `POST /remote/sunshine/session/start`: 确认 pairing，按请求的 `displayId` 配置 Sunshine，向 GameStream HTTPS `/launch` 发起会话，并返回 bclaw native client 后续 RTSP/UDP 所需的 launch metadata。
- `GET /remote/sunshine/client/pair`: 生成 bclaw client cert/key，并用 Sunshine admin API 自动提交 PIN 完成 GameStream pairing。
- `POST /remote/sunshine/pair`: 代理 Sunshine `POST /api/pin`。
- `GET /remote/sunshine/prepare`: 执行 `BCLAW_SUNSHINE_PREPARE_CMD`，未配置时返回 skipped。
- `GET /remote/sunshine/close`: 请求 Sunshine close 当前 app，再执行 `BCLAW_SUNSHINE_CLOSE_CMD`，未配置时返回 skipped。

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

Android remote sidecar 当前会在 `STREAM` 后按选中的 display 直连 Sunshine RTSP 端口执行 `OPTIONS` / `DESCRIBE` / `SETUP audio` / `SETUP video` / `SETUP control` / `ANNOUNCE` / `PLAY`，并把 native `SurfaceView` 挂到画面区域。Wake 按钮会使用 bridge 上一次返回的 LAN broadcast 和 MAC 发送 Wake-on-LAN magic packet；跨网段或 Tailnet 场景仍需要 LAN 内 relay。

## agents.json

配置格式:

```json
{
  "agents": {
    "claude": {
      "command": "npx",
      "args": ["--yes", "@zed-industries/claude-code-acp@latest"]
    }
  }
}
```

你可以按需替换命令或增加新的 agent。

## 行长限制

- 单条 WebSocket 文本消息超过 10MB 会被丢弃并打印 warning
- agent stdout 的单行输出超过 10MB 也会被丢弃并打印 warning

## 日志

- agent `stderr` 会直接输出到 bridge 的 `console.error`
- 启动、连接、退出事件会输出到标准日志
