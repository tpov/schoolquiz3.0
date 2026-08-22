package com.tpov.schoolquiz.android.core.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChip
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChipTone
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroupHeader
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirProgressBar
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirRow
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirRowIcon
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSwitch
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType

/**
 * Design System catalog screen.
 * Runtime debug screen — navigated via Destination.OpenDesignCatalog → LocalConfig.DesignCatalogRoot.
 * Spec FR #17: NOT a DrawerSection — footer action target.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun DesignCatalogScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
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

        SectionLabel("BrandCard")
        BrandCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "BrandCard content",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SectionLabel("BrandPrimaryButton")
        BrandPrimaryButton(text = "Primary Action", onClick = {})

        SectionLabel("BrandSecondaryButton")
        BrandSecondaryButton(text = "Secondary Action", onClick = {})

        SectionLabel("BrandProgressBar")
        BrandProgressBar(progress = 0.7f, modifier = Modifier.fillMaxWidth())

        SectionLabel("BrandCircleIconButton")
        BrandCircleIconButton(icon = Icons.Default.Star, contentDescription = "Star", onClick = {})

        SectionLabel("CategoryIcon")
        CategoryIcon(icon = Icons.Default.Home, contentDescription = "Home")

        NoirShowcase()
    }
}

/**
 * NOIR components, shown next to the ones above so the two systems can be compared without
 * launching the whole app. Both are live at once by design: NoirTheme only supplies
 * CompositionLocals, so it nests inside SchoolQuizTheme and nothing already built has to move.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun NoirShowcase() {
    NoirTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "NOIR",
                style = NoirType.appbar,
                color = NoirT1,
            )

            SectionLabel("NoirGroup — hairline instead of a floating card")
            NoirGroup {
                NoirGroupHeader(label = "Настройки")
                NoirRow(
                    leading = { NoirRowIcon(NoirIcons.Sliders) },
                    trailing = { NoirSwitch(checked = true, onCheckedChange = {}) },
                ) {
                    Text("Переключатель, а не галочка", style = NoirType.rowTitle)
                    Text("On/off — это switch", style = NoirType.rowSub)
                }
                NoirRow(
                    showDivider = false,
                    leading = { NoirRowIcon(NoirIcons.Clock) },
                    trailing = { Text("12:30", style = NoirType.num) },
                ) {
                    Text("Числа моноширинные", style = NoirType.rowTitle)
                }
            }

            SectionLabel("NoirButton — mono uppercase, one primary per screen")
            NoirButton(text = "Основное действие", onClick = {})
            NoirButton(text = "Второстепенное", onClick = {}, style = NoirButtonStyle.Ghost)
            NoirButton(text = "Pro", onClick = {}, style = NoirButtonStyle.Gold)
            NoirButton(text = "Недоступно", onClick = {}, enabled = false)

            SectionLabel("NoirChip")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoirChip(text = "Обычный")
                NoirChip(text = "Акцент", tone = NoirChipTone.Accent)
                NoirChip(text = "Pro", tone = NoirChipTone.Gold)
                NoirChip(text = "Ошибка", tone = NoirChipTone.Danger)
            }

            SectionLabel("NoirProgressBar")
            NoirProgressBar(fraction = 0.7f, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DesignCatalogScreenPreview() {
    SchoolQuizTheme {
        DesignCatalogScreen()
    }
}
