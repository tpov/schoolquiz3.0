---
phase: phase-07
role: backend-dev
---

# Phase-07: Backend Tasks — MainActivity Wiring

## 1. Verify AppApplication.kt (phase-01 created)

Убедиться что `AppApplication.kt` содержит все 3 модуля:

```kotlin
startKoin {
    androidContext(this@AppApplication)
    modules(
        firebaseModule,
        appShellDataModule,
        appShellPresentationModule,   // added in phase-04
    )
}
```

Если `appShellPresentationModule` ещё не добавлен — добавить.

## 2. MainActivity.kt — полная реализация

**Файл**: `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt`

```kotlin
package com.tpov.schoolquiz.apps.android_next

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arkivanov.decompose.defaultComponentContext
// import: com.arkivanov.decompose.defaultComponentContext
// This extension auto-wires Activity.onBackPressedDispatcher to Essenty BackHandler.
// OQ-COMP-3 resolved: do NOT use DefaultComponentContext(lifecycle, stateKeeper) manually.
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.AppShellScreen
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf

/**
 * Single Activity entry point.
 *
 * Lifecycle rules per .claude/rules/lifecycle.md:
 * - onDestroy: NO business actions (moveTaskToBack, endComponent, kill signals)
 * - Business lifecycle delegated to RootComponent (Essenty lifecycle binding)
 * - RootEvent.SystemBack collected via repeatOnLifecycle(STARTED) — not in onCreate directly
 *
 * OQ-COMP-2 DEFERRED: essentyLifecycle() extension not needed when using defaultComponentContext().
 * OQ-COMP-3 RESOLVED: use defaultComponentContext() extension.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rootComponent: DefaultRootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Decompose ComponentContext — automatically wires Activity lifecycle + BackDispatcher
        val componentContext = defaultComponentContext()

        // RootComponent via Koin factory (parametersOf passes ComponentContext)
        rootComponent = get { parametersOf(componentContext) }

        // Collect domain→UI events lifecycle-aware: stops collecting when STOPPED
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rootComponent.events.collect { event ->
                    when (event) {
                        RootEvent.SystemBack -> moveTaskToBack(true)
                    }
                }
            }
        }

        setContent {
            SchoolQuizTheme {
                // H8 fix: appVersionName passed from app layer (BuildConfig.VERSION_NAME is app-module-only).
                // AppShellScreen signature: fun AppShellScreen(rootComponent, appVersionName: String)
                AppShellScreen(
                    rootComponent = rootComponent,
                    appVersionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Deep link hook — MVP stub (no URL patterns registered)
        val uri = intent?.dataString ?: return
        rootComponent.onDeepLink(DeepLink(uri))
    }

    override fun onDestroy() {
        super.onDestroy()
        // NOTHING business here — RootComponent lifecycle managed by Essenty (ComponentContext)
        // per .claude/rules/lifecycle.md: onDestroy = instance cleanup only
        // binding = null would go here if view binding used
    }
}
```

### Pattern Invariants

1. **onDestroy: no business actions** — per `.claude/rules/lifecycle.md`. `moveTaskToBack` NOT in onDestroy. `RootEvent.SystemBack` collected в `lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }` только пока Activity STARTED.

2. **defaultComponentContext() import**: `com.arkivanov.decompose.defaultComponentContext` — Extension function, не class. Обеспечивает связь `Activity.onBackPressedDispatcher` ↔ Essenty BackHandler. Не заменять на `DefaultComponentContext(lifecycle, stateKeeper)`.

3. **repeatOnLifecycle(STARTED)**: Flow collection через `repeatOnLifecycle` — stops when Activity goes to STOPPED (home press), resumes on STARTED. Правильный Android lifecycle pattern. Не использовать `lifecycleScope.launch { events.collect {} }` без `repeatOnLifecycle` — это collect без lifecycle awareness.

4. **Koin get { parametersOf(ctx) }**: `DefaultRootComponent` объявлен как Koin `factory` с `ComponentContext` параметром. Без `parametersOf(componentContext)` — `MissingPropertyException`. Именно так, не `get<DefaultRootComponent>()`.
