package com.bclaw.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bclaw.app.BclawApplication
import com.bclaw.app.MainActivity
import com.bclaw.app.R
import com.bclaw.app.data.ConnectionConfig
import com.bclaw.app.domain.model.BclawUiState
import com.bclaw.app.domain.model.ChatThreadState
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.domain.model.TimelineItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BclawForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: BclawSessionController

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        controller = BclawSessionController(
            context = this,
            configRepository = (application as BclawApplication).configRepository,
            parentScope = scope,
        )
        BclawRuntime.install(controller)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                NotificationContent(
                    title = getString(R.string.notification_title),
                    text = "Starting…",
                ),
            ),
        )
        scope.launch {
            combine(
                controller.uiState,
                (application as BclawApplication).configRepository.configFlow,
            ) { state, config ->
                buildNotificationContent(state = state, config = config)
            }.collect { content ->
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(content))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_BOOTSTRAP -> controller.bootstrap()

            ACTION_RECONNECT_NOW -> controller.requestReconnectNow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        controller.shutdown()
        BclawRuntime.clear(controller)
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(content: NotificationContent) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun buildNotificationContent(
        state: BclawUiState,
        config: ConnectionConfig,
    ): NotificationContent {
        val host = config.host
            .removePrefix("ws://")
            .removePrefix("wss://")
            .ifBlank { "not configured" }
        val title = when (state.connectionPhase) {
            ConnectionPhase.Connected -> "bclaw — connected to $host"
            ConnectionPhase.Connecting -> "bclaw — connecting to $host"
            ConnectionPhase.Reconnecting -> "bclaw — reconnecting to $host"
            else -> "bclaw — $host"
        }
        val activeThread = state.threadStates.values.firstOrNull { threadState ->
            threadState.activeTurnId != null
        }
        val text = when {
            activeThread != null -> activeThread.notificationStatusLine()
            state.connectionPhase == ConnectionPhase.Connected -> "idle"
            !state.statusMessage.isNullOrBlank() -> state.statusMessage
            else -> state.connectionPhase.toNotificationLabel()
        }
        return NotificationContent(
            title = title,
            text = text,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_CHANNEL_ID = "bclaw-connection"
        private const val ACTION_BOOTSTRAP = "com.bclaw.app.action.BOOTSTRAP"
        private const val ACTION_RECONNECT_NOW = "com.bclaw.app.action.RECONNECT_NOW"

        fun bootstrap(context: Context) {
            start(context, ACTION_BOOTSTRAP)
        }

        fun reconnectNow(context: Context) {
            start(context, ACTION_RECONNECT_NOW)
        }

        private fun start(context: Context, action: String) {
            val intent = Intent(context, BclawForegroundService::class.java).apply {
                this.action = action
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

private data class NotificationContent(
    val title: String,
    val text: String,
)

private fun ConnectionPhase.toNotificationLabel(): String {
    return when (this) {
        ConnectionPhase.Idle -> "starting"
        ConnectionPhase.Connecting -> "connecting"
        ConnectionPhase.Connected -> "idle"
        ConnectionPhase.Reconnecting -> "reconnecting"
        ConnectionPhase.Offline -> "offline"
        ConnectionPhase.Error -> "error"
        ConnectionPhase.AuthFailed -> "auth failed"
    }
}

private fun ChatThreadState.notificationStatusLine(): String {
    val threadName = thread?.name?.takeIf { it.isNotBlank() }
        ?: thread?.preview?.takeIf { it.isNotBlank() }
        ?: "turn running"
    val livePreview = items.asReversed().firstNotNullOfOrNull { item ->
        when (item) {
            is TimelineItemUi.AgentMessage -> item.text.lastMeaningfulLine()
            is TimelineItemUi.CommandExecution -> item.output.lastMeaningfulLine()
            is TimelineItemUi.Reasoning -> item.summary.lastMeaningfulLine()
            is TimelineItemUi.Error -> item.message.lastMeaningfulLine()
            else -> null
        }
    }
    return livePreview?.let { "running — $it" } ?: "running $threadName"
}

private fun String.lastMeaningfulLine(): String? {
    return lineSequence()
        .map { it.trim() }
        .lastOrNull { it.isNotEmpty() }
}
