package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAck
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamResultProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerDuration
import kotlinx.serialization.json.Json

/*
 * Правила экзаменационного прогона — ЗЕРКАЛА отображения, не вторые счётчики.
 *
 * Сервер считает и замораживает всё: ответные секунды каждого вопроса, агрегатный процент,
 * проход, звёзды, совет. Клиенту разрешено ровно три вещи, и все три живут здесь:
 *
 * 1. **Ответный таймер по all-HARD правилу** — `max(5, roundHalfUp(chars × 0.24))`, картинка
 *    стоит 100 символов — для обратного отсчёта между серверной выдачей и закреплённым ACK'ом
 *    дедлайном. Включая пять EASY-позиций темы: показанная сложность на коэффициент не влияет.
 *    Никакого examFactor 0.75 не существует. Авторитет — серверное `answerSeconds` в проекции
 *    вопроса; зеркало нужно только пока ACK ещё не закрепил точный дедлайн.
 * 2. **Линейные звёзды темы** — `floor((30·p + 50)/100)` десятых — тем же зеркальным статусом:
 *    сервер возвращает десятые, и клиентская формула существует для предзагрузочной валидности,
 *    не для пересчёта результата.
 * 3. **Разбор публичной половины вопроса** — только в `RedactedQuestionContent` и никогда в
 *    `QuestionContent`: правильные варианты отсутствуют структурно, а не скрыты флагом.
 *
 * Терминальный результат не пересчитывается вообще: [`renderResult`] проверяет диапазоны и
 * возвращает проекцию как есть.
 */

/** Ordinary-HARD коэффициент экзаменационного таймера. Обычный EASY 0.36 сюда не входит. */
const val EXAM_TIMER_K_HARD: Double = 0.24

/** Эквивалент картинки в символах — обычная конвенция подсчёта. */
const val EXAM_IMAGE_CHARS: Int = 100

/** Пол ответного времени, секунд. */
const val EXAM_MIN_SECONDS: Int = 5

/**
 * Ответные секунды экзаменационного вопроса по числу символов.
 *
 * `max(5, roundHalfUp(chars × 0.24))`: 100 символов → 24 секунды, 10 символов → пол 5 секунд.
 * Половина вверх — `floor(x + 0.5)`, как в Kotlin-зеркале `computeTimer`.
 *
 * Это ЗЕРКАЛО: сервер замораживает величину в сидинге, и расхождение часов не даёт клиенту ни
 * секунды — дедлайн ставит сервер.
 */
fun examAnswerSecondsFor(charsCount: Int): Int {
    require(charsCount >= 0) { "charsCount must be non-negative, got $charsCount" }
    val scaled = charsCount * EXAM_TIMER_K_HARD
    return maxOf(EXAM_MIN_SECONDS, (scaled + 0.5).toInt())
}

/**
 * Ответные секунды по содержимому вопроса, включая картинку — 100 символов.
 *
 * Показанная сложность в подписи стоит намеренно и на результат не влияет: пять EASY-позиций
 * темы отвечают по тому же ordinary-HARD правилу. Вызывающий обязан назвать сложность вслух —
 * так место вызова доказывает, что коэффициент от неё не зависит.
 */
fun examAnswerSeconds(
    shownDifficulty: String,
    charsCount: Int,
    hasImage: Boolean,
): Int {
    require(shownDifficulty == "EASY" || shownDifficulty == "HARD") {
        "shownDifficulty must be EASY or HARD, got $shownDifficulty"
    }
    return examAnswerSecondsFor(charsCount + if (hasImage) EXAM_IMAGE_CHARS else 0)
}

/**
 * Таймер отображения для бегущего отсчёта.
 *
 * Серверный `answerSeconds` из проекции авторитетен; зеркало используется только когда ACK ещё
 * не закрепил дедлайн и отсчёт ведётся от окна ожидания.
 */
fun examDisplayTimer(
    charsCount: Int,
    hasImage: Boolean,
): TimerDuration = TimerDuration(examAnswerSeconds("HARD", charsCount, hasImage))

/**
 * Линейные звёзды темы в десятых: floor((30·p + 50)/100).
 *
 * 0/50/100% → 0/1.5/3 звезды; 15% → 0.5; при захваченных 60% и 59%, и 60% показывают 1.8.
 * ЗЕРКАЛО: сервер возвращает десятые в результате, здесь формула существует для отображения
 * процентов, которые сервер ещё не разметил (например, в витрине доступности). Обычные звёзды
 * уроков это правило не задевает; финальные/курсовые звёзды — Q-EX-7, вне объёма.
 */
fun themeStarsTenthsMirror(percent: Int): Int {
    require(percent in 0..100) { "percent must be 0..100, got $percent" }
    return (30 * percent + 50) / 100
}

/**
 * Отрисовка терминального результата — без пересчёта.
 *
 * Проверяются диапазоны (тот же контракт, что несёт `ExamResultProjection.init`), значения
 * возвращаются как есть. Никакого локального процента, прохода или звёзд не существует.
 */
fun renderResult(result: ExamResultProjection): ExamResultProjection = result

/** Разбор публичной половины вопроса — только в редактированную иерархию. */
private val redactedJson = Json { ignoreUnknownKeys = true }

/**
 * Разбирает `publicPayload` в `RedactedQuestionContent`.
 *
 * `QuestionContent` здесь недостижим по построению: правильные варианты в публичной половине
 * отсутствуют, и попытка разобрать её как полный вопрос обязана упасть, а не выдать вопрос без
 * ответа под видом полного.
 *
 * @throws IllegalArgumentException если payload — не редактированная половина.
 */
fun parseRedactedExamPayload(publicPayload: String): RedactedQuestionContent {
    return redactedJson.decodeFromString(RedactedQuestionContent.serializer(), publicPayload)
}

/**
 * Вердикт ACK'а — чистая функция контракта «первый ACK закрепляет».
 *
 * - Закреплённого нет и окно не истекло: закрепить дедлайн от момента подтверждения, но не позже
 *   закрытия окна (опоздавший ACK не удлиняет час).
 * - Закреплённый есть: вернуть его же — ACK никогда не перезапускает и не удлиняет часы.
 * - Окно истекло без ACK'а: отказ — вопрос закроется неотвеченным решением сервера.
 *
 * @param boundForQuestion закрытие окна этого вопроса: первый-ACK разрыв (нулевой вопрос) или
 *   окно загрузки следующего вопроса.
 */
fun examAckVerdict(
    established: ExamAck?,
    nowMs: Long,
    boundForQuestion: Long?,
    answerSeconds: Int,
): ExamAckDecision {
    return when {
        established != null -> ExamAckDecision.Duplicate(established)
        boundForQuestion == null || nowMs > boundForQuestion -> ExamAckDecision.Expired
        else -> {
            val requested = nowMs + answerSeconds * 1000L
            val deadline = minOf(requested, boundForQuestion + answerSeconds * 1000L)
            ExamAckDecision.Establish(ExamAck(ackedAtMs = nowMs, answerDeadlineMs = deadline))
        }
    }
}

/** Итог чистого вердикта ACK'а. */
sealed interface ExamAckDecision {
    /** Первый ACK: дедлайн закреплён. */
    data class Establish(val ack: ExamAck) : ExamAckDecision

    /** Повтор: возвращается закреплённое, ничего не перезакрепляется. */
    data class Duplicate(val ack: ExamAck) : ExamAckDecision

    /** Окно истекло без подтверждения: вопрос закроет сервер. */
    data object Expired : ExamAckDecision
}
