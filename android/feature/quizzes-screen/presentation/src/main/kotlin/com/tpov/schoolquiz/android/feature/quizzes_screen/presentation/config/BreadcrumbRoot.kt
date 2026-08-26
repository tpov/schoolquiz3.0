package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config

import kotlinx.serialization.Serializable

/**
 * Neutral breadcrumb segment carried inside the serializable navigation configuration.
 *
 * Static roots carry NO display text — screens resolve them to localized strings at render
 * time via [com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen.breadcrumbTitles].
 * Only [Dynamic] holds a ready-made title coming from domain data (catalog / quest / section /
 * theme / lesson names); blank values fall back to the localized no-catalog label on screen.
 */
@Serializable
sealed interface BreadcrumbRoot {
    /** «Каталоги» */
    @Serializable
    data object Catalogs : BreadcrumbRoot

    /** «Архив» */
    @Serializable
    data object Archive : BreadcrumbRoot

    /** «Курсы» */
    @Serializable
    data object Courses : BreadcrumbRoot

    /** «Арена» */
    @Serializable
    data object Arena : BreadcrumbRoot

    /** «Домашние квесты» — public quest picker entry point from the home shelf. */
    @Serializable
    data object HomeQuests : BreadcrumbRoot

    /** «Отборочный турнир» — public quest picker entry point from the qualifier tournament. */
    @Serializable
    data object QualifierTournament : BreadcrumbRoot

    /** «Чемпионат мира» — public quest picker entry point from the world championship. */
    @Serializable
    data object WorldChampionship : BreadcrumbRoot

    /**
     * Segment arriving from outside already resolved as text (a domain entity name).
     * Blank values are rendered as the localized no-catalog label.
     */
    @Serializable
    data class Dynamic(
        val title: String,
    ) : BreadcrumbRoot
}
