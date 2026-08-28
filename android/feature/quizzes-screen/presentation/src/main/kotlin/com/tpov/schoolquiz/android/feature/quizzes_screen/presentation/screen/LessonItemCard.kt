package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChip
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChipTone
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi

/**
 * One lesson: its number, its title, the mode it will be played in, and how it was rated.
 *
 * A 64dp row rather than a card. A list of twenty cards on black is twenty floating rectangles;
 * hairline rows read as one list, which is what it is.
 *
 * The mode chip is the only thing here that is not a label — tapping it flips the next run between
 * easy and hard, and it only appears once hard has been unlocked.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun LessonItemCard(
    item: LessonItemUi,
    onClick: () -> Unit,
    onHardCheckChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            // A card, as the design draws it: the row carries a chip and a rating beside its
            // title, and without a container those three read as three separate columns rather
            // than one lesson.
            .clip(NoirShapeLg)
            .background(NoirS1)
            .border(1.dp, NoirGlassStroke, NoirShapeLg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (item.orderLabel != null) {
            Text(item.orderLabel, style = NoirType.num.copy(color = NoirT3))
        }
        Text(
            text = item.title,
            style = NoirType.rowTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.hardUnlocked) {
            NoirChip(
                text =
                    stringResource(
                        if (item.isHardChecked) R.string.quizzes_chip_hard else R.string.quizzes_chip_easy,
                    ),
                tone = if (item.isHardChecked) NoirChipTone.Danger else NoirChipTone.Ok,
                modifier = Modifier.clickable { onHardCheckChanged(!item.isHardChecked) },
            )
        }
        LessonStars(item)
    }
}

/**
 * The rating, in gold and in figures.
 *
 * A lesson nobody has rated says so instead of showing an empty row of stars — zero stars and no
 * votes look identical, and they mean opposite things.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun LessonStars(item: LessonItemUi) {
    // Three stars, and nothing else. They say how far this player has got through the lesson —
    // what a crowd scored it out of five is a different question, asked nowhere on this row.
    StarRating(
        rating = item.bestStarsRawTenths / STAR_TENTHS,
        tint = if (item.bestStarsRawTenths == 0) NoirOutline2 else NoirGold,
        size = 15.dp,
    )
}

/** Stars are stored in tenths so half-stars survive the trip; the widget wants whole stars. */
private const val STAR_TENTHS = 10f

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
                    averageRating = 2.0f,
                    ratingCount = 12,
                    hardUnlocked = false,
                    isDownloaded = true,
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
                    averageRating = 2.7f,
                    ratingCount = 8,
                    hardUnlocked = true,
                    isHardChecked = true,
                    isDownloaded = false,
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
                    isDownloading = true,
                ),
            onClick = {},
            onHardCheckChanged = {},
        )
    }
}
