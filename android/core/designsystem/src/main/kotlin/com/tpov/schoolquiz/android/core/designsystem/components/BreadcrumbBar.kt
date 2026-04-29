package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Horizontal breadcrumb navigation bar. Displays a path of titles separated by "›".
 *
 * The last segment is the current location — it is non-clickable and visually
 * distinct (full opacity, bold weight). All preceding segments are clickable
 * navigation targets with reduced opacity.
 *
 * Reused by all 5 drill-down screens (Section / Theme / Lesson / etc.).
 * Accepts List<String> — no feature-specific types (ADR-QS-09).
 *
 * Spec: docs/features/quizzes-screen/06-api-contract.md:677, AC#9 / Matrix 3.
 * BrandComponentsInvariantsTest: @Preview required; all colors via MaterialTheme.colorScheme.*
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun BreadcrumbBar(
    titles: List<String>,
    onSegmentClick: (uiLevel: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (titles.isEmpty()) return

    val scrollState = rememberScrollState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val isLast = index == titles.lastIndex

            if (index > 0) {
                Text(
                    text = " › ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    ),
                color =
                    if (isLast) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = if (isLast) Modifier else Modifier.clickable { onSegmentClick(index) },
            )
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun BreadcrumbBarThreeSegmentsPreview() {
    SchoolQuizTheme {
        BreadcrumbBar(
            titles = listOf("Математика", "Квест 1", "Секция 2"),
            onSegmentClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun BreadcrumbBarSingleSegmentPreview() {
    SchoolQuizTheme {
        BreadcrumbBar(
            titles = listOf("Математика"),
            onSegmentClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun BreadcrumbBarLongTitlesPreview() {
    SchoolQuizTheme {
        BreadcrumbBar(
            titles =
                listOf(
                    "Очень длинный раздел с большим количеством символов",
                    "Тоже длинная тема",
                    "Финальный урок",
                ),
            onSegmentClick = {},
        )
    }
}
