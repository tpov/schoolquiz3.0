package com.tpov.schoolquiz.shared.core.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class StringSetConverterTest {

    private val converter = StringSetConverter()

    // Spec scenario: GIVEN converter.fromSet(null) THEN == null
    @Test
    fun `fromSet null returns null`() {
        assertNull(converter.fromSet(null))
    }

    // Spec scenario: GIVEN converter.toSet(null) THEN == null
    @Test
    fun `toSet null returns null`() {
        assertNull(converter.toSet(null))
    }

    // Spec scenario: GIVEN original = setOf("home","arena") WHEN toSet(fromSet(original)) THEN == original
    @Test
    fun `roundtrip non empty set`() {
        val original = setOf("home", "arena")
        val result = converter.toSet(converter.fromSet(original))
        assertEquals(original, result)
    }

    // Spec scenario: GIVEN original = emptySet WHEN toSet(fromSet(original)) THEN == emptySet
    @Test
    fun `roundtrip empty set`() {
        val original = emptySet<String>()
        val result = converter.toSet(converter.fromSet(original))
        assertEquals(original, result)
    }

    // Spec scenario: validate separator is \u001F not comma
    @Test
    fun `fromSet does not use comma separator`() {
        val encoded = converter.fromSet(setOf("home", "arena"))
        assertFalse("Separator must be \\u001F, not comma", encoded?.contains(",") ?: false)
    }
}
