package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Настройки приватной ссылки, которые автор выбирает в форме (AD-29/UX-16, эпик 8, история 8.3).
 *
 * Дефолты нового квиза приняты владельцем и живут здесь, в доменной модели, а не в UI: лимит —
 * одна попытка, результат участнику скрыт, ссылка без конечной даты. Рантайм ссылки —
 * `enabled` и счётчик стартов — серверное слово (гейт `startPrivateQuizAttempt`), клиентским
 * полем ему места нет: подставленный в черновик `enabled: true` не открывал бы закрытую ссылку.
 *
 * @property attemptLimit целый лимит попыток, ≥ 1. Повышение допускает новые старты в пределах
 *   разницы, понижение не обрывает начатую попытку — считает сервер.
 * @property showResult видимость собственного результата участнику. Текущее значение
 *   применяется ко всем попыткам, включая прошлые; авторская статистика от него не зависит.
 * @property linkExpiresAtMs момент, ДО которого старт разрешён; `null` — «без срока», до ручного
 *   отключения. Отключить вручную можно и раньше заданного срока.
 */
data class PrivateQuizSettings(
    val attemptLimit: Int = DEFAULT_ATTEMPT_LIMIT,
    val showResult: Boolean = DEFAULT_SHOW_RESULT,
    val linkExpiresAtMs: Long? = null,
) {
    init {
        require(attemptLimit >= MIN_ATTEMPT_LIMIT) {
            "PrivateQuizSettings.attemptLimit must be >= $MIN_ATTEMPT_LIMIT, got $attemptLimit"
        }
        require(linkExpiresAtMs == null || linkExpiresAtMs >= 0) {
            "PrivateQuizSettings.linkExpiresAtMs must be null or a non-negative epoch ms, got $linkExpiresAtMs"
        }
    }

    /**
     * Проверка на сохранении: срок ссылки либо отсутствует, либо ещё в будущем (AD-29).
     *
     * Проверка структурных полей живёт в `init` — она про форму данных. Проверка «в будущем» —
     * про момент сохранения, и потому берёт часы параметром: init'у взять их неоткуда, а
     * подставлять часы внутрь модели значило бы делать её нетестируемой.
     */
    fun validateForSave(nowMs: Long): Boolean = linkExpiresAtMs == null || linkExpiresAtMs > nowMs

    companion object {
        const val DEFAULT_ATTEMPT_LIMIT: Int = 1
        const val DEFAULT_SHOW_RESULT: Boolean = false
        const val MIN_ATTEMPT_LIMIT: Int = 1

        /**
         * Технический идентификатор единственной засеянной категории «Приватные квизы».
         *
         * Название категории — продуктовое слово, идентификатор — технический (AD-29: «технические
         * IDs этим названием не меняются»); questType у неё REGULAR, потому что приватный квиз —
         * оцениваемый HARD-набор, а не опрос. Форма отличает приватную категорию именно по нему.
         */
        const val PRIVATE_QUIZ_CATALOG_ID: String = "private-quizzes-v1-catalog"

        /** Дефолты нового приватного квиза (AD-29): 1 попытка, результат скрыт, ссылка без срока. */
        val DEFAULTS: PrivateQuizSettings = PrivateQuizSettings()
    }
}
