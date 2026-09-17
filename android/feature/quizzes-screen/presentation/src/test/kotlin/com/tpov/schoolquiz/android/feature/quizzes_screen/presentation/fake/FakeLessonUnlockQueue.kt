package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonUnlockOutcome
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonUnlockQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Разблокировка в памяти.
 *
 * Форму записи, транзакцию и арифметику ноликов проверяет фича lesson, которой они принадлежат;
 * здесь проверяется только то, что экран доходит до неё, называет ей посчитанную цену и говорит
 * игроку то, что она ответила.
 */
class FakeLessonUnlockQueue : LessonUnlockQueue {

    /** Каждое принятое намерение: uid, урок, вид покупки, названная цена. */
    val queued = mutableListOf<QueuedUnlock>()

    /** Чем отвечает очередь. По умолчанию урок открывается сразу — как в проде после 5.3. */
    var outcome: LessonUnlockOutcome = LessonUnlockOutcome.Opened

    /** Когда задано — постановка не удаётся; так выглядит отказавшая база. */
    var failure: Throwable? = null

    /** Уроки, чья покупка ушла в карантин и была откачена. */
    val refused = MutableStateFlow<Set<LessonId>>(emptySet())

    override suspend fun queueUnlock(
        ownerUid: String,
        lessonId: LessonId,
        kindWireName: String,
        priceNolics: Int,
    ): Result<LessonUnlockOutcome> {
        failure?.let { return Result.failure(it) }
        queued += QueuedUnlock(ownerUid, lessonId, kindWireName, priceNolics)
        return Result.success(outcome)
    }

    override fun observeRefusedUnlocks(ownerUid: String): Flow<Set<LessonId>> = refused
}

data class QueuedUnlock(
    val ownerUid: String,
    val lessonId: LessonId,
    val kindWireName: String,
    val priceNolics: Int,
)
