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
