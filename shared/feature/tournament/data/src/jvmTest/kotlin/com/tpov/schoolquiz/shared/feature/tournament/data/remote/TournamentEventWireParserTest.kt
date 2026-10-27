package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Контракт разбора проекций события против настоящих ответов контроллера: фикстуры сняты с
 * реального `functions/index.js` (firestore-stub) и связаны с хэшами его исходников. Каждая
 * разрешённая форма читается целиком; лишний ключ, недостающее поле и неверный тип — отказ.
 */
class TournamentEventWireParserTest {
    private val fixture = loadFixture()
    private val parser = TournamentEventWireParser()

    @Test
    fun `qualification event read carries every allowlisted field`() {
        val read = parser.parseEventRead(fixture.wire("eventReadQualification"))
        val event = read.event
        assertEquals("event_fixture_00000001", event.eventId)
        assertEquals("author", event.organizerUid)
        assertEquals("QUALIFICATION", event.status)
        assertNull(event.cancelReason)
        assertEquals("COURSE", event.source.kind)
        assertEquals("course", event.source.id)
        assertEquals(1788739200000L, event.qualification.startsAtMs)
        assertEquals(1789344000000L, event.qualification.endsAtMs)
        assertEquals(3, event.qualification.roundCount)
        assertEquals(20, event.qualification.questionsPerRound)
        assertEquals(1, event.qualification.attemptsPerRound)
        // Единая призовая проекция v1: строки {place, amountBoxes} и сохранённая ответственность.
        assertEquals(1, event.prizes.listVersion)
        assertEquals(listOf(5L, 3L, 1L, 1L), event.prizes.rows.map { it.amountBoxes })
        assertEquals(listOf(1, 2, 3, 3), event.prizes.rows.map { it.place })
        assertEquals(10L, event.prizes.maximumLiabilityBoxes)
        assertEquals(12L, event.escrow.reservedBoxes)
        assertEquals(0L, event.escrow.paidBoxes)
        assertTrue(event.progress == null && event.matches.isEmpty())
        assertEquals(1788739200000L, read.serverNowMs)
    }

    @Test
    fun `bracket event read adds progress and matches only in the grid read`() {
        val read = parser.parseEventRead(fixture.wire("eventReadBracket"))
        val event = read.event
        assertEquals("BRACKET", event.status)
        val progress = requireNotNull(event.progress)
        assertEquals("ACTIVE", progress.status)
        assertTrue(progress.resolved.isEmpty() && progress.placements.isEmpty())
        val matchId = "[\"tournament\",\"event_fixture_00000001\",\"match\",1,0]"
        val pending = progress.pending.single()
        assertEquals(matchId, pending.matchId)
        assertEquals(listOf("alice", "bob"), pending.entrants.map { it.uid })
        assertEquals(listOf(1L, 2L), pending.entrants.map { it.seed })
        val match = event.matches.single()
        assertEquals(matchId, match.matchId)
        assertEquals(1L, match.stage)
        assertEquals(280000L, match.maxDurationMs)
        assertEquals(1789344000000L, match.stageStartsAtMs)
        assertEquals(1789603200000L, match.stageEndsAtMs)
        // Час планирования — край сцены минус час готовности и минус длительность общей формы.
        assertEquals(1789603200000L - 3_600_000 - 280000, match.schedulingDeadlineMs)
        assertEquals(1789344000000L, read.serverNowMs)
    }

    @Test
    fun `finished event read decodes the frozen prize-slot names`() {
        val read = parser.parseEventRead(fixture.wire("eventReadFinished"))
        val event = read.event
        assertEquals("FINISHED", event.status)
        val progress = requireNotNull(event.progress)
        // Слот — имя призового слота сервера («champion», «runner-up»), а не число.
        assertEquals(listOf("champion", "runner-up"), progress.placements.map { it.slot })
        assertEquals(listOf(1L, 2L), progress.placements.map { it.rank })
        assertEquals(listOf("alice", "bob"), progress.placements.map { it.uid })
        assertEquals("PLAYED", progress.resolved.single().status)
        // Терминальный эскроу замороженной публикации читается тем же чтением.
        assertEquals(0L, event.escrow.remainingBoxes)
        assertEquals(8L, event.escrow.paidBoxes)
    }

