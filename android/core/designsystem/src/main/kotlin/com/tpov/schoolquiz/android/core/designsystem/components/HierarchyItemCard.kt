package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.R
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType

/**
 * Generic card for hierarchy levels: Section / Theme / Lesson.
 *
 * Accepts only primitives to comply with ADR-QS-09 (designsystem must not
 * import feature-specific types). Callers map their domain models before
 * passing data here.
 *
 * NOIR draws the row as the lesson list does: a 64dp plate on surface 1 with a hairline edge and
 * rounded corners — the same plate for every level of the hierarchy.
 *
 * Spec: docs/features/quizzes-screen/06-api-contract.md:677
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun HierarchyItemCard(
    title: String,
    orderLabel: String? = null,
    subtitleCount: String? = null,
    rating: Float? = null,
    ratingTint: Color? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    modifier: Modifier = Modifier,
    downloadStatus: HierarchyDownloadStatus = HierarchyDownloadStatus.Hidden,
    onDownloadClick: (() -> Unit)? = null,
) {
    val actualRatingTint = ratingTint ?: NoirGold
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

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(HIERARCHY_ROW_HEIGHT)
                .clip(NoirShapeLg)
                .background(NoirS1)
                .border(1.dp, NoirGlassStroke, NoirShapeLg)
                .then(clickModifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (orderLabel != null) {
            Text(
                text = orderLabel,
                style = NoirType.num.copy(fontSize = 12.sp, color = NoirT3),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = title,
            style = NoirType.rowTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (subtitleCount != null) {
            Text(
                text = subtitleCount,
                style = NoirType.kicker,
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
            size = 14.dp,
            tint = actualRatingTint,
        )
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
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            HierarchyDownloadStatus.Hidden -> Unit
            HierarchyDownloadStatus.Downloading ->
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            HierarchyDownloadStatus.Available,
            HierarchyDownloadStatus.Complete,
            ->
                IconButton(
                    enabled = status == HierarchyDownloadStatus.Available && onClick != null,
                    onClick = { onClick?.invoke() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector =
                            if (status == HierarchyDownloadStatus.Complete) {
                                NoirIcons.Check
                            } else {
                                NoirIcons.Download
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
                                NoirSuccess
                            } else {
                                LocalNoirAccent.current
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
        }
    }
}

private val HIERARCHY_ROW_HEIGHT = 64.dp

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
            downloadStatus = HierarchyDownloadStatus.Available,
            onDownloadClick = {},
            onClick = {},
        )
    }
}
