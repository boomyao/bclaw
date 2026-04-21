package com.bclaw.app.ui
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bclaw.app.BclawApplication
import com.bclaw.app.data.ConnectionConfig
import com.bclaw.app.data.WorkspaceConfig
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.service.BclawForegroundService
import com.bclaw.app.service.BclawSessionController
import com.bclaw.app.ui.chat.ChatScreen
import com.bclaw.app.ui.connection.BclawStatusSheet
import com.bclaw.app.ui.connection.ConnectionScreen
import com.bclaw.app.ui.connection.WelcomeScreen
import com.bclaw.app.ui.permissions.openNotificationSettings
import com.bclaw.app.ui.permissions.rememberNotificationPermissionUiState
import com.bclaw.app.ui.workspaces.BclawDrawerContent
import com.bclaw.app.ui.workspaces.buildWorkspacePresence
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography

@Composable
fun BclawApp(
    application: BclawApplication,
    controller: BclawSessionController?,
) {
    val context = LocalContext.current
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val config by application.configRepository.configFlow.collectAsState(initial = ConnectionConfig())

    LaunchedEffect(Unit) {
        BclawForegroundService.bootstrap(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.terminalBlack),
    ) {
        if (controller == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Starting background session…",
                    style = typography.body,
                    color = colors.textPrimary,
                )
            }
            return@Box
        }

        val uiState by controller.uiState.collectAsState()
        val navState = rememberBclawNavState(
            currentWorkspaceId = config.lastOpenedWorkspaceId ?: config.workspaces.firstOrNull()?.id,
            currentThreadId = config.workspaces.firstOrNull { it.id == config.lastOpenedWorkspaceId }?.lastOpenedThreadId
                ?: config.workspaces.firstOrNull()?.lastOpenedThreadId,
        )
        val notificationPermission = rememberNotificationPermissionUiState(
            repository = application.notificationPermissionRepository,
            shouldPrompt = config.isConfigured && !navState.connectionSettingsOpen,
        )
        val workspacePresenceById = config.workspaces.associate { workspace ->
            workspace.id to buildWorkspacePresence(workspace, uiState)
        }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        BackHandler(
            enabled = navState.drawerOpen || navState.statusSheetOpen || navState.connectionSettingsOpen,
        ) {
            when {
                navState.drawerOpen -> navState.closeDrawer()
                navState.statusSheetOpen -> navState.closeStatusSheet()
                navState.connectionSettingsOpen -> navState.closeConnectionSettings()
            }
        }

        LaunchedEffect(navState.drawerOpen) {
            if (navState.drawerOpen) {
                drawerState.open()
            } else {
                drawerState.close()
            }
        }

        LaunchedEffect(drawerState.currentValue) {
            navState.drawerOpen = drawerState.currentValue == DrawerValue.Open
        }

        LaunchedEffect(config.isConfigured) {
            if (!config.isConfigured) {
                navState.clear()
            }
        }

        LaunchedEffect(
            config.isConfigured,
            config.lastOpenedWorkspaceId,
            config.workspaces.map { "${it.id}:${it.lastOpenedThreadId}:${it.cwd}" },
        ) {
            if (!config.isConfigured) return@LaunchedEffect
            val currentWorkspace = navState.currentWorkspaceId
                ?.takeIf { workspaceId -> config.workspaces.any { it.id == workspaceId } }
            val resolvedWorkspaceId = currentWorkspace
                ?: config.lastOpenedWorkspaceId
                ?: config.workspaces.firstOrNull()?.id
            val resolvedThreadId = when {
                resolvedWorkspaceId == null -> null
                currentWorkspace == resolvedWorkspaceId && navState.currentThreadId != null -> navState.currentThreadId
                else -> config.workspaces.firstOrNull { it.id == resolvedWorkspaceId }?.lastOpenedThreadId
            }
            navState.syncSelection(resolvedWorkspaceId, resolvedThreadId)
        }

        LaunchedEffect(
            config.isConfigured,
            config.workspaces.map { "${it.id}:${it.cwd}" },
            uiState.connectionPhase,
            uiState.workspaceThreads.keys,
        ) {
            if (!config.isConfigured) return@LaunchedEffect
            if (uiState.connectionPhase != ConnectionPhase.Connected) return@LaunchedEffect
            config.workspaces.forEach { workspace ->
                if (uiState.workspaceThreads[workspace.id] == null) {
                    controller.loadThreads(workspace.id)
                }
            }
        }

        val currentWorkspace = config.workspaces.firstOrNull { it.id == navState.currentWorkspaceId }
            ?: config.workspaces.firstOrNull()
        val currentThreadId = navState.currentThreadId
            ?: currentWorkspace?.let { workspace ->
                workspace.lastOpenedThreadId
                    ?: uiState.workspaceThreads[workspace.id]?.threads?.firstOrNull()?.id
            }

        LaunchedEffect(currentWorkspace?.id, currentThreadId, navState.currentThreadId) {
            if (navState.currentThreadId == null && currentWorkspace != null && currentThreadId != null) {
                navState.syncSelection(currentWorkspace.id, currentThreadId)
            }
        }

        LaunchedEffect(
            navState.connectionSettingsOpen,
            navState.closeConnectionSettingsAfterSave,
            uiState.connectionPhase,
        ) {
            if (
                navState.connectionSettingsOpen &&
                navState.closeConnectionSettingsAfterSave &&
                uiState.connectionPhase == ConnectionPhase.Connected
            ) {
                navState.closeConnectionSettings()
            }
        }

        val chatWorkspace = currentWorkspace
        val chatThreadId = currentThreadId
        val rootSurface = when {
            !config.isConfigured && !navState.connectionSettingsOpen -> RootSurface.Welcome
            !config.isConfigured || navState.connectionSettingsOpen -> RootSurface.Connection
            else -> RootSurface.Chat
        }

        Crossfade(targetState = rootSurface, label = "bclaw-root-crossfade") { surface ->
            when (surface) {
                RootSurface.Welcome -> WelcomeScreen(
                    onContinue = { navState.openConnectionSettings() },
                )

                RootSurface.Connection -> ConnectionScreen(
                    initialConfig = config,
                    connectionPhase = uiState.connectionPhase,
                    statusMessage = uiState.protocolWarning ?: uiState.statusMessage,
                    onConnect = { connectionConfig ->
                        navState.submitConnectionSettings()
                        controller.saveConfigAndConnect(connectionConfig)
                    },
                    onDismiss = navState.takeIf { config.isConfigured }?.let {
                        { navState.closeConnectionSettings() }
                    },
                )

                RootSurface.Chat -> ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !navState.connectionSettingsOpen,
                    scrimColor = Color.Black.copy(alpha = 0.4f),
                    drawerContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.75f)
                                .background(colors.surfaceNear),
                        ) {
                            BclawDrawerContent(
                                drawerOpen = drawerState.currentValue == DrawerValue.Open ||
                                    drawerState.targetValue == DrawerValue.Open,
                                workspaces = config.workspaces,
                                uiState = uiState,
                                workspacePresenceById = workspacePresenceById,
                                currentWorkspaceId = chatWorkspace?.id,
                                currentThreadId = chatThreadId,
                                onSelectWorkspace = { workspace ->
                                    controller.rememberWorkspace(workspace.id)
                                    val threadId = workspace.lastOpenedThreadId
                                        ?: uiState.workspaceThreads[workspace.id]?.threads?.firstOrNull()?.id
                                    navState.selectThread(workspace.id, threadId)
                                },
                                onSelectThread = { workspace, threadId ->
                                    navState.selectThread(workspace.id, threadId)
                                },
                                onCreateThread = { workspace ->
                                    controller.startThread(workspace.id) { startedThreadId ->
                                        navState.selectThread(workspace.id, startedThreadId)
                                    }
                                },
                                onAddWorkspace = { navState.openConnectionSettings(prefillNewWorkspace = true) },
                                onOpenSettings = { navState.openConnectionSettings() },
                                onSelectAgent = { agentName ->
                                    controller.connectToAgent(agentName)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChatScreen(
                            workspace = chatWorkspace,
                            threadId = chatThreadId,
                            connectionPhase = uiState.connectionPhase,
                            threadState = chatThreadId?.let { uiState.threadStates[it] },
                            statusMessage = uiState.protocolWarning ?: uiState.statusMessage,
                            onOpenDrawer = { navState.openDrawer() },
                            onOpenStatusSheet = { navState.toggleStatusSheet() },
                            onOpenThread = chatWorkspace?.let { workspace ->
                                chatThreadId?.let { threadId ->
                                    { controller.openThread(workspace.id, threadId) }
                                }
                            },
                            onSend = { message ->
                                chatThreadId?.let { controller.sendMessage(it, message) }
                            },
                            onInterrupt = {
                                chatThreadId?.let { controller.interruptTurn(it) }
                            },
                        )
                        if (navState.statusSheetOpen) {
                            BclawStatusSheet(
                                config = config,
                                showNotificationsDisabledHint = notificationPermission.showDeniedHint,
                                onDismiss = { navState.closeStatusSheet() },
                                onDisconnect = {
                                    controller.disconnect()
                                    navState.closeStatusSheet()
                                },
                                onReconnectNow = {
                                    controller.requestReconnectNow()
                                    navState.closeStatusSheet()
                                },
                                onOpenSettings = {
                                    navState.openConnectionSettings()
                                },
                                onOpenNotificationSettings = {
                                    openNotificationSettings(context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class RootSurface {
    Welcome,
    Connection,
    Chat,
}
