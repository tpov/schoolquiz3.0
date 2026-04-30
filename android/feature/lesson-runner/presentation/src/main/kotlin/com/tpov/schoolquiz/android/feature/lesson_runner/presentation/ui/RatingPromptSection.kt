@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState.RatingSubmissionState

private const val MAX_RATING = 3

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun RatingPromptSection(
    ratingSubmissionState: RatingSubmissionState,
    onSubmitRating: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRating by remember { mutableIntStateOf(0) }
    val isEnabled =
        ratingSubmissionState == RatingSubmissionState.Idle ||
            ratingSubmissionState == RatingSubmissionState.Failed

    // On failure: reset local selection so user can pick again
    LaunchedEffect(ratingSubmissionState) {
        if (ratingSubmissionState == RatingSubmissionState.Failed) {
            selectedRating = 0
        }
    }

    RunnerDesignCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.secondary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Оцените урок",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 1..MAX_RATING) {
                    IconButton(
                        onClick = {
                            if (isEnabled) {
                                selectedRating = i
                                onSubmitRating(i)
                            }
                        },
                        enabled = isEnabled,
                    ) {
                        Icon(
                            imageVector =
                                if (i <= selectedRating) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarOutline
                                },
                            contentDescription = "$i звезды",
                            tint =
                                if (i <= selectedRating) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f)
                                },
                        )
                    }
                }
            }
            if (ratingSubmissionState == RatingSubmissionState.Failed) {
                Text(
                    text = "Не удалось сохранить оценку",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun RatingPromptSectionPreview() {
    SchoolQuizTheme {
        RatingPromptSection(
            ratingSubmissionState = RatingSubmissionState.Idle,
            onSubmitRating = {},
        )
    }
}
