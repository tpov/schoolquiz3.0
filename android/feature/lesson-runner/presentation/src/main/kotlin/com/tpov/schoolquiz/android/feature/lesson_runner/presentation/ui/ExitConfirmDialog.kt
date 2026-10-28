package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialog
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialogAction
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R

/**
 * Подтверждение выхода из попытки (NOIR, 9.2): NoirDialog вместо material3 AlertDialog; строки
 * и семантика не менялись.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun ExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NoirDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.runner_exit_confirm_title),
        actions = {
            NoirDialogAction(stringResource(R.string.runner_action_exit), onConfirm, primary = true)
            NoirDialogAction(stringResource(R.string.runner_action_cancel), onDismiss)
        },
    )
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ExitConfirmDialogPreview() {
    SchoolQuizTheme {
        ExitConfirmDialog(onConfirm = {}, onDismiss = {})
    }
}
