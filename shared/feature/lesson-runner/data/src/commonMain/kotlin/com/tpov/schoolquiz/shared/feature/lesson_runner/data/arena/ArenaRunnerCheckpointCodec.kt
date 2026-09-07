package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointCompletion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.canonicalArenaCheckpointAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.initialArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.toCheckpointAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.toCheckpointDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.validateArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.validateArenaRunnerTransition
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RawAnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A strict, canonical, local-only format. No generic JSON blob can bypass the typed validator.
 * Canonical byte equality rejects unknown/duplicate fields, coerced primitives and duplicate sets.
 * The journal still owns its owner-generation gate and Room CAS; this codec grants no authority.
 */
class ArenaRunnerCheckpointCodec : ArenaJournalCheckpointCodec {
    private val completionAnswerJson = Json { encodeDefaults = true }

    override fun initial(
        snapshot: ArenaTicketSnapshot,
        snapshotSha256: String,
        request: ArenaInitialCheckpoint,
    ): String {
        val binding = ArenaRunnerCheckpointBinding(snapshot, snapshotSha256)
        return encode(initialArenaRunnerCheckpoint(binding, request), binding)
    }

    override fun validate(
        snapshot: ArenaTicketSnapshot,
        snapshotSha256: String,
        previousJson: String?,
        candidateJson: String,
        completionPayloadJson: String?,
    ) {
        val binding = ArenaRunnerCheckpointBinding(snapshot, snapshotSha256)
        val candidate = decode(candidateJson, binding)
        if (previousJson == null) {
            val initial =
                initialArenaRunnerCheckpoint(
                    binding,
                    ArenaInitialCheckpoint(
                        candidate.sessionMode,
                        candidate.seed,
                        candidate.questionStartedAtMs,
                        candidate.deadlineMs,
                    ),
                )
            require(candidate == initial) { "A fresh claim requires an initial checkpoint" }
        } else {
            validateArenaRunnerTransition(decode(previousJson, binding), candidate, binding)
        }
        completionPayloadJson?.let { validateCompletion(candidate, binding, it) }
    }

    fun encode(
        checkpoint: ArenaRunnerCheckpoint,
        binding: ArenaRunnerCheckpointBinding,
    ): String {
        validateArenaRunnerCheckpoint(checkpoint, binding)
        val value =
            buildJsonObject {
                put("schemaVersion", checkpoint.schemaVersion)
                put("ownerUid", checkpoint.ownerUid)
                put("ticketId", checkpoint.ticketId)
                put("attemptId", checkpoint.attemptId)
                put("snapshotSha256", checkpoint.snapshotSha256)
                put("sessionMode", checkpoint.sessionMode.name)
                put("seed", checkpoint.seed)
                put("phase", checkpoint.phase.name)
                put("indexInPool", checkpoint.indexInPool)
                put("questionStartedAtMs", checkpoint.questionStartedAtMs)
                put("deadlineMs", checkpoint.deadlineMs)
                put("currentDraft", checkpoint.currentDraft?.let { answerJson(it.toCheckpointAnswer()) } ?: JsonNull)
                put(
                    "pendingFeedback",
                    checkpoint.pendingFeedback?.let { feedback ->
                        buildJsonObject {
                            put("questionId", feedback.questionId)
                            put("codeAnswerIndex", feedback.codeAnswerIndex)
                            put("answer", answerJson(feedback.answer.toCheckpointAnswer()))
                        }
                    } ?: JsonNull,
                )
                put(
                    "acceptedAnswers",
                    JsonArray(
                        checkpoint.acceptedAnswers.map { raw ->
                            buildJsonObject {
                                put("questionId", raw.questionId.value)
                                put("codeAnswerIndex", raw.codeAnswerIndex)
                                put("answer", answerJson(raw.answer))
                                put("answeredAtMs", raw.answeredAtMs)
                                put("durationMs", raw.durationMs)
                                put("wasTimeout", raw.wasTimeout)
                            }
                        },
                    ),
                )
                put("chargeClaims", checkpoint.chargeClaims.raw)
                put("endedAtMs", checkpoint.endedAtMs?.let(::JsonPrimitive) ?: JsonNull)
            }.toString()
        require(value.encodeToByteArray().size <= MAX_ENCODED_BYTES) { "Checkpoint exceeds 1 MiB; never truncate" }
        return value
    }

