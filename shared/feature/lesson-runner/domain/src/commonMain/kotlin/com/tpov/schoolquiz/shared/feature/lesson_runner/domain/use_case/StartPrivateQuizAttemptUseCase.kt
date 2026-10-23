package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.PrivateQuizRunRepository
import kotlinx.coroutines.CancellationException

/**
 * Вход в приватный квиз (AD-29, история 8.4): дверь — единственная, отказы — типизированные.
 *
 * [startId] рождается здесь, в момент намерения участника, и обязан переживать повтор: вызываю­
 * щий держит его, пока вход не разрешится (AD-6). Повтор входа с тем же [startId] возвращает
 * сохранённый старт сервера и не тратит лимит второй раз — повтор после оборванной сети
 * не становится второй попыткой.
 *
 * Приватный прогон не наследует ни экзаменационной платы, ни таймера, ни pass-fail (AD-29):
 * door refusals — единственные именованные исходы входа.
 */
class StartPrivateQuizAttemptUseCase(
    private val repository: PrivateQuizRunRepository,
    private val authRepository: AuthRepository,
    private val startIdProvider: () -> String,
) {
    suspend operator fun invoke(
        ownerUid: String,
        catalogId: String,
        questId: String,
        existingStartId: String? = null,
    ): PrivateQuizEntry {
        val uid =
            authRepository.currentUid()
                ?: return PrivateQuizEntry.Refused(PrivateQuizEntryRefusal.AUTH_REQUIRED)
        // Повтор намерения — тот же ключ (AD-6); новый вход — новый ключ.
        val startId = existingStartId?.ifBlank { null } ?: startIdProvider()
        return try {
            repository.start(
                ownerUid = ownerUid,
                catalogId = catalogId,
                questId = questId,
                startId = startId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Транспортная неудача — не отказ двери: повтор с тем же ключом остаётся честным.
            PrivateQuizEntry.Refused(PrivateQuizEntryRefusal.UNAVAILABLE)
        }
    }
}
