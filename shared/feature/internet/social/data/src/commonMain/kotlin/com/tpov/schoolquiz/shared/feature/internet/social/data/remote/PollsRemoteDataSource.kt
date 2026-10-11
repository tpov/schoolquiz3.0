package com.tpov.schoolquiz.shared.feature.internet.social.data.remote

import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary

/**
 * Транспорт опросов (UX-O7 §5, история 8.2): два читающих view-коллбла.
 *
 * Владелец выводится сервером из Firebase Auth — в полезной нагрузке его нет. Ответ приводится
 * к доменной проекции строгим парсером; расхождение конверта — отказ чтения.
 */
interface PollsRemoteDataSource {
    suspend fun listSeries(pageSize: Int): List<PollSeriesSummary>

    suspend fun questionState(
        seriesId: String,
        questionIndex: Int,
    ): PollQuestionState
}
