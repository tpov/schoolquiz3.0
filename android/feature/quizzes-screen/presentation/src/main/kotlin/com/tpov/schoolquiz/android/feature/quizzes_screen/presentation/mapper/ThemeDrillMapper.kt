package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme

fun Theme.toDrillItem(): HierarchyItemUi =
    HierarchyItemUi(
        id = id.value,
        title = title,
        orderLabel = "${order + 1}.",
        subtitleCount = null,
    )
