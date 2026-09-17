package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptSyncMark
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.AttemptSyncMarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Ответ очереди про прохождение — задаётся тестом и меняется на ходу. */
class FakeAttemptSyncMarkRepository(
    initial: AttemptSyncMark = AttemptSyncMark.NONE,
) : AttemptSyncMarkRepository {

    private val marks = MutableStateFlow(initial)

    /** О какой ссылке спрашивали — чтобы утверждать, что спрашивали именно про это прохождение. */
    val askedFor = mutableListOf<AttemptId>()

    fun emit(mark: AttemptSyncMark) {
        marks.value = mark
    }

    override fun observeAttemptMark(attemptId: AttemptId): Flow<AttemptSyncMark> {
        askedFor += attemptId
        return marks
    }
}
