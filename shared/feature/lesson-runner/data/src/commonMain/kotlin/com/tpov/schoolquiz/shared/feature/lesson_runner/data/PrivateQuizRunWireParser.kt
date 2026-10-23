package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizOwnResult
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRun
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRunQuestion

/**
 * Разбор ответов приватного прогона (AD-29/AD-31, история 8.4) — строгий и без домыслов.
 *
 * Форма ответа — то, что отдаёт сервер: карта с именованными полями. Чужая форма — отказ
 * разбора, а не пустой прогон: пустой ответ на AD-8 не превращается в подтверждённый ноль.
 * Поля счёта читаются только когда они пришли: отсутствие — «сервер скрыл», и никаких нулей
 * вместо неизвестного (AD-21).
 */
object PrivateQuizRunWireParser {

    fun run(reply: Any?, expectedStartId: String): PrivateQuizRun {
        val map = reply.asMap()
        val startId = map.string("startId")
        require(startId == expectedStartId) {
            "PrivateQuizRunWireParser: reply belongs to start $startId, expected $expectedStartId"
        }
        val startedAtMs =
            map.long("startedAtMs")
                ?: throw IllegalArgumentException("PrivateQuizRunWireParser: startedAtMs is required")
        val deadlineMs =
            map.long("deadlineMs")
                ?: throw IllegalArgumentException("PrivateQuizRunWireParser: deadlineMs is required")
        val questions =
            (map["questions"] as? List<*>)
                ?.mapIndexed { index, raw ->
                    val question = raw.asMap("questions[$index]")
                    PrivateQuizRunQuestion(
                        id = question.string("id"),
                        order = (question.long("order") ?: index.toLong()).toInt(),
                        payload = question.string("payload"),
                    )
                }
                .orEmpty()
        return PrivateQuizRun(
            startId = startId,
            startedAtMs = startedAtMs,
            deadlineMs = deadlineMs,
            questions = questions,
        )
    }

    fun ownResult(reply: Any?, startId: String): PrivateQuizOwnResult {
        val map = reply.asMap()
        val status =
            when (map.string("status")) {
                "running" -> PrivateQuizAttemptStatus.RUNNING
                "terminal" -> PrivateQuizAttemptStatus.TERMINAL
                else -> throw IllegalArgumentException("PrivateQuizRunWireParser: unknown attempt status ${map["status"]}")
            }
        val reason =
            when (map.string("reason")) {
                null -> null
                "finished" -> PrivateQuizAttemptReason.FINISHED
                "timeout" -> PrivateQuizAttemptReason.TIMEOUT
                else -> throw IllegalArgumentException("PrivateQuizRunWireParser: unknown attempt reason ${map["reason"]}")
            }
        require(status == PrivateQuizAttemptStatus.TERMINAL || reason == null) {
            "PrivateQuizRunWireParser: a running attempt carries no reason"
        }
        val percent = (map.long("percentScore"))?.toInt()
        val correctCount = map.long("correctCount")?.toInt()
        val questionCount = map.long("questionCount")?.toInt()
        val questionScores =
            (map["questionScores"] as? List<*>)?.map { (it as? Number)?.toInt() ?: 0 }
        return PrivateQuizOwnResult(
            startId = startId,
            attemptStatus = status,
            reason = reason,
            finishedAtMs = map.long("finishedAtMs"),
            percentScore = percent,
            correctCount = correctCount,
            questionCount = questionCount,
            questionScores = questionScores,
        )
    }

    // ─── чтение сырых полей ─────────────────────────────────────────────────────────────────────

    private fun Any?.asMap(what: String = "reply"): Map<*, *> =
        this as? Map<*, *> ?: throw IllegalArgumentException("PrivateQuizRunWireParser: $what is not an object")

    private fun Map<*, *>.string(field: String): String =
        (this[field] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("PrivateQuizRunWireParser: $field is required")

    private fun Map<*, *>.long(field: String): Long? =
        when (val raw = this[field]) {
            null -> null
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: throw IllegalArgumentException("PrivateQuizRunWireParser: $field is not a number")
            else -> throw IllegalArgumentException("PrivateQuizRunWireParser: $field is not a number")
        }
}
