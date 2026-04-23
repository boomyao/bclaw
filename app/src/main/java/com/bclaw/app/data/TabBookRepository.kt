package com.bclaw.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bclaw.app.domain.v2.DeviceId
import com.bclaw.app.domain.v2.TabBook
import com.bclaw.app.domain.v2.TabId
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.net.BclawJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.tabBookDataStore by preferencesDataStore(name = "bclaw_tab_books")

class TabBookRepository(context: Context) {
    private val appContext = context.applicationContext

    fun observe(deviceId: DeviceId): Flow<TabBook> = appContext.tabBookDataStore.data.map { prefs ->
        readTabBook(prefs[bookKey(deviceId)], deviceId)
    }

    suspend fun replaceTabBook(tabBook: TabBook) = withContext(Dispatchers.IO) {
        appContext.tabBookDataStore.edit { prefs -> writeTabBook(prefs, normalize(tabBook)) }
    }

    suspend fun upsertTab(deviceId: DeviceId, tab: TabState) = withContext(Dispatchers.IO) {
        appContext.tabBookDataStore.edit { prefs ->
            val current = readTabBook(prefs[bookKey(deviceId)], deviceId)
            writeTabBook(
                prefs,
                normalize(current.copy(tabs = current.tabs.upsert(tab))),
            )
        }
    }

    suspend fun removeTab(deviceId: DeviceId, tabId: TabId) = withContext(Dispatchers.IO) {
        appContext.tabBookDataStore.edit { prefs ->
            val current = readTabBook(prefs[bookKey(deviceId)], deviceId)
            writeTabBook(
                prefs,
                normalize(current.copy(tabs = current.tabs.filterNot { it.id == tabId })),
            )
        }
    }

    suspend fun setActiveTab(deviceId: DeviceId, tabId: TabId?) = withContext(Dispatchers.IO) {
        appContext.tabBookDataStore.edit { prefs ->
            val current = readTabBook(prefs[bookKey(deviceId)], deviceId)
            if (tabId != null && current.tabs.none { it.id == tabId }) return@edit
            writeTabBook(prefs, normalize(current.copy(activeTabId = tabId)))
        }
    }

    suspend fun removeDeviceTabs(deviceId: DeviceId) = withContext(Dispatchers.IO) {
        appContext.tabBookDataStore.edit { prefs -> prefs.remove(bookKey(deviceId)) }
    }

    private fun readTabBook(raw: String?, deviceId: DeviceId): TabBook = runCatching {
        if (raw.isNullOrBlank()) TabBook(deviceId = deviceId) else BclawJson.decodeFromString(raw)
    }.getOrElse { TabBook(deviceId = deviceId) }

    private fun writeTabBook(prefs: MutablePreferences, tabBook: TabBook) {
        prefs[bookKey(tabBook.deviceId)] = BclawJson.encodeToString(tabBook)
    }

    private fun normalize(tabBook: TabBook): TabBook {
        val activeTabId = tabBook.activeTabId?.takeIf { id -> tabBook.tabs.any { it.id == id } }
        return tabBook.copy(activeTabId = activeTabId)
    }

    private fun List<TabState>.upsert(tab: TabState): List<TabState> {
        val index = indexOfFirst { it.id == tab.id }
        if (index < 0) return this + tab
        return toMutableList().apply { this[index] = tab }
    }

    private fun bookKey(deviceId: DeviceId) = stringPreferencesKey("tab_book_${deviceId.value}")
}
