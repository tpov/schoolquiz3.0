package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.ReviewFeeQuoteWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewFeeQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт цитаты платы за проверку (история 4.11).
 *
 * Имя коллбла — ровно регистрация в `functions/index.js` (сторож —
 * [ReviewFeeQuoteCallableNamesTest]). Владелец выводится сервером из Auth, поэтому ключа
 * владельца в полезной нагрузке нет; отказ транспорта едет как [SyncFailure], отказ строгого
 * декодера — тоже отказ чтения.
 */
class FirebaseReviewFeeQuoteRemoteDataSource(
    private val functions: FirebaseFunctions,
) : ReviewFeeQuoteRemoteDataSource {
    override suspend fun quoteReviewFee(
        questId: String,
        questionCount: Int,
    ): ReviewFeeQuote =
        try {
            val reply =
                functions
                    .getHttpsCallable(ReviewFeeQuoteCallableNames.QUOTE)
                    .withAppTimeout()
                    .call(mapOf(QUEST_ID to questId, QUESTION_COUNT to questionCount))
                    .await()
                    .data
            ReviewFeeQuoteWireParser.reviewFeeQuote(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }
}

/** Имена callable-поверхности цитаты — ровно регистрации в `functions/index.js`. */
internal object ReviewFeeQuoteCallableNames {
    const val QUOTE = "quoteQuestReviewFee"
}

private const val QUEST_ID = "questId"
private const val QUESTION_COUNT = "questionCount"
