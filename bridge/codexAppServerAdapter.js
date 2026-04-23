// Translates between the ACP protocol that the Android client speaks and the
// codex app-server protocol that the official `codex app-server --listen stdio://`
// binary speaks. Used only for the `codex` agent; claude/gemini continue through
// their native ACP agents unchanged.
//
// Mapping table (minimal working set):
//
//   ACP                           ↔  app-server
//   ────────────────────────────────────────────────────────────
//   initialize                     →  initialize (reshape clientInfo + fake an ACP result)
//   session/new                    →  thread/start
//   session/load                   →  thread/resume
//   session/prompt                 →  turn/start
//   session/cancel (notification)  →  turn/interrupt (as request, response discarded)
//
//   item/started userMessage       →  session/update user_message_chunk
//   item/agentMessage/delta        →  session/update agent_message_chunk
//   item/reasoning* delta          →  session/update agent_thought_chunk
//   item/started commandExecution  →  session/update tool_call (kind=execute)
//   item/completed commandExecution →  session/update tool_call_update
//   item/started fileChange        →  session/update tool_call (kind=edit)
//   item/completed fileChange      →  session/update tool_call_update
//   item/started imageView         →  session/update tool_call (title="view_image")
//   item/completed imageView       →  session/update tool_call_update with image data
//                                      (bridge reads the file bytes and base64-encodes)
//   turn/completed                 →  RPC response to the pending session/prompt
//
// Dropped (noise the Android side doesn't care about):
//   mcpServer/startupStatus, thread/started, thread/status/changed,
//   thread/tokenUsage/updated, account/rateLimits/updated, …

const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const HISTORY_JSONL = path.join(os.homedir(), ".codex", "history.jsonl");

