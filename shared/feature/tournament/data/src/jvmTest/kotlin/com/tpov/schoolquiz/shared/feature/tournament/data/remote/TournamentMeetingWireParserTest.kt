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

/** Контракт разбора страницы встречи против формы `readMeeting`; курсоры круглого хода. */
class TournamentMeetingWireParserTest {
    private val fixture = loadFixture()
    private val parser = TournamentMeetingWireParser()

    @Test
    fun `meeting page carries meeting bounds participants pages and readiness`() {
        val page = parser.parseMeetingPage(fixture.wire("meetingPage"))
        assertEquals("event_fixture_00000001", page.eventId)
        assertEquals("[\"tournament\",\"event_fixture_00000001\",\"match\",1,0]", page.meeting.matchId)
        assertEquals("WAITING_FOR_READY", page.meeting.status)
        assertEquals(1789344001000L, page.meeting.agreedAtMs)
        assertEquals(1789344004000L, page.meeting.slotStartsAtMs)
        assertEquals(1789347604000L, page.meeting.windowEndMs)
        assertNull(page.meeting.startedAtMs)
        assertNull(page.meeting.winnerUid)
        assertEquals(1789344000000L, page.stageStartsAtMs)
        assertEquals(1789603200000L, page.stageEndsAtMs)
        assertEquals(1789603200000L - 3_600_000 - 280000, page.schedulingDeadlineMs)
        assertEquals(listOf("alice", "bob"), page.participants.map { it.uid })
        assertEquals(listOf(1L, 2L), page.participants.map { it.seed })
        // Первая страница предложений: две из трёх, курсор зовёт последнюю строку страницы.
        assertEquals(2, page.offers.size)
        assertEquals(listOf("alice", "alice"), page.offers.map { it.uid })
        assertTrue(page.offers.all { it.id.length == 64 })
        assertTrue(page.hasMoreOffers)
        assertEquals(page.offers.last().id, page.nextOfferId)
        assertEquals(listOf("bob", "alice"), page.confirmations.map { it.uid })
        assertTrue(!page.hasMoreConfirmations)
        assertNull(page.nextConfirmationId)
        assertEquals(listOf("alice"), page.ready.map { it.uid })
        assertEquals(1789344004000L, page.serverNowMs)
    }

    @Test
    fun `offer and confirmation rows reject unknown keys`() {
        val root = Json.parseToJsonElement(fixture.wire("meetingPage")).jsonObject
        val copy = TournamentWireTestMutable.deepCopy(root)
        val offers = copy["offers"] as MutableList<Any?>
        (offers.first() as MutableMap<String, Any?>).put("answerPayload", "{}")
        val wire = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        assertFailsWith<MalformedWire> { parser.parseMeetingPage(wire) }
    }

    @Test
    fun `meeting view alone decodes for the operation response`() {
        val page = Json.parseToJsonElement(fixture.wire("meetingPage")).jsonObject
        val meeting = parser.meeting(page.getValue("meeting").jsonObject)
        assertEquals("[\"tournament\",\"event_fixture_00000001\",\"match\",1,0]", meeting.matchId)
        assertEquals(1789344001000L, meeting.agreedAtMs)
    }

    @Test
    fun `a cursor without its page is malformed`() {
        val root = Json.parseToJsonElement(fixture.wire("meetingPage")).jsonObject
        val copy = TournamentWireTestMutable.deepCopy(root)
        copy["offers"] = mutableListOf<Any?>()
        val wire = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        assertFailsWith<MalformedWire> { parser.parseMeetingPage(wire) }
    }

    @Test
    fun `optional meeting scalars fail closed when present but wrongly typed`() {
        fun mutated(name: String, block: (MutableMap<String, Any?>) -> Unit): String {
            val copy = TournamentWireTestMutable.deepCopy(Json.parseToJsonElement(fixture.wire(name)).jsonObject)
            block(copy)
            return Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        }
        // Необязательное время встречи, несущее строку, — «испорчено», а не «ещё не согласовано».
        assertFailsWith<MalformedWire> {
            parser.parseMeetingPage(
                mutated("meetingPage") {
                    val meeting = it["meeting"] as MutableMap<String, Any?>
                    meeting.put("slotStartsAtMs", "123")
                },
            )
        }
        // Участник без сида — честная пустота; сид-строка — испорчен.
        assertFailsWith<MalformedWire> {
            parser.parseMeetingPage(
                mutated("meetingPage") {
                    val participants = it["participants"] as MutableList<Any?>
                    (participants.first() as MutableMap<String, Any?>).put("seed", "1")
                },
            )
        }
    }

    private fun loadFixture(): kotlinx.serialization.json.JsonObject {
        val path = "shared/feature/tournament/data/src/jvmTest/resources/tournament-client-fixtures.json"
        val repo =
            generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        return Json.parseToJsonElement(File(repo, path).readText()).jsonObject
    }

    /** Сырой провод члена фикстуры — для парсера, а не для скалярного чтения. */
    private fun kotlinx.serialization.json.JsonObject.wire(key: String): String = getValue(key).toString()
}
