---
phase: phase-03
role: frontend-dev
---

# Phase-03: Frontend Tasks — DS Wrappers + DesignCatalogScreen

## 1. BrandCard.kt

```kotlin
package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * SchoolQuiz branded card.
 * Flat design: elevation = 0dp, 1dp outline stroke per ADR-0010.
 */
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrandCardPreview() {
    SchoolQuizTheme {
        BrandCard { /* preview content */ }
    }
}
```

## 2. BrandPrimaryButton.kt

```kotlin
package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Primary CTA button — Google Blue fill per ADR-0010.
 */
@Composable
fun BrandPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Preview
@Composable
private fun BrandPrimaryButtonPreview() {
    SchoolQuizTheme {
        BrandPrimaryButton(text = "Primary Action", onClick = {})
    }
}
```

## 3. BrandSecondaryButton.kt

```kotlin
package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Secondary / outlined button per ADR-0010.
 */
@Composable
fun BrandSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Preview
@Composable
private fun BrandSecondaryButtonPreview() {
    SchoolQuizTheme {
        BrandSecondaryButton(text = "Secondary Action", onClick = {})
    }
}
```

## 4. BrandProgressBar.kt

```kotlin
package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Horizontal progress bar using brand secondary color (gold) by default.
 * Used for streak bar in DrawerHeader (0..10 segments = 0f..1f progress).
 *
 * H2 fix: added `color` param per 06-api-contract.md:409.
 */
@Composable
fun BrandProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .fillMaxHeight()
                .background(color),  // H2 fix: uses color param instead of hardcoded secondary
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrandProgressBarPreview() {
    SchoolQuizTheme {
        BrandProgressBar(progress = 0.7f)
    }
}
```

## 5. BrandCircleIconButton.kt

```kotlin
package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Circular icon button — used in stats row, category grids.
 * H2 fix: simplified signature per 06-api-contract.md:410.
 * Signature: BrandCircleIconButton(icon, contentDescription, onClick, modifier).
 * Added 1dp stroke (outline) as required by api-contract.
 * `size` removed from public signature — internal constant 48.dp per design spec.
 */
@Composable
fun BrandCircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrandCircleIconButtonPreview() {
    SchoolQuizTheme {
        BrandCircleIconButton(icon = Icons.Default.Star, contentDescription = null, onClick = {})
    }
}
```

## 6. CategoryIcon.kt

```kotlin
package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Icon with configurable tint — used for drawer section / category icons.
 * H2 fix: added `tint` param per 06-api-contract.md:411. Defaults to brand primary.
 * Signature: CategoryIcon(icon, tint: Color, modifier) per api-contract.
 */
@Composable
fun CategoryIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CategoryIconPreview() {
    SchoolQuizTheme {
        CategoryIcon(icon = Icons.Default.Category, contentDescription = "Preview")
    }
}
```

## 7. DesignCatalogScreen.kt

**Файл**: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/catalog/DesignCatalogScreen.kt`

```kotlin
package com.tpov.schoolquiz.android.core.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCircleIconButton
import com.tpov.schoolquiz.android.core.designsystem.components.BrandPrimaryButton
import com.tpov.schoolquiz.android.core.designsystem.components.BrandProgressBar
import com.tpov.schoolquiz.android.core.designsystem.components.BrandSecondaryButton
import com.tpov.schoolquiz.android.core.designsystem.components.CategoryIcon

/**
 * Design System catalog screen.
 * Runtime debug screen — navigated via Destination.OpenDesignCatalog → LocalConfig.DesignCatalogRoot.
 *
 * Spec FR #17: this is NOT a DrawerSection — it is a footer action target.
 * UI guard: rendered only via LocalConfig.DesignCatalogRoot, filtered to debug builds in AppShellScreen.
 */
@Composable
fun DesignCatalogScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Design Catalog",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text("BrandCard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        BrandCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "BrandCard content",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text("BrandPrimaryButton", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        BrandPrimaryButton(text = "Primary Action", onClick = {})

        Text("BrandSecondaryButton", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        BrandSecondaryButton(text = "Secondary Action", onClick = {})

        Text("BrandProgressBar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        BrandProgressBar(progress = 0.7f, modifier = Modifier.fillMaxWidth())

        Text("BrandCircleIconButton", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        BrandCircleIconButton(icon = Icons.Default.Star, contentDescription = "Star", onClick = {})

        Text("CategoryIcon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        CategoryIcon(icon = Icons.Default.Home, contentDescription = "Home")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DesignCatalogScreenPreview() {
    SchoolQuizTheme {
        DesignCatalogScreen()
    }
}
```

### Pattern Invariants

1. **No hardcoded colors**: Все цвета через `MaterialTheme.colorScheme.*` — нет `Color(0xFF...)` в компонентах (цвета только в `Color.kt`).
2. **elevation = 0dp**: Все `Card`, `Button` используют `elevation = 0dp` или `CardDefaults.cardElevation(0.dp)`. `NavigationBar` — исключение (tonal elevation Material3).
3. **@Preview for all**: Каждый публичный composable имеет `private @Preview`. Catalog screen видна в IDE без запуска.
4. **DesignCatalogScreen не DrawerSection**: `DesignCatalogScreen` — отдельный Composable, не элемент drawer. Route activation через `LocalConfig.DesignCatalogRoot` в phase-05.
