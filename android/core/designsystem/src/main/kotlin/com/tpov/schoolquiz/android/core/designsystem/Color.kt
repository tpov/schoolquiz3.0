@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.ui.graphics.Color

// ---- Brand palette (ADR-0010) ----

/** Pure black background — ADR-0010 */
val Black = Color(0xFF000000)

/** Google Blue — primary brand color, ADR-0010 */
val GoogleBlue = Color(0xFF4285F4)

/** Gold / Yellow — secondary brand color, ADR-0010 */
val BrandGold = Color(0xFFFFD700)

/** Purple — tertiary color, ADR-0010 */
val BrandPurple = Color(0xFF7D4FAB)

/** Surface — slightly elevated from pure black, 1dp stroke baseline.
 *  Value: #242429 per spec AC 14 (0-spec.md:769) and 06-api-contract.md. */
val DarkSurface = Color(0xFF242429)

/** On-primary (text/icon on GoogleBlue) */
val OnPrimary = Color(0xFFFFFFFF)

/** On-secondary (text/icon on BrandGold) */
val OnSecondary = Color(0xFF000000)

/** On-background (primary text on Black) */
val OnBackground = Color(0xFFE0E0E0)

/** Outline / stroke color — 1dp borders per ADR-0010 flat design */
val OutlineColor = Color(0xFF2C2C2C)
