@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material3 defaults. Custom font integration deferred to future phase.
val SchoolQuizTypography = Typography()

val SchoolQuizCleanTypography =
    Typography(
        headlineSmall =
            Typography().headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleLarge =
            Typography().titleLarge.copy(
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleMedium =
            Typography().titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        bodyMedium =
            Typography().bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelLarge =
            Typography().labelLarge.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
        labelMedium =
            Typography().labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
    )
