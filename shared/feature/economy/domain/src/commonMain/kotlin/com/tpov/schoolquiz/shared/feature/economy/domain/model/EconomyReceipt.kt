package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * Квитанция сервера: что начислено и почему — одним документом на начисление.
 *
 * Текст объяснения сюда не входит: он приходит ключом [messageKey] с [params] и собирается
 * устройством на языке игрока — сервер языка игрока не знает, а украинский интерфейс обязателен
 * по закону. [amount] со знаком и [currency] — сумма движения; [createdAtMs] — момент сервера.
 */
data class EconomyReceipt(
    val id: String,
    val kind: String,
    val messageKey: String,
    val params: Map<String, String>,
    val amount: Long,
    val currency: String,
    val createdAtMs: Long,
)
