package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun BrandCircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, schoolQuizDesignLightBorderColor()),
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = onClick,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun BrandSquareIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = schoolQuizDesignCardShape(),
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, schoolQuizDesignLightBorderColor()),
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = onClick,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrandSquareIconButtonPreview() {
    SchoolQuizTheme {
        BrandSquareIconButton(icon = Icons.Default.Star, contentDescription = null, onClick = {})
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrandCircleIconButtonPreview() {
    SchoolQuizTheme {
        BrandCircleIconButton(icon = Icons.Default.Star, contentDescription = null, onClick = {})
    }
}
