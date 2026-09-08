package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryEvent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

/** Navigation stays outside Entry persistence. A host without this capability renders no start buttons. */
data class ArenaEntryCallbacks(
    val onEvent: (ArenaEntryEvent) -> Unit,
    val onOpenRun: ((ArenaSessionStamp, String, Boolean) -> Unit)? = null,
)
