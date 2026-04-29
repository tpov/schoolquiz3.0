package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun LessonItemCard(
    item: LessonItemUi,
    onClick: () -> Unit,
    onHardCheckChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BrandCard(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .height(64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (item.orderLabel != null) {
                Text(
                    text = item.orderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StarRating(
                rating = item.bestStarsRawTenths / 10f,
                modifier = Modifier.padding(start = 8.dp),
                size = 28.dp,
            )
            if (item.hardUnlocked) {
                Checkbox(
                    checked = item.isHardChecked,
                    onCheckedChange = onHardCheckChanged,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonItemCardBasicPreview() {
    SchoolQuizTheme {
        LessonItemCard(
            item =
                LessonItemUi(
                    id = "l1",
                    title = "Урок 1 — Введение в алгебру",
                    orderLabel = "1.",
                    bestStarsRawTenths = 20,
                    hardUnlocked = false,
                ),
            onClick = {},
            onHardCheckChanged = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonItemCardHardUnlockedPreview() {
    SchoolQuizTheme {
        LessonItemCard(
            item =
                LessonItemUi(
                    id = "l2",
                    title = "Урок 2 — Сложные задачи с длинным названием которое не помещается",
                    orderLabel = "2.",
                    bestStarsRawTenths = 27,
                    hardUnlocked = true,
                    isHardChecked = true,
                ),
            onClick = {},
            onHardCheckChanged = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonItemCardZeroStarsPreview() {
    SchoolQuizTheme {
        LessonItemCard(
            item =
                LessonItemUi(
                    id = "l3",
                    title = "Урок 3 — Новый урок (нет попыток)",
                    bestStarsRawTenths = 0,
                    hardUnlocked = false,
                ),
            onClick = {},
            onHardCheckChanged = {},
        )
    }
}
