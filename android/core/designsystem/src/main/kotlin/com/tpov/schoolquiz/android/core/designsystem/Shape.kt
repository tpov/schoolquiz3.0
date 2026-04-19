package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Values per 06-api-contract.md:392-394:
//   extraSmall=4dp, small=8dp, medium=12dp, large=16dp, extraLarge=24dp
val SchoolQuizShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
