package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.LessonProposalQuoteWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonProposalQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт цитаты платы предложения (UX-23, история 4.22).
 *
 * Имя коллбла — ровно регистрация в `functions/index.js` (сторож —
 * [LessonProposalQuoteCallableNamesTest]). Владелец выводится сервером из Auth, поэтому ключа
 * владельца в полезной нагрузке нет; отказ транспорта едет как [SyncFailure], отказ строгого
 * декодера — тоже отказ чтения.
 */
class FirebaseLessonProposalQuoteRemoteDataSource(
    private val functions: FirebaseFunctions,
) : LessonProposalQuoteRemoteDataSource {
    override suspend fun quoteProposalFee(
        kind: String,
        changedQuestions: Int,
    ): LessonProposalFeeQuote =
        try {
            val reply =
                functions
                    .getHttpsCallable(LessonProposalQuoteCallableNames.QUOTE)
                    .withAppTimeout()
                    .call(mapOf(KIND to kind, CHANGED_QUESTIONS to changedQuestions))
                    .await()
                    .data
            LessonProposalQuoteWireParser.lessonProposalFeeQuote(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }
}

/** Имена callable-поверхности цитаты предложения — ровно регистрации в `functions/index.js`. */
internal object LessonProposalQuoteCallableNames {
    const val QUOTE = "quoteLessonProposalFee"
}

private const val KIND = "kind"
private const val CHANGED_QUESTIONS = "changedQuestions"
