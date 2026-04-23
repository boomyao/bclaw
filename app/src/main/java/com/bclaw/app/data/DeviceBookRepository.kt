package com.bclaw.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bclaw.app.domain.v2.Device
import com.bclaw.app.domain.v2.DeviceBook
import com.bclaw.app.domain.v2.DeviceId
import com.bclaw.app.net.BclawJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.deviceBookDataStore by preferencesDataStore(name = "bclaw_device_book")

class DeviceBookRepository(context: Context) {
    private val appContext = context.applicationContext
    private val bookKey = stringPreferencesKey("device_book_json")
    private val tokenPrefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createTokenPrefs(appContext) }

    val deviceBookFlow: Flow<DeviceBook> = appContext.deviceBookDataStore.data.map { prefs ->
        rehydrateTokens(readStoredBook(prefs[bookKey]).book)
    }

    suspend fun replaceDeviceBook(deviceBook: DeviceBook) = withContext(Dispatchers.IO) {
        appContext.deviceBookDataStore.edit { prefs ->
            val current = rehydrateTokens(readStoredBook(prefs[bookKey]).book)
            val normalized = normalize(deviceBook)
            syncTokens(current.devices, normalized.devices)
            writeStoredBook(prefs, normalized, readStoredBook(prefs[bookKey]).revision + 1)
        }
    }

    suspend fun upsertDevice(device: Device) = withContext(Dispatchers.IO) {
        appContext.deviceBookDataStore.edit { prefs ->
            val stored = readStoredBook(prefs[bookKey])
            val current = rehydrateTokens(stored.book)
            val updated = normalize(
                current.copy(devices = current.devices.upsert(device)),
            )
            tokenPrefs.edit(commit = true) { putString(tokenKey(device.id), device.token) }
            writeStoredBook(prefs, updated, stored.revision + 1)
        }
    }

    suspend fun removeDevice(deviceId: DeviceId) = withContext(Dispatchers.IO) {
        appContext.deviceBookDataStore.edit { prefs ->
            val stored = readStoredBook(prefs[bookKey])
            val updated = normalize(
                stored.book.copy(devices = stored.book.devices.filterNot { it.id == deviceId }),
            )
            tokenPrefs.edit(commit = true) { remove(tokenKey(deviceId)) }
            writeStoredBook(prefs, updated, stored.revision + 1)
        }
    }

    suspend fun setActiveDevice(deviceId: DeviceId?) = withContext(Dispatchers.IO) {
        appContext.deviceBookDataStore.edit { prefs ->
            val stored = readStoredBook(prefs[bookKey])
            if (deviceId != null && stored.book.devices.none { it.id == deviceId }) return@edit
            writeStoredBook(
                prefs,
                normalize(stored.book.copy(activeDeviceId = deviceId)),
                stored.revision,
            )
        }
    }

    private fun readStoredBook(raw: String?): StoredDeviceBook = runCatching {
        if (raw.isNullOrBlank()) StoredDeviceBook() else BclawJson.decodeFromString(raw)
    }.getOrElse { StoredDeviceBook() }

    private fun writeStoredBook(
        prefs: MutablePreferences,
        deviceBook: DeviceBook,
        revision: Long,
    ) {
        prefs[bookKey] = BclawJson.encodeToString(StoredDeviceBook(stripTokens(deviceBook), revision))
    }

    private fun rehydrateTokens(deviceBook: DeviceBook): DeviceBook = deviceBook.copy(
        devices = deviceBook.devices.map { device ->
            device.copy(token = tokenPrefs.getString(tokenKey(device.id), "") ?: "")
        },
    )

    private fun syncTokens(oldDevices: List<Device>, newDevices: List<Device>) {
        val newTokens = newDevices.associateBy({ it.id }, { it.token })
        tokenPrefs.edit(commit = true) {
            oldDevices.map { it.id }.filterNot(newTokens::containsKey).forEach { remove(tokenKey(it)) }
            newTokens.forEach { (id, token) -> putString(tokenKey(id), token) }
        }
    }

    private fun stripTokens(deviceBook: DeviceBook): DeviceBook = deviceBook.copy(
        devices = deviceBook.devices.map { it.copy(token = "") },
    )

    private fun normalize(deviceBook: DeviceBook): DeviceBook {
        val activeDeviceId = deviceBook.activeDeviceId
            ?.takeIf { id -> deviceBook.devices.any { it.id == id } }
            ?: deviceBook.devices.firstOrNull()?.id
        return deviceBook.copy(activeDeviceId = activeDeviceId)
    }

    private fun List<Device>.upsert(device: Device): List<Device> {
        val index = indexOfFirst { it.id == device.id }
        if (index < 0) return this + device
        return toMutableList().apply { this[index] = device }
    }

    private fun tokenKey(deviceId: DeviceId): String = "token.${deviceId.value}"

    private fun createTokenPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            TOKEN_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        const val TOKEN_PREFS_FILE = "bclaw_device_tokens"
    }
}

@Serializable
private data class StoredDeviceBook(
    val book: DeviceBook = DeviceBook(),
    val revision: Long = 0,
)
