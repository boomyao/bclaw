package com.bclaw.app.testing

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.bclaw.app.BclawApplication
import com.bclaw.app.data.ConnectionConfig
import com.bclaw.app.service.BclawForegroundService
import com.bclaw.app.service.BclawRuntime
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ForegroundServiceTeardownRule : TestWatcher() {
    override fun starting(description: Description) {
        resetEnvironment()
    }

    override fun finished(description: Description) {
        resetEnvironment()
    }

    private fun resetEnvironment() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        BclawRuntime.controller.value?.disconnect()
        context.stopService(Intent(context, BclawForegroundService::class.java))
        waitForForegroundServiceTeardown()
        runBlocking {
            (context.applicationContext as BclawApplication)
                .configRepository
                .saveConfig(ConnectionConfig())
        }
        runShell("input keyevent KEYCODE_HOME")
        Thread.sleep(300)
    }

    private fun runShell(command: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .close()
    }

    private fun waitForForegroundServiceTeardown(timeoutMs: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (BclawRuntime.controller.value == null) {
                return
            }
            Thread.sleep(50)
        }
    }
}
