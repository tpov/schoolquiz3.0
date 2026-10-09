package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Инварианты контракта UX-O6 (история 4.4 эпика `epic-authoring-review`, полная редакция:
 * `_bmad-output/mvp-candidate/epic-authoring-review/ux-o6-annotation-contract.md`).
 *
 * Закрепляется то, что уже существует и что editor-адаптеры (4.5+) обязаны переиспользовать:
 * единицы и инварианты авторской схемы FillBlank (§2 — blank/correctness/защита как разные
 * значения, типизированные id), и семантика redaction поверх общих кросс-языковых фикстур (§5/§7 —
 * ответ, выписанный словом, не публикуется; переводная защита без ответа переживает; ключи
 * ответов не появляются нигде). Байт-уровень формы уже держат `RedactedQuestionWireTest` и
 * `functions/question-redaction-wire.test.js`; здесь — правила, а не байты.
 */
class UxO6AnnotationSerializationContractTest {
    private val parser = KotlinxSerializationQuestionContentParser()
    private val fixtures by lazy {
        val stream = javaClass.classLoader
            .getResourceAsStream("redacted-question-fixtures.json")
            ?: error("redacted-question-fixtures.json не на classpath jvmTest")
        Json.parseToJsonElement(stream.readBytes().decodeToString()).jsonObject
    }

    private fun fillBlank(
        blanks: List<QuestionContent.Blank> = listOf(
            QuestionContent.Blank(BlankId("b1"), CandidateId("c1")),
        ),
        candidates: List<QuestionContent.Candidate> =
            listOf("Kotlin", "Java", "Swift", "Go", "Scala").mapIndexed { i, t ->
                QuestionContent.Candidate(CandidateId("c${i + 1}"), t)
            },
        protectedTextSegments: List<String> = emptyList(),
    ) = QuestionContent.FillBlank(
        id = "q-fb",
        difficulty = Difficulty.EASY,
        text = "***Kotlin*** targets the JVM.",
        imageUrl = null,
        blanks = blanks,
        candidates = candidates,
        protectedTextSegments = protectedTextSegments,
    )

    // ── §2: единицы схемы — blank, correctness и защита как разные значения ──────────────────

