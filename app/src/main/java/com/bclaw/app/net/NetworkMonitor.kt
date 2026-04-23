package com.bclaw.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches device-level network reachability so the controller can notice when the phone
 * lost / regained connectivity (Wi-Fi flip, airplane mode, leaving coverage). Does not say
 * whether a specific WebSocket is open — that still comes from OkHttp callbacks. Used as a
 * fast negative signal (stop pretending we're Connected when the radio is off) and as a
 * trigger to re-dial transports that went Offline while the user wasn't looking.
 *
 * The callback lifecycle follows the process: we never unregister. If you move this into
 * something shorter-lived, call [close] from its teardown.
 */
class NetworkMonitor(context: Context) {
    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _available = MutableStateFlow(initialAvailable())
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = cm.getNetworkCapabilities(network)
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                _available.value = true
            }
        }

        override fun onLost(network: Network) {
            // Only flip to false if the OS has no remaining internet-capable network.
            if (!hasInternetRoute()) _available.value = false
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                _available.value = true
            }
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    fun close() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private fun initialAvailable(): Boolean = hasInternetRoute()

    private fun hasInternetRoute(): Boolean {
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
