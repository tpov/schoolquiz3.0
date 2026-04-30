@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

@Composable
internal fun QuestionInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = runnerDeepSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, runnerLightBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(46.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Инфо",
            )
        }
    }
}

@Composable
internal fun QuestionInfoDialog(
    info: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            RunnerDesignCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = runnerDeepSurfaceColor(),
                borderColor = runnerLightBorderColor(),
                elevated = true,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Инфо",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = info,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    RunnerPrimaryAction(
                        text = "Понятно",
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun QuestionInfoDialogPreview() {
    SchoolQuizTheme {
        QuestionInfoDialog(
            info =
                "val создает ссылку, которую нельзя переназначить. " +
                    "Объект внутри все еще может быть изменяемым, если его тип это позволяет.",
            onDismiss = {},
        )
    }
}
