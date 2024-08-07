package com.tpov.userguide

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.tpov.userguide.domain.UserGuideUseCase

data class GuideItem(
    val view: View?,
    val text: String?,
    val titleText: String?,
    val icon: Drawable?,
    val video: String?,
    val options: Options,
    val callback: (() -> Unit)?,
    val context: Context
) {
    private val useCase = UserGuideUseCase(context)

    fun showDot() {
        DotView().showDot(
            item = view,
            context = context,
            text = text ?: "",
            titleText = titleText,
            icon = icon,
            video = video,
            theme = null,
            options = options,
            buttonClick = { viewId ->
                useCase.incrementCounterDialogView(viewId)
            }
        )
    }
}
