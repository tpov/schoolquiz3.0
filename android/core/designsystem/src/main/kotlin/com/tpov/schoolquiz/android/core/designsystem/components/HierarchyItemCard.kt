package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.R
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.currentSchoolQuizDesignStyle
import java.util.Locale

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
    ratingTint: Color? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    modifier: Modifier = Modifier,
    downloadStatus: HierarchyDownloadStatus = HierarchyDownloadStatus.Hidden,
    onDownloadClick: (() -> Unit)? = null,
) {
    val designStyle = currentSchoolQuizDesignStyle()
    val isClean = designStyle == SchoolQuizDesignStyle.Clean
    val actualRatingTint = ratingTint ?: MaterialTheme.colorScheme.primary
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
                    .height(if (isClean) 72.dp else 64.dp)
                    .padding(
                        horizontal = if (isClean) 16.dp else 12.dp,
                        vertical = if (isClean) 12.dp else 8.dp,
                    ),
        ) {
            if (orderLabel != null) {
                Text(
                    text = orderLabel,
                    style = if (isClean) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isClean) 0.5f else 0.62f),
                    modifier = Modifier.padding(end = if (isClean) 10.dp else 8.dp),
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
            hierarchyDownloadButton(
                status = downloadStatus,
                onClick = onDownloadClick,
                modifier = Modifier.padding(start = 8.dp),
            )
            StarRating(
                rating = rating,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .semantics { contentDescription = "rating" },
                size = if (isClean) 24.dp else 28.dp,
                tint = actualRatingTint,
            )
            if (rating != null) {
                Text(
                    text = String.format(Locale.US, "%.1f", rating),
                    style = MaterialTheme.typography.labelSmall,
                    color = actualRatingTint,
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

enum class HierarchyDownloadStatus {
    Hidden,
    Available,
    Downloading,
    Complete,
}

@Composable
private fun hierarchyDownloadButton(
    status: HierarchyDownloadStatus,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (status == HierarchyDownloadStatus.Hidden) return

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            HierarchyDownloadStatus.Hidden -> Unit
            HierarchyDownloadStatus.Downloading ->
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            HierarchyDownloadStatus.Available,
            HierarchyDownloadStatus.Complete,
            ->
                IconButton(
                    enabled = status == HierarchyDownloadStatus.Available && onClick != null,
                    onClick = { onClick?.invoke() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector =
                            if (status == HierarchyDownloadStatus.Complete) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.FileDownload
                            },
                        contentDescription =
                            stringResource(
                                if (status == HierarchyDownloadStatus.Complete) {
                                    R.string.ds_hierarchy_downloaded
                                } else {
                                    R.string.ds_hierarchy_download
                                },
                            ),
                        tint =
                            if (status == HierarchyDownloadStatus.Complete) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        modifier = Modifier.size(22.dp),
                    )
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
            downloadStatus = HierarchyDownloadStatus.Available,
            onDownloadClick = {},
            onClick = {},
        )
    }
}
