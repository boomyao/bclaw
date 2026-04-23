package com.bclaw.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bclaw.app.BclawApplication
import com.bclaw.app.MainActivity
import com.bclaw.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * bclaw v2 foreground service — keeps the process alive so the controller's coroutines
 * (AcpTransport WebSockets, streaming turns) survive backgrounding + screen-off.
 *
 * Lifetime:
 *   - Started by [MainActivity] when `uiState.hasActiveDevice` flips true
 *   - Stopped when `hasActiveDevice` flips false (last device removed)
 *
 * The notification reflects device + per-agent connection phase so the user gets the
 * continuous "remote machine is present" signal required by UX_V2 §0 principle 5 even when
 * the app isn't foreground.
 *
 * START_NOT_STICKY — if the OS kills the process, don't auto-restart the service without
 * user action. The next MainActivity launch will re-start it once it sees an active device.
 */
class BclawForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initial = buildNotification(summary = "starting…")
        startForegroundCompat(initial)
        observeConnectionPhase()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Observe [BclawV2Controller.uiState] and push a fresh notification every time the
     * connection summary changes. Dedup via distinctUntilChanged so we don't spam
     * NotificationManager during chatty `session/update` streams.
     */
    private fun observeConnectionPhase() {
        observeJob?.cancel()
        val controller = (application as BclawApplication).controller
        observeJob = scope.launch {
            controller.uiState
                .map { state -> summarize(state) }
                .distinctUntilChanged()
                .collect { summary ->
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification(summary))
                }
        }
    }

    private fun summarize(state: BclawV2UiState): String {
        val device = state.deviceBook.activeDevice?.displayName ?: return "no device paired"
        val connected = state.agentConnections.values.count { it == AgentConnectionPhase.Connected }
        val connecting = state.agentConnections.values.count {
            it == AgentConnectionPhase.Connecting || it == AgentConnectionPhase.Reconnecting
        }
        val inflight = state.tabRuntimes.values.count { it.streamingTurnInFlight }
        return buildString {
            append(device)
            if (connected > 0) append(" · ").append(connected).append(" agent")
                .append(if (connected == 1) "" else "s")
            if (connecting > 0) append(" · ").append(connecting).append(" connecting")
            if (inflight > 0) append(" · turn in flight")
            if (connected == 0 && connecting == 0 && inflight == 0) append(" · idle")
        }
    }

    private fun buildNotification(summary: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // v0 parity — real icon comes in polish batch
            .setContentTitle(getString(R.string.app_name))
            .setContentText(summary)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "bclaw-v2-presence"
        private const val NOTIFICATION_ID = 0xBC_12

        /** Start the service. Safe to call repeatedly; Android dedups. */
        fun start(context: Context) {
            val intent = Intent(context, BclawForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stop the service. Safe to call when not running. */
        fun stop(context: Context) {
            val intent = Intent(context, BclawForegroundService::class.java)
            context.stopService(intent)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "bclaw presence",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps bclaw connected while the app is in the background."
                    setShowBadge(false)
                },
            )
        }
    }
}
