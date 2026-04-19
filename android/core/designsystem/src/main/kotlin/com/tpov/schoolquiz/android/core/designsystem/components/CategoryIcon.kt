package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun CategoryIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CategoryIconPreview() {
    SchoolQuizTheme {
        CategoryIcon(icon = Icons.Default.Category, contentDescription = "Preview")
    }
}
