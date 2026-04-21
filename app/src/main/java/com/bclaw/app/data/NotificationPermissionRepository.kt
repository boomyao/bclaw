package com.bclaw.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.notificationPermissionDataStore by preferencesDataStore(name = "bclaw_permissions")

class NotificationPermissionRepository(
    private val context: Context,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {
    private val notificationsPromptedKey = booleanPreferencesKey("notifications_prompted")

    val notificationsPromptedFlow: Flow<Boolean> = context.notificationPermissionDataStore.data.map { preferences ->
        preferences[notificationsPromptedKey] ?: false
    }

    suspend fun markNotificationsPrompted() {
        withContext(ioContext) {
            context.notificationPermissionDataStore.edit { preferences ->
                preferences[notificationsPromptedKey] = true
            }
        }
    }

    suspend fun resetForTesting() {
        withContext(ioContext) {
            context.notificationPermissionDataStore.edit { preferences ->
                preferences.remove(notificationsPromptedKey)
            }
        }
    }
}
