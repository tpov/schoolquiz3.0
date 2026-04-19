package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// ADR-0010: #000000 background, #4285F4 primary, #FFD700 secondary, #7D4FAB tertiary.
private val DarkColorScheme =
    darkColorScheme(
        primary = GoogleBlue,
        onPrimary = OnPrimary,
        secondary = BrandGold,
        onSecondary = OnSecondary,
        tertiary = BrandPurple,
        background = Black,
        onBackground = OnBackground,
        surface = DarkSurface,
        onSurface = OnBackground,
        outline = OutlineColor,
    )

// Dark-only: light colorScheme not implemented per ADR-0010 + spec NFR #4.
// darkTheme preserved for future light theme support.
@Suppress("FunctionNaming", "UnusedParameter", "ktlint:standard:function-naming")
@Composable
fun SchoolQuizTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        shapes = SchoolQuizShapes,
        typography = SchoolQuizTypography,
        content = content,
    )
}
