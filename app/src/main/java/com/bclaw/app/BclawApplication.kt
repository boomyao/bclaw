package com.bclaw.app

import android.app.Application
import com.bclaw.app.data.DeviceBookRepository
import com.bclaw.app.data.NotificationPermissionRepository
import com.bclaw.app.data.TabBookRepository
import com.bclaw.app.data.TimelineCacheRepository
import com.bclaw.app.net.NetworkMonitor
import com.bclaw.app.service.BclawV2Controller

/**
 * bclaw v2 Application class — owns process-lifetime singletons.
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
    lateinit var tabBookRepository: TabBookRepository
        private set
    lateinit var timelineCacheRepository: TimelineCacheRepository
        private set
    lateinit var notificationPermissionRepository: NotificationPermissionRepository
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var controller: BclawV2Controller
        private set

    override fun onCreate() {
        super.onCreate()
        deviceBookRepository = DeviceBookRepository(this)
        tabBookRepository = TabBookRepository(this)
        timelineCacheRepository = TimelineCacheRepository(this)
        notificationPermissionRepository = NotificationPermissionRepository(this)
        networkMonitor = NetworkMonitor(this)
        controller = BclawV2Controller(
            deviceBookRepository = deviceBookRepository,
            tabBookRepository = tabBookRepository,
            timelineCacheRepository = timelineCacheRepository,
            networkAvailableFlow = networkMonitor.available,
        )
    }
}
