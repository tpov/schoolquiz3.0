package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.ReferralDeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReferralDeepLinkTest {
    @Test
    fun `parses the link format the app itself hands out`() {
        assertEquals("abc123", ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=abc123")))
    }

    @Test
    fun `code survives alongside other query params`() {
        assertEquals(
            "xyz",
            ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?utm_source=telegram&id=xyz")),
        )
    }

    @Test
    fun `wrong scheme is not our link`() {
        assertNull(ReferralDeepLink.referrerCode(DeepLink("https://referral?id=abc123")))
    }

    @Test
    fun `wrong host is not our link`() {
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://quest?id=abc123")))
    }

    @Test
    fun `path under the host is not registered`() {
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral/invite?id=abc123")))
    }

    @Test
    fun `missing or blank code is refused`() {
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral")))
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?utm_source=x")))
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=")))
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=   ")))
    }

    @Test
    fun `code that the server would reject as a document id is refused`() {
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=a/b")))
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=.")))
        assertNull(ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=..")))
        assertNull(
            ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=${"a".repeat(129)}")),
        )
    }

    @Test
    fun `a code of the maximal server length passes`() {
        val code = "a".repeat(ReferralDeepLink.MAX_CODE_LENGTH)
        assertEquals(code, ReferralDeepLink.referrerCode(DeepLink("schoolquiz://referral?id=$code")))
    }
}
