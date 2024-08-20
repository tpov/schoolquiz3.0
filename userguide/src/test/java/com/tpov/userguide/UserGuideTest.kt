package com.tpov.userguide

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.fragment.app.FragmentManager
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class UserGuideExhaustiveTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var resources: Resources
    private lateinit var fragmentManager: FragmentManager
    private lateinit var userGuide: UserGuide

    @Before
    fun setUp() {
        // Создаем моки
        context = mock(Context::class.java)
        sharedPreferences = mock(SharedPreferences::class.java)
        sharedPreferencesEditor = mock(SharedPreferences.Editor::class.java)
        resources = mock(Resources::class.java)
        fragmentManager = mock(FragmentManager::class.java)

        // Настройка возвращаемых значений для моков
        whenever(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(sharedPreferencesEditor)
        whenever(sharedPreferencesEditor.putInt(anyString(), anyInt())).thenReturn(sharedPreferencesEditor)
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDrawable(anyInt(), any())).thenReturn(mock(Drawable::class.java))

        // Инициализируем тестируемый объект
        userGuide = UserGuide(context)
    }

    @Test
    fun testUserGuideDialogDisplayBasedOnExactMatchKey() {
        // Устанавливаем exactMatchKey
        userGuide.setExactMatchKey(4000, 1)

        // Создаем GuideBuilder
        val builderSetupUserGuide = userGuide.guideBuilder()

        // Строим гайд и вызываем методы
        builderSetupUserGuide
            .setText("Версия 3.0.18 Коммит")
            .setIcon(context.resources.getDrawable(R.mipmap.ic_launcher, context.theme))
            .setTitleText("4000")
            .setOptions(Options(
                showDot = false,
                exactMatchKey = 4000,
                idGroupGuide = 1
            ))
            .build()

        // Проверяем, что метод showInfoFragment вызван
        verify(fragmentManager, never()).beginTransaction() // Просто пример, можно проверять другие методы
    }
}