    fun decode(
        payload: String,
        binding: ArenaRunnerCheckpointBinding,
    ): ArenaRunnerCheckpoint {
        val root = readObject(payload)
        val pending =
            root.required("pendingFeedback").takeUnless { it == JsonNull }?.objectValue()?.let {
                ArenaPendingFeedback(
                    it.text("questionId"),
                    it.integer("codeAnswerIndex"),
                    readAnswer(it.required("answer")).toCheckpointDraft(),
                )
            }
        val answers = root.required("acceptedAnswers").arrayValue()
        require(answers.size <= ArenaRunnerCheckpoint.MAX_QUESTIONS)
        val checkpoint =
            ArenaRunnerCheckpoint(
                root.integer("schemaVersion"), root.text("ownerUid"), root.text("ticketId"), root.text("attemptId"),
                root.text("snapshotSha256"), SessionMode.valueOf(root.text("sessionMode")), root.long("seed"),
                ArenaRunnerPhase.valueOf(root.text("phase")), root.integer("indexInPool"),
                root.long("questionStartedAtMs"), root.long("deadlineMs"),
                root.required("currentDraft").takeUnless { it == JsonNull }?.let { readAnswer(it).toCheckpointDraft() },
                pending,
                answers.map { element ->
                    element.objectValue().let { row ->
                        RawAnsweredQuestion(
                            QuestionId(row.text("questionId")),
                            row.integer("codeAnswerIndex"),
                            readAnswer(row.required("answer")),
                            row.long("answeredAtMs"),
                            row.long("durationMs"),
                            row.boolean("wasTimeout"),
                        )
                    }
                },
                ChargeClaimMask(root.text("chargeClaims")),
                root.required("endedAtMs").takeUnless { it == JsonNull }?.strictLong(),
            )
        require(encode(checkpoint, binding) == payload) { "Noncanonical or unknown checkpoint fields" }
        return checkpoint
    }

    fun validateTransition(
        previous: ArenaRunnerCheckpoint,
        next: ArenaRunnerCheckpoint,
        binding: ArenaRunnerCheckpointBinding,
    ) = validateArenaRunnerTransition(previous, next, binding)

    private fun validateCompletion(
        checkpoint: ArenaRunnerCheckpoint,
        binding: ArenaRunnerCheckpointBinding,
        payload: String,
    ) {
        val intent = arenaCheckpointCompletion(checkpoint, binding)
        val body = readObject(payload)
        val context = binding.snapshot.contentContext
        val contextFields = if (context == null) emptySet() else setOf("catalogId", "questId", "sectionId", "themeId")
        require(body.keys == COMPLETION_FIELDS + contextFields)
        require(body.text("ownerUid") == intent.identity.userId && body.text("userId") == intent.identity.userId)
        require(body.text("ticketId") == checkpoint.ticketId && body.text("attemptId") == intent.identity.id.value)
        require(
            body.text(
                "lessonId",
            ) == intent.identity.lessonId.value && body.long("lessonVersion") == intent.identity.lessonVersion,
        )
        require(
            body.text("difficulty") == intent.identity.mode.name && body.text("scope") == "public" &&
                body.text("sourceShelf") == "arena" && body.text("completionKind") == "COMPLETED",
        )
        require(body.long("completedAtMs") == intent.endedAtMs && body.long("createdAtMs") == intent.endedAtMs)
        require(body.text("chargeClaims") == intent.claims.raw)
        if (context != null) {
            require(
                body.text("catalogId") == context.catalogId && body.text("questId") == context.questId &&
                    body.text("sectionId") == context.sectionId && body.text("themeId") == context.themeId,
            )
        }
        val served = body.required("served").arrayValue()
        require(served.size == intent.served.size)
        served.zip(intent.served).forEach { (element, expected) ->
            val row = element.objectValue()
            require(row.keys == setOf("questionId", "codeAnswerIndex"))
            require(
                row.text(
                    "questionId",
                ) == expected.questionId.value && row.integer("codeAnswerIndex") == expected.codeAnswerIndex,
            )
        }
        val rows = body.required("answers").arrayValue()
        require(rows.size == intent.answers.size)
        rows.zip(intent.answers).forEach { (element, expected) ->
            val row = element.objectValue()
            require(
                row.keys ==
                    setOf(
                        "questionId",
                        "codeAnswerIndex",
                        "answerPayload",
                        "answeredAtMs",
                        "durationMs",
                        "wasTimeout",
                    ),
            )
            require(
                row.text(
                    "questionId",
                ) == expected.questionId.value && row.integer("codeAnswerIndex") == expected.codeAnswerIndex,
            )
            require(
                row.long("answeredAtMs") == expected.answeredAtMs && row.long("durationMs") == expected.durationMs &&
                    row.boolean("wasTimeout") == expected.wasTimeout,
            )
            val answer = readObject(row.text("answerPayload"))
            require(readAnswer(answer) == expected.answer)
            require(
                row.text(
                    "answerPayload",
                ) == completionAnswerJson.encodeToString(UserAnswer.serializer(), expected.answer),
            ) { "Terminal answer bytes must match the durable canonical checkpoint" }
        }
    }

