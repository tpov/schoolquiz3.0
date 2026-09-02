package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerEntity
import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строит строки общей очереди для того, что делает игрок в уроке.
 *
 * Строит, а не пишет: строка обязана лечь в базу той же транзакцией, что и локальное изменение
 * (AD-23) — иначе прохождение показано как сохранённое и никогда не уедет, или уедет то, чего
 * локально нет. Записывают её DAO фичи, здесь только собирается тело.
 *
 * Тело собирается целиком в момент намерения (AD-2): ключ и `payload` живут дальше сами по себе,
 * и дочитывать что-то в момент отправки уже нельзя — движок в `payload` не смотрит.
 */
interface LessonResultOutboxWriter {
    /**
     * Строка очереди для прохождения вместе с ответами, которые к нему относятся, и списком
     * вопросов, сданных в порядок показа.
     *
     * [served] — весь порядок показа, а не только достигнутое: недостигнутые при выходе вопросы тоже
     * здесь, потому что `buildCodeAnswerOnAbort` уже поставил им `'1'` и считает показанными. Сервер
     * сегодня поле отбрасывает; когда шаг подключения начнёт его читать, цифры встанут по этому
     * списку, а не по слову клиента о том, какие позиции были показаны. Список живёт только в теле
     * очереди; строка прохождения его не несёт.
     *
     * `null` — список не передан, и ключа в теле не будет: для сервера это «неизвестно», а не
     * «ничего не показано». Пустой список — именно «ничего не показано» — пишется как `[]`.
     */
    suspend fun buildAttemptRow(
        attempt: Attempt,
        answers: List<QuestionAnswerEntity> = emptyList(),
        served: List<ServedQuestion>? = null,
        claims: ChargeClaimMask? = null,
    ): OutboxEntity? = null

    /** Строка очереди для оценки квеста. */
    suspend fun buildRatingRow(rating: LessonRating): OutboxEntity? = null

    companion object {
        /** Ничего не откладывает. Для сборок и тестов, где очередь не участвует. */
        val NoOp: LessonResultOutboxWriter = object : LessonResultOutboxWriter {}
    }
}

