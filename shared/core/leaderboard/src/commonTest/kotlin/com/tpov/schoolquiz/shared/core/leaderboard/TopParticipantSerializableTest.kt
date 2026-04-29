package com.tpov.schoolquiz.shared.core.leaderboard

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TopParticipantSerializableTest {

    private val json = Json

    @Test
    fun `given TopParticipant with avatarUrl when json roundtrip then decoded equals original`() {
        // Spec scenario E-01: topParticipant_serializable_roundtrip
        val original = TopParticipant(
            nickname = "Bob",
            avatarUrl = "https://example.com/avatar.png",
            percent = 85,
        )
        val encoded = json.encodeToString(TopParticipant.serializer(), original)
        val decoded = json.decodeFromString(TopParticipant.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `given TopParticipant with null avatarUrl when json roundtrip then avatarUrl remains null`() {
        // Spec scenario E-02: topParticipant_nullAvatarUrl_roundtrip
        val original = TopParticipant(nickname = "X", avatarUrl = null, percent = 0)
        val encoded = json.encodeToString(TopParticipant.serializer(), original)
        val decoded = json.decodeFromString(TopParticipant.serializer(), encoded)

        assertNull(decoded.avatarUrl, "avatarUrl must round-trip as null, not the string \"null\"")
        assertEquals(original, decoded)
    }
}
