@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.tpov.schoolquiz.android.core.designsystem.noir

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tpov.schoolquiz.android.core.designsystem.R

/*
 * The bundled NOIR faces.
 *
 * Archivo ships as a variable cut; the specification draws it at width 112, and without that axis
 * the headings read as an ordinary grotesque. JetBrains Mono covers everything numeric. Both fall
 * back gracefully below the axes' support floor, which minSdk 26 already clears.
 */

/** Display width used across NOIR: slightly expanded, per the specification. */
private const val ARCHIVO_WIDTH = 112

private val FONT_WEIGHTS =
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
    )

/** Archivo variable at width 112 — display face for app bars, headings, question text. */
val NoirDisplayFont: FontFamily by lazy {
    FontFamily(
        FONT_WEIGHTS.map { weight ->
            Font(
                resId = R.font.archivo_variable,
                weight = weight,
                variationSettings =
                    FontVariation.Settings(
                        FontVariation.weight(weight.weight),
                        FontVariation.width(ARCHIVO_WIDTH.toFloat()),
                    ),
            )
        },
    )
}

/** JetBrains Mono variable — kickers, buttons, prices, timers, anything numeric. */
val NoirMonoFont: FontFamily by lazy {
    FontFamily(
        FONT_WEIGHTS.map { weight ->
            Font(
                resId = R.font.jetbrains_mono_variable,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )
}
