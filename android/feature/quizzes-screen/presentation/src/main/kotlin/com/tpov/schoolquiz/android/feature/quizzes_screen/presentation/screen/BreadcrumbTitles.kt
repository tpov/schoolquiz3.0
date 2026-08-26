package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot

/**
 * Resolves neutral breadcrumb segments to display text at render time.
 *
 * Static roots map to localized string resources; [BreadcrumbRoot.Dynamic] carries domain-provided
 * text and passes through as-is. Blank dynamic segments (e.g. catalogs without a name coming from
 * the app shell) resolve to the localized no-catalog label.
 */
@Composable
internal fun breadcrumbTitles(breadcrumbs: List<BreadcrumbRoot>): List<String> {
    val noCatalog = stringResource(R.string.quizzes_no_catalog)
    return breadcrumbs.map { root ->
        when (root) {
            BreadcrumbRoot.Catalogs -> stringResource(R.string.quizzes_breadcrumb_catalogs)
            BreadcrumbRoot.Archive -> stringResource(R.string.quizzes_breadcrumb_archive)
            BreadcrumbRoot.Courses -> stringResource(R.string.quizzes_breadcrumb_courses)
            BreadcrumbRoot.Arena -> stringResource(R.string.quizzes_breadcrumb_arena)
            BreadcrumbRoot.HomeQuests -> stringResource(R.string.quizzes_breadcrumb_home_quests)
            BreadcrumbRoot.QualifierTournament ->
                stringResource(R.string.quizzes_breadcrumb_qualifier_tournament)
            BreadcrumbRoot.WorldChampionship ->
                stringResource(R.string.quizzes_breadcrumb_world_championship)
            is BreadcrumbRoot.Dynamic -> root.title.takeIf { it.isNotBlank() } ?: noCatalog
        }
    }
}