    @Test
    fun `fill blank units are ids and the schema rejects wrong counts duplicates and stray answers`() {
        val fiveCandidates = (1..5).map { QuestionContent.Candidate(CandidateId("c$it"), "t$it") }
        val tenCandidates = (1..10).map { QuestionContent.Candidate(CandidateId("c$it"), "t$it") }

        // Границы количества: 0 и 4 пробела отвергнуты, 1 и 3 законны; пул — только 5 или 10.
        assertFailsWith<IllegalArgumentException> { fillBlank(blanks = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            fillBlank(
                blanks = (1..4).map { QuestionContent.Blank(BlankId("b$it"), CandidateId("c$it")) },
            )
        }
        assertFailsWith<IllegalArgumentException> { fillBlank(candidates = fiveCandidates.dropLast(1)) }
        assertEquals(1, fillBlank(candidates = tenCandidates).blanks.size)

        // Идентичность внутри единиц: дубликаты id и ссылка мимо пула отвергнуты.
        assertFailsWith<IllegalArgumentException> {
            fillBlank(
                blanks = listOf(
                    QuestionContent.Blank(BlankId("b1"), CandidateId("c1")),
                    QuestionContent.Blank(BlankId("b1"), CandidateId("c2")),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            fillBlank(
                candidates = fiveCandidates + QuestionContent.Candidate(CandidateId("c1"), "dupe"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            fillBlank(blanks = listOf(QuestionContent.Blank(BlankId("b1"), CandidateId("nope"))))
        }

        // Защита — не blank и не correctness: свои значения, без пустых строк.
        assertFailsWith<IllegalArgumentException> {
            fillBlank(protectedTextSegments = listOf(" "))
        }
        assertEquals(listOf("JVM targets"), fillBlank(protectedTextSegments = listOf("JVM targets")).protectedTextSegments)
    }

    // ── §2: позиционная связка маркер↔blank, порядок как единственная привязка ───────────────

    @Test
    fun `redacted blanks stay bare ids in marker order with no correctness mapping`() {
        for (case in redactedCases()) {
            val payload = (case["publicPayload"] as? JsonObject ?: continue)
            if (payload["type"]?.toString() != "\"FillBlankRedacted\"") continue
            val blanks = payload["blanks"] as? JsonArray ?: continue
            // Голые id в порядке маркеров; никакого mapping на кандидатов рядом нет.
            assertTrue(blanks.all { it.toString().matches(Regex("\"[^\"]+\"")) })
            assertEquals(null, payload["blankToCandidate"])
            assertEquals(null, payload["correctCandidateId"])
        }
    }

    // ── §5/§7: redaction-семантика на общем корпусе фикстур ──────────────────────────────────

    @Test
    fun `redacted corpus never carries answer keys or answer-spelled-out segments`() {
        val forbidden = stringList("forbiddenKeys")
        assertTrue(forbidden.contains("correctCandidateId") && forbidden.contains("info"))

        for (case in redactedCases()) {
            val payload = (case["publicPayload"] as? JsonObject) ?: error("фикстура без payload")
            // (а) ни один ключ-ответ не появляется ни в одной публичной половине (§7).
            for (key in payload.keys) {
                assertTrue(key !in forbidden, "ключ ответа \"$key\" просочился в ${case["name"]}")
            }
            if (payload["type"]?.toString() != "\"FillBlankRedacted\"") continue
            // (б) выписанный ответ не публикуется рядом с перемешанным пулом (§5): сегмент,
            //     совпадающий с текстом кандидата, не переживает redaction.
            val candidateTexts =
                (payload["candidates"] as? JsonArray)
                    ?.map { (it as JsonObject)["text"]?.toString()?.trim('"') ?: "" }
                    ?: emptyList()
            val segments = (payload["protectedTextSegments"] as? JsonArray)
                ?.map { it.toString().trim('"') }
                ?: emptyList()
            assertTrue(
                segments.none { it in candidateTexts },
                "сегмент-ответ пережил redaction в ${case["name"]}",
            )
        }
    }

    @Test
    fun `translation-only protected segment survives redaction while absent segments omit the key`() {
        // Фикстура «segment that gives nothing away»: переводная защита без ответа — публикуется.
        val survivor = redactedCases().first { it["name"]!!.toString().trim('"').contains("gives nothing away") }
        val payload = survivor["publicPayload"] as? JsonObject ?: error("нет payload")
        val segments = (payload["protectedTextSegments"] as? JsonArray)?.map { it.toString().trim('"') }
        assertTrue(!segments.isNullOrEmpty(), "переводная защита обязана пережить redaction")
        assertTrue(segments.all { it !in listOf("Kotlin", "Scala") }, "это выписанный ответ, не защита")

        // Фикстура «never carried protectedTextSegments»: ключ отсутствует, а не пустой массив.
        val omitted = redactedCases().first { it["name"]!!.toString().trim('"').contains("never carried") }
        val omittedPayload = omitted["publicPayload"] as? JsonObject ?: error("нет payload")
        assertEquals(null, omittedPayload["protectedTextSegments"])
    }

    // ── §3: типизированные id — авторские единицы не склеиваются со строками redaction ───────

    @Test
    fun `author side keeps typed ids distinct from redacted string identities`() {
        val json =
            """{"type":"FillBlank","id":"q1","difficulty":"EASY","text":"***Kotlin*** time","imageUrl":null,""" +
                """"blanks":[{"id":"b1","correctCandidateId":"c1"}],""" +
                """"candidates":[{"id":"c1","text":"Kotlin"},{"id":"c2","text":"Java"},{"id":"c3","text":"Swift"},{"id":"c4","text":"Go"},{"id":"c5","text":"Scala"}]}"""
        val content = parser.parse(json).getOrThrow() as QuestionContent.FillBlank
        // Blank и Candidate — разные типы значений: blank называет кандидата корректностью,
        // но сам им не является; идентичности не сравниваются «по строке».
        val blank = content.blanks.single()
        assertEquals(BlankId("b1"), blank.id)
        assertEquals(CandidateId("c1"), blank.correctCandidateId)
        // Корректность — ссылка между типами: blank (BlankId) называет кандидата (CandidateId).
        assertTrue(content.candidates.any { it.id == blank.correctCandidateId })
        // Авторский payload парсится в авторский тип; redacted-строка id (rc-…) сюда не доходит.
        assertTrue(content.candidates.map { it.id }.none { it.raw.startsWith("rc-") })
    }

    private fun redactedCases(): List<JsonObject> {
        val array = fixtures["redacted"] as? JsonArray ?: error("фикстуры без массива redacted")
        return array.map { case ->
            val obj = case.jsonObject
            // publicPayload в фикстуре — JSON-строка с байтами публичной половины.
            val payload = (obj["publicPayload"] as kotlinx.serialization.json.JsonPrimitive).content
            buildMap {
                putAll(obj)
                put("publicPayload", Json.parseToJsonElement(payload).jsonObject)
            }.let { kotlinx.serialization.json.JsonObject(it) }
        }
    }

    private fun stringList(key: String): List<String> {
        val array = fixtures[key] as? JsonArray ?: error("фикстуры без массива $key")
        return array.map { it.toString().trim('"') }
    }
}
