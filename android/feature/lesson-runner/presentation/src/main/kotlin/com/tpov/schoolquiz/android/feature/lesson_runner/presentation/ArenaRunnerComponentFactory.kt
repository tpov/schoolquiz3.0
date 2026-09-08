package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

/** Separate factory: the ordinary four-argument SAM remains unchanged. */
fun interface ArenaRunnerComponentFactory {
    fun create(
        context: ComponentContext,
        request: ArenaRunnerRequest,
    ): LessonRunnerRootComponent
}

/** Route pointer only. A restored route supplies no [fresh] claim and cannot silently become a new launch. */
data class ArenaRunnerRequest(
    val ownerUid: String,
    val ticketId: String,
    val fresh: ArenaFreshStart? = null,
) {
    init {
        require(ownerUid.isNotBlank() && ticketId.isNotBlank())
        require(fresh == null || fresh.session.ownerUid == ownerUid)
    }
}

/** Transient explicit intent, captured from the selected ticket frame; never serialize this epoch. */
data class ArenaFreshStart(val session: ArenaSessionStamp, val mode: SessionMode)
