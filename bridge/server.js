#!/usr/bin/env node

const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");
const { spawn } = require("node:child_process");
const WebSocket = require("ws");
const { WebSocketServer } = WebSocket;

const DEFAULT_PORT = 8766;
const HOST = "0.0.0.0";
const MAX_LINE_BYTES = 10 * 1024 * 1024;
const CONFIG_PATH = path.join(__dirname, "agents.json");

function loadAgentsConfig() {
  let rawConfig;
  try {
    rawConfig = fs.readFileSync(CONFIG_PATH, "utf8");
  } catch (error) {
    throw new Error(`Failed to read ${CONFIG_PATH}: ${error.message}`);
  }

  let parsedConfig;
  try {
    parsedConfig = JSON.parse(rawConfig);
  } catch (error) {
    throw new Error(`Failed to parse ${CONFIG_PATH}: ${error.message}`);
  }

  if (!parsedConfig || typeof parsedConfig !== "object" || typeof parsedConfig.agents !== "object") {
    throw new Error(`${CONFIG_PATH} must contain an "agents" object`);
  }

  for (const [agentName, config] of Object.entries(parsedConfig.agents)) {
    if (!config || typeof config !== "object") {
      throw new Error(`Agent "${agentName}" config must be an object`);
    }

    if (typeof config.command !== "string" || config.command.length === 0) {
      throw new Error(`Agent "${agentName}" must define a non-empty "command" string`);
    }

    if (!Array.isArray(config.args) || !config.args.every((value) => typeof value === "string")) {
      throw new Error(`Agent "${agentName}" must define "args" as an array of strings`);
    }
  }

  return parsedConfig.agents;
}

function parseAgentName(requestUrl) {
  if (!requestUrl) {
    return null;
  }

  let parsedUrl;
  try {
    parsedUrl = new URL(requestUrl, "ws://localhost");
  } catch {
    return null;
  }

  const segments = parsedUrl.pathname.split("/").filter(Boolean);
  if (segments.length !== 1) {
    return null;
  }

  return segments[0];
}

function sendUpgradeError(socket, statusCode, message) {
  socket.write(
    `HTTP/1.1 ${statusCode} ${http.STATUS_CODES[statusCode] || "Error"}\r\n` +
      "Connection: close\r\n" +
      "Content-Type: text/plain; charset=utf-8\r\n" +
      `Content-Length: ${Buffer.byteLength(message)}\r\n` +
      "\r\n" +
      message,
  );
  socket.destroy();
}

function terminateChild(child, signal) {
  if (!child || child.exitCode !== null || child.signalCode !== null || child.killed) {
    return;
  }

  try {
    child.kill(signal);
  } catch (error) {
    console.error("Failed to terminate agent process:", error);
  }
}

function closeWebSocketIfOpen(ws, code, reason) {
  if (ws.readyState === ws.CLOSING || ws.readyState === ws.CLOSED) {
    return;
  }

  ws.close(code, reason);
}

function lineBufferForwarder(onLine, sourceLabel) {
  let buffered = "";
  let discardingOversizedLine = false;

  return (chunk) => {
    buffered += chunk.toString("utf8");

    while (buffered.length > 0) {
      const newlineIndex = buffered.indexOf("\n");

      if (newlineIndex === -1) {
        if (!discardingOversizedLine && Buffer.byteLength(buffered, "utf8") > MAX_LINE_BYTES) {
          discardingOversizedLine = true;
          console.warn(`${sourceLabel}: dropping line larger than 10MB`);
        }
        return;
      }

      const rawLine = buffered.slice(0, newlineIndex);
      buffered = buffered.slice(newlineIndex + 1);
      const line = rawLine.endsWith("\r") ? rawLine.slice(0, -1) : rawLine;

      if (discardingOversizedLine) {
        discardingOversizedLine = false;
        continue;
      }

      if (Buffer.byteLength(line, "utf8") > MAX_LINE_BYTES) {
        console.warn(`${sourceLabel}: dropping line larger than 10MB`);
        continue;
      }

      onLine(line);
    }
  };
}

const agents = loadAgentsConfig();
const port = Number.parseInt(process.env.PORT || `${DEFAULT_PORT}`, 10);

