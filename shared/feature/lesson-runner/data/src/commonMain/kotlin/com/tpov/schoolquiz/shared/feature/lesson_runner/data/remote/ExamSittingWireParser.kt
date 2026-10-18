package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAck
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAwaitingAck
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAckOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamQuestionProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamResultProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingRules
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamShownDifficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamStartOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamFetchOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSubmitOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAnswerReceipt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Строгий разбор проводных фактов экзаменационного сидинга — по образцу
 * `QuestionWindowWireParser`.
 *
 * Отсутствующее или испорченное поле — «неизвестно», никогда не подставляется нулём. Лишние и
 * недостающие ключи ловятся одинаково: форма ответа — часть контракта, дрейф формы не читается
 * молча. Приватное не приезжает по определению: ключ канонического содержимого, ключа ответа или
 * цифр в публичной форме делает её испорченной, а не доверенной частично.
 */
class ExamSittingWireParser {

    /** Разбор ответа `startThemeTest` / `startFinalExam`: сидинг, восстановление или отказ. */
    fun parseStartOutcome(wire: String): ExamStartOutcome = fail { parseStartOutcomeOrThrow(wire) }

    /** Разбор ответа `acknowledgeQuestion`. */
    fun parseAckOutcome(wire: String): ExamAckOutcome = fail { parseAckOutcomeOrThrow(wire) }

    /** Разбор ответа `submitAnswer`. */
    fun parseSubmitOutcome(wire: String): ExamSubmitOutcome = fail { parseSubmitOutcomeOrThrow(wire) }

    /** Разбор ответа `finishSitting`: запрошено или уже закрыто — оба безопасны для повтора. */
    fun parseFinishOutcome(wire: String): ExamFinishVerdict = fail { parseFinishOutcomeOrThrow(wire) }

    /** Разбор ответа `fetchSittingResult`: открытое состояние или терминальный результат. */
    fun parseFetchOutcome(wire: String): ExamFetchOutcome = fail { parseFetchOutcomeOrThrow(wire) }

    private fun parseStartOutcomeOrThrow(wire: String): ExamStartOutcome {
        val root = objectValue(wire)
        return when {
            root.boolean("refused") == true -> {
                root.exactWith(required = listOf("refused", "kind"),
                    optional = listOf("reason", "availableAtMs"))
                ExamStartOutcome.Refused(refusal(root))
            }
            root.boolean("recovered") == true -> {
                root.exactWith(
                    required = listOf(
                        "recovered", "schemaVersion", "sittingId", "mode", "assessmentId",
                        "state", "currentIndex", "totalQuestions", "rules", "serverNowMs",
                    ),
                    optional = listOf("nextQuestion"),
                )
                ExamStartOutcome.Recovered(sittingProjection(root))
            }
            else -> {
                root.exactWith(
                    required = listOf(
                        "recovered", "debited", "lifePoints", "schemaVersion", "sittingId",
                        "mode", "assessmentId", "state", "currentIndex", "totalQuestions",
                        "rules", "serverNowMs",
                    ),
                    optional = listOf("nextQuestion"),
                )
                ExamStartOutcome.Started(
                    debited = root.requiredInt("debited"),
                    sitting = sittingProjection(root),
                )
            }
        }
    }

    private fun refusal(root: JsonObject): ExamRefusal = when (root.requiredText("kind")) {
        "Locked" -> ExamRefusal.Locked(root.text("reason") ?: "LOCKED")
        "ResultsNotSynced" -> ExamRefusal.ResultsNotSynced
        "Cooldown" -> ExamRefusal.Cooldown(root.requiredWhole("availableAtMs"))
        "InsufficientCharge" -> ExamRefusal.InsufficientCharge
        "PoolTooSmall" -> ExamRefusal.PoolTooSmall
        "unavailable" -> ExamRefusal.Unavailable(root.text("reason") ?: "UNAVAILABLE")
        else -> invalid()
    }

    private fun parseAckOutcomeOrThrow(wire: String): ExamAckOutcome {
        val root = objectValue(wire)
        if (root.boolean("acknowledged") != true) {
            root.exact("acknowledged", "reason", "ack")
            return ExamAckOutcome.Refused(root.requiredText("reason"))
        }
        root.exact(
            "acknowledged", "duplicate", "ack", "index", "questionId", "revision",
            "shownDifficulty", "answerSeconds", "publicPayload", "serverNowMs",
        )
        return ExamAckOutcome.Acknowledged(
            ack = root.obj("ack")?.let(::ack) ?: invalid(),
            duplicate = root.boolean("duplicate") == true,
        )
    }

