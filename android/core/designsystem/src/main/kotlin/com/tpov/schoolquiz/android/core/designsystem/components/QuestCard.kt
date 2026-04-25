package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

/**
 * Quest list card: picture (or placeholder), title, StarRating.
 *
 * pictureUrl null → placeholder icon (QuestionMark).
 * averageRating null → StarRating(null) = outline stars.
 * Min touch target: 48dp (Material3 a11y); Card itself ≥48dp.
 *
 * Color: MaterialTheme.colorScheme.primary — no hardcoded color literals.
 * BrandComponentsInvariantsTest compliance: @Preview in this file.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §3 QuestCard; FR#10, FR#11
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun QuestCard(
    item: QuestDisplayItem,
    onClick: (QuestId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clickable { onClick(item.id) },
    ) {
        Column {
            val safeUrl = item.pictureUrl?.takeIf {
                it.startsWith("https://") && it.contains("firebasestorage.googleapis.com")
            }
            if (safeUrl != null) {
                AsyncImage(
                    model = safeUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QuestionMark,
                    contentDescription = item.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StarRating(rating = item.averageRating)
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QuestCardEmptyPreview() {
    SchoolQuizTheme {
        QuestCard(
            item = QuestDisplayItem(
                id = QuestId("preview-id"),
                title = "Квест без рейтинга",
                pictureUrl = null,
                averageRating = null,
            ),
            onClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QuestCardRatedPreview() {
    SchoolQuizTheme {
        QuestCard(
            item = QuestDisplayItem(
                id = QuestId("preview-id-2"),
                title = "Квест с рейтингом 2.7",
                pictureUrl = null,
                averageRating = 2.7f,
                averageRatingCount = 15,
            ),
            onClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QuestCardUnratedPreview() {
    SchoolQuizTheme {
        QuestCard(
            item = QuestDisplayItem(
                id = QuestId("preview-id-3"),
                title = "Нет оценок",
                pictureUrl = null,
                averageRating = null,
                averageRatingCount = 0,
            ),
            onClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QuestCardLongTitlePreview() {
    SchoolQuizTheme {
        QuestCard(
            item = QuestDisplayItem(
                id = QuestId("preview-id-4"),
                title = "Очень длинное название квеста которое не влезает в одну строку и обрезается многоточием",
                pictureUrl = null,
                averageRating = 1.5f,
                averageRatingCount = 3,
            ),
            onClick = {},
        )
    }
}
