package com.bclaw.app.domain.state

import com.bclaw.app.domain.model.ChatThreadState
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.net.codex.ThreadSummary

class ThreadTimelineStore(
    private val store: BclawStateStore,
) {
    fun hydrateThread(thread: ThreadSummary, history: List<TimelineItemUi>) {
        val previous = store.threadState(thread.id)
        val optimistic = previous?.items
            ?.filterIsInstance<TimelineItemUi.UserMessage>()
            ?.filter { it.optimistic }
            .orEmpty()
        val latestTurn = thread.turns.lastOrNull()
        store.mutate {
            it.copy(
                threadStates = it.threadStates + (
                    thread.id to ChatThreadState(
                        thread = thread,
                        items = (history + optimistic).distinctBy { item -> item.id },
                        activeTurnId = latestTurn?.id?.takeIf { latestTurn.status == "inProgress" }
                            ?: previous?.activeTurnId,
                        latestTurnId = latestTurn?.id ?: previous?.latestTurnId,
                        latestTurnStatus = latestTurn?.status ?: previous?.latestTurnStatus,
                        historyLoaded = true,
                        loading = false,
                        error = null,
                        diffByTurn = previous?.diffByTurn.orEmpty(),
                    )
                ),
            )
        }
        store.mergeThreadSummary(thread)
    }

    fun upsertTimelineItem(threadId: String, item: TimelineItemUi) {
        val current = store.threadState(threadId) ?: ChatThreadState()
        val items = current.items.toMutableList()
        val optimisticIndex = if (item is TimelineItemUi.UserMessage) {
            items.indexOfFirst { existing ->
                existing is TimelineItemUi.UserMessage && existing.optimistic && existing.text == item.text
            }
        } else {
            -1
        }
        when {
            optimisticIndex >= 0 -> items[optimisticIndex] = item
            else -> {
                val existingIndex = items.indexOfFirst { it.id == item.id }
                if (existingIndex >= 0) items[existingIndex] = item else items += item
            }
        }
        store.mutate {
            it.copy(
                threadStates = it.threadStates + (
                    threadId to current.copy(items = items, loading = false, error = null)
                ),
            )
        }
    }

    fun appendAgentDelta(threadId: String, turnId: String, itemId: String, delta: String) {
        mutateTimeline(threadId) { item ->
            if (item is TimelineItemUi.AgentMessage && item.id == itemId) item.copy(text = item.text + delta) else item
        } ?: upsertTimelineItem(
            threadId,
            TimelineItemUi.AgentMessage(id = itemId, turnId = turnId, text = delta),
        )
    }

    fun appendCommandOutput(threadId: String, turnId: String, itemId: String, delta: String) {
        mutateTimeline(threadId) { item ->
            if (item is TimelineItemUi.CommandExecution && item.id == itemId) item.copy(output = item.output + delta) else item
        } ?: upsertTimelineItem(
            threadId,
            TimelineItemUi.CommandExecution(
                id = itemId,
                turnId = turnId,
                command = "",
                cwd = "",
                status = "inProgress",
                output = delta,
            ),
        )
    }

    fun appendReasoningDelta(threadId: String, turnId: String, itemId: String, delta: String) {
        mutateTimeline(threadId) { item ->
            if (item is TimelineItemUi.Reasoning && item.id == itemId) item.copy(summary = item.summary + delta) else item
        } ?: upsertTimelineItem(
            threadId,
            TimelineItemUi.Reasoning(id = itemId, turnId = turnId, summary = delta),
        )
    }

    fun appendTurnDiff(threadId: String, turnId: String, diff: String) {
        val current = store.threadState(threadId) ?: ChatThreadState()
        val updatedItems = current.items.map { item ->
            if (item is TimelineItemUi.FileChange && item.turnId == turnId) item.copy(diff = diff) else item
        }
        store.mutate {
            it.copy(
                threadStates = it.threadStates + (
                    threadId to current.copy(
                        items = updatedItems,
                        diffByTurn = current.diffByTurn + (turnId to diff),
                    )
                ),
            )
        }
    }

    fun appendError(threadId: String, turnId: String, message: String) {
        upsertTimelineItem(
            threadId,
            TimelineItemUi.Error(
                id = "error:${threadId}:${turnId}:${message.hashCode()}",
                turnId = turnId,
                message = message,
            ),
        )
    }

    private fun mutateTimeline(
        threadId: String,
        transform: (TimelineItemUi) -> TimelineItemUi,
    ): TimelineItemUi? {
        var changed: TimelineItemUi? = null
        store.mutate { state ->
            val current = state.threadStates[threadId] ?: return@mutate state
            val updated = current.items.map { item ->
                val next = transform(item)
                if (next !== item) {
                    changed = next
                }
                next
            }
            state.copy(threadStates = state.threadStates + (threadId to current.copy(items = updated)))
        }
        return changed
    }
}
