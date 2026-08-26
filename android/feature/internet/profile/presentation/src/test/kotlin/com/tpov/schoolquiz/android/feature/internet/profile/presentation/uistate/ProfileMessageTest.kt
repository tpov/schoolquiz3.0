package com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The message-to-words resolver ([ProfileScreen]'s private `resolvedText`) is a composable, so the
 * wording itself belongs to screen tests. What a plain unit test can pin down without composition
 * is the payload contract each message carries into that resolver.
 */
class ProfileMessageTest {

    @Test
    fun `failure without detail keeps null so the screen falls back to its own line`() {
        assertNull(ProfileMessage.Failure(detail = null).detail)
    }

    @Test
    fun `failure keeps the raw platform detail verbatim`() {
        val detail = "SERVER_500_UNAVAILABLE"

        assertEquals(detail, ProfileMessage.Failure(detail = detail).detail)
    }

    @Test
    fun `nickname activated carries the activated nickname`() {
        val state = ProfileMessage.NicknameActivated(nickname = "НовыйНик")

        assertEquals("НовыйНик", state.nickname)
    }
}