if (!Number.isInteger(port) || port <= 0 || port > 65535) {
  throw new Error(`Invalid PORT value: ${process.env.PORT}`);
}

const server = http.createServer((request, response) => {
  const url = new URL(request.url || "/", "http://localhost");

  if (url.pathname === "/agents") {
    const agentList = Object.entries(agents).map(([name, config]) => ({
      name,
      command: config.command,
      displayName: config.displayName || name,
    }));
    response.writeHead(200, {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
    });
    response.end(JSON.stringify({ agents: agentList }));
    return;
  }

  response.writeHead(200, { "content-type": "text/plain; charset=utf-8" });
  response.end("ACP bridge is running.\n");
});

const wss = new WebSocketServer({ noServer: true });

wss.on("connection", (ws, request, agentName) => {
  const agentConfig = agents[agentName];
  const childEnv = { ...process.env };
  // Remove vars that prevent nested agent launches
  delete childEnv.CLAUDECODE;
  delete childEnv.CLAUDE_CODE;
  const child = spawn(agentConfig.command, agentConfig.args, {
    stdio: ["pipe", "pipe", "pipe"],
    env: childEnv,
  });

  const clientAddress = request.socket.remoteAddress || "unknown";
  let childClosed = false;

  console.log(`Accepted ${clientAddress} -> ${agentName}`);

  child.on("spawn", () => {
    console.log(`Spawned ${agentName}: ${agentConfig.command} ${agentConfig.args.join(" ")}`);
  });

  child.on("error", (error) => {
    console.error(`Failed to start agent "${agentName}":`, error);
    closeWebSocketIfOpen(ws, 1011, "Agent failed to start");
  });

  child.stdout.on(
    "data",
    lineBufferForwarder((line) => {
      if (ws.readyState !== WebSocket.OPEN) {
        return;
      }
      ws.send(line);
    }, `${agentName} stdout`),
  );

  child.stderr.on("data", (chunk) => {
    const text = chunk.toString("utf8").replace(/\s+$/, "");
    if (text.length > 0) {
      console.error(`[${agentName} stderr] ${text}`);
    }
  });

  ws.on("message", (data, isBinary) => {
    if (isBinary) {
      console.warn(`${agentName}: dropping binary WebSocket frame`);
      return;
    }

    const message = data.toString("utf8");
    const normalized = message.replace(/(?:\r?\n)+$/, "");

    if (/[\r\n]/.test(normalized)) {
      console.warn(`${agentName}: dropping WebSocket frame containing embedded newlines`);
      return;
    }

    if (Buffer.byteLength(normalized, "utf8") > MAX_LINE_BYTES) {
      console.warn(`${agentName}: dropping WebSocket message larger than 10MB`);
      return;
    }

    if (child.stdin.destroyed || !child.stdin.writable) {
      console.warn(`${agentName}: child stdin is not writable; dropping message`);
      return;
    }

    child.stdin.write(`${normalized}\n`);
  });

  ws.on("close", () => {
    console.log(`WebSocket closed for ${agentName}`);
    terminateChild(child, "SIGTERM");
    setTimeout(() => terminateChild(child, "SIGKILL"), 5000).unref();
  });

  ws.on("error", (error) => {
    console.error(`WebSocket error for ${agentName}:`, error);
  });

  child.on("close", (code, signal) => {
    childClosed = true;
    console.log(`Agent ${agentName} exited with code=${code} signal=${signal}`);
    closeWebSocketIfOpen(ws, 1011, "Agent exited");
  });

  child.stdin.on("error", (error) => {
    if (!childClosed && error.code !== "EPIPE") {
      console.error(`${agentName} stdin error:`, error);
    }
  });
});

server.on("upgrade", (request, socket, head) => {
  const agentName = parseAgentName(request.url);

  if (!agentName || !Object.prototype.hasOwnProperty.call(agents, agentName)) {
    sendUpgradeError(socket, 404, "Unknown agent\n");
    return;
  }

  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit("connection", ws, request, agentName);
  });
});

server.listen(port, HOST, () => {
  console.log(`ACP bridge listening on ws://${HOST}:${port}`);
  console.log(`Available agents: ${Object.keys(agents).join(", ")}`);
});
