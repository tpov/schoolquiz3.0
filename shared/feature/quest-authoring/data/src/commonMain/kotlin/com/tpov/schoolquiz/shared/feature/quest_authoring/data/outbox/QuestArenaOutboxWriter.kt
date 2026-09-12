package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для заявки черновика на арену.
 *
 * Заявка уходит единственным приёмником, а не прямой записью в Firestore (AD-6): ключ
 * идемпотентности проверяется только там, где выполняется код, и прямая запись обходила эту
 * проверку в принципе.
 *
 * Тело собирается целиком здесь, в момент намерения автора (AD-2). Прежний отправитель дочитывал
 * черновик в момент отправки — то есть отправлял не то, что автор подал, а то, во что черновик
 * успел превратиться, пока лежал без сети.
 */
object QuestArenaOutboxWriter {
    /** Ссылка на локальную сущность — та же форма, что выдала миграция 5 → 6. */
    const val DRAFT_REF_PREFIX: String = "quest_authoring:draft:"

    fun draftRef(draftId: String): String = DRAFT_REF_PREFIX + draftId

    /**
     * Reads only the correlation key owned by this feature from an already-stored outbox body.
     *
     * Conflict replacement intentionally preserves the original body: the server rejected it
     * before creating a review submission, so minting a different submission ID would make a
     * later terminal outcome impossible to correlate. Parsing is deliberately fail-closed.
     */
    fun submissionIdFromPayload(payload: String): String? =
        runCatching {
            kotlinx.serialization.json.Json
                .parseToJsonElement(payload)
                .jsonObject["submissionId"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    fun row(
        request: QuestArenaSubmissionRequest,
        createdAtMs: Long = request.requestedAtMs,
    ): OutboxEntity =
        OutboxEntity(
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, request.submissionId),
            ownerUid = request.ownerUid,
            operation = OutboxOperations.SUBMIT_ARENA,
            payload = request.toPayload().toString(),
            entityRef = draftRef(request.draftId),
            // Версия едет колонкой очереди, а не полем тела: сверяет её приёмник до обработчика,
            // и ядро в тело не смотрит (AD-24). Прежний `localRevision` в теле был счётчиком
            // правок автора и к расхождению отношения не имел.
            expectedVersion = request.expectedVersion,
            state = OutboxState.WAITING.name,
            attemptCount = 0,
            nextRetryAtMs = 0L,
            lastError = null,
            createdAtMs = createdAtMs,
        )

    /**
     * Тело заявки.
     *
     * Имена полей — те же, что читает `normalizeRequest` на сервере: он собран для документа,
     * который раньше писался в Firestore напрямую, и переезд на приёмник не повод их менять.
     *
     * Ключа `localRevision` здесь нет намеренно (AD-12): версию назначает сервер, и счётчику
     * правок автора на проводе делать нечего. Он остаётся тем, чем был, — локальным числом.
     */
    private fun QuestArenaSubmissionRequest.toPayload(): JsonObject =
        buildJsonObject {
            put("submissionId", submissionId)
            put("draftId", draftId)
            put("ownerUid", ownerUid)
            put("requestedAtMs", requestedAtMs)
            put("targetShelf", targetShelf)
            put("targetLessonIds", buildJsonArray { targetLessonIds.forEach { add(JsonPrimitive(it)) } })
            // Полный состав черновика рядом с порцией: сервер снимает выпавшие урок, тему и раздел
            // только по нему, а по одной порции отличить удаление от «не в этой заявке» нельзя.
            // Три списка едут всегда, в том числе пустыми — молчания сервер не ждёт.
            put(
                "draftTree",
                buildJsonObject {
                    put("sections", buildJsonArray { draftTree.sections.forEach { add(JsonPrimitive(it)) } })
                    put("themes", buildJsonArray { draftTree.themes.forEach { add(JsonPrimitive(it)) } })
                    put("lessons", buildJsonArray { draftTree.lessons.forEach { add(JsonPrimitive(it)) } })
                },
            )
            put(
                "draft",
                buildJsonObject {
                    put("id", draft.id)
                    put("catalogId", draft.catalogId)
                    put("title", draft.title)
                    putNullable("description", draft.description)
                    put("defaultLanguage", draft.defaultLanguage)
                    put("defaultDifficulty", draft.defaultDifficulty)
                    putNullable("publicQuestId", draft.publicQuestId)
                    put("createdAtMs", draft.createdAtMs)
                    put("updatedAtMs", draft.updatedAtMs)
                },
            )
            put(
                "sections",
                buildJsonArray {
                    sections.forEach { section ->
                        add(
                            buildJsonObject {
                                put("id", section.id)
                                put("draftId", section.draftId)
                                put("title", section.title)
                                put("order", section.order)
                            },
                        )
                    }
                },
            )
            put(
                "themes",
                buildJsonArray {
                    themes.forEach { theme ->
                        add(
                            buildJsonObject {
                                put("id", theme.id)
                                put("draftId", theme.draftId)
                                put("sectionId", theme.sectionId)
                                put("title", theme.title)
                                put("order", theme.order)
                            },
                        )
                    }
                },
            )
            put(
                "lessons",
                buildJsonArray {
                    lessons.forEach { lesson ->
                        add(
                            buildJsonObject {
                                put("id", lesson.id)
                                put("draftId", lesson.draftId)
                                put("themeId", lesson.themeId)
                                put("title", lesson.title)
                                put("order", lesson.order)
                            },
                        )
                    }
                },
            )
            put("questions", buildJsonArray { questions.forEach { add(it.toJson()) } })
            put(
                "review",
                buildJsonObject {
                    put("isTested", review.isTested)
                    putNullable("testingScore", review.testingScore)
                    put("isLogicReviewed", review.isLogicReviewed)
                    putNullable("logicScore", review.logicScore)
                    put("isTranslationReviewed", review.isTranslationReviewed)
                    putNullable("translationScore", review.translationScore)
                    put(
                        "translatedLanguages",
                        buildJsonObject {
                            review.translatedLanguages.forEach { (language, level) -> put(language, level) }
                        },
                    )
                },
            )
        }

    private fun ArenaQuestionDto.toJson(): JsonObject =
        buildJsonObject {
            put("id", id)
            put("draftId", draftId)
            put("lessonId", lessonId)
            put("type", type)
            put("language", language)
            put("languageLevel", languageLevel)
            put("difficulty", difficulty)
            put("order", order)
            put("text", text)
            putNullable("imagePath", imagePath)
            put("payload", payload)
            put("updatedAtMs", updatedAtMs)
        }
}

/** Пусто — это `null`, а не пропущенное поле: разница на приёмнике значимая. */
private fun JsonObjectBuilder.putNullable(
    name: String,
    value: String?,
) {
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

private fun JsonObjectBuilder.putNullable(
    name: String,
    value: Double?,
) {
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

private fun JsonObjectBuilder.putNullable(
    name: String,
    value: Int?,
) {
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
}
