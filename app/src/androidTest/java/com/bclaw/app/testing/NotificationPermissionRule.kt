package com.bclaw.app.testing

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.bclaw.app.data.NotificationPermissionRepository
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class NotificationPermissionRule(
    private val grantBeforeTest: Boolean = true,
    private val resetPromptState: Boolean = false,
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (resetPromptState) {
                        runBlocking {
                            NotificationPermissionRepository(
                                InstrumentationRegistry.getInstrumentation().targetContext,
                            ).resetForTesting()
                        }
                    }
                    if (grantBeforeTest) {
                        runShell("pm grant com.bclaw.app android.permission.POST_NOTIFICATIONS")
                        runShell("appops set com.bclaw.app POST_NOTIFICATION allow")
                    } else {
                        runShell("pm revoke com.bclaw.app android.permission.POST_NOTIFICATIONS")
                        runShell("appops set com.bclaw.app POST_NOTIFICATION default")
                    }
                }
                base.evaluate()
            }
        }
    }

    private fun runShell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { stream ->
            while (stream.read() != -1) {
                // Drain shell output so the command can complete cleanly.
            }
        }
    }
}
