---
phase: phase-02
role: test-dev
---

# Phase-02: Test Tasks — Design System Foundation

## Scope

Compile-time verification (основная) + optional instrumented smoke test.

Pure theme тесты (цвета, shapes) — instrumented (`androidTest`) так как `MaterialTheme` требует Compose runtime. Для phase-02 минимальный набор: compile check + lint/detekt.

## 1. Smoke Compile Test

Не является отдельным тест-файлом — проверяется через Gradle:

```bash
./gradlew :android:core:designsystem:compileDebugKotlin
# Expected: BUILD SUCCESSFUL, no unresolved references
```

## 2. No Light Theme Static Check

```bash
# Pattern invariant: lightColorScheme absent
grep -r "lightColorScheme" /home/Programming/Android/schoolquiz4.0/android/core/designsystem/
# Expected: no output
```

## 3. Optional — SchoolQuizThemeTest.kt (instrumented)

**Файл**: `android/core/designsystem/src/androidTest/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizThemeTest.kt`

```kotlin
package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SchoolQuizThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theme_background_is_pure_black() {
        var background: Color = Color.Transparent
        composeTestRule.setContent {
            SchoolQuizTheme {
                background = MaterialTheme.colorScheme.background
            }
        }
        assertEquals(Color(0xFF000000), background)
    }

    @Test
    fun theme_primary_is_google_blue() {
        var primary: Color = Color.Transparent
        composeTestRule.setContent {
            SchoolQuizTheme {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(Color(0xFF4285F4), primary)
    }

    @Test
    fun theme_secondary_is_gold() {
        var secondary: Color = Color.Transparent
        composeTestRule.setContent {
            SchoolQuizTheme {
                secondary = MaterialTheme.colorScheme.secondary
            }
        }
        assertEquals(Color(0xFFFFD700), secondary)
    }
}
```

Run instrumented tests only if emulator available:
```bash
./gradlew :android:core:designsystem:connectedDebugAndroidTest
```

## Validation

```bash
./gradlew :android:core:designsystem:compileDebugKotlin --no-configuration-cache
./gradlew :android:core:designsystem:lint --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
```
