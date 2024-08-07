package com.tpov.userguide

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.view.View
import com.tpov.userguide.domain.UserGuideUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UserGuideParameterizedTest(
    private val view: View?,
    private val text: String?,
    private val titleText: String?,
    private val icon: Drawable?,
    private val video: String?,
    private val callback: (() -> Unit)?
) {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var useCase: UserGuideUseCase
    private lateinit var userGuide: UserGuide

    @Before
    fun setUp() {
        context = mockk()
        sharedPreferences = mockk(relaxed = true)
        useCase = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences

        userGuide = UserGuide(context)
        userGuide.useCase = useCase
    }

    @Test
    fun `test build with various parameter combinations`() {
        val options = Options().apply {
            idGroupGuide = 1
            exactMatchKey = 42
            minValueKey = 10
            countRepeat = 5
            showWithoutOptions = false
        }

        if (view != null) {
            every { view.id } returns 123
        }

        // Настройка моков для UserGuideUseCase
        every { useCase.getExactMatchKey(options.idGroupGuide) } returns 42
        every { useCase.getMinValueKey(options.idGroupGuide) } returns 15
        every { useCase.getCountRepeat(any()) } returns 3

        // Создание GuideBuilder и вызов build
        userGuide.guideBuilder()
            .setView(view)
            .setTitleText(titleText)
            .setText(text ?: "")
            .setIcon(icon)
            .setVideo(video)
            .setCallback(callback)
            .setOptions(options)
            .build()

        // Определение, должен ли элемент быть добавлен
        val expectedItemAdded = options.showWithoutOptions ||
                (options.exactMatchKey == null || options.exactMatchKey == 42) &&
                (options.minValueKey == null || 15 >= options.minValueKey!!) &&
                (options.countRepeat >= 3)

        // Проверка условий добавления элемента
        if (expectedItemAdded) {
            assert(userGuide.guideItems.size == 1)
            userGuide.guideItems[0].apply {
                assert(this.view == view)
                assert(this.text == text)
                assert(this.titleText == titleText)
                assert(this.icon == icon)
                assert(this.video == video)
                assert(this.callback == callback)
            }
        } else {
            assert(userGuide.guideItems.size == 0)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: Test with view={0}, text={1}, titleText={2}, icon={3}, video={4}, callback={5}")
        fun data(): Collection<Array<Any?>> {
            val views = arrayOf<Any?>(null, mockk<View>())
            val texts = arrayOf<Any?>(null, "Test Text")
            val titleTexts = arrayOf<Any?>(null, "Title Text")
            val icons = arrayOf<Any?>(null, mockk<Drawable>())
            val videos = arrayOf<Any?>(null, "video_url")
            val callbacks = arrayOf<Any?>(null, mockk<() -> Unit>())

            val combinations = mutableListOf<Array<Any?>>()
            for (view in views) {
                for (text in texts) {
                    for (titleText in titleTexts) {
                        for (icon in icons) {
                            for (video in videos) {
                                for (callback in callbacks) {
                                    combinations.add(arrayOf(view, text, titleText, icon, video, callback))
                                }
                            }
                        }
                    }
                }
            }
            return combinations
        }
    }
}