class RoomLessonResultOutboxWriter(
    private val lessonDao: LessonDao,
    private val themeDao: ThemeDao,
    private val sectionDao: SectionDao,
    private val questDao: QuestDao,
    private val clock: Clock,
) : LessonResultOutboxWriter {

    override suspend fun buildAttemptRow(
        attempt: Attempt,
        answers: List<QuestionAnswerEntity>,
        served: List<ServedQuestion>?,
        claims: ChargeClaimMask?,
    ): OutboxEntity {
        served?.let { requireConsistent(attempt, answers, it) }
        val context = resolveContentContext(attempt.lessonId.value)
        val createdAtMs = clock.now().toEpochMilliseconds()
        val payload =
            buildJsonObject {
                put("attemptId", attempt.id.value)
                put("userId", attempt.userId)
                putContext(context)
                put("lessonVersion", attempt.lessonVersion)
                put("difficulty", attempt.mode.name)
                put("codeAnswer", attempt.codeAnswer.raw)
                put("percentScore", attempt.percentScore.raw)
                put("completedAtMs", attempt.completedAt)
                put("createdAtMs", createdAtMs)
                // Ключ только когда заявки есть: попытка без них обязана выглядеть на сервере ровно
                // так же, как выглядела до появления маски.
                claims?.takeIf { it.hasClaims }?.let { put("chargeClaims", it.raw) }
                put(
                    "answers",
                    buildJsonArray {
                        answers.forEach { row ->
                            add(
                                buildJsonObject {
                                    put("questionId", row.questionId)
                                    put("codeAnswerIndex", row.codeAnswerIndex)
                                    put("score", row.score)
                                    put("answerPayload", row.answerPayload)
                                    put("answeredAtMs", row.answeredAtMs)
                                    put("durationMs", row.durationMs)
                                    put("wasTimeout", row.wasTimeout == 1)
                                },
                            )
                        }
                    },
                )
                // Ключ только когда список передан: отсутствие — «неизвестно», `[]` — «ничего».
                // Те же два поля, что и у строк ответов: по ним сервер и сопоставит одно с другим.
                served?.let { list ->
                    put(
                        "served",
                        buildJsonArray {
                            list.forEach { question ->
                                add(
                                    buildJsonObject {
                                        put("questionId", question.questionId.value)
                                        put("codeAnswerIndex", question.codeAnswerIndex)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        return row(
            operation = OutboxOperations.SUBMIT_ATTEMPT,
            sourceId = attempt.id.value,
            ownerUid = attempt.userId,
            entityRef = LessonResultEntityRef.attempt(attempt.id.value),
            payload = payload.toString(),
            createdAtMs = createdAtMs,
        )
    }

    override suspend fun buildRatingRow(rating: LessonRating): OutboxEntity {
        val context = resolveContentContext(rating.lessonId.value)
        val createdAtMs = clock.now().toEpochMilliseconds()
        val payload =
            buildJsonObject {
                put("ratingId", rating.id.value)
                put("userId", rating.userId)
                putContext(context)
                put("lessonVersion", rating.lessonVersion)
                put("rating", rating.rating)
                put("ratedAtMs", rating.ratedAt)
                put("createdAtMs", createdAtMs)
            }
        return row(
            operation = OutboxOperations.SUBMIT_RATING,
            sourceId = rating.id.value,
            ownerUid = rating.userId,
            entityRef = LessonResultEntityRef.rating(rating.id.value),
            payload = payload.toString(),
            createdAtMs = createdAtMs,
        )
    }

    /**
     * Тело замораживается здесь (AD-2), и это последнее дешёвое место сверить список с цифрами
     * рядом. Нарушение всплывает как `SaveFailed` на устройстве — `save` оборачивает писателя в
     * `runCatching` — а не как карантин недели спустя.
     */
    private fun requireConsistent(
        attempt: Attempt,
        answers: List<QuestionAnswerEntity>,
        served: List<ServedQuestion>,
    ) {
        val poolSize = attempt.codeAnswer.raw.length
        served.forEach { question ->
            require(question.codeAnswerIndex < poolSize) {
                "served position ${question.codeAnswerIndex} is outside a codeAnswer of length $poolSize"
            }
        }
        val servedPairs = served.map { it.questionId.value to it.codeAnswerIndex }.toSet()
        answers.forEach { row ->
            require((row.questionId to row.codeAnswerIndex) in servedPairs) {
                "answer for ${row.questionId} at ${row.codeAnswerIndex} names a question that was not served there"
            }
        }
    }

    private fun row(
        operation: String,
        sourceId: String,
        ownerUid: String,
        entityRef: String,
        payload: String,
        createdAtMs: Long,
    ) = OutboxEntity(
        mutationId = OutboxOperations.mutationKey(operation, sourceId),
        ownerUid = ownerUid,
        operation = operation,
        payload = payload,
        entityRef = entityRef,
        // Ни прохождение, ни оценка не версионируются: обе создаются один раз и не редактируются.
        expectedVersion = null,
        state = OutboxState.WAITING.name,
        attemptCount = 0,
        nextRetryAtMs = 0L,
        lastError = null,
        createdAtMs = createdAtMs,
    )

    private suspend fun resolveContentContext(lessonId: String): LessonContentContext {
        val lesson = checkNotNull(lessonDao.findById(lessonId)) { "Lesson $lessonId not found" }
        val theme = checkNotNull(themeDao.findById(lesson.themeId)) { "Theme ${lesson.themeId} not found" }
        val section = checkNotNull(sectionDao.findById(theme.sectionId)) { "Section ${theme.sectionId} not found" }
        val quest = checkNotNull(questDao.findById(section.questId)) { "Quest ${section.questId} not found" }
        val scope = if (quest.visibleOn.isEmpty()) PRIVATE_SCOPE else PUBLIC_SCOPE
        return LessonContentContext(
            scope = scope,
            ownerUid = if (scope == PRIVATE_SCOPE) quest.authorUid else null,
            catalogId = quest.catalogId,
            questId = quest.id,
            sectionId = section.id,
            themeId = theme.id,
            lessonId = lesson.id,
            sourceShelf = quest.visibleOn.sourceShelf(),
        )
    }

    private fun Set<String>.sourceShelf(): String =
        when {
            ARENA_SHELF in this -> ARENA_SHELF
            HOME_SHELF in this -> HOME_SHELF
            ARCHIVE_SHELF in this -> ARCHIVE_SHELF
            isEmpty() -> PRIVATE_SCOPE
            else -> first()
        }
}

/**
 * Ссылка на локальную сущность, которую эта запись очереди подтверждает (AD-14).
 *
 * Её же читает откат по карантину: он обязан узнать, что именно убирать, не разбирая `payload`
 * там, где хватает ссылки.
 */
object LessonResultEntityRef {
    const val ATTEMPT_PREFIX: String = "lesson_runner:attempt:"
    const val RATING_PREFIX: String = "lesson_runner:rating:"

    fun attempt(attemptId: String): String = ATTEMPT_PREFIX + attemptId

    /**
     * Форма ровно та же, что выдала миграция 5 → 6 перенесённым строкам: откат по карантину
     * обязан узнавать и их тоже, а не только записи, поставленные уже новым писателем.
     */
    fun rating(ratingId: String): String = RATING_PREFIX + ratingId
}

private data class LessonContentContext(
    val scope: String,
    val ownerUid: String?,
    val catalogId: String,
    val questId: String,
    val sectionId: String,
    val themeId: String,
    val lessonId: String,
    val sourceShelf: String,
)

private fun kotlinx.serialization.json.JsonObjectBuilder.putContext(context: LessonContentContext) {
    put("scope", context.scope)
    put("ownerUid", context.ownerUid?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
    put("catalogId", context.catalogId)
    put("questId", context.questId)
    put("sectionId", context.sectionId)
    put("themeId", context.themeId)
    put("lessonId", context.lessonId)
    put("sourceShelf", context.sourceShelf)
}

private const val PUBLIC_SCOPE = "public"
private const val PRIVATE_SCOPE = "private"
private const val ARENA_SHELF = "arena"
private const val HOME_SHELF = "home"
private const val ARCHIVE_SHELF = "archive"
