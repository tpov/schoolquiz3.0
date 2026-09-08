@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerRootComponent

/** Decide rotation at the Android boundary before any durable pause; never undo a persisted answer. */
@Composable
internal fun ArenaRunnerLifecycle(component: ArenaRunnerRootComponent) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.runnerActivity()
    DisposableEffect(component, lifecycle, activity) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> component.onArenaForeground()
                    Lifecycle.Event.ON_STOP -> component.onArenaBackground(activity?.isChangingConfigurations == true)
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) component.onArenaForeground()
        onDispose {
            lifecycle.removeObserver(observer)
            component.onArenaBackground(activity?.isChangingConfigurations == true)
        }
    }
}

private fun Context.runnerActivity(): Activity? {
    var current = this
    while (current is ContextWrapper && current !is Activity && current.baseContext !== current) {
        current = current.baseContext
    }
    return current as? Activity
}
