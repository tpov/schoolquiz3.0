package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R

/**
 * Breadcrumb titles may contain blank segments (e.g. catalogs without a name).
 * They are resolved to the localized no-catalog label at render time.
 */
@Composable
internal fun breadcrumbTitles(titles: List<String>): List<String> {
    val noCatalog = stringResource(R.string.quizzes_no_catalog)
    return titles.map { segment -> segment.takeIf { it.isNotBlank() } ?: noCatalog }
}