    private fun parseSubmitOutcomeOrThrow(wire: String): ExamSubmitOutcome {
        val root = objectValue(wire)
        if (root.boolean("accepted") != true) {
            root.exactWith(
                required = listOf(
                    "accepted", "reason", "schemaVersion", "sittingId", "mode", "assessmentId",
                    "state", "currentIndex", "totalQuestions", "rules", "serverNowMs",
                ),
                optional = listOf("nextQuestion"),
            )
            return ExamSubmitOutcome.Refused(root.requiredText("reason"))
        }
        root.exactWith(
            required = listOf(
                "accepted", "duplicate", "receipt", "schemaVersion", "sittingId", "mode",
                "assessmentId", "state", "currentIndex", "totalQuestions", "rules",
                "serverNowMs",
            ),
            optional = listOf("nextQuestion"),
        )
        val receiptObject = root.obj("receipt") ?: invalid()
        receiptObject.exact("receiptId", "sittingId", "index", "revision", "receivedAtMs")
        val receipt = ExamAnswerReceipt(
            receiptId = receiptObject.requiredText("receiptId"),
            sittingId = receiptObject.requiredText("sittingId"),
            index = receiptObject.requiredInt("index"),
            revision = receiptObject.requiredWhole("revision"),
            receivedAtMs = receiptObject.requiredWhole("receivedAtMs"),
        )
        return ExamSubmitOutcome.Submitted(
            receipt = receipt,
            duplicate = root.boolean("duplicate") == true,
            sitting = sittingProjection(root),
        )
    }

    private fun parseFinishOutcomeOrThrow(wire: String): ExamFinishVerdict {
        val root = objectValue(wire)
        return when (root.boolean("alreadyClosed")) {
            true -> {
                root.exact("sittingId", "state", "alreadyClosed")
                ExamFinishVerdict(
                    state = root.requiredText("state"),
                    alreadyRequested = false,
                    alreadyClosed = true,
                )
            }
            else -> {
                root.exact("sittingId", "state", "alreadyRequested")
                ExamFinishVerdict(
                    state = root.requiredText("state"),
                    alreadyRequested = root.boolean("alreadyRequested") == true,
                    alreadyClosed = false,
                )
            }
        }
    }

    private fun parseFetchOutcomeOrThrow(wire: String): ExamFetchOutcome {
        val root = objectValue(wire)
        root.exact("sittingId", "state", "result", "open")
        return when (root.boolean("open")) {
            true -> ExamFetchOutcome.Open(
                sittingId = root.requiredText("sittingId"),
                state = sittingState(root.requiredText("state")),
            )
            else -> ExamFetchOutcome.Terminal(
                resultProjection(root.obj("result") ?: invalid()),
            )
        }
    }

