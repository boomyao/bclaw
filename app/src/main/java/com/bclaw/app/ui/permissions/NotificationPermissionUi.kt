package com.bclaw.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bclaw.app.data.NotificationPermissionRepository
import kotlinx.coroutines.launch

enum class NotificationPermissionStatus {
    Granted,
    Requestable,
    Denied,
}

data class NotificationPermissionUiState(
    val status: NotificationPermissionStatus,
    val prompted: Boolean,
) {
    val granted: Boolean
        get() = status == NotificationPermissionStatus.Granted

    val showDeniedHint: Boolean
        get() = status == NotificationPermissionStatus.Denied
}

@Composable
fun rememberNotificationPermissionUiState(
    repository: NotificationPermissionRepository,
    shouldPrompt: Boolean,
): NotificationPermissionUiState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prompted by repository.notificationsPromptedFlow.collectAsState(initial = false)
    var permissionSnapshot by remember { mutableIntStateOf(notificationPermissionSnapshot(context)) }
    var requestInFlight by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionSnapshot = if (granted) 1 else 0
        requestInFlight = false
        scope.launch { repository.markNotificationsPrompted() }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionSnapshot = notificationPermissionSnapshot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(shouldPrompt, prompted, permissionSnapshot, requestInFlight) {
        if (
            shouldPrompt &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permissionSnapshot == 0 &&
            !prompted &&
            !requestInFlight
        ) {
            requestInFlight = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val status = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> NotificationPermissionStatus.Granted
        permissionSnapshot == 1 -> NotificationPermissionStatus.Granted
        prompted -> NotificationPermissionStatus.Denied
        else -> NotificationPermissionStatus.Requestable
    }

    return remember(status, prompted) {
        NotificationPermissionUiState(
            status = status,
            prompted = prompted,
        )
    }
}

fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun notificationPermissionSnapshot(context: Context): Int {
    return if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        1
    } else {
        0
    }
}
