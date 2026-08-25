package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun CrossButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RunnerIconBadge(color = NoirDanger, modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Выйти из урока",
            )
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun CrossButtonPreview() {
    SchoolQuizTheme { CrossButton(onClick = {}) }
}
