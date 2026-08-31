package com.tpov.schoolquiz.shared.feature.lesson.domain

import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.LessonAccess
import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.resolveLessonAccess
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlin.test.Test
import kotlin.test.assertEquals

class LessonGateTest {

    private val l1 = LessonId("l1")
    private val l2 = LessonId("l2")
    private val l3 = LessonId("l3")
    private val order = listOf(l1, l2, l3)

    @Test
    fun `first lesson is open with nothing passed`() {
        val access = resolveLessonAccess(order, passed = emptySet(), purchased = emptySet())
        assertEquals(LessonAccess.OPEN, access[l1])
    }

    @Test
    fun `second lesson is locked until the first is passed`() {
        val access = resolveLessonAccess(order, passed = emptySet(), purchased = emptySet())
        assertEquals(LessonAccess.LOCKED, access[l2])
        assertEquals(LessonAccess.LOCKED, access[l3])
    }

    @Test
    fun `passing the first opens the second and no further`() {
        val access = resolveLessonAccess(order, passed = setOf(l1), purchased = emptySet())
        assertEquals(LessonAccess.OPEN, access[l2])
        assertEquals(LessonAccess.LOCKED, access[l3])
    }

    @Test
    fun `any locked lesson can be bought, not only the next`() {
        val access = resolveLessonAccess(order, passed = emptySet(), purchased = setOf(l3))
        assertEquals(LessonAccess.LOCKED, access[l2])
        assertEquals(LessonAccess.PURCHASED, access[l3])
    }

    @Test
    fun `buying does not open the lesson after it`() {
        // The point of the whole rule: nolics grant access, never progress. Buying lesson 2 leaves
        // lesson 3 shut, because lesson 2 was never played and so was never passed.
        val access = resolveLessonAccess(order, passed = emptySet(), purchased = setOf(l2))
        assertEquals(LessonAccess.PURCHASED, access[l2])
        assertEquals(LessonAccess.LOCKED, access[l3])
    }

    @Test
    fun `playing a bought lesson opens the next one normally`() {
        val access = resolveLessonAccess(order, passed = setOf(l2), purchased = setOf(l2))
        assertEquals(LessonAccess.OPEN, access[l3])
    }

    @Test
    fun `passing a lesson without passing the one before still opens only the one after it`() {
        // Reachable by buying l2, playing it, and never returning to l1.
        val access = resolveLessonAccess(order, passed = setOf(l2), purchased = setOf(l2))
        assertEquals(LessonAccess.OPEN, access[l1])
        assertEquals(LessonAccess.PURCHASED, access[l2])
        assertEquals(LessonAccess.OPEN, access[l3])
    }

    @Test
    fun `an empty course resolves to nothing`() {
        assertEquals(emptyMap(), resolveLessonAccess(emptyList(), emptySet(), emptySet()))
    }

    @Test
    fun `ids outside the course order are ignored`() {
        val access = resolveLessonAccess(
            orderedLessonIds = listOf(l1),
            passed = setOf(LessonId("elsewhere")),
            purchased = setOf(LessonId("elsewhere")),
        )
        assertEquals(mapOf(l1 to LessonAccess.OPEN), access)
    }
}
