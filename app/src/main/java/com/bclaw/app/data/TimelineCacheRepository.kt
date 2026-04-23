package com.bclaw.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bclaw.app.domain.v2.DeviceId
import com.bclaw.app.domain.v2.SessionId
import com.bclaw.app.domain.v2.TimelineItem
import com.bclaw.app.net.BclawJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

private val Context.timelineCacheDataStore by preferencesDataStore(name = "bclaw_timeline_cache")

/**
 * On-disk cache of per-session [TimelineItem] lists.
 *
 * Lets cold-start reopen of a historical session render instantly from cache instead of
 * waiting for `session/load` to re-stream the entire history from the bridge. New content
 * still arrives via `session/update` notifications during user interaction; we don't try
 * to fetch deltas from the server because ACP has no delta API.
 *
 * Keyed by `${deviceId}|${sessionId}` so removing a device wipes its cache atomically.
 */
class TimelineCacheRepository(context: Context) {
    private val appContext = context.applicationContext
    private val listSerializer = ListSerializer(TimelineItem.serializer())

    suspend fun read(deviceId: DeviceId, sessionId: SessionId): List<TimelineItem> =
        withContext(Dispatchers.IO) {
            val raw = appContext.timelineCacheDataStore.data.first()[entryKey(deviceId, sessionId)]
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { BclawJson.decodeFromString(listSerializer, raw) }
                .getOrElse { emptyList() }
        }

    suspend fun write(deviceId: DeviceId, sessionId: SessionId, items: List<TimelineItem>) =
        withContext(Dispatchers.IO) {
            appContext.timelineCacheDataStore.edit { prefs ->
                prefs[entryKey(deviceId, sessionId)] =
                    BclawJson.encodeToString(listSerializer, items)
            }
        }

    suspend fun removeForSession(deviceId: DeviceId, sessionId: SessionId) =
        withContext(Dispatchers.IO) {
            appContext.timelineCacheDataStore.edit { prefs ->
                prefs.remove(entryKey(deviceId, sessionId))
            }
        }

    suspend fun removeForDevice(deviceId: DeviceId) = withContext(Dispatchers.IO) {
        val prefix = "${deviceId.value}|"
        appContext.timelineCacheDataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith(prefix) }
            toRemove.forEach { prefs.remove(it) }
        }
    }

    private fun entryKey(deviceId: DeviceId, sessionId: SessionId) =
        stringPreferencesKey("${deviceId.value}|${sessionId.value}")
}
