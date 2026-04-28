package com.bclaw.app

import android.app.Application
import com.bclaw.app.data.DeviceBookRepository
import com.bclaw.app.net.NetworkMonitor
import com.bclaw.app.service.BclawV2Controller

/**
 * bclaw Application class — owns process-lifetime singletons.
 *
 * Controller is hosted here (not in [MainActivity]) so it survives:
 *   - Activity config changes (rotate, fold)
 *   - Activity destruction while the foreground service pins the process
 *
 * The service (see [com.bclaw.app.service.BclawForegroundService]) starts when any
 * device becomes active and stops when all are removed.
 */
class BclawApplication : Application() {
    lateinit var deviceBookRepository: DeviceBookRepository
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var controller: BclawV2Controller
        private set

    override fun onCreate() {
        super.onCreate()
        deviceBookRepository = DeviceBookRepository(this)
        networkMonitor = NetworkMonitor(this)
        controller = BclawV2Controller(
            deviceBookRepository = deviceBookRepository,
            networkAvailableFlow = networkMonitor.available,
        )
    }
}
