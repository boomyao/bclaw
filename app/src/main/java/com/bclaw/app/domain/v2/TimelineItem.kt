package com.bclaw.app.domain.v2

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UI-layer projection of ACP `session/update` payloads — the atoms the message list renders.
 *
 * v2.0 ships 5 core types + an unknown fallback (SPEC_V2 §4.3). The remaining 7 types from
 * UX_V2 §3 (image, table, web search, MCP tool call, approval, plan, etc.) are deferred to
 * Batch 4 and arrive as [Unsupported] rows in the meantime — never crash on new kinds.
 *
 * All items are immutable; streaming updates (agent message chunks, tool call output deltas)
 * produce a new instance via `copy()`. Equality is identity-via-[id], not structural, so the
 * LazyColumn can use `items(timeline, key = { it.id })` and diff cheaply.
 *
 * Serialized polymorphically via [BclawJson] for [TimelineCacheRepository] persistence; the
 * @SerialName tags pin wire keys so future package moves don't invalidate stored caches.
 */
@Serializable
sealed class TimelineItem {
    abstract val id: String
    abstract val createdAtEpochMs: Long

    /**
     * Right-aligned, accent-color — the user's prompt input. [streaming] mirrors the agent
     * variants so the optimistic-append-then-merge-codex-echo flow works the same way (see
     * AcpTimelineReducer.appendUserChunk + freezeStreaming).
     */
    /**
     * [imageAttachments]: image files (phone picker or promoted mac files) that render as
     * thumbnails. URIs point to app-private storage so they survive process restart.
     *
     * [fileAttachments]: mac-side text-like files (path refs only, not content). Bridge
     * re-reads bytes at send time and we embed them in an ACP `resource` content block;
     * we don't persist file bytes in the timeline cache.
     */
    @Serializable
    @SerialName("user_message")
    data class UserMessage(
        override val id: String = "u-${UUID.randomUUID()}",
        override val createdAtEpochMs: Long,
        val text: String,
        val streaming: Boolean = false,
        val imageAttachments: List<ImageAttachment> = emptyList(),
        val fileAttachments: List<FileAttachment> = emptyList(),
    ) : TimelineItem()

    /**
     * Left-aligned, full-width, no bubble. Renders as plain text; inline `code` detection
     * (backticks) lives in the renderer, not here. Streaming: chunks are concatenated onto
     * [text] as they arrive; [streaming] flips false when the ACP prompt returns.
     */
    @Serializable
    @SerialName("agent_message")
    data class AgentMessage(
        override val id: String = "a-${UUID.randomUUID()}",
        override val createdAtEpochMs: Long,
        val text: String,
        val streaming: Boolean = false,
    ) : TimelineItem()

    /**
     * Flat card, 2dp accent left border. Renders the tool call as a titled command execution.
     *
     * Status values: `pending` / `running` / `completed` / `failed` / `cancelled` (from ACP
     * tool_call / tool_call_update). [outputTail] is the last N lines of the live stream;
     * the renderer collapses to a single "exit 0 · 24s" summary on completion.
     */
    @Serializable
    @SerialName("command_execution")
    data class CommandExecution(
        override val id: String,                  // = ACP toolCallId
        override val createdAtEpochMs: Long,
        val command: String,
        val cwd: String? = null,
        val status: ToolStatus = ToolStatus.Pending,
        val outputTail: String = "",              // live-tailed stdout/stderr (last few lines)
        val exitCode: Int? = null,
        val durationMs: Long? = null,
    ) : TimelineItem()

    /**
     * Flat card, 2dp accent left border. +N/-M summary header + expandable diff body.
     *
     * `paths` is the user-visible list of affected paths; counts optional for when the agent
     * emits a file edit without a unified diff yet (pre-commit).
     */
    @Serializable
    @SerialName("file_change")
    data class FileChange(
        override val id: String,                  // = ACP toolCallId
        override val createdAtEpochMs: Long,
        val paths: List<String>,
        val addedLines: Int = 0,
        val removedLines: Int = 0,
        val status: ToolStatus = ToolStatus.Pending,
        val unifiedDiff: String? = null,          // null until the agent ships the diff body
    ) : TimelineItem()

    /**
     * Collapsed `💭 thinking…` row, expandable to the streamed summary. Deliberately low
     * emphasis — reasoning is a power-user surface per UX_V2 §3.6.
     */
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        override val id: String = "r-${UUID.randomUUID()}",
        override val createdAtEpochMs: Long,
        val summary: String = "",
        val streaming: Boolean = false,
    ) : TimelineItem()

    /**
     * Unknown ACP update kind. Graceful fallback — never crash on a kind the renderer doesn't
     * know. The string [kind] is surfaced in a muted single-line row so debugging is cheap.
     */
    @Serializable
    @SerialName("unsupported")
    data class Unsupported(
        override val id: String = "x-${UUID.randomUUID()}",
        override val createdAtEpochMs: Long,
        val kind: String,
    ) : TimelineItem()

    /**
     * Inline image(s) the agent is showing to the user — typically via the `view_image` tool
     * call. Images arrive as `data:image/...;base64,...` URLs in `tool_call_update.rawOutput`
     * and are stored verbatim here; the renderer hands them straight to Coil's AsyncImage
     * (which supports the `data:` scheme). [sourcePath] mirrors the file path the agent read,
     * for the user's breadcrumb.
     */
    @Serializable
    @SerialName("agent_images")
    data class AgentImages(
        override val id: String,                   // = toolCallId
        override val createdAtEpochMs: Long,
        val title: String,
        val status: ToolStatus = ToolStatus.Completed,
        val sourcePath: String? = null,
        val imageUrls: List<String> = emptyList(),
    ) : TimelineItem()
}

@Serializable
enum class ToolStatus { Pending, Running, Completed, Failed, Cancelled }

/**
 * Reference to an image the user attached to a prompt. [uri] is a `file://` URI in the app's
 * private attachments dir (copied at send time so the reference is stable across restart and
 * after the picker's content:// URI has expired). [sizeBytes] powers the composer's cap check.
 */
@Serializable
data class ImageAttachment(
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
)

/**
 * Reference to a file on the paired Mac that the user picked via Tools→files. We store only
 * the path (relative to its project [cwd]) so the timeline cache stays small; the bridge
 * re-reads the content at send time. [truncated] mirrors the bridge's response so the
 * renderer can show "first 256 KB only" when the file was too big.
 *
 * [mimeType] is the best-effort MIME derived from the filename at pick time. When it starts
 * with `image/`, the controller promotes this attachment to an [ImageAttachment] (saves
 * bytes to app-private storage, renders as a thumbnail) and sends it as an ACP `image`
 * content block; otherwise the bytes go over the wire as a `resource` text block.
 */
@Serializable
data class FileAttachment(
    val cwd: String,
    val rel: String,
    val sizeBytes: Long,
    val truncated: Boolean = false,
    val mimeType: String? = null,
)
