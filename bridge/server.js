#!/usr/bin/env node

const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");
const WebSocket = require("ws");
const { WebSocketServer } = WebSocket;

const DEFAULT_PORT = 8766;
const HOST = "0.0.0.0";
const MAX_LINE_BYTES = 10 * 1024 * 1024;
const CONFIG_PATH = path.join(__dirname, "agents.json");
const CODEX_CONFIG_PATH = path.join(os.homedir(), ".codex", "config.toml");
const CODEX_SESSIONS_DIR = path.join(os.homedir(), ".codex", "sessions");
const CLAUDE_PROJECTS_DIR = path.join(os.homedir(), ".claude", "projects");
const GEMINI_PROJECTS_PATH = path.join(os.homedir(), ".gemini", "projects.json");
const SESSIONS_LIMIT = 30;
const CODEX_SCAN_FILE_BUDGET = 400; // most-recent rollout files to peek at per /sessions call

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

function readCodexProjects() {
  let raw;
  try {
    raw = fs.readFileSync(CODEX_CONFIG_PATH, "utf8");
  } catch (error) {
    if (error.code === "ENOENT") {
      return [];
    }
    console.warn(`Failed to read ${CODEX_CONFIG_PATH}: ${error.message}`);
    return [];
  }

  const cwds = [];
  const seen = new Set();
  // Match `[projects."<path>"]` headers. Path is between the first `"` and the last `"` before `]`.
  const headerRegex = /^\[projects\."([^\r\n]+?)"\]\s*$/gm;
  let match;
  while ((match = headerRegex.exec(raw)) !== null) {
    const cwd = match[1];
    if (!seen.has(cwd)) {
      seen.add(cwd);
      cwds.push(cwd);
    }
  }
  return cwds;
}

function safeReadJsonLinePrefix(filePath, maxBytes = 64 * 1024) {
  try {
    const fd = fs.openSync(filePath, "r");
    try {
      const buf = Buffer.alloc(maxBytes);
      const read = fs.readSync(fd, buf, 0, maxBytes, 0);
      return buf.slice(0, read).toString("utf8");
    } finally {
      fs.closeSync(fd);
    }
  } catch {
    return "";
  }
}

function readClaudeProjects() {
  if (!fs.existsSync(CLAUDE_PROJECTS_DIR)) return [];
  const entries = fs.readdirSync(CLAUDE_PROJECTS_DIR, { withFileTypes: true });
  const cwdSet = new Set();
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const dirPath = path.join(CLAUDE_PROJECTS_DIR, entry.name);
    let files;
    try {
      files = fs.readdirSync(dirPath).filter((f) => f.endsWith(".jsonl"));
    } catch {
      continue;
    }
    // Peek the most recent jsonl — cwd is written in every event, so first event wins.
    files.sort();
    const recent = files.slice(-3);
    let cwd = null;
    for (const f of recent) {
      const prefix = safeReadJsonLinePrefix(path.join(dirPath, f));
      const match = prefix.match(/"cwd":"([^"]+)"/);
      if (match) {
        cwd = match[1];
        break;
      }
    }
    if (cwd) cwdSet.add(cwd);
  }
  return [...cwdSet];
}

function readGeminiProjects() {
  if (!fs.existsSync(GEMINI_PROJECTS_PATH)) return [];
  try {
    const raw = fs.readFileSync(GEMINI_PROJECTS_PATH, "utf8");
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed.projects !== "object") return [];
    return Object.keys(parsed.projects);
  } catch (error) {
    console.warn(`Failed to read ${GEMINI_PROJECTS_PATH}: ${error.message}`);
    return [];
  }
}

function readProjectsFor(agent) {
  switch (agent) {
    case "codex":
      return readCodexProjects();
    case "claude":
    case "claude-code":
      return readClaudeProjects();
    case "gemini":
      return readGeminiProjects();
    default:
      return [];
  }
}

function listCodexRolloutFilesMostRecent(limit) {
  if (!fs.existsSync(CODEX_SESSIONS_DIR)) return [];
  const files = [];
  function walk(dir) {
    if (files.length >= limit * 4) return;
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    // Sort descending so most recent year/month/day is visited first.
    entries.sort((a, b) => (a.name < b.name ? 1 : -1));
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile() && entry.name.startsWith("rollout-") && entry.name.endsWith(".jsonl")) {
        files.push(full);
      }
      if (files.length >= limit * 4) break;
    }
  }
  walk(CODEX_SESSIONS_DIR);
  // Rollout filenames embed the timestamp → string sort descending == recency descending.
  files.sort((a, b) => (path.basename(a) < path.basename(b) ? 1 : -1));
  return files;
}

