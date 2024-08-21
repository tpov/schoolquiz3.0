package com.tpov.userguide

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.fragment.app.FragmentManager
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class UserGuideExhaustiveTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var resources: Resources
    private lateinit var fragmentManager: FragmentManager
    private lateinit var userGuide: UserGuide
    private lateinit var guideBuilder: UserGuide.GuideBuilder

    @Before
    fun setUp() {
        context = mockk()
        sharedPreferences = mockk()
        sharedPreferencesEditor = mockk()
        resources = mockk()
        fragmentManager = mockk()
        userGuide = mockk()
        guideBuilder = mockk(relaxed = true) // Relaxed mock returns default values for all functions

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putInt(any(), any()) } returns sharedPreferencesEditor
        every { context.resources } returns resources
        every { resources.getDrawable(any(), any()) } returns mockk<Drawable>()
        every { userGuide.guideBuilder() } returns guideBuilder
    }

    @Test
    fun testUserGuideDialogDisplayBasedOnExactMatchKey() {
        every { guideBuilder.setText("Версия 3.0.18 Коммит") } returns guideBuilder
        every { guideBuilder.setOptions(any()) } returns guideBuilder
        every { guideBuilder.build() } returns Unit
        every { guideBuilder.show() } just Runs

        guideBuilder.setText("Версия 3.0.18 Коммит")
            .setOptions(Options(showDot = false, exactMatchKey = 4000, idGroupGuide = 1))
            .build()
        guideBuilder.show()

        // Проверка вызовов
        verify { guideBuilder.setText("Версия 3.0.18 Коммит") }
        verify { guideBuilder.setOptions(Options(showDot = false, exactMatchKey = 4000, idGroupGuide = 1)) }
        verify { guideBuilder.show() }

        // Подтверждение, что больше не было взаимодействий
        confirmVerified(guideBuilder)
    }

}
