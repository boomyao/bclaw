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

只依赖一个包: `ws`

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
