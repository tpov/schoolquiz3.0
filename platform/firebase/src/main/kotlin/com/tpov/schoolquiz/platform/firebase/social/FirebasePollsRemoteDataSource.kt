package com.tpov.schoolquiz.platform.firebase.social

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.internet.social.data.mapper.PollWireParser
import com.tpov.schoolquiz.shared.feature.internet.social.data.remote.PollsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт опросов (UX-O7 §5, история 8.2).
 *
 * Имена коллблов — ровно регистрации в `functions/index.js`. Владелец выводится сервером из
 * Auth, поэтому ключа владельца в полезной нагрузке нет; транспортные отказы едут как [SyncFailure].
 */
class FirebasePollsRemoteDataSource(
    private val functions: FirebaseFunctions,
) : PollsRemoteDataSource {
    override suspend fun listSeries(pageSize: Int): List<PollSeriesSummary> {
        val payload = buildMap<String, Any?> { put(PAGE_SIZE, pageSize) }
        return try {
            val reply =
                functions
                    .getHttpsCallable(CALLABLE_LIST)
                    .withAppTimeout()
                    .call(payload)
                    .await()
                    .data
            PollWireParser.seriesList(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }
    }

    override suspend fun questionState(
        seriesId: String,
        questionIndex: Int,
    ): PollQuestionState {
        val payload =
            buildMap<String, Any?> {
                put(SERIES_ID, seriesId)
                put(QUESTION_INDEX, questionIndex)
            }
        return try {
            val reply =
                functions
                    .getHttpsCallable(CALLABLE_STATE)
                    .withAppTimeout()
                    .call(payload)
                    .await()
                    .data
            PollWireParser.questionState(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }
    }
}

private const val CALLABLE_LIST = "listPollSeries"
private const val CALLABLE_STATE = "getPollQuestionState"
private const val PAGE_SIZE = "pageSize"
private const val SERIES_ID = "seriesId"
private const val QUESTION_INDEX = "questionIndex"
