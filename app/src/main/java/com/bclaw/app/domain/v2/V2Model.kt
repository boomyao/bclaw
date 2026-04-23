package com.bclaw.app.domain.v2

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * v2 domain types — the persistent + runtime model for bclaw's tab-and-device world.
 *
 * Naming contract (mirrors SPEC_V2 §4):
 *   - DeviceId      — a paired bridge host
 *   - AgentId       — one agent CLI on a device (== `bridge/agents.json` key; also the path segment)
 *   - SessionId     — returned by ACP `session/new`; scope of session/update notifications
 *   - CwdPath       — absolute project path on the device (value class; never mixed with phone paths)
 *   - TabId         — local-only, generated when the user opens a new tab
 *
 * Everything in this file is SAFE to serialize (kotlinx.serialization). Runtime-only fields
 * (WebSocket handles, coroutine jobs) live outside — see `service/`'s runtime classes (Batch 1a TBD).
 */

// ── IDs (value classes, all cheap to pass around) ─────────────────────────

@JvmInline
@Serializable
value class DeviceId(val value: String) {
    companion object {
        fun generate(): DeviceId = DeviceId(UUID.randomUUID().toString())
    }
}

@JvmInline
@Serializable
value class AgentId(val value: String) {
    // agent id equals the `bridge/agents.json` key and the WebSocket path segment:
    //   "codex" / "claude" / "gemini" / future "kimi"
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class SessionId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class CwdPath(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class TabId(val value: String) {
    companion object {
        fun generate(): TabId = TabId(UUID.randomUUID().toString())
    }
}

// ── Agent descriptor ──────────────────────────────────────────────────────

/**
 * What the phone knows about an agent installed on a device.
 *
 * Populated from two sources:
 *   1. The pairing `bclaw2://` URL (authored by `bclaw-handoff` — includes `agent=<id>` params).
 *   2. The bridge's REST probe at `/agents` (via [com.bclaw.app.net.acp.AcpAgentDiscovery]).
 *
 * Capabilities are NOT stored here — they come from each agent's ACP `initialize` response
 * (per SPEC_V2 §2.1) and live in runtime state, not persistence.
 */
@Serializable
data class AgentDescriptor(
    val id: AgentId,
    val displayName: String,   // e.g. "Codex", "Claude Code", "Gemini"
)

/**
 * A device the user has paired with. One device = one Mac / Linux box running `bridge/server.js`.
 * One device at a time is "active"; switching device = app-level context swap (UX_V2 §2.6).
 */
@Serializable
data class Device(
    val id: DeviceId,
    val displayName: String,          // user-renameable, defaults to host's tailnet short name
    val wsBaseUrl: String,            // "ws://100.64.1.2:8766" — no trailing slash, no path
    val token: String,                // capability token (stored via EncryptedPrefs in DeviceBook repo)
    val knownAgents: List<AgentDescriptor> = emptyList(),
    /**
     * Projects declared at pairing time via `bclaw2://?cwd=...`. Agent-agnostic — shown as
     * available for every known agent until `knownProjectsByAgent` supersedes them per-agent.
     */
    val knownProjects: List<CwdPath> = emptyList(),
    /**
     * Per-agent project list synced from the bridge's `GET /projects?agent=X`. Source of truth:
     *   - codex:  `~/.codex/config.toml` `[projects."<cwd>"]`
     *   - claude: `~/.claude/projects/<slug>/<session-id>.jsonl` (cwd is read out of event records)
     *   - gemini: `~/.gemini/projects.json`
     *
     * Cleared only on explicit re-sync or remove-device. Union across agents (including
     * `knownProjects`) is what the project switcher displays; per-agent list answers
     * "which agents can open a new tab on this cwd?".
     */
    val knownProjectsByAgent: Map<AgentId, List<CwdPath>> = emptyMap(),
    /**
     * Currently-selected project (cwd) on this device. Drives Home filtering + `+ new session`.
     * `null` → user hasn't chosen yet; Home chip shows "select project" (or "no projects" when
     * the device has no known cwds at all). No implicit fallback — explicit selection is the
     * project-first navigation contract.
     */
    val activeProjectCwd: CwdPath? = null,
    val pairedAtEpochMs: Long,
) {
    /** Flat union of all project cwds this device knows about, across all agents. */
    val allKnownProjects: List<CwdPath>
        get() {
            val seen = LinkedHashSet<CwdPath>()
            seen.addAll(knownProjects)
            knownProjectsByAgent.values.forEach { seen.addAll(it) }
            return seen.toList()
        }

    /** Alias for [activeProjectCwd] — kept for call-site clarity. */
    val effectiveProjectCwd: CwdPath?
        get() = activeProjectCwd

    /** Agents that support opening a new session on [cwd]. */
    fun agentsFor(cwd: CwdPath): List<AgentDescriptor> {
        val supported = knownAgents.filter { descriptor ->
            val perAgent = knownProjectsByAgent[descriptor.id]
            when {
                perAgent == null -> cwd in knownProjects           // pre-sync fallback
                perAgent.isEmpty() -> cwd in knownProjects
                else -> cwd in perAgent
            }
        }
        // If nothing matched (e.g. fresh pair before first sync), fall back to all known agents
        // when the cwd is URL-declared — otherwise the UI would appear broken on first pair.
        return if (supported.isEmpty() && cwd in knownProjects) knownAgents else supported
    }
}

/**
 * The user's full paired-device book. Persisted in plain DataStore (tokens encrypted separately).
 */
@Serializable
data class DeviceBook(
    val devices: List<Device> = emptyList(),
    val activeDeviceId: DeviceId? = null,
) {
    val activeDevice: Device?
        get() = devices.firstOrNull { it.id == activeDeviceId }

    fun withActive(id: DeviceId): DeviceBook = copy(activeDeviceId = id)
}

// ── Tab state (persisted + runtime) ───────────────────────────────────────

/**
 * Persisted projection of a tab — survives app restart. Does not carry the live
 * ACP session runtime (socket, coroutine job, streaming buffers).
 *
 * One tab = one agent × one cwd × (optionally) one ACP session.
 *   - `sessionId == null` → empty tab (user opened new tab, hasn't sent first turn yet)
 *   - `sessionId != null` → live or resumable ACP session on the bound agent+cwd
 *
 * Home tab is NOT a [TabState]; it's always leftmost and not stored here (UX_V2 §1.1).
 */
@Serializable
data class TabState(
    val id: TabId,
    val agentId: AgentId,             // locked at creation (UX_V2 §0 principle 3)
    val projectCwd: CwdPath,          // locked at creation
    val sessionId: SessionId? = null,
    val sessionName: String? = null,  // agent-auto-named after first turn (ACP session_info_update)
    val unread: Boolean = false,
    val lastActivityEpochMs: Long,
    val forkedFromTabId: TabId? = null,  // provenance — set when opened from agent picker
)

/**
 * Per-device tab book. Persisted under DeviceId namespace so switching device swaps the set.
 *
 * `activeTabId == null` → Home tab is selected.
 */
@Serializable
data class TabBook(
    val deviceId: DeviceId,
    val tabs: List<TabState> = emptyList(),
    val activeTabId: TabId? = null,
)

// ── Session discovery (runtime only) ──────────────────────────────────────

/**
 * A historical session on the bridge, discovered via `GET /sessions?agent=X&cwd=Y`.
 *
 * Runtime-only — not persisted. The phone asks the bridge to enumerate sessions each time
 * the user opens a project, because the source of truth (codex rollouts / claude jsonl)
 * lives on the Mac, not here.
 *
 * "title" is best-effort:
 *   - codex provides `thread_name` (user-assigned or auto-generated)
 *   - claude: first user message, trimmed to ~120 chars
 *   - gemini: not supported (always empty list)
 */
@Serializable
data class SessionRef(
    val agentId: AgentId,
    val projectCwd: CwdPath,
    val sessionId: SessionId,
    val title: String? = null,
    val lastActivityEpochMs: Long? = null,
)

// ── URL parsing result ────────────────────────────────────────────────────

/**
 * Result of parsing a `bclaw2://` pairing URL. Returned by `BclawV2UrlParser`.
 *
 * Shape chosen so the Pair screen can render a specific inline error rather
 * than a generic "malformed".
 */
sealed class BclawV2UrlParseResult {
    data class Success(
        val wsBaseUrl: String,                     // "ws://host:port" (no trailing slash)
        val token: String,                         // raw capability token (caller stores via EncryptedPrefs)
        val agents: List<AgentDescriptor>,         // zero or more — zero means "discover after connect"
        val projects: List<CwdPath>,               // zero or more cwd paths
    ) : BclawV2UrlParseResult()

    sealed class Error(open val reason: String) : BclawV2UrlParseResult() {
        data object Blank : Error("enter a bclaw2://… url")
        data object Malformed : Error("not a valid url")
        data class WrongScheme(val scheme: String) : Error(
            "expected bclaw2://…, got $scheme://"
        )
        data object LegacyV1 : Error("bclaw1:// is from the v0 paste flow. re-run bclaw-handoff v2 on your mac.")
        data object MissingHost : Error("missing host")
        data object MissingPort : Error("missing port")
        data object MissingToken : Error("missing tok=… query parameter")
    }
}