function createAdapter() {
  // Per-connection state.
  // Map threadId → acpRequestId of the session/prompt awaiting turn/completed.
  const pendingPromptByThread = new Map();
  // Outstanding app-server requests we sent, keyed by request id. Lets us recognize
  // which original call a response belongs to — app-server responses don't echo the
  // method so we need to remember. Entry shape:
  //   { method, acpId?: number (only when forwarded verbatim from client),
  //     acpMethod?: string (original client method when bridge-synthesized),
  //     threadId?: string }
  const outstanding = new Map();
  // When session/load triggers a thread/resume + thread/turns/list sequence, we park
  // the ACP id here until both finish. Keyed by threadId because the first response
  // (thread/resume) is what tells us the id is valid.
  const pendingLoad = new Map(); // threadId → acpLoadId
  let nextBridgeId = 1_000_000_000;

  function translateFromClient(raw) {
    // Parse ACP JSON-RPC message, return an array of { type:"child"|"ws", payload }
    let msg;
    try { msg = JSON.parse(raw); } catch { return []; }

    if (msg.method === "initialize") {
      const info = msg.params?.clientInfo || {};
      outstanding.set(msg.id, { method: "initialize", acpId: msg.id });
      return [{ type: "child", payload: JSON.stringify({
        jsonrpc: "2.0",
        id: msg.id,
        method: "initialize",
        params: {
          capabilities: {},
          clientInfo: {
            name: info.name || "bclaw",
            version: info.version || "0",
            title: info.name || "bclaw",
          },
        },
      })}];
    }

    if (msg.method === "session/new") {
      outstanding.set(msg.id, { method: "thread/start", acpId: msg.id });
      return [{ type: "child", payload: JSON.stringify({
        jsonrpc: "2.0",
        id: msg.id,
        method: "thread/start",
        params: { cwd: msg.params?.cwd },
      })}];
    }

    if (msg.method === "session/load") {
      // Two-step flow: thread/resume registers the thread with the server, then
      // thread/turns/list pulls historical items which we replay as session/update
      // notifications. The ACP session/load response is delayed until both finish.
      const threadId = msg.params?.sessionId;
      pendingLoad.set(threadId, msg.id);
      outstanding.set(msg.id, { method: "thread/resume", acpId: msg.id, threadId });
      return [{ type: "child", payload: JSON.stringify({
        jsonrpc: "2.0",
        id: msg.id,
        method: "thread/resume",
        params: { threadId },
      })}];
    }

    if (msg.method === "session/prompt") {
      const sessionId = msg.params?.sessionId;
      pendingPromptByThread.set(sessionId, msg.id);
      outstanding.set(msg.id, { method: "turn/start", acpId: msg.id, threadId: sessionId });
      const input = (msg.params?.prompt || []).map(translateContentBlock).filter(Boolean);
      // `codex app-server` skips the codex CLI's history.jsonl append (that path only runs
      // in the CLI's TUI entrypoint). Without the entry, `codex resume`'s picker won't list
      // our sessions. Write the first text chunk ourselves so the session shows up there
      // with the same shape CLI produces: {session_id, ts, text}.
      const firstText = input.find((b) => b?.type === "text")?.text;
      if (sessionId && firstText) {
        appendHistoryEntry(sessionId, firstText);
      }
      return [{ type: "child", payload: JSON.stringify({
        jsonrpc: "2.0",
        id: msg.id,
        method: "turn/start",
        params: { threadId: sessionId, input },
      })}];
    }

    if (msg.method === "session/cancel") {
      const sessionId = msg.params?.sessionId;
      // turn/interrupt is a request on the server side but we don't care about its reply.
      return [{ type: "child", payload: JSON.stringify({
        jsonrpc: "2.0",
        id: nextBridgeId++,
        method: "turn/interrupt",
        params: { threadId: sessionId },
      })}];
    }

    // Unknown method — forward verbatim. Server will error if unsupported.
    return [{ type: "child", payload: raw }];
  }

  function translateContentBlock(block) {
    if (!block || typeof block !== "object") return null;
    if (block.type === "text") {
      return { type: "text", text: block.text ?? "" };
    }
    if (block.type === "image") {
      const mime = block.mimeType || "image/jpeg";
      const data = block.data || "";
      return { type: "image", url: `data:${mime};base64,${data}` };
    }
    if (block.type === "resource") {
      // app-server has no "resource" UserInput type; inline as text with a path
      // breadcrumb so the agent knows what it's reading.
      const inner = block.resource || {};
      const header = inner.uri ? `// ${inner.uri}\n` : "";
      return { type: "text", text: header + (inner.text || "") };
    }
    return null;
  }

  function translateFromChild(raw) {
    let msg;
    try { msg = JSON.parse(raw); } catch { return []; }

    // --- RPC responses --------------------------------------------------
    if (msg.id != null && (msg.result !== undefined || msg.error !== undefined)) {
      const pending = outstanding.get(msg.id);
      outstanding.delete(msg.id);

      if (msg.error) {
        // If this was an ACP-originated call, surface the error; otherwise drop.
        if (pending && pending.acpId != null) {
          return [{ type: "ws", payload: raw }];
        }
        return [];
      }

      const r = msg.result;
      const method = pending?.method;

      if (method === "initialize") {
        return [{ type: "ws", payload: JSON.stringify({
          jsonrpc: "2.0",
          id: msg.id,
          result: {
            protocolVersion: 1,
            agentCapabilities: {
              loadSession: true,
              promptCapabilities: { image: true, audio: false, embeddedContext: true },
            },
            agentInfo: { name: "codex", title: "Codex", version: r?.userAgent || "app-server" },
            authMethods: [],
          },
        })}];
      }

      if (method === "thread/start") {
        return [{ type: "ws", payload: JSON.stringify({
          jsonrpc: "2.0",
          id: msg.id,
          result: { sessionId: r?.thread?.id },
        })}];
      }

      if (method === "thread/resume") {
        // Don't respond to Android yet — we still need to replay history via
        // thread/turns/list. Fire that now and wait for its response.
        const threadId = pending.threadId || r?.thread?.id;
        const turnsId = nextBridgeId++;
        outstanding.set(turnsId, { method: "thread/turns/list", threadId, acpLoadId: pending.acpId });
        return [{ type: "child", payload: JSON.stringify({
          jsonrpc: "2.0",
          id: turnsId,
          method: "thread/turns/list",
          params: { threadId, sortDirection: "asc", limit: 500 },
        })}];
      }

      if (method === "thread/turns/list") {
        const threadId = pending.threadId;
        const acpLoadId = pending.acpLoadId;
        pendingLoad.delete(threadId);
        const outs = [];
        const turns = r?.data || [];
        for (const turn of turns) {
          for (const item of turn.items || []) {
            const frames = replayItemAsUpdates(threadId, item);
            for (const f of frames) outs.push(f);
          }
        }
        // Finally acknowledge the ACP session/load.
        outs.push({ type: "ws", payload: JSON.stringify({
          jsonrpc: "2.0",
          id: acpLoadId,
          result: { sessionId: threadId },
        })});
        return outs;
      }

      if (method === "turn/start") {
        // Don't reply — wait for turn/completed notification.
        return [];
      }

      // Unknown / bridge-synthesized (turn/interrupt) — drop.
      return [];
    }

    // --- Notifications --------------------------------------------------
    if (msg.method === "item/started") {
      return translateItemStarted(msg.params);
    }
    if (msg.method === "item/completed") {
      return translateItemCompleted(msg.params);
    }
    if (msg.method === "item/agentMessage/delta") {
      return [updateNotif(msg.params.threadId, {
        sessionUpdate: "agent_message_chunk",
        content: { type: "text", text: msg.params.delta || "" },
      })];
    }
    if (
      msg.method === "item/reasoningText/delta" ||
      msg.method === "item/reasoningSummaryText/delta"
    ) {
      return [updateNotif(msg.params.threadId, {
        sessionUpdate: "agent_thought_chunk",
        content: { type: "text", text: msg.params.delta || "" },
      })];
    }
    if (msg.method === "turn/completed") {
      const threadId = msg.params?.threadId;
      const turn = msg.params?.turn || {};
      const acpId = pendingPromptByThread.get(threadId);
      if (acpId == null) return [];
      pendingPromptByThread.delete(threadId);
      const stopReason =
        turn.status === "completed" ? "end_turn" :
        turn.status === "cancelled" ? "cancelled" :
        turn.status === "failed" ? "error" :
        "end_turn";
      return [{ type: "ws", payload: JSON.stringify({
        jsonrpc: "2.0",
        id: acpId,
        result: { stopReason },
      })}];
    }

    // Known noise we silently drop.
    const DROP = new Set([
      "mcpServer/startupStatus/updated",
      "thread/started",
      "thread/status/changed",
      "thread/tokenUsage/updated",
      "thread/nameUpdated",
      "account/rateLimits/updated",
      "account/updated",
      "account/loginCompleted",
      "fsChanged",
      "modelRerouted",
      "skillsChanged",
      "appListUpdated",
      "mcpServerStatusUpdated",
    ]);
    if (msg.method && DROP.has(msg.method)) return [];

    // Anything else (unknown kind) → drop rather than confuse the reducer.
    return [];
  }

  /**
   * Synthesize `session/update` notifications from a historical ThreadItem so the
   * Android reducer can replay the past turn. For streaming-shaped items (agent
   * message, reasoning) we emit one chunk carrying the full text — the reducer
   * builds a message/Reasoning TimelineItem from that. For tool-shaped items we
   * emit tool_call + tool_call_update as a pair so `status` lands correctly.
   */
  function replayItemAsUpdates(threadId, item) {
    const frames = [];
    if (!item || !item.type) return frames;

    if (item.type === "userMessage") {
      const first = (item.content || [])[0];
      if (first?.type === "text" && typeof first.text === "string") {
        frames.push(updateNotif(threadId, {
          sessionUpdate: "user_message_chunk",
          content: { type: "text", text: first.text },
        }));
      }
      return frames;
    }
    if (item.type === "agentMessage") {
      if (item.text) {
        frames.push(updateNotif(threadId, {
          sessionUpdate: "agent_message_chunk",
          content: { type: "text", text: item.text },
        }));
      }
      return frames;
    }
    if (item.type === "reasoning") {
      const summary = (item.summary || []).map((s) => s.text).filter(Boolean).join("\n");
      const body = (item.content || []).map((c) => c.text).filter(Boolean).join("\n");
      const text = [summary, body].filter(Boolean).join("\n\n");
      if (text) {
        frames.push(updateNotif(threadId, {
          sessionUpdate: "agent_thought_chunk",
          content: { type: "text", text },
        }));
      }
      return frames;
    }
    if (item.type === "commandExecution") {
      frames.push(updateNotif(threadId, {
        sessionUpdate: "tool_call",
        toolCallId: item.id,
        title: item.command || "exec",
        kind: "execute",
        status: mapStatus(item.status || "completed"),
        rawInput: { cmd: item.command || "", workdir: item.cwd || "" },
      }));
      frames.push(updateNotif(threadId, {
        sessionUpdate: "tool_call_update",
        toolCallId: item.id,
        status: mapStatus(item.status || "completed"),
        rawOutput: item.aggregatedOutput || "",
      }));
      return frames;
    }
    if (item.type === "fileChange") {
      const paths = (item.changes || []).map((c) => c.path).filter(Boolean);
      frames.push(updateNotif(threadId, {
        sessionUpdate: "tool_call",
        toolCallId: item.id,
        title: paths.length === 1 ? paths[0] : `edit ${paths.length} files`,
        kind: "edit",
        status: mapStatus(item.status || "completed"),
        locations: paths.map((p) => ({ path: p })),
      }));
      frames.push(updateNotif(threadId, {
        sessionUpdate: "tool_call_update",
        toolCallId: item.id,
        status: mapStatus(item.status || "completed"),
      }));
      return frames;
    }
    if (item.type === "imageView" || item.type === "imageGeneration") {
      // Replay carries only the path — renderer hits BridgeFsClient for the bytes.
      const sourcePath = item.type === "imageView" ? item.path : item.savedPath;
      frames.push(updateNotif(threadId, {
        sessionUpdate: "tool_call",
        toolCallId: item.id,
        title: "view_image",
        kind: "fetch",
        status: mapStatus(item.status || "completed"),
        rawInput: { path: sourcePath || "" },
      }));
      frames.push(updateNotif(threadId, {
        sessionUpdate: "tool_call_update",
        toolCallId: item.id,
        status: mapStatus(item.status || "completed"),
      }));
      return frames;
    }
    // Unhandled types (plan, mcpToolCall, etc.) — drop silently.
    return frames;
  }

  function translateItemStarted(params) {
    const item = params?.item;
    if (!item) return [];
    const threadId = params.threadId;

    if (item.type === "userMessage") {
      // codex echoes every user message as an item. Translate the first text chunk.
      const first = (item.content || [])[0];
      if (first?.type === "text" && typeof first.text === "string") {
        return [updateNotif(threadId, {
          sessionUpdate: "user_message_chunk",
          content: { type: "text", text: first.text },
        })];
      }
      return [];
    }

    if (item.type === "commandExecution") {
      return [updateNotif(threadId, {
        sessionUpdate: "tool_call",
        toolCallId: item.id,
        title: item.command || "exec",
        kind: "execute",
        status: mapStatus(item.status),
        rawInput: { cmd: item.command || "", workdir: item.cwd || "" },
      })];
    }

    if (item.type === "fileChange") {
      const paths = (item.changes || []).map((c) => c.path).filter(Boolean);
      return [updateNotif(threadId, {
        sessionUpdate: "tool_call",
        toolCallId: item.id,
        title: paths.length === 1 ? paths[0] : `edit ${paths.length} files`,
        kind: "edit",
        status: mapStatus(item.status),
        locations: paths.map((p) => ({ path: p })),
      })];
    }

    if (item.type === "imageView") {
      return [updateNotif(threadId, {
        sessionUpdate: "tool_call",
        toolCallId: item.id,
        title: "view_image",
        kind: "fetch",
        status: mapStatus(item.status),
        rawInput: { path: item.path },
      })];
    }

    if (item.type === "imageGeneration") {
      // Present as a view_image card — the Android renderer already handles that shape.
      // path isn't known until completion (savedPath lands only when status="completed"),
      // so leave rawInput.path empty on start.
      return [updateNotif(threadId, {
        sessionUpdate: "tool_call",
        toolCallId: item.id,
        title: "view_image",
        kind: "fetch",
        status: mapStatus(item.status),
        rawInput: { path: item.savedPath || "" },
      })];
    }

    // agentMessage / reasoning / other — wait for their delta/completed frames.
    return [];
  }

  function translateItemCompleted(params) {
    const item = params?.item;
    if (!item) return [];
    const threadId = params.threadId;

    if (item.type === "commandExecution") {
      return [updateNotif(threadId, {
        sessionUpdate: "tool_call_update",
        toolCallId: item.id,
        status: mapStatus(item.status || "completed"),
        rawOutput: item.aggregatedOutput || "",
      })];
    }

    if (item.type === "fileChange") {
      return [updateNotif(threadId, {
        sessionUpdate: "tool_call_update",
        toolCallId: item.id,
        status: mapStatus(item.status || "completed"),
      })];
    }

    if (item.type === "imageView" || item.type === "imageGeneration") {
      // Don't inline the image bytes here — a 3 MB PNG base64-encodes to ~4 MB and
      // flowing that through session/update for every history replay bloats the
      // Android timeline cache (each item persists to DataStore). Android renders
      // the thumbnail by fetching the path via BridgeFsClient on demand; the agent
      // writes images to disk under sandboxed Codex dirs that the bridge can serve.
      const sourcePath = item.type === "imageView" ? item.path : item.savedPath;
      const update = {
        sessionUpdate: "tool_call_update",
        toolCallId: item.id,
        status: mapStatus(item.status || "completed"),
      };
      // For live imageGeneration, savedPath lands only on completion; ship it in the
      // update so the Android reducer can fill in the AgentImages.sourcePath the
      // renderer needs to fetch.
      if (sourcePath) update.rawInput = { path: sourcePath };
      return [updateNotif(threadId, update)];
    }

    return [];
  }

  function updateNotif(threadId, update) {
    return {
      type: "ws",
      payload: JSON.stringify({
        jsonrpc: "2.0",
        method: "session/update",
        params: { sessionId: threadId, update },
      }),
    };
  }

  function mapStatus(s) {
    switch (s) {
      case "inProgress":
      case "in_progress":
      case "running": return "running";
      case "pending": return "pending";
      case "completed":
      case "success": return "completed";
      case "failed":
      case "error": return "failed";
      case "cancelled":
      case "canceled": return "cancelled";
      default: return "pending";
    }
  }

  function appendHistoryEntry(sessionId, text) {
    try {
      const entry = JSON.stringify({
        session_id: sessionId,
        ts: Math.floor(Date.now() / 1000),
        text,
      });
      fs.appendFileSync(HISTORY_JSONL, entry + "\n");
    } catch (e) {
      // Best-effort; don't let history write block the turn.
      console.warn("[codex-adapter] history.jsonl append failed:", e.message);
    }
  }

  function readImageAsDataUrl(filePath) {
    try {
      const stat = fs.statSync(filePath);
      if (!stat.isFile() || stat.size > IMAGE_MAX_BYTES) return null;
      const buf = fs.readFileSync(filePath);
      const ext = path.extname(filePath).toLowerCase();
      const mime = ({
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".gif": "image/gif",
        ".webp": "image/webp",
        ".bmp": "image/bmp",
        ".heic": "image/heic",
      })[ext] || "image/png";
      return `data:${mime};base64,${buf.toString("base64")}`;
    } catch {
      return null;
    }
  }

  return { translateFromClient, translateFromChild };
}

module.exports = { createAdapter };