    private fun resultProjection(result: JsonObject): ExamResultProjection {
        result.exact(
            "schemaVersion", "sittingId", "mode", "assessmentId", "resultVersion", "percent",
            "passed", "starsTenths", "advice", "closedAtMs", "nextAvailableAtMs",
        )
        val advice = (result["advice"] as? kotlinx.serialization.json.JsonArray)
            ?.map { entry ->
                (entry as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
                    ?: invalid()
            }
            ?: invalid()
        return ExamResultProjection(
            sittingId = result.requiredText("sittingId"),
            mode = examMode(result.requiredText("mode")),
            assessmentId = result.requiredText("assessmentId"),
            resultVersion = result.requiredInt("resultVersion"),
            percent = result.requiredInt("percent"),
            passed = result.boolean("passed") ?: invalid(),
            starsTenths = result.int("starsTenths"),
            advice = advice,
            closedAtMs = result.requiredWhole("closedAtMs"),
            nextAvailableAtMs = result.whole("nextAvailableAtMs"),
        )
    }

    private fun ack(ackObject: JsonObject): ExamAck {
        ackObject.exact("ackedAtMs", "answerDeadlineMs")
        return ExamAck(
            ackedAtMs = ackObject.requiredWhole("ackedAtMs"),
            answerDeadlineMs = ackObject.requiredWhole("answerDeadlineMs"),
        )
    }

    private fun sittingProjection(root: JsonObject): ExamSittingProjection {
        val rules = root.obj("rules") ?: invalid()
        rules.exact(
            "nextQuestionWindowSeconds", "firstAckGapSeconds", "timerRule", "timerRuleVersion",
        )
        return ExamSittingProjection(
            schemaVersion = root.int("schemaVersion") ?: invalid(),
            sittingId = root.requiredText("sittingId"),
            mode = examMode(root.requiredText("mode")),
            assessmentId = root.requiredText("assessmentId"),
            state = sittingState(root.requiredText("state")),
            currentIndex = root.requiredInt("currentIndex"),
            totalQuestions = root.requiredInt("totalQuestions"),
            rules = ExamSittingRules(
                nextQuestionWindowSeconds = rules.requiredInt("nextQuestionWindowSeconds"),
                firstAckGapSeconds = rules.requiredInt("firstAckGapSeconds"),
                timerRule = rules.requiredText("timerRule"),
                timerRuleVersion = rules.requiredInt("timerRuleVersion"),
            ),
            nextQuestion = root.obj("nextQuestion")?.let(::questionProjection),
        )
    }

    private fun questionProjection(question: JsonObject): ExamQuestionProjection {
        question.exact(
            "index", "questionId", "revision", "shownDifficulty", "answerSeconds",
            "publicPayload", "ack", "serverNowMs",
        )
        val ackObject = question.obj("ack") ?: invalid()
        return ExamQuestionProjection(
            index = question.requiredInt("index"),
            questionId = question.requiredText("questionId"),
            revision = question.requiredWhole("revision"),
            shownDifficulty = when (question.requiredText("shownDifficulty")) {
                "EASY" -> ExamShownDifficulty.EASY
                "HARD" -> ExamShownDifficulty.HARD
                else -> invalid()
            },
            answerSeconds = question.requiredInt("answerSeconds"),
            publicPayload = question.requiredText("publicPayload"),
            ack = if (ackObject.boolean("awaitingAck") == true) {
                ackObject.exact("awaitingAck", "windowDeadlineMs")
                null
            } else {
                ack(ackObject)
            },
            awaitingAck = if (ackObject.boolean("awaitingAck") == true) {
                ExamAwaitingAck(windowDeadlineMs = ackObject.whole("windowDeadlineMs"))
            } else {
                null
            },
            serverNowMs = question.requiredWhole("serverNowMs"),
        )
    }

    private fun examMode(raw: String): ExamMode = when (raw) {
        "ThemeTest" -> ExamMode.THEME_TEST
        "FinalExam" -> ExamMode.FINAL_EXAM
        else -> invalid()
    }

    private fun sittingState(raw: String): ExamSittingState = when (raw) {
        "OPEN" -> ExamSittingState.OPEN
        "FINISH_REQUESTED" -> ExamSittingState.FINISH_REQUESTED
        "CLOSED" -> ExamSittingState.CLOSED
        else -> invalid()
    }

    private fun objectValue(wire: String): JsonObject =
        Json.parseToJsonElement(wire) as? JsonObject ?: invalid()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.exact(vararg keys: String) {
        val expected = keys.toSet()
        // Приватные ключи в публичном ответе — испорченная форма, а не лишняя информация.
        if (keys.size != expected.size || this.keys != expected) invalid()
    }

    /**
     * Форма с опциональными ключами: обязательные присутствуют всегда, присутствующие — только
     * из известного списка. Опциональность — часть контракта, а не снисхождение к дрейфу.
     */
    private fun JsonObject.exactWith(required: List<String>, optional: List<String> = emptyList()) {
        val allowed = (required + optional).toSet()
        if (!this.keys.containsAll(required.toSet()) || !allowed.containsAll(this.keys)) invalid()
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun JsonObject.whole(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?.takeIf { it in 0..MAX_SAFE_SERVER_INTEGER }

    private fun JsonObject.requiredText(key: String): String =
        text(key)?.takeIf { it.isNotBlank() } ?: invalid()

    private fun JsonObject.requiredInt(key: String): Int = int(key) ?: invalid()

    private fun JsonObject.requiredWhole(key: String): Long = whole(key) ?: invalid()

    private fun invalid(): Nothing = throw MalformedExamWire()

    private inline fun <T> fail(block: () -> T): T =
        runCatching { block() }.getOrElse { failure ->
            if (failure is MalformedExamWire) throw failure else throw MalformedExamWire()
        }

    /** Маркер испорченной проводной формы; границы транспорта переводят его в типовой отказ. */
    class MalformedExamWire : Exception()

    private companion object {
        const val MAX_SAFE_SERVER_INTEGER = 9_007_199_254_740_991L
    }
}
