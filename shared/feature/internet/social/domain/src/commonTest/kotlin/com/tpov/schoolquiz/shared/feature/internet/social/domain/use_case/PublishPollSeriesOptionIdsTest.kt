package com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionDraft
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesDraft
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsTimestampProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.repository.PollsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Клиентская половина фикса ретро эпика 8 (находка 1): продакшн-авторинг назначал одинаковые
 * optionId на каждый вопрос (`opt-1..opt-8`), и серверный счёт без оси вопроса смешивал чужие
 * голоса в любой мульти-вопросной серии из UI. Тела публикации обязаны нести optionId,
 * попарно различные между вопросами, и укладываться в серверный чарсет.
 */
class PublishPollSeriesOptionIdsTest {

    private class CapturingRepository : PollsRepository {
        val published = mutableListOf<PollPublishSubmission>()

        override suspend fun listSeries(pageSize: Int): Result<List<PollSeriesSummary>> =
            error("not used in this test")

        override suspend fun questionState(
            seriesId: String,
            questionIndex: Int,
        ): Result<PollQuestionState> = error("not used in this test")

        override suspend fun queueVote(submission: PollVoteSubmission): Result<Unit> =
            error("not used in this test")

        override suspend fun queuePublish(submission: PollPublishSubmission): Result<Unit> {
            published += submission
            return Result.success(Unit)
        }
    }

    @Test
    fun `given a multi question draft then option ids differ across questions and fit the server charset`() =
        runTest {
            val repository = CapturingRepository()
            val useCase = PublishPollSeriesUseCase(
                repository,
                object : PollsIdProvider {
                    override fun nextId(prefix: String) = "intent-1"
                },
                object : PollsTimestampProvider {
                    override fun nowMs() = 1_000L
                },
            )
            val draft = PollSeriesDraft(
                title = "Two questions",
                questions = listOf(
                    PollQuestionDraft(text = "Q0", mode = PollMode.SINGLE, optionTexts = listOf("A", "B")),
                    PollQuestionDraft(text = "Q1", mode = PollMode.SINGLE, optionTexts = listOf("A", "B")),
                    PollQuestionDraft(text = "Q2", mode = PollMode.MULTIPLE, optionTexts = (1..8).map { "o$it" }),
                ),
            )

            val result = useCase(
                ownerUid = "uid-author",
                seriesId = "series-1",
                draft = draft,
                expectedRevisionId = 0L,
            )

            assertTrue(result.isSuccess)
            val command = repository.published.single().command
            val perQuestion = command.questions.map { question -> question.options.map { it.optionId } }
            val all = perQuestion.flatten()
            assertEquals(all.size, all.toSet().size, "option ids must not repeat across questions: $perQuestion")
            all.forEach { id -> assertTrue(id.matches(SERVER_CHARSET), "server charset: $id") }
            // Позиция варианта остаётся читаемой в id: ось вопроса + нумерация варианта.
            perQuestion.forEachIndexed { questionIndex, ids ->
                ids.forEachIndexed { optionIndex, id -> assertEquals("q$questionIndex-opt-${optionIndex + 1}", id) }
            }
        }

    private companion object {
        val SERVER_CHARSET = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
