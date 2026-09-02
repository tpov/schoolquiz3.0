package com.tpov.schoolquiz.shared.feature.economy.domain.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Загрузочная копия обязана совпадать с тем, чем вырождается сервер.
 *
 * Устройство, которое ещё ни разу не синхронизировалось, считает по этой копии, а сервер при
 * отсутствующем или испорченном документе — по своим начальным значениям. Разойдись они, и первая
 * же попытка на новом устройстве была бы оценена в двух местах по-разному: игрок увидел бы одну
 * цену, а списали бы другую, и объяснить это было бы нечем.
 *
 * Поэтому источник один — `config/economy-constants.json`, — и обе стороны его читают.
 *
 * JVM-тест, а не общий: файл с диска общий код не видит. Разбор минимальный — тащить зависимость
 * на JSON ради десятка полей дороже, чем прочитать их.
 */
class EconomyConstantsParityTest {

    private val text: String by lazy {
        val file = File("../../../../config/economy-constants.json")
        assertTrue(file.exists(), "нет общего файла настроек: ${file.absolutePath}")
        file.readText()
    }

    private fun long(path: String): Long =
        Regex(""""$path"\s*:\s*(-?\d+)""").find(text)?.groupValues?.get(1)?.toLong()
            ?: error("нет поля $path в общем файле")

    private fun bool(path: String): Boolean =
        Regex(""""$path"\s*:\s*(true|false)""").find(text)?.groupValues?.get(1)?.toBoolean()
            ?: error("нет поля $path в общем файле")

    private fun section(name: String): String =
        Regex(""""$name"\s*:\s*\{(.*?)\n  }""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1)
            ?: error("нет раздела $name в общем файле")

    private fun rulesFrom(name: String): ChargeRules {
        val body = section(name)
        fun number(field: String) =
            Regex(""""$field"\s*:\s*(-?\d+)""").find(body)?.groupValues?.get(1)?.toLong()
                ?: error("нет поля $field в разделе $name")
        val rungs =
            Regex(""""priceLadder"\s*:\s*\[([^]]*)]""").find(body)?.groupValues?.get(1)
                ?.split(",")?.map { it.trim().toLong() }
                ?: error("нет лестницы цен в разделе $name")
        val currency =
            Regex(""""currency"\s*:\s*"([A-Z_]+)"""").find(body)?.groupValues?.get(1)
                ?: error("нет валюты в разделе $name")
        val settled =
            Regex(""""requiresSettledAccount"\s*:\s*(true|false)""").find(body)?.groupValues?.get(1)
                ?.toBoolean() ?: error("нет requiresSettledAccount в разделе $name")
        return ChargeRules(
            maxOwned = number("maxOwned").toInt(),
            regenMs = number("regenMs"),
            priceLadder = rungs,
            currency = ShopCurrency.valueOf(currency),
            requiresSettledAccount = settled,
            premiumRegenDivisor = number("premiumRegenDivisor").toInt(),
        )
    }

    @Test
    fun `given the shared file then the bootstrap copy matches it field for field`() {
        val bootstrap = EconomyConstants.BOOTSTRAP

        assertEquals(long("version"), bootstrap.version)
        assertEquals(rulesFrom("standard"), bootstrap.standard)
        assertEquals(rulesFrom("plasma"), bootstrap.plasma)
        assertEquals(long("clockSkewToleranceMs"), bootstrap.clockSkewToleranceMs)
        assertEquals(bool("auditEnabled"), bootstrap.auditEnabled)
    }

    @Test
    fun `given the shared file then every activity price matches`() {
        val body = section("activityPrices")
        val prices =
            ActivityKind.entries.associateWith { kind ->
                Regex(""""${kind.name}"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toInt()
                    ?: error("нет цены ${kind.name} в общем файле")
            }

        assertEquals(prices, EconomyConstants.BOOTSTRAP.activityPrices)
    }
}
