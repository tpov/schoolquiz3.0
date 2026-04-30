package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Generic card for hierarchy levels: Section / Theme / Lesson.
 *
 * Accepts only primitives to comply with ADR-QS-09 (designsystem must not
 * import feature-specific types). Callers map their domain models before
 * passing data here.
 *
 * Spec: docs/features/quizzes-screen/06-api-contract.md:677
 * BrandComponentsInvariantsTest: @Preview required; all colors via MaterialTheme.colorScheme.*
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun HierarchyItemCard(
    title: String,
    orderLabel: String? = null,
    subtitleCount: String? = null,
    rating: Float? = null,
    ratingCount: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val clickModifier =
        if (onLongClick != null) {
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            )
        } else {
            Modifier.clickable(onClick = onClick)
        }

    BrandCard(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickModifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .height(64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (orderLabel != null) {
                Text(
                    text = orderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (subtitleCount != null) {
                Text(
                    text = subtitleCount,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .wrapContentHeight(),
                )
            }
            StarRating(
                rating = rating,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .semantics { contentDescription = "rating" },
                size = 28.dp,
            )
            if (rating != null) {
                Text(
                    text = "%.1f".format(rating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp),
                )
                if (ratingCount != null && ratingCount > 0) {
                    Text(
                        text = "($ratingCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun HierarchyItemCardBasicPreview() {
    SchoolQuizTheme {
        HierarchyItemCard(
            title = "Алгебра — основы",
            orderLabel = "1.",
            subtitleCount = "5 тем",
            onClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun HierarchyItemCardMinimalPreview() {
    SchoolQuizTheme {
        HierarchyItemCard(
            title = "Тема без нумерации и счётчика",
            onClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun HierarchyItemCardLongTitlePreview() {
    SchoolQuizTheme {
        HierarchyItemCard(
            title = "Очень длинное название раздела которое не помещается в одну строку и должно обрезаться",
            orderLabel = "3.",
            onClick = {},
            onLongClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun HierarchyItemCardWithRatingPreview() {
    SchoolQuizTheme {
        HierarchyItemCard(
            title = "Алгебра — основы",
            orderLabel = "1.",
            rating = 2.5f,
            ratingCount = 42,
            onClick = {},
        )
    }
}
