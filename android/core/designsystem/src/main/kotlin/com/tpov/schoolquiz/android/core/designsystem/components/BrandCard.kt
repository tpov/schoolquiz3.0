package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SchoolQuizDesignCard(
        modifier = modifier,
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Column(content = content)
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrandCardPreview() {
    SchoolQuizTheme {
        BrandCard { /* preview content */ }
    }
}
