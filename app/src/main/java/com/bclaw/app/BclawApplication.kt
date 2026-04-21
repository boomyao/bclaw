package com.bclaw.app

import android.app.Application
import com.bclaw.app.data.ConnectionConfigRepository
import com.bclaw.app.data.NotificationPermissionRepository

class BclawApplication : Application() {
    lateinit var configRepository: ConnectionConfigRepository
        private set
    lateinit var notificationPermissionRepository: NotificationPermissionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        configRepository = ConnectionConfigRepository(this)
        notificationPermissionRepository = NotificationPermissionRepository(this)
    }
}
