package com.bclaw.app.service

import com.bclaw.app.data.BclawV2UrlParser
import com.bclaw.app.data.DeviceBookRepository
import com.bclaw.app.domain.v2.BclawV2UrlParseResult
import com.bclaw.app.domain.v2.Device
import com.bclaw.app.domain.v2.DeviceBook
import com.bclaw.app.domain.v2.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Remote-desktop-first app controller.
 *
 * It owns only host pairing and device selection. The old chat/session runtime was removed:
 * no discovery, no prompt transport, no history, no timeline cache.
 */
class BclawV2Controller(
    private val deviceBookRepository: DeviceBookRepository,
    networkAvailableFlow: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow(),
    parentScope: CoroutineScope? = null,
) {
    private val supervisor: Job = SupervisorJob(parentScope?.coroutineContext?.get(Job))
    private val scope = CoroutineScope(supervisor + Dispatchers.Default)

    private val _pairError = MutableStateFlow<BclawV2UrlParseResult.Error?>(null)
    val pairError: StateFlow<BclawV2UrlParseResult.Error?> = _pairError.asStateFlow()

    val networkAvailable: StateFlow<Boolean> = networkAvailableFlow

    val uiState: StateFlow<BclawV2UiState> = combine(
        deviceBookRepository.deviceBookFlow,
        networkAvailableFlow,
    ) { book, online ->
        BclawV2UiState(
            deviceBook = book,
            networkAvailable = online,
        )
    }.stateIn(scope, SharingStarted.Eagerly, BclawV2UiState())

    fun onIntent(intent: BclawV2Intent) {
        when (intent) {
            is BclawV2Intent.PairNewDevice -> pairNewDevice(intent.rawUrl)
            is BclawV2Intent.SwitchDevice -> switchDevice(intent.id)
            is BclawV2Intent.RemoveDevice -> removeDevice(intent.id)
        }
    }

    fun clearPairError() {
        _pairError.value = null
    }

    fun shutdown() {
        supervisor.cancel()
    }

    private fun pairNewDevice(rawUrl: String) {
        when (val parsed = BclawV2UrlParser.parse(rawUrl)) {
            is BclawV2UrlParseResult.Error -> _pairError.value = parsed
            is BclawV2UrlParseResult.Success -> {
                _pairError.value = null
                scope.launch {
                    val device = Device(
                        id = DeviceId.generate(),
                        displayName = defaultDeviceName(parsed.hostApiBaseUrl),
                        hostApiBaseUrl = parsed.hostApiBaseUrl,
                        token = parsed.token,
                        pairedAtEpochMs = System.currentTimeMillis(),
                    )
                    deviceBookRepository.upsertDevice(device)
                    deviceBookRepository.setActiveDevice(device.id)
                }
            }
        }
    }

    private fun switchDevice(id: DeviceId) {
        scope.launch { deviceBookRepository.setActiveDevice(id) }
    }

    private fun removeDevice(id: DeviceId) {
        scope.launch { deviceBookRepository.removeDevice(id) }
    }
}

sealed class BclawV2Intent {
    data class PairNewDevice(val rawUrl: String) : BclawV2Intent()
    data class SwitchDevice(val id: DeviceId) : BclawV2Intent()
    data class RemoveDevice(val id: DeviceId) : BclawV2Intent()
}

data class BclawV2UiState(
    val deviceBook: DeviceBook = DeviceBook(),
    val networkAvailable: Boolean = true,
) {
    val hasActiveDevice: Boolean get() = deviceBook.activeDeviceId != null
}

private fun defaultDeviceName(hostApiBaseUrl: String): String {
    val hostPort = hostApiBaseUrl
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("ws://")
        .removePrefix("wss://")
        .trimEnd('/')
    val host = hostPort.substringBefore(':').ifBlank { hostPort }
    return host.substringBefore('.')
}
