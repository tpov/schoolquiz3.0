package com.tpov.userguide

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.fragment.app.FragmentManager
import com.tpov.userguide.domain.UserGuideUseCase

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

class UserGuide(private val context: Context, private val theme: Drawable? = null) {
    internal val guideItems: MutableList<GuideItem> = mutableListOf()
    internal var useCase = UserGuideUseCase(context)

    class GuideBuilder(private val userGuide: UserGuide) {
        private var view: View? = null
        private var text: String? = null
        private var titleText: String? = null
        private var icon: Drawable? = null
        private var video: String? = null
        private var callback: (() -> Unit)? = null
        private var options: Options = Options()

        fun setView(view: View?) = apply { this.view = view }
        fun setTitleText(titleText: String?) = apply { this.titleText = titleText }
        fun setText(text: String) = apply { this.text = text }
        fun setIcon(icon: Drawable?) = apply { this.icon = icon }
        fun setVideo(video: String?) = apply { this.video = video }
        fun setCallback(callback: (() -> Unit)?) = apply { this.callback = callback }
        fun setOptions(options: Options) = apply { this.options = options }

        fun build() {
            if (options.showWithoutOptions
                || ((options.exactMatchKey == null || userGuide.useCase.getExactMatchKey(options.idGroupGuide) == options.exactMatchKey)
                        && (options.minValueKey == null || userGuide.useCase.getMinValueKey(options.idGroupGuide)!! >= options.minValueKey!!)
                        && (options.countRepeat >= userGuide.useCase.getCountRepeat(
                    view?.id ?: titleText?.hashCode() ?: text?.hashCode() ?: options.idGroupGuide)))) {

                val guideItem = GuideItem(
                    view, text, titleText, icon, video, options, callback, userGuide.context
                )

                if (!userGuide.guideItems.any { it.view == view }) {
                    userGuide.guideItems.add(guideItem)
                }

            }
        }

        fun show() { userGuide.showAllDots() }
    }

    fun setExactMatchKey(value: Int, idGroupGuide: Int = Options().idGroupGuide) {
        useCase.setExactMatchKey(value, idGroupGuide)
    }

    fun setMinValueKey(value: Int, idGroupGuide: Int = Options().idGroupGuide) {
        useCase.setMinValueKey(value, idGroupGuide)
    }

    fun guideBuilder(): GuideBuilder = GuideBuilder(this)

    private fun showAllDots() {
        guideItems.forEach { it.showDot() }
    }

    fun showInfoFragment(text: String, fragmentManager: FragmentManager) {
        val fragment = InfoFragment.newInstance(guideItems.toList())
        fragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }
}