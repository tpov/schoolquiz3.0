package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

interface QuizzesNavigator {
    fun openQuestList(
        catalogId: CatalogId,
        catalogName: String,
    )

    fun openCourseArchive()

    fun openCourseArena()

    fun openPublicQuestCatalogPicker(targetShelf: String)

    fun openPublicQuestShelfCatalog(
        targetShelf: String,
        forcedHardMode: Boolean? = null,
    )

    fun openSectionList(
        questId: QuestId,
        breadcrumbs: List<BreadcrumbRoot>,
    )

    fun dismissQuizzes()
}
