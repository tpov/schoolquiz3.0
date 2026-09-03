package com.tpov.schoolquiz.platform.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The buyer tag must match the server's `accountIdFor` byte for byte.
 *
 * These are not "does sha256 work" tests. They are the contract between two languages: if this
 * value drifts from `functions/purchase-verification.js`, Play reports a buyer the server does not
 * recognise and every real purchase is refused as belonging to another account. The expected
 * strings below are the published sha-256 digests of their inputs, so a change on either side of
 * the wire has to notice this file.
 */
class BuyerTagTest {

    /** The value the story's matrix names for uid "abc". */
    @Test
    fun `matches the server's hash for the uid in the specification`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            BuyerTag.of("abc"),
        )
    }

    @Test
    fun `hashes the empty uid to the published empty digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            BuyerTag.of(""),
        )
    }

    /**
     * Firebase uids are 28 alphanumeric characters. Nothing about the length may leak through, and
     * the result must stay inside Play's 64-character limit for the field.
     */
    @Test
    fun `is always sixty four lowercase hex characters`() {
        val tags =
            listOf("", "abc", "a", "wq2Zt3XkLmNbVcXz9PqRsTuVwXy1", "uid-with-🙂-and-ünïcode")
                .map(BuyerTag::of)

        tags.forEach { tag ->
            assertEquals("Wrong length for $tag", 64, tag.length)
            assertTrue("Not lowercase hex: $tag", tag.all { it in "0123456789abcdef" })
        }
    }

    /** Non-ASCII must be hashed as UTF-8, the same encoding Node's `update(value)` uses. */
    @Test
    fun `hashes non-ascii uids as utf-8`() {
        assertEquals(
            "607474ca475a9724d7360aba71a56d5df77e61350e3f724cfa1f46e857e2d85f",
            BuyerTag.of("ü"),
        )
    }

    @Test
    fun `different accounts get different tags`() {
        assertNotEquals(BuyerTag.of("uid-A"), BuyerTag.of("uid-B"))
    }

    @Test
    fun `the same account always gets the same tag`() {
        assertEquals(BuyerTag.of("uid-A"), BuyerTag.of("uid-A"))
    }
}
