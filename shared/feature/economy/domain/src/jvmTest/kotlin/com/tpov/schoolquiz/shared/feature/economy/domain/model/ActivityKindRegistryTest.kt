package com.tpov.schoolquiz.shared.feature.economy.domain.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Клиент и приёмник называют виды платной активности одинаково.
 *
 * Согласие с условиями едет с ценой каждому виду, который знает сервер (`readCapturedTerms`
 * требует строку для каждого имени `ACTIVITY_KINDS`), и пишет его клиентский кодек
 * (`PayableTermsCodec` перебирает `ActivityKind.entries`). Разойдись списки — и первый же
 * клиент старой сборки отвечает неполным согласием на каждую платную мутацию, а первый же
 * новый вид не имел бы цены на устройстве. В бою это выглядит как вечное «условия изменились»
 * или как вечный отказ формы — ловить нечем, кроме этого пина (ретро эпика 2, F7).
 *
 * Поэтому список читается текстом из серверного исходника. JVM-тест, а не общий: файл с диска
 * общий код не видит. Образец — `OutboxOperationsRegistryTest`.
 */
class ActivityKindRegistryTest {
    private val source: String by lazy {
        val file = File("../../../../functions/economy-constants.js")
        assertTrue(file.exists(), "нет серверного прейскуранта: ${file.absolutePath}")
        file.readText()
    }

    private val serverKinds: List<String> by lazy {
        val body =
            Regex("""const ACTIVITY_KINDS = \[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
        assertTrue(body != null, "не нашёл список ACTIVITY_KINDS в functions/economy-constants.js")
        Regex(""""([A-Z_]+)"""").findAll(body!!)
            .map { it.groupValues[1] }
            .toList()
    }

    @Test
    fun `given every activity kind the client names then the server pricelist names it too`() {
        assertEquals(ActivityKind.entries.map { it.name }, serverKinds)
    }

    @Test
    fun `given every kind the server prices then the client names it`() {
        assertEquals(serverKinds.toSet(), ActivityKind.entries.map { it.name }.toSet())
    }
}
