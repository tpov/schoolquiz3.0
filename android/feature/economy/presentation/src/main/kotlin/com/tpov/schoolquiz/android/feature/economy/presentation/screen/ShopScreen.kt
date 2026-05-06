@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.economy.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopComponent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewEvent
import com.tpov.schoolquiz.android.feature.economy.presentation.view.ShopView
import kotlinx.coroutines.delay

private const val MESSAGE_AUTO_DISMISS_MS = 3_000L

@Composable
fun ShopScreen(
    component: ShopComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    ShopView(
        state = state,
        onEvent = component::obtainEvent,
        modifier = modifier,
    )
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_AUTO_DISMISS_MS)
            component.obtainEvent(ShopViewEvent.MessageShown)
        }
    }
}
