package com.tpov.userguide.presentation

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import com.tpov.log_api.logger.Logger
import com.tpov.userguide.domain.UserGuideUseCase
import java.util.Objects

/**
 * This library is designed to simplify the use of applications for users,
 * Its functionality is based on drawing a point on the element, and displaying a dialog box that slides out from below.
 *
 * The library contains some functions that allow you to use the dialog box as a regular Alert.dialog,
 * calling it in one line, with different parameters
 *
 * ╔═══════════════════════════════╗
 * ║                               ║
 * ║              Title            ║
 * ║                               ║
 * ║   ┌────────┬──────────────┐   ║
 * ║   │        │              │   ║
 * ║   │ *icon* │    *text*    │   ║
 * ║   │        │              │   ║
 * ║   └────────┴──────────────┘   ║
 * ║   ┌────────┬──────────────┐   ║
 * ║   │ *Button│   *Button    │   ║
 * ║   │ open   │     Ok*      │   ║
 * ║   │ video* │              │   ║
 * ║   └────────┴──────────────┘   ║
 * ║                               ║
 * ╚═══════════════════════════════╝
 *
 * @param context context to display the dialog box
 * @param theme Theme for dialog box
 */

class UserGuide(
    private val context: Context,
    private val minGlobalKey: Int? = null,
    private val globalKey: Int? = null,
    private val theme: Drawable? = null
) {
    internal var useCase = UserGuideUseCase(context)

    class GuideBuilder(private val userGuide: UserGuide) {

        var views: List<View?> = listOf()
        var text: String? = null
        var titleText: String? = null
        var icon: Drawable? = null
        var video: String? = null
        private var callback: (() -> Unit)? = null
        private var options: Options = Options()

        fun setViews(vararg views: View?) = apply { this.views = views.toList() }
        fun setTitleText(titleText: String?) = apply { this.titleText = titleText }
        fun setText(text: String) = apply { this.text = text }
        fun setIcon(icon: Drawable?) = apply { this.icon = icon }
        fun setVideo(video: String?) = apply { this.video = video }
        fun setCallback(callback: (() -> Unit)?) = apply { this.callback = callback }
        fun setOptions(options: Options) = apply { this.options = options }

        fun build() = with(options) {
            Log.d("UserGuide", "build()")
            val uniqueId = Objects.hash(text, titleText, video, views.firstOrNull()?.id ?: 0)
            val isCountRepeat = countRepeat > userGuide.useCase.getCountRepeat(uniqueId) || isInfinityCount
            val hasnotParams = minValueKey == null && exactMatchKey == null && isCountRepeat
            val isMinKey = when {
                userGuide.minGlobalKey != null -> minValueKey?.let { it >= userGuide.minGlobalKey } ?: true
                minValueKey != null -> throw IllegalStateException("minGlobalKey must not be null")
                else -> true
            }
            val isKey = when {
                userGuide.globalKey != null -> exactMatchKey?.let { it == userGuide.globalKey } ?: true
                exactMatchKey != null -> throw IllegalStateException("globalKey must not be null")
                else -> true
            }
            val isValidBothKey = !(exactMatchKey != null && minValueKey != null)

            Log.d("UserGuide", "_______________________________")
            Log.d("UserGuide", "${Objects.hash(text)}")
            Log.d("UserGuide", "${Objects.hash(titleText)}")
            Log.d("UserGuide", "${Objects.hash(Objects.hash(video))}")
            Log.d("UserGuide", "Title: $titleText")
            Log.d("UserGuide", "getHashCode(): ${uniqueId}")
            Log.d("UserGuide", "userGuide.useCase.getCountRepeat(getHashCode()): ${userGuide.useCase.getCountRepeat(uniqueId)}")
            Log.d("UserGuide", "isCountRepeat: $isCountRepeat")
            Log.d("UserGuide", "hasnotParams: $hasnotParams")
            Log.d("UserGuide", "isMinKey: $isMinKey")
            Log.d("UserGuide", "isKey: $isKey")
            if (hasnotParams || (isCountRepeat && (isMinKey && isKey && isValidBothKey))) {
                Log.d("UserGuide", "if")
                GuideItem(
                    views, text, titleText, icon, video, options, callback, userGuide.context, uniqueId
                ).init()
            }
        }

    }

    fun guideBuilder(): GuideBuilder = GuideBuilder(this)

}
