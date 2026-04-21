package com.bclaw.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BclawRuntime {
    private val mutableController = MutableStateFlow<BclawSessionController?>(null)
    val controller: StateFlow<BclawSessionController?> = mutableController.asStateFlow()

    fun install(controller: BclawSessionController) {
        mutableController.value = controller
    }

    fun clear(controller: BclawSessionController) {
        if (mutableController.value === controller) {
            mutableController.value = null
        }
    }
}
