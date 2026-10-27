package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * Вид заявки подарка — те же строки, что понимает `submitGiftBoxOperation`.
 *
 * Четвёртый вид сервера (OPEN с `openingId`) сюда не входит: открытия едут очередью как
 * `economy.OPEN_GIFT_BOX` (7.5), эти три — прямые вызываемые с локальным operationId-интентом.
 */
enum class GiftClaimKind { VISIT, OFFLINE_STREAK, PREMIUM_DAILY }

/**
 * Статус ответа сервера — исход, а не ошибка.
 *
 * CLAIMED — начислено; NO_CHANGE — повтор в тех же сутках или заявлять нечего; STALE_BASE —
 * чужое слово ушло вперёд, слово сервера выигрывает; PREMIUM_REQUIRED — премиум истёк;
 * DAILY_LIMIT — суточный слот уже занят. Каждому — свои нейтральные слова на экране, и ни один
 * не показывается сбоем.
 */
enum class GiftClaimStatus { CLAIMED, NO_CHANGE, STALE_BASE, PREMIUM_REQUIRED, DAILY_LIMIT }

/**
 * Разобранный ответ сервера на заявку — строго из конверта `{replayed, result}`.
 *
 * Повтор после смерти процесса приезжает с теми же числами и пометкой [replayed]; для экрана
 * они неразличимы — начисление одно, и оно уже случилось. Числа — последнее серверное слово о
 * коробках и премиуме; клиент показывает их, а не достраивает своё.
 */
data class GiftClaimOutcome(
    val kind: GiftClaimKind,
    val status: GiftClaimStatus,
    val replayed: Boolean,
    val boxCount: Int,
    val boxStreakDays: Int,
    val nextBoxAtMs: Long,
    val nextBonusBoxAtMs: Long,
    val earnedBoxes: Int,
    val premiumUntilMs: Long,
)

/** Чем кончилась попытка заявить — по именам, а не текстом исключения. */
enum class GiftClaimFailureReason {
    /** Нет связи: намерение не создавалось, повтор на входе всё сделает. */
    OFFLINE,

    /** Некому заявлять: аккаунт не определён или сменился посреди запроса. */
    NO_ACCOUNT,

    /** Ответа нет: сервер не решил ничего, тот же operationId уедет повтором. */
    TRANSPORT,

    /**
     * Сохранённое ТЕЛО заявки не читается ДО вызова: запрос не покидал устройство, решения
     * сервера для него не существует в принципе — намерение отпускается (карантин).
     *
     * Направление важно и запинено типом: это НЕ ответный отказ — [ENVELOPE_INVALID] о ОТВЕТЕ
     * держит намерение, потому что сервер мог решить. Перепутать направления — регрессия денег.
     */
    REQUEST_INVALID,

    /** Конверт не разобрался: решение сервера потерять нельзя, намерение остаётся. */
    ENVELOPE_INVALID,

    /** operationId уже скреплён с другим телом или владельцем: повторять вслепую нельзя. */
    CONFLICTING_REPLAY,
}

class GiftClaimException(
    val reason: GiftClaimFailureReason,
    val kind: GiftClaimKind? = null,
    val operationId: String? = null,
    val ownerUid: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause)
