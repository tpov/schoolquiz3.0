package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignBackground
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignCard
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R

/**
 * Generic placeholder for screens not yet implemented.
 * Spec FR #13: one composable for ~14 sections/placeholders.
 * AC 13: subtitle "Скоро здесь будет..." per spec 0-spec.md:767.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun UnderConstructionScreen(
    title: String,
    icon: ImageVector = Icons.Default.Construction,
    modifier: Modifier = Modifier,
) {
    SchoolQuizDesignBackground(
        isHard = false,
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            SchoolQuizDesignCard(
                containerColor = schoolQuizDesignDeepSurfaceColor(),
                borderColor = schoolQuizDesignLightBorderColor(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.under_construction_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming", "UnusedPrivateMember")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun UnderConstructionScreenPreview() {
    SchoolQuizTheme {
        UnderConstructionScreen(title = "Мои квесты")
    }
}
