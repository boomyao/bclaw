package com.bclaw.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bclaw.app.net.BclawJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

private val Context.pinnedDirsDataStore by preferencesDataStore(name = "bclaw_pinned_dirs")

/**
 * Per-cwd list of pinned relative directory paths. Lets the user jump straight to "the
 * file location I always ask about in this project" without retraversing the tree. Pins
 * are scoped per-cwd because the same rel (`src/main`) means different things in different
 * projects.
 */
class PinnedDirsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val listSerializer = ListSerializer(String.serializer())

    fun observe(cwd: String): Flow<List<String>> =
        appContext.pinnedDirsDataStore.data.map { prefs ->
            val raw = prefs[keyFor(cwd)] ?: return@map emptyList()
            runCatching { BclawJson.decodeFromString(listSerializer, raw) }
                .getOrElse { emptyList() }
        }

    suspend fun toggle(cwd: String, rel: String) = withContext(Dispatchers.IO) {
        appContext.pinnedDirsDataStore.edit { prefs ->
            val current = prefs[keyFor(cwd)]
                ?.let { runCatching { BclawJson.decodeFromString(listSerializer, it) }.getOrNull() }
                ?: emptyList()
            val next = if (rel in current) current - rel else current + rel
            prefs[keyFor(cwd)] = BclawJson.encodeToString(listSerializer, next)
        }
    }

    private fun keyFor(cwd: String) = stringPreferencesKey("pinned_dirs:$cwd")
}
