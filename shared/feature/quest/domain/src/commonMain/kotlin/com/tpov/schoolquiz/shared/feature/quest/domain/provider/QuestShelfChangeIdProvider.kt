package com.tpov.schoolquiz.shared.feature.quest.domain.provider

/**
 * Откуда берётся ключ намерения перенести квест (AD-2).
 *
 * Ключ обязан быть свежим на каждое нажатие: перенос того же квеста туда же второй раз — новое
 * намерение, и под старым ключом сервер вернул бы сохранённый ответ, ничего не сделав.
 * Тесты подставляют предсказуемый источник, приложение — [Random].
 */
fun interface QuestShelfChangeIdProvider {
    fun nextId(): String

    companion object {
        /** Ключ, который не повторится за время жизни хранимых на сервере `mutationId`. */
        val Random: QuestShelfChangeIdProvider =
            QuestShelfChangeIdProvider {
                "shelf-" + kotlin.random.Random.nextLong(1, Long.MAX_VALUE).toString(RADIX)
            }

        private const val RADIX = 36
    }
}