function readCodexSessions(cwd) {
  // First line of a codex rollout embeds the full system prompt under
  // `payload.base_instructions.text` — commonly 15–19 KB, occasionally larger.
  // We read 48 KB: that covers session_meta + several event lines, enough to
  // find the first `user_message` event_msg whose `message` is the title we want.
  // (Codex does not persist `thread_name` to the rollout file — the live
  // ThreadList RPC has it, but that requires an active ACP session.)
  const files = listCodexRolloutFilesMostRecent(CODEX_SCAN_FILE_BUDGET);
  const hits = [];
  const cwdNeedle = `"cwd":${JSON.stringify(cwd)}`;
  for (const file of files) {
    if (hits.length >= SESSIONS_LIMIT) break;
    const prefix = safeReadJsonLinePrefix(file, 128 * 1024);
    if (!prefix.startsWith('{"timestamp":"')) continue;
    if (!prefix.includes('"type":"session_meta"')) continue;
    if (!prefix.includes(cwdNeedle)) continue;
    // Only skip when forked_from_id has a string value; `null` means not forked.
    if (prefix.includes('"forked_from_id":"')) continue;
    const idMatch = prefix.match(/"payload":\s*\{\s*"id":"([^"]+)"/);
    if (!idMatch) continue;
    const timestampMatch = prefix.match(/^\{"timestamp":"([^"]+)"/);
    // First real user turn: `event_msg` payload with `type:"user_message"` and `message:"..."`.
    // Response_items with role:user can be synthetic (AGENTS.md etc.) so we filter via event_msg.
    const userMsgMatch = prefix.match(
      /"type":"event_msg","payload":\{"type":"user_message","message":"((?:\\.|[^"\\])*)"/,
    );
    let title = null;
    if (userMsgMatch) {
      title = userMsgMatch[1]
        .replace(/\\"/g, '"')
        .replace(/\\n/g, " ")
        .replace(/\\\\/g, "\\")
        .slice(0, 120);
    }
    hits.push({
      id: idMatch[1],
      title,
      lastActivityEpochMs: timestampMatch ? Date.parse(timestampMatch[1]) : null,
    });
  }
  return hits;
}

function readClaudeSessions(cwd) {
  if (!fs.existsSync(CLAUDE_PROJECTS_DIR)) return [];
  const entries = fs.readdirSync(CLAUDE_PROJECTS_DIR, { withFileTypes: true });
  const hits = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const dirPath = path.join(CLAUDE_PROJECTS_DIR, entry.name);
    let files;
    try {
      files = fs.readdirSync(dirPath).filter((f) => f.endsWith(".jsonl"));
    } catch {
      continue;
    }
    // Sort by filename descending (uuid so no recency signal) — fall back to mtime.
    files.sort((a, b) => {
      try {
        const aM = fs.statSync(path.join(dirPath, a)).mtimeMs;
        const bM = fs.statSync(path.join(dirPath, b)).mtimeMs;
        return bM - aM;
      } catch {
        return 0;
      }
    });
    for (const file of files) {
      if (hits.length >= SESSIONS_LIMIT) break;
      const full = path.join(dirPath, file);
      const prefix = safeReadJsonLinePrefix(full, 16 * 1024);
      const cwdMatch = prefix.match(/"cwd":"([^"]+)"/);
      if (!cwdMatch || cwdMatch[1] !== cwd) continue;
      const id = file.replace(/\.jsonl$/, "");
      // Pick first "content" field as preview — queue-operation enqueue usually carries the first user msg.
      const previewMatch = prefix.match(/"content":"((?:\\"|[^"])*?)"/);
      let preview = null;
      if (previewMatch) {
        preview = previewMatch[1].replace(/\\"/g, '"').replace(/\\n/g, " ").slice(0, 120);
      }
      let lastActivityEpochMs = null;
      try {
        lastActivityEpochMs = fs.statSync(full).mtimeMs;
      } catch {}
      hits.push({ id, title: preview, lastActivityEpochMs });
    }
    if (hits.length >= SESSIONS_LIMIT) break;
  }
  return hits;
}

function readSessionsFor(agent, cwd) {
  if (!cwd) return [];
  switch (agent) {
    case "codex":
      return readCodexSessions(cwd);
    case "claude":
    case "claude-code":
      return readClaudeSessions(cwd);
    case "gemini":
      return []; // no discoverable session store yet
    default:
      return [];
  }
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

  if (url.pathname === "/projects") {
    const agent = url.searchParams.get("agent") || "codex";
    const projects = readProjectsFor(agent).map((cwd) => ({ cwd }));
    response.writeHead(200, {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
    });
    response.end(JSON.stringify({ agent, projects }));
    return;
  }

  if (url.pathname === "/sessions") {
    const agent = url.searchParams.get("agent") || "";
    const cwd = url.searchParams.get("cwd") || "";
    const sessions = readSessionsFor(agent, cwd);
    response.writeHead(200, {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
    });
    response.end(JSON.stringify({ agent, cwd, sessions }));
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
