package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.backhandler.BackHandler

/**
 * Test-only BackHandler that records registered BackCallbacks for inspection.
 * Used for QZ-U-04: verify priority == 100 on the registered backCallback.
 */
class FakeBackDispatcher : BackHandler {

    private val _callbacks = mutableListOf<BackCallback>()
    val callbacks: List<BackCallback> get() = _callbacks.toList()

    override fun register(callback: BackCallback) {
        _callbacks += callback
    }

    override fun unregister(callback: BackCallback) {
        _callbacks -= callback
    }

    override fun isRegistered(callback: BackCallback): Boolean = _callbacks.contains(callback)
}
