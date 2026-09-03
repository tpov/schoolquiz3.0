package com.tpov.schoolquiz.platform.billing

import java.security.MessageDigest

/**
 * The buyer, named to Play in a way that identifies nobody.
 *
 * Play carries this value as `obfuscatedAccountId` and hands it back to the server with the
 * purchase, which is how the server refuses a token presented by an account that did not pay for
 * it. Play stores it, shows it in the console and puts it in exported reports, so the raw uid must
 * not go in: a hash is enough to compare two purchases, and useless for identifying anyone.
 *
 * The value has to be **byte-identical** to `accountIdFor` in `functions/purchase-verification.js`,
 * which is `sha256` of the uid as UTF-8, hex, lowercase. Any drift — a different encoding, upper
 * case, a truncation — turns every purchase into "made by another account" and refuses real money.
 * The 64 hex characters also happen to be exactly Play's limit for the field.
 */
object BuyerTag {
    fun of(uid: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> HEX[byte.toInt() and 0xFF] }

    /** Both hex digits per byte, precomputed — the loop above runs on every purchase. */
    private val HEX: Array<String> = Array(256) { value -> value.toString(16).padStart(2, '0') }
}