    private fun answerJson(answer: UserAnswer): JsonElement =
        Json.encodeToJsonElement(UserAnswer.serializer(), answer.canonicalArenaCheckpointAnswer())

    private fun readAnswer(value: JsonElement): UserAnswer {
        val answer = Json.decodeFromJsonElement(UserAnswer.serializer(), value)
        // Also rejects duplicate set entries and unexpected nested fields after deserialization.
        require(Json.encodeToJsonElement(UserAnswer.serializer(), answer) == value)
        return answer
    }

    private fun readObject(payload: String): JsonObject {
        require(payload.encodeToByteArray().size <= MAX_ENCODED_BYTES)
        requireBoundedDepth(payload)
        val value = Json.parseToJsonElement(payload).objectValue()
        // Only our canonical local writer produces this format. No duplicate object key survives.
        require(value.toString() == payload) { "Noncanonical JSON" }
        return value
    }

    companion object {
        const val MAX_ENCODED_BYTES = 1_048_576
        private const val MAX_JSON_DEPTH = 16
        private val COMPLETION_FIELDS =
            setOf(
                "ownerUid", "userId", "ticketId", "attemptId", "lessonId", "lessonVersion",
                "difficulty", "scope", "sourceShelf", "completionKind", "completedAtMs", "createdAtMs",
                "chargeClaims", "answers", "served",
            )

        private fun requireBoundedDepth(payload: String) {
            var depth = 0
            var quoted = false
            var escaped = false
            payload.forEach { char ->
                if (quoted) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> quoted = false
                    }
                } else {
                    when (char) {
                        '"' -> quoted = true
                        '{', '[' -> {
                            depth++
                            require(depth <= MAX_JSON_DEPTH)
                        }
                        '}', ']' -> {
                            depth--
                            require(depth >= 0)
                        }
                    }
                }
            }
            require(depth == 0 && !quoted)
        }
    }
}

private fun JsonElement.objectValue() = this as? JsonObject ?: throw IllegalArgumentException("Expected object")

private fun JsonElement.arrayValue() = this as? JsonArray ?: throw IllegalArgumentException("Expected array")

private fun JsonObject.required(key: String) =
    get(
        key,
    ) ?: throw IllegalArgumentException("Missing checkpoint field $key")

private fun JsonObject.text(key: String): String {
    val primitive = required(key) as? JsonPrimitive ?: throw IllegalArgumentException("Expected string")
    require(primitive.isString)
    return primitive.content
}

private fun JsonElement.strictLong(): Long {
    val primitive = this as? JsonPrimitive ?: throw IllegalArgumentException("Expected integer")
    require(!primitive.isString)
    return requireNotNull(primitive.longOrNull)
}

private fun JsonObject.long(key: String) = required(key).strictLong()

private fun JsonObject.integer(key: String): Int =
    long(key).also {
        require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
    }.toInt()

private fun JsonObject.boolean(key: String): Boolean {
    val primitive = required(key) as? JsonPrimitive ?: throw IllegalArgumentException("Expected boolean")
    require(!primitive.isString)
    return requireNotNull(primitive.booleanOrNull)
}
