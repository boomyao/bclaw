package com.bclaw.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable

@Stable
class BclawNavState(
    currentWorkspaceId: String?,
    currentThreadId: String?,
    drawerOpen: Boolean,
    statusSheetOpen: Boolean,
    connectionSettingsOpen: Boolean,
) {
    var currentWorkspaceId by mutableStateOf(currentWorkspaceId)
    var currentThreadId by mutableStateOf(currentThreadId)
    var drawerOpen by mutableStateOf(drawerOpen)
    var statusSheetOpen by mutableStateOf(statusSheetOpen)
    var connectionSettingsOpen by mutableStateOf(connectionSettingsOpen)
    var openBlankWorkspaceSlot by mutableStateOf(false)
    var closeConnectionSettingsAfterSave by mutableStateOf(false)
    var connectionModalVersion by mutableIntStateOf(0)

    fun openDrawer() {
        statusSheetOpen = false
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
    }

    fun toggleStatusSheet() {
        drawerOpen = false
        statusSheetOpen = !statusSheetOpen
    }

    fun closeStatusSheet() {
        statusSheetOpen = false
    }

    fun openConnectionSettings(prefillNewWorkspace: Boolean = false) {
        drawerOpen = false
        statusSheetOpen = false
        openBlankWorkspaceSlot = prefillNewWorkspace
        closeConnectionSettingsAfterSave = false
        connectionSettingsOpen = true
        connectionModalVersion += 1
    }

    fun submitConnectionSettings() {
        closeConnectionSettingsAfterSave = true
    }

    fun closeConnectionSettings() {
        connectionSettingsOpen = false
        openBlankWorkspaceSlot = false
        closeConnectionSettingsAfterSave = false
    }

    fun selectThread(workspaceId: String, threadId: String?) {
        currentWorkspaceId = workspaceId
        currentThreadId = threadId
        drawerOpen = false
        statusSheetOpen = false
    }

    fun syncSelection(workspaceId: String?, threadId: String?) {
        currentWorkspaceId = workspaceId
        currentThreadId = threadId
    }

    fun clear() {
        currentWorkspaceId = null
        currentThreadId = null
        drawerOpen = false
        statusSheetOpen = false
        connectionSettingsOpen = false
        openBlankWorkspaceSlot = false
        closeConnectionSettingsAfterSave = false
    }

    companion object {
        val Saver: Saver<BclawNavState, List<Any?>> = Saver(
            save = { state ->
                listOf(
                    state.currentWorkspaceId,
                    state.currentThreadId,
                    state.drawerOpen,
                    state.statusSheetOpen,
                    state.connectionSettingsOpen,
                    state.connectionModalVersion,
                    state.openBlankWorkspaceSlot,
                    state.closeConnectionSettingsAfterSave,
                )
            },
            restore = { raw ->
                BclawNavState(
                    currentWorkspaceId = raw[0] as String?,
                    currentThreadId = raw[1] as String?,
                    drawerOpen = raw[2] as Boolean,
                    statusSheetOpen = raw[3] as Boolean,
                    connectionSettingsOpen = raw[4] as Boolean,
                ).also { state ->
                    state.connectionModalVersion = raw[5] as Int
                    state.openBlankWorkspaceSlot = raw[6] as Boolean
                    state.closeConnectionSettingsAfterSave = raw[7] as Boolean
                }
            },
        )
    }
}

@Composable
fun rememberBclawNavState(
    currentWorkspaceId: String?,
    currentThreadId: String?,
): BclawNavState {
    return rememberSaveable(saver = BclawNavState.Saver) {
        BclawNavState(
            currentWorkspaceId = currentWorkspaceId,
            currentThreadId = currentThreadId,
            drawerOpen = false,
            statusSheetOpen = false,
            connectionSettingsOpen = false,
        )
    }
}
