package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.core.scoring.Score
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.RoomLessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * История 2.1 (ordinary-lesson-offline-restart) на продакшн-проводке.
 *
 * Обычный урок завершается офлайн: [LessonAttemptRepositoryImpl] с настоящим
 * [RoomLessonResultOutboxWriter] пишет попытку, ответы, расписание повторений и ряд очереди
 * одной транзакцией в файловую базу. «Рестарт» — закрытие и переоткрытие того же файла:
 * после него читаются та же попытка и тот же исход, а ряд очереди всё ещё WAITING и серверной
 * квитанции нет — показ результата не ждёт сети (CAP-1, сетевой-гейт-отрицание).
 */
class OrdinaryLessonOfflineRestartTest {

    private lateinit var dir: File
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ordinary-restart").toFile()
        db = open()
    }

    private fun open(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = File(dir, "lesson-restart.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    /** Продакшн-проводка: репозиторий + настоящий писатель очереди над текущим соединением. */
    private fun repository(): LessonAttemptRepositoryImpl =
        LessonAttemptRepositoryImpl(
            attemptDao = db.lessonAttemptDao(),
            outboxWriter = RoomLessonResultOutboxWriter(
                lessonDao = db.lessonDao(),
                themeDao = db.themeDao(),
                sectionDao = db.sectionDao(),
                questDao = db.questDao(),
                clock = object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(4_000)
                },
            ),
            repetitionDao = db.questionRepetitionDao(),
        )

    /** catalog → quest → section → theme → lesson: контекст, который читает писатель очереди. */
    private suspend fun seedOrdinaryLesson() {
        db.catalogDao().insertOrReplace(
            CatalogEntity(id = "catalog-1", name = "Catalog 1", picturePath = null, pictureUrl = null),
        )
        db.questDao().insertOrReplace(
            QuestEntity(
                id = "quest-1",
                catalogId = "catalog-1",
                authorUid = "uid-A",
                title = "Quest 1",
                picturePath = null,
                pictureUrl = null,
                visibleOn = emptySet(),
                averageRating = null,
                averageRatingCount = 0,
                version = 1,
                lastModifiedAt = 0,
                archived = false,
            ),
        )
        db.sectionDao().insertOrReplace(
            SectionEntity(
                id = "section-1",
                questId = "quest-1",
                title = "Section 1",
                order = 0,
                version = 1,
                lastModifiedAt = 0,
                archived = false,
            ),
        )
        db.themeDao().insertOrReplace(
            ThemeEntity(
                id = "theme-1",
                sectionId = "section-1",
                title = "Theme 1",
                order = 0,
                version = 1,
                lastModifiedAt = 0,
                archived = false,
            ),
        )
        db.lessonDao().insertOrReplace(
            LessonEntity(
                id = "lesson-1",
                themeId = "theme-1",
                title = "Lesson 1",
                order = 0,
                version = 3,
                lastModifiedAt = 0,
                archived = false,
            ),
        )
    }

    private fun attempt(percentScore: Int = 100) = Attempt(
        id = AttemptId("a1"),
        userId = "uid-A",
        lessonId = LessonId("lesson-1"),
        lessonVersion = 3,
        mode = Difficulty.EASY,
        completedAt = 5_000,
        codeAnswer = CodeAnswer("99"),
        percentScore = PercentScore(percentScore),
    )

    private fun answered(questionId: String, index: Int) = AnsweredQuestion(
        questionId = QuestionId(questionId),
        codeAnswerIndex = index,
        score = Score(9),
        answer = UserAnswer.SingleChoiceAnswer(OptionId("A")),
        answeredAtMs = 4_500,
        durationMs = 500,
        wasTimeout = false,
    )

    private fun answers() = listOf(answered("q-1", 0), answered("q-2", 1))

    private fun served() = listOf(ServedQuestion(QuestionId("q-1"), 0), ServedQuestion(QuestionId("q-2"), 1))

    @Test
    fun offlineCompletionSurvivesRestartWithTheSameAttemptOutcomeAndAnUnsentRow() = runTest {
        seedOrdinaryLesson()
        val writer = repository()
        val mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "a1")

        assertTrue(
            writer.save(attempt(), answers(), served(), null, AttemptCompletionKind.COMPLETED).isSuccess,
        )
        val queuedBefore = db.outboxDao().findByMutationId(mutationId)
        val scheduleBefore = db.questionRepetitionDao().findByQuestion("uid-A", "q-1")
        assertNotNull(queuedBefore)
        assertNotNull(scheduleBefore)

        // «Рестарт»: то же самое приложение заново открыло ту же базу.
        db.close()
        db = open()
        val afterRestart = repository()

        // После рестарта показываются та же попытка и тот же исход.
        assertEquals(listOf(attempt()), afterRestart.observeByLesson("uid-A", LessonId("lesson-1")).first())
        assertEquals(attempt().toEntity(), db.lessonAttemptDao().findAttempt("a1"))
        assertEquals(answers().map { it.toEntity(attempt()) }, db.lessonAttemptDao().findAttemptAnswers("a1"))
        assertEquals(scheduleBefore, db.questionRepetitionDao().findByQuestion("uid-A", "q-1"))

        // Сетевой гейт отсутствует: ряд очереди не уехал, квитанции нет — результат читаем.
        assertEquals(queuedBefore, db.outboxDao().findByMutationId(mutationId))
        assertEquals("WAITING", db.outboxDao().findByMutationId(mutationId)!!.state)
        assertNull(db.mutationReceiptDao().find("uid-A", mutationId))

        // Тот же терминальный запрос после рестарта не задваивает ряд очереди.
        assertTrue(
            afterRestart.save(attempt(), answers(), served(), null, AttemptCompletionKind.COMPLETED).isSuccess,
        )
        assertEquals(queuedBefore, db.outboxDao().findByMutationId(mutationId))

        // Изменённый исход после рестарта первую запись не переписывает.
        assertTrue(
            afterRestart.save(attempt(percentScore = 0), answers(), served(), null, AttemptCompletionKind.COMPLETED)
                .isFailure,
        )
        assertEquals(attempt().toEntity(), db.lessonAttemptDao().findAttempt("a1"))
    }
}
