package com.tpov.schoolquiz.shared.feature.internet.profile.domain

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.sanitizeProfileNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.validateProfileNickname
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileNicknameTest {
    @Test
    fun sanitizeProfileNickname_collapsesWhitespace() {
        assertEquals("User Name", sanitizeProfileNickname("  User   Name  "))
    }

    @Test
    fun validateProfileNickname_rejectsTooShortNickname() {
        assertTrue(validateProfileNickname("ab").isFailure)
    }

    @Test
    fun validateProfileNickname_acceptsNormalNickname() {
        assertEquals("User Name", validateProfileNickname(" User Name ").getOrThrow())
    }
}
