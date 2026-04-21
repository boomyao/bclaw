package com.bclaw.app.domain.state

import com.bclaw.app.data.WorkspaceConfig
import com.bclaw.app.domain.model.AgentSlot
import com.bclaw.app.domain.model.BclawUiState
import com.bclaw.app.domain.model.ChatThreadState
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.domain.model.WorkspaceThreadsState
import com.bclaw.app.net.codex.ThreadStatus
import com.bclaw.app.net.codex.ThreadSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BclawStateStore {
    private val mutableState = MutableStateFlow(BclawUiState())
    val uiState: StateFlow<BclawUiState> = mutableState.asStateFlow()

    val snapshot: BclawUiState
        get() = mutableState.value

    fun setWorkspaces(workspaces: List<WorkspaceConfig>) {
        mutableState.update { it.copy(workspaces = workspaces) }
    }

    fun setAvailableAgents(agents: List<AgentSlot>) {
        mutableState.update { it.copy(availableAgents = agents) }
    }

    fun setActiveAgent(agentName: String?) {
        mutableState.update { it.copy(activeAgentName = agentName) }
    }

    fun clearAllThreadStates() {
        mutableState.update {
            it.copy(
                workspaceThreads = emptyMap(),
                threadStates = emptyMap(),
            )
        }
    }

    fun workspaceForId(workspaceId: String): WorkspaceConfig? {
        return snapshot.workspaces.firstOrNull { it.id == workspaceId }
    }

    fun workspaceThreadsState(workspaceId: String): WorkspaceThreadsState? = snapshot.workspaceThreads[workspaceId]

    fun threadState(threadId: String): ChatThreadState? = snapshot.threadStates[threadId]

    fun activeTurnId(threadId: String): String? = snapshot.threadStates[threadId]?.activeTurnId

    fun setConnectionPhase(phase: ConnectionPhase, statusMessage: String? = snapshot.statusMessage) {
        mutableState.update { it.copy(connectionPhase = phase, statusMessage = statusMessage) }
    }

    fun setStatusMessage(message: String?) {
        mutableState.update { it.copy(statusMessage = message) }
    }

    fun setProtocolWarning(message: String) {
        mutableState.update {
            it.copy(
                protocolWarning = message,
                statusMessage = message,
            )
        }
    }

    fun markWorkspaceThreadsLoading(workspaceId: String) {
        val current = snapshot.workspaceThreads[workspaceId] ?: WorkspaceThreadsState(workspaceId = workspaceId)
        mutableState.update {
            it.copy(
                workspaceThreads = it.workspaceThreads + (
                    workspaceId to current.copy(loading = true, error = null)
                ),
            )
        }
    }

    fun applyWorkspaceThreadPage(
        workspaceId: String,
        threads: List<ThreadSummary>,
        nextCursor: String?,
        append: Boolean,
    ) {
        val current = snapshot.workspaceThreads[workspaceId]
        val merged = if (append) {
            (current?.threads.orEmpty() + threads).distinctBy { it.id }
        } else {
            threads
        }
        mutableState.update {
            it.copy(
                workspaceThreads = it.workspaceThreads + (
                    workspaceId to WorkspaceThreadsState(
                        workspaceId = workspaceId,
                        threads = merged,
                        nextCursor = nextCursor,
                        loading = false,
                        error = null,
                    )
                ),
            )
        }
    }

    fun setWorkspaceThreadsError(workspaceId: String, message: String) {
        val current = snapshot.workspaceThreads[workspaceId] ?: WorkspaceThreadsState(workspaceId = workspaceId)
        mutableState.update {
            it.copy(
                workspaceThreads = it.workspaceThreads + (
                    workspaceId to current.copy(loading = false, error = message)
                ),
                statusMessage = message,
            )
        }
    }

    fun markThreadLoading(threadId: String) {
        val current = snapshot.threadStates[threadId] ?: ChatThreadState()
        mutableState.update {
            it.copy(
                threadStates = it.threadStates + (
                    threadId to current.copy(loading = true, error = null)
                ),
            )
        }
    }

    fun setThreadError(threadId: String, message: String) {
        val current = snapshot.threadStates[threadId] ?: ChatThreadState()
        mutableState.update {
            it.copy(
                threadStates = it.threadStates + (
                    threadId to current.copy(loading = false, error = message)
                ),
                statusMessage = message,
            )
        }
    }

    fun markThreadClosed(threadId: String) {
        applyThreadStatus(threadId, ThreadStatus(type = "notLoaded"))
    }

    fun mergeThreadSummary(thread: ThreadSummary) {
        mutableState.update { state ->
            val updatedThreadStates = state.threadStates + (
                thread.id to (state.threadStates[thread.id] ?: ChatThreadState()).copy(thread = thread)
            )
            val updatedWorkspaceThreads = state.workspaceThreads.mapValues { (_, workspaceState) ->
                val workspace = state.workspaces.firstOrNull { it.id == workspaceState.workspaceId }
                if (workspace?.cwd != thread.cwd && workspaceState.threads.none { it.id == thread.id }) {
                    workspaceState
                } else {
                    val updatedThreads = workspaceState.threads.toMutableList()
                    val index = updatedThreads.indexOfFirst { it.id == thread.id }
                    if (index >= 0) {
                        updatedThreads[index] = thread
                    } else {
                        updatedThreads.add(0, thread)
                    }
                    workspaceState.copy(threads = updatedThreads)
                }
            }
            state.copy(
                threadStates = updatedThreadStates,
                workspaceThreads = updatedWorkspaceThreads,
            )
        }
    }

    fun applyThreadStatus(threadId: String, status: ThreadStatus) {
        mutableState.update { state ->
            val updatedThreadStates = state.threadStates.mapValues { (_, threadState) ->
                if (threadState.thread?.id == threadId) {
                    threadState.copy(thread = threadState.thread.copy(status = status))
                } else {
                    threadState
                }
            }
            val updatedWorkspaceThreads = state.workspaceThreads.mapValues { (_, workspaceState) ->
                workspaceState.copy(
                    threads = workspaceState.threads.map { thread ->
                        if (thread.id == threadId) thread.copy(status = status) else thread
                    },
                )
            }
            state.copy(
                threadStates = updatedThreadStates,
                workspaceThreads = updatedWorkspaceThreads,
            )
        }
    }

    fun applyThreadName(threadId: String, name: String?) {
        mutableState.update { state ->
            val updatedThreadStates = state.threadStates.mapValues { (_, threadState) ->
                if (threadState.thread?.id == threadId) {
                    threadState.copy(thread = threadState.thread.copy(name = name))
                } else {
                    threadState
                }
            }
            val updatedWorkspaceThreads = state.workspaceThreads.mapValues { (_, workspaceState) ->
                workspaceState.copy(
                    threads = workspaceState.threads.map { thread ->
                        if (thread.id == threadId) thread.copy(name = name) else thread
                    },
                )
            }
            state.copy(
                threadStates = updatedThreadStates,
                workspaceThreads = updatedWorkspaceThreads,
            )
        }
    }

    fun setTurnStarted(threadId: String, turnId: String, status: String) {
        val current = snapshot.threadStates[threadId] ?: ChatThreadState()
        mutableState.update {
            it.copy(
                threadStates = it.threadStates + (
                    threadId to current.copy(
                        activeTurnId = turnId,
                        latestTurnId = turnId,
                        latestTurnStatus = status,
                        historyLoaded = current.historyLoaded,
                        loading = false,
                        error = null,
                    )
                ),
            )
        }
    }

    fun setTurnCompleted(threadId: String, turnId: String, status: String, errorMessage: String?) {
        val current = snapshot.threadStates[threadId] ?: ChatThreadState()
        mutableState.update {
            it.copy(
                threadStates = it.threadStates + (
                    threadId to current.copy(
                        activeTurnId = null,
                        latestTurnId = turnId,
                        latestTurnStatus = status,
                        historyLoaded = current.historyLoaded,
                        error = errorMessage,
                    )
                ),
            )
        }
    }

    internal fun mutate(transform: (BclawUiState) -> BclawUiState) {
        mutableState.update(transform)
    }
}
