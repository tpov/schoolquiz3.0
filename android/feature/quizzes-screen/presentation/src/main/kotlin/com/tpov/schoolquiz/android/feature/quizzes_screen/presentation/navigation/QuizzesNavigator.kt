package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

interface QuizzesNavigator {
    fun openQuestList(
        catalogId: CatalogId,
        catalogName: String,
    )

    fun openCourseArchive()

    fun openSectionList(
        questId: QuestId,
        titles: List<String>,
    )

    fun dismissQuizzes()
}
