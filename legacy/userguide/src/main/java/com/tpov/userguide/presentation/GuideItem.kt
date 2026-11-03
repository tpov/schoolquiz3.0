package com.tpov.userguide.presentation

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.tpov.userguide.domain.UserGuideUseCase
import java.util.Objects

data class GuideItem(
    val views: List<View?>,
    val text: String?,
    val titleText: String?,
    val icon: Drawable?,
    val video: String?,
    val options: Options,
    val callback: (() -> Unit)?,
    val context: Context,
    val uniqueId: Int
) {
    private val useCase = UserGuideUseCase(context)

    fun init() {
        DotView().showDot(
            views = views,
            context = context,
            text = text ?: "",
            titleText = titleText,
            icon = icon,
            video = video,
            theme = null,
            options = options,uniqueId,
            buttonClick = { viewId ->
                useCase.incrementCounterDialogView(uniqueId)
            }
        )
    }
        override fun hashCode(): Int {
            return Objects.hash(titleText, views, video, text)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GuideItem) return false
            return titleText == other.titleText &&
                    views == other.views &&
                    video == other.video &&
                    text == other.text
        }

}