    @Test
    fun `numeric prize slot fails closed`() {
        fun mutated(block: (MutableMap<String, Any?>) -> Unit): String {
            val root = Json.parseToJsonElement(fixture.wire("eventReadFinished")).jsonObject
            val copy = TournamentWireTestMutable.deepCopy(root)
            block(copy)
            return Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        }
        // Числового слота сервер не публикует: имя, замороженное planPrizePayouts, — не число.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutated {
                    val event = it["event"] as MutableMap<String, Any?>
                    val progress = event["progress"] as MutableMap<String, Any?>
                    val placements = progress["placements"] as MutableList<Any?>
                    (placements[0] as MutableMap<String, Any?>)["slot"] = 1
                },
            )
        }
    }

    @Test
    fun `embedded event of an operation view never accepts match rows`() {
        val bracket = Json.parseToJsonElement(fixture.wire("eventReadBracket")).jsonObject
        val event = bracket.getValue("event").jsonObject
        assertFailsWith<MalformedWire> { parser.event(event, withMatches = false) }
    }

    @Test
    fun `both list pages decode and the cursor stays bound to the last row`() {
        val first = parser.parseEventPage(fixture.wire("listEventsFirst"))
        assertTrue(first.hasMore)
        assertEquals("event_fixture_00000001", first.nextEventId)
        assertEquals(first.nextEventId, first.events.last().eventId)
        val last = parser.parseEventPage(fixture.wire("listEventsLast"))
        assertTrue(!last.hasMore)
        assertNull(last.nextEventId)
    }

    @Test
    fun `legacy prize projection v0 decodes through the same rows`() {
        val page = parser.parseEventPage(fixture.wire("listEventsLegacyPrizes"))
        val prizes = page.events.single().prizes
        assertEquals(0, prizes.listVersion)
        assertEquals(listOf(5L, 3L, 1L, 1L), prizes.rows.map { it.amountBoxes })
        assertEquals(10L, prizes.maximumLiabilityBoxes)
    }

    @Test
    fun `unknown key sets and broken types fail closed`() {
        fun mutated(block: (MutableMap<String, Any?>) -> Unit): String {
            val root = Json.parseToJsonElement(fixture.wire("eventReadQualification")).jsonObject
            val copy = TournamentWireTestMutable.deepCopy(root)
            block(copy)
            return Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        }
        // Лишний ключ проекции — дрейф, не поле.
        assertFailsWith<MalformedWire> { parser.parseEventRead(mutated { it.put("seed", 1) }) }
        // Отсутствующее обязательное поле — не «ноль», а отказ.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutated {
                    val event = it["event"] as MutableMap<String, Any?>
                    event.remove("escrow")
                },
            )
        }
        // Вложенный дрейф тоже не читается молча.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutated {
                    val event = it["event"] as MutableMap<String, Any?>
                    val prizes = event["prizes"] as MutableMap<String, Any?>
                    prizes.put("first", 5)
                },
            )
        }
        // Неверный тип числа — отказ, а не приведение.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(mutated { it.put("serverNowMs", "1788739200000") })
        }
        // Необязательный текст, которого сервер не назвал, — пустота; названный не текстом — отказ.
        assertNull(parser.parseEventRead(fixture.wire("eventReadQualification")).event.cancelReason)
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutated {
                    val event = it["event"] as MutableMap<String, Any?>
                    event.put("cancelReason", 5)
                },
            )
        }
        // Число вне безопасного целочисленного диапазона сервера — отказ.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(mutated { it.put("serverNowMs", 9_007_199_254_740_992L) })
        }
        // Курсор, не зовущий последнюю строку, — несвязная страница.
        val page = Json.parseToJsonElement(fixture.wire("listEventsFirst")).jsonObject
        val pageCopy = TournamentWireTestMutable.deepCopy(page)
        (pageCopy["events"] as MutableList<Any?>).clear()
        val broken = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(pageCopy))
        assertFailsWith<MalformedWire> { parser.parseEventPage(broken) }
    }

    @Test
    fun `published ranking rides only with the event read`() {
        val read = parser.parseEventRead(fixture.wire("eventReadRanking"))
        val ranking = requireNotNull(read.event.ranking)
        assertEquals(2L, ranking.totalRanked)
        assertEquals(listOf("alice", "bob"), ranking.rows.map { it.uid })
        assertEquals(listOf(1, 2), ranking.rows.map { it.place })
        // Замороженный документ сегодня проценты не хранит: пустота честная, а не ноль.
        assertTrue(ranking.rows.all { it.ratingPercent == null && it.averagePercent == null })
        // У страницы просмотра ключа быть не может: дрейф, а не витрина.
        val rankedEvent = Json.parseToJsonElement(fixture.wire("eventReadRanking")).jsonObject.getValue("event")
        val page = Json.parseToJsonElement(fixture.wire("listEventsFirst")).jsonObject
        val pageCopy = TournamentWireTestMutable.deepCopy(page)
        (pageCopy["events"] as MutableList<Any?>).add(TournamentWireTestMutable.deepCopy(rankedEvent))
        assertFailsWith<MalformedWire> {
            parser.parseEventPage(
                Json.encodeToString(
                    kotlinx.serialization.json.JsonElement.serializer(),
                    TournamentWireTestMutable.build(pageCopy),
                ),
            )
        }
    }

    @Test
    fun `ranking drift and broken ranking rows fail closed`() {
        fun mutatedEventRead(block: (MutableMap<String, Any?>) -> Unit): String {
            val root = Json.parseToJsonElement(fixture.wire("eventReadRanking")).jsonObject
            val copy = TournamentWireTestMutable.deepCopy(root)
            block(copy)
            return Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        }
        // Лишний ключ строки рейтинга — дрейф замороженного документа, а не витрина.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutatedEventRead {
                    val event = it["event"] as MutableMap<String, Any?>
                    val ranking = event["qualificationRanking"] as MutableMap<String, Any?>
                    (ranking["rows"] as MutableList<Any?>).add(mapOf("uid" to "eve", "place" to 3, "percentScore" to 99))
                },
            )
        }
        // Отсутствующий объём когорты — не «ноль», а отказ.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutatedEventRead {
                    val event = it["event"] as MutableMap<String, Any?>
                    (event["qualificationRanking"] as MutableMap<String, Any?>).remove("totalRanked")
                },
            )
        }
        // Место меньше единицы — не строка рейтинга.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutatedEventRead {
                    val event = it["event"] as MutableMap<String, Any?>
                    val ranking = event["qualificationRanking"] as MutableMap<String, Any?>
                    ranking["rows"] = listOf(mapOf("uid" to "alice", "place" to 0))
                },
            )
        }
        // Объём когорты меньше, чем строк, — не проекция: сервер не публикует такую витрину.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutatedEventRead {
                    val event = it["event"] as MutableMap<String, Any?>
                    val ranking = event["qualificationRanking"] as MutableMap<String, Any?>
                    ranking["totalRanked"] = 1
                },
            )
        }
        // Отрицательный объём вне целого диапазона сервера — отказ разбора, а не ноль.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutatedEventRead {
                    val event = it["event"] as MutableMap<String, Any?>
                    val ranking = event["qualificationRanking"] as MutableMap<String, Any?>
                    ranking["totalRanked"] = -1
                },
            )
        }
    }

    @Test
    fun `sixteen prize rows with place 16 decode and the seventeenth fails closed`() {
        // L3: серверный кап MAX_QUALIFIERS=16 — полный список top16 (место 16) легален на
        // проводе и читается целиком; 17-я строка/место 17 — MalformedWire, не «почти влезло».
        val read = parser.parseEventRead(fixture.wire("eventReadPrizeBoundary16"))
        val prizes = read.event.prizes
        assertEquals(1, prizes.listVersion)
        assertEquals((1..16).toList(), prizes.rows.map { it.place })
        assertEquals(5L, prizes.rows.first().amountBoxes)
        assertEquals(1L, prizes.rows.last().amountBoxes)
        assertEquals(22L, prizes.maximumLiabilityBoxes)

        fun mutated(block: (MutableMap<String, Any?>) -> Unit): String {
            val root = Json.parseToJsonElement(fixture.wire("eventReadPrizeBoundary16")).jsonObject
            val copy = TournamentWireTestMutable.deepCopy(root)
            block(copy)
            return Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        }
        // 17-я строка — не призовой список этого контракта.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutated {
                    val event = it["event"] as MutableMap<String, Any?>
                    val prizesMap = event["prizes"] as MutableMap<String, Any?>
                    (prizesMap["rows"] as MutableList<Any?>).add(mapOf("place" to 16, "amountBoxes" to 1))
                },
            )
        }
        // Место 17 вне замороженного cap'а — отказ разбора.
        assertFailsWith<MalformedWire> {
            parser.parseEventRead(
                mutated {
                    val event = it["event"] as MutableMap<String, Any?>
                    val prizesMap = event["prizes"] as MutableMap<String, Any?>
                    val rows = prizesMap["rows"] as MutableList<Any?>
                    (rows[15] as MutableMap<String, Any?>)["place"] = 17
                },
            )
        }
    }

    @Test
    fun `fixture shapes stay bound to the actual server sources`() {
        val sources = fixture.getValue("sourceSha256").jsonObject
        for (name in listOf("course-tournament-controller.js", "tournament-session.js", "tournament-policy.js")) {
            assertEquals(
                sources.getValue(name).jsonPrimitive.content,
                TournamentWireTestHash.sha256(File(findRepoRoot(), "functions/$name").readText()),
                "Regenerate tournament fixtures for $name",
            )
        }
    }

    private fun loadFixture(): kotlinx.serialization.json.JsonObject {
        val path = "shared/feature/tournament/data/src/jvmTest/resources/tournament-client-fixtures.json"
        return Json.parseToJsonElement(File(findRepoRoot(), path).readText()).jsonObject
    }

    /** Сырой провод члена фикстуры — для парсера, а не для скалярного чтения. */
    private fun kotlinx.serialization.json.JsonObject.wire(key: String): String = getValue(key).toString()

    private fun findRepoRoot(): File =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
}
