package com.tpov.schoolquiz.apps.android_next.push

import com.tpov.schoolquiz.platform.firebase.push.AndroidPushNotifier
import com.tpov.schoolquiz.platform.firebase.push.PushEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * У каждого события реестра есть заголовок и текст во всех трёх локалях (Story 10.4).
 *
 * Украинская локализация обязательна по закону, а не по вкусу, поэтому `values-uk` проверяется
 * наравне с остальными. Чисел в строках нет: push несёт повод, а не данные (AD-20) — допускается
 * не более одного строкового аргумента, и это имя сущности.
 */
class PushStringsResourceTest {
    private val locales = listOf("values", "values-ru", "values-uk")

    @Test
    fun `every event has a title and a text in every locale`() {
        val missing =
            locales.flatMap { locale ->
                val strings = stringsOf(locale)
                PushEvents.all.flatMap { event ->
                    listOf(event.titleKey, event.textKey)
                        .filterNot { it in strings }
                        .map { "$locale/$it" }
                }
            }
        assertEquals("нет строк: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `channel name is translated everywhere`() {
        locales.forEach { locale ->
            val key = AndroidPushNotifier.CHANNEL_NAME_KEY
            assertTrue("$locale: нет $key", key in stringsOf(locale))
        }
    }

    @Test
    fun `notification strings carry no numeric placeholder and at most one argument`() {
        val offenders =
            locales.flatMap { locale ->
                stringsOf(locale)
                    .filterKeys { key -> PushEvents.all.any { key == it.titleKey || key == it.textKey } }
                    .filterValues { value ->
                        NUMERIC_PLACEHOLDER.containsMatchIn(value) || ANY_PLACEHOLDER.findAll(value).count() > 1
                    }
                    .keys
                    .map { "$locale/$it" }
            }
        assertEquals("числа или лишние аргументы в уведомлении: $offenders", emptyList<String>(), offenders)
    }

    private fun stringsOf(locale: String): Map<String, String> {
        val file = File("src/main/res/$locale/strings.xml")
        assertTrue("нет файла ${file.absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .associate { node -> node.attributes.getNamedItem("name").nodeValue to node.textContent }
    }

    private companion object {
        val NUMERIC_PLACEHOLDER = Regex("""%(\d+\$)?[dfx]""")
        val ANY_PLACEHOLDER = Regex("""%(\d+\$)?[sdfx]""")
    }
}
