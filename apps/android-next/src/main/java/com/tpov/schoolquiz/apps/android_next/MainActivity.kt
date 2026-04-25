package com.tpov.schoolquiz.apps.android_next

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.arkivanov.decompose.defaultComponentContext
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.AppShellScreen
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf

class MainActivity : AppCompatActivity() {
    private lateinit var rootComponent: DefaultRootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootComponent = get { parametersOf(defaultComponentContext()) }

        setContent {
            SchoolQuizTheme {
                AppShellScreen(
                    rootComponent = rootComponent,
                    appVersionName = BuildConfig.VERSION_NAME,
                    isDebugBuild = BuildConfig.DEBUG,
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
