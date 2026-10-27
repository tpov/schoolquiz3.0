package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Цитата платы за проверку — то, что диалог подачи называет автору до отправки (история 4.11).
 *
 * Показ, а не власть: сервер считает цитату той же формулой, что и плату приёмки, но платит
 * заявка по расчёту приёмки из тела реальной подачи. Тариф повторяет решение сервера
 * (`initial` — первая подача, `recheck` — у квеста уже был оплаченный цикл или публикация).
 */
data class ReviewFeeQuote(
    val priorCycle: Boolean,
    val tariff: String,
    val feeNolics: Int,
    val tariffRevision: Int,
)
