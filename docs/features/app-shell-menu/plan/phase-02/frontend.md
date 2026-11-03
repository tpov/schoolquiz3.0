---
phase: phase-02
role: frontend-dev
---

# Phase-02: Frontend Tasks — Design System Foundation

## 1. android/core/designsystem/build.gradle.kts

Полная замена:
```kotlin
plugins {
    id("schoolquiz.android.compose.library")  // созданный в phase-01
}

android {
    namespace = "com.tpov.schoolquiz.android.core.designsystem"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.bundles.compose.ui)
    api(libs.compose.material.icons.extended)
    api(libs.bundles.compose.ui.tooling)
    implementation(libs.bundles.androidx.ui.base)
}
```

`api` вместо `implementation` для Compose BOM — потому что consumer-модули (presentation) наследуют BOM version management. Это стандартный паттерн для design system модулей.

## 2. Color.kt

**Файл**: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Color.kt`

```kotlin
package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.ui.graphics.Color

// ---- Brand palette (ADR-0010) ----

/** Pure black background — ADR-0010 */
val Black = Color(0xFF000000)

/** Google Blue — primary brand color, ADR-0010 */
val GoogleBlue = Color(0xFF4285F4)

/** Gold / Yellow — secondary brand color, ADR-0010 */
val BrandGold = Color(0xFFFFD700)

/** Purple — tertiary color, ADR-0010 */
val BrandPurple = Color(0xFF7D4FAB)

/** Surface — slightly elevated from pure black, 1dp stroke baseline.
 *  Value: #242429 per spec AC 14 (0-spec.md:769) and 06-api-contract.md. */
val DarkSurface = Color(0xFF242429)

/** On-primary (text/icon on GoogleBlue) */
val OnPrimary = Color(0xFFFFFFFF)

/** On-secondary (text/icon on BrandGold) */
val OnSecondary = Color(0xFF000000)

/** On-background (primary text on Black) */
val OnBackground = Color(0xFFE0E0E0)

/** Outline / stroke color — 1dp borders per ADR-0010 flat design */
val OutlineColor = Color(0xFF2C2C2C)
```

## 3. Shape.kt

**Файл**: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Shape.kt`

```kotlin
package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SchoolQuiz shape system (ADR-0010 flat design with rounded corners).
 * Values per 06-api-contract.md:392-394:
 *   extraSmall=4dp, small=8dp, medium=12dp, large=16dp, extraLarge=24dp
 *
 * Note: phase-02/overview.md AC 5 references small=4/medium=8/large=12 — those were wrong.
 * Correct values from api-contract take precedence (H1 fix).
 */
val SchoolQuizShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

## 4. Type.kt

**Файл**: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Type.kt`

```kotlin
package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.material3.Typography

/**
 * SchoolQuiz typography — Material3 defaults.
 * Custom font integration deferred to future phase.
 * Spec: Material3 defaults per user decision Q (typography not customized in MVP).
 */
val SchoolQuizTypography = Typography()
```

## 5. SchoolQuizTheme.kt

**Файл**: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizTheme.kt`

```kotlin
package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * SchoolQuiz design system theme.
 *
 * Dark-only: light colorScheme not implemented per ADR-0010 + spec NFR #4.
 * Flat design: elevation = 0dp default (NavigationBar uses tonal elevation only).
 *
 * @param darkTheme Forced true for now. Parameter preserved for future light theme support.
 * @param content App content wrapped in MaterialTheme.
 */
@Composable
fun SchoolQuizTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = schoolQuizDarkColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SchoolQuizShapes,
        typography = SchoolQuizTypography,
        content = content,
    )
}

/**
 * SchoolQuiz dark color scheme per ADR-0010.
 * #000000 background, #4285F4 primary, #FFD700 secondary, #7D4FAB tertiary.
 * Elevation = 0dp default → flat design with 1dp outline strokes.
 */
fun schoolQuizDarkColorScheme() = darkColorScheme(
    primary = GoogleBlue,
    onPrimary = OnPrimary,
    secondary = BrandGold,
    onSecondary = OnSecondary,
    tertiary = BrandPurple,
    background = Black,
    onBackground = OnBackground,
    surface = DarkSurface,
    onSurface = OnBackground,
    outline = OutlineColor,
)
```

### Pattern Invariants

1. **Dark-only**: `lightColorScheme` НЕ создаётся, НЕ экспортируется. Если будет нужна light theme — это отдельный ADR.
2. **elevation = 0 по умолчанию**: Компоненты этого модуля не устанавливают `elevation` > 0dp явно. `NavigationBar` использует tonal elevation Material3 — это исключение из spec NFR #5.
3. **api vs implementation**: Compose BOM и core compose libs объявляются через `api(...)` чтобы consumer-modules (presentation) автоматически получали версии из BOM.
4. **Compose BOM usage**: После `api(platform(libs.compose.bom))` остальные Compose deps не указывают версию (следуют BOM). Не добавлять явные версии для Compose artifacts.
