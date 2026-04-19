package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun BrandProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.secondary,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(clampedProgress)
                    .fillMaxHeight()
                    .background(color),
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrandProgressBarPreview() {
    SchoolQuizTheme {
        BrandProgressBar(progress = 0.7f)
    }
}
