package com.tpov.schoolquiz.platform.firebase.push

/**
 * Событие, о котором сервер сообщает уведомлением.
 *
 * @property eventType имя вида `<фича>.<СОБЫТИЕ>` — то же пространство имён, что у `entity_ref`
 *   очереди (NFR4). Обе стороны обязаны писать его одинаково: реестр на сервере живёт в
 *   `functions/push-notifications.js`, и незнакомое имя здесь означает лишь «синхронизироваться
 *   молча».
 */
data class PushEvent(
    val eventType: String,
) {
    /** Фича, которой принадлежит событие, — текст до первой точки. */
    val feature: String get() = eventType.substringBefore('.')

    /**
     * Ключ строковых ресурсов заголовка и текста: `<ключ>_title`, `<ключ>_text`.
     *
     * Ключ выводится из имени события, а не задаётся отдельно: имя ресурса не может содержать
     * точку, поэтому «ключ, равный `eventType`» здесь значит «выводимый из него без вариантов».
     */
    val stringKey: String get() = STRING_PREFIX + eventType.lowercase().replace('.', '_')

    val titleKey: String get() = stringKey + "_title"
    val textKey: String get() = stringKey + "_text"

    private companion object {
        const val STRING_PREFIX = "push_"
    }
}

/**
 * Реестр событий, на которые приходит push (AD-20, Story 10.3).
 *
 * Полезная нагрузка сообщения — повод, а не данные: три поля `eventType`, `entityRef`,
 * `targetUid`, и ни одно из них приложение не применяет к своим таблицам. Получив push,
 * приложение синхронизируется обычным путём, а реестр нужен только чтобы решить, показывать ли
 * уведомление и какими словами.
 */
object PushEvents {
    /** Квест продвинулся: заявка на арену прошла, квест опубликован. */
    val QUEST_PUBLISHED = PushEvent("quest_authoring.QUEST_PUBLISHED")

    /** Предложение прошло проверку и доехало до автора цели (AD-33, история 4.25). */
    val QUEST_PROPOSAL_RECEIVED = PushEvent("quest_authoring.QUEST_PROPOSAL_RECEIVED")

    /** Автор цели решил предложение; исход видит предложивший (история 4.25). */
    val QUEST_PROPOSAL_DECIDED = PushEvent("quest_authoring.QUEST_PROPOSAL_DECIDED")

    /**
     * Рейтинг автора изменился — с ним уровень квалификации (ADR-0006). Имя — серверное: реестр в
     * `functions/push-notifications.js` единственный источник, а клиентская копия обязана ему
     * буква в букву; расхождение ловит `PushEventsRegistryTest`.
     */
    val AUTHOR_RATING_CHANGED = PushEvent("qualification.AUTHOR_RATING_CHANGED")

    /** Верификация одобрена. */
    val VERIFICATION_APPROVED = PushEvent("profile.VERIFICATION_APPROVED")

    val all: List<PushEvent> = listOf(
        QUEST_PUBLISHED,
        QUEST_PROPOSAL_RECEIVED,
        QUEST_PROPOSAL_DECIDED,
        AUTHOR_RATING_CHANGED,
        VERIFICATION_APPROVED,
    )

    /**
     * Фичи, чьи имена допустимы в `eventType` и `entityRef` (NFR4) — те же, что стоят перед
     * точкой в операциях очереди.
     */
    val knownFeatures: Set<String> = setOf("lesson_runner", "quest_authoring", "profile", "quest", "qualification")

    fun byType(eventType: String?): PushEvent? = all.firstOrNull { it.eventType == eventType }
}
