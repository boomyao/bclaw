package com.bclaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bclaw.app.service.BclawRuntime
import com.bclaw.app.ui.BclawApp
import com.bclaw.app.ui.theme.BclawTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainActivityContent(application as BclawApplication)
        }
    }
}

@Composable
private fun MainActivityContent(application: BclawApplication) {
    val controller by BclawRuntime.controller.collectAsState()
    BclawTheme {
        BclawApp(
            application = application,
            controller = controller,
        )
    }
}
