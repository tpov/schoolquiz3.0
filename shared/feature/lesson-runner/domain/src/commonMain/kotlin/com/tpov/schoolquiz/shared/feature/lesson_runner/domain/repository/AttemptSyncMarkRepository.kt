package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptSyncMark
import kotlinx.coroutines.flow.Flow

/**
 * Пометка «не отправлено» на конкретном прохождении.
 *
 * Спрашивается по ссылке на сущность, которую составил писатель очереди этой же фичи: ядро её не
 * разбирает и ни одной таблицы фичи не читает (AD-7). Ответ — закрытый набор [AttemptSyncMark], а
 * не `Boolean` и не `null`.
 *
 * Поток auth-scoped (инвариант 8): при смене аккаунта он пересобирается сам, а гость получает
 * [AttemptSyncMark.NONE], а не пустой поток — иначе экран показал бы чужую пометку или замер бы
 * без единой эмиссии.
 */
interface AttemptSyncMarkRepository {
    fun observeAttemptMark(attemptId: AttemptId): Flow<AttemptSyncMark>
}
