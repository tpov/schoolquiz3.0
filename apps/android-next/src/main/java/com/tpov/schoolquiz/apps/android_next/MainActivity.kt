package com.tpov.schoolquiz.apps.android_next

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.arkivanov.decompose.defaultComponentContext
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignStyle
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.AppShellScreen
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf

private const val DESIGN_PREFS_NAME = "schoolquiz_design"
private const val DESIGN_STYLE_KEY = "selected_design_style"

class MainActivity : AppCompatActivity() {
    private lateinit var rootComponent: DefaultRootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootComponent = get { parametersOf(defaultComponentContext()) }

        setContent {
            val preferences = remember { getSharedPreferences(DESIGN_PREFS_NAME, MODE_PRIVATE) }
            var selectedDesignStyle by remember {
                mutableStateOf(
                    preferences.getString(DESIGN_STYLE_KEY, null).toSchoolQuizDesignStyle(),
                )
            }

            SchoolQuizTheme(designStyle = selectedDesignStyle) {
                AppShellScreen(
                    rootComponent = rootComponent,
                    appVersionName = BuildConfig.VERSION_NAME,
                    isDebugBuild = BuildConfig.DEBUG,
                    selectedDesignStyle = selectedDesignStyle,
                    onDesignStyleSelected = { style ->
                        selectedDesignStyle = style
                        preferences.edit().putString(DESIGN_STYLE_KEY, style.name).apply()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.dataString ?: return
        if (!uri.startsWith("schoolquiz://")) return
        rootComponent.onDeepLink(DeepLink(uri))
    }
}

private fun String?.toSchoolQuizDesignStyle(): SchoolQuizDesignStyle =
    when (this) {
        "MainLegacy" -> SchoolQuizDesignStyle.Main
        else ->
            this
                ?.let { value -> runCatching { SchoolQuizDesignStyle.valueOf(value) }.getOrNull() }
                ?: SchoolQuizDesignStyle.Main
    